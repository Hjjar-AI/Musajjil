# py/maintenance_backup.py
import os
import glob
import time
import sqlite3
import shutil
import threading
import datetime
import logging
from flask import Blueprint, render_template, request, redirect, url_for, flash, session, jsonify, send_file
from werkzeug.security import check_password_hash
from .config import BACKUP_DIR, DATABASE, MAX_BACKUP_KEEP, AUTO_BACKUP_DAYS
from .database import get_db

maintenance_bp = Blueprint('maintenance', __name__)
_shutdown_event = threading.Event()
logger = logging.getLogger(__name__)

# كشف بيئة PythonAnywhere
IS_PYANYWHERE = 'PYTHONANYWHERE_SITE' in os.environ or 'PYTHONANYWHERE_DOMAIN' in os.environ

def do_backup():
    timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
    backup_name = f'employee_data_backup_{timestamp}.db'
    backup_path = os.path.join(BACKUP_DIR, backup_name)
    try:
        shutil.copy2(DATABASE, backup_path)
        db = sqlite3.connect(DATABASE)
        db.execute('INSERT INTO backup_log (backup_date, filename) VALUES (?,?)',
                   (datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'), backup_name))
        db.commit()
        db.close()
        backups = sorted(glob.glob(os.path.join(BACKUP_DIR, 'employee_data_backup_*.db')))
        while len(backups) > MAX_BACKUP_KEEP:
            oldest = backups.pop(0)
            os.remove(oldest)
        logger.info(f"Backup created: {backup_name}")
    except Exception as e:
        logger.error(f"Backup failed: {e}")

def backup_thread():
    while not _shutdown_event.wait(AUTO_BACKUP_DAYS * 24 * 3600):
        if not IS_PYANYWHERE:   # لا تشغل الخيط على PythonAnywhere
            try:
                do_backup()
            except Exception:
                pass

def shutdown_backup_thread():
    _shutdown_event.set()

@maintenance_bp.route('/backup', methods=['POST'])
def manual_backup():
    do_backup()
    flash('تم إنشاء نسخة احتياطية بنجاح', 'success')
    return redirect(url_for('dashboard.dashboard'))

@maintenance_bp.route('/backups')
def list_backups():
    backups = sorted(glob.glob(os.path.join(BACKUP_DIR, 'employee_data_backup_*.db')))
    backup_info = []
    for path in backups:
        filename = os.path.basename(path)
        size = os.path.getsize(path)
        mtime = datetime.datetime.fromtimestamp(os.path.getmtime(path))
        backup_info.append({
            'filename': filename,
            'size': size,
            'date': mtime.strftime('%Y-%m-%d %H:%M:%S'),
            'path': path
        })
    return render_template('backup_list.html', backups=backup_info)

@maintenance_bp.route('/backups/restore', methods=['POST'])
def restore_backup():
    filename = request.form.get('filename')
    if not filename:
        flash('لم يتم تحديد ملف', 'error')
        return redirect(url_for('maintenance.list_backups'))
    backup_path = os.path.join(BACKUP_DIR, filename)
    if not os.path.exists(backup_path):
        flash('ملف النسخة الاحتياطية غير موجود', 'error')
        return redirect(url_for('maintenance.list_backups'))
    try:
        shutil.copy2(backup_path, DATABASE)
        flash(f'تم استعادة قاعدة البيانات من النسخة {filename}', 'success')
    except Exception as e:
        flash(f'فشل الاستعادة: {e}', 'error')
    return redirect(url_for('maintenance.list_backups'))