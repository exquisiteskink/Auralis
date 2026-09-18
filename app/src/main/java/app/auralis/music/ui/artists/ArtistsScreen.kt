package app.auralis.music.ui.artists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import app.auralis.music.data.remote.ArtistID3
import app.auralis.music.ui.components.ArtistTile
import app.auralis.music.ui.components.ErrorText
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(onArtist: (String) -> Unit) {
    val client = LocalClient.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var artists by remember { mutableStateOf<List<ArtistID3>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        try {
            artists = client.getArtists().sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(isRefreshing = loading, onRefresh = { scope.launch { load() } }) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Artists",
                    color = p.onBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                )
            }
            if (error != null && artists.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { ErrorText(error ?: "") }
            }
            items(artists, key = { it.id }) { artist ->
                ArtistTile(artist, onClick = { onArtist(artist.id) })
            }
        }
    }
}
