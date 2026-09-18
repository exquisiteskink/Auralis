package app.auralis.music.data.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min

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
) {
    companion object {
        fun darkDefault() = AuralisPalette(
            isDark = true,
            background = Color(0xFF0B0B10),
            surface = Color(0xFF1A1B22),
            surfaceHigh = Color(0xFF262733),
            onBackground = Color(0xFFF4F1EA),
            onSurface = Color(0xFFE8E4DC),
            primary = Color(0xFF8AA4FF),
            onPrimary = Color(0xFF0B0B10),
            secondary = Color(0xFFD4B483),
            outline = Color(0x33FFFFFF),
            gradientTop = Color(0xFF161622),
            gradientBottom = Color(0xFF0B0B10),
            scrim = Color(0xCC0B0B10),
        )

        fun lightDefault() = AuralisPalette(
            isDark = false,
            background = Color(0xFFF4F1EA),
            surface = Color(0xFFFFFFFF),
            surfaceHigh = Color(0xFFEAE4D8),
            onBackground = Color(0xFF16151C),
            onSurface = Color(0xFF1C1B22),
            primary = Color(0xFF3D5A9A),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF8A6A3A),
            outline = Color(0x33000000),
            gradientTop = Color(0xFFE8E2D6),
            gradientBottom = Color(0xFFF4F1EA),
            scrim = Color(0x66F4F1EA),
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
            val bg = (darkMuted ?: darkVibrant ?: dominant)?.rgb.toColor(AuralisPalette.darkDefault().background)
            val primary = (vibrant ?: lightVibrant ?: muted)?.rgb.toColor(AuralisPalette.darkDefault().primary)
            val secondary = (muted ?: darkVibrant)?.rgb.toColor(AuralisPalette.darkDefault().secondary)
            val top = (darkVibrant ?: vibrant ?: dominant)?.rgb.toColor(bg)
            AuralisPalette(
                isDark = true,
                background = bg.darken(0.35f),
                surface = bg.lighten(0.10f).copy(alpha = 1f),
                surfaceHigh = bg.lighten(0.18f),
                onBackground = Color(0xFFF6F3EC),
                onSurface = Color(0xFFECE8E0),
                primary = primary.ensureContrast(),
                onPrimary = Color(0xFF0B0B10),
                secondary = secondary,
                outline = Color.White.copy(alpha = 0.14f),
                gradientTop = top.darken(0.15f),
                gradientBottom = bg.darken(0.45f),
                scrim = Color.Black.copy(alpha = 0.45f),
            )
        } else {
            val bg = (lightMuted ?: lightVibrant ?: muted)?.rgb.toColor(AuralisPalette.lightDefault().background)
            val primary = (darkVibrant ?: vibrant ?: muted)?.rgb.toColor(AuralisPalette.lightDefault().primary)
            val secondary = (muted ?: darkMuted)?.rgb.toColor(AuralisPalette.lightDefault().secondary)
            AuralisPalette(
                isDark = false,
                background = bg.lighten(0.18f),
                surface = Color.White.copy(alpha = 0.82f),
                surfaceHigh = bg.lighten(0.08f),
                onBackground = Color(0xFF16151C),
                onSurface = Color(0xFF1C1B22),
                primary = primary.darken(0.1f),
                onPrimary = Color.White,
                secondary = secondary.darken(0.1f),
                outline = Color.Black.copy(alpha = 0.10f),
                gradientTop = bg,
                gradientBottom = bg.lighten(0.22f),
                scrim = Color.White.copy(alpha = 0.35f),
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

    private fun Color.ensureContrast(): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        if (hsv[2] < 0.45f) hsv[2] = 0.55f
        if (hsv[1] < 0.25f) hsv[1] = 0.35f
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
