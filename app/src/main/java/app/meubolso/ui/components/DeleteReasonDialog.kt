package app.meubolso.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.Transaction

private val QuickReasons = listOf(
    "Lançado em duplicidade", "Valor errado", "Compra cancelada / estornada", "Lançamento de teste", "Categoria errada",
)

/** Pede a justificativa antes de excluir um lançamento. A justificativa fica salva em Ajustes → Excluídos. */
@Composable
fun DeleteReasonDialog(t: Transaction, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DeleteOutline, null) },
        title = { Text("Excluir lançamento?") },
        text = {
            Column {
                Text(
                    "${Money.format(t.amount)} · ${Dates.format(t.date)}" + (if (t.description.isNotBlank()) " · ${t.description}" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text("Por que você está excluindo? Isso fica guardado no histórico de excluídos.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickReasons.forEach { r -> SuggestionChip(onClick = { reason = r }, label = { Text(r) }) }
                }
                OutlinedTextField(
                    reason, { reason = it }, Modifier.fillMaxWidth().shake(if (tried && reason.isBlank()) 1 else 0),
                    label = { Text("Justificativa") }, minLines = 2,
                    isError = tried && reason.isBlank(),
                    supportingText = { if (tried && reason.isBlank()) Text("Escreva o motivo para excluir") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { tried = true; if (reason.isNotBlank()) onConfirm(reason) }) {
                Text("Excluir", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
