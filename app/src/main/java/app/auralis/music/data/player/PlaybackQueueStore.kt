package app.auralis.music.data.player

import android.content.Context
import app.auralis.music.data.remote.Song
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persists the active play queue so [PlayerController] / [PlaybackService] can
 * rebuild MediaItems after process death. Never stores authenticated stream URLs
 * (those are rebuilt via SubsonicClient + DownloadStore at restore time).
 */
class PlaybackQueueStore internal constructor(private val file: File) {
    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "playback_queue.json"),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }

    @Serializable
    data class Snapshot(
        val version: Int = VERSION,
        val serverKey: String,
        val songs: List<Song> = emptyList(),
        val index: Int = 0,
        val positionMs: Long = 0L,
        val playWhenReady: Boolean = false,
        val shuffle: Boolean = false,
        val repeatMode: Int = 0,
    )

    @Synchronized
    fun load(): Snapshot? {
        if (!file.isFile) return null
        return runCatching {
            val snap = json.decodeFromString(Snapshot.serializer(), file.readText())
            if (snap.version != VERSION || snap.songs.isEmpty()) {
                clear()
                return null
            }
            val songs = snap.songs.take(MAX_SONGS)
            snap.copy(
                index = snap.index.coerceIn(0, songs.lastIndex),
                positionMs = snap.positionMs.coerceAtLeast(0L),
                songs = songs,
            )
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun save(snapshot: Snapshot) {
        if (snapshot.songs.isEmpty()) {
            clear()
            return
        }
        val songs = snapshot.songs.take(MAX_SONGS)
        val trimmed = snapshot.copy(
            version = VERSION,
            songs = songs,
            index = snapshot.index.coerceIn(0, songs.lastIndex),
            positionMs = snapshot.positionMs.coerceAtLeast(0L),
        )
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.writeText(json.encodeToString(Snapshot.serializer(), trimmed))
            if (!tmp.renameTo(file)) {
                file.delete()
                check(tmp.renameTo(file)) { "Cannot replace playback queue file" }
            }
        }.onFailure {
            tmp.delete()
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
    }

    companion object {
        const val VERSION = 1
        const val MAX_SONGS = 500
    }
}
