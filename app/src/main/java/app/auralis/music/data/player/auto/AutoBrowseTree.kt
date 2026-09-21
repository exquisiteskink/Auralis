package app.auralis.music.data.player.auto

import androidx.media3.common.MediaItem
import app.auralis.music.AppContainer
import app.auralis.music.data.remote.Song

/**
 * Loads the four Auto browse roots using the same Subsonic calls as Home.
 * Empty lists and logged-out state return empty children (no throw).
 */
class AutoBrowseTree(
    private val container: AppContainer,
    private val items: AutoMediaItemFactory,
) {
    private val client get() = container.client

    suspend fun ensureCredentials(): Boolean {
        if (client.credentials != null) return true
        runCatching { container.restoreSession() }
        return client.credentials != null
    }

    fun rootItem(): MediaItem = items.root()

    fun rootChildren(): List<MediaItem> = listOf(
        items.playlistsRoot(),
        items.recentRoot(),
        items.favoritesRoot(),
        items.newestRoot(),
    )

    suspend fun childrenOf(parentId: String): List<MediaItem> {
        if (!ensureCredentials()) return emptyList()
        return runCatching {
            when (parentId) {
                AutoBrowseIds.ROOT -> rootChildren()
                AutoBrowseIds.PLAYLISTS -> client.getPlaylists().map { items.playlist(it) }
                AutoBrowseIds.RECENT -> client.getAlbumList2("recent", 48).map { items.album(it) }
                AutoBrowseIds.NEWEST -> client.getAlbumList2("newest", 48).map { items.album(it) }
                AutoBrowseIds.FAVORITES -> {
                    val songs = client.getStarredSongs()
                    items.playableSongs(songs, AutoBrowseIds.FAVORITES)
                }
                else -> {
                    AutoBrowseIds.parsePlaylistId(parentId)?.let { id ->
                        val songs = client.getPlaylist(id).entry
                        items.playableSongs(songs, parentId)
                    } ?: AutoBrowseIds.parseAlbumId(parentId)?.let { id ->
                        val songs = client.getAlbum(id).song
                        items.playableSongs(songs, parentId)
                    } ?: emptyList()
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Resolve a play request into a Song queue (same path phone UI uses) + start index.
     */
    suspend fun resolveQueue(
        requested: List<MediaItem>,
        startIndex: Int,
    ): ResolvedQueue {
        if (requested.isEmpty()) return ResolvedQueue(emptyList(), 0)
        if (!ensureCredentials()) return ResolvedQueue(emptyList(), 0)

        val focus = requested.getOrNull(startIndex.coerceIn(0, requested.lastIndex)) ?: requested.first()
        val mediaId = focus.mediaId

        return runCatching {
            when {
                mediaId == AutoBrowseIds.FAVORITES -> {
                    val songs = client.getStarredSongs()
                    ResolvedQueue(songs, 0)
                }
                AutoBrowseIds.parsePlaylistId(mediaId) != null -> {
                    val id = AutoBrowseIds.parsePlaylistId(mediaId)!!
                    ResolvedQueue(client.getPlaylist(id).entry, 0)
                }
                AutoBrowseIds.parseAlbumId(mediaId) != null -> {
                    val id = AutoBrowseIds.parseAlbumId(mediaId)!!
                    ResolvedQueue(client.getAlbum(id).song, 0)
                }
                AutoBrowseIds.parseSongId(mediaId) != null -> {
                    val songId = AutoBrowseIds.parseSongId(mediaId)!!
                    val parent = focus.mediaMetadata.extras?.getString(AutoBrowseIds.EXTRA_PARENT)
                        ?: requested.firstOrNull()?.mediaMetadata?.extras?.getString(AutoBrowseIds.EXTRA_PARENT)
                    resolveSongInParent(songId, parent, focus)
                }
                else -> {
                    // Unknown id — try treat as bare Subsonic song id (legacy / phone)
                    val bare = mediaId.takeIf { it.isNotBlank() && !it.contains('/') }
                    if (bare != null) {
                        ResolvedQueue(listOf(songFromMetadata(bare, focus)), 0)
                    } else {
                        ResolvedQueue(emptyList(), 0)
                    }
                }
            }
        }.getOrDefault(ResolvedQueue(emptyList(), 0))
    }

    private suspend fun resolveSongInParent(
        songId: String,
        parentId: String?,
        focus: MediaItem,
    ): ResolvedQueue {
        val siblings: List<Song> = when {
            parentId == AutoBrowseIds.FAVORITES -> client.getStarredSongs()
            parentId != null && AutoBrowseIds.parsePlaylistId(parentId) != null ->
                client.getPlaylist(AutoBrowseIds.parsePlaylistId(parentId)!!).entry
            parentId != null && AutoBrowseIds.parseAlbumId(parentId) != null ->
                client.getAlbum(AutoBrowseIds.parseAlbumId(parentId)!!).song
            else -> emptyList()
        }
        if (siblings.isNotEmpty()) {
            val idx = siblings.indexOfFirst { it.id == songId }.takeIf { it >= 0 } ?: 0
            return ResolvedQueue(siblings, idx)
        }
        return ResolvedQueue(listOf(songFromMetadata(songId, focus)), 0)
    }

    private fun songFromMetadata(id: String, item: MediaItem): Song {
        val md = item.mediaMetadata
        return Song(
            id = id,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString(),
            album = md.albumTitle?.toString(),
            coverArt = null,
            duration = 0,
        )
    }

    data class ResolvedQueue(
        val songs: List<Song>,
        val startIndex: Int,
    )
}
