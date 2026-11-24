package app.meubolso

import android.content.Context
import app.meubolso.data.db.AppDatabase
import app.meubolso.data.prefs.SettingsRepository
import app.meubolso.data.repo.BillsRepository
import app.meubolso.data.repo.CategoryRepository
import app.meubolso.data.repo.SavingsRepository
import app.meubolso.data.repo.ShoppingRepository
import app.meubolso.data.repo.TransactionRepository
import app.meubolso.data.repo.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Injeção de dependência manual: um único container criado na Application. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val categories = CategoryRepository(db)
    val wallets = WalletRepository(db)
    val transactions = TransactionRepository(db, wallets)
    val bills = BillsRepository(db, transactions)
    val savings = SavingsRepository(db)
    val shopping = ShoppingRepository(db, transactions)
}
