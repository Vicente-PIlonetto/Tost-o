package app.meubolso.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.meubolso.AppContainer
import app.meubolso.data.db.ListEntry
import app.meubolso.data.db.MarketSection
import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.Purchase
import app.meubolso.data.db.ShoppingItem
import app.meubolso.data.db.ShoppingTrip
import app.meubolso.data.db.TxType
import app.meubolso.domain.ShoppingSuggestions
import app.meubolso.ui.transactions.normalized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EntryUi(val entry: ListEntry, val item: ShoppingItem, val lastPrice: Purchase?)
data class SectionGroup(val section: MarketSection, val entries: List<EntryUi>)
data class Suggestion(val item: ShoppingItem, val stats: ShoppingSuggestions.Stats)

data class ShoppingState(
    val groups: List<SectionGroup>,
    val suggestions: List<Suggestion>,
    val catalog: List<ShoppingItem>,
    val inList: Set<Long>,
    val sections: List<MarketSection>,
    val checkedCount: Int,
    /** Soma dos preços anotados nos itens marcados (sugestão para o total do cupom). */
    val checkedPriceSum: Long,
    val trips: List<ShoppingTrip>,
    val totalCount: Int,
)

class ShoppingViewModel(private val c: AppContainer) : ViewModel() {
    /** Nome digitado que não existe no catálogo: a UI pergunta a seção (uma vez só). */
    val askSectionFor = MutableStateFlow<String?>(null)

    private val base = combine(c.shopping.sections, c.shopping.items, c.shopping.list, c.shopping.purchases) { s, i, l, p -> Base(s, i, l, p) }
    private data class Base(val sections: List<MarketSection>, val items: List<ShoppingItem>, val list: List<ListEntry>, val purchases: List<Purchase>)

    val state: StateFlow<ShoppingState?> = combine(base, c.shopping.trips) { b, trips ->
        val today = LocalDate.now()
        val itemMap = b.items.associateBy { it.id }
        val lastPurchase = b.purchases.filter { it.price != null }.groupBy { it.itemId }
            .mapValues { (_, l) -> l.maxWith(compareBy({ it.date }, { it.id })) }
        val bySection = b.list.mapNotNull { e -> itemMap[e.itemId]?.let { EntryUi(e, it, lastPurchase[it.id]) } }
            .groupBy { it.item.sectionId }
        val groups = b.sections.mapNotNull { s ->
            bySection[s.id]?.let { list ->
                // Não marcados primeiro (ordem de inclusão); marcados vão para o fim da seção.
                SectionGroup(s, list.sortedWith(compareBy({ it.entry.checked }, { it.entry.checkedAt ?: it.entry.addedAt })))
            }
        }
        val inList = b.list.map { it.itemId }.toSet()
        val suggestions = ShoppingSuggestions.suggest(
            b.purchases.groupBy({ it.itemId }, { it.date }), inList, today,
        ).mapNotNull { st -> itemMap[st.itemId]?.let { Suggestion(it, st) } }
        val checked = b.list.filter { it.checked }
        ShoppingState(
            groups = groups, suggestions = suggestions, catalog = b.items, inList = inList, sections = b.sections,
            checkedCount = checked.size, checkedPriceSum = checked.sumOf { it.price ?: 0 },
            trips = trips, totalCount = b.list.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Autocompletar: itens do catálogo que combinam com o texto e ainda não estão na lista. */
    fun matches(query: String): List<ShoppingItem> {
        val s = state.value ?: return emptyList()
        val q = query.normalized().trim()
        if (q.isEmpty()) return emptyList()
        return s.catalog.filter { it.id !in s.inList && it.name.normalized().contains(q) }
            .sortedBy { if (it.name.normalized().startsWith(q)) 0 else 1 }
            .take(6)
    }

    fun add(name: String) = viewModelScope.launch {
        val n = name.trim()
        if (n.isEmpty()) return@launch
        val existing = c.shopping.findItem(n)
        if (existing != null) c.shopping.addToList(existing.id) else askSectionFor.value = n
    }

    fun addItem(item: ShoppingItem) = viewModelScope.launch { c.shopping.addToList(item.id) }

    fun createAndAdd(name: String, sectionId: Long, unit: String) = viewModelScope.launch {
        askSectionFor.value = null
        c.shopping.addToList(c.shopping.createItem(name, sectionId, unit))
    }

    fun toggle(e: ListEntry) = viewModelScope.launch { c.shopping.setChecked(e, !e.checked) }
    fun setPrice(e: ListEntry, price: Long?) = viewModelScope.launch { c.shopping.updateEntry(e.copy(price = price)) }
    fun updateEntry(e: ListEntry, item: ShoppingItem) = viewModelScope.launch {
        c.shopping.updateEntry(e); c.shopping.updateItem(item)
    }
    fun remove(e: ListEntry) = viewModelScope.launch { c.shopping.removeEntry(e) }
    fun restore(e: ListEntry) = viewModelScope.launch { c.shopping.restoreEntry(e) }

    fun finish(total: Long, date: LocalDate, payment: app.meubolso.domain.PaymentChoice, onDone: (Int) -> Unit) = viewModelScope.launch {
        val cats = c.categories.all.first()
        val mercado = cats.firstOrNull { it.type == TxType.EXPENSE && !it.archived && it.name.equals("Mercado", true) }
            ?: cats.first { it.type == TxType.EXPENSE && !it.archived }
        onDone(c.shopping.finish(total, date, payment, mercado.id))
    }

    suspend fun tripItems(id: Long) = c.shopping.tripItems(id)
}
