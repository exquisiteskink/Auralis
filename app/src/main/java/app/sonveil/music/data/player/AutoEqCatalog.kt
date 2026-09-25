package app.sonveil.music.data.player

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AutoEqFilterDto(
    @SerialName("t") val type: String = "PK",
    @SerialName("f") val fc: Float = 0f,
    @SerialName("g") val gain: Float = 0f,
    @SerialName("q") val q: Float = 1f,
)

@Serializable
data class AutoEqEntry(
    val id: String,
    @SerialName("n") val name: String,
    @SerialName("s") val source: String = "",
    @SerialName("r") val rig: String = "",
    @SerialName("pp") val parametricPreamp: Float = 0f,
    @SerialName("gp") val graphicPreamp: Float = 0f,
    @SerialName("f") val filters: List<AutoEqFilterDto> = emptyList(),
    @SerialName("b") val bands: List<Float> = emptyList(),
) {
    val hasGraphic: Boolean get() = bands.size == 10
    val hasParametric: Boolean get() = filters.isNotEmpty()

    fun toFilters(): List<EqFilter> = filters.map {
        EqFilter(it.type.uppercase(), it.fc, it.q, it.gain)
    }
}

object AutoEqCatalog {
    private const val TAG = "Sonveil/AutoEq"
    private val json = Json { ignoreUnknownKeys = true }
    private val gate = Mutex()
    @Volatile private var cache: List<AutoEqEntry>? = null

    suspend fun load(context: Context): List<AutoEqEntry> {
        cache?.let { return it }
        return gate.withLock {
            cache?.let { return it }
            val loaded = withContext(Dispatchers.IO) {
                runCatching { readCatalog(context.applicationContext) }
                    .onFailure { Log.e(TAG, "Headphone catalog failed to load", it) }
                    .getOrDefault(emptyList())
            }
            if (loaded.isNotEmpty()) {
                cache = loaded
                Log.i(TAG, "Loaded ${loaded.size} headphone measurements")
            }
            loaded
        }
    }

    /**
     * aapt stores [autoeq.json.gz] uncompressed and drops the .gz suffix, so the
     * APK asset is plain `autoeq.json`. Keep the gzip path for an unprocessed file.
     */
    private fun readCatalog(context: Context): List<AutoEqEntry> {
        val text = runCatching {
            context.assets.open("autoeq.json").bufferedReader().use { it.readText() }
        }.getOrElse {
            val packed = context.assets.open("autoeq.json.gz").use { it.readBytes() }
            GZIPInputStream(ByteArrayInputStream(packed)).bufferedReader().use { it.readText() }
        }
        return json.decodeFromString<List<AutoEqEntry>>(text)
    }

    fun search(entries: List<AutoEqEntry>, query: String, limit: Int = 40): List<AutoEqEntry> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return entries.asSequence()
            .filter { entry ->
                val hay = "${entry.name} ${entry.source} ${entry.rig}".lowercase()
                words.all { hay.contains(it) }
            }
            .take(limit)
            .toList()
    }
}
