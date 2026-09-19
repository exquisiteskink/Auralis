package app.auralis.music.data.player

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.log10
import kotlin.math.pow

/**
 * 10-band EQ attached to an ExoPlayer audio session.
 * Uses DynamicsProcessing on API 28+; otherwise the platform Equalizer.
 */
class EqController {
    private var dynamics: DynamicsProcessing? = null
    private var equalizer: Equalizer? = null
    private var sessionId = 0

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
                DynamicsProcessing(0, audioSessionId, cfg).apply { enabled = settings.eqEnabled }
            }.getOrNull()
        }
        if (dynamics == null) {
            equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        }
        apply(settings)
    }

    fun apply(settings: PlayerSettings) {
        val gains = settings.eqGains
        val on = settings.eqEnabled
        dynamics?.let { dp ->
            if (Build.VERSION.SDK_INT >= 28) applyDynamics(dp, gains, on)
            return
        }
        equalizer?.let { eq ->
            runCatching {
                eq.enabled = on
                if (!on) return
                val n = eq.numberOfBands.toInt()
                val min = eq.bandLevelRange[0].toInt()
                val max = eq.bandLevelRange[1].toInt()
                for (b in 0 until n) {
                    val center = eq.getCenterFreq(b.toShort()) / 1000f
                    val db = interpolate(center, gains)
                    val millibel = (db * 100f).toInt().coerceIn(min, max).toShort()
                    eq.setBandLevel(b.toShort(), millibel)
                }
            }
        }
    }

    @RequiresApi(28)
    private fun applyDynamics(dp: DynamicsProcessing, gains: FloatArray, on: Boolean) {
        runCatching {
            dp.enabled = on
            if (!on) return
            val pre = dp.getPreEqByChannelIndex(0)
            val n = pre.bandCount.coerceAtMost(10)
            for (i in 0 until n) {
                val band = pre.getBand(i)
                band.isEnabled = true
                band.cutoffFrequency = EqPresets.BANDS_HZ[i].toFloat()
                band.gain = gains[i]
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
    }

    private fun interpolate(freqHz: Float, gains: FloatArray): Float {
        val hz = EqPresets.BANDS_HZ
        if (freqHz <= hz.first()) return gains.first()
        if (freqHz >= hz.last()) return gains.last()
        for (i in 0 until hz.lastIndex) {
            if (freqHz in hz[i].toFloat()..hz[i + 1].toFloat()) {
                val t = (log10(freqHz) - log10(hz[i].toFloat())) /
                    (log10(hz[i + 1].toFloat()) - log10(hz[i].toFloat()))
                return gains[i] + t * (gains[i + 1] - gains[i])
            }
        }
        return 0f
    }
}

fun replayGainToPlayerVolume(linear: Float): Float = linear.coerceIn(0.05f, 1f)
