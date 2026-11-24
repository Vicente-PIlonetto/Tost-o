package app.meubolso.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.meubolso.AppContainer
import app.meubolso.core.FinancialMonth
import app.meubolso.data.db.Category
import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate

data class Filters(
    val type: TxType? = null,
    val categoryId: Long? = null,
    val walletId: Long? = null,
    val query: String = "",
) {
    val active: Boolean get() = type != null || categoryId != null || walletId != null || query.isNotBlank()
}

data class DayGroup(val date: LocalDate, val items: List<Transaction>) {
    val total: Long get() = items.sumOf { it.signedAmount }
}

data class TxListState(
    val month: FinancialMonth,
    val filters: Filters,
    val days: List<DayGroup>,
    val totalIncome: Long,
    val totalExpense: Long,
    val categories: Map<Long, Category>,
)

data class CategorySlice(val category: Category?, val total: Long)
data class MonthBar(val month: FinancialMonth, val income: Long, val expense: Long, val endBalance: Long)

data class ChartsState(
    val month: FinancialMonth,
    val slices: List<CategorySlice>,
    val bars: List<MonthBar>,
)

fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(private val c: AppContainer) : ViewModel() {
    private val offset = MutableStateFlow(0L)
    private val filters = MutableStateFlow(Filters())

    private val month = combine(c.settings.settings, offset) { s, off ->
        FinancialMonth.of(LocalDate.now(), s.monthStartDay).let { it.copy(ym = it.ym.plusMonths(off)) }
    }

    val state: StateFlow<TxListState?> = combine(month, filters) { m, f -> m to f }.flatMapLatest { (m, f) ->
        combine(c.transactions.observeRange(m.start, m.endExclusive), c.categories.all, c.wallets.all) { txs, cats, wallets ->
            val q = f.query.normalized().trim()
            val catMap = cats.associateBy { it.id }
            val filtered = txs.filter { t ->
                (f.type == null || t.type == f.type) &&
                    (f.categoryId == null || t.categoryId == f.categoryId) &&
                    (f.walletId == null || app.meubolso.domain.Wallets.resolve(t.walletId, t.paymentMethod, wallets)?.id == f.walletId) &&
                    (q.isEmpty() || t.description.normalized().contains(q) ||
                        (catMap[t.categoryId]?.name?.normalized()?.contains(q) == true))
            }
            TxListState(
                month = m, filters = f,
                days = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it }).map { DayGroup(it.key, it.value) },
                totalIncome = filtered.filter { it.type == TxType.INCOME }.sumOf { it.amount },
                totalExpense = filtered.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
                categories = catMap,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val charts: StateFlow<ChartsState?> = month.flatMapLatest { m ->
        val months = (5 downTo 0).map { m.copy(ym = m.ym.minusMonths(it.toLong())) }
        val first = months.first()
        combine(
            c.transactions.observeRange(first.start, m.endExclusive),
            c.transactions.observeBalanceBefore(first.start),
            c.categories.all,
        ) { txs, startBalance, cats ->
            val catMap = cats.associateBy { it.id }
            val slices = txs.filter { it.type == TxType.EXPENSE && it.date in m }
                .groupBy { it.categoryId }
                .map { (id, l) -> CategorySlice(catMap[id], l.sumOf { it.amount }) }
                .sortedByDescending { it.total }
            var running = startBalance
            val bars = months.map { fm ->
                val inMonth = txs.filter { it.date in fm }
                val inc = inMonth.filter { it.type == TxType.INCOME }.sumOf { it.amount }
                val exp = inMonth.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
                running += inc - exp
                MonthBar(fm, inc, exp, running)
            }
            ChartsState(m, slices, bars)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun prev() { offset.value -= 1 }
    fun next() { offset.value += 1 }

    fun setType(t: TxType?) = filters.update { it.copy(type = t, categoryId = null) }
    fun setCategory(id: Long?) = filters.update { it.copy(categoryId = id) }
    fun setWallet(id: Long?) = filters.update { it.copy(walletId = id) }
    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun clearFilters() { filters.value = Filters() }

}
