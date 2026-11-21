package io.github.jamieharwood.moonphase

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jamieharwood.moonphase.data.Alerts
import io.github.jamieharwood.moonphase.data.Awtrix
import io.github.jamieharwood.moonphase.data.HistoryStore
import io.github.jamieharwood.moonphase.data.LiveFetcher
import io.github.jamieharwood.moonphase.data.METRICS
import io.github.jamieharwood.moonphase.data.Point
import io.github.jamieharwood.moonphase.data.Settings
import io.github.jamieharwood.moonphase.data.WearSync
import io.github.jamieharwood.moonphase.data.toCsv
import io.github.jamieharwood.moonphase.widget.WidgetData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val RANGES: List<Pair<Int?, String>> =
    listOf(7 to "7 days", 14 to "14 days", 30 to "30 days", 90 to "90 days", 365 to "365 days", null to "All")

data class UiState(
    val snapshot: Snapshot,
    val settings: Settings,
    val refreshing: Boolean = false,
    val metricIndex: Int = 0,
    val rangeIndex: Int = 2,
    val points: List<Point> = emptyList(),
    val stored: Long = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val history = HistoryStore(app)
    private val _state = MutableStateFlow(
        Settings.load(app).let { UiState(Astro.compute(latitude = it.latitude, longitude = it.longitude), it) },
    )
    val state = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        WearSync.push(app, _state.value.settings)
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                recomputeLocal()
            }
        }
    }

    private fun recomputeLocal() = _state.update {
        it.copy(snapshot = Astro.compute(latitude = it.settings.latitude, longitude = it.settings.longitude)
            .copy(live = it.snapshot.live))
    }

    fun refresh(force: Boolean = false) {
        if (_state.value.refreshing) {
            _messages.tryEmit("A refresh is already running")
            return
        }
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            if (force) LiveFetcher.clear()
            val settings = _state.value.settings
            val live = LiveFetcher.fetch()
            val s = Astro.compute(latitude = settings.latitude, longitude = settings.longitude).copy(live = live)
            _state.update { it.copy(snapshot = s, refreshing = false) }
            WidgetData.publish(getApplication(), live)
            if (settings.historyEnabled) withContext(Dispatchers.IO) {
                history.save(s)
                history.prune(settings.retentionDays)
            }
            loadChart()
            Alerts.maybeNotify(getApplication(), s)
            if (settings.awtrixEnabled) {
                val n = Awtrix.pushAll(settings.awtrixHost, s)
                _messages.tryEmit(if (n > 0) "Pushed $n metrics to Awtrix" else "Awtrix device unreachable")
            }
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_state.value.settings)
        s.save(getApplication())
        WearSync.push(getApplication(), s)
        _state.update { it.copy(settings = s) }
        recomputeLocal()
        loadChart()
        viewModelScope.launch { WidgetData.updateAll(getApplication()) } // location may have changed
    }

    fun selectMetric(i: Int) { _state.update { it.copy(metricIndex = i) }; loadChart() }
    fun selectRange(i: Int) { _state.update { it.copy(rangeIndex = i) }; loadChart() }

    fun loadChart() {
        viewModelScope.launch {
            val st = _state.value
            val (pts, n) = withContext(Dispatchers.IO) {
                history.query(METRICS[st.metricIndex].key, RANGES[st.rangeIndex].first) to history.count()
            }
            _state.update { it.copy(points = pts, stored = n) }
        }
    }

    fun populate(days: Int) = viewModelScope.launch {
        val s = _state.value.settings
        val n = withContext(Dispatchers.IO) { history.backfill(days, s.latitude, s.longitude) }
        _messages.tryEmit("Added $n historical snapshots")
        loadChart()
    }

    fun resetHistory() = viewModelScope.launch {
        withContext(Dispatchers.IO) { history.clear() }
        _messages.tryEmit("History cleared")
        loadChart()
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        val st = _state.value
        val key = METRICS[st.metricIndex].key
        withContext(Dispatchers.IO) {
            val csv = toCsv(key, history.query(key, RANGES[st.rangeIndex].first))
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
        }
        _messages.tryEmit("CSV exported")
    }

    override fun onCleared() {
        history.close()
    }
}
