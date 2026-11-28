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
import java.net.URLEncoder
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Live feeds (port of AuroraKpFetcher / LeoDataFetcher / JamesWebbDistance). Every fetch
 * fails soft to -1. Cached in memory: Kp 15 minutes, James Webb 1 hour, LEO / crew 6 hours,
 * as in the original app.
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

    private const val HORIZONS_URL = "https://ssd.jpl.nasa.gov/api/horizons.api"

    private const val KP_TTL = 15 * 60 * 1000L
    private const val JWST_TTL = 60 * 60 * 1000L
    private const val LEO_TTL = 6 * 60 * 60 * 1000L

    private val mutex = Mutex()
    private var kpCache: Pair<Double, Long>? = null
    private var jwstCache: Pair<Double, Long>? = null
    private var leoCache: Pair<LiveData, Long>? = null

    suspend fun clear() = mutex.withLock { kpCache = null; jwstCache = null; leoCache = null }

    suspend fun fetch(): LiveData = mutex.withLock {
        val now = System.currentTimeMillis()
        val kp = kpCache?.takeIf { now - it.second < KP_TTL }?.first
            ?: parseLatestKp(get(KP_URL)).also { kpCache = it to now }
        // Only successful JWST fetches are cached, so a failure is retried next time.
        val jwst = jwstCache?.takeIf { now - it.second < JWST_TTL }?.first
            ?: parseJwstKm(get(jwstUrl(ZonedDateTime.now(ZoneOffset.UTC)), 20_000)).also { if (it > 0) jwstCache = it to now }
        val leo = leoCache?.takeIf { now - it.second < LEO_TTL }?.first
            ?: fetchLeo().also { leoCache = it to now }
        leo.copy(kp = kp, jwstKm = jwst)
    }

    /** JPL Horizons observer query: James Webb (target -170) range from Earth's centre. */
    fun jwstUrl(now: ZonedDateTime): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val params = linkedMapOf(
            "format" to "json",
            "COMMAND" to "'-170'",
            "OBJ_DATA" to "'NO'",
            "MAKE_EPHEM" to "'YES'",
            "EPHEM_TYPE" to "'OBSERVER'",
            "CENTER" to "'500@399'",
            "START_TIME" to "'${now.format(fmt)}'",
            "STOP_TIME" to "'${now.plusMinutes(1).format(fmt)}'",
            "STEP_SIZE" to "'1m'",
            "QUANTITIES" to "'20'",
            "CSV_FORMAT" to "'YES'",
        )
        return HORIZONS_URL + "?" + params.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
    }

    /**
     * Observer range (delta, AU) from a Horizons JSON response, in km; -1 if missing.
     * The ephemeris row looks like ` 2026-Nov-27 23:28, , ,  0.01136300827198,  0.0069837,`.
     */
    fun parseJwstKm(json: String?): Double = try {
        val result = JSONObject(json ?: return -1.0).optString("result", "")
        val soe = result.indexOf("\$\$SOE")
        val eoe = result.indexOf("\$\$EOE")
        if (soe < 0 || eoe < soe) -1.0 else {
            val row = result.substring(soe + 5, eoe).trim().lines().first()
            val deltaAu = row.split(",")[3].trim().toDouble()
            if (deltaAu > 0) deltaAu * Astro.KM_PER_AU else -1.0
        }
    } catch (e: Exception) {
        -1.0
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
