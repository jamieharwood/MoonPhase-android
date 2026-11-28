package io.github.jamieharwood.moonphase.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.jamieharwood.moonphase.Astro
import io.github.jamieharwood.moonphase.Loc
import io.github.jamieharwood.moonphase.R
import io.github.jamieharwood.moonphase.SkyEvents
import io.github.jamieharwood.moonphase.RANGES
import io.github.jamieharwood.moonphase.Snapshot
import io.github.jamieharwood.moonphase.UiState
import io.github.jamieharwood.moonphase.data.METRICS
import io.github.jamieharwood.moonphase.data.Units
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A Solar System or Deep Space distance stored in AU. Planets use 3 decimals in AU, probes 1. */
fun distAu(au: Double, u: Units, decimals: Int = 3): String = when {
    au <= 0 -> "—"
    u == Units.KM -> "%,.0f km".format(au * Astro.KM_PER_AU)
    u == Units.MI -> "%,.0f mi".format(au * Astro.KM_PER_AU / Astro.KM_PER_MILE)
    else -> "%.${decimals}f AU".format(au)
}

/**
 * A near-Earth distance stored in km (the Moon, orbit altitudes, James Webb). Never shown in
 * AU, where 0.000003 AU means nothing: the AU setting falls back to km.
 */
fun distKm(km: Double, u: Units): String = when {
    km <= 0 -> "—"
    u == Units.MI -> "%,.0f mi".format(km / Astro.KM_PER_MILE)
    else -> "%,.0f km".format(km)
}

private fun num(v: Int) = if (v <= 0) "—" else "%,d".format(v)

/** Moon colour for the illumination figure, phase name, cycle bar and phase strip. */
@Composable
private fun moonColor(): Color = when {
    LocalNightMode.current -> MaterialTheme.colorScheme.primary
    else -> LocalPalette.current?.moon ?: MaterialTheme.colorScheme.primary
}

@Composable
fun Dashboard(
    state: UiState,
    contentPadding: PaddingValues,
    onToggleAscii: () -> Unit,
    onMetric: (Int) -> Unit,
    onRange: (Int) -> Unit,
    onExport: () -> Unit,
    pro: Boolean,
    onUnlock: () -> Unit,
) {
    val s = state.snapshot
    val u = state.settings.units
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(340.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item { MoonCard(s, u, state.settings.asciiMoon, onToggleAscii) }
        item { SolarCard(s, u) }
        item { EarthCard(s, u) }
        item { NearEarthCard(s, u, state.refreshing) }
        item { DeepSpaceCard(s, u) }
        item { EventsCard(s) }
        item {
            if (pro) SkyCalendarCard(s)
            else LockedCard(stringResource(R.string.card_sky_calendar), stringResource(R.string.pro_calendar_teaser), onUnlock)
        }
        if (state.settings.historyEnabled) {
            item(span = StaggeredGridItemSpan.FullLine) {
                if (pro) HistoryCard(state, onMetric, onRange, onExport)
                else LockedCard(stringResource(R.string.card_history), stringResource(R.string.pro_history_teaser), onUnlock)
            }
        }
    }
}

@Composable
private fun DashCard(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Card(
        Modifier.fillMaxWidth(),
        shape = palette?.let { RoundedCornerShape(it.radius) } ?: CardDefaults.shape,
        border = palette?.let { BorderStroke(1.dp, it.border) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = palette?.accent ?: Color.Unspecified, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun KV(key: String, value: String, extra: String? = null, header: Boolean = false, highlight: Boolean = false) {
    val keyStyle = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth()) {
        Text(key, style = keyStyle, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (header) FontWeight.Bold else null)
        Text(value, style = keyStyle, textAlign = TextAlign.End,
            fontWeight = if (header || highlight) FontWeight.Bold else null,
            color = if (highlight) MaterialTheme.colorScheme.error
            else if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        if (extra != null) {
            Text(extra, style = keyStyle, textAlign = TextAlign.End, modifier = Modifier.width(80.dp),
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (header) FontWeight.Bold else null)
        }
    }
}

/** "24 Dec · in 26 days", "25 Dec · tomorrow" or "Tonight", in the device's time zone. */
private fun moonDate(ctx: android.content.Context, at: ZonedDateTime, now: ZonedDateTime): String {
    val zone = ZoneId.systemDefault()
    val days = Math.round(Duration.between(now, at).seconds / 86_400.0)
    if (days <= 0) return ctx.getString(R.string.stat_tonight)
    val date = at.withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("d MMM"))
    return "$date · ${Loc.daysText(ctx, days)}"
}

/** Phase strip, New → Full → New, as Moon − Sun longitude fractions. */
private val PHASE_STRIP = List(8) { it / 8.0 }
private val STRIP_INDEX = listOf(
    "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous",
    "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
)

@Composable
private fun MoonCard(s: Snapshot, u: Units, ascii: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    val moon = moonColor()
    DashCard(stringResource(R.string.card_moon), action = {
        FilterChip(selected = ascii, onClick = onToggle, label = { Text("ASCII") })
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            if (ascii) {
                Text(s.moonAscii.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 24.dp))
            } else {
                MoonGraphic(s.moonPhaseFraction)
            }
        }
        Text("${s.moonIlluminationPercent}%", style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold, color = moon, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.moon_illuminated).uppercase(), style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(Loc.phaseName(ctx, s.moonPhaseName), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = moon, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        KV(stringResource(R.string.moon_lunar_cycle),
            stringResource(R.string.moon_cycle_day, s.moonAgeDays, "%.1f".format(Astro.SYNODIC_MONTH)))
        LinearProgressIndicator(
            progress = { (s.moonAgeDays / Astro.SYNODIC_MONTH).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = moon, trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {},
        )
        val current = STRIP_INDEX.indexOf(s.moonPhaseName)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            PHASE_STRIP.forEachIndexed { i, f -> PhaseIcon(f, i == current, moon) }
        }
        KV(stringResource(R.string.moon_next_new), moonDate(ctx, s.nextNewMoon, s.timestamp))
        KV(stringResource(R.string.moon_next_full), moonDate(ctx, s.nextFullMoon, s.timestamp))
        KV(stringResource(R.string.stat_distance), distKm(s.moonDistanceKm, u))
        val zone = ZoneId.systemDefault()
        KV(stringResource(R.string.stat_moonrise), Astro.formatMoonEvent(s.moonrise, s.timestamp, zone))
        KV(stringResource(R.string.stat_moonset), Astro.formatMoonEvent(s.moonset, s.timestamp, zone))
    }
}

/** One small moon in the lunar-cycle strip; [current] gets a ring. Waxing lights the right limb. */
@Composable
private fun PhaseIcon(phase: Double, current: Boolean, lit: Color) {
    val dark = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val ring = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(26.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.31f
        drawCircle(dark, r, c)
        when {
            abs(phase - 0.5) < 1e-6 -> drawCircle(lit, r, c)
            phase > 1e-6 -> {
                val tx = cos(2 * PI * phase).toFloat()
                val side = if (phase < 0.5) 1f else -1f
                val path = Path()
                val n = 32
                for (i in 0..n) { // limb, top -> bottom on the lit side
                    val t = -PI / 2 + PI * i / n
                    val x = c.x + side * r * cos(t).toFloat()
                    val y = c.y + r * sin(t).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                for (i in 0..n) { // terminator, bottom -> top
                    val t = PI / 2 - PI * i / n
                    path.lineTo(c.x + side * tx * r * cos(t).toFloat(), c.y + r * sin(t).toFloat())
                }
                path.close()
                drawPath(path, lit)
            }
        }
        if (current) drawCircle(ring, size.minDimension * 0.44f, c, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun SolarCard(s: Snapshot, u: Units) = DashCard(stringResource(R.string.card_solar)) {
    val ctx = LocalContext.current
    KV(stringResource(R.string.col_sun_from), stringResource(R.string.col_distance), stringResource(R.string.col_light), header = true)
    s.heliocentricAu.forEach { (p, au) -> KV(Labels.planet(ctx, p), distAu(au, u), s.lightTimes[p]) }
}

@Composable
private fun EarthCard(s: Snapshot, u: Units) = DashCard(stringResource(R.string.card_earth)) {
    val kp = s.live.kp
    val ctx = LocalContext.current
    if (u == Units.MI) {
        KV(stringResource(R.string.earth_orbital_speed), "%.2f mi/s".format(s.earthSpeedKmPerSec / Astro.KM_PER_MILE))
        KV(stringResource(R.string.earth_speed), "%,.0f mph".format(s.earthSpeedKmPerHour / Astro.KM_PER_MILE))
    } else {
        KV(stringResource(R.string.earth_orbital_speed), "%.2f km/s".format(s.earthSpeedKmPerSec))
        KV(stringResource(R.string.earth_speed), "%,.0f km/h".format(s.earthSpeedKmPerHour))
    }
    KV(stringResource(R.string.earth_daylight), "%.2f h".format(s.daylightHours))
    KV(stringResource(R.string.earth_sunrise), s.sunriseTime)
    KV(stringResource(R.string.earth_sunset), s.sunsetTime)
    KV(stringResource(R.string.earth_axial_tilt), "%.3f°".format(s.earthAxialTiltDegrees))
    KV(stringResource(R.string.earth_aurora_kp), if (kp < 0) "—" else "%.2f (%s)".format(kp, Loc.kpActivity(ctx, kp)), highlight = kp >= 5)
    KV(stringResource(R.string.earth_location), "%.4f°, %.4f°".format(s.latitude, s.longitude))
}

/** Stations and telescopes, satellite constellations and the people in space. */
@Composable
private fun NearEarthCard(s: Snapshot, u: Units, loading: Boolean) = DashCard(stringResource(R.string.card_near)) {
    val l = s.live
    KV(stringResource(R.string.near_stations), stringResource(R.string.near_altitude), header = true)
    KV("ISS", distKm(l.issAltitudeKm, u))
    KV("Tiangong", distKm(l.tiangongAltitudeKm, u))
    KV("Hubble", distKm(l.hubbleAltitudeKm, u))
    Spacer(Modifier.height(4.dp))
    KV(stringResource(R.string.near_constellations), stringResource(R.string.near_satellites), header = true)
    KV("Starlink", num(l.starlink))
    KV(stringResource(R.string.leo_kuiper), num(l.kuiper))
    if (l.totalInOrbit > 0) KV(stringResource(R.string.leo_total), num(l.totalInOrbit))
    Spacer(Modifier.height(4.dp))
    KV(stringResource(R.string.near_people), num(l.peopleInSpace), header = true)
    if (l.crafts.isEmpty()) {
        Text(stringResource(if (loading) R.string.waiting_live else R.string.unavailable), style = MaterialTheme.typography.bodyMedium)
    } else {
        l.crafts.forEach { (c, n) -> KV(c, n.toString()) }
    }
    if (l.issCrew > 0) {
        Text(pluralStringResource(R.plurals.near_iss_note, l.issCrew, l.issCrew), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (loading) Text(stringResource(R.string.fetching_live), style = MaterialTheme.typography.labelSmall)
}

/** Distances from Earth. James Webb is live from JPL Horizons; the probes are calculated. */
@Composable
private fun DeepSpaceCard(s: Snapshot, u: Units) = DashCard(stringResource(R.string.card_deep)) {
    KV("James Webb (L2)", distKm(s.live.jwstKm, u))
    KV("Voyager 1", distAu(s.voyager1DistanceAu, u, 1))
    KV("Voyager 2", distAu(s.voyager2DistanceAu, u, 1))
    KV("New Horizons", distAu(s.newHorizonsDistanceAu, u, 1))
}

/** Solstices, perihelion and aphelion, soonest first (the full moon is on the Moon card). */
@Composable
private fun EventsCard(s: Snapshot) = DashCard(stringResource(R.string.card_events)) {
    val ctx = LocalContext.current
    listOf(
        R.string.ev_summer_solstice to s.daysUntilSummerSolstice,
        R.string.ev_winter_solstice to s.daysUntilWinterSolstice,
        R.string.ev_perihelion to s.daysUntilPerihelion,
        R.string.ev_aphelion to s.daysUntilAphelion,
    ).sortedBy { it.second }.forEach { (n, d) ->
        KV(stringResource(n), Loc.daysCount(ctx, d))
    }
}

@Composable
private fun SkyCalendarCard(s: Snapshot) = DashCard(stringResource(R.string.card_sky_calendar)) {
    val ctx = LocalContext.current
    val today = s.timestamp.toLocalDate()
    SkyEvents.upcoming(today, 8).forEach { e ->
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            KV("${e.icon} ${Loc.eventName(ctx, e)}", "${e.date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))} · ${Loc.daysText(ctx, e.daysFrom(today))}")
            Text(Loc.eventDetail(ctx, e), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryCard(state: UiState, onMetric: (Int) -> Unit, onRange: (Int) -> Unit, onExport: () -> Unit) =
    DashCard(stringResource(R.string.card_history), action = { TextButton(onClick = onExport) { Text("↓ CSV") } }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val ctx = LocalContext.current
            Picker(METRICS.map { Labels.metric(ctx, it) }, state.metricIndex, onMetric, Modifier.weight(1f))
            Picker(
                RANGES.map { r -> r.first?.let { d -> ctx.getString(R.string.range_days, d) } ?: ctx.getString(R.string.range_all) },
                state.rangeIndex, onRange,
            )
        }
        HistoryChart(state.points, emptyText = stringResource(R.string.history_no_data))
        Text(stringResource(R.string.history_points, state.points.size, state.stored),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable
fun Picker(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedButton(onClick = { open = true }) { Text(options[selected], maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}
