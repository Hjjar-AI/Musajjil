# py/import_employees.py
import io
import csv
import json
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db
from .employee_helpers import get_dynamic_fields
from .export_employees import export_import_bp

def import_employee_row(db, row_dict, key_title, key_col, fields):
    key_val = row_dict.get(key_title, '').strip() if key_title else None
    existing = None
    if key_val:
        existing = db.execute(f'SELECT id FROM employees WHERE {key_col}=?', (key_val,)).fetchone()
    vals = {}
    for f in fields:
        col = f['column_name']
        title = f['original_title']
        vals[col] = row_dict.get(title, '')
    class_name = row_dict.get('تصنيف', row_dict.get('classification', '')).strip()
    classification_id = None
    if class_name:
        cls = db.execute('SELECT id FROM classifications WHERE name=?', (class_name,)).fetchone()
        if cls:
            classification_id = cls['id']
    extra = row_dict.get('extra_data', '{}')
    target = float(row_dict.get('target_service_days', 0))
    if existing:
        emp_id = existing['id']
        for col, val in vals.items():
            db.execute(f'UPDATE employees SET {col}=? WHERE id=?', (val, emp_id))
        db.execute('UPDATE employees SET classification_id=?, extra_data=?, target_service_days=? WHERE id=?',
                   (classification_id, extra, target, emp_id))
        db.execute('DELETE FROM employee_tags WHERE employee_id=?', (emp_id,))
        tags = row_dict.get('tags', '')
        if isinstance(tags, str):
            tags = [t.strip() for t in tags.split(',') if t.strip()]
        for tag_name in tags:
            tag_row = db.execute('SELECT id FROM tags WHERE name=?', (tag_name,)).fetchone()
            if not tag_row:
                try:
                    db.execute('INSERT INTO tags (name) VALUES (?)', (tag_name,))
                    tag_id = db.lastrowid
                except:
                    continue
            else:
                tag_id = tag_row['id']
            db.execute('INSERT OR IGNORE INTO employee_tags (employee_id, tag_id) VALUES (?,?)', (emp_id, tag_id))
        return emp_id
    else:
        columns = list(vals.keys()) + ['classification_id', 'extra_data', 'target_service_days']
        values = list(vals.values()) + [classification_id, extra, target]
        cur = db.execute(f'INSERT INTO employees ({",".join(columns)}) VALUES ({",".join("?"*len(columns))})', values)
        emp_id = cur.lastrowid
        tags = row_dict.get('tags', '')
        if isinstance(tags, str):
            tags = [t.strip() for t in tags.split(',') if t.strip()]
        for tag_name in tags:
            tag_row = db.execute('SELECT id FROM tags WHERE name=?', (tag_name,)).fetchone()
            if not tag_row:
                try:
                    db.execute('INSERT INTO tags (name) VALUES (?)', (tag_name,))
                    tag_id = db.lastrowid
                except:
                    continue
            else:
                tag_id = tag_row['id']
            db.execute('INSERT OR IGNORE INTO employee_tags (employee_id, tag_id) VALUES (?,?)', (emp_id, tag_id))
        return emp_id

@export_import_bp.route('/import/employees', methods=['GET', 'POST'])
def import_employees():
    if request.method == 'POST':
        file = request.files['file']
        if not file:
            flash('الرجاء اختيار ملف', 'error')
            return render_template('import_employees_form.html')
        filename = file.filename.lower()
        db = get_db()
        fields = get_dynamic_fields()
        key_title = fields[0]['original_title'] if fields else None
        key_col = fields[0]['column_name'] if fields else None
        try:
            if filename.endswith('.csv'):
                stream = io.StringIO(file.stream.read().decode('utf-8-sig'))
                reader = csv.DictReader(stream)
                for row in reader:
                    import_employee_row(db, row, key_title, key_col, fields)
            elif filename.endswith('.json'):
                data = json.load(file)
                if not isinstance(data, list):
                    data = [data]
                for item in data:
                    import_employee_row(db, item, key_title, key_col, fields)
            else:
                flash('تنسيق الملف غير مدعوم', 'error')
                return render_template('import_employees_form.html')
            db.commit()
        except Exception as e:
            flash(f'حدث خطأ أثناء الاستيراد: {e}', 'error')
            return render_template('import_employees_form.html')
        flash('تم استيراد الموظفين بنجاح', 'success')
        return redirect(url_for('employees.list'))
    return render_template('import_employees_form.html')