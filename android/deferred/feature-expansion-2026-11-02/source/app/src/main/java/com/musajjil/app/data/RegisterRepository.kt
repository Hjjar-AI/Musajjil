package com.musajjil.app.data

import androidx.room.withTransaction
import com.musajjil.app.data.local.*
import com.musajjil.app.domain.*
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Every mutation, correction and its history commit atomically. Backup/restore use the same gate. */
class RegisterRepository(val database: RegisterDatabase, private val clock: Clock = Clock.systemDefaultZone()) {
    private val dao = database.registerDao()
    private val workspace = Mutex()
    private val policy = combine(dao.settings(), dao.holidays()) { settings, holidays ->
        (settings ?: SettingsEntity()) to holidays.map { Holiday(it.id, it.name, LocalDate.parse(it.date)) }
    }
    private val core = combine(dao.employees(), dao.leaves(), dao.hours(), dao.conversions(), policy) {
            employees, leaves, hours, conversions, options ->
        RegisterData(employees = employees.map { it.model() }, leaves = leaves.map { it.model() }, hours = hours.map { it.model() },
            conversions = conversions.map { HourConversion(it.id, it.employeeId, it.year, it.threshold, it.days, it.hoursConsumed) },
            holidays = options.second, threshold = options.first.threshold,
            leavePolicy = LeavePolicy(decodeWeekdays(options.first.excludedMask), options.first.excludeHolidays), loading = false)
    }
    private data class Configuration(val groups: List<GroupEntity>, val fields: List<FieldEntity>, val types: List<LeaveTypeEntity>)
    private val configuration = combine(dao.groups(), dao.fields(), dao.leaveTypes(), ::Configuration)
    private val service = combine(dao.periods(), dao.profiles()) { periods, profiles -> periods to profiles }
    val data: Flow<RegisterData> = combine(core, configuration, service, dao.history()) { base, config, service, audit ->
        base.copy(employees = base.employees.map { employee -> employee.copy(
            department = config.groups.firstOrNull { it.id == employee.departmentId }?.name ?: employee.department) },
            groups = config.groups.map { it.model() }, fields = config.fields.map { it.model() }, leaveTypes = config.types.map { it.model() },
            servicePeriods = service.first.map { it.model() }, serviceProfiles = service.second.map { it.model() },
            history = audit.map { AuditEvent(it.id, it.entityId, it.operation, it.before, it.after, it.actor, it.createdAt, it.reason) })
    }
    private suspend fun <T> write(block: suspend () -> T): T = workspace.withLock { database.withTransaction { block() } }

    private suspend fun validateEmployee(value: Employee): EmployeeEntity {
        val groups = dao.allGroups()
        fun validRef(id: String, kind: GroupKind) = id.isBlank() || groups.any { it.id == id && it.kind == kind.name }
        require(validRef(value.departmentId, GroupKind.Department) && validRef(value.classificationId, GroupKind.Classification))
        require(value.tagIds.all { validRef(it, GroupKind.Tag) && it.isNotBlank() })
        val actual = value.copy(department = groups.firstOrNull { it.id == value.departmentId }?.name ?: value.department)
        require(EmployeeValidation.invalidFields(actual, dao.allFields().map { it.model() }).isEmpty())
        return actual.entity()
    }

    suspend fun saveEmployee(employee: Employee) = write {
        val before = dao.employee(employee.id)
        val entity = validateEmployee(employee)
        dao.saveEmployee(entity)
        audit(entity.id, if (before == null) "employee.create" else "employee.update", before?.let { stored(it) }, stored(entity))
        if (before == null) {
            dao.saveProfile(ServiceProfileEntity(employee.id, anchor = employee.employmentStart?.toString()))
            employee.employmentStart?.let { start ->
                val period = ServicePeriodEntity(newId(), employee.id, start.toString(), null)
                dao.savePeriod(period); audit(period.id, "service.create", null, stored(period))
            }
        }
    }

    suspend fun archiveEmployee(id: String, date: LocalDate) = write {
        val before = requireNotNull(dao.employee(id))
        if (!before.archived) {
            require(!date.isAfter(LocalDate.now(clock)))
            val periods = dao.employeePeriods(id).filter { it.end == null || LocalDate.parse(it.end).isAfter(date) }
            require(periods.none { LocalDate.parse(it.start).isAfter(date) })
            periods.forEach { period ->
                val next = period.copy(end = date.toString()); dao.savePeriod(next)
                audit(period.id, "service.update", stored(period), stored(next))
            }
        } else {
            val period = ServicePeriodEntity(newId(), id, date.toString(), null)
            dao.savePeriod(period); audit(period.id, "service.create", null, stored(period))
        }
        val next = before.copy(archived = !before.archived)
        dao.saveEmployee(next)
        audit(id, if (next.archived) "employee.archive" else "employee.restore", stored(before), stored(next))
    }

    suspend fun bulkEmployees(change: BulkEmployeeChange) = write {
        require(change.employeeIds.isNotEmpty() && change.addTags.intersect(change.removeTags).isEmpty())
        val groups = dao.allGroups()
        val updates = change.employeeIds.map { id ->
            val before = requireNotNull(dao.employee(id))
            val employee = before.model(groups).copy(
                departmentId = change.departmentId ?: before.departmentId,
                department = if (change.departmentId != null) groups.firstOrNull { it.id == change.departmentId }?.name.orEmpty() else before.department,
                classificationId = change.classificationId ?: before.classificationId,
                tagIds = (restored<Set<String>>(before.tagIds) + change.addTags) - change.removeTags)
            before to validateEmployee(employee)
        }
        updates.forEach { (before, after) ->
            dao.saveEmployee(after); audit(after.id, "employee.bulk_update", stored(before), stored(after))
        }
    }

    suspend fun saveGroup(group: EmployeeGroup) = write {
        require(group.name.isNotBlank())
        val before = dao.group(group.id)
        require(before == null || before.kind == group.kind.name)
        val next = GroupEntity(group.id, group.name.trim(), group.kind.name, group.archived)
        dao.saveGroup(next); audit(next.id, "group.update", before?.let { stored(it) }, stored(next))
        if (group.kind == GroupKind.Department && before?.name != next.name) {
            dao.allEmployees().filter { it.departmentId == next.id }.forEach { employee ->
                val updated = employee.copy(department = next.name); dao.saveEmployee(updated)
                audit(employee.id, "employee.update", stored(employee), stored(updated))
            }
        }
    }

    suspend fun saveField(field: FieldDefinition) = write {
        require(field.label.isNotBlank() && field.position >= 0)
        val before = dao.field(field.id)
        require(before == null || (before.type == field.type.name && before.builtinKey == field.builtinKey))
        require(before != null || field.builtinKey.isBlank())
        val choices = field.choices.map(String::trim).filter(String::isNotBlank).distinct()
        require(field.type != FieldType.Dropdown || choices.isNotEmpty())
        val next = FieldEntity(field.id, field.label.trim(), field.type.name, field.required, field.position,
            stored(choices), field.archived, field.builtinKey)
        dao.saveField(next); audit(next.id, "field.update", before?.let { stored(it) }, stored(next))
    }

    suspend fun saveLeaveType(type: LeaveType) = write {
        require(type.name.isNotBlank())
        val before = dao.leaveType(type.id)
        require(!type.archived || dao.allTypes().any { it.id != type.id && !it.archived })
        val next = LeaveTypeEntity(type.id, type.name.trim(), encodeWeekdays(type.policy.excludedWeekdays), type.policy.excludeHolidays, type.archived)
        dao.saveType(next); audit(next.id, "leave_type.update", before?.let { stored(it) }, stored(next))
        if (next.id == "administrative") {
            val settings = dao.currentSettings() ?: SettingsEntity()
            dao.saveSettings(settings.copy(excludedMask = next.excludedMask, excludeHolidays = next.excludeHolidays))
        }
    }

    suspend fun saveLeave(leave: DayLeave, reason: String = "") = write {
        val employee = requireNotNull(dao.employee(leave.employeeId))
        val before = dao.leave(leave.id)
        require(before?.deleted != true && (!employee.archived || before != null))
        val type = requireNotNull(dao.leaveType(leave.typeId))
        require(!type.archived || before?.typeId == type.id)
        require(LeaveRules.validAllocation(leave.days, leave.allocations, leave.start, leave.end))
        val same = before != null && before.start == leave.start.toString() && before.end == leave.end.toString() && before.typeId == type.id
        val suggestion = if (same) requireNotNull(before).suggestedAllocations else stored(LeaveRules.suggest(leave.start, leave.end,
            type.model().policy, dao.allHolidays().map { LocalDate.parse(it.date) }.toSet()))
        val entity = DayLeaveEntity(leave.id, leave.employeeId, leave.start.toString(), leave.end.toString(), leave.days,
            stored(leave.allocations), suggestion, leave.department.trim(), leave.notes,
            if (same) before!!.excludedMask else type.excludedMask, if (same) before!!.excludeHolidays else type.excludeHolidays,
            type.id, if (same) before!!.typeName else type.name)
        dao.saveLeave(entity)
        audit(leave.id, if (before == null) "leave.create" else "leave.update", before?.let { stored(it) }, stored(entity), reason)
    }

    suspend fun deleteLeave(id: String, reason: String) = write {
        val before = requireNotNull(dao.leave(id)); require(!before.deleted)
        val next = before.copy(deleted = true); dao.saveLeave(next)
        audit(id, "leave.delete", stored(before), stored(next), reason)
    }

    suspend fun addHours(entry: HourEntry) = write {
        require(entry.hours > 0 && entry.consumedHours == 0)
        require(dao.employee(entry.employeeId)?.archived == false)
        if (dao.hour(entry.id) != null) return@write
        val entity = HourEntryEntity(entry.id, entry.employeeId, entry.date.toString(), entry.date.year, entry.hours, notes = entry.notes)
        dao.insertHour(entity)
        convert(entry.employeeId, LocalDate.now(clock).year, (dao.currentSettings() ?: SettingsEntity()).threshold)
        audit(entry.id, "hours.create", null, stored(requireNotNull(dao.hour(entry.id))))
    }

    suspend fun correctHours(entry: HourEntry, reason: String = "", delete: Boolean = false) = write {
        val before = requireNotNull(dao.hour(entry.id))
        require(!before.deleted && before.employeeId == entry.employeeId && entry.hours > 0)
        val next = before.copy(date = entry.date.toString(), year = entry.date.year, hours = entry.hours, notes = entry.notes,
            consumedHours = 0, deleted = delete)
        dao.saveHour(next)
        setOf(before.year, next.year).forEach { reconcile(before.employeeId, it, reason) }
        convert(before.employeeId, LocalDate.now(clock).year, (dao.currentSettings() ?: SettingsEntity()).threshold)
        audit(entry.id, if (delete) "hours.delete" else "hours.update", stored(before), stored(requireNotNull(dao.hour(entry.id))), reason)
    }

    private suspend fun reconcile(employee: String, year: Int, reason: String) {
        val entries = dao.employeeHours(employee, year)
        val conversions = dao.employeeConversions(employee, year)
        val plan = HourCorrectionRules.plan(entries.filterNot { it.deleted }.map { it.model() }, conversions.map {
            SavedConversion(it.id, it.year, it.threshold, it.days, restored(it.sources), it.ruleOrder, it.originId)
        })
        entries.forEach { dao.saveHour(it.copy(consumedHours = plan.consumption[it.id] ?: 0)) }
        plan.changes.forEach { change ->
            val original = conversions.first { it.id == change.original.id }
            dao.saveConversion(original.copy(reversed = true))
            val reversal = original.copy(id = newId(), days = -original.days, hoursConsumed = -original.hoursConsumed,
                createdAt = clock.millis(), reversed = false, reversalOf = original.id, correctionOf = null)
            dao.insertConversion(reversal)
            audit(reversal.id, "hours.reverse", stored(original), stored(reversal), reason)
            if (change.days > 0) {
                val replacement = original.copy(id = newId(), days = change.days, hoursConsumed = change.days * original.threshold,
                    sources = stored(change.sources), createdAt = clock.millis(), reversed = false, correctionOf = original.id)
                dao.insertConversion(replacement)
                audit(replacement.id, "hours.recalculate", stored(original), stored(replacement), reason)
            }
        }
    }

    suspend fun updateThreshold(value: Int) = write {
        require(value > 0)
        val before = dao.currentSettings() ?: SettingsEntity()
        val next = before.copy(threshold = value); dao.saveSettings(next)
        audit("settings", "threshold.update", stored(before), stored(next))
        val year = LocalDate.now(clock).year
        dao.pendingEmployees(year).forEach { convert(it, year, value) }
    }

    suspend fun updateLeavePolicy(value: LeavePolicy) {
        val current = requireNotNull(dao.leaveType("administrative"))
        saveLeaveType(current.model().copy(policy = value))
    }

    suspend fun addHoliday(value: Holiday) = saveHoliday(value)
    suspend fun saveHoliday(value: Holiday) = write {
        require(value.name.isNotBlank())
        val before = dao.allHolidays().firstOrNull { it.id == value.id }
        val next = HolidayEntity(value.id, value.name.trim(), value.date.toString())
        dao.saveHoliday(next); audit(next.id, "holiday.update", before?.let { stored(it) }, stored(next))
    }
    suspend fun deleteHoliday(id: String) = write {
        val before = requireNotNull(dao.allHolidays().firstOrNull { it.id == id })
        dao.deleteHoliday(id); audit(id, "holiday.delete", stored(before), "{}")
    }

    suspend fun saveServicePeriod(period: ServicePeriod, reason: String = "") = write {
        require(dao.employee(period.employeeId) != null && (period.end == null || period.end >= period.start))
        val before = dao.period(period.id)
        require(before == null || before.employeeId == period.employeeId)
        val next = ServicePeriodEntity(period.id, period.employeeId, period.start.toString(), period.end?.toString())
        dao.savePeriod(next); audit(next.id, if (before == null) "service.create" else "service.update", before?.let { stored(it) }, stored(next), reason)
        val profile = dao.profile(period.employeeId) ?: ServiceProfileEntity(period.employeeId)
        if (profile.anchor == null && profile.targetYears == 0 && profile.targetMonths == 0 && profile.targetDays == 0)
            dao.saveProfile(profile.copy(anchor = dao.employeePeriods(period.employeeId).minOf { it.start }))
    }
    suspend fun deleteServicePeriod(id: String, reason: String) = write {
        val before = requireNotNull(dao.period(id)); dao.deletePeriod(id)
        audit(id, "service.delete", stored(before), "{}", reason)
    }
    suspend fun saveServiceProfile(profile: ServiceProfile, reason: String = "") = write {
        require(dao.employee(profile.employeeId) != null)
        ServiceRules.targetDays(profile)
        val before = dao.profile(profile.employeeId)
        val next = ServiceProfileEntity(profile.employeeId, profile.deduction, profile.targetYears, profile.targetMonths,
            profile.targetDays, profile.anchor?.toString(), profile.contractEnd?.toString())
        dao.saveProfile(next); audit(profile.employeeId, "service.profile", before?.let { stored(it) }, stored(next), reason)
    }

    private suspend fun convert(employee: String, year: Int, threshold: Int) {
        val plan = HourlyRules.plan(dao.employeeHours(employee, year).filterNot { it.deleted }.map { it.model() }, year, threshold)
        if (plan.days == 0L) return
        plan.sources.forEach { dao.consume(it.entryId, it.hours) }
        val id = newId()
        val conversion = HourConversionEntity(id, employee, year, threshold, plan.days, plan.hours,
            stored(plan.sources), clock.millis(), ruleOrder = clock.millis(), originId = id)
        dao.insertConversion(conversion); audit(id, "hours.convert", null, stored(conversion))
    }

    suspend fun snapshot(): RegisterSnapshot = workspace.withLock { database.withTransaction { snapshotInside() } }
    private suspend fun snapshotInside() = RegisterSnapshot(employees = dao.allEmployees(), leaves = dao.allLeaves(),
        hours = dao.allHours(), conversions = dao.allConversions(), holidays = dao.allHolidays(),
        settings = dao.currentSettings() ?: SettingsEntity(), history = dao.allAudit(), groups = dao.allGroups(),
        fields = dao.allFields(), leaveTypes = dao.allTypes(), periods = dao.allPeriods(), profiles = dao.allProfiles())

    /** Caller's safety writer must succeed before any replacement. All writes wait behind this gate. */
    suspend fun restore(snapshot: RegisterSnapshot, writeSafety: suspend (RegisterSnapshot) -> Unit) = workspace.withLock {
        snapshot.validate()
        val previous = database.withTransaction { snapshotInside() }
        writeSafety(previous)
        database.withTransaction {
            val sql = database.openHelper.writableDatabase
            listOf("audit_events", "hour_conversions", "hour_entries", "day_leaves", "service_periods", "service_profiles",
                "employees", "holidays", "settings", "employee_groups", "field_definitions", "leave_types").forEach { sql.execSQL("DELETE FROM $it") }
            snapshot.groups.forEach { dao.saveGroup(it) }; snapshot.fields.forEach { dao.saveField(it) }
            snapshot.leaveTypes.forEach { dao.saveType(it) }; dao.saveSettings(snapshot.settings)
            snapshot.employees.forEach { dao.saveEmployee(it) }; snapshot.holidays.forEach { dao.saveHoliday(it) }
            snapshot.periods.forEach { dao.savePeriod(it) }; snapshot.profiles.forEach { dao.saveProfile(it) }
            snapshot.leaves.forEach { dao.saveLeave(it) }; snapshot.hours.forEach { dao.saveHour(it) }
            snapshot.conversions.forEach { dao.insertConversion(it) }; snapshot.history.forEach { dao.audit(it) }
            audit("workspace", "workspace.restore", null, stored(mapOf("employees" to snapshot.employees.size,
                "leaves" to snapshot.leaves.size, "hours" to snapshot.hours.size)))
        }
    }

    private suspend fun audit(id: String, operation: String, before: String?, after: String, reason: String = "") {
        dao.audit(AuditEntity(newId(), id, operation, before, after, createdAt = clock.millis(), reason = reason))
    }
    private fun newId() = UUID.randomUUID().toString()
}
