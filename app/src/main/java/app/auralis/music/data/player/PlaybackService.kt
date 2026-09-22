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
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import app.auralis.music.AuralisApp
import app.auralis.music.MainActivity
import app.auralis.music.R
import app.auralis.music.data.player.auto.AutoLibraryCallback
import com.google.common.collect.ImmutableList

@UnstableApi
class PlaybackService : MediaLibraryService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var player: ExoPlayer? = null
    private var fadePlayer: ExoPlayer? = null
    private var session: MediaLibraryService.MediaLibrarySession? = null
    private var libraryCallback: AutoLibraryCallback? = null
    private val eqMain = EqController()
    private val eqFade = EqController()
    private var rgLinear = 1f
    private lateinit var settings: PlayerSettings
    private val handler = Handler(Looper.getMainLooper())
    private var fadeAnim: ValueAnimator? = null
    private var fading = false

    private val sleepFire = Runnable { onSleepTimerFired() }
    private val sleepState = SleepTimerState()

    private val tick = object : Runnable {
        override fun run() {
            maybeStartCrossfade()
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = PlayerSettings(this)
        settings.register(this)

        val exo = buildPlayer()
        player = exo
        applyGapless(exo)
        applyDisconnectPolicy(exo)
        attachEq(exo, eqMain)

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
        val app = application as AuralisApp
        val callback = AutoLibraryCallback(app.container, app.container.player)
        libraryCallback = callback
        session = MediaLibraryService.MediaLibrarySession.Builder(this, exo, callback)
            .setId("app.auralis.music.session")
            .setSessionActivity(openApp)
            .setExtras(extras)
            .build()
        installPlayerListeners(exo)
        applyReplayGain(exo, exo.currentMediaItem)
        setMediaNotificationProvider(AuralisNotificationProvider(this))
        handler.post(tick)
        armSleepTimerFromSettings()
    }

    private fun installPlayerListeners(exo: ExoPlayer) {
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player === exo) applyReplayGain(exo, mediaItem)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (player === exo) attachEq(exo, eqMain)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (player !== exo) return
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    cancelCrossfade()
                    cancelSleepFromUser()
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (player === exo && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    cancelCrossfade()
                    cancelSleepFromUser()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (player !== exo) return
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    cancelCrossfade()
                }
                when (sleepState.onPlayWhenReadyChanged(playWhenReady, reason)) {
                    SleepTimerState.Action.Fire -> onSleepTimerFired()
                    SleepTimerState.Action.Cancel -> cancelSleepFromUser()
                    SleepTimerState.Action.None -> Unit
                }
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? = session

    override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
        val exo = player ?: return
        when (key) {
            PlayerSettings.EQ_ON, PlayerSettings.EQ_GAINS, PlayerSettings.EQ_PRESET -> {
                eqMain.apply(settings)
                eqFade.apply(settings)
            }
            PlayerSettings.RG_MODE, PlayerSettings.RG_LIMIT -> applyReplayGain(exo, exo.currentMediaItem)
            PlayerSettings.GAPLESS, PlayerSettings.CROSSFADE -> {
                if (!settings.gapless) cancelCrossfade()
                applyGapless(exo)
            }
            PlayerSettings.PAUSE_DISC -> applyDisconnectPolicy(exo)
            PlayerSettings.SLEEP_MINUTES, PlayerSettings.SLEEP_DEADLINE -> armSleepTimerFromSettings()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(sleepFire)
        cancelCrossfade()
        settings.unregister(this)
        libraryCallback?.release()
        libraryCallback = null
        session?.run {
            player.release()
            release()
        }
        fadePlayer?.release()
        eqMain.release()
        eqFade.release()
        session = null
        player = null
        fadePlayer = null
        super.onDestroy()
    }

    private fun buildPlayer(): ExoPlayer {
        val http = OkHttpDataSource.Factory((application as AuralisApp).container.client.http)
            .setUserAgent("Auralis/${app.auralis.music.BuildConfig.VERSION_NAME}")
        return ExoPlayer.Builder(this)
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
        exo.pauseAtEndOfMediaItems = sleepState.endOfTrack || (fading && player === exo)
        exo.skipSilenceEnabled = false
    }

    private fun applyDisconnectPolicy(exo: ExoPlayer) {
        exo.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
        fadePlayer?.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
    }

    private fun attachEq(exo: ExoPlayer, eq: EqController) {
        eq.attach(exo.audioSessionId, settings)
    }

    private fun applyReplayGain(exo: ExoPlayer, item: MediaItem?) {
        if (fading) return
        rgLinear = ReplayGainProcessor.fromExtras(
            item?.mediaMetadata?.extras,
            settings.replayGainMode,
            settings.peakLimiter,
        )
        exo.volume = replayGainToPlayerVolume(rgLinear)
    }

    private fun maybeStartCrossfade() {
        if (fading || sleepState.endOfTrack) return
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
        val next = buildPlayer()
        fadePlayer = next
        applyDisconnectPolicy(next)
        attachEq(next, eqFade)
        val nextGain = replayGainToPlayerVolume(
            ReplayGainProcessor.fromExtras(
                items[nextIndex].mediaMetadata.extras,
                settings.replayGainMode,
                settings.peakLimiter,
            ),
        )
        val fromGain = replayGainToPlayerVolume(rgLinear)
        from.pauseAtEndOfMediaItems = true
        next.setMediaItems(items, nextIndex, 0L)
        next.volume = 0f
        next.prepare()
        next.play()
        fadeAnim?.cancel()
        fadeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeMs.coerceAtLeast(200L)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                from.volume = fromGain * (1f - t)
                next.volume = nextGain * t
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    finishCrossfade(from, next, nextGain)
                }

            })
            start()
        }
    }

    private fun finishCrossfade(from: ExoPlayer, next: ExoPlayer, nextGain: Float) {
        if (!fading || player !== from || fadePlayer !== next) return
        player = next
        session?.player = next
        fadePlayer = null
        from.stop()
        from.release()
        next.volume = nextGain
        rgLinear = nextGain
        fading = false
        fadeAnim = null
        applyGapless(next)
        installPlayerListeners(next)
        attachEq(next, eqMain)
        applyReplayGain(next, next.currentMediaItem)
    }

    private fun armSleepTimerFromSettings() {
        handler.removeCallbacks(sleepFire)
        sleepState.arm(settings.sleepTimerMinutes)
        if (sleepState.endOfTrack) cancelCrossfade()
        player?.let(::applyGapless)
        when (val minutes = settings.sleepTimerMinutes) {
            0 -> Unit
            PlayerSettings.SLEEP_END_OF_TRACK -> {
                // ExoPlayer pauses at the item boundary, including repeat, without polling.
            }
            else -> {
                val deadline = settings.sleepDeadlineElapsed
                val delay = if (deadline > 0L) {
                    deadline - SystemClock.elapsedRealtime()
                } else {
                    minutes * 60_000L
                }
                if (delay <= 0L) {
                    onSleepTimerFired()
                } else {
                    handler.postDelayed(sleepFire, delay)
                }
            }
        }
    }

    private fun onSleepTimerFired() {
        handler.removeCallbacks(sleepFire)
        sleepState.arm(0)
        // Detach the animation callbacks before cancellation: cancel also dispatches end.
        cancelCrossfade()
        player?.pause()
        settings.clearSleepTimer()
    }

    private fun cancelSleepFromUser() {
        if (settings.sleepTimerMinutes == 0 && !sleepState.endOfTrack) return
        handler.removeCallbacks(sleepFire)
        sleepState.arm(0)
        player?.let(::applyGapless)
        settings.clearSleepTimer()
    }

    private fun cancelCrossfade() {
        fadeAnim?.removeAllListeners()
        fadeAnim?.removeAllUpdateListeners()
        fadeAnim?.cancel()
        fadeAnim = null
        fadePlayer?.let {
            it.stop()
            it.release()
        }
        fadePlayer = null
        player?.volume = replayGainToPlayerVolume(rgLinear)
        fading = false
        player?.let(::applyGapless)
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
