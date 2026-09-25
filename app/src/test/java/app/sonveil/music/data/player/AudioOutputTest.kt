package app.sonveil.music.data.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioOutputTest {
    @Test
    fun headphonesWinOverThePhoneSpeaker() {
        val chosen = AudioOutputs.select(
            listOf(
                OutputDevice.SPEAKER,
                OutputDevice("8:buds", "Galaxy Buds", OutputKind.Bluetooth, 0),
                OutputDevice("4:wire", "Wired headphones", OutputKind.Wired, 7),
            ),
        )
        assertEquals("Galaxy Buds", chosen.name)
    }

    @Test
    fun speakerIsTheFallbackWhenNothingIsConnected() {
        assertEquals(OutputDevice.SPEAKER, AudioOutputs.select(emptyList()))
    }

    @Test
    fun profileRoundTripKeepsTheHeadphoneCurve() {
        val profile = EqDeviceProfile(
            id = "8:aa:bb",
            name = "HD 600",
            kind = OutputKind.Bluetooth.name,
            enabled = true,
            mode = EqMode.Parametric.name,
            preset = "autoeq",
            gains = "1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0",
            preampGraphic = -4.8f,
            preampParametric = -6.2f,
            filters = "PK:1000.00:1.400:3.00",
            headphone = "Sennheiser HD 600",
        )
        val decoded = EqProfileCodec.decode(EqProfileCodec.encode(mapOf(profile.id to profile)))
        assertEquals("Sennheiser HD 600", decoded.getValue(profile.id).curveLabel)
        assertEquals(-6.2f, decoded.getValue(profile.id).preampParametric, 0.01f)
        assertEquals(EqMode.Parametric.name, decoded.getValue(profile.id).mode)
    }
}
