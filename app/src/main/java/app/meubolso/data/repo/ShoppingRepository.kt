package app.meubolso.data.repo

import androidx.room.withTransaction
import app.meubolso.data.db.AppDatabase
import app.meubolso.data.db.ListEntry
import app.meubolso.data.db.MarketSection
import app.meubolso.domain.PaymentChoice
import app.meubolso.data.db.Purchase
import app.meubolso.data.db.ShoppingItem
import app.meubolso.data.db.ShoppingTrip
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxOrigin
import app.meubolso.data.db.TxType
import java.time.LocalDate

class ShoppingRepository(private val db: AppDatabase, private val txs: TransactionRepository) {
    private val dao = db.shopping()

    val sections = dao.observeSections()
    val items = dao.observeItems()
    val list = dao.observeList()
    val purchases = dao.observePurchases()
    val trips = dao.observeTrips()

    suspend fun ensureDefaultSections() {
        if (dao.sectionCount() == 0) dao.insertSections(DEFAULT_SECTIONS.mapIndexed { i, n -> MarketSection(name = n, sortOrder = i) })
    }

    // Seções
    suspend fun saveSection(s: MarketSection): Long =
        if (s.id == 0L) dao.insertSection(s.copy(sortOrder = dao.maxSectionOrder() + 1)) else { dao.updateSection(s); s.id }

    suspend fun reorderSections(ordered: List<MarketSection>) = dao.updateSections(ordered.mapIndexed { i, s -> s.copy(sortOrder = i) })

    /** Remove a seção movendo os itens dela para [moveTo]. */
    suspend fun deleteSection(s: MarketSection, moveTo: MarketSection) = db.withTransaction {
        dao.moveItemsToSection(s.id, moveTo.id); dao.deleteSection(s.id)
    }

    // Catálogo e lista
    suspend fun findItem(name: String) = dao.itemByName(name.trim())

    suspend fun createItem(name: String, sectionId: Long, unit: String): Long =
        dao.itemByName(name.trim())?.id ?: dao.insertItem(ShoppingItem(name = name.trim().replaceFirstChar { it.uppercase() }, sectionId = sectionId, unit = unit))

    suspend fun updateItem(i: ShoppingItem) = dao.updateItem(i)
    suspend fun deleteItem(i: ShoppingItem) = dao.deleteItem(i.id)

    suspend fun addToList(itemId: Long, quantity: String = "") = dao.insertEntry(ListEntry(itemId = itemId, quantity = quantity))
    suspend fun updateEntry(e: ListEntry) = dao.updateEntry(e)
    suspend fun removeEntry(e: ListEntry) = dao.deleteEntry(e.id)
    suspend fun restoreEntry(e: ListEntry) = dao.insertEntry(e)

    suspend fun setChecked(e: ListEntry, checked: Boolean) =
        dao.updateEntry(e.copy(checked = checked, checkedAt = if (checked) System.currentTimeMillis() else null, price = if (checked) e.price else null))

    /**
     * Finalizar compra: cria a saída na categoria Mercado (origem "compra"), registra a data (e o preço)
     * de cada item marcado e tira os marcados da lista. Não marcados continuam.
     */
    suspend fun finish(total: Long, date: LocalDate, payment: PaymentChoice, categoryId: Long): Int {
        var created: Transaction? = null
        val count = db.withTransaction {
            val checked = dao.checkedEntries()
            val t = txs.withWallet(Transaction(
                type = TxType.EXPENSE, amount = total, categoryId = categoryId, date = date, walletId = payment.walletId,
                description = "Compra do mercado" + if (checked.isNotEmpty()) " (${checked.size} ${if (checked.size == 1) "item" else "itens"})" else "",
                paymentMethod = payment.method, origin = TxOrigin.COMPRA,
            ))
            val txId = db.transactions().insert(t)
            created = t.copy(id = txId)
            val tripId = dao.insertTrip(ShoppingTrip(date = date, total = total, paymentMethod = payment.method, transactionId = txId, itemCount = checked.size))
            dao.insertPurchases(checked.map { Purchase(itemId = it.itemId, date = date, price = it.price, quantity = it.quantity, tripId = tripId) })
            dao.deleteChecked()
            checked.size
        }
        txs.notifyChanged(created)
        return count
    }

    suspend fun tripItems(tripId: Long) = dao.tripItems(tripId)

    suspend fun deleteSamples() { dao.deleteSampleTrips(); dao.deleteSampleItems() }

    companion object {
        val DEFAULT_SECTIONS = listOf(
            "Hortifrúti", "Padaria", "Açougue", "Frios e laticínios", "Mercearia",
            "Bebidas", "Limpeza", "Higiene", "Congelados", "Outros",
        )
        val UNITS = listOf("", "un", "kg", "g", "L", "ml", "pct", "cx", "dz")
    }
}
