package app.auralis.music.data.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min

/** Signature Plexamp waveform / progress gold. */
val WaveformGold = Color(0xFFE8A317)

data class AuralisPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onBackground: Color,
    val onSurface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val outline: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val scrim: Color,
    val playButton: Color,
    val onPlayButton: Color,
    val blurA: Color,
    val blurB: Color,
    val blurC: Color,
) {
    companion object {
        fun darkDefault() = AuralisPalette(
            isDark = true,
            background = Color(0xFF000000),
            surface = Color(0xFF111111),
            surfaceHigh = Color(0xFF1C1C1C),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFE6E6E6),
            primary = WaveformGold,
            onPrimary = Color(0xFF1A1200),
            secondary = Color(0xFF8A8A8A),
            outline = Color(0xFF2A2A2A),
            gradientTop = Color(0xFF1A1A1A),
            gradientBottom = Color(0xFF000000),
            scrim = Color(0xFF000000),
            playButton = Color(0xE61A1A1A),
            onPlayButton = Color.White,
            blurA = Color(0xFF1A1A1A),
            blurB = Color(0xFF141414),
            blurC = Color(0xFF0A0A0A),
        )

        fun lightDefault() = AuralisPalette(
            isDark = false,
            background = Color(0xFFE2E2E2),
            surface = Color(0xFFEAEAEA),
            surfaceHigh = Color(0xFFD8D8D8),
            onBackground = Color(0xFF141414),
            onSurface = Color(0xFF1A1A1A),
            primary = WaveformGold,
            onPrimary = Color(0xFF1A1200),
            secondary = Color(0xFF6A6A6A),
            outline = Color(0xFFC4C4C4),
            gradientTop = Color(0xFFD8D8D8),
            gradientBottom = Color(0xFFE6E6E6),
            scrim = Color(0xFFE2E2E2),
            playButton = Color(0xFFC2C2C2),
            onPlayButton = Color(0xFF111111),
            blurA = Color(0xFFD4D4D4),
            blurB = Color(0xFFDEDEDE),
            blurC = Color(0xFFE8E8E8),
        )
    }
}

object PaletteExtractor {
    fun from(bitmap: Bitmap, preferDark: Boolean): AuralisPalette {
        val palette = Palette.from(bitmap).clearFilters().generate()
        val vibrant = palette.vibrantSwatch
        val darkVibrant = palette.darkVibrantSwatch
        val muted = palette.mutedSwatch
        val darkMuted = palette.darkMutedSwatch
        val lightMuted = palette.lightMutedSwatch
        val lightVibrant = palette.lightVibrantSwatch
        val dominant = palette.dominantSwatch

        return if (preferDark) {
            val a = (darkVibrant ?: vibrant ?: dominant)?.rgb.toColor(Color(0xFF3A2A22)).asBlur(0.22f, 0.42f, 0.55f)
            val b = (muted ?: darkMuted ?: dominant)?.rgb.toColor(Color(0xFF2A2420)).asBlur(0.16f, 0.32f, 0.45f)
            val c = (darkMuted ?: dominant ?: muted)?.rgb.toColor(Color(0xFF1A1614)).asBlur(0.10f, 0.24f, 0.40f)
            val mini = a.asBlur(0.18f, 0.30f, 0.40f)
            val accent = (vibrant ?: lightVibrant ?: darkVibrant ?: dominant)?.rgb.toColor(WaveformGold).asAccent(dark = true)
            AuralisPalette(
                isDark = true,
                background = Color(0xFF000000),
                surface = mini,
                surfaceHigh = mini.lighten(0.08f),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFE8E8E8),
                primary = accent,
                onPrimary = Color(0xFF1A1200),
                secondary = b,
                outline = Color.White.copy(alpha = 0.10f),
                gradientTop = a,
                gradientBottom = c,
                scrim = Color(0xFF000000),
                playButton = Color(0xE6141414),
                onPlayButton = Color.White,
                blurA = a,
                blurB = b,
                blurC = c,
            )
        } else {
            val a = (vibrant ?: lightVibrant ?: dominant)?.rgb.toColor(Color(0xFFC8C0B8)).asLightBlur(0.62f, 0.78f, 0.52f)
            val b = (muted ?: lightMuted ?: dominant)?.rgb.toColor(Color(0xFFC4C4C4)).asLightBlur(0.68f, 0.82f, 0.40f)
            val c = (lightMuted ?: muted ?: dominant)?.rgb.toColor(Color(0xFFD0D0D0)).asLightBlur(0.74f, 0.86f, 0.32f)
            val page = b.asLightBlur(0.78f, 0.86f, 0.22f)
            val mini = a.asLightBlur(0.70f, 0.80f, 0.36f)
            val accent = (vibrant ?: darkVibrant ?: lightVibrant ?: dominant)?.rgb.toColor(WaveformGold).asAccent(dark = false)
            AuralisPalette(
                isDark = false,
                background = page,
                surface = mini,
                surfaceHigh = a.asLightBlur(0.66f, 0.76f, 0.30f),
                onBackground = Color(0xFF141414),
                onSurface = Color(0xFF1A1A1A),
                primary = accent,
                onPrimary = Color.White,
                secondary = b,
                outline = Color.Black.copy(alpha = 0.10f),
                gradientTop = a,
                gradientBottom = c,
                scrim = page,
                playButton = Color(0xFFBDBDBD),
                onPlayButton = Color(0xFF111111),
                blurA = a,
                blurB = b,
                blurC = c,
            )
        }
    }

    private fun Int?.toColor(fallback: Color): Color = if (this == null) fallback else Color(this)

    private fun Color.darken(amount: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        hsv[2] = max(0f, hsv[2] * (1f - amount))
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun Color.lighten(amount: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        hsv[2] = min(1f, hsv[2] + (1f - hsv[2]) * amount)
        hsv[1] = min(1f, hsv[1] * (1f - amount * 0.35f))
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun Color.asBlur(minV: Float, maxV: Float, sat: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        hsv[2] = hsv[2].coerceIn(minV, maxV)
        hsv[1] = hsv[1].coerceIn(0.12f, 0.72f) * (0.55f + sat * 0.45f)
        hsv[1] = hsv[1].coerceIn(0.12f, 0.70f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun Color.asLightBlur(minV: Float, maxV: Float, sat: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        if (hsv[1] < 0.08f) hsv[1] = 0.12f
        hsv[1] = (hsv[1] * 0.85f).coerceIn(0.16f, sat.coerceIn(0.16f, 0.62f))
        hsv[2] = hsv[2].coerceIn(minV, maxV)
        if (hsv[2] < minV) hsv[2] = minV
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun Color.asAccent(dark: Boolean): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        if (hsv[1] < 0.28f) hsv[1] = 0.45f
        hsv[1] = hsv[1].coerceIn(0.40f, 0.85f)
        hsv[2] = if (dark) hsv[2].coerceIn(0.62f, 0.95f) else hsv[2].coerceIn(0.38f, 0.62f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
