package io.github.jamieharwood.moonphase.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TitleCard
import io.github.jamieharwood.moonphase.Astro
import io.github.jamieharwood.moonphase.Snapshot
import io.github.jamieharwood.moonphase.data.LiveFetcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WearApp() } }
    }
}

private fun compute(settings: WearSettings) =
    Astro.compute(latitude = settings.latitude, longitude = settings.longitude)

@Composable
fun WearApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(WearSettings.load(ctx)) }
    var snapshot by remember { mutableStateOf(compute(settings)) }
    var loading by remember { mutableStateOf(false) }

    fun refresh(force: Boolean) = scope.launch {
        loading = true
        if (force) LiveFetcher.clear()
        WearSettings.syncFromDataLayer(ctx)
        settings = WearSettings.load(ctx)
        snapshot = compute(settings).copy(live = LiveFetcher.fetch())
        loading = false
    }

    LaunchedEffect(Unit) {
        refresh(false)
        while (true) { // keep the deterministic values current while the app is open
            delay(60_000)
            snapshot = compute(settings).copy(live = snapshot.live)
        }
    }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            item { MoonDisc(snapshot.moonPhaseFraction, Modifier.size(96.dp)) }
            item {
                Text(snapshot.moonPhaseName, style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            item {
                Text("${snapshot.moonIlluminationPercent}% lit · ${snapshot.moonAgeDays} days old",
                    style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
            }
            item {
                val d = snapshot.daysUntilFullMoon
                InfoCard("🌕 Full moon", if (d == 0) "Tonight" else "In $d day${if (d == 1) "" else "s"}")
            }
            item { SunCard(snapshot, settings) }
            item { MoonTimesCard(snapshot) }
            item {
                val kp = snapshot.live.kp
                InfoCard("🌌 Aurora", if (kp < 0) "—" else "Kp %.1f · %s".format(kp, Astro.kpActivity(kp)),
                    highlight = kp >= 5)
            }
            item {
                val l = snapshot.live
                InfoCard("🛰️ ISS",
                    (if (l.issAltitudeKm < 0) "—" else "%,.0f km up".format(l.issAltitudeKm)) +
                        (if (l.issCrew < 0) "" else " · ${l.issCrew} crew"))
            }
            item {
                val n = snapshot.live.peopleInSpace
                InfoCard("👨‍🚀 People in space", if (n < 0) "—" else n.toString(),
                    snapshot.live.crafts.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifEmpty { null })
            }
            item { InfoCard("🌙 Moon distance", dist(snapshot.moonDistanceKm / Astro.KM_PER_AU, settings.units, km = true)) }
            item { EventsCard(snapshot) }
            item {
                InfoCard("🚀 Voyager 1", dist(snapshot.voyager1DistanceAu, settings.units),
                    "Light from Sun: ${snapshot.lightTimes["Voyager 1"]}")
            }
            item {
                InfoCard("☀️ Earth", "%.2f km/s".format(snapshot.earthSpeedKmPerSec),
                    "Sun: ${dist(snapshot.heliocentricAu.getValue("Earth"), settings.units)}\n" +
                        "Tilt %.3f°".format(snapshot.earthAxialTiltDegrees))
            }
            item {
                CompactChip(
                    onClick = { refresh(true) },
                    label = { Text(if (loading) "Updating…" else "Refresh") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
            item {
                Text(
                    if (settings.fromPhone) "Location from phone" else "Greenwich · open phone app to sync",
                    style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

private fun dist(au: Double, units: String, km: Boolean = false): String = when {
    units == "MI" -> "%,.0f mi".format(au * Astro.KM_PER_AU / Astro.KM_PER_MILE)
    units == "KM" || km -> "%,.0f km".format(au * Astro.KM_PER_AU)
    au < 100 -> "%.4f AU".format(au)
    else -> "%.2f AU".format(au)
}

@Composable
private fun InfoCard(title: String, value: String, detail: String? = null, highlight: Boolean = false) {
    TitleCard(onClick = {}, title = { Text(title) }, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(value, fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colors.error else MaterialTheme.colors.onSurface)
            if (detail != null) Text(detail, style = MaterialTheme.typography.caption2)
        }
    }
}

@Composable
private fun SunCard(s: Snapshot, settings: WearSettings) = InfoCard(
    "🌅 Sun (UTC)",
    "${s.sunriseTime} – ${s.sunsetTime}",
    "%.1f h daylight\n%.2f°, %.2f°".format(s.daylightHours, settings.latitude, settings.longitude),
)

@Composable
private fun MoonTimesCard(s: Snapshot) {
    val zone = java.time.ZoneId.systemDefault()
    InfoCard(
        "🌙 Moonrise / set",
        "${Astro.formatMoonEvent(s.moonrise, s.timestamp, zone)} – ${Astro.formatMoonEvent(s.moonset, s.timestamp, zone)}",
        "Next rise and set, local time",
    )
}

@Composable
private fun EventsCard(s: Snapshot) {
    val events = listOf(
        "Winter solstice" to s.daysUntilWinterSolstice,
        "Summer solstice" to s.daysUntilSummerSolstice,
        "Perihelion" to s.daysUntilPerihelion,
        "Aphelion" to s.daysUntilAphelion,
    ).sortedBy { it.second }
    TitleCard(onClick = {}, title = { Text("📅 Upcoming") }, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            events.forEach { (name, d) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.caption2, modifier = Modifier.weight(1f))
                    Text("$d d", style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Moon disc with a terminator matching [phase] (0 new … 0.5 full). Static to save battery. */
@Composable
fun MoonDisc(phase: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.46f
        drawCircle(Color(0xFFEDE6CC), r, c)
        listOf(floatArrayOf(-0.35f, -0.30f, 0.16f), floatArrayOf(0.30f, -0.42f, 0.10f),
            floatArrayOf(0.18f, 0.05f, 0.22f), floatArrayOf(-0.20f, 0.38f, 0.13f)).forEach { k ->
            drawCircle(Color(0x8C9E998C), k[2] * r, Offset(c.x + k[0] * r, c.y + k[1] * r))
        }
        val tx = cos(2 * PI * phase).toFloat()
        val side = if (phase < 0.5) 1f else -1f
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(c, r))
            val n = 48
            for (i in 0..n) {
                val a = -PI / 2 + PI * i / n
                val x = c.x + side * r * cos(a).toFloat()
                val y = c.y + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (i in 0..n) {
                val a = PI / 2 - PI * i / n
                lineTo(c.x + side * tx * r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            }
            close()
        }
        drawPath(path, Color(0xE6000000))
    }
}
