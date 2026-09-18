package app.auralis.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.auralis.music.AppContainer
import app.auralis.music.data.player.AuralisPalette
import app.auralis.music.data.player.PlayerController
import app.auralis.music.data.remote.SubsonicClient

val LocalPalette = staticCompositionLocalOf { AuralisPalette.darkDefault() }
val LocalClient = staticCompositionLocalOf<SubsonicClient> { error("SubsonicClient not provided") }
val LocalPlayer = staticCompositionLocalOf<PlayerController> { error("PlayerController not provided") }
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

private val AuralisTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.2.sp),
)

enum class ThemeMode { System, Dark, Light }

@Composable
fun AuralisTheme(
    palette: AuralisPalette,
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            secondary = palette.secondary,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceHigh,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            secondary = palette.secondary,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceHigh,
        )
    }
    val top = animateColorAsState(palette.gradientTop, tween(700), label = "gradTop")
    val bottom = animateColorAsState(palette.gradientBottom, tween(700), label = "gradBottom")
    val bg = animateColorAsState(palette.background, tween(700), label = "bg")

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AuralisTypography) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bg.value)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                top.value.copy(alpha = 0.92f),
                                bg.value.copy(alpha = 0.85f),
                                bottom.value,
                            ),
                        ),
                    ),
            ) {
                content()
            }
        }
    }
}

fun Color.glass(dark: Boolean, extra: Float = 0f): Color =
    if (dark) Color.White.copy(alpha = 0.08f + extra) else Color.White.copy(alpha = 0.55f + extra)
