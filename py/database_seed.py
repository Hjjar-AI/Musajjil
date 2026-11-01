import os
import random
import datetime
from .database import get_db, migrate_db

def seed_data():
    migrate_db()
    db = get_db()

    # --- الحقول الافتراضية (تضاف مرة واحدة فقط) ---
    default_fields = [
        "الرقم الوظيفي", "الاسم الأول", "الكنية", "اسم الأب", "اسم الأم وكنيتها",
        "مكان الولادة", "تاريخ الولادة", "الجنس", "تصنيف", "القسم",
        "الجنسية", "لديه إعاقة", "نوع الإعاقة", "مكان السكن الحالي", "زمرة الدم",
        "الحالة الاجتماعية", "الرقم الوطني", "النفوس", "القيد ورقمه",
        "رقم الهاتف المحمول", "تاريخ المباشرة الأخيرة", "الشهادة العلمية",
        "حساب شام كاش", "الرقم الترتيبي في المشفى", "الفئة الوظيفية",
        "صفة التعيين", "مكان العمل", "نوع العمل المكلف به", "تاريخ آخر تحديث"
    ]

    existing = db.execute('SELECT column_name, original_title, position FROM employee_fields ORDER BY position').fetchall()
    existing_titles = {row['original_title']: row for row in existing}
    next_pos = len(existing)
    for i, title in enumerate(default_fields):
        if title not in existing_titles:
            col = f'f_{next_pos}'
            try:
                db.execute(f'ALTER TABLE employees ADD COLUMN {col} TEXT')
            except Exception:
                pass
            db.execute('INSERT INTO employee_fields (original_title, column_name, position) VALUES (?, ?, ?)',
                       (title, col, next_pos))
            next_pos += 1

    # --- أنواع الإجازات ---
    types = [
        ("إدارية", 8, 1),
        ("بلا أجر", 8, 1),
        ("مرضية إدارية", 8, 1),
        ("مرضية بلا أجر", 8, 1),
        ("أمومة", 8, 0),
        ("غير مبرر", 8, 1),
        ("تأخير (3 ساعات)", 3, 0),
        ("إجازة ساعية", 8, 0)
    ]
    for name, hpd, yearly in types:
        db.execute('INSERT OR IGNORE INTO vacation_types (name, hours_per_day, is_yearly) VALUES (?,?,?)',
                   (name, hpd, yearly))

    # --- التصنيفات الافتراضية (للقائمة فقط، بدون استحقاقات) ---
    default_classifications = ["طبيب", "ممرض", "إداري"]
    for cname in default_classifications:
        db.execute('INSERT OR IGNORE INTO classifications (name) VALUES (?)', (cname,))

    # --- معايير التقييم العامة ---
    general_criteria = [
        ("الأداء الوظيفي", '{"1":"غير مقبول","2":"ضعيف","3":"متوسط","4":"جيد","5":"ممتاز"}'),
        ("الالتزام", '{"1":"غير ملتزم","2":"ضعيف","3":"متوسط","4":"جيد","5":"ملتزم جدا"}'),
        ("المهارات", '{"1":"غير ماهر","2":"ضعيف","3":"متوسط","4":"جيد","5":"ماهر جدا"}')
    ]
    for crit, desc in general_criteria:
        db.execute('INSERT OR IGNORE INTO evaluation_criteria (name, classification_id, descriptions) VALUES (?, NULL, ?)', (crit, desc))

    # --- معايير التقييم الخاصة بكل تصنيف ---
    specific_criteria = {
        "طبيب": [
            ("الأداء الأكاديمي", '{"1":"غير مقبول","2":"ضعيف","3":"متوسط","4":"جيد","5":"ممتاز"}'),
            ("الإنتاج العلمي", '{"1":"لا يوجد","2":"ضعيف","3":"متوسط","4":"جيد","5":"ممتاز"}'),
            ("المهارات العملية", '{"1":"غير ماهر","2":"ضعيف","3":"متوسط","4":"جيد","5":"ماهر جدا"}'),
            ("التواصل", '{"1":"غير متواصل","2":"ضعيف","3":"متوسط","4":"جيد","5":"ممتاز"}')
        ],
        "ممرض": [
            ("رعاية المرضى", '{"1":"غير مقبول","2":"ضعيف","3":"متوسط","4":"جيد","5":"ممتاز"}'),
            ("المهارات الفنية", '{"1":"غير ماهر","2":"ضعيف","3":"متوسط","4":"جيد","5":"ماهر جدا"}'),
            ("التعاون", '{"1":"غير متعاون","2":"ضعيف","3":"متوسط","4":"جيد","5":"متعاون جدا"}')
        ],
        "إداري": [
            ("التنظيم", '{"1":"غير منظم","2":"ضعيف","3":"متوسط","4":"جيد","5":"ممتاز"}'),
            ("السرعة", '{"1":"بطيء","2":"ضعيف","3":"متوسط","4":"جيد","5":"سريع جدا"}'),
            ("المرونة", '{"1":"غير مرن","2":"ضعيف","3":"متوسط","4":"جيد","5":"مرن جدا"}')
        ]
    }
    class_rows = db.execute('SELECT id, name FROM classifications').fetchall()
    for cls in class_rows:
        cname = cls['name']
        cid = cls['id']
        if cname in specific_criteria:
            for crit, desc in specific_criteria[cname]:
                db.execute('INSERT OR IGNORE INTO evaluation_criteria (name, classification_id, descriptions) VALUES (?, ?, ?)', (crit, cid, desc))

    # --- العطل الرسمية الجديدة (بعد التحرير) ---
    # يمكن تعديل هذه القائمة حسب المناسبات الرسمية المعتمدة
    fixed_holidays = [
        ("عيد التحرير", "2000-12-08", 1),      # 8 ديسمبر - يوم التحرير
        ("عيد العمال", "2000-05-01", 1),       # 1 أيار - عيد العمال
        ("رأس السنة الميلادية", "2000-01-01", 1),
    ]
    for name, date_str, is_fixed in fixed_holidays:
        db.execute('INSERT OR IGNORE INTO public_holidays (name, date, is_fixed) VALUES (?,?,?)', (name, date_str, is_fixed))

    # --- أقسام افتراضية ---
    default_departments = [
        "تقنية المعلومات", "الموارد البشرية", "المالية", "التسويق",
        "المبيعات", "الدعم الفني", "التمريض", "الطوارئ",
    ]
    for dname in default_departments:
        db.execute('INSERT OR IGNORE INTO departments (name) VALUES (?)', (dname,))

    db.commit()