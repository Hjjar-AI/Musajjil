import datetime
from flask import Blueprint, render_template, request, redirect, url_for, flash, Response
from .database import get_db
from .utils import parse_date_robust

holidays_bp = Blueprint('holidays', __name__, url_prefix='/holidays')

@holidays_bp.route('/')
def list_holidays():
    db = get_db()
    fixed = db.execute('SELECT * FROM public_holidays WHERE is_fixed=1 ORDER BY date').fetchall()
    variable = db.execute('SELECT * FROM public_holidays WHERE is_fixed=0 ORDER BY date DESC').fetchall()
    return render_template('holidays.html', fixed_holidays=fixed, variable_holidays=variable)

@holidays_bp.route('/add', methods=['POST'])
def add():
    name = request.form['name'].strip()
    is_fixed = request.form.get('is_fixed', '0')
    if is_fixed == '1':
        month = request.form['month']
        day = request.form['day']
        date_str = f"2000-{int(month):02d}-{int(day):02d}"
    else:
        date_str = parse_date_robust(request.form['date'])
        if not date_str:
            flash('التاريخ غير صالح', 'error')
            return redirect(url_for('holidays.list_holidays'))
    if name and date_str:
        db = get_db()
        try:
            db.execute('INSERT INTO public_holidays (name, date, is_fixed) VALUES (?,?,?)',
                       (name, date_str, int(is_fixed)))
            db.commit()
            flash('تمت إضافة العطلة', 'success')
        except:
            flash('العطلة موجودة مسبقاً', 'error')
    return redirect(url_for('holidays.list_holidays'))

@holidays_bp.route('/<int:id>/delete', methods=['POST'])
def delete(id):
    db = get_db()
    db.execute('DELETE FROM public_holidays WHERE id=?', (id,))
    db.commit()
    flash('تم حذف العطلة', 'success')
    return redirect(url_for('holidays.list_holidays'))

@holidays_bp.route('/ical')
def export_ical():
    db = get_db()
    year = request.args.get('year', datetime.date.today().year, type=int)
    holidays = db.execute('SELECT * FROM public_holidays').fetchall()
    cal_lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Employee System//Holidays//AR', 'CALSCALE:GREGORIAN', 'METHOD:PUBLISH']
    for h in holidays:
        if h['is_fixed']:
            fixed_date = datetime.datetime.strptime(h['date'], '%Y-%m-%d')
            try:
                date_obj = datetime.date(year, fixed_date.month, fixed_date.day)
            except ValueError:
                continue
        else:
            date_obj = datetime.datetime.strptime(h['date'], '%Y-%m-%d').date()
            if date_obj.year != year:
                continue
        dtstamp = datetime.datetime.now().strftime('%Y%m%dT%H%M%S')
        dtstart = date_obj.strftime('%Y%m%d')
        cal_lines.append('BEGIN:VEVENT')
        cal_lines.append(f'UID:holiday_{h["id"]}_{year}@employeesystem')
        cal_lines.append(f'DTSTAMP:{dtstamp}')
        cal_lines.append(f'DTSTART;VALUE=DATE:{dtstart}')
        cal_lines.append(f'DTEND;VALUE=DATE:{dtstart}')
        cal_lines.append(f'SUMMARY:{h["name"]}')
        cal_lines.append('TRANSP:OPAQUE')
        cal_lines.append('END:VEVENT')
    cal_lines.append('END:VCALENDAR')
    response = '\r\n'.join(cal_lines)
    return Response(response, mimetype='text/calendar', headers={
        'Content-Disposition': f'attachment; filename=holidays_{year}.ics'
    })