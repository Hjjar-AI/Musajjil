import datetime
from flask import render_template, request, flash, redirect, url_for
from .database import get_db
from .employee_helpers import (
    get_dynamic_fields, get_classification_id, compute_target_days_from_years_months,
    breakdown_days_to_ym
)
from .vacation_balance import invalidate_balance_cache
from .employees_core import employees_bp
from .utils import parse_date_robust

@employees_bp.route('/employees/add', methods=['GET', 'POST'])
def add():
    db = get_db()
    fields = get_dynamic_fields()
    all_tags = db.execute('SELECT * FROM tags').fetchall()
    class_col = None
    for f in fields:
        if f['original_title'].strip().lower() in ('تصنيف', 'classification'):
            class_col = f['column_name']
            break
    if request.method == 'POST':
        try:
            columns = []
            values = []
            for f in fields:
                if f['original_title'] == 'تاريخ آخر تحديث':
                    continue
                val = request.form.get(f['column_name'], '')
                columns.append(f['column_name'])
                values.append(val)
            classification_id = None
            if class_col:
                class_name = request.form.get(class_col, '')
                if class_name:
                    c = db.execute('SELECT id FROM classifications WHERE name=?', (class_name,)).fetchone()
                    if c:
                        classification_id = c['id']
            extra_data = request.form.get('extra_data', '{}')
            target_years = request.form.get('target_years', 0, type=int)
            target_months = request.form.get('target_months', 0, type=int)
            target_service = round(target_years * 365.25 + target_months * 30.4375) if (target_years or target_months) else 0
            contract_end_raw = request.form.get('contract_end_date', '') or None
            contract_end = parse_date_robust(contract_end_raw) if contract_end_raw else None
            uncompensated_days = request.form.get('uncompensated_days', 0, type=float)
            last_updated = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            columns += ['classification_id', 'extra_data', 'target_service_days', 'contract_end_date', 'last_updated', 'uncompensated_days']
            values += [classification_id, extra_data, target_service, contract_end, last_updated, uncompensated_days]
            cur = db.execute(f'INSERT INTO employees ({",".join(columns)}) VALUES ({",".join("?"*len(columns))})', values)
            emp_id = cur.lastrowid
            selected_tags = request.form.getlist('tags', type=int)
            for tag_id in selected_tags:
                db.execute('INSERT INTO employee_tags (employee_id, tag_id) VALUES (?,?)', (emp_id, tag_id))
            db.commit()
            flash('تم إضافة الموظف بنجاح', 'success')
            return redirect(url_for('employees.list'))
        except Exception as e:
            flash(f'حدث خطأ أثناء الإضافة: {e}', 'error')
    classifications = db.execute('SELECT * FROM classifications').fetchall() if class_col else []
    departments = db.execute('SELECT name FROM departments ORDER BY name').fetchall()
    target_years_val = 0
    target_months_val = 0
    uncompensated_days = 0
    return render_template('employee_form.html', fields=fields, employee=None, all_tags=all_tags,
                           classifications=classifications, class_col=class_col, current_tags=[],
                           target_years_val=target_years_val, target_months_val=target_months_val,
                           departments_list=departments, uncompensated_days=uncompensated_days)

@employees_bp.route('/employees/<int:id>/edit', methods=['GET', 'POST'])
def edit(id):
    db = get_db()
    fields = get_dynamic_fields()
    emp = db.execute('SELECT * FROM employees WHERE id=?', (id,)).fetchone()
    if not emp:
        flash('الموظف غير موجود', 'error')
        return redirect(url_for('employees.list'))
    all_tags = db.execute('SELECT * FROM tags').fetchall()
    current_tags = [row['tag_id'] for row in db.execute('SELECT tag_id FROM employee_tags WHERE employee_id=?', (id,)).fetchall()]
    class_col = None
    for f in fields:
        if f['original_title'].strip().lower() in ('تصنيف', 'classification'):
            class_col = f['column_name']
            break
    departments = db.execute('SELECT name FROM departments ORDER BY name').fetchall()
    if request.method == 'POST':
        try:
            for f in fields:
                if f['original_title'] == 'تاريخ آخر تحديث':
                    continue
                val = request.form.get(f['column_name'], '')
                db.execute(f'UPDATE employees SET {f["column_name"]}=? WHERE id=?', (val, id))
            classification_id = None
            if class_col:
                class_name = request.form.get(class_col, '')
                if class_name:
                    c = db.execute('SELECT id FROM classifications WHERE name=?', (class_name,)).fetchone()
                    if c:
                        classification_id = c['id']
            extra_data = request.form.get('extra_data', '{}')
            target_years = request.form.get('target_years', 0, type=int)
            target_months = request.form.get('target_months', 0, type=int)
            target_service = compute_target_days_from_years_months(id, target_years, target_months)
            contract_end_raw = request.form.get('contract_end_date', '') or None
            contract_end = parse_date_robust(contract_end_raw) if contract_end_raw else None
            uncompensated_days = request.form.get('uncompensated_days', 0, type=float)
            last_updated = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            db.execute('UPDATE employees SET classification_id=?, extra_data=?, target_service_days=?, contract_end_date=?, last_updated=?, uncompensated_days=? WHERE id=?',
                       (classification_id, extra_data, target_service, contract_end, last_updated, uncompensated_days, id))
            db.execute('DELETE FROM employee_tags WHERE employee_id=?', (id,))
            selected_tags = request.form.getlist('tags', type=int)
            for tag_id in selected_tags:
                db.execute('INSERT INTO employee_tags (employee_id, tag_id) VALUES (?,?)', (id, tag_id))
            db.commit()
            flash('تم تحديث بيانات الموظف', 'success')
            return redirect(url_for('employees.detail', id=id))
        except Exception as e:
            flash(f'حدث خطأ أثناء التحديث: {e}', 'error')
    classifications = db.execute('SELECT * FROM classifications').fetchall() if class_col else []
    target_years_val, target_months_val = breakdown_days_to_ym(emp['target_service_days'] or 0)
    uncompensated_days = emp['uncompensated_days'] or 0
    return render_template('employee_form.html', fields=fields, employee=emp, all_tags=all_tags,
                           current_tags=current_tags, classifications=classifications, class_col=class_col,
                           target_years_val=target_years_val, target_months_val=target_months_val,
                           departments_list=departments, uncompensated_days=uncompensated_days)

@employees_bp.route('/employees/<int:id>/delete', methods=['POST'])
def delete(id):
    db = get_db()
    try:
        db.execute('UPDATE employees SET deleted_at = CURRENT_TIMESTAMP WHERE id=?', (id,))
        db.commit()
        invalidate_balance_cache()
        flash('تم حذف الموظف (حذف مؤقت - يمكن استعادته)', 'success')
    except Exception as e:
        flash(f'فشل حذف الموظف: {e}', 'error')
    return redirect(url_for('employees.list'))