package io.github.jamieharwood.moonphase.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import io.github.jamieharwood.moonphase.Astro

/**
 * Watch-face complication: moon illumination (short text / ranged ring) or the phase name (long text).
 * Pure calculation, no network, refreshed every 30 minutes.
 */
class MoonComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, "Waxing Gibbous", 89, 3)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val s = Astro.compute()
        return build(request.complicationType, s.moonPhaseName, s.moonIlluminationPercent, s.daysUntilFullMoon)
    }

    private fun build(type: ComplicationType, phase: String, illum: Int, toFull: Int): ComplicationData? {
        fun text(t: String) = PlainComplicationText.Builder(t).build()
        val desc = text("$phase, $illum% illuminated, full moon in $toFull days")
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_moon)).build()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, WearActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text("$illum%"), desc)
                .setTitle(text(if (toFull == 0) "Full" else "${toFull}d"))
                .setMonochromaticImage(icon).setTapAction(tap).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(illum.toFloat(), 0f, 100f, desc)
                .setText(text("$illum%")).setMonochromaticImage(icon).setTapAction(tap).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text(phase), desc)
                .setTitle(text("$illum% · full in ${toFull}d"))
                .setMonochromaticImage(icon).setTapAction(tap).build()
            else -> null
        }
    }
}
