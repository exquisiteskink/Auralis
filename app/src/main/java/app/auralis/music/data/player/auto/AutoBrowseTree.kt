package app.auralis.music.data.player.auto

import androidx.media3.common.MediaItem
import app.auralis.music.AppContainer
import app.auralis.music.data.remote.AlbumID3
import app.auralis.music.data.remote.Playlist
import app.auralis.music.data.remote.Song

internal class AutoBrowseTree(
    private val container: AppContainer,
    private val items: AutoMediaItemFactory,
) {
    private val client get() = container.client
    private val resolver = AutoQueueResolver(::songsIn)

    private suspend fun ensureCredentials(): Boolean {
        if (client.credentials == null) container.restoreSession()
        return client.credentials != null
    }

    fun rootItem(): MediaItem = items.root()
    fun rootChildren(): List<MediaItem> = listOf(
        items.playlistsRoot(), items.recentRoot(), items.favoritesRoot(), items.newestRoot(),
    )

    suspend fun songsIn(parent: String): List<Song> = when {
        parent == AutoBrowseIds.FAVORITES -> client.getStarredSongs()
        AutoBrowseIds.parsePlaylistId(parent) != null -> client.getPlaylist(AutoBrowseIds.parsePlaylistId(parent)!!).entry
        AutoBrowseIds.parseAlbumId(parent) != null -> client.getAlbum(AutoBrowseIds.parseAlbumId(parent)!!).song
        else -> emptyList()
    }

    suspend fun childrenOf(parentId: String): List<MediaItem> {
        if (!ensureCredentials()) return emptyList()
        return when (parentId) {
            AutoBrowseIds.ROOT -> rootChildren()
            AutoBrowseIds.PLAYLISTS -> client.getPlaylists().map(items::playlist)
            AutoBrowseIds.RECENT -> client.getAlbumList2("recent", 48).map(items::album)
            AutoBrowseIds.NEWEST -> client.getAlbumList2("newest", 48).map(items::album)
            else -> items.playableSongs(songsIn(parentId), parentId)
        }
    }

    suspend fun item(mediaId: String): MediaItem? {
        rootChildren().firstOrNull { it.mediaId == mediaId }?.let { return it }
        if (mediaId == AutoBrowseIds.ROOT) return rootItem()
        if (!ensureCredentials()) return null
        AutoBrowseIds.parsePlaylistId(mediaId)?.let { id ->
            val pl = client.getPlaylist(id)
            return items.playlist(Playlist(id = pl.id, name = pl.name, songCount = pl.songCount, coverArt = pl.coverArt))
        }
        AutoBrowseIds.parseAlbumId(mediaId)?.let { id ->
            val album = client.getAlbum(id)
            return items.album(AlbumID3(id = album.id, name = album.displayName, artist = album.artist, coverArt = album.coverArt))
        }
        val resolved = resolver.resolve(mediaId) ?: return null
        return items.song(resolved.songs[resolved.startIndex], resolved.parent, resolved.startIndex)
    }

    suspend fun resolveQueue(requested: List<MediaItem>, startIndex: Int): AutoQueueResolver.Queue? {
        if (requested.isEmpty() || !ensureCredentials()) return null
        val focus = requested.getOrNull(if (startIndex < 0) 0 else startIndex) ?: return null
        return resolver.resolve(focus.mediaId)
    }
}
