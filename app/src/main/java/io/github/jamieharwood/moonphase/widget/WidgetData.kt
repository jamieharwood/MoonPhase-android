package io.github.jamieharwood.moonphase.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.jamieharwood.moonphase.Astro
import io.github.jamieharwood.moonphase.LiveData
import io.github.jamieharwood.moonphase.Snapshot
import io.github.jamieharwood.moonphase.data.LiveFetcher
import io.github.jamieharwood.moonphase.data.Settings
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Data for the home-screen widgets. Astronomy is calculated on the spot; the live values
 * (aurora Kp, ISS, crew) come from the last app/background refresh, cached in preferences,
 * and are re-fetched here only when that cache is over an hour old.
 */
object WidgetData {
    private const val PREFS = "widget_live"
    private const val MAX_AGE_MS = 60 * 60 * 1000L

    suspend fun snapshot(ctx: Context): Snapshot {
        val settings = Settings.load(ctx)
        val s = Astro.compute(latitude = settings.latitude, longitude = settings.longitude)
        var live = loadLive(ctx)
        if (live == null) {
            live = withTimeoutOrNull(15_000) { LiveFetcher.fetch() }?.also { saveLive(ctx, it) }
        }
        return s.copy(live = live ?: LiveData())
    }

    /** Called after every refresh so the widgets show the same live values as the app. */
    suspend fun publish(ctx: Context, live: LiveData) {
        saveLive(ctx, live)
        updateAll(ctx)
    }

    /**
     * Redraws every placed widget. Looks the widgets up by *receiver* through AppWidgetManager
     * rather than Glance's updateAll(), whose saved receiver→widget-class map can go stale when
     * R8 renames classes between releases (it then drew the moon layout into the sky widget).
     */
    suspend fun updateAll(ctx: Context) {
        val awm = AppWidgetManager.getInstance(ctx)
        val glance = GlanceAppWidgetManager(ctx)
        for ((receiver, widget) in listOf(
            MoonWidgetReceiver::class.java to MoonWidget(),
            SkyWidgetReceiver::class.java to SkyWidget(),
        )) {
            for (id in awm.getAppWidgetIds(ComponentName(ctx, receiver))) {
                runCatching { widget.update(ctx, glance.getGlanceIdBy(id)) }
            }
        }
    }

    private fun saveLive(ctx: Context, l: LiveData) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("kp", l.kp.toFloat())
            .putFloat("iss", l.issAltitudeKm.toFloat())
            .putInt("people", l.peopleInSpace)
            .putInt("crew", l.issCrew)
            .putLong("at", System.currentTimeMillis())
            .apply()
    }

    private fun loadLive(ctx: Context): LiveData? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("at", 0) > MAX_AGE_MS) return null
        return LiveData(
            kp = p.getFloat("kp", -1f).toDouble(),
            issAltitudeKm = p.getFloat("iss", -1f).toDouble(),
            peopleInSpace = p.getInt("people", -1),
            issCrew = p.getInt("crew", -1),
        )
    }

    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    /** The snapshot's sunrise/sunset are "HH:mm" UTC; widgets show local time. */
    fun localSunTimes(s: Snapshot): Pair<String, String> {
        val day: LocalDate = s.timestamp.withZoneSameInstant(ZoneOffset.UTC).toLocalDate()
        fun local(t: String) = runCatching {
            ZonedDateTime.of(day, LocalTime.parse(t), ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).format(HM)
        }.getOrDefault(t)
        return local(s.sunriseTime) to local(s.sunsetTime)
    }

    /**
     * The app's moon drawing (same colours, craters and terminator as the dashboard),
     * rendered to a bitmap because widgets can't run Compose Canvas code.
     */
    fun moonBitmap(phase: Double, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = sizePx / 2f
        val r = sizePx * 0.46f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.parseColor("#EDE6CC")
        c.drawCircle(cx, cx, r, paint)
        paint.color = Color.argb(0x8C, 0x9E, 0x99, 0x8C)
        for ((x, y, k) in CRATERS) c.drawCircle(cx + x * r, cx + y * r, k * r, paint)

        // Shadow = disc minus lit region, using even-odd fill.
        val tx = cos(2 * PI * phase).toFloat()
        val side = if (phase < 0.5) 1f else -1f
        val path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addOval(RectF(cx - r, cx - r, cx + r, cx + r), Path.Direction.CW)
            val n = 64
            for (i in 0..n) {
                val a = -PI / 2 + PI * i / n
                val x = cx + side * r * cos(a).toFloat()
                val y = cx + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (i in 0..n) {
                val a = PI / 2 - PI * i / n
                lineTo(cx + side * tx * r * cos(a).toFloat(), cx + r * sin(a).toFloat())
            }
            close()
        }
        paint.color = Color.argb(0xE0, 0x08, 0x0D, 0x1A)
        c.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx / 100f
        paint.color = Color.argb(0x40, 0xFF, 0xF7, 0xD9)
        c.drawCircle(cx, cx, r, paint)
        return bmp
    }

    private val CRATERS = listOf(
        Triple(-0.35f, -0.30f, 0.16f), Triple(0.30f, -0.42f, 0.10f), Triple(0.18f, 0.05f, 0.22f),
        Triple(-0.20f, 0.38f, 0.13f), Triple(0.48f, 0.30f, 0.09f), Triple(-0.55f, 0.05f, 0.08f),
    )
}
