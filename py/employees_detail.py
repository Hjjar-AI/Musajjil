import datetime
from flask import render_template, flash, redirect, url_for, request
from .database import get_db
from .employee_helpers import (
    get_dynamic_fields, get_classification_id, compute_service_days,
    get_employee_tags, get_name_column, get_search_columns,
    compute_target_days_from_years_months, breakdown_days_to_ym, breakdown_days_to_years_days
)
from .vacation_holidays import parse_date_robust
from .employees_core import employees_bp

@employees_bp.route('/employees/<int:id>')
def detail(id):
    db = get_db()
    fields = get_dynamic_fields()
    emp = db.execute('SELECT * FROM employees WHERE id=? AND deleted_at IS NULL', (id,)).fetchone()
    if not emp:
        flash('الموظف غير موجود', 'error')
        return redirect(url_for('employees.list'))
    emp_data = {f['original_title']: (emp[f['column_name']] or '') for f in fields}
    emp_data['id'] = emp['id']
    classification_name = ''
    if emp['classification_id']:
        cls = db.execute('SELECT name FROM classifications WHERE id=?', (emp['classification_id'],)).fetchone()
        if cls:
            classification_name = cls['name']
    vac_types = db.execute('SELECT * FROM vacation_types').fetchall()
    current_year = datetime.date.today().year

    # Selected year for yearly totals
    selected_year = request.args.get('year', current_year, type=int)

    # Balances removed – leave empty list
    balances = []

    # Leave log (all)
    leave_log_rows = db.execute('SELECT ll.*, vt.name as vt_name FROM leave_log ll JOIN vacation_types vt ON ll.vacation_type_id=vt.id WHERE ll.employee_id=? ORDER BY start_date DESC', (id,)).fetchall()
    leave_log = [dict(row) for row in leave_log_rows]

    # Yearly totals per vacation type (for the selected year) – kept as "taken days" only
    yearly_totals = []
    for vt in vac_types:
        total = db.execute('''
            SELECT COALESCE(SUM(days_taken), 0) FROM leave_log
            WHERE employee_id=? AND vacation_type_id=?
              AND strftime('%Y', start_date) = ?
        ''', (id, vt['id'], str(selected_year))).fetchone()[0]
        yearly_totals.append({'type': vt['name'], 'total': total})

    # Available years for dropdown
    min_year_row = db.execute('SELECT MIN(strftime("%Y", start_date)) as min_year FROM leave_log WHERE employee_id=?', (id,)).fetchone()
    min_year = int(min_year_row['min_year']) if min_year_row and min_year_row['min_year'] else current_year
    available_years = list(range(current_year, min_year - 1, -1)) if min_year <= current_year else [current_year]

    periods = db.execute('SELECT * FROM employee_work_periods WHERE employee_id=? ORDER BY start_date', (id,)).fetchall()
    total_service = compute_service_days(id)
    target = emp['target_service_days'] or 0
    uncompensated_days = emp['uncompensated_days'] or 0
    effective_service = total_service - uncompensated_days
    remaining = target - effective_service if target else 0
    tags = get_employee_tags(id)
    contract_end = emp['contract_end_date']
    contract_alert = None
    if contract_end:
        end_date = parse_date_robust(contract_end)
        if end_date:
            end_date_obj = datetime.datetime.strptime(end_date, '%Y-%m-%d').date()
            today = datetime.date.today()
            delta = (end_date_obj - today).days
            if delta <= 30:
                contract_alert = delta
    total_years, total_remain_days = breakdown_days_to_years_days(total_service)
    target_years, target_remain_days = breakdown_days_to_years_days(target) if target else (0,0)
    effective_years, effective_remain_days = breakdown_days_to_years_days(effective_service)
    remaining_years, remaining_remain_days = breakdown_days_to_years_days(abs(remaining))
    remaining_for_completion = max(remaining, 0)
    if remaining > 0:
        completion_date = datetime.date.today() + datetime.timedelta(days=remaining_for_completion)
        completion_date_str = completion_date.strftime('%Y-%m-%d')
    else:
        completion_date_str = "تم الوصول بالفعل"

    summary = {}
    for l in leave_log:
        vt_name = l['vt_name']
        days = l['days_taken']
        summary[vt_name] = summary.get(vt_name, 0) + days

    return render_template('employee_detail.html', emp=emp, emp_data=emp_data,
                           classification=classification_name,
                           balances=balances,
                           yearly_totals=yearly_totals,
                           selected_year=selected_year,
                           available_years=available_years,
                           leave_log=leave_log,
                           periods=periods,
                           total_service=total_service,
                           target=target,
                           remaining=remaining,
                           tags=tags,
                           contract_end=contract_end,
                           contract_alert=contract_alert,
                           total_years=total_years,
                           total_remain_days=total_remain_days,
                           target_years=target_years,
                           target_remain_days=target_remain_days,
                           effective_service=effective_service,
                           effective_years=effective_years,
                           effective_remain_days=effective_remain_days,
                           remaining_years=remaining_years,
                           remaining_remain_days=remaining_remain_days,
                           uncompensated_days=uncompensated_days,
                           completion_date=completion_date_str,
                           summary=summary)

@employees_bp.route('/employees/<int:id>/restore', methods=['POST'])
def restore(id):
    db = get_db()
    try:
        db.execute('UPDATE employees SET deleted_at = NULL WHERE id=?', (id,))
        db.commit()
        flash('تم استعادة الموظف بنجاح', 'success')
    except Exception as e:
        flash(f'فشل استعادة الموظف: {e}', 'error')
    return redirect(url_for('employees.list'))

@employees_bp.route('/employees/deleted')
def deleted_list():
    db = get_db()
    name_col = get_name_column() or 'id'
    rows = db.execute(f'''
        SELECT id, {name_col} AS name, deleted_at
        FROM employees
        WHERE deleted_at IS NOT NULL
        ORDER BY deleted_at DESC
    ''').fetchall()
    return render_template('deleted_employees.html', deleted_employees=rows)