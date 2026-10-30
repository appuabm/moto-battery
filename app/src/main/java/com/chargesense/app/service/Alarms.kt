package com.chargesense.app.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chargesense.app.ChargeSenseApp
import com.chargesense.app.R
import com.chargesense.app.data.BatterySnapshot
import com.chargesense.app.data.Prefs
import com.chargesense.app.ui.MainActivity

/** Fires each alarm once per crossing, re-arming when the condition clears with some hysteresis. */
class Alarms(private val context: Context) {
    private val prefs = Prefs.get(context)
    private var chargeFired = false
    private var lowFired = false
    private var tempFired = false

    fun check(s: BatterySnapshot) {
        val level = s.levelPct

        if (!s.isPlugged) chargeFired = false
        if (prefs.chargeAlarmEnabled && s.isPlugged && !chargeFired && level >= prefs.chargeAlarmLevel) {
            chargeFired = true
            notify(
                ID_CHARGE, "Charged to ${level.toInt()}%",
                "Unplug now to reduce battery wear (limit ${prefs.chargeAlarmLevel}%)."
            )
        }

        if (s.isPlugged || level > prefs.lowAlarmLevel + 3) lowFired = false
        if (prefs.lowAlarmEnabled && !s.isPlugged && !lowFired && level <= prefs.lowAlarmLevel) {
            lowFired = true
            notify(ID_LOW, "Battery low: ${level.toInt()}%", "Plug in your charger soon.")
        }

        if (s.temperatureC < prefs.tempAlarmC - 2) tempFired = false
        if (prefs.tempAlarmEnabled && !tempFired && s.temperatureC >= prefs.tempAlarmC) {
            tempFired = true
            notify(
                ID_TEMP, "Battery hot: %.1f °C".format(s.temperatureC),
                if (s.isPlugged) "Consider unplugging or removing the case while it cools."
                else "Close heavy apps and let the phone cool down."
            )
        }
    }

    private fun notify(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, ChargeSenseApp.CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
        if (prefs.alarmVibrate) vibrate()
    }

    private fun vibrate() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        v?.vibrate(VibrationEffect.createWaveform(ChargeSenseApp.TICK_VIBRATION, -1))
    }

    companion object {
        const val ID_CHARGE = 101
        const val ID_LOW = 102
        const val ID_TEMP = 103
    }
}
