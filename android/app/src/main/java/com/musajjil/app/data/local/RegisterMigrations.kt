package com.musajjil.app.data.local

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Frozen v2 defaults are shared by fresh installation and the 1 → 2 upgrade. */
object V2Defaults {
    val fields = listOf(
        FieldEntity("builtin:Name", "الاسم", "Text", true, 0, builtinKey = "Name"),
        FieldEntity("builtin:Number", "الرقم الوظيفي", "Text", position = 1, builtinKey = "Number"),
        FieldEntity("builtin:NationalId", "الرقم الوطني", "Text", position = 2, builtinKey = "NationalId"),
        FieldEntity("builtin:Department", "القسم", "Text", position = 3, builtinKey = "Department"),
        FieldEntity("builtin:JobTitle", "المسمى الوظيفي", "Text", position = 4, builtinKey = "JobTitle"),
        FieldEntity("builtin:Phone", "الهاتف", "Text", position = 5, builtinKey = "Phone"),
        FieldEntity("builtin:EmploymentStart", "تاريخ بدء العمل", "Date", position = 6, builtinKey = "EmploymentStart"),
    )
    fun seed(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO settings(id, threshold, excludedMask, excludeHolidays) VALUES(1,8,0,0)")
        db.execSQL("INSERT OR IGNORE INTO leave_types(id,name,excludedMask,excludeHolidays,archived) SELECT 'administrative','إدارية',excludedMask,excludeHolidays,0 FROM settings WHERE id=1")
        fields.forEach { field ->
            db.execSQL("INSERT OR IGNORE INTO field_definitions(id,label,type,required,position,choices,archived,builtinKey) VALUES(?,?,?,?,?,?,?,?)",
                arrayOf(field.id, field.label, field.type, if (field.required) 1 else 0, field.position, "[]", 0, field.builtinKey))
        }
    }
}

val REGISTER_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) { V2Defaults.seed(db) }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE employees ADD COLUMN departmentId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE employees ADD COLUMN classificationId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE employees ADD COLUMN tagIds TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE employees ADD COLUMN customValues TEXT NOT NULL DEFAULT '{}'")
        db.execSQL("ALTER TABLE day_leaves ADD COLUMN typeId TEXT NOT NULL DEFAULT 'administrative'")
        db.execSQL("ALTER TABLE day_leaves ADD COLUMN typeName TEXT NOT NULL DEFAULT 'إدارية'")
        db.execSQL("ALTER TABLE day_leaves ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE hour_entries ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE hour_entries ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE hour_conversions ADD COLUMN reversed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE hour_conversions ADD COLUMN reversalOf TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE hour_conversions ADD COLUMN correctionOf TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE hour_conversions ADD COLUMN ruleOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE hour_conversions ADD COLUMN originId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE hour_conversions SET ruleOrder=createdAt, originId=id")
        db.execSQL("ALTER TABLE audit_events ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS employee_groups(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, archived INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_groups_kind ON employee_groups(kind)")
        db.execSQL("CREATE TABLE IF NOT EXISTS field_definitions(id TEXT NOT NULL PRIMARY KEY, label TEXT NOT NULL, type TEXT NOT NULL, required INTEGER NOT NULL, position INTEGER NOT NULL, choices TEXT NOT NULL, archived INTEGER NOT NULL, builtinKey TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS leave_types(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, excludedMask INTEGER NOT NULL, excludeHolidays INTEGER NOT NULL, archived INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS service_periods(id TEXT NOT NULL PRIMARY KEY, employeeId TEXT NOT NULL, start TEXT NOT NULL, end TEXT, FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_service_periods_employeeId ON service_periods(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_service_periods_start ON service_periods(start)")
        db.execSQL("CREATE TABLE IF NOT EXISTS service_profiles(employeeId TEXT NOT NULL PRIMARY KEY, deduction INTEGER NOT NULL, targetYears INTEGER NOT NULL, targetMonths INTEGER NOT NULL, targetDays INTEGER NOT NULL, anchor TEXT, contractEnd TEXT, targetRule TEXT NOT NULL, FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        V2Defaults.seed(db)
        db.execSQL("INSERT INTO employee_groups(id,name,kind,archived) SELECT 'department:'||lower(hex(randomblob(16))),department,'Department',0 FROM employees WHERE department<>'' GROUP BY department")
        db.execSQL("UPDATE employees SET departmentId=COALESCE((SELECT id FROM employee_groups WHERE kind='Department' AND name=employees.department LIMIT 1),'')")
        db.execSQL("INSERT INTO service_profiles(employeeId,deduction,targetYears,targetMonths,targetDays,anchor,contractEnd,targetRule) SELECT id,0,0,0,0,employmentStart,NULL,'calendar' FROM employees")
        // Previous versions only knew the employment start; do not invent an end for archived staff.
        db.execSQL("INSERT INTO service_periods(id,employeeId,start,end) SELECT 'initial:'||id,id,employmentStart,NULL FROM employees WHERE archived=0 AND employmentStart IS NOT NULL")
    }
}
