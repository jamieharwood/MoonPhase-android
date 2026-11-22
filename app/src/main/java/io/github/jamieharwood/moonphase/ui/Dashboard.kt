package io.github.jamieharwood.moonphase.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
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

fun distAu(au: Double, u: Units): String = when (u) {
    Units.KM -> "%,.0f km".format(au * Astro.KM_PER_AU)
    Units.MI -> "%,.0f mi".format(au * Astro.KM_PER_AU / Astro.KM_PER_MILE)
    Units.AU -> if (au < 100) "%.4f AU".format(au) else "%.2f AU".format(au)
}

fun distKm(km: Double, u: Units): String = when {
    km < 0 -> "—"
    u == Units.AU -> "%.6f AU".format(km / Astro.KM_PER_AU)
    u == Units.MI -> "%,.0f mi".format(km / Astro.KM_PER_MILE)
    else -> "%,.0f km".format(km)
}

private fun num(v: Int) = if (v < 0) "—" else "%,d".format(v)

@Composable
fun Dashboard(
    state: UiState,
    contentPadding: PaddingValues,
    onToggleAscii: () -> Unit,
    onMetric: (Int) -> Unit,
    onRange: (Int) -> Unit,
    onExport: () -> Unit,
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
        item { ProbesCard(s, u) }
        item { EarthCard(s) }
        item { EventsCard(s) }
        item { SkyCalendarCard(s) }
        item { LeoCard(s, u, state.refreshing) }
        item { PeopleCard(s, state.refreshing) }
        if (state.settings.historyEnabled) {
            item(span = StaggeredGridItemSpan.FullLine) { HistoryCard(state, onMetric, onRange, onExport) }
        }
    }
}

@Composable
private fun DashCard(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
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
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if (extra != null) {
            Text(extra, style = keyStyle, textAlign = TextAlign.End, modifier = Modifier.width(80.dp),
                fontWeight = if (header) FontWeight.Bold else null)
        }
    }
}

@Composable
private fun MoonCard(s: Snapshot, u: Units, ascii: Boolean, onToggle: () -> Unit) {
    DashCard(stringResource(R.string.card_moon), action = {
        FilterChip(selected = ascii, onClick = onToggle, label = { Text("ASCII") })
    }) {
        Text(Loc.phaseName(LocalContext.current, s.moonPhaseName), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            if (ascii) {
                Text(s.moonAscii.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 24.dp))
            } else {
                MoonGraphic(s.moonPhaseFraction)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Stat("${s.moonIlluminationPercent}%", stringResource(R.string.stat_illumination))
            Stat(stringResource(R.string.days_short, s.moonAgeDays), stringResource(R.string.stat_age))
            Stat(
                if (s.daysUntilFullMoon == 0) stringResource(R.string.stat_tonight)
                else stringResource(R.string.days_short, s.daysUntilFullMoon),
                stringResource(R.string.stat_full_moon_in),
            )
            Stat(distKm(s.moonDistanceKm, u), stringResource(R.string.stat_distance))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SolarCard(s: Snapshot, u: Units) = DashCard(stringResource(R.string.card_solar)) {
    val ctx = LocalContext.current
    KV(stringResource(R.string.col_planet), stringResource(R.string.col_from_sun), stringResource(R.string.col_light), header = true)
    s.heliocentricAu.forEach { (p, au) -> KV(Labels.planet(ctx, p), distAu(au, u), s.lightTimes[p]) }
    Spacer(Modifier.height(4.dp))
    KV(stringResource(R.string.from_earth), "", header = true)
    KV(stringResource(R.string.planet_mars), distAu(s.marsDistanceAu, u))
    KV(stringResource(R.string.planet_jupiter), distAu(s.jupiterDistanceAu, u))
    KV(stringResource(R.string.planet_saturn), distAu(s.saturnDistanceAu, u))
}

@Composable
private fun ProbesCard(s: Snapshot, u: Units) = DashCard(stringResource(R.string.card_probes)) {
    val kmU = if (u == Units.AU) Units.KM else u
    KV(stringResource(R.string.probe_hubble_alt), distKm(s.live.hubbleAltitudeKm, kmU))
    KV("James Webb", distKm(s.jamesWebbDistanceKm, u))
    KV("Voyager 1", distAu(s.voyager1DistanceAu, u))
    KV("Voyager 2", distAu(s.voyager2DistanceAu, u))
    KV("New Horizons", distAu(s.newHorizonsDistanceAu, u))
    Spacer(Modifier.height(4.dp))
    KV(stringResource(R.string.light_time_from_sun), "", header = true)
    KV("Voyager 1", s.lightTimes["Voyager 1"] ?: "")
    KV("Voyager 2", s.lightTimes["Voyager 2"] ?: "")
}

@Composable
private fun EarthCard(s: Snapshot) = DashCard(stringResource(R.string.card_earth)) {
    val kp = s.live.kp
    val ctx = LocalContext.current
    KV(stringResource(R.string.earth_orbital_speed), "%.2f km/s".format(s.earthSpeedKmPerSec))
    KV(stringResource(R.string.earth_speed_kmh), "%,.0f km/h".format(s.earthSpeedKmPerHour))
    KV(stringResource(R.string.earth_daylight), "%.2f h".format(s.daylightHours))
    KV(stringResource(R.string.earth_sunrise), s.sunriseTime)
    KV(stringResource(R.string.earth_sunset), s.sunsetTime)
    KV(stringResource(R.string.earth_axial_tilt), "%.3f°".format(s.earthAxialTiltDegrees))
    KV(stringResource(R.string.earth_aurora_kp), if (kp < 0) "—" else "%.2f (%s)".format(kp, Loc.kpActivity(ctx, kp)), highlight = kp >= 5)
    KV(stringResource(R.string.earth_location), "%.4f°, %.4f°".format(s.latitude, s.longitude))
}

@Composable
private fun EventsCard(s: Snapshot) = DashCard(stringResource(R.string.card_events)) {
    val ctx = LocalContext.current
    listOf(
        R.string.ev_full_moon to s.daysUntilFullMoon.toLong(),
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
private fun LeoCard(s: Snapshot, u: Units, loading: Boolean) = DashCard(stringResource(R.string.card_leo)) {
    val kmU = if (u == Units.AU) Units.KM else u
    val l = s.live
    KV(stringResource(R.string.leo_iss_altitude), distKm(l.issAltitudeKm, kmU))
    KV(stringResource(R.string.leo_iss_crew), num(l.issCrew))
    KV(stringResource(R.string.leo_tiangong_altitude), distKm(l.tiangongAltitudeKm, kmU))
    KV(stringResource(R.string.leo_hubble_altitude), distKm(l.hubbleAltitudeKm, kmU))
    KV(stringResource(R.string.leo_starlink), num(l.starlink))
    KV(stringResource(R.string.leo_kuiper), num(l.kuiper))
    KV(stringResource(R.string.leo_total), num(l.totalInOrbit))
    if (loading) Text(stringResource(R.string.fetching_live), style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun PeopleCard(s: Snapshot, loading: Boolean) = DashCard(stringResource(R.string.card_people)) {
    KV(stringResource(R.string.people_spacecraft), stringResource(R.string.people_total, num(s.live.peopleInSpace)), header = true)
    if (s.live.crafts.isEmpty()) {
        Text(stringResource(if (loading) R.string.waiting_live else R.string.unavailable), style = MaterialTheme.typography.bodyMedium)
    } else {
        s.live.crafts.forEach { (c, n) -> KV(c, n.toString()) }
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
