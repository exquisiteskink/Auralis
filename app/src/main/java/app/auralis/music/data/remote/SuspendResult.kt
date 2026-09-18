package app.auralis.music.data.remote

import kotlinx.coroutines.CancellationException

/** A failed optional request may fall back; a cancelled screen must stop working. */
suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
