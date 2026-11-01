import datetime
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db
from .employee_helpers import get_classification_id, compute_target_days_from_years_months
from .vacation_balance import invalidate_balance_cache, set_entitlement_for_year, get_entitlement_for_year
from .employees_core import employees_bp
from .utils import parse_date_robust

@employees_bp.route('/employees/<int:id>/entitlements', methods=['GET', 'POST'])
def entitlements(id):
    db = get_db()
    vac_types = db.execute('SELECT * FROM vacation_types').fetchall()
    current_year = datetime.date.today().year

    if request.method == 'POST':
        try:
            for vt in vac_types:
                days = request.form.get(f'entitled_{vt["id"]}', type=float)
                if days is not None:
                    set_entitlement_for_year(id, vt['id'], current_year, days)
            db.commit()
            invalidate_balance_cache(id)
            flash('تم تحديث الاستحقاقات', 'success')
            return redirect(url_for('employees.detail', id=id))
        except Exception as e:
            flash(f'فشل تحديث الاستحقاقات: {e}', 'error')

    current = {}
    for vt in vac_types:
        entitled = get_entitlement_for_year(id, vt['id'], current_year, default_if_missing=False)
        if entitled is not None:
            current[vt['id']] = entitled

    cid = get_classification_id(id)
    defaults = {}
    if cid:
        for vt in vac_types:
            ent = db.execute('SELECT default_days FROM classification_entitlements WHERE classification_id=? AND vacation_type_id=?', (cid, vt['id'])).fetchone()
            defaults[vt['id']] = ent['default_days'] if ent else 0

    return render_template('entitlements_form.html', employee_id=id, vac_types=vac_types,
                           current=current, defaults=defaults)

@employees_bp.route('/employees/<int:id>/service/period/add', methods=['POST'])
def add_service_period(id):
    db = get_db()
    try:
        start_raw = request.form['start_date']
        end_raw = request.form.get('end_date', '')
        start = parse_date_robust(start_raw)
        if not start:
            flash('تاريخ البداية غير صالح', 'error')
            return redirect(url_for('employees.detail', id=id))
        if not end_raw:
            end = datetime.date.today().strftime('%Y-%m-%d')
        else:
            end = parse_date_robust(end_raw)
            if not end:
                flash('تاريخ النهاية غير صالح', 'error')
                return redirect(url_for('employees.detail', id=id))
        label = request.form.get('label', '')
        db.execute('INSERT INTO employee_work_periods (employee_id, start_date, end_date, label) VALUES (?,?,?,?)',
                   (id, start, end, label))
        db.commit()
        flash('تم إضافة فترة خدمة', 'success')
    except Exception as e:
        flash(f'فشل إضافة الفترة: {e}', 'error')
    return redirect(url_for('employees.detail', id=id))

@employees_bp.route('/employees/<int:id>/service/period/<int:period_id>/delete', methods=['POST'])
def delete_service_period(id, period_id):
    db = get_db()
    try:
        db.execute('DELETE FROM employee_work_periods WHERE id=? AND employee_id=?', (period_id, id))
        db.commit()
        flash('تم حذف الفترة', 'success')
    except Exception as e:
        flash(f'فشل حذف الفترة: {e}', 'error')
    return redirect(url_for('employees.detail', id=id))

@employees_bp.route('/employees/<int:id>/service/target', methods=['POST'])
def update_target_service(id):
    db = get_db()
    try:
        years = request.form.get('target_years', 0, type=int)
        months = request.form.get('target_months', 0, type=int)
        target_days = compute_target_days_from_years_months(id, years, months)
        db.execute('UPDATE employees SET target_service_days=? WHERE id=?', (target_days, id))
        db.commit()
        flash('تم تحديث المدة المستهدفة', 'success')
    except Exception as e:
        flash(f'فشل التحديث: {e}', 'error')
    return redirect(url_for('employees.detail', id=id))

@employees_bp.route('/employees/<int:id>/update_uncompensated_days', methods=['POST'])
def update_uncompensated_days(id):
    db = get_db()
    try:
        days = request.form.get('uncompensated_days', 0, type=float)
        db.execute('UPDATE employees SET uncompensated_days=? WHERE id=?', (days, id))
        db.commit()
        flash('تم تحديث أيام الغياب غير المعوَّضة', 'success')
    except Exception as e:
        flash(f'فشل التحديث: {e}', 'error')
    return redirect(url_for('employees.detail', id=id))