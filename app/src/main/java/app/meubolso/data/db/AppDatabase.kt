package app.meubolso.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Category::class, Transaction::class,
        // v2
        DeletedTransaction::class, SavingsMovement::class,
        // v3
        Bill::class, BillPayment::class, RecurringIncome::class, IncomeReceipt::class, Notified::class,
        // v4
        MarketSection::class, ShoppingItem::class, ListEntry::class, ShoppingTrip::class, Purchase::class,
        // v5
        Wallet::class, WalletTransfer::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun deleted(): DeletedTransactionDao
    abstract fun savings(): SavingsDao
    abstract fun bills(): BillDao
    abstract fun incomes(): IncomeDao
    abstract fun notified(): NotifiedDao
    abstract fun wallets(): WalletDao
    abstract fun shopping(): ShoppingDao

    companion object {
        const val NAME = "meubolso.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
