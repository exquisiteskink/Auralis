package app.auralis.music.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.music.BuildConfig
import app.auralis.music.data.player.EqPresets
import app.auralis.music.data.player.ExternalEqRisk
import app.auralis.music.data.player.PlayerSettings
import app.auralis.music.data.player.ReplayGainMode
import app.auralis.music.ui.components.GlassSurface
import app.auralis.music.ui.theme.LocalContainer
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    transcode: Int,
    onTranscode: (Int) -> Unit,
    onLoggedOut: () -> Unit,
) {
    val p = LocalPalette.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val playerPrefs = remember { PlayerSettings(context) }
    val downloads = container.downloads
    val dlState by downloads.state.collectAsState()
    val creds = container.credentials.load()
    val server = creds?.serverUrl.orEmpty()
    val user = if (creds?.authMode?.name == "ApiKey") "API key" else creds?.username.orEmpty()

    var rgMode by remember { mutableStateOf(playerPrefs.replayGainMode) }
    var peak by remember { mutableStateOf(playerPrefs.peakLimiter) }
    var gapless by remember { mutableStateOf(playerPrefs.gapless) }
    var crossfade by remember { mutableStateOf(playerPrefs.crossfade) }
    var fadeMs by remember { mutableStateOf(playerPrefs.crossfadeMs.toFloat()) }
    val externalEqDualCfBlocked = remember { ExternalEqRisk.isDualPlayerCrossfadeUnsafe(context) }
    var pauseDisc by remember { mutableStateOf(playerPrefs.pauseOnDisconnect) }
    var sleepMins by remember { mutableStateOf(playerPrefs.sleepTimerMinutes) }
    // PlaybackService clears the timer on fire / seek / new queue; keep the radio in sync.
    DisposableEffect(playerPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == PlayerSettings.SLEEP_MINUTES || key == PlayerSettings.SLEEP_DEADLINE) {
                sleepMins = playerPrefs.sleepTimerMinutes
            }
        }
        playerPrefs.register(listener)
        onDispose { playerPrefs.unregister(listener) }
    }
    var eqOn by remember { mutableStateOf(playerPrefs.eqEnabled) }
    var eqPreset by remember { mutableStateOf(playerPrefs.eqPreset) }
    var eqGains by remember { mutableStateOf(playerPrefs.eqGains.copyOf()) }
    var wifiOnlyHiRes by remember { mutableStateOf(playerPrefs.wifiOnlyHiResDownloads) }
    LaunchedEffect(Unit) { downloads.refreshBytes() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 64.dp),
    ) {
        Text(
            "Settings",
            color = p.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        SettingsGroup("Account") {
            Text(server, color = p.onBackground, fontWeight = FontWeight.Medium)
            if (user.isNotBlank()) {
                Text(user, color = p.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            Text(
                "Login uses salted token auth (or an API key). The password is encrypted in Android Keystore.",
                color = p.onBackground.copy(alpha = 0.45f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = {
                container.signOut()
                onLoggedOut()
            }) {
                Text("Sign out", color = p.onBackground)
            }
        }

        SettingsGroup("Appearance") {
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    selected = themeMode == mode,
                    label = when (mode) {
                        ThemeMode.System -> "Match system"
                        ThemeMode.Dark -> "Dark"
                        ThemeMode.Light -> "Light"
                    },
                    onClick = { onThemeMode(mode) },
                )
            }
            Hint("While music plays, backgrounds and the seek bar follow colors from the album art.")
        }

        SettingsGroup("Playback") {
            Text("Streaming quality", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            listOf(0 to "Original (audiophile)", 320 to "320 kbps", 192 to "192 kbps", 128 to "128 kbps").forEach { (rate, label) ->
                RadioRow(selected = transcode == rate, label = label, onClick = { onTranscode(rate) })
            }
            Hint("Original streams the file as stored on the server.")

            Spacer(Modifier.height(12.dp))
            Text("ReplayGain / R128", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            ReplayGainMode.entries.forEach { mode ->
                RadioRow(
                    selected = rgMode == mode,
                    label = when (mode) {
                        ReplayGainMode.Off -> "Off"
                        ReplayGainMode.Track -> "Track gain"
                        ReplayGainMode.Album -> "Album gain"
                    },
                    onClick = {
                        rgMode = mode
                        playerPrefs.replayGainMode = mode
                    },
                )
            }
            ToggleRow(
                title = "Peak limiter",
                subtitle = "Lower gain so tagged peaks do not clip",
                checked = peak,
                onChecked = { peak = it; playerPrefs.peakLimiter = it },
            )

            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "True gapless",
                subtitle = if (externalEqDualCfBlocked) {
                    "Join tracks without a pause when the external EQ is ready"
                } else {
                    "Join tracks without a pause using decoder delay/padding"
                },
                checked = gapless,
                onChecked = {
                    gapless = it
                    playerPrefs.gapless = it
                    if (!it) {
                        crossfade = false
                    }
                },
            )
            ToggleRow(
                title = "Crossfade",
                subtitle = when {
                    !gapless -> "Turn on true gapless to enable crossfade"
                    externalEqDualCfBlocked ->
                        "External EQ detected — overlapping tracks are disabled"
                    else -> "Overlap the end of one track with the start of the next"
                },
                checked = crossfade,
                enabled = gapless,
                onChecked = {
                    if (!gapless) return@ToggleRow
                    crossfade = it
                    playerPrefs.crossfade = it
                },
            )
            if (crossfade && gapless && !externalEqDualCfBlocked) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Crossfade length ${PlayerSettings.crossfadeLabel(fadeMs.toInt())}",
                    color = p.onBackground.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
                Slider(
                    value = fadeMs,
                    onValueChange = { fadeMs = it },
                    onValueChangeFinished = { playerPrefs.crossfadeMs = fadeMs.toInt() },
                    valueRange = 1000f..12000f,
                    steps = 10,
                    colors = SliderDefaults.colors(thumbColor = p.primary, activeTrackColor = p.primary),
                )
            }
            ToggleRow(
                title = "Pause on disconnect",
                subtitle = "Pause when headphones or Bluetooth audio disconnect",
                checked = pauseDisc,
                onChecked = { pauseDisc = it; playerPrefs.pauseOnDisconnect = it },
            )

            Spacer(Modifier.height(12.dp))
            Text("Sleep timer", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            listOf(
                0 to "Off",
                15 to "15 minutes",
                30 to "30 minutes",
                45 to "45 minutes",
                60 to "60 minutes",
                PlayerSettings.SLEEP_END_OF_TRACK to "End of track",
            ).forEach { (value, label) ->
                RadioRow(
                    selected = sleepMins == value,
                    label = label,
                    onClick = {
                        sleepMins = value
                        when (value) {
                            0 -> playerPrefs.clearSleepTimer()
                            PlayerSettings.SLEEP_END_OF_TRACK -> playerPrefs.armSleepEndOfTrack()
                            else -> playerPrefs.armSleepMinutes(value)
                        }
                    },
                )
            }
            Hint("Pauses playback after the chosen time. Seek, a new queue, or resume after pause clears it.")
        }


        SettingsGroup("Offline downloads") {
            ToggleRow(
                title = "Wi‑Fi only for HiRes downloads",
                subtitle = "Original OpenSubsonic download files require unmetered Wi‑Fi when enabled",
                checked = wifiOnlyHiRes,
                onChecked = {
                    wifiOnlyHiRes = it
                    playerPrefs.wifiOnlyHiResDownloads = it
                },
            )
            Hint("Uses the documented download endpoint (original file). Streaming quality is separate.")
            Spacer(Modifier.height(8.dp))
            val usedMb = dlState.bytesUsed / (1024.0 * 1024.0)
            Text(
                if (dlState.bytesUsed > 0) "Stored offline  %.1f MB".format(usedMb) else "No offline files yet",
                color = p.onBackground.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            if (dlState.phase.name != "Idle" && !dlState.message.isNullOrBlank()) {
                Text(dlState.message ?: "", color = p.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            TextButton(onClick = { downloads.refreshBytes() }) {
                Text("Refresh size", color = p.onBackground)
            }
            TextButton(onClick = { downloads.clearDownloads() }) {
                Text("Clear offline downloads", color = p.onBackground)
            }
        }

        SettingsGroup("Equalizer") {
            ToggleRow(
                title = "10-band equalizer",
                subtitle = "Software EQ in the player, independent of the system equalizer",
                checked = eqOn,
                onChecked = { eqOn = it; playerPrefs.eqEnabled = it },
            )
            if (eqOn) {
                Spacer(Modifier.height(8.dp))
                Text("Presets", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Hint("Genre and speaker curves. Drag a band to make a custom curve.")
                EqPresets.all.forEach { preset ->
                    RadioRow(
                        selected = eqPreset == preset.id,
                        label = preset.name,
                        onClick = {
                            eqPreset = preset.id
                            eqGains = preset.gains.copyOf()
                            playerPrefs.eqPreset = preset.id
                            playerPrefs.eqGains = preset.gains
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Bands", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                EqPresets.BANDS_HZ.forEachIndexed { i, hz ->
                    EqBandRow(
                        hz = hz,
                        gain = eqGains[i],
                        onGain = { g ->
                            val next = eqGains.copyOf()
                            next[i] = g
                            eqGains = next
                            eqPreset = "custom"
                            playerPrefs.eqPreset = "custom"
                            playerPrefs.eqGains = next
                        },
                    )
                }
            }
        }

        SettingsGroup("About") {
            Text("Auralis", color = p.onBackground, fontWeight = FontWeight.SemiBold)
            Text(BuildConfig.VERSION_NAME, color = p.onBackground.copy(alpha = 0.5f), fontSize = 13.sp)
            Text(
                "A listening app for Navidrome, Subsonic, and OpenSubsonic.",
                color = p.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Text(
        title.uppercase(),
        color = p.onBackground.copy(alpha = 0.5f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
    GlassSurface(Modifier.fillMaxWidth()) { content() }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun Hint(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.onBackground.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = p.primary),
        )
        Text(label, color = p.onBackground)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = p.onBackground.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 15.sp)
            Text(subtitle, color = p.onBackground.copy(alpha = 0.45f), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = p.primary, checkedTrackColor = p.primary.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun EqBandRow(hz: Int, gain: Float, onGain: (Float) -> Unit) {
    val p = LocalPalette.current
    val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.onBackground.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(56.dp))
        Slider(
            value = gain,
            onValueChange = onGain,
            valueRange = -12f..12f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = p.primary, activeTrackColor = p.primary),
        )
        Text(
            (if (gain > 0) "+" else "") + "%.1f".format(gain),
            color = p.onBackground.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
    }
}

class AppearancePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("auralis_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.entries.getOrElse(prefs.getInt("theme", 0)) { ThemeMode.System }
        set(value) { prefs.edit().putInt("theme", value.ordinal).apply() }

    var transcode: Int
        get() = prefs.getInt("transcode", 0)
        set(value) { prefs.edit().putInt("transcode", value).apply() }

    var artistGrid: Boolean
        get() = prefs.getBoolean("artist_grid", true)
        set(value) { prefs.edit().putBoolean("artist_grid", value).apply() }
}
