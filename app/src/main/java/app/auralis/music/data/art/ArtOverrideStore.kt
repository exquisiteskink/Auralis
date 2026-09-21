package app.auralis.music.data.art

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Client-only cover/artist image overrides.
 *
 * Layout: `filesDir/art_overrides/{album|artist}/<sanitizedId>.jpg`
 *
 * - Never writes embedded tags or library files on the server
 * - Never calls Subsonic upload / invented setCoverArt APIs
 * - Does not touch PlayerSettings (sleep_timer / wifi_only_hires_dl keys)
 */
class ArtOverrideStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "art_overrides").apply { mkdirs() }
    private val albumDir = File(root, "album").apply { mkdirs() }
    private val artistDir = File(root, "artist").apply { mkdirs() }

    fun getAlbumOverrideUri(albumId: String): Uri? = uriIfExists(albumDir, albumId)
    fun getArtistOverrideUri(artistId: String): Uri? = uriIfExists(artistDir, artistId)

    fun hasAlbumOverride(albumId: String): Boolean = fileFor(albumDir, albumId).isFile
    fun hasArtistOverride(artistId: String): Boolean = fileFor(artistDir, artistId).isFile

    fun setAlbumOverride(albumId: String, bytes: ByteArray) {
        writeBytes(albumDir, albumId, bytes)
    }

    fun setArtistOverride(artistId: String, bytes: ByteArray) {
        writeBytes(artistDir, artistId, bytes)
    }

    fun setAlbumOverride(albumId: String, uri: Uri) {
        writeUri(albumDir, albumId, uri)
    }

    fun setArtistOverride(artistId: String, uri: Uri) {
        writeUri(artistDir, artistId, uri)
    }

    fun clearAlbumOverride(albumId: String) {
        fileFor(albumDir, albumId).delete()
    }

    fun clearArtistOverride(artistId: String) {
        fileFor(artistDir, artistId).delete()
    }

    private fun uriIfExists(dir: File, id: String): Uri? {
        val f = fileFor(dir, id)
        return if (f.isFile && f.length() > 0L) Uri.fromFile(f) else null
    }

    private fun writeBytes(dir: File, id: String, bytes: ByteArray) {
        require(bytes.isNotEmpty()) { "empty image bytes" }
        val target = fileFor(dir, id)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    private fun writeUri(dir: File, id: String, uri: Uri) {
        val resolver = appContext.contentResolver
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "cannot open $uri" }
            writeBytes(dir, id, input.readBytes())
        }
    }

    private fun fileFor(dir: File, id: String): File =
        File(dir, sanitizeId(id) + ".jpg")

    companion object {
        fun sanitizeId(id: String): String =
            id.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }
                .joinToString("")
                .ifBlank { "unknown" }
    }

    /** Brief API: album or artist override Uri, or null. */
    fun getOverrideUri(albumId: String? = null, artistId: String? = null): Uri? = when {
        !albumId.isNullOrBlank() -> getAlbumOverrideUri(albumId)
        !artistId.isNullOrBlank() -> getArtistOverrideUri(artistId)
        else -> null
    }

    fun setOverride(albumId: String? = null, artistId: String? = null, bytes: ByteArray) {
        when {
            !albumId.isNullOrBlank() -> setAlbumOverride(albumId, bytes)
            !artistId.isNullOrBlank() -> setArtistOverride(artistId, bytes)
            else -> error("albumId or artistId required")
        }
    }

    fun setOverride(albumId: String? = null, artistId: String? = null, uri: Uri) {
        when {
            !albumId.isNullOrBlank() -> setAlbumOverride(albumId, uri)
            !artistId.isNullOrBlank() -> setArtistOverride(artistId, uri)
            else -> error("albumId or artistId required")
        }
    }

    fun clearOverride(albumId: String? = null, artistId: String? = null) {
        when {
            !albumId.isNullOrBlank() -> clearAlbumOverride(albumId)
            !artistId.isNullOrBlank() -> clearArtistOverride(artistId)
            else -> error("albumId or artistId required")
        }
    }

}
