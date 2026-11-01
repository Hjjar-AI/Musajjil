import datetime
import glob
import os
from flask import Blueprint, render_template
from .database import get_db
from .employee_helpers import get_name_column
from .vacation_analytics import get_std_deviation_by_department, detect_suspicious_patterns
from .config import BACKUP_DIR, IS_PYTHONANYWHERE

dashboard_bp = Blueprint('dashboard', __name__)

@dashboard_bp.route('/dashboard')
def dashboard():
    db = get_db()
    emp_count = db.execute('SELECT COUNT(*) FROM employees').fetchone()[0]
    tag_count = db.execute('SELECT COUNT(*) FROM tags').fetchone()[0]
    eval_count = db.execute('SELECT COUNT(*) FROM evaluations').fetchone()[0]
    backups = sorted(glob.glob(os.path.join(BACKUP_DIR, 'employee_data_backup_*.db')))
    last_backup = os.path.basename(backups[-1]) if backups else 'لا يوجد'
    current_year = datetime.date.today().year

    today = datetime.date.today()
    soon = today + datetime.timedelta(days=30)
    contracts_ending = db.execute('SELECT id, contract_end_date FROM employees WHERE contract_end_date BETWEEN ? AND ?', (today.strftime('%Y-%m-%d'), soon.strftime('%Y-%m-%d'))).fetchall()
    contract_alerts = []
    name_col = get_name_column()
    for c in contracts_ending:
        name_row = db.execute(f"SELECT {name_col} FROM employees WHERE id=?", (c['id'],)).fetchone() if name_col else None
        name = name_row[0] if name_row else c['id']
        end_date = datetime.datetime.strptime(c['contract_end_date'], '%Y-%m-%d').date()
        remaining = (end_date - today).days
        contract_alerts.append((name, c['contract_end_date'], remaining))
    unreduced = db.execute('SELECT SUM(hours - reduced_hours) FROM lateness_log WHERE (hours - reduced_hours) > 0').fetchone()[0] or 0
    most_absent = []
    if name_col:
        rows = db.execute(f'SELECT e.id, {name_col} AS name, COALESCE(SUM(ll.days_taken),0) AS total_days FROM employees e LEFT JOIN leave_log ll ON e.id = ll.employee_id AND strftime("%Y", ll.start_date) = ? GROUP BY e.id ORDER BY total_days DESC LIMIT 5', (str(current_year),)).fetchall()
        most_absent = [(r['name'], r['total_days']) for r in rows if r['total_days'] > 0]
    leave_distribution = []
    for vt in db.execute('SELECT id, name FROM vacation_types WHERE name != "تأخير (3 ساعات)" AND name != "إجازة ساعية"').fetchall():
        total_days = db.execute('SELECT COALESCE(SUM(days_taken),0) FROM leave_log WHERE vacation_type_id=? AND strftime("%Y", start_date)=?', (vt['id'], str(current_year))).fetchone()[0]
        if total_days > 0:
            leave_distribution.append({'type': vt['name'], 'days': total_days})
    total_leave_days_year = db.execute("SELECT COALESCE(SUM(days_taken),0) FROM leave_log WHERE strftime('%Y', start_date)=? AND vacation_type_id NOT IN (SELECT id FROM vacation_types WHERE name IN ('تأخير (3 ساعات)','إجازة ساعية'))", (str(current_year),)).fetchone()[0] or 0
    emp_with_leave = db.execute("SELECT COUNT(DISTINCT employee_id) FROM leave_log WHERE strftime('%Y', start_date)=? AND vacation_type_id NOT IN (SELECT id FROM vacation_types WHERE name IN ('تأخير (3 ساعات)','إجازة ساعية'))", (str(current_year),)).fetchone()[0] or 0
    avg_leave_days_active = round(total_leave_days_year / emp_with_leave, 1) if emp_with_leave > 0 else 0
    week_later = today + datetime.timedelta(days=7)
    upcoming_leaves = db.execute("SELECT COUNT(DISTINCT employee_id) FROM leave_log WHERE start_date BETWEEN ? AND ? AND vacation_type_id NOT IN (SELECT id FROM vacation_types WHERE name IN ('تأخير (3 ساعات)','إجازة ساعية'))", (today.strftime('%Y-%m-%d'), week_later.strftime('%Y-%m-%d'))).fetchone()[0] or 0
    pct_took_leave = round((emp_with_leave / emp_count) * 100) if emp_count > 0 else 0
    top_dept = ''
    top_dept_days = 0
    dept_col = None
    for f in db.execute('SELECT original_title, column_name FROM employee_fields').fetchall():
        if f['original_title'].strip() == 'القسم':
            dept_col = f['column_name']
            break
    if dept_col:
        dept_row = db.execute(f'SELECT e.{dept_col} AS dept, COALESCE(SUM(ll.days_taken),0) AS total FROM employees e JOIN leave_log ll ON e.id = ll.employee_id AND strftime("%Y", ll.start_date)=? WHERE ll.vacation_type_id NOT IN (SELECT id FROM vacation_types WHERE name IN ("تأخير (3 ساعات)","إجازة ساعية")) GROUP BY e.{dept_col} ORDER BY total DESC LIMIT 1', (str(current_year),)).fetchone()
        if dept_row:
            top_dept = dept_row['dept']
            top_dept_days = dept_row['total']
    current_month_start = today.replace(day=1)
    if today.month == 12:
        next_month_start = today.replace(year=today.year+1, month=1, day=1)
    else:
        next_month_start = today.replace(month=today.month+1, day=1)
    holidays_this_month = db.execute('SELECT COUNT(*) FROM public_holidays WHERE date BETWEEN ? AND ?', (current_month_start.strftime('%Y-%m-%d'), next_month_start.strftime('%Y-%m-%d'))).fetchone()[0] or 0
    sick_ids = [r['id'] for r in db.execute("SELECT id FROM vacation_types WHERE name IN ('مرضية إدارية', 'مرضية بلا أجر')").fetchall()]
    total_sick_days = 0
    if sick_ids:
        placeholders = ','.join(['?']*len(sick_ids))
        total_sick_days = db.execute(f"SELECT COALESCE(SUM(days_taken),0) FROM leave_log WHERE vacation_type_id IN ({placeholders}) AND strftime('%Y', start_date)=?", sick_ids + [str(current_year)]).fetchone()[0] or 0
    no_leave_count = emp_count - emp_with_leave
    working_days_year = 260
    absence_rate = round((total_leave_days_year / (emp_count * working_days_year)) * 100, 2) if emp_count > 0 else 0
    oldest = db.execute('SELECT employee_id, COALESCE(SUM(julianday(end_date)-julianday(start_date)+1),0) AS days FROM employee_work_periods GROUP BY employee_id ORDER BY days DESC LIMIT 1').fetchone()
    oldest_name = ''
    oldest_days = 0
    oldest_id = None
    if oldest:
        oldest_id = oldest['employee_id']
        name_col = get_name_column()
        if name_col:
            oldest_name_row = db.execute(f'SELECT {name_col} FROM employees WHERE id=?', (oldest_id,)).fetchone()
            oldest_name = oldest_name_row[0] if oldest_name_row else ''
        oldest_days = oldest['days']
    suspicious_patterns = detect_suspicious_patterns()
    std_deviation_data = get_std_deviation_by_department()
    return render_template('dashboard.html', 
                           emp_count=emp_count, tag_count=tag_count, eval_count=eval_count,
                           last_backup=last_backup,
                           contract_alerts=contract_alerts,
                           unreduced_hours=unreduced, most_absent=most_absent,
                           leave_distribution=leave_distribution, current_year=current_year,
                           total_leave_days_year=total_leave_days_year,
                           avg_leave_days_active=avg_leave_days_active,
                           upcoming_leaves=upcoming_leaves,
                           pct_took_leave=pct_took_leave, top_dept=top_dept,
                           top_dept_days=top_dept_days,
                           holidays_this_month=holidays_this_month, total_sick_days=total_sick_days,
                           no_leave_count=no_leave_count, absence_rate=absence_rate,
                           oldest_name=oldest_name, oldest_days=oldest_days, oldest_id=oldest_id,
                           suspicious_patterns=suspicious_patterns,
                           std_deviation_data=std_deviation_data,
                           IS_PYTHONANYWHERE=IS_PYTHONANYWHERE)