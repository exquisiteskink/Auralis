package app.sonveil.music.data.player

import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

enum class EqMode { Graphic, Parametric }

data class EqFilter(
    val type: String,
    val fc: Float,
    val q: Float,
    val gainDb: Float,
)

/** Tone curve rendered by [GraphicEqProcessor]. Session effects do not see this. */
data class EqProgram(
    val active: Boolean,
    val preampDb: Float,
    val filters: List<EqFilter>,
) {
    val preampLinear: Float
        get() = 10.0.pow(preampDb / 20.0).toFloat()

    companion object {
        val BYPASS = EqProgram(false, 0f, emptyList())
        private val GRAPHIC_Q = sqrt(2.0).toFloat()

        fun from(settings: PlayerSettings): EqProgram {
            if (!settings.eqEnabled) return BYPASS
            return if (settings.eqMode == EqMode.Parametric && settings.eqFilters.isNotEmpty()) {
                EqProgram(
                    active = true,
                    preampDb = settings.eqPreampParametric,
                    filters = settings.eqFilters,
                )
            } else {
                val gains = settings.eqGains
                val filters = EqPresets.BANDS_HZ.mapIndexed { i, hz ->
                    EqFilter("PK", hz.toFloat(), GRAPHIC_Q, gains.getOrElse(i) { 0f })
                }.filter { it.gainDb != 0f }
                val preamp = settings.eqPreampGraphic
                if (filters.isEmpty() && preamp == 0f) return BYPASS
                EqProgram(active = true, preampDb = preamp, filters = filters)
            }
        }
    }
}

object EqFilterCodec {
    fun encode(filters: List<EqFilter>): String =
        filters.joinToString("|") { f ->
            "${f.type}:${"%.2f".format(Locale.US, f.fc)}:${"%.3f".format(Locale.US, f.q)}:${"%.2f".format(Locale.US, f.gainDb)}"
        }

    fun decode(raw: String?): List<EqFilter> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('|').mapNotNull { token ->
            val parts = token.split(':')
            if (parts.size != 4) return@mapNotNull null
            val type = parts[0].uppercase()
            if (type != "PK" && type != "LS" && type != "HS") return@mapNotNull null
            val fc = parts[1].toFloatOrNull() ?: return@mapNotNull null
            val q = parts[2].toFloatOrNull() ?: return@mapNotNull null
            val gain = parts[3].toFloatOrNull() ?: return@mapNotNull null
            EqFilter(type, fc, q, gain)
        }.take(24)
    }
}
