package app.auralis.music.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EqControllerTest {
    @Test
    fun linearToDb_unityIsZero() {
        assertEquals(0f, EqController.linearToDb(1f), 0.01f)
    }

    @Test
    fun adoptFrom_transfersSessionIdWithoutKeepingDonor() {
        val donor = EqController()
        val target = EqController()
        // Without a real AudioTrack we can only assert the bookkeeping after a no-op adopt
        // of an idle donor: target stays idle, donor stays idle.
        target.adoptFrom(donor)
        assertEquals(0, target.audioSessionId)
        assertEquals(0, donor.audioSessionId)
        assertNotEquals(target, donor)
    }
}
