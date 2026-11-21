package io.github.jamieharwood.moonphase.data

import io.github.jamieharwood.moonphase.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Optional push of metrics to an Awtrix pixel clock (`/api/custom`), same set as the original. */
object Awtrix {
    fun pushes(s: Snapshot): List<Triple<String, String, String>> = buildList {
        add(Triple("marsDistanceAu", "%.1fau".format(s.marsDistanceAu), "MARS"))
        add(Triple("jupiterDistanceAu", "%.1fau".format(s.jupiterDistanceAu), "JUPITER"))
        add(Triple("saturnDistanceAu", "%.1fau".format(s.saturnDistanceAu), "SATURN"))
        add(Triple("CurrentDayLength", "%.1fhrs".format(s.daylightHours), "DAYLENGTH"))
        add(Triple("voyager1", "V1:%.0fau".format(s.voyager1DistanceAu), "VOYAGER"))
        add(Triple("voyager2", "V2:%.0fau".format(s.voyager2DistanceAu), "VOYAGER"))
        add(Triple("newhorizons", "NH:%.0fau".format(s.newHorizonsDistanceAu), "NEWHORIZONS"))
        add(Triple("summersolstice", "${s.daysUntilSummerSolstice}d", "SUMMER"))
        add(Triple("wintersolstice", "${s.daysUntilWinterSolstice}d", "WINTER"))
        add(Triple("perihelion", "${s.daysUntilPerihelion}d", "PERIHELION"))
        add(Triple("aphelion", "${s.daysUntilAphelion}d", "PERIHELION"))
        add(Triple("earthSpeed", "%.1fkm/s".format(s.earthSpeedKmPerSec), "EARTH"))
        add(Triple("moonDistance", "%,.0fkm".format(s.moonDistanceKm), "MOON"))
        add(Triple("lightMars", "Lt:" + s.lightTimes["Mars"], "LIGHT"))
        add(Triple("lightJupiter", "Lt:" + s.lightTimes["Jupiter"], "LIGHT"))
        add(Triple("moonphase", s.moonPhaseName, s.moonPhaseIcon))
        add(Triple("moonillumination", "${s.moonIlluminationPercent}%", s.moonPhaseIcon))
        if (s.live.kp >= 0) {
            val k = s.live.kp
            add(Triple("auroraKp", if (k >= 5) "⚡Kp%.1f".format(k) else "Kp%.1f".format(k), "AURORA"))
        }
    }

    /** Returns the number of metrics pushed successfully; stops at the first failure. */
    suspend fun pushAll(host: String, s: Snapshot): Int = withContext(Dispatchers.IO) {
        var ok = 0
        for ((name, text, icon) in pushes(s)) {
            try {
                val c = URL(host.trimEnd('/') + "/api/custom?name=" + URLEncoder.encode(name, "UTF-8"))
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 5_000
                c.readTimeout = 10_000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().put("name", name).put("text", text)
                    .put("save", "true").put("effect", "").put("icon", icon).toString()
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode in 200..299) ok++ else break
                c.disconnect()
            } catch (e: Exception) {
                break
            }
        }
        ok
    }
}
