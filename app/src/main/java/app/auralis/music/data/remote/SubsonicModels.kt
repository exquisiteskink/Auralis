package app.auralis.music.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicRoot(
    @SerialName("subsonic-response") val response: SubsonicEnvelope,
)

@Serializable
data class SubsonicEnvelope(
    val status: String = "failed",
    val version: String = "",
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean = false,
    val error: SubsonicError? = null,
    val license: License? = null,
    val artists: ArtistsID3? = null,
    val artist: ArtistWithAlbums? = null,
    val album: AlbumWithSongs? = null,
    val albumList2: AlbumList2? = null,
    val playlists: Playlists? = null,
    val playlist: PlaylistWithSongs? = null,
    val searchResult3: SearchResult3? = null,
    val artistInfo2: ArtistInfo2? = null,
    val topSongs: SongsWrap? = null,
    @Serializable(with = OpenSubsonicExtensionListSerializer::class)
    val openSubsonicExtensions: List<OpenSubsonicExtension> = emptyList(),
)

@Serializable
data class SubsonicError(
    val code: Int = 0,
    val message: String? = null,
    val helpUrl: String? = null,
)

@Serializable
data class License(
    val valid: Boolean = true,
)

@Serializable
data class OpenSubsonicExtension(
    val name: String = "",
    val versions: List<Int> = emptyList(),
)

@Serializable
data class ArtistsID3(
    val ignoredArticles: String = "",
    @Serializable(with = ArtistIndexListSerializer::class)
    val index: List<ArtistIndex> = emptyList(),
)

@Serializable
data class ArtistIndex(
    val name: String = "",
    @Serializable(with = ArtistListSerializer::class)
    val artist: List<ArtistID3> = emptyList(),
)

@Serializable
data class ArtistID3(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val name: String,
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
)

@Serializable
data class ArtistWithAlbums(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val name: String,
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    @Serializable(with = AlbumListSerializer::class)
    val album: List<AlbumID3> = emptyList(),
)

@Serializable
data class AlbumID3(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val name: String = "",
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    @Serializable(with = FlexibleStringOrNullSerializer::class) val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val playCount: Int = 0,
    val created: String? = null,
    val year: Int = 0,
    val genre: String? = null,
    val starred: String? = null,
) {
    val displayName: String get() = name.ifBlank { title ?: album ?: "Album" }
}

@Serializable
data class AlbumWithSongs(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val name: String = "",
    val title: String? = null,
    val artist: String? = null,
    @Serializable(with = FlexibleStringOrNullSerializer::class) val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int = 0,
    val genre: String? = null,
    val starred: String? = null,
    @Serializable(with = SongListSerializer::class)
    val song: List<Song> = emptyList(),
) {
    val displayName: String get() = name.ifBlank { title ?: "Album" }
}

@Serializable
data class AlbumList2(
    @Serializable(with = AlbumListSerializer::class)
    val album: List<AlbumID3> = emptyList(),
)

@Serializable
data class Playlists(
    @Serializable(with = PlaylistListSerializer::class)
    val playlist: List<Playlist> = emptyList(),
)

@Serializable
data class Playlist(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val name: String = "",
    val comment: String? = null,
    val owner: String? = null,
    val public: Boolean = false,
    val songCount: Int = 0,
    val duration: Int = 0,
    val created: String? = null,
    val changed: String? = null,
    val coverArt: String? = null,
)

@Serializable
data class PlaylistWithSongs(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val name: String = "",
    val comment: String? = null,
    val owner: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    @Serializable(with = SongListSerializer::class)
    val entry: List<Song> = emptyList(),
)

@Serializable
data class SearchResult3(
    @Serializable(with = ArtistListSerializer::class)
    val artist: List<ArtistID3> = emptyList(),
    @Serializable(with = AlbumListSerializer::class)
    val album: List<AlbumID3> = emptyList(),
    @Serializable(with = SongListSerializer::class)
    val song: List<Song> = emptyList(),
)

@Serializable
data class ArtistInfo2(
    val biography: String? = null,
    val musicBrainzId: String? = null,
    val lastFmUrl: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
    @Serializable(with = SimilarArtistListSerializer::class)
    val similarArtist: List<SimilarArtist> = emptyList(),
)

@Serializable
data class SimilarArtist(
    @Serializable(with = FlexibleStringOrNullSerializer::class) val id: String? = null,
    val name: String = "",
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val albumCount: Int = 0,
)

@Serializable
data class SongsWrap(
    @Serializable(with = SongListSerializer::class)
    val song: List<Song> = emptyList(),
)

@Serializable
data class Song(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val title: String = "",
    val album: String? = null,
    val artist: String? = null,
    @Serializable(with = FlexibleStringOrNullSerializer::class) val albumId: String? = null,
    @Serializable(with = FlexibleStringOrNullSerializer::class) val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int = 0,
    val year: Int = 0,
    val genre: String? = null,
    val size: Long = 0,
    val suffix: String? = null,
    val contentType: String? = null,
    val bitRate: Int = 0,
    val samplingRate: Int = 0,
    val bitDepth: Int = 0,
    val channelCount: Int = 0,
    val discNumber: Int = 0,
    val playCount: Int = 0,
    val starred: String? = null,
) {
    val qualityLabel: String?
        get() {
            val fmt = suffix?.uppercase()?.ifBlank { null }
            val depth = if (bitDepth > 0) "$bitDepth-bit" else null
            val rate = when {
                samplingRate >= 1000 -> "${samplingRate / 1000}.${(samplingRate % 1000) / 100} kHz"
                    .replace(".0 kHz", " kHz")
                samplingRate > 0 -> "$samplingRate Hz"
                else -> null
            }
            val br = if (bitRate > 0) "$bitRate kbps" else null
            val parts = listOfNotNull(fmt, depth, rate, br)
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
}

object ArtistListSerializer : FlexListSerializer<ArtistID3>(ArtistID3.serializer())
object ArtistIndexListSerializer : FlexListSerializer<ArtistIndex>(ArtistIndex.serializer())
object AlbumListSerializer : FlexListSerializer<AlbumID3>(AlbumID3.serializer())
object SongListSerializer : FlexListSerializer<Song>(Song.serializer())
object PlaylistListSerializer : FlexListSerializer<Playlist>(Playlist.serializer())
object SimilarArtistListSerializer : FlexListSerializer<SimilarArtist>(SimilarArtist.serializer())
object OpenSubsonicExtensionListSerializer :
    FlexListSerializer<OpenSubsonicExtension>(OpenSubsonicExtension.serializer())

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDurationMs(ms: Long): String = formatDuration((ms / 1000L).toInt().coerceAtLeast(0))
