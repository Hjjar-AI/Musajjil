# py/routes_tags.py
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db
from .employee_helpers import get_name_column

tags_bp = Blueprint('tags', __name__, url_prefix='/tags')

@tags_bp.route('/')
def list():
    db = get_db()
    tags = db.execute('SELECT * FROM tags ORDER BY name').fetchall()
    name_col = get_name_column()
    all_employees = []
    dept_col = None
    for f in db.execute('SELECT original_title, column_name FROM employee_fields').fetchall():
        if f['original_title'] == 'القسم':
            dept_col = f['column_name']
            break
    if name_col:
        rows = db.execute(f'''
            SELECT e.id, {name_col} AS name
            FROM employees e
            WHERE e.deleted_at IS NULL
            ORDER BY {name_col}
        ''').fetchall()
        for row in rows:
            emp = dict(row)
            if dept_col:
                dept = db.execute(f'SELECT {dept_col} FROM employees WHERE id=?', (emp['id'],)).fetchone()
                emp['department'] = dept[0] if dept else ''
            else:
                emp['department'] = ''
            all_employees.append(emp)
    tag_list = []
    for tag_row in tags:
        tag = dict(tag_row)
        employees = []
        if name_col:
            rows = db.execute(f'''
                SELECT e.id, {name_col} AS name
                FROM employee_tags et
                JOIN employees e ON et.employee_id = e.id
                WHERE et.tag_id = ? AND e.deleted_at IS NULL
                ORDER BY {name_col}
            ''', (tag['id'],)).fetchall()
            for row in rows:
                emp = dict(row)
                if dept_col:
                    dept = db.execute(f'SELECT {dept_col} FROM employees WHERE id=?', (emp['id'],)).fetchone()
                    emp['department'] = dept[0] if dept else ''
                else:
                    emp['department'] = ''
                employees.append(emp)
        else:
            rows = db.execute('''
                SELECT e.id, e.id AS name
                FROM employee_tags et
                JOIN employees e ON et.employee_id = e.id
                WHERE et.tag_id = ? AND e.deleted_at IS NULL
            ''', (tag['id'],)).fetchall()
            for row in rows:
                emp = dict(row)
                emp['department'] = ''
                employees.append(emp)
        tag['employees'] = employees
        tag['employee_count'] = len(employees)
        tag_list.append(tag)
    return render_template('tags_list.html', tags=tag_list, all_employees=all_employees)

@tags_bp.route('/add', methods=['POST'])
def add():
    name = request.form['name'].strip()
    if name:
        db = get_db()
        try:
            db.execute('INSERT INTO tags (name) VALUES (?)', (name,))
            db.commit()
            flash('تم إضافة الوسم', 'success')
        except:
            flash('الوسم موجود مسبقاً', 'error')
    else:
        flash('اسم الوسم مطلوب', 'error')
    return redirect(url_for('tags.list'))

@tags_bp.route('/assign/<int:tag_id>', methods=['POST'])
def assign_to_employee(tag_id):
    employee_id = request.form.get('employee_id', type=int)
    if not employee_id:
        flash('يرجى اختيار موظف', 'error')
        return redirect(url_for('tags.list'))
    db = get_db()
    try:
        db.execute('INSERT OR IGNORE INTO employee_tags (employee_id, tag_id) VALUES (?,?)', (employee_id, tag_id))
        db.commit()
        flash('تم إضافة الوسم للموظف', 'success')
    except Exception as e:
        flash(f'فشل إضافة الوسم للموظف: {e}', 'error')
    return redirect(url_for('tags.list'))

@tags_bp.route('/remove/<int:tag_id>/<int:employee_id>', methods=['POST'])
def remove_from_employee(tag_id, employee_id):
    db = get_db()
    try:
        db.execute('DELETE FROM employee_tags WHERE employee_id=? AND tag_id=?', (employee_id, tag_id))
        db.commit()
        flash('تم إزالة الوسم من الموظف', 'success')
    except Exception as e:
        flash(f'فشل إزالة الوسم: {e}', 'error')
    return redirect(url_for('tags.list'))

@tags_bp.route('/<int:id>/delete', methods=['POST'])
def delete(id):
    db = get_db()
    try:
        db.execute('DELETE FROM tags WHERE id=?', (id,))
        db.commit()
        flash('تم حذف الوسم', 'success')
    except Exception as e:
        flash(f'فشل حذف الوسم: {e}', 'error')
    return redirect(url_for('tags.list'))