package com.musajjil.app.data

import com.musajjil.app.data.local.*
import com.musajjil.app.domain.*
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class RegisterSnapshot(
    val schema: Int = 2,
    val employees: List<EmployeeEntity> = emptyList(), val leaves: List<DayLeaveEntity> = emptyList(),
    val hours: List<HourEntryEntity> = emptyList(), val conversions: List<HourConversionEntity> = emptyList(),
    val holidays: List<HolidayEntity> = emptyList(), val settings: SettingsEntity = SettingsEntity(),
    val history: List<AuditEntity> = emptyList(), val groups: List<GroupEntity> = emptyList(),
    val fields: List<FieldEntity> = V2Defaults.fields,
    val leaveTypes: List<LeaveTypeEntity> = listOf(LeaveTypeEntity("administrative", "إدارية")),
    val periods: List<ServicePeriodEntity> = emptyList(), val profiles: List<ServiceProfileEntity> = emptyList(),
) {
    /** Validate before replacement, including source attribution, not just foreign keys. */
    fun validate() {
        require(schema == 2 && settings.id == 1 && settings.threshold > 0 && settings.excludedMask in 0..127)
        fun unique(ids: List<String>) { require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) }
        listOf(employees.map { it.id }, leaves.map { it.id }, hours.map { it.id }, conversions.map { it.id },
            holidays.map { it.id }, history.map { it.id }, groups.map { it.id }, fields.map { it.id },
            leaveTypes.map { it.id }, periods.map { it.id }, profiles.map { it.employeeId }).forEach(::unique)
        val employeeIds = employees.map { it.id }.toSet()
        val groupMap = groups.associateBy { it.id }
        val fieldMap = fields.associateBy { it.id }
        val typeMap = leaveTypes.associateBy { it.id }
        require(typeMap.containsKey("administrative") && leaveTypes.any { !it.archived })
        groups.forEach { require(it.name.isNotBlank()); GroupKind.valueOf(it.kind) }
        fields.forEach {
            require(it.label.isNotBlank() && it.position >= 0)
            val type = FieldType.valueOf(it.type)
            val choices = restored<List<String>>(it.choices)
            require(choices.all(String::isNotBlank) && choices.distinct().size == choices.size)
            require(type != FieldType.Dropdown || choices.isNotEmpty())
            if (it.builtinKey.isNotBlank()) require(V2Defaults.fields.any { original -> original.builtinKey == it.builtinKey && original.type == it.type })
        }
        require(fields.filter { it.builtinKey.isNotBlank() }.map { it.builtinKey }.sorted() == V2Defaults.fields.map { it.builtinKey }.sorted())
        employees.forEach { employee ->
            employee.employmentStart?.let(LocalDate::parse)
            fun reference(id: String, kind: GroupKind) { require(id.isBlank() || groupMap[id]?.kind == kind.name) }
            reference(employee.departmentId, GroupKind.Department); reference(employee.classificationId, GroupKind.Classification)
            restored<Set<String>>(employee.tagIds).forEach { require(it.isNotBlank()); reference(it, GroupKind.Tag) }
            val values = restored<Map<String, String>>(employee.customValues)
            require(values.keys.all { fieldMap[it]?.builtinKey == "" })
            // Required flags may have been enabled after these employees were saved.
            require(EmployeeValidation.invalidFields(employee.model(groups), fields.map { it.model().copy(required = false) }).isEmpty())
        }
        leaveTypes.forEach { require(it.name.isNotBlank() && it.excludedMask in 0..127) }
        leaves.forEach {
            require(it.employeeId in employeeIds && it.typeId in typeMap && it.typeName.isNotBlank() && it.excludedMask in 0..127)
            val start = LocalDate.parse(it.start); val end = LocalDate.parse(it.end)
            require(LeaveRules.validAllocation(it.days, restored(it.allocations), start, end))
            require(restored<Map<Int, Int>>(it.suggestedAllocations).values.all { count -> count >= 0 })
        }
        holidays.forEach { require(it.name.isNotBlank()); LocalDate.parse(it.date) }
        val hourMap = hours.associateBy { it.id }
        hours.forEach {
            require(it.employeeId in employeeIds && LocalDate.parse(it.date).year == it.year && it.hours > 0 && it.consumedHours in 0..it.hours)
            require(!it.deleted || it.consumedHours == 0)
        }
        val conversionMap = conversions.associateBy { it.id }
        val activeConsumption = mutableMapOf<String, Long>()
        conversions.forEach { conversion ->
            require(conversion.employeeId in employeeIds && conversion.threshold > 0 && conversion.originId.isNotBlank())
            val source = restored<List<HourConsumption>>(conversion.sources)
            require(source.isNotEmpty() && source.all { it.hours > 0 } && source.map { it.entryId }.distinct().size == source.size)
            require(source.sumOf { it.hours.toLong() } == kotlin.math.abs(conversion.hoursConsumed))
            require(source.all { hourMap[it.entryId]?.employeeId == conversion.employeeId })
            require(conversion.days != 0L && conversion.days > Long.MIN_VALUE / conversion.threshold && conversion.days <= Long.MAX_VALUE / conversion.threshold)
            require(conversion.hoursConsumed == conversion.days * conversion.threshold)
            if (conversion.reversalOf != null) {
                val original = requireNotNull(conversionMap[conversion.reversalOf])
                require(!conversion.reversed && conversion.days < 0 && original.reversed && original.reversalOf == null)
                require(conversion.days == -original.days && conversion.threshold == original.threshold && conversion.sources == original.sources)
                require(conversion.employeeId == original.employeeId && conversion.year == original.year)
            } else {
                require(conversion.days > 0)
                if (conversion.reversed) require(conversions.count { it.reversalOf == conversion.id } == 1)
                if (!conversion.reversed) source.forEach { item ->
                    val entry = requireNotNull(hourMap[item.entryId])
                    require(!entry.deleted && entry.year == conversion.year)
                    activeConsumption[item.entryId] = activeConsumption.getOrDefault(item.entryId, 0) + item.hours
                }
            }
            conversion.correctionOf?.let { parent ->
                val original = requireNotNull(conversionMap[parent]); require(original.reversed && original.reversalOf == null)
                require(original.threshold == conversion.threshold && original.year == conversion.year && original.employeeId == conversion.employeeId)
            }
        }
        hours.forEach { require(activeConsumption.getOrDefault(it.id, 0) == it.consumedHours.toLong()) }
        periods.forEach {
            require(it.employeeId in employeeIds)
            val period = it.model(); require(period.end == null || period.end >= period.start)
        }
        profiles.forEach {
            require(it.employeeId in employeeIds && it.targetRule == "calendar")
            ServiceRules.targetDays(it.model()); it.contractEnd?.let(LocalDate::parse)
        }
    }
}
