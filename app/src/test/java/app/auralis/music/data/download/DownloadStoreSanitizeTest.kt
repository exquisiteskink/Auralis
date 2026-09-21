package app.auralis.music.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadStoreSanitizeTest {
    @Test
    fun sanitizeIdStripsPathTraversalAndQueryLikeChars() {
        assertEquals("song_a_b", DownloadStore.sanitizeId("song/a?b"))
        assertEquals(".._.._etc_passwd", DownloadStore.sanitizeId("../../etc/passwd"))
        assertFalse(DownloadStore.sanitizeId("a&apiKey=secret").contains("apiKey"))
    }

    @Test
    fun guessSuffixFromContentType() {
        assertEquals("flac", DownloadStore.guessSuffix("audio/flac"))
        assertEquals("mp3", DownloadStore.guessSuffix("audio/mpeg"))
    }
}
