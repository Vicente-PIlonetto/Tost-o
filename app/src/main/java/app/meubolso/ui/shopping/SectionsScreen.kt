package app.meubolso.ui.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.container
import app.meubolso.data.db.MarketSection
import app.meubolso.data.db.ShoppingItem
import app.meubolso.data.repo.ShoppingRepository
import app.meubolso.ui.components.EmptyState
import app.meubolso.ui.components.SectionTitle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** Ajustes → Seções do mercado (ordem do percurso) e itens do catálogo. */
@Composable
fun SectionsScreen(vm: ShoppingViewModel, contentPadding: PaddingValues) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val repo = LocalContext.current.container.shopping
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingSection by remember { mutableStateOf<MarketSection?>(null) }
    var editingItem by remember { mutableStateOf<ShoppingItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Seções") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Itens do catálogo") })
            }
            if (tab == 0) LazyColumn(contentPadding = PaddingValues(bottom = 96.dp + contentPadding.calculateBottomPadding())) {
                item {
                    Text("Coloque as seções na ordem em que você anda no mercado — a lista segue essa ordem.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(s.sections, key = { it.id }) { sec ->
                    val count = s.catalog.count { it.sectionId == sec.id }
                    ListItem(
                        headlineContent = { Text(sec.name) },
                        supportingContent = { Text("$count ${if (count == 1) "item" else "itens"}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { scope.launch { repo.reorderSections(s.sections.move(sec, -1)) } }, enabled = sec != s.sections.first()) {
                                    Icon(Icons.Filled.KeyboardArrowUp, "Subir")
                                }
                                IconButton(onClick = { scope.launch { repo.reorderSections(s.sections.move(sec, 1)) } }, enabled = sec != s.sections.last()) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Descer")
                                }
                            }
                        },
                        modifier = Modifier.animateItem().clickable { editingSection = sec },
                    )
                }
            } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + contentPadding.calculateBottomPadding())) {
                if (s.catalog.isEmpty()) item { EmptyState(Icons.Filled.Inventory2, "Catálogo vazio", "Os itens entram aqui quando você os adiciona na lista.") }
                s.sections.forEach { sec ->
                    val list = s.catalog.filter { it.sectionId == sec.id }
                    if (list.isNotEmpty()) {
                        item(key = "h" + sec.id) { SectionTitle(sec.name) }
                        items(list, key = { "i" + it.id }) { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                supportingContent = item.unit.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                                modifier = Modifier.animateItem().clickable { editingItem = item },
                            )
                        }
                    }
                }
            }
        }
        if (tab == 0) ExtendedFloatingActionButton(
            onClick = { editingSection = MarketSection(name = "") },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nova seção") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }

    editingSection?.let { sec ->
        var name by remember(sec) { mutableStateOf(sec.name) }
        val others = s.sections.filter { it.id != sec.id }
        AlertDialog(
            onDismissRequest = { editingSection = null },
            title = { Text(if (sec.id == 0L) "Nova seção" else "Editar seção") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)
                    if (sec.id != 0L && others.isNotEmpty()) {
                        val target = others.firstOrNull { it.name == "Outros" } ?: others.first()
                        TextButton(onClick = {
                            scope.launch { repo.deleteSection(sec, target) }; editingSection = null
                        }) { Text("Excluir seção (itens vão para \"${target.name}\")", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { repo.saveSection(sec.copy(name = name.trim())) }; editingSection = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editingSection = null }) { Text("Cancelar") } },
        )
    }

    editingItem?.let { item ->
        var name by remember(item) { mutableStateOf(item.name) }
        var section by remember(item) { mutableStateOf(item.sectionId) }
        var unit by remember(item) { mutableStateOf(item.unit) }
        var confirmDelete by remember(item) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Editar item") },
            text = {
                Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)
                    Text("Seção", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.sections.forEach { sec -> FilterChip(sec.id == section, { section = sec.id }, { Text(sec.name) }) }
                    }
                    Text("Unidade", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShoppingRepository.UNITS.forEach { u -> FilterChip(u == unit, { unit = u }, { Text(u.ifEmpty { "—" }) }) }
                    }
                    if (confirmDelete) Text("Toque de novo para confirmar: o histórico de compras deste item também é apagado.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        if (confirmDelete) { scope.launch { repo.deleteItem(item) }; editingItem = null } else confirmDelete = true
                    }) { Text("Excluir do catálogo", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { repo.updateItem(item.copy(name = name.trim(), sectionId = section, unit = unit)) }; editingItem = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text("Cancelar") } },
        )
    }
}

private fun List<MarketSection>.move(s: MarketSection, delta: Int): List<MarketSection> {
    val l = toMutableList()
    val i = l.indexOfFirst { it.id == s.id }
    val j = (i + delta).coerceIn(0, l.lastIndex)
    l.add(j, l.removeAt(i))
    return l
}
