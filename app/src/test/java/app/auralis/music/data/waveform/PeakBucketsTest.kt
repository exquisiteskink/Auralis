package app.auralis.music.data.waveform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeakBucketsTest {
    @Test
    fun cacheKeyStableAndSensitiveToInputs() {
        val a = PeakBuckets.cacheKey("song1", 120, 1000L, 160)
        val b = PeakBuckets.cacheKey("song1", 120, 1000L, 160)
        val c = PeakBuckets.cacheKey("song1", 121, 1000L, 160)
        val d = PeakBuckets.cacheKey("song1", 120, 1001L, 160)
        assertEquals(a, b)
        assertTrue(a.matches(Regex("[a-f0-9]{64}")))
        assertTrue(a != c && a != d)
    }

    @Test
    fun clampBarCountBounds() {
        assertEquals(100, PeakBuckets.clampBarCount(1))
        assertEquals(200, PeakBuckets.clampBarCount(999))
        assertEquals(160, PeakBuckets.clampBarCount(160))
    }

    @Test
    fun accumulatePcm16PutsEnergyInCorrectBucket() {
        val bars = FloatArray(4)
        // 8 frames mono, all silence then a loud spike in last two frames → last bar
        val pcm = ByteArrayOutputStream()
        fun writeSample(s: Short) {
            val b = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(s)
            pcm.write(b.array())
        }
        repeat(6) { writeSample(0) }
        writeSample(32000)
        writeSample(32000)
        val bytes = pcm.toByteArray()
        PeakBuckets.accumulatePcm16Le(
            bytes, 0, bytes.size,
            channels = 1,
            sampleIndexStart = 0L,
            totalSamplesPerChannel = 8L,
            bars = bars,
        )
        assertTrue(bars[3] > 0.5f)
        assertTrue(bars[0] == 0f)
        PeakBuckets.normalizeInPlace(bars)
        assertEquals(1f, bars[3], 0.001f)
    }
}
