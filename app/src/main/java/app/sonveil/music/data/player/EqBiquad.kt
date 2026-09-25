package app.sonveil.music.data.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Direct-form I biquad. Coefficients follow the Audio EQ Cookbook. */
internal class EqBiquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun peaking(fs: Int, freq: Float, q: Float, gainDb: Float) {
        if (gainDb == 0f || freq <= 0f || freq >= fs / 2f) {
            bypass()
            return
        }
        val w = (2.0 * PI * freq / fs).toFloat()
        val sn = sin(w.toDouble()).toFloat()
        val cs = cos(w.toDouble()).toFloat()
        val alpha = sn / (2f * q.coerceAtLeast(0.05f))
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        assign(
            b0n = 1f + alpha * a,
            b1n = -2f * cs,
            b2n = 1f - alpha * a,
            a0n = 1f + alpha / a,
            a1n = -2f * cs,
            a2n = 1f - alpha / a,
        )
    }

    fun lowShelf(fs: Int, freq: Float, q: Float, gainDb: Float) {
        shelf(fs, freq, q, gainDb, high = false)
    }

    fun highShelf(fs: Int, freq: Float, q: Float, gainDb: Float) {
        shelf(fs, freq, q, gainDb, high = true)
    }

    private fun shelf(fs: Int, freq: Float, q: Float, gainDb: Float, high: Boolean) {
        if (gainDb == 0f || freq <= 0f || freq >= fs / 2f) {
            bypass()
            return
        }
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val w = (2.0 * PI * freq / fs).toFloat()
        val sn = sin(w.toDouble()).toFloat()
        val cs = cos(w.toDouble()).toFloat()
        val qSafe = q.coerceAtLeast(0.05f)
        val inside = (a + 1f / a) * (1f / qSafe - 1f) + 2f
        val alpha = sn / 2f * sqrt(inside.coerceAtLeast(0f))
        val twoSqrtAAlpha = 2f * sqrt(a) * alpha
        if (!high) {
            assign(
                b0n = a * ((a + 1f) - (a - 1f) * cs + twoSqrtAAlpha),
                b1n = 2f * a * ((a - 1f) - (a + 1f) * cs),
                b2n = a * ((a + 1f) - (a - 1f) * cs - twoSqrtAAlpha),
                a0n = (a + 1f) + (a - 1f) * cs + twoSqrtAAlpha,
                a1n = -2f * ((a - 1f) + (a + 1f) * cs),
                a2n = (a + 1f) + (a - 1f) * cs - twoSqrtAAlpha,
            )
        } else {
            assign(
                b0n = a * ((a + 1f) + (a - 1f) * cs + twoSqrtAAlpha),
                b1n = -2f * a * ((a - 1f) + (a + 1f) * cs),
                b2n = a * ((a + 1f) + (a - 1f) * cs - twoSqrtAAlpha),
                a0n = (a + 1f) - (a - 1f) * cs + twoSqrtAAlpha,
                a1n = 2f * ((a - 1f) - (a + 1f) * cs),
                a2n = (a + 1f) - (a - 1f) * cs - twoSqrtAAlpha,
            )
        }
    }

    private fun assign(b0n: Float, b1n: Float, b2n: Float, a0n: Float, a1n: Float, a2n: Float) {
        if (a0n == 0f) {
            bypass()
            return
        }
        b0 = b0n / a0n
        b1 = b1n / a0n
        b2 = b2n / a0n
        a1 = a1n / a0n
        a2 = a2n / a0n
    }

    private fun bypass() {
        b0 = 1f
        b1 = 0f
        b2 = 0f
        a1 = 0f
        a2 = 0f
    }

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0f
        z2 = 0f
    }
}
