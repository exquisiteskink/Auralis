package app.sonveil.music.data.player.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResumptionTest {
    @Test
    fun serviceFirstResumptionRestoresSessionBeforeRebuildingItems() = runBlocking {
        var credentialsAvailable = false
        val calls = mutableListOf<String>()

        val result = loadPlaybackResumption(
            restoreSession = {
                calls += "restore"
                credentialsAvailable = true
            },
            loadItems = {
                calls += "load"
                assertTrue(credentialsAvailable)
                "resumed-items"
            },
        )

        assertEquals("resumed-items", result)
        assertEquals(listOf("restore", "load"), calls)
    }
}
