package app.meubolso.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.meubolso.core.Dates
import app.meubolso.data.db.Category
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxOrigin

@Composable
fun TransactionRow(
    t: Transaction,
    category: Category?,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.description.ifBlank { category?.name ?: "Sem categoria" },
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                if (t.description.isNotBlank()) add(category?.name ?: "")
                if (showDate) add(Dates.formatShort(t.date))
                val wallets = LocalWallets.current.map { it.wallet }
                val w = app.meubolso.domain.Wallets.resolve(t.walletId, t.paymentMethod, wallets)
                when {
                    w == null -> t.paymentMethod?.let { add(it.label) }
                    w.kind == app.meubolso.data.db.WalletKind.ACCOUNT && t.paymentMethod != null && wallets.count { it.kind == w.kind } == 1 -> add(t.paymentMethod.label)
                    w.kind == app.meubolso.data.db.WalletKind.ACCOUNT && t.paymentMethod != null -> add("${w.name} (${t.paymentMethod.label})")
                    else -> add(w.name)
                }
                if (t.origin != TxOrigin.MANUAL) add(t.origin.label)
            }.filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(
                sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        AmountText(t.amount, t.type)
    }
}
