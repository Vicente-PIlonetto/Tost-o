package app.meubolso.ui.shopping

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.ListEntry
import app.meubolso.data.db.MarketSection
import app.meubolso.data.db.ShoppingItem
import app.meubolso.data.db.ShoppingTrip
import app.meubolso.data.repo.ShoppingRepository
import app.meubolso.ui.components.BasicPaymentMethods
import app.meubolso.ui.components.DateChip
import app.meubolso.ui.components.EmptyState
import app.meubolso.ui.components.Motion
import app.meubolso.ui.components.MoneyField
import app.meubolso.ui.components.PaymentChips
import app.meubolso.ui.components.SectionTitle
import app.meubolso.ui.components.showUndo
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun ShoppingScreen(vm: ShoppingViewModel, contentPadding: PaddingValues, snackbar: SnackbarHostState) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Lista" + if (s.totalCount > 0) " (${s.totalCount})" else "") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Histórico") })
        }
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(120))) }, label = "shopTab") { t ->
            if (t == 0) ListTab(vm, s, contentPadding, snackbar) else HistoryTab(vm, s.trips, contentPadding)
        }
    }
}

@Composable
private fun ListTab(vm: ShoppingViewModel, s: ShoppingState, contentPadding: PaddingValues, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<EntryUi?>(null) }
    var pricing by remember { mutableStateOf<EntryUi?>(null) }
    var finishing by remember { mutableStateOf(false) }
    val askSection by vm.askSectionFor.collectAsStateWithLifecycle()

    fun removeWithUndo(e: ListEntry, name: String) {
        vm.remove(e)
        scope.launch { if (snackbar.showUndo("$name saiu da lista")) vm.restore(e) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp)) {
            item(key = "add") { AddBar(vm) }
            if (s.suggestions.isNotEmpty()) item(key = "sugg") { SuggestionsBlock(s.suggestions, Modifier.animateItem(), vm::addItem) }
            if (s.groups.isEmpty()) item(key = "empty") {
                EmptyState(
                    Icons.Filled.AddShoppingCart, "Lista vazia",
                    "Digite o que precisa comprar lá em cima. A lista se organiza sozinha pelas seções do mercado, na ordem em que você anda nele.",
                    Modifier.animateItem(),
                )
            }
            s.groups.forEach { g ->
                item(key = "sec" + g.section.id) {
                    val done = g.entries.count { it.entry.checked }
                    SectionTitle("${g.section.name}  ·  $done/${g.entries.size}", Modifier.animateItem())
                }
                items(g.entries, key = { "e" + it.entry.id }) { ui ->
                    SwipeToRemove(Modifier.animateItem(), onRemove = { removeWithUndo(ui.entry, ui.item.name) }) {
                        EntryRow(ui, onToggle = { vm.toggle(ui.entry) }, onEdit = { editing = ui }, onPrice = { pricing = ui })
                    }
                }
            }
        }
        AnimatedVisibility(
            s.checkedCount > 0,
            enter = scaleIn(Motion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            ExtendedFloatingActionButton(
                onClick = { finishing = true },
                icon = { Icon(Icons.Filled.ShoppingCartCheckout, null) },
                text = { Text("Finalizar compra (${s.checkedCount})") },
            )
        }
    }

    askSection?.let { name ->
        NewItemDialog(name, s.sections, onDismiss = { vm.askSectionFor.value = null }) { sectionId, unit ->
            vm.createAndAdd(name, sectionId, unit)
        }
    }
    editing?.let { ui ->
        EditEntryDialog(ui, s.sections, onDismiss = { editing = null },
            onSave = { e, i -> vm.updateEntry(e, i); editing = null },
            onRemove = { editing = null; removeWithUndo(ui.entry, ui.item.name) })
    }
    pricing?.let { ui ->
        PriceDialog(ui, onDismiss = { pricing = null }) { vm.setPrice(ui.entry, it); pricing = null }
    }
    if (finishing) FinishDialog(s, onDismiss = { finishing = false }) { total, date, pm ->
        finishing = false
        vm.finish(total, date, pm) { n ->
            scope.launch { snackbar.showSnackbar("Compra registrada! ${Money.format(total)} em Mercado · $n ${if (n == 1) "item" else "itens"}") }
        }
    }
}

@Composable
private fun AddBar(vm: ShoppingViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val matches = remember(text, vm.state.collectAsStateWithLifecycle().value) { vm.matches(text) }
    fun submit() { if (text.isNotBlank()) { vm.add(text); text = "" } }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            text, { text = it }, Modifier.fillMaxWidth(),
            placeholder = { Text("Adicionar item… (ex.: leite)") }, singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Filled.AddShoppingCart, null) },
            trailingIcon = {
                AnimatedVisibility(text.isNotBlank(), enter = scaleIn(Motion.bouncy()), exit = scaleOut()) {
                    IconButton(onClick = ::submit) { Icon(Icons.Filled.Add, "Adicionar") }
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        AnimatedVisibility(matches.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(Modifier.fillMaxWidth().padding(top = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                matches.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.addItem(item); text = "" }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.name, Modifier.weight(1f))
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsBlock(list: List<Suggestion>, modifier: Modifier, onAdd: (ShoppingItem) -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(6.dp))
                Text("Talvez esteja acabando", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.forEach { sg ->
                    AssistChip(
                        onClick = { onAdd(sg.item) },
                        label = {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(sg.item.name, fontWeight = FontWeight.Bold)
                                Text("costuma durar ~${sg.stats.avgIntervalDays.roundToInt()} dias",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                }
            }
        }
    }
}

/** Caixa de seleção grande, fácil de marcar com o polegar. */
@Composable
private fun BigCheck(checked: Boolean) {
    val bg by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, label = "chkBg")
    val scale by animateFloatAsState(if (checked) 1f else 0f, Motion.bouncy(), label = "chk")
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(bg)
            .border(2.5.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale })
    }
}

@Composable
private fun EntryRow(ui: EntryUi, onToggle: () -> Unit, onEdit: () -> Unit, onPrice: () -> Unit) {
    val e = ui.entry
    val haptics = LocalHapticFeedback.current
    val alpha by animateFloatAsState(if (e.checked) 0.5f else 1f, tween(250), label = "a")
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .clickable { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onToggle() }
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BigCheck(e.checked)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).alpha(alpha)) {
            val qty = listOf(e.quantity, ui.item.unit.takeIf { e.quantity.isNotBlank() } ?: "").filter { it.isNotBlank() }.joinToString(" ")
            Text(
                ui.item.name + if (qty.isNotEmpty()) "  ·  $qty" else "",
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (e.checked) TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            ui.lastPrice?.let { p ->
                Text("último: ${Money.format(p.price ?: 0)} em ${Dates.formatShort(p.date)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(e.checked, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            AssistChip(onClick = onPrice, label = { Text(e.price?.let { Money.format(it) } ?: "R$ ?") })
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.MoreVertCompat, "Editar") }
    }
}

@Composable
private fun SwipeToRemove(modifier: Modifier, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val st = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it != SwipeToDismissBoxValue.Settled) onRemove()
        false
    })
    SwipeToDismissBox(
        st, modifier = modifier,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = if (st.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                Icon(Icons.Filled.Delete, "Tirar da lista")
            }
        },
    ) { content() }
}

@Composable
private fun SectionChips(sections: List<MarketSection>, selected: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sections.forEach { sec -> FilterChip(sec.id == selected, { onSelect(sec.id) }, { Text(sec.name) }) }
    }
}

@Composable
private fun UnitChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ShoppingRepository.UNITS.forEach { u -> FilterChip(u == selected, { onSelect(u) }, { Text(u.ifEmpty { "—" }) }) }
    }
}

@Composable
private fun NewItemDialog(name: String, sections: List<MarketSection>, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    var section by remember { mutableStateOf<Long?>(null) }
    var unit by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Onde fica \"$name\"?") },
        text = {
            Column(Modifier.verticalScrollCompat()) {
                Text("Item novo! Escolha a seção do mercado — só vou perguntar desta vez.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                SectionChips(sections, section) { section = it }
                Spacer(Modifier.height(8.dp))
                Text("Unidade (opcional)", style = MaterialTheme.typography.labelLarge)
                UnitChips(unit) { unit = it }
            }
        },
        confirmButton = { TextButton(enabled = section != null, onClick = { onConfirm(section!!, unit) }) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun EditEntryDialog(
    ui: EntryUi, sections: List<MarketSection>, onDismiss: () -> Unit,
    onSave: (ListEntry, ShoppingItem) -> Unit, onRemove: () -> Unit,
) {
    var qty by remember { mutableStateOf(ui.entry.quantity) }
    var unit by remember { mutableStateOf(ui.item.unit) }
    var section by remember { mutableStateOf(ui.item.sectionId) }
    var name by remember { mutableStateOf(ui.item.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ui.item.name) },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(qty, { qty = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade (opcional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                Text("Unidade", style = MaterialTheme.typography.labelLarge)
                UnitChips(unit) { unit = it }
                HorizontalDivider()
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome no catálogo") }, singleLine = true)
                Text("Seção", style = MaterialTheme.typography.labelLarge)
                SectionChips(sections, section) { section = it }
                TextButton(onClick = onRemove) { Text("Tirar da lista", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(ui.entry.copy(quantity = qty.trim()), ui.item.copy(name = name.trim(), unit = unit, sectionId = section))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PriceDialog(ui: EntryUi, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var price by remember { mutableLongStateOf(ui.entry.price ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preço de ${ui.item.name}") },
        text = {
            Column {
                MoneyField(price, { price = it }, "Preço", Modifier.fillMaxWidth())
                ui.lastPrice?.let {
                    Text("Última vez: ${Money.format(it.price ?: 0)} em ${Dates.format(it.date)}",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(price.takeIf { it > 0 }) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = { onSave(null) }) { Text("Sem preço") } },
    )
}

@Composable
private fun FinishDialog(s: ShoppingState, onDismiss: () -> Unit, onConfirm: (Long, LocalDate, app.meubolso.domain.PaymentChoice) -> Unit) {
    var total by remember { mutableLongStateOf(s.checkedPriceSum) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pm by remember { mutableStateOf(app.meubolso.domain.PaymentChoice()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.ShoppingCartCheckout, null) },
        title = { Text("Finalizar compra") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(total, { total = it }, "Total do cupom", Modifier.fillMaxWidth())
                if (s.checkedPriceSum > 0) Text("Soma dos preços anotados: ${Money.format(s.checkedPriceSum)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DateChip(date, { date = it })
                app.meubolso.ui.components.WalletPicker(pm, { pm = it }, total)
                Text("Vira uma saída em Mercado. Os ${s.checkedCount} itens marcados saem da lista; os não marcados continuam.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = total > 0, onClick = { onConfirm(total, date, pm) }) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun HistoryTab(vm: ShoppingViewModel, trips: List<ShoppingTrip>, contentPadding: PaddingValues) {
    if (trips.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Nenhuma compra finalizada",
            "Quando você finalizar uma compra na lista, ela aparece aqui com os itens e o total.")
        return
    }
    var expanded by remember { mutableStateOf<Long?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp, top = 8.dp)) {
        items(trips, key = { it.id }) { t ->
            Card(
                Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { expanded = if (expanded == t.id) null else t.id },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Dates.dayHeader(t.date), style = MaterialTheme.typography.titleSmall)
                            Text("${t.itemCount} ${if (t.itemCount == 1) "item" else "itens"}" + (t.paymentMethod?.let { " · ${it.label}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(t.total), style = MaterialTheme.typography.titleMedium)
                    }
                    AnimatedVisibility(expanded == t.id, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        val items by produceState(emptyList<app.meubolso.data.db.TripItem>(), t.id) { value = vm.tripItems(t.id) }
                        Column(Modifier.padding(top = 8.dp)) {
                            HorizontalDivider()
                            items.forEach { i ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(i.name + listOf(i.quantity, if (i.quantity.isNotBlank()) i.unit else "").filter { it.isNotBlank() }
                                        .joinToString(" ", prefix = " ").takeIf { it.isNotBlank() }.orEmpty(), Modifier.weight(1f))
                                    i.price?.let { Text(Money.format(it), style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                            if (t.transactionId == null) Text("O lançamento desta compra foi excluído.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
