package io.github.jamieharwood.moonphase.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Sends location and units to the paired watch app over the Wear OS Data Layer. */
object WearSync {
    const val PATH = "/moonphase/settings"

    fun push(ctx: Context, s: Settings) {
        try {
            val req = PutDataMapRequest.create(PATH).apply {
                dataMap.putDouble("latitude", s.latitude)
                dataMap.putDouble("longitude", s.longitude)
                dataMap.putString("units", s.units.name)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(ctx).putDataItem(req)
                .addOnFailureListener { Log.d("WearSync", "no watch / Wear API unavailable: $it") }
        } catch (e: Exception) {
            Log.d("WearSync", "Wear API unavailable: $e") // phones without Play services
        }
    }
}
