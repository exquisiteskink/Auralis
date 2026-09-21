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
    private var job: Job? = null

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
        if (client.credentials == null) {
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
        job?.cancel()
        job = scope.launch {
            runBatch(songs, collectionLabel)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update {
            it.copy(phase = DownloadPhase.Idle, currentTitle = null, message = "Cancelled")
        }
    }

    fun clearDownloads() {
        cancel()
        val creds = client.credentials
        if (creds != null) store.clearAll(store.serverKey(creds))
        else store.clearEverything()
        _state.update {
            DownloadUiState(phase = DownloadPhase.Idle, message = "Offline downloads cleared", bytesUsed = 0L)
        }
    }

    private suspend fun runBatch(songs: List<Song>, collectionLabel: String?) {
        val creds = client.credentials ?: return
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
            ensureActive()
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
                downloadOne(key, song)
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
                        message = e.message ?: "Download failed",
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

    private suspend fun downloadOne(key: String, song: Song) = withContext(Dispatchers.IO) {
        // URL exists only for this request — never written to index or log files.
        val url = client.downloadUrl(song.id)
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "*/*")
            .build()
        val target = store.targetFile(key, song)
        val tmp = File(target.parentFile, target.name + ".part")
        client.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SubsonicException(0, "HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty download body")
            val contentType = body.contentType()?.toString().orEmpty()
            body.byteStream().use { input ->
                val buffered = input.buffered()
                buffered.mark(64)
                val head = ByteArray(32)
                val n = buffered.read(head)
                buffered.reset()
                val headText = if (n > 0) head.copyOf(n).toString(Charsets.UTF_8).trimStart() else ""
                if (contentType.contains("json", ignoreCase = true) || headText.startsWith("{")) {
                    val peek = buffered.readBytes().toString(Charsets.UTF_8).take(500)
                    throw SubsonicException(0, "Server refused download: ${peek.take(120)}")
                }
                tmp.outputStream().use { out -> buffered.copyTo(out) }
            }
        }
        if (tmp.length() <= 0L) {
            tmp.delete()
            throw IOException("Downloaded file was empty")
        }
        if (song.size > 0 && tmp.length() < song.size / 2) {
            // Soft check only — some servers omit Content-Length / report wrong size.
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        store.markDownloaded(key, song, target)
    }
}
