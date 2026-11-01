import datetime
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db

smart_holidays_bp = Blueprint('smart_holidays', __name__, url_prefix='/smart-holidays')

def init_smart_holidays_db():
    db = get_db()
    # Add repeat_type and repeat_interval columns if they don't exist
    try:
        db.execute('ALTER TABLE holiday_rules ADD COLUMN repeat_type TEXT DEFAULT "once"')
    except:
        pass
    try:
        db.execute('ALTER TABLE holiday_rules ADD COLUMN repeat_interval INTEGER DEFAULT 1')
    except:
        pass
    db.commit()

@smart_holidays_bp.route('/')
def list_rules():
    init_smart_holidays_db()
    db = get_db()
    rules = db.execute('SELECT * FROM holiday_rules ORDER BY id').fetchall()
    departments = [row['name'] for row in db.execute('SELECT name FROM departments').fetchall()]
    classifications = [row['name'] for row in db.execute('SELECT name FROM classifications').fetchall()]
    return render_template('smart_holidays.html', rules=rules, departments=departments, classifications=classifications)

@smart_holidays_bp.route('/add', methods=['POST'])
def add_rule():
    name = request.form['name'].strip()
    rule_type = request.form['rule_type']
    rule_value = request.form['rule_value'].strip()
    repeat_type = request.form.get('repeat_type', 'once')
    repeat_interval = request.form.get('repeat_interval', 1, type=int)
    target_depts_list = request.form.getlist('target_departments')
    target_classes_list = request.form.getlist('target_classifications')
    target_departments = ','.join([d for d in target_depts_list if d]) if target_depts_list else ''
    target_classifications = ','.join([c for c in target_classes_list if c]) if target_classes_list else ''
    description = request.form.get('description', '')
    is_active = 1 if request.form.get('is_active') else 0

    # If rule_type is 'formula' and a preset is selected, map to formula string
    if rule_type == 'formula':
        preset_map = {
            'preset_last_friday': 'last Friday of month',
            'preset_first_monday': 'first Monday of month',
            'preset_last_monday': 'last Monday of month',
            'preset_first_friday': 'first Friday of month'
        }
        if rule_value in preset_map:
            rule_value = preset_map[rule_value]
        # otherwise keep as entered (custom formula)

    if not name or not rule_value:
        flash('الاسم وقاعدة العطلة مطلوبان', 'error')
        return redirect(url_for('smart_holidays.list_rules'))
    db = get_db()
    db.execute('''
        INSERT INTO holiday_rules (name, rule_type, rule_value, repeat_type, repeat_interval,
                                   target_departments, target_classifications, is_active, description)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ''', (name, rule_type, rule_value, repeat_type, repeat_interval,
          target_departments, target_classifications, is_active, description))
    db.commit()
    flash('تمت إضافة القاعدة بنجاح', 'success')
    return redirect(url_for('smart_holidays.list_rules'))

@smart_holidays_bp.route('/delete/<int:id>', methods=['POST'])
def delete_rule(id):
    db = get_db()
    db.execute('DELETE FROM holiday_rules WHERE id=?', (id,))
    db.commit()
    flash('تم حذف القاعدة', 'success')
    return redirect(url_for('smart_holidays.list_rules'))

@smart_holidays_bp.route('/toggle/<int:id>', methods=['POST'])
def toggle_rule(id):
    db = get_db()
    rule = db.execute('SELECT is_active FROM holiday_rules WHERE id=?', (id,)).fetchone()
    new_status = 0 if rule['is_active'] else 1
    db.execute('UPDATE holiday_rules SET is_active=? WHERE id=?', (new_status, id))
    db.commit()
    flash('تم تغيير حالة القاعدة', 'success')
    return redirect(url_for('smart_holidays.list_rules'))

@smart_holidays_bp.route('/preview')
def preview():
    db = get_db()
    rules = db.execute('SELECT * FROM holiday_rules WHERE is_active=1').fetchall()
    year = request.args.get('year', datetime.date.today().year, type=int)
    holidays = []
    start_date = datetime.date(year, 1, 1)
    end_date = datetime.date(year, 12, 31)
    for rule in rules:
        dates = get_dates_for_rule(rule, year, start_date, end_date)
        for d in dates:
            holidays.append({'date': d.strftime('%Y-%m-%d'), 'name': rule['name'], 'rule_id': rule['id']})
    # Remove duplicates
    unique = {}
    for h in holidays:
        key = (h['date'], h['name'])
        if key not in unique:
            unique[key] = h
    holidays = list(unique.values())
    holidays.sort(key=lambda x: x['date'])
    return render_template('smart_holidays_preview.html', holidays=holidays, year=year)

def get_dates_for_rule(rule, year, start_date, end_date):
    """Return a list of dates within the year that match this rule, considering repetition."""
    rule_type = rule['rule_type']
    rule_value = rule['rule_value']
    repeat_type = rule.get('repeat_type', 'once')
    repeat_interval = rule.get('repeat_interval', 1)
    dates = []

    # Helper to add a date if within year
    def add_date(d):
        if start_date <= d <= end_date:
            dates.append(d)

    if rule_type == 'weekday':
        # rule_value is a weekday number (0-6, Monday=0)
        try:
            weekday = int(rule_value)
        except:
            return []
        # Determine start of first occurrence
        first = datetime.date(year, 1, 1)
        # Find first occurrence of that weekday
        days_ahead = (weekday - first.weekday()) % 7
        first_occurrence = first + datetime.timedelta(days=days_ahead)
        if repeat_type == 'once':
            add_date(first_occurrence)
        elif repeat_type == 'weekly':
            current = first_occurrence
            while current <= end_date:
                add_date(current)
                current += datetime.timedelta(weeks=repeat_interval)
        elif repeat_type == 'monthly':
            # Same weekday of each month (e.g., first Monday)
            current = first_occurrence
            while current <= end_date:
                add_date(current)
                # Move to next month, same weekday position
                next_month = current.replace(day=28) + datetime.timedelta(days=4)
                # Find the same weekday in next month
                days_ahead = (weekday - next_month.weekday()) % 7
                current = next_month + datetime.timedelta(days=days_ahead)
        elif repeat_type == 'yearly':
            # Same day-of-year each year (only if not a leap day issue)
            month_day = (first_occurrence.month, first_occurrence.day)
            for y in range(year, year+1):
                try:
                    d = datetime.date(y, month_day[0], month_day[1])
                    add_date(d)
                except ValueError:
                    pass

    elif rule_type == 'date':
        # rule_value is comma-separated days of month (e.g., "1,15")
        try:
            days = [int(d.strip()) for d in rule_value.split(',') if d.strip()]
        except:
            return []
        for day in days:
            try:
                d = datetime.date(year, 1, day)
                if repeat_type == 'once':
                    add_date(d)
                elif repeat_type == 'monthly':
                    for month in range(1, 13):
                        try:
                            d = datetime.date(year, month, day)
                            add_date(d)
                        except ValueError:
                            pass
                elif repeat_type == 'yearly':
                    # Already only one date per year if day is fixed
                    add_date(d)
            except ValueError:
                pass

    elif rule_type == 'formula':
        # Formulas like "last Friday of month" – they produce a date for each month by definition
        # We'll generate for each month
        for month in range(1, 13):
            first_of_month = datetime.date(year, month, 1)
            last_day = datetime.date(year, month, 28) + datetime.timedelta(days=4)
            formula = rule_value.lower()
            if 'last friday' in formula:
                # Find last Friday
                last_friday = last_day - datetime.timedelta(days=(last_day.weekday() - 4) % 7)
                add_date(last_friday)
            elif 'first monday' in formula:
                days_to_monday = (7 - first_of_month.weekday()) % 7
                first_monday = first_of_month + datetime.timedelta(days=days_to_monday) if days_to_monday != 0 else first_of_month
                add_date(first_monday)
            elif 'last monday' in formula:
                last_monday = last_day - datetime.timedelta(days=(last_day.weekday() - 0) % 7)
                add_date(last_monday)
            elif 'first friday' in formula:
                days_to_friday = (4 - first_of_month.weekday()) % 7
                first_friday = first_of_month + datetime.timedelta(days=days_to_friday)
                add_date(first_friday)
    return dates