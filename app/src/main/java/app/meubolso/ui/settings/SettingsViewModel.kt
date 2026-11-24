package app.meubolso.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.meubolso.AppContainer
import app.meubolso.data.db.Category
import app.meubolso.data.db.DeletedTransaction
import app.meubolso.notify.DailyReminder
import app.meubolso.notify.ReminderWorker
import app.meubolso.data.db.TxType
import app.meubolso.data.prefs.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val sampleCount = c.transactions.sampleCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val categories: StateFlow<List<Category>> = c.categories.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMonthStartDay(d: Int) = viewModelScope.launch { c.settings.setMonthStartDay(d) }
    fun setAppLock(on: Boolean) = viewModelScope.launch { c.settings.setAppLock(on) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { c.settings.setDynamicColor(on) }
    fun deleteSamples() = viewModelScope.launch { c.transactions.deleteSamples(); c.bills.deleteSamples(); c.shopping.deleteSamples() }

    val deleted = c.transactions.deletedLog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deletedCount = c.transactions.deletedCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun restoreDeleted(d: DeletedTransaction) = viewModelScope.launch { c.transactions.restoreDeleted(d) }

    fun setNotifyBills(on: Boolean) = viewModelScope.launch { c.settings.setNotifyBills(on); if (on) ReminderWorker.runNow(app) }
    fun setNotifyLimits(on: Boolean) = viewModelScope.launch { c.settings.setNotifyLimits(on) }
    fun setNotifyIncome(on: Boolean) = viewModelScope.launch { c.settings.setNotifyIncome(on); if (on) ReminderWorker.runNow(app) }
    fun setDaily(on: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        c.settings.setDailyReminder(on, hour, minute)
        DailyReminder.sync(app)
    }
    private val app get() = c.appContext

    fun saveCategory(cat: Category) = viewModelScope.launch { c.categories.save(cat) }

    /** Move a categoria uma posição para cima (-1) ou para baixo (+1) entre as ativas do mesmo tipo. */
    fun move(cat: Category, delta: Int) = viewModelScope.launch {
        val list = categories.value.filter { it.type == cat.type && !it.archived }.sortedBy { it.sortOrder }.toMutableList()
        val i = list.indexOfFirst { it.id == cat.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@launch
        list.add(j, list.removeAt(i))
        c.categories.reorder(list)
    }

    fun newCategory(type: TxType) = Category(name = "", type = type, icon = "more", color = 0xFF546E7A)
}
