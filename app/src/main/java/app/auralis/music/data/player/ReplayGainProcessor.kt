package app.auralis.music.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.pow

@UnstableApi
class ReplayGainProcessor : BaseAudioProcessor() {
    @Volatile
    var linearGain: Float = 1f

    @Volatile
    var limiter: Boolean = true

    private var floatPcm = false
    private var channels = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        floatPcm = enc == C.ENCODING_PCM_FLOAT
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val gain = linearGain
        val limit = limiter
        if ((gain == 1f && !limit) || !inputBuffer.hasRemaining()) {
            val out = replaceOutputBuffer(inputBuffer.remaining())
            out.put(inputBuffer)
            out.flip()
            return
        }
        if (floatPcm) processFloat(inputBuffer, gain, limit) else process16(inputBuffer, gain, limit)
    }

    private fun process16(input: ByteBuffer, gain: Float, limit: Boolean) {
        val out = replaceOutputBuffer(input.remaining())
        while (input.remaining() >= 2) {
            var x = input.short / 32768f * gain
            if (limit) x = x.coerceIn(-1f, 1f)
            out.putShort((x * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }

    private fun processFloat(input: ByteBuffer, gain: Float, limit: Boolean) {
        val out = replaceOutputBuffer(input.remaining())
        while (input.remaining() >= 4) {
            var x = input.float * gain
            if (limit) x = x.coerceIn(-1f, 1f)
            out.putFloat(x)
        }
        out.flip()
    }

    companion object {
        fun computeLinearGain(
            mode: ReplayGainMode,
            trackDb: Float,
            albumDb: Float,
            trackPeak: Float,
            albumPeak: Float,
            fallbackDb: Float,
            limiter: Boolean,
        ): Float {
            if (mode == ReplayGainMode.Off) return 1f
            val db = when (mode) {
                ReplayGainMode.Track -> firstFinite(trackDb, fallbackDb, albumDb)
                ReplayGainMode.Album -> firstFinite(albumDb, fallbackDb, trackDb)
                ReplayGainMode.Off -> 0f
            }
            var linear = 10.0.pow(db / 20.0).toFloat()
            if (limiter) {
                val peak = when (mode) {
                    ReplayGainMode.Track -> firstPositive(trackPeak, albumPeak)
                    ReplayGainMode.Album -> firstPositive(albumPeak, trackPeak)
                    ReplayGainMode.Off -> 0f
                }
                if (peak > 0f) {
                    val ceiling = 0.99f / peak
                    linear = min(linear, ceiling)
                }
            }
            return linear.coerceIn(0.05f, 4f)
        }

        private fun firstFinite(vararg values: Float): Float {
            for (v in values) if (v.isFinite()) return v
            return 0f
        }

        private fun firstPositive(vararg values: Float): Float {
            for (v in values) if (v.isFinite() && v > 0f) return if (v > 8f) 10.0.pow(v / 20.0).toFloat() else v
            return 0f
        }

        fun fromExtras(
            extras: android.os.Bundle?,
            mode: ReplayGainMode,
            limiter: Boolean,
        ): Float {
            if (extras == null) return 1f
            return computeLinearGain(
                mode = mode,
                trackDb = extras.getFloat(PlayerSettings.EXTRA_RG_TRACK, Float.NaN),
                albumDb = extras.getFloat(PlayerSettings.EXTRA_RG_ALBUM, Float.NaN),
                trackPeak = extras.getFloat(PlayerSettings.EXTRA_RG_TRACK_PEAK, Float.NaN),
                albumPeak = extras.getFloat(PlayerSettings.EXTRA_RG_ALBUM_PEAK, Float.NaN),
                fallbackDb = extras.getFloat(PlayerSettings.EXTRA_RG_FALLBACK, Float.NaN),
                limiter = limiter,
            )
        }
    }
}
