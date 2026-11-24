package app.meubolso.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthSummaryTest {
    @Test fun freeToSpendUsesExpectedIncome() {
        // Recebido 1.000, previsto ainda 4.000, gastou 1.500, contas a vencer 800.
        val s = MonthSummary(incomeReceived = 100000, incomePending = 400000, expenses = 150000, committed = 80000)
        assertEquals(500000L, s.incomeExpected)
        assertEquals(270000L, s.freeToSpend)
        assertFalse(s.isNegative)
    }

    @Test fun negativeWhenOverspent() {
        val s = MonthSummary(incomeReceived = 300000, incomePending = 0, expenses = 250000, committed = 60000)
        assertEquals(-10000L, s.freeToSpend)
        assertTrue(s.isNegative)
    }

    @Test fun savingsReduceFreeButAreNotExpenses() {
        val s = MonthSummary(incomeReceived = 500000, incomePending = 0, expenses = 200000, committed = 50000, saved = 100000)
        assertEquals(150000L, s.freeToSpend)
        assertEquals(200000L, s.expenses)
    }

    @Test fun emptyIsZero() = assertEquals(0L, MonthSummary.EMPTY.freeToSpend)
}
