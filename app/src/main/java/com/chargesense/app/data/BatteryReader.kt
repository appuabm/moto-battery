package com.chargesense.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

data class BatterySnapshot(
    val timestamp: Long,
    val levelPct: Float,
    val status: Int,
    val plugged: Int,
    val health: Int,
    val technology: String,
    val temperatureC: Float,
    val voltageMv: Int,
    /** Positive while current flows into the battery, negative while draining. Null if unsupported. */
    val currentMa: Int?,
    /** Remaining charge reported by the fuel gauge, in mAh. Null if unsupported. */
    val chargeCounterMah: Int?,
    val cycleCount: Int?,
) {
    val isPlugged get() = plugged != 0
    val isCharging get() = status == BatteryManager.BATTERY_STATUS_CHARGING
    val isFull get() = status == BatteryManager.BATTERY_STATUS_FULL
    val powerW: Float? get() = currentMa?.let { it * voltageMv / 1_000_000f }

    val statusText: String
        get() = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }

    val plugText: String
        get() = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
            0 -> "Unplugged"
            else -> "Plugged"
        }

    val healthText: String
        get() = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
}

/** Reads the sticky battery broadcast plus BatteryManager properties and normalises the current. */
class BatteryReader(private val context: Context) {
    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val prefs = Prefs.get(context)
    private var smallReadings = 0

    fun read(intent: Intent? = null): BatterySnapshot? {
        val i = intent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        // EXTRA_CYCLE_COUNT exists from Android 14; read by name so older SDK levels still compile/run.
        val cycles = i.getIntExtra("android.os.extra.CYCLE_COUNT", -1).takeIf { it >= 0 }

        val levelPct = if (level >= 0) level * 100f / scale
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toFloat()

        return BatterySnapshot(
            timestamp = System.currentTimeMillis(),
            levelPct = levelPct,
            status = status,
            plugged = plugged,
            health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
            technology = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.ifBlank { null } ?: "Unknown",
            temperatureC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageMv = normaliseVoltage(i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)),
            currentMa = readCurrentMa(plugged != 0),
            chargeCounterMah = readChargeCounterMah(),
            cycleCount = cycles,
        )
    }

    private fun normaliseVoltage(v: Int): Int = when {
        v in 1..20 -> v * 1000          // reported in volts
        v > 100_000 -> v / 1000         // reported in µV
        else -> v
    }

    private fun readCurrentMa(plugged: Boolean): Int? {
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == Int.MIN_VALUE || raw == Int.MAX_VALUE) return null
        var ma = when (prefs.currentUnit) {
            CurrentUnit.MICRO_AMPS -> raw / 1000
            CurrentUnit.MILLI_AMPS -> raw
            CurrentUnit.AUTO -> autoUnit(raw)
        }
        // A manual unit that yields an impossible value is wrong for this phone; fall back to auto.
        if (abs(ma) > 20_000 && prefs.currentUnit != CurrentUnit.AUTO) ma = autoUnit(raw)
        // Anything beyond 20 A is a glitch, not a reading.
        if (abs(ma) > 20_000) return null
        val inverted = when (prefs.currentSign) {
            CurrentSign.NORMAL -> false
            CurrentSign.INVERTED -> true
            CurrentSign.AUTO -> {
                // Unplugged the battery can only be draining, so the sign seen there is ground truth.
                if (!plugged && abs(ma) > 30) {
                    val inv = ma > 0
                    if (inv != prefs.learnedSignInverted) prefs.learnedSignInverted = inv
                }
                prefs.learnedSignInverted
            }
        }
        if (inverted) ma = -ma
        return ma
    }

    /**
     * Android documents µA, but some kernels report mA. A µA device eventually shows a value of
     * 20 000 or more (20 mA); a mA device never does. Until a large value is seen, small values are
     * ambiguous: −11 000 could be −11 mA or −11 A. Assume µA, and only switch to mA after a long run
     * of readings that never reached 20 000.
     */
    private fun autoUnit(raw: Int): Int {
        val learned = prefs.learnedUnit
        if (abs(raw) >= 20_000) {
            if (learned != 1) prefs.learnedUnit = 1
            return raw / 1000
        }
        if (learned == 0 && raw != 0 && ++smallReadings >= 400) prefs.learnedUnit = 2
        return if (prefs.learnedUnit == 2) raw else raw / 1000
    }

    private fun readChargeCounterMah(): Int? {
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (raw == Int.MIN_VALUE || raw <= 0) return null
        // Documented as µAh; a few devices report mAh.
        return if (raw > 100_000) raw / 1000 else raw
    }
}
