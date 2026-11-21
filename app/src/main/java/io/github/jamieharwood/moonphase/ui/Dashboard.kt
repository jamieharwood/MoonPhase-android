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
import io.github.jamieharwood.moonphase.Astro
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
    DashCard("🌙 Moon Phase", action = {
        FilterChip(selected = ascii, onClick = onToggle, label = { Text("ASCII") })
    }) {
        Text(s.moonPhaseName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
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
            Stat("${s.moonIlluminationPercent}%", "Illumination")
            Stat("${s.moonAgeDays} d", "Age")
            Stat(if (s.daysUntilFullMoon == 0) "Tonight" else "${s.daysUntilFullMoon} d", "Full Moon In")
            Stat(distKm(s.moonDistanceKm, u), "Distance")
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
private fun SolarCard(s: Snapshot, u: Units) = DashCard("☀️ Solar System") {
    KV("Planet", "From Sun", "Light", header = true)
    s.heliocentricAu.forEach { (p, au) -> KV(p, distAu(au, u), s.lightTimes[p]) }
    Spacer(Modifier.height(4.dp))
    KV("From Earth", "", header = true)
    KV("Mars", distAu(s.marsDistanceAu, u))
    KV("Jupiter", distAu(s.jupiterDistanceAu, u))
    KV("Saturn", distAu(s.saturnDistanceAu, u))
}

@Composable
private fun ProbesCard(s: Snapshot, u: Units) = DashCard("🚀 Deep Space Probes") {
    val kmU = if (u == Units.AU) Units.KM else u
    KV("Hubble (altitude)", distKm(s.live.hubbleAltitudeKm, kmU))
    KV("James Webb", distKm(s.jamesWebbDistanceKm, u))
    KV("Voyager 1", distAu(s.voyager1DistanceAu, u))
    KV("Voyager 2", distAu(s.voyager2DistanceAu, u))
    KV("New Horizons", distAu(s.newHorizonsDistanceAu, u))
    Spacer(Modifier.height(4.dp))
    KV("Light time from Sun", "", header = true)
    KV("Voyager 1", s.lightTimes["Voyager 1"] ?: "")
    KV("Voyager 2", s.lightTimes["Voyager 2"] ?: "")
}

@Composable
private fun EarthCard(s: Snapshot) = DashCard("🌍 Earth") {
    val kp = s.live.kp
    KV("Orbital Speed", "%.2f km/s".format(s.earthSpeedKmPerSec))
    KV("Speed (km/h)", "%,.0f km/h".format(s.earthSpeedKmPerHour))
    KV("Daylight Hours", "%.2f h".format(s.daylightHours))
    KV("Sunrise (UTC~)", s.sunriseTime)
    KV("Sunset (UTC~)", s.sunsetTime)
    KV("Axial Tilt", "%.3f°".format(s.earthAxialTiltDegrees))
    KV("Aurora Kp Index", if (kp < 0) "—" else "%.2f (%s)".format(kp, Astro.kpActivity(kp)), highlight = kp >= 5)
    KV("Location", "%.4f°, %.4f°".format(s.latitude, s.longitude))
}

@Composable
private fun EventsCard(s: Snapshot) = DashCard("📅 Upcoming Events") {
    listOf(
        "Full Moon" to s.daysUntilFullMoon.toLong(),
        "Summer Solstice" to s.daysUntilSummerSolstice,
        "Winter Solstice" to s.daysUntilWinterSolstice,
        "Perihelion" to s.daysUntilPerihelion,
        "Aphelion" to s.daysUntilAphelion,
    ).sortedBy { it.second }.forEach { (n, d) ->
        KV(n, if (d == 0L) "today" else "$d day${if (d == 1L) "" else "s"}")
    }
}

@Composable
private fun LeoCard(s: Snapshot, u: Units, loading: Boolean) = DashCard("🛰️ LEO (Low Earth Orbit)") {
    val kmU = if (u == Units.AU) Units.KM else u
    val l = s.live
    KV("ISS Altitude", distKm(l.issAltitudeKm, kmU))
    KV("ISS Crew", num(l.issCrew))
    KV("Tiangong Altitude", distKm(l.tiangongAltitudeKm, kmU))
    KV("Hubble Altitude", distKm(l.hubbleAltitudeKm, kmU))
    KV("Starlink Satellites", num(l.starlink))
    KV("Amazon Kuiper", num(l.kuiper))
    KV("Total in Orbit", num(l.totalInOrbit))
    if (loading) Text("Fetching live data…", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun PeopleCard(s: Snapshot, loading: Boolean) = DashCard("👨‍🚀 People in Space") {
    KV("Spacecraft", "Total: ${num(s.live.peopleInSpace)}", header = true)
    if (s.live.crafts.isEmpty()) {
        Text(if (loading) "Waiting for live data…" else "Unavailable", style = MaterialTheme.typography.bodyMedium)
    } else {
        s.live.crafts.forEach { (c, n) -> KV(c, n.toString()) }
    }
}

@Composable
private fun HistoryCard(state: UiState, onMetric: (Int) -> Unit, onRange: (Int) -> Unit, onExport: () -> Unit) =
    DashCard("📊 Historical Data", action = { TextButton(onClick = onExport) { Text("↓ CSV") } }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Picker(METRICS.map { "${it.group} · ${it.label}" }, state.metricIndex, onMetric, Modifier.weight(1f))
            Picker(RANGES.map { it.second }, state.rangeIndex, onRange)
        }
        HistoryChart(state.points)
        Text("${state.points.size} points shown · ${state.stored} snapshots stored on this device",
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
