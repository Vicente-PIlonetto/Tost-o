package app.meubolso.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinancialMonthTest {
    @Test fun calendarMonthWhenStartIs1() {
        val m = FinancialMonth.of(LocalDate.of(2026, 2, 15), 1)
        assertEquals(LocalDate.of(2026, 2, 1), m.start)
        assertEquals(LocalDate.of(2026, 2, 28), m.endInclusive)
    }

    @Test fun startOnSalaryDay() {
        val before = FinancialMonth.of(LocalDate.of(2026, 11, 4), 5)
        assertEquals(YearMonth.of(2026, 10), before.ym)
        assertEquals(LocalDate.of(2026, 10, 5), before.start)
        assertEquals(LocalDate.of(2026, 11, 4), before.endInclusive)
        val on = FinancialMonth.of(LocalDate.of(2026, 11, 5), 5)
        assertEquals(YearMonth.of(2026, 11), on.ym)
        assertTrue(LocalDate.of(2026, 12, 4) in on)
        assertFalse(LocalDate.of(2026, 12, 5) in on)
    }

    @Test fun yearBoundary() {
        val m = FinancialMonth.of(LocalDate.of(2027, 1, 3), 10)
        assertEquals(YearMonth.of(2026, 12), m.ym)
        assertEquals(LocalDate.of(2027, 1, 9), m.endInclusive)
    }
}
