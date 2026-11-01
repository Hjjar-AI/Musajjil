# py/employees_bulk.py
import datetime
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db
from .employee_helpers import get_dynamic_fields
from .vacation_holidays import calculate_leave_days
from .vacation_balance import invalidate_balance_cache
from .employees_core import employees_bp

@employees_bp.route('/bulk-edit', methods=['GET'])
def bulk_edit_form():
    employee_ids = request.args.getlist('ids', type=int)
    if not employee_ids:
        flash('الرجاء اختيار موظفين أولاً', 'error')
        return redirect(url_for('employees.list'))
    db = get_db()
    fields = get_dynamic_fields()
    tags = db.execute('SELECT * FROM tags').fetchall()
    classifications = db.execute('SELECT * FROM classifications').fetchall()
    departments = db.execute('SELECT name FROM departments ORDER BY name').fetchall()
    return render_template('bulk_edit.html', employee_ids=employee_ids, fields=fields,
                           tags=tags, classifications=classifications, departments=departments)

@employees_bp.route('/bulk-edit/apply', methods=['POST'])
def bulk_edit_apply():
    employee_ids = request.form.getlist('employee_ids', type=int)
    if not employee_ids:
        flash('لا توجد موظفين محددين', 'error')
        return redirect(url_for('employees.list'))

    db = get_db()
    field_name = request.form.get('field_name')
    field_value = request.form.get('field_value')

    if not field_name or not field_value:
        flash('يرجى اختيار الحقل والقيمة', 'error')
        return redirect(url_for('employees.bulk_edit_form', ids=employee_ids))

    try:
        placeholders = ','.join('?' * len(employee_ids))
        if field_name == 'tags':
            tag_id = int(field_value)
            action = request.form.get('tag_action', 'add')
            if action == 'add':
                for emp_id in employee_ids:
                    db.execute('INSERT OR IGNORE INTO employee_tags (employee_id, tag_id) VALUES (?,?)', (emp_id, tag_id))
            else:
                for emp_id in employee_ids:
                    db.execute('DELETE FROM employee_tags WHERE employee_id=? AND tag_id=?', (emp_id, tag_id))
        elif field_name == 'department':
            dept_col = None
            for f in get_dynamic_fields():
                if f['original_title'] == 'القسم':
                    dept_col = f['column_name']
                    break
            if dept_col:
                db.execute(f'UPDATE employees SET {dept_col}=? WHERE id IN ({placeholders})', [field_value] + employee_ids)
        elif field_name == 'classification':
            classification_id = int(field_value)
            db.execute(f'UPDATE employees SET classification_id=? WHERE id IN ({placeholders})', [classification_id] + employee_ids)
        else:
            flash('حقل غير صالح للتعديل الجماعي', 'error')
            return redirect(url_for('employees.list'))

        db.commit()
        flash(f'تم تحديث {len(employee_ids)} موظف بنجاح', 'success')
    except Exception as e:
        flash(f'خطأ في التحديث الجماعي: {e}', 'error')
    return redirect(url_for('employees.list'))

@employees_bp.route('/bulk-leave-edit', methods=['GET'])
def bulk_leave_edit_form():
    leave_ids = request.args.getlist('ids', type=int)
    if not leave_ids:
        flash('الرجاء اختيار إجازات أولاً', 'error')
        return redirect(url_for('employees.list'))
    db = get_db()
    first = db.execute('SELECT * FROM leave_log WHERE id=?', (leave_ids[0],)).fetchone()
    vac_types = db.execute('SELECT id, name FROM vacation_types').fetchall()
    return render_template('bulk_leave_edit.html', leave_ids=leave_ids, vac_types=vac_types, first=first)

@employees_bp.route('/bulk-leave-edit/apply', methods=['POST'])
def bulk_leave_edit_apply():
    leave_ids = request.form.getlist('leave_ids', type=int)
    if not leave_ids:
        flash('لا توجد إجازات محددة', 'error')
        return redirect(url_for('employees.list'))

    new_start = request.form.get('start_date')
    new_end = request.form.get('end_date')
    new_type_id = request.form.get('vacation_type_id', type=int)
    new_notes = request.form.get('notes', '')

    db = get_db()
    success = 0
    errors = 0

    for lid in leave_ids:
        leave = db.execute('SELECT start_date, employee_id, vacation_type_id FROM leave_log WHERE id=?', (lid,)).fetchone()
        if not leave:
            errors += 1
            continue
        if new_start and new_end:
            days = calculate_leave_days(new_start, new_end, new_type_id if new_type_id else leave['vacation_type_id'])
            db.execute('UPDATE leave_log SET start_date=?, end_date=?, days_taken=?, vacation_type_id=?, notes=? WHERE id=?',
                       (new_start, new_end, days, new_type_id or leave['vacation_type_id'], new_notes, lid))
            year = datetime.datetime.strptime(new_start, '%Y-%m-%d').year
        else:
            db.execute('UPDATE leave_log SET vacation_type_id=?, notes=? WHERE id=?',
                       (new_type_id or leave['vacation_type_id'], new_notes, lid))
            year = datetime.datetime.strptime(leave['start_date'], '%Y-%m-%d').year
        invalidate_balance_cache(leave['employee_id'], new_type_id or leave['vacation_type_id'], year)
        success += 1

    db.commit()
    flash(f'تم تحديث {success} إجازة بنجاح، {errors} فشل.', 'success')
    return redirect(url_for('employees.list'))