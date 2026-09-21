package app.auralis.music.data.eq.autoeq

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json

/** Loads AutoEq presets from ASCII base64 parts under assets/autoeq/b64/ (MCP binary workaround). */
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
        private const val PART_COUNT = 23
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
            val text = openPresetsJson(context)
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

        private fun openPresetsJson(context: Context): String {
            val am = context.assets
            val b64 = buildString {
                for (i in 0 until PART_COUNT) {
                    val name = "autoeq/b64/part%02d.txt".format(i)
                    append(am.open(name).bufferedReader(Charsets.US_ASCII).use { it.readText() })
                }
            }
            val rawGzip = Base64.decode(b64, Base64.DEFAULT)
            return GZIPInputStream(ByteArrayInputStream(rawGzip)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        fun clearCache() {
            synchronized(this) { cached = null }
        }
    }
}
