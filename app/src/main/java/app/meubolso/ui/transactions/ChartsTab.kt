package app.meubolso.ui.transactions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import app.meubolso.ui.components.Motion
import app.meubolso.ui.components.animatedCents
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.ui.components.EmptyState
import app.meubolso.ui.theme.MoneyColors
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ChartsTab(vm: TransactionsViewModel, contentPadding: PaddingValues) {
    val state by vm.charts.collectAsStateWithLifecycle()
    val s = state ?: return
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(bottom = contentPadding.calculateBottomPadding() + 88.dp),
    ) {
        ChartCard("Saídas por categoria") {
            if (s.slices.isEmpty()) EmptyState(Icons.Filled.PieChart, "Sem saídas neste mês", "Os gastos lançados aparecem aqui divididos por categoria.")
            else Donut(s)
        }
        ChartCard("Últimos 6 meses") { Bars(s) }
        ChartCard("Evolução do saldo (fim de cada mês)") { BalanceLine(s) }
    }
}

/** Progresso 0→1 que reinicia quando os dados mudam (troca de mês, novo lançamento). */
@Composable
private fun drawProgress(key: Any, durationMs: Int = 900): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(key) {
        a.snapTo(0f)
        a.animateTo(1f, tween(durationMs, easing = Motion.Emphasized))
    }
    return a.value
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Donut(s: ChartsState) {
    val total = s.slices.sumOf { it.total }.coerceAtLeast(1)
    val p = drawProgress(s.slices)
    val shownTotal = animatedCents(total)
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val stroke = 36.dp.toPx()
            var start = -90f + (1f - p) * -120f
            val gap = if (s.slices.size > 1) 1.5f else 0f
            s.slices.forEach { sl ->
                val sweep = 360f * sl.total / total * p
                drawArc(
                    Color(sl.category?.color ?: 0xFF757575), start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                    topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total", style = MaterialTheme.typography.labelMedium)
            Text(Money.format(shownTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(8.dp))
    s.slices.forEach { sl ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(12.dp)) { drawCircle(Color(sl.category?.color ?: 0xFF757575)) }
            Spacer(Modifier.width(8.dp))
            Text(sl.category?.name ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${(sl.total * 100 / total)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(Money.format(sl.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Bars(s: ChartsState) {
    val inc = MoneyColors.income
    val exp = MoneyColors.expense
    val maxV = s.bars.maxOf { max(it.income, it.expense) }.coerceAtLeast(1)
    val p = drawProgress(s.bars, 1100)
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val n = s.bars.size
        val slot = size.width / n
        val bw = slot * 0.3f
        s.bars.forEachIndexed { i, b ->
            val x0 = i * slot + slot * 0.18f
            // Cada mês cresce um pouco depois do anterior.
            val local = ((p * (n + 2) - i) / 3f).coerceIn(0f, 1f)
            val hi = size.height * b.income / maxV * local
            val he = size.height * b.expense / maxV * local
            drawRoundRect(inc, Offset(x0, size.height - hi), Size(bw, hi), CornerRadius(4.dp.toPx()))
            drawRoundRect(exp, Offset(x0 + bw + 2.dp.toPx(), size.height - he), Size(bw, he), CornerRadius(4.dp.toPx()))
        }
    }
    MonthLabels(s)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Legend(inc, "Entradas"); Legend(exp, "Saídas")
    }
}

@Composable
private fun MonthLabels(s: ChartsState) {
    Row(Modifier.fillMaxWidth()) {
        s.bars.forEach {
            Text(Dates.monthShort(it.month.ym), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(c) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BalanceLine(s: ChartsState) {
    val line = MaterialTheme.colorScheme.primary
    val zero = MaterialTheme.colorScheme.outline
    val values = s.bars.map { it.endBalance }
    val maxV = values.max().coerceAtLeast(0)
    val minV = values.min().coerceAtMost(0)
    val range = (maxV - minV).coerceAtLeast(1)
    val p = drawProgress(values, 1200)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val n = values.size
        val slot = size.width / n
        fun y(v: Long) = size.height - size.height * (v - minV) / range
        drawLine(zero, Offset(0f, y(0)), Offset(size.width, y(0)), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        // A linha é "desenhada" da esquerda para a direita.
        val pts = values.mapIndexed { i, v -> Offset(i * slot + slot / 2, y(v)) }
        val upto = p * (pts.size - 1)
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val f = (upto - (i - 1)).coerceIn(0f, 1f)
            if (f <= 0f) break
            val a = pts[i - 1]; val b = pts[i]
            path.lineTo(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
        }
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEachIndexed { i, pt ->
            val f = ((upto - i) + 1f).coerceIn(0f, 1f)
            if (f > 0f) drawCircle(line, 4.dp.toPx() * f, pt)
        }
    }
    MonthLabels(s)
    val last = values.last()
    Text(
        "Saldo acumulado: ${Money.format(last)}",
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp),
        color = if (last < 0) MoneyColors.expense else MaterialTheme.colorScheme.onSurface,
    )
    if (abs(last) == 0L && values.all { it == 0L }) Text("Sem lançamentos no período.", style = MaterialTheme.typography.bodySmall)
}
