package app.auralis.music.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.music.data.remote.SearchResult3
import app.auralis.music.ui.components.ArtistTile
import app.auralis.music.ui.components.HorizontalAlbums
import app.auralis.music.ui.components.SectionHeader
import app.auralis.music.ui.components.SongRow
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(SearchResult3()) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            result = SearchResult3()
            return@LaunchedEffect
        }
        delay(280)
        result = runCatching { client.search3(query.trim()) }.getOrDefault(SearchResult3())
    }

    Column(Modifier.fillMaxSize().padding(bottom = 120.dp)) {
        Text(
            "Search",
            color = p.onBackground,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Artists, albums, songs") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.primary,
                unfocusedBorderColor = p.outline,
                focusedTextColor = p.onBackground,
                unfocusedTextColor = p.onBackground,
                cursorColor = p.primary,
            ),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (result.artist.isNotEmpty()) {
                item { SectionHeader("Artists") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(result.artist, key = { it.id }) { artist ->
                            Column(Modifier.width(120.dp).padding(bottom = 8.dp)) {
                                ArtistTile(artist, onClick = { onArtist(artist.id) })
                            }
                        }
                    }
                }
            }
            if (result.album.isNotEmpty()) {
                item { SectionHeader("Albums") }
                item { HorizontalAlbums(result.album) { onAlbum(it.id) } }
            }
            if (result.song.isNotEmpty()) {
                item { SectionHeader("Songs") }
                items(result.song, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { player.play(result.song, result.song.indexOf(song)) },
                        playing = player.state.value.current?.id == song.id,
                    )
                }
            }
            if (query.isNotBlank() && result.artist.isEmpty() && result.album.isEmpty() && result.song.isEmpty()) {
                item {
                    Text(
                        "Nothing matched “$query”",
                        color = p.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}
