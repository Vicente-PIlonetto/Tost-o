package app.meubolso.domain

import app.meubolso.core.FinancialMonth
import app.meubolso.data.db.Bill
import app.meubolso.data.db.BillPayment
import app.meubolso.data.db.IncomeReceipt
import app.meubolso.data.db.RecurringIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PlanningTest {
    private fun bill(id: Long, amount: Long, day: Int, interval: Int = 1, anchor: YearMonth = YearMonth.of(2026, 1), sub: Boolean = false) =
        Bill(id = id, name = "b$id", amount = amount, categoryId = 1, dueDay = day, intervalMonths = interval, anchorMonth = anchor, isSubscription = sub)

    @Test fun recurrenceEveryN() {
        val a = YearMonth.of(2026, 2)
        assertTrue(Recurrence.occursIn(a, 3, YearMonth.of(2026, 2)))
        assertFalse(Recurrence.occursIn(a, 3, YearMonth.of(2026, 3)))
        assertTrue(Recurrence.occursIn(a, 3, YearMonth.of(2026, 5)))
        assertFalse(Recurrence.occursIn(a, 3, YearMonth.of(2025, 11))) // antes do início
    }

    @Test fun day31InFebruaryFallsOnLastDay() {
        val occ = Recurrence.occurrences(YearMonth.of(2026, 1), 1, 31, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1))
        assertEquals(LocalDate.of(2026, 2, 28), occ.single().second)
    }

    @Test fun annualCountsAsOneTwelfthPerMonth() {
        val t = Planning.totals(listOf(
            bill(1, 120000, 10, interval = 12),          // IPVA anual 1.200
            bill(2, 4490, 5, sub = true),                 // Netflix
            bill(3, 60000, 5, interval = 12, sub = true), // assinatura anual 600
            bill(4, 9000, 5, interval = 3),               // a cada 3 meses
        ))
        assertEquals(10000L + 4490 + 5000 + 3000, t.monthly)
        assertEquals(120000L + 4490 * 12 + 60000 + 36000, t.yearly)
        assertEquals(4490L + 5000, t.subsMonthly)
        assertEquals(4490L * 12 + 60000, t.subsYearly)
    }

    @Test fun pausedBillsDoNotCount() {
        val t = Planning.totals(listOf(bill(1, 1000, 1).copy(active = false)))
        assertEquals(0L, t.monthly)
    }

    @Test fun committedOnlyUnpaidInFinancialMonth() {
        val fm = FinancialMonth(YearMonth.of(2026, 11), 5) // 05/11 a 04/12
        val bills = listOf(bill(1, 1000, 10), bill(2, 2000, 2), bill(3, 3000, 20), bill(4, 50000, 15, interval = 12, anchor = YearMonth.of(2027, 3)))
        val payments = listOf(BillPayment(billId = 3, period = YearMonth.of(2026, 11), amount = 3100, paidDate = LocalDate.of(2026, 11, 19)))
        val occ = Planning.billOccurrences(bills, payments, fm)
        // bill1 em 10/11, bill2 em 02/12 (dentro do mês financeiro), bill3 paga, bill4 só em março.
        assertEquals(listOf(1L, 3L, 2L), occ.map { it.bill.id })
        assertEquals(3000L, Planning.committed(occ))
        assertEquals(BillStatus.OVERDUE, occ.first { it.bill.id == 1L }.status(LocalDate.of(2026, 11, 19)))
        assertEquals(BillStatus.PAID, occ.first { it.bill.id == 3L }.status(LocalDate.of(2026, 11, 19)))
        assertEquals(3100L, occ.first { it.bill.id == 3L }.amount) // valor pago diferente do cadastrado
    }

    @Test fun pendingIncomeIgnoresConfirmed() {
        val fm = FinancialMonth(YearMonth.of(2026, 11))
        val incomes = listOf(
            RecurringIncome(id = 1, name = "Salário", amount = 500000, categoryId = 1, day = 5, anchorMonth = YearMonth.of(2026, 1)),
            RecurringIncome(id = 2, name = "Aluguel recebido", amount = 80000, categoryId = 1, day = 20, anchorMonth = YearMonth.of(2026, 1)),
        )
        val receipts = listOf(IncomeReceipt(incomeId = 1, period = YearMonth.of(2026, 11), amount = 510000))
        assertEquals(80000L, Planning.pendingIncome(Planning.incomeOccurrences(incomes, receipts, fm)))
    }

    @Test fun nextOccurrenceOfAnnual() {
        val (ym, d) = Recurrence.next(YearMonth.of(2027, 3), 12, 15, LocalDate.of(2026, 11, 19))
        assertEquals(YearMonth.of(2027, 3), ym)
        assertEquals(LocalDate.of(2027, 3, 15), d)
    }
}
