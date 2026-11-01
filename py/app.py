import atexit
import datetime
import threading
import os
import sys
import logging
import uuid
from datetime import timedelta
from logging.handlers import RotatingFileHandler
from flask import Flask, session, redirect, url_for, flash, request, g
from flask_wtf.csrf import CSRFProtect
from flask_jwt_extended import JWTManager
from werkzeug.middleware.proxy_fix import ProxyFix

from .config import (
    PORT, SECRET_KEY, MAX_CONTENT_LENGTH, SESSION_COOKIE_SECURE,
    SESSION_COOKIE_HTTPONLY, SESSION_COOKIE_SAMESITE, JWT_SECRET_KEY,
    PREFERRED_URL_SCHEME, HTTPS_ENABLED, IS_PYTHONANYWHERE,
    WTF_CSRF_TIME_LIMIT, JWT_ACCESS_TOKEN_EXPIRES_MINUTES
)
from .radar_chart import generate_radar_chart
from .employee_helpers import breakdown_days_to_ym, get_dynamic_fields, get_name_column, get_search_columns
from .notification_helpers import get_global_notifications
from .role_permissions import is_endpoint_allowed

if getattr(sys, 'frozen', False):
    BASE_DIR = sys._MEIPASS
else:
    BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

if not os.path.exists('logs'):
    os.mkdir('logs')
file_handler = RotatingFileHandler('logs/employee_system.log', maxBytes=5*1024*1024, backupCount=3)
file_handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s: %(message)s [in %(pathname)s:%(lineno)d]'))

if IS_PYTHONANYWHERE:
    file_handler.setLevel(logging.WARNING)
    app_log_level = logging.WARNING
else:
    file_handler.setLevel(logging.INFO)
    app_log_level = logging.INFO

# ------------------------------------------------------------------
# Import all blueprints
# ------------------------------------------------------------------
from .employees_core import employees_bp
from .employees_list import employees_bp
from .employees_detail import employees_bp
from .employees_manage import employees_bp
from .employees_leave import employees_bp
from .employees_entitlements import employees_bp
from .employees_bulk import employees_bp

from .evaluations_core import evaluations_bp
from .evaluations_import import evaluations_bp

from .reports_individual import reports_bp

from .dashboard_core import dashboard_bp

from .calendar_core import calendar_bp
from .calendar_team import calendar_bp

from .maintenance_backup import maintenance_bp

from .export_employees import export_import_bp
from .export_reports import export_import_bp
from .import_employees import export_import_bp
from .import_full import export_import_bp

from .routes_tags import tags_bp
from .routes_classifications import classifications_bp
from .routes_vacation_types import vacation_types_bp
from .routes_auth import auth_bp
from .routes_holidays import holidays_bp
from .routes_excel import excel_bp
from .routes_settings import settings_bp
from .routes_evaluation_criteria import eval_criteria_bp
from .routes_setup import setup_bp
from .routes_bulk_leave import bulk_leave_bp
from .routes_settings_page import settings_page_bp
from .routes_manual import manual_bp
from .routes_user_manager import user_manager_bp
from .routes_departments import departments_bp
from .routes_smart_holidays import smart_holidays_bp
from .wizard_mode import wizard_bp
from .routes_employee_import import employee_import_bp
from .routes_id_card import id_card_bp
from .routes_api import api_bp

# ------------------------------------------------------------------
# Create Flask app
# ------------------------------------------------------------------
app = Flask(__name__,
            template_folder=os.path.join(BASE_DIR, 'templates'),
            static_folder=os.path.join(BASE_DIR, 'static'))

app.logger.addHandler(file_handler)
app.logger.setLevel(app_log_level)
app.logger.info('Employee system startup' + (' (reduced logging on PythonAnywhere)' if IS_PYTHONANYWHERE else ''))

if IS_PYTHONANYWHERE:
    os.environ['DISABLE_3D_DASHBOARD'] = '1'
    app.logger.debug("3D Dashboard disabled on PythonAnywhere.")

# ------------------------------------------------------------------
# Configuration
# ------------------------------------------------------------------
app.secret_key = SECRET_KEY
app.config['MAX_CONTENT_LENGTH'] = MAX_CONTENT_LENGTH
app.config['SESSION_COOKIE_SECURE'] = SESSION_COOKIE_SECURE
app.config['SESSION_COOKIE_HTTPONLY'] = SESSION_COOKIE_HTTPONLY
app.config['SESSION_COOKIE_SAMESITE'] = SESSION_COOKIE_SAMESITE
app.config['WTF_CSRF_TIME_LIMIT'] = WTF_CSRF_TIME_LIMIT
app.config['WTF_CSRF_ENABLED'] = True
app.config['JWT_SECRET_KEY'] = JWT_SECRET_KEY
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = timedelta(minutes=JWT_ACCESS_TOKEN_EXPIRES_MINUTES)
app.config["JWT_REFRESH_TOKEN_EXPIRES"] = timedelta(days=30)
app.config['PREFERRED_URL_SCHEME'] = PREFERRED_URL_SCHEME

if IS_PYTHONANYWHERE:
    app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1)
    app.logger.debug("ProxyFix applied for PythonAnywhere HTTPS support")

jwt = JWTManager(app)
csrf = CSRFProtect()
csrf.init_app(app)

# ------------------------------------------------------------------
# Initialize database and seed if needed
# ------------------------------------------------------------------
with app.app_context():
    from .database import init_db, get_db, migrate_db, close_db
    from .database_seed import seed_data

    init_db()
    migrate_db()
    db = get_db()
    try:
        count = db.execute('SELECT COUNT(*) FROM classifications').fetchone()[0]
        if count == 0:
            seed_data()
            app.logger.info("Database seeded with initial data.")
        else:
            app.logger.info("Database already contains data.")
    except Exception as e:
        app.logger.error(f"Error during database initialization: {e}")
        init_db()
        migrate_db()
        seed_data()

# ------------------------------------------------------------------
# Request ID and dynamic fields
# ------------------------------------------------------------------
@app.before_request
def assign_request_id():
    request_id = request.headers.get('X-Request-Id', str(uuid.uuid4()))
    g.request_id = request_id
    for handler in app.logger.handlers:
        handler.setFormatter(logging.Formatter(f'%(asctime)s %(levelname)s [req={request_id}] %(message)s'))

@app.before_request
def load_dynamic_fields_into_g():
    if request.endpoint and request.endpoint.startswith('static'):
        return
    try:
        g.dynamic_fields = get_dynamic_fields()
        g.name_column = get_name_column()
        g.search_columns = get_search_columns()
        g.dept_column = None
        for f in g.dynamic_fields:
            if f['original_title'] == 'القسم':
                g.dept_column = f['column_name']
                break
    except Exception as e:
        app.logger.error(f"Failed to load dynamic fields: {e}")
        g.dynamic_fields = []
        g.name_column = 'id'
        g.search_columns = ['id']
        g.dept_column = None

# ------------------------------------------------------------------
# Security headers
# ------------------------------------------------------------------
@app.after_request
def set_security_headers(response):
    response.headers['X-Frame-Options'] = 'DENY'
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['X-XSS-Protection'] = '1; mode=block'
    return response

# ------------------------------------------------------------------
# Root route - now redirects to employee list instead of dashboard
# ------------------------------------------------------------------
@app.route('/')
def root():
    if 'user_id' in session:
        return redirect(url_for('employees.list'))
    return redirect(url_for('auth.login'))

# ------------------------------------------------------------------
# Register blueprints
# ------------------------------------------------------------------
app.register_blueprint(employees_bp)
app.register_blueprint(evaluations_bp)
app.register_blueprint(reports_bp)
app.register_blueprint(dashboard_bp)
app.register_blueprint(calendar_bp)
app.register_blueprint(maintenance_bp)
app.register_blueprint(export_import_bp)
app.register_blueprint(tags_bp)
app.register_blueprint(classifications_bp)
app.register_blueprint(vacation_types_bp)
app.register_blueprint(auth_bp)
app.register_blueprint(holidays_bp)
app.register_blueprint(excel_bp)
app.register_blueprint(settings_bp)
app.register_blueprint(eval_criteria_bp)
app.register_blueprint(setup_bp)
app.register_blueprint(bulk_leave_bp)
app.register_blueprint(settings_page_bp)
app.register_blueprint(manual_bp)
app.register_blueprint(user_manager_bp)
app.register_blueprint(departments_bp)
app.register_blueprint(smart_holidays_bp)
app.register_blueprint(wizard_bp)
app.register_blueprint(employee_import_bp)
app.register_blueprint(id_card_bp)
app.register_blueprint(api_bp)

# ------------------------------------------------------------------
# Template helpers
# ------------------------------------------------------------------
app.jinja_env.globals['radar_chart_func'] = generate_radar_chart
app.jinja_env.globals['breakdown_days_to_ym'] = breakdown_days_to_ym

@app.template_filter('arabic_date')
def arabic_date_filter(date_str, format='%d/%m/%Y'):
    if not date_str:
        return ''
    for fmt in ('%Y-%m-%d %H:%M:%S', '%Y-%m-%d'):
        try:
            dt = datetime.datetime.strptime(date_str, fmt)
            return dt.strftime(format)
        except ValueError:
            continue
    return date_str

# ------------------------------------------------------------------
# Teardown
# ------------------------------------------------------------------
app.teardown_appcontext(close_db)

@app.context_processor
def inject_notifications():
    if 'user_id' in session:
        try:
            notif = get_global_notifications()
        except Exception:
            notif = {}
        return dict(global_notifications=notif)
    return dict(global_notifications={})

@app.context_processor
def inject_now():
    return {'now': datetime.datetime.now}

# ------------------------------------------------------------------
# Authentication and setup checks
# ------------------------------------------------------------------
@app.before_request
def check_login():
    if request.endpoint and (request.endpoint.startswith('static') or
                             request.endpoint in ('auth.login', 'auth.logout', 'setup.wizard', 'root')):
        return
    if 'user_id' not in session:
        flash('يجب تسجيل الدخول أولاً', 'error')
        return redirect(url_for('auth.login'))
    user_role = session.get('role', 'viewer')
    endpoint = request.endpoint
    if not is_endpoint_allowed(user_role, endpoint):
        flash('ليس لديك صلاحية للوصول إلى هذه الصفحة', 'error')
        app.logger.warning(f'Access denied: {session["username"]} role {user_role} tried {endpoint}')
        return redirect(url_for('dashboard.dashboard'))

@app.before_request
def check_setup():
    if request.endpoint and (request.endpoint.startswith('static') or
                             request.endpoint in ('auth.login', 'auth.logout', 'setup.wizard', 'root')):
        return
    if 'user_id' in session:
        db = get_db()
        if db.execute('SELECT COUNT(*) FROM classifications').fetchone()[0] == 0:
            user_role = session.get('role', 'viewer')
            if user_role == 'admin':
                return redirect(url_for('setup.wizard'))
            else:
                flash('لا توجد تصنيفات في النظام. يرجى الاتصال بالمدير.', 'error')
                return redirect(url_for('dashboard.dashboard'))

# ------------------------------------------------------------------
# Shutdown endpoint
# ------------------------------------------------------------------
@app.route('/shutdown', methods=['POST'])
def shutdown():
    if request.remote_addr not in ('127.0.0.1', 'localhost'):
        return "Forbidden", 403
    func = request.environ.get('werkzeug.server.shutdown')
    if func is None:
        import os, sys
        sys.exit(0)
    func()
    return "Server shutting down..."

# ------------------------------------------------------------------
# Backup thread (only enabled outside PythonAnywhere)
# ------------------------------------------------------------------
from .maintenance_backup import backup_thread, shutdown_backup_thread

if not IS_PYTHONANYWHERE:
    backup_thread_instance = threading.Thread(target=backup_thread, daemon=True)
    backup_thread_instance.start()
    app.logger.info("Auto backup thread started.")
else:
    app.logger.info("PythonAnywhere environment detected – auto backup thread disabled. Use scheduled tasks or manual backup.")

atexit.register(shutdown_backup_thread)

def on_exit():
    try:
        from .maintenance_backup import do_backup
        do_backup()
    except Exception:
        pass
atexit.register(on_exit)