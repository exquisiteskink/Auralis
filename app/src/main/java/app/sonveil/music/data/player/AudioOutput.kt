package app.sonveil.music.data.player

/**
 * One playback output Sonveil can remember an equalizer for.
 * [rank] is lower for outputs that should win when several are connected:
 * Bluetooth headphones beat USB, USB beats a wire, a wire beats the phone speaker.
 */
data class OutputDevice(
    val id: String,
    val name: String,
    val kind: OutputKind,
    val rank: Int,
) {
    companion object {
        val SPEAKER = OutputDevice("speaker:builtin", "Phone speaker", OutputKind.Speaker, 100)
    }
}

enum class OutputKind { Bluetooth, Usb, Wired, Speaker, Other }

object AudioOutputs {
    fun select(connected: List<OutputDevice>): OutputDevice =
        connected.filter { it.kind != OutputKind.Other }.minByOrNull { it.rank } ?: OutputDevice.SPEAKER
}
