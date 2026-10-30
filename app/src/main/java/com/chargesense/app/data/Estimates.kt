package com.chargesense.app.data

import kotlin.math.roundToInt

data class CapacityEstimate(
    val mah: Float,
    val source: String,
    val samples: Int,
)

object Estimates {

    /**
     * Best guess at the battery's real full-charge capacity. Prefers the median of recent charge
     * sessions that covered >= 15 %, since that measures charge actually pushed into the cell.
     * Falls back to the fuel gauge's charge counter scaled by level.
     */
    fun capacity(snapshot: BatterySnapshot?, sessions: List<Session>): CapacityEstimate? {
        val fromCharging = sessions.filter { it.charging }.mapNotNull { it.capacityEstimate }.take(10)
        if (fromCharging.isNotEmpty()) {
            return CapacityEstimate(median(fromCharging), "Measured over ${fromCharging.size} charge session(s)", fromCharging.size)
        }
        val fromAny = sessions.mapNotNull { it.capacityEstimate }.take(10)
        if (fromAny.isNotEmpty()) {
            return CapacityEstimate(median(fromAny), "Measured over ${fromAny.size} discharge session(s)", fromAny.size)
        }
        val s = snapshot ?: return null
        val counter = s.chargeCounterMah ?: return null
        if (s.levelPct < 10f) return null
        return CapacityEstimate(counter / (s.levelPct / 100f), "Fuel-gauge charge counter", 1)
    }

    /** Health as a percentage of design capacity, capped at 100. */
    fun healthPct(estimate: CapacityEstimate?, design: Int): Int? {
        if (estimate == null || design <= 0) return null
        return (estimate.mah / design * 100f).roundToInt().coerceIn(1, 100)
    }

    /**
     * Minutes until [chargeTarget] % (or 100 % once past it) while charging, or until empty while
     * discharging. Null when there isn't enough information.
     */
    fun minutesRemaining(s: BatterySnapshot, currentMa: Float?, capacityMah: Float, chargeTarget: Int): Int? {
        if (currentMa == null || capacityMah <= 0f) return null
        return if (s.isPlugged) {
            if (s.isFull || currentMa < 20f) return null
            val target = if (s.levelPct < chargeTarget) chargeTarget.toFloat() else 100f
            // Above ~80 % the charger switches to constant-voltage and current tapers; pad for it.
            val bulk = (minOf(target, 80f) - s.levelPct).coerceAtLeast(0f)
            val taper = (target - maxOf(s.levelPct, 80f)).coerceAtLeast(0f) * 1.6f
            ((bulk + taper) / 100f * capacityMah / currentMa * 60f).roundToInt()
        } else {
            if (currentMa > -5f) return null
            (s.levelPct / 100f * capacityMah / -currentMa * 60f).roundToInt()
        }
    }

    fun chargeTargetFor(s: BatterySnapshot, chargeTarget: Int): Int =
        if (s.levelPct < chargeTarget) chargeTarget else 100

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}

fun formatMinutes(min: Int): String = when {
    min < 1 -> "< 1 min"
    min < 60 -> "$min min"
    else -> "${min / 60} h ${min % 60} min"
}
