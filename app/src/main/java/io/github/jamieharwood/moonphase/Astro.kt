package io.github.jamieharwood.moonphase

import java.time.Duration
import java.time.LocalDate
import java.time.Year
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Deterministic astronomy calculations, ported from the `MoonPhaseModule` classes of the
 * original MoonPhase (Java / Spring Boot) project so the Android app shows the same numbers.
 * Pure Kotlin – no Android dependencies – so it is fully unit-testable on the JVM.
 */
object Astro {
    const val KM_PER_AU = 149_597_870.7
    const val C_KM_S = 299_792.458
    const val KM_PER_MILE = 1.60934
    const val DEFAULT_LATITUDE = 51.4769
    const val DEFAULT_LONGITUDE = 0.0

    private const val EARTH_M0 = 357.529
    private const val EARTH_N = 0.98560028

    fun toRad(d: Double) = Math.toRadians(d)
    fun normalizeAngle(deg: Double) = ((deg % 360.0) + 360.0) % 360.0

    fun julianDayNumber(day: Int, month: Int, year: Int): Long {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        val gregorian = year > 1582 || (year == 1582 && (month > 10 || (month == 10 && day >= 15)))
        return if (gregorian) day + (153 * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045
        else day + (153 * m + 2) / 5 + 365L * y + y / 4 - 32083
    }

    fun daysSinceJ2000(zdt: ZonedDateTime): Double {
        val t = zdt.withZoneSameInstant(ZoneOffset.UTC)
        val jdn = julianDayNumber(t.dayOfMonth, t.monthValue, t.year)
        val frac = (t.hour - 12) / 24.0 + t.minute / 1440.0 + t.second / 86400.0
        return jdn + frac - 2451545.0
    }

    // ── Moon phase ───────────────────────────────────────────────────────────

    const val SYNODIC_MONTH = 29.530588853
    private const val KNOWN_NEW_MOON_JD = 2451550.1

    val PHASE_NAMES = listOf(
        "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
        "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous",
    )
    val PHASE_ICONS = listOf("FullMoon", "wangmoon", "lqmoon", "wcmoon", "nwmoon", "wancrebmoon", "fqmoon", "wgmoon")
    val PHASE_ASCII = listOf(
        listOf("       _..._     ", "     .:::::::.   ", "    :::::::::::  ", "    :::::::::::  ", "    `:::::::::'  ", "      `':::''    "),
        listOf("       _..._     ", "     .::::. `.   ", "    :::::::.  :  ", "    ::::::::  :  ", "    `::::::' .'  ", "      `'::'-'    "),
        listOf("       _..._     ", "     .::::  `.   ", "    ::::::    :  ", "    ::::::    :  ", "    `:::::   .'  ", "      `'::.-'    "),
        listOf("       _..._     ", "     .::'   `.   ", "    :::       :  ", "    :::       :  ", "    `::.     .'  ", "      `':..-'    "),
        listOf("       _..._     ", "     .'     `.   ", "    :         :  ", "    :         :  ", "    `.       .'  ", "      `-...-'    "),
        listOf("       _..._     ", "     .'   `::.   ", "    :       :::  ", "    :       :::  ", "    `.     .::'  ", "      `-..:''    "),
        listOf("       _..._     ", "     .'  ::::.   ", "    :    ::::::  ", "    :    ::::::  ", "    `.   :::::'  ", "      `-.::''    "),
        listOf("       _..._     ", "     .' .::::.   ", "    :  ::::::::  ", "    :  ::::::::  ", "    `. '::::::'  ", "      `-.::''    "),
    )

    data class MoonPhase(val fraction: Double) {
        val ageDaysExact get() = fraction * SYNODIC_MONTH
        val ageDays get() = ageDaysExact.roundToInt()
        val illuminationPercent get() = ((1 - cos(2 * Math.PI * fraction)) / 2 * 100).roundToInt()
        val daysUntilFullMoon get() = (((0.5 - fraction + 1.0) % 1.0) * SYNODIC_MONTH).roundToInt()
        private val index: Int
            get() {
                val p = fraction
                return when {
                    p < 0.03 || p >= 0.97 -> 4
                    p < 0.22 -> 5
                    p < 0.28 -> 6
                    p < 0.47 -> 7
                    p < 0.53 -> 0
                    p < 0.72 -> 1
                    p < 0.78 -> 2
                    else -> 3
                }
            }
        val name get() = PHASE_NAMES[index]
        val icon get() = PHASE_ICONS[index]
        val ascii get() = PHASE_ASCII[index]

        companion object {
            fun at(zdt: ZonedDateTime): MoonPhase {
                val t = zdt.withZoneSameInstant(ZoneOffset.UTC)
                val noonJd = julianDayNumber(t.dayOfMonth, t.monthValue, t.year).toDouble()
                val frac = (t.hour + t.minute / 60.0 + t.second / 3600.0) / 24.0
                return fromJulianDate(noonJd - 0.5 + frac)
            }

            fun fromJulianDate(jd: Double): MoonPhase {
                var p = (jd - KNOWN_NEW_MOON_JD) / SYNODIC_MONTH
                p -= floor(p)
                return MoonPhase(p)
            }
        }
    }

    // ── Distances ────────────────────────────────────────────────────────────

    fun sunDistanceAu(zdt: ZonedDateTime): Double {
        val m = toRad(normalizeAngle(EARTH_M0 + EARTH_N * daysSinceJ2000(zdt)))
        return 1.00014 - 0.01671 * cos(m) - 0.00014 * cos(2 * m)
    }

    fun moonDistanceKm(zdt: ZonedDateTime): Double {
        val m = normalizeAngle(134.9634 + 13.0649929509 * daysSinceJ2000(zdt))
        return 384_400.0 - 21_000.0 * cos(toRad(m))
    }

    data class Planet(
        val name: String, val a: Double, val e: Double,
        val m0: Double, val n: Double, val lonPerihelion: Double,
    ) {
        fun heliocentricAu(zdt: ZonedDateTime): Double {
            val m = normalizeAngle(m0 + n * daysSinceJ2000(zdt))
            return a * (1.0 - e * cos(toRad(m)))
        }

        fun geocentricAu(zdt: ZonedDateTime): Double {
            val d = daysSinceJ2000(zdt)
            val rEarth = sunDistanceAu(zdt)
            val longEarth = toRad(normalizeAngle(EARTH_M0 + EARTH_N * d) + 102.93735)
            val mPlanet = normalizeAngle(m0 + n * d)
            val rPlanet = a * (1.0 - e * cos(toRad(mPlanet)))
            val longPlanet = toRad(mPlanet + lonPerihelion)
            val delta = abs(longPlanet - longEarth)
            return sqrt(rEarth * rEarth + rPlanet * rPlanet - 2.0 * rEarth * rPlanet * cos(delta))
        }
    }

    val MERCURY = Planet("Mercury", 0.38709927, 0.20563593, 174.7947670, 4.09233445, 29.12427)
    val VENUS = Planet("Venus", 0.72333566, 0.00677672, 50.37663, 1.60213034, 54.85229)
    val MARS = Planet("Mars", 1.523679, 0.0934, 19.3870, 0.5240207766, 336.04084)
    val JUPITER = Planet("Jupiter", 5.2026, 0.0489, 20.0202, 0.0831294, 14.75385)
    val SATURN = Planet("Saturn", 9.5549, 0.0557, 317.0207, 0.0334442, 92.43194)
    val URANUS = Planet("Uranus", 19.18916464, 0.04716771, 142.238599, 0.01176904, 96.93735)
    val NEPTUNE = Planet("Neptune", 30.06992276, 0.00858587, 256.228347, 0.00598103, 276.33630)
    val PLUTO = Planet("Pluto", 39.48211675, 0.24882730, 14.53, 0.003975, 224.0)

    private val PROBE_EPOCH: ZonedDateTime = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    private val JWST_EPOCH: ZonedDateTime = ZonedDateTime.of(2022, 1, 24, 0, 0, 0, 0, ZoneOffset.UTC)

    private fun auPerDay(kmS: Double) = kmS * 86_400.0 / KM_PER_AU
    private fun daysSince(epoch: ZonedDateTime, zdt: ZonedDateTime) =
        Duration.between(epoch, zdt.withZoneSameInstant(ZoneOffset.UTC)).seconds / 86_400.0

    fun voyager1HelioAu(z: ZonedDateTime) = 159.0 + auPerDay(17.0) * daysSince(PROBE_EPOCH, z)
    fun voyager2HelioAu(z: ZonedDateTime) = 133.0 + auPerDay(15.4) * daysSince(PROBE_EPOCH, z)
    fun newHorizonsHelioAu(z: ZonedDateTime) = 58.0 + auPerDay(13.8) * daysSince(PROBE_EPOCH, z)
    fun fromEarthAu(helio: Double) = max(0.0, abs(helio - 1.0))
    fun jamesWebbKm(z: ZonedDateTime) =
        1_500_000.0 + 500_000.0 * sin(2.0 * Math.PI * daysSince(JWST_EPOCH, z) / 180.0)

    // ── Earth ────────────────────────────────────────────────────────────────

    fun earthSpeedKmS(sunAu: Double): Double {
        val rKm = sunAu * KM_PER_AU
        return sqrt(1.32712440018e11 * (2.0 / rKm - 1.0 / KM_PER_AU))
    }

    fun axialTiltDeg(z: ZonedDateTime) = 23.439291 - 0.013004 * (daysSinceJ2000(z) / 36525.0)

    private const val TILT = 23.44
    private fun declination(d: LocalDate) =
        toRad(TILT) * sin(2.0 * Math.PI * (284 + d.dayOfYear) / Year.of(d.year).length())

    fun dayLengthHours(d: LocalDate, latitude: Double): Double {
        val x = -tan(toRad(latitude)) * tan(declination(d))
        return when {
            x >= 1.0 -> 0.0
            x <= -1.0 -> 24.0
            else -> (24.0 / Math.PI) * acos(x)
        }
    }

    private fun formatHour(hIn: Double): String {
        val h = ((hIn % 24) + 24) % 24
        var hours = h.toInt()
        var minutes = ((h - hours) * 60).roundToInt()
        if (minutes >= 60) { hours++; minutes -= 60 }
        if (hours >= 24) hours -= 24
        return "%02d:%02d".format(hours, minutes)
    }

    fun sunriseSunsetUtc(d: LocalDate, latitude: Double, longitude: Double): Pair<String, String> {
        val x = -tan(toRad(latitude)) * tan(declination(d))
        if (x >= 1.0) return "Polar Night" to "Polar Night"
        if (x <= -1.0) return "Midnight Sun" to "Midnight Sun"
        val half = acos(x) * (12.0 / Math.PI)
        val b = toRad(360.0 / 365.0 * (d.dayOfYear - 81))
        val eot = (9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)) / 60.0
        val noon = 12.0 - longitude / 15.0 - eot
        return formatHour(noon - half) to formatHour(noon + half)
    }

    // ── Light time ───────────────────────────────────────────────────────────

    fun lightSeconds(au: Double) = au * KM_PER_AU / C_KM_S

    fun formatLightTime(au: Double): String {
        val total = lightSeconds(au)
        if (total < 60) return "%.0fs".format(total)
        val minutes = (total / 60.0).toLong()
        val secs = (total % 60.0).toLong()
        return if (minutes < 60) "${minutes}m ${secs}s" else "${minutes / 60}h ${minutes % 60}m"
    }

    // ── Events ───────────────────────────────────────────────────────────────

    fun daysUntil(from: LocalDate, month: Int, day: Int): Long {
        var c = LocalDate.of(from.year, month, day)
        if (!from.isBefore(c)) c = c.plusYears(1)
        return ChronoUnit.DAYS.between(from, c)
    }

    fun kpActivity(kp: Double) = when {
        kp < 0 -> "Unavailable"
        kp < 2 -> "Quiet"
        kp < 4 -> "Unsettled"
        kp < 5 -> "Active"
        else -> "Storm ⚡"
    }

    fun validLat(v: Double) = if (v in -90.0..90.0) v else DEFAULT_LATITUDE
    fun validLon(v: Double) = if (v in -180.0..180.0) v else DEFAULT_LONGITUDE

    /** Compute every deterministic value (no network). Live fields keep their "unavailable" defaults. */
    fun compute(
        now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
        latitude: Double = DEFAULT_LATITUDE,
        longitude: Double = DEFAULT_LONGITUDE,
    ): Snapshot {
        val t = now.withZoneSameInstant(ZoneOffset.UTC)
        val lat = validLat(latitude)
        val lon = validLon(longitude)
        val today = t.toLocalDate()
        val mp = MoonPhase.at(t)
        val sun = sunDistanceAu(t)
        val v1h = voyager1HelioAu(t)
        val v2h = voyager2HelioAu(t)
        val helio = linkedMapOf(
            "Mercury" to MERCURY.heliocentricAu(t),
            "Venus" to VENUS.heliocentricAu(t),
            "Earth" to sun,
            "Mars" to MARS.heliocentricAu(t),
            "Jupiter" to JUPITER.heliocentricAu(t),
            "Saturn" to SATURN.heliocentricAu(t),
            "Uranus" to URANUS.heliocentricAu(t),
            "Neptune" to NEPTUNE.heliocentricAu(t),
            "Pluto" to PLUTO.heliocentricAu(t),
        )
        val (sunrise, sunset) = sunriseSunsetUtc(today, lat, lon)
        val speed = earthSpeedKmS(sun)
        return Snapshot(
            timestamp = t,
            latitude = lat,
            longitude = lon,
            moonPhaseName = mp.name,
            moonPhaseIcon = mp.icon,
            moonAscii = mp.ascii,
            moonPhaseFraction = mp.fraction,
            moonIlluminationPercent = mp.illuminationPercent,
            moonAgeDays = mp.ageDays,
            daysUntilFullMoon = mp.daysUntilFullMoon,
            moonDistanceKm = moonDistanceKm(t),
            heliocentricAu = helio,
            lightTimes = helio.mapValues { formatLightTime(it.value) } +
                mapOf("Voyager 1" to formatLightTime(v1h), "Voyager 2" to formatLightTime(v2h)),
            marsDistanceAu = MARS.geocentricAu(t),
            jupiterDistanceAu = JUPITER.geocentricAu(t),
            saturnDistanceAu = SATURN.geocentricAu(t),
            voyager1HelioAu = v1h,
            voyager2HelioAu = v2h,
            voyager1DistanceAu = fromEarthAu(v1h),
            voyager2DistanceAu = fromEarthAu(v2h),
            newHorizonsDistanceAu = fromEarthAu(newHorizonsHelioAu(t)),
            jamesWebbDistanceKm = jamesWebbKm(t),
            earthSpeedKmPerSec = speed,
            earthSpeedKmPerHour = speed * 3600.0,
            earthAxialTiltDegrees = axialTiltDeg(t),
            daylightHours = dayLengthHours(today, lat),
            sunriseTime = sunrise,
            sunsetTime = sunset,
            daysUntilSummerSolstice = daysUntil(today, 6, 21),
            daysUntilWinterSolstice = daysUntil(today, 12, 21),
            daysUntilPerihelion = daysUntil(today, 1, 3),
            daysUntilAphelion = daysUntil(today, 7, 4),
        )
    }

    /** Orbit altitude (km) from TLE line 2 mean motion via Kepler's third law. */
    fun altitudeFromTleLine2(line2: String): Double {
        val meanMotion = line2.substring(52, 63).trim().toDouble()
        val period = 86400.0 / meanMotion
        val smaM = cbrt(3.986004418e14 * Math.pow(period / (2 * Math.PI), 2.0))
        return smaM / 1000.0 - 6371.0
    }
}

/** All dashboard values at an instant. Names follow the original SnapshotDocument. */
data class Snapshot(
    val timestamp: ZonedDateTime,
    val latitude: Double,
    val longitude: Double,
    val moonPhaseName: String,
    val moonPhaseIcon: String,
    val moonAscii: List<String>,
    val moonPhaseFraction: Double,
    val moonIlluminationPercent: Int,
    val moonAgeDays: Int,
    val daysUntilFullMoon: Int,
    val moonDistanceKm: Double,
    val heliocentricAu: Map<String, Double>,
    val lightTimes: Map<String, String>,
    val marsDistanceAu: Double,
    val jupiterDistanceAu: Double,
    val saturnDistanceAu: Double,
    val voyager1HelioAu: Double,
    val voyager2HelioAu: Double,
    val voyager1DistanceAu: Double,
    val voyager2DistanceAu: Double,
    val newHorizonsDistanceAu: Double,
    val jamesWebbDistanceKm: Double,
    val earthSpeedKmPerSec: Double,
    val earthSpeedKmPerHour: Double,
    val earthAxialTiltDegrees: Double,
    val daylightHours: Double,
    val sunriseTime: String,
    val sunsetTime: String,
    val daysUntilSummerSolstice: Long,
    val daysUntilWinterSolstice: Long,
    val daysUntilPerihelion: Long,
    val daysUntilAphelion: Long,
    val live: LiveData = LiveData(),
) {
    /** Numeric metrics stored in history (same keys as the original app / MongoDB schema). */
    fun metrics(): Map<String, Double> = linkedMapOf(
        "moonIlluminationPercent" to moonIlluminationPercent.toDouble(),
        "moonAgeDays" to moonAgeDays.toDouble(),
        "moonDistanceKm" to moonDistanceKm,
        "daysUntilFullMoon" to daysUntilFullMoon.toDouble(),
        "daylightHours" to daylightHours,
        "sunDistanceAu" to heliocentricAu.getValue("Earth"),
        "mercuryDistanceAu" to heliocentricAu.getValue("Mercury"),
        "venusDistanceAu" to heliocentricAu.getValue("Venus"),
        "marsDistanceAu" to marsDistanceAu,
        "jupiterDistanceAu" to jupiterDistanceAu,
        "saturnDistanceAu" to saturnDistanceAu,
        "uranusDistanceAu" to heliocentricAu.getValue("Uranus"),
        "neptuneDistanceAu" to heliocentricAu.getValue("Neptune"),
        "plutoDistanceAu" to heliocentricAu.getValue("Pluto"),
        "voyager1HelioDistanceAu" to voyager1HelioAu,
        "voyager2HelioDistanceAu" to voyager2HelioAu,
        "voyager1DistanceAu" to voyager1DistanceAu,
        "voyager2DistanceAu" to voyager2DistanceAu,
        "newHorizonsDistanceAu" to newHorizonsDistanceAu,
        "earthSpeedKmPerSec" to earthSpeedKmPerSec,
        "earthSpeedKmPerHour" to earthSpeedKmPerHour,
        "earthAxialTiltDegrees" to earthAxialTiltDegrees,
        "auroraKpIndex" to live.kp,
        "issAltitudeKm" to live.issAltitudeKm,
        "tiangongAltitudeKm" to live.tiangongAltitudeKm,
        "hubbleAltitudeKm" to live.hubbleAltitudeKm,
        "starlinkSatelliteCount" to live.starlink.toDouble(),
        "kuiperSatelliteCount" to live.kuiper.toDouble(),
        "totalSatellitesInOrbit" to live.totalInOrbit.toDouble(),
        "issCrew" to live.issCrew.toDouble(),
        "totalPeopleInSpace" to live.peopleInSpace.toDouble(),
        "daysUntilSummerSolstice" to daysUntilSummerSolstice.toDouble(),
        "daysUntilWinterSolstice" to daysUntilWinterSolstice.toDouble(),
        "daysUntilPerihelion" to daysUntilPerihelion.toDouble(),
        "daysUntilAphelion" to daysUntilAphelion.toDouble(),
    )
}

/** Network-sourced values; -1 means unavailable. */
data class LiveData(
    val kp: Double = -1.0,
    val issAltitudeKm: Double = -1.0,
    val tiangongAltitudeKm: Double = -1.0,
    val hubbleAltitudeKm: Double = -1.0,
    val starlink: Int = -1,
    val kuiper: Int = -1,
    val totalInOrbit: Int = -1,
    val issCrew: Int = -1,
    val peopleInSpace: Int = -1,
    val crafts: Map<String, Int> = emptyMap(),
)
