package com.musajjil.app.domain

import java.time.LocalDate

object LeaveRules {
    /** Inclusive dates; a holiday/weekend date is excluded once, even with duplicate labels. */
    fun suggest(start: LocalDate, end: LocalDate, policy: LeavePolicy, holidays: Set<LocalDate>): Map<Int, Int> {
        require(!end.isBefore(start))
        val result = linkedMapOf<Int, Int>()
        var date = start
        while (!date.isAfter(end)) {
            result.putIfAbsent(date.year, 0)
            if (date.dayOfWeek !in policy.excludedWeekdays && !(policy.excludeHolidays && date in holidays)) {
                result[date.year] = result.getValue(date.year) + 1
            }
            date = date.plusDays(1)
        }
        return result
    }

    fun validAllocation(days: Int, allocation: Map<Int, Int>, start: LocalDate, end: LocalDate): Boolean =
        days > 0 && !end.isBefore(start) && allocation.isNotEmpty() &&
            allocation.keys == (start.year..end.year).toSet() &&
            allocation.values.all { it >= 0 } && allocation.values.sumOf { it.toLong() } == days.toLong()
}

data class HourConsumption(val entryId: String, val hours: Int)
data class ConversionPlan(val days: Long, val hours: Long, val sources: List<HourConsumption>)

object HourlyRules {
    /** Caller supplies one employee's entries. Past years never enter the current year's pool. */
    fun plan(entries: List<HourEntry>, year: Int, threshold: Int): ConversionPlan {
        require(threshold > 0)
        require(entries.all { it.hours > 0 && it.consumedHours in 0..it.hours })
        val pending = entries.filter { it.date.year == year && it.consumedHours < it.hours }
            .sortedWith(compareBy<HourEntry> { it.date }.thenBy { it.id })
        val total = pending.sumOf { (it.hours - it.consumedHours).toLong() }
        val days = total / threshold
        var remaining = days * threshold
        val sources = buildList {
            pending.forEach { entry ->
                val used = minOf(remaining, (entry.hours - entry.consumedHours).toLong()).toInt()
                if (used > 0) add(HourConsumption(entry.id, used))
                remaining -= used
            }
        }
        return ConversionPlan(days, days * threshold, sources)
    }
}
