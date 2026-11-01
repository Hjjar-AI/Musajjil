package com.musajjil.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ServiceSummary(val countedDays: Long, val effectiveDays: Long, val targetDays: Long?,
    val remainingDays: Long?, val estimatedCompletion: LocalDate?)

object ServiceRules {
    /** Inclusive intervals, merged before counting; future dates and gaps do not accrue. */
    fun countedDays(periods: List<ServicePeriod>, asOf: LocalDate): Long {
        val intervals = periods.filter { !it.start.isAfter(asOf) }.map {
            require(it.end == null || !it.end.isBefore(it.start))
            it.start to minOf(it.end ?: asOf, asOf)
        }.sortedBy { it.first }
        var total = 0L
        var start: LocalDate? = null
        var end: LocalDate? = null
        for ((nextStart, nextEnd) in intervals) {
            if (end != null && nextStart <= end.plusDays(1)) end = maxOf(end, nextEnd)
            else {
                if (start != null && end != null) total += ChronoUnit.DAYS.between(start, end) + 1
                start = nextStart; end = nextEnd
            }
        }
        if (start != null && end != null) total += ChronoUnit.DAYS.between(start, end) + 1
        return total
    }

    /** Anniversary is exclusive: Jan 1 + one year requires service through Dec 31.
     * java.time clamps a missing month-end date to the destination month's last day. */
    fun targetDays(profile: ServiceProfile): Long? {
        require(profile.deduction >= 0 && profile.targetYears >= 0 && profile.targetMonths >= 0 && profile.targetDays >= 0)
        if (profile.targetYears == 0 && profile.targetMonths == 0 && profile.targetDays == 0) return null
        val anchor = requireNotNull(profile.anchor)
        return ChronoUnit.DAYS.between(anchor, anchor.plusYears(profile.targetYears.toLong())
            .plusMonths(profile.targetMonths.toLong()).plusDays(profile.targetDays.toLong()))
    }

    fun summarize(periods: List<ServicePeriod>, profile: ServiceProfile, today: LocalDate): ServiceSummary {
        val counted = countedDays(periods, today)
        val effective = counted - profile.deduction
        val target = targetDays(profile)
        val remaining = target?.let { maxOf(0, it - effective) }
        val active = periods.any { !it.start.isAfter(today) && (it.end == null || !it.end.isBefore(today)) }
        // Projection requires an open period; a fixed period ending today cannot promise tomorrow.
        val ongoing = active && periods.any { !it.start.isAfter(today) && it.end == null }
        val estimate = if (remaining != null && remaining > 0 && ongoing) today.plusDays(remaining) else null
        return ServiceSummary(counted, effective, target, remaining, estimate)
    }
}
