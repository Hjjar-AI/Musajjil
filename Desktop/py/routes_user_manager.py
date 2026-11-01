# py/routes_user_manager.py
from flask import Blueprint, render_template, request, redirect, url_for, flash, abort, session
from werkzeug.security import generate_password_hash
from .database import get_db

user_manager_bp = Blueprint('user_manager', __name__, url_prefix='/user-manager')

def admin_required():
    if session.get('role') != 'admin':
        abort(403)

@user_manager_bp.route('/')
def list_users():
    admin_required()
    db = get_db()
    users = db.execute('SELECT * FROM users ORDER BY id').fetchall()
    return render_template('user_manager.html', users=users)

@user_manager_bp.route('/add', methods=['POST'])
def add_user():
    admin_required()
    username = request.form['username'].strip()
    password = request.form['password'].strip()
    role = request.form.get('role', 'viewer')
    if username and password:
        db = get_db()
        try:
            db.execute('INSERT INTO users (username, password, role) VALUES (?,?,?)',
                       (username, generate_password_hash(password), role))
            db.commit()
            flash('تم إضافة المستخدم', 'success')
        except:
            flash('اسم المستخدم موجود مسبقاً', 'error')
    return redirect(url_for('user_manager.list_users'))

@user_manager_bp.route('/edit/<int:id>', methods=['POST'])
def edit_user(id):
    admin_required()
    username = request.form['username'].strip()
    role = request.form.get('role', 'viewer')
    password = request.form.get('password', '')
    db = get_db()
    if username:
        if password:
            db.execute('UPDATE users SET username=?, role=?, password=? WHERE id=?',
                       (username, role, generate_password_hash(password), id))
        else:
            db.execute('UPDATE users SET username=?, role=? WHERE id=?',
                       (username, role, id))
        db.commit()
        flash('تم تحديث المستخدم', 'success')
    return redirect(url_for('user_manager.list_users'))

@user_manager_bp.route('/delete/<int:id>', methods=['POST'])
def delete_user(id):
    admin_required()
    db = get_db()
    db.execute('DELETE FROM users WHERE id=?', (id,))
    db.commit()
    flash('تم حذف المستخدم', 'success')
    return redirect(url_for('user_manager.list_users'))