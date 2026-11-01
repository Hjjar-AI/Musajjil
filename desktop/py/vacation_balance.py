import datetime
from .database import get_db
from .config import CACHE_TTL_BALANCE

def compute_vacation_balance(employee_id, vacation_type_id, year):
    """Return (0, taken, 0) – balance is always zero/ignored."""
    db = get_db()
    # Sum taken days for the given year
    start_of_year = datetime.date(year, 1, 1)
    end_of_year = datetime.date(year, 12, 31)
    start_str = start_of_year.strftime('%Y-%m-%d')
    end_str = end_of_year.strftime('%Y-%m-%d')

    taken = 0.0
    leaves = db.execute('''
        SELECT start_date, end_date, days_taken FROM leave_log
        WHERE employee_id=? AND vacation_type_id=?
          AND start_date <= ? AND end_date >= ?
    ''', (employee_id, vacation_type_id, end_str, start_str)).fetchall()

    from .vacation_holidays import calculate_leave_days
    for leave in leaves:
        leave_start = datetime.datetime.strptime(leave['start_date'], '%Y-%m-%d').date()
        leave_end = datetime.datetime.strptime(leave['end_date'], '%Y-%m-%d').date()
        overlap_start = max(leave_start, start_of_year)
        overlap_end = min(leave_end, end_of_year)
        if overlap_start <= overlap_end:
            prorated_days = calculate_leave_days(
                overlap_start.strftime('%Y-%m-%d'),
                overlap_end.strftime('%Y-%m-%d'),
                vacation_type_id
            )
            taken += prorated_days

    return 0, taken, 0   # entitled = 0, taken = actual, balance = 0

def get_cached_balance(employee_id, vacation_type_id, year, ttl=None):
    """Return (0, taken, 0) – no cache needed, but kept for interface"""
    return compute_vacation_balance(employee_id, vacation_type_id, year)

def invalidate_balance_cache(*args, **kwargs):
    """No‑op"""
    pass

def ensure_year_initialized_for_employee(employee_id, year):
    """No‑op – no entitlements to initialise"""
    pass

def get_entitlement_for_year(employee_id, vacation_type_id, year, default_if_missing=True):
    return 0

def set_entitlement_for_year(employee_id, vacation_type_id, year, entitled_days):
    pass

def initialize_year_for_employee(employee_id, year):
    pass