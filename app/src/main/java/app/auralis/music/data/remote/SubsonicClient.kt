package app.auralis.music.data.remote

import app.auralis.music.data.auth.AuthMode
import app.auralis.music.data.auth.StoredCredentials
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.asKotlinRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SubsonicException(val code: Int, message: String) : Exception(message)

data class ServerInfo(
    val type: String?,
    val version: String,
    val serverVersion: String?,
    val openSubsonic: Boolean,
    val extensions: List<String>,
)

class SubsonicClient(
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    @Volatile
    var credentials: StoredCredentials? = null

    /** Stable salt so cover/stream URLs stay cacheable for the session. */
    @Volatile
    private var sessionSalt: String = randomSalt()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val baseUrl: String
        get() = credentials?.serverUrl?.trim()?.trimEnd('/') ?: ""

    fun rotateSessionSalt() {
        sessionSalt = randomSalt()
    }

    suspend fun ping(): ServerInfo {
        val env = get("ping")
        return ServerInfo(
            type = env.type,
            version = env.version,
            serverVersion = env.serverVersion,
            openSubsonic = env.openSubsonic,
            extensions = env.openSubsonicExtensions.map { it.name },
        )
    }

    suspend fun login(candidate: StoredCredentials): Pair<StoredCredentials, ServerInfo> {
        credentials = candidate
        rotateSessionSalt()
        return try {
            val info = ping()
            candidate to info
        } catch (e: SubsonicException) {
            if (e.code == 41 && candidate.authMode == AuthMode.Token && candidate.password.isNotEmpty()) {
                val fallback = candidate.copy(authMode = AuthMode.HexPassword)
                credentials = fallback
                fallback to ping()
            } else {
                credentials = null
                throw e
            }
        } catch (t: Throwable) {
            credentials = null
            throw t
        }
    }

    suspend fun getPlaylists(): List<Playlist> =
        get("getPlaylists").playlists?.playlist.orEmpty()

    suspend fun getPlaylist(id: String): PlaylistWithSongs =
        get("getPlaylist", "id" to id).playlist
            ?: throw SubsonicException(70, "Playlist not found")

    suspend fun getAlbumList2(type: String, size: Int = 24, offset: Int = 0): List<AlbumID3> =
        get(
            "getAlbumList2",
            "type" to type,
            "size" to size.toString(),
            "offset" to offset.toString(),
        ).albumList2?.album.orEmpty()

    suspend fun getArtists(): List<ArtistID3> =
        get("getArtists").artists?.index.orEmpty().flatMap { it.artist }

    suspend fun getArtist(id: String): ArtistWithAlbums =
        get("getArtist", "id" to id).artist
            ?: throw SubsonicException(70, "Artist not found")

    suspend fun getArtistInfo2(id: String, count: Int = 12): ArtistInfo2 =
        get(
            "getArtistInfo2",
            "id" to id,
            "count" to count.toString(),
            "includeNotPresent" to "true",
        ).artistInfo2 ?: ArtistInfo2()

    suspend fun getAlbum(id: String): AlbumWithSongs =
        get("getAlbum", "id" to id).album
            ?: throw SubsonicException(70, "Album not found")

    suspend fun getTopSongs(artist: String, count: Int = 20): List<Song> =
        get("getTopSongs", "artist" to artist, "count" to count.toString())
            .topSongs?.song.orEmpty()

    suspend fun search3(query: String, count: Int = 20): SearchResult3 =
        get(
            "search3",
            "query" to query,
            "artistCount" to count.toString(),
            "albumCount" to count.toString(),
            "songCount" to count.toString(),
        ).searchResult3 ?: SearchResult3()

    suspend fun scrobble(id: String, submission: Boolean) {
        runCatching {
            get("scrobble", "id" to id, "submission" to submission.toString())
        }
    }

    fun coverUrl(coverId: String?, size: Int = 600): String? {
        if (coverId.isNullOrBlank()) return null
        return buildUrl("getCoverArt", mapOf("id" to coverId, "size" to size.toString()), session = true)
            .toString()
    }

    fun streamUrl(songId: String, maxBitRate: Int = 0): String {
        val extra = mutableMapOf(
            "id" to songId,
            "estimateContentLength" to "true",
        )
        if (maxBitRate > 0) extra["maxBitRate"] = maxBitRate.toString()
        else extra["maxBitRate"] = "0"
        return buildUrl("stream", extra, session = true).toString()
    }

    private suspend fun get(endpoint: String, vararg params: Pair<String, String>): SubsonicEnvelope =
        withContext(Dispatchers.IO) {
            val url = buildUrl(endpoint, params.toMap(), session = false)
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SubsonicException(0, "HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val root = json.decodeFromString(SubsonicRoot.serializer(), body)
                val env = root.response
                if (env.status != "ok") {
                    val err = env.error
                    throw SubsonicException(err?.code ?: 0, err?.message ?: "Request failed")
                }
                env
            }
        }

    private fun buildUrl(
        endpoint: String,
        extra: Map<String, String>,
        session: Boolean,
    ): HttpUrl {
        val creds = credentials ?: throw SubsonicException(40, "Not signed in")
        val root = creds.serverUrl.trim().trimEnd('/').toHttpUrl()
        val builder = root.newBuilder()
            .addPathSegment("rest")
            .addPathSegment(endpoint)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")

        when (creds.authMode) {
            AuthMode.ApiKey -> {
                builder.addQueryParameter("apiKey", creds.apiKey)
            }
            AuthMode.Token -> {
                val salt = if (session) sessionSalt else randomSalt()
                builder.addQueryParameter("u", creds.username)
                builder.addQueryParameter("t", md5(creds.password + salt))
                builder.addQueryParameter("s", salt)
            }
            AuthMode.HexPassword -> {
                builder.addQueryParameter("u", creds.username)
                builder.addQueryParameter("p", "enc:" + toHex(creds.password))
            }
        }

        extra.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    companion object {
        const val CLIENT_NAME = "Auralis"
        const val API_VERSION = "1.16.1"

        private val rng = SecureRandom().asKotlinRandom()

        fun randomSalt(length: Int = 16): String {
            val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
            return (1..length).map { alphabet[rng.nextInt(alphabet.length)] }.joinToString("")
        }

        fun md5(value: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            return toHex(digest)
        }

        fun toHex(value: String): String = toHex(value.toByteArray(Charsets.UTF_8))

        fun toHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }
    }
}
