package app.auralis.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.music.data.remote.PlaylistWithSongs
import app.auralis.music.data.remote.formatDuration
import app.auralis.music.ui.components.CoverArt
import app.auralis.music.ui.components.ErrorText
import app.auralis.music.ui.components.PlayFab
import app.auralis.music.ui.components.SongRow
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer

@Composable
fun PlaylistScreen(playlistId: String, onBack: () -> Unit) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val p = LocalPalette.current
    var playlist by remember { mutableStateOf<PlaylistWithSongs?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        try {
            playlist = client.getPlaylist(playlistId)
        } catch (e: Exception) {
            error = e.message
        }
    }

    val current = playlist
    if (current == null) {
        if (error != null) ErrorText(error ?: "")
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 140.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                CoverArt(current.coverArt, Modifier.fillMaxSize(), current.name, corner = 0.dp)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, p.background.copy(alpha = 0.94f))),
                    ),
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(current.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${current.entry.size} tracks  ·  ${formatDuration(current.duration)}",
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            current.entry.forEachIndexed { i, song ->
                SongRow(
                    song = song,
                    onClick = { player.play(current.entry, i) },
                    playing = player.state.value.current?.id == song.id,
                )
            }
        }
        if (current.entry.isNotEmpty()) {
            PlayFab(
                onClick = { player.play(current.entry, 0) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 148.dp),
            )
        }
    }
}
