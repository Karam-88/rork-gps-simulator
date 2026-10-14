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

/** Raster tile endpoints used for each map type. */
enum class TileSource(val id: String, val maxZoom: Int, val attribution: String) {
    STANDARD("osm", 19, "© OpenStreetMap contributors"),
    SATELLITE("esri", 18, "© Esri, Maxar, Earthstar Geographics"),
    TERRAIN("topo", 16, "© OpenTopoMap (CC-BY-SA), data © OpenStreetMap contributors, SRTM"),

    /**
     * A more detailed street basemap than [STANDARD]: the Humanitarian
     * OpenStreetMap Team (HOT) style, which draws roads, buildings and place
     * labels more prominently. It is served free of charge and without an API
     * key by OpenStreetMap France, built directly on OpenStreetMap data, and
     * requires attribution: the compact credit below is shown in the map's
     * attribution chip, and the full wording is on the Licenses screen.
     * Tiles are spread over the a/b/c hosts by tile coordinate.
     */
    DETAILED_STREETS(
        "hot",
        19,
        "© OpenStreetMap contributors · Tiles: HOT, hosted by OSM France",
    );

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

/**
 * Downloads and caches map tiles in memory. Requests are de-duplicated and
 * throttled; [version] increments whenever tile state changes so the map redraws.
 */
class TileStore(private val scope: CoroutineScope) {

    private val cache = object : LruCache<String, ImageBitmap>(360) {}
    private val inFlight = mutableSetOf<String>()
    private val failed = mutableMapOf<String, Int>()
    private val retryScheduled = mutableSetOf<String>()
    private val semaphore = Semaphore(6)

    var version by mutableIntStateOf(0)
        private set

    /** Returns a cached tile, scheduling a download when it is missing. */
    fun tile(source: TileSource, z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "${source.id}/$z/$x/$y"
        cache.get(key)?.let { return it }
        if (failed.getOrDefault(key, 0) >= MAX_RETRIES) return null
        if (!inFlight.add(key)) return null

        scope.launch {
            val bitmap = semaphore.withPermit { download(source.url(z, x, y)) }
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    cache.put(key, bitmap)
                    failed.remove(key)
                    retryScheduled.remove(key)
                    version++
                } else {
                    val failureCount = failed.getOrDefault(key, 0) + 1
                    failed[key] = failureCount

                    // A failed request also needs to invalidate drawing; otherwise
                    // the same blank tile may never be requested again until the
                    // user pans or changes location.
                    version++

                    if (failureCount >= MAX_RETRIES && retryScheduled.add(key)) {
                        scheduleRetry(key)
                    }
                }
                inFlight.remove(key)
            }
        }
        return null
    }

    /** Looks up a tile without triggering a download (used for low-res fallback). */
    fun cached(source: TileSource, z: Int, x: Int, y: Int): ImageBitmap? =
        cache.get("${source.id}/$z/$x/$y")

    private fun scheduleRetry(key: String) {
        scope.launch {
            delay(RETRY_COOLDOWN_MS)
            withContext(Dispatchers.Main) {
                failed.remove(key)
                retryScheduled.remove(key)
                // Trigger a fresh draw so the tile is requested again even if
                // the map camera has not moved at all.
                version++
            }
        }
    }

    private suspend fun download(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/png,image/jpeg,image/*")
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

    companion object {
        private const val TAG = "TileStore"
        private const val MAX_RETRIES = 3
        private const val RETRY_COOLDOWN_MS = 5_000L
        private const val USER_AGENT = "GPSSimulator/1.0 (Android developer testing tool)"
    }
}
