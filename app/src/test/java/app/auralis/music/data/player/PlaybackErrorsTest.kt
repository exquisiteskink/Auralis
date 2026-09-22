package app.auralis.music.data.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PlaybackErrorsTest {
    @Test
    fun networkCodesAreTransientWithFriendlyLabels() {
        val failed = PlaybackException(
            "net",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )
        assertTrue(PlaybackErrors.isTransient(failed))
        assertEquals("Connection error", PlaybackErrors.userMessage(failed))

        val timeout = PlaybackException(
            "timeout",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        )
        assertTrue(PlaybackErrors.isTransient(timeout))
        assertEquals("Connection error", PlaybackErrors.userMessage(timeout))
    }

    @Test
    fun httpCauseIsTransientEvenIfCodeGeneric() {
        val err = PlaybackException(
            "io",
            UnknownHostException("music.example"),
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )
        assertTrue(PlaybackErrors.isTransient(err))
        assertEquals("Connection error", PlaybackErrors.userMessage(err))
    }

    @Test
    fun decoderFailureIsNotTransient() {
        val err = PlaybackException(
            "decode",
            null,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
        )
        assertFalse(PlaybackErrors.isTransient(err))
        assertEquals("Playback error", PlaybackErrors.userMessage(err))
    }

    @Test
    fun nestedSocketTimeoutIsTransient() {
        val err = PlaybackException(
            "wrap",
            RuntimeException(SocketTimeoutException("read")),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        assertTrue(PlaybackErrors.isTransient(err))
    }
}
