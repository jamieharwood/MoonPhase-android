package io.github.jamieharwood.moonphase.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.jamieharwood.moonphase.Astro
import io.github.jamieharwood.moonphase.Loc

/** Tile: phase name, illumination ring and days to the next full moon. Tap to open the app. */
class MoonTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val settings = WearSettings.load(this)
        val s = Astro.compute(latitude = settings.latitude, longitude = settings.longitude)
        val device = requestParams.deviceConfiguration
        val moonColor = argb(0xFFE8CD78.toInt())
        val d = s.daysUntilFullMoon
        val pro = settings.pro

        val openApp = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearActivity::class.java.name)
                            .build(),
                    ).build(),
            ).build()

        val layout = EdgeContentLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setEdgeContent(
                CircularProgressIndicator.Builder()
                    .setProgress(if (pro) s.moonIlluminationPercent / 100f else 0f)
                    .setCircularProgressIndicatorColors(ProgressIndicatorColors(moonColor, argb(0x33FFFFFF)))
                    .build(),
            )
            .setPrimaryLabelTextContent(
                Text.Builder(this, if (pro) getString(R.string.lit_percent, s.moonIlluminationPercent) else getString(R.string.pro_tile_label))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(moonColor).build(),
            )
            .setContent(
                Text.Builder(this, if (pro) Loc.phaseName(this, s.moonPhaseName) else getString(R.string.pro_name))
                    .setTypography(Typography.TYPOGRAPHY_TITLE2).setColor(argb(0xFFFFFFFF.toInt()))
                    .setMaxLines(2).build(),
            )
            .setSecondaryLabelTextContent(
                Text.Builder(this, when {
                    !pro -> getString(R.string.pro_tile_text)
                    d == 0 -> getString(R.string.full_moon_tonight)
                    else -> getString(R.string.full_moon_when, Loc.daysText(this, d.toLong()))
                })
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(argb(0xFFBBBBBB.toInt())).build(),
            )
            .build()

        val root = androidx.wear.protolayout.LayoutElementBuilders.Box.Builder()
            .addContent(layout)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp).build())
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(30 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
