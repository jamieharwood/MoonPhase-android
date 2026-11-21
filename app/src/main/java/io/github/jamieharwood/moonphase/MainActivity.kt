package io.github.jamieharwood.moonphase

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import io.github.jamieharwood.moonphase.data.Alerts
import io.github.jamieharwood.moonphase.data.METRICS
import io.github.jamieharwood.moonphase.data.RefreshWorker
import io.github.jamieharwood.moonphase.data.Units
import io.github.jamieharwood.moonphase.ui.Dashboard
import io.github.jamieharwood.moonphase.ui.SettingsScreen
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Alerts.ensureChannel(this)
        RefreshWorker.schedule(this)
        setContent { MoonPhaseTheme { App(vm) } }
    }
}

@Composable
fun MoonPhaseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFFE8CD78), secondary = Color(0xFF9DB7E8))
        else -> lightColorScheme(primary = Color(0xFF1B4F8F), secondary = Color(0xFF8A6D1E))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var unitsMenu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var populate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(state.settings.notifications) {
        if (state.settings.notifications && Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }
    val exportCsv = { csv.launch("moonphase-${METRICS[state.metricIndex].key}.csv") }

    BackHandler(showSettings) { showSettings = false }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    // Never wrap the title: on narrow phones the actions take most of the bar.
                    Text(if (showSettings) "Settings" else "MoonPhase", maxLines = 1, softWrap = false)
                },
                navigationIcon = {
                    if (showSettings) IconButton(onClick = { showSettings = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!showSettings) {
                        // Two short lines keep the action area narrow enough for small screens.
                        Text(
                            "Updated\n" + state.snapshot.timestamp.withZoneSameInstant(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                        Box {
                            TextButton(onClick = { unitsMenu = true }) { Text(state.settings.units.name.lowercase()) }
                            DropdownMenu(unitsMenu, { unitsMenu = false }) {
                                Units.entries.forEach { u ->
                                    DropdownMenuItem(text = { Text(u.name) }, onClick = {
                                        unitsMenu = false
                                        vm.updateSettings { it.copy(units = u) }
                                    })
                                }
                            }
                        }
                        IconButton(onClick = { vm.refresh(force = true) }) { Icon(Icons.Default.Refresh, "Refresh") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; showSettings = true })
                                DropdownMenuItem(text = { Text("Populate past…") }, onClick = { menu = false; populate = true })
                                DropdownMenuItem(text = { Text("Export CSV…") }, onClick = { menu = false; exportCsv() })
                                DropdownMenuItem(text = { Text("Reset history…") }, onClick = { menu = false; confirmReset = true })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (showSettings) {
            SettingsScreen(state.settings, padding) { new -> vm.updateSettings { new.copy(units = it.units, asciiMoon = it.asciiMoon) } }
        } else {
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh(force = true) }) {
                Dashboard(
                    state, padding,
                    onToggleAscii = { vm.updateSettings { it.copy(asciiMoon = !it.asciiMoon) } },
                    onMetric = vm::selectMetric,
                    onRange = vm::selectRange,
                    onExport = exportCsv,
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset history?") },
            text = { Text("This permanently deletes every snapshot stored on this device.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resetHistory() }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    if (populate) {
        var days by remember { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { populate = false },
            title = { Text("Populate past") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("Backfill calculated values for the past N days (1–365). " +
                        "LEO and aurora values can't be reconstructed and are stored as 0.")
                    OutlinedTextField(days, { days = it.filter(Char::isDigit).take(3) }, label = { Text("Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.padding(top = androidx.compose.ui.unit.Dp(12f)))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    populate = false
                    vm.populate((days.toIntOrNull() ?: 30).coerceIn(1, 365))
                }) { Text("Populate") }
            },
            dismissButton = { TextButton(onClick = { populate = false }) { Text("Cancel") } },
        )
    }
}
