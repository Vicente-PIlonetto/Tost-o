package app.meubolso.ui.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import app.meubolso.ui.components.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxType
import app.meubolso.ui.components.EmptyState
import app.meubolso.ui.components.MonthSwitcher
import app.meubolso.ui.components.TransactionRow
import app.meubolso.ui.theme.MoneyColors
import kotlinx.coroutines.launch

@Composable
fun TransactionsScreen(
    vm: TransactionsViewModel,
    contentPadding: PaddingValues,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Lista") })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Gráficos") })
        }
        MonthSwitcher(s.month, vm::prev, vm::next, Modifier.padding(horizontal = 4.dp))
        Crossfade(tab, animationSpec = tween(250), label = "tab") { t ->
            Column {
                if (t == 0) TxList(vm, s, contentPadding, onEdit, onDelete)
                else ChartsTab(vm, contentPadding)
            }
        }
    }
}

@Composable
private fun TxList(
    vm: TransactionsViewModel,
    s: TxListState,
    contentPadding: PaddingValues,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
) {
    FiltersBar(vm, s)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Entradas ", style = MaterialTheme.typography.bodySmall)
        Text(Money.format(s.totalIncome), style = MaterialTheme.typography.bodySmall, color = MoneyColors.income, fontWeight = FontWeight.SemiBold)
        Text("   Saídas ", style = MaterialTheme.typography.bodySmall)
        Text(Money.format(s.totalExpense), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }

    // Ao trocar de mês a lista desliza para o lado; filtros só animam os itens.
    AnimatedContent(
        targetState = s,
        contentKey = { it.month },
        transitionSpec = {
            val dir = if (targetState.month.ym > initialState.month.ym) 1 else -1
            (slideInHorizontally(tween(320, easing = Motion.Emphasized)) { it / 4 * dir } + fadeIn(tween(250)))
                .togetherWith(slideOutHorizontally(tween(220)) { -it / 4 * dir } + fadeOut(tween(150)))
        },
        label = "monthList",
    ) { st -> MonthList(vm, st, contentPadding, onEdit, onDelete) }
}

@Composable
private fun MonthList(
    vm: TransactionsViewModel,
    s: TxListState,
    contentPadding: PaddingValues,
    onEdit: (Transaction) -> Unit,
    deleteWithUndo: (Transaction) -> Unit,
) {
    if (s.days.isEmpty()) {
        if (s.filters.active) EmptyState(Icons.Filled.SearchOff, "Nada encontrado",
            "Nenhum lançamento combina com os filtros neste mês.") { TextButton(onClick = vm::clearFilters) { Text("Limpar filtros") } }
        else EmptyState(Icons.Filled.ReceiptLong, "Nenhum lançamento neste mês",
            "Use o botão + para lançar um gasto ou uma entrada. Deslize um lançamento para a direita para editar e para a esquerda para excluir.")
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 88.dp)) {
        s.days.forEach { day ->
            item(key = "d" + day.date.toEpochDay()) {
                Row(
                    Modifier.animateItem().fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(Dates.dayHeader(day.date), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(day.total, showPlus = true), style = MaterialTheme.typography.labelLarge,
                        color = if (day.total >= 0) MoneyColors.income else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(day.items, key = { it.id }) { t ->
                SwipeableRow(Modifier.animateItem(), onEdit = { onEdit(t) }, onDelete = { deleteWithUndo(t) }) {
                    TransactionRow(t, s.categories[t.categoryId], Modifier.background(MaterialTheme.colorScheme.surface), onClick = { onEdit(t) })
                }
            }
        }
    }
}

@Composable
private fun SwipeableRow(modifier: Modifier, onEdit: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        when (it) {
            SwipeToDismissBoxValue.StartToEnd -> onEdit()
            SwipeToDismissBoxValue.EndToStart -> onDelete()
            else -> {}
        }
        false // volta à posição; a lista atualiza sozinha
    })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val dir = state.dismissDirection
            val (color, icon, align) = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(MaterialTheme.colorScheme.secondaryContainer, Icons.Filled.Edit, Alignment.CenterStart)
                else -> Triple(MaterialTheme.colorScheme.errorContainer, Icons.Filled.Delete, Alignment.CenterEnd)
            }
            Box(Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp), contentAlignment = align) {
                // O ícone cresce conforme o dedo arrasta.
                val p = if (dir == SwipeToDismissBoxValue.Settled) 0f else state.progress.coerceIn(0f, 1f)
                Icon(
                    icon, if (dir == SwipeToDismissBoxValue.StartToEnd) "Editar" else "Excluir",
                    Modifier.graphicsLayer { val sc = 0.6f + 0.8f * p.coerceAtMost(0.5f); scaleX = sc; scaleY = sc },
                )
            }
        },
    ) { content() }
}

@Composable
private fun FiltersBar(vm: TransactionsViewModel, s: TxListState) {
    val f = s.filters
    OutlinedTextField(
        value = f.query, onValueChange = vm::setQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        singleLine = true, placeholder = { Text("Buscar na descrição") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (f.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Limpar") } },
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(f.type == TxType.EXPENSE, { vm.setType(if (f.type == TxType.EXPENSE) null else TxType.EXPENSE) }, { Text("Saídas") })
        FilterChip(f.type == TxType.INCOME, { vm.setType(if (f.type == TxType.INCOME) null else TxType.INCOME) }, { Text("Entradas") })

        var catOpen by remember { mutableStateOf(false) }
        Box {
            FilterChip(f.categoryId != null, { catOpen = true }, { Text(f.categoryId?.let { s.categories[it]?.name } ?: "Categoria") })
            DropdownMenu(catOpen, { catOpen = false }) {
                DropdownMenuItem({ Text("Todas") }, { vm.setCategory(null); catOpen = false })
                s.categories.values.filter { f.type == null || it.type == f.type }
                    .sortedWith(compareBy({ it.type }, { it.archived }, { it.sortOrder }))
                    .forEach { cat ->
                        DropdownMenuItem(
                            { Text(cat.name + (if (cat.type == TxType.INCOME) " (entrada)" else "") + (if (cat.archived) " · arquivada" else "")) },
                            { vm.setCategory(cat.id); catOpen = false },
                        )
                    }
            }
        }
        var pmOpen by remember { mutableStateOf(false) }
        val wallets = app.meubolso.ui.components.LocalWallets.current.map { it.wallet }
        Box {
            FilterChip(f.walletId != null, { pmOpen = true }, { Text(f.walletId?.let { id -> wallets.firstOrNull { it.id == id }?.name } ?: "Carteira") })
            DropdownMenu(pmOpen, { pmOpen = false }) {
                DropdownMenuItem({ Text("Todas") }, { vm.setWallet(null); pmOpen = false })
                wallets.forEach { w ->
                    DropdownMenuItem({ Text(w.name + if (w.archived) " · arquivada" else "") }, { vm.setWallet(w.id); pmOpen = false })
                }
            }
        }
        if (f.active) TextButton(onClick = vm::clearFilters) { Text("Limpar") }
    }
}
