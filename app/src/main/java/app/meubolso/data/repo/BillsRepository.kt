package app.meubolso.data.repo

import androidx.room.withTransaction
import app.meubolso.data.db.AppDatabase
import app.meubolso.data.db.Bill
import app.meubolso.data.db.BillPayment
import app.meubolso.data.db.IncomeReceipt
import app.meubolso.domain.PaymentChoice
import app.meubolso.data.db.RecurringIncome
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxOrigin
import app.meubolso.data.db.TxType
import java.time.LocalDate
import java.time.YearMonth

/** Contas fixas, assinaturas e entradas recorrentes. */
class BillsRepository(private val db: AppDatabase, private val txs: TransactionRepository) {
    private val bills = db.bills()
    private val incomes = db.incomes()

    val allBills = bills.observeAll()
    val payments = bills.observePayments()
    val allIncomes = incomes.observeAll()
    val receipts = incomes.observeReceipts()

    suspend fun getBill(id: Long) = bills.get(id)
    suspend fun getIncome(id: Long) = incomes.get(id)

    suspend fun saveBill(b: Bill): Long = if (b.id == 0L) bills.insert(b) else { bills.update(b); b.id }
    suspend fun deleteBill(b: Bill) = bills.delete(b.id)

    suspend fun saveIncome(i: RecurringIncome): Long = if (i.id == 0L) incomes.insert(i) else { incomes.update(i); i.id }
    suspend fun deleteIncome(i: RecurringIncome) = incomes.delete(i.id)

    /**
     * Marca a conta como paga no [period]. Se [createTransaction], cria a movimentação de saída
     * (origem "conta fixa") com o valor informado — que pode ser diferente do cadastrado (ex.: conta de luz).
     * Retorna false se já estava paga.
     */
    suspend fun markPaid(
        bill: Bill, period: YearMonth, amount: Long, date: LocalDate = LocalDate.now(),
        payment: PaymentChoice = PaymentChoice(null, bill.paymentMethod), createTransaction: Boolean = true,
    ): Boolean {
        var created: Transaction? = null
        val ok = db.withTransaction {
            if (bills.getPayment(bill.id, period) != null) return@withTransaction false
            val txId = if (createTransaction) {
                val t = txs.withWallet(Transaction(
                    type = TxType.EXPENSE, amount = amount, categoryId = bill.categoryId, date = date,
                    description = bill.name, paymentMethod = payment.method, origin = TxOrigin.CONTA_FIXA,
                    walletId = payment.walletId,
                ))
                db.transactions().insert(t).also { created = t.copy(id = it) }
            } else null
            bills.insertPayment(BillPayment(billId = bill.id, period = period, amount = amount, paidDate = date, transactionId = txId))
            true
        }
        if (ok) txs.notifyChanged(created)
        return ok
    }

    /** Desmarca o pagamento. O lançamento gerado vai para os excluídos com a justificativa automática. */
    suspend fun unmarkPaid(p: BillPayment, billName: String) {
        val tx = p.transactionId?.let { db.transactions().get(it) }
        if (tx != null) txs.deleteWithReason(tx, "Pagamento da conta \"$billName\" desmarcado") // cascata remove o pagamento
        else bills.deletePayment(p.id)
    }

    suspend fun confirmIncome(income: RecurringIncome, period: YearMonth, amount: Long, date: LocalDate = LocalDate.now()): Boolean {
        var created: Transaction? = null
        val ok = db.withTransaction {
            if (incomes.getReceipt(income.id, period) != null) return@withTransaction false
            val t = txs.withWallet(Transaction(
                type = TxType.INCOME, amount = amount, categoryId = income.categoryId, date = date,
                description = income.name, origin = TxOrigin.CONTA_FIXA,
            ))
            val txId = db.transactions().insert(t)
            created = t.copy(id = txId)
            incomes.insertReceipt(IncomeReceipt(incomeId = income.id, period = period, amount = amount, transactionId = txId))
            true
        }
        if (ok) txs.notifyChanged(created)
        return ok
    }

    suspend fun undoIncome(r: IncomeReceipt, incomeName: String) {
        val tx = r.transactionId?.let { db.transactions().get(it) }
        if (tx != null) txs.deleteWithReason(tx, "Recebimento de \"$incomeName\" desmarcado")
        else incomes.deleteReceipt(r.id)
    }

    suspend fun deleteSamples() { bills.deleteSamples(); incomes.deleteSamples() }
}
