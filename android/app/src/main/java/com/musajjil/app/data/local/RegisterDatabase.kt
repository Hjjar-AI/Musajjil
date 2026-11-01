package com.musajjil.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "employees", indices = [Index("name"), Index("department"), Index("archived")])
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val number: String,
    val nationalId: String,
    val department: String,
    val jobTitle: String,
    val phone: String,
    val employmentStart: String?,
    val archived: Boolean,
)

@Entity(tableName = "day_leaves", indices = [Index("employeeId"), Index("start"), Index("end")],
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"])])
data class DayLeaveEntity(
    @PrimaryKey val id: String,
    val employeeId: String,
    val start: String,
    val end: String,
    val days: Int,
    val allocations: String,
    val suggestedAllocations: String,
    val department: String,
    val notes: String,
    val excludedMask: Int,
    val excludeHolidays: Boolean,
)

@Entity(tableName = "hour_entries", indices = [Index("employeeId"), Index("year"), Index("date")],
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"])])
data class HourEntryEntity(
    @PrimaryKey val id: String,
    val employeeId: String,
    val date: String,
    val year: Int,
    val hours: Int,
    val consumedHours: Int = 0,
)

@Entity(tableName = "hour_conversions", indices = [Index("employeeId"), Index("year")],
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"])])
data class HourConversionEntity(
    @PrimaryKey val id: String,
    val employeeId: String,
    val year: Int,
    val threshold: Int,
    val days: Long,
    val hoursConsumed: Long,
    val sources: String,
    val createdAt: Long,
)

@Entity(tableName = "holidays", indices = [Index("date")])
data class HolidayEntity(@PrimaryKey val id: String, val name: String, val date: String)

@Entity(tableName = "settings")
data class SettingsEntity(@PrimaryKey val id: Int = 1, val threshold: Int = 8,
    val excludedMask: Int = 0, val excludeHolidays: Boolean = false)

@Entity(tableName = "audit_events", indices = [Index("entityId"), Index("createdAt")])
data class AuditEntity(@PrimaryKey val id: String, val entityId: String, val operation: String,
    val before: String?, val after: String, val actor: String = "local-owner", val createdAt: Long)

@Dao
interface RegisterDao {
    @Query("SELECT * FROM employees ORDER BY name") fun employees(): Flow<List<EmployeeEntity>>
    @Query("SELECT * FROM day_leaves ORDER BY start DESC") fun leaves(): Flow<List<DayLeaveEntity>>
    @Query("SELECT * FROM hour_entries ORDER BY date DESC") fun hours(): Flow<List<HourEntryEntity>>
    @Query("SELECT * FROM hour_conversions ORDER BY createdAt DESC") fun conversions(): Flow<List<HourConversionEntity>>
    @Query("SELECT * FROM holidays ORDER BY date") fun holidays(): Flow<List<HolidayEntity>>
    @Query("SELECT * FROM settings WHERE id = 1") fun settings(): Flow<SettingsEntity?>
    @Query("SELECT * FROM employees WHERE id = :id") suspend fun employee(id: String): EmployeeEntity?
    @Query("SELECT * FROM day_leaves WHERE id = :id") suspend fun leave(id: String): DayLeaveEntity?
    @Query("SELECT * FROM hour_entries WHERE id = :id") suspend fun hour(id: String): HourEntryEntity?
    @Query("SELECT * FROM hour_entries WHERE employeeId = :employee AND year = :year ORDER BY date, id")
    suspend fun employeeHours(employee: String, year: Int): List<HourEntryEntity>
    @Query("SELECT DISTINCT employeeId FROM hour_entries WHERE year = :year AND consumedHours < hours")
    suspend fun pendingEmployees(year: Int): List<String>
    @Query("SELECT * FROM settings WHERE id = 1") suspend fun currentSettings(): SettingsEntity?
    @Upsert suspend fun saveEmployee(value: EmployeeEntity)
    @Upsert suspend fun saveLeave(value: DayLeaveEntity)
    @Insert suspend fun insertHour(value: HourEntryEntity)
    @Insert suspend fun insertConversion(value: HourConversionEntity)
    @Insert suspend fun insertHoliday(value: HolidayEntity)
    @Insert suspend fun audit(value: AuditEntity)
    @Upsert suspend fun saveSettings(value: SettingsEntity)
    @Query("UPDATE hour_entries SET consumedHours = consumedHours + :amount WHERE id = :id")
    suspend fun consume(id: String, amount: Int)
}

@Database(entities = [EmployeeEntity::class, DayLeaveEntity::class, HourEntryEntity::class,
    HourConversionEntity::class, HolidayEntity::class, SettingsEntity::class, AuditEntity::class],
    version = 1, exportSchema = true)
abstract class RegisterDatabase : RoomDatabase() {
    abstract fun registerDao(): RegisterDao
}
