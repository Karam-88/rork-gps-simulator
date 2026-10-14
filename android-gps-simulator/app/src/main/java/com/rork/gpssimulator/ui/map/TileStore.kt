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
 *
 * When a detailed tile is first requested we also prefetch its z-2 parent. The
 * renderer can stretch that parent as a temporary low-resolution fallback, so a
 * cold launch shows a recognizable map quickly instead of a blank background
 * while the street-level tiles are still arriving.
 */
class TileStore(private val scope: CoroutineScope) {

    private val cache = object : LruCache<String, ImageBitmap>(360) {}
    private val inFlight = mutableSetOf<String>()
    private val failed = mutableMapOf<String, Int>()
    private val retryScheduled = mutableSetOf<String>()
    private val semaphore = Semaphore(8)

    var version by mutableIntStateOf(0)
        private set

    /** Returns a cached tile, scheduling a download when it is missing. */
    fun tile(source: TileSource, z: Int, x: Int, y: Int): ImageBitmap? {
        val key = key(source, z, x, y)
        cache.get(key)?.let { return it }

        // On a cold start, ask for a much cheaper parent tile first. MapView's
        // existing fallback renderer will use it as soon as it arrives, while
        // this exact tile continues loading in parallel.
        if (z >= 2) {
            val parentZ = z - 2
            prefetch(source, parentZ, x shr 2, y shr 2)
        }

        if (failed.getOrDefault(key, 0) >= MAX_RETRIES) return null
        request(source, z, x, y, key, countFailures = true)
        return null
    }

    /** Looks up a tile without triggering a download (used for low-res fallback). */
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
    }

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
                // Do not leave the whole map blank for a long network timeout.
                // Failed tiles can retry automatically, so shorter timeouts give
                // a much better startup experience on weak/mobile connections.
                connectTimeout = 4_000
                readTimeout = 6_000
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

    private fun key(source: TileSource, z: Int, x: Int, y: Int): String =
        "${source.id}/$z/$x/$y"

    companion object {
        private const val TAG = "TileStore"
        private const val MAX_RETRIES = 3
        private const val RETRY_COOLDOWN_MS = 3_000L
        private const val USER_AGENT = "GPSSimulator/1.0 (Android developer testing tool)"
    }
}
