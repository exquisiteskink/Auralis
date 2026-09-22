package app.auralis.music.data.player

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

enum class ReplayGainMode { Off, Track, Album }

class PlayerSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var replayGainMode: ReplayGainMode
        get() = ReplayGainMode.entries.getOrElse(prefs.getInt(RG_MODE, 0)) { ReplayGainMode.Off }
        set(value) { prefs.edit().putInt(RG_MODE, value.ordinal).apply() }

    var peakLimiter: Boolean
        get() = prefs.getBoolean(RG_LIMIT, true)
        set(value) { prefs.edit().putBoolean(RG_LIMIT, value).apply() }

    var gapless: Boolean
        get() = prefs.getBoolean(GAPLESS, true)
        set(value) {
            prefs.edit().putBoolean(GAPLESS, value).apply()
            if (!value && crossfade) crossfade = false
        }

    var crossfade: Boolean
        get() = prefs.getBoolean(CROSSFADE, false)
        set(value) {
            if (value && !gapless) gapless = true
            prefs.edit().putBoolean(CROSSFADE, value && gapless).apply()
        }

    var crossfadeMs: Int
        get() = prefs.getInt(CROSSFADE_MS, 5000).coerceIn(1000, 12000)
        set(value) { prefs.edit().putInt(CROSSFADE_MS, value.coerceIn(1000, 12000)).apply() }

    var pauseOnDisconnect: Boolean
        get() = prefs.getBoolean(PAUSE_DISC, true)
        set(value) { prefs.edit().putBoolean(PAUSE_DISC, value).apply() }

    /**
     * Sleep timer selection written by Settings / cleared by PlaybackService.
     * `0` = off, `-1` = end of current track, otherwise minutes (`15`/`30`/`45`/`60`).
     */
    var sleepTimerMinutes: Int
        get() = prefs.getInt(SLEEP_MINUTES, 0)
        set(value) { prefs.edit().putInt(SLEEP_MINUTES, value).apply() }

    /** `SystemClock.elapsedRealtime()` deadline for minute-based timers; `0` if inactive or end-of-track. */
    var sleepDeadlineElapsed: Long
        get() = prefs.getLong(SLEEP_DEADLINE, 0L)
        set(value) { prefs.edit().putLong(SLEEP_DEADLINE, value).apply() }

    fun clearSleepTimer() {
        prefs.edit()
            .putInt(SLEEP_MINUTES, 0)
            .putLong(SLEEP_DEADLINE, 0L)
            .apply()
    }

    fun armSleepMinutes(minutes: Int) {
        require(minutes in SLEEP_MINUTE_OPTIONS)
        val deadline = android.os.SystemClock.elapsedRealtime() + minutes * 60_000L
        prefs.edit()
            .putInt(SLEEP_MINUTES, minutes)
            .putLong(SLEEP_DEADLINE, deadline)
            .apply()
    }

    fun armSleepEndOfTrack() {
        prefs.edit()
            .putInt(SLEEP_MINUTES, SLEEP_END_OF_TRACK)
            .putLong(SLEEP_DEADLINE, 0L)
            .apply()
    }

    /** When true, OpenSubsonic original/`download` offline fetches require unmetered Wi‑Fi. */
    var wifiOnlyHiResDownloads: Boolean
        get() = prefs.getBoolean(WIFI_ONLY_HIRES_DL, true)
        set(value) { prefs.edit().putBoolean(WIFI_ONLY_HIRES_DL, value).apply() }

    var eqEnabled: Boolean
        get() = prefs.getBoolean(EQ_ON, false)
        set(value) { prefs.edit().putBoolean(EQ_ON, value).apply() }

    var eqPreset: String
        get() {
            val id = prefs.getString(EQ_PRESET, EqPresets.FLAT.id) ?: EqPresets.FLAT.id
            if (id == "custom" || EqPresets.all.any { it.id == id }) return id
            return "custom"
        }
        set(value) { prefs.edit().putString(EQ_PRESET, value).apply() }

    var eqGains: FloatArray
        get() {
            val raw = prefs.getString(EQ_GAINS, null) ?: return EqPresets.FLAT.gains.copyOf()
            val parts = raw.split(',')
            if (parts.size != 10) return EqPresets.FLAT.gains.copyOf()
            return FloatArray(10) { i -> parts[i].toFloatOrNull() ?: 0f }
        }
        set(value) {
            prefs.edit().putString(EQ_GAINS, value.joinToString(",") { "%.1f".format(it) }).apply()
        }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val PREFS = "auralis_prefs"
        const val RG_MODE = "rg_mode"
        const val RG_LIMIT = "rg_limit"
        const val GAPLESS = "gapless"
        const val CROSSFADE = "crossfade"
        const val CROSSFADE_MS = "crossfade_ms"
        const val PAUSE_DISC = "pause_disconnect"
        const val SLEEP_MINUTES = "sleep_minutes"
        const val SLEEP_DEADLINE = "sleep_deadline"
        const val SLEEP_END_OF_TRACK = -1
        val SLEEP_MINUTE_OPTIONS = setOf(15, 30, 45, 60)

        const val WIFI_ONLY_HIRES_DL = "wifi_only_hires_dl"
        const val EQ_ON = "eq_on"
        const val EQ_PRESET = "eq_preset"
        const val EQ_GAINS = "eq_gains"
        const val EXTRA_RG_TRACK = "auralis.rg.track"
        const val EXTRA_RG_ALBUM = "auralis.rg.album"
        const val EXTRA_RG_TRACK_PEAK = "auralis.rg.trackPeak"
        const val EXTRA_RG_ALBUM_PEAK = "auralis.rg.albumPeak"
        const val EXTRA_RG_FALLBACK = "auralis.rg.fallback"

        fun crossfadeLabel(ms: Int): String {
            val s = ms / 1000f
            return if (s == s.roundToInt().toFloat()) "${s.roundToInt()} s" else "${"%.1f".format(s)} s"
        }
    }
}
