from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db

vacation_types_bp = Blueprint('vacation_types', __name__)

@vacation_types_bp.route('/vacation-types')
def list():
    db = get_db()
    vtypes = db.execute('SELECT * FROM vacation_types').fetchall()
    return render_template('vacation_types_list.html', vtypes=vtypes)

@vacation_types_bp.route('/vacation-types/add', methods=['POST'])
def add():
    name = request.form['name'].strip()
    hours_per_day = request.form.get('hours_per_day', 8, type=float)
    if name:
        db = get_db()
        try:
            db.execute('INSERT INTO vacation_types (name, hours_per_day) VALUES (?, ?)', (name, hours_per_day))
            db.commit()
            flash('تم إضافة نوع الإجازة', 'success')
        except:
            flash('نوع الإجازة موجود مسبقاً', 'error')
    return redirect(url_for('vacation_types.list'))

@vacation_types_bp.route('/vacation-types/<int:id>/edit', methods=['POST'])
def edit(id):
    db = get_db()
    is_yearly = 1 if request.form.get('is_yearly') == '1' else 0
    hours_per_day = request.form.get('hours_per_day', 8, type=float)
    name = request.form.get('name', '').strip()
    if name:
        db.execute('UPDATE vacation_types SET name=?, hours_per_day=?, is_yearly=? WHERE id=?',
                   (name, hours_per_day, is_yearly, id))
        db.commit()
        flash('تم تحديث نوع الإجازة', 'success')
    return redirect(url_for('vacation_types.list'))

@vacation_types_bp.route('/vacation-types/<int:id>/delete', methods=['POST'])
def delete(id):
    db = get_db()
    db.execute('DELETE FROM vacation_types WHERE id=?', (id,))
    db.commit()
    flash('تم حذف نوع الإجازة', 'success')
    return redirect(url_for('vacation_types.list'))