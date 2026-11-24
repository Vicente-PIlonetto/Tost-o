package app.meubolso.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.meubolso.AppContainer
import app.meubolso.core.FinancialMonth
import app.meubolso.data.db.Bill
import app.meubolso.data.db.BillPayment
import app.meubolso.data.db.Category
import app.meubolso.data.db.IncomeReceipt
import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.RecurringIncome
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxType
import app.meubolso.domain.BillOccurrence
import app.meubolso.domain.IncomeOccurrence
import app.meubolso.domain.MonthSummary
import app.meubolso.domain.Planning
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LimitProgress(val category: Category, val spent: Long) {
    val limit: Long get() = category.monthlyLimit ?: 0
    val fraction: Float get() = if (limit <= 0) 0f else spent.toFloat() / limit
}

data class HomeState(
    val month: FinancialMonth,
    val today: LocalDate,
    val summary: MonthSummary = MonthSummary.EMPTY,
    val nearLimit: List<LimitProgress> = emptyList(),
    val latest: List<Transaction> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val isCurrentMonth: Boolean = true,
    val hasAnyTransaction: Boolean = true,
    /** Atrasadas + que vencem nos próximos 7 dias (não pagas). */
    val upcomingBills: List<BillOccurrence> = emptyList(),
    /** Entradas fixas do mês que ainda não foram confirmadas. */
    val pendingIncomes: List<IncomeOccurrence> = emptyList(),
    val savingsBalance: Long = 0,
    val savingsGoal: Long = 0,
)

private data class Plan(
    val bills: List<Bill>, val payments: List<BillPayment>,
    val incomes: List<RecurringIncome>, val receipts: List<IncomeReceipt>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    /** Deslocamento em meses em relação ao mês financeiro atual. */
    private val offset = MutableStateFlow(0L)

    private val month = combine(c.settings.settings, offset) { s, off ->
        FinancialMonth.of(LocalDate.now(), s.monthStartDay).let { m -> m.copy(ym = m.ym.plusMonths(off)) }
    }

    private val plan = combine(c.bills.allBills, c.bills.payments, c.bills.allIncomes, c.bills.receipts, ::Plan)

    val state: StateFlow<HomeState?> = month.flatMapLatest { m ->
        val txData = combine(
            c.transactions.observeRange(m.start, m.endExclusive),
            c.categories.all,
            c.transactions.observeLatest(5),
            c.transactions.count,
            c.wallets.all,
        ) { txs, cats, latest, count, wallets ->
            // Gasto no vale não sai do salário: fica fora do "livre pra gastar" (o vale tem o próprio saldo).
            val own = txs.filter { app.meubolso.domain.Wallets.resolve(it.walletId, it.paymentMethod, wallets)?.kind != app.meubolso.data.db.WalletKind.VOUCHER }
            TxData(own, cats, latest, count, txs)
        }
        val savings = combine(c.savings.balance, c.savings.netInRange(m.start, m.endExclusive), c.settings.settings) { b, n, s -> Triple(b, n, s.savingsGoal) }
        combine(txData, plan, savings) { d, p, sv -> build(m, d, p, sv) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private data class TxData(
        val txs: List<Transaction>, val cats: List<Category>, val latest: List<Transaction>, val count: Int,
        /** Todos do mês, com o vale (para os limites das categorias). */
        val allTxs: List<Transaction>,
    )

    private fun build(m: FinancialMonth, d: TxData, p: Plan, sv: Triple<Long, Long, Long>): HomeState {
        val today = LocalDate.now()
        val income = d.txs.filter { it.type == TxType.INCOME }.sumOf { it.amount }
        val expense = d.txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
        val byCat = d.allTxs.filter { it.type == TxType.EXPENSE }.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amount } }
        val near = d.cats.filter { it.type == TxType.EXPENSE && !it.archived && (it.monthlyLimit ?: 0) > 0 }
            .map { LimitProgress(it, byCat[it.id] ?: 0) }
            .filter { it.fraction >= 0.7f }
            .sortedByDescending { it.fraction }
        val billOcc = Planning.billOccurrences(p.bills, p.payments, m)
        val incOcc = Planning.incomeOccurrences(p.incomes, p.receipts, m)

        // Próximas contas: sempre a partir de hoje (atrasadas do mês atual + próximos 7 dias).
        val current = FinancialMonth.of(today, m.startDay)
        val upcoming = (Planning.billOccurrences(p.bills, p.payments, current.start, today) +
            Planning.billOccurrences(p.bills, p.payments, today, today.plusDays(8)))
            .filter { it.payment == null }.distinctBy { it.bill.id to it.period }.sortedBy { it.dueDate }

        return HomeState(
            month = m, today = today,
            summary = MonthSummary(
                incomeReceived = income, incomePending = Planning.pendingIncome(incOcc),
                expenses = expense, committed = Planning.committed(billOcc), saved = sv.second,
            ),
            nearLimit = near,
            latest = d.latest,
            categories = d.cats.associateBy { it.id },
            isCurrentMonth = today in m,
            hasAnyTransaction = d.count > 0,
            upcomingBills = upcoming,
            pendingIncomes = if (today in m) incOcc.filter { !it.received } else emptyList(),
            savingsBalance = sv.first, savingsGoal = sv.third,
        )
    }

    fun prev() { offset.value -= 1 }
    fun next() { offset.value += 1 }
    fun today() { offset.value = 0 }

    fun markPaid(o: BillOccurrence, amount: Long, date: LocalDate, pm: app.meubolso.domain.PaymentChoice, createTx: Boolean) = viewModelScope.launch {
        c.bills.markPaid(o.bill, o.period, amount, date, pm, createTx)
    }
    fun confirmIncome(o: IncomeOccurrence, amount: Long, date: LocalDate) = viewModelScope.launch {
        c.bills.confirmIncome(o.income, o.period, amount, date)
    }
}
