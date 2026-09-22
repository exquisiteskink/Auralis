package app.auralis.music.data.player

import androidx.media3.common.PlaybackException

/**
 * Classifies ExoPlayer / Media3 playback failures for recovery UI and auto-retry.
 * Network / HTTP stream blips are transient; decoder / DRM failures are not.
 */
internal object PlaybackErrors {
    fun isTransient(error: PlaybackException): Boolean {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            -> return true
        }
        var cause: Throwable? = error.cause
        while (cause != null) {
            val name = cause.javaClass.name
            if (name.contains("HttpDataSource") ||
                name.contains("UnknownHostException") ||
                name.contains("SocketTimeoutException") ||
                name.contains("ConnectException") ||
                name.contains("SSLException") ||
                name.contains("InterruptedIOException") ||
                name.contains("NoRouteToHostException")
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    /** Short label for mini / NP subtitle (not the raw ERROR_CODE_* name). */
    fun userMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "Connection error"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server error"
        PlaybackException.ERROR_CODE_TIMEOUT -> "Playback timed out"
        else -> if (isTransient(error)) "Connection error" else "Playback error"
    }
}
