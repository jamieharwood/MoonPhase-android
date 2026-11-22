package io.github.jamieharwood.moonphase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SkyEventsTest {
    private fun fraction(d: LocalDate) =
        Astro.MoonPhase.at(ZonedDateTime.of(d, java.time.LocalTime.NOON, ZoneOffset.UTC)).fraction

    @Test fun eclipsesFallOnTheRightMoonPhase() {
        for (e in SkyEvents.ECLIPSES) {
            val f = fraction(e.date)
            val target = if (e.kind == SkyEventKind.SOLAR_ECLIPSE) 0.0 else 0.5
            val off = minOf(Math.abs(f - target), Math.abs(f - target - 1), Math.abs(f - target + 1))
            assertTrue("${e.name} ${e.date} phase $f", off < 0.04)
        }
    }

    @Test fun upcomingIsSortedAndMixesKinds() {
        val today = LocalDate.of(2026, 8, 1)
        val list = SkyEvents.upcoming(today, 6)
        assertEquals(6, list.size)
        assertEquals(list.sortedBy { it.date }, list)
        assertTrue(list.all { !it.date.isBefore(today) })
        assertEquals("Total solar eclipse", list.first { it.kind == SkyEventKind.SOLAR_ECLIPSE }.name)
        assertEquals(LocalDate.of(2026, 8, 12), list.first { it.name == "Perseids" }.date)
    }

    @Test fun showerRollsToNextYear() {
        val geminids = SkyEvents.meteorShowers(LocalDate.of(2026, 12, 15)).first { it.name == "Geminids" }
        assertEquals(LocalDate.of(2027, 12, 14), geminids.date)
    }
}
