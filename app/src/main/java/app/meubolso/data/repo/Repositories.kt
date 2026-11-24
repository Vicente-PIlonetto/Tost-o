package app.meubolso.data.repo

import androidx.room.withTransaction
import app.meubolso.data.DefaultCategories
import app.meubolso.data.db.AppDatabase
import app.meubolso.data.db.BillPayment
import app.meubolso.data.db.Category
import app.meubolso.data.db.IncomeReceipt
import app.meubolso.data.db.DeletedTransaction
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalDate

class CategoryRepository(private val db: AppDatabase) {
    private val dao = db.categories()

    val all = dao.observeAll()
    fun byUsage(type: TxType) = dao.observeByUsage(type, LocalDate.now().minusDays(90).toEpochDay())

    suspend fun ensureDefaults() {
        if (dao.count() == 0) dao.insertAll(DefaultCategories.all)
    }

    suspend fun save(c: Category): Long = if (c.id == 0L) {
        dao.insert(c.copy(sortOrder = dao.maxSort(c.type) + 1))
    } else {
        dao.update(c); c.id
    }

    suspend fun reorder(ordered: List<Category>) =
        dao.updateAll(ordered.mapIndexed { i, c -> c.copy(sortOrder = i) })

    suspend fun findByName(type: TxType, name: String) = dao.findByName(type, name)
}

class TransactionRepository(private val db: AppDatabase, private val wallets: WalletRepository) {
    private val dao = db.transactions()

    /** Emite sempre que uma movimentação é criada/alterada/removida (widget, alertas de limite). */
    private val _changes = MutableSharedFlow<Transaction?>(extraBufferCapacity = 16)
    val changes: SharedFlow<Transaction?> = _changes

    fun observeRange(start: LocalDate, endExclusive: LocalDate) = dao.observeRange(start, endExclusive)
    fun observeLatest(limit: Int) = dao.observeLatest(limit)
    fun observeBalanceBefore(d: LocalDate) = dao.observeBalanceBefore(d)
    val count = dao.observeCount()
    val sampleCount = dao.observeSampleCount()

    suspend fun get(id: Long) = dao.get(id)
    suspend fun getRange(start: LocalDate, endExclusive: LocalDate) = dao.getRange(start, endExclusive)

    suspend fun save(tx: Transaction): Long {
        val t = withWallet(tx)
        val id = if (t.id == 0L) dao.insert(t) else { dao.update(t); t.id }
        _changes.tryEmit(t.copy(id = id))
        return id
    }

    /** O que foi apagado junto (pagamentos de conta / recebimentos), para o "desfazer" voltar tudo. */
    class DeleteHandle(
        val transaction: Transaction,
        val logId: Long,
        val payments: List<BillPayment>,
        val receipts: List<IncomeReceipt>,
    )

    /** Exclui guardando uma cópia e a justificativa. */
    suspend fun deleteWithReason(t: Transaction, reason: String): DeleteHandle {
        val handle = db.withTransaction {
            val payments = db.bills().paymentsForTransaction(t.id)
            val receipts = db.incomes().receiptsForTransaction(t.id)
            val id = db.deleted().insert(DeletedTransaction.of(t, reason.trim()))
            dao.delete(t.id) // pagamentos/recebimentos ligados caem em cascata
            DeleteHandle(t, id, payments, receipts)
        }
        _changes.tryEmit(null)
        return handle
    }

    /** Desfazer logo após excluir: reinsere com o mesmo id, religa pagamentos e apaga o registro. */
    suspend fun undoDelete(h: DeleteHandle) {
        db.withTransaction {
            dao.insert(h.transaction)
            h.payments.forEach { db.bills().insertPayment(it) }
            h.receipts.forEach { db.incomes().insertReceipt(it) }
            db.deleted().delete(h.logId)
        }
        _changes.tryEmit(h.transaction)
    }

    /** Restaurar a partir do registro de excluídos. */
    suspend fun restoreDeleted(d: DeletedTransaction) {
        db.withTransaction {
            val t = d.toTransaction()
            // Se o id original já foi reaproveitado, insere com id novo.
            if (dao.get(t.id) == null) dao.insert(t) else dao.insert(t.copy(id = 0))
            db.deleted().delete(d.id)
        }
        _changes.tryEmit(null)
    }

    val deletedLog = db.deleted().observeAll()
    val deletedCount = db.deleted().observeCount()

    internal fun notifyChanged(t: Transaction?) { _changes.tryEmit(t) }

    /** Lançamento sem carteira vai para a padrão da forma de pagamento (dinheiro, conta). */
    suspend fun withWallet(t: Transaction): Transaction =
        if (t.walletId != null) t else t.copy(walletId = wallets.defaultFor(t.paymentMethod)?.id)

    suspend fun insertAll(list: List<Transaction>) = db.withTransaction {
        dao.insertAll(list.map { withWallet(it) }); _changes.tryEmit(null)
    }

    suspend fun deleteSamples() {
        dao.deleteSamples(); _changes.tryEmit(null)
    }
}
