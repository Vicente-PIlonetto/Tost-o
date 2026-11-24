package app.meubolso.data.repo

import app.meubolso.data.db.AppDatabase
import app.meubolso.data.db.SavingsMovement
import java.time.LocalDate

/** Poupança / reserva: dinheiro guardado, separado do "livre pra gastar". */
class SavingsRepository(db: AppDatabase) {
    private val dao = db.savings()
    val movements = dao.observeAll()
    val balance = dao.observeBalance()
    fun netInRange(start: LocalDate, endExclusive: LocalDate) = dao.observeNetInRange(start, endExclusive)

    suspend fun save(m: SavingsMovement): Long = if (m.id == 0L) dao.insert(m) else { dao.update(m); m.id }
    suspend fun delete(m: SavingsMovement) = dao.delete(m.id)
    suspend fun restore(m: SavingsMovement) = dao.insert(m)
}
