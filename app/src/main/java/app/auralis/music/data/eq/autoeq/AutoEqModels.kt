package app.auralis.music.data.eq.autoeq

import kotlinx.serialization.Serializable

@Serializable
data class AutoEqBundle(
    val version: Int = 1,
    val bandsHz: List<Int> = emptyList(),
    val autoeqGitSha: String? = null,
    val generatedAt: String? = null,
    val mode: String? = null,
    val count: Int = 0,
    val presets: List<AutoEqPresetJson> = emptyList(),
)

@Serializable
data class AutoEqPresetJson(
    val id: String,
    val name: String,
    val brand: String? = null,
    val source: String? = null,
    val rig: String? = null,
    val preampDb: Float? = null,
    val gains: List<Float> = emptyList(),
)

/** Domain preset used by UI / PlayerSettings apply. */
data class AutoEqPreset(
    val id: String,
    val name: String,
    val brand: String?,
    val source: String?,
    val preampDb: Float?,
    val gains: FloatArray,
) {
    val subtitle: String
        get() = listOfNotNull(
            brand?.takeIf { it.isNotBlank() && !name.startsWith(it) },
            source?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
}
