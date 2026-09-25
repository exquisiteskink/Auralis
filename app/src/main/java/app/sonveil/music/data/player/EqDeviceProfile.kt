package app.sonveil.music.data.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Equalizer remembered for one headphone, USB device, or the phone speaker. */
@Serializable
data class EqDeviceProfile(
    val id: String,
    val name: String,
    val kind: String,
    val enabled: Boolean = false,
    val mode: String = EqMode.Graphic.name,
    val preset: String = EqPresets.FLAT.id,
    val gains: String = "",
    val preampGraphic: Float = 0f,
    val preampParametric: Float = 0f,
    val filters: String = "",
    val headphone: String = "",
) {
    val curveLabel: String
        get() = headphone.ifBlank {
            EqPresets.all.find { it.id == preset }?.name ?: if (enabled) "Custom" else "Off"
        }
}

object EqProfileCodec {
    private val json = Json { ignoreUnknownKeys = true }

    private val listSerializer = ListSerializer(EqDeviceProfile.serializer())

    fun encode(profiles: Map<String, EqDeviceProfile>): String =
        json.encodeToString(listSerializer, profiles.values.sortedBy { it.name.lowercase() })

    fun decode(raw: String?): Map<String, EqDeviceProfile> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(listSerializer, raw).associateBy { it.id }
        }.getOrDefault(emptyMap())
    }
}
