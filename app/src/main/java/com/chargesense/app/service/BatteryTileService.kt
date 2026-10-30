package com.chargesense.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.chargesense.app.R
import com.chargesense.app.data.BatteryReader
import com.chargesense.app.ui.MainActivity
import kotlin.math.roundToInt

/** Quick Settings tile showing level and live current; tapping it opens the app. */
class BatteryTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val s = BatteryReader(this).read() ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_battery)
        tile.label = "${s.levelPct.roundToInt()}%"
        val detail = listOfNotNull(
            s.currentMa?.let { (if (it > 0) "+" else "") + "$it mA" },
            "%.1f °C".format(s.temperatureC),
        ).joinToString(" · ")
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = detail else tile.label = "${tile.label} · $detail"
        tile.state = if (s.isPlugged) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
