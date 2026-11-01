from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db

classifications_bp = Blueprint('classifications', __name__)

@classifications_bp.route('/classifications')
def list():
    db = get_db()
    classes = db.execute('SELECT * FROM classifications').fetchall()
    return render_template('classifications_list.html', classes=classes)

@classifications_bp.route('/classifications/add', methods=['POST'])
def add():
    name = request.form['name'].strip()
    if name:
        db = get_db()
        try:
            db.execute('INSERT INTO classifications (name) VALUES (?)', (name,))
            db.commit()
            flash('تم إضافة التصنيف', 'success')
        except:
            flash('التصنيف موجود مسبقاً', 'error')
    return redirect(url_for('classifications.list'))

@classifications_bp.route('/classifications/<int:id>/delete', methods=['POST'])
def delete(id):
    db = get_db()
    db.execute('DELETE FROM classifications WHERE id=?', (id,))
    db.commit()
    flash('تم حذف التصنيف', 'success')
    return redirect(url_for('classifications.list'))