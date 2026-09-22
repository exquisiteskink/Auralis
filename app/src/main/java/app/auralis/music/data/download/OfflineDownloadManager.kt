package app.auralis.music.data.download

import android.content.Context
import app.auralis.music.data.player.PlayerSettings
import app.auralis.music.data.remote.Song
import app.auralis.music.data.remote.SubsonicClient
import app.auralis.music.data.remote.SubsonicException
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import app.auralis.music.data.auth.StoredCredentials
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Sequential offline downloader using OpenSubsonic [download](https://opensubsonic.netlify.app/docs/endpoints/download/).
 * Authenticated URLs are built per request and never written to disk.
 */
class OfflineDownloadManager(
    context: Context,
    private val client: SubsonicClient,
    private val store: DownloadStore,
    private val settings: PlayerSettings,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val work = DownloadWorkQueue(scope)

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state

    fun refreshBytes() {
        val creds = client.credentials ?: run {
            _state.update { it.copy(bytesUsed = 0L) }
            return
        }
        val key = store.serverKey(creds)
        _state.update { it.copy(bytesUsed = store.bytesUsed(key)) }
    }

    fun isDownloaded(songId: String): Boolean {
        val creds = client.credentials ?: return false
        return store.hasSong(store.serverKey(creds), songId)
    }

    fun playbackUri(songId: String): android.net.Uri? {
        val creds = client.credentials ?: return null
        return store.playbackUri(store.serverKey(creds), songId)
    }

    fun enqueueAlbum(albumId: String, songs: List<Song>, label: String = "Album") {
        enqueue(songs, collectionLabel = "$label", collectionId = "album:$albumId")
    }

    fun enqueuePlaylist(playlistId: String, songs: List<Song>, label: String = "Playlist") {
        enqueue(songs, collectionLabel = "$label", collectionId = "playlist:$playlistId")
    }

    fun enqueue(songs: List<Song>, collectionLabel: String? = null, collectionId: String? = null) {
        if (songs.isEmpty()) return
        val creds = client.credentials
        if (creds == null) {
            _state.update {
                it.copy(phase = DownloadPhase.Failed, message = "Not signed in")
            }
            return
        }
        if (!WifiGate.allowHiResDownload(appContext, settings.wifiOnlyHiResDownloads)) {
            _state.update {
                it.copy(
                    phase = DownloadPhase.PausedWifi,
                    message = "HiRes downloads require Wi‑Fi",
                    collectionLabel = collectionLabel,
                    done = 0,
                    total = songs.size,
                )
            }
            return
        }
        work.replace {
            if (client.credentials != creds) return@replace
            runBatch(songs.toList(), collectionLabel, creds)
        }
    }

    fun cancel() = work.stop {
        _state.value = DownloadUiState(message = "Cancelled")
    }

    suspend fun cancelAndJoin() { cancel().join() }

    fun clearDownloads() {
        val creds = client.credentials
        work.stop {
            withContext(Dispatchers.IO) {
                if (creds != null) store.clearAll(store.serverKey(creds))
                else store.clearEverything()
            }
            _state.value = DownloadUiState(message = "Offline downloads cleared")
        }
    }

    private suspend fun runBatch(songs: List<Song>, collectionLabel: String?, creds: StoredCredentials) {
        val key = store.serverKey(creds)
        val pending = songs.filter { !store.hasSong(key, it.id) }
        if (pending.isEmpty()) {
            _state.update {
                it.copy(
                    phase = DownloadPhase.Done,
                    collectionLabel = collectionLabel,
                    done = songs.size,
                    total = songs.size,
                    message = "Already downloaded",
                    bytesUsed = store.bytesUsed(key),
                    currentTitle = null,
                )
            }
            return
        }
        _state.update {
            it.copy(
                phase = DownloadPhase.Running,
                collectionLabel = collectionLabel,
                done = 0,
                total = pending.size,
                message = null,
                currentTitle = pending.firstOrNull()?.title,
                bytesUsed = store.bytesUsed(key),
            )
        }
        var done = 0
        for (song in pending) {
            currentCoroutineContext().ensureActive()
            if (client.credentials != creds) return
            if (!WifiGate.allowHiResDownload(appContext, settings.wifiOnlyHiResDownloads)) {
                _state.update {
                    it.copy(
                        phase = DownloadPhase.PausedWifi,
                        message = "HiRes downloads require Wi‑Fi",
                        done = done,
                        currentTitle = song.title,
                        bytesUsed = store.bytesUsed(key),
                    )
                }
                return
            }
            _state.update { it.copy(currentTitle = song.title, phase = DownloadPhase.Running) }
            try {
                downloadOne(key, song, creds)
                done++
                _state.update {
                    it.copy(done = done, bytesUsed = store.bytesUsed(key))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = DownloadPhase.Failed,
                        message = "Download failed; check the connection and retry",
                        done = done,
                        currentTitle = song.title,
                        bytesUsed = store.bytesUsed(key),
                    )
                }
                return
            }
        }
        _state.update {
            it.copy(
                phase = DownloadPhase.Done,
                done = done,
                total = pending.size,
                currentTitle = null,
                message = "Download complete",
                bytesUsed = store.bytesUsed(key),
            )
        }
    }

    private suspend fun downloadOne(key: String, song: Song, creds: StoredCredentials) {
        val request = Request.Builder().url(client.downloadUrl(song.id, creds)).get().build()
        OfflineTransfer.download(client.http.newCall(request), store.targetFile(key, song), song.size) { file ->
            check(client.credentials == creds) { "Account changed" }
            store.markDownloaded(key, song, file)
        }
    }
}
