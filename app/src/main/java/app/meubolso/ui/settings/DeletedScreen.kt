package app.meubolso.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.TxType
import app.meubolso.ui.components.CategoryBadge
import app.meubolso.ui.components.EmptyState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val stamp = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")

/** Histórico de lançamentos excluídos, com a justificativa. Permite restaurar. */
@Composable
fun DeletedScreen(vm: SettingsViewModel, contentPadding: PaddingValues, snackbar: SnackbarHostState) {
    val list by vm.deleted.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val catMap = cats.associateBy { it.id }
    val scope = rememberCoroutineScope()

    if (list.isEmpty()) {
        EmptyState(Icons.Filled.History, "Nenhum lançamento excluído",
            "Quando você excluir um lançamento, ele aparece aqui com o motivo informado — e dá para restaurar.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp, top = 8.dp)) {
        items(list, key = { it.id }) { d ->
            Card(
                Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(catMap[d.categoryId], 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.description.ifBlank { catMap[d.categoryId]?.name ?: "—" }, style = MaterialTheme.typography.bodyLarge)
                            Text("${Dates.format(d.date)} · ${d.origin.label}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(if (d.type == TxType.INCOME) d.amount else -d.amount), fontWeight = FontWeight.SemiBold)
                    }
                    Text("“${d.reason}”", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Excluído em " + Instant.ofEpochMilli(d.deletedAt).atZone(ZoneId.systemDefault()).format(stamp),
                            Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = {
                            vm.restoreDeleted(d)
                            scope.launch { snackbar.showSnackbar("Lançamento restaurado") }
                        }) { Text("Restaurar") }
                    }
                }
            }
        }
    }
}
