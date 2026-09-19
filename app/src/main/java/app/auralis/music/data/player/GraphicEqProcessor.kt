package app.auralis.music.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
class GraphicEqProcessor : BaseAudioProcessor() {
    @Volatile
    var enabled: Boolean = false

    private val gainsDb = FloatArray(10)
    private var filters: Array<Biquad> = emptyArray()
    private var channels = 2
    private var floatPcm = false
    private var sampleRate = 44100

    @Synchronized
    fun setGains(db: FloatArray) {
        for (i in 0 until 10) {
            gainsDb[i] = db.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
        }
        rebuild()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        floatPcm = enc == C.ENCODING_PCM_FLOAT
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        rebuild()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!enabled || filters.isEmpty() || !inputBuffer.hasRemaining()) {
            val out = replaceOutputBuffer(inputBuffer.remaining())
            out.put(inputBuffer)
            out.flip()
            return
        }
        if (floatPcm) processFloat(inputBuffer) else process16(inputBuffer)
    }

    private fun process16(input: ByteBuffer) {
        val out = replaceOutputBuffer(input.remaining())
        val ch = channels
        while (input.remaining() >= 2 * ch) {
            for (c in 0 until ch) {
                val s = input.short
                var x = s / 32768f
                x = apply(x, c)
                out.putShort((x * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        out.flip()
    }

    private fun processFloat(input: ByteBuffer) {
        val out = replaceOutputBuffer(input.remaining())
        val ch = channels
        while (input.remaining() >= 4 * ch) {
            for (c in 0 until ch) {
                var x = input.float
                x = apply(x, c)
                out.putFloat(x)
            }
        }
        out.flip()
    }

    private fun apply(sample: Float, channel: Int): Float {
        var x = sample
        val f = filters
        var i = channel
        while (i < f.size) {
            x = f[i].process(x)
            i += channels
        }
        return x
    }

    @Synchronized
    private fun rebuild() {
        if (channels <= 0) return
        val q = sqrt(2.0).toFloat()
        val built = Array(10 * channels) { Biquad() }
        for (b in 0 until 10) {
            val freq = EqPresets.BANDS_HZ[b].toFloat()
            for (c in 0 until channels) {
                built[b * channels + c].peaking(sampleRate, freq, q, gainsDb[b])
            }
        }
        filters = built
    }

    override fun onFlush() {
        filters.forEach { it.reset() }
    }

    override fun onReset() {
        filters.forEach { it.reset() }
    }
}

private class Biquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun peaking(fs: Int, freq: Float, q: Float, gainDb: Float) {
        if (gainDb == 0f || freq >= fs / 2f) {
            b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
            return
        }
        val w = (2.0 * PI * freq / fs).toFloat()
        val sn = sin(w.toDouble()).toFloat()
        val cs = cos(w.toDouble()).toFloat()
        val alpha = sn / (2f * q)
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val b0n = 1f + alpha * a
        val b1n = -2f * cs
        val b2n = 1f - alpha * a
        val a0n = 1f + alpha / a
        val a1n = -2f * cs
        val a2n = 1f - alpha / a
        b0 = b0n / a0n
        b1 = b1n / a0n
        b2 = b2n / a0n
        a1 = a1n / a0n
        a2 = a2n / a0n
    }

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0f
        z2 = 0f
    }
}
