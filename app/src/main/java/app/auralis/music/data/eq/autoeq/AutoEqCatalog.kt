package app.auralis.music.data.eq.autoeq

import android.content.Context
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json

/**
 * Loads `assets/autoeq/presets.json.gz` once and offers case-insensitive
 * substring search on name / brand. Apply paths stay in PlayerSettings →
 * EqController (audio session); this class never touches ExoPlayer processors.
 */
class AutoEqCatalog private constructor(
    val presets: List<AutoEqPreset>,
    val generatedAt: String? = null,
    val autoeqGitSha: String? = null,
) {
    val count: Int get() = presets.size

    fun search(query: String, limit: Int = 48): List<AutoEqPreset> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val needle = q.lowercase()
        return presets.asSequence()
            .filter { p ->
                p.name.lowercase().contains(needle) ||
                    (p.brand?.lowercase()?.contains(needle) == true)
            }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    fun byId(id: String): AutoEqPreset? = presets.firstOrNull { it.id == id }

    companion object {
        const val ASSET_PATH = "autoeq/presets.json.gz"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        @Volatile
        private var cached: AutoEqCatalog? = null

        fun get(context: Context): AutoEqCatalog {
            cached?.let { return it }
            return synchronized(this) {
                cached ?: load(context.applicationContext).also { cached = it }
            }
        }

        fun load(context: Context): AutoEqCatalog {
            val text = context.assets.open(ASSET_PATH).use { raw ->
                GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val bundle = json.decodeFromString(AutoEqBundle.serializer(), text)
            val mapped = bundle.presets.mapNotNull { row ->
                if (row.gains.size != 10) return@mapNotNull null
                AutoEqPreset(
                    id = row.id,
                    name = row.name,
                    brand = row.brand,
                    source = row.source,
                    preampDb = row.preampDb,
                    gains = FloatArray(10) { i -> row.gains[i] },
                )
            }
            return AutoEqCatalog(
                presets = mapped,
                generatedAt = bundle.generatedAt,
                autoeqGitSha = bundle.autoeqGitSha,
            )
        }

        /** Test / preview helper — clears process-wide cache. */
        fun clearCache() {
            synchronized(this) { cached = null }
        }
    }
}
