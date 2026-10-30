package com.chargesense.app.data

import android.annotation.SuppressLint
import android.content.Context

object DesignCapacity {
    private var cached: Int? = null

    /** Factory capacity in mAh: user override, else the highest PowerProfile value seen, else 0. */
    fun get(context: Context): Int {
        val override = Prefs.get(context).designCapacityOverride
        if (override > 0) return override
        return cached ?: detected(context).also { cached = it }
    }

    /**
     * Design capacity never shrinks, but some builds report the battery's learned (worn) capacity
     * through PowerProfile at times. Keep the highest value ever seen so health stays meaningful.
     */
    fun detected(context: Context): Int {
        val sp = context.getSharedPreferences("design_capacity", Context.MODE_PRIVATE)
        val best = maxOf(fromPowerProfile(context), sp.getInt("max_seen", 0))
        if (best > sp.getInt("max_seen", 0)) sp.edit().putInt("max_seen", best).apply()
        return best
    }

    fun fromPowerProfile(context: Context): Int = try {
        @SuppressLint("PrivateApi")
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(context)
        // The raw "battery.capacity" entry is the factory value from the device's power_profile.xml;
        // getBatteryCapacity() can return a learned value instead on newer builds.
        val raw = runCatching { cls.getMethod("getAveragePower", String::class.java).invoke(profile, "battery.capacity") as Double }.getOrDefault(0.0)
        val reported = runCatching { cls.getMethod("getBatteryCapacity").invoke(profile) as Double }.getOrDefault(0.0)
        val cap = maxOf(raw, reported)
        // The stub profile in some ROMs reports 1000 mAh, which is never right for a phone.
        if (cap > 1000.0) cap.toInt() else 0
    } catch (_: Throwable) {
        0
    }
}
