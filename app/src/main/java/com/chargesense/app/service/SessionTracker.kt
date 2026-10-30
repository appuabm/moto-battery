package com.chargesense.app.service

import android.content.Context
import android.os.SystemClock
import com.chargesense.app.data.BatterySnapshot
import com.chargesense.app.data.HistoryDb
import com.chargesense.app.data.Session
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Splits battery activity into charge and discharge sessions, counting the charge moved, peak
 * temperature and screen-on/off drain. The open session is persisted so a service restart resumes it.
 */
class SessionTracker(context: Context) {
    private val db = HistoryDb.get(context)
    private val sp = context.applicationContext.getSharedPreferences("active_session", Context.MODE_PRIVATE)

    private var active: Session? = null
    private var lastTs = 0L
    private var lastLevel = 0f
    private var lastCounter: Int? = null
    private var lastScreenOn = true
    private var tempWeightedSum = 0.0
    private var tempWeightMs = 0L
    private var lastElapsed = 0L
    private var lastUptime = 0L

    init {
        restore()
    }

    fun update(s: BatterySnapshot, screenOn: Boolean): Session? {
        val now = s.timestamp
        var cur = active
        if (cur != null && (cur.charging != s.isPlugged || now - lastTs > RESUME_GAP_MS)) {
            finish()
            cur = null
        }
        if (cur == null) {
            cur = Session(
                charging = s.isPlugged, startTs = now, endTs = now,
                startLevel = s.levelPct, endLevel = s.levelPct, mah = 0f,
                maxTempC = s.temperatureC, minTempC = s.temperatureC, avgTempC = s.temperatureC,
                screenOnMs = 0, screenOffMs = 0, screenOnDrop = 0f, screenOffDrop = 0f,
                plugType = s.plugText,
            )
            tempWeightedSum = 0.0
            tempWeightMs = 0
            lastTs = now
            lastLevel = s.levelPct
            lastCounter = s.chargeCounterMah
            lastScreenOn = screenOn
            lastElapsed = SystemClock.elapsedRealtime()
            lastUptime = SystemClock.uptimeMillis()
            active = cur
            persist()
            return cur
        }

        val dt = (now - lastTs).coerceAtLeast(0)
        val moved = chargeMoved(cur.charging, s, dt)
        var onMs = cur.screenOnMs
        var offMs = cur.screenOffMs
        var onDrop = cur.screenOnDrop
        var offDrop = cur.screenOffDrop
        var deepSleep = cur.deepSleepMs
        val elapsed = SystemClock.elapsedRealtime()
        val uptime = SystemClock.uptimeMillis()
        // uptimeMillis stops while the CPU is suspended, so the gap between the two clocks is deep sleep.
        // Both clocks reset on reboot; a negative delta just means a reboot happened in between.
        val slept = ((elapsed - lastElapsed) - (uptime - lastUptime)).takeIf { elapsed >= lastElapsed && uptime >= lastUptime } ?: 0L
        if (!cur.charging && !lastScreenOn) deepSleep += slept.coerceAtLeast(0)
        if (!cur.charging) {
            val drop = (lastLevel - s.levelPct).coerceAtLeast(0f)
            // Attribute the interval to the screen state it started in.
            if (lastScreenOn) { onMs += dt; onDrop += drop } else { offMs += dt; offDrop += drop }
        }
        tempWeightedSum += s.temperatureC.toDouble() * dt
        tempWeightMs += dt

        cur = cur.copy(
            endTs = now,
            endLevel = s.levelPct,
            mah = cur.mah + moved,
            maxTempC = max(cur.maxTempC, s.temperatureC),
            minTempC = min(cur.minTempC, s.temperatureC),
            deepSleepMs = deepSleep,
            avgTempC = if (tempWeightMs > 0) (tempWeightedSum / tempWeightMs).toFloat() else s.temperatureC,
            screenOnMs = onMs, screenOffMs = offMs, screenOnDrop = onDrop, screenOffDrop = offDrop,
            plugType = if (cur.charging) s.plugText else cur.plugType,
        )
        active = cur
        lastTs = now
        lastLevel = s.levelPct
        lastCounter = s.chargeCounterMah
        lastScreenOn = screenOn
        lastElapsed = elapsed
        lastUptime = uptime
        persist()
        return cur
    }

    /** mAh moved since the last update, from the fuel-gauge counter if present, else by integrating current. */
    private fun chargeMoved(charging: Boolean, s: BatterySnapshot, dt: Long): Float {
        val prevCounter = lastCounter
        val counter = s.chargeCounterMah
        if (prevCounter != null && counter != null) {
            val d = (counter - prevCounter).toFloat()
            return if (charging) d.coerceAtLeast(0f) else (-d).coerceAtLeast(0f)
        }
        val c = s.currentMa ?: return 0f
        // Long gaps mean the CPU slept; don't extrapolate one reading across them.
        val ms = dt.coerceAtMost(MAX_INTEGRATION_MS)
        val flowsRightWay = if (charging) c > 0 else c < 0
        return if (flowsRightWay) abs(c) * ms / 3_600_000f else 0f
    }

    /** Closes the open session, storing it if it is long enough to be meaningful. */
    fun finish() {
        val cur = active ?: return
        if (cur.durationMs >= MIN_SESSION_MS && cur.levelDelta >= 1f) db.insertSession(cur)
        active = null
        sp.edit().clear().apply()
    }

    private fun persist() {
        val c = active ?: return
        sp.edit()
            .putBoolean("charging", c.charging).putLong("start_ts", c.startTs).putLong("end_ts", c.endTs)
            .putFloat("start_level", c.startLevel).putFloat("end_level", c.endLevel).putFloat("mah", c.mah)
            .putFloat("max_temp", c.maxTempC).putFloat("min_temp", c.minTempC).putFloat("avg_temp", c.avgTempC)
            .putLong("deep_sleep", c.deepSleepMs).putLong("last_elapsed", lastElapsed).putLong("last_uptime", lastUptime)
            .putLong("on_ms", c.screenOnMs).putLong("off_ms", c.screenOffMs)
            .putFloat("on_drop", c.screenOnDrop).putFloat("off_drop", c.screenOffDrop)
            .putString("plug", c.plugType)
            .putInt("last_counter", lastCounter ?: -1)
            .apply()
    }

    private fun restore() {
        if (!sp.contains("start_ts")) return
        val s = Session(
            charging = sp.getBoolean("charging", false),
            startTs = sp.getLong("start_ts", 0), endTs = sp.getLong("end_ts", 0),
            startLevel = sp.getFloat("start_level", 0f), endLevel = sp.getFloat("end_level", 0f),
            mah = sp.getFloat("mah", 0f),
            maxTempC = sp.getFloat("max_temp", 0f), minTempC = sp.getFloat("min_temp", sp.getFloat("avg_temp", 0f)),
            avgTempC = sp.getFloat("avg_temp", 0f),
            screenOnMs = sp.getLong("on_ms", 0), screenOffMs = sp.getLong("off_ms", 0),
            screenOnDrop = sp.getFloat("on_drop", 0f), screenOffDrop = sp.getFloat("off_drop", 0f),
            plugType = sp.getString("plug", "") ?: "",
            deepSleepMs = sp.getLong("deep_sleep", 0),
        )
        active = s
        lastElapsed = sp.getLong("last_elapsed", SystemClock.elapsedRealtime())
        lastUptime = sp.getLong("last_uptime", SystemClock.uptimeMillis())
        lastTs = s.endTs
        lastLevel = s.endLevel
        lastCounter = sp.getInt("last_counter", -1).takeIf { it >= 0 }
        tempWeightMs = s.durationMs
        tempWeightedSum = s.avgTempC.toDouble() * s.durationMs
    }

    companion object {
        private const val MIN_SESSION_MS = 3 * 60_000L
        private const val MAX_INTEGRATION_MS = 60_000L
        // Doze can delay updates for hours on an idle phone, so only a very long gap ends a session.
        private const val RESUME_GAP_MS = 6 * 60 * 60_000L
    }
}
