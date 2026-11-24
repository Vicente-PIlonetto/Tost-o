package app.meubolso.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

/** Curvas do app: saída suave e um "pulinho" elástico para toques. */
object Motion {
    val EaseOut = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    fun <T> bouncy() = spring<T>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
    fun <T> soft() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Valor em centavos que "conta" até o alvo (na primeira vez parte de zero).
 * Durante a animação mostra valores intermediários; ao final, o valor exato.
 */
@Composable
fun animatedCents(target: Long, durationMs: Int = 700): Long {
    val last = remember { LongArray(1) }
    val from = remember { LongArray(1) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        from[0] = last[0]
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = Motion.EaseOut))
    }
    val p = progress.value
    val v = if (p >= 1f) target else from[0] + ((target - from[0]) * p).roundToLong()
    last[0] = v
    return v
}

/** Clique com "afundada" elástica — dá a sensação de botão físico. */
fun Modifier.bounceClick(enabled: Boolean = true, scaleDown: Float = 0.9f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleDown else 1f, Motion.bouncy(), label = "bounce")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = ripple(bounded = false), enabled = enabled, onClick = onClick)
}

/** Entrada suave (sobe e aparece), com atraso escalonado por [index]. Roda uma vez. */
fun Modifier.enterUp(index: Int = 0): Modifier = composed {
    val anim = remember { Animatable(0f) }
    val dy = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40L * index.coerceAtMost(8))
        anim.animateTo(1f, tween(450, easing = Motion.Emphasized))
    }
    graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * dy
    }
}

/** Flutuação leve e infinita, para ícones de estados vazios. */
fun Modifier.floating(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "float")
    val y by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    val r by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "r")
    val d = with(LocalDensity.current) { 6.dp.toPx() }
    graphicsLayer { translationY = y * d; rotationZ = r * 4f }
}

/** "Balança a cabeça" — usado quando falta o valor. Incremente [trigger] para disparar. */
fun Modifier.shake(trigger: Int): Modifier = composed {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        offset.animateTo(0f, keyframes {
            durationMillis = 380
            -14f at 50; 12f at 110; -9f at 170; 6f at 230; -3f at 290; 0f at 380
        })
    }
    val d = LocalDensity.current.density
    graphicsLayer { translationX = offset.value * d }
}

/** Progresso que anima de 0 até o valor na primeira exibição e depois segue mudanças com mola. */
@Composable
fun animatedProgress(target: Float): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target, tween(800, easing = Motion.EaseOut)) }
    return a.value
}
