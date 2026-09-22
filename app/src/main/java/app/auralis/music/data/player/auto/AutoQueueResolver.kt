package app.auralis.music.data.player.auto

import app.auralis.music.data.remote.Song

internal class AutoQueueResolver(private val loadSongs: suspend (String) -> List<Song>) {
    data class Queue(val songs: List<Song>, val startIndex: Int, val parent: String)

    suspend fun resolve(mediaId: String): Queue? {
        val ref = AutoBrowseIds.parseSong(mediaId)
        val parent = ref?.parent ?: mediaId
        if (parent != AutoBrowseIds.FAVORITES && AutoBrowseIds.parseAlbumId(parent) == null &&
            AutoBrowseIds.parsePlaylistId(parent) == null) return null
        val songs = loadSongs(parent)
        if (songs.isEmpty()) return null
        val index = if (ref == null) 0 else {
            // A changed playlist must never silently select a different song/occurrence.
            if (songs.getOrNull(ref.index)?.id != ref.songId) return null
            ref.index
        }
        return Queue(songs, index, parent)
    }
}
