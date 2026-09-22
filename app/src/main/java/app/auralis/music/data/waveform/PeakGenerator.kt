package app.auralis.music.data.waveform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/**
 * Offline waveform peak decode via [MediaExtractor] + [MediaCodec] (PCM).
 * Runs entirely off the playback path; caller must use a background dispatcher
 * and cancel when the track changes.
 *
 * No Subsonic waveform API exists (OpenSubsonic/Navidrome expose only ReplayGain
 * scalar peaks) — this is client-side only.
 */
class PeakGenerator(
    private val appContext: Context,
) {
    /**
     * @return normalized bar amplitudes in (0, 1], or null if decode fails / cancelled.
     */
    suspend fun generate(
        uri: Uri,
        barCount: Int = PeakBuckets.DEFAULT_BAR_COUNT,
        headers: Map<String, String>? = null,
    ): FloatArray? {
        val barsN = PeakBuckets.clampBarCount(barCount)
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            coroutineContext.ensureActive()
            if (headers.isNullOrEmpty()) {
                extractor.setDataSource(appContext, uri, null)
            } else {
                extractor.setDataSource(appContext, uri, headers)
            }
            val track = selectAudioTrack(extractor) ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            if (!mime.startsWith("audio/")) return null

            decoder = MediaCodec.createDecoderByType(mime)
            if (Build.VERSION.SDK_INT >= 24) {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            }
            decoder.configure(format, null, null, 0)
            decoder.start()

            val channels = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
            val sampleRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44100).coerceAtLeast(1)
            val durationUs = format.getLongOrDefault(MediaFormat.KEY_DURATION, 0L).let { d ->
                when {
                    d > 0L -> d
                    Build.VERSION.SDK_INT >= 28 && extractor.cachedDuration > 0 -> extractor.cachedDuration
                    else -> 0L
                }
            }
            val totalSamples = if (durationUs > 0L) {
                (durationUs * sampleRate) / 1_000_000L
            } else {
                0L
            }

            val bars = FloatArray(barsN)
            var sampleIndex = 0L
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            val unknownDurationPeaks = if (totalSamples <= 0L) ArrayList<Float>(barsN * 4) else null

            while (!outputDone && coroutineContext.isActive) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuf = decoder.getInputBuffer(inIndex) ?: break
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = decoder.dequeueOutputBuffer(info, timeoutUs)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,
                    -> Unit
                    else -> if (outIndex >= 0) {
                        if (info.size > 0) {
                            val outBuf = decoder.getOutputBuffer(outIndex)
                            if (outBuf != null) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                val outFormat = decoder.outputFormat
                                val ch = outFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channels)
                                    .coerceAtLeast(1)
                                val pcmEncoding = if (Build.VERSION.SDK_INT >= 24) {
                                    outFormat.getIntegerOrDefault(
                                        MediaFormat.KEY_PCM_ENCODING,
                                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                                    )
                                } else {
                                    android.media.AudioFormat.ENCODING_PCM_16BIT
                                }
                                if (unknownDurationPeaks != null) {
                                    appendUnknownDuration(outBuf, info.size, ch, pcmEncoding, unknownDurationPeaks)
                                } else {
                                    sampleIndex = foldBuffer(
                                        outBuf, info.size, ch, pcmEncoding,
                                        sampleIndex, totalSamples.coerceAtLeast(1L), bars,
                                    )
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            coroutineContext.ensureActive()
            if (unknownDurationPeaks != null) {
                if (unknownDurationPeaks.isEmpty()) return null
                downsampleToBars(unknownDurationPeaks, bars)
            }
            var any = false
            for (v in bars) if (v > 0f) { any = true; break }
            if (!any) return null
            PeakBuckets.normalizeInPlace(bars)
            return bars
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun foldBuffer(
        buf: ByteBuffer,
        size: Int,
        channels: Int,
        pcmEncoding: Int,
        sampleIndexStart: Long,
        totalSamples: Long,
        bars: FloatArray,
    ): Long {
        return when (pcmEncoding) {
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = FloatArray(size / 4)
                buf.order(ByteOrder.nativeOrder()).asFloatBuffer().get(floats)
                PeakBuckets.accumulatePcmFloat(
                    floats, 0, floats.size, channels, sampleIndexStart, totalSamples, bars,
                )
            }
            android.media.AudioFormat.ENCODING_PCM_8BIT -> {
                val bytes = ByteArray(size)
                buf.get(bytes)
                val expanded = ByteArray(bytes.size * 2)
                for (i in bytes.indices) {
                    val s = (bytes[i].toInt() and 0xff) - 128
                    expanded[i * 2] = (s and 0xff).toByte()
                    expanded[i * 2 + 1] = (s shr 8).toByte()
                }
                PeakBuckets.accumulatePcm16Le(
                    expanded, 0, expanded.size, channels, sampleIndexStart, totalSamples, bars,
                )
            }
            else -> {
                val bytes = ByteArray(size)
                buf.get(bytes)
                PeakBuckets.accumulatePcm16Le(
                    bytes, 0, bytes.size, channels, sampleIndexStart, totalSamples, bars,
                )
            }
        }
    }

    private fun appendUnknownDuration(
        buf: ByteBuffer,
        size: Int,
        channels: Int,
        pcmEncoding: Int,
        out: ArrayList<Float>,
    ) {
        var peak = 0f
        when (pcmEncoding) {
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = FloatArray(size / 4)
                buf.order(ByteOrder.nativeOrder()).asFloatBuffer().get(floats)
                val ch = channels.coerceAtLeast(1)
                var i = 0
                while (i + ch <= floats.size) {
                    var frame = 0f
                    for (c in 0 until ch) frame = maxOf(frame, kotlin.math.abs(floats[i + c]))
                    if (frame > peak) peak = frame
                    i += ch
                }
            }
            else -> {
                val bytes = ByteArray(size)
                buf.get(bytes)
                val bars = floatArrayOf(0f)
                PeakBuckets.accumulatePcm16Le(bytes, 0, bytes.size, channels, 0L, 1L, bars)
                peak = bars[0]
            }
        }
        if (out.size < 50_000) out.add(peak)
    }

    private fun downsampleToBars(src: List<Float>, bars: FloatArray) {
        if (src.isEmpty()) return
        val n = bars.size
        for (i in 0 until n) {
            val start = (i * src.size) / n
            val end = (((i + 1) * src.size) / n).coerceAtLeast(start + 1)
            var peak = 0f
            for (j in start until end.coerceAtMost(src.size)) {
                val v = src[j]
                if (v > peak) peak = v
            }
            bars[i] = peak
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
        try {
            // containsKey is API 29+; minSdk 26 — fall back to getInteger + catch.
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                if (containsKey(key)) getInteger(key) else default
            } else {
                getInteger(key)
            }
        } catch (_: Exception) {
            default
        }

    private fun MediaFormat.getLongOrDefault(key: String, default: Long): Long =
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                if (containsKey(key)) getLong(key) else default
            } else {
                getLong(key)
            }
        } catch (_: Exception) {
            default
        }
}
