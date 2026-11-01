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
)

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
)

data class HourEntry(
    val id: String,
    val employeeId: String,
    val date: LocalDate,
    val hours: Int,
    val consumedHours: Int = 0,
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
)
