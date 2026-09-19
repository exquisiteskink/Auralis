package app.auralis.music.data.player

import android.animation.ValueAnimator
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.auralis.music.AuralisApp
import app.auralis.music.MainActivity
import app.auralis.music.R
import com.google.common.collect.ImmutableList

@UnstableApi
class PlaybackService : MediaSessionService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var player: ExoPlayer? = null
    private var fadePlayer: ExoPlayer? = null
    private var session: MediaSession? = null
    private var rgMain = ReplayGainProcessor()
    private var rgFade = ReplayGainProcessor()
    private val eq = GraphicEqProcessor()
    private lateinit var settings: PlayerSettings
    private val handler = Handler(Looper.getMainLooper())
    private var fadeAnim: ValueAnimator? = null
    private var fading = false

    private val tick = object : Runnable {
        override fun run() {
            maybeStartCrossfade()
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = PlayerSettings(this)
        eq.enabled = settings.eqEnabled
        eq.setGains(settings.eqGains)
        settings.register(this)

        val exo = buildPlayer(rgMain)
        player = exo
        applyGapless(exo)
        applyDisconnectPolicy(exo)

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
                    val accepted = super.onConnect(session, controller)
                    if (controller.uid == android.os.Process.myUid()) return accepted
                    if (!controller.isTrusted) return MediaSession.ConnectionResult.reject()
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
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyReplayGain(rgMain, mediaItem)
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) cancelCrossfade()
                }
            }
        })
        applyReplayGain(rgMain, exo.currentMediaItem)
        setMediaNotificationProvider(AuralisNotificationProvider(this))
        handler.post(tick)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
        val exo = player ?: return
        when (key) {
            PlayerSettings.EQ_ON, PlayerSettings.EQ_GAINS, PlayerSettings.EQ_PRESET -> {
                eq.enabled = settings.eqEnabled
                eq.setGains(settings.eqGains)
            }
            PlayerSettings.RG_MODE, PlayerSettings.RG_LIMIT -> applyReplayGain(rgMain, exo.currentMediaItem)
            PlayerSettings.GAPLESS, PlayerSettings.CROSSFADE -> {
                if (!settings.gapless) cancelCrossfade()
                applyGapless(exo)
            }
            PlayerSettings.PAUSE_DISC -> applyDisconnectPolicy(exo)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        cancelCrossfade()
        settings.unregister(this)
        session?.run {
            player.release()
            release()
        }
        fadePlayer?.release()
        session = null
        player = null
        fadePlayer = null
        super.onDestroy()
    }

    private fun buildPlayer(rg: ReplayGainProcessor): ExoPlayer {
        val http = OkHttpDataSource.Factory((application as AuralisApp).container.client.http)
            .setUserAgent("Auralis/${app.auralis.music.BuildConfig.VERSION_NAME}")
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(eq, rg))
                    .build()
            }
        }
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(DefaultDataSource.Factory(this, http)),
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private fun applyGapless(exo: ExoPlayer) {
        exo.pauseAtEndOfMediaItems = false
        exo.skipSilenceEnabled = false
    }

    private fun applyDisconnectPolicy(exo: ExoPlayer) {
        exo.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
        fadePlayer?.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
    }

    private fun applyReplayGain(rg: ReplayGainProcessor, item: MediaItem?) {
        rg.limiter = settings.peakLimiter
        rg.linearGain = ReplayGainProcessor.fromExtras(
            item?.mediaMetadata?.extras,
            settings.replayGainMode,
            settings.peakLimiter,
        )
    }

    private fun maybeStartCrossfade() {
        if (fading) return
        if (!settings.gapless || !settings.crossfade) return
        val exo = player ?: return
        if (!exo.isPlaying) return
        val dur = exo.duration
        if (dur <= 0) return
        val remain = dur - exo.currentPosition
        val fade = settings.crossfadeMs.toLong()
        if (remain <= 0 || remain > fade) return
        if (!exo.hasNextMediaItem()) return
        startCrossfade(exo, fade)
    }

    private fun startCrossfade(from: ExoPlayer, fadeMs: Long) {
        val nextIndex = from.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        fading = true
        val items = (0 until from.mediaItemCount).map { from.getMediaItemAt(it) }
        val next = buildPlayer(rgFade)
        fadePlayer = next
        applyDisconnectPolicy(next)
        applyReplayGain(rgFade, items[nextIndex])
        from.pauseAtEndOfMediaItems = true
        next.setMediaItems(items, nextIndex, 0L)
        next.volume = 0f
        next.prepare()
        next.play()
        val startFrom = from.volume
        fadeAnim?.cancel()
        fadeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeMs.coerceAtLeast(200L)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                from.volume = startFrom * (1f - t)
                next.volume = t
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    finishCrossfade(from, next)
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    next.stop()
                    next.release()
                    if (fadePlayer === next) fadePlayer = null
                    from.volume = 1f
                    fading = false
                }
            })
            start()
        }
    }

    private fun finishCrossfade(from: ExoPlayer, next: ExoPlayer) {
        if (player !== from) {
            next.release()
            fading = false
            return
        }
        session?.player = next
        player = next
        fadePlayer = null
        from.stop()
        from.release()
        next.volume = 1f
        rgMain = rgFade
        rgFade = ReplayGainProcessor()
        fading = false
        next.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyReplayGain(rgMain, mediaItem)
            }
        })
        applyReplayGain(rgMain, next.currentMediaItem)
    }

    private fun cancelCrossfade() {
        fadeAnim?.cancel()
        fadeAnim = null
        fadePlayer?.let {
            it.stop()
            it.release()
        }
        fadePlayer = null
        player?.volume = 1f
        fading = false
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
