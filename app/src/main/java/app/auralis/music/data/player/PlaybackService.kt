package app.auralis.music.data.player

import android.animation.ValueAnimator
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
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
import kotlin.math.abs

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
    private var volumeAnim: ValueAnimator? = null
    private var fading = false
    private var pendingReady = false
    private var pendingNextIndex = C.INDEX_UNSET
    private var pendingNextLinear = 1f
    private var lastNotifiedSessionId = 0

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
        applyReplayGain(exo, exo.currentMediaItem, eqMain)
        setMediaNotificationProvider(AuralisNotificationProvider(this))
        handler.post(tick)
        armSleepTimerFromSettings()
    }

    private fun installPlayerListeners(exo: ExoPlayer) {
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player === exo) applyReplayGain(exo, mediaItem, eqMain)
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
            PlayerSettings.RG_MODE, PlayerSettings.RG_LIMIT -> applyReplayGain(exo, exo.currentMediaItem, eqMain)
            PlayerSettings.GAPLESS, PlayerSettings.CROSSFADE -> {
                if (!settings.gapless) cancelCrossfade()
                applyGapless(exo)
            }
            PlayerSettings.PAUSE_DISC -> applyDisconnectPolicy(exo)
            PlayerSettings.SLEEP_MINUTES,
            PlayerSettings.SLEEP_DEADLINE,
            PlayerSettings.SLEEP_DEADLINE_WALL,
            PlayerSettings.SLEEP_DEADLINE_BOOT_COUNT,
            -> armSleepTimerFromSettings()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(sleepFire)
        cancelCrossfade()
        volumeAnim?.cancel()
        volumeAnim = null
        settings.unregister(this)
        libraryCallback?.release()
        libraryCallback = null
        notifyExternalEqSession(lastNotifiedSessionId, open = false)
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

    private fun buildPlayer(handleAudioFocus: Boolean = true): ExoPlayer {
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
                mediaAudioAttributes(),
                handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private fun mediaAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

    private fun applyGapless(exo: ExoPlayer) {
        exo.pauseAtEndOfMediaItems = sleepState.endOfTrack || (fading && player === exo)
        exo.skipSilenceEnabled = false
    }

    private fun applyDisconnectPolicy(exo: ExoPlayer) {
        exo.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
        fadePlayer?.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
    }

    private fun attachEq(exo: ExoPlayer, eq: EqController) {
        val prev = eq.audioSessionId
        val sid = exo.audioSessionId
        if (prev != 0 && prev != sid && eq === eqMain) {
            notifyExternalEqSession(prev, open = false)
        }
        eq.attach(sid, settings)
        if (eq === eqMain && sid != 0) {
            notifyExternalEqSession(sid, open = true)
        }
    }

    /**
     * Prefer DynamicsProcessing input gain for ReplayGain (same platform path as EQ).
     * Keep [ExoPlayer.setVolume] at 1f whenever possible so Poweramp EQ DVC (and similar
     * external equalizers) are not fighting abrupt AudioTrack volume snaps on skip.
     * Fallback (no DP): short smooth ramp of player volume — never a hard set.
     */
    private fun applyReplayGain(exo: ExoPlayer, item: MediaItem?, eq: EqController) {
        if (fading) return
        rgLinear = ReplayGainProcessor.fromExtras(
            item?.mediaMetadata?.extras,
            settings.replayGainMode,
            settings.peakLimiter,
        )
        val viaEffect = eq.applyReplayGainLinear(rgLinear, settings)
        if (viaEffect) {
            setPlayerVolumeSmooth(exo, 1f)
        } else {
            setPlayerVolumeSmooth(exo, replayGainToPlayerVolume(rgLinear))
        }
    }

    private fun setPlayerVolumeSmooth(exo: ExoPlayer, target: Float) {
        volumeAnim?.cancel()
        val from = exo.volume
        if (abs(from - target) < 0.01f) {
            if (from != target) exo.volume = target
            volumeAnim = null
            return
        }
        volumeAnim = ValueAnimator.ofFloat(from, target).apply {
            duration = VOLUME_RAMP_MS
            addUpdateListener { a ->
                if (!fading) exo.volume = a.animatedValue as Float
            }
            start()
        }
    }

    private fun notifyExternalEqSession(sessionId: Int, open: Boolean) {
        if (sessionId == 0) return
        if (open && sessionId == lastNotifiedSessionId) return
        if (!open && sessionId != lastNotifiedSessionId && lastNotifiedSessionId != 0) {
            // Already tracking a different session; still close the requested one.
        }
        val action = if (open) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        } else {
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        }
        runCatching {
            sendBroadcast(
                Intent(action).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                },
            )
        }
        lastNotifiedSessionId = if (open) sessionId else 0
    }

    private fun maybeStartCrossfade() {
        if (fading || sleepState.endOfTrack) return
        if (!settings.gapless || !settings.crossfade) {
            releasePendingFadePlayer()
            return
        }
        val exo = player ?: return
        if (!exo.isPlaying) return
        val dur = exo.duration
        if (dur <= 0) return
        val remain = dur - exo.currentPosition
        val fade = settings.crossfadeMs.toLong()
        if (!exo.hasNextMediaItem()) {
            releasePendingFadePlayer()
            return
        }
        val nextIndex = exo.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return

        // Warm the next player well before the fade window so prepare/buffer is not
        // on the critical path (owner: pause before fade / next not loaded).
        if (remain > fade + PREPARE_LEAD_MS) {
            releasePendingFadePlayer()
            return
        }
        if (remain > 0) {
            ensureNextPrepared(exo, nextIndex)
        }

        // Only start the volume ramp once we are inside the fade window AND the next
        // player is READY. Do not duck the outgoing track while the next is cold.
        if (remain <= 0 || remain > fade) return
        if (fadePlayer == null || !pendingReady) return
        beginCrossfadeRamp(exo, fadeMs = minOf(fade, remain).coerceAtLeast(200L))
    }

    /**
     * Build/prepare the secondary player without taking audio focus and without playing.
     * Critical: [handleAudioFocus]=false so [play] later does not pause the outgoing
     * player (owner: hard cut / early stop instead of true overlap).
     */
    private fun ensureNextPrepared(from: ExoPlayer, nextIndex: Int) {
        val existing = fadePlayer
        if (existing != null && pendingNextIndex == nextIndex) return
        releasePendingFadePlayer()

        val items = (0 until from.mediaItemCount).map { from.getMediaItemAt(it) }
        val next = buildPlayer(handleAudioFocus = false)
        fadePlayer = next
        pendingNextIndex = nextIndex
        pendingReady = false
        pendingNextLinear = ReplayGainProcessor.fromExtras(
            items[nextIndex].mediaMetadata.extras,
            settings.replayGainMode,
            settings.peakLimiter,
        )
        applyDisconnectPolicy(next)
        attachEq(next, eqFade)
        eqFade.applyReplayGainLinear(pendingNextLinear, settings)
        next.volume = 0f
        next.setMediaItems(items, nextIndex, 0L)
        next.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (fadePlayer !== next) return
                if (playbackState == Player.STATE_READY) {
                    pendingReady = true
                }
            }
        })
        next.prepare()
        // prepare() may already be READY before the listener is observed.
        if (next.playbackState == Player.STATE_READY) {
            pendingReady = true
        }
    }

    private fun beginCrossfadeRamp(from: ExoPlayer, fadeMs: Long) {
        val next = fadePlayer ?: return
        if (!pendingReady || fading) return
        fading = true
        volumeAnim?.cancel()
        volumeAnim = null

        val nextLinear = pendingNextLinear
        // Keep ReplayGain on DynamicsProcessing when available; crossfade envelopes are
        // mix-only (0↔1). Multiplying RG into player volume again caused jumps and
        // fought Poweramp DVC on the secondary session.
        val nextViaEffect = eqFade.applyReplayGainLinear(nextLinear, settings)
        eqMain.applyReplayGainLinear(rgLinear, settings)
        val nextGain = if (nextViaEffect) 1f else replayGainToPlayerVolume(nextLinear)
        val fromGain = from.volume.coerceIn(0.05f, 1f)

        from.pauseAtEndOfMediaItems = true
        // Secondary was built without audio focus — both players can be audible together.
        next.playWhenReady = true
        if (!next.isPlaying) next.play()

        fadeAnim?.cancel()
        fadeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeMs
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                from.volume = fromGain * (1f - t)
                next.volume = nextGain * t
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    finishCrossfade(from, next, nextLinear)
                }
            })
            start()
        }
    }

    private fun finishCrossfade(from: ExoPlayer, next: ExoPlayer, nextLinear: Float) {
        if (!fading || player !== from || fadePlayer !== next) return
        player = next
        session?.player = next
        fadePlayer = null
        pendingReady = false
        pendingNextIndex = C.INDEX_UNSET
        from.stop()
        from.release()
        // Promote secondary to focus owner now that it is the sole primary player.
        next.setAudioAttributes(mediaAudioAttributes(), /* handleAudioFocus= */ true)
        rgLinear = nextLinear
        fading = false
        fadeAnim = null
        applyGapless(next)
        installPlayerListeners(next)
        attachEq(next, eqMain)
        applyReplayGain(next, next.currentMediaItem, eqMain)
    }

    private fun releasePendingFadePlayer() {
        if (fading) return
        fadeAnim?.removeAllListeners()
        fadeAnim?.removeAllUpdateListeners()
        fadeAnim?.cancel()
        fadeAnim = null
        fadePlayer?.let {
            it.stop()
            it.release()
        }
        fadePlayer = null
        pendingReady = false
        pendingNextIndex = C.INDEX_UNSET
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
                val delay = SleepTimerDeadline.remainingDelayMs(
                    savedBootCount = settings.sleepDeadlineBootCount,
                    currentBootCount = settings.currentBootCount,
                    elapsedDeadlineMs = settings.sleepDeadlineElapsed,
                    wallDeadlineMs = settings.sleepDeadlineWallMs,
                    elapsedNowMs = SystemClock.elapsedRealtime(),
                    wallNowMs = System.currentTimeMillis(),
                    fallbackDurationMs = minutes * 60_000L,
                )
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
        pendingReady = false
        pendingNextIndex = C.INDEX_UNSET
        fading = false
        player?.let { exo ->
            applyReplayGain(exo, exo.currentMediaItem, eqMain)
            applyGapless(exo)
        }
    }

    companion object {
        private const val VOLUME_RAMP_MS = 120L
        /** How far ahead of the fade window to prepare the next ExoPlayer. */
        private const val PREPARE_LEAD_MS = 8_000L
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
