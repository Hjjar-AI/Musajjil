# py/routes_bulk_leave.py
import datetime
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db
from .vacation_holidays import calculate_leave_days, is_public_holiday
from .vacation_balance import invalidate_balance_cache

bulk_leave_bp = Blueprint('bulk_leave', __name__, url_prefix='/bulk-leave')

@bulk_leave_bp.route('/', methods=['GET', 'POST'])
def register():
    db = get_db()
    vac_types = db.execute('SELECT id, name, hours_per_day FROM vacation_types WHERE name NOT IN ("تأخير (3 ساعات)", "إجازة ساعية")').fetchall()
    tags = db.execute('SELECT * FROM tags').fetchall()
    classifications = db.execute('SELECT * FROM classifications').fetchall()

    if request.method == 'POST':
        tag_ids = request.form.getlist('tags', type=int)
        classification_id = request.form.get('classification', type=int)
        vt_id = request.form.get('vacation_type_id', type=int)
        start = request.form['start_date']
        end = request.form.get('end_date', start)
        days = request.form.get('days_taken', type=float)
        notes = request.form.get('notes', '')

        if not vt_id or not start:
            flash('يرجى اختيار نوع الإجازة والتاريخ', 'error')
            return redirect(url_for('bulk_leave.register'))

        # No lock check

        query = 'SELECT id FROM employees WHERE 1=1'
        params = []
        if tag_ids:
            placeholders = ','.join('?'*len(tag_ids))
            query += f' AND id IN (SELECT employee_id FROM employee_tags WHERE tag_id IN ({placeholders}) GROUP BY employee_id HAVING COUNT(DISTINCT tag_id)=?)'
            params.extend(tag_ids)
            params.append(len(tag_ids))
        if classification_id:
            query += ' AND classification_id = ?'
            params.append(classification_id)

        employees = db.execute(query, params).fetchall()

        if not days:
            days = calculate_leave_days(start, end, vt_id)

        vt = db.execute('SELECT * FROM vacation_types WHERE id=?', (vt_id,)).fetchone()
        vt_name = vt['name'] if vt else ''
        if any(x in vt_name for x in ['أمومة', 'مرضية']) and days != int(days):
            flash('لا يمكن تسجيل إجازة أمومة أو مرضية بكسور (يوم كامل فقط)', 'error')
            return redirect(url_for('bulk_leave.register'))
        if not vt_name.startswith('تأخير') and not vt_name.startswith('إجازة ساعية') and is_public_holiday(start):
            flash('لا يمكن تسجيل إجازة في يوم عطلة رسمية', 'error')
            return redirect(url_for('bulk_leave.register'))

        count = 0
        errors = []
        last_updated = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        year = datetime.datetime.strptime(start, '%Y-%m-%d').year

        for emp in employees:
            try:
                db.execute('INSERT INTO leave_log (employee_id, vacation_type_id, start_date, end_date, days_taken, notes) VALUES (?,?,?,?,?,?)',
                           (emp['id'], vt_id, start, end, days, notes))
                db.execute('UPDATE employees SET last_updated=? WHERE id=?', (last_updated, emp['id']))
                invalidate_balance_cache(emp['id'], vt_id, year)
                count += 1
            except Exception as e:
                errors.append(f'موظف ID {emp["id"]}: {str(e)}')
        db.commit()

        if errors:
            flash(f'تم تسجيل الإجازة لـ {count} موظف، ولكن حدثت أخطاء: {"; ".join(errors[:3])}', 'warning')
        else:
            flash(f'تم تسجيل الإجازة لـ {count} موظف', 'success')
        return redirect(url_for('bulk_leave.register'))

    return render_template('bulk_leave.html', vac_types=vac_types, tags=tags, classifications=classifications)