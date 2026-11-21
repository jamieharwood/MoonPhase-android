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
        "${s.moonIlluminationPercent}%", "LIT",
        "Moon ${s.moonIlluminationPercent}% illuminated",
        Triple(s.moonIlluminationPercent.toFloat(), 0f, 100f),
    )
}

class FullMoonComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val d = s.daysUntilFullMoon
        return AstroValue(
            if (d == 0) "TODAY" else "${d}D", "FULL",
            if (d == 0) "Full moon tonight" else "Full moon in $d days",
            Triple((29.5f - d).coerceAtLeast(0f), 0f, 29.5f),
        )
    }
}

class MoonAgeComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings) = AstroValue(
        "${s.moonAgeDays}D", "AGE", "Moon is ${s.moonAgeDays} days old",
        Triple(s.moonAgeDays.toFloat(), 0f, 29.5f),
    )
}

class MoonDistanceComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val km = s.moonDistanceKm
        val (t, unit) = if (settings.units == "MI") compact(km / Astro.KM_PER_MILE) to "MI" else compact(km) to "KM"
        // Perigee ~356,500 km, apogee ~406,700 km.
        return AstroValue(t, "MOON $unit", "Moon is %,.0f km away".format(km), Triple(km.toFloat(), 356_500f, 406_700f))
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
            if (up) set else rise, if (up) "SUNSET" else "SUNRISE",
            "Sunrise $rise, sunset $set",
        )
    }
}

class DaylightComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings) = AstroValue(
        "%.1fH".format(s.daylightHours), "DAYLIGHT", "%.1f hours of daylight".format(s.daylightHours),
        Triple(s.daylightHours.toFloat(), 0f, 24f),
    )
}

class SolsticeComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val winter = s.daysUntilWinterSolstice <= s.daysUntilSummerSolstice
        val d = if (winter) s.daysUntilWinterSolstice else s.daysUntilSummerSolstice
        val name = if (winter) "Winter" else "Summer"
        return AstroValue("${d}D", "SOLSTICE", "$name solstice in $d days")
    }
}

class EarthSpeedComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings) = AstroValue(
        "%.2f".format(s.earthSpeedKmPerSec), "KM/S",
        "Earth orbits the Sun at %.2f km/s".format(s.earthSpeedKmPerSec),
        // Aphelion ~29.29 km/s, perihelion ~30.29 km/s.
        Triple(s.earthSpeedKmPerSec.toFloat(), 29.29f, 30.29f),
    )
}

class VoyagerComplicationService : AstroComplicationService() {
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val (t, unit) = distance(s.voyager1DistanceAu, settings.units)
        return AstroValue(t, "VGR1 $unit", "Voyager 1 is %.2f AU from Earth".format(s.voyager1DistanceAu))
    }
}

class AuroraComplicationService : AstroComplicationService() {
    override val needsLive = true
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val kp = s.live.kp
        return if (kp < 0) AstroValue(DASH, "KP", "Aurora Kp unavailable", Triple(0f, 0f, 9f))
        else AstroValue(
            "%.1f".format(kp), "KP", "Aurora Kp %.1f, %s".format(kp, Astro.kpActivity(kp)),
            Triple(kp.toFloat(), 0f, 9f),
        )
    }
}

class IssComplicationService : AstroComplicationService() {
    override val needsLive = true
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val km = s.live.issAltitudeKm
        if (km < 0) return AstroValue(DASH, "ISS", "ISS altitude unavailable")
        val mi = settings.units == "MI"
        val v = if (mi) km / Astro.KM_PER_MILE else km
        return AstroValue("%.0f".format(v), if (mi) "ISS MI" else "ISS KM", "ISS is %.0f km up".format(km))
    }
}

class CrewComplicationService : AstroComplicationService() {
    override val needsLive = true
    override fun value(s: Snapshot, settings: WearSettings): AstroValue {
        val n = s.live.peopleInSpace
        return if (n < 0) AstroValue(DASH, "IN SPACE", "People in space unavailable")
        else AstroValue("$n", "IN SPACE", "$n people in space")
    }
}
