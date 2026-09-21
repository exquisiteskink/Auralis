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
        try {
            val (accepted, _) = client.login(stored)
            credentials.save(accepted)
            setLoggedIn(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is app.auralis.music.data.remote.SubsonicException && e.code in listOf(40, 41, 42, 43, 44)) {
                credentials.clear()
            }
            client.credentials = null
            setLoggedIn(false)
        }
        restored = true
    }

    suspend fun signIn(candidate: StoredCredentials) = loginMutex.withLock {
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
