import datetime
import random
from flask import Blueprint, render_template, redirect, url_for, flash, session
from .database import get_db
from .employee_helpers import get_dynamic_fields, get_name_column
from .vacation_holidays import calculate_leave_days

wizard_bp = Blueprint('wizard', __name__, url_prefix='/wizard')
demo_employees_key = 'demo_employees'

def generate_demo_data():
    db = get_db()
    fields = get_dynamic_fields()
    col_map = {f['original_title']: f['column_name'] for f in fields}
    classifications = [row['id'] for row in db.execute('SELECT id, name FROM classifications').fetchall()]
    class_names = {row['id']: row['name'] for row in db.execute('SELECT id, name FROM classifications').fetchall()}
    departments = [row['name'] for row in db.execute('SELECT name FROM departments').fetchall()]
    tags = [row['id'] for row in db.execute('SELECT id FROM tags').fetchall()]
    vac_types = [row['id'] for row in db.execute('SELECT id, name FROM vacation_types WHERE name NOT IN ("تأخير (3 ساعات)","إجازة ساعية")').fetchall()]
    if not classifications or not departments:
        return False, "لا توجد تصنيفات أو أقسام كافية. قم بإعداد النظام أولاً."

    demo_emp_ids = []
    today = datetime.date.today()
    current_year = today.year
    names = ['أحمد', 'محمد', 'علي', 'حسن', 'محمود', 'فاطمة', 'نور', 'ليلى', 'سارة', 'هند']
    surnames = ['عباس', 'حسن', 'علي', 'حسين', 'موسى', 'كريم', 'سعيد', 'ناصر', 'جلال', 'مراد']
    fathers = ['علي', 'حسين', 'محمد', 'أحمد', 'خالد', 'سمير', 'جمال', 'ناصر', 'ماجد', 'عماد']
    blood_types = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-']
    marital_status_male = ['أعزب', 'متزوج', 'مطلق', 'أرمل']
    marital_status_female = ['عزباء', 'متزوجة', 'مطلقة', 'أرملة']
    cities = ['دمشق', 'حلب', 'حمص', 'حماة', 'اللاذقية', 'طرطوس', 'دير الزور', 'الرقة', 'السويداء', 'درعا']
    nationalities = ['سوري', 'مصري', 'لبناني', 'أردني', 'عراقي']
    education = ['ثانوية', 'بكالوريوس', 'ماجستير', 'دكتوراه', 'دبلوم']

    for i in range(10):
        job_num = f"DEMO{1000+i}"
        class_id = random.choice(classifications)
        class_name = class_names[class_id]
        dept = random.choice(departments)
        gender = random.choice(['ذكر', 'أنثى'])
        hire_date = today - datetime.timedelta(days=random.randint(365, 1800))  # 1-5 years ago
        contract_end = (today + datetime.timedelta(days=random.randint(100, 800))).strftime('%Y-%m-%d') if random.random() < 0.5 else None
        birth_date = hire_date - datetime.timedelta(days=random.randint(22*365, 40*365))  # age 22-40 at hire

        dyn_values = {}
        if 'الرقم الوظيفي' in col_map:
            dyn_values[col_map['الرقم الوظيفي']] = job_num
        if 'الاسم الأول' in col_map:
            dyn_values[col_map['الاسم الأول']] = names[i % len(names)]
        if 'الكنية' in col_map:
            dyn_values[col_map['الكنية']] = surnames[i % len(surnames)]
        if 'اسم الأب' in col_map:
            dyn_values[col_map['اسم الأب']] = random.choice(fathers)
        if 'اسم الأم وكنيتها' in col_map:
            dyn_values[col_map['اسم الأم وكنيتها']] = f"{random.choice(['فاطمة', 'نور', 'ليلى', 'سارة', 'هدى'])} {random.choice(surnames)}"
        if 'مكان الولادة' in col_map:
            dyn_values[col_map['مكان الولادة']] = random.choice(cities)
        if 'تاريخ الولادة' in col_map:
            dyn_values[col_map['تاريخ الولادة']] = birth_date.strftime('%Y-%m-%d')
        if 'الجنس' in col_map:
            dyn_values[col_map['الجنس']] = gender
        if 'القسم' in col_map:
            dyn_values[col_map['القسم']] = dept
        if 'تصنيف' in col_map:
            dyn_values[col_map['تصنيف']] = class_name
        if 'الجنسية' in col_map:
            dyn_values[col_map['الجنسية']] = random.choice(nationalities)
        if 'لديه إعاقة' in col_map:
            dyn_values[col_map['لديه إعاقة']] = random.choice(['لا', 'نعم']) if random.random() < 0.95 else 'نعم'
        if 'نوع الإعاقة' in col_map and dyn_values.get(col_map['لديه إعاقة']) == 'نعم':
            dyn_values[col_map['نوع الإعاقة']] = random.choice(['سمعية', 'بصرية', 'حركية', 'ذهنية'])
        if 'مكان السكن الحالي' in col_map:
            dyn_values[col_map['مكان السكن الحالي']] = random.choice(cities)
        if 'زمرة الدم' in col_map:
            dyn_values[col_map['زمرة الدم']] = random.choice(blood_types)
        if 'الحالة الاجتماعية' in col_map:
            if gender == 'أنثى':
                dyn_values[col_map['الحالة الاجتماعية']] = random.choice(marital_status_female)
            else:
                dyn_values[col_map['الحالة الاجتماعية']] = random.choice(marital_status_male)
        if 'الرقم الوطني' in col_map:
            dyn_values[col_map['الرقم الوطني']] = str(random.randint(10000000, 99999999))
        if 'رقم الهاتف المحمول' in col_map:
            dyn_values[col_map['رقم الهاتف المحمول']] = f"09{random.randint(10000000, 99999999)}"
        if 'تاريخ المباشرة الأخيرة' in col_map:
            dyn_values[col_map['تاريخ المباشرة الأخيرة']] = hire_date.strftime('%Y-%m-%d')
        if 'الشهادة العلمية' in col_map:
            dyn_values[col_map['الشهادة العلمية']] = random.choice(education)
        if 'حساب شام كاش' in col_map:
            dyn_values[col_map['حساب شام كاش']] = str(random.randint(1000000000, 9999999999))
        if 'الفئة الوظيفية' in col_map:
            dyn_values[col_map['الفئة الوظيفية']] = random.choice(['أولى', 'ثانية', 'ثالثة', 'رابعة'])
        if 'صفة التعيين' in col_map:
            dyn_values[col_map['صفة التعيين']] = random.choice(['مثبت', 'عقد', 'وكيل', 'مكلف'])
        if 'مكان العمل' in col_map:
            dyn_values[col_map['مكان العمل']] = dept
        if 'نوع العمل المكلف به' in col_map:
            dyn_values[col_map['نوع العمل المكلف به']] = random.choice(['إداري', 'فني', 'خدمي', 'اشرافي'])

        columns = list(dyn_values.keys()) + ['classification_id', 'extra_data', 'target_service_days', 'contract_end_date', 'last_updated']
        values = list(dyn_values.values()) + [class_id, '{}', 0, contract_end, today.strftime('%Y-%m-%d %H:%M:%S')]
        cur = db.execute(f'INSERT INTO employees ({",".join(columns)}) VALUES ({",".join("?"*len(columns))})', values)
        emp_id = cur.lastrowid
        demo_emp_ids.append(emp_id)

        # Add tags
        if tags:
            for tid in random.sample(tags, min(2, len(tags))):
                db.execute('INSERT OR IGNORE INTO employee_tags (employee_id, tag_id) VALUES (?,?)', (emp_id, tid))

        # Generate leave logs for previous years (up to 3 years back)
        current_year = today.year
        for year_offset in range(0, 4):  # current year, year-1, year-2, year-3
            year = current_year - year_offset
            if year < hire_date.year:
                continue
            # Random number of leaves per year (1-4)
            num_leaves = random.randint(1, 4)
            for _ in range(num_leaves):
                vt_id = random.choice(vac_types)
                # Random start date within the year
                start_date = datetime.date(year, random.randint(1, 12), random.randint(1, 28))
                if start_date < hire_date:
                    continue
                end_date = start_date + datetime.timedelta(days=random.randint(1, 7))
                days = calculate_leave_days(start_date.strftime('%Y-%m-%d'), end_date.strftime('%Y-%m-%d'), vt_id)
                db.execute('INSERT INTO leave_log (employee_id, vacation_type_id, start_date, end_date, days_taken, notes) VALUES (?,?,?,?,?,?)',
                           (emp_id, vt_id, start_date.strftime('%Y-%m-%d'), end_date.strftime('%Y-%m-%d'), days, 'بيانات تجريبية (وضع الساحر)'))

        # Add work periods (employment history)
        start_period = hire_date
        # Split into 1-3 periods
        num_periods = random.randint(1, 3)
        for p in range(num_periods):
            period_start = start_period
            if p == num_periods - 1:
                period_end = today
            else:
                period_end = period_start + datetime.timedelta(days=random.randint(180, 730))
                start_period = period_end + datetime.timedelta(days=1)
            db.execute('INSERT INTO employee_work_periods (employee_id, start_date, end_date, label) VALUES (?,?,?,?)',
                       (emp_id, period_start.strftime('%Y-%m-%d'), period_end.strftime('%Y-%m-%d'), f'فترة عمل {p+1}'))

    db.commit()
    return True, demo_emp_ids

@wizard_bp.route('/start', methods=['POST'])
def start_wizard():
    if 'demo_employees' in session:
        flash('يوجد بيانات تجريبية بالفعل. قم بحذفها أولاً.', 'warning')
        return redirect(url_for('dashboard.dashboard'))
    success, result = generate_demo_data()
    if success:
        session['demo_employees'] = result
        flash('تم إنشاء بيانات تجريبية بنجاح! سيتم حذفها تلقائياً بعد 24 ساعة.', 'success')
    else:
        flash(result, 'error')
    return redirect(url_for('dashboard.dashboard'))

@wizard_bp.route('/cleanup', methods=['POST'])
def cleanup_demo():
    if 'demo_employees' not in session:
        flash('لا توجد بيانات تجريبية', 'warning')
        return redirect(url_for('dashboard.dashboard'))
    db = get_db()
    for emp_id in session['demo_employees']:
        db.execute('DELETE FROM employees WHERE id=?', (emp_id,))
    db.commit()
    session.pop('demo_employees')
    flash('تم حذف البيانات التجريبية.', 'success')
    return redirect(url_for('dashboard.dashboard'))

@wizard_bp.route('/interactive')
def interactive_demo():
    if 'demo_employees' in session:
        flash('وضع التجربة مفعل بالفعل.', 'info')
        return redirect(url_for('dashboard.dashboard'))
    success, ids = generate_demo_data()
    if success:
        session['demo_employees'] = ids
        session['demo_mode'] = True
        flash('تم تفعيل وضع التجربة التفاعلي. جميع البيانات مؤقتة ويمكن حذفها بنقرة واحدة.', 'success')
    else:
        flash('فشل إنشاء البيانات التجريبية', 'error')
    return redirect(url_for('dashboard.dashboard'))

@wizard_bp.route('/interactive/cleanup', methods=['POST'])
def cleanup_interactive():
    if 'demo_employees' in session:
        db = get_db()
        for emp_id in session['demo_employees']:
            db.execute('DELETE FROM employees WHERE id=?', (emp_id,))
        db.commit()
        session.pop('demo_employees', None)
        session.pop('demo_mode', None)
        flash('تم تنظيف البيانات التجريبية.', 'success')
    else:
        flash('لا توجد بيانات تجريبية', 'warning')
    return redirect(url_for('dashboard.dashboard'))