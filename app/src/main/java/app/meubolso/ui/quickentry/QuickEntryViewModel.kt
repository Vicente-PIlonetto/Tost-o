package app.meubolso.ui.quickentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.meubolso.AppContainer
import app.meubolso.data.db.CategoryWithUsage
import app.meubolso.domain.PaymentChoice
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxOrigin
import app.meubolso.data.db.TxType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class QuickEntryState(
    val editing: Transaction? = null,
    val type: TxType = TxType.EXPENSE,
    val amount: Long = 0,
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val description: String = "",
    val payment: PaymentChoice = PaymentChoice(lastWallet, null),
    val showDetails: Boolean = false,
    val error: String? = null,
    /** Incrementa a cada erro, para disparar a animação de "balançar". */
    val errorTick: Int = 0,
)

/** Última carteira escolhida num lançamento novo: o próximo já começa nela. */
private var lastWallet: Long? = null

@OptIn(ExperimentalCoroutinesApi::class)
class QuickEntryViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(QuickEntryState())
    val state: StateFlow<QuickEntryState> = _state.asStateFlow()

    val categories: StateFlow<List<CategoryWithUsage>> = _state.map { it.type }
        .flatMapLatest { c.categories.byUsage(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startNew(type: TxType = TxType.EXPENSE) {
        _state.value = QuickEntryState(type = type)
    }

    fun startEdit(t: Transaction) {
        _state.value = QuickEntryState(
            editing = t, type = t.type, amount = t.amount, categoryId = t.categoryId, date = t.date,
            description = t.description, payment = PaymentChoice(t.walletId, t.paymentMethod),
            showDetails = t.description.isNotEmpty() || t.date != LocalDate.now(),
        )
    }

    fun setType(t: TxType) = _state.update { if (it.type == t) it else it.copy(type = t, categoryId = null, error = null) }
    fun setAmount(cents: Long) = _state.update { it.copy(amount = cents, error = null) }
    fun setDate(d: LocalDate) = _state.update { it.copy(date = d) }
    fun setDescription(s: String) = _state.update { it.copy(description = s) }
    fun setPayment(p: PaymentChoice) {
        if (_state.value.editing == null) lastWallet = p.walletId
        _state.update { it.copy(payment = p) }
    }
    fun toggleDetails() = _state.update { it.copy(showDetails = !it.showDetails) }

    /** Toque na categoria = salvar. Retorna true se salvou. */
    fun saveWith(categoryId: Long, onSaved: (String) -> Unit) {
        val s = _state.value
        if (s.amount <= 0) {
            _state.update { it.copy(error = "Digite o valor primeiro", categoryId = categoryId, errorTick = it.errorTick + 1) }
            return
        }
        val base = s.editing ?: Transaction(type = s.type, amount = 0, categoryId = categoryId, date = s.date, origin = TxOrigin.MANUAL)
        val t = base.copy(
            type = s.type, amount = s.amount, categoryId = categoryId, date = s.date,
            description = s.description.trim(), paymentMethod = s.payment.method, walletId = s.payment.walletId,
        )
        viewModelScope.launch {
            c.transactions.save(t)
            val cat = categories.value.firstOrNull { it.id == categoryId }?.name ?: ""
            val verb = if (s.editing != null) "Atualizado" else if (s.type == TxType.INCOME) "Entrou" else "Anotado"
            onSaved("$verb! ${app.meubolso.core.Money.format(s.amount)} em $cat")
        }
    }
}
