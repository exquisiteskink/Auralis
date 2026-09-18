package app.auralis.music.data.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.auralis.music.AuralisApp
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.auralis.music.MainActivity
import app.auralis.music.R
import com.google.common.collect.ImmutableList

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val http = OkHttpDataSource.Factory((application as AuralisApp).container.client.http)
            .setUserAgent("Auralis/${app.auralis.music.BuildConfig.VERSION_NAME}")

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(DefaultDataSource.Factory(this, http)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player = exo

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val extras = Bundle().apply {
            putString("com.android.music.musicsource", "Auralis")
            putString("app_name", "Auralis")
        }

        session = MediaSession.Builder(this, exo)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    if (!controller.isTrusted) return MediaSession.ConnectionResult.reject()
                    val accepted = super.onConnect(session, controller)
                    if (controller.uid == android.os.Process.myUid()) return accepted
                    // System controls can operate playback, but cannot inject arbitrary URLs/files.
                    return MediaSession.ConnectionResult.accept(
                        accepted.availableSessionCommands,
                        accepted.availablePlayerCommands.buildUpon()
                            .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                            .remove(Player.COMMAND_SET_MEDIA_ITEM)
                            .build(),
                    )
                }
            })
            .setId("app.auralis.music.session")
            .setSessionActivity(openApp)
            .setExtras(extras)
            .build()

        setMediaNotificationProvider(AuralisNotificationProvider(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        super.onDestroy()
    }
}

@UnstableApi
private class AuralisNotificationProvider(
    private val appContext: Context,
) : MediaNotification.Provider {

    private val inner = DefaultMediaNotificationProvider.Builder(appContext)
        .setChannelId("auralis_playback")
        .setChannelName(R.string.playback_channel)
        .build()
        .also { it.setSmallIcon(R.drawable.ic_stat_auralis) }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val created = inner.createNotification(
            mediaSession,
            mediaButtonPreferences,
            actionFactory,
            onNotificationChangedCallback,
        )
        val rebuilt = Notification.Builder.recoverBuilder(appContext, created.notification)
            .setSubText(appContext.getString(R.string.app_name))
            .setContentInfo(appContext.getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_stat_auralis)
            .setColorized(true)
            .build()
        return MediaNotification(created.notificationId, rebuilt)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = inner.handleCustomCommand(session, action, extras)
}
