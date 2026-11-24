package app.meubolso.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.Bill
import app.meubolso.data.db.Category
import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.RecurringIncome
import app.meubolso.data.db.TxType
import app.meubolso.domain.Recurrence
import app.meubolso.ui.components.BasicPaymentMethods
import app.meubolso.ui.components.CategoryBadge
import app.meubolso.ui.components.DateChip
import app.meubolso.ui.components.MoneyField
import app.meubolso.ui.components.PaymentChips
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import app.meubolso.core.PtBr

@Composable
fun CategoryPicker(categories: Collection<Category>, type: TxType, selectedId: Long, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = categories.filter { it.type == type && (!it.archived || it.id == selectedId) }.sortedBy { it.sortOrder }
    val sel = options.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            CategoryBadge(sel, 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(sel?.name ?: "Categoria", Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name) }, leadingIcon = { CategoryBadge(c, 24.dp) },
                    onClick = { onSelect(c.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun <T> Dropdown(label: String, value: String, options: List<T>, text: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(value)
            }
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(text(o)) }, { onSelect(o); open = false }) }
        }
    }
}

private enum class Period(val label: String) { MONTHLY("Mensal"), YEARLY("Anual"), EVERY_N("A cada N meses") }

private fun monthName(m: Month) = m.getDisplayName(TextStyle.FULL, PtBr).replaceFirstChar { it.uppercase() }

/** Próximo mês (a partir do atual) que cai em [month]. */
private fun nextYearMonth(month: Month): YearMonth {
    val now = YearMonth.now()
    val candidate = YearMonth.of(now.year, month)
    return if (candidate < now) candidate.plusYears(1) else candidate
}

@Composable
fun BillEditorSheet(
    initial: Bill,
    categories: Collection<Category>,
    onDismiss: () -> Unit,
    onSave: (Bill) -> Unit,
    onDelete: (Bill) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var amount by remember { mutableLongStateOf(initial.amount) }
    var categoryId by remember { mutableLongStateOf(initial.categoryId) }
    var dueDay by remember { mutableIntStateOf(initial.dueDay) }
    var period by remember {
        mutableStateOf(when (initial.intervalMonths) { 1 -> Period.MONTHLY; 12 -> Period.YEARLY; else -> Period.EVERY_N })
    }
    var interval by remember { mutableIntStateOf(if (initial.intervalMonths in 2..11 || initial.intervalMonths > 12) initial.intervalMonths else 2) }
    var anchor by remember { mutableStateOf(initial.anchorMonth) }
    var payment by remember { mutableStateOf(initial.paymentMethod) }
    var remind by remember { mutableIntStateOf(initial.remindDaysBefore) }
    var keywords by remember { mutableStateOf(initial.keywords) }
    var isSub by remember { mutableStateOf(initial.isSubscription) }
    var active by remember { mutableStateOf(initial.active) }
    var variable by remember { mutableStateOf(initial.variableAmount) }
    var tried by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val subsCatId = categories.firstOrNull { it.name == "Assinaturas" && it.type == TxType.EXPENSE }?.id

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (initial.id == 0L) "Nova conta" else "Editar conta", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome (ex.: Netflix, Luz, Aluguel)") },
                singleLine = true, isError = tried && name.isBlank())
            SwitchRow("Valor variável", "Condomínio, luz, água… o valor muda todo mês e você informa ao pagar", variable) { variable = it }
            MoneyField(
                amount, { amount = it }, if (variable) "Valor estimado (opcional)" else "Valor", Modifier.fillMaxWidth(),
                isError = tried && !variable && amount <= 0,
            )
            if (variable) Text(
                "Sem estimativa, o app usa a média dos últimos pagamentos para calcular o comprometido do mês.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CategoryPicker(categories, TxType.EXPENSE, categoryId) {
                categoryId = it
                if (it == subsCatId) isSub = true
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    if (dueDay == 0) "" else dueDay.toString(),
                    { t -> dueDay = t.filter(Char::isDigit).take(2).toIntOrNull()?.coerceIn(1, 31) ?: 0 },
                    Modifier.width(140.dp), label = { Text("Dia do vencimento") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = tried && dueDay == 0,
                )
                Spacer(Modifier.width(12.dp))
                if (dueDay > 28) Text("Em meses mais curtos, vence no último dia.", style = MaterialTheme.typography.bodySmall)
            }

            Text("Periodicidade", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(period == p, {
                        period = p
                        if (p == Period.YEARLY) anchor = nextYearMonth(anchor.month)
                        if (p == Period.MONTHLY && initial.id == 0L) anchor = YearMonth.now()
                    }, SegmentedButtonDefaults.itemShape(i, Period.entries.size)) { Text(p.label, maxLines = 1) }
                }
            }
            when (period) {
                Period.MONTHLY -> {}
                Period.YEARLY -> Dropdown(
                    "Mês do vencimento", monthName(anchor.month), Month.entries.toList(), ::monthName,
                    { anchor = nextYearMonth(it) }, Modifier.fillMaxWidth(),
                )
                Period.EVERY_N -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A cada", Modifier.padding(end = 8.dp))
                    Stepper(interval, 2, 24) { interval = it }
                    Text(" meses", Modifier.padding(end = 8.dp))
                    Dropdown(
                        "Próximo", Dates.monthShort(anchor) + "/" + anchor.year,
                        (0..interval).map { YearMonth.now().plusMonths(it.toLong()) },
                        { Dates.monthName(it) }, { anchor = it }, Modifier.weight(1f),
                    )
                }
            }

            Text("Forma de pagamento", style = MaterialTheme.typography.labelLarge)
            PaymentChips(payment, BasicPaymentMethods) { payment = it }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Avisar", Modifier.padding(end = 8.dp))
                Stepper(remind, 0, 15) { remind = it }
                Text(if (remind == 0) " no dia" else " dia${if (remind > 1) "s" else ""} antes")
            }
            OutlinedTextField(keywords, { keywords = it }, Modifier.fillMaxWidth(),
                label = { Text("Palavras-chave no extrato (opcional)") },
                supportingText = { Text("Separe por vírgula. Ex.: NETFLIX, NETFLIX.COM — usado na importação do extrato") },
                singleLine = true)
            SwitchRow("É uma assinatura", "Entra no total separado de assinaturas", isSub) { isSub = it }
            if (initial.id != 0L) SwitchRow("Ativa", "Pausada: não avisa e não entra nos totais", active) { active = it }

            val interval2 = when (period) { Period.MONTHLY -> 1; Period.YEARLY -> 12; Period.EVERY_N -> interval }
            if (amount > 0 && interval2 > 1) Text(
                "Custo: ${Money.format(Recurrence.monthlyCost(amount, interval2))}/mês · ${Money.format(Recurrence.yearlyCost(amount, interval2))}/ano",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.id != 0L) TextButton(onClick = { confirmDelete = true }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(onClick = {
                    tried = true
                    if (name.isNotBlank() && (variable || amount > 0) && dueDay in 1..31) onSave(
                        initial.copy(
                            name = name.trim(), amount = amount, categoryId = categoryId, dueDay = dueDay,
                            intervalMonths = interval2, anchorMonth = if (period == Period.MONTHLY && initial.id == 0L) YearMonth.now() else anchor,
                            paymentMethod = payment, remindDaysBefore = remind, keywords = keywords.trim(),
                            isSubscription = isSub, active = active, variableAmount = variable,
                        )
                    )
                }) { Text("Salvar") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Excluir \"${initial.name}\"?") },
        text = { Text("A conta sai da lista. Os lançamentos de pagamentos já feitos continuam em Movimentações. Se só quer parar por um tempo, desligue \"Ativa\".") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(initial) }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
    )
}

@Composable
fun IncomeEditorSheet(
    initial: RecurringIncome,
    categories: Collection<Category>,
    onDismiss: () -> Unit,
    onSave: (RecurringIncome) -> Unit,
    onDelete: (RecurringIncome) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var amount by remember { mutableLongStateOf(initial.amount) }
    var categoryId by remember { mutableLongStateOf(initial.categoryId) }
    var day by remember { mutableIntStateOf(initial.day) }
    var active by remember { mutableStateOf(initial.active) }
    var tried by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (initial.id == 0L) "Nova entrada fixa" else "Editar entrada fixa", style = MaterialTheme.typography.titleLarge)
            Text("No dia, o app pergunta se caiu. Confirmando, vira uma entrada em Movimentações.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true, isError = tried && name.isBlank())
            MoneyField(amount, { amount = it }, "Valor previsto", Modifier.fillMaxWidth(), isError = tried && amount <= 0)
            CategoryPicker(categories, TxType.INCOME, categoryId) { categoryId = it }
            OutlinedTextField(
                if (day == 0) "" else day.toString(),
                { t -> day = t.filter(Char::isDigit).take(2).toIntOrNull()?.coerceIn(1, 31) ?: 0 },
                Modifier.width(160.dp), label = { Text("Dia que cai") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = tried && day == 0,
            )
            if (initial.id != 0L) SwitchRow("Ativa", "Desligada: não pergunta e não entra nas previstas", active) { active = it }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.id != 0L) TextButton(onClick = { onDelete(initial) }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(onClick = {
                    tried = true
                    if (name.isNotBlank() && amount > 0 && day in 1..31) onSave(
                        initial.copy(name = name.trim(), amount = amount, categoryId = categoryId, day = day, active = active)
                    )
                }) { Text("Salvar") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
internal fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) { Icon(Icons.Filled.Remove, "Menos") }
        Text("$value", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) { Icon(Icons.Filled.Add, "Mais") }
    }
}

/**
 * Confirmar pagamento de conta ou recebimento de entrada, com valor ajustável
 * (ex.: a conta de luz veio diferente).
 */
@Composable
fun ConfirmAmountDialog(
    title: String,
    initialAmount: Long,
    confirmLabel: String,
    showPayment: Boolean,
    initialPayment: PaymentMethod?,
    showCreateTx: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, date: LocalDate, payment: app.meubolso.domain.PaymentChoice, createTx: Boolean) -> Unit,
) {
    var amount by remember { mutableLongStateOf(initialAmount) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var payment by remember { mutableStateOf(app.meubolso.domain.PaymentChoice(null, initialPayment)) }
    var createTx by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(amount, { amount = it }, "Valor", Modifier.fillMaxWidth())
                if (amount != initialAmount && initialAmount > 0) Text(
                    "Cadastrado: ${Money.format(initialAmount)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DateChip(date, { date = it })
                if (showPayment) app.meubolso.ui.components.WalletPicker(payment, { payment = it }, amount)
                if (showCreateTx) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(createTx, { createTx = it })
                    Text("Lançar em Movimentações", style = MaterialTheme.typography.bodyMedium)
                }
                if (showCreateTx && !createTx) Text(
                    "Use quando já lançou esse pagamento antes — evita contar duas vezes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = amount > 0, onClick = { onConfirm(amount, date, payment, createTx) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
