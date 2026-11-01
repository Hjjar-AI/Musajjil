import sqlite3
import secrets
import random
import string
from flask import g
from werkzeug.security import generate_password_hash
from .config import DATABASE

def get_db():
    if 'db' not in g:
        g.db = sqlite3.connect(DATABASE)
        g.db.row_factory = sqlite3.Row
        g.db.execute("PRAGMA foreign_keys = ON")
    return g.db

def close_db(exception):
    db = g.pop('db', None)
    if db is not None:
        db.close()

def init_db():
    db = sqlite3.connect(DATABASE)
    db.execute("PRAGMA foreign_keys = ON")
    db.executescript("""
        CREATE TABLE IF NOT EXISTS employee_fields (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            original_title TEXT NOT NULL,
            column_name TEXT NOT NULL UNIQUE,
            position INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS employees (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            classification_id INTEGER,
            extra_data TEXT DEFAULT '{}',
            target_service_days REAL DEFAULT 0,
            contract_end_date TEXT,
            last_updated TEXT,
            uncompensated_days REAL DEFAULT 0,
            deleted_at TEXT,
            FOREIGN KEY (classification_id) REFERENCES classifications(id)
        );
        CREATE TABLE IF NOT EXISTS classifications (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL
        );
        CREATE TABLE IF NOT EXISTS classification_entitlements (
            classification_id INTEGER NOT NULL,
            vacation_type_id INTEGER NOT NULL,
            default_days REAL NOT NULL DEFAULT 0,
            PRIMARY KEY (classification_id, vacation_type_id),
            FOREIGN KEY (classification_id) REFERENCES classifications(id),
            FOREIGN KEY (vacation_type_id) REFERENCES vacation_types(id)
        );
        CREATE TABLE IF NOT EXISTS employee_entitlements (
            employee_id INTEGER NOT NULL,
            vacation_type_id INTEGER NOT NULL,
            year INTEGER NOT NULL,
            entitled_days REAL NOT NULL,
            PRIMARY KEY (employee_id, vacation_type_id, year),
            FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
            FOREIGN KEY (vacation_type_id) REFERENCES vacation_types(id)
        );
        CREATE TABLE IF NOT EXISTS vacation_types (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            hours_per_day REAL DEFAULT 8,
            is_yearly INTEGER DEFAULT 1
        );
        CREATE TABLE IF NOT EXISTS leave_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            employee_id INTEGER NOT NULL,
            vacation_type_id INTEGER NOT NULL,
            start_date TEXT NOT NULL,
            end_date TEXT NOT NULL,
            days_taken REAL NOT NULL,
            notes TEXT,
            source_lateness_id INTEGER,
            lateness_reduced_hours REAL,
            FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
            FOREIGN KEY (vacation_type_id) REFERENCES vacation_types(id)
        );
        CREATE TABLE IF NOT EXISTS lateness_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            employee_id INTEGER NOT NULL,
            date TEXT NOT NULL,
            hours REAL NOT NULL,
            reduced_hours REAL DEFAULT 0,
            notes TEXT,
            vacation_type_id INTEGER,
            FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
            FOREIGN KEY (vacation_type_id) REFERENCES vacation_types(id)
        );
        CREATE TABLE IF NOT EXISTS tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL
        );
        CREATE TABLE IF NOT EXISTS employee_tags (
            employee_id INTEGER NOT NULL,
            tag_id INTEGER NOT NULL,
            PRIMARY KEY (employee_id, tag_id),
            FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
            FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS employee_work_periods (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            employee_id INTEGER NOT NULL,
            start_date TEXT NOT NULL,
            end_date TEXT NOT NULL,
            label TEXT,
            FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS evaluation_criteria (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            classification_id INTEGER,
            descriptions TEXT DEFAULT '{}',
            UNIQUE(name, classification_id)
        );
        CREATE TABLE IF NOT EXISTS evaluations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            employee_id INTEGER NOT NULL,
            evaluator_name TEXT NOT NULL,
            evaluation_date TEXT NOT NULL,
            notes TEXT,
            FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS evaluation_scores (
            evaluation_id INTEGER NOT NULL,
            criterion_id INTEGER NOT NULL,
            score REAL NOT NULL CHECK (score >= 1 AND score <= 5),
            PRIMARY KEY (evaluation_id, criterion_id),
            FOREIGN KEY (evaluation_id) REFERENCES evaluations(id) ON DELETE CASCADE,
            FOREIGN KEY (criterion_id) REFERENCES evaluation_criteria(id)
        );
        CREATE TABLE IF NOT EXISTS backup_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            backup_date TEXT NOT NULL,
            filename TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS year_settings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            year INTEGER NOT NULL,
            processed INTEGER DEFAULT 0,
            processed_date TEXT
        );
        CREATE TABLE IF NOT EXISTS public_holidays (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            date TEXT NOT NULL,
            is_fixed INTEGER DEFAULT 0,
            UNIQUE(name, date, is_fixed)
        );
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL,
            role TEXT NOT NULL DEFAULT 'viewer',
            must_change_password INTEGER DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS departments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL
        );
        CREATE TABLE IF NOT EXISTS holiday_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            rule_type TEXT NOT NULL,
            rule_value TEXT NOT NULL,
            repeat_type TEXT DEFAULT 'once',
            repeat_interval INTEGER DEFAULT 1,
            target_departments TEXT,
            target_classifications TEXT,
            is_active INTEGER DEFAULT 1,
            description TEXT
        );
    """)
    
    # إضافة فهارس الأداء لتحسين سرعة الاستعلامات
    cursor = db.cursor()
    
    # فهارس leave_log
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_leave_log_employee_start ON leave_log(employee_id, start_date)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_leave_log_vacation_type ON leave_log(vacation_type_id)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_leave_log_dates ON leave_log(start_date, end_date)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_leave_log_employee_dates ON leave_log(employee_id, start_date, end_date)")
    
    # فهارس lateness_log
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_lateness_log_date ON lateness_log(date)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_lateness_log_employee ON lateness_log(employee_id, date)")
    
    # فهارس employees
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_employees_deleted ON employees(deleted_at)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_employees_classification ON employees(classification_id)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_employees_contract_end ON employees(contract_end_date)")
    
    # فهارس employee_work_periods
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_work_periods_employee ON employee_work_periods(employee_id, start_date)")
    
    # فهارس evaluations
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_evaluations_employee ON evaluations(employee_id, evaluation_date)")
    
    db.commit()
    db.close()

def migrate_db():
    db = sqlite3.connect(DATABASE)
    cur = db.cursor()

    # Remove old columns if they exist (ignore errors)
    try:
        cur.execute("ALTER TABLE vacation_types DROP COLUMN carry_over_allowed")
    except sqlite3.OperationalError:
        pass
    try:
        cur.execute("ALTER TABLE vacation_types DROP COLUMN max_carry_over")
    except sqlite3.OperationalError:
        pass
    try:
        cur.execute("ALTER TABLE employee_entitlements DROP COLUMN carried_over_days")
    except sqlite3.OperationalError:
        pass

    # Drop locked_periods table if exists
    cur.execute("DROP TABLE IF EXISTS locked_periods")
    # Drop advanced evaluation tables if they exist (clean up)
    cur.execute("DROP TABLE IF EXISTS advanced_evaluation_scores")
    cur.execute("DROP TABLE IF EXISTS advanced_evaluations")
    cur.execute("DROP TABLE IF EXISTS tool_criteria")
    cur.execute("DROP TABLE IF EXISTS evaluation_tools")

    # Add year column to employee_entitlements if not exists
    try:
        cur.execute("ALTER TABLE employee_entitlements ADD COLUMN year INTEGER DEFAULT 0")
    except sqlite3.OperationalError:
        pass

    # Create default users with password = username, and force change on first login
    if cur.execute("SELECT COUNT(*) FROM users WHERE username='admin'").fetchone()[0] == 0:
        admin_pass = "admin"
        print(f"\n=== IMPORTANT: Default admin password is: {admin_pass} ===")
        print("Please change it after first login.\n")
        cur.execute("INSERT INTO users (username, password, role, must_change_password) VALUES (?, ?, ?, ?)",
                    ('admin', generate_password_hash(admin_pass), 'admin', 1))

    if cur.execute("SELECT COUNT(*) FROM users WHERE username='registerar'").fetchone()[0] == 0:
        reg_pass = "registerar"
        print(f"Default registerar password: {reg_pass}")
        cur.execute("INSERT INTO users (username, password, role, must_change_password) VALUES (?, ?, ?, ?)",
                    ('registerar', generate_password_hash(reg_pass), 'registerar', 1))

    if cur.execute("SELECT COUNT(*) FROM users WHERE username='supervisor'").fetchone()[0] == 0:
        sup_pass = "supervisor"
        print(f"Default supervisor password: {sup_pass}")
        cur.execute("INSERT INTO users (username, password, role, must_change_password) VALUES (?, ?, ?, ?)",
                    ('supervisor', generate_password_hash(sup_pass), 'supervisor', 1))

    db.commit()
    db.close()