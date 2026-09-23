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
        val failed = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        val timeout = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        assertTrue(PlaybackErrors.isTransient(failed, "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"))
        assertEquals("Connection error", PlaybackErrors.userMessage(failed, "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"))
        assertTrue(PlaybackErrors.isTransient(timeout, "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"))
        assertEquals("Connection error", PlaybackErrors.userMessage(timeout, "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"))
    }

    @Test
    fun httpCauseIsTransientEvenIfCodeGeneric() {
        val code = PlaybackException.ERROR_CODE_UNSPECIFIED
        val cause = UnknownHostException("music.example")
        assertTrue(PlaybackErrors.isTransient(code, "ERROR_CODE_UNSPECIFIED", cause))
        assertEquals("Connection error", PlaybackErrors.userMessage(code, "ERROR_CODE_UNSPECIFIED", cause))
    }

    @Test
    fun decoderFailureIsNotTransient() {
        val code = PlaybackException.ERROR_CODE_DECODING_FAILED
        assertFalse(PlaybackErrors.isTransient(code, "ERROR_CODE_DECODING_FAILED"))
        assertEquals("Playback error", PlaybackErrors.userMessage(code, "ERROR_CODE_DECODING_FAILED"))
    }

    @Test
    fun nestedSocketTimeoutIsTransient() {
        val cause = RuntimeException(SocketTimeoutException("read"))
        assertTrue(PlaybackErrors.isTransient(PlaybackException.ERROR_CODE_UNSPECIFIED, "ERROR_CODE_UNSPECIFIED", cause))
    }

    @Test
    fun errorCodeNameContainingNetworkConnectionIsTransient() {
        val code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        val name = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"
        assertTrue(PlaybackErrors.isTransient(code, name))
        assertEquals("Connection error", PlaybackErrors.userMessage(code, name))
    }
}
