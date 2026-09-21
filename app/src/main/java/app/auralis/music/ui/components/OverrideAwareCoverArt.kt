package app.auralis.music.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.auralis.music.data.art.ArtOverrideStore
import app.auralis.music.ui.theme.LocalPalette
import coil.compose.AsyncImage

/**
 * Drop-in around [CoverArt] when an album id is known.
 * Prefers [ArtOverrideStore] local JPEG; otherwise delegates to network [CoverArt].
 *
 * Wire [ArtOverrideStore] from AppContainer (new field) — do not add Settings keys that
 * collide with sleep_timer / wifi_only_hires_dl.
 */
@Composable
fun OverrideAwareCoverArt(
    albumId: String?,
    coverId: String?,
    artOverrides: ArtOverrideStore?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    corner: Dp = 8.dp,
    fallback: ImageVector = Icons.Rounded.Album,
    imageUrl: String? = null,
) {
    val overrideUri: Uri? = remember(albumId, artOverrides) {
        if (albumId.isNullOrBlank() || artOverrides == null) null
        else artOverrides.getAlbumOverrideUri(albumId)
    }
    if (overrideUri != null) {
        LocalOverrideImage(
            uri = overrideUri,
            modifier = modifier,
            contentDescription = contentDescription,
            corner = corner,
            fallback = fallback,
        )
    } else {
        CoverArt(
            coverId = coverId,
            modifier = modifier,
            contentDescription = contentDescription,
            corner = corner,
            fallback = fallback,
            imageUrl = imageUrl,
        )
    }
}

@Composable
private fun LocalOverrideImage(
    uri: Uri,
    modifier: Modifier,
    contentDescription: String?,
    corner: Dp,
    fallback: ImageVector,
) {
    val p = LocalPalette.current
    val shape = if (corner >= 48.dp) CircleShape else RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(p.surfaceHigh.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
