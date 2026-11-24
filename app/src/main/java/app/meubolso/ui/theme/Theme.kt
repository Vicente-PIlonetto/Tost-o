package app.meubolso.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.meubolso.R

/** Verde do ícone do app. */
val BrandGreen = Color(0xFF1B6C3A)
val BrandGreenDark = Color(0xFF0F4A26)
val BrandMint = Color(0xFFA4F4B4)
val CoinGold = Color(0xFFFFD54F)

private val Light = lightColorScheme(
    primary = BrandGreen, onPrimary = Color.White,
    primaryContainer = BrandMint, onPrimaryContainer = Color(0xFF00210C),
    inversePrimary = Color(0xFF89D79A),
    secondary = Color(0xFF4F6352), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E8D3), onSecondaryContainer = Color(0xFF0D1F12),
    tertiary = Color(0xFF7A5900), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDF9E), onTertiaryContainer = Color(0xFF261A00),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF3), onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF3), onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA), onSurfaceVariant = Color(0xFF414941),
    surfaceTint = BrandGreen,
    inverseSurface = Color(0xFF2D322C), inverseOnSurface = Color(0xFFEDF2EA),
    outline = Color(0xFF717971), outlineVariant = Color(0xFFC1C9BF),
    surfaceBright = Color(0xFFF6FBF3), surfaceDim = Color(0xFFD6DBD4),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainer = Color(0xFFEAEFE7), surfaceContainerHigh = Color(0xFFE4EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF89D79A), onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228), onPrimaryContainer = BrandMint,
    inversePrimary = BrandGreen,
    secondary = Color(0xFFB6CCB8), onSecondary = Color(0xFF223526),
    secondaryContainer = Color(0xFF384B3C), onSecondaryContainer = Color(0xFFD2E8D3),
    tertiary = Color(0xFFF0C048), onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300), onTertiaryContainer = Color(0xFFFFDF9E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101510), onBackground = Color(0xFFDFE4DC),
    surface = Color(0xFF101510), onSurface = Color(0xFFDFE4DC),
    surfaceVariant = Color(0xFF414941), onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceTint = Color(0xFF89D79A),
    inverseSurface = Color(0xFFDFE4DC), inverseOnSurface = Color(0xFF2D322C),
    outline = Color(0xFF8B9389), outlineVariant = Color(0xFF414941),
    surfaceBright = Color(0xFF353A35), surfaceDim = Color(0xFF101510),
    surfaceContainerLowest = Color(0xFF0B0F0B), surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C), surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313630),
)

@OptIn(ExperimentalTextApi::class)
private fun nunito(w: Int) = Font(
    R.font.nunito, weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Nunito = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800), nunito(900))

private val Base = Typography()
private fun TextStyle.n(weight: FontWeight? = null) = copy(fontFamily = Nunito, fontWeight = weight ?: fontWeight)

private val AppTypography = Typography(
    displayLarge = Base.displayLarge.n(FontWeight.ExtraBold),
    displayMedium = Base.displayMedium.n(FontWeight.ExtraBold),
    displaySmall = Base.displaySmall.n(FontWeight.ExtraBold),
    headlineLarge = Base.headlineLarge.n(FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.n(FontWeight.Bold),
    headlineSmall = Base.headlineSmall.n(FontWeight.Bold),
    titleLarge = Base.titleLarge.n(FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.n(FontWeight.Bold),
    titleSmall = Base.titleSmall.n(FontWeight.Bold),
    bodyLarge = Base.bodyLarge.n(FontWeight.Medium),
    bodyMedium = Base.bodyMedium.n(FontWeight.Medium),
    bodySmall = Base.bodySmall.n(FontWeight.Medium),
    labelLarge = Base.labelLarge.n(FontWeight.Bold),
    labelMedium = Base.labelMedium.n(FontWeight.Bold),
    labelSmall = Base.labelSmall.n(FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Tema verde do app. [dynamicColor] = usar as cores do papel de parede (Android 12+), desligado por padrão. */
@Composable
fun MeuBolsoTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

/** Verde/vermelho legíveis nos dois temas, para entradas e saídas. */
object MoneyColors {
    val income: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF7BD88F) else Color(0xFF1B7F3A)
    val expense: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFC62828)
}
