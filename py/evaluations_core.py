# py/evaluations_core.py
import json
import datetime
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db
from .employee_helpers import get_classification_id, get_name_column

evaluations_bp = Blueprint('evaluations', __name__)

# Removed the /home route – no longer needed
# Redirect /home to general evaluations (for backward compatibility)
@evaluations_bp.route('/home')
def home():
    return redirect(url_for('evaluations.general'))

@evaluations_bp.route('/start')
def start():
    return redirect(url_for('evaluations.general'))

@evaluations_bp.route('/general')
def general():
    db = get_db()
    tag_id = request.args.get('tag', type=int)
    classification_id = request.args.get('classification', type=int)
    query = 'SELECT e.id,'
    name_col = get_name_column()
    if name_col:
        query += f' e.{name_col} AS name,'
    query += ' c.name AS classification_name FROM employees e LEFT JOIN classifications c ON e.classification_id = c.id WHERE e.deleted_at IS NULL'
    params = []
    if tag_id:
        query += ' AND e.id IN (SELECT employee_id FROM employee_tags WHERE tag_id=?)'
        params.append(tag_id)
    if classification_id:
        query += ' AND e.classification_id = ?'
        params.append(classification_id)
    employees = db.execute(query, params).fetchall()
    tags = db.execute('SELECT * FROM tags').fetchall()
    classifications = db.execute('SELECT * FROM classifications').fetchall()
    return render_template('evaluations_start.html',
                           employees=employees,
                           tags=tags,
                           classifications=classifications,
                           selected_tag=tag_id,
                           selected_classification=classification_id)

@evaluations_bp.route('/employees/<int:id>/evaluations')
def list(id):
    db = get_db()
    emp = db.execute('SELECT * FROM employees WHERE id=? AND deleted_at IS NULL', (id,)).fetchone()
    if not emp:
        flash('الموظف غير موجود', 'error')
        return redirect(url_for('employees.list'))
    evals = db.execute('SELECT * FROM evaluations WHERE employee_id=? ORDER BY evaluation_date DESC', (id,)).fetchall()
    eval_data = []
    for ev in evals:
        scores = db.execute('SELECT ec.name, es.score FROM evaluation_scores es JOIN evaluation_criteria ec ON es.criterion_id=ec.id WHERE es.evaluation_id=?', (ev['id'],)).fetchall()
        criteria_names = [s['name'] for s in scores]
        score_vals = [s['score'] for s in scores]
        eval_data.append({'evaluation': ev, 'criteria': criteria_names, 'scores': score_vals})
    class_name = ''
    if emp['classification_id']:
        cls = db.execute('SELECT name FROM classifications WHERE id=?', (emp['classification_id'],)).fetchone()
        if cls:
            class_name = cls['name']
    return render_template('evaluations_list.html', employee_id=id, eval_data=eval_data, class_name=class_name)

@evaluations_bp.route('/employees/<int:id>/evaluations/add', methods=['GET', 'POST'])
def add(id):
    db = get_db()
    emp = db.execute('SELECT * FROM employees WHERE id=? AND deleted_at IS NULL', (id,)).fetchone()
    if not emp:
        flash('الموظف غير موجود', 'error')
        return redirect(url_for('employees.list'))
    cid = get_classification_id(id)
    criteria = []
    if cid is not None:
        criteria = db.execute('SELECT * FROM evaluation_criteria WHERE classification_id=?', (cid,)).fetchall()
    if not criteria:
        criteria = db.execute('SELECT * FROM evaluation_criteria WHERE classification_id IS NULL').fetchall()
    crit_list = []
    for c in criteria:
        crit = dict(c)
        if crit['descriptions']:
            try:
                crit['descriptions'] = json.loads(crit['descriptions'])
            except:
                crit['descriptions'] = {}
        else:
            crit['descriptions'] = {}
        crit_list.append(crit)
    if request.method == 'POST':
        evaluator = request.form['evaluator_name']
        eval_date = request.form['evaluation_date']
        notes = request.form.get('notes', '')
        cur = db.execute('INSERT INTO evaluations (employee_id, evaluator_name, evaluation_date, notes) VALUES (?,?,?,?)',
                         (id, evaluator, eval_date, notes))
        eval_id = cur.lastrowid
        for crit in crit_list:
            score = request.form.get(f'score_{crit["id"]}', type=float)
            if score is not None:
                score = max(1.0, min(5.0, score))
                db.execute('INSERT INTO evaluation_scores (evaluation_id, criterion_id, score) VALUES (?,?,?)',
                           (eval_id, crit['id'], score))
        db.commit()
        flash('تم إضافة التقييم بنجاح', 'success')
        return redirect(url_for('evaluations.list', id=id))
    return render_template('evaluation_form.html', employee_id=id, criteria=crit_list)

@evaluations_bp.route('/employees/<int:id>/evaluations/<int:eval_id>/delete', methods=['POST'])
def delete(id, eval_id):
    db = get_db()
    db.execute('DELETE FROM evaluations WHERE id=? AND employee_id=?', (eval_id, id))
    db.commit()
    flash('تم حذف التقييم', 'success')
    return redirect(url_for('evaluations.list', id=id))