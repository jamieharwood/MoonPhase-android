package io.github.jamieharwood.moonphase.data

import android.util.Log
import io.github.jamieharwood.moonphase.Astro
import io.github.jamieharwood.moonphase.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live feeds (port of AuroraKpFetcher / LeoDataFetcher). Every fetch fails soft to -1.
 * Cached in memory: Kp 15 minutes, LEO / crew 6 hours, as in the original app.
 */
object LiveFetcher {
    private const val TAG = "LiveFetcher"
    private const val UA = "MoonPhase-Android/1.0"
    const val KP_URL = "https://services.swpc.noaa.gov/json/planetary_k_index_1m.json"
    private const val TLE_URL = "https://tle.ivanstanojevic.me/api/tle/%d"
    private const val GROUP_URL = "https://tle.ivanstanojevic.me/api/tle/?search=%s&page-size=1"
    private const val TOTAL_URL = "https://tle.ivanstanojevic.me/api/tle/?page-size=1"
    private const val CREW_URL =
        "https://corquaid.github.io/international-space-station-APIs/JSON/people-in-space.json"

    private const val KP_TTL = 15 * 60 * 1000L
    private const val LEO_TTL = 6 * 60 * 60 * 1000L

    private val mutex = Mutex()
    private var kpCache: Pair<Double, Long>? = null
    private var leoCache: Pair<LiveData, Long>? = null

    suspend fun clear() = mutex.withLock { kpCache = null; leoCache = null }

    suspend fun fetch(): LiveData = mutex.withLock {
        val now = System.currentTimeMillis()
        val kp = kpCache?.takeIf { now - it.second < KP_TTL }?.first
            ?: parseLatestKp(get(KP_URL)).also { kpCache = it to now }
        val leo = leoCache?.takeIf { now - it.second < LEO_TTL }?.first
            ?: fetchLeo().also { leoCache = it to now }
        leo.copy(kp = kp)
    }

    private suspend fun fetchLeo(): LiveData = coroutineScope {
        // Crew feed is another host – fetch in parallel. The TLE API rejects concurrent
        // clients with HTTP 508, so its requests run one after another.
        val people = async(Dispatchers.IO) { parsePeople(get(CREW_URL, 10_000)) }
        val iss = altitude(25544)
        val tiangong = altitude(48274)
        val hubble = altitude(20580)
        val starlink = count(GROUP_URL.format("STARLINK"))
        val kuiper = count(GROUP_URL.format("KUIPER"))
        val total = count(TOTAL_URL)
        val p = people.await()
        LiveData(
            issAltitudeKm = iss, tiangongAltitudeKm = tiangong, hubbleAltitudeKm = hubble,
            starlink = starlink, kuiper = kuiper, totalInOrbit = total,
            issCrew = p.issCrew, peopleInSpace = p.total, crafts = p.crafts,
        )
    }

    private suspend fun altitude(norad: Int): Double {
        val body = get(TLE_URL.format(norad)) ?: return -1.0
        return try {
            Astro.altitudeFromTleLine2(JSONObject(body).getString("line2"))
        } catch (e: Exception) {
            -1.0
        }
    }

    private suspend fun count(url: String): Int {
        val body = get(url) ?: return -1
        return try {
            JSONObject(body).optInt("totalItems", -1)
        } catch (e: Exception) {
            -1
        }
    }

    private suspend fun get(url: String, timeoutMs: Int = 15_000, attempts: Int = 2): String? =
        withContext(Dispatchers.IO) {
            repeat(attempts) { attempt ->
                try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 10_000
                    c.readTimeout = timeoutMs
                    c.setRequestProperty("User-Agent", UA)
                    c.setRequestProperty("Accept", "application/json")
                    try {
                        if (c.responseCode in 200..299) return@withContext c.inputStream.bufferedReader().readText()
                    } finally {
                        c.disconnect()
                    }
                } catch (e: Exception) {
                    if (attempt == attempts - 1) Log.w(TAG, "fetch failed $url: $e")
                }
                if (attempt < attempts - 1) delay(1500)
            }
            null
        }

    fun parseLatestKp(json: String?): Double = try {
        val arr = JSONArray(json ?: return -1.0)
        if (arr.length() == 0) -1.0 else arr.getJSONObject(arr.length() - 1).optDouble("estimated_kp", -1.0)
    } catch (e: Exception) {
        -1.0
    }

    data class People(val total: Int = -1, val issCrew: Int = -1, val crafts: Map<String, Int> = emptyMap())

    fun parsePeople(json: String?): People = try {
        val root = JSONObject(json ?: return People())
        val total = root.optInt("number", -1)
        val arr = root.optJSONArray("people")
        if (arr == null) People(total, 0) else {
            val counts = linkedMapOf<String, Int>()
            val issCraft = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val craft = p.optString("spacecraft", "")
                if (craft.isNotEmpty()) {
                    counts[craft] = (counts[craft] ?: 0) + 1
                    if (p.optBoolean("iss", false)) issCraft += craft
                }
            }
            val crafts = counts.entries.sortedByDescending { it.value }
                .associate { (if (it.key in issCraft) "${it.key} ★" else it.key) to it.value }
            People(total, issCraft.sumOf { counts[it] ?: 0 }, crafts)
        }
    } catch (e: Exception) {
        People()
    }
}
