package app.auralis.music.data.waveform

import android.content.Context
import android.net.Uri
import app.auralis.music.data.download.DownloadStore
import app.auralis.music.data.remote.Song
import app.auralis.music.data.remote.SubsonicClient
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads / caches waveform peaks for Now Playing.
 *
 * Source order: offline [DownloadStore] file → authenticated Subsonic stream URL.
 * There is no OpenSubsonic/Navidrome peaks endpoint; client decode only.
 */
class WaveformRepository(
    context: Context,
    private val client: SubsonicClient,
    private val downloadStore: DownloadStore,
    private val generator: PeakGenerator = PeakGenerator(context.applicationContext),
    private val barCount: Int = PeakBuckets.DEFAULT_BAR_COUNT,
) {
    private val cacheDir = File(context.applicationContext.filesDir, "waveforms").apply { mkdirs() }
    private val memory = ConcurrentHashMap<String, FloatArray>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Returns cached or freshly decoded peaks for [song], or null on failure.
     * Safe to cancel mid-decode when the track changes.
     */
    suspend fun peaksFor(song: Song): FloatArray? = withContext(Dispatchers.IO) {
        val key = PeakBuckets.cacheKey(song.id, song.duration, song.size, barCount)
        memory[key]?.let { return@withContext it }
        readDisk(key)?.let {
            memory[key] = it
            return@withContext it
        }
        val mutex = locks.getOrPut(key) { Mutex() }
        mutex.withLock {
            memory[key]?.let { return@withLock it }
            readDisk(key)?.let {
                memory[key] = it
                return@withLock it
            }
            val uri = resolveUri(song) ?: return@withLock null
            val peaks = generator.generate(uri, barCount) ?: return@withLock null
            writeDisk(key, peaks)
            memory[key] = peaks
            peaks
        }
    }

    private fun resolveUri(song: Song): Uri? {
        val creds = client.credentials
        if (creds != null) {
            val serverKey = downloadStore.serverKey(creds)
            downloadStore.playbackUri(serverKey, song.id)?.let { return it }
        }
        return runCatching {
            Uri.parse(client.streamUrl(song.id, maxBitRate = 0))
        }.getOrNull()
    }

    private fun diskFile(key: String): File = File(cacheDir, "$key.awf")

    private fun readDisk(key: String): FloatArray? {
        val file = diskFile(key)
        if (!file.isFile || file.length() < 8) return null
        return runCatching {
            DataInputStream(FileInputStream(file)).use { input ->
                val magic = input.readInt()
                if (magic != MAGIC) return@use null
                val n = input.readInt()
                if (n !in PeakBuckets.MIN_BAR_COUNT..PeakBuckets.MAX_BAR_COUNT) return@use null
                FloatArray(n) { input.readFloat() }
            }
        }.getOrNull()
    }

    private fun writeDisk(key: String, peaks: FloatArray) {
        val file = diskFile(key)
        val tmp = File(cacheDir, "$key.awf.tmp")
        runCatching {
            DataOutputStream(FileOutputStream(tmp)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(peaks.size)
                for (v in peaks) out.writeFloat(v)
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure {
            tmp.delete()
        }
    }

    companion object {
        private const val MAGIC = 0x41574631 // "AWF1"
    }
}
