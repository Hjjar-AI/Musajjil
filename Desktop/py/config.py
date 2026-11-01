import configparser
import os
import sys
import secrets

CONFIG_FILE = 'config.ini'

def ensure_config():
    config = configparser.ConfigParser()
    config_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), CONFIG_FILE)
    if not os.path.exists(config_path):
        config['server'] = {}
        config['security'] = {}
        config['jwt'] = {}
        config['system'] = {}
        config['server']['port'] = '5435'
        config['server']['backup_dir'] = 'backups'
        config['security']['secret_key'] = secrets.token_hex(32)
        config['jwt']['jwt_secret_key'] = secrets.token_hex(32) + '_jwt'
        config['system']['pagination_per_page'] = '15'  # تم التخفيض من 20 إلى 15
        config['system']['cache_ttl_balance'] = '300'
        config['system']['max_backup_keep'] = '4'
        config['system']['auto_backup_days'] = '7'
        config['system']['default_hours_per_day'] = '8'
        config['system']['max_hours_per_day'] = '24'
        config['system']['min_hours_per_day'] = '0.5'
        with open(config_path, 'w') as f:
            config.write(f)
    else:
        config.read(config_path)
        if 'server' not in config:
            config['server'] = {}
        if 'port' not in config['server']:
            config['server']['port'] = '5435'
        if 'backup_dir' not in config['server']:
            config['server']['backup_dir'] = 'backups'
        if 'security' not in config:
            config['security'] = {}
        if 'secret_key' not in config['security'] or not config['security']['secret_key']:
            config['security']['secret_key'] = secrets.token_hex(32)
        if 'jwt' not in config:
            config['jwt'] = {}
        if 'jwt_secret_key' not in config['jwt'] or not config['jwt']['jwt_secret_key']:
            config['jwt']['jwt_secret_key'] = secrets.token_hex(32) + '_jwt'
        if 'system' not in config:
            config['system'] = {}
        # تحديث قيمة pagination_per_page إذا كانت قديمة
        if 'pagination_per_page' not in config['system']:
            config['system']['pagination_per_page'] = '15'
        else:
            # إذا كانت القيمة أكبر من 20، نخفضها إلى 15
            current = int(config['system']['pagination_per_page'])
            if current > 20:
                config['system']['pagination_per_page'] = '15'
        for key, default in [
            ('pagination_per_page', '15'),
            ('cache_ttl_balance', '300'),
            ('max_backup_keep', '4'),
            ('auto_backup_days', '7'),
            ('default_hours_per_day', '8'),
            ('max_hours_per_day', '24'),
            ('min_hours_per_day', '0.5')
        ]:
            if key not in config['system']:
                config['system'][key] = default
        with open(config_path, 'w') as f:
            config.write(f)

    PORT = int(config['server']['port'])
    BACKUP_DIR = config['server']['backup_dir']
    SECRET_KEY = config['security']['secret_key']
    JWT_SECRET_KEY = config['jwt']['jwt_secret_key']

    PAGINATION_PER_PAGE = int(config['system']['pagination_per_page'])
    CACHE_TTL_BALANCE = int(config['system']['cache_ttl_balance'])
    MAX_BACKUP_KEEP = int(config['system']['max_backup_keep'])
    AUTO_BACKUP_DAYS = int(config['system']['auto_backup_days'])
    DEFAULT_HOURS_PER_DAY = float(config['system']['default_hours_per_day'])
    MAX_HOURS_PER_DAY = float(config['system']['max_hours_per_day'])
    MIN_HOURS_PER_DAY = float(config['system']['min_hours_per_day'])

    MAX_CONTENT_LENGTH = 5 * 1024 * 1024
    SESSION_COOKIE_SECURE = False
    SESSION_COOKIE_HTTPONLY = True
    SESSION_COOKIE_SAMESITE = 'Lax'

    if os.environ.get('PYANYWHERE_SECRET_KEY'):
        SECRET_KEY = os.environ['PYANYWHERE_SECRET_KEY']
    if os.environ.get('PYANYWHERE_JWT_SECRET_KEY'):
        JWT_SECRET_KEY = os.environ['PYANYWHERE_JWT_SECRET_KEY']

    return (PORT, BACKUP_DIR, SECRET_KEY, MAX_CONTENT_LENGTH,
            SESSION_COOKIE_SECURE, SESSION_COOKIE_HTTPONLY,
            SESSION_COOKIE_SAMESITE, JWT_SECRET_KEY,
            PAGINATION_PER_PAGE, CACHE_TTL_BALANCE,
            MAX_BACKUP_KEEP, AUTO_BACKUP_DAYS,
            DEFAULT_HOURS_PER_DAY, MAX_HOURS_PER_DAY,
            MIN_HOURS_PER_DAY)

(PORT, BACKUP_DIR, SECRET_KEY, MAX_CONTENT_LENGTH,
 SESSION_COOKIE_SECURE, SESSION_COOKIE_HTTPONLY,
 SESSION_COOKIE_SAMESITE, JWT_SECRET_KEY,
 PAGINATION_PER_PAGE, CACHE_TTL_BALANCE,
 MAX_BACKUP_KEEP, AUTO_BACKUP_DAYS,
 DEFAULT_HOURS_PER_DAY, MAX_HOURS_PER_DAY,
 MIN_HOURS_PER_DAY) = ensure_config()

BASE_DIR = os.path.dirname(os.path.dirname(__file__))
DATABASE = os.path.join(BASE_DIR, 'employee_data.db')
BACKUP_DIR = os.path.join(BASE_DIR, BACKUP_DIR)
os.makedirs(BACKUP_DIR, exist_ok=True)

IS_PYTHONANYWHERE = 'PYTHONANYWHERE_SITE' in os.environ
HTTPS_ENABLED = IS_PYTHONANYWHERE or os.environ.get('HTTPS', '').lower() == 'on'

if HTTPS_ENABLED:
    SESSION_COOKIE_SECURE = True
    PREFERRED_URL_SCHEME = 'https'
else:
    SESSION_COOKIE_SECURE = False
    PREFERRED_URL_SCHEME = 'http'

# تحسين الأداء: تقليل مدة صلاحية الـ CSRF و JWT
WTF_CSRF_TIME_LIMIT = 3600  # 1 ساعة بدلاً من 24 ساعة
JWT_ACCESS_TOKEN_EXPIRES_MINUTES = 5  # 5 دقائق بدلاً من 15

ROLE_ADMIN = 'admin'
ROLE_REGISTERAR = 'registerar'
ROLE_SUPERVISOR = 'supervisor'
ROLE_VIEWER = 'viewer'

VACATION_TYPE_ADMINISTRATIVE = 'إدارية'
VACATION_TYPE_UNPAID = 'بلا أجر'
VACATION_TYPE_SICK_PAID = 'مرضية إدارية'
VACATION_TYPE_SICK_UNPAID = 'مرضية بلا أجر'
VACATION_TYPE_MATERNITY = 'أمومة'
VACATION_TYPE_UNEXCUSED = 'غير مبرر'
VACATION_TYPE_LATENESS = 'تأخير (3 ساعات)'
VACATION_TYPE_HOURLY = 'إجازة ساعية'

LATENESS_CONVERSION_ORDER = [
    VACATION_TYPE_ADMINISTRATIVE,
    VACATION_TYPE_UNPAID,
    VACATION_TYPE_UNEXCUSED
]

DEFAULT_PAGE = 1
DEFAULT_PER_PAGE = PAGINATION_PER_PAGE

DATE_FORMAT_DISPLAY = '%d/%m/%Y'
DATE_FORMAT_DB = '%Y-%m-%d'
DATE_FORMAT_DATETIME_DB = '%Y-%m-%d %H:%M:%S'

LOW_BALANCE_THRESHOLD = 5
CONTRACT_ALERT_DAYS = 30

CHART_COLORS = [
    '#36a2eb', '#ff6384', '#ffcd56', '#4bc0c0', '#9966ff', '#c9cbcf'
]

PUBLIC_HOLIDAY_FIXED_YEAR = 2000