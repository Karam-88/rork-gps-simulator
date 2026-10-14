package com.rork.gpssimulator.ui.map

import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.rork.gpssimulator.data.model.MapType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Legacy raster source metadata. The main map is rendered by Google Maps; the URLs
 * below remain only for old fallback/utility code that is not used by MapScreen.
 */
enum class TileSource(val id: String, val maxZoom: Int, val attribution: String) {
    STANDARD("legacy-standard", 19, "Google Maps"),
    SATELLITE("legacy-satellite", 18, "Google Maps"),
    TERRAIN("legacy-terrain", 16, "Google Maps"),
    DETAILED_STREETS("legacy-detailed", 19, "Google Maps");

    fun url(z: Int, x: Int, y: Int): String = when (this) {
        STANDARD -> "https://tile.openstreetmap.org/$z/$x/$y.png"
        SATELLITE ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        TERRAIN -> "https://tile.opentopomap.org/$z/$x/$y.png"
        DETAILED_STREETS -> "https://${"abc"[(x + y) % 3]}.tile.openstreetmap.fr/hot/$z/$x/$y.png"
    }

    companion object {
        fun of(type: MapType): TileSource = when (type) {
            MapType.STANDARD -> STANDARD
            MapType.SATELLITE -> SATELLITE
            MapType.TERRAIN -> TERRAIN
            MapType.DETAILED_STREETS -> DETAILED_STREETS
        }
    }
}

/** Legacy raster cache retained for fallback/utility code outside the primary map screen. */
class TileStore(private val scope: CoroutineScope) {

    private val cache = object : LruCache<String, ImageBitmap>(360) {}
    private val inFlight = mutableSetOf<String>()
    private val failed = mutableMapOf<String, Int>()
    private val retryScheduled = mutableSetOf<String>()
    private val semaphore = Semaphore(4)

    var version by mutableIntStateOf(0)
        private set

    fun tile(source: TileSource, z: Int, x: Int, y: Int): ImageBitmap? {
        val key = key(source, z, x, y)
        cache.get(key)?.let { return it }

        if (z >= 4) {
            val previewZ = z - 4
            prefetch(source, previewZ, x shr 4, y shr 4)
        }

        if (failed.getOrDefault(key, 0) >= MAX_RETRIES) return null
        request(source, z, x, y, key, countFailures = true)
        return null
    }

    fun cached(source: TileSource, z: Int, x: Int, y: Int): ImageBitmap? =
        cache.get(key(source, z, x, y))

    private fun prefetch(source: TileSource, z: Int, x: Int, y: Int) {
        val key = key(source, z, x, y)
        if (cache.get(key) != null || failed.getOrDefault(key, 0) >= MAX_RETRIES) return
        request(source, z, x, y, key, countFailures = false)
    }

    private fun request(
        source: TileSource,
        z: Int,
        x: Int,
        y: Int,
        key: String,
        countFailures: Boolean,
    ) {
        if (!inFlight.add(key)) return

        scope.launch {
            val bitmap = semaphore.withPermit { download(source.url(z, x, y)) }
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    cache.put(key, bitmap)
                    failed.remove(key)
                    retryScheduled.remove(key)
                    version++
                } else if (countFailures) {
                    val failureCount = failed.getOrDefault(key, 0) + 1
                    failed[key] = failureCount
                    version++

                    if (failureCount >= MAX_RETRIES && retryScheduled.add(key)) {
                        scheduleRetry(key)
                    }
                }
                inFlight.remove(key)
            }
        }
    }

    private fun scheduleRetry(key: String) {
        scope.launch {
            delay(RETRY_COOLDOWN_MS)
            withContext(Dispatchers.Main) {
                failed.remove(key)
                retryScheduled.remove(key)
                version++
            }
        }
    }

    private suspend fun download(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3_000
                readTimeout = 5_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/png,image/jpeg,image/*")
                setRequestProperty("Connection", "keep-alive")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            val bytes = connection.inputStream.use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            Log.d(TAG, "Tile download failed: ${e.javaClass.simpleName}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun key(source: TileSource, z: Int, x: Int, y: Int): String =
        "${source.id}/$z/$x/$y"

    companion object {
        private const val TAG = "TileStore"
        private const val MAX_RETRIES = 3
        private const val RETRY_COOLDOWN_MS = 2_000L
        private const val USER_AGENT = "GPSSimulator/1.0 (Android developer testing tool)"
    }
}
