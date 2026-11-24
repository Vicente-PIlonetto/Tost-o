package app.meubolso.domain

import app.meubolso.core.Dates
import app.meubolso.core.FinancialMonth
import app.meubolso.data.db.Bill
import app.meubolso.data.db.BillPayment
import app.meubolso.data.db.IncomeReceipt
import app.meubolso.data.db.RecurringIncome
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** Regras de recorrência mensal (mensal, anual, a cada N meses). */
object Recurrence {
    fun occursIn(anchor: YearMonth, interval: Int, ym: YearMonth): Boolean {
        if (ym < anchor) return false
        return anchor.until(ym, ChronoUnit.MONTHS) % interval.coerceAtLeast(1) == 0L
    }

    /** Ocorrências (mês, data de vencimento) com data em [from, toExclusive). */
    fun occurrences(anchor: YearMonth, interval: Int, day: Int, from: LocalDate, toExclusive: LocalDate): List<Pair<YearMonth, LocalDate>> {
        val out = mutableListOf<Pair<YearMonth, LocalDate>>()
        var ym = YearMonth.from(from)
        val last = YearMonth.from(toExclusive.minusDays(1))
        while (ym <= last) {
            if (occursIn(anchor, interval, ym)) {
                val d = Dates.dayIn(ym, day)
                if (!d.isBefore(from) && d.isBefore(toExclusive)) out += ym to d
            }
            ym = ym.plusMonths(1)
        }
        return out
    }

    /** Próxima ocorrência com vencimento em ou depois de [from]. */
    fun next(anchor: YearMonth, interval: Int, day: Int, from: LocalDate): Pair<YearMonth, LocalDate> {
        var ym = maxOf(YearMonth.from(from), anchor)
        repeat(240) {
            if (occursIn(anchor, interval, ym)) {
                val d = Dates.dayIn(ym, day)
                if (!d.isBefore(from)) return ym to d
            }
            ym = ym.plusMonths(1)
        }
        return anchor to Dates.dayIn(anchor, day)
    }

    /** Custo mensal equivalente: anual entra dividido por 12, "a cada N meses" dividido por N. */
    fun monthlyCost(amount: Long, interval: Int): Long = (amount.toDouble() / interval.coerceAtLeast(1)).roundToLong()

    fun yearlyCost(amount: Long, interval: Int): Long = (amount * 12.0 / interval.coerceAtLeast(1)).roundToLong()
}

enum class BillStatus { PAID, DUE, OVERDUE }

data class BillOccurrence(
    val bill: Bill,
    val period: YearMonth,
    val dueDate: LocalDate,
    val payment: BillPayment?,
    /** Valor previsto: o cadastrado, ou — se for de valor variável sem estimativa — a média dos últimos pagamentos. */
    val estimate: Long = bill.amount,
) {
    fun status(today: LocalDate): BillStatus = when {
        payment != null -> BillStatus.PAID
        dueDate.isBefore(today) -> BillStatus.OVERDUE
        else -> BillStatus.DUE
    }
    /** Valor efetivo: o pago (se pago) ou o previsto. */
    val amount: Long get() = payment?.amount ?: estimate
}

data class IncomeOccurrence(val income: RecurringIncome, val period: YearMonth, val date: LocalDate, val receipt: IncomeReceipt?) {
    val received: Boolean get() = receipt != null
}

data class BillTotals(val monthly: Long, val yearly: Long, val subsMonthly: Long, val subsYearly: Long)

object Planning {
    /**
     * Valor previsto da conta. Fixa: o cadastrado. Variável: a estimativa cadastrada, ou a média
     * dos últimos 3 pagamentos, ou 0 se nunca foi paga.
     */
    fun estimate(bill: Bill, payments: List<BillPayment>): Long {
        if (!bill.variableAmount || bill.amount > 0) return bill.amount
        val last = payments.filter { it.billId == bill.id }.sortedByDescending { it.period }.take(3)
        return if (last.isEmpty()) 0 else last.sumOf { it.amount } / last.size
    }

    fun billOccurrences(bills: List<Bill>, payments: List<BillPayment>, from: LocalDate, toExclusive: LocalDate): List<BillOccurrence> {
        val byKey = payments.associateBy { it.billId to it.period }
        return bills.filter { it.active }.flatMap { b ->
            val est = estimate(b, payments)
            Recurrence.occurrences(b.anchorMonth, b.intervalMonths, b.dueDay, from, toExclusive)
                .map { (ym, d) -> BillOccurrence(b, ym, d, byKey[b.id to ym], est) }
        }.sortedBy { it.dueDate }
    }

    fun billOccurrences(bills: List<Bill>, payments: List<BillPayment>, fm: FinancialMonth) =
        billOccurrences(bills, payments, fm.start, fm.endExclusive)

    fun incomeOccurrences(incomes: List<RecurringIncome>, receipts: List<IncomeReceipt>, fm: FinancialMonth): List<IncomeOccurrence> {
        val byKey = receipts.associateBy { it.incomeId to it.period }
        return incomes.filter { it.active }.flatMap { i ->
            Recurrence.occurrences(i.anchorMonth, 1, i.day, fm.start, fm.endExclusive)
                .map { (ym, d) -> IncomeOccurrence(i, ym, d, byKey[i.id to ym]) }
        }.sortedBy { it.date }
    }

    /** Comprometido: contas do mês ainda não pagas (a vencer ou atrasadas). */
    fun committed(occ: List<BillOccurrence>): Long = occ.filter { it.payment == null }.sumOf { it.estimate }

    /** Entradas previstas que ainda não caíram. */
    fun pendingIncome(occ: List<IncomeOccurrence>): Long = occ.filter { !it.received }.sumOf { it.income.amount }

    fun totals(bills: List<Bill>, payments: List<BillPayment> = emptyList()): BillTotals {
        val active = bills.filter { it.active }
        val subs = active.filter { it.isSubscription }
        fun m(b: Bill) = Recurrence.monthlyCost(estimate(b, payments), b.intervalMonths)
        fun y(b: Bill) = Recurrence.yearlyCost(estimate(b, payments), b.intervalMonths)
        return BillTotals(
            monthly = active.sumOf(::m), yearly = active.sumOf(::y),
            subsMonthly = subs.sumOf(::m), subsYearly = subs.sumOf(::y),
        )
    }
}
