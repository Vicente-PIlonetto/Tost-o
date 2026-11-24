package app.meubolso.notify

import android.content.Context
import app.meubolso.AppContainer
import app.meubolso.core.FinancialMonth
import app.meubolso.data.db.Notified
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxType
import app.meubolso.domain.Recurrence
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/** Decide quais lembretes mandar. Cada aviso tem uma chave e é enviado uma única vez. */
class Reminders(private val ctx: Context, private val c: AppContainer) {
    private val notified = c.db.notified()

    private suspend fun once(key: String, block: () -> Unit) {
        if (notified.exists(key)) return
        block()
        notified.insert(Notified(key))
    }

    /** Lembretes de contas e confirmação de entradas. Fora do horário (21h–8h) não avisa — fica para a próxima rodada. */
    suspend fun runChecks(now: LocalDateTime = LocalDateTime.now()) {
        if (now.hour < 8 || now.hour >= 21) return
        if (!Notifications.canPost(ctx)) return
        val s = c.settings.current()
        val today = now.toLocalDate()

        if (s.notifyBills) {
            val bills = c.db.bills().getAll().filter { it.active }
            val allPayments = c.db.bills().getPayments()
            val payments = allPayments.associateBy { it.billId to it.period }
            for (b in bills) {
                val est = app.meubolso.domain.Planning.estimate(b, allPayments)
                val window = Recurrence.occurrences(b.anchorMonth, b.intervalMonths, b.dueDay, today.minusDays(3), today.plusDays(b.remindDaysBefore + 1L))
                for ((period, due) in window) {
                    if (payments.containsKey(b.id to period)) continue
                    val base = "bill:${b.id}:$period"
                    when {
                        today.isBefore(due) && !today.isBefore(due.minusDays(b.remindDaysBefore.toLong())) ->
                            once("$base:pre") { Notifications.showBill(ctx, b, period, due, today, est) }
                        today == due -> once("$base:due") { Notifications.showBill(ctx, b, period, due, today, est) }
                        today.isAfter(due) -> once("$base:late") { Notifications.showBill(ctx, b, period, due, today, est) }
                    }
                }
            }
        }

        if (s.notifyIncome) {
            val incomes = c.db.incomes().getAll().filter { it.active }
            val receipts = c.db.incomes().getReceipts().associateBy { it.incomeId to it.period }
            for (i in incomes) {
                for ((period, date) in Recurrence.occurrences(i.anchorMonth, 1, i.day, today.minusDays(5), today.plusDays(1))) {
                    if (receipts.containsKey(i.id to period)) continue
                    once("income:${i.id}:$period") { Notifications.showIncome(ctx, i, period) }
                }
            }
        }
        notified.purgeOlderThan(System.currentTimeMillis() - 400L * 24 * 3600 * 1000)
    }

    /** Alerta de limite (80% e 100%) para a categoria do lançamento salvo. */
    suspend fun checkLimit(t: Transaction) {
        if (t.type != TxType.EXPENSE) return
        val s = c.settings.current()
        if (!s.notifyLimits) return
        val cat = c.db.categories().get(t.categoryId) ?: return
        val limit = cat.monthlyLimit ?: return
        if (limit <= 0) return
        val fm = FinancialMonth.of(t.date, s.monthStartDay)
        if (LocalDate.now() !in fm) return // lançamento de mês passado não dispara alerta
        val spent = c.db.transactions().expenseSum(cat.id, fm.start, fm.endExclusive)
        val pct = (spent * 100 / limit).toInt()
        val threshold = when { pct >= 100 -> 100; pct >= 80 -> 80; else -> return }
        once("limit:${cat.id}:${fm.ym}:$threshold") {
            // Se pulou direto para 100%, não manda também o de 80%.
            Notifications.showLimit(ctx, cat, spent, limit, pct)
        }
        if (threshold == 100) notified.insert(Notified("limit:${cat.id}:${fm.ym}:80"))
    }

    suspend fun todayCount(): Int = c.db.transactions().countOn(LocalDate.now())

    @Suppress("unused")
    private suspend fun settings() = c.settings.settings.first()
}
