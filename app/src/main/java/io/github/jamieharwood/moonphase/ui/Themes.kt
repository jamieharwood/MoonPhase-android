package io.github.jamieharwood.moonphase.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jamieharwood.moonphase.R

/**
 * Colour themes, ported from the MoonPhase web dashboard's theme menu. The web app names
 * several palettes after space organisations; Orbitly is a store app, so here they have
 * neutral names and make no reference to any organisation. [SYSTEM] keeps Material You.
 */
data class Palette(
    val key: String,
    @StringRes val label: Int,
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val border: Color,
    val fg: Color,
    val fgDim: Color,
    val accent: Color,
    val moon: Color,
    val red: Color,
    val headerBg: Color,
    val headerFg: Color,
    val rule: Color,
    val ruleWidth: Dp,
    val radius: Dp,
    val mono: Boolean,
)

object Themes {
    const val SYSTEM = "system"

    val ALL = listOf(
        Palette("classic", R.string.theme_classic, true, Color(0xFF030A1A), Color(0xFF071526), Color(0xFF0D2A4A),
            Color(0xFFC8DFF0), Color(0xFF7AA8CC), Color(0xFF00D4FF), Color(0xFFF0E68C), Color(0xFFFF4444),
            Color(0xFF030A1A), Color(0xFF00D4FF), Color(0xFF0D2A4A), 1.dp, 8.dp, mono = true),
        Palette("light", R.string.theme_light, false, Color(0xFFF0F4F8), Color(0xFFFFFFFF), Color(0xFFC0D4EB),
            Color(0xFF1A2E45), Color(0xFF3A6A9A), Color(0xFF005FA3), Color(0xFF000000), Color(0xFFBB2222),
            Color(0xFFF0F4F8), Color(0xFF005FA3), Color(0xFFC0D4EB), 1.dp, 8.dp, mono = true),
        Palette("monochrome", R.string.theme_monochrome, true, Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF2A2A2A),
            Color(0xFFFFFFFF), Color(0xFF9A9A9A), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFF5A5A),
            Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFFFFFF), 1.dp, 0.dp, mono = false),
        Palette("mission", R.string.theme_mission, false, Color(0xFFEEF2F7), Color(0xFFFFFFFF), Color(0xFFD3DCE8),
            Color(0xFF1B1B1B), Color(0xFF3E4C63), Color(0xFF0B3D91), Color(0xFF0B3D91), Color(0xFFFC3D21),
            Color(0xFF0B3D91), Color(0xFFFFFFFF), Color(0xFFFC3D21), 4.dp, 2.dp, mono = false),
        Palette("sky", R.string.theme_sky, true, Color(0xFF061525), Color(0xFF0C243C), Color(0xFF1D4E78),
            Color(0xFFE7F4FB), Color(0xFF8EB8D0), Color(0xFF5EC8EF), Color(0xFFD7F1FB), Color(0xFFFF6B6B),
            Color(0xFF00205B), Color(0xFFFFFFFF), Color(0xFF7ED0F2), 3.dp, 6.dp, mono = false),
        Palette("neon", R.string.theme_neon, true, Color(0xFF050505), Color(0xFF111111), Color(0xFF2C2C2C),
            Color(0xFFFFFFFF), Color(0xFFB3B3B3), Color(0xFFFF2E97), Color(0xFFFFD0EA), Color(0xFFFF4D6A),
            Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFF2E97), 3.dp, 0.dp, mono = false),
        Palette("daylight", R.string.theme_daylight, false, Color(0xFFF3F5F7), Color(0xFFFFFFFF), Color(0xFFD5DDE6),
            Color(0xFF1C2430), Color(0xFF4D5D70), Color(0xFF004B87), Color(0xFF004B87), Color(0xFFC0392B),
            Color(0xFF004B87), Color(0xFFFFFFFF), Color(0xFFE87722), 4.dp, 2.dp, mono = false),
        Palette("indigo", R.string.theme_indigo, true, Color(0xFF07101C), Color(0xFF0E1C30), Color(0xFF1E3D66),
            Color(0xFFE8F2FF), Color(0xFF9BB4D4), Color(0xFF3EC6FF), Color(0xFFD6EBFF), Color(0xFFEF4135),
            Color(0xFF001E60), Color(0xFFFFFFFF), Color(0xFF3EC6FF), 3.dp, 4.dp, mono = false),
        Palette("copper", R.string.theme_copper, true, Color(0xFF161210), Color(0xFF221C19), Color(0xFF3D322B),
            Color(0xFFF6EEE8), Color(0xFFC2AEA0), Color(0xFFE07A3D), Color(0xFFF0C7A8), Color(0xFFE05A4F),
            Color(0xFF100E0C), Color(0xFFF6EEE8), Color(0xFFE07A3D), 3.dp, 2.dp, mono = false),
        Palette("gold", R.string.theme_gold, true, Color(0xFF070B16), Color(0xFF12182B), Color(0xFF2A3658),
            Color(0xFFF8F3E4), Color(0xFFCBB98A), Color(0xFFF5C518), Color(0xFFFFE27A), Color(0xFFFF6B5A),
            Color(0xFF070B16), Color(0xFFFFE27A), Color(0xFFF5C518), 3.dp, 10.dp, mono = false),
    )
    private val BY_KEY = ALL.associateBy { it.key }

    /** Menu order: follow the system first, then the palettes. */
    val CHOICES: List<Pair<String, Int>> = listOf(SYSTEM to R.string.theme_system) + ALL.map { it.key to it.label }

    /** The palette for a key, or null for [SYSTEM] and unknown keys. */
    fun get(key: String): Palette? = BY_KEY[key]

    private fun on(c: Color) = if (c.luminance() > 0.4f) Color.Black else Color.White

    /** A Material 3 colour scheme that paints the app in [p]. */
    fun colorScheme(p: Palette): ColorScheme {
        val base = if (p.dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = p.accent, onPrimary = on(p.accent),
            primaryContainer = p.card, onPrimaryContainer = p.accent,
            secondary = p.accent, onSecondary = on(p.accent),
            secondaryContainer = p.border, onSecondaryContainer = p.fg,
            tertiary = p.moon, onTertiary = on(p.moon),
            background = p.bg, onBackground = p.fg,
            surface = p.bg, onSurface = p.fg,
            surfaceVariant = p.card, onSurfaceVariant = p.fgDim,
            surfaceContainerLowest = p.bg, surfaceContainerLow = p.card,
            surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.card,
            outline = p.border, outlineVariant = p.border,
            error = p.red, onError = on(p.red),
        )
    }

    /** Material typography in a monospace face, for the palettes the web app sets in Courier. */
    fun typography(p: Palette): Typography {
        if (!p.mono) return Typography()
        val t = Typography()
        fun TextStyle.mono() = copy(fontFamily = FontFamily.Monospace)
        return t.copy(
            displayLarge = t.displayLarge.mono(), displayMedium = t.displayMedium.mono(), displaySmall = t.displaySmall.mono(),
            headlineLarge = t.headlineLarge.mono(), headlineMedium = t.headlineMedium.mono(), headlineSmall = t.headlineSmall.mono(),
            titleLarge = t.titleLarge.mono(), titleMedium = t.titleMedium.mono(), titleSmall = t.titleSmall.mono(),
            bodyLarge = t.bodyLarge.mono(), bodyMedium = t.bodyMedium.mono(), bodySmall = t.bodySmall.mono(),
            labelLarge = t.labelLarge.mono(), labelMedium = t.labelMedium.mono(), labelSmall = t.labelSmall.mono(),
        )
    }
}

/** The active palette, or null when following the system (Material You) or in night mode. */
val LocalPalette = staticCompositionLocalOf<Palette?> { null }

/** Theme picker: a radio list of "Follow system" and the ten palettes, each with a swatch. */
@androidx.compose.runtime.Composable
fun ThemeDialog(selected: String, nightModeOn: Boolean, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.theme_title)) },
        text = {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                if (nightModeOn) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(R.string.theme_night_note),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp),
                    )
                }
                Themes.CHOICES.forEach { (key, label) ->
                    androidx.compose.foundation.layout.Row(
                        androidx.compose.ui.Modifier.fillMaxWidth()
                            .selectable(selected = key == selected, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(key) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = key == selected, onClick = null)
                        androidx.compose.material3.Text(
                            androidx.compose.ui.res.stringResource(label),
                            modifier = androidx.compose.ui.Modifier.weight(1f).padding(start = 12.dp),
                        )
                        Themes.get(key)?.let { p ->
                            androidx.compose.foundation.layout.Row {
                                listOf(p.bg, p.card, p.accent, p.moon).forEach { c ->
                                    androidx.compose.foundation.layout.Box(
                                        androidx.compose.ui.Modifier.padding(start = 2.dp).size(14.dp)
                                            .background(c, androidx.compose.foundation.shape.CircleShape)
                                            .border(1.dp, p.border, androidx.compose.foundation.shape.CircleShape),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.close))
            }
        },
    )
}
