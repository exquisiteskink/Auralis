package app.auralis.music.data.player

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test

class SleepTimerStateTest {
    @Test fun bufferingAndAudioFocusDoNotCancelTimer() {
        val state = SleepTimerState().apply { arm(15) }
        assertEquals(SleepTimerState.Action.None, state.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS))
        assertEquals(SleepTimerState.Action.None, state.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST))
        // A rebuffer has no playWhenReady transition at all.
        assertEquals(SleepTimerState.Action.None, state.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST))
    }

    @Test fun explicitPauseThenResumeCancelsAndRearmingResetsIntent() {
        val state = SleepTimerState().apply { arm(15) }
        state.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertEquals(SleepTimerState.Action.Cancel, state.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST))
        state.arm(30)
        assertEquals(SleepTimerState.Action.None, state.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST))
    }

    @Test fun endBoundaryFiresWithoutPositionOrHandlerPolling() {
        val state = SleepTimerState().apply { arm(PlayerSettings.SLEEP_END_OF_TRACK) }
        assertTrue(state.endOfTrack)
        assertEquals(SleepTimerState.Action.Fire, state.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM))
        state.arm(0)
        assertFalse(state.endOfTrack)
        assertEquals(SleepTimerState.Action.None, state.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM))
    }
}
