package app.auralis.music.data.remote

/**
 * Popularity and biography from the user's own server only.
 * No third-party lookups (Deezer / MusicBrainz / Wikipedia).
 */
class MetadataRepository(
    private val client: SubsonicClient,
) {
    suspend fun popularLibrarySongs(
        artistName: String,
        artistId: String? = null,
        librarySongs: List<Song> = emptyList(),
        count: Int = 20,
    ): List<Song> {
        val serverTop = suspendRunCatching { client.getTopSongs(artistName, count) }.getOrDefault(emptyList())
        if (serverTop.size >= 5) {
            return serverTop.take(count)
        }

        val searchHits = if (librarySongs.isNotEmpty()) librarySongs else suspendRunCatching {
            client.search3(
                query = artistName,
                artistCount = 0,
                albumCount = 0,
                songCount = 80,
            ).song.filter { song ->
                (artistId != null && song.artistId == artistId) || song.artist.equals(artistName, ignoreCase = true)
            }
        }.getOrDefault(emptyList())

        val merged = LinkedHashMap<String, Song>()
        serverTop.forEach { merged.putIfAbsent(it.id, it) }
        searchHits
            .sortedByDescending { it.playCount }
            .forEach { merged.putIfAbsent(it.id, it) }
        val result = merged.values.toList()
        return result.take(count)
    }

    suspend fun biography(artistName: String, serverBio: String?): String? {
        val cleaned = serverBio?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim()
        if (!cleaned.isNullOrBlank()) {
            return cleaned
        }
        return null
    }
}
