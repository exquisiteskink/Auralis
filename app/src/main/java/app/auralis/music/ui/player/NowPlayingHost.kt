package app.auralis.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import app.auralis.music.data.player.PlayerUiState
import app.auralis.music.data.remote.formatDurationMs
import app.auralis.music.ui.components.CoverArt
import app.auralis.music.ui.components.SongRow
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerSheet { Mini, Expanded, Queue }

@Composable
fun NowPlayingHost(
    sheet: androidx.compose.runtime.MutableState<Float>,
    onArtist: (String) -> Unit,
    bottomNavVisible: Boolean,
) {
    val player = LocalPlayer.current
    val ui by player.state.collectAsState()
    val song = ui.current ?: return
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val progress = remember { Animatable(sheet.value) }
    val updatedNav = rememberUpdatedState(bottomNavVisible)

    LaunchedEffect(sheet.value) {
        if (abs(progress.value - sheet.value) > 0.01f) {
            progress.snapTo(sheet.value)
        }
    }

    fun setProgress(value: Float) {
        sheet.value = value
    }

    suspend fun snapTo(target: Float) {
        val dest = target.coerceIn(0f, 2f)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        progress.animateTo(
            dest,
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        )
        setProgress(dest)
    }

    val expand = progress.value.coerceIn(0f, 1f)
    val queueT = (progress.value - 1f).coerceIn(0f, 1f)
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navReserve = if (updatedNav.value) 80.dp else 0.dp
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullH = maxHeight
        val miniH = 64.dp
        val height = androidx.compose.ui.unit.lerp(miniH, fullH, expand)
        val bottomPad = androidx.compose.ui.unit.lerp(navReserve + navInset, 0.dp, expand)
        val radius = androidx.compose.ui.unit.lerp(18.dp, 0.dp, expand)
        val pxPerUnit = with(density) { (fullH - miniH).toPx() }.coerceAtLeast(1f)

        Box(
            Modifier
                .fillMaxSize()
                .alpha(expand * 0.55f)
                .background(p.scrim)
                .then(if (expand > 0.05f) Modifier.clickable { scope.launch { snapTo(0f) } } else Modifier),
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPad, start = androidx.compose.ui.unit.lerp(10.dp, 0.dp, expand), end = androidx.compose.ui.unit.lerp(10.dp, 0.dp, expand))
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    shadowElevation = lerp(8f, 0f, expand)
                    shape = RoundedCornerShape(radius)
                    clip = true
                }
                .clip(RoundedCornerShape(radius))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            p.gradientTop.copy(alpha = lerp(0.92f, 1f, expand)),
                            p.background,
                        ),
                    ),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = lerp(0.14f, 0.04f, expand)),
                    RoundedCornerShape(radius),
                )
                .then(
                    if (queueT < 0.55f) {
                        Modifier.pointerInput(pxPerUnit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    val delta = -dragAmount / pxPerUnit
                                    val next = (progress.value + delta).coerceIn(0f, 2f)
                                    scope.launch { progress.snapTo(next) }
                                },
                                onDragEnd = {
                                    val v = progress.value
                                    val target = when {
                                        v < 0.45f -> 0f
                                        v < 1.45f -> 1f
                                        else -> 2f
                                    }
                                    scope.launch { snapTo(target) }
                                },
                                onDragCancel = {
                                    val nearest = listOf(0f, 1f, 2f).minBy { abs(it - progress.value) }
                                    scope.launch { snapTo(nearest) }
                                },
                            )
                        }
                    } else Modifier
                )
                .clickable(enabled = progress.value < 0.2f) { scope.launch { snapTo(1f) } },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (expand > 0.2f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = androidx.compose.ui.unit.lerp(0.dp, statusInset, expand))
                            .alpha(expand)
                            .pointerInput(pxPerUnit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        val delta = -dragAmount / pxPerUnit
                                        val next = (progress.value + delta).coerceIn(0f, 2f)
                                        scope.launch { progress.snapTo(next) }
                                    },
                                    onDragEnd = {
                                        val v = progress.value
                                        val target = when {
                                            v < 0.45f -> 0f
                                            v < 1.45f -> 1f
                                            else -> 2f
                                        }
                                        scope.launch { snapTo(target) }
                                    },
                                    onDragCancel = {
                                        val nearest = listOf(0f, 1f, 2f).minBy { abs(it - progress.value) }
                                        scope.launch { snapTo(nearest) }
                                    },
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = { scope.launch { snapTo(if (queueT > 0.5f) 1f else 0f) } }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = p.onBackground)
                        }
                        Text(
                            if (queueT > 0.4f) "Up next" else "Now playing",
                            color = p.onBackground.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                        IconButton(onClick = { scope.launch { snapTo(if (queueT > 0.5f) 1f else 2f) } }) {
                            Icon(Icons.Rounded.ExpandLess, "Queue", tint = p.onBackground.copy(alpha = 0.8f))
                        }
                    }
                }

                val art = androidx.compose.ui.unit.lerp(
                    androidx.compose.ui.unit.lerp(52.dp, 300.dp, expand),
                    64.dp,
                    queueT,
                )
                val rowish = expand < 0.55f && queueT == 0f

                if (rowish) {
                    MiniRow(ui = ui, onPlayPause = { player.playPause() }, onNext = { player.next() })
                } else {
                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = true),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CoverArt(
                            coverId = song.coverArt,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(art)
                                .graphicsLayer {
                                    shadowElevation = lerp(0f, 24f, expand * (1f - queueT))
                                    rotationZ = lerp(0f, 0.2f, expand)
                                },
                            contentDescription = song.title,
                            corner = androidx.compose.ui.unit.lerp(10.dp, 22.dp, expand),
                        )
                        Spacer(Modifier.height(androidx.compose.ui.unit.lerp(8.dp, 28.dp, expand * (1f - queueT))))
                        Text(
                            song.title,
                            color = p.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = lerp(14f, 24f, expand).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 28.dp)
                                .clickable(enabled = song.artistId != null) {
                                    song.artistId?.let(onArtist)
                                    scope.launch { snapTo(0f) }
                                },
                        )
                        Text(
                            song.artist.orEmpty(),
                            color = p.onBackground.copy(alpha = 0.6f),
                            fontSize = 15.sp,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable(enabled = song.artistId != null) {
                                    song.artistId?.let(onArtist)
                                    scope.launch { snapTo(0f) }
                                },
                        )
                        song.qualityLabel?.let { q ->
                            Text(
                                q,
                                color = p.primary.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        if (queueT < 0.5f) {
                            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).alpha(1f - queueT * 2f)) {
                                val dur = ui.durationMs.coerceAtLeast(1L)
                                Slider(
                                    value = (ui.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                                    onValueChange = { player.seek((it * dur).toLong()) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = p.primary,
                                        activeTrackColor = p.primary,
                                        inactiveTrackColor = p.onBackground.copy(alpha = 0.15f),
                                    ),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatDurationMs(ui.positionMs), color = p.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                                    Text(formatDurationMs(ui.durationMs), color = p.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                            }
                            ControlsRow(ui)
                            Text(
                                "Swipe up for the queue",
                                color = p.onBackground.copy(alpha = 0.35f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            ControlsRow(ui)
                            LazyColumn(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .alpha(queueT),
                            ) {
                                item {
                                    Text(
                                        "Up next",
                                        color = p.onBackground,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                    )
                                }
                                itemsIndexed(ui.upNext, key = { i, s -> "${s.id}-$i" }) { i, s ->
                                    SongRow(
                                        song = s,
                                        onClick = { player.playFromQueue(ui.index + 1 + i) },
                                        playing = false,
                                    )
                                }
                                if (ui.upNext.isEmpty()) {
                                    item {
                                        Text(
                                            "Nothing else in the queue",
                                            color = p.onBackground.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniRow(
    ui: PlayerUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val p = LocalPalette.current
    val song = ui.current ?: return
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(song.coverArt, Modifier.size(48.dp), song.title, corner = 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(song.artist.orEmpty(), color = p.onBackground.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (ui.isPlaying) "Pause" else "Play",
                tint = p.onBackground,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = p.onBackground)
        }
    }
}

@Composable
private fun ControlsRow(ui: PlayerUiState) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = { player.toggleShuffle() }) {
            Icon(
                Icons.Rounded.Shuffle,
                "Shuffle",
                tint = if (ui.shuffle) p.primary else p.onBackground.copy(alpha = 0.55f),
            )
        }
        IconButton(onClick = { player.previous() }) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = p.onBackground, modifier = Modifier.size(36.dp))
        }
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(p.primary)
                .clickable { player.playPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (ui.isPlaying) "Pause" else "Play",
                tint = p.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = { player.next() }) {
            Icon(Icons.Rounded.SkipNext, "Next", tint = p.onBackground, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { player.toggleRepeat() }) {
            val icon = if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
            Icon(
                icon,
                "Repeat",
                tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) p.primary else p.onBackground.copy(alpha = 0.55f),
            )
        }
    }
}
