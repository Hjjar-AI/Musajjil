package com.musajjil.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "employees", indices = [Index("name"), Index("department"), Index("archived")])
data class EmployeeEntity(
    @PrimaryKey val id: String, val name: String, val number: String, val nationalId: String,
    val department: String, val jobTitle: String, val phone: String, val employmentStart: String?, val archived: Boolean,
    @ColumnInfo(defaultValue = "''") val departmentId: String = "",
    @ColumnInfo(defaultValue = "''") val classificationId: String = "",
    @ColumnInfo(defaultValue = "'[]'") val tagIds: String = "[]",
    @ColumnInfo(defaultValue = "'{}'") val customValues: String = "{}",
)

@Serializable
@Entity(tableName = "day_leaves", indices = [Index("employeeId"), Index("start"), Index("end")],
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"])])
data class DayLeaveEntity(
    @PrimaryKey val id: String, val employeeId: String, val start: String, val end: String, val days: Int,
    val allocations: String, val suggestedAllocations: String, val department: String, val notes: String,
    val excludedMask: Int, val excludeHolidays: Boolean,
    @ColumnInfo(defaultValue = "'administrative'") val typeId: String = "administrative",
    @ColumnInfo(defaultValue = "'إدارية'") val typeName: String = "إدارية",
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)

@Serializable
@Entity(tableName = "hour_entries", indices = [Index("employeeId"), Index("year"), Index("date")],
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"])])
data class HourEntryEntity(
    @PrimaryKey val id: String, val employeeId: String, val date: String, val year: Int,
    val hours: Int, val consumedHours: Int = 0,
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)

@Serializable
@Entity(tableName = "hour_conversions", indices = [Index("employeeId"), Index("year")],
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"])])
data class HourConversionEntity(
    @PrimaryKey val id: String, val employeeId: String, val year: Int, val threshold: Int,
    val days: Long, val hoursConsumed: Long, val sources: String, val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val reversed: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val reversalOf: String? = null,
    @ColumnInfo(defaultValue = "NULL") val correctionOf: String? = null,
    @ColumnInfo(defaultValue = "0") val ruleOrder: Long = 0,
    @ColumnInfo(defaultValue = "''") val originId: String = "",
)

@Serializable
@Entity(tableName = "holidays", indices = [Index("date")])
data class HolidayEntity(@PrimaryKey val id: String, val name: String, val date: String)

@Serializable
@Entity(tableName = "settings")
data class SettingsEntity(@PrimaryKey val id: Int = 1, val threshold: Int = 8,
    val excludedMask: Int = 0, val excludeHolidays: Boolean = false)

@Serializable
@Entity(tableName = "audit_events", indices = [Index("entityId"), Index("createdAt")])
data class AuditEntity(@PrimaryKey val id: String, val entityId: String, val operation: String,
    val before: String?, val after: String, val actor: String = "local-owner", val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val reason: String = "")

@Serializable
@Entity(tableName = "employee_groups", indices = [Index("kind")])
data class GroupEntity(@PrimaryKey val id: String, val name: String, val kind: String, val archived: Boolean = false)

@Serializable
@Entity(tableName = "field_definitions")
data class FieldEntity(@PrimaryKey val id: String, val label: String, val type: String,
    val required: Boolean = false, val position: Int = 0, val choices: String = "[]", val archived: Boolean = false,
    val builtinKey: String = "")

@Serializable
@Entity(tableName = "leave_types")
data class LeaveTypeEntity(@PrimaryKey val id: String, val name: String, val excludedMask: Int = 0,
    val excludeHolidays: Boolean = false, val archived: Boolean = false)

@Serializable
@Entity(tableName = "service_periods", indices = [Index("employeeId"), Index("start")],
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"])])
data class ServicePeriodEntity(@PrimaryKey val id: String, val employeeId: String, val start: String, val end: String?)

@Serializable
@Entity(tableName = "service_profiles", foreignKeys = [ForeignKey(entity = EmployeeEntity::class,
    parentColumns = ["id"], childColumns = ["employeeId"])])
data class ServiceProfileEntity(@PrimaryKey val employeeId: String, val deduction: Int = 0,
    val targetYears: Int = 0, val targetMonths: Int = 0, val targetDays: Int = 0,
    val anchor: String? = null, val contractEnd: String? = null, val targetRule: String = "calendar")

@Dao
interface RegisterDao {
    @Query("SELECT * FROM employees ORDER BY name") fun employees(): Flow<List<EmployeeEntity>>
    @Query("SELECT * FROM day_leaves WHERE deleted = 0 ORDER BY start DESC") fun leaves(): Flow<List<DayLeaveEntity>>
    @Query("SELECT * FROM hour_entries WHERE deleted = 0 ORDER BY date DESC") fun hours(): Flow<List<HourEntryEntity>>
    @Query("SELECT * FROM hour_conversions WHERE reversed = 0 AND reversalOf IS NULL ORDER BY createdAt DESC")
    fun conversions(): Flow<List<HourConversionEntity>>
    @Query("SELECT * FROM holidays ORDER BY date") fun holidays(): Flow<List<HolidayEntity>>
    @Query("SELECT * FROM settings WHERE id = 1") fun settings(): Flow<SettingsEntity?>
    @Query("SELECT * FROM employee_groups ORDER BY name") fun groups(): Flow<List<GroupEntity>>
    @Query("SELECT * FROM field_definitions ORDER BY position, id") fun fields(): Flow<List<FieldEntity>>
    @Query("SELECT * FROM leave_types ORDER BY name") fun leaveTypes(): Flow<List<LeaveTypeEntity>>
    @Query("SELECT * FROM service_periods ORDER BY start") fun periods(): Flow<List<ServicePeriodEntity>>
    @Query("SELECT * FROM service_profiles") fun profiles(): Flow<List<ServiceProfileEntity>>
    @Query("SELECT * FROM audit_events ORDER BY createdAt DESC, id DESC") fun history(): Flow<List<AuditEntity>>
    @Query("SELECT * FROM employees WHERE id = :id") suspend fun employee(id: String): EmployeeEntity?
    @Query("SELECT * FROM day_leaves WHERE id = :id") suspend fun leave(id: String): DayLeaveEntity?
    @Query("SELECT * FROM hour_entries WHERE id = :id") suspend fun hour(id: String): HourEntryEntity?
    @Query("SELECT * FROM employee_groups WHERE id = :id") suspend fun group(id: String): GroupEntity?
    @Query("SELECT * FROM field_definitions WHERE id = :id") suspend fun field(id: String): FieldEntity?
    @Query("SELECT * FROM leave_types WHERE id = :id") suspend fun leaveType(id: String): LeaveTypeEntity?
    @Query("SELECT * FROM service_periods WHERE id = :id") suspend fun period(id: String): ServicePeriodEntity?
    @Query("SELECT * FROM service_profiles WHERE employeeId = :id") suspend fun profile(id: String): ServiceProfileEntity?
    @Query("SELECT * FROM hour_entries WHERE employeeId = :employee AND year = :year ORDER BY date, id")
    suspend fun employeeHours(employee: String, year: Int): List<HourEntryEntity>
    @Query("SELECT * FROM hour_conversions WHERE employeeId = :employee AND year = :year AND reversed = 0 AND reversalOf IS NULL ORDER BY ruleOrder, originId")
    suspend fun employeeConversions(employee: String, year: Int): List<HourConversionEntity>
    @Query("SELECT * FROM service_periods WHERE employeeId = :employee ORDER BY start")
    suspend fun employeePeriods(employee: String): List<ServicePeriodEntity>
    @Query("SELECT DISTINCT employeeId FROM hour_entries WHERE deleted = 0 AND year = :year AND consumedHours < hours")
    suspend fun pendingEmployees(year: Int): List<String>
    @Query("SELECT * FROM settings WHERE id = 1") suspend fun currentSettings(): SettingsEntity?
    @Query("SELECT * FROM employees") suspend fun allEmployees(): List<EmployeeEntity>
    @Query("SELECT * FROM day_leaves") suspend fun allLeaves(): List<DayLeaveEntity>
    @Query("SELECT * FROM hour_entries") suspend fun allHours(): List<HourEntryEntity>
    @Query("SELECT * FROM hour_conversions") suspend fun allConversions(): List<HourConversionEntity>
    @Query("SELECT * FROM holidays") suspend fun allHolidays(): List<HolidayEntity>
    @Query("SELECT * FROM audit_events") suspend fun allAudit(): List<AuditEntity>
    @Query("SELECT * FROM employee_groups") suspend fun allGroups(): List<GroupEntity>
    @Query("SELECT * FROM field_definitions ORDER BY position, id") suspend fun allFields(): List<FieldEntity>
    @Query("SELECT * FROM leave_types") suspend fun allTypes(): List<LeaveTypeEntity>
    @Query("SELECT * FROM service_periods") suspend fun allPeriods(): List<ServicePeriodEntity>
    @Query("SELECT * FROM service_profiles") suspend fun allProfiles(): List<ServiceProfileEntity>
    @Upsert suspend fun saveEmployee(value: EmployeeEntity)
    @Upsert suspend fun saveLeave(value: DayLeaveEntity)
    @Insert suspend fun insertHour(value: HourEntryEntity)
    @Upsert suspend fun saveHour(value: HourEntryEntity)
    @Insert suspend fun insertConversion(value: HourConversionEntity)
    @Upsert suspend fun saveConversion(value: HourConversionEntity)
    @Upsert suspend fun saveHoliday(value: HolidayEntity)
    @Insert suspend fun audit(value: AuditEntity)
    @Upsert suspend fun saveSettings(value: SettingsEntity)
    @Upsert suspend fun saveGroup(value: GroupEntity)
    @Upsert suspend fun saveField(value: FieldEntity)
    @Upsert suspend fun saveType(value: LeaveTypeEntity)
    @Upsert suspend fun savePeriod(value: ServicePeriodEntity)
    @Upsert suspend fun saveProfile(value: ServiceProfileEntity)
    @Query("DELETE FROM service_periods WHERE id = :id") suspend fun deletePeriod(id: String)
    @Query("DELETE FROM holidays WHERE id = :id") suspend fun deleteHoliday(id: String)
    @Query("UPDATE hour_entries SET consumedHours = consumedHours + :amount WHERE id = :id")
    suspend fun consume(id: String, amount: Int)
}

@Database(entities = [EmployeeEntity::class, DayLeaveEntity::class, HourEntryEntity::class,
    HourConversionEntity::class, HolidayEntity::class, SettingsEntity::class, AuditEntity::class,
    GroupEntity::class, FieldEntity::class, LeaveTypeEntity::class, ServicePeriodEntity::class, ServiceProfileEntity::class],
    version = 2, exportSchema = true)
abstract class RegisterDatabase : RoomDatabase() {
    abstract fun registerDao(): RegisterDao
}
