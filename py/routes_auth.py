# py/routes_auth.py
from flask import Blueprint, render_template, request, redirect, url_for, flash, session
from werkzeug.security import check_password_hash, generate_password_hash
from .database import get_db

auth_bp = Blueprint('auth', __name__)

@auth_bp.route('/login', methods=['GET', 'POST'])
def login():
    # If already logged in, redirect to dashboard
    if 'user_id' in session:
        return redirect(url_for('dashboard.dashboard'))

    if request.method == 'POST':
        username = request.form['username'].strip()
        password = request.form['password'].strip()
        db = get_db()
        user = db.execute('SELECT * FROM users WHERE username=?', (username,)).fetchone()
        if user and check_password_hash(user['password'], password):
            session['user_id'] = user['id']
            session['username'] = user['username']
            session['role'] = user['role']
            if user['must_change_password']:
                flash('يرجى تغيير كلمة المرور الخاصة بك.', 'warning')
                return redirect(url_for('auth.change_password'))
            flash('تم تسجيل الدخول بنجاح', 'success')
            return redirect(url_for('dashboard.dashboard'))
        else:
            flash('اسم المستخدم أو كلمة المرور غير صحيحة', 'error')
    return render_template('login.html')

@auth_bp.route('/logout')
def logout():
    session.clear()
    flash('تم تسجيل الخروج', 'success')
    return redirect(url_for('auth.login'))

@auth_bp.route('/change_password', methods=['GET', 'POST'])
def change_password():
    if 'user_id' not in session:
        return redirect(url_for('auth.login'))

    if request.method == 'POST':
        old_password = request.form['old_password'].strip()
        new_password = request.form['new_password'].strip()
        confirm = request.form['confirm_password'].strip()
        if new_password != confirm:
            flash('كلمة المرور الجديدة غير متطابقة', 'error')
        else:
            db = get_db()
            user = db.execute('SELECT * FROM users WHERE id=?', (session['user_id'],)).fetchone()
            if not user or not check_password_hash(user['password'], old_password):
                flash('كلمة المرور الحالية غير صحيحة', 'error')
            else:
                db.execute('UPDATE users SET password=?, must_change_password=0 WHERE id=?',
                           (generate_password_hash(new_password), session['user_id']))
                db.commit()
                flash('تم تغيير كلمة المرور بنجاح', 'success')
                return redirect(url_for('dashboard.dashboard'))
    return render_template('change_password.html')