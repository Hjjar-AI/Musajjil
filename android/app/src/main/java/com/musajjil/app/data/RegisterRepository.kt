package com.musajjil.app.data

import androidx.room.withTransaction
import com.musajjil.app.data.local.*
import com.musajjil.app.domain.*
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject

/** The UI never talks directly to Room. Writes and their history commit together. */
class RegisterRepository(private val database: RegisterDatabase, private val clock: Clock = Clock.systemDefaultZone()) {
    private val dao = database.registerDao()
    private val policy = combine(dao.settings(), dao.holidays()) { settings, holidays ->
        (settings ?: SettingsEntity()) to holidays.map { Holiday(it.id, it.name, LocalDate.parse(it.date)) }
    }
    val data: Flow<RegisterData> = combine(dao.employees(), dao.leaves(), dao.hours(), dao.conversions(), policy) {
            employees, leaves, hours, conversions, options ->
        RegisterData(
            employees = employees.map { it.model() }, leaves = leaves.map { it.model() },
            hours = hours.map { it.model() },
            conversions = conversions.map { HourConversion(it.id, it.employeeId, it.year, it.threshold, it.days, it.hoursConsumed) },
            holidays = options.second, threshold = options.first.threshold,
            leavePolicy = LeavePolicy(decodeWeekdays(options.first.excludedMask), options.first.excludeHolidays),
            loading = false,
        )
    }

    suspend fun saveEmployee(employee: Employee) = database.withTransaction {
        require(employee.name.isNotBlank())
        val before = dao.employee(employee.id)
        val entity = EmployeeEntity(employee.id, employee.name.trim(), employee.number.trim(), employee.nationalId.trim(),
            employee.department.trim(), employee.jobTitle.trim(), employee.phone.trim(), employee.employmentStart?.toString(), employee.archived)
        dao.saveEmployee(entity)
        audit(employee.id, if (before == null) "employee.create" else "employee.update", before?.toString(), entity.toString())
    }

    suspend fun saveLeave(leave: DayLeave) = database.withTransaction {
        val employee = requireNotNull(dao.employee(leave.employeeId))
        val before = dao.leave(leave.id)
        require(!employee.archived || before != null)
        require(LeaveRules.validAllocation(leave.days, leave.allocations, leave.start, leave.end))
        val entity = DayLeaveEntity(leave.id, leave.employeeId, leave.start.toString(), leave.end.toString(), leave.days,
            encodeAllocation(leave.allocations), encodeAllocation(leave.suggestedAllocations), leave.department.trim(), leave.notes,
            encodeWeekdays(leave.policy.excludedWeekdays), leave.policy.excludeHolidays)
        dao.saveLeave(entity)
        audit(leave.id, if (before == null) "leave.create" else "leave.update", before?.toString(), entity.toString())
    }

    suspend fun addHours(entry: HourEntry) = database.withTransaction {
        require(entry.hours > 0 && entry.consumedHours == 0)
        require(dao.employee(entry.employeeId)?.archived == false)
        // A retained operation ID makes a retry safe, separately from deliberate duplicate warnings.
        if (dao.hour(entry.id) != null) return@withTransaction
        val entity = HourEntryEntity(entry.id, entry.employeeId, entry.date.toString(), entry.date.year, entry.hours)
        dao.insertHour(entity)
        audit(entry.id, "hours.create", null, entity.toString())
        convert(entry.employeeId, LocalDate.now(clock).year, (dao.currentSettings() ?: SettingsEntity()).threshold)
    }

    suspend fun updateThreshold(value: Int) = database.withTransaction {
        require(value > 0)
        val before = dao.currentSettings() ?: SettingsEntity()
        dao.saveSettings(before.copy(threshold = value))
        audit("settings", "threshold.update", before.threshold.toString(), value.toString())
        val year = LocalDate.now(clock).year
        dao.pendingEmployees(year).forEach { employee -> convert(employee, year, value) }
    }

    suspend fun updateLeavePolicy(value: LeavePolicy) = database.withTransaction {
        val before = dao.currentSettings() ?: SettingsEntity()
        val next = before.copy(excludedMask = encodeWeekdays(value.excludedWeekdays), excludeHolidays = value.excludeHolidays)
        dao.saveSettings(next)
        audit("settings", "leave-policy.update", before.toString(), next.toString())
    }

    suspend fun addHoliday(value: Holiday) = database.withTransaction {
        require(value.name.isNotBlank())
        dao.insertHoliday(HolidayEntity(value.id, value.name.trim(), value.date.toString()))
        audit(value.id, "holiday.create", null, value.toString())
    }

    private suspend fun convert(employee: String, year: Int, threshold: Int) {
        val plan = HourlyRules.plan(dao.employeeHours(employee, year).map { it.model() }, year, threshold)
        if (plan.days == 0L) return
        val sourceJson = JSONArray().apply {
            plan.sources.forEach { put(JSONObject().put("entryId", it.entryId).put("hours", it.hours)) }
        }.toString()
        plan.sources.forEach { dao.consume(it.entryId, it.hours) }
        val conversion = HourConversionEntity(UUID.randomUUID().toString(), employee, year, threshold,
            plan.days, plan.hours, sourceJson, clock.millis())
        dao.insertConversion(conversion)
        audit(conversion.id, "hours.convert", null, conversion.toString())
    }

    private suspend fun audit(id: String, operation: String, before: String?, after: String) {
        dao.audit(AuditEntity(UUID.randomUUID().toString(), id, operation, before, after, createdAt = clock.millis()))
    }
}

private fun EmployeeEntity.model() = Employee(id, name, number, nationalId, department, jobTitle, phone,
    employmentStart?.let(LocalDate::parse), archived)
private fun HourEntryEntity.model() = HourEntry(id, employeeId, LocalDate.parse(date), hours, consumedHours)
private fun DayLeaveEntity.model() = DayLeave(id, employeeId, LocalDate.parse(start), LocalDate.parse(end), days,
    decodeAllocation(allocations), decodeAllocation(suggestedAllocations), department, notes,
    LeavePolicy(decodeWeekdays(excludedMask), excludeHolidays))
private fun encodeAllocation(values: Map<Int, Int>) = JSONObject().apply { values.forEach { (year, days) -> put(year.toString(), days) } }.toString()
private fun decodeAllocation(value: String): Map<Int, Int> = JSONObject(value).let { json ->
    json.keys().asSequence().associate { it.toInt() to json.getInt(it) }.toSortedMap()
}
private fun encodeWeekdays(values: Set<DayOfWeek>) = values.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
private fun decodeWeekdays(mask: Int) = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
