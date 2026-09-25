package app.sonveil.music.data.player

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.log10

/**
 * 10-band EQ attached to an ExoPlayer audio session.
 * Uses DynamicsProcessing on API 28+; otherwise the platform Equalizer.
 *
 * ReplayGain is applied as DynamicsProcessing input gain (dB) when available so
 * [androidx.media3.common.Player.setVolume] can stay at 1f — critical for external
 * equalizers that use Direct Volume Control (e.g. Poweramp EQ DVC).
 */
class EqController {
    private var dynamics: DynamicsProcessing? = null
    private var equalizer: Equalizer? = null
    private var sessionId = 0
    /** Last ReplayGain applied via platform effect, in dB (0 = unity). */
    private var replayGainDb = 0f

    fun attach(audioSessionId: Int, settings: PlayerSettings) {
        if (audioSessionId == 0 || audioSessionId == sessionId) {
            apply(settings)
            return
        }
        release()
        sessionId = audioSessionId
        if (Build.VERSION.SDK_INT >= 28) {
            dynamics = runCatching {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true,
                    10,
                    false,
                    0,
                    false,
                    0,
                    false,
                ).build()
                DynamicsProcessing(0, audioSessionId, cfg).apply {
                    enabled = settings.eqEnabled || abs(replayGainDb) > RG_DB_EPS
                }
            }.getOrNull()
        }
        if (dynamics == null) {
            equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        }
        apply(settings)
    }

    /**
     * Apply ReplayGain as platform-effect input gain when DynamicsProcessing is active.
     * @return true if gain was applied via the effect (caller should keep player volume at 1f).
     */
    fun applyReplayGainLinear(linear: Float, settings: PlayerSettings): Boolean {
        replayGainDb = linearToDb(linear)
        val dp = dynamics
        if (dp != null && Build.VERSION.SDK_INT >= 28) {
            val ok = runCatching {
                applyDynamics(dp)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /**
     * Session effects carry ReplayGain only. Tone (10-band or parametric, plus
     * preamp) is applied in [GraphicEqProcessor] so it does not fight Poweramp.
     */
    fun apply(settings: PlayerSettings) {
        dynamics?.let { dp ->
            if (Build.VERSION.SDK_INT >= 28) applyDynamics(dp)
            return
        }
        equalizer?.let { eq -> runCatching { eq.enabled = false } }
    }

    @RequiresApi(28)
    private fun applyDynamics(dp: DynamicsProcessing) {
        runCatching {
            val rgActive = abs(replayGainDb) > RG_DB_EPS
            dp.enabled = rgActive
            dp.setInputGainAllChannelsTo(replayGainDb.coerceIn(RG_DB_MIN, RG_DB_MAX))
            if (!rgActive) return
            val pre = dp.getPreEqByChannelIndex(0)
            val n = pre.bandCount.coerceAtMost(10)
            for (i in 0 until n) {
                val band = pre.getBand(i)
                band.isEnabled = true
                band.cutoffFrequency = EqPresets.BANDS_HZ[i].toFloat()
                band.gain = 0f
                dp.setPreEqBandAllChannelsTo(i, band)
            }
        }
    }

    fun release() {
        runCatching { dynamics?.release() }
        runCatching { equalizer?.release() }
        dynamics = null
        equalizer = null
        sessionId = 0
        // Keep replayGainDb so a re-attach restores the same gain.
    }

    /**
     * Steal platform effects from [other] without releasing them.
     * Used when promoting the crossfade player so DynamicsProcessing (and RG
     * input gain) stay attached to the already-audible audio session.
     */
    fun adoptFrom(other: EqController) {
        if (other === this) return
        release()
        dynamics = other.dynamics
        equalizer = other.equalizer
        sessionId = other.sessionId
        replayGainDb = other.replayGainDb
        other.dynamics = null
        other.equalizer = null
        other.sessionId = 0
    }

    val audioSessionId: Int get() = sessionId

    companion object {
        private const val RG_DB_EPS = 0.05f
        private const val RG_DB_MIN = -30f
        private const val RG_DB_MAX = 12f

        fun linearToDb(linear: Float): Float {
            if (!linear.isFinite() || linear <= 0f) return RG_DB_MIN
            return (20.0 * log10(linear.toDouble())).toFloat().coerceIn(RG_DB_MIN, RG_DB_MAX)
        }
    }
}

fun replayGainToPlayerVolume(linear: Float): Float = linear.coerceIn(0.05f, 1f)
