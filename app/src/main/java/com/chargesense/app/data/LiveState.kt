package com.chargesense.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process state published by the monitor service and observed by the UI. */
object LiveState {
    private const val RECENT_WINDOW_MS = 15 * 60_000L

    private val _snapshot = MutableStateFlow<BatterySnapshot?>(null)
    val snapshot: StateFlow<BatterySnapshot?> = _snapshot.asStateFlow()

    /** Exponentially smoothed current, steadier than the instantaneous reading for time estimates. */
    private val _smoothedCurrentMa = MutableStateFlow<Float?>(null)
    val smoothedCurrentMa: StateFlow<Float?> = _smoothedCurrentMa.asStateFlow()

    /** Snapshots from the last few minutes, for the current and temperature sparklines. */
    private val _recent = MutableStateFlow<List<BatterySnapshot>>(emptyList())
    val recent: StateFlow<List<BatterySnapshot>> = _recent.asStateFlow()

    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession: StateFlow<Session?> = _activeSession.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private var lastPluggedState: Boolean? = null

    fun publish(s: BatterySnapshot) {
        val prev = _snapshot.value
        _snapshot.value = s
        // Plugging/unplugging flips the current sign; restart smoothing so the estimate reacts at once.
        if (lastPluggedState != s.isPlugged) {
            _smoothedCurrentMa.value = null
            _recent.value = emptyList()
            lastPluggedState = s.isPlugged
        }
        val cutoff = s.timestamp - RECENT_WINDOW_MS
        _recent.value = (_recent.value + s).dropWhile { it.timestamp < cutoff }
        val c = s.currentMa ?: return
        val old = _smoothedCurrentMa.value
        _smoothedCurrentMa.value = if (old == null || prev == null) c.toFloat() else old * 0.85f + c * 0.15f
    }

    fun publishSession(s: Session?) {
        _activeSession.value = s
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }
}
