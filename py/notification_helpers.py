import datetime
from datetime import timedelta
from .database import get_db

def get_global_notifications():
    db = get_db()
    today = datetime.date.today()
    current_year = today.year

    year_processed = db.execute('SELECT processed FROM year_settings WHERE year=?', (current_year,)).fetchone()
    unprocessed_year = 0 if (year_processed and year_processed['processed']) else 1

    # Low balance count removed

    soon = today + timedelta(days=30)
    contract_count = db.execute(
        'SELECT COUNT(*) FROM employees WHERE contract_end_date BETWEEN ? AND ?',
        (today.strftime('%Y-%m-%d'), soon.strftime('%Y-%m-%d'))
    ).fetchone()[0]

    unreduced = db.execute(
        'SELECT SUM(hours - reduced_hours) FROM lateness_log WHERE (hours - reduced_hours) > 0'
    ).fetchone()[0] or 0

    return {
        'unprocessed_year': unprocessed_year,
        'contract_ending': contract_count,
        'unreduced_hours': unreduced
    }