package app.auralis.music.data.download

import android.content.Context
import android.net.Uri
import app.auralis.music.data.auth.StoredCredentials
import app.auralis.music.data.remote.Song
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json

/**
 * App-private offline media store. Persists only relative paths and metadata —
 * never authenticated download/stream URLs (credentials live in query strings).
 */
class DownloadStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "offline").apply { mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedIndex: OfflineIndex? = null

    fun serverKey(creds: StoredCredentials): String {
        val raw = listOf(creds.serverUrl.trim().trimEnd('/'), creds.username, creds.authMode.name, creds.apiKey.take(8))
            .joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    private fun serverDir(key: String): File =
        File(root, key).apply { mkdirs() }

    private fun songsDir(key: String): File =
        File(serverDir(key), "songs").apply { mkdirs() }

    private fun indexFile(key: String): File =
        File(serverDir(key), "index.json")

    @Synchronized
    fun loadIndex(key: String): OfflineIndex {
        if (cachedKey == key && cachedIndex != null) return cachedIndex!!
        val file = indexFile(key)
        val index = if (file.isFile) {
            runCatching { json.decodeFromString(OfflineIndex.serializer(), file.readText()) }
                .getOrElse { OfflineIndex(serverKey = key) }
        } else {
            OfflineIndex(serverKey = key)
        }
        cachedKey = key
        cachedIndex = index
        return index
    }

    @Synchronized
    private fun saveIndex(index: OfflineIndex) {
        val file = indexFile(index.serverKey)
        val tmp = File(file.parentFile, "index.json.tmp")
        tmp.writeText(json.encodeToString(OfflineIndex.serializer(), index))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        cachedKey = index.serverKey
        cachedIndex = index
    }

    fun hasSong(key: String, songId: String): Boolean {
        val rec = loadIndex(key).songs[songId] ?: return false
        return File(serverDir(key), rec.relPath).isFile
    }

    fun playbackUri(key: String, songId: String): Uri? {
        val rec = loadIndex(key).songs[songId] ?: return null
        val file = File(serverDir(key), rec.relPath)
        if (!file.isFile) return null
        return Uri.fromFile(file)
    }

    /** Absolute target file for a song (may not exist yet). */
    fun targetFile(key: String, song: Song): File {
        val suffix = song.suffix?.trim('.')?.takeIf { it.isNotBlank() } ?: guessSuffix(song.contentType) ?: "bin"
        val safeId = sanitizeId(song.id)
        return File(songsDir(key), "$safeId.$suffix")
    }

    fun markDownloaded(key: String, song: Song, file: File) {
        val rel = file.relativeTo(serverDir(key)).path.replace('\\', '/')
        val rec = OfflineSongRecord(
            songId = song.id,
            relPath = rel,
            size = file.length(),
            suffix = song.suffix,
            contentType = song.contentType,
            bitRate = song.bitRate,
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumId = song.albumId,
            downloadedAtMs = System.currentTimeMillis(),
        )
        val index = loadIndex(key)
        saveIndex(index.copy(songs = index.songs + (song.id to rec)))
    }

    fun removeSong(key: String, songId: String) {
        val index = loadIndex(key)
        val rec = index.songs[songId]
        if (rec != null) {
            File(serverDir(key), rec.relPath).delete()
            saveIndex(index.copy(songs = index.songs - songId))
        }
    }

    fun bytesUsed(key: String): Long {
        val dir = songsDir(key)
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearAll(key: String) {
        serverDir(key).deleteRecursively()
        if (cachedKey == key) {
            cachedKey = null
            cachedIndex = null
        }
        serverDir(key).mkdirs()
        songsDir(key)
        saveIndex(OfflineIndex(serverKey = key))
    }

    fun clearEverything() {
        root.deleteRecursively()
        root.mkdirs()
        cachedKey = null
        cachedIndex = null
    }

    companion object {
        fun sanitizeId(id: String): String {
            val cleaned = id.map { ch ->
                if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-') ch else '_'
            }.joinToString("")
            return cleaned.take(120).ifBlank { "song" }
        }

        fun guessSuffix(contentType: String?): String? = when {
            contentType == null -> null
            contentType.contains("flac", true) -> "flac"
            contentType.contains("mpeg", true) || contentType.contains("mp3", true) -> "mp3"
            contentType.contains("mp4", true) || contentType.contains("aac", true) || contentType.contains("m4a", true) -> "m4a"
            contentType.contains("ogg", true) || contentType.contains("opus", true) -> "ogg"
            contentType.contains("wav", true) -> "wav"
            contentType.contains("aiff", true) -> "aiff"
            else -> null
        }
    }
}
