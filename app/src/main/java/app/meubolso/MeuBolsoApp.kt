package app.meubolso

import android.app.Application
import android.content.Context
import app.meubolso.notify.DailyReminder
import app.meubolso.notify.Notifications
import app.meubolso.notify.ReminderWorker
import app.meubolso.notify.Reminders
import kotlinx.coroutines.launch

class MeuBolsoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.appScope.launch {
            container.categories.ensureDefaults()
            container.wallets.ensureDefaults()
            container.shopping.ensureDefaultSections()
            DailyReminder.sync(this@MeuBolsoApp)
        }
        ReminderWorker.schedule(this)
        // Alerta de limite sempre que uma saída é salva.
        val reminders = Reminders(this, container)
        container.appScope.launch {
            container.transactions.changes.collect { t -> if (t != null) runCatching { reminders.checkLimit(t) } }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as MeuBolsoApp).container
