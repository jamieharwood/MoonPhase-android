package io.github.jamieharwood.moonphase

import android.content.Context
import io.github.jamieharwood.moonphase.core.R
import java.time.LocalDate

/**
 * Translated text for values the pure-Kotlin [Astro] and [SkyEvents] objects produce in English.
 * Lookups are explicit (not by resource name) so resource shrinking keeps every string.
 */
object Loc {
    private val PHASES = mapOf(
        "Full Moon" to R.string.phase_full_moon,
        "Waning Gibbous" to R.string.phase_waning_gibbous,
        "Last Quarter" to R.string.phase_last_quarter,
        "Waning Crescent" to R.string.phase_waning_crescent,
        "New Moon" to R.string.phase_new_moon,
        "Waxing Crescent" to R.string.phase_waxing_crescent,
        "First Quarter" to R.string.phase_first_quarter,
        "Waxing Gibbous" to R.string.phase_waxing_gibbous,
    )

    private val SHOWERS = mapOf(
        "quadrantids" to R.string.shower_quadrantids,
        "lyrids" to R.string.shower_lyrids,
        "eta_aquariids" to R.string.shower_eta_aquariids,
        "delta_aquariids" to R.string.shower_delta_aquariids,
        "perseids" to R.string.shower_perseids,
        "draconids" to R.string.shower_draconids,
        "orionids" to R.string.shower_orionids,
        "s_taurids" to R.string.shower_s_taurids,
        "n_taurids" to R.string.shower_n_taurids,
        "leonids" to R.string.shower_leonids,
        "geminids" to R.string.shower_geminids,
        "ursids" to R.string.shower_ursids,
    )

    private val ECLIPSE_TYPES = mapOf(
        "solar_annular" to R.string.ecl_solar_annular,
        "solar_total" to R.string.ecl_solar_total,
        "solar_partial" to R.string.ecl_solar_partial,
        "lunar_total" to R.string.ecl_lunar_total,
        "lunar_partial" to R.string.ecl_lunar_partial,
        "lunar_penumbral" to R.string.ecl_lunar_penumbral,
    )

    private val ECLIPSE_REGIONS = mapOf(
        LocalDate.of(2026, 2, 17) to R.string.ecl_2026_02_17,
        LocalDate.of(2026, 3, 3) to R.string.ecl_2026_03_03,
        LocalDate.of(2026, 8, 12) to R.string.ecl_2026_08_12,
        LocalDate.of(2026, 8, 28) to R.string.ecl_2026_08_28,
        LocalDate.of(2027, 2, 6) to R.string.ecl_2027_02_06,
        LocalDate.of(2027, 2, 20) to R.string.ecl_2027_02_20,
        LocalDate.of(2027, 7, 18) to R.string.ecl_2027_07_18,
        LocalDate.of(2027, 8, 2) to R.string.ecl_2027_08_02,
        LocalDate.of(2027, 8, 17) to R.string.ecl_2027_08_17,
        LocalDate.of(2028, 1, 12) to R.string.ecl_2028_01_12,
        LocalDate.of(2028, 1, 26) to R.string.ecl_2028_01_26,
        LocalDate.of(2028, 7, 6) to R.string.ecl_2028_07_06,
        LocalDate.of(2028, 7, 22) to R.string.ecl_2028_07_22,
        LocalDate.of(2028, 12, 31) to R.string.ecl_2028_12_31,
        LocalDate.of(2029, 1, 14) to R.string.ecl_2029_01_14,
        LocalDate.of(2029, 6, 12) to R.string.ecl_2029_06_12,
        LocalDate.of(2029, 6, 26) to R.string.ecl_2029_06_26,
        LocalDate.of(2029, 7, 11) to R.string.ecl_2029_07_11,
        LocalDate.of(2029, 12, 5) to R.string.ecl_2029_12_05,
        LocalDate.of(2029, 12, 20) to R.string.ecl_2029_12_20,
        LocalDate.of(2030, 6, 1) to R.string.ecl_2030_06_01,
        LocalDate.of(2030, 6, 15) to R.string.ecl_2030_06_15,
        LocalDate.of(2030, 11, 25) to R.string.ecl_2030_11_25,
        LocalDate.of(2030, 12, 9) to R.string.ecl_2030_12_09,
    )

    /** [english] is a Snapshot.moonPhaseName; unknown names are returned unchanged. */
    fun phaseName(ctx: Context, english: String): String =
        PHASES[english]?.let(ctx::getString) ?: english

    fun kpActivity(ctx: Context, kp: Double): String = ctx.getString(
        when {
            kp < 0 -> R.string.kp_unavailable
            kp < 2 -> R.string.kp_quiet
            kp < 4 -> R.string.kp_unsettled
            kp < 5 -> R.string.kp_active
            else -> R.string.kp_storm
        },
    )

    fun eventName(ctx: Context, e: SkyEvent): String =
        (if (e.kind == SkyEventKind.METEOR_SHOWER) SHOWERS[e.key] else ECLIPSE_TYPES[e.key])
            ?.let(ctx::getString) ?: e.name

    fun eventDetail(ctx: Context, e: SkyEvent): String {
        if (e.kind != SkyEventKind.METEOR_SHOWER) return ECLIPSE_REGIONS[e.date]?.let(ctx::getString) ?: e.detail
        val sky = ctx.getString(
            when {
                e.moonLit <= 25 -> R.string.sky_dark
                e.moonLit <= 60 -> R.string.sky_some_moon
                else -> R.string.sky_bright_moon
            },
        )
        return ctx.getString(R.string.shower_detail, e.zhr, e.moonLit, sky)
    }

    /** "today", "tomorrow" or "in N days". */
    fun daysText(ctx: Context, days: Long): String = when (days) {
        0L -> ctx.getString(R.string.days_today)
        1L -> ctx.getString(R.string.days_tomorrow)
        else -> ctx.getString(R.string.days_in, days.toInt())
    }

    /** "today", "1 day" or "N days". */
    fun daysCount(ctx: Context, days: Long): String =
        if (days == 0L) ctx.getString(R.string.days_today)
        else ctx.resources.getQuantityString(R.plurals.days_count, days.toInt(), days.toInt())
}
