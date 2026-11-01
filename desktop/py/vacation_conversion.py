import datetime
from .database import get_db
from .vacation_balance import invalidate_balance_cache
from .vacation_holidays import calculate_leave_days

def convert_all_hourly_to_days(employee_id):
    db = get_db()
    order = ['إدارية', 'بلا أجر', 'غير مبرر']
    hourly_types = db.execute(
        "SELECT id, name, hours_per_day FROM vacation_types WHERE is_yearly=0 AND name != 'أمومة'"
    ).fetchall()
    if not hourly_types:
        return "لا توجد أنواع إجازات بالساعة"
    total_days_to_deduct = 0.0
    type_details = {}
    current_year = datetime.date.today().year

    for ht in hourly_types:
        total_hours = db.execute(
            'SELECT COALESCE(SUM(hours - reduced_hours),0) FROM lateness_log WHERE employee_id=? AND vacation_type_id=?',
            (employee_id, ht['id'])
        ).fetchone()[0] or 0
        if total_hours > 0:
            days = total_hours / ht['hours_per_day']
            type_details[ht['id']] = (total_hours, ht['hours_per_day'], days)
            total_days_to_deduct += days

    if total_days_to_deduct <= 0:
        return "لا توجد ساعات متراكمة غير محولة"

    remaining_days = total_days_to_deduct
    deductions_made = []
    for vname in order:
        vt = db.execute("SELECT id FROM vacation_types WHERE name=?", (vname,)).fetchone()
        if not vt:
            continue
        # No balance check – always deduct as much as needed
        if remaining_days > 0:
            to_deduct = min(remaining_days, remaining_days)  # deduct all remaining
            db.execute(
                "INSERT INTO leave_log (employee_id, vacation_type_id, start_date, end_date, days_taken, notes) VALUES (?,?,date('now'),date('now'),?,?)",
                (employee_id, vt['id'], to_deduct, 'خصم تلقائي من الساعات المتراكمة')
            )
            deductions_made.append((vname, to_deduct))
            remaining_days -= to_deduct
            # Update lateness records
            for tid in list(type_details.keys()):
                total_hours, hpd, days = type_details[tid]
                if days == 0:
                    continue
                hours_to_reduce = min(total_hours, to_deduct * hpd)
                if hours_to_reduce > 0:
                    logs = db.execute(
                        'SELECT id, hours, reduced_hours FROM lateness_log WHERE employee_id=? AND vacation_type_id=? AND (hours - reduced_hours) > 0 ORDER BY date',
                        (employee_id, tid)
                    ).fetchall()
                    for log in logs:
                        if hours_to_reduce <= 0:
                            break
                        unreduced = log['hours'] - log['reduced_hours']
                        reduce_now = min(unreduced, hours_to_reduce)
                        db.execute('UPDATE lateness_log SET reduced_hours = reduced_hours + ? WHERE id=?',
                                   (reduce_now, log['id']))
                        hours_to_reduce -= reduce_now
                    new_total_hours = max(0, total_hours - (to_deduct * hpd))
                    new_days = new_total_hours / hpd
                    type_details[tid] = (new_total_hours, hpd, new_days)
    db.commit()
    invalidate_balance_cache(employee_id, None, current_year)
    if deductions_made:
        parts = [f"{name}: {days} يوم" for name, days in deductions_made]
        return "تم خصم الساعات: " + "، ".join(parts)
    return "لا يوجد رصيد كافٍ للخصم"