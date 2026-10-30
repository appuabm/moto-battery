package com.chargesense.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.RemoteViews
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.chargesense.app.ChargeSenseApp
import com.chargesense.app.R
import com.chargesense.app.data.BatteryReader
import com.chargesense.app.data.BatterySnapshot
import com.chargesense.app.data.DesignCapacity
import com.chargesense.app.data.Estimates
import com.chargesense.app.data.HistoryDb
import com.chargesense.app.data.LiveState
import com.chargesense.app.data.Prefs
import com.chargesense.app.data.Sample
import com.chargesense.app.data.formatMinutes
import com.chargesense.app.ui.MainActivity
import kotlin.math.roundToInt

/** Foreground service that samples the battery, tracks sessions, raises alarms and keeps a live notification. */
class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var reader: BatteryReader
    private lateinit var tracker: SessionTracker
    private lateinit var alarms: Alarms
    private lateinit var db: HistoryDb
    private lateinit var prefs: Prefs
    private var screenOn = true
    private var lastSampleTs = 0L
    private var lastPruneTs = 0L
    private var lastPlugged: Boolean? = null
    private var lastNotifKey: String? = null
    private var lastNotifTs = 0L
    private var sampleElapsed = SystemClock.elapsedRealtime()
    private var sampleUptime = SystemClock.uptimeMillis()
    private var cachedCapacity = 0f
    private var capacityRefreshedTs = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> tick(intent)
                Intent.ACTION_SCREEN_ON -> { screenOn = true; reschedule() }
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; reschedule() }
                else -> tick(null)
            }
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            tick(null)
            handler.postDelayed(this, if (screenOn) INTERVAL_SCREEN_ON else INTERVAL_SCREEN_OFF)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        reader = BatteryReader(this)
        tracker = SessionTracker(this)
        alarms = Alarms(this)
        db = HistoryDb.get(this)
        screenOn = getSystemService(PowerManager::class.java).isInteractive

        val initial = reader.read()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(initial),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        LiveState.setServiceRunning(true)
        handler.post(loop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(batteryReceiver) }
        LiveState.setServiceRunning(false)
        super.onDestroy()
    }

    private fun reschedule() {
        handler.removeCallbacks(loop)
        handler.post(loop)
    }

    private fun tick(intent: Intent?) {
        val s = reader.read(intent) ?: return
        LiveState.publish(s)
        LiveState.publishSession(tracker.update(s, screenOn))
        alarms.check(s)

        val plugChanged = lastPlugged != null && lastPlugged != s.isPlugged
        lastPlugged = s.isPlugged
        if (s.timestamp - lastSampleTs >= SAMPLE_EVERY_MS || plugChanged) {
            lastSampleTs = s.timestamp
            db.insertSample(
                Sample(s.timestamp, s.levelPct, s.currentMa, s.temperatureC, s.voltageMv, s.isPlugged, screenOn, sleepFractionSinceLastSample())
            )
            runCatching { Widgets.updateAll(this, s) }
            runCatching { TileService.requestListeningState(this, ComponentName(this, BatteryTileService::class.java)) }
        }
        if (s.timestamp - lastPruneTs >= 6 * 60 * 60_000L) {
            lastPruneTs = s.timestamp
            db.pruneSamples(s.timestamp - KEEP_SAMPLES_MS)
        }
        // Re-post the live notification only when something visible changed state, or every 10 s for
        // the live readings; rebuilding it on every 3 s tick wasted CPU and battery.
        val notifKey = "${s.levelPct.toInt()}|${s.status}|${s.plugged}"
        if (notifKey != lastNotifKey || s.timestamp - lastNotifTs >= NOTIFY_EVERY_MS) {
            lastNotifKey = notifKey
            lastNotifTs = s.timestamp
            NotificationManagerCompat.from(this).runCatching { notify(NOTIFICATION_ID, buildNotification(s)) }
        }
    }

    /** uptimeMillis pauses during deep sleep, so the gap to elapsedRealtime is time spent asleep. */
    private fun sleepFractionSinceLastSample(): Float {
        val elapsed = SystemClock.elapsedRealtime()
        val uptime = SystemClock.uptimeMillis()
        val de = elapsed - sampleElapsed
        val du = uptime - sampleUptime
        sampleElapsed = elapsed
        sampleUptime = uptime
        return if (de > 0 && du >= 0) ((de - du).toFloat() / de).coerceIn(0f, 1f) else 0f
    }

    private fun capacityForEstimates(s: BatterySnapshot): Float {
        if (s.timestamp - capacityRefreshedTs > 10 * 60_000L || cachedCapacity <= 0f) {
            capacityRefreshedTs = s.timestamp
            cachedCapacity = Estimates.capacity(s, db.sessions(20))?.mah
                ?: DesignCapacity.get(this).toFloat()
        }
        return cachedCapacity
    }

    private fun buildNotification(s: BatterySnapshot?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, ChargeSenseApp.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s == null) return b.setContentTitle("Monitoring battery").build()
        StatusIcon.create(s, prefs.statusIcon)?.let { b.setSmallIcon(it) }

        val level = s.levelPct.roundToInt()
        val current = s.currentMa?.let { (if (it > 0) "+" else "") + "$it mA" } ?: "—"
        val power = s.powerW?.let { "%.1f W".format(kotlin.math.abs(it)) } ?: "—"
        val temp = "%.1f °C".format(s.temperatureC)
        val mins = Estimates.minutesRemaining(s, LiveState.smoothedCurrentMa.value, capacityForEstimates(s), prefs.chargeAlarmLevel)
        val status = when {
            s.isFull -> "Fully charged"
            s.isPlugged && !s.isCharging -> "Charging paused"
            mins != null && s.isPlugged -> "Charging · ${formatMinutes(mins)} to ${Estimates.chargeTargetFor(s, prefs.chargeAlarmLevel)}%"
            mins != null -> "On battery · ~${formatMinutes(mins)} left"
            s.isPlugged -> "Charging"
            else -> "On battery"
        }
        val metrics = "$current · $temp · $power"
        val summary = LiveState.activeSession.value?.let { a ->
            val d = (a.endLevel - a.startLevel).roundToInt()
            val sign = if (d >= 0) "+" else "−"
            (if (a.charging) "This charge: " else "Since unplugged: ") +
                "$sign${kotlin.math.abs(d)}% · ${a.mah.roundToInt()} mAh · ${formatMinutes((a.durationMs / 60_000).toInt())}"
        } ?: s.plugText

        fun RemoteViews.fillHeader() {
            setTextViewText(R.id.n_level, "$level%")
            setTextViewText(R.id.n_status, status)
            setTextViewText(R.id.n_metrics, metrics)
        }
        val collapsed = RemoteViews(packageName, R.layout.notif_collapsed).apply { fillHeader() }
        val expanded = RemoteViews(packageName, R.layout.notif_expanded).apply {
            fillHeader()
            setProgressBar(R.id.n_progress, 100, level, false)
            setTextViewText(R.id.n_current, current)
            setTextViewText(R.id.n_power, power)
            setTextViewText(R.id.n_temp, temp)
            setTextViewText(R.id.n_voltage, "%.2f V".format(s.voltageMv / 1000f))
            setTextViewText(R.id.n_summary, summary)
        }
        return b
            // Plain text stays set for accessibility, the lock screen and older launchers.
            .setContentTitle("$level% · $status")
            .setContentText(metrics)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_SCREEN_ON = 3_000L
        private const val INTERVAL_SCREEN_OFF = 60_000L
        private const val SAMPLE_EVERY_MS = 60_000L
        private const val NOTIFY_EVERY_MS = 10_000L
        private const val KEEP_SAMPLES_MS = 7 * 24 * 60 * 60_000L
        const val ACTION_STOP = "com.chargesense.app.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
