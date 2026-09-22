package app.auralis.music.data.player

import androidx.media3.common.Player

/** User intent is independent of buffering / isPlaying changes. */
internal class SleepTimerState {
    enum class Action { None, Cancel, Fire }
    var endOfTrack = false
        private set
    private var armed = false
    private var userPaused = false

    fun arm(minutes: Int) {
        armed = minutes != 0
        endOfTrack = minutes == PlayerSettings.SLEEP_END_OF_TRACK
        userPaused = false
    }

    fun onPlayWhenReadyChanged(ready: Boolean, reason: Int): Action {
        if (!armed) return Action.None
        if (endOfTrack && !ready && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            return Action.Fire
        }
        if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return Action.None
        if (!ready) userPaused = true
        return if (ready && userPaused) Action.Cancel else Action.None
    }
}
