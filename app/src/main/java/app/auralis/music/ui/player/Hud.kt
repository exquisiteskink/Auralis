package app.auralis.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.auralis.music.data.remote.Song
import app.auralis.music.data.waveform.WaveformRepository
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.UltraBlurBackground
import kotlinx.coroutines.CancellationException

@Composable
fun AuroraWash(modifier: Modifier = Modifier) {
    UltraBlurBackground(modifier)
}

/**
 * Expanded Now Playing seek control.
 * Shows a Plexamp-ish amplitude waveform when [peaks] are ready; otherwise a flat
 * scrubber (same hit target / height) so layout stays stable while decoding.
 * Mini player must keep its own flat bar — do not reuse this there.
 */
@Composable
fun WaveformSeekBar(
    positionMs: Long,
    durationMs: Long,
    peaks: FloatArray?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val dur = durationMs.coerceAtLeast(1L)
    var preview by remember { mutableFloatStateOf(-1f) }
    val shown = if (preview >= 0f) preview else (positionMs.toFloat() / dur).coerceIn(0f, 1f)
    val played = p.primary
    val rest = if (p.isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.22f)

    fun fractionAt(x: Float, width: Float): Float = (x / width).coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(dur) {
                detectTapGestures { offset ->
                    onSeek((fractionAt(offset.x, size.width.toFloat()) * dur).toLong())
                }
            }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        preview = fractionAt(offset.x, size.width.toFloat())
                    },
                    onHorizontalDrag = { change, _ ->
                        preview = fractionAt(change.position.x, size.width.toFloat())
                    },
                    onDragEnd = {
                        if (preview >= 0f) onSeek((preview * dur).toLong())
                        preview = -1f
                    },
                    onDragCancel = { preview = -1f },
                )
            },
    ) {
        val splitX = size.width * shown
        val samples = peaks
        if (samples == null || samples.isEmpty()) {
            // Flat fallback — thin track + progress, same 56.dp scrub area.
            val mid = size.height / 2f
            val trackH = 3.dp.toPx()
            drawLine(
                rest,
                Offset(0f, mid),
                Offset(size.width, mid),
                trackH,
                StrokeCap.Round,
            )
            if (shown > 0.001f) {
                drawLine(
                    played,
                    Offset(0f, mid),
                    Offset(splitX, mid),
                    trackH,
                    StrokeCap.Round,
                )
            }
            // Playhead
            drawCircle(played, radius = 5.dp.toPx(), center = Offset(splitX, mid))
            return@Canvas
        }

        val n = samples.size
        val gap = 1.5f
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val mid = size.height / 2f
        val maxAmp = size.height * 0.46f
        val minAmp = size.height * 0.04f
        val radius = CornerRadius(barW / 2f, barW / 2f)

        for (i in 0 until n) {
            val x = i * (barW + gap)
            val amp = samples[i].coerceIn(0.04f, 1f)
            val h = (minAmp + amp * (maxAmp - minAmp / 2f)).coerceAtMost(size.height)
            val top = mid - h / 2f
            val color = if (x + barW / 2f <= splitX) played else rest
            drawRoundRect(
                color = color,
                topLeft = Offset(x, top),
                size = Size(barW, h),
                cornerRadius = radius,
            )
        }
        // Progress playhead line
        if (shown > 0.002f) {
            drawLine(
                played.copy(alpha = 0.9f),
                Offset(splitX, mid - maxAmp),
                Offset(splitX, mid + maxAmp),
                2.dp.toPx(),
                StrokeCap.Round,
            )
        }
    }
}

/**
 * Loads peaks for [song] via [WaveformRepository]; cancels on track change.
 * Yields null while loading / on failure so [WaveformSeekBar] stays flat.
 */
@Composable
fun rememberWaveformPeaks(
    song: Song?,
    repository: WaveformRepository?,
): FloatArray? {
    var peaks by remember(song?.id) { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(song?.id, repository) {
        peaks = null
        val s = song ?: return@LaunchedEffect
        val repo = repository ?: return@LaunchedEffect
        peaks = try {
            repo.peaksFor(s)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    return peaks
}
