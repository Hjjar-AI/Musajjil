# py/utils.py
import datetime
import re
from flask import g
from .database import get_db

def parse_date_robust(date_str):
    """Convert various date formats to YYYY-MM-DD."""
    if not date_str:
        return None
    # Already in correct format
    if re.match(r'^\d{4}-\d{2}-\d{2}$', date_str):
        return date_str
    # Try DD/MM/YYYY
    try:
        dt = datetime.datetime.strptime(date_str, '%d/%m/%Y')
        return dt.strftime('%Y-%m-%d')
    except ValueError:
        pass
    # Try YYYY-MM-DD (already handled but for safety)
    try:
        dt = datetime.datetime.strptime(date_str, '%Y-%m-%d')
        return dt.strftime('%Y-%m-%d')
    except ValueError:
        raise ValueError(f"Invalid date format: {date_str}")

def get_department_column(db=None):
    """Return the database column name for 'القسم'."""
    if db is None:
        db = get_db()
    if hasattr(g, '_dept_col'):
        return g._dept_col
    row = db.execute(
        "SELECT column_name FROM employee_fields WHERE original_title = 'القسم' LIMIT 1"
    ).fetchone()
    g._dept_col = row['column_name'] if row else None
    return g._dept_col

def get_default_vacation_type_order():
    """Return list of vacation type names in priority order."""
    return ['إدارية', 'بلا أجر', 'غير مبرر']

def get_default_vacation_type_id(db=None):
    """Return the ID of the first available vacation type in priority order."""
    if db is None:
        db = get_db()
    for name in get_default_vacation_type_order():
        vt = db.execute('SELECT id FROM vacation_types WHERE name=?', (name,)).fetchone()
        if vt:
            return vt['id']
    return None