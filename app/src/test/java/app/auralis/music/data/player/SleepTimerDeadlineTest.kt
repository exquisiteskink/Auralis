package app.auralis.music.data.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerDeadlineTest {
    @Test fun sameBootUsesElapsedDeadlineWhenWallClockMovesForwardOrBackward() {
        val expected = 600_000L
        assertEquals(expected, remaining(savedBoot = 12, currentBoot = 12, wallNow = 9_000_000L))
        assertEquals(expected, remaining(savedBoot = 12, currentBoot = 12, wallNow = -9_000_000L))
    }

    @Test fun rebootUsesStillActiveWallDeadline() {
        assertEquals(300_000L, remaining(savedBoot = 12, currentBoot = 13, wallNow = 1_700_000L))
    }

    @Test fun rebootWithExpiredWallDeadlineFiresImmediately() {
        assertEquals(-1L, remaining(savedBoot = 12, currentBoot = 13, wallNow = 2_000_001L))
    }

    private fun remaining(savedBoot: Int, currentBoot: Int, wallNow: Long): Long =
        SleepTimerDeadline.remainingDelayMs(
            savedBootCount = savedBoot,
            currentBootCount = currentBoot,
            elapsedDeadlineMs = 1_600_000L,
            wallDeadlineMs = 2_000_000L,
            elapsedNowMs = 1_000_000L,
            wallNowMs = wallNow,
            fallbackDurationMs = 900_000L,
        )
}
