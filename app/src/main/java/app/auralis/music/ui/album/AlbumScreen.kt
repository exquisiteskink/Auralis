package app.auralis.music.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.auralis.music.data.remote.AlbumWithSongs
import app.auralis.music.data.remote.formatDuration
import app.auralis.music.ui.components.CoverArt
import app.auralis.music.ui.components.ErrorText
import app.auralis.music.ui.components.PlayFab
import app.auralis.music.ui.components.SongRow
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer

@Composable
fun AlbumScreen(
    albumId: String,
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val p = LocalPalette.current
    var album by remember { mutableStateOf<AlbumWithSongs?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(albumId) {
        try {
            album = client.getAlbum(albumId)
        } catch (e: Exception) {
            error = e.message
        }
    }

    val current = album
    if (current == null) {
        if (error != null) ErrorText(error ?: "")
        return
    }

    val songs = current.song.sortedWith(compareBy({ it.discNumber }, { it.track }, { it.title }))

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 140.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                CoverArt(current.coverArt, Modifier.fillMaxSize(), current.displayName, corner = 0.dp)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, p.background.copy(alpha = 0.94f))),
                    ),
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(current.displayName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Row {
                        Text(
                            current.artist.orEmpty(),
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.clickable(enabled = current.artistId != null) {
                                current.artistId?.let(onArtist)
                            },
                        )
                        if (current.year > 0) {
                            Text("  ·  ${current.year}", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    val stats = buildString {
                        append("${songs.size} tracks")
                        if (current.duration > 0) append("  ·  ${formatDuration(current.duration)}")
                    }
                    Text(stats, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            songs.forEachIndexed { i, song ->
                SongRow(
                    song = song,
                    showArtist = false,
                    onClick = { player.play(songs, i) },
                    playing = player.state.value.current?.id == song.id,
                )
            }
        }
        if (songs.isNotEmpty()) {
            PlayFab(
                onClick = { player.play(songs, 0) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 148.dp),
            )
        }
    }
}
