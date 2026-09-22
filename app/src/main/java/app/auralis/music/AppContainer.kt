package app.auralis.music

import android.content.Context
import app.auralis.music.data.auth.CredentialStore
import app.auralis.music.data.auth.StoredCredentials
import app.auralis.music.data.player.PlayerController
import app.auralis.music.data.player.PlayerSettings
import app.auralis.music.data.download.DownloadStore
import app.auralis.music.data.download.OfflineDownloadManager
import app.auralis.music.data.remote.MetadataRepository
import app.auralis.music.data.remote.SubsonicClient
import app.auralis.music.data.waveform.WaveformRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

class AppContainer(context: Context) {
    val credentials = CredentialStore(context)
    val client = SubsonicClient()
    val metadata = MetadataRepository(client)
    val playerSettings = PlayerSettings(context.applicationContext)
    val downloadStore = DownloadStore(context.applicationContext)
    val downloads = OfflineDownloadManager(context.applicationContext, client, downloadStore, playerSettings)
    val waveforms = WaveformRepository(context.applicationContext, client, downloadStore)
    val player = PlayerController(context.applicationContext, client, downloadStore)

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn
    private val loginMutex = Mutex()
    private var restored = false

    suspend fun restoreSession() = loginMutex.withLock {
        if (restored || _loggedIn.value) return@withLock
        val stored = credentials.load()
        if (stored == null) {
            restored = true
            return@withLock
        }
        // Credentials were saved only after a successful login. Local playback must
        // not depend on a fresh network round trip on every process launch.
        val hasDownloads = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            downloadStore.availableSongs(downloadStore.serverKey(stored)).isNotEmpty()
        }
        if (hasDownloads) {
            client.credentials = stored
            restored = true
            setLoggedIn(true)
            return@withLock
        }
        try {
            val (accepted, _) = client.login(stored)
            credentials.save(accepted)
            restored = true
            setLoggedIn(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            client.credentials = null
            setLoggedIn(false)
            if (e is app.auralis.music.data.remote.SubsonicException && e.code in listOf(40, 41, 42, 43, 44)) {
                // Permanent auth failure — forget the saved session.
                credentials.clear()
                restored = true
            }
            // Transient network/server errors: keep the encrypted store and leave
            // restored=false so Login / Android Auto can retry restoreSession().
        }
    }

    suspend fun signIn(candidate: StoredCredentials) = loginMutex.withLock {
        downloads.cancelAndJoin()
        try {
            val (accepted, _) = client.login(candidate)
            credentials.save(accepted)
            restored = true
            setLoggedIn(true)
        } catch (e: Exception) {
            client.credentials = null
            throw e
        }
    }

    fun signOut() {
        downloads.cancel()
        player.stopAndReset()
        client.http.dispatcher.cancelAll()
        client.credentials = null
        client.rotateSessionSalt()
        credentials.clear()
        restored = true
        setLoggedIn(false)
    }

    fun setLoggedIn(value: Boolean) {
        _loggedIn.value = value
        if (value) player.connect()
    }
}
