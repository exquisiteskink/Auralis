package app.sonveil.music.data.player

import app.sonveil.music.data.remote.Song
import org.junit.Assert.*
import org.junit.Test

class PlayerUiStateTest {
    @Test fun shuffledQueueKeepsActualIndicesIncludingDuplicateSongs() {
        val songs = listOf(Song("a"), Song("b"), Song("a"), Song("c"))
        val state = PlayerUiState(queue = songs, index = 1, shuffle = true, upcomingIndices = listOf(3, 0, 2))
        assertEquals(listOf("c", "a", "a"), state.upNext.map { it.id })
        assertEquals(listOf(3, 0, 2), state.upNextIndices)
        assertTrue(state.copy(upcomingIndices = emptyList()).upNext.isEmpty())
    }

    @Test fun favoriteOverrideTakesPrecedenceOverStaleSongMetadata() {
        val favorite = Song("a", starred = "2026-09-18")
        assertFalse(PlayerUiState(favoriteById = mapOf("a" to false)).isFavorite(favorite))
        assertTrue(PlayerUiState(favoriteById = mapOf("a" to true)).isFavorite(favorite.copy(starred = null)))
        assertTrue(PlayerUiState().isFavorite(favorite))
    }
}
