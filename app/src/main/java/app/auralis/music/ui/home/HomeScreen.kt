package app.auralis.music.ui.home

import app.auralis.music.data.remote.suspendRunCatching
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.music.data.remote.AlbumID3
import app.auralis.music.data.remote.ArtistID3
import app.auralis.music.data.remote.Genre
import app.auralis.music.data.remote.Playlist
import app.auralis.music.data.remote.Song
import app.auralis.music.ui.components.ArtistTile
import app.auralis.music.ui.components.ErrorText
import app.auralis.music.ui.components.GenreChip
import app.auralis.music.ui.components.HorizontalAlbums
import app.auralis.music.ui.components.MixOrb
import app.auralis.music.ui.components.PlaylistCard
import app.auralis.music.ui.components.SectionHeader
import app.auralis.music.ui.components.SongRow
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onPlaylist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val playerState by player.state.collectAsState()
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var recent by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var newest by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        try {
            coroutineScope {
                val pDef = async { suspendRunCatching { client.getPlaylists() }.getOrDefault(emptyList()) }
                val rDef = async { suspendRunCatching { client.getAlbumList2("recent", 24) }.getOrDefault(emptyList()) }
                val nDef = async { suspendRunCatching { client.getAlbumList2("newest", 24) }.getOrDefault(emptyList()) }
                val gDef = async { suspendRunCatching { client.getGenres() }.getOrDefault(emptyList()) }
                val fDef = async { suspendRunCatching { client.getStarredSongs() }.getOrDefault(emptyList()) }
                playlists = pDef.await()
                recent = rDef.await()
                newest = nDef.await()
                genres = gDef.await().filter { it.value.isNotBlank() }.sortedByDescending { it.songCount }.take(24)
                favorites = fDef.await()
                if (playlists.isEmpty() && recent.isEmpty() && newest.isEmpty()) {
                    error = "Could not load library"
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(playerState.favoriteEpoch) {
        if (playerState.favoriteEpoch > 0) {
            favorites = suspendRunCatching { client.getStarredSongs() }.getOrDefault(favorites)
        }
    }

    val recentArtists = remember(recent) {
        val seen = LinkedHashSet<String>()
        recent.mapNotNull { album ->
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

    fun playGenre(genre: Genre) {
        scope.launch {
            val songs = suspendRunCatching { client.getSongsByGenre(genre.value, 80) }.getOrDefault(emptyList())
            if (songs.isNotEmpty()) player.play(songs.shuffled(), 0)
        }
    }

    fun playArtistMix(artist: ArtistID3) {
        scope.launch {
            val top = suspendRunCatching { client.getTopSongs(artist.name, 40) }.getOrDefault(emptyList())
            val songs = if (top.size >= 8) top else {
                suspendRunCatching { client.getArtist(artist.id).album }.getOrDefault(emptyList())
                    .flatMap { alb -> suspendRunCatching { client.getAlbum(alb.id).song }.getOrDefault(emptyList()) }
                    .ifEmpty { top }
            }
            if (songs.isNotEmpty()) player.play(songs.shuffled(), 0)
        }
    }

    PullToRefreshBox(isRefreshing = loading, onRefresh = { scope.launch { load() } }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 64.dp),
        ) {
            Text(
                "Home",
                color = p.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            )
            if (error != null && playlists.isEmpty() && recent.isEmpty()) {
                ErrorText(error ?: "Could not load library")
            }

            if (recentArtists.isNotEmpty()) {
                SectionHeader("For you")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(recentArtists.take(8), key = { "mix-${it.id}" }) { artist ->
                        MixOrb(
                            title = artist.name,
                            subtitle = "Artist mix",
                            coverId = artist.coverArt,
                            onClick = { playArtistMix(artist) },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            SectionHeader("Playlists")
            if (playlists.isEmpty() && !loading) {
                Text(
                    "No playlists yet",
                    color = p.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        PlaylistCard(pl, onClick = { onPlaylist(pl.id) })
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (favorites.isNotEmpty()) {
                SectionHeader("Favorites") { player.play(favorites, 0) }
                favorites.take(8).forEachIndexed { i, song ->
                    SongRow(
                        song = song,
                        onClick = { player.play(favorites, i) },
                        playing = playerState.current?.id == song.id,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
            SectionHeader("Recently played")
            HorizontalAlbums(recent) { onAlbum(it.id) }

            if (recentArtists.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionHeader("Recently played artists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recentArtists, key = { "ra-${it.id}" }) { artist ->
                        Column(Modifier.width(96.dp)) {
                            ArtistTile(artist, onClick = { onArtist(artist.id) }, circular = true)
                        }
                    }
                }
            }

            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionHeader("Genres")
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.forEach { genre ->
                        GenreChip(genre, onClick = { playGenre(genre) })
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("Recently added")
            HorizontalAlbums(newest) { onAlbum(it.id) }
        }
    }
}
