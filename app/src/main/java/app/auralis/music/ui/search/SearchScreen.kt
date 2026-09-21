package app.auralis.music.ui.search

import app.auralis.music.data.remote.suspendRunCatching
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.auralis.music.data.remote.AlbumID3
import app.auralis.music.data.remote.ArtistID3
import app.auralis.music.data.remote.Genre
import app.auralis.music.data.remote.SearchResult3
import app.auralis.music.ui.components.ArtistTile
import app.auralis.music.ui.components.GenreChip
import app.auralis.music.ui.components.HorizontalAlbums
import app.auralis.music.ui.components.SectionHeader
import app.auralis.music.ui.components.SongRow
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(SearchResult3()) }
    var allGenres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var recentAlbums by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }

    LaunchedEffect(Unit) {
        allGenres = suspendRunCatching { client.getGenres() }.getOrDefault(emptyList()).filter { it.value.isNotBlank() }
        recentAlbums = suspendRunCatching { client.getAlbumList2("recent", 24) }.getOrDefault(emptyList())
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            result = SearchResult3()
            return@LaunchedEffect
        }
        delay(280)
        result = suspendRunCatching { client.search3(query.trim()) }.getOrDefault(SearchResult3())
    }

    val matchedGenres = remember(query, allGenres) {
        if (query.isBlank()) emptyList()
        else allGenres.filter { it.value.contains(query.trim(), ignoreCase = true) }.take(16)
    }
    val browseGenres = remember(allGenres) {
        allGenres.sortedByDescending { it.songCount }.take(24)
    }
    val recentArtists = remember(recentAlbums) { recentArtistsFromAlbums(recentAlbums) }
    // Spec: blank AND unfocused → genres + recent artists; focus or typing → results only.
    val showEmptyBrowse = query.isBlank() && !searchFocused

    Column(Modifier.fillMaxSize().padding(bottom = 64.dp)) {
        Text(
            "Search",
            color = p.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .onFocusChanged { searchFocused = it.isFocused },
            placeholder = { Text("Artists, albums, songs, genres") },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = p.onBackground.copy(alpha = 0.45f)) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.onBackground.copy(alpha = 0.18f),
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = p.surfaceHigh,
                unfocusedContainerColor = p.surfaceHigh,
                focusedTextColor = p.onBackground,
                unfocusedTextColor = p.onBackground,
                cursorColor = p.onBackground,
            ),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (showEmptyBrowse) {
                if (browseGenres.isNotEmpty()) {
                    item { SectionHeader("Genres") }
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            browseGenres.forEach { genre ->
                                GenreChip(genre, onClick = {
                                    scope.launch {
                                        val songs = suspendRunCatching { client.getSongsByGenre(genre.value, 80) }.getOrDefault(emptyList())
                                        if (songs.isNotEmpty()) player.play(songs.shuffled(), 0)
                                    }
                                })
                            }
                        }
                    }
                }
                if (recentArtists.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        SectionHeader("Recently played artists")
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(recentArtists, key = { "ra-${it.id}" }) { artist ->
                                Column(Modifier.width(96.dp).padding(bottom = 8.dp)) {
                                    ArtistTile(artist, onClick = { onArtist(artist.id) }, circular = true)
                                }
                            }
                        }
                    }
                }
            } else {
                if (matchedGenres.isNotEmpty()) {
                    item { SectionHeader("Genres") }
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            matchedGenres.forEach { genre ->
                                GenreChip(genre, onClick = {
                                    scope.launch {
                                        val songs = suspendRunCatching { client.getSongsByGenre(genre.value, 80) }.getOrDefault(emptyList())
                                        if (songs.isNotEmpty()) player.play(songs.shuffled(), 0)
                                    }
                                })
                            }
                        }
                    }
                }
                if (result.artist.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(result.artist, key = { it.id }) { artist ->
                                Column(Modifier.width(120.dp).padding(bottom = 8.dp)) {
                                    ArtistTile(artist, onClick = { onArtist(artist.id) }, circular = true)
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
                    itemsIndexed(result.song, key = { i, s -> "${s.id}-$i" }) { i, song ->
                        SongRow(
                            song = song,
                            onClick = { player.play(result.song, i) },
                        )
                    }
                }
                if (query.isNotBlank() && result.artist.isEmpty() && result.album.isEmpty() && result.song.isEmpty() && matchedGenres.isEmpty()) {
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
}

private fun recentArtistsFromAlbums(albums: List<AlbumID3>): List<ArtistID3> {
    val seen = LinkedHashSet<String>()
    return albums.mapNotNull { album ->
        val id = album.artistId ?: return@mapNotNull null
        if (!seen.add(id)) null
        else ArtistID3(
            id = id,
            name = album.artist ?: "Artist",
            coverArt = album.coverArt,
            albumCount = 0,
        )
    }
}
