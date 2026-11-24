package app.meubolso.data.repo

import androidx.room.withTransaction
import app.meubolso.data.DefaultWallets
import app.meubolso.data.db.AppDatabase
import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.Wallet
import app.meubolso.data.db.WalletTransfer
import app.meubolso.domain.WalletStatus
import app.meubolso.domain.Wallets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

class WalletRepository(private val db: AppDatabase) {
    private val dao = db.wallets()

    /** Todas, arquivadas no fim. */
    val all: Flow<List<Wallet>> = dao.observeAll()
    val transfers = dao.observeTransfers()

    /** Situação de cada carteira (saldo, fatura, vale) calculada para hoje. */
    val statuses: Flow<List<WalletStatus>> = combine(all, dao.observeFlows(), transfers) { w, f, t ->
        Wallets.statuses(w, f, t, LocalDate.now())
    }

    suspend fun ensureDefaults() {
        if (dao.count() == 0) dao.insertAll(DefaultWallets.all)
    }

    suspend fun get(id: Long) = dao.get(id)

    /** Carteira para um lançamento criado sem escolher (contas fixas antigas, dados de exemplo). */
    suspend fun defaultFor(payment: PaymentMethod?): Wallet? = Wallets.defaultFor(payment, dao.getAll())

    suspend fun save(w: Wallet): Long = if (w.id == 0L) {
        dao.insert(w.copy(sortOrder = dao.maxSort() + 1))
    } else {
        dao.update(w); w.id
    }

    suspend fun reorder(ordered: List<Wallet>) = dao.updateAll(ordered.mapIndexed { i, w -> w.copy(sortOrder = i) })

    suspend fun usage(id: Long) = dao.usage(id)

    /**
     * Apaga a carteira. Os lançamentos dela passam para [moveTo]; as transferências dela somem.
     * Carteira com lançamentos e sem destino não é apagada (arquive).
     */
    suspend fun delete(w: Wallet, moveTo: Long?) = db.withTransaction {
        if (moveTo != null) dao.moveTransactions(w.id, moveTo)
        else check(dao.usage(w.id) == 0) { "carteira em uso" }
        dao.delete(w.id)
    }

    /** Pagar fatura, sacar, passar de uma conta para outra. */
    suspend fun transfer(from: Long, to: Long, amount: Long, date: LocalDate, note: String = "") =
        dao.insertTransfer(WalletTransfer(fromWalletId = from, toWalletId = to, amount = amount, date = date, note = note))

    suspend fun deleteTransfer(t: WalletTransfer) = dao.deleteTransfer(t.id)
}
