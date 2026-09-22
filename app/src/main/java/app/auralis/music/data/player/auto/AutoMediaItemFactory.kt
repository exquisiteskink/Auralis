package app.auralis.music.data.player.auto

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.auralis.music.data.player.PlayerSettings
import app.auralis.music.data.remote.AlbumID3
import app.auralis.music.data.remote.Playlist
import app.auralis.music.data.remote.Song
import app.auralis.music.data.remote.SubsonicClient

/**
 * Builds browsable / playable MediaItems for Auto, mirroring [app.auralis.music.data.player.PlayerController]
 * stream + artwork extras so leaf playback reuses the same Subsonic stream URLs.
 */
class AutoMediaItemFactory(
    private val client: SubsonicClient,
    private val bitrate: () -> Int,
) {
    fun root(): MediaItem = folder(
        mediaId = AutoBrowseIds.ROOT,
        title = "Auralis",
        isPlayable = false,
    )

    fun playlistsRoot(): MediaItem = folder(
        mediaId = AutoBrowseIds.PLAYLISTS,
        title = "Playlists",
    )

    fun recentRoot(): MediaItem = folder(
        mediaId = AutoBrowseIds.RECENT,
        title = "Recently played",
    )

    fun favoritesRoot(): MediaItem = folder(
        mediaId = AutoBrowseIds.FAVORITES,
        title = "Favorites",
    )

    fun newestRoot(): MediaItem = folder(
        mediaId = AutoBrowseIds.NEWEST,
        title = "Recently added",
    )

    fun playlist(pl: Playlist): MediaItem = folder(
        mediaId = AutoBrowseIds.playlist(pl.id),
        title = pl.name.ifBlank { "Playlist" },
        subtitle = if (pl.songCount > 0) "${pl.songCount} songs" else null,
        artworkId = pl.coverArt,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        isPlayable = true,
    )

    fun album(album: AlbumID3): MediaItem = folder(
        mediaId = AutoBrowseIds.album(album.id),
        title = album.displayName,
        subtitle = album.artist,
        artworkId = album.coverArt,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
        isPlayable = true,
    )

    fun song(song: Song, parentId: String, index: Int): MediaItem {
        val art = client.coverUrl(song.coverArt, 800)
        val extras = Bundle().apply {
            putString("app_name", "Auralis")
            putString("com.android.music.musicsource", "Auralis")
            if (!parentId.isNullOrBlank()) putString(AutoBrowseIds.EXTRA_PARENT, parentId)
            song.replayGain?.trackGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_TRACK, it) }
            song.replayGain?.albumGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_ALBUM, it) }
            song.replayGain?.trackPeak?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_TRACK_PEAK, it) }
            song.replayGain?.albumPeak?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_ALBUM_PEAK, it) }
            song.replayGain?.fallbackGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_FALLBACK, it) }
        }
        return MediaItem.Builder()
            .setMediaId(AutoBrowseIds.song(song.id, parentId, index))
            .setUri(client.streamUrl(song.id, bitrate()))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title.ifBlank { "Track" })
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setSubtitle(song.artist)
                    .setDescription("Auralis")
                    .setWriter("Auralis")
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setArtworkUri(art?.let { Uri.parse(it) })
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    /** Playable MediaItem list matching phone [PlayerController] stream path. */
    fun playableSongs(songs: List<Song>, parentId: String): List<MediaItem> =
        songs.mapIndexed { index, song -> song(song, parentId, index) }

    private fun folder(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkId: String? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        isPlayable: Boolean = false,
    ): MediaItem {
        val art = client.coverUrl(artworkId, 400)
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription("Auralis")
                    .setWriter("Auralis")
                    .setMediaType(mediaType)
                    .setIsBrowsable(true)
                    .setIsPlayable(isPlayable)
                    .setArtworkUri(art?.let { Uri.parse(it) })
                    .build(),
            )
            .build()
    }
}
