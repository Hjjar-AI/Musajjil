# py/routes_setup.py
from flask import Blueprint, render_template, request, redirect, url_for, flash
from werkzeug.security import generate_password_hash
from .database import get_db

setup_bp = Blueprint('setup', __name__, url_prefix='/setup')

@setup_bp.route('/', methods=['GET', 'POST'])
def wizard():
    db = get_db()
    if db.execute('SELECT COUNT(*) FROM classifications').fetchone()[0] > 0:
        flash('الإعداد الأولي مكتمل بالفعل', 'info')
        return redirect(url_for('dashboard.dashboard'))

    if request.method == 'POST':
        classification_names = request.form.getlist('classification_name')
        for i, cname in enumerate(classification_names):
            cname = cname.strip()
            if not cname:
                continue
            db.execute('INSERT OR IGNORE INTO classifications (name) VALUES (?)', (cname,))
            cid = db.execute('SELECT id FROM classifications WHERE name=?', (cname,)).fetchone()['id']
            for vt in db.execute('SELECT id, name FROM vacation_types WHERE name NOT IN ("تأخير (3 ساعات)", "إجازة ساعية")').fetchall():
                days = request.form.get(f'ent_{i}_{vt["id"]}', type=float)
                if days is not None:
                    db.execute('INSERT OR REPLACE INTO classification_entitlements (classification_id, vacation_type_id, default_days) VALUES (?,?,?)',
                               (cid, vt['id'], days))

        general_criteria = request.form.getlist('general_criteria')
        for crit in general_criteria:
            crit = crit.strip()
            if crit:
                db.execute('INSERT OR IGNORE INTO evaluation_criteria (name, classification_id) VALUES (?, NULL)', (crit,))

        # Ensure at least one admin user exists
        admin_exists = db.execute("SELECT COUNT(*) FROM users WHERE role='admin'").fetchone()[0]
        if not admin_exists:
            admin_password = request.form.get('admin_password', '').strip()
            if not admin_password:
                admin_password = 'admin123'
                flash('تم إنشاء مدير بكلمة مرور افتراضية: admin123. يرجى تغييرها فوراً.', 'warning')
            db.execute("INSERT INTO users (username, password, role, must_change_password) VALUES (?, ?, ?, ?)",
                       ('admin', generate_password_hash(admin_password), 'admin', 1))

        db.commit()
        flash('تم إتمام الإعداد الأولي بنجاح', 'success')
        return redirect(url_for('dashboard.dashboard'))

    vac_types = db.execute('SELECT * FROM vacation_types WHERE name NOT IN ("تأخير (3 ساعات)", "إجازة ساعية")').fetchall()
    return render_template('setup_wizard.html', vac_types=vac_types, show_admin_field=True)