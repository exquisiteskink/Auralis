package app.sonveil.music.data.player.auto

import java.util.Base64

/** IDs carry parent and occurrence, so legacy ID-only requests retain duplicate tracks. */
object AutoBrowseIds {
    const val ROOT = "auralis_root"
    const val PLAYLISTS = "auralis_playlists"
    const val RECENT = "auralis_recent"
    const val FAVORITES = "auralis_favorites"
    const val NEWEST = "auralis_newest"
    const val EXTRA_PARENT = "auralis.parent_id"

    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decode(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8).takeIf { encode(it) == value }
    }.getOrNull()

    fun playlist(id: String) = "playlist/${encode(id)}"
    fun album(id: String) = "album/${encode(id)}"
    fun song(id: String, parent: String, index: Int) = "song/${encode(parent)}/$index/${encode(id)}"
    fun parsePlaylistId(mediaId: String): String? = parseFolder(mediaId, "playlist/")
    fun parseAlbumId(mediaId: String): String? = parseFolder(mediaId, "album/")
    private fun parseFolder(id: String, prefix: String): String? =
        id.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.let(::decode)?.takeIf { it.isNotEmpty() }

    data class SongRef(val songId: String, val parent: String, val index: Int)
    fun parseSong(mediaId: String): SongRef? {
        val parts = mediaId.split('/')
        if (parts.size != 4 || parts[0] != "song") return null
        val parent = decode(parts[1]) ?: return null
        if (parent != FAVORITES && parsePlaylistId(parent) == null && parseAlbumId(parent) == null) return null
        val index = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val song = decode(parts[3])?.takeIf { it.isNotEmpty() } ?: return null
        return SongRef(song, parent, index)
    }
}
