package app.meubolso.ui.bills

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.Bill
import app.meubolso.data.db.RecurringIncome
import app.meubolso.domain.BillOccurrence
import app.meubolso.domain.BillStatus
import app.meubolso.domain.IncomeOccurrence
import app.meubolso.domain.Recurrence
import app.meubolso.notify.ReminderWorker
import app.meubolso.ui.components.CategoryBadge
import app.meubolso.ui.components.EmptyState
import app.meubolso.ui.components.MonthSwitcher
import app.meubolso.ui.components.SectionTitle
import app.meubolso.ui.components.animatedCents
import app.meubolso.ui.components.enterUp
import app.meubolso.ui.components.rememberNotificationPermission
import app.meubolso.ui.theme.MoneyColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import app.meubolso.core.PtBr

@Composable
fun BillsScreen(vm: BillsViewModel, contentPadding: PaddingValues) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingBill by remember { mutableStateOf<Bill?>(null) }
    var editingIncome by remember { mutableStateOf<RecurringIncome?>(null) }
    var paying by remember { mutableStateOf<BillOccurrence?>(null) }
    var receiving by remember { mutableStateOf<IncomeOccurrence?>(null) }
    var actions by remember { mutableStateOf<BillRow?>(null) }
    val askPermission = rememberNotificationPermission()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Contas") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Calendário") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Entradas fixas") })
            }
            MonthSwitcher(s.month, vm::prev, vm::next, Modifier.padding(horizontal = 4.dp))
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(120))) }, label = "billsTab") { t ->
                when (t) {
                    0 -> BillsList(s, contentPadding, onRow = { actions = it }, onPay = { paying = it })
                    1 -> CalendarTab(s, contentPadding, onRow = { actions = it }, onPay = { paying = it })
                    else -> IncomesTab(s, contentPadding, onEdit = { editingIncome = it }, onConfirm = { receiving = it })
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (tab == 2) editingIncome = vm.newIncome() else editingBill = vm.newBill() },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(if (tab == 2) "Nova entrada" else "Nova conta") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }

    editingBill?.let { b ->
        BillEditorSheet(b, s.categories.values, onDismiss = { editingBill = null },
            onSave = { vm.saveBill(it) { ReminderWorker.runNow(ctx) }; editingBill = null; askPermission() },
            onDelete = { vm.deleteBill(it); editingBill = null })
    }
    editingIncome?.let { i ->
        IncomeEditorSheet(i, s.categories.values, onDismiss = { editingIncome = null },
            onSave = { vm.saveIncome(it) { ReminderWorker.runNow(ctx) }; editingIncome = null; askPermission() },
            onDelete = { vm.deleteIncome(it); editingIncome = null })
    }
    paying?.let { o ->
        ConfirmAmountDialog(
            "Pagar ${o.bill.name}", o.estimate, "Marcar como paga", showPayment = true,
            initialPayment = o.bill.paymentMethod, showCreateTx = true, onDismiss = { paying = null },
        ) { amount, date, pm, create -> vm.markPaid(o, amount, date, pm, create); paying = null }
    }
    receiving?.let { o ->
        ConfirmAmountDialog(
            "${o.income.name} caiu?", o.income.amount, "Confirmar", showPayment = false,
            initialPayment = null, showCreateTx = false, onDismiss = { receiving = null },
        ) { amount, date, _, _ -> vm.confirmIncome(o, amount, date); receiving = null }
    }
    actions?.let { row ->
        BillActionsSheet(row, s.today, onDismiss = { actions = null },
            onPay = { actions = null; paying = it },
            onUnmark = { vm.unmark(it); actions = null },
            onEdit = { actions = null; editingBill = row.bill })
    }
}

@Composable
private fun TotalsCard(s: BillsState) {
    val monthly = animatedCents(s.totals.monthly)
    val yearly = animatedCents(s.totals.yearly)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).enterUp(0),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Todas as contas", style = MaterialTheme.typography.labelMedium)
                Text(perMonth(Money.format(monthly)), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
                Text(Money.format(yearly) + "/ano", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Só assinaturas", style = MaterialTheme.typography.labelMedium)
                Text(perMonth(Money.format(s.totals.subsMonthly)), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                Text(Money.format(s.totals.subsYearly) + "/ano", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (s.committed > 0) Text(
            "Falta pagar neste mês: ${Money.format(s.committed)}",
            Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BillsList(s: BillsState, contentPadding: PaddingValues, onRow: (BillRow) -> Unit, onPay: (BillOccurrence) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp)) {
        item(key = "totals") { TotalsCard(s) }
        if (s.rows.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Filled.EventRepeat, "Nenhuma conta cadastrada",
                "Cadastre aluguel, luz, internet, Netflix… O app avisa antes de vencer e lança o pagamento pra você com um toque.")
        }
        items(s.rows, key = { it.bill.id }) { row ->
            BillRowItem(row, s, Modifier.animateItem(), onClick = { onRow(row) }, onPay = onPay)
        }
    }
}

@Composable
fun StatusChip(status: BillStatus?, due: LocalDate, today: LocalDate, active: Boolean) {
    val (text, bg, fg) = when {
        !active -> Triple("Pausada", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        status == BillStatus.PAID -> Triple("Paga", MoneyColors.income.copy(alpha = 0.15f), MoneyColors.income)
        status == BillStatus.OVERDUE -> Triple("Atrasada", MoneyColors.expense.copy(alpha = 0.15f), MoneyColors.expense)
        status == BillStatus.DUE -> {
            val d = due.toEpochDay() - today.toEpochDay()
            Triple(when (d) { 0L -> "Vence hoje"; 1L -> "Amanhã"; else -> "Em $d dias" },
                if (d <= 2) Color(0xFFF2A900).copy(alpha = 0.18f) else MaterialTheme.colorScheme.secondaryContainer,
                if (d <= 2) Color(0xFF8A6100) else MaterialTheme.colorScheme.onSecondaryContainer)
        }
        else -> Triple("Próx.: ${Dates.formatShort(due)}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val animBg by animateColorAsState(bg, label = "chipBg")
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(animBg).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = fg)
}

@Composable
private fun BillRowItem(row: BillRow, s: BillsState, modifier: Modifier, onClick: () -> Unit, onPay: (BillOccurrence) -> Unit) {
    val b = row.bill
    val occ = row.occurrence
    val status = occ?.status(s.today)
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(s.categories[b.categoryId])
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (b.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            val per = when (b.intervalMonths) { 1 -> "mensal"; 12 -> "anual"; else -> "a cada ${b.intervalMonths} meses" }
            Text("Dia ${b.dueDay} · $per" + (b.paymentMethod?.let { " · ${it.label}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            StatusChip(status, row.nextDue, s.today, b.active)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(billAmountLabel(b, occ), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (b.active && occ != null && status != BillStatus.PAID) {
                FilledTonalButton(onClick = { onPay(occ) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)) { Text("Pagar") }
            } else if (status == BillStatus.PAID) {
                Icon(Icons.Filled.CheckCircle, "Paga", tint = MoneyColors.income)
            }
        }
    }
}

/** "R$ 120,00", "≈ R$ 480,00" (variável, estimado) ou "Variável" (sem estimativa). */
fun billAmountLabel(b: Bill, occ: BillOccurrence?): String = when {
    occ?.payment != null -> Money.format(occ.payment.amount)
    !b.variableAmount -> Money.format(b.amount)
    (occ?.estimate ?: b.amount) > 0 -> "≈ " + Money.format(occ?.estimate ?: b.amount)
    else -> "Variável"
}

@Composable
private fun BillActionsSheet(
    row: BillRow, today: LocalDate, onDismiss: () -> Unit,
    onPay: (BillOccurrence) -> Unit, onUnmark: (BillOccurrence) -> Unit, onEdit: () -> Unit,
) {
    val b = row.bill
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(b.name, style = MaterialTheme.typography.titleLarge)
            Text("${billAmountLabel(b, row.occurrence)} · vence dia ${b.dueDay}" + if (b.variableAmount) " · valor variável" else "",
                style = MaterialTheme.typography.bodyMedium)
            if (b.intervalMonths > 1) Text(
                "Equivale a ${Money.format(Recurrence.monthlyCost(b.amount, b.intervalMonths))}/mês · ${Money.format(Recurrence.yearlyCost(b.amount, b.intervalMonths))}/ano",
                style = MaterialTheme.typography.bodySmall,
            )
            val occ = row.occurrence
            if (occ?.payment != null) {
                Text("Paga em ${Dates.format(occ.payment.paidDate)}: ${Money.format(occ.payment.amount)}" +
                    if (occ.payment.transactionId == null) " (sem lançamento)" else "", color = MoneyColors.income)
                TextButton(onClick = { onUnmark(occ) }) { Text("Desmarcar pagamento") }
                if (occ.payment.transactionId != null) Text(
                    "Ao desmarcar, o lançamento do pagamento vai para os excluídos (com o motivo registrado).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (occ != null && b.active) {
                FilledTonalButton(onClick = { onPay(occ) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Payments, null); Spacer(Modifier.width(8.dp)); Text("Marcar como paga")
                }
            } else {
                Text("Não vence neste mês. Próximo: ${Dates.format(row.nextDue)}", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onEdit) { Text("Editar conta") }
        }
    }
}

@Composable
private fun CalendarTab(s: BillsState, contentPadding: PaddingValues, onRow: (BillRow) -> Unit, onPay: (BillOccurrence) -> Unit) {
    val today = s.today
    var selected by remember(s.month) { mutableStateOf(if (today in s.month) today else s.month.start) }
    val byDay = s.occurrences.groupBy { it.dueDate }
    // Semana começando no domingo.
    val first = s.month.start.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val last = s.month.endInclusive.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    val weeks = generateSequence(first) { it.plusDays(7) }.takeWhile { !it.isAfter(last) }.toList()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp)) {
        item(key = "grid") {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row {
                    (0..6).map { DayOfWeek.SUNDAY.plus(it.toLong()) }.forEach { d ->
                        Text(d.getDisplayName(TextStyle.SHORT, PtBr).take(3).replaceFirstChar { it.uppercase() }, Modifier.weight(1f),
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                weeks.forEach { w ->
                    Row {
                        (0..6).forEach { i ->
                            val day = w.plusDays(i.toLong())
                            DayCell(day, day in s.month, day == today, day == selected, byDay[day].orEmpty(), today, Modifier.weight(1f)) {
                                selected = day
                            }
                        }
                    }
                }
            }
        }
        item(key = "selT") { SectionTitle(Dates.dayHeader(selected)) }
        val list = byDay[selected].orEmpty()
        if (list.isEmpty()) item(key = "none") {
            Text("Nenhum vencimento neste dia.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(list, key = { "c" + it.bill.id }) { o ->
            BillRowItem(BillRow(o.bill, o, o.dueDate), s, Modifier.animateItem(), onClick = { onRow(BillRow(o.bill, o, o.dueDate)) }, onPay = onPay)
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate, inMonth: Boolean, isToday: Boolean, isSelected: Boolean,
    occ: List<BillOccurrence>, today: LocalDate, modifier: Modifier, onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        }, label = "dayBg",
    )
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier.aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, color = fg,
            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp)) {
            occ.take(3).forEach { o ->
                val c = when (o.status(today)) {
                    BillStatus.PAID -> MoneyColors.income
                    BillStatus.OVERDUE -> MoneyColors.expense
                    BillStatus.DUE -> Color(0xFFF2A900)
                }
                Box(Modifier.padding(top = 2.dp).size(6.dp).clip(CircleShape).background(c)
                    .then(if (isSelected) Modifier.border(1.dp, Color.White, CircleShape) else Modifier))
            }
        }
    }
}

@Composable
private fun IncomesTab(s: BillsState, contentPadding: PaddingValues, onEdit: (RecurringIncome) -> Unit, onConfirm: (IncomeOccurrence) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp)) {
        if (s.incomes.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Filled.Payments, "Nenhuma entrada fixa",
                "Cadastre seu salário com o valor e o dia que cai. No dia, o app pergunta \"O salário caiu?\" — um toque e está lançado.")
        }
        items(s.incomes, key = { it.first.id }) { (inc, occ) ->
            Row(
                Modifier.animateItem().fillMaxWidth().clickable { onEdit(inc) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(s.categories[inc.categoryId])
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(inc.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            !inc.active -> "Desligada"
                            occ == null -> "Todo dia ${inc.day}"
                            occ.received -> "Recebida: ${Money.format(occ.receipt!!.amount)}"
                            occ.date.isAfter(s.today) -> "Prevista para ${Dates.formatShort(occ.date)}"
                            else -> "Era para ${Dates.formatShort(occ.date)} — já caiu?"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (occ?.received == true) MoneyColors.income else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(inc.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MoneyColors.income)
                    if (occ != null && !occ.received && inc.active) FilledTonalButton(
                        onClick = { onConfirm(occ) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                    ) { Text("Caiu!") }
                    else if (occ?.received == true) Icon(Icons.Filled.CheckCircle, "Recebida", tint = MoneyColors.income)
                }
            }
        }
    }
}

/** "R$ 1.986,70/mês" com o "/mês" menor, para o valor caber numa linha. */
private fun perMonth(value: String) = androidx.compose.ui.text.buildAnnotatedString {
    append(value)
    pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = androidx.compose.ui.unit.TextUnit(0.6f, androidx.compose.ui.unit.TextUnitType.Em)))
    append("/mês")
    pop()
}
