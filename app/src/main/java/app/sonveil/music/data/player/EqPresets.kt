package app.sonveil.music.data.player

data class EqPreset(
    val id: String,
    val name: String,
    val gains: FloatArray,
) {
    override fun equals(other: Any?) = other is EqPreset && id == other.id
    override fun hashCode() = id.hashCode()
}

/** 10-band graphic EQ: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz. */
object EqPresets {
    val BANDS_HZ = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val FLAT = EqPreset("flat", "Flat", FloatArray(10))

    val all: List<EqPreset> = listOf(
        FLAT,
        EqPreset("bass", "Bass boost", floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("bass_cut", "Bass reducer", floatArrayOf(-5f, -4f, -2.5f, -1f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("treble", "Treble boost", floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 5f, 5.5f)),
        EqPreset("vocal", "Vocal", floatArrayOf(-2f, -1.5f, -1f, 1f, 3f, 4f, 3f, 1.5f, 0f, -1f)),
        EqPreset("acoustic", "Acoustic", floatArrayOf(3f, 2f, 0.5f, 0.5f, 1.5f, 2f, 3f, 3.5f, 2f, 1f)),
        EqPreset("electronic", "Electronic", floatArrayOf(4f, 3.5f, 1f, 0f, -1.5f, 1f, 0.5f, 1.5f, 3f, 4f)),
        EqPreset("rock", "Rock", floatArrayOf(4.5f, 3f, 1.5f, 0f, -1f, 0.5f, 2f, 3f, 3.5f, 3f)),
        EqPreset("jazz", "Jazz", floatArrayOf(3f, 2f, 0.5f, 1f, -0.5f, 0f, 1.5f, 2.5f, 2f, 1.5f)),
        EqPreset("classical", "Classical", floatArrayOf(4f, 3f, 2f, 1f, 0f, 0f, 1f, 2.5f, 3.5f, 4f)),
        EqPreset("hiphop", "Hip-hop", floatArrayOf(5.5f, 4.5f, 1.5f, 1f, -0.5f, 0f, 1f, 1.5f, 2f, 2.5f)),
        EqPreset("dance", "Dance", floatArrayOf(4f, 3f, 0f, -1f, -1.5f, 0f, 1.5f, 3f, 4f, 4f)),
        EqPreset("laptop", "Laptop speakers", floatArrayOf(6f, 5f, 2f, 0.5f, 0f, 1f, 2f, 3f, 2f, 1f)),
    )

    fun byId(id: String): EqPreset = all.find { it.id == id } ?: FLAT
}
