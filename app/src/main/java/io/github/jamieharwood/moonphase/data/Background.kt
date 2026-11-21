package io.github.jamieharwood.moonphase.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.jamieharwood.moonphase.Astro
import io.github.jamieharwood.moonphase.MainActivity
import io.github.jamieharwood.moonphase.R
import io.github.jamieharwood.moonphase.Snapshot
import io.github.jamieharwood.moonphase.widget.WidgetData
import java.util.concurrent.TimeUnit

object Alerts {
    private const val CHANNEL = "alerts"
    private const val THROTTLE_MS = 6 * 60 * 60 * 1000L

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Sky alerts", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Aurora storms and full moon"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    /** Aurora storm (Kp ≥ 5) and full moon tonight/tomorrow; each alert at most once per 6 hours. */
    fun maybeNotify(ctx: Context, s: Snapshot) {
        if (!Settings.load(ctx).notifications) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val alerts = buildList {
            if (s.live.kp >= 5) add(Triple(1, "🌌 Aurora Alert — Geomagnetic Storm!",
                "Kp %.1f: visible aurora may be possible at mid-latitudes.".format(s.live.kp)))
            if (s.daysUntilFullMoon == 0) add(Triple(2, "🌕 Full Moon Tonight!", "${s.moonIlluminationPercent}% illuminated."))
            else if (s.daysUntilFullMoon == 1) add(Triple(3, "🌔 Full Moon Tomorrow", "${s.moonIlluminationPercent}% illuminated tonight."))
        }
        val prefs = ctx.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        for ((id, title, body) in alerts) {
            val now = System.currentTimeMillis()
            if (now - prefs.getLong("a$id", 0) < THROTTLE_MS) continue
            prefs.edit().putLong("a$id", now).apply()
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(body)
                .setContentIntent(open).setAutoCancel(true).build()
            try {
                NotificationManagerCompat.from(ctx).notify(id, n)
            } catch (_: SecurityException) {
            }
        }
    }
}

/** Periodic background refresh: saves history (max one per 12 h window), sends alerts, pushes Awtrix. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val settings = Settings.load(applicationContext)
        val s = Astro.compute(latitude = settings.latitude, longitude = settings.longitude)
            .let { it.copy(live = LiveFetcher.fetch()) }
        if (settings.historyEnabled) HistoryStore(applicationContext).use {
            it.save(s)
            it.prune(settings.retentionDays)
        }
        Alerts.maybeNotify(applicationContext, s)
        WidgetData.publish(applicationContext, s.live)
        if (settings.awtrixEnabled) Awtrix.pushAll(settings.awtrixHost, s)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
