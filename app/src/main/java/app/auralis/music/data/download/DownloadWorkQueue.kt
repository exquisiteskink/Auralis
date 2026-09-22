package app.auralis.music.data.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Called on the owning (UI) thread; serializes replacement and destructive operations. */
internal class DownloadWorkQueue(private val scope: CoroutineScope) {
    private val commands = Mutex()
    private var active: Job? = null
    private var generation = 0L

    fun replace(block: suspend () -> Unit): Job {
        val request = ++generation
        active?.cancel()
        return scope.launch {
            commands.withLock {
                active?.cancelAndJoin()
                if (request == generation) active = scope.launch { block() }
            }
        }
    }

    fun stop(afterStop: suspend () -> Unit = {}): Job {
        ++generation
        active?.cancel()
        return scope.launch {
            commands.withLock {
                active?.cancelAndJoin()
                active = null
                afterStop()
            }
        }
    }
}
