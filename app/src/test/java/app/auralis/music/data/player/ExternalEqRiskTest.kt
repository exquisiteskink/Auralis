package app.auralis.music.data.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalEqRiskTest {
    @Test
    fun powerampEqualizerImpliesUnsafe() {
        assertTrue(
            ExternalEqRisk.packagesImplyDualPlayerUnsafe(
                listOf("com.android.vending", ExternalEqRisk.POWERAMP_EQUALIZER),
            ),
        )
    }

    @Test
    fun waveletImpliesUnsafe() {
        assertTrue(
            ExternalEqRisk.packagesImplyDualPlayerUnsafe(listOf("com.pittvandewitt.wavelet")),
        )
    }

    @Test
    fun noKnownEqIsSafeForDualPlayerPolicy() {
        assertFalse(
            ExternalEqRisk.packagesImplyDualPlayerUnsafe(
                listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
            ),
        )
    }

    @Test
    fun emptyIsSafe() {
        assertFalse(ExternalEqRisk.packagesImplyDualPlayerUnsafe(emptyList()))
    }
}
