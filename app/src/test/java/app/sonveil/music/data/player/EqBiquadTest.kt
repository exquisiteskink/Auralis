package app.sonveil.music.data.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class EqBiquadTest {
    @Test
    fun peakingBoostsItsCenterFrequency() {
        val biquad = EqBiquad()
        biquad.peaking(48_000, 1000f, 1.414f, 6f)
        val peak = settledPeak(biquad, 48_000, 1000f)
        assertEquals(2.0, peak.toDouble(), 0.2)
    }

    @Test
    fun flatPeakingDoesNotChangeLevel() {
        val biquad = EqBiquad()
        biquad.peaking(48_000, 1000f, 1f, 0f)
        val peak = settledPeak(biquad, 48_000, 1000f)
        assertEquals(1.0, peak.toDouble(), 0.05)
    }

    @Test
    fun filterCodecRoundTrips() {
        val filters = listOf(
            EqFilter("LS", 80f, 0.7f, 4.5f),
            EqFilter("PK", 2400f, 2.25f, -1.2f),
            EqFilter("HS", 10000f, 0.7f, 2f),
        )
        val decoded = EqFilterCodec.decode(EqFilterCodec.encode(filters))
        assertEquals(3, decoded.size)
        assertEquals("LS", decoded[0].type)
        assertEquals(80f, decoded[0].fc, 0.01f)
        assertEquals(2.25f, decoded[1].q, 0.01f)
        assertEquals(-1.2f, decoded[1].gainDb, 0.01f)
        assertEquals("HS", decoded[2].type)
    }

    @Test
    fun preampLinearMatchesDecibels() {
        val program = EqProgram(active = true, preampDb = -6f, filters = emptyList())
        assertEquals(0.501, program.preampLinear.toDouble(), 0.01)
    }

    @Test
    fun headphoneSearchMatchesEveryWord() {
        val entries = listOf(
            AutoEqEntry(id = "a", name = "Sennheiser HD 600", source = "oratory1990", rig = "GRAS"),
            AutoEqEntry(id = "b", name = "Sennheiser HD 650", source = "crinacle", rig = "711"),
            AutoEqEntry(id = "c", name = "HD 600", source = "Innerfidelity", rig = ""),
        )
        val hits = AutoEqCatalog.search(entries, "hd 600 oratory")
        assertEquals(listOf("a"), hits.map { it.id })
    }

    private fun settledPeak(biquad: EqBiquad, sampleRate: Int, freq: Float): Float {
        var peak = 0f
        val total = sampleRate
        for (n in 0 until total) {
            val x = sin(2.0 * PI * freq * n / sampleRate).toFloat()
            val y = abs(biquad.process(x))
            if (n > total / 2 && y > peak) peak = y
        }
        return peak
    }
}
