package app.meubolso.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.meubolso.core.Dates
import app.meubolso.domain.BillOccurrence
import app.meubolso.domain.IncomeOccurrence
import app.meubolso.ui.bills.ConfirmAmountDialog
import app.meubolso.ui.bills.StatusChip
import app.meubolso.ui.components.bounceClick
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.core.Money
import app.meubolso.data.db.Transaction
import app.meubolso.domain.MonthSummary
import app.meubolso.ui.components.CategoryBadge
import app.meubolso.ui.components.EmptyState
import app.meubolso.ui.components.MonthSwitcher
import app.meubolso.ui.components.SectionTitle
import app.meubolso.ui.components.TransactionRow
import app.meubolso.ui.components.animatedCents
import app.meubolso.ui.components.animatedProgress
import app.meubolso.ui.components.enterUp
import app.meubolso.ui.theme.BrandGreen
import app.meubolso.ui.theme.BrandGreenDark
import app.meubolso.ui.theme.CoinGold
import app.meubolso.ui.theme.MoneyColors
import java.time.LocalTime

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    contentPadding: PaddingValues,
    onEdit: (Transaction) -> Unit,
    onSeeAll: () -> Unit,
    onBills: () -> Unit,
    onSavings: () -> Unit,
    onWallets: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var paying by remember { mutableStateOf<BillOccurrence?>(null) }
    var receiving by remember { mutableStateOf<IncomeOccurrence?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 88.dp)) {
        item(key = "greet") {
            Text(
                greeting(), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp).enterUp(0),
            )
        }
        item(key = "month") {
            Column(Modifier.enterUp(1)) {
                MonthSwitcher(s.month, vm::prev, vm::next, Modifier.padding(horizontal = 4.dp))
                AnimatedVisibility(!s.isCurrentMonth, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    TextButton(onClick = vm::today, modifier = Modifier.padding(start = 12.dp)) { Text("Voltar para o mês atual") }
                }
            }
        }
        item(key = "card") { HeroCard(s.summary, Modifier.enterUp(2)) }
        item(key = "wallets") { WalletsRow(Modifier.enterUp(3), onWallets) }

        if (s.pendingIncomes.isNotEmpty()) {
            item(key = "incT") { SectionTitle("Entradas previstas", Modifier.animateItem()) }
            items(s.pendingIncomes, key = { "inc" + it.income.id + it.period }) { o ->
                PendingIncomeRow(o, s, Modifier.animateItem()) { receiving = o }
            }
        }
        if (s.upcomingBills.isNotEmpty()) {
            item(key = "billT") {
                SectionTitle("Próximas contas", Modifier.animateItem(), trailing = { TextButton(onClick = onBills) { Text("Ver contas") } })
            }
            items(s.upcomingBills, key = { "bill" + it.bill.id + it.period }) { o ->
                UpcomingBillRow(o, s, Modifier.animateItem()) { paying = o }
            }
        }
        item(key = "savings") { SavingsMini(s, Modifier.animateItem(), onSavings) }

        if (s.nearLimit.isNotEmpty()) {
            item(key = "limT") { SectionTitle("De olho no limite", Modifier.animateItem()) }
            items(s.nearLimit, key = { "lim" + it.category.id }) { lp -> LimitRow(lp, Modifier.animateItem()) }
        }

        item(key = "lastT") {
            SectionTitle("Últimos lançamentos", Modifier.animateItem(), trailing = {
                if (s.latest.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("Ver todos") }
            })
        }
        if (!s.hasAnyTransaction) {
            item(key = "empty") {
                EmptyState(
                    Icons.Filled.Savings, "Seu bolso está zerado por aqui",
                    "Toque no + para anotar um gasto ou uma entrada. É rapidinho: valor e categoria, pronto.",
                    Modifier.animateItem(),
                )
            }
        } else {
            items(s.latest, key = { "tx" + it.id }) { t ->
                TransactionRow(t, s.categories[t.categoryId], Modifier.animateItem(), showDate = true, onClick = { onEdit(t) })
            }
        }
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
}

@Composable
private fun PendingIncomeRow(o: IncomeOccurrence, s: HomeState, modifier: Modifier, onConfirm: () -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(s.categories[o.income.categoryId], 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(o.income.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (o.date.isAfter(s.today)) "Prevista para ${Dates.formatShort(o.date)}" else "Era para ${Dates.formatShort(o.date)} — já caiu?",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money.format(o.income.amount), color = MoneyColors.income, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onConfirm, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)) { Text("Caiu!") }
    }
}

@Composable
private fun UpcomingBillRow(o: BillOccurrence, s: HomeState, modifier: Modifier, onPay: () -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(s.categories[o.bill.categoryId], 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(o.bill.name, style = MaterialTheme.typography.bodyLarge)
            StatusChip(o.status(s.today), o.dueDate, s.today, true)
        }
        Text(app.meubolso.ui.bills.billAmountLabel(o.bill, o), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onPay, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)) { Text("Pagar") }
    }
}

@Composable
private fun WalletsRow(modifier: Modifier, onWallets: () -> Unit) {
    val statuses = app.meubolso.ui.components.LocalWallets.current.filter { !it.wallet.archived }
    if (statuses.isEmpty()) return
    Column(modifier) {
        SectionTitle("Carteiras", trailing = { TextButton(onClick = onWallets) { Text("Gerenciar") } })
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(statuses, key = { it.wallet.id }) { st ->
                app.meubolso.ui.components.WalletCard(
                    st, Modifier.width(230.dp).animateItem().bounceClick(scaleDown = 0.97f, onClick = onWallets),
                )
            }
        }
    }
}

@Composable
private fun SavingsMini(s: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val bal = animatedCents(s.savingsBalance)
    val p = animatedProgress(if (s.savingsGoal > 0) (s.savingsBalance.toFloat() / s.savingsGoal).coerceIn(0f, 1f) else 0f)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer).bounceClick(scaleDown = 0.97f, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Savings, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Poupança", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(Money.format(bal), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            if (s.savingsGoal > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.tertiary, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
            } else Text("Toque para guardar um dinheiro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Bom dia!"
    in 12..17 -> "Boa tarde!"
    else -> "Boa noite!"
}

/** Frase com o "humor" do mês. */
private fun mood(m: MonthSummary): String = when {
    m.incomeExpected == 0L && m.expenses == 0L -> "Mês novinho. Bora anotar?"
    m.isNegative -> "Opa, passou do planejado. Hora de segurar."
    m.incomeExpected > 0 && m.freeToSpend < m.incomeExpected / 10 -> "Tá apertando — vai com calma."
    m.incomeExpected > 0 && m.freeToSpend > m.incomeExpected / 2 -> "Tudo sob controle por aqui."
    else -> "No caminho certo."
}

@Composable
private fun HeroCard(m: MonthSummary, modifier: Modifier = Modifier) {
    val free = animatedCents(m.freeToSpend)
    val negColor by animateColorAsState(if (m.isNegative) Color(0xFFFFB4AB) else Color.White, label = "neg")
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(shape)
            .background(Brush.linearGradient(listOf(BrandGreen, BrandGreenDark), Offset.Zero, Offset(900f, 900f))),
    ) {
        // Moedas decorativas no canto — a "assinatura" visual do app.
        Canvas(Modifier.matchParentSize()) {
            val r = size.height * 0.42f
            drawCircle(CoinGold.copy(alpha = 0.16f), r, Offset(size.width - r * 0.35f, r * 0.45f))
            drawCircle(CoinGold.copy(alpha = 0.22f), r * 0.62f, Offset(size.width - r * 0.35f, r * 0.45f), style = Stroke(r * 0.07f))
            drawCircle(Color.White.copy(alpha = 0.06f), r * 0.7f, Offset(size.width * 0.12f, size.height * 1.05f))
        }
        Column(Modifier.padding(22.dp)) {
            Text("Livre pra gastar", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
            Text(
                Money.format(free),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp),
                fontWeight = FontWeight.Black, color = negColor,
            )
            Text(mood(m), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Entradas", m.incomeExpected,
                    if (m.incomePending > 0) "${Money.format(m.incomeReceived)} já caiu" else null, Modifier.weight(1f))
                Pill("Saídas", m.expenses, null, Modifier.weight(1f))
                Pill("A vencer", m.committed, if (m.saved > 0) "${Money.format(m.saved)} guardado" else null, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Pill(label: String, value: Long, sub: String?, modifier: Modifier) {
    val v = animatedCents(value)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        Text(Money.format(v), style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
    }
}

@Composable
fun LimitRow(lp: LimitProgress, modifier: Modifier = Modifier) {
    val p = animatedProgress(lp.fraction.coerceIn(0f, 1f))
    val over = lp.fraction >= 1f
    val color by animateColorAsState(
        when {
            over -> MoneyColors.expense
            lp.fraction >= 0.8f -> Color(0xFFF2A900)
            else -> MaterialTheme.colorScheme.primary
        }, label = "limColor",
    )
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(lp.category, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(lp.category.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("${Money.format(lp.spent)} de ${Money.format(lp.limit)}", style = MaterialTheme.typography.bodySmall,
                    color = if (over) MoneyColors.expense else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
            )
        }
    }
}
