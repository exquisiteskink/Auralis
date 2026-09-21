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
class AutoLibraryCallback(
    private val container: AppContainer,
    private val playerController: PlayerController,
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val factory = AutoMediaItemFactory(container.client)
    private val tree = AutoBrowseTree(container, factory)

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val accepted = super.onConnect(session, controller)
        if (controller.uid == android.os.Process.myUid()) return accepted
        if (!controller.isTrusted) return MediaSession.ConnectionResult.reject()
        return MediaSession.ConnectionResult.accept(
            accepted.availableSessionCommands,
            accepted.availablePlayerCommands.buildUpon()
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .remove(Player.COMMAND_SET_MEDIA_ITEM)
                .build(),
        )
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
        val from = (page * pageSize).coerceAtMost(all.size)
        val to = (from + pageSize).coerceAtMost(all.size)
        LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = futureResult {
        when (mediaId) {
            AutoBrowseIds.ROOT -> LibraryResult.ofItem(tree.rootItem(), null)
            AutoBrowseIds.PLAYLISTS -> LibraryResult.ofItem(factory.playlistsRoot(), null)
            AutoBrowseIds.RECENT -> LibraryResult.ofItem(factory.recentRoot(), null)
            AutoBrowseIds.FAVORITES -> LibraryResult.ofItem(factory.favoritesRoot(), null)
            AutoBrowseIds.NEWEST -> LibraryResult.ofItem(factory.newestRoot(), null)
            else -> {
                val kids = tree.childrenOf(parentIdForLookup(mediaId))
                val match = kids.firstOrNull { it.mediaId == mediaId }
                if (match != null) {
                    LibraryResult.ofItem(match, null)
                } else {
                    // Unknown id: empty error without throwing (Auto shows blank detail).
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                }
            }
        }
    }

    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = futureValue {
        val resolved = tree.resolveQueue(mediaItems, startIndex)
        if (resolved.songs.isEmpty()) {
            MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
        } else {
            val parentHint = mediaItems.getOrNull(startIndex.coerceIn(0, mediaItems.lastIndex))
                ?.mediaMetadata?.extras?.getString(AutoBrowseIds.EXTRA_PARENT)
                ?: mediaItems.firstOrNull()?.mediaId?.takeIf {
                    it == AutoBrowseIds.FAVORITES ||
                        AutoBrowseIds.parsePlaylistId(it) != null ||
                        AutoBrowseIds.parseAlbumId(it) != null
                }
            val playable = factory.playableSongs(resolved.songs, parentHint)
            val idx = resolved.startIndex.coerceIn(0, playable.lastIndex)
            // Keep phone UI queue in sync without going through MediaController (avoids re-entrancy).
            playerController.adoptExternalQueue(resolved.songs, idx)
            MediaSession.MediaItemsWithStartPosition(playable, idx, startPositionMs.coerceAtLeast(0L))
        }
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = futureValue {
        val resolved = tree.resolveQueue(mediaItems, 0)
        if (resolved.songs.isEmpty()) {
            mutableListOf()
        } else {
            factory.playableSongs(resolved.songs, null).toMutableList()
        }
    }

    fun release() {
        scope.coroutineContext.job.cancel()
    }

    private fun parentIdForLookup(mediaId: String): String = when {
        AutoBrowseIds.parsePlaylistId(mediaId) != null -> AutoBrowseIds.PLAYLISTS
        AutoBrowseIds.parseAlbumId(mediaId) != null -> AutoBrowseIds.RECENT
        AutoBrowseIds.parseSongId(mediaId) != null -> AutoBrowseIds.FAVORITES
        else -> AutoBrowseIds.ROOT
    }

    private fun <T> futureResult(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> {
        val future = SettableFuture.create<LibraryResult<T>>()
        scope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return future
    }

    private fun <T> futureValue(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
