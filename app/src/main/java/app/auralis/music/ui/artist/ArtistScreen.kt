package app.auralis.music.ui.artist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.music.data.remote.AlbumID3
import app.auralis.music.data.remote.ArtistID3
import app.auralis.music.data.remote.ArtistInfo2
import app.auralis.music.data.remote.ArtistWithAlbums
import app.auralis.music.data.remote.SimilarArtist
import app.auralis.music.data.remote.Song
import app.auralis.music.ui.components.AlbumCard
import app.auralis.music.ui.components.CoverArt
import app.auralis.music.ui.components.ErrorText
import app.auralis.music.ui.components.HorizontalAlbums
import app.auralis.music.ui.components.PlayFab
import app.auralis.music.ui.components.SectionHeader
import app.auralis.music.ui.components.SongRow
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalContainer
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ArtistScreen(
    artistId: String,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    onPopular: (String, String) -> Unit,
    onAlbums: (String, String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val client = LocalClient.current
    val metadata = LocalContainer.current.metadata
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var artist by remember { mutableStateOf<ArtistWithAlbums?>(null) }
    var info by remember { mutableStateOf(ArtistInfo2()) }
    var popular by remember { mutableStateOf<List<Song>>(emptyList()) }
    var bio by remember { mutableStateOf<String?>(null) }
    var aboutOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var libraryArtists by remember { mutableStateOf<List<ArtistID3>>(emptyList()) }

    LaunchedEffect(artistId) {
        try {
            coroutineScope {
                val aDef = async { client.getArtist(artistId) }
                val iDef = async { runCatching { client.getArtistInfo2(artistId) }.getOrDefault(ArtistInfo2()) }
                val a = aDef.await()
                artist = a
                info = iDef.await()
                val librarySongs = a.album.flatMap { alb ->
                    runCatching { client.getAlbum(alb.id).song }.getOrDefault(emptyList())
                }
                popular = metadata.popularLibrarySongs(a.name, librarySongs, 20)
                bio = metadata.biography(a.name, info.biography)
                libraryArtists = runCatching { client.getArtists() }.getOrDefault(emptyList())
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    val current = artist
    if (current == null) {
        if (error != null) ErrorText(error ?: "")
        return
    }

    val similarInLibrary = info.similarArtist.mapNotNull { sim ->
        when {
            !sim.id.isNullOrBlank() && sim.id != artistId -> sim
            else -> libraryArtists.find { it.name.equals(sim.name, ignoreCase = true) && it.id != artistId }?.let { match ->
                sim.copy(id = match.id, coverArt = sim.coverArt ?: match.coverArt, artistImageUrl = sim.artistImageUrl ?: match.artistImageUrl)
            }
        }
    }.distinctBy { it.id }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 140.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.05f)) {
                CoverArt(
                    coverId = current.coverArt ?: current.id,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = current.name,
                    corner = 0.dp,
                    imageUrl = current.artistImageUrl ?: info.largeImageUrl ?: info.mediumImageUrl,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, p.background.copy(alpha = 0.92f)),
                            ),
                        ),
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    current.name,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (popular.isNotEmpty()) {
                SectionHeader("Popular") { onPopular(artistId, current.name) }
                popular.take(5).forEachIndexed { i, song ->
                    SongRow(
                        song = song,
                        rank = i + 1,
                        onClick = { player.play(popular, i) },
                        playing = player.state.value.current?.id == song.id,
                    )
                }
            }

            if (current.album.isNotEmpty()) {
                SectionHeader("Albums") { onAlbums(artistId, current.name) }
                HorizontalAlbums(current.album) { onAlbum(it.id) }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { aboutOpen = !aboutOpen }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("About", color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (aboutOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (aboutOpen) "Collapse" else "Expand",
                    tint = p.onBackground.copy(alpha = 0.6f),
                )
            }
            AnimatedVisibility(visible = aboutOpen) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (!bio.isNullOrBlank()) {
                        Text(bio!!, color = p.onBackground.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 21.sp)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (similarInLibrary.isNotEmpty()) {
                        Text("Similar in your library", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(similarInLibrary, key = { it.id ?: it.name }) { similar ->
                                SimilarChip(similar, onClick = { similar.id?.let(onArtist) })
                            }
                        }
                    } else if (bio.isNullOrBlank()) {
                        Text("No artist notes available.", color = p.onBackground.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        if (popular.isNotEmpty() || current.album.isNotEmpty()) {
            PlayFab(
                onClick = {
                    when {
                        popular.isNotEmpty() -> player.play(popular, 0)
                        current.album.isNotEmpty() -> scope.launch {
                            val songs = runCatching { client.getAlbum(current.album.first().id).song }.getOrDefault(emptyList())
                            if (songs.isNotEmpty()) player.play(songs, 0)
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 148.dp),
            )
        }
    }
}

@Composable
private fun SimilarChip(artist: SimilarArtist, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.width(96.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverArt(
            coverId = artist.coverArt ?: artist.id,
            modifier = Modifier.size(88.dp).aspectRatio(1f),
            contentDescription = artist.name,
            corner = 14.dp,
            imageUrl = artist.artistImageUrl,
        )
        Spacer(Modifier.height(6.dp))
        Text(artist.name, color = p.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
    }
}

@Composable
fun PopularSongsScreen(
    artistId: String,
    artistName: String,
    onBack: () -> Unit,
) {
    val client = LocalClient.current
    val metadata = LocalContainer.current.metadata
    val player = LocalPlayer.current
    val p = LocalPalette.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(artistId) {
        val artist = runCatching { client.getArtist(artistId) }.getOrNull() ?: return@LaunchedEffect
        val librarySongs = artist.album.flatMap { alb ->
            runCatching { client.getAlbum(alb.id).song }.getOrDefault(emptyList())
        }
        songs = metadata.popularLibrarySongs(artist.name, librarySongs, 20)
    }

    Column(Modifier.fillMaxSize().padding(bottom = 120.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.onBackground)
            }
            Text("Popular", color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        }
        Text(
            artistName,
            color = p.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        songs.forEachIndexed { i, song ->
            SongRow(
                song = song,
                rank = i + 1,
                onClick = { player.play(songs, i) },
                playing = player.state.value.current?.id == song.id,
            )
        }
    }
}

@Composable
fun ArtistAlbumsScreen(
    artistId: String,
    artistName: String,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
) {
    val client = LocalClient.current
    val p = LocalPalette.current
    var albums by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }

    LaunchedEffect(artistId) {
        albums = runCatching { client.getArtist(artistId).album }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.onBackground)
            }
            Column {
                Text("Albums", color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                Text(artistName, color = p.onBackground.copy(alpha = 0.55f), fontSize = 13.sp)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            gridItems(albums, key = { it.id }) { album ->
                AlbumCard(album, onClick = { onAlbum(album.id) }, width = 200.dp)
            }
        }
    }
}
