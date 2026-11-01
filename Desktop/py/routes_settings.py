from flask import Blueprint, redirect, url_for, flash
from .database_import import import_config_from_files

settings_bp = Blueprint('settings', __name__, url_prefix='/settings')

@settings_bp.route('/import', methods=['POST'])
def import_data():
    try:
        import_config_from_files()
        flash('تم استيراد البيانات من الملفات بنجاح', 'success')
    except Exception as e:
        flash(f'فشل الاستيراد: {e}', 'error')
    return redirect(url_for('dashboard.dashboard'))