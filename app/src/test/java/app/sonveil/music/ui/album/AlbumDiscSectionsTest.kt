package app.sonveil.music.ui.album

import app.sonveil.music.data.remote.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDiscSectionsTest {
    @Test
    fun `groups and orders tracks from multiple discs`() {
        val sections = albumDiscSections(
            listOf(
                song("d2t2", disc = 2, track = 2),
                song("d1t2", disc = 1, track = 2),
                song("d2t1", disc = 2, track = 1),
                song("d1t1", disc = 1, track = 1),
            ),
        )

        assertEquals(listOf(1, 2), sections.map { it.number })
        assertEquals(listOf("d1t1", "d1t2"), sections[0].songs.map { it.id })
        assertEquals(listOf("d2t1", "d2t2"), sections[1].songs.map { it.id })
    }

    @Test
    fun `treats missing disc metadata as disc one`() {
        val sections = albumDiscSections(
            listOf(
                song("untagged", disc = 0, track = 2),
                song("tagged", disc = 1, track = 1),
            ),
        )

        assertEquals(listOf(1), sections.map { it.number })
        assertEquals(listOf("tagged", "untagged"), sections.single().songs.map { it.id })
    }

    @Test
    fun `places tracks without a track number after numbered tracks`() {
        val sections = albumDiscSections(
            listOf(
                song("unknown", disc = 2, track = 0),
                song("numbered", disc = 2, track = 4),
            ),
        )

        assertEquals(listOf("numbered", "unknown"), sections.single().songs.map { it.id })
    }

    private fun song(id: String, disc: Int, track: Int) = Song(
        id = id,
        title = id,
        discNumber = disc,
        track = track,
    )
}
