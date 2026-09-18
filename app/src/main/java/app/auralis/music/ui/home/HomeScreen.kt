package app.auralis.music.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.music.data.remote.AlbumID3
import app.auralis.music.data.remote.Playlist
import app.auralis.music.ui.components.ErrorText
import app.auralis.music.ui.components.PlaylistCard
import app.auralis.music.ui.components.SectionHeader
import app.auralis.music.ui.components.HorizontalAlbums
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalPalette
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlaylist: (String) -> Unit,
    onAlbum: (String) -> Unit,
) {
    val client = LocalClient.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var recent by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var newest by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        try {
            coroutineScope {
                val pDef = async { client.getPlaylists() }
                val rDef = async { client.getAlbumList2("recent", 24) }
                val nDef = async { client.getAlbumList2("newest", 24) }
                playlists = pDef.await()
                recent = rDef.await()
                newest = nDef.await()
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(isRefreshing = loading, onRefresh = { scope.launch { load() } }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 140.dp),
        ) {
            Text(
                "Listen",
                color = p.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
            )
            if (error != null && playlists.isEmpty() && recent.isEmpty()) {
                ErrorText(error ?: "Could not load library")
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
            Spacer(Modifier.height(20.dp))
            SectionHeader("Recently played")
            HorizontalAlbums(recent) { onAlbum(it.id) }
            Spacer(Modifier.height(20.dp))
            SectionHeader("Recently added")
            HorizontalAlbums(newest) { onAlbum(it.id) }
        }
    }
}
