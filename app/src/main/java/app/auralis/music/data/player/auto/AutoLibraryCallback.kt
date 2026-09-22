package app.auralis.music.data.player.auto

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import app.auralis.music.AppContainer
import app.auralis.music.data.player.PlayerController
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Media3 library callback: four Home-aligned browse roots + leaf play via
 * [PlayerController.adoptExternalQueue] + resolved stream MediaItems (same Subsonic stream URLs).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoLibraryCallback(
    private val container: AppContainer,
    private val playerController: PlayerController,
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val factory = AutoMediaItemFactory(container.client) { playerController.transcodeBitrate }
    private val tree = AutoBrowseTree(container, factory)

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val accepted = super.onConnect(session, controller)
        if (controller.uid == android.os.Process.myUid()) return accepted
        if (!controller.isTrusted) return MediaSession.ConnectionResult.reject()
        // Trusted clients may select catalog IDs; callbacks resolve them to our own URLs.
        return accepted
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(LibraryResult.ofItem(tree.rootItem(), params))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = futureResult {
        val all = tree.childrenOf(parentId)
        if (page < 0 || pageSize <= 0) return@futureResult LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
        val to = (from.toLong() + pageSize).coerceAtMost(all.size.toLong()).toInt()
        LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = futureResult {
        val item = tree.item(mediaId)
        if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        else LibraryResult.ofItem(item, null)
    }

    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (controller.uid == android.os.Process.myUid()) {
            // Preserve the entire phone queue, metadata, URI/bitrate and start position.
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
        }
        return futureValue {
            check(controller.isTrusted) { "Untrusted controller" }
            val resolved = tree.resolveQueue(mediaItems, startIndex)
                ?: throw IllegalArgumentException("Unknown or stale browse item")
            val playable = factory.playableSongs(resolved.songs, resolved.parent)
            playerController.adoptExternalQueue(resolved.songs, resolved.startIndex)
            MediaSession.MediaItemsWithStartPosition(playable, resolved.startIndex, startPositionMs)
        }
    }


    override fun onPlaybackResumption(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return futureValue {
            playerController.resumptionMediaItems()
                ?: throw UnsupportedOperationException("No persisted queue to resume")
        }
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> {
        if (controller.uid == android.os.Process.myUid()) return Futures.immediateFuture(mediaItems)
        // External selection uses onSetMediaItems. Arbitrary insertion has no index here
        // with which to keep the phone's Song queue synchronized.
        return Futures.immediateFailedFuture(UnsupportedOperationException("Select a browse item to replace the queue"))
    }

    fun release() {
        scope.coroutineContext.job.cancel()
    }

    private fun <T : Any> futureResult(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> {
        val future = SettableFuture.create<LibraryResult<T>>()
        val job = scope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.set(LibraryResult.ofError<T>(SessionError.ERROR_UNKNOWN))
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, { it.run() })
        return future
    }

    private fun <T> futureValue(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        val job = scope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, { it.run() })
        return future
    }
}
