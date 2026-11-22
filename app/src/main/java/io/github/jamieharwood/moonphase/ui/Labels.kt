package io.github.jamieharwood.moonphase.ui

import android.content.Context
import io.github.jamieharwood.moonphase.R
import io.github.jamieharwood.moonphase.data.Metric

/** Translated labels for values the shared code identifies by English name or key. */
object Labels {
    private val GROUPS = mapOf(
        "Moon" to R.string.mg_moon,
        "Solar System" to R.string.mg_solar,
        "Deep Space Probes" to R.string.mg_probes,
        "Earth" to R.string.mg_earth,
        "LEO" to R.string.mg_leo,
        "Upcoming Events" to R.string.mg_events,
    )

    private val METRICS = mapOf(
        "moonIlluminationPercent" to R.string.m_moonIlluminationPercent,
        "moonAgeDays" to R.string.m_moonAgeDays,
        "moonDistanceKm" to R.string.m_moonDistanceKm,
        "daysUntilFullMoon" to R.string.m_daysUntilFullMoon,
        "daylightHours" to R.string.m_daylightHours,
        "sunDistanceAu" to R.string.m_sunDistanceAu,
        "mercuryDistanceAu" to R.string.m_mercuryDistanceAu,
        "venusDistanceAu" to R.string.m_venusDistanceAu,
        "marsDistanceAu" to R.string.m_marsDistanceAu,
        "jupiterDistanceAu" to R.string.m_jupiterDistanceAu,
        "saturnDistanceAu" to R.string.m_saturnDistanceAu,
        "uranusDistanceAu" to R.string.m_uranusDistanceAu,
        "neptuneDistanceAu" to R.string.m_neptuneDistanceAu,
        "plutoDistanceAu" to R.string.m_plutoDistanceAu,
        "voyager1HelioDistanceAu" to R.string.m_voyager1HelioDistanceAu,
        "voyager2HelioDistanceAu" to R.string.m_voyager2HelioDistanceAu,
        "voyager1DistanceAu" to R.string.m_voyager1DistanceAu,
        "voyager2DistanceAu" to R.string.m_voyager2DistanceAu,
        "newHorizonsDistanceAu" to R.string.m_newHorizonsDistanceAu,
        "earthSpeedKmPerSec" to R.string.m_earthSpeedKmPerSec,
        "earthSpeedKmPerHour" to R.string.m_earthSpeedKmPerHour,
        "earthAxialTiltDegrees" to R.string.m_earthAxialTiltDegrees,
        "auroraKpIndex" to R.string.m_auroraKpIndex,
        "issAltitudeKm" to R.string.m_issAltitudeKm,
        "tiangongAltitudeKm" to R.string.m_tiangongAltitudeKm,
        "hubbleAltitudeKm" to R.string.m_hubbleAltitudeKm,
        "starlinkSatelliteCount" to R.string.m_starlinkSatelliteCount,
        "kuiperSatelliteCount" to R.string.m_kuiperSatelliteCount,
        "totalSatellitesInOrbit" to R.string.m_totalSatellitesInOrbit,
        "issCrew" to R.string.m_issCrew,
        "totalPeopleInSpace" to R.string.m_totalPeopleInSpace,
        "daysUntilSummerSolstice" to R.string.m_daysUntilSummerSolstice,
        "daysUntilWinterSolstice" to R.string.m_daysUntilWinterSolstice,
        "daysUntilPerihelion" to R.string.m_daysUntilPerihelion,
        "daysUntilAphelion" to R.string.m_daysUntilAphelion,
    )

    private val PLANETS = mapOf(
        "Mercury" to R.string.planet_mercury,
        "Venus" to R.string.planet_venus,
        "Earth" to R.string.planet_earth,
        "Mars" to R.string.planet_mars,
        "Jupiter" to R.string.planet_jupiter,
        "Saturn" to R.string.planet_saturn,
        "Uranus" to R.string.planet_uranus,
        "Neptune" to R.string.planet_neptune,
        "Pluto" to R.string.planet_pluto,
    )

    fun metric(ctx: Context, m: Metric): String =
        "${GROUPS[m.group]?.let(ctx::getString) ?: m.group} · ${METRICS[m.key]?.let(ctx::getString) ?: m.label}"

    fun planet(ctx: Context, name: String): String = PLANETS[name]?.let(ctx::getString) ?: name
}
