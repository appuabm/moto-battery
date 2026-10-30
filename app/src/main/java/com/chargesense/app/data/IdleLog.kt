package com.chargesense.app.data

/** A stretch of time on battery with the screen off. */
data class IdlePeriod(
    val startTs: Long,
    val endTs: Long,
    val startLevel: Float,
    val endLevel: Float,
    /** Share of the period the CPU spent in deep sleep, 0..1. */
    val deepSleep: Float,
) {
    val durationMs get() = endTs - startTs
    val drop get() = (startLevel - endLevel).coerceAtLeast(0f)
    val ratePerHour: Float get() = if (durationMs > 0) drop / (durationMs / 3_600_000f) else 0f
}

object IdleLog {
    private const val GAP_MS = 20 * 60_000L
    private const val MIN_PERIOD_MS = 15 * 60_000L

    /** Splits samples into screen-off, unplugged runs, newest first. */
    fun periods(samples: List<Sample>): List<IdlePeriod> {
        val out = mutableListOf<IdlePeriod>()
        var run = mutableListOf<Sample>()
        fun flush() {
            if (run.size >= 2 && run.last().ts - run.first().ts >= MIN_PERIOD_MS) {
                // Weight each sample's sleep fraction by the interval it covers.
                var sleepMs = 0.0
                var totalMs = 0.0
                for (i in 1 until run.size) {
                    val dt = (run[i].ts - run[i - 1].ts).toDouble()
                    sleepMs += dt * run[i].sleepFrac
                    totalMs += dt
                }
                out += IdlePeriod(
                    run.first().ts, run.last().ts, run.first().level, run.last().level,
                    if (totalMs > 0) (sleepMs / totalMs).toFloat() else 0f,
                )
            }
            run = mutableListOf()
        }
        for (s in samples) {
            val idle = !s.screenOn && !s.plugged
            val prev = run.lastOrNull()
            if (!idle || (prev != null && s.ts - prev.ts > GAP_MS)) flush()
            if (idle) run += s
        }
        flush()
        return out.reversed()
    }
}
