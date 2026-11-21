package io.github.jamieharwood.moonphase.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.DataEventBuffer
import io.github.jamieharwood.moonphase.Astro
import kotlinx.coroutines.tasks.await

/** Location and units on the watch; set from the phone app via the Data Layer. */
data class WearSettings(
    val latitude: Double = Astro.DEFAULT_LATITUDE,
    val longitude: Double = Astro.DEFAULT_LONGITUDE,
    val units: String = "AU",
    val fromPhone: Boolean = false,
) {
    companion object {
        const val PATH = "/moonphase/settings"
        private const val PREFS = "wear_settings"

        fun load(ctx: Context): WearSettings {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!p.contains("latitude")) return WearSettings()
            return WearSettings(
                Astro.validLat(p.getFloat("latitude", 0f).toDouble()),
                Astro.validLon(p.getFloat("longitude", 0f).toDouble()),
                p.getString("units", "AU") ?: "AU",
                fromPhone = true,
            )
        }

        fun store(ctx: Context, map: DataMap) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putFloat("latitude", map.getDouble("latitude", Astro.DEFAULT_LATITUDE).toFloat())
                .putFloat("longitude", map.getDouble("longitude", Astro.DEFAULT_LONGITUDE).toFloat())
                .putString("units", map.getString("units", "AU"))
                .apply()
        }

        /** Pull the latest settings the phone published (covers installs after the last change). */
        suspend fun syncFromDataLayer(ctx: Context) {
            try {
                val items = Wearable.getDataClient(ctx).dataItems.await()
                try {
                    items.filter { it.uri.path == PATH }
                        .forEach { store(ctx, DataMapItem.fromDataItem(it).dataMap) }
                } finally {
                    items.release()
                }
            } catch (_: Exception) {
                // No paired phone / Play services unavailable – keep current settings.
            }
        }
    }
}

/** Receives settings pushed by the phone app. */
class SettingsListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.dataItem.uri.path == WearSettings.PATH }
            .forEach { WearSettings.store(this, DataMapItem.fromDataItem(it.dataItem).dataMap) }
    }
}
