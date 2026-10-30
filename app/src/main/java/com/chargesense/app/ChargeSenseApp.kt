package com.chargesense.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.media.AudioAttributes
import android.net.Uri
import com.chargesense.app.data.CurrentUnit
import com.chargesense.app.data.Prefs

class ChargeSenseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        // 3.1: the status-bar icon was never visible before (silent channel) and a manual mA unit
        // blanked the current on µA phones, so start once from the working defaults.
        val sp = getSharedPreferences("settings_migrations", MODE_PRIVATE)
        if (!sp.getBoolean("v31_defaults", false)) {
            Prefs.get(this).apply {
                statusIcon = com.chargesense.app.service.StatusIcon.LEVEL
                currentUnit = CurrentUnit.AUTO
            }
            sp.edit().putBoolean("v31_defaults", true).apply()
        }
        // Android hides status-bar icons of low-importance ("Silent") notifications, so the live
        // icon needs default importance; with no sound or vibration it still never makes noise.
        nm.deleteNotificationChannel("monitor")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Battery monitor", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Live battery status shown while monitoring"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
        )
        // A channel's sound can't change after creation, so the tick sound lives on a new channel id.
        nm.deleteNotificationChannel("alarms_v1")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Battery alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Charge limit, low battery and temperature alerts"
                enableVibration(true)
                vibrationPattern = TICK_VIBRATION
                setSound(
                    Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.tick_tick}"),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
    }

    companion object {
        const val CHANNEL_MONITOR = "monitor_status"
        const val CHANNEL_ALARMS = "alarms_tick"

        /** Two short pulses, twice — matches the tick-tick sound. */
        val TICK_VIBRATION = longArrayOf(0, 35, 160, 35, 450, 35, 160, 35)
    }
}
