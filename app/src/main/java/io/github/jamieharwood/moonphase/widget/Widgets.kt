package io.github.jamieharwood.moonphase.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.jamieharwood.moonphase.Astro
import io.github.jamieharwood.moonphase.MainActivity
import io.github.jamieharwood.moonphase.Snapshot
import kotlin.math.min
import kotlin.math.roundToInt

private val NIGHT = Color(0xFF0B1530)
private val WHITE = ColorProvider(Color(0xFFFFFFFF))
private val MUTED = ColorProvider(Color(0xFFB8C2D9))
private val MOON = ColorProvider(Color(0xFFF4E3A6))
private val STORM = ColorProvider(Color(0xFFFF8A80))

private fun fullMoonText(s: Snapshot): String = when (val d = s.daysUntilFullMoon) {
    0 -> "Full moon tonight"
    1 -> "Full moon tomorrow"
    else -> "Full moon in $d days"
}

@Composable
private fun WidgetFrame(content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(NIGHT).cornerRadius(20.dp).padding(10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun MoonImage(s: Snapshot, sizeDp: Int) {
    val px = (sizeDp * LocalContext.current.resources.displayMetrics.density).roundToInt().coerceIn(48, 480)
    Image(
        ImageProvider(WidgetData.moonBitmap(s.moonPhaseFraction, px)),
        contentDescription = "${s.moonPhaseName}, ${s.moonIlluminationPercent}% illuminated",
        modifier = GlanceModifier.size(sizeDp.dp),
    )
}

@Composable
private fun Line(text: String, size: TextUnit = 13.sp, color: ColorProvider = MUTED, bold: Boolean = false, center: Boolean = false) =
    Text(
        text,
        maxLines = 1,
        style = TextStyle(
            color = color, fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        ),
    )

/** Small square widget: the Moon, its phase name, illumination and days to full moon. */
class MoonWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData.snapshot(context)
        provideContent { MoonContent(s) }
    }

    @Composable
    private fun MoonContent(s: Snapshot) {
        val size = LocalSize.current
        val roomy = size.height >= 150.dp
        val textDp = if (roomy) 60 else 48 // two text lines + spacing, incl. descenders
        val moon = min(size.width.value - 20, size.height.value - 20 - textDp).toInt().coerceAtLeast(32)
        WidgetFrame {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically) {
                MoonImage(s, moon)
                Spacer(GlanceModifier.height(4.dp))
                Line(s.moonPhaseName, if (roomy) 15.sp else 13.sp, WHITE, bold = true, center = true)
                Line(
                    if (roomy) "${s.moonIlluminationPercent}% · ${fullMoonText(s).lowercase()}"
                    else "${s.moonIlluminationPercent}% · full in ${s.daysUntilFullMoon}d",
                    if (roomy) 12.sp else 11.sp, MOON, center = true,
                )
            }
        }
    }
}

/** Wide widget: the Moon plus the sky at a glance (sun, aurora, ISS, people in space). */
class SkyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(320.dp, 110.dp), DpSize(320.dp, 180.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData.snapshot(context)
        provideContent { SkyContent(s) }
    }

    @Composable
    private fun SkyContent(s: Snapshot) {
        val size = LocalSize.current
        val tall = size.height >= 160.dp
        val moon = min(size.height.value - 20, size.width.value * 0.42f).toInt().coerceAtLeast(48)
        val (rise, set) = WidgetData.localSunTimes(s)
        val l = s.live
        val kp = if (l.kp < 0) "—" else "Kp %.1f · %s".format(l.kp, Astro.kpActivity(l.kp))
        val iss = buildString {
            append(if (l.issAltitudeKm < 0) "ISS —" else "ISS %.0f km".format(l.issAltitudeKm))
            if (l.peopleInSpace >= 0) append(" · ${l.peopleInSpace} in space")
        }
        WidgetFrame {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                MoonImage(s, moon)
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Line(s.moonPhaseName, if (tall) 18.sp else 16.sp, WHITE, bold = true)
                    Line("${s.moonIlluminationPercent}% lit · ${s.moonAgeDays} days old", 12.sp, MOON)
                    Spacer(GlanceModifier.height(if (tall) 8.dp else 4.dp))
                    Line("🌕 ${fullMoonText(s)}")
                    Line("☀️ $rise – $set")
                    Line("🌌 $kp", color = if (l.kp >= 5) STORM else MUTED)
                    if (tall) {
                        Line("🛰️ $iss")
                        Line("🌙 %,.0f km away".format(s.moonDistanceKm))
                    }
                }
            }
        }
    }
}

class MoonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoonWidget()
}

class SkyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SkyWidget()
}
