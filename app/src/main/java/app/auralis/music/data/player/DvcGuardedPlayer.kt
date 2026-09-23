package app.auralis.music.data.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Intercepts transport that Media3 applies **before** [Player.Listener] callbacks,
 * so we can mute **before** [ExoPlayer.seekTo] / pause hit the playback thread.
 *
 * Why: Media3 [androidx.media3.exoplayer.audio.DefaultAudioSink.flush] releases the
 * AudioTrack on every seek. With Poweramp EQ DVC, a new track at volume≈1f before
 * PA rebinds is the seek blast. Listener-only mute races the flush.
 *
 * [player] remains the underlying [ExoPlayer] for session/EQ code; the MediaSession
 * is wired to this wrapper.
 */
@UnstableApi
class DvcGuardedPlayer(
    private val exo: ExoPlayer,
    private val guard: Guard,
) : ForwardingPlayer(exo) {

    interface Guard {
        /** Mute immediately; arm settle only when appropriate (not while paused). */
        fun beforeSeek(reason: String)
        /** Mute and hold — do not settle/unmute until a later play/resume. */
        fun beforePause(reason: String)
        /** If held muted / bind pending, re-arm mute-until-bound before audible play. */
        fun beforePlayOrResume(reason: String)
    }

    val exoPlayer: ExoPlayer get() = exo

    override fun seekTo(positionMs: Long) {
        guard.beforeSeek("seekTo")
        super.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        guard.beforeSeek("seekTo-index")
        super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekToDefaultPosition() {
        guard.beforeSeek("seekToDefault")
        super.seekToDefaultPosition()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        guard.beforeSeek("seekToDefault-index")
        super.seekToDefaultPosition(mediaItemIndex)
    }

    override fun seekBack() {
        guard.beforeSeek("seekBack")
        super.seekBack()
    }

    override fun seekForward() {
        guard.beforeSeek("seekForward")
        super.seekForward()
    }

    override fun seekToPreviousMediaItem() {
        guard.beforeSeek("seekToPreviousMediaItem")
        super.seekToPreviousMediaItem()
    }

    override fun seekToPrevious() {
        guard.beforeSeek("seekToPrevious")
        super.seekToPrevious()
    }

    override fun seekToNextMediaItem() {
        guard.beforeSeek("seekToNextMediaItem")
        super.seekToNextMediaItem()
    }

    override fun seekToNext() {
        guard.beforeSeek("seekToNext")
        super.seekToNext()
    }

    override fun seekToPreviousWindow() {
        guard.beforeSeek("seekToPreviousWindow")
        super.seekToPreviousWindow()
    }

    override fun seekToNextWindow() {
        guard.beforeSeek("seekToNextWindow")
        super.seekToNextWindow()
    }

    override fun pause() {
        guard.beforePause("pause")
        super.pause()
    }

    override fun play() {
        guard.beforePlayOrResume("play")
        super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            guard.beforePlayOrResume("setPlayWhenReady-true")
        } else {
            guard.beforePause("setPlayWhenReady-false")
        }
        super.setPlayWhenReady(playWhenReady)
    }

    override fun stop() {
        guard.beforePause("stop")
        super.stop()
    }
}
