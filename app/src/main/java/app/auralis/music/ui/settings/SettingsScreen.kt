package app.auralis.music.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.music.BuildConfig
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
    val creds = container.credentials.load()
    val server = creds?.serverUrl.orEmpty()
    val user = if (creds?.authMode?.name == "ApiKey") "API key" else creds?.username.orEmpty()

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
        GlassSurface(Modifier.fillMaxWidth()) {
            Text("Server", color = p.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(server, color = p.onBackground, fontWeight = FontWeight.Medium)
            if (user.isNotBlank()) {
                Text(user, color = p.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            Text(
                "Login uses salted token auth (or an API key). The password is encrypted in Android Keystore and is never shown again.",
                color = p.onBackground.copy(alpha = 0.45f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                container.signOut()
                onLoggedOut()
            }) {
                Text("Sign out", color = p.onBackground)
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassSurface(Modifier.fillMaxWidth()) {
            Text("Appearance", color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = themeMode == mode, onClick = { onThemeMode(mode) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        colors = RadioButtonDefaults.colors(selectedColor = p.primary),
                    )
                    Text(
                        when (mode) {
                            ThemeMode.System -> "Match system"
                            ThemeMode.Dark -> "Dark"
                            ThemeMode.Light -> "Light"
                        },
                        color = p.onBackground,
                    )
                }
            }
            Text(
                "While music plays, backgrounds wash with colors sampled from the album art.",
                color = p.onBackground.copy(alpha = 0.45f),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        GlassSurface(Modifier.fillMaxWidth()) {
            Text("Playback quality", color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            listOf(0 to "Original (audiophile)", 320 to "320 kbps", 192 to "192 kbps", 128 to "128 kbps").forEach { (rate, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onTranscode(rate) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = transcode == rate,
                        onClick = { onTranscode(rate) },
                        colors = RadioButtonDefaults.colors(selectedColor = p.primary),
                    )
                    Text(label, color = p.onBackground)
                }
            }
            Text(
                "Original streams the file as stored on the server (FLAC, high-rate PCM, etc.) with no transcode.",
                color = p.onBackground.copy(alpha = 0.45f),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        GlassSurface(Modifier.fillMaxWidth()) {
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
