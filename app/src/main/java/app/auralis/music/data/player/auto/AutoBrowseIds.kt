package app.auralis.music.data.player.auto

/**
 * Stable Media3 mediaIds for Android Auto browse + play.
 * Format: type segments separated by `/` (no Subsonic ids contain `/`).
 */
object AutoBrowseIds {
    const val ROOT = "auralis_root"

    const val PLAYLISTS = "auralis_playlists"
    const val RECENT = "auralis_recent"
    const val FAVORITES = "auralis_favorites"
    const val NEWEST = "auralis_newest"

    const val EXTRA_PARENT = "auralis.parent_id"

    fun playlist(id: String) = "playlist/$id"
    fun album(id: String) = "album/$id"
    fun song(id: String) = "song/$id"

    fun parsePlaylistId(mediaId: String): String? =
        mediaId.takeIf { it.startsWith("playlist/") }?.removePrefix("playlist/")?.takeIf { it.isNotEmpty() }

    fun parseAlbumId(mediaId: String): String? =
        mediaId.takeIf { it.startsWith("album/") }?.removePrefix("album/")?.takeIf { it.isNotEmpty() }

    fun parseSongId(mediaId: String): String? =
        mediaId.takeIf { it.startsWith("song/") }?.removePrefix("song/")?.takeIf { it.isNotEmpty() }
}
