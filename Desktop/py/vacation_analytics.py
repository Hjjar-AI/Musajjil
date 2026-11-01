# py/vacation_analytics.py
import math
import datetime
from collections import defaultdict
from .database import get_db
from .employee_helpers import get_name_column

def get_std_deviation_by_department(year=None):
    if year is None:
        year = datetime.date.today().year
    db = get_db()
    dept_col = None
    for f in db.execute('SELECT original_title, column_name FROM employee_fields WHERE original_title="القسم"').fetchall():
        dept_col = f['column_name']
    if not dept_col:
        return []
    rows = db.execute(f'''
        SELECT e.{dept_col} AS dept, e.id, COALESCE(SUM(ll.days_taken),0) AS total
        FROM employees e
        LEFT JOIN leave_log ll ON e.id = ll.employee_id AND strftime('%%Y', ll.start_date) = ?
        WHERE ll.vacation_type_id NOT IN (SELECT id FROM vacation_types WHERE name IN ('تأخير (3 ساعات)','إجازة ساعية'))
        GROUP BY e.{dept_col}, e.id
    ''', (str(year),)).fetchall()
    dept_data = defaultdict(list)
    for r in rows:
        if r['dept']:
            dept_data[r['dept']].append(r['total'])
    result = []
    for dept, values in dept_data.items():
        if len(values) < 2:
            continue
        mean = sum(values) / len(values)
        variance = sum((x - mean) ** 2 for x in values) / len(values)
        std_dev = math.sqrt(variance)
        result.append({
            'dept': dept,
            'mean': round(mean, 1),
            'std_dev': round(std_dev, 1),
            'min': min(values),
            'max': max(values),
            'count': len(values)
        })
    return sorted(result, key=lambda x: x['std_dev'], reverse=True)

def detect_suspicious_patterns(year=None):
    if year is None:
        year = datetime.date.today().year
    db = get_db()
    rows = db.execute('''
        SELECT employee_id, 
               COUNT(CASE WHEN strftime('%w', start_date) = '4' THEN 1 END) as thu_starts,
               COUNT(CASE WHEN strftime('%w', end_date) = '6' THEN 1 END) as sat_ends,
               COUNT(*) as total_leaves
        FROM leave_log
        WHERE strftime('%Y', start_date) = ? 
        AND vacation_type_id NOT IN (SELECT id FROM vacation_types WHERE name IN ('تأخير (3 ساعات)','إجازة ساعية'))
        GROUP BY employee_id
        HAVING thu_starts + sat_ends >= 3 AND (thu_starts + sat_ends) * 2 >= total_leaves
    ''', (str(year),)).fetchall()
    suspicious = []
    name_col = get_name_column()
    for r in rows:
        if name_col:
            emp = db.execute(f'SELECT {name_col} FROM employees WHERE id=?', (r['employee_id'],)).fetchone()
            if emp:
                suspicious.append({
                    'name': emp[0],
                    'thu_starts': r['thu_starts'],
                    'sat_ends': r['sat_ends'],
                    'total_leaves': r['total_leaves']
                })
    return suspicious