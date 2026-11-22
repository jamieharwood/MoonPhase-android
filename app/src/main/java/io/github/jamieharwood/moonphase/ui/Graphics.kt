package io.github.jamieharwood.moonphase.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jamieharwood.moonphase.data.Point
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = 2 * PI

/** True when the red night-vision theme is active. */
val LocalNightMode = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Red-on-black palette for stargazing: no blue or green light, so dark-adapted eyes keep their sensitivity. */
val NightColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFFF5252), onPrimary = Color(0xFF200000),
    secondary = Color(0xFFD84343), onSecondary = Color(0xFF200000),
    tertiary = Color(0xFFB83030),
    background = Color(0xFF000000), onBackground = Color(0xFFE04040),
    surface = Color(0xFF000000), onSurface = Color(0xFFE04040),
    surfaceVariant = Color(0xFF2A0505), onSurfaceVariant = Color(0xFFB03030),
    surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF140000),
    surfaceContainer = Color(0xFF1A0000), surfaceContainerHigh = Color(0xFF220303),
    surfaceContainerHighest = Color(0xFF2A0505),
    outline = Color(0xFF7F1D1D), outlineVariant = Color(0xFF450A0A),
    error = Color(0xFFFF8A80), onError = Color(0xFF200000),
    primaryContainer = Color(0xFF4A0A0A), onPrimaryContainer = Color(0xFFFF8A80),
    secondaryContainer = Color(0xFF3A0808), onSecondaryContainer = Color(0xFFE04040),
)

/** Maps any colour to a dim red of the same brightness when night mode is on. */
@Composable
private fun nightTone(c: Color): Color {
    if (!LocalNightMode.current) return c
    val l = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue).coerceIn(0f, 1f)
    return Color(red = 0.15f + 0.65f * l, green = 0f, blue = 0f, alpha = c.alpha)
}

/** Moon disc with craters, a terminator matching [phase] (0 new … 0.5 full) and twinkling stars. */
@Composable
fun MoonGraphic(phase: Double, size: Dp = 200.dp) {
    val stars = remember {
        val r = Random(42)
        List(70) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.4f, r.nextFloat() * TAU.toFloat()) }
    }
    val craters = remember {
        listOf(
            floatArrayOf(-0.35f, -0.30f, 0.16f), floatArrayOf(0.30f, -0.42f, 0.10f), floatArrayOf(0.18f, 0.05f, 0.22f),
            floatArrayOf(-0.20f, 0.38f, 0.13f), floatArrayOf(0.48f, 0.30f, 0.09f), floatArrayOf(-0.55f, 0.05f, 0.08f),
        )
    }
    val t by rememberInfiniteTransition(label = "twinkle").animateFloat(
        0f, TAU.toFloat() * 10, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val sky = nightTone(Color(0xFF050A1A))
    val starColor = nightTone(Color(1f, 1f, 0.95f))
    val disc = nightTone(Color(0xFFEDE6CC))
    val crater = nightTone(Color(0x8C9E998C))
    val shadow = nightTone(Color(0xE0080D1A))
    val rim = nightTone(Color(0x26FFF7D9))
    Canvas(Modifier.size(size).clip(RoundedCornerShape(16.dp))) {
        drawRect(sky)
        for (s in stars) {
            val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 1.7f + s[3]))
            drawCircle(starColor.copy(alpha = a), s[2], Offset(s[0] * this.size.width, s[1] * this.size.height))
        }
        val c = center
        val r = this.size.minDimension * 0.36f
        drawCircle(disc, r, c)
        for (k in craters) drawCircle(crater, k[2] * r, Offset(c.x + k[0] * r, c.y + k[1] * r))

        // Shadow = disc minus lit region, using even-odd fill.
        val tx = cos(TAU * phase).toFloat()
        val side = if (phase < 0.5) 1f else -1f
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(androidx.compose.ui.geometry.Rect(c, r))
            val n = 64
            for (i in 0..n) {
                val a = -PI / 2 + PI * i / n
                val p = Offset(c.x + side * r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            for (i in 0..n) {
                val a = PI / 2 - PI * i / n
                lineTo(c.x + side * tx * r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            }
            close()
        }
        drawPath(path, shadow)
        drawCircle(rim, r + 2f, c, style = Stroke(3f))
    }
}

/** Minimal line chart for history points. */
@Composable
fun HistoryChart(points: List<Point>, modifier: Modifier = Modifier, emptyText: String = "No history yet") {
    val measurer = rememberTextMeasurer()
    val fg = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val style = TextStyle(fontSize = 11.sp, color = fg.copy(alpha = 0.7f))
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yy").withZone(ZoneId.systemDefault()) }
    Canvas(modifier.fillMaxWidth().height(240.dp)) {
        if (points.isEmpty()) {
            val l = measurer.measure(emptyText, style)
            drawText(l, topLeft = Offset((size.width - l.size.width) / 2, size.height / 2))
            return@Canvas
        }
        var lo = points.minOf { it.value }
        var hi = points.maxOf { it.value }
        if (abs(hi - lo) < 1e-12) {
            val pad = if (lo == 0.0) 1.0 else abs(lo) * 0.01
            lo -= pad; hi += pad
        }
        val left = 64.dp.toPx(); val right = 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = 26.dp.toPx()
        val pw = size.width - left - right
        val ph = size.height - top - bottom
        val t0 = points.first().time.epochSecond.toDouble()
        val span = (points.last().time.epochSecond - t0).takeIf { it > 0 } ?: 1.0
        fun xy(p: Point) = Offset(
            (if (points.size > 1) left + ((p.time.epochSecond - t0) / span * pw) else left + pw / 2.0).toFloat(),
            (top + (hi - p.value) / (hi - lo) * ph).toFloat(),
        )
        for (i in 0..4) {
            val v = lo + (hi - lo) * i / 4
            val y = (top + (hi - v) / (hi - lo) * ph).toFloat()
            drawLine(fg.copy(alpha = 0.12f), Offset(left, y), Offset(left + pw, y))
            val l = measurer.measure(fmtAxis(v), style)
            drawText(l, topLeft = Offset(left - 6.dp.toPx() - l.size.width, y - l.size.height / 2))
        }
        for (i in setOf(0, points.size / 2, points.size - 1)) {
            val l = measurer.measure(dateFmt.format(points[i].time), style)
            val x = (xy(points[i]).x - l.size.width / 2).coerceIn(left, left + pw - l.size.width)
            drawText(l, topLeft = Offset(x, size.height - l.size.height))
        }
        val line = Path().apply {
            points.forEachIndexed { i, p -> xy(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(xy(points.last()).x, top + ph)
            lineTo(xy(points.first()).x, top + ph)
            close()
        }
        drawPath(area, accent.copy(alpha = 0.15f))
        drawPath(line, accent, style = Stroke(2.dp.toPx()))
        if (points.size <= 60) points.forEach { drawCircle(accent, 2.5.dp.toPx(), xy(it)) }
    }
}

private fun fmtAxis(v: Double): String = when {
    abs(v) >= 100_000 -> "%,.0f".format(v)
    abs(v) >= 100 -> "%.1f".format(v)
    abs(v) >= 1 -> "%.3f".format(v)
    else -> "%.4f".format(v)
}
