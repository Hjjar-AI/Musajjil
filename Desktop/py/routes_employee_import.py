# py/routes_employee_import.py
import io
import datetime
import pandas as pd
from flask import Blueprint, render_template, request, redirect, url_for, flash, session
from werkzeug.utils import secure_filename
from .database import get_db
from .employee_helpers import get_dynamic_fields, get_name_column, get_search_columns
from .config import MAX_CONTENT_LENGTH

employee_import_bp = Blueprint('employee_import', __name__, url_prefix='/employee-import')

ALLOWED_EXTENSIONS = {'xlsx', 'xls'}

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def get_system_fields():
    """إرجاع قائمة الحقول المتاحة في النظام مع أسمائها الأصلية وأسماء الأعمدة."""
    db = get_db()
    fields = db.execute('SELECT original_title, column_name FROM employee_fields ORDER BY position').fetchall()
    return [{'original': f['original_title'], 'column': f['column_name']} for f in fields]

def get_classifications_dict():
    db = get_db()
    rows = db.execute('SELECT id, name FROM classifications').fetchall()
    return {row['name']: row['id'] for row in rows}

def get_departments_dict():
    db = get_db()
    rows = db.execute('SELECT id, name FROM departments').fetchall()
    return {row['name']: row['id'] for row in rows}

def get_tags_dict():
    db = get_db()
    rows = db.execute('SELECT id, name FROM tags').fetchall()
    return {row['name']: row['id'] for row in rows}

@employee_import_bp.route('/')
def index():
    return redirect(url_for('employee_import.upload'))

@employee_import_bp.route('/upload', methods=['GET', 'POST'])
def upload():
    if request.method == 'POST':
        if 'file' not in request.files:
            flash('الرجاء اختيار ملف', 'error')
            return redirect(request.url)
        file = request.files['file']
        if file.filename == '' or not allowed_file(file.filename):
            flash('نوع الملف غير مدعوم. استخدم Excel (.xlsx, .xls)', 'error')
            return redirect(request.url)
        
        # التحقق من حجم الملف
        file.seek(0, 2)
        size = file.tell()
        file.seek(0)
        if size > MAX_CONTENT_LENGTH:
            flash(f'حجم الملف يتجاوز الحد المسموح ({MAX_CONTENT_LENGTH // (1024*1024)} MB)', 'error')
            return redirect(request.url)
        
        try:
            # استخدام defusedxml إذا كان متاحاً (لأمان Excel)
            try:
                from defusedxml import lxml as defused_lxml
                df = pd.read_excel(file, engine='openpyxl')
            except ImportError:
                df = pd.read_excel(file)
            
            if df.empty:
                flash('الملف فارغ', 'error')
                return redirect(request.url)
            
            # تنظيف أسماء الأعمدة
            df.columns = [str(col).strip().replace('\n', ' ') for col in df.columns]
            
            # حفظ البيانات في الجلسة مؤقتاً
            session['import_df'] = df.to_json(orient='split')
            session['import_filename'] = secure_filename(file.filename)
            
            # معاينة أول 5 صفوف
            preview = df.head(5).to_html(classes='table table-sm', border=0, index=False)
            
            # الحقول المتاحة في النظام
            system_fields = get_system_fields()
            # أعمدة الملف
            file_columns = df.columns.tolist()
            
            return render_template('employee_import_wizard.html',
                                   preview=preview,
                                   file_columns=file_columns,
                                   system_fields=system_fields,
                                   filename=session['import_filename'])
        except Exception as e:
            flash(f'خطأ في قراءة الملف: {str(e)}', 'error')
            return redirect(request.url)
    
    return render_template('employee_import_upload.html')

@employee_import_bp.route('/preview', methods=['POST'])
def preview_import():
    """معاينة البيانات بعد تعيين الأعمدة (دون حفظ)"""
    if 'import_df' not in session:
        flash('لم يتم رفع أي ملف', 'error')
        return redirect(url_for('employee_import.upload'))
    
    df = pd.read_json(session['import_df'], orient='split')
    
    # قراءة تعيين الأعمدة من النموذج
    mapping = {}
    for key, value in request.form.items():
        if key.startswith('map_'):
            field = key[4:]
            mapping[field] = value if value else None
    
    # تطبيق التعيين على أول 10 صفوف للمعاينة
    preview_data = []
    system_fields = get_system_fields()
    field_names = [f['original'] for f in system_fields]
    
    for idx, row in df.head(10).iterrows():
        row_data = {}
        for field in field_names:
            col = mapping.get(field)
            if col and col in df.columns:
                val = row[col]
                if pd.isna(val):
                    val = ''
                row_data[field] = str(val)
            else:
                row_data[field] = ''
        preview_data.append(row_data)
    
    classifications_dict = get_classifications_dict()
    departments_dict = get_departments_dict()
    
    # التحقق من صحة كل صف (للمعاينة)
    errors = []
    for idx, row in df.iterrows():
        row_num = idx + 2
        emp_code_col = mapping.get('الرقم الوظيفي')
        if not emp_code_col:
            errors.append(f'الصف {row_num}: لم يتم تعيين عمود الرقم الوظيفي')
            continue
        emp_code = str(row[emp_code_col]).strip() if pd.notna(row[emp_code_col]) else ''
        if not emp_code:
            errors.append(f'الصف {row_num}: الرقم الوظيفي فارغ')
            continue
        
        # التحقق من وجود تصنيف صحيح (إذا تم تعيين العمود)
        class_col = mapping.get('تصنيف')
        if class_col and class_col in df.columns:
            class_name = str(row[class_col]).strip() if pd.notna(row[class_col]) else ''
            if class_name and class_name not in classifications_dict:
                errors.append(f'الصف {row_num}: التصنيف "{class_name}" غير موجود في النظام')
        
        # التحقق من وجود قسم صحيح
        dept_col = mapping.get('القسم')
        if dept_col and dept_col in df.columns:
            dept_name = str(row[dept_col]).strip() if pd.notna(row[dept_col]) else ''
            if dept_name and dept_name not in departments_dict:
                errors.append(f'الصف {row_num}: القسم "{dept_name}" غير موجود في النظام')
    
    # حفظ التعيين في الجلسة
    session['import_mapping'] = mapping
    session['import_preview_errors'] = errors[:20]  # أول 20 خطأ
    
    return render_template('employee_import_confirm.html',
                           preview_data=preview_data[:10],
                           field_names=field_names,
                           total_rows=len(df),
                           errors=errors[:20],
                           has_more_errors=len(errors) > 20,
                           filename=session['import_filename'])

@employee_import_bp.route('/confirm', methods=['POST'])
def confirm_import():
    """تأكيد الاستيراد وحفظ البيانات في قاعدة البيانات"""
    if 'import_df' not in session or 'import_mapping' not in session:
        flash('لا توجد بيانات للاستيراد', 'error')
        return redirect(url_for('employee_import.upload'))
    
    df = pd.read_json(session['import_df'], orient='split')
    mapping = session['import_mapping']
    dry_run = 'dry_run' in request.form
    
    db = get_db()
    system_fields = get_system_fields()
    field_col_map = {f['original']: f['column'] for f in system_fields}
    
    classifications_dict = get_classifications_dict()
    departments_dict = get_departments_dict()
    tags_dict = get_tags_dict()
    
    success_count = 0
    update_count = 0
    error_count = 0
    errors = []
    
    # الحصول على عمود الرقم الوظيفي في جدول الموظفين
    key_field = None
    for f in system_fields:
        if f['original'] == 'الرقم الوظيفي':
            key_field = f['column']
            break
    
    for idx, row in df.iterrows():
        row_num = idx + 2
        emp_code_col = mapping.get('الرقم الوظيفي')
        if not emp_code_col:
            errors.append(f'الصف {row_num}: لم يتم تعيين عمود الرقم الوظيفي')
            error_count += 1
            continue
        
        emp_code = str(row[emp_code_col]).strip() if pd.notna(row[emp_code_col]) else ''
        if not emp_code:
            errors.append(f'الصف {row_num}: الرقم الوظيفي فارغ')
            error_count += 1
            continue
        
        # التحقق من وجود الموظف (تحديث أو إضافة)
        existing = db.execute(f'SELECT id FROM employees WHERE {key_field}=?', (emp_code,)).fetchone()
        
        # تجميع القيم الديناميكية
        values = {}
        classification_id = None
        department_name = None
        
        for field in field_col_map:
            col = mapping.get(field)
            if col and col in df.columns:
                val = row[col]
                if pd.isna(val):
                    val = ''
                values[field_col_map[field]] = str(val)
                
                # معالجة خاصة للتصنيف والقسم
                if field == 'تصنيف' and str(val).strip():
                    class_name = str(val).strip()
                    if class_name in classifications_dict:
                        classification_id = classifications_dict[class_name]
                if field == 'القسم' and str(val).strip():
                    department_name = str(val).strip()
        
        if dry_run:
            success_count += 1
            continue
        
        try:
            if existing:
                # تحديث موظف موجود
                emp_id = existing['id']
                for col, val in values.items():
                    db.execute(f'UPDATE employees SET {col}=? WHERE id=?', (val, emp_id))
                db.execute('UPDATE employees SET classification_id=?, last_updated=? WHERE id=?',
                           (classification_id, datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'), emp_id))
                update_count += 1
            else:
                # إضافة موظف جديد
                columns = list(values.keys()) + ['classification_id', 'last_updated']
                vals = list(values.values()) + [classification_id, datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')]
                placeholders = ','.join(['?' for _ in columns])
                cur = db.execute(f'INSERT INTO employees ({",".join(columns)}) VALUES ({placeholders})', vals)
                emp_id = cur.lastrowid
                success_count += 1
            
            # معالجة الوسوم (إذا تم تعيين عمود الوسوم)
            tags_col = mapping.get('tags')
            if tags_col and tags_col in df.columns:
                tags_str = str(row[tags_col]).strip() if pd.notna(row[tags_col]) else ''
                if tags_str:
                    tag_names = [t.strip() for t in tags_str.split(',') if t.strip()]
                    for tag_name in tag_names:
                        if tag_name in tags_dict:
                            tag_id = tags_dict[tag_name]
                        else:
                            # إضافة وسم جديد
                            db.execute('INSERT INTO tags (name) VALUES (?)', (tag_name,))
                            tag_id = db.lastrowid
                            tags_dict[tag_name] = tag_id
                        db.execute('INSERT OR IGNORE INTO employee_tags (employee_id, tag_id) VALUES (?,?)', (emp_id, tag_id))
            
            db.commit()
        except Exception as e:
            db.rollback()
            errors.append(f'الصف {row_num}: خطأ في قاعدة البيانات - {str(e)}')
            error_count += 1
    
    # تنظيف الجلسة
    session.pop('import_df', None)
    session.pop('import_mapping', None)
    session.pop('import_filename', None)
    session.pop('import_preview_errors', None)
    
    flash(f'تم استيراد {success_count} موظف جديد، تحديث {update_count} موظف، فشل {error_count}', 'success')
    for err in errors[:10]:
        flash(err, 'error')
    if len(errors) > 10:
        flash(f'و {len(errors)-10} أخطاء أخرى...', 'warning')
    
    return redirect(url_for('employees.list'))

@employee_import_bp.route('/cancel', methods=['POST'])
def cancel_import():
    session.pop('import_df', None)
    session.pop('import_mapping', None)
    session.pop('import_filename', None)
    session.pop('import_preview_errors', None)
    flash('تم إلغاء عملية الاستيراد', 'info')
    return redirect(url_for('employee_import.upload'))