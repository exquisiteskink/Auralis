package app.auralis.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun NowPlayingHost(
    sheet: MutableState<Float>,
    onArtist: (String) -> Unit,
    bottomNavVisible: Boolean,
) {
    val player = LocalPlayer.current
    val ui by player.state.collectAsState()
    val song = ui.current ?: return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navVisible = rememberUpdatedState(bottomNavVisible)

    var expand by remember { mutableFloatStateOf(sheet.value.coerceIn(0f, 1f)) }
    var queue by remember { mutableFloatStateOf(0f) }
    val expandAnim = remember { Animatable(expand) }
    val queueAnim = remember { Animatable(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(expand) { sheet.value = expand }

    fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
    }

    fun settleExpand(target: Float) {
        cancelSettle()
        val dest = target.coerceIn(0f, 1f)
        settleJob = scope.launch {
            expandAnim.snapTo(expand)
            expandAnim.animateTo(dest, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) {
                expand = value
            }
            expand = dest
            if (dest == 0f) {
                queue = 0f
                queueAnim.snapTo(0f)
            }
        }
    }

    fun settleQueue(target: Float) {
        cancelSettle()
        val dest = target.coerceIn(0f, 1f)
        settleJob = scope.launch {
            queueAnim.snapTo(queue)
            queueAnim.animateTo(dest, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) {
                queue = value
            }
            queue = dest
        }
    }

    BackHandler(enabled = expand > 0.2f) {
        if (queue > 0.4f) settleQueue(0f) else settleExpand(0f)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val fling = with(density) { 900.dp.toPx() }

        val overlayDrag = rememberDraggableState { delta ->
            cancelSettle()
            if (expand > 0.97f && (queue > 0.02f || delta < 0f)) {
                queue = (queue - delta / heightPx).coerceIn(0f, 1f)
            } else {
                expand = (expand - delta / heightPx).coerceIn(0f, 1f)
                if (expand < 0.98f) queue = 0f
            }
        }

        val miniDrag = rememberDraggableState { delta ->
            cancelSettle()
            expand = (expand - delta / heightPx).coerceIn(0f, 1f)
        }

        val queueHeaderDrag = rememberDraggableState { delta ->
            if (delta > 0f) {
                cancelSettle()
                queue = (queue - delta / heightPx).coerceIn(0f, 1f)
            }
        }

        val queueDismiss = remember(heightPx, fling) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y > 0f) {
                        cancelSettle()
                        queue = (queue - available.y / heightPx).coerceIn(0f, 1f)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (available.y > fling || queue < 0.72f) {
                        settleQueue(if (available.y > fling || queue < 0.55f) 0f else 1f)
                        return available
                    }
                    settleQueue(1f)
                    return Velocity.Zero
                }
            }
        }

        if (expand < 0.98f) {
            MiniBar(
                ui = ui,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (navVisible.value) 80.dp else 0.dp)
                    .navigationBarsPadding()
                    .draggable(
                        state = miniDrag,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val open = when {
                                velocity < -fling -> true
                                velocity > fling -> false
                                else -> expand >= 0.18f
                            }
                            settleExpand(if (open) 1f else 0f)
                        },
                    )
                    .clickable { settleExpand(1f) },
                onPlayPause = { player.playPause() },
            )
        }

        if (expand > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, ((1f - expand) * heightPx).toInt()) }
                    .draggable(
                        state = overlayDrag,
                        orientation = Orientation.Vertical,
                        enabled = queue < 0.35f,
                        onDragStopped = { velocity ->
                            if (queue > 0.03f) {
                                val open = when {
                                    velocity < -fling -> true
                                    velocity > fling -> false
                                    else -> queue >= 0.45f
                                }
                                settleQueue(if (open) 1f else 0f)
                            } else {
                                val open = when {
                                    velocity < -fling -> true
                                    velocity > fling -> false
                                    else -> expand >= 0.45f
                                }
                                settleExpand(if (open) 1f else 0f)
                            }
                        },
                    ),
            ) {
                UltraBlurBackground(Modifier.fillMaxSize())
                NowPlayingPage(
                    ui = ui,
                    onArtist = {
                        onArtist(it)
                        settleExpand(0f)
                    },
                    onClose = { settleExpand(0f) },
                    onOpenQueue = { settleQueue(1f) },
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, ((1f - queue) * heightPx).toInt()) }
                        .then(if (queue > 0.02f) Modifier.nestedScroll(queueDismiss) else Modifier),
                ) {
                    UltraBlurBackground(Modifier.fillMaxSize())
                    QueuePage(
                        ui = ui,
                        onClose = { settleQueue(0f) },
                        headerModifier = Modifier.draggable(
                            state = queueHeaderDrag,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                val close = velocity > fling || queue < 0.72f
                                settleQueue(if (close) 0f else 1f)
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingPage(
    ui: PlayerUiState,
    onArtist: (String) -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val song = ui.current ?: return

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
            CoverArt(
                coverId = song.coverArt,
                modifier = Modifier
                    .size(artSize)
                    .shadow(18.dp, RoundedCornerShape(12.dp)),
                contentDescription = song.title,
                corner = 12.dp,
            )

            Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatDurationMs(ui.positionMs),
                color = p.onBackground.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(40.dp),
            )
            WaveformSeekBar(
                positionMs = ui.positionMs,
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
                onToggleFavorite = { player.toggleFavorite(song) },
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
    onToggleFavorite: () -> Unit,
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
            Spacer(Modifier.width(16.dp))
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
            Spacer(Modifier.width(16.dp))
            Text(sampleRate, color = mute, fontSize = 13.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium)
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
private fun MiniBar(
    ui: PlayerUiState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
) {
    val p = LocalPalette.current
    val song = ui.current ?: return
    val progress = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
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
