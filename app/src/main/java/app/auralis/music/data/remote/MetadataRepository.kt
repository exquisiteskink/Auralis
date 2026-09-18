package app.auralis.music.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Popularity and biography from free public APIs.
 * Deezer (no key) for ranked top tracks; MusicBrainz + Wikipedia for bios.
 */
class MetadataRepository(
    private val http: OkHttpClient,
    private val client: SubsonicClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun popularLibrarySongs(
        artistName: String,
        librarySongs: List<Song>,
        count: Int = 20,
    ): List<Song> {
        val serverTop = runCatching { client.getTopSongs(artistName, count) }.getOrDefault(emptyList())
        if (serverTop.size >= count) return serverTop.take(count)

        val deezerTitles = runCatching { deezerTopTitles(artistName, count) }.getOrDefault(emptyList())
        val matched = matchToLibrary(deezerTitles, librarySongs)
        val merged = LinkedHashMap<String, Song>()
        (serverTop + matched).forEach { merged.putIfAbsent(it.id, it) }
        if (merged.size < 5) {
            librarySongs
                .sortedByDescending { it.playCount }
                .forEach { merged.putIfAbsent(it.id, it) }
        }
        return merged.values.take(count)
    }

    suspend fun biography(artistName: String, serverBio: String?): String? {
        val cleaned = serverBio?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim()
        if (!cleaned.isNullOrBlank() && cleaned.length > 40) return cleaned
        return runCatching { wikipediaBio(artistName) }.getOrNull() ?: cleaned
    }

    private suspend fun deezerTopTitles(artistName: String, count: Int): List<String> =
        withContext(Dispatchers.IO) {
            val searchUrl =
                "https://api.deezer.com/search/artist?q=${java.net.URLEncoder.encode(artistName, "UTF-8")}"
            val search = getJson(searchUrl) ?: return@withContext emptyList()
            val first = search["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@withContext emptyList()
            val id = first["id"]?.jsonPrimitive?.contentOrNull ?: return@withContext emptyList()
            val top = getJson("https://api.deezer.com/artist/$id/top?limit=$count") ?: return@withContext emptyList()
            top["data"]?.jsonArray?.mapNotNull { el ->
                el.jsonObject["title"]?.jsonPrimitive?.contentOrNull
            }.orEmpty()
        }

    private suspend fun wikipediaBio(artistName: String): String? = withContext(Dispatchers.IO) {
        val mbUrl =
            "https://musicbrainz.org/ws/2/artist/?query=artist:${java.net.URLEncoder.encode("\"$artistName\"", "UTF-8")}&fmt=json&limit=1"
        val mb = getJson(mbUrl, musicBrainz = true) ?: return@withContext null
        val artists = mb["artists"] as? JsonArray ?: return@withContext null
        val first = artists.firstOrNull()?.jsonObject ?: return@withContext null
        val name = first["name"]?.jsonPrimitive?.contentOrNull ?: artistName
        val wikiTitle = name.replace(" ", "_")
        val wiki =
            getJson("https://en.wikipedia.org/api/rest_v1/page/summary/${java.net.URLEncoder.encode(wikiTitle, "UTF-8")}")
                ?: return@withContext null
        wiki["extract"]?.jsonPrimitive?.contentOrNull
    }

    private fun matchToLibrary(titles: List<String>, library: List<Song>): List<Song> {
        val index = library.groupBy { normalize(it.title) }
        val out = ArrayList<Song>()
        val used = HashSet<String>()
        for (title in titles) {
            val key = normalize(title)
            val hit = index[key]?.firstOrNull { it.id !in used }
                ?: library.firstOrNull { it.id !in used && normalize(it.title).contains(key) && key.length > 4 }
            if (hit != null) {
                used += hit.id
                out += hit
            }
        }
        return out
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun getJson(url: String, musicBrainz: Boolean = false): JsonObject? {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (musicBrainz) {
                    header("User-Agent", "Auralis/0.1 (https://github.com/exquisiteskink/Auralis)")
                }
            }
            .build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            return json.parseToJsonElement(body) as? JsonObject
        }
    }
}
