package app.auralis.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.music.data.remote.AlbumID3
import app.auralis.music.data.remote.ArtistID3
import app.auralis.music.data.remote.Playlist
import app.auralis.music.data.remote.Song
import app.auralis.music.data.remote.formatDuration
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer
import coil.compose.AsyncImage

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(if (p.isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.52f))
            .border(
                1.dp,
                if (p.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.7f),
                RoundedCornerShape(radius),
            )
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun CoverArt(
    coverId: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    corner: Dp = 14.dp,
    fallback: ImageVector = Icons.Rounded.Album,
    imageUrl: String? = null,
) {
    val client = LocalClient.current
    val p = LocalPalette.current
    val url = imageUrl ?: client.coverUrl(coverId, 600)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(p.surfaceHigh.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(fallback, null, tint = p.onSurface.copy(alpha = 0.35f), modifier = Modifier.size(36.dp))
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
        if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "See all", tint = p.onBackground.copy(alpha = 0.55f))
        }
    }
}

@Composable
fun AlbumCard(
    album: AlbumID3,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .width(width)
            .clickable(onClick = onClick),
    ) {
        CoverArt(album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f), album.displayName, corner = 16.dp)
        Spacer(Modifier.height(8.dp))
        Text(album.displayName, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(album.artist.orEmpty(), color = p.onBackground.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
) {
    val p = LocalPalette.current
    Column(modifier.width(width).clickable(onClick = onClick)) {
        CoverArt(playlist.coverArt, Modifier.fillMaxWidth().aspectRatio(1f), playlist.name, corner = 16.dp)
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(
            "${playlist.songCount} tracks",
            color = p.onBackground.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )
    }
}

@Composable
fun ArtistTile(
    artist: ArtistID3,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val client = LocalClient.current
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverArt(
            coverId = artist.coverArt ?: artist.id,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentDescription = artist.name,
            corner = 16.dp,
            fallback = Icons.Rounded.Person,
            imageUrl = artist.artistImageUrl ?: client.coverUrl(artist.coverArt ?: artist.id, 600),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            color = p.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun HorizontalAlbums(
    albums: List<AlbumID3>,
    onAlbum: (AlbumID3) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album, onClick = { onAlbum(album) })
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    showArtist: Boolean = true,
    playing: Boolean = false,
) {
    val p = LocalPalette.current
    val player = LocalPlayer.current
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                rank.toString(),
                color = if (playing) p.primary else p.onBackground.copy(alpha = 0.45f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(28.dp),
            )
        }
        CoverArt(song.coverArt, Modifier.size(48.dp), song.title, corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (playing) p.primary else p.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            val sub = buildString {
                if (showArtist) append(song.artist.orEmpty())
                if (song.qualityLabel != null) {
                    if (isNotEmpty()) append("  ·  ")
                    append(song.qualityLabel)
                }
            }
            if (sub.isNotBlank()) {
                Text(sub, color = p.onBackground.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        }
        Text(formatDuration(song.duration), color = p.onBackground.copy(alpha = 0.45f), fontSize = 12.sp)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = p.onBackground.copy(alpha = 0.5f))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = { menu = false; player.playNext(song) },
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = { menu = false; player.addToQueue(song) },
                )
            }
        }
    }
}

@Composable
fun PlayFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(p.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = p.onPrimary, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun ErrorText(message: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(message, color = p.onBackground.copy(alpha = 0.7f), modifier = modifier.padding(24.dp))
}
