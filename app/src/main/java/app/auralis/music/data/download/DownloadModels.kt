package app.auralis.music.data.download

import kotlinx.serialization.Serializable

@Serializable
data class OfflineSongRecord(
    val songId: String,
    val relPath: String,
    val size: Long = 0L,
    val suffix: String? = null,
    val contentType: String? = null,
    val bitRate: Int = 0,
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val downloadedAtMs: Long = 0L,
    val song: app.auralis.music.data.remote.Song? = null,
)

@Serializable
data class OfflineIndex(
    val serverKey: String,
    val songs: Map<String, OfflineSongRecord> = emptyMap(),
)

enum class DownloadPhase {
    Idle,
    Running,
    PausedWifi,
    Failed,
    Done,
}

data class DownloadUiState(
    val phase: DownloadPhase = DownloadPhase.Idle,
    val collectionLabel: String? = null,
    val done: Int = 0,
    val total: Int = 0,
    val currentTitle: String? = null,
    val message: String? = null,
    val bytesUsed: Long = 0L,
)
