package io.github.jamieharwood.moonphase

import io.github.jamieharwood.moonphase.data.halfDayWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class HistoryTest {
    private fun utc(h: Int) = ZonedDateTime.of(2026, 11, 21, h, 0, 0, 0, ZoneOffset.UTC)

    @Test fun historyWindow() {
        val (a, b) = halfDayWindow(utc(5))
        assertEquals(utc(0).toEpochSecond(), a)
        assertEquals(12 * 3600L, b - a)
        assertEquals(utc(12).toEpochSecond(), halfDayWindow(utc(13)).first)
    }
}
