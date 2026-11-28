package io.github.jamieharwood.moonphase

import io.github.jamieharwood.moonphase.data.LiveFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Checks the ephemeris against JPL Horizons (https://ssd.jpl.nasa.gov/horizons/), observer
 * table, geocentric (CENTER=500@399): r = distance from the Sun (AU), delta = distance from
 * Earth (AU), illuminated %. Planet dates span both element tables (Table 1: 1800–2050,
 * Table 2: 2075); spacecraft dates span 2020–2035 around the 2026 state vectors.
 * These are the same reference values the MoonPhase web app and the Ubuntu app use.
 */
class EphemerisAccuracyTest {
    private data class Row(val body: String, val utc: String, val r: Double, val delta: Double, val illum: Double)

    private val horizons = listOf(
        Row("Moon", "2000-01-01T12:00:00Z", 0.981872991987, 0.00268998925442, 23.00638),
        Row("Moon", "2015-07-14T12:00:00Z", 1.014093364195, 0.00258893367913, 2.80715),
        Row("Moon", "2026-11-27T23:28:00Z", 0.988335675755, 0.00242629346672, 84.11571),
        Row("Moon", "2040-06-01T00:00:00Z", 1.014622416956, 0.00247864868104, 62.3655),
        Row("Moon", "2075-03-15T00:00:00Z", 0.991853156389, 0.00251286916114, 4.01549),
        Row("Sun", "2000-01-01T12:00:00Z", 0.0, 0.98332762652701, 100.0),
        Row("Sun", "2015-07-14T12:00:00Z", 0.0, 1.01653722930979, 100.0),
        Row("Sun", "2026-11-27T23:28:00Z", 0.0, 0.98668159281108, 100.0),
        Row("Sun", "2040-06-01T00:00:00Z", 0.0, 1.01401203003873, 100.0),
        Row("Sun", "2075-03-15T00:00:00Z", 0.0, 0.99416461269571, 100.0),
        Row("Mercury", "2000-01-01T12:00:00Z", 0.466468841931, 1.41546946904999, 97.48652),
        Row("Mercury", "2015-07-14T12:00:00Z", 0.309190162025, 1.24239600766868, 89.42002),
        Row("Mercury", "2026-11-27T23:28:00Z", 0.378519674543, 1.16083625301682, 79.42448),
        Row("Mercury", "2040-06-01T00:00:00Z", 0.420104868102, 0.79730382988487, 33.88215),
        Row("Mercury", "2075-03-15T00:00:00Z", 0.419357052028, 1.34002851911789, 93.73588),
        Row("Venus", "2000-01-01T12:00:00Z", 0.720212286809, 1.13757924245212, 75.80544),
        Row("Venus", "2015-07-14T12:00:00Z", 0.727063246288, 0.41812775457853, 22.87572),
        Row("Venus", "2026-11-27T23:28:00Z", 0.719887281313, 0.40643257821581, 25.20597),
        Row("Venus", "2040-06-01T00:00:00Z", 0.720916066211, 1.73489962490728, 99.99888),
        Row("Venus", "2075-03-15T00:00:00Z", 0.72679128062, 1.27090944065987, 81.25808),
        Row("Mars", "2000-01-01T12:00:00Z", 1.391202576247, 1.84968786270204, 92.65057),
        Row("Mars", "2015-07-14T12:00:00Z", 1.588259587485, 2.58663164091405, 99.77691),
        Row("Mars", "2026-11-27T23:28:00Z", 1.621036289394, 1.20386500513761, 89.75553),
        Row("Mars", "2040-06-01T00:00:00Z", 1.662191499153, 1.82338768718413, 91.73498),
        Row("Mars", "2075-03-15T00:00:00Z", 1.385108951973, 2.37867167249401, 99.99085),
        Row("Jupiter", "2000-01-01T12:00:00Z", 4.965377642079, 4.62117524331177, 99.07602),
        Row("Jupiter", "2015-07-14T12:00:00Z", 5.377225371039, 6.20754388955521, 99.74263),
        Row("Jupiter", "2026-11-27T23:28:00Z", 5.325365759933, 5.07976560617289, 99.15565),
        Row("Jupiter", "2040-06-01T00:00:00Z", 5.446108558008, 5.16037454936609, 99.15835),
        Row("Jupiter", "2075-03-15T00:00:00Z", 5.407321443042, 4.44813980830185, 99.92909),
        Row("Saturn", "2000-01-01T12:00:00Z", 9.183858256403, 8.65279676096631, 99.78468),
        Row("Saturn", "2015-07-14T12:00:00Z", 9.986643162591, 9.34477940561514, 99.83367),
        Row("Saturn", "2026-11-27T23:28:00Z", 9.417060471932, 8.85272387588537, 99.8037),
        Row("Saturn", "2040-06-01T00:00:00Z", 9.590117268223, 9.1311306095071, 99.76675),
        Row("Saturn", "2075-03-15T00:00:00Z", 10.02644726861, 9.87098068694784, 99.75629),
        Row("Uranus", "2000-01-01T12:00:00Z", 19.9240150244, 20.727171130885, 99.98055),
        Row("Uranus", "2015-07-14T12:00:00Z", 19.99032142132, 19.9412638083133, 99.93529),
        Row("Uranus", "2026-11-27T23:28:00Z", 19.43076834045, 18.4447497346863, 99.99991),
        Row("Uranus", "2040-06-01T00:00:00Z", 18.56896819234, 19.2113531895362, 99.95691),
        Row("Uranus", "2075-03-15T00:00:00Z", 19.45569681685, 19.7716814268018, 99.9422),
        Row("Neptune", "2000-01-01T12:00:00Z", 30.12058818217, 31.024498221734, 99.996),
        Row("Neptune", "2015-07-14T12:00:00Z", 29.96382363343, 29.2715099470401, 99.98418),
        Row("Neptune", "2026-11-27T23:28:00Z", 29.87667954767, 29.4320638256214, 99.97797),
        Row("Neptune", "2040-06-01T00:00:00Z", 29.81042658002, 30.6197572090097, 99.98976),
        Row("Neptune", "2075-03-15T00:00:00Z", 29.96440392234, 29.5329657015062, 99.97736),
        Row("Pluto", "2000-01-01T12:00:00Z", 30.22318615617, 31.0643527312227, 99.99308),
        Row("Pluto", "2015-07-14T12:00:00Z", 32.90832829897, 31.9012331276215, 99.99955),
        Row("Pluto", "2026-11-27T23:28:00Z", 35.65038403251, 36.1654464971515, 99.98629),
        Row("Pluto", "2040-06-01T00:00:00Z", 39.10115251431, 38.8470547272902, 99.98412),
        Row("Pluto", "2075-03-15T00:00:00Z", 46.34361095354, 47.2697102650016, 99.99851),
        Row("Voyager 1", "2020-01-01T00:00:00Z", 147.8673484958, 148.605563056091, 99.99952),
        Row("Voyager 1", "2024-01-01T00:00:00Z", 162.1186258501, 162.859045233514, 99.9996),
        Row("Voyager 1", "2026-11-27T23:28:00Z", 172.4726207521, 173.264340821595, 99.99971),
        Row("Voyager 1", "2030-01-01T00:00:00Z", 183.4854610277, 184.225761621311, 99.99969),
        Row("Voyager 1", "2035-01-01T00:00:00Z", 201.2506300484, 201.994116220434, 99.99975),
        Row("Voyager 2", "2020-01-01T00:00:00Z", 122.6759061005, 123.45125333803, 99.9994),
        Row("Voyager 2", "2024-01-01T00:00:00Z", 135.3586174591, 136.122235415381, 99.99948),
        Row("Voyager 2", "2026-11-27T23:28:00Z", 144.5821025366, 145.126611289025, 99.9992),
        Row("Voyager 2", "2030-01-01T00:00:00Z", 154.4049823751, 155.15561907585, 99.99958),
        Row("Voyager 2", "2035-01-01T00:00:00Z", 170.2696843896, 171.009897717743, 99.99964),
        Row("New Horizons", "2020-01-01T00:00:00Z", 46.21582350257, 47.1916615967235, 99.99983),
        Row("New Horizons", "2024-01-01T00:00:00Z", 57.83054644178, 58.8034435272464, 99.99985),
        Row("New Horizons", "2026-11-27T23:28:00Z", 66.16268960782, 66.8851603769669, 99.99745),
        Row("New Horizons", "2030-01-01T00:00:00Z", 74.96070702767, 75.9318217327052, 99.9999),
        Row("New Horizons", "2035-01-01T00:00:00Z", 89.03964718231, 90.0081078701794, 99.99991),
    )

    private val planets = listOf(
        Astro.MERCURY, Astro.VENUS, Astro.MARS, Astro.JUPITER, Astro.SATURN, Astro.URANUS, Astro.NEPTUNE, Astro.PLUTO,
    ).associateBy { it.name }
    private val craft = listOf(Astro.VOYAGER_1, Astro.VOYAGER_2, Astro.NEW_HORIZONS).associateBy { it.name }

    @Test fun matchesHorizons() {
        val failures = mutableListOf<String>()
        fun check(row: Row, what: String, app: Double, ref: Double, tol: Double) {
            if (Math.abs(app - ref) > tol) failures += "${row.body} ${row.utc} $what: app $app vs Horizons $ref (tolerance $tol)"
        }
        for (row in horizons) {
            val t = ZonedDateTime.parse(row.utc)
            when (row.body) {
                "Sun" -> check(row, "distance", Astro.sunDistanceAu(t), row.delta, 0.0002)
                "Moon" -> {
                    check(row, "km", Astro.moonDistanceKm(t), row.delta * Astro.KM_PER_AU, 1_200.0)
                    check(row, "illum %", Astro.moonSunPosition(Astro.julianDate(t)).illuminatedFraction * 100, row.illum, 1.0)
                }
                in craft -> {
                    val c = craft.getValue(row.body)
                    check(row, "from Sun", c.heliocentricAu(t), row.r, 0.03)
                    check(row, "from Earth", c.fromEarthAu(t), row.delta, 0.03)
                }
                else -> {
                    val p = planets.getValue(row.body)
                    check(row, "from Sun", p.heliocentricAu(t), row.r, row.r * 0.002)
                    check(row, "from Earth", p.geocentricAu(t), row.delta, row.delta * 0.004)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Moon − Sun ecliptic longitude 0° / 180° from Horizons quantity 31 at 2-minute steps. */
    @Test fun newAndFullMoonTimesMatchHorizons() {
        val mp = Astro.MoonPhase.at(ZonedDateTime.parse("2026-11-27T23:28:00Z"))
        assertTrue(Math.abs(Duration.between(ZonedDateTime.parse("2026-12-09T00:52:00Z"), mp.nextNewMoon).toMinutes()) <= 15)
        assertTrue(Math.abs(Duration.between(ZonedDateTime.parse("2026-12-24T01:28:00Z"), mp.nextFullMoon).toMinutes()) <= 15)
        assertEquals("Waning Gibbous", mp.name)
        assertTrue(Math.abs(mp.illuminationPercent - 84) <= 1)
    }

    @Test fun probesBeforeLaunchAreZero() {
        val t = ZonedDateTime.parse("1970-01-01T00:00:00Z")
        assertEquals(0.0, Astro.VOYAGER_1.heliocentricAu(t), 0.0)
        assertEquals(0.0, Astro.NEW_HORIZONS.fromEarthAu(t), 0.0)
    }

    // Almanac times for the Royal Observatory, Greenwich (official sunrise incl. refraction).
    private val greenwich = 51.4769

    private fun assertNear(expected: String, actual: String, minutes: Long = 2) {
        val diff = Math.abs(ChronoUnit.MINUTES.between(LocalTime.parse(expected), LocalTime.parse(actual)))
        assertTrue("expected ~$expected, got $actual", diff <= minutes)
    }

    @Test fun sunriseSunsetMatchAlmanac() {
        Astro.sunriseSunsetUtc(LocalDate.of(2026, 6, 21), greenwich, 0.0).let { (r, s) -> assertNear("03:43", r); assertNear("20:21", s) }
        Astro.sunriseSunsetUtc(LocalDate.of(2026, 12, 21), greenwich, 0.0).let { (r, s) -> assertNear("08:04", r); assertNear("15:53", s) }
        val east = Astro.sunriseSunsetUtc(LocalDate.of(2026, 3, 20), greenwich, 0.0).first
        val west = Astro.sunriseSunsetUtc(LocalDate.of(2026, 3, 20), greenwich, -15.0).first
        assertEquals(60.0, ChronoUnit.MINUTES.between(LocalTime.parse(east), LocalTime.parse(west)).toDouble(), 2.0)
        assertEquals("Polar Night", Astro.sunriseSunsetUtc(LocalDate.of(2026, 12, 21), 89.0, 0.0).first)
        assertEquals("Midnight Sun", Astro.sunriseSunsetUtc(LocalDate.of(2026, 6, 21), 89.0, 0.0).second)
    }

    @Test fun dayLengthMatchesAlmanac() {
        assertEquals(16 + 38 / 60.0, Astro.dayLengthHours(LocalDate.of(2026, 6, 21), greenwich), 3 / 60.0)
        assertEquals(7 + 49 / 60.0, Astro.dayLengthHours(LocalDate.of(2026, 12, 21), greenwich), 3 / 60.0)
        assertNotEquals(
            Astro.dayLengthHours(LocalDate.of(2025, 3, 1), greenwich),
            Astro.dayLengthHours(LocalDate.of(2027, 3, 1), greenwich), 1 / 3600.0,
        )
    }

    @Test fun parsesJwstFromHorizons() {
        val marker = "$" + "$"
        val body = "{\"result\":\"header\\n${marker}SOE\\n 2026-Nov-27 23:28, , ,  0.01136300827198,  0.0069837,\\n${marker}EOE\\nfooter\"}"
        assertEquals(0.01136300827198 * Astro.KM_PER_AU, LiveFetcher.parseJwstKm(body), 1e-3)
        assertEquals(-1.0, LiveFetcher.parseJwstKm("{\"result\":\"no ephemeris\"}"), 0.0)
        assertEquals(-1.0, LiveFetcher.parseJwstKm(null), 0.0)
        assertTrue(LiveFetcher.jwstUrl(ZonedDateTime.parse("2026-11-27T23:28:00Z")).contains("COMMAND=%27-170%27"))
    }
}
