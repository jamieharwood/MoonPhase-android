package io.github.jamieharwood.moonphase.data

import android.content.Context
import io.github.jamieharwood.moonphase.Astro

enum class Units { AU, KM, MI }

data class Settings(
    val latitude: Double = Astro.DEFAULT_LATITUDE,
    val longitude: Double = Astro.DEFAULT_LONGITUDE,
    val units: Units = Units.AU,
    val asciiMoon: Boolean = false,
    val historyEnabled: Boolean = true,
    val retentionDays: Int = 0,
    val notifications: Boolean = true,
    val awtrixEnabled: Boolean = false,
    val awtrixHost: String = "http://moonclock.local",
) {
    companion object {
        private const val PREFS = "moonphase"

        fun load(ctx: Context): Settings {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val d = Settings()
            return Settings(
                latitude = Astro.validLat(p.getFloat("latitude", d.latitude.toFloat()).toDouble()),
                longitude = Astro.validLon(p.getFloat("longitude", d.longitude.toFloat()).toDouble()),
                units = runCatching { Units.valueOf(p.getString("units", d.units.name)!!) }.getOrDefault(Units.AU),
                asciiMoon = p.getBoolean("asciiMoon", d.asciiMoon),
                historyEnabled = p.getBoolean("historyEnabled", d.historyEnabled),
                retentionDays = p.getInt("retentionDays", d.retentionDays),
                notifications = p.getBoolean("notifications", d.notifications),
                awtrixEnabled = p.getBoolean("awtrixEnabled", d.awtrixEnabled),
                awtrixHost = p.getString("awtrixHost", d.awtrixHost) ?: d.awtrixHost,
            )
        }
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("latitude", latitude.toFloat())
            .putFloat("longitude", longitude.toFloat())
            .putString("units", units.name)
            .putBoolean("asciiMoon", asciiMoon)
            .putBoolean("historyEnabled", historyEnabled)
            .putInt("retentionDays", retentionDays)
            .putBoolean("notifications", notifications)
            .putBoolean("awtrixEnabled", awtrixEnabled)
            .putString("awtrixHost", awtrixHost)
            .apply()
    }
}
