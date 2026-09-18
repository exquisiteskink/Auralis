package app.auralis.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.UltraBlurBackground
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AuroraWash(modifier: Modifier = Modifier) {
    UltraBlurBackground(modifier)
}

@Composable
fun WaveformSeekBar(
    positionMs: Long,
    durationMs: Long,
    seed: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val dur = durationMs.coerceAtLeast(1L)
    var preview by remember { mutableFloatStateOf(-1f) }
    val shown = if (preview >= 0f) preview else (positionMs.toFloat() / dur).coerceIn(0f, 1f)
    val samples = remember(seed) { waveformSamples(seed) }
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
        val n = samples.size
        val step = size.width / (n - 1).coerceAtLeast(1)
        val mid = size.height / 2f
        val amp = size.height * 0.46f
        val splitX = size.width * shown

        fun envelope(color: Color, fromX: Float, toX: Float) {
            if (toX <= fromX) return
            val path = Path()
            var started = false
            for (i in 0 until n) {
                val x = i * step
                if (x < fromX) continue
                if (x > toX) break
                val y = mid - samples[i] * amp
                if (!started) {
                    path.moveTo(x.coerceAtLeast(fromX), y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            for (i in n - 1 downTo 0) {
                val x = i * step
                if (x > toX) continue
                if (x < fromX) break
                val y = mid + samples[i] * amp
                path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color, style = Fill)
        }

        envelope(rest, 0f, size.width)
        envelope(played, 0f, splitX)
        if (shown > 0.004f) {
            drawLine(
                played,
                Offset(0f, mid),
                Offset(splitX, mid),
                1.6f,
                StrokeCap.Round,
            )
        }
    }
}

private fun waveformSamples(seed: String, count: Int = 128): FloatArray {
    val rnd = Random(seed.hashCode().toLong())
    return FloatArray(count) { i ->
        val t = i / (count - 1f)
        val envelope = (0.22f + 0.78f * sin(t * PI).toFloat()).coerceIn(0.18f, 1f)
        val bump = 0.55f + 0.45f * sin(t * 7.0 * PI + rnd.nextDouble() * 0.4).toFloat()
        val noise = 0.35f + rnd.nextFloat() * 0.65f
        (envelope * bump * noise).coerceIn(0.10f, 1f)
    }
}
