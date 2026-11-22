package io.github.jamieharwood.moonphase.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import io.github.jamieharwood.moonphase.Astro
import io.github.jamieharwood.moonphase.Loc
import io.github.jamieharwood.moonphase.Snapshot
import io.github.jamieharwood.moonphase.data.LiveFetcher
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * What one astronomy complication shows. [text] is the big value (max ~7 characters),
 * [title] the short label under it, [range] an optional (value, min, max) for ring-style slots.
 */
data class AstroValue(
    val text: String,
    val title: String,
    val description: String,
    val range: Triple<Float, Float, Float>? = null,
)

/**
 * Base for the astronomy complication data sources used by the MoonPhase watch face
 * (and any other watch face). Each subclass picks one value from the [Snapshot].
 * Values that need the network (aurora, ISS, crew) are fetched with a timeout and
 * fall back to "—" when the watch is offline.
 */
abstract class AstroComplicationService : SuspendingComplicationDataSourceService() {

    /** Whether this value needs the live feeds (NOAA / TLE / crew). */
    protected open val needsLive: Boolean = false

    protected abstract fun value(s: Snapshot, settings: WearSettings): AstroValue

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val settings = WearSettings.load(this)
        var s = Astro.compute(latitude = settings.latitude, longitude = settings.longitude)
        if (needsLive) {
            withTimeoutOrNull(20_000) { LiveFetcher.fetch() }?.let { s = s.copy(live = it) }
        }
        return build(request.complicationType, value(s, settings))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val s = Astro.compute(ZonedDateTime.parse("2026-11-21T22:10:00Z"))
            .copy(live = PREVIEW_LIVE)
        return build(type, value(s, WearSettings()))
    }

    private fun build(type: ComplicationType, v: AstroValue): ComplicationData? {
        fun text(t: String) = PlainComplicationText.Builder(t).build()
        val desc = text(v.description)
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_moon)).build()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, WearActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(v.text), desc)
                .setTitle(text(v.title)).setTapAction(tap).build()
            ComplicationType.RANGED_VALUE -> {
                val (value, min, max) = v.range ?: return null
                RangedValueComplicationData.Builder(value.coerceIn(min, max), min, max, desc)
                    .setText(text(v.text)).setTitle(text(v.title)).setTapAction(tap).build()
            }
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text("${v.title} ${v.text}"), desc)
                .setMonochromaticImage(icon).setTapAction(tap).build()
            else -> null
        }
    }

    companion object {
        const val DASH = "—"
        val PREVIEW_LIVE = io.github.jamieharwood.moonphase.LiveData(
            kp = 2.7, issAltitudeKm = 418.0, issCrew = 7, peopleInSpace = 10,
        )

        /** "1.2k", "384k", "25.1M", "4.3B" – fits a short-text slot. */
        fun compact(x: Double): String = when {
            x >= 1e9 -> "%.1fB".format(x / 1e9)
            x >= 1e6 -> "%.1fM".format(x / 1e6)
            x >= 1e4 -> "%.0fk".format(x / 1e3)
            x >= 1e3 -> "%.1fk".format(x / 1e3)
            else -> "%.0f".format(x)
        }

        /** Distance in the user's units: AU stays AU unless tiny, KM/MI are compacted. */
        fun distance(au: Double, units: String): Pair<String, String> = when (units) {
            "KM" -> compact(au * Astro.KM_PER_AU) to "KM"
            "MI" -> compact(au * Astro.KM_PER_AU / Astro.KM_PER_MILE) to "MI"
            else -> (if (au >= 100) "%.1f".format(au) else "%.2f".format(au)) to "AU"
        }

        private val HM = DateTimeFormatter.ofPattern("HH:mm")

        /** Converts the snapshot's "HH:mm" UTC sun time to the watch's time zone. */
        fun localTime(utc: String, date: LocalDate): String = runCatching {
            ZonedDateTime.of(date, LocalTime.parse(utc), ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault()).format(HM)
        }.getOrDefault(utc)
    }
}

class IlluminationComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings) = AstroValue(
        "${s.moonIlluminationPercent}%", getString(R.string.ac_lit),
        getString(R.string.ac_illum_desc, s.moonIlluminationPercent),
        Triple(s.moonIlluminationPercent.toFloat(), 0f, 100f),
    )
}

class FullMoonComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val d = s.daysUntilFullMoon
        return AstroValue(
            if (d == 0) getString(R.string.ac_today) else getString(R.string.ac_days, d),
            getString(R.string.ac_full),
            if (d == 0) getString(R.string.full_moon_tonight)
            else getString(R.string.full_moon_when, Loc.daysText(this, d.toLong())),
            Triple((29.5f - d).coerceAtLeast(0f), 0f, 29.5f),
        )
    }
}

class MoonAgeComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings) = AstroValue(
        getString(R.string.ac_days, s.moonAgeDays), getString(R.string.ac_age),
        resources.getQuantityString(R.plurals.moon_age_desc, s.moonAgeDays, s.moonAgeDays),
        Triple(s.moonAgeDays.toFloat(), 0f, 29.5f),
    )
}

class MoonDistanceComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val km = s.moonDistanceKm
        val (t, unit) = if (settings.units == "MI") compact(km / Astro.KM_PER_MILE) to "MI" else compact(km) to "KM"
        // Perigee ~356,500 km, apogee ~406,700 km.
        return AstroValue(
            t, getString(R.string.ac_moon_unit, unit), getString(R.string.moon_away_desc, km),
            Triple(km.toFloat(), 356_500f, 406_700f))
    }
}

class SunComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val today = s.timestamp.withZoneSameInstant(ZoneOffset.UTC).toLocalDate()
        val rise = localTime(s.sunriseTime, today)
        val set = localTime(s.sunsetTime, today)
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        // Show the next event: sunset while the sun is up, otherwise sunrise.
        val up = now >= rise && now < set
        return AstroValue(
            if (up) set else rise, getString(if (up) R.string.ac_sunset else R.string.ac_sunrise),
            getString(R.string.sun_desc, rise, set),
        )
    }
}

class DaylightComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings) = AstroValue(
        getString(R.string.ac_hours, s.daylightHours), getString(R.string.ac_daylight),
        getString(R.string.daylight_desc, s.daylightHours),
        Triple(s.daylightHours.toFloat(), 0f, 24f),
    )
}

class SolsticeComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val winter = s.daysUntilWinterSolstice <= s.daysUntilSummerSolstice
        val d = if (winter) s.daysUntilWinterSolstice else s.daysUntilSummerSolstice
        val desc = getString(
            if (winter) R.string.winter_solstice_desc else R.string.summer_solstice_desc,
            Loc.daysText(this, d),
        )
        return AstroValue(getString(R.string.ac_days, d.toInt()), getString(R.string.ac_solstice), desc)
    }
}

class EarthSpeedComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings) = AstroValue(
        "%.2f".format(s.earthSpeedKmPerSec), "KM/S",
        getString(R.string.earth_speed_desc, s.earthSpeedKmPerSec),
        // Aphelion ~29.29 km/s, perihelion ~30.29 km/s.
        Triple(s.earthSpeedKmPerSec.toFloat(), 29.29f, 30.29f),
    )
}

class VoyagerComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val (t, unit) = distance(s.voyager1DistanceAu, settings.units)
        return AstroValue(t, "VGR1 $unit", getString(R.string.voyager_desc, s.voyager1DistanceAu))
    }
}

class AuroraComplicationService : AstroComplicationService() {
    override val needsLive = true
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val kp = s.live.kp
        return if (kp < 0) AstroValue(DASH, "KP", getString(R.string.aurora_unavailable), Triple(0f, 0f, 9f))
        else AstroValue(
            "%.1f".format(kp), "KP", getString(R.string.aurora_desc, kp, Loc.kpActivity(this, kp)),
            Triple(kp.toFloat(), 0f, 9f),
        )
    }
}

class IssComplicationService : AstroComplicationService() {
    override val needsLive = true
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val km = s.live.issAltitudeKm
        if (km < 0) return AstroValue(DASH, "ISS", getString(R.string.iss_unavailable))
        val mi = settings.units == "MI"
        val v = if (mi) km / Astro.KM_PER_MILE else km
        return AstroValue("%.0f".format(v), if (mi) "ISS MI" else "ISS KM", getString(R.string.iss_desc, km))
    }
}

class CrewComplicationService : AstroComplicationService() {
    override val needsLive = true
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val n = s.live.peopleInSpace
        return if (n < 0) AstroValue(DASH, getString(R.string.ac_in_space), getString(R.string.crew_unavailable))
        else AstroValue("$n", getString(R.string.ac_in_space), resources.getQuantityString(R.plurals.crew_desc, n, n))
    }
}
