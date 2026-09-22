package app.auralis.music.data.player

import app.auralis.music.data.remote.ReplayGain
import app.auralis.music.data.remote.Song
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaybackQueueStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(): PlaybackQueueStore =
        PlaybackQueueStore(File(tmp.root, "playback_queue.json"))

    @Test
    fun roundTripPreservesQueueAndPosition() {
        val s = store()
        val songs = listOf(
            Song(id = "1", title = "A", artist = "X", replayGain = ReplayGain(trackGain = 1.5f)),
            Song(id = "2", title = "B"),
        )
        s.save(
            PlaybackQueueStore.Snapshot(
                serverKey = "srv",
                songs = songs,
                index = 1,
                positionMs = 12_345L,
                playWhenReady = true,
                shuffle = true,
                repeatMode = 2,
            ),
        )
        val loaded = s.load()!!
        assertEquals("srv", loaded.serverKey)
        assertEquals(2, loaded.songs.size)
        assertEquals("2", loaded.songs[1].id)
        assertEquals(1, loaded.index)
        assertEquals(12_345L, loaded.positionMs)
        assertTrue(loaded.playWhenReady)
        assertTrue(loaded.shuffle)
        assertEquals(2, loaded.repeatMode)
        assertEquals(1.5f, loaded.songs[0].replayGain!!.trackGain, 0.01f)
    }

    @Test
    fun corruptFileClearsAndReturnsNull() {
        val file = File(tmp.root, "playback_queue.json")
        file.writeText("{not-json")
        val s = PlaybackQueueStore(file)
        assertNull(s.load())
        assertTrue(!file.exists())
    }

    @Test
    fun emptySongsClears() {
        val s = store()
        s.save(PlaybackQueueStore.Snapshot(serverKey = "srv", songs = listOf(Song(id = "1"))))
        s.save(PlaybackQueueStore.Snapshot(serverKey = "srv", songs = emptyList()))
        assertNull(s.load())
    }
}
