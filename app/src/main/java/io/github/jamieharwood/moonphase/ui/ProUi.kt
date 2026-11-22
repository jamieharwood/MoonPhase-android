package io.github.jamieharwood.moonphase.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.jamieharwood.moonphase.BuildConfig
import io.github.jamieharwood.moonphase.R
import io.github.jamieharwood.moonphase.data.Pro

/** What Pro unlocks, shown in the dialog. Keep in step with the store listing. */
private val FEATURES = listOf(
    R.string.pro_feat_widgets,
    R.string.pro_feat_calendar,
    R.string.pro_feat_watch,
    R.string.pro_feat_night,
    R.string.pro_feat_history,
)

@Composable
fun ProDialog(
    state: Pro.State,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onDebugToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pro_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(if (state.owned) R.string.pro_owned else R.string.pro_intro))
                FEATURES.forEach { Text("• " + stringResource(it)) }
                when {
                    state.owned -> Unit
                    state.pending -> Text(stringResource(R.string.pro_pending), color = MaterialTheme.colorScheme.primary)
                    !state.available -> Text(stringResource(R.string.pro_unavailable), color = MaterialTheme.colorScheme.error)
                }
                if (BuildConfig.DEBUG) {
                    TextButton(onClick = onDebugToggle) {
                        Text(if (state.owned) "Debug: lock Pro" else "Debug: unlock Pro without Play")
                    }
                }
            }
        },
        confirmButton = {
            if (state.owned) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            } else {
                Button(onClick = onBuy, enabled = state.available && !state.pending) {
                    Text(state.price?.let { stringResource(R.string.pro_buy, it) } ?: stringResource(R.string.pro_buy_noprice))
                }
            }
        },
        dismissButton = {
            if (!state.owned) TextButton(onClick = onRestore) { Text(stringResource(R.string.pro_restore)) }
        },
    )
}

/** Stand-in for a dashboard card whose content is part of Pro. */
@Composable
fun LockedCard(title: String, teaser: String, onUnlock: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(teaser, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🔒 " + stringResource(R.string.pro_part_of), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onUnlock) { Text(stringResource(R.string.pro_unlock)) }
            }
        }
    }
}
