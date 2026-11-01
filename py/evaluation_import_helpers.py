# py/evaluation_import_helpers.py
import io
import csv
import datetime
import pandas as pd
from flask import flash
from .database import get_db
from .employee_helpers import get_dynamic_fields

ALLOWED_EXTENSIONS = {'xlsx', 'xls', 'csv'}

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def detect_file_type(file):
    filename = file.filename.lower()
    if filename.endswith('.csv'):
        return 'csv'
    return 'excel'

def read_file_data(file, file_type):
    """قراءة البيانات من ملف Excel أو CSV وإرجاع DataFrame مع تطبيع الترويسات."""
    if file_type == 'csv':
        # قراءة CSV مع ترميز متعدد المحاولات
        content = file.read()
        try:
            df = pd.read_csv(io.BytesIO(content), encoding='utf-8')
        except UnicodeDecodeError:
            try:
                df = pd.read_csv(io.BytesIO(content), encoding='cp1256')
            except:
                df = pd.read_csv(io.BytesIO(content), encoding='iso-8859-6')
        file.seek(0)
    else:
        df = pd.read_excel(file)
        file.seek(0)
    # تنظيف أسماء الأعمدة: إزالة المسافات الزائدة، توحيد الترميز
    df.columns = [str(col).strip().replace('\n', ' ').replace('\r', '') for col in df.columns]
    return df

def get_available_criteria(db, classification_id=None):
    """إرجاع قائمة المعايير المتاحة (عامة أو حسب التصنيف)."""
    if classification_id:
        criteria = db.execute('''
            SELECT id, name FROM evaluation_criteria 
            WHERE classification_id = ? OR classification_id IS NULL
        ''', (classification_id,)).fetchall()
    else:
        criteria = db.execute('SELECT id, name FROM evaluation_criteria WHERE classification_id IS NULL').fetchall()
    return {c['name']: c['id'] for c in criteria}

def get_employee_key_field():
    """إرجاع اسم عمود الرقم الوظيفي في جدول الموظفين."""
    fields = get_dynamic_fields()
    for f in fields:
        if f['original_title'] == 'الرقم الوظيفي':
            return f['column_name']
    return None

def find_column(df, possible_names):
    """البحث عن عمود في DataFrame بأحد الأسماء الممكنة."""
    for name in possible_names:
        for col in df.columns:
            if col.strip() == name or name in col:
                return col
    return None

class ImportResult:
    def __init__(self):
        self.total_rows = 0
        self.success_count = 0
        self.error_count = 0
        self.errors = []  # قائمة tuples (row_num, error_message)
        self.details = []  # تفاصيل كل عملية استيراد (اختياري)

    def add_error(self, row_num, message):
        self.error_count += 1
        self.errors.append((row_num, message))

    def add_success(self):
        self.success_count += 1

    def get_summary(self):
        return {
            'total': self.total_rows,
            'success': self.success_count,
            'errors': self.error_count,
            'error_list': self.errors[:50]  # حد أقصى 50 خطأ للعرض
        }