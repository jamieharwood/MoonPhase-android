package io.github.jamieharwood.moonphase

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

enum class SkyEventKind { METEOR_SHOWER, SOLAR_ECLIPSE, LUNAR_ECLIPSE }

/** One entry of the sky calendar. [date] is the UTC date of the peak or of greatest eclipse. */
data class SkyEvent(
    val kind: SkyEventKind,
    val name: String,
    val date: LocalDate,
    val detail: String,
    /** Stable id used to look up translations: shower id (e.g. "perseids") or eclipse type (e.g. "solar_total"). */
    val key: String = "",
    val zhr: Int = 0,
    val moonLit: Int = 0,
) {
    val icon get() = when (kind) {
        SkyEventKind.METEOR_SHOWER -> "🌠"
        SkyEventKind.SOLAR_ECLIPSE -> "🌑"
        SkyEventKind.LUNAR_ECLIPSE -> "🌕"
    }

    fun daysFrom(today: LocalDate) = ChronoUnit.DAYS.between(today, date)
}

/**
 * Meteor shower peaks (annual, from the IMO calendar) and eclipses (NASA catalogue, 2026–2030).
 * Pure Kotlin so it can be unit-tested on the JVM.
 */
object SkyEvents {
    private class Shower(val id: String, val name: String, val month: Int, val day: Int, val zhr: Int)

    private val SHOWERS = listOf(
        Shower("quadrantids", "Quadrantids", 1, 3, 110),
        Shower("lyrids", "Lyrids", 4, 22, 18),
        Shower("eta_aquariids", "Eta Aquariids", 5, 6, 50),
        Shower("delta_aquariids", "Southern Delta Aquariids", 7, 30, 25),
        Shower("perseids", "Perseids", 8, 12, 100),
        Shower("draconids", "Draconids", 10, 8, 10),
        Shower("orionids", "Orionids", 10, 21, 20),
        Shower("s_taurids", "Southern Taurids", 11, 5, 5),
        Shower("n_taurids", "Northern Taurids", 11, 12, 5),
        Shower("leonids", "Leonids", 11, 17, 15),
        Shower("geminids", "Geminids", 12, 14, 150),
        Shower("ursids", "Ursids", 12, 22, 10),
    )

    private fun e(kind: SkyEventKind, y: Int, m: Int, d: Int, name: String, where: String) =
        SkyEvent(kind, name, LocalDate.of(y, m, d), where, "${if (kind == S) "solar" else "lunar"}_${name.substringBefore(' ').lowercase()}")

    private val S = SkyEventKind.SOLAR_ECLIPSE
    private val L = SkyEventKind.LUNAR_ECLIPSE

    val ECLIPSES: List<SkyEvent> = listOf(
        e(S, 2026, 2, 17, "Annular solar eclipse", "Antarctica, southern Africa (partial)"),
        e(L, 2026, 3, 3, "Total lunar eclipse", "Pacific, Americas, East Asia, Australia"),
        e(S, 2026, 8, 12, "Total solar eclipse", "Greenland, Iceland, Spain; partial in Europe"),
        e(L, 2026, 8, 28, "Partial lunar eclipse", "Americas, Europe, Africa"),
        e(S, 2027, 2, 6, "Annular solar eclipse", "South America, Atlantic, Africa (partial)"),
        e(L, 2027, 2, 20, "Penumbral lunar eclipse", "Americas, Europe, Africa, Asia"),
        e(L, 2027, 7, 18, "Penumbral lunar eclipse", "Asia, Australia, Pacific"),
        e(S, 2027, 8, 2, "Total solar eclipse", "Spain, North Africa, Middle East"),
        e(L, 2027, 8, 17, "Penumbral lunar eclipse", "Americas, Atlantic"),
        e(L, 2028, 1, 12, "Partial lunar eclipse", "Americas, Europe, Africa"),
        e(S, 2028, 1, 26, "Annular solar eclipse", "South America, Spain, Portugal"),
        e(L, 2028, 7, 6, "Partial lunar eclipse", "Europe, Africa, Asia, Australia"),
        e(S, 2028, 7, 22, "Total solar eclipse", "Australia, New Zealand"),
        e(L, 2028, 12, 31, "Total lunar eclipse", "Europe, Africa, Asia, Australia"),
        e(S, 2029, 1, 14, "Partial solar eclipse", "North America"),
        e(L, 2029, 6, 26, "Total lunar eclipse", "Americas, Europe, Africa"),
        e(S, 2029, 6, 12, "Partial solar eclipse", "Arctic, Scandinavia, Russia"),
        e(S, 2029, 7, 11, "Partial solar eclipse", "Southern Chile and Argentina"),
        e(S, 2029, 12, 5, "Partial solar eclipse", "Antarctica, southern Argentina"),
        e(L, 2029, 12, 20, "Total lunar eclipse", "Americas, Europe, Africa, Asia"),
        e(S, 2030, 6, 1, "Annular solar eclipse", "Europe, North Africa, Asia"),
        e(L, 2030, 6, 15, "Partial lunar eclipse", "Africa, Asia, Australia"),
        e(L, 2030, 12, 9, "Penumbral lunar eclipse", "Asia, Australia, Americas"),
        e(S, 2030, 11, 25, "Total solar eclipse", "Southern Africa, Australia"),
    ).sortedBy { it.date }

    /** Next peak of every shower on or after [from], with the Moon's brightness that night. */
    fun meteorShowers(from: LocalDate): List<SkyEvent> = SHOWERS.map { s ->
        var date = LocalDate.of(from.year, s.month, s.day)
        if (date.isBefore(from)) date = date.plusYears(1)
        val lit = Astro.MoonPhase.at(ZonedDateTime.of(date, java.time.LocalTime.MIDNIGHT, ZoneOffset.UTC)).illuminationPercent
        val sky = when {
            lit <= 25 -> "dark skies"
            lit <= 60 -> "some moonlight"
            else -> "bright Moon"
        }
        SkyEvent(SkyEventKind.METEOR_SHOWER, s.name, date, "up to ~${s.zhr}/hr · Moon $lit% lit ($sky)", s.id, s.zhr, lit)
    }

    /** The next [count] events (showers and eclipses) from [from], soonest first. */
    fun upcoming(from: LocalDate, count: Int = 8): List<SkyEvent> =
        (meteorShowers(from) + ECLIPSES.filter { !it.date.isBefore(from) })
            .sortedBy { it.date }
            .take(count)

    fun daysText(days: Long) = when (days) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "in $days days"
    }
}
