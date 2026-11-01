import os
from .database import get_db

def import_config_from_files():
    db = get_db()

    if os.path.exists('classifications.txt'):
        with open('classifications.txt', 'r', encoding='utf-8') as f:
            class_names = [line.strip() for line in f if line.strip()]
        for cname in class_names:
            db.execute('INSERT OR IGNORE INTO classifications (name) VALUES (?)', (cname,))
            row = db.execute('SELECT id FROM classifications WHERE name = ?', (cname,)).fetchone()
            if row is None:
                continue
            cid = row['id']
            ent_file = f'entitlements_{cname}.txt'
            if os.path.exists(ent_file):
                with open(ent_file, 'r', encoding='utf-8') as ef:
                    for line in ef:
                        if ':' in line:
                            vt_name, days_str = line.split(':', 1)
                            vt_name = vt_name.strip()
                            try:
                                days = float(days_str.strip())
                            except ValueError:
                                continue
                            vt_row = db.execute('SELECT id FROM vacation_types WHERE name = ?', (vt_name,)).fetchone()
                            if vt_row:
                                vt_id = vt_row['id']
                                db.execute('INSERT OR IGNORE INTO classification_entitlements (classification_id, vacation_type_id, default_days) VALUES (?,?,?)',
                                           (cid, vt_id, days))

    if os.path.exists('evaluation_criteria.txt'):
        with open('evaluation_criteria.txt', 'r', encoding='utf-8') as ef:
            criteria_global = [line.strip() for line in ef if line.strip()]
        for crit in criteria_global:
            db.execute('INSERT OR IGNORE INTO evaluation_criteria (name, classification_id) VALUES (?, NULL)', (crit,))

    if os.path.exists('classifications.txt'):
        with open('classifications.txt', 'r', encoding='utf-8') as f:
            class_names = [line.strip() for line in f if line.strip()]
        for cname in class_names:
            cid_row = db.execute('SELECT id FROM classifications WHERE name = ?', (cname,)).fetchone()
            if cid_row is None:
                continue
            cid = cid_row['id']
            crit_file = f'evaluation_criteria_{cname}.txt'
            if os.path.exists(crit_file):
                with open(crit_file, 'r', encoding='utf-8') as ef:
                    criteria_list = [line.strip() for line in ef if line.strip()]
                for crit in criteria_list:
                    db.execute('INSERT OR IGNORE INTO evaluation_criteria (name, classification_id) VALUES (?, ?)', (crit, cid))

    db.commit()