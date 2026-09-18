package app.auralis.music

import android.content.Context
import app.auralis.music.data.auth.CredentialStore
import app.auralis.music.data.player.PlayerController
import app.auralis.music.data.remote.MetadataRepository
import app.auralis.music.data.remote.SubsonicClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppContainer(context: Context) {
    val credentials = CredentialStore(context)
    val client = SubsonicClient()
    val metadata = MetadataRepository(client.http, client)
    val player = PlayerController(context.applicationContext, client)

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    fun setLoggedIn(value: Boolean) {
        _loggedIn.value = value
        if (value) player.connect()
    }
}
