package app.auralis.music.ui.playlist

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.music.data.remote.PlaylistWithSongs
import app.auralis.music.data.remote.formatDuration
import app.auralis.music.ui.components.CoverArt
import app.auralis.music.ui.components.ErrorText
import app.auralis.music.ui.components.HeaderPlay
import app.auralis.music.ui.components.ScreenTopBar
import app.auralis.music.ui.components.SongRow
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer

@Composable
fun PlaylistScreen(playlistId: String, onBack: () -> Unit) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val p = LocalPalette.current
    var playlist by remember(playlistId) { mutableStateOf<PlaylistWithSongs?>(null) }
    var error by remember(playlistId) { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        try {
            playlist = client.getPlaylist(playlistId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }

    val current = playlist
    if (current == null) {
        if (error != null) ErrorText(error ?: "")
        return
    }

    val stats = "${current.entry.size} tracks  –  ${formatDuration(current.duration)}"

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(bottom = 64.dp),
    ) {
        item {
            ScreenTopBar(current.name, onBack)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CoverArt(
                    current.coverArt,
                    Modifier
                        .size(132.dp)
                        .shadow(10.dp, RoundedCornerShape(8.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                    current.name,
                    corner = 8.dp,
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (current.entry.isNotEmpty()) {
                        HeaderPlay(onClick = { player.play(current.entry, 0) }, size = 64.dp)
                        Spacer(Modifier.height(8.dp))
                        IconButton(onClick = { player.play(current.entry.shuffled(), 0) }) {
                            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = p.onBackground.copy(alpha = 0.75f))
                        }
                    }
                }
            }
            Text(
                stats,
                color = p.onBackground.copy(alpha = 0.55f),
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(1.dp)
                    .background(p.onBackground.copy(alpha = 0.22f)),
            )
        }
        itemsIndexed(current.entry) { i, song ->
            SongRow(
                song = song,
                onClick = { player.play(current.entry, i) },
                showCover = false,
                rank = i + 1,
            )
        }
    }
}
