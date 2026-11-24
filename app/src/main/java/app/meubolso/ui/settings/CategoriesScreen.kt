package app.meubolso.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.core.Money
import app.meubolso.data.db.Category
import app.meubolso.data.db.TxType
import app.meubolso.ui.components.CategoryBadge
import app.meubolso.ui.components.CategoryIcons
import app.meubolso.ui.components.CategoryPalette
import app.meubolso.ui.components.SectionTitle

@Composable
fun CategoriesScreen(vm: SettingsViewModel, contentPadding: PaddingValues) {
    val all by vm.categories.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val type = if (tab == 0) TxType.EXPENSE else TxType.INCOME
    var editing by remember { mutableStateOf<Category?>(null) }

    val active = all.filter { it.type == type && !it.archived }.sortedBy { it.sortOrder }
    val archived = all.filter { it.type == type && it.archived }

    Box(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Column {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Saídas") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Entradas") })
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp + contentPadding.calculateBottomPadding())) {
                items(active, key = { it.id }) { cat ->
                    ListItem(
                        headlineContent = { Text(cat.name) },
                        supportingContent = cat.monthlyLimit?.let { { Text("Limite: ${Money.format(it)}/mês") } },
                        leadingContent = { CategoryBadge(cat) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { vm.move(cat, -1) }, enabled = cat != active.first()) { Icon(Icons.Filled.KeyboardArrowUp, "Subir") }
                                IconButton(onClick = { vm.move(cat, 1) }, enabled = cat != active.last()) { Icon(Icons.Filled.KeyboardArrowDown, "Descer") }
                            }
                        },
                        modifier = Modifier.clickable { editing = cat },
                    )
                }
                if (archived.isNotEmpty()) {
                    item { SectionTitle("Arquivadas") }
                    items(archived, key = { it.id }) { cat ->
                        ListItem(
                            headlineContent = { Text(cat.name, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            supportingContent = { Text("Não aparece no lançamento; o histórico continua") },
                            leadingContent = { CategoryBadge(cat) },
                            modifier = Modifier.clickable { editing = cat },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = vm.newCategory(type) },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nova categoria") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }

    editing?.let { cat ->
        CategoryEditDialog(cat, onDismiss = { editing = null }, onSave = { vm.saveCategory(it); editing = null })
    }
}

@Composable
private fun CategoryEditDialog(initial: Category, onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableStateOf(initial.color) }
    var archived by remember { mutableStateOf(initial.archived) }
    var limitText by remember { mutableStateOf(initial.monthlyLimit?.let { Money.formatNumber(it) } ?: "") }
    val limit = limitText.takeIf { it.isNotBlank() }?.let { Money.parse(it) }
    val limitInvalid = limitText.isNotBlank() && (limit == null || limit <= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nova categoria" else "Editar categoria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(icon, color, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                }
                if (initial.type == TxType.EXPENSE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        limitText, { limitText = it }, label = { Text("Limite mensal (opcional)") },
                        prefix = { Text("R$ ") }, singleLine = true, isError = limitInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text("Avisamos ao chegar em 80% e 100%") },
                    )
                }
                Text("Cor", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryPalette.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .then(if (c == color) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                                .clickable { color = c },
                        )
                    }
                }
                Text("Ícone", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryIcons.map.forEach { (key, vec) ->
                        val sel = key == icon
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (sel) Color(color) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vec, key, tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (initial.id != 0L) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Arquivada")
                            Text("Some do lançamento, mas o histórico é mantido", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(archived, { archived = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !limitInvalid,
                onClick = { onSave(initial.copy(name = name.trim(), icon = icon, color = color, archived = archived, monthlyLimit = limit)) },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
