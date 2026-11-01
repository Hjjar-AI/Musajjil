import datetime
from datetime import date
from functools import lru_cache
from flask import g
from .database import get_db
import sqlite3

@lru_cache(maxsize=1)
def _get_dynamic_fields_cached():
    """استعلام واحد فقط طوال عمر التطبيق (حتى إعادة التشغيل)."""
    db = get_db()
    try:
        rows = db.execute('SELECT original_title, column_name, position FROM employee_fields ORDER BY position').fetchall()
        return [dict(r) for r in rows]
    except sqlite3.OperationalError:
        # الجدول غير موجود بعد – نرجع قائمة فارغة
        return []

def get_dynamic_fields():
    if hasattr(g, 'dynamic_fields'):
        return g.dynamic_fields
    # استخدام النتيجة المخزنة مؤقتاً
    g.dynamic_fields = _get_dynamic_fields_cached()
    return g.dynamic_fields

def get_classification_id(employee_id):
    db = get_db()
    fields = get_dynamic_fields()
    class_col = None
    for f in fields:
        if f['original_title'].strip().lower() in ('تصنيف', 'classification'):
            class_col = f['column_name']
            break
    if not class_col:
        return None
    row = db.execute(f'SELECT {class_col} FROM employees WHERE id = ?', (employee_id,)).fetchone()
    if row and row[0]:
        class_name = row[0]
        c = db.execute('SELECT id FROM classifications WHERE name = ?', (class_name,)).fetchone()
        return c['id'] if c else None
    return None

def compute_service_days(employee_id):
    db = get_db()
    total = db.execute('SELECT COALESCE(SUM(julianday(end_date) - julianday(start_date) + 1),0) FROM employee_work_periods WHERE employee_id=?',
                       (employee_id,)).fetchone()[0]
    return total

def get_employee_tags(employee_id):
    db = get_db()
    rows = db.execute('SELECT t.name FROM tags t JOIN employee_tags et ON t.id=et.tag_id WHERE et.employee_id=?', (employee_id,)).fetchall()
    return [r['name'] for r in rows]

def get_name_column():
    if hasattr(g, 'name_column'):
        return g.name_column
    fields = get_dynamic_fields()
    for f in fields:
        if f['original_title'].strip() == 'الاسم الأول':
            g.name_column = f['column_name']
            return f['column_name']
    if fields:
        g.name_column = fields[0]['column_name']
        return fields[0]['column_name']
    g.name_column = 'id'
    return 'id'

def get_search_columns():
    if hasattr(g, 'search_columns'):
        return g.search_columns
    fields = get_dynamic_fields()
    cols = []
    for f in fields:
        if f['original_title'].strip() in ('الاسم الأول', 'الكنية', 'اسم الأب', 'اسم الأم وكنيتها', 'الرقم الوطني'):
            cols.append(f['column_name'])
    if not cols and fields:
        cols = [fields[0]['column_name']]
    if not cols:
        cols = ['id']
    g.search_columns = cols
    return cols

def add_years_months(start_date, years, months):
    if not isinstance(start_date, date):
        raise ValueError("start_date must be a date")
    y = start_date.year + years
    m = start_date.month + months
    while m > 12:
        y += 1
        m -= 12
    while m < 1:
        y -= 1
        m += 12
    d = start_date.day
    while True:
        try:
            return date(y, m, d)
        except ValueError:
            d -= 1
            if d == 0:
                raise

def compute_target_days_from_years_months(employee_id, years, months):
    db = get_db()
    first = db.execute('SELECT start_date FROM employee_work_periods WHERE employee_id=? ORDER BY start_date ASC LIMIT 1',
                       (employee_id,)).fetchone()
    if first and first['start_date']:
        start = datetime.datetime.strptime(first['start_date'], '%Y-%m-%d').date()
        target = add_years_months(start, years, months)
        return (target - start).days
    else:
        return round(years * 365.25 + months * 30.4375)

def breakdown_days_to_ym(total_days):
    if total_days <= 0:
        return 0, 0
    years = int(total_days // 365.25)
    remainder = total_days - years * 365.25
    months = int(remainder // 30.4375)
    return years, months

def breakdown_days_to_years_days(total_days):
    if total_days <= 0:
        return 0, 0
    years = total_days // 365
    remaining = total_days - (years * 365)
    return years, remaining

def get_employee_key_field():
    fields = get_dynamic_fields()
    for f in fields:
        if f['original_title'] == 'الرقم الوظيفي':
            return f['column_name']
    return None