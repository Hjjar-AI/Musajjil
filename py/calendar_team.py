# py/calendar_team.py
import datetime
from flask import Blueprint, render_template, request
from .database import get_db
from .employee_helpers import get_name_column
from .vacation_holidays import get_all_holiday_dates
from .calendar_core import calendar_bp

@calendar_bp.route('/team')
def team_calendar():
    db = get_db()
    year = request.args.get('year', datetime.date.today().year, type=int)
    month = request.args.get('month', datetime.date.today().month, type=int)
    department = request.args.get('department', '').strip()
    
    dept_col = None
    for f in db.execute('SELECT original_title, column_name FROM employee_fields WHERE original_title="القسم"').fetchall():
        dept_col = f['column_name']
        break
    
    if not dept_col:
        flash('لم يتم العثور على حقل القسم في النظام', 'error')
        return redirect(url_for('calendar.show'))
    
    if department:
        employees = db.execute(f'SELECT id, {get_name_column()} AS name FROM employees WHERE {dept_col}=?', (department,)).fetchall()
    else:
        employees = db.execute(f'SELECT id, {get_name_column()} AS name FROM employees').fetchall()
    
    start_date = datetime.date(year, month, 1)
    if month == 12:
        end_date = datetime.date(year + 1, 1, 1) - datetime.timedelta(days=1)
    else:
        end_date = datetime.date(year, month + 1, 1) - datetime.timedelta(days=1)
    
    emp_ids = [emp['id'] for emp in employees]
    if emp_ids:
        placeholders = ','.join('?' * len(emp_ids))
        leaves = db.execute(f'''
            SELECT ll.id, ll.start_date, ll.end_date, ll.employee_id, vt.name AS type_name
            FROM leave_log ll
            JOIN vacation_types vt ON ll.vacation_type_id = vt.id
            WHERE ll.employee_id IN ({placeholders})
            AND ll.start_date <= ? AND ll.end_date >= ?
            ORDER BY ll.start_date
        ''', emp_ids + [end_date.strftime('%Y-%m-%d'), start_date.strftime('%Y-%m-%d')]).fetchall()
    else:
        leaves = []
    
    from collections import defaultdict
    events = defaultdict(list)
    emp_names = {emp['id']: emp['name'] for emp in employees}
    for leave in leaves:
        start = datetime.datetime.strptime(leave['start_date'], '%Y-%m-%d').date()
        end = datetime.datetime.strptime(leave['end_date'], '%Y-%m-%d').date()
        d = start
        while d <= end:
            if d.month == month and d.year == year:
                name = emp_names.get(leave['employee_id'], str(leave['employee_id']))
                events[d.day].append({
                    'id': leave['id'],
                    'text': f"{name} ({leave['type_name']})",
                    'start': leave['start_date'],
                    'end': leave['end_date'],
                    'employee_name': name
                })
            d += datetime.timedelta(days=1)
    
    import calendar
    cal = calendar.monthcalendar(year, month)
    shifted_weeks = []
    for week in cal:
        shifted_weeks.append([week[5], week[6], week[0], week[1], week[2], week[3], week[4]])
    
    prev_month = month - 1 if month > 1 else 12
    prev_year = year if month > 1 else year - 1
    next_month = month + 1 if month < 12 else 1
    next_year = year if month < 12 else year + 1
    
    holiday_dates = set()
    for h in get_all_holiday_dates():
        try:
            d = datetime.datetime.strptime(h, '%Y-%m-%d').date()
            if d.year == year and d.month == month:
                holiday_dates.add(d.day)
        except:
            pass
    
    departments = [r[0] for r in db.execute(f'SELECT DISTINCT {dept_col} FROM employees WHERE {dept_col} IS NOT NULL AND {dept_col} != ""').fetchall()]
    
    return render_template('team_calendar.html',
                           year=year, month=month, cal=shifted_weeks,
                           events=events,
                           prev_year=prev_year, prev_month=prev_month,
                           next_year=next_year, next_month=next_month,
                           month_name=calendar.month_name[month],
                           holiday_dates=holiday_dates,
                           departments=departments,
                           selected_department=department)