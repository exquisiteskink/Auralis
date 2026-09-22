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
    val genres: GenresWrap? = null,
    val songsByGenre: SongsWrap? = null,
    val starred2: Starred2? = null,
    val lyricsList: LyricsList? = null,
    val lyrics: PlainLyrics? = null,
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
data class RecordLabel(
    val name: String = "",
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
    @Serializable(with = RecordLabelListSerializer::class)
    val recordLabels: List<RecordLabel> = emptyList(),
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
    @Serializable(with = RecordLabelListSerializer::class)
    val recordLabels: List<RecordLabel> = emptyList(),
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
data class Starred2(
    @Serializable(with = ArtistListSerializer::class)
    val artist: List<ArtistID3> = emptyList(),
    @Serializable(with = AlbumListSerializer::class)
    val album: List<AlbumID3> = emptyList(),
    @Serializable(with = SongListSerializer::class)
    val song: List<Song> = emptyList(),
)

@Serializable
data class GenresWrap(
    @Serializable(with = GenreListSerializer::class)
    val genre: List<Genre> = emptyList(),
)

@Serializable
data class Genre(
    val value: String = "",
    val songCount: Int = 0,
    val albumCount: Int = 0,
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
    val replayGain: ReplayGain? = null,
) {
    val isFavorite: Boolean get() = !starred.isNullOrBlank()

    val codecLabel: String?
        get() = suffix?.uppercase()?.ifBlank { null }

    val sampleRateLabel: String?
        get() = when {
            samplingRate >= 1000 -> "${samplingRate / 1000}.${(samplingRate % 1000) / 100} kHz"
                .replace(".0 kHz", " kHz")
            samplingRate > 0 -> "$samplingRate Hz"
            else -> null
        }

    val qualityLabel: String?
        get() {
            val depth = if (bitDepth > 0) "$bitDepth-bit" else null
            val br = if (bitRate > 0) "$bitRate kbps" else null
            val parts = listOfNotNull(codecLabel, depth, sampleRateLabel, br)
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
object GenreListSerializer : FlexListSerializer<Genre>(Genre.serializer())
object RecordLabelListSerializer : FlexListSerializer<RecordLabel>(RecordLabel.serializer())
object StructuredLyricsListSerializer : FlexListSerializer<StructuredLyrics>(StructuredLyrics.serializer())
object LyricLineListSerializer : FlexListSerializer<LyricLine>(LyricLine.serializer())

@Serializable
data class ReplayGain(
    val trackGain: Float = Float.NaN,
    val albumGain: Float = Float.NaN,
    val trackPeak: Float = Float.NaN,
    val albumPeak: Float = Float.NaN,
    val baseGain: Float = Float.NaN,
    val fallbackGain: Float = Float.NaN,
)

@Serializable
data class LyricsList(
    @Serializable(with = StructuredLyricsListSerializer::class)
    val structuredLyrics: List<StructuredLyrics> = emptyList(),
)

@Serializable
data class StructuredLyrics(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String? = null,
    val offset: Int = 0,
    val synced: Boolean = false,
    @Serializable(with = LyricLineListSerializer::class)
    val line: List<LyricLine> = emptyList(),
)

@Serializable
data class LyricLine(
    val start: Long = 0,
    val value: String = "",
)

@Serializable
data class PlainLyrics(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null,
)

data class SongLyrics(
    val synced: Boolean,
    val offsetMs: Int,
    val lines: List<LyricLine>,
)

fun parseLrcOrPlain(raw: String?): SongLyrics? {
    val text = raw?.trim().orEmpty()
    if (text.isBlank()) return null
    val lrc = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]\s*(.*)""")
    val lines = mutableListOf<LyricLine>()
    for (line in text.lineSequence()) {
        val m = lrc.find(line.trim()) ?: continue
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3]
        val ms = when {
            frac.isEmpty() -> 0L
            frac.length == 1 -> frac.toLong() * 100L
            frac.length == 2 -> frac.toLong() * 10L
            else -> frac.take(3).toLong()
        }
        val value = m.groupValues[4].trim()
        if (value.isNotEmpty()) lines += LyricLine(start = min * 60_000 + sec * 1000 + ms, value = value)
    }
    if (lines.isNotEmpty()) return SongLyrics(synced = true, offsetMs = 0, lines = lines.sortedBy { it.start })
    val plain = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("[") }
    if (plain.isEmpty()) return null
    return SongLyrics(synced = false, offsetMs = 0, lines = plain.map { LyricLine(value = it) })
}

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDurationMs(ms: Long): String = formatDuration((ms / 1000L).toInt().coerceAtLeast(0))
