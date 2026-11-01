import datetime
from flask import Blueprint, render_template, request, jsonify
from .database import get_db
from .employee_helpers import get_name_column
from .vacation_holidays import get_all_holiday_dates

calendar_bp = Blueprint('calendar', __name__, url_prefix='/calendar')

@calendar_bp.route('/')
def show():
    db = get_db()
    year = request.args.get('year', datetime.date.today().year, type=int)
    month = request.args.get('month', datetime.date.today().month, type=int)
    employee_id = request.args.get('employee_id', type=int)
    department = request.args.get('department', '').strip()

    start_date = datetime.date(year, month, 1)
    if month == 12:
        end_date = datetime.date(year + 1, 1, 1) - datetime.timedelta(days=1)
    else:
        end_date = datetime.date(year, month + 1, 1) - datetime.timedelta(days=1)

    query = '''
        SELECT ll.id, ll.start_date, ll.end_date, e.id AS emp_id, vt.name AS type_name
        FROM leave_log ll
        JOIN employees e ON ll.employee_id = e.id
        JOIN vacation_types vt ON ll.vacation_type_id = vt.id
        WHERE ll.start_date <= ? AND ll.end_date >= ?
    '''
    params = [end_date.strftime('%Y-%m-%d'), start_date.strftime('%Y-%m-%d')]

    if employee_id:
        query += ' AND e.id = ?'
        params.append(employee_id)
    if department:
        for f in db.execute('SELECT original_title, column_name FROM employee_fields WHERE original_title="القسم"').fetchall():
            dept_col = f['column_name']
            query += f' AND e.{dept_col} = ?'
            params.append(department)
            break

    leaves = db.execute(query + ' ORDER BY ll.start_date', params).fetchall()

    from collections import defaultdict
    events = defaultdict(list)
    name_col = get_name_column()
    if name_col:
        emp_names = {row['id']: row[name_col] for row in db.execute(f'SELECT id, {name_col} FROM employees').fetchall()}
    else:
        emp_names = {}

    for leave in leaves:
        start = datetime.datetime.strptime(leave['start_date'], '%Y-%m-%d').date()
        end = datetime.datetime.strptime(leave['end_date'], '%Y-%m-%d').date()
        d = start
        while d <= end:
            if d.month == month and d.year == year:
                name = emp_names.get(leave['emp_id'], str(leave['emp_id']))
                events[d.day].append({
                    'id': leave['id'],
                    'text': f"{name} ({leave['type_name']})",
                    'start': leave['start_date'],
                    'end': leave['end_date']
                })
            d += datetime.timedelta(days=1)

    import calendar
    cal = calendar.monthcalendar(year, month)
    # Shift to start week on Saturday (index 5)
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

    employees = db.execute(f'SELECT id, {name_col} AS name FROM employees') if name_col else []
    departments = []
    dept_col = None
    for f in db.execute('SELECT original_title, column_name FROM employee_fields WHERE original_title="القسم"').fetchall():
        dept_col = f['column_name']
    if dept_col:
        departments = [r[0] for r in db.execute(f'SELECT DISTINCT {dept_col} FROM employees WHERE {dept_col} IS NOT NULL AND {dept_col} != ""').fetchall()]

    return render_template('calendar.html',
                           year=year, month=month, cal=shifted_weeks,
                           events=events,
                           prev_year=prev_year, prev_month=prev_month,
                           next_year=next_year, next_month=next_month,
                           month_name=calendar.month_name[month],
                           holiday_dates=holiday_dates,
                           employees=employees, departments=departments,
                           selected_employee=employee_id, selected_department=department)

@calendar_bp.route('/update_leave_date', methods=['POST'])
def update_leave_date():
    data = request.get_json()
    leave_id = data.get('id')
    new_start = data.get('start')
    new_end = data.get('end')
    if not leave_id or not new_start or not new_end:
        return jsonify({'success': False, 'error': 'بيانات ناقصة'})
    db = get_db()
    db.execute('UPDATE leave_log SET start_date=?, end_date=? WHERE id=?', (new_start, new_end, leave_id))
    db.commit()
    return jsonify({'success': True})

@calendar_bp.route('/print')
def print_view():
    db = get_db()
    year = request.args.get('year', datetime.date.today().year, type=int)
    month = request.args.get('month', datetime.date.today().month, type=int)

    start_date = datetime.date(year, month, 1)
    if month == 12:
        end_date = datetime.date(year + 1, 1, 1) - datetime.timedelta(days=1)
    else:
        end_date = datetime.date(year, month + 1, 1) - datetime.timedelta(days=1)

    leaves = db.execute('''
        SELECT ll.start_date, ll.end_date, e.id AS emp_id, vt.name AS type_name
        FROM leave_log ll
        JOIN employees e ON ll.employee_id = e.id
        JOIN vacation_types vt ON ll.vacation_type_id = vt.id
        WHERE ll.start_date <= ? AND ll.end_date >= ?
        ORDER BY ll.start_date
    ''', (end_date.strftime('%Y-%m-%d'), start_date.strftime('%Y-%m-%d'))).fetchall()

    from collections import defaultdict
    events = defaultdict(list)
    name_col = get_name_column()
    if name_col:
        emp_names = {row['id']: row[name_col] for row in db.execute(f'SELECT id, {name_col} FROM employees').fetchall()}
    else:
        emp_names = {}
    for leave in leaves:
        start = datetime.datetime.strptime(leave['start_date'], '%Y-%m-%d').date()
        end = datetime.datetime.strptime(leave['end_date'], '%Y-%m-%d').date()
        d = start
        while d <= end:
            if d.month == month and d.year == year:
                name = emp_names.get(leave['emp_id'], str(leave['emp_id']))
                events[d.day].append(f"{name} ({leave['type_name']})")
            d += datetime.timedelta(days=1)

    import calendar
    cal = calendar.monthcalendar(year, month)
    shifted_weeks = []
    for week in cal:
        shifted_weeks.append([week[5], week[6], week[0], week[1], week[2], week[3], week[4]])

    holiday_dates = set()
    for h in get_all_holiday_dates():
        try:
            d = datetime.datetime.strptime(h, '%Y-%m-%d').date()
            if d.year == year and d.month == month:
                holiday_dates.add(d.day)
        except:
            pass

    return render_template('calendar_print.html',
                           year=year, month=month,
                           cal=shifted_weeks,
                           events=events,
                           month_name=calendar.month_name[month],
                           holiday_dates=holiday_dates)