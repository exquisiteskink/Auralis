package app.auralis.music.data.player

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.auralis.music.data.remote.Song
import app.auralis.music.data.remote.SubsonicClient
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val palette: AuralisPalette = AuralisPalette.darkDefault(),
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
) {
    val current: Song? get() = queue.getOrNull(index)
    val upNext: List<Song> get() = if (index + 1 < queue.size) queue.subList(index + 1, queue.size) else emptyList()
}

class PlayerController(
    private val context: Context,
    private val client: SubsonicClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var scrobbledId: String? = null
    private var preferDark: Boolean = true
    var transcodeBitrate: Int = 0

    fun setPreferDark(dark: Boolean) {
        preferDark = dark
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val c = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = c
                c.addListener(listener)
                syncFromPlayer()
                startPositionLoop()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun play(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        val c = controller ?: return
        c.setMediaItems(items, startIndex.coerceIn(0, songs.lastIndex), 0L)
        c.prepare()
        c.play()
        _state.update { it.copy(queue = songs, index = startIndex.coerceIn(0, songs.lastIndex)) }
        songs.getOrNull(startIndex)?.let { scope.launch { client.scrobble(it.id, submission = false) } }
        scrobbledId = null
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seek(ms: Long) {
        controller?.seekTo(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        val nextIndex = (_state.value.index + 1).coerceAtMost(_state.value.queue.size)
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        _state.update {
            val q = it.queue.toMutableList()
            q.add(nextIndex, song)
            it.copy(queue = q)
        }
    }

    fun addToQueue(song: Song) {
        controller?.addMediaItem(song.toMediaItem())
        _state.update { it.copy(queue = it.queue + song) }
    }

    fun playFromQueue(index: Int) {
        controller?.seekToDefaultPosition(index)
        controller?.play()
    }

    fun toggleRepeat() {
        val c = controller ?: return
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        _state.update { it.copy(repeatMode = next) }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        _state.update { it.copy(shuffle = c.shuffleModeEnabled) }
    }

    fun extractPalette(bitmap: Bitmap) {
        scope.launch(Dispatchers.Default) {
            val palette = PaletteExtractor.from(bitmap, preferDark)
            _state.update { it.copy(palette = palette) }
        }
    }

    fun resetPalette() {
        _state.update {
            it.copy(palette = if (preferDark) AuralisPalette.darkDefault() else AuralisPalette.lightDefault())
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromPlayer()
            scrobbledId = null
            val song = _state.value.current
            if (song != null) {
                scope.launch { client.scrobble(song.id, submission = false) }
                scope.launch { loadArtwork(song) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromPlayer()
        }
    }

    private fun syncFromPlayer() {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex.coerceAtLeast(0)
        _state.update {
            it.copy(
                index = if (it.queue.isEmpty()) 0 else idx.coerceIn(0, it.queue.lastIndex),
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: (it.current?.duration?.times(1000L) ?: 0L),
                repeatMode = c.repeatMode,
                shuffle = c.shuffleModeEnabled,
            )
        }
    }

    private fun startPositionLoop() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    val pos = c.currentPosition.coerceAtLeast(0L)
                    val dur = c.duration.takeIf { it > 0 } ?: (_state.value.current?.duration?.times(1000L) ?: 0L)
                    _state.update { it.copy(positionMs = pos, durationMs = dur, isPlaying = c.isPlaying) }
                    val song = _state.value.current
                    if (song != null && scrobbledId != song.id && dur > 0) {
                        val threshold = minOf(30_000L, dur / 2)
                        if (pos >= threshold) {
                            scrobbledId = song.id
                            launch { client.scrobble(song.id, submission = true) }
                        }
                    }
                }
                delay(400)
            }
        }
    }

    private suspend fun loadArtwork(song: Song) {
        val url = client.coverUrl(song.coverArt, 800) ?: return
        val req = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val result = context.imageLoader.execute(req)
        if (result is SuccessResult) {
            val bmp = (result.drawable as? BitmapDrawable)?.bitmap ?: return
            withContext(Dispatchers.Default) {
                val palette = PaletteExtractor.from(bmp, preferDark)
                _state.update { it.copy(palette = palette) }
            }
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val art = client.coverUrl(coverArt, 800)
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(client.streamUrl(id, transcodeBitrate))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(art?.let { android.net.Uri.parse(it) })
                    .build(),
            )
            .build()
    }
}
