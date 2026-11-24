package app.meubolso.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.meubolso.AppContainer
import app.meubolso.core.FinancialMonth
import app.meubolso.data.db.SavingsKind
import app.meubolso.data.db.SavingsMovement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SavingsState(
    val balance: Long,
    val goal: Long,
    val savedThisMonth: Long,
    val movements: List<SavingsMovement>,
) {
    val progress: Float get() = if (goal <= 0) 0f else (balance.toFloat() / goal).coerceIn(0f, 1f)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsViewModel(private val c: AppContainer) : ViewModel() {
    private val month = c.settings.settings.map { FinancialMonth.of(LocalDate.now(), it.monthStartDay) }

    val state: StateFlow<SavingsState?> = combine(
        c.savings.balance, c.settings.settings, c.savings.movements,
        month.flatMapLatest { c.savings.netInRange(it.start, it.endExclusive) },
    ) { bal, s, mv, net -> SavingsState(bal, s.savingsGoal, net, mv) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun add(kind: SavingsKind, amount: Long, date: LocalDate, note: String) = viewModelScope.launch {
        c.savings.save(SavingsMovement(kind = kind, amount = amount, date = date, note = note.trim()))
    }

    fun delete(m: SavingsMovement) = viewModelScope.launch { c.savings.delete(m) }
    fun restore(m: SavingsMovement) = viewModelScope.launch { c.savings.restore(m) }
    fun setGoal(cents: Long) = viewModelScope.launch { c.settings.setSavingsGoal(cents) }
}
