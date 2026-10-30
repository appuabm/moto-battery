package com.chargesense.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Current-unit handling: some kernels report mA instead of the documented µA. */
enum class CurrentUnit { AUTO, MICRO_AMPS, MILLI_AMPS }

/** Sign handling: some devices report discharge as positive. */
enum class CurrentSign { AUTO, NORMAL, INVERTED }

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("chargesense", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)
    /** Bumps on every write so Compose screens can recompose on settings changes. */
    val version: StateFlow<Int> = _version

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        _version.value++
    }

    var monitoringEnabled: Boolean
        get() = sp.getBoolean("monitoring", true)
        set(v) = edit { putBoolean("monitoring", v) }

    var startOnBoot: Boolean
        get() = sp.getBoolean("boot", true)
        set(v) = edit { putBoolean("boot", v) }

    var chargeAlarmEnabled: Boolean
        get() = sp.getBoolean("charge_alarm", true)
        set(v) = edit { putBoolean("charge_alarm", v) }

    var chargeAlarmLevel: Int
        get() = sp.getInt("charge_alarm_level", 80)
        set(v) = edit { putInt("charge_alarm_level", v) }

    var lowAlarmEnabled: Boolean
        get() = sp.getBoolean("low_alarm", true)
        set(v) = edit { putBoolean("low_alarm", v) }

    var lowAlarmLevel: Int
        get() = sp.getInt("low_alarm_level", 20)
        set(v) = edit { putInt("low_alarm_level", v) }

    var tempAlarmEnabled: Boolean
        get() = sp.getBoolean("temp_alarm", true)
        set(v) = edit { putBoolean("temp_alarm", v) }

    var tempAlarmC: Int
        get() = sp.getInt("temp_alarm_c", 42)
        set(v) = edit { putInt("temp_alarm_c", v) }

    var alarmVibrate: Boolean
        get() = sp.getBoolean("alarm_vibrate", true)
        set(v) = edit { putBoolean("alarm_vibrate", v) }

    var currentUnit: CurrentUnit
        get() = CurrentUnit.valueOf(sp.getString("current_unit", CurrentUnit.AUTO.name)!!)
        set(v) = edit { putString("current_unit", v.name) }

    var currentSign: CurrentSign
        get() = CurrentSign.valueOf(sp.getString("current_sign", CurrentSign.AUTO.name)!!)
        set(v) = edit { putString("current_sign", v.name) }

    /** Learned in AUTO mode: true if this device reports discharge as a positive number. */
    var learnedSignInverted: Boolean
        get() = sp.getBoolean("learned_inverted", false)
        set(v) = edit { putBoolean("learned_inverted", v) }

    /** AUTO-mode unit learned from readings: 0 = unknown yet, 1 = µA, 2 = mA. */
    var learnedUnit: Int
        get() = sp.getInt("learned_unit", 0)
        set(v) = edit { putInt("learned_unit", v) }

    /** What the status-bar icon shows: 0 = app icon, 1 = level, 2 = current, 3 = temperature. */
    var statusIcon: Int
        get() = sp.getInt("status_icon", 1)
        set(v) = edit { putInt("status_icon", v) }

    /** Explore cards on Home the user closed (by key). */
    var hiddenCards: Set<String>
        get() = sp.getStringSet("hidden_cards", emptySet()) ?: emptySet()
        set(v) = edit { putStringSet("hidden_cards", v) }

    /** 0 = use the value from the system power profile. */
    var designCapacityOverride: Int
        get() = sp.getInt("design_capacity", 0)
        set(v) = edit { putInt("design_capacity", v) }

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = edit { putBoolean("onboarding_done", v) }

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(context).also { instance = it } }
    }
}
