package com.musajjil.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

data class Employee(
    val id: String,
    val name: String,
    val number: String = "",
    val nationalId: String = "",
    val department: String = "",
    val jobTitle: String = "",
    val phone: String = "",
    val employmentStart: LocalDate? = null,
    val archived: Boolean = false,
    val departmentId: String = "",
    val classificationId: String = "",
    val tagIds: Set<String> = emptySet(),
    val customValues: Map<String, String> = emptyMap(),
) {
    val displayName: String get() = name.ifBlank { number.ifBlank { nationalId.ifBlank { "موظف ${id.take(6)}" } } }
}

data class LeavePolicy(
    val excludedWeekdays: Set<DayOfWeek> = emptySet(),
    val excludeHolidays: Boolean = false,
)

data class DayLeave(
    val id: String,
    val employeeId: String,
    val start: LocalDate,
    val end: LocalDate,
    val days: Int,
    val allocations: Map<Int, Int>,
    val suggestedAllocations: Map<Int, Int>,
    val department: String,
    val notes: String,
    val policy: LeavePolicy,
    val typeId: String = "administrative",
    val typeName: String = "إدارية",
)

data class HourEntry(
    val id: String,
    val employeeId: String,
    val date: LocalDate,
    val hours: Int,
    val consumedHours: Int = 0,
    val notes: String = "",
)

data class HourConversion(
    val id: String,
    val employeeId: String,
    val year: Int,
    val threshold: Int,
    val days: Long,
    val hoursConsumed: Long,
)

data class Holiday(val id: String, val name: String, val date: LocalDate)

enum class GroupKind { Department, Classification, Tag }
data class EmployeeGroup(val id: String, val name: String, val kind: GroupKind, val archived: Boolean = false)
enum class FieldType { Text, Number, Date, Dropdown }
data class FieldDefinition(val id: String, val label: String, val type: FieldType,
    val required: Boolean = false, val position: Int = 0, val choices: List<String> = emptyList(),
    val archived: Boolean = false, val builtinKey: String = "")
data class LeaveType(val id: String, val name: String, val policy: LeavePolicy = LeavePolicy(), val archived: Boolean = false)
data class ServicePeriod(val id: String, val employeeId: String, val start: LocalDate, val end: LocalDate? = null)
data class ServiceProfile(val employeeId: String, val deduction: Int = 0, val targetYears: Int = 0,
    val targetMonths: Int = 0, val targetDays: Int = 0, val anchor: LocalDate? = null, val contractEnd: LocalDate? = null)
data class AuditEvent(val id: String, val entityId: String, val operation: String, val before: String?,
    val after: String, val actor: String, val createdAt: Long, val reason: String = "")
data class BulkEmployeeChange(val employeeIds: Set<String>, val departmentId: String? = null,
    val classificationId: String? = null, val addTags: Set<String> = emptySet(), val removeTags: Set<String> = emptySet())

data class RegisterData(
    val employees: List<Employee> = emptyList(),
    val leaves: List<DayLeave> = emptyList(),
    val hours: List<HourEntry> = emptyList(),
    val conversions: List<HourConversion> = emptyList(),
    val holidays: List<Holiday> = emptyList(),
    val threshold: Int = 8,
    val leavePolicy: LeavePolicy = LeavePolicy(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val groups: List<EmployeeGroup> = emptyList(),
    val fields: List<FieldDefinition> = emptyList(),
    val leaveTypes: List<LeaveType> = emptyList(),
    val servicePeriods: List<ServicePeriod> = emptyList(),
    val serviceProfiles: List<ServiceProfile> = emptyList(),
    val history: List<AuditEvent> = emptyList(),
)
