package io.github.jamieharwood.moonphase.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jamieharwood.moonphase.R
import io.github.jamieharwood.moonphase.data.Settings

/** Edits a copy of the settings; the caller saves it when the screen is closed. */
@Composable
fun SettingsScreen(initial: Settings, padding: PaddingValues, onChange: (Settings) -> Unit) {
    var s by remember { mutableStateOf(initial) }
    var lat by remember { mutableStateOf(initial.latitude.toString()) }
    var lon by remember { mutableStateOf(initial.longitude.toString()) }
    var keep by remember { mutableStateOf(initial.retentionDays.toString()) }
    fun update(n: Settings) { s = n; onChange(n) }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(stringResource(R.string.set_section_location))
        Text(stringResource(R.string.set_location_hint),
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(lat, { v ->
            lat = v
            v.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }?.let { update(s.copy(latitude = it)) }
        }, label = { Text(stringResource(R.string.set_latitude)) }, isError = lat.toDoubleOrNull()?.let { it !in -90.0..90.0 } ?: true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lon, { v ->
            lon = v
            v.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { update(s.copy(longitude = it)) }
        }, label = { Text(stringResource(R.string.set_longitude)) }, isError = lon.toDoubleOrNull()?.let { it !in -180.0..180.0 } ?: true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

        Section(stringResource(R.string.set_section_display))
        Toggle(stringResource(R.string.set_night_mode), s.nightMode) { update(s.copy(nightMode = it)) }

        Section(stringResource(R.string.set_section_history))
        Toggle(stringResource(R.string.set_store_history), s.historyEnabled) { update(s.copy(historyEnabled = it)) }
        OutlinedTextField(keep, { v ->
            keep = v
            v.toIntOrNull()?.takeIf { it >= 0 }?.let { update(s.copy(retentionDays = it)) }
        }, label = { Text(stringResource(R.string.set_retention)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

        Section(stringResource(R.string.set_section_integrations))
        Toggle(stringResource(R.string.set_notifications), s.notifications) { update(s.copy(notifications = it)) }
        Toggle(stringResource(R.string.set_awtrix), s.awtrixEnabled) { update(s.copy(awtrixEnabled = it)) }
        OutlinedTextField(s.awtrixHost, { update(s.copy(awtrixHost = it.trim())) },
            label = { Text(stringResource(R.string.set_awtrix_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
