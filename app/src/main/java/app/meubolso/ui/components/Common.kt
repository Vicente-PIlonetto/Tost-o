package app.meubolso.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.meubolso.core.FinancialMonth
import app.meubolso.core.Money
import app.meubolso.data.db.Category
import app.meubolso.data.db.TxType
import app.meubolso.ui.theme.MoneyColors

@Composable
fun CategoryBadge(icon: String, color: Long, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color(color)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CategoryIcons.get(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun CategoryBadge(category: Category?, size: Dp = 40.dp, modifier: Modifier = Modifier) =
    CategoryBadge(category?.icon ?: "more", category?.color ?: 0xFF757575, size, modifier)

@Composable
fun AmountText(
    cents: Long,
    type: TxType,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val signed = if (type == TxType.INCOME) cents else -cents
    Text(
        Money.format(signed, showPlus = true),
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = if (type == TxType.INCOME) MoneyColors.income else MaterialTheme.colorScheme.onSurface,
    )
}

/** Estado vazio amigável, explicando o que fazer. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.floating().size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** Troca de mês: o nome desliza para o lado da navegação. */
@Composable
fun MonthSwitcher(month: FinancialMonth, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior") }
        AnimatedContent(
            targetState = month,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState.ym > initialState.ym) 1 else -1
                (slideInHorizontally(tween(320, easing = Motion.Emphasized)) { it / 3 * dir } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(220)) { -it / 3 * dir } + fadeOut(tween(150)))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.Center,
            label = "month",
        ) { m ->
            Text(m.label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês") }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

val Long.asColor: Color get() = Color(this)
