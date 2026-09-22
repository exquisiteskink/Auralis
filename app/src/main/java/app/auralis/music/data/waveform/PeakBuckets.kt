package app.auralis.music.data.waveform

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure peak-bucketing helpers (unit-testable without MediaCodec).
 * Bars are max-abs amplitude in [0, 1] after normalize.
 */
object PeakBuckets {
    const val DEFAULT_BAR_COUNT = 160
    const val MIN_BAR_COUNT = 100
    const val MAX_BAR_COUNT = 200

    fun clampBarCount(n: Int): Int = n.coerceIn(MIN_BAR_COUNT, MAX_BAR_COUNT)

    fun cacheKey(songId: String, durationSec: Int, sizeBytes: Long, barCount: Int): String {
        val raw = "$songId|$durationSec|$sizeBytes|$barCount"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Fold PCM 16-bit little-endian samples into [bars] max-abs buckets by sample index. */
    fun accumulatePcm16Le(
        pcm: ByteArray,
        offset: Int,
        length: Int,
        channels: Int,
        sampleIndexStart: Long,
        totalSamplesPerChannel: Long,
        bars: FloatArray,
    ): Long {
        val ch = channels.coerceAtLeast(1)
        val frameBytes = 2 * ch
        if (frameBytes <= 0 || length < frameBytes || totalSamplesPerChannel <= 0L) {
            return sampleIndexStart
        }
        val usable = length - (length % frameBytes)
        var sampleIndex = sampleIndexStart
        var i = offset
        val end = offset + usable
        while (i < end) {
            var peak = 0
            for (c in 0 until ch) {
                val lo = pcm[i].toInt() and 0xff
                val hi = pcm[i + 1].toInt()
                val sample = (hi shl 8) or lo
                val s = if (sample > 32767) sample - 65536 else sample
                peak = max(peak, abs(s))
                i += 2
            }
            val bar = ((sampleIndex * bars.size) / totalSamplesPerChannel)
                .toInt()
                .coerceIn(0, bars.size - 1)
            val amp = peak / 32768f
            if (amp > bars[bar]) bars[bar] = amp
            sampleIndex++
        }
        return sampleIndex
    }

    /** Fold float PCM into buckets (MediaCodec output often float on API 31+). */
    fun accumulatePcmFloat(
        pcm: FloatArray,
        offset: Int,
        length: Int,
        channels: Int,
        sampleIndexStart: Long,
        totalSamplesPerChannel: Long,
        bars: FloatArray,
    ): Long {
        val ch = channels.coerceAtLeast(1)
        if (length < ch || totalSamplesPerChannel <= 0L) return sampleIndexStart
        val frames = length / ch
        var sampleIndex = sampleIndexStart
        var i = offset
        repeat(frames) {
            var peak = 0f
            for (c in 0 until ch) {
                peak = max(peak, abs(pcm[i + c]))
            }
            i += ch
            val bar = ((sampleIndex * bars.size) / totalSamplesPerChannel)
                .toInt()
                .coerceIn(0, bars.size - 1)
            if (peak > bars[bar]) bars[bar] = peak
            sampleIndex++
        }
        return sampleIndex
    }

    fun normalizeInPlace(bars: FloatArray, floor: Float = 0.06f) {
        var maxV = 0f
        for (v in bars) if (v > maxV) maxV = v
        if (maxV <= 1e-6f) {
            bars.fill(floor)
            return
        }
        for (i in bars.indices) {
            bars[i] = (bars[i] / maxV).coerceIn(floor, 1f)
        }
    }

    /** Optional RMS blend for quieter sections — keeps max feel of Plexamp bars. */
    fun rmsOf(window: FloatArray): Float {
        if (window.isEmpty()) return 0f
        var sum = 0.0
        for (v in window) sum += v * v
        return sqrt(sum / window.size).toFloat()
    }
}
