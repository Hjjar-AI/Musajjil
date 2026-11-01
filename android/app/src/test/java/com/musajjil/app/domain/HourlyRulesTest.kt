package com.musajjil.app.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HourlyRulesTest {
    private fun entry(id: String, year: Int, hours: Int, consumed: Int = 0) =
        HourEntry(id, "employee", LocalDate.of(year, 1, 1), hours, consumed)

    @Test fun `whole day threshold leaves the remainder and prevents double conversion`() {
        val first = HourlyRules.plan(listOf(entry("a", 2026, 10)), 2026, 8)
        assertEquals(1L, first.days)
        assertEquals(8L, first.hours)
        assertEquals(listOf(HourConsumption("a", 8)), first.sources)
        assertEquals(0L, HourlyRules.plan(listOf(entry("a", 2026, 10, 8)), 2026, 8).days)
        val next = HourlyRules.plan(listOf(entry("a", 2026, 10, 8), entry("b", 2026, 6)), 2026, 8)
        assertEquals(1L, next.days)
        assertEquals(listOf(HourConsumption("a", 2), HourConsumption("b", 6)), next.sources)
    }

    @Test fun `new threshold applies to pending hours without reusing prior consumption`() {
        val plan = HourlyRules.plan(listOf(entry("a", 2026, 8, 8), entry("b", 2026, 6)), 2026, 6)
        assertEquals(1L, plan.days)
        assertEquals(listOf(HourConsumption("b", 6)), plan.sources)
    }

    @Test fun `December hours cannot contribute to January conversion`() {
        val plan = HourlyRules.plan(listOf(entry("dec", 2025, 2), entry("jan", 2026, 6)), 2026, 8)
        assertEquals(0L, plan.days)
        assertTrue(plan.sources.isEmpty())
    }
}
