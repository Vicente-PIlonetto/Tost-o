package app.meubolso.ui.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.meubolso.AppContainer
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.DefaultWallets
import app.meubolso.data.db.Wallet
import app.meubolso.data.db.WalletKind
import app.meubolso.data.db.WalletTransfer
import app.meubolso.domain.WalletStatus
import app.meubolso.ui.bills.Stepper
import app.meubolso.ui.components.DateChip
import app.meubolso.ui.components.MoneyField
import app.meubolso.ui.components.SectionTitle
import app.meubolso.ui.components.WalletBadge
import app.meubolso.ui.components.WalletCard
import app.meubolso.ui.components.enterUp
import app.meubolso.ui.components.icon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class WalletsViewModel(private val c: AppContainer) : ViewModel() {
    val statuses: StateFlow<List<WalletStatus>> = c.wallets.statuses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transfers: StateFlow<List<WalletTransfer>> = c.wallets.transfers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(w: Wallet) = viewModelScope.launch { c.wallets.save(w) }
    suspend fun usage(w: Wallet) = c.wallets.usage(w.id)
    fun delete(w: Wallet) = viewModelScope.launch { runCatching { c.wallets.delete(w, null) } }
    fun transfer(from: Long, to: Long, amount: Long, date: LocalDate, note: String) =
        viewModelScope.launch { c.wallets.transfer(from, to, amount, date, note) }
    fun deleteTransfer(t: WalletTransfer) = viewModelScope.launch { c.wallets.deleteTransfer(t) }
}

/** Pedido de transferência: pagar fatura já vem com o cartão de destino e o valor. */
data class TransferRequest(val to: Long? = null, val amount: Long = 0, val note: String = "")

@Composable
fun WalletsScreen(vm: WalletsViewModel, contentPadding: PaddingValues) {
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Wallet?>(null) }
    var transfer by remember { mutableStateOf<TransferRequest?>(null) }
    val names = statuses.associate { it.wallet.id to it.wallet }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 88.dp, top = 4.dp),
        ) {
            item(key = "hint") {
                Text(
                    "De onde sai o seu dinheiro. Cartão e vale mostram quanto sobra do limite; conta e dinheiro, o saldo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            items(statuses, key = { "w" + it.wallet.id }) { st ->
                val w = st.wallet
                WalletCard(
                    st,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).animateItem().enterUp(0)
                        .clickable { editing = w },
                ) {
                    if (w.archived) Text("Arquivada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (w.kind == WalletKind.CREDIT_CARD && !w.archived && (st.closedDue > 0 || st.openInvoice > 0)) {
                        FilledTonalButton(
                            onClick = {
                                val amount = if (st.closedDue > 0) st.closedDue else st.openInvoice
                                transfer = TransferRequest(w.id, amount, "Fatura ${w.name}")
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        ) { Text(if (st.closedDue > 0) "Pagar fatura" else "Adiantar fatura") }
                    }
                }
            }
            if (transfers.isNotEmpty()) {
                item(key = "trT") { SectionTitle("Transferências e faturas pagas", Modifier.animateItem()) }
                items(transfers.take(30), key = { "t" + it.id }) { t ->
                    Row(
                        Modifier.fillMaxWidth().animateItem().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.SwapHoriz, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.note.ifBlank { "Transferência" }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${names[t.fromWalletId]?.name ?: "?"} → ${names[t.toWalletId]?.name ?: "?"} · ${Dates.formatShort(t.date)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.format(t.amount), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { vm.deleteTransfer(t) }) { Icon(Icons.Filled.DeleteOutline, "Desfazer") }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(bottom = contentPadding.calculateBottomPadding() + 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (statuses.count { !it.wallet.archived } >= 2) {
                ExtendedFloatingActionButton(
                    onClick = { transfer = TransferRequest() },
                    icon = { Icon(Icons.Filled.SwapHoriz, null) }, text = { Text("Transferir") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
            ExtendedFloatingActionButton(
                onClick = { editing = Wallet(name = "", kind = WalletKind.CREDIT_CARD, color = DefaultWallets.CARD_COLOR, closingDay = 1, dueDay = 10, rechargeDay = 1) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nova") },
            )
        }
    }

    editing?.let { w -> WalletDialog(w, vm, onDismiss = { editing = null }) }
    transfer?.let { r ->
        TransferDialog(r, statuses.map { it.wallet }.filter { !it.archived }, onDismiss = { transfer = null }) { from, to, amount, date, note ->
            vm.transfer(from, to, amount, date, note); transfer = null
        }
    }
}

@Composable
private fun WalletDialog(initial: Wallet, vm: WalletsViewModel, onDismiss: () -> Unit) {
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.kind) }
    var limit by remember { mutableLongStateOf(initial.limitAmount) }
    var closing by remember { mutableIntStateOf(initial.closingDay.coerceIn(1, 31)) }
    var due by remember { mutableIntStateOf(initial.dueDay.coerceIn(1, 31)) }
    var recharge by remember { mutableIntStateOf(initial.rechargeDay.coerceIn(1, 31)) }
    var balance by remember { mutableLongStateOf(initial.initialBalance) }
    var color by remember { mutableLongStateOf(initial.color) }
    var usage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(initial.id) { if (!isNew) usage = vm.usage(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nova carteira" else "Editar ${initial.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WalletKind.entries.forEach { k ->
                        FilterChip(
                            kind == k,
                            {
                                if (isNew && color == DefaultWallets.colorFor(kind)) color = DefaultWallets.colorFor(k)
                                kind = k
                            },
                            { Text(k.label) },
                            leadingIcon = { Icon(k.icon(), null, Modifier.size(18.dp)) },
                            enabled = isNew || usage == 0 || k == initial.kind,
                        )
                    }
                }
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Nome") },
                    placeholder = { Text(when (kind) { WalletKind.ACCOUNT -> "Ex.: Itaú"; WalletKind.CREDIT_CARD -> "Ex.: Nubank"; WalletKind.VOUCHER -> "Ex.: VR, Alelo"; WalletKind.CASH -> "Ex.: Carteira" }) },
                )
                when (kind) {
                    WalletKind.CREDIT_CARD -> {
                        MoneyField(limit, { limit = it }, "Limite do cartão", Modifier.fillMaxWidth())
                        DayRow("Fecha no dia", closing) { closing = it }
                        DayRow("Vence no dia", due) { due = it }
                        Text(
                            "Compras do dia $closing em diante entram na fatura seguinte.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    WalletKind.VOUCHER -> {
                        MoneyField(limit, { limit = it }, "Valor que cai por mês", Modifier.fillMaxWidth())
                        DayRow("Cai no dia", recharge) { recharge = it }
                    }
                    else -> MoneyField(balance, { balance = it }, "Saldo hoje (antes dos lançamentos)", Modifier.fillMaxWidth())
                }
                Text("Cor", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultWallets.palette.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) { if (c == color) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                if (!isNew) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.save(initial.copy(archived = !initial.archived)); onDismiss() }) {
                        Text(if (initial.archived) "Desarquivar" else "Arquivar")
                    }
                    if (usage == 0) TextButton(onClick = { vm.delete(initial); onDismiss() }) {
                        Text("Excluir", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (!isNew && usage > 0) Text(
                    "Tem $usage ${if (usage == 1) "lançamento" else "lançamentos"}. Para tirar da lista sem perder nada, arquive.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            val ok = name.isNotBlank() && (kind == WalletKind.ACCOUNT || kind == WalletKind.CASH || limit > 0)
            TextButton(enabled = ok, onClick = {
                vm.save(
                    initial.copy(
                        name = name.trim(), kind = kind, limitAmount = if (kind == WalletKind.CREDIT_CARD || kind == WalletKind.VOUCHER) limit else 0,
                        closingDay = if (kind == WalletKind.CREDIT_CARD) closing else 0,
                        dueDay = if (kind == WalletKind.CREDIT_CARD) due else 0,
                        rechargeDay = if (kind == WalletKind.VOUCHER) recharge else 0,
                        initialBalance = if (kind == WalletKind.ACCOUNT || kind == WalletKind.CASH) balance else 0,
                        color = color,
                    ),
                )
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DayRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Stepper(value, 1, 31, onChange)
    }
}

@Composable
private fun TransferDialog(
    r: TransferRequest, wallets: List<Wallet>, onDismiss: () -> Unit,
    onConfirm: (from: Long, to: Long, amount: Long, date: LocalDate, note: String) -> Unit,
) {
    val sources = wallets.filter { it.kind == WalletKind.ACCOUNT || it.kind == WalletKind.CASH }
    var to by remember { mutableStateOf(r.to ?: wallets.firstOrNull { it.kind == WalletKind.CASH }?.id) }
    var from by remember { mutableStateOf(sources.firstOrNull { it.id != to }?.id) }
    var amount by remember { mutableLongStateOf(r.amount) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val toWallet = wallets.firstOrNull { it.id == to }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.SwapHoriz, null) },
        title = { Text(if (toWallet?.kind == WalletKind.CREDIT_CARD && r.to != null) "Pagar fatura do ${toWallet.name}" else "Transferir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(amount, { amount = it }, "Valor", Modifier.fillMaxWidth())
                DateChip(date, { date = it })
                Text("Sai de", style = MaterialTheme.typography.labelLarge)
                WalletChoice(sources, from) { from = it }
                if (r.to == null) {
                    Text("Vai para", style = MaterialTheme.typography.labelLarge)
                    WalletChoice(wallets.filter { it.kind != WalletKind.VOUCHER }, to) { to = it }
                }
                Text(
                    "Não conta como gasto: o dinheiro só muda de lugar. Os gastos do cartão já contaram quando você comprou.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            val f = from; val t = to
            TextButton(enabled = amount > 0 && f != null && t != null && f != t, onClick = {
                onConfirm(f!!, t!!, amount, date, r.note.ifBlank { if (toWallet?.kind == WalletKind.CREDIT_CARD) "Fatura ${toWallet.name}" else "" })
            }) { Text(if (toWallet?.kind == WalletKind.CREDIT_CARD) "Pagar" else "Transferir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WalletChoice(options: List<Wallet>, selected: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { w ->
            FilterChip(selected == w.id, { onSelect(w.id) }, { Text(w.name) }, leadingIcon = { WalletBadge(w, 20.dp) })
        }
    }
}
