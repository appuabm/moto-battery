package com.chargesense.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Process
import android.provider.Settings

data class AppDrain(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val foregroundMs: Long,
    val mah: Float,
    val pctOfBattery: Float,
)

/**
 * Estimates per-app battery use for a discharge session. Android doesn't expose per-app energy to
 * normal apps, so the session's screen-on drain is split between apps by foreground time.
 */
object AppUsage {

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun forSession(context: Context, session: Session, capacityMah: Float, limit: Int = 8): List<AppDrain> {
        if (!hasAccess(context) || session.charging) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val fg = foregroundTimes(usm, session.startTs, maxOf(session.endTs, System.currentTimeMillis()))
        val totalFg = fg.values.sum()
        if (totalFg <= 0) return emptyList()

        // Charge used while the screen was on; fall back to time share if the level hasn't moved yet.
        val drops = session.screenOnDrop + session.screenOffDrop
        val onShare = when {
            drops > 0f -> session.screenOnDrop / drops
            session.screenOnMs + session.screenOffMs > 0 -> session.screenOnMs.toFloat() / (session.screenOnMs + session.screenOffMs)
            else -> 1f
        }
        val screenOnMah = session.mah * onShare
        val pm = context.packageManager
        return fg.entries.sortedByDescending { it.value }.take(limit).map { (pkg, ms) ->
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            val mah = screenOnMah * ms / totalFg
            AppDrain(
                packageName = pkg,
                label = info?.let { pm.getApplicationLabel(it).toString() } ?: pkg,
                icon = info?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() },
                foregroundMs = ms,
                mah = mah,
                pctOfBattery = if (capacityMah > 0) mah / capacityMah * 100f else 0f,
            )
        }
    }

    private fun foregroundTimes(usm: UsageStatsManager, from: Long, to: Long): Map<String, Long> {
        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        val resumedAt = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = resumedAt.remove(e.packageName) ?: continue
                    totals[e.packageName] = (totals[e.packageName] ?: 0L) + (e.timeStamp - start)
                }
            }
        }
        // Whatever is still in front counts up to now.
        resumedAt.forEach { (pkg, start) -> totals[pkg] = (totals[pkg] ?: 0L) + (to - start) }
        return totals.filterValues { it > 1_000 }
    }
}
