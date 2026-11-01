package com.musajjil.app.data

import com.musajjil.app.data.local.*
import com.musajjil.app.domain.*
import java.time.DayOfWeek
import java.time.LocalDate

internal fun EmployeeEntity.model(groups: List<GroupEntity> = emptyList()) = Employee(id, name, number, nationalId,
    groups.firstOrNull { it.id == departmentId }?.name ?: department, jobTitle, phone,
    employmentStart?.let(LocalDate::parse), archived, departmentId, classificationId, restored(tagIds), restored(customValues))
internal fun Employee.entity() = EmployeeEntity(id, name.trim(), number.trim(), nationalId.trim(), department.trim(),
    jobTitle.trim(), phone.trim(), employmentStart?.toString(), archived, departmentId, classificationId, stored(tagIds), stored(customValues))
internal fun HourEntryEntity.model() = HourEntry(id, employeeId, LocalDate.parse(date), hours, consumedHours, notes)
internal fun DayLeaveEntity.model() = DayLeave(id, employeeId, LocalDate.parse(start), LocalDate.parse(end), days,
    restored(allocations), restored(suggestedAllocations), department, notes,
    LeavePolicy(decodeWeekdays(excludedMask), excludeHolidays), typeId, typeName)
internal fun GroupEntity.model() = EmployeeGroup(id, name, GroupKind.valueOf(kind), archived)
internal fun FieldEntity.model() = FieldDefinition(id, label, FieldType.valueOf(type), required, position, restored(choices), archived, builtinKey)
internal fun LeaveTypeEntity.model() = LeaveType(id, name, LeavePolicy(decodeWeekdays(excludedMask), excludeHolidays), archived)
internal fun ServicePeriodEntity.model() = ServicePeriod(id, employeeId, LocalDate.parse(start), end?.let(LocalDate::parse))
internal fun ServiceProfileEntity.model() = ServiceProfile(employeeId, deduction, targetYears, targetMonths, targetDays,
    anchor?.let(LocalDate::parse), contractEnd?.let(LocalDate::parse))
internal fun encodeWeekdays(values: Set<DayOfWeek>) = values.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
internal fun decodeWeekdays(mask: Int) = DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
