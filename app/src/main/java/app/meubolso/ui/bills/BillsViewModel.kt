package app.meubolso.ui.bills

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
import app.meubolso.data.db.TxType
import app.meubolso.domain.BillOccurrence
import app.meubolso.domain.BillTotals
import app.meubolso.domain.IncomeOccurrence
import app.meubolso.domain.Planning
import app.meubolso.domain.Recurrence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Linha da lista de contas: a ocorrência no mês (se houver) ou a próxima data. */
data class BillRow(val bill: Bill, val occurrence: BillOccurrence?, val nextDue: LocalDate)

data class BillsState(
    val month: FinancialMonth,
    val today: LocalDate,
    val rows: List<BillRow>,
    val occurrences: List<BillOccurrence>,
    val totals: BillTotals,
    val incomes: List<Pair<RecurringIncome, IncomeOccurrence?>>,
    val categories: Map<Long, Category>,
    val committed: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BillsViewModel(private val c: AppContainer) : ViewModel() {
    private val offset = MutableStateFlow(0L)

    private val month = combine(c.settings.settings, offset) { s, off ->
        FinancialMonth.of(LocalDate.now(), s.monthStartDay).let { it.copy(ym = it.ym.plusMonths(off)) }
    }

    private val data = combine(c.bills.allBills, c.bills.payments, c.bills.allIncomes, c.bills.receipts, c.categories.all) { b, p, i, r, cats ->
        Data(b, p, i, r, cats)
    }

    private data class Data(
        val bills: List<Bill>, val payments: List<BillPayment>, val incomes: List<RecurringIncome>,
        val receipts: List<IncomeReceipt>, val cats: List<Category>,
    )

    val state: StateFlow<BillsState?> = combine(month, data) { m, d ->
        val today = LocalDate.now()
        val occ = Planning.billOccurrences(d.bills, d.payments, m)
        val occByBill = occ.groupBy { it.bill.id }
        val rows = d.bills.map { b ->
            val o = occByBill[b.id]?.firstOrNull()
            BillRow(b, o, o?.dueDate ?: Recurrence.next(b.anchorMonth, b.intervalMonths, b.dueDay, maxOf(today, m.start)).second)
        }.sortedWith(compareBy({ !it.bill.active }, { it.occurrence == null }, { it.nextDue }))
        val incOcc = Planning.incomeOccurrences(d.incomes, d.receipts, m).associateBy { it.income.id }
        BillsState(
            month = m, today = today, rows = rows, occurrences = occ,
            totals = Planning.totals(d.bills, d.payments),
            incomes = d.incomes.map { it to incOcc[it.id] },
            categories = d.cats.associateBy { it.id },
            committed = Planning.committed(occ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun prev() { offset.value -= 1 }
    fun next() { offset.value += 1 }

    fun newBill(): Bill {
        val cats = state.value?.categories?.values.orEmpty()
        val cat = cats.firstOrNull { it.type == TxType.EXPENSE && it.name == "Contas da casa" && !it.archived }
            ?: cats.first { it.type == TxType.EXPENSE && !it.archived }
        return Bill(name = "", amount = 0, categoryId = cat.id, dueDay = LocalDate.now().dayOfMonth.coerceAtMost(28), anchorMonth = YearMonth.now())
    }

    fun newIncome(): RecurringIncome {
        val cats = state.value?.categories?.values.orEmpty()
        val cat = cats.firstOrNull { it.type == TxType.INCOME && it.name == "Salário" && !it.archived }
            ?: cats.first { it.type == TxType.INCOME && !it.archived }
        return RecurringIncome(name = "Salário", amount = 0, categoryId = cat.id, day = 5, anchorMonth = YearMonth.now())
    }

    fun saveBill(b: Bill, onDone: () -> Unit = {}) = viewModelScope.launch { c.bills.saveBill(b); onDone() }
    fun deleteBill(b: Bill) = viewModelScope.launch { c.bills.deleteBill(b) }
    fun saveIncome(i: RecurringIncome, onDone: () -> Unit = {}) = viewModelScope.launch { c.bills.saveIncome(i); onDone() }
    fun deleteIncome(i: RecurringIncome) = viewModelScope.launch { c.bills.deleteIncome(i) }

    fun markPaid(o: BillOccurrence, amount: Long, date: LocalDate, pm: app.meubolso.domain.PaymentChoice, createTx: Boolean) = viewModelScope.launch {
        c.bills.markPaid(o.bill, o.period, amount, date, pm, createTx)
    }
    fun unmark(o: BillOccurrence) = viewModelScope.launch { o.payment?.let { c.bills.unmarkPaid(it, o.bill.name) } }

    fun confirmIncome(o: IncomeOccurrence, amount: Long, date: LocalDate) = viewModelScope.launch {
        c.bills.confirmIncome(o.income, o.period, amount, date)
    }
    fun undoIncome(o: IncomeOccurrence) = viewModelScope.launch { o.receipt?.let { c.bills.undoIncome(it, o.income.name) } }
}
