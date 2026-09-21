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
import app.auralis.music.data.remote.SongLyrics
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.CancellationException
import com.google.common.util.concurrent.ListenableFuture

data class PlayerUiState(
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val palette: AuralisPalette = AuralisPalette.darkDefault(),
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val favoriteById: Map<String, Boolean> = emptyMap(),
    val favoriteEpoch: Int = 0,
    val upcomingIndices: List<Int>? = null,
    val playbackError: String? = null,
    val lyrics: SongLyrics? = null,
) {
    val current: Song? get() = queue.getOrNull(index)
    val upNextIndices: List<Int> get() = upcomingIndices ?: ((index + 1) until queue.size).toList()
    val upNext: List<Song> get() = upNextIndices.mapNotNull(queue::getOrNull)
    fun isFavorite(song: Song): Boolean = favoriteById[song.id] ?: song.isFavorite
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val client: SubsonicClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var controller: MediaController? = null
    private var connection: ListenableFuture<MediaController>? = null
    private var artworkJob: Job? = null
    private val favoriteInFlight = mutableSetOf<String>()
    private var positionJob: Job? = null
    private var scrobbledId: String? = null
    private var listenedMs: Long = 0
    private var preferDark: Boolean = true
    private data class PendingQueue(val songs: List<Song>, val index: Int, val autoPlay: Boolean)
    private var pendingPlay: PendingQueue? = null
    var transcodeBitrate: Int = 0

    fun setPreferDark(dark: Boolean) {
        val changed = preferDark != dark
        preferDark = dark
        if (changed) {
            _state.value.current?.let { refreshArtwork(it) } ?: resetPalette()
        }
    }

    fun connect() {
        if (controller != null || connection != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(disconnected: MediaController) {
                    if (controller === disconnected) {
                        controller = null
                        positionJob?.cancel()
                        _state.update { it.copy(isPlaying = false) }
                    }
                }
            }).buildAsync()
        connection = future
        future.addListener(
            {
                if (connection !== future) return@addListener
                connection = null
                val c = runCatching { future.get() }.getOrNull() ?: run {
                    _state.update { it.copy(isPlaying = false) }
                    return@addListener
                }
                controller = c
                c.addListener(listener)
                syncFromPlayer()
                startPositionLoop()
                pendingPlay?.let { (songs, idx, autoPlay) ->
                    pendingPlay = null
                    setQueue(songs, idx, autoPlay)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun play(songs: List<Song>, startIndex: Int = 0) {
        setQueue(songs, startIndex, autoPlay = true)
    }

    private fun setQueue(songs: List<Song>, startIndex: Int, autoPlay: Boolean) {
        if (songs.isEmpty()) return
        if (client.credentials == null) return
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        val c = controller
        if (c == null) {
            pendingPlay = PendingQueue(songs.toList(), idx, autoPlay)
            _state.update { it.copy(queue = songs.toList(), index = idx, isPlaying = false) }
            connect()
            return
        }
        val items = songs.map { it.toMediaItem() }
        // MediaController may notify listeners synchronously from setMediaItems.
        _state.update { it.copy(queue = songs.toList(), index = idx, positionMs = 0, durationMs = 0, upcomingIndices = null, playbackError = null) }
        scrobbledId = null
        c.playWhenReady = autoPlay
        c.setMediaItems(items, idx, 0L)
        c.prepare()
        syncFromPlayer()
    }

    fun applyTranscode(bps: Int) {
        if (transcodeBitrate == bps) return
        transcodeBitrate = bps
        val c = controller ?: return
        val st = _state.value
        if (st.queue.isEmpty()) return
        val pos = c.currentPosition.coerceAtLeast(0L)
        val idx = c.currentMediaItemIndex.coerceAtLeast(0)
        val playing = c.playWhenReady
        c.setMediaItems(st.queue.map { it.toMediaItem() }, idx.coerceIn(0, st.queue.lastIndex), pos)
        c.prepare()
        if (playing) c.play()
    }

    fun stopAndReset() {
        scope.coroutineContext.cancelChildren()
        artworkJob = null
        favoriteInFlight.clear()
        connection?.let { MediaController.releaseFuture(it) }
        connection = null
        pendingPlay = null
        scrobbledId = null
        runCatching {
            controller?.removeListener(listener)
            controller?.stop()
            controller?.clearMediaItems()
            controller?.release()
        }
        controller = null
        _state.value = PlayerUiState(
            palette = if (preferDark) AuralisPalette.darkDefault() else AuralisPalette.lightDefault(),
        )
    }

    fun playPause() {
        val c = controller ?: run {
            play(_state.value.queue, _state.value.index)
            return
        }
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seek(ms: Long) {
        val bounded = ms.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        controller?.seekTo(bounded)
        _state.update { it.copy(positionMs = bounded) }
    }

    fun playNext(song: Song) {
        val c = controller
        if (c == null) {
            val pending = pendingPlay
            val q = _state.value.queue.toMutableList()
            q.add((_state.value.index + 1).coerceAtMost(q.size), song)
            setQueue(q, pending?.index ?: 0, pending?.autoPlay ?: false)
            return
        }
        val insertAt = (_state.value.index + 1).coerceAtMost(_state.value.queue.size)
        val item = song.toMediaItem()
        _state.update {
            val q = it.queue.toMutableList()
            q.add(insertAt, song)
            it.copy(queue = q)
        }
        c.addMediaItem(insertAt, item)
    }

    fun addToQueue(song: Song) {
        val c = controller
        if (c == null) {
            val pending = pendingPlay
            setQueue(_state.value.queue + song, pending?.index ?: 0, pending?.autoPlay ?: false)
            return
        }
        val item = song.toMediaItem()
        _state.update { it.copy(queue = it.queue + song) }
        c.addMediaItem(item)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
    }

    fun playFromQueue(index: Int) {
        if (index !in _state.value.queue.indices) return
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

    fun toggleFavorite(song: Song? = _state.value.current) {
        val target = song ?: return
        setFavorite(target, !_state.value.isFavorite(target))
    }

    fun setFavorite(song: Song, favorite: Boolean) {
        val currentlyFav = _state.value.isFavorite(song)
        if (currentlyFav == favorite) return
        if (!favoriteInFlight.add(song.id)) return
        val previous = _state.value.favoriteById[song.id]
        val previousStarred = _state.value.queue.firstOrNull { it.id == song.id }?.starred ?: song.starred
        val stamped = if (favorite) java.time.Instant.now().toString() else null
        _state.update { st ->
            st.copy(
                favoriteById = st.favoriteById + (song.id to favorite),
                queue = st.queue.map { if (it.id == song.id) it.copy(starred = stamped) else it },
            )
        }
        scope.launch {
            try {
                if (favorite) client.starSong(song.id) else client.unstarSong(song.id)
                _state.update { it.copy(favoriteEpoch = it.favoriteEpoch + 1) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { st ->
                    val reverted = st.favoriteById.toMutableMap()
                    if (previous == null) reverted.remove(song.id) else reverted[song.id] = previous
                    st.copy(
                        favoriteById = reverted,
                        queue = st.queue.map {
                            if (it.id == song.id) it.copy(starred = previousStarred) else it
                        },
                    )
                }
            } finally {
                favoriteInFlight.remove(song.id)
            }
        }
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

    /**
     * Adopt a queue set by Android Auto / MediaLibrarySession without calling
     * MediaController.setMediaItems (session already applies the playable items).
     */
    fun adoptExternalQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        scrobbledId = null
        listenedMs = 0
        _state.update {
            it.copy(
                queue = songs.toList(),
                index = idx,
                positionMs = 0,
                durationMs = songs.getOrNull(idx)?.duration?.times(1000L) ?: 0L,
                upcomingIndices = null,
                playbackError = null,
            )
        }
        if (controller == null) connect()
        else {
            syncFromPlayer()
            songs.getOrNull(idx)?.let {
                refreshArtwork(it)
                refreshLyrics(it)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.update { it.copy(playbackError = "Playback failed: ${error.errorCodeName}", isPlaying = false) }
        }
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromPlayer()
            scrobbledId = null
            listenedMs = 0
            val song = _state.value.current
            if (song != null) {
                scope.launch { client.scrobble(song.id, submission = false) }
                refreshArtwork(song)
                refreshLyrics(song)
            } else {
                _state.update { it.copy(lyrics = null) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) _state.update { it.copy(playbackError = null) }
            syncFromPlayer()
        }
    }

    private fun syncFromPlayer() {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex.coerceAtLeast(0)
        val timeline = c.currentTimeline
        val upcoming = mutableListOf<Int>()
        if (!timeline.isEmpty && idx < timeline.windowCount) {
            var next = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            while (next != androidx.media3.common.C.INDEX_UNSET && upcoming.size < timeline.windowCount) {
                upcoming.add(next)
                next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            }
        }
        _state.update {
            it.copy(
                index = if (it.queue.isEmpty()) 0 else idx.coerceIn(0, it.queue.lastIndex),
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: (it.queue.getOrNull(idx)?.duration?.times(1000L) ?: 0L),
                repeatMode = c.repeatMode,
                shuffle = c.shuffleModeEnabled,
                upcomingIndices = upcoming,
            )
        }
    }

    private fun startPositionLoop() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var lastTick = android.os.SystemClock.elapsedRealtime()
            var wasPlaying = false
            while (isActive) {
                val tick = android.os.SystemClock.elapsedRealtime()
                val elapsed = (tick - lastTick).coerceIn(0, 2000)
                lastTick = tick
                val c = controller
                if (c != null) {
                    if (wasPlaying && c.isPlaying) listenedMs += elapsed
                    wasPlaying = c.isPlaying
                    val pos = c.currentPosition.coerceAtLeast(0L)
                    val dur = c.duration.takeIf { it > 0 } ?: (_state.value.current?.duration?.times(1000L) ?: 0L)
                    _state.update { it.copy(positionMs = pos, durationMs = dur, isPlaying = c.isPlaying) }
                    val song = _state.value.current
                    if (song != null && scrobbledId != song.id && dur > 0) {
                        val threshold = minOf(30_000L, dur / 2)
                        // Seeking forward (especially while paused) is not listening.
                        if (listenedMs >= threshold) {
                            scrobbledId = song.id
                            launch { client.scrobble(song.id, submission = true) }
                        }
                    }
                }
                delay(400)
            }
        }
    }

    private fun refreshLyrics(song: Song) {
        _state.update { it.copy(lyrics = null) }
        scope.launch {
            val lyrics = runCatching { client.lyricsForSong(song) }.getOrNull()
            if (_state.value.current?.id == song.id) _state.update { it.copy(lyrics = lyrics) }
        }
    }

    private fun refreshArtwork(song: Song) {
        artworkJob?.cancel()
        resetPalette()
        artworkJob = scope.launch {
            try {
                loadArtwork(song)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Missing or malformed artwork must never crash audio playback.
            }
        }
    }

    private suspend fun loadArtwork(song: Song) {
        val url = client.coverUrl(song.coverArt, 800) ?: return
        val req = ImageRequest.Builder(context).data(url).size(800).allowHardware(false).build()
        val result = context.imageLoader.execute(req)
        if (result is SuccessResult) {
            val bmp = (result.drawable as? BitmapDrawable)?.bitmap ?: return
            val palette = withContext(Dispatchers.Default) { PaletteExtractor.from(bmp, preferDark) }
            if (_state.value.current?.id == song.id) _state.update { it.copy(palette = palette) }
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val art = client.coverUrl(coverArt, 800)
        val extras = android.os.Bundle().apply {
            putString("app_name", "Auralis")
            putString("com.android.music.musicsource", "Auralis")
            replayGain?.trackGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_TRACK, it) }
            replayGain?.albumGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_ALBUM, it) }
            replayGain?.trackPeak?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_TRACK_PEAK, it) }
            replayGain?.albumPeak?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_ALBUM_PEAK, it) }
            replayGain?.fallbackGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_FALLBACK, it) }
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(client.streamUrl(id, transcodeBitrate))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setSubtitle(artist)
                    .setDescription("Auralis")
                    .setWriter("Auralis")
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(art?.let { android.net.Uri.parse(it) })
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }
}
