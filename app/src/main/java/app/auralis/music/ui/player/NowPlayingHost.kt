package app.auralis.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.auralis.music.data.player.PlayerUiState
import app.auralis.music.data.remote.formatDurationMs
import app.auralis.music.ui.components.CoverArt
import app.auralis.music.ui.components.SongRow
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer
import app.auralis.music.ui.theme.UltraBlurBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class PlayerSheetValue { Collapsed, Player, Queue }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingHost(
    sheet: MutableFloatState,
    onArtist: (String) -> Unit,
    bottomNavVisible: Boolean,
) {
    val player = LocalPlayer.current
    val playerState = player.state.collectAsState()
    val ui by remember(playerState) {
        derivedStateOf { playerState.value.copy(positionMs = 0L) }
    }
    val position = remember(playerState) {
        derivedStateOf { playerState.value.positionMs }
    }
    if (ui.current == null) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var queueComposed by remember { mutableStateOf(false) }
    val sheetState = remember(density) {
        AnchoredDraggableState(
            initialValue = PlayerSheetValue.Collapsed,
            positionalThreshold = { distance -> distance * 0.45f },
            velocityThreshold = { with(density) { 900.dp.toPx() } },
            snapAnimationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
            decayAnimationSpec = splineBasedDecay(density),
            confirmValueChange = { target -> target != PlayerSheetValue.Queue || queueComposed },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val anchors = remember(heightPx) {
            DraggableAnchors {
                PlayerSheetValue.Collapsed at heightPx
                PlayerSheetValue.Player at 0f
                PlayerSheetValue.Queue at -heightPx
            }
        }
        SideEffect { sheetState.updateAnchors(anchors) }

        LaunchedEffect(sheetState, heightPx) {
            snapshotFlow { sheetState.offset }
                .collect { offset ->
                    if (offset.isFinite()) {
                        sheet.floatValue = (1f - offset / heightPx).coerceIn(0f, 1f)
                    }
                }
        }
        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.settledValue }
                .distinctUntilChanged()
                .collect { settled ->
                    queueComposed = settled != PlayerSheetValue.Collapsed
                }
        }

        val handlesBack by remember(sheetState, heightPx) {
            derivedStateOf { sheetState.offset.isFinite() && sheetState.offset < heightPx * 0.8f }
        }
        val playerDragEnabled by remember(sheetState, heightPx) {
            derivedStateOf { !sheetState.offset.isFinite() || sheetState.offset > -heightPx * 0.35f }
        }
        BackHandler(enabled = handlesBack) {
            val target = if (sheetState.offset < -heightPx * 0.35f) {
                PlayerSheetValue.Player
            } else {
                PlayerSheetValue.Collapsed
            }
            scope.launch { sheetState.animateTo(target) }
        }

        val queueDismiss = remember(sheetState) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y > 0f && sheetState.offset < 0f) {
                        val consumed = sheetState.dispatchRawDelta(available.y)
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (sheetState.offset < 0f) {
                        val consumedY = sheetState.settle(available.y)
                        return Velocity(0f, consumedY)
                    }
                    return Velocity.Zero
                }
            }
        }

        PositionAwareMiniBar(
            ui = ui,
            position = position,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (bottomNavVisible) 80.dp else 0.dp)
                .navigationBarsPadding()
                .anchoredDraggable(sheetState, Orientation.Vertical)
                .clickable { scope.launch { sheetState.animateTo(PlayerSheetValue.Player) } },
            onPlayPause = { player.playPause() },
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = sheetState.offset.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: heightPx
                }
                .anchoredDraggable(sheetState, Orientation.Vertical, enabled = playerDragEnabled),
        ) {
            UltraBlurBackground(Modifier.fillMaxSize())
            PositionAwareNowPlayingPage(
                ui = ui,
                position = position,
                onArtist = {
                    onArtist(it)
                    scope.launch { sheetState.animateTo(PlayerSheetValue.Collapsed) }
                },
                onClose = { scope.launch { sheetState.animateTo(PlayerSheetValue.Collapsed) } },
                onOpenQueue = {
                    queueComposed = true
                    scope.launch {
                        withFrameNanos { }
                        sheetState.animateTo(PlayerSheetValue.Queue)
                    }
                },
            )
            if (queueComposed) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val offset = sheetState.offset.takeIf { it.isFinite() } ?: 0f
                            translationY = heightPx + offset.coerceAtMost(0f)
                        }
                        .nestedScroll(queueDismiss),
                ) {
                    UltraBlurBackground(Modifier.fillMaxSize())
                    QueuePage(
                        ui = ui,
                        onClose = { scope.launch { sheetState.animateTo(PlayerSheetValue.Player) } },
                        headerModifier = Modifier.anchoredDraggable(sheetState, Orientation.Vertical),
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionAwareNowPlayingPage(
    ui: PlayerUiState,
    position: State<Long>,
    onArtist: (String) -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    NowPlayingPage(
        ui = ui,
        positionMs = position.value,
        onArtist = onArtist,
        onClose = onClose,
        onOpenQueue = onOpenQueue,
    )
}

@Composable
private fun NowPlayingPage(
    ui: PlayerUiState,
    positionMs: Long,
    onArtist: (String) -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val song = ui.current ?: return
    var showLyrics by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        val artSize = minOf(maxWidth * 0.78f, maxHeight * 0.34f)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            if (showLyrics) {
                LyricsPane(
                    lyrics = ui.lyrics,
                    positionMs = positionMs,
                    modifier = Modifier.size(artSize),
                    onClose = { showLyrics = false },
                )
            } else {
                CoverArt(
                    coverId = song.coverArt,
                    modifier = Modifier
                        .size(artSize)
                        .shadow(18.dp, RoundedCornerShape(12.dp))
                        .clickable { showLyrics = true },
                    contentDescription = song.title,
                    corner = 12.dp,
                )
            }

            Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatDurationMs(positionMs),
                color = p.onBackground.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(40.dp),
            )
            WaveformSeekBar(
                positionMs = positionMs,
                durationMs = ui.durationMs,
                seed = song.id,
                onSeek = { ms -> player.seek(ms) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(
                formatDurationMs(ui.durationMs),
                color = p.onBackground.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(40.dp),
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(22.dp))
        Text(
            song.artist.orEmpty(),
            color = p.onBackground.copy(alpha = 0.92f),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(enabled = song.artistId != null) {
                song.artistId?.let(onArtist)
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            song.title,
            color = p.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        song.album?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = p.onBackground.copy(alpha = 0.55f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

            Spacer(Modifier.height(16.dp))
            MetaRow(
                codec = song.codecLabel,
                sampleRate = song.sampleRateLabel,
                favorite = ui.isFavorite(song),
                lyricsOpen = showLyrics,
                onToggleFavorite = { player.toggleFavorite(song) },
                onToggleLyrics = { showLyrics = !showLyrics },
            )

            Spacer(Modifier.height(18.dp))
            ControlsDeck(ui)
            Spacer(Modifier.height(8.dp))
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = p.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.size(32.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clickable(onClick = onOpenQueue))
            Spacer(Modifier.height(4.dp))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetaRow(
    codec: String?,
    sampleRate: String?,
    favorite: Boolean,
    lyricsOpen: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleLyrics: () -> Unit,
) {
    val p = LocalPalette.current
    val mute = p.onBackground.copy(alpha = 0.45f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!codec.isNullOrBlank()) {
            Text(codec, color = mute, fontSize = 13.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
        }
        IconButton(onClick = onToggleLyrics, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Lyrics,
                contentDescription = if (lyricsOpen) "Hide lyrics" else "Show lyrics",
                tint = if (lyricsOpen) p.primary else mute,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                tint = if (favorite) p.primary else mute,
                modifier = Modifier.size(22.dp),
            )
        }
        if (!sampleRate.isNullOrBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(sampleRate, color = mute, fontSize = 13.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LyricsPane(
    lyrics: app.auralis.music.data.remote.SongLyrics?,
    positionMs: Long,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    val lines = lyrics?.lines.orEmpty()
    val t = positionMs - (lyrics?.offsetMs ?: 0)
    val active = if (lyrics?.synced == true && lines.isNotEmpty()) {
        lines.indexOfLast { it.start <= t }.coerceAtLeast(0)
    } else -1
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) runCatching { listState.animateScrollToItem(active.coerceAtLeast(0)) }
    }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface.copy(alpha = 0.55f))
            .clickable(onClick = onClose)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No lyrics on the server for this track",
                    color = p.onBackground.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lines) { i, line ->
                    val current = i == active
                    Text(
                        line.value,
                        color = p.onBackground.copy(alpha = if (current) 1f else 0.42f),
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (current) 18.sp else 15.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuePage(
    ui: PlayerUiState,
    onClose: () -> Unit,
    headerModifier: Modifier = Modifier,
) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val song = ui.current ?: return

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            headerModifier
                .fillMaxWidth()
                .clickable(onClick = onClose)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Back", tint = p.onBackground)
            }
            CoverArt(song.coverArt, Modifier.size(44.dp), song.title, corner = 4.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(song.artist.orEmpty(), color = p.onBackground.copy(alpha = 0.55f), maxLines = 1, fontSize = 12.sp)
            }
            IconButton(onClick = { player.playPause() }) {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Play",
                    tint = p.onBackground,
                )
            }
        }
        Text(
            "UP NEXT",
            color = p.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(ui.upNext, key = { i, s -> "${s.id}-$i" }) { i, s ->
                SongRow(
                    song = s,
                    onClick = { player.playFromQueue(ui.upNextIndices[i]) },
                    playing = false,
                    showCover = true,
                )
            }
            if (ui.upNext.isEmpty()) {
                item {
                    Text(
                        "Nothing else queued",
                        color = p.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionAwareMiniBar(
    ui: PlayerUiState,
    position: State<Long>,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
) {
    MiniBar(
        ui = ui,
        positionMs = position.value,
        modifier = modifier,
        onPlayPause = onPlayPause,
    )
}

@Composable
private fun MiniBar(
    ui: PlayerUiState,
    positionMs: Long,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
) {
    val p = LocalPalette.current
    val song = ui.current ?: return
    val progress = if (ui.durationMs > 0) (positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .fillMaxWidth()
            .background(p.surface),
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(p.onBackground.copy(alpha = 0.12f))) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(p.primary),
            )
        }
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(song.coverArt, Modifier.size(44.dp), song.title, corner = 2.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    ui.playbackError ?: song.artist.orEmpty(),
                    color = p.onBackground.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Play",
                    tint = p.onBackground,
                )
            }
        }
    }
}

@Composable
private fun ControlsDeck(ui: PlayerUiState) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = { player.toggleShuffle() }) {
            Icon(
                Icons.Rounded.Shuffle,
                "Shuffle",
                tint = if (ui.shuffle) p.onBackground else p.onBackground.copy(alpha = 0.32f),
            )
        }
        IconButton(onClick = { player.previous() }) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = p.onBackground, modifier = Modifier.size(36.dp))
        }
        Box(
            Modifier
                .size(72.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(p.playButton)
                .clickable { player.playPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (ui.isPlaying) "Pause" else "Play",
                tint = p.onPlayButton,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = { player.next() }) {
            Icon(Icons.Rounded.SkipNext, "Next", tint = p.onBackground, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { player.toggleRepeat() }) {
            Icon(
                if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat",
                tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) p.onBackground else p.onBackground.copy(alpha = 0.32f),
            )
        }
    }
}
