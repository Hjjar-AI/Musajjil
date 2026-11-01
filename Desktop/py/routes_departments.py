# py/routes_departments.py
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db

departments_bp = Blueprint('departments', __name__, url_prefix='/departments')

@departments_bp.route('/')
def list_departments():
    db = get_db()
    depts = db.execute('SELECT * FROM departments ORDER BY name').fetchall()
    return render_template('departments.html', departments=depts)

@departments_bp.route('/add', methods=['POST'])
def add():
    name = request.form.get('name', '').strip()
    if name:
        db = get_db()
        try:
            db.execute('INSERT INTO departments (name) VALUES (?)', (name,))
            db.commit()
            flash('تم إضافة القسم', 'success')
        except:
            flash('القسم موجود مسبقاً', 'error')
    return redirect(url_for('departments.list_departments'))

@departments_bp.route('/edit/<int:id>', methods=['POST'])
def edit(id):
    name = request.form.get('name', '').strip()
    if name:
        db = get_db()
        db.execute('UPDATE departments SET name=? WHERE id=?', (name, id))
        db.commit()
        flash('تم تحديث القسم', 'success')
    return redirect(url_for('departments.list_departments'))

@departments_bp.route('/delete/<int:id>', methods=['POST'])
def delete(id):
    db = get_db()
    db.execute('DELETE FROM departments WHERE id=?', (id,))
    db.commit()
    flash('تم حذف القسم', 'success')
    return redirect(url_for('departments.list_departments'))