package com.chargesense.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chargesense.app.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs.get(context)
        if (prefs.monitoringEnabled && prefs.startOnBoot) {
            runCatching { MonitorService.start(context) }
        }
    }
}
