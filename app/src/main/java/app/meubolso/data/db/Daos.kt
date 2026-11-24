package app.meubolso.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction as RoomTx
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

data class CategoryWithUsage(
    val id: Long,
    val name: String,
    val type: TxType,
    val icon: String,
    val color: Long,
    val sort_order: Int,
    val archived: Boolean,
    val monthly_limit: Long?,
    val usage: Int,
) {
    fun toCategory() = Category(id, name, type, icon, color, sort_order, archived, monthly_limit)
}

data class CategoryTotal(val categoryId: Long, val total: Long)

data class DayTotals(val date: LocalDate, val income: Long, val expense: Long)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, archived, sort_order, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

    @Query("SELECT * FROM categories WHERE type = :type AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(type: TxType, name: String): Category?

    /** Categorias ativas ordenadas pelas mais usadas nos últimos 90 dias (empate: ordem definida). */
    @Query(
        """
        SELECT c.id, c.name, c.type, c.icon, c.color, c.sort_order, c.archived, c.monthly_limit,
               (SELECT COUNT(*) FROM transactions t WHERE t.category_id = c.id AND t.date >= :sinceEpochDay) AS usage
        FROM categories c
        WHERE c.type = :type AND c.archived = 0
        ORDER BY usage DESC, c.sort_order, c.name
        """
    )
    fun observeByUsage(type: TxType, sinceEpochDay: Long): Flow<List<CategoryWithUsage>>

    @Insert suspend fun insert(c: Category): Long
    @Insert suspend fun insertAll(c: List<Category>)
    @Update suspend fun update(c: Category)
    @Update suspend fun updateAll(c: List<Category>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM categories WHERE type = :type")
    suspend fun maxSort(type: TxType): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date >= :start AND date < :endExclusive ORDER BY date DESC, created_at DESC")
    fun observeRange(start: LocalDate, endExclusive: LocalDate): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :start AND date < :endExclusive")
    suspend fun getRange(start: LocalDate, endExclusive: LocalDate): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY date DESC, created_at DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Transaction?

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0)
           FROM transactions WHERE date < :before"""
    )
    suspend fun balanceBefore(before: LocalDate): Long

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0)
           FROM transactions WHERE date < :before"""
    )
    fun observeBalanceBefore(before: LocalDate): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE date = :date")
    suspend fun countOn(date: LocalDate): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE' AND category_id = :categoryId AND date >= :start AND date < :endExclusive")
    suspend fun expenseSum(categoryId: Long, start: LocalDate, endExclusive: LocalDate): Long

    @Insert suspend fun insert(t: Transaction): Long
    @Insert suspend fun insertAll(t: List<Transaction>)
    @Update suspend fun update(t: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions WHERE is_sample = 1")
    suspend fun deleteSamples()

    @Query("SELECT COUNT(*) FROM transactions WHERE is_sample = 1")
    fun observeSampleCount(): Flow<Int>
}

@Dao
interface DeletedTransactionDao {
    @Query("SELECT * FROM deleted_transactions ORDER BY deleted_at DESC")
    fun observeAll(): Flow<List<DeletedTransaction>>

    @Insert suspend fun insert(d: DeletedTransaction): Long

    @Query("DELETE FROM deleted_transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM deleted_transactions")
    fun observeCount(): Flow<Int>
}

@Dao
interface SavingsDao {
    @Query("SELECT * FROM savings_movements ORDER BY date DESC, created_at DESC")
    fun observeAll(): Flow<List<SavingsMovement>>

    @Query(
        """SELECT COALESCE(SUM(CASE kind WHEN 'WITHDRAW' THEN -amount ELSE amount END), 0)
           FROM savings_movements"""
    )
    fun observeBalance(): Flow<Long>

    /** Líquido guardado no período (depósitos − resgates; ajustes não contam). */
    @Query(
        """SELECT COALESCE(SUM(CASE kind WHEN 'DEPOSIT' THEN amount WHEN 'WITHDRAW' THEN -amount ELSE 0 END), 0)
           FROM savings_movements WHERE date >= :start AND date < :endExclusive"""
    )
    fun observeNetInRange(start: LocalDate, endExclusive: LocalDate): Flow<Long>

    @Insert suspend fun insert(m: SavingsMovement): Long
    @Update suspend fun update(m: SavingsMovement)

    @Query("DELETE FROM savings_movements WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY active DESC, due_day, name")
    fun observeAll(): Flow<List<Bill>>

    @Query("SELECT * FROM bills")
    suspend fun getAll(): List<Bill>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun get(id: Long): Bill?

    @Insert suspend fun insert(b: Bill): Long
    @Update suspend fun update(b: Bill)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM bill_payments")
    fun observePayments(): Flow<List<BillPayment>>

    @Query("SELECT * FROM bill_payments")
    suspend fun getPayments(): List<BillPayment>

    @Query("SELECT * FROM bill_payments WHERE bill_id = :billId AND period = :period")
    suspend fun getPayment(billId: Long, period: YearMonth): BillPayment?

    @Insert suspend fun insertPayment(p: BillPayment): Long

    @Query("SELECT * FROM bill_payments WHERE transaction_id = :txId")
    suspend fun paymentsForTransaction(txId: Long): List<BillPayment>

    @Query("DELETE FROM bill_payments WHERE id = :id")
    suspend fun deletePayment(id: Long)

    @Query("DELETE FROM bills WHERE is_sample = 1")
    suspend fun deleteSamples()
}

@Dao
interface IncomeDao {
    @Query("SELECT * FROM recurring_incomes ORDER BY active DESC, day, name")
    fun observeAll(): Flow<List<RecurringIncome>>

    @Query("SELECT * FROM recurring_incomes")
    suspend fun getAll(): List<RecurringIncome>

    @Query("SELECT * FROM recurring_incomes WHERE id = :id")
    suspend fun get(id: Long): RecurringIncome?

    @Insert suspend fun insert(i: RecurringIncome): Long
    @Update suspend fun update(i: RecurringIncome)

    @Query("DELETE FROM recurring_incomes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM income_receipts")
    fun observeReceipts(): Flow<List<IncomeReceipt>>

    @Query("SELECT * FROM income_receipts")
    suspend fun getReceipts(): List<IncomeReceipt>

    @Query("SELECT * FROM income_receipts WHERE income_id = :incomeId AND period = :period")
    suspend fun getReceipt(incomeId: Long, period: YearMonth): IncomeReceipt?

    @Insert suspend fun insertReceipt(r: IncomeReceipt): Long

    @Query("SELECT * FROM income_receipts WHERE transaction_id = :txId")
    suspend fun receiptsForTransaction(txId: Long): List<IncomeReceipt>

    @Query("DELETE FROM income_receipts WHERE id = :id")
    suspend fun deleteReceipt(id: Long)

    @Query("DELETE FROM recurring_incomes WHERE is_sample = 1")
    suspend fun deleteSamples()
}

@Dao
interface NotifiedDao {
    @Query("SELECT EXISTS(SELECT 1 FROM notified WHERE `key` = :key)")
    suspend fun exists(key: String): Boolean

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(n: Notified)

    @Query("DELETE FROM notified WHERE at < :before")
    suspend fun purgeOlderThan(before: Long)
}

data class TripItem(val name: String, val price: Long?, val quantity: String, val unit: String)

@Dao
interface ShoppingDao {
    // Seções
    @Query("SELECT * FROM market_sections ORDER BY sort_order, name")
    fun observeSections(): Flow<List<MarketSection>>

    @Query("SELECT COUNT(*) FROM market_sections")
    suspend fun sectionCount(): Int

    @Query("SELECT * FROM market_sections WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun sectionByName(name: String): MarketSection?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM market_sections")
    suspend fun maxSectionOrder(): Int

    @Insert suspend fun insertSection(s: MarketSection): Long
    @Insert suspend fun insertSections(s: List<MarketSection>)
    @Update suspend fun updateSection(s: MarketSection)
    @Update suspend fun updateSections(s: List<MarketSection>)

    @Query("DELETE FROM market_sections WHERE id = :id")
    suspend fun deleteSection(id: Long)

    @Query("UPDATE shopping_items SET section_id = :to WHERE section_id = :from")
    suspend fun moveItemsToSection(from: Long, to: Long)

    // Catálogo
    @Query("SELECT * FROM shopping_items ORDER BY name COLLATE NOCASE")
    fun observeItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun itemByName(name: String): ShoppingItem?

    @Insert suspend fun insertItem(i: ShoppingItem): Long
    @Update suspend fun updateItem(i: ShoppingItem)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    // Lista atual
    @Query("SELECT * FROM shopping_list")
    fun observeList(): Flow<List<ListEntry>>

    @Query("SELECT * FROM shopping_list WHERE checked = 1")
    suspend fun checkedEntries(): List<ListEntry>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertEntry(e: ListEntry): Long

    @Update suspend fun updateEntry(e: ListEntry)

    @Query("DELETE FROM shopping_list WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM shopping_list WHERE checked = 1")
    suspend fun deleteChecked()

    // Histórico
    @Query("SELECT * FROM purchase_history")
    fun observePurchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM purchase_history")
    suspend fun allPurchases(): List<Purchase>

    @Insert suspend fun insertPurchases(p: List<Purchase>)

    @Query("SELECT * FROM shopping_trips ORDER BY date DESC, id DESC")
    fun observeTrips(): Flow<List<ShoppingTrip>>

    @Insert suspend fun insertTrip(t: ShoppingTrip): Long

    @Query(
        """SELECT i.name AS name, p.price AS price, p.quantity AS quantity, i.unit AS unit
           FROM purchase_history p JOIN shopping_items i ON i.id = p.item_id
           WHERE p.trip_id = :tripId ORDER BY i.name COLLATE NOCASE"""
    )
    suspend fun tripItems(tripId: Long): List<TripItem>

    @Query("DELETE FROM shopping_items WHERE is_sample = 1")
    suspend fun deleteSampleItems()

    @Query("DELETE FROM shopping_trips WHERE is_sample = 1")
    suspend fun deleteSampleTrips()
}

/** Soma líquida por carteira e dia (saída positiva, entrada negativa), para saldos e faturas. */
data class WalletFlow(
    @androidx.room.ColumnInfo(name = "wallet_id") val walletId: Long?,
    @androidx.room.ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod?,
    val date: LocalDate,
    /** Saídas menos entradas do dia, em centavos. */
    val charge: Long,
)

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY archived, sort_order, id")
    fun observeAll(): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets ORDER BY archived, sort_order, id")
    suspend fun getAll(): List<Wallet>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun get(id: Long): Wallet?

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM wallets")
    suspend fun maxSort(): Int

    @Insert suspend fun insert(w: Wallet): Long
    @Insert suspend fun insertAll(w: List<Wallet>)
    @Update suspend fun update(w: Wallet)
    @Update suspend fun updateAll(w: List<Wallet>)

    @Query("SELECT COUNT(*) FROM transactions WHERE wallet_id = :id")
    suspend fun usage(id: Long): Int

    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT wallet_id, payment_method, date,
               SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE -amount END) AS charge
        FROM transactions GROUP BY wallet_id, payment_method, date
        """
    )
    fun observeFlows(): Flow<List<WalletFlow>>

    @Query("SELECT * FROM wallet_transfers ORDER BY date DESC, created_at DESC")
    fun observeTransfers(): Flow<List<WalletTransfer>>

    @Insert suspend fun insertTransfer(t: WalletTransfer): Long

    @Query("DELETE FROM wallet_transfers WHERE id = :id")
    suspend fun deleteTransfer(id: Long)

    @Query("UPDATE transactions SET wallet_id = :to WHERE wallet_id = :from")
    suspend fun moveTransactions(from: Long, to: Long)
}
