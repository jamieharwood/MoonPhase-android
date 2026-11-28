package io.github.jamieharwood.moonphase

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
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

    // ── Julian dates and orbit helpers ───────────────────────────────────────

    private const val JD_UNIX_EPOCH = 2440587.5
    private const val JD_J2000 = 2451545.0

    /** Julian Date (UT) of an instant. */
    fun julianDate(zdt: ZonedDateTime): Double = JD_UNIX_EPOCH + zdt.toInstant().toEpochMilli() / 86_400_000.0

    private fun centuriesSinceJ2000(jd: Double) = (jd - JD_J2000) / 36525.0

    fun dateTimeFromJd(jd: Double): ZonedDateTime =
        java.time.Instant.ofEpochMilli(Math.round((jd - JD_UNIX_EPOCH) * 86_400_000.0)).atZone(ZoneOffset.UTC).withNano(0)

    /** Solve Kepler's equation E − e·sin E = M (radians) by Newton iteration. */
    fun solveKepler(m: Double, e: Double): Double {
        var ecc = m + e * sin(m)
        for (k in 0 until 30) {
            val delta = (ecc - e * sin(ecc) - m) / (1.0 - e * cos(ecc))
            ecc -= delta
            if (abs(delta) < 1e-12) break
        }
        return ecc
    }

    private fun norm(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    private fun distanceBetween(p: DoubleArray, q: DoubleArray): Double {
        val dx = p[0] - q[0]; val dy = p[1] - q[1]; val dz = p[2] - q[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    private fun sind(deg: Double) = sin(toRad(deg))
    private fun cosd(deg: Double) = cos(toRad(deg))

    // ── Moon and Sun ephemeris ───────────────────────────────────────────────
    //
    // Paul Schlyter, "How to compute planetary positions" (stjarnhimlen.se/comp/ppcomp.html):
    // Keplerian lunar elements plus the principal perturbations. Longitude is good to
    // ~1–2 arcmin and distance to a few hundred km, enough to place new and full moon
    // within minutes. The same model as the MoonPhase web app (MoonEphemeris.java).

    private const val EARTH_RADIUS_KM = 6378.14

    /** Mean synodic month; only seeds root-finding and the lunar-cycle bar. */
    const val SYNODIC_MONTH = 29.530588853

    /** Geocentric ecliptic-of-date positions; angles in degrees. */
    data class MoonSunPosition(
        val moonLon: Double, val moonLat: Double, val moonDistanceKm: Double,
        val sunLon: Double, val sunDistanceKm: Double,
    ) {
        /** Moon − Sun ecliptic longitude in [0, 360): 0 = new moon, 180 = full moon. */
        val elongationLongitude get() = normalizeAngle(moonLon - sunLon)

        /** Fraction of the Moon's disc that is lit, 0–1. */
        val illuminatedFraction: Double
            get() {
                val cosElong = cosd(moonLat) * cosd(moonLon - sunLon)
                val elong = acos(cosElong.coerceIn(-1.0, 1.0))
                val phaseAngle = atan2(sunDistanceKm * sin(elong), moonDistanceKm - sunDistanceKm * cos(elong))
                return (1.0 + cos(phaseAngle)) / 2.0
            }
    }

    fun moonSunPosition(jd: Double): MoonSunPosition {
        val d = jd - 2451543.5 // Schlyter's day number: 2000 Jan 0.0 UT
        // Sun
        val ws = 282.9404 + 4.70935e-5 * d
        val es = 0.016709 - 1.151e-9 * d
        val ms = normalizeAngle(356.0470 + 0.9856002585 * d)
        val eSun = toRad(ms) + es * sind(ms) * (1.0 + es * cosd(ms))
        val xs = cos(eSun) - es
        val ys = sqrt(1.0 - es * es) * sin(eSun)
        val sunDistAu = sqrt(xs * xs + ys * ys)
        val sunLon = normalizeAngle(Math.toDegrees(atan2(ys, xs)) + ws)
        // Moon orbital elements
        val node = 125.1228 - 0.0529538083 * d
        val inc = 5.1454
        val w = 318.0634 + 0.1643573223 * d
        val a = 60.2666 // Earth radii
        val e = 0.054900
        val mm = normalizeAngle(115.3654 + 13.0649929509 * d)
        val ecc = solveKepler(toRad(mm), e)
        val xv = a * (cos(ecc) - e)
        val yv = a * sqrt(1.0 - e * e) * sin(ecc)
        val v = Math.toDegrees(atan2(yv, xv))
        var r = sqrt(xv * xv + yv * yv)
        val vw = v + w
        val xh = r * (cosd(node) * cosd(vw) - sind(node) * sind(vw) * cosd(inc))
        val yh = r * (sind(node) * cosd(vw) + cosd(node) * sind(vw) * cosd(inc))
        val zh = r * (sind(vw) * sind(inc))
        var lon = Math.toDegrees(atan2(yh, xh))
        var lat = Math.toDegrees(atan2(zh, sqrt(xh * xh + yh * yh)))
        // Perturbations
        val ls = ms + ws          // Sun mean longitude
        val lm = mm + w + node    // Moon mean longitude
        val dd = lm - ls          // mean elongation
        val f = lm - node         // argument of latitude
        lon += -1.274 * sind(mm - 2 * dd) +   // evection
            0.658 * sind(2 * dd) -            // variation
            0.186 * sind(ms) -                // yearly equation
            0.059 * sind(2 * mm - 2 * dd) -
            0.057 * sind(mm - 2 * dd + ms) +
            0.053 * sind(mm + 2 * dd) +
            0.046 * sind(2 * dd - ms) +
            0.041 * sind(mm - ms) -
            0.035 * sind(dd) -                // parallactic equation
            0.031 * sind(mm + ms) -
            0.015 * sind(2 * f - 2 * dd) +
            0.011 * sind(mm - 4 * dd)
        lat += -0.173 * sind(f - 2 * dd) -
            0.055 * sind(mm - f - 2 * dd) -
            0.046 * sind(mm + f - 2 * dd) +
            0.033 * sind(f + 2 * dd) +
            0.017 * sind(2 * mm + f)
        r += -0.58 * cosd(mm - 2 * dd) - 0.46 * cosd(2 * dd)
        return MoonSunPosition(normalizeAngle(lon), lat, r * EARTH_RADIUS_KM, sunLon, sunDistAu * KM_PER_AU)
    }

    /** Newton iteration on the Moon − Sun longitude (≈12.19°/day). */
    private fun refinePhase(start: Double, targetDeg: Double): Double {
        var t = start
        for (k in 0 until 8) {
            var diff = normalizeAngle(moonSunPosition(t).elongationLongitude - targetDeg)
            if (diff > 180.0) diff -= 360.0
            var rate = moonSunPosition(t + 0.01).elongationLongitude - moonSunPosition(t - 0.01).elongationLongitude
            if (rate < -180.0) rate += 360.0
            if (rate > 180.0) rate -= 360.0
            val step = diff / (rate / 0.02)
            t -= step
            if (abs(step) < 1e-5) break // < 1 second
        }
        return t
    }

    /** JD of the next moment after [jd] when Moon − Sun longitude = [targetDeg] (0 new, 180 full). */
    fun nextPhaseJd(jd: Double, targetDeg: Double): Double {
        val behind = normalizeAngle(targetDeg - moonSunPosition(jd).elongationLongitude)
        var t = refinePhase(jd + behind / 360.0 * SYNODIC_MONTH, targetDeg)
        if (t <= jd) t = refinePhase(t + SYNODIC_MONTH, targetDeg)
        return t
    }

    /** JD of the most recent moment at or before [jd] with the given elongation. */
    fun previousPhaseJd(jd: Double, targetDeg: Double): Double {
        val ahead = normalizeAngle(moonSunPosition(jd).elongationLongitude - targetDeg)
        var t = refinePhase(jd - ahead / 360.0 * SYNODIC_MONTH, targetDeg)
        if (t > jd) t = refinePhase(t - SYNODIC_MONTH, targetDeg)
        return t
    }

    // ── Moon phase ───────────────────────────────────────────────────────────

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

    /**
     * True phase from the geocentric Sun and Moon positions, not a mean 29.53-day cycle,
     * so new and full moon land within minutes of the published times.
     *
     * @param fraction Moon − Sun longitude / 360°: 0 = new, 0.5 = full
     * @param ageDaysExact days since the previous true new moon
     * @param illumination lit fraction of the disc, 0–1
     * @param jd the moment of the calculation
     */
    data class MoonPhase(val fraction: Double, val ageDaysExact: Double, val illumination: Double, val jd: Double) {
        val ageDays get() = ageDaysExact.roundToInt()
        val illuminationPercent get() = (illumination * 100).roundToInt()

        /** Days until the next full moon; 0 means within the last half day ("tonight"). */
        val daysUntilFullMoon: Int
            get() = if (jd - previousPhaseJd(jd, 180.0) < 0.5) 0
            else (nextPhaseJd(jd, 180.0) - jd).roundToInt()

        val nextNewMoon: ZonedDateTime get() = dateTimeFromJd(nextPhaseJd(jd, 0.0))
        val nextFullMoon: ZonedDateTime get() = dateTimeFromJd(nextPhaseJd(jd, 180.0))

        // Boundaries follow moonphases.co.uk (phase 0 = new moon), so a 95% moon is still
        // waxing gibbous rather than full.
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
            fun at(zdt: ZonedDateTime): MoonPhase = fromJulianDate(julianDate(zdt))

            fun fromJulianDate(jd: Double): MoonPhase {
                val pos = moonSunPosition(jd)
                return MoonPhase(pos.elongationLongitude / 360.0, jd - previousPhaseJd(jd, 0.0), pos.illuminatedFraction, jd)
            }
        }
    }

    // ── Distances ────────────────────────────────────────────────────────────

    fun sunDistanceAu(zdt: ZonedDateTime): Double {
        val m = toRad(normalizeAngle(EARTH_M0 + EARTH_N * daysSinceJ2000(zdt)))
        return 1.00014 - 0.01671 * cos(m) - 0.00014 * cos(2 * m)
    }

    /** Earth–Moon distance (centre to centre) from the lunar ephemeris. */
    fun moonDistanceKm(zdt: ZonedDateTime): Double = moonSunPosition(julianDate(zdt)).moonDistanceKm

    // ── Planets ──────────────────────────────────────────────────────────────
    //
    // JPL "Approximate Positions of the Planets" (E.M. Standish):
    // https://ssd.jpl.nasa.gov/planets/approx_pos.html
    // Each element (a, e, I, L, long. perihelion, long. node) is a value at J2000 plus
    // a rate per Julian century. Table 1 is used for 1800–2050 and Table 2a/2b (with the
    // extra mean-anomaly terms for Jupiter–Neptune) outside that range. Kepler's equation
    // is solved fully and positions are 3-D (J2000 ecliptic): good to ~0.001–0.01 AU.

    class Planet(
        val name: String,
        private val table1: DoubleArray,
        private val table2: DoubleArray? = null,
        private val table2b: DoubleArray? = null,
    ) {
        /** Heliocentric position in AU, J2000 ecliptic frame. */
        fun heliocentricPosition(zdt: ZonedDateTime): DoubleArray {
            val utc = zdt.withZoneSameInstant(ZoneOffset.UTC)
            val t = centuriesSinceJ2000(julianDate(utc))
            val useTable1 = table2 == null || utc.year in 1800..2050
            val el = if (useTable1) table1 else table2!!
            val a = el[0] + el[6] * t
            val e = el[1] + el[7] * t
            val inc = el[2] + el[8] * t
            val meanLong = el[3] + el[9] * t
            val varpi = el[4] + el[10] * t
            val node = el[5] + el[11] * t
            val argPeri = varpi - node
            var meanAnomaly = meanLong - varpi
            if (!useTable1 && table2b != null) {
                val (b, c, s, f) = table2b.toList()
                meanAnomaly += b * t * t + c * cosd(f * t) + s * sind(f * t)
            }
            var m = normalizeAngle(meanAnomaly)
            if (m > 180.0) m -= 360.0
            val ecc = solveKepler(toRad(m), e)
            val xp = a * (cos(ecc) - e)
            val yp = a * sqrt(1.0 - e * e) * sin(ecc)
            val cw = cosd(argPeri); val sw = sind(argPeri)
            val co = cosd(node); val so = sind(node)
            val ci = cosd(inc); val si = sind(inc)
            return doubleArrayOf(
                (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp,
                (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp,
                (sw * si) * xp + (cw * si) * yp,
            )
        }

        fun heliocentricAu(zdt: ZonedDateTime) = norm(heliocentricPosition(zdt))

        /** Distance from Earth (the Earth–Moon barycentre, ≤ 4,700 km off). */
        fun geocentricAu(zdt: ZonedDateTime) = distanceBetween(heliocentricPosition(zdt), EARTH.heliocentricPosition(zdt))
    }

    val MERCURY = Planet("Mercury",
        doubleArrayOf(0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
            0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081),
        doubleArrayOf(0.38709843, 0.20563661, 7.00559432, 252.25166724, 77.45771895, 48.33961819,
            0.00000000, 0.00002123, -0.00590158, 149472.67486623, 0.15940013, -0.12214182))
    val VENUS = Planet("Venus",
        doubleArrayOf(0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
            0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418),
        doubleArrayOf(0.72332102, 0.00676399, 3.39777545, 181.97970850, 131.76755713, 76.67261496,
            -0.00000026, -0.00005107, 0.00043494, 58517.81560260, 0.05679648, -0.27274174))
    /** Earth–Moon barycentre. */
    val EARTH = Planet("Earth",
        doubleArrayOf(1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
            0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0),
        doubleArrayOf(1.00000018, 0.01673163, -0.00054346, 100.46691572, 102.93005885, -5.11260389,
            -0.00000003, -0.00003661, -0.01337178, 35999.37306329, 0.31795260, -0.24123856))
    val MARS = Planet("Mars",
        doubleArrayOf(1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
            0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343),
        doubleArrayOf(1.52371243, 0.09336511, 1.85181869, -4.56813164, -23.91744784, 49.71320984,
            0.00000097, 0.00009149, -0.00724757, 19140.29934243, 0.45223625, -0.26852431))
    val JUPITER = Planet("Jupiter",
        doubleArrayOf(5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
            -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106),
        doubleArrayOf(5.20248019, 0.04853590, 1.29861416, 34.33479152, 14.27495244, 100.29282654,
            -0.00002864, 0.00018026, -0.00322699, 3034.90371757, 0.18199196, 0.13024619),
        doubleArrayOf(-0.00012452, 0.06064060, -0.35635438, 38.35125000))
    val SATURN = Planet("Saturn",
        doubleArrayOf(9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
            -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794),
        doubleArrayOf(9.54149883, 0.05550825, 2.49424102, 50.07571329, 92.86136063, 113.63998702,
            -0.00003065, -0.00032044, 0.00451969, 1222.11494724, 0.54179478, -0.25015002),
        doubleArrayOf(0.00025899, -0.13434469, 0.87320147, 38.35125000))
    val URANUS = Planet("Uranus",
        doubleArrayOf(19.18916464, 0.04725744, 0.77263783, 313.23810451, 170.95427630, 74.01692503,
            -0.00196176, -0.00004397, -0.00242939, 428.48202785, 0.40805281, 0.04240589),
        doubleArrayOf(19.18797948, 0.04685740, 0.77298127, 314.20276625, 172.43404441, 73.96250215,
            -0.00020455, -0.00001550, -0.00180155, 428.49512595, 0.09266985, 0.05739699),
        doubleArrayOf(0.00058331, -0.97731848, 0.17689245, 7.67025000))
    val NEPTUNE = Planet("Neptune",
        doubleArrayOf(30.06992276, 0.00859048, 1.77004347, -55.12002969, 44.96476227, 131.78422574,
            0.00026291, 0.00005105, 0.00035372, 218.45945325, -0.32241464, -0.00508664),
        doubleArrayOf(30.06952752, 0.00895439, 1.77005520, 304.22289287, 46.68158724, 131.78635853,
            0.00006447, 0.00000818, 0.00022400, 218.46515314, 0.01009938, -0.00606302),
        doubleArrayOf(-0.00041348, 0.68346318, -0.10162547, 7.67025000))
    /** Pluto: the earlier edition of Table 1 (1800–2050), the last one that listed it. */
    val PLUTO = Planet("Pluto",
        doubleArrayOf(39.48211675, 0.24882730, 17.14001206, 238.92903833, 224.06891629, 110.30393684,
            -0.00031596, 0.00005170, 0.00004818, 145.20780515, -0.04062942, -0.01183482))

    // ── Deep-space probes ────────────────────────────────────────────────────
    //
    // Propagated from JPL Horizons heliocentric state vectors (J2000 ecliptic, AU and
    // AU/day; CENTER=500@10, REF_PLANE=ECLIPTIC, OUT_UNITS=AU-D) under the Sun's gravity.
    // Beyond the planets the Sun is the only significant force, so RK4 integration of
    // two-body motion (≤ 5-day steps) from one state vector stays within a few
    // thousandths of an AU for decades either side of the epoch. Distance from Earth uses
    // Earth's real position, so it carries the ±1 AU annual swing.

    private const val GM_SUN_AU3_D2 = 0.01720209895 * 0.01720209895
    private const val MAX_STEP_DAYS = 5.0

    class Spacecraft(val name: String, private val launchJd: Double, private val epochJd: Double, private val state: DoubleArray) {
        /** Heliocentric position (AU, J2000 ecliptic), or null before launch. */
        fun heliocentricPosition(zdt: ZonedDateTime): DoubleArray? {
            val jd = julianDate(zdt)
            if (jd < launchJd) return null
            val span = jd - epochJd
            val steps = max(1, kotlin.math.ceil(abs(span) / MAX_STEP_DAYS).toInt())
            val h = span / steps
            var s = state.copyOf()
            repeat(steps) { s = rk4(s, h) }
            return doubleArrayOf(s[0], s[1], s[2])
        }

        fun heliocentricAu(zdt: ZonedDateTime) = heliocentricPosition(zdt)?.let { norm(it) } ?: 0.0

        fun fromEarthAu(zdt: ZonedDateTime) =
            heliocentricPosition(zdt)?.let { distanceBetween(it, EARTH.heliocentricPosition(zdt)) } ?: 0.0

        private fun rk4(s: DoubleArray, h: Double): DoubleArray {
            val k1 = derivative(s)
            val k2 = derivative(DoubleArray(6) { s[it] + k1[it] * h / 2 })
            val k3 = derivative(DoubleArray(6) { s[it] + k2[it] * h / 2 })
            val k4 = derivative(DoubleArray(6) { s[it] + k3[it] * h })
            return DoubleArray(6) { s[it] + h / 6.0 * (k1[it] + 2 * k2[it] + 2 * k3[it] + k4[it]) }
        }

        private fun derivative(s: DoubleArray): DoubleArray {
            val r = sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2])
            val k = -GM_SUN_AU3_D2 / (r * r * r)
            return doubleArrayOf(s[3], s[4], s[5], k * s[0], k * s[1], k * s[2])
        }
    }

    private const val HORIZONS_EPOCH_JD = 2461041.5 // 2026-01-01 00:00 TDB

    /** Horizons target -31, launched 1977-09-05. */
    val VOYAGER_1 = Spacecraft("Voyager 1", 2443391.5, HORIZONS_EPOCH_JD, doubleArrayOf(
        -31.83641491929577, -134.6784911967567, 97.44481075134993,
        -0.001197151937928288, -0.00786107792125082, 0.005680005843310299))
    /** Horizons target -32, launched 1977-08-20. */
    val VOYAGER_2 = Spacecraft("Voyager 2", 2443375.5, HORIZONS_EPOCH_JD, doubleArrayOf(
        39.22753395002931, -103.9915540306074, -87.91866331046981,
        0.002427683334171075, -0.005395071892829595, -0.006538419567248777))
    /** Horizons target -98, launched 2006-01-19. */
    val NEW_HORIZONS = Spacecraft("New Horizons", 2453754.5, HORIZONS_EPOCH_JD, doubleArrayOf(
        20.00963528566975, -60.30445704637835, 2.212929493946444,
        0.003070495042525652, -0.007229267480329574, 0.0002845557285883782))

    // James Webb flies a station-kept halo orbit around Sun–Earth L2 that can't be modelled
    // offline, so its distance is fetched live (LiveFetcher → LiveData.jwstKm).

    // ── Earth ────────────────────────────────────────────────────────────────

    fun earthSpeedKmS(sunAu: Double): Double {
        val rKm = sunAu * KM_PER_AU
        return sqrt(1.32712440018e11 * (2.0 / rKm - 1.0 / KM_PER_AU))
    }

    fun axialTiltDeg(z: ZonedDateTime) = 23.439291 - 0.013004 * (daysSinceJ2000(z) / 36525.0)

    // ── Sun position, daylight, sunrise and sunset ───────────────────────────
    //
    // NOAA solar calculator algorithm (Meeus, "Astronomical Algorithms", low-precision
    // solar coordinates): declination ~0.01°, equation of time ~0.1 min, which puts
    // sunrise and sunset within about a minute of almanac values.

    /** Altitude of the Sun's centre at official sunrise/sunset: upper limb plus refraction. */
    private const val SUNRISE_ALTITUDE_DEG = -0.833

    /** (declination in radians, equation of time in minutes) at an instant. */
    fun solarPosition(zdt: ZonedDateTime): Pair<Double, Double> {
        val t = daysSinceJ2000(zdt) / 36525.0
        val meanLongitude = normalizeAngle(280.46646 + t * (36000.76983 + t * 0.0003032))
        val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val m = toRad(meanAnomaly)
        val centre = sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * m) * (0.019993 - 0.000101 * t) + sin(3 * m) * 0.000289
        val omega = toRad(125.04 - 1934.136 * t)
        val apparentLongitude = toRad(meanLongitude + centre - 0.00569 - 0.00478 * sin(omega))
        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = toRad(meanObliquity + 0.00256 * cos(omega))
        val declination = asin(sin(obliquity) * sin(apparentLongitude))
        val y = tan(obliquity / 2.0).let { it * it }
        val l0 = toRad(meanLongitude)
        val eot = y * sin(2 * l0) - 2 * eccentricity * sin(m) +
            4 * eccentricity * y * sin(m) * cos(2 * l0) -
            0.5 * y * y * sin(4 * l0) - 1.25 * eccentricity * eccentricity * sin(2 * m)
        return declination to 4.0 * Math.toDegrees(eot)
    }

    private fun solarPositionOn(d: LocalDate, hoursUtc: Double) =
        solarPosition(d.atStartOfDay(ZoneOffset.UTC).plusSeconds(Math.round(hoursUtc * 3600.0)))

    /** Hours between solar noon and sunrise; 0 for polar night, 12 for midnight sun. */
    private fun halfDayHours(latitude: Double, declination: Double): Double {
        val lat = toRad(latitude)
        val cosH = (sin(toRad(SUNRISE_ALTITUDE_DEG)) - sin(lat) * sin(declination)) / (cos(lat) * cos(declination))
        return when {
            cosH >= 1.0 -> 0.0
            cosH <= -1.0 -> 12.0
            else -> Math.toDegrees(acos(cosH)) / 15.0
        }
    }

    /** Sunrise to sunset, in hours (0 polar night, 24 midnight sun). */
    fun dayLengthHours(d: LocalDate, latitude: Double): Double =
        2.0 * halfDayHours(latitude, solarPositionOn(d, 12.0).first)

    private fun formatHour(hIn: Double): String {
        val h = ((hIn % 24) + 24) % 24
        var hours = h.toInt()
        var minutes = ((h - hours) * 60).roundToInt()
        if (minutes >= 60) { hours++; minutes -= 60 }
        if (hours >= 24) hours -= 24
        return "%02d:%02d".format(hours, minutes)
    }

    /** UTC hours after midnight of sunrise or sunset, refined once at the estimated time; or a polar label. */
    private fun sunEvent(d: LocalDate, latitude: Double, longitude: Double, rising: Boolean): String {
        val offset = -longitude / 15.0
        var t = 12.0 + offset
        repeat(2) {
            val (declination, eot) = solarPositionOn(d, t)
            val half = halfDayHours(latitude, declination)
            if (half <= 0.0) return "Polar Night"
            if (half >= 12.0) return "Midnight Sun"
            val noon = 12.0 + offset - eot / 60.0
            t = if (rising) noon - half else noon + half
        }
        return formatHour(t)
    }

    fun sunriseSunsetUtc(d: LocalDate, latitude: Double, longitude: Double): Pair<String, String> =
        sunEvent(d, latitude, longitude, true) to sunEvent(d, latitude, longitude, false)

    // ── Moonrise / moonset ───────────────────────────────────────────────────

    /** Low-precision lunar position (Schlyter's method, ~0.3°): RA/Dec in degrees and distance in Earth radii. */
    private fun moonEquatorial(d2000: Double): Triple<Double, Double, Double> {
        val d = d2000 + 1.5 // days since 1999-12-31.0
        val n = 125.1228 - 0.0529538083 * d
        val m = normalizeAngle(115.3654 + 13.0649929509 * d)
        val w = 318.0634 + 0.1643573223 * d
        val ms = normalizeAngle(356.0470 + 0.9856002585 * d)
        val ls = ms + 282.9404 + 4.70935e-5 * d
        val lm = m + w + n
        val dd = lm - ls
        val f = lm - n
        val e = 0.0549
        val mr = toRad(m)
        var ea = mr + e * sin(mr) * (1.0 + e * cos(mr))
        ea -= (ea - e * sin(ea) - mr) / (1.0 - e * cos(ea))
        val xv = 60.2666 * (cos(ea) - e)
        val yv = 60.2666 * sqrt(1.0 - e * e) * sin(ea)
        val v = atan2(yv, xv)
        val r = sqrt(xv * xv + yv * yv)
        val nr = toRad(n)
        val vw = v + toRad(w)
        val inc = toRad(5.1454)
        val xh = r * (cos(nr) * cos(vw) - sin(nr) * sin(vw) * cos(inc))
        val yh = r * (sin(nr) * cos(vw) + cos(nr) * sin(vw) * cos(inc))
        val zh = r * sin(vw) * sin(inc)
        fun s(x: Double) = sin(toRad(x))
        val lon = Math.toDegrees(atan2(yh, xh)) +
            -1.274 * s(m - 2 * dd) + 0.658 * s(2 * dd) - 0.186 * s(ms) - 0.059 * s(2 * m - 2 * dd) -
            0.057 * s(m - 2 * dd + ms) + 0.053 * s(m + 2 * dd) + 0.046 * s(2 * dd - ms) +
            0.041 * s(m - ms) - 0.035 * s(dd) - 0.031 * s(m + ms) - 0.015 * s(2 * f - 2 * dd) + 0.011 * s(m - 4 * dd)
        val lat = Math.toDegrees(atan2(zh, sqrt(xh * xh + yh * yh))) +
            -0.173 * s(f - 2 * dd) - 0.055 * s(m - f - 2 * dd) - 0.046 * s(m + f - 2 * dd) +
            0.033 * s(f + 2 * dd) + 0.017 * s(2 * m + f)
        val dist = r - 0.58 * cos(toRad(m - 2 * dd)) - 0.46 * cos(toRad(2 * dd))
        val ecl = toRad(23.4393 - 3.563e-7 * d)
        val lr = toRad(lon)
        val br = toRad(lat)
        val x = cos(lr) * cos(br)
        val y = sin(lr) * cos(br) * cos(ecl) - sin(br) * sin(ecl)
        val z = sin(lr) * cos(br) * sin(ecl) + sin(br) * cos(ecl)
        return Triple(normalizeAngle(Math.toDegrees(atan2(y, x))), Math.toDegrees(asin(z)), dist)
    }

    /** Moon's altitude above the rising/setting threshold (degrees) at [zdt]; crosses zero at moonrise/moonset. */
    private fun moonAltitudeAboveHorizon(zdt: ZonedDateTime, latitude: Double, longitude: Double): Double {
        val d2000 = daysSinceJ2000(zdt)
        val (ra, dec, dist) = moonEquatorial(d2000)
        val ha = toRad(280.46061837 + 360.98564736629 * d2000 + longitude - ra)
        val la = toRad(latitude)
        val de = toRad(dec)
        val alt = Math.toDegrees(asin(sin(la) * sin(de) + cos(la) * cos(de) * cos(ha)))
        val parallax = Math.toDegrees(asin(1.0 / dist))
        return alt - (0.7275 * parallax - 0.5667)
    }

    /** The next moonrise and moonset after [from] (within 30 hours), or null when there is none, e.g. near the poles. */
    fun nextMoonRiseSet(from: ZonedDateTime, latitude: Double, longitude: Double): Pair<ZonedDateTime?, ZonedDateTime?> {
        val start = from.withZoneSameInstant(ZoneOffset.UTC)
        fun alt(t: ZonedDateTime) = moonAltitudeAboveHorizon(t, latitude, longitude)
        var rise: ZonedDateTime? = null
        var set: ZonedDateTime? = null
        var t0 = start
        var a0 = alt(t0)
        for (i in 1..180) {
            val t1 = start.plusMinutes(10L * i)
            val a1 = alt(t1)
            if ((a0 < 0) != (a1 < 0)) {
                var lo = t0
                var hi = t1
                repeat(20) {
                    val mid = lo.plusSeconds(Duration.between(lo, hi).seconds / 2)
                    if ((alt(mid) < 0) == (a0 < 0)) lo = mid else hi = mid
                }
                if (a0 < 0) { if (rise == null) rise = hi } else if (set == null) set = hi
                if (rise != null && set != null) break
            }
            t0 = t1
            a0 = a1
        }
        return rise to set
    }

    /** "HH:mm" in [zone], with " +1" when the event falls on the next local day; "—" when [t] is null. */
    fun formatMoonEvent(t: ZonedDateTime?, now: ZonedDateTime, zone: java.time.ZoneId): String {
        if (t == null) return "—"
        val local = t.withZoneSameInstant(zone)
        val days = ChronoUnit.DAYS.between(now.withZoneSameInstant(zone).toLocalDate(), local.toLocalDate())
        return "%02d:%02d".format(local.hour, local.minute) + if (days > 0) " +$days" else ""
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
        val v1h = VOYAGER_1.heliocentricAu(t)
        val v2h = VOYAGER_2.heliocentricAu(t)
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
        val (moonrise, moonset) = nextMoonRiseSet(t, lat, lon)
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
            nextNewMoon = mp.nextNewMoon,
            nextFullMoon = mp.nextFullMoon,
            heliocentricAu = helio,
            lightTimes = helio.mapValues { formatLightTime(it.value) } +
                mapOf("Voyager 1" to formatLightTime(v1h), "Voyager 2" to formatLightTime(v2h)),
            marsDistanceAu = MARS.geocentricAu(t),
            jupiterDistanceAu = JUPITER.geocentricAu(t),
            saturnDistanceAu = SATURN.geocentricAu(t),
            voyager1HelioAu = v1h,
            voyager2HelioAu = v2h,
            voyager1DistanceAu = VOYAGER_1.fromEarthAu(t),
            voyager2DistanceAu = VOYAGER_2.fromEarthAu(t),
            newHorizonsDistanceAu = NEW_HORIZONS.fromEarthAu(t),
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
            moonrise = moonrise,
            moonset = moonset,
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
    val nextNewMoon: ZonedDateTime,
    val nextFullMoon: ZonedDateTime,
    /** Distance from the Sun (AU): Mercury → Pluto, with "Earth" = the Sun–Earth distance. */
    val heliocentricAu: Map<String, Double>,
    val lightTimes: Map<String, String>,
    /** Mars, Jupiter and Saturn from Earth (AU), as stored in history and sent to Awtrix. */
    val marsDistanceAu: Double,
    val jupiterDistanceAu: Double,
    val saturnDistanceAu: Double,
    val voyager1HelioAu: Double,
    val voyager2HelioAu: Double,
    val voyager1DistanceAu: Double,
    val voyager2DistanceAu: Double,
    val newHorizonsDistanceAu: Double,
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
    val moonrise: ZonedDateTime? = null,
    val moonset: ZonedDateTime? = null,
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
    /** James Webb's distance from Earth (km), live from JPL Horizons. */
    val jwstKm: Double = -1.0,
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
