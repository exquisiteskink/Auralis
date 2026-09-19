package app.auralis.music.data.player

data class EqPreset(
    val id: String,
    val name: String,
    val group: String,
    val gains: FloatArray,
) {
    override fun equals(other: Any?) = other is EqPreset && id == other.id
    override fun hashCode() = id.hashCode()
}

/**
 * 10-band graphic EQ: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz.
 * Headphone entries are AutoEq-style 10-band approximations (Harman target).
 */
object EqPresets {
    val BANDS_HZ = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val FLAT = EqPreset("flat", "Flat", "General", FloatArray(10))

    val all: List<EqPreset> = listOf(
        FLAT,
        EqPreset("bass", "Bass boost", "General", floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("bass_cut", "Bass reducer", "General", floatArrayOf(-5f, -4f, -2.5f, -1f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("treble", "Treble boost", "General", floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 5f, 5.5f)),
        EqPreset("vocal", "Vocal", "General", floatArrayOf(-2f, -1.5f, -1f, 1f, 3f, 4f, 3f, 1.5f, 0f, -1f)),
        EqPreset("acoustic", "Acoustic", "General", floatArrayOf(3f, 2f, 0.5f, 0.5f, 1.5f, 2f, 3f, 3.5f, 2f, 1f)),
        EqPreset("electronic", "Electronic", "General", floatArrayOf(4f, 3.5f, 1f, 0f, -1.5f, 1f, 0.5f, 1.5f, 3f, 4f)),
        EqPreset("rock", "Rock", "General", floatArrayOf(4.5f, 3f, 1.5f, 0f, -1f, 0.5f, 2f, 3f, 3.5f, 3f)),
        EqPreset("jazz", "Jazz", "General", floatArrayOf(3f, 2f, 0.5f, 1f, -0.5f, 0f, 1.5f, 2.5f, 2f, 1.5f)),
        EqPreset("classical", "Classical", "General", floatArrayOf(4f, 3f, 2f, 1f, 0f, 0f, 1f, 2.5f, 3.5f, 4f)),
        EqPreset("hiphop", "Hip-hop", "General", floatArrayOf(5.5f, 4.5f, 1.5f, 1f, -0.5f, 0f, 1f, 1.5f, 2f, 2.5f)),
        EqPreset("dance", "Dance", "General", floatArrayOf(4f, 3f, 0f, -1f, -1.5f, 0f, 1.5f, 3f, 4f, 4f)),
        EqPreset("laptop", "Laptop speakers", "General", floatArrayOf(6f, 5f, 2f, 0.5f, 0f, 1f, 2f, 3f, 2f, 1f)),
        EqPreset("ae_hd600", "Sennheiser HD 600", "AutoEq", floatArrayOf(2.2f, 1.4f, -0.6f, -1.8f, 0.4f, 1.6f, 3.4f, 4.1f, 1.2f, 2.8f)),
        EqPreset("ae_hd650", "Sennheiser HD 650", "AutoEq", floatArrayOf(2.8f, 1.8f, -0.4f, -1.5f, 0.6f, 1.2f, 3.8f, 4.6f, 0.8f, 2.4f)),
        EqPreset("ae_dt770", "Beyerdynamic DT 770 Pro", "AutoEq", floatArrayOf(-1.2f, 0.6f, 1.8f, 0.4f, -1.6f, -0.8f, 2.2f, 4.8f, -2.6f, 1.4f)),
        EqPreset("ae_m50x", "Audio-Technica ATH-M50x", "AutoEq", floatArrayOf(-2.4f, -1.1f, 2.6f, 1.2f, -0.8f, 0.4f, 3.2f, 4.4f, -1.8f, 0.6f)),
        EqPreset("ae_xm4", "Sony WH-1000XM4", "AutoEq", floatArrayOf(1.8f, -0.6f, -4.2f, -2.8f, 0.8f, 1.4f, 3.6f, 5.1f, -1.4f, 0.2f)),
        EqPreset("ae_xm5", "Sony WH-1000XM5", "AutoEq", floatArrayOf(2.1f, -0.2f, -3.6f, -2.2f, 0.6f, 1.8f, 3.2f, 4.7f, -0.8f, 0.8f)),
        EqPreset("ae_airpods_pro", "Apple AirPods Pro", "AutoEq", floatArrayOf(0.8f, 0.4f, -1.2f, -0.6f, 1.2f, 2.4f, 3.8f, 2.6f, -0.4f, 1.6f)),
        EqPreset("ae_qc45", "Bose QC45", "AutoEq", floatArrayOf(0.4f, -1.8f, -3.4f, -1.6f, 1.6f, 2.2f, 3.4f, 4.2f, 0.6f, 1.2f)),
        EqPreset("ae_k701", "AKG K701", "AutoEq", floatArrayOf(3.4f, 2.2f, 0.2f, -0.8f, 0.2f, 1.8f, 3.6f, 3.2f, 1.4f, 2.6f)),
    )

    fun byId(id: String): EqPreset = all.find { it.id == id } ?: FLAT
}
