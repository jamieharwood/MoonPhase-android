package io.github.jamieharwood.moonphase

import io.github.jamieharwood.moonphase.data.LiveFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AstroTest {
    private fun utc(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneOffset.UTC)

    @Test fun julianDay() {
        assertEquals(2451545L, Astro.julianDayNumber(1, 1, 2000))
        assertEquals(0.0, Astro.daysSinceJ2000(utc(2000, 1, 1, 12)), 1e-9)
    }

    @Test fun knownFullAndNewMoons() {
        val full = Astro.MoonPhase.at(utc(2024, 4, 23, 23, 49))
        assertEquals("Full Moon", full.name)
        assertTrue(full.illuminationPercent >= 98)
        val new = Astro.MoonPhase.at(utc(2024, 4, 8, 18, 21))
        assertEquals("New Moon", new.name)
        assertTrue(new.illuminationPercent <= 2)
    }

    @Test fun moonRiseSetMatchesReference() {
        // Reference times from the PyEphem library; allow a few minutes for the low-precision model.
        fun check(from: ZonedDateTime, lat: Double, lon: Double, rise: ZonedDateTime, set: ZonedDateTime) {
            val (r, s) = Astro.nextMoonRiseSet(from, lat, lon)
            assertTrue("rise $r vs $rise", Math.abs(Duration.between(rise, r!!).toMinutes()) <= 6)
            assertTrue("set $s vs $set", Math.abs(Duration.between(set, s!!).toMinutes()) <= 6)
        }
        check(utc(2026, 11, 21, 9, 30), 51.4769, 0.0, utc(2026, 11, 21, 14, 3), utc(2026, 11, 22, 4, 42))
        check(utc(2025, 6, 10, 12, 0), 40.7, -74.0, utc(2025, 6, 11, 0, 26), utc(2025, 6, 11, 9, 7))
        check(utc(2024, 4, 23, 0, 0), -33.87, 151.2, utc(2024, 4, 23, 6, 53), utc(2024, 4, 23, 20, 22))
    }

    @Test fun moonEventFormatting() {
        val now = utc(2026, 11, 21, 20, 0)
        assertEquals("—", Astro.formatMoonEvent(null, now, ZoneOffset.UTC))
        assertEquals("21:05", Astro.formatMoonEvent(utc(2026, 11, 21, 21, 5), now, ZoneOffset.UTC))
        assertEquals("04:42 +1", Astro.formatMoonEvent(utc(2026, 11, 22, 4, 42), now, ZoneOffset.UTC))
    }

    @Test fun allEightPhasesInAMonth() {
        val names = (0 until 30).map { Astro.MoonPhase.at(utc(2025, 1, 1).plusDays(it.toLong())).name }.toSet()
        assertEquals(8, names.size)
    }

    @Test fun matchesDesktopPortToday() {
        // Same values the Python/GTK port produced for 2026-11-21.
        val s = Astro.compute(utc(2026, 11, 21, 9, 30))
        assertEquals("Waxing Gibbous", s.moonPhaseName)
        assertEquals(3, s.daysUntilFullMoon)
        assertEquals(30L, s.daysUntilWinterSolstice)
        assertEquals("8m 12s", s.lightTimes["Earth"])
    }

    @Test fun calendarEvents() {
        assertEquals(10L, Astro.daysUntil(LocalDate.of(2026, 6, 11), 6, 21))
        assertEquals(20L, Astro.daysUntil(LocalDate.of(2026, 12, 1), 12, 21))
        assertEquals(365L, Astro.daysUntil(LocalDate.of(2026, 6, 21), 6, 21))
    }

    @Test fun earth() {
        assertEquals(23.439291, Astro.axialTiltDeg(utc(2000, 1, 1, 12)), 1e-3)
        assertEquals(0.0, Astro.dayLengthHours(LocalDate.of(2026, 12, 21), 89.0), 0.0)
        assertEquals(24.0, Astro.dayLengthHours(LocalDate.of(2026, 6, 21), 89.0), 0.0)
        assertTrue(Astro.dayLengthHours(LocalDate.of(2026, 6, 21), 51.4769) in 16.0..18.0)
        assertTrue(Astro.earthSpeedKmS(0.9833) in 30.2..30.4)
        assertEquals("8m 19s", Astro.formatLightTime(1.0))
    }

    @Test fun parsers() {
        assertEquals(2.33, LiveFetcher.parseLatestKp("""[{"estimated_kp":1.0},{"estimated_kp":2.33}]"""), 1e-9)
        assertEquals(-1.0, LiveFetcher.parseLatestKp("[]"), 0.0)
        assertEquals(-1.0, LiveFetcher.parseLatestKp(null), 0.0)
        val p = LiveFetcher.parsePeople(
            """{"number":3,"people":[{"spacecraft":"ISS","iss":true},{"spacecraft":"ISS","iss":true},
               {"spacecraft":"Tiangong","iss":false}]}""",
        )
        assertEquals(3, p.total)
        assertEquals(2, p.issCrew)
        assertEquals(mapOf("ISS ★" to 2, "Tiangong" to 1), p.crafts)
        val line2 = "2 25544  51.6416 247.4627 0006703 130.5360 325.0288 15.50125391563537"
        assertTrue(Astro.altitudeFromTleLine2(line2) in 400.0..440.0)
    }
}
