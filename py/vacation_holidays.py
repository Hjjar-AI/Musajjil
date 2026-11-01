import datetime
from datetime import date, timedelta
from .database import get_db
from .utils import parse_date_robust

def get_public_holidays_in_range(start, end):
    db = get_db()
    holidays = []
    rows = db.execute('SELECT date, is_fixed FROM public_holidays').fetchall()
    for row in rows:
        holiday_date = datetime.datetime.strptime(row['date'], '%Y-%m-%d').date()
        if row['is_fixed']:
            for year in range(start.year, end.year + 1):
                try:
                    d = date(year, holiday_date.month, holiday_date.day)
                except ValueError:
                    continue
                if start <= d <= end:
                    holidays.append(d)
        else:
            if start <= holiday_date <= end:
                holidays.append(holiday_date)
    return holidays

def calculate_leave_days(start_str, end_str, vacation_type_id=None):
    start_str = parse_date_robust(start_str)
    end_str = parse_date_robust(end_str)
    if not start_str or not end_str:
        return 0
    start = datetime.datetime.strptime(start_str, '%Y-%m-%d').date()
    end = datetime.datetime.strptime(end_str, '%Y-%m-%d').date()
    if end < start:
        return 0

    total_days = (end - start).days + 1

    exclude_holidays = True
    if vacation_type_id is not None:
        db = get_db()
        vt = db.execute('SELECT name FROM vacation_types WHERE id=?', (vacation_type_id,)).fetchone()
        if vt and ('بلا أجر' in vt['name'] or 'مرضية' in vt['name']):
            exclude_holidays = False

    first_friday_offset = (4 - start.weekday()) % 7
    fridays = (total_days + first_friday_offset) // 7 if first_friday_offset < total_days else 0

    holiday_days = 0
    if exclude_holidays:
        holidays = get_public_holidays_in_range(start, end)
        holiday_days = len([h for h in holidays if h.weekday() != 4])

    return total_days - fridays - holiday_days

def is_public_holiday(date_str):
    date_str = parse_date_robust(date_str)
    if not date_str:
        return False
    d = datetime.datetime.strptime(date_str, '%Y-%m-%d').date()
    db = get_db()
    row = db.execute('SELECT 1 FROM public_holidays WHERE date=? AND is_fixed=0', (date_str,)).fetchone()
    if row:
        return True
    row = db.execute('SELECT 1 FROM public_holidays WHERE is_fixed=1 AND strftime("%m-%d", date)=?',
                     (d.strftime('%m-%d'),)).fetchone()
    return row is not None

def get_all_holiday_dates():
    today = date.today()
    current_year = today.year
    db = get_db()
    rows = db.execute('SELECT date, is_fixed FROM public_holidays').fetchall()
    result = set()
    for row in rows:
        d = datetime.datetime.strptime(row['date'], '%Y-%m-%d').date()
        if row['is_fixed']:
            try:
                fixed_date = date(current_year, d.month, d.day)
                result.add(fixed_date.strftime('%Y-%m-%d'))
            except ValueError:
                pass
        else:
            result.add(d.strftime('%Y-%m-%d'))
    return result