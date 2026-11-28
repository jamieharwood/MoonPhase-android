package io.github.jamieharwood.moonphase.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.jamieharwood.moonphase.Astro
import io.github.jamieharwood.moonphase.Snapshot
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** (key, label, group) – the numeric fields of the original SnapshotDocument. */
data class Metric(val key: String, val label: String, val group: String)

val METRICS = listOf(
    Metric("moonIlluminationPercent", "Illumination (%)", "Moon"),
    Metric("moonAgeDays", "Moon Age (days)", "Moon"),
    Metric("moonDistanceKm", "Moon Distance (km)", "Moon"),
    Metric("daysUntilFullMoon", "Days Until Full Moon", "Moon"),
    Metric("daylightHours", "Daylight Hours", "Moon"),
    Metric("sunDistanceAu", "Sun → Earth (AU)", "Solar System"),
    Metric("mercuryDistanceAu", "Sun → Mercury (AU)", "Solar System"),
    Metric("venusDistanceAu", "Sun → Venus (AU)", "Solar System"),
    Metric("marsDistanceAu", "Earth → Mars (AU)", "Solar System"),
    Metric("jupiterDistanceAu", "Earth → Jupiter (AU)", "Solar System"),
    Metric("saturnDistanceAu", "Earth → Saturn (AU)", "Solar System"),
    Metric("uranusDistanceAu", "Sun → Uranus (AU)", "Solar System"),
    Metric("neptuneDistanceAu", "Sun → Neptune (AU)", "Solar System"),
    Metric("plutoDistanceAu", "Sun → Pluto (AU)", "Solar System"),
    Metric("voyager1HelioDistanceAu", "Sun → Voyager 1 (AU)", "Solar System"),
    Metric("voyager2HelioDistanceAu", "Sun → Voyager 2 (AU)", "Solar System"),
    Metric("voyager1DistanceAu", "Earth → Voyager 1 (AU)", "Deep Space Probes"),
    Metric("voyager2DistanceAu", "Earth → Voyager 2 (AU)", "Deep Space Probes"),
    Metric("newHorizonsDistanceAu", "Earth → New Horizons (AU)", "Deep Space Probes"),
    Metric("earthSpeedKmPerSec", "Orbital Speed (km/s)", "Earth"),
    Metric("earthSpeedKmPerHour", "Orbital Speed (km/h)", "Earth"),
    Metric("earthAxialTiltDegrees", "Axial Tilt (°)", "Earth"),
    Metric("auroraKpIndex", "Aurora Kp Index", "Earth"),
    Metric("issAltitudeKm", "ISS Altitude (km)", "LEO"),
    Metric("tiangongAltitudeKm", "Tiangong Altitude (km)", "LEO"),
    Metric("hubbleAltitudeKm", "Hubble Altitude (km)", "LEO"),
    Metric("starlinkSatelliteCount", "Starlink Satellites", "LEO"),
    Metric("kuiperSatelliteCount", "Kuiper Satellites", "LEO"),
    Metric("totalSatellitesInOrbit", "Total in Orbit", "LEO"),
    Metric("issCrew", "ISS Crew", "LEO"),
    Metric("totalPeopleInSpace", "People in Space", "LEO"),
    Metric("daysUntilSummerSolstice", "Days to Summer Solstice", "Upcoming Events"),
    Metric("daysUntilWinterSolstice", "Days to Winter Solstice", "Upcoming Events"),
    Metric("daysUntilPerihelion", "Days to Perihelion", "Upcoming Events"),
    Metric("daysUntilAphelion", "Days to Aphelion", "Upcoming Events"),
)

data class Point(val time: Instant, val value: Double)

/** Start/end (epoch seconds) of the 12-hour UTC window containing [t]. */
fun halfDayWindow(t: ZonedDateTime): Pair<Long, Long> {
    val u = t.withZoneSameInstant(ZoneOffset.UTC)
    val start = u.withHour(if (u.hour < 12) 0 else 12).withMinute(0).withSecond(0).withNano(0)
    return start.toEpochSecond() to start.plusHours(12).toEpochSecond()
}

fun toCsv(metric: String, points: List<Point>): String = buildString {
    append("timestamp,").append(metric).append('\n')
    val fmt = DateTimeFormatter.ISO_INSTANT
    points.forEach { append(fmt.format(it.time)).append(',').append(it.value).append('\n') }
}

/** SQLite history; at most one snapshot per 12-hour UTC window, like the original app. */
class HistoryStore(context: Context) :
    SQLiteOpenHelper(context, "history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        val cols = METRICS.joinToString(", ") { "\"${it.key}\" REAL" }
        db.execSQL("CREATE TABLE snapshots (ts INTEGER PRIMARY KEY, $cols)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun save(s: Snapshot, at: ZonedDateTime = s.timestamp, dedupe: Boolean = true): Boolean {
        val db = writableDatabase
        val (start, end) = halfDayWindow(at)
        if (dedupe) {
            db.rawQuery("SELECT 1 FROM snapshots WHERE ts >= ? AND ts < ? LIMIT 1",
                arrayOf(start.toString(), end.toString())).use { if (it.moveToFirst()) return false }
        }
        val cv = ContentValues().apply {
            put("ts", at.toEpochSecond())
            s.metrics().forEach { (k, v) -> put(k, v) }
        }
        db.insertWithOnConflict("snapshots", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    @Synchronized
    fun query(metric: String, days: Int?): List<Point> {
        require(METRICS.any { it.key == metric })
        val since = days?.let { Instant.now().minusSeconds(it * 86_400L).epochSecond } ?: 0L
        val out = ArrayList<Point>()
        readableDatabase.rawQuery(
            "SELECT ts, \"$metric\" FROM snapshots WHERE ts >= ? AND \"$metric\" IS NOT NULL ORDER BY ts DESC LIMIT 500",
            arrayOf(since.toString()),
        ).use { c -> while (c.moveToNext()) out += Point(Instant.ofEpochSecond(c.getLong(0)), c.getDouble(1)) }
        return out.asReversed()
    }

    @Synchronized
    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM snapshots", null)
        .use { it.moveToFirst(); it.getLong(0) }

    @Synchronized
    fun clear() = writableDatabase.execSQL("DELETE FROM snapshots")

    @Synchronized
    fun prune(retentionDays: Int) {
        if (retentionDays > 0) {
            val cutoff = Instant.now().minusSeconds(retentionDays * 86_400L).epochSecond
            writableDatabase.execSQL("DELETE FROM snapshots WHERE ts < ?", arrayOf(cutoff))
        }
    }

    /** Backfill 00:01 and 12:01 UTC snapshots for the past [days] days (live values stored as 0). */
    fun backfill(days: Int, lat: Double, lon: Double): Int {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        var added = 0
        for (d in days.coerceIn(1, 365) downTo 1) {
            for (h in intArrayOf(0, 12)) {
                val t = now.minusDays(d.toLong()).withHour(h).withMinute(1).withSecond(0).withNano(0)
                val s = Astro.compute(t, lat, lon).copy(
                    live = io.github.jamieharwood.moonphase.LiveData(
                        kp = 0.0, issAltitudeKm = 0.0, tiangongAltitudeKm = 0.0, hubbleAltitudeKm = 0.0,
                        starlink = 0, kuiper = 0, totalInOrbit = 0, issCrew = 0, peopleInSpace = 0,
                    ),
                )
                if (save(s, t)) added++
            }
        }
        return added
    }
}
