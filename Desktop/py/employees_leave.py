import datetime
from flask import Blueprint, render_template, request, redirect, url_for, flash
from marshmallow import ValidationError
from .database import get_db
from .employee_helpers import get_name_column, get_search_columns, get_dynamic_fields
from .vacation_balance import invalidate_balance_cache
from .vacation_holidays import calculate_leave_days, is_public_holiday, parse_date_robust
from .vacation_conversion import convert_all_hourly_to_days
from .validation import LeaveSchema, LatenessSchema
from .employees_core import employees_bp
from .utils import get_default_vacation_type_id

@employees_bp.route('/employees/<int:id>/leave', methods=['GET', 'POST'])
def leave(id):
    db = get_db()
    vac_types = db.execute('SELECT * FROM vacation_types').fetchall()
    if request.method == 'POST':
        try:
            vt_id = request.form.get('vacation_type_id', type=int)
            start = parse_date_robust(request.form['start_date'])
            end = parse_date_robust(request.form.get('end_date', request.form['start_date']))
            days = request.form.get('days_taken', type=float)
            notes = request.form.get('notes', '')
            if not days:
                flash('يرجى إدخال عدد الأيام', 'error')
                return redirect(url_for('employees.leave', id=id))
            db.execute('INSERT INTO leave_log (employee_id, vacation_type_id, start_date, end_date, days_taken, notes) VALUES (?,?,?,?,?,?)',
                       (id, vt_id, start, end, days, notes))
            last_updated = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            db.execute('UPDATE employees SET last_updated=? WHERE id=?', (last_updated, id))
            db.commit()
            year = datetime.datetime.strptime(start, '%Y-%m-%d').year
            invalidate_balance_cache(id, vt_id, year)
            flash('تم تسجيل الإجازة', 'success')
            return redirect(url_for('employees.detail', id=id))
        except Exception as e:
            flash(f'فشل تسجيل الإجازة: {e}', 'error')
    return render_template('leave_form.html', employee_id=id, vac_types=vac_types)

@employees_bp.route('/employees/<int:id>/leave/<int:log_id>/delete', methods=['POST'])
def leave_delete(id, log_id):
    db = get_db()
    try:
        leave = db.execute('SELECT start_date, vacation_type_id FROM leave_log WHERE id=?', (log_id,)).fetchone()
        if not leave:
            flash('الإجازة غير موجودة', 'error')
            return redirect(url_for('employees.detail', id=id))
        vt_id = leave['vacation_type_id']
        start_date = leave['start_date']
        year = datetime.datetime.strptime(start_date, '%Y-%m-%d').year
        db.execute('DELETE FROM leave_log WHERE id=? AND employee_id=?', (log_id, id))
        last_updated = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        db.execute('UPDATE employees SET last_updated=? WHERE id=?', (last_updated, id))
        db.commit()
        invalidate_balance_cache(id, vt_id, year)
        flash('تم حذف سجل الإجازة', 'success')
    except Exception as e:
        flash(f'فشل حذف الإجازة: {e}', 'error')
    return redirect(url_for('employees.detail', id=id))

@employees_bp.route('/leave/<int:leave_id>/edit', methods=['POST'])
def edit_leave(leave_id):
    db = get_db()
    leave = db.execute('SELECT employee_id, vacation_type_id, start_date FROM leave_log WHERE id=?', (leave_id,)).fetchone()
    if not leave:
        flash('الإجازة غير موجودة', 'error')
        return redirect(request.referrer or url_for('employees.list'))

    start = parse_date_robust(request.form['start_date'])
    end = parse_date_robust(request.form['end_date'])
    days = request.form.get('days_taken', type=float)
    notes = request.form.get('notes', '')

    if not days:
        flash('يرجى إدخال عدد الأيام', 'error')
        return redirect(request.referrer or url_for('employees.detail', id=leave['employee_id']))

    db.execute('UPDATE leave_log SET start_date=?, end_date=?, days_taken=?, notes=? WHERE id=?',
               (start, end, days, notes, leave_id))
    db.commit()
    year = datetime.datetime.strptime(start, '%Y-%m-%d').year
    invalidate_balance_cache(leave['employee_id'], leave['vacation_type_id'], year)
    flash('تم تحديث الإجازة بنجاح', 'success')
    return redirect(url_for('employees.detail', id=leave['employee_id']))

@employees_bp.route('/vacation/register', methods=['GET', 'POST'])
def vacation_register():
    db = get_db()
    vac_types = db.execute('SELECT id, name, hours_per_day FROM vacation_types').fetchall()
    if request.method == 'POST':
        try:
            employee_id = request.form.get('employee_id', type=int)
            vt_id = request.form.get('vacation_type_id', type=int)
            start_raw = request.form['start_date']
            end_raw = request.form.get('end_date', start_raw)
            hours = request.form.get('hours', type=float)
            days = request.form.get('days_taken', type=float)
            notes = request.form.get('notes', '')

            if not employee_id:
                flash('الرجاء اختيار الموظف', 'error')
                return redirect(url_for('employees.vacation_register'))

            try:
                start = parse_date_robust(start_raw)
                end = parse_date_robust(end_raw)
            except ValueError as e:
                flash(f'تاريخ غير صالح: {e}', 'error')
                return redirect(url_for('employees.vacation_register'))

            if not vt_id:
                vt_id = get_default_vacation_type_id(db)
                if not vt_id:
                    flash('لا يوجد نوع إجازة افتراضي (إداري، بلا أجر، غير مبرر)', 'error')
                    return redirect(url_for('employees.vacation_register'))

            vt = db.execute('SELECT * FROM vacation_types WHERE id=?', (vt_id,)).fetchone()
            if not vt:
                flash('نوع الإجازة غير موجود', 'error')
                return redirect(url_for('employees.vacation_register'))

            vt_name = vt['name']
            year = datetime.datetime.strptime(start, '%Y-%m-%d').year

            # Handle lateness/hourly
            if vt_name.startswith('تأخير') or vt_name.startswith('إجازة ساعية'):
                if not hours:
                    flash('يجب إدخال عدد الساعات', 'error')
                    return redirect(url_for('employees.vacation_register'))
                schema = LatenessSchema()
                data = {'employee_id': employee_id, 'date': start, 'hours': hours, 'notes': notes, 'vacation_type_id': vt_id}
                try:
                    schema.load(data)
                except ValidationError as e:
                    flash(f'بيانات غير صحيحة: {e.messages}', 'error')
                    return redirect(url_for('employees.vacation_register'))
                existing = db.execute('SELECT 1 FROM lateness_log WHERE employee_id=? AND date=? AND vacation_type_id=?',
                                      (employee_id, start, vt_id)).fetchone()
                if existing:
                    flash('لا يمكن تسجيل أكثر من تأخير أو إجازة ساعية لنفس الموظف في نفس اليوم', 'error')
                    return redirect(url_for('employees.vacation_register'))
                db.execute('INSERT INTO lateness_log (employee_id, date, hours, notes, vacation_type_id) VALUES (?,?,?,?,?)',
                           (employee_id, start, hours, notes, vt_id))
                last_updated = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                db.execute('UPDATE employees SET last_updated=? WHERE id=?', (last_updated, employee_id))
                db.commit()
                invalidate_balance_cache(employee_id, vt_id, year)
                msg = convert_all_hourly_to_days(employee_id)
                flash(f'تم تسجيل الساعات. {msg}', 'success')
            else:
                # For ALL other leaves, user must enter days manually
                if not days:
                    flash(f'يرجى إدخال عدد الأيام لنوع الإجازة "{vt_name}".', 'error')
                    return redirect(url_for('employees.vacation_register'))
                if days <= 0:
                    flash('عدد الأيام يجب أن يكون أكبر من صفر', 'error')
                    return redirect(url_for('employees.vacation_register'))

                if is_public_holiday(start):
                    flash('تنبيه: هذا اليوم هو عطلة رسمية. هل أنت متأكد؟', 'warning')

                # No balance check – insert directly
                schema = LeaveSchema()
                data = {'employee_id': employee_id, 'vacation_type_id': vt_id, 'start_date': start, 'end_date': end, 'days_taken': days, 'notes': notes}
                try:
                    schema.load(data)
                except ValidationError as e:
                    flash(f'بيانات غير صحيحة: {e.messages}', 'error')
                    return redirect(url_for('employees.vacation_register'))

                db.execute('INSERT INTO leave_log (employee_id, vacation_type_id, start_date, end_date, days_taken, notes) VALUES (?,?,?,?,?,?)',
                           (employee_id, vt_id, start, end, round(days, 2), notes))
                last_updated = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                db.execute('UPDATE employees SET last_updated=? WHERE id=?', (last_updated, employee_id))
                db.commit()
                invalidate_balance_cache(employee_id, vt_id, year)
                flash('تم تسجيل الإجازة بنجاح', 'success')

            return redirect(url_for('employees.vacation_register'))
        except Exception as e:
            flash(f'فشل تسجيل الإجازة: {e}', 'error')
    today = datetime.date.today()
    return render_template('vacation_register.html', vac_types=vac_types, today=today)