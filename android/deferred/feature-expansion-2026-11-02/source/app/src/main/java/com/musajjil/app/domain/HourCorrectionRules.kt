package com.musajjil.app.domain

/** Each group is an active saved conversion with its original rule and source caps. */
data class SavedConversion(val id: String, val year: Int, val threshold: Int, val days: Long,
    val sources: List<HourConsumption>, val order: Long, val originId: String)
data class CorrectedConversion(val original: SavedConversion, val days: Long, val sources: List<HourConsumption>)
data class HourCorrectionPlan(val consumption: Map<String, Int>, val changes: List<CorrectedConversion>)

object HourCorrectionRules {
    /** Preserve earlier commitments and unrelated groups. Only reduce an affected group's
     * complete days, at its SAVED threshold; extra/new hours enter the ordinary pending pool.
     * Caller may convert current-year pending hours afterwards at the CURRENT threshold. */
    fun plan(entries: List<HourEntry>, conversions: List<SavedConversion>): HourCorrectionPlan {
        require(entries.all { it.hours > 0 })
        val available = entries.associateBy { it.id }
        val consumed = mutableMapOf<String, Int>()
        val changes = mutableListOf<CorrectedConversion>()
        for (conversion in conversions.sortedWith(compareBy<SavedConversion> { it.order }.thenBy { it.originId })) {
            require(conversion.threshold > 0 && conversion.days > 0 && conversion.sources.all { it.hours > 0 })
            require(conversion.sources.map { it.entryId }.distinct().size == conversion.sources.size)
            require(conversion.sources.sumOf { it.hours.toLong() } == conversion.days * conversion.threshold)
            val capacity = conversion.sources.map { source ->
                val entry = available[source.entryId]?.takeIf { it.date.year == conversion.year }
                val remaining = if (entry == null) 0 else maxOf(0, entry.hours - consumed.getOrDefault(entry.id, 0))
                HourConsumption(source.entryId, minOf(source.hours, remaining))
            }
            val days = capacity.sumOf { it.hours.toLong() } / conversion.threshold
            var remaining = days * conversion.threshold
            val sources = capacity.mapNotNull {
                val count = minOf(remaining, it.hours.toLong()).toInt()
                remaining -= count
                if (count == 0) null else HourConsumption(it.entryId, count)
            }
            sources.forEach { consumed[it.entryId] = consumed.getOrDefault(it.entryId, 0) + it.hours }
            if (days != conversion.days || sources != conversion.sources) changes += CorrectedConversion(conversion, days, sources)
        }
        return HourCorrectionPlan(consumed, changes)
    }
}
