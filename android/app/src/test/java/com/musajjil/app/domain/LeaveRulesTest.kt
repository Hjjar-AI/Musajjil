package com.musajjil.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LeaveRulesTest {
    @Test fun `Friday holiday overlap is excluded once and a single Friday is zero`() {
        val friday = LocalDate.of(2026, 11, 6)
        val policy = LeavePolicy(setOf(DayOfWeek.FRIDAY), true)
        assertEquals(mapOf(2026 to 0), LeaveRules.suggest(friday, friday, policy, setOf(friday)))
        assertEquals(mapOf(2026 to 1), LeaveRules.suggest(friday, friday.plusDays(1), policy, setOf(friday)))
    }

    @Test fun `cross year suggestions and manual allocations retain the confirmed total`() {
        val start = LocalDate.of(2026, 12, 30)
        val end = LocalDate.of(2027, 1, 2)
        assertEquals(mapOf(2026 to 2, 2027 to 2), LeaveRules.suggest(start, end, LeavePolicy(), emptySet()))
        assertTrue(LeaveRules.validAllocation(3, mapOf(2026 to 1, 2027 to 2), start, end))
        assertFalse(LeaveRules.validAllocation(3, mapOf(2026 to 2, 2027 to 2), start, end))
        assertFalse(LeaveRules.validAllocation(3, mapOf(2026 to 3), start, end))
    }

    @Test fun `typed dates validate leap years and both Arabic digit sets`() {
        assertNull(DateText.parse("30/02/2026"))
        assertEquals(LocalDate.of(2028, 2, 29), DateText.parse("٢٩/٠٢/٢٠٢٨"))
        assertEquals(LocalDate.of(2026, 11, 1), DateText.parse("۰۱/۱۱/۲۰۲۶"))
        assertNull(DateText.positiveWholeNumber("2.5"))
        assertNull(DateText.positiveWholeNumber("0"))
        assertEquals(2, DateText.positiveWholeNumber("٢"))
    }
}
