import json
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db

eval_criteria_bp = Blueprint('eval_criteria', __name__, url_prefix='/evaluation-criteria')

@eval_criteria_bp.route('/')
def list():
    db = get_db()
    rows = db.execute('''
        SELECT ec.*, c.name AS classification_name
        FROM evaluation_criteria ec
        LEFT JOIN classifications c ON ec.classification_id = c.id
        ORDER BY ec.classification_id, ec.id
    ''').fetchall()
    criteria = []
    for r in rows:
        crit = dict(r)
        if crit['descriptions']:
            try:
                crit['descriptions'] = json.loads(crit['descriptions'])
            except:
                crit['descriptions'] = {}
        else:
            crit['descriptions'] = {}
        criteria.append(crit)
    classifications = db.execute('SELECT * FROM classifications').fetchall()
    return render_template('evaluation_criteria_list.html', criteria=criteria, classifications=classifications)

@eval_criteria_bp.route('/add', methods=['POST'])
def add():
    name = request.form['name'].strip()
    classification_id = request.form.get('classification_id')
    if classification_id == '':
        classification_id = None
    else:
        classification_id = int(classification_id)
    if name:
        db = get_db()
        try:
            db.execute('INSERT INTO evaluation_criteria (name, classification_id) VALUES (?, ?)',
                       (name, classification_id))
            db.commit()
            flash('تمت إضافة المعيار', 'success')
        except:
            flash('المعيار موجود مسبقاً', 'error')
    return redirect(url_for('eval_criteria.list'))

@eval_criteria_bp.route('/<int:id>/edit', methods=['GET', 'POST'])
def edit(id):
    db = get_db()
    crit_row = db.execute('SELECT * FROM evaluation_criteria WHERE id=?', (id,)).fetchone()
    if not crit_row:
        flash('المعيار غير موجود', 'error')
        return redirect(url_for('eval_criteria.list'))
    crit = dict(crit_row)
    if request.method == 'POST':
        name = request.form['name'].strip()
        desc = {}
        for i in range(1, 6):
            val = request.form.get(f'desc_{i}', '').strip()
            if val:
                desc[str(i)] = val
        descriptions_json = json.dumps(desc, ensure_ascii=False)
        db.execute('UPDATE evaluation_criteria SET name=?, descriptions=? WHERE id=?',
                   (name, descriptions_json, id))
        db.commit()
        flash('تم تحديث المعيار', 'success')
        return redirect(url_for('eval_criteria.list'))

    descriptions = {}
    if crit['descriptions']:
        try:
            descriptions = json.loads(crit['descriptions'])
        except:
            descriptions = {}
    return render_template('evaluation_criteria_edit.html', crit=crit, descriptions=descriptions)

@eval_criteria_bp.route('/<int:id>/delete', methods=['POST'])
def delete(id):
    db = get_db()
    db.execute('DELETE FROM evaluation_criteria WHERE id=?', (id,))
    db.commit()
    flash('تم حذف المعيار', 'success')
    return redirect(url_for('eval_criteria.list'))