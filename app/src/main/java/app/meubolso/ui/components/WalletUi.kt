package app.meubolso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.Wallet
import app.meubolso.data.db.WalletKind
import app.meubolso.domain.PaymentChoice
import app.meubolso.domain.WalletStatus
import app.meubolso.domain.Wallets
import app.meubolso.ui.theme.MoneyColors

/** Carteiras do app, com a situação de hoje. Fornecido na tela principal. */
val LocalWallets = compositionLocalOf<List<WalletStatus>> { emptyList() }

fun WalletKind.icon(): ImageVector = when (this) {
    WalletKind.ACCOUNT -> Icons.Filled.AccountBalance
    WalletKind.CREDIT_CARD -> Icons.Filled.CreditCard
    WalletKind.VOUCHER -> Icons.Filled.Restaurant
    WalletKind.CASH -> Icons.Filled.Payments
}

@Composable
fun WalletBadge(w: Wallet, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(w.color)),
        contentAlignment = Alignment.Center,
    ) { Icon(w.kind.icon(), null, tint = Color.White, modifier = Modifier.size(size * 0.55f)) }
}

/** Uma linha curta do que importa na carteira: saldo, limite livre ou saldo do vale. */
fun WalletStatus.headline(): String = when (kind) {
    WalletKind.ACCOUNT, WalletKind.CASH -> "Saldo ${Money.format(balance)}"
    WalletKind.CREDIT_CARD -> "Livre ${Money.format(available)}"
    WalletKind.VOUCHER -> "Sobra ${Money.format(available)}"
}

/** Detalhe do ciclo: fechamento e vencimento da fatura, ou a próxima recarga do vale. */
fun WalletStatus.cycleLine(): String? = when (kind) {
    WalletKind.CREDIT_CARD -> invoice?.let { "Fatura aberta ${Money.format(openInvoice)} · fecha ${Dates.formatShort(it.closing)}, vence ${Dates.formatShort(it.due)}" }
    WalletKind.VOUCHER -> cycle?.let { "Gastou ${Money.format(spent)} de ${Money.format(limit)} · recarga ${Dates.formatShort(it.next)}" }
    else -> null
}

/**
 * Escolha de onde sai o dinheiro: uma carteira e, se for conta, Pix, débito ou boleto.
 * Mostra quanto sobra no cartão ou no vale e avisa se o valor passa disso.
 */
@Composable
fun WalletPicker(choice: PaymentChoice, onChange: (PaymentChoice) -> Unit, amount: Long = 0, modifier: Modifier = Modifier) {
    val statuses = LocalWallets.current.filter { !it.wallet.archived }
    if (statuses.isEmpty()) return
    val selected = Wallets.resolve(choice.walletId, choice.method, statuses.map { it.wallet })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            statuses.forEach { st ->
                val w = st.wallet
                FilterChip(
                    selected = selected?.id == w.id,
                    onClick = { onChange(PaymentChoice(w.id, Wallets.paymentFor(w.kind, choice.method))) },
                    label = { Text(w.name, maxLines = 1) },
                    leadingIcon = { Icon(w.kind.icon(), null, Modifier.size(18.dp), tint = Color(w.color)) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
        val st = statuses.firstOrNull { it.wallet.id == selected?.id }
        if (st != null && selected?.kind == WalletKind.ACCOUNT) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(PaymentMethod.PIX, PaymentMethod.DEBITO, PaymentMethod.BOLETO).forEach { pm ->
                    FilterChip(
                        choice.method == pm, { onChange(PaymentChoice(selected.id, if (choice.method == pm) null else pm)) },
                        { Text(pm.label) },
                    )
                }
            }
        }
        if (st != null && (st.kind == WalletKind.CREDIT_CARD || st.kind == WalletKind.VOUCHER)) {
            val over = amount > st.available
            Text(
                if (over) "Passa do que sobra: ${st.headline().lowercase()}" else st.headline(),
                style = MaterialTheme.typography.bodySmall,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Cartão de uma carteira: nome, número principal e barra de uso (cartão e vale). */
@Composable
fun WalletCard(st: WalletStatus, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val w = st.wallet
    val tint = Color(w.color)
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.12f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WalletBadge(w, 30.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(w.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(w.kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val main = when (st.kind) {
            WalletKind.CREDIT_CARD, WalletKind.VOUCHER -> st.available
            else -> st.balance
        }
        val shown = animatedCents(main)
        Text(
            Money.format(shown), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = if (main < 0) MoneyColors.expense else MaterialTheme.colorScheme.onSurface, maxLines = 1,
        )
        when (st.kind) {
            WalletKind.CREDIT_CARD -> {
                Text("livre de ${Money.format(st.limit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                UsageBar(st.usedFraction, tint)
                st.cycleLine()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (st.closedDue > 0) Text(
                    "Fatura fechada ${Money.format(st.closedDue)}" + (st.closedDueDate?.let { " · vence ${Dates.formatShort(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MoneyColors.expense, fontWeight = FontWeight.SemiBold,
                )
            }
            WalletKind.VOUCHER -> {
                Text("sobra no vale", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                UsageBar(st.usedFraction, tint)
                st.cycleLine()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> Text("saldo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
private fun UsageBar(fraction: Float, tint: Color) {
    val p = animatedProgress(fraction.coerceIn(0f, 1f))
    val color = when {
        fraction >= 1f -> MoneyColors.expense
        fraction >= 0.8f -> Color(0xFFF2A900)
        else -> tint
    }
    LinearProgressIndicator(
        progress = { p }, modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp),
        color = color, trackColor = tint.copy(alpha = 0.18f), strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
    )
}
