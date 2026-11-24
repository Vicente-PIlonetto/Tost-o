package app.meubolso.ui.savings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.SavingsKind
import app.meubolso.data.db.SavingsMovement
import app.meubolso.ui.components.DateChip
import app.meubolso.ui.components.EmptyState
import app.meubolso.ui.components.MoneyField
import app.meubolso.ui.components.SectionTitle
import app.meubolso.ui.components.animatedCents
import app.meubolso.ui.components.showUndo
import app.meubolso.ui.components.animatedProgress
import app.meubolso.ui.components.enterUp
import app.meubolso.ui.theme.CoinGold
import app.meubolso.ui.theme.MoneyColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SavingsScreen(vm: SavingsViewModel, contentPadding: PaddingValues, snackbar: SnackbarHostState) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var adding by remember { mutableStateOf<SavingsKind?>(null) }
    var editingGoal by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)) {
        item(key = "card") { SavingsHero(s, Modifier.enterUp(0)) }
        item(key = "buttons") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).enterUp(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = SavingsKind.DEPOSIT }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Guardar")
                }
                FilledTonalButton(onClick = { adding = SavingsKind.WITHDRAW }, Modifier.weight(1f), enabled = s.balance > 0) {
                    Icon(Icons.Filled.Upload, null); Spacer(Modifier.width(6.dp)); Text("Resgatar")
                }
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { editingGoal = true }) {
                    Icon(Icons.Filled.Flag, null); Spacer(Modifier.width(6.dp))
                    Text(if (s.goal > 0) "Meta: ${Money.format(s.goal)}" else "Definir uma meta")
                }
                TextButton(onClick = { adding = SavingsKind.ADJUST }) {
                    Icon(Icons.Filled.Tune, null); Spacer(Modifier.width(6.dp)); Text("Ajustar saldo")
                }
            }
            Text(
                "Guardar tira o valor do \"livre pra gastar\" do mês, mas não conta como gasto. Resgatar devolve. " +
                    "Use \"Ajustar saldo\" para informar o que você já tinha guardado.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "histT") { SectionTitle("Histórico") }
        if (s.movements.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Filled.Savings, "Cofrinho vazio", "Toque em \"Guardar\" sempre que separar um dinheiro. Pouco a pouco faz diferença.")
        }
        items(s.movements, key = { it.id }) { m ->
            MovementRow(m, Modifier.animateItem()) {
                vm.delete(m)
                scope.launch {
                    if (snackbar.showUndo("Movimento removido")) vm.restore(m)
                }
            }
        }
    }

    adding?.let { kind ->
        AddMovementDialog(kind, s.balance, onDismiss = { adding = null }) { amount, date, note ->
            vm.add(kind, amount, date, note); adding = null
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(when (kind) {
                    SavingsKind.DEPOSIT -> "Guardado! ${Money.format(amount)} no cofrinho"
                    SavingsKind.WITHDRAW -> "Resgatado: ${Money.format(amount)}"
                    SavingsKind.ADJUST -> "Saldo ajustado"
                })
            }
        }
    }
    if (editingGoal) {
        var goal by remember { mutableLongStateOf(s.goal) }
        AlertDialog(
            onDismissRequest = { editingGoal = false },
            title = { Text("Meta da poupança") },
            text = {
                Column {
                    Text("Uma reserva de emergência costuma ser de 3 a 6 meses dos seus gastos.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    MoneyField(goal, { goal = it }, "Meta", Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.setGoal(goal); editingGoal = false }) { Text("Salvar") } },
            dismissButton = {
                TextButton(onClick = { vm.setGoal(0); editingGoal = false }) { Text("Sem meta") }
            },
        )
    }
}

@Composable
fun SavingsHero(s: SavingsState, modifier: Modifier = Modifier) {
    val bal = animatedCents(s.balance)
    val p = animatedProgress(s.progress)
    Box(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7A5900), Color(0xFF4A3500)), Offset.Zero, Offset(900f, 900f))),
    ) {
        Canvas(Modifier.size(140.dp).align(Alignment.TopEnd)) {
            drawCircle(CoinGold.copy(alpha = 0.18f), size.minDimension * 0.5f, Offset(size.width * 0.75f, size.height * 0.3f))
        }
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = CoinGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardado", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
            }
            Text(Money.format(bal), style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp), fontWeight = FontWeight.Black, color = Color.White)
            if (s.savedThisMonth != 0L) Text(
                (if (s.savedThisMonth > 0) "+" else "") + Money.format(s.savedThisMonth) + " neste mês",
                style = MaterialTheme.typography.bodyMedium, color = CoinGold,
            )
            if (s.goal > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { p }, modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = CoinGold, trackColor = Color.White.copy(alpha = 0.15f), strokeCap = StrokeCap.Round,
                    gapSize = 0.dp, drawStopIndicator = {},
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (s.balance >= s.goal) "Meta batida! 🎉" else "${(s.progress * 100).toInt()}% da meta de ${Money.format(s.goal)}",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun MovementRow(m: SavingsMovement, modifier: Modifier, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().clickable { confirm = true }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.note.ifBlank { m.kind.label }, style = MaterialTheme.typography.bodyLarge)
            Text("${m.kind.label} · ${Dates.format(m.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val d = m.balanceDelta
        Text(Money.format(d, showPlus = true), fontWeight = FontWeight.SemiBold,
            color = if (d >= 0) MoneyColors.income else MoneyColors.expense)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Remover este movimento?") },
        text = { Text("${m.kind.label} de ${Money.format(m.amount)} em ${Dates.format(m.date)}") },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Remover", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun AddMovementDialog(kind: SavingsKind, balance: Long, onDismiss: () -> Unit, onConfirm: (Long, LocalDate, String) -> Unit) {
    var amount by remember { mutableLongStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    // Ajuste: informa o saldo real; gravamos a diferença.
    var adjustTo by remember { mutableLongStateOf(balance) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (kind) { SavingsKind.DEPOSIT -> "Guardar dinheiro"; SavingsKind.WITHDRAW -> "Resgatar"; SavingsKind.ADJUST -> "Ajustar saldo" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == SavingsKind.ADJUST) {
                    Text("Informe quanto você tem guardado hoje. Não mexe no mês.", style = MaterialTheme.typography.bodySmall)
                    MoneyField(adjustTo, { adjustTo = it }, "Saldo real", Modifier.fillMaxWidth())
                } else {
                    MoneyField(amount, { amount = it }, "Valor", Modifier.fillMaxWidth(),
                        isError = kind == SavingsKind.WITHDRAW && amount > balance)
                    if (kind == SavingsKind.WITHDRAW && amount > balance) Text("Maior que o saldo guardado", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    DateChip(date, { date = it })
                }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Observação (opcional)") }, singleLine = true)
            }
        },
        confirmButton = {
            val ok = if (kind == SavingsKind.ADJUST) adjustTo != balance else amount > 0 && !(kind == SavingsKind.WITHDRAW && amount > balance)
            TextButton(enabled = ok, onClick = {
                if (kind == SavingsKind.ADJUST) onConfirm(adjustTo - balance, LocalDate.now(), note.ifBlank { "Ajuste de saldo" })
                else onConfirm(amount, date, note)
            }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
