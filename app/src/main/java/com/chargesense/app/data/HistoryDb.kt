package com.chargesense.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Sample(
    val ts: Long,
    val level: Float,
    val currentMa: Int?,
    val tempC: Float,
    val voltageMv: Int,
    val plugged: Boolean,
    val screenOn: Boolean,
    /** Fraction (0..1) of the interval before this sample that the CPU spent in deep sleep. */
    val sleepFrac: Float = 0f,
)

data class Session(
    val id: Long = 0,
    val charging: Boolean,
    val startTs: Long,
    val endTs: Long,
    val startLevel: Float,
    val endLevel: Float,
    /** Charge moved in (charging) or out (discharging), always >= 0. */
    val mah: Float,
    val maxTempC: Float,
    val minTempC: Float,
    val avgTempC: Float,
    val screenOnMs: Long,
    val screenOffMs: Long,
    val screenOnDrop: Float,
    val screenOffDrop: Float,
    val plugType: String,
    /** Screen-off time the CPU spent suspended. */
    val deepSleepMs: Long = 0,
) {
    val durationMs get() = endTs - startTs
    val levelDelta get() = kotlin.math.abs(endLevel - startLevel)
    val avgCurrentMa: Float get() = if (durationMs > 0) mah / (durationMs / 3_600_000f) else 0f

    /** Full-battery capacity implied by this session, or null if the level change is too small to trust. */
    val capacityEstimate: Float?
        get() = if (levelDelta >= 15f && mah > 0f) mah / (levelDelta / 100f) else null

    val screenOnRatePerHour: Float?
        get() = if (screenOnMs > 5 * 60_000) screenOnDrop / (screenOnMs / 3_600_000f) else null
    val deepSleepPct: Float?
        get() = if (screenOffMs > 10 * 60_000) (deepSleepMs * 100f / screenOffMs).coerceAtMost(100f) else null

    val screenOffRatePerHour: Float?
        get() = if (screenOffMs > 15 * 60_000) screenOffDrop / (screenOffMs / 3_600_000f) else null
}

class HistoryDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "history.db", null, 4) {

    private val _changes = MutableStateFlow(0)
    /** Bumps when sessions change so the history screen can reload. */
    val changes: StateFlow<Int> = _changes

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE samples(
                ts INTEGER PRIMARY KEY, level REAL, current INTEGER, temp REAL,
                voltage INTEGER, plugged INTEGER, screen INTEGER, sleep REAL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE sessions(
                id INTEGER PRIMARY KEY AUTOINCREMENT, charging INTEGER, start_ts INTEGER, end_ts INTEGER,
                start_level REAL, end_level REAL, mah REAL, max_temp REAL, avg_temp REAL,
                screen_on_ms INTEGER, screen_off_ms INTEGER, screen_on_drop REAL, screen_off_drop REAL,
                plug_type TEXT, min_temp REAL, deep_sleep_ms INTEGER DEFAULT 0)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN min_temp REAL")
            db.execSQL("ALTER TABLE sessions ADD COLUMN deep_sleep_ms INTEGER DEFAULT 0")
            db.execSQL("UPDATE sessions SET min_temp = avg_temp")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE samples ADD COLUMN sleep REAL DEFAULT 0")
        }
        if (oldVersion < 4) {
            // Earlier builds misread tiny µA currents as amps; drop those impossible readings.
            db.execSQL("UPDATE samples SET current = NULL WHERE current >= 9000 OR current <= -9000")
        }
    }

    fun insertSample(s: Sample) {
        writableDatabase.insertWithOnConflict("samples", null, ContentValues().apply {
            put("ts", s.ts)
            put("level", s.level)
            if (s.currentMa != null) put("current", s.currentMa) else putNull("current")
            put("temp", s.tempC)
            put("voltage", s.voltageMv)
            put("plugged", if (s.plugged) 1 else 0)
            put("screen", if (s.screenOn) 1 else 0)
            put("sleep", s.sleepFrac)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun samplesSince(since: Long): List<Sample> =
        readableDatabase.rawQuery(
            "SELECT ts, level, current, temp, voltage, plugged, screen, sleep FROM samples WHERE ts >= ? ORDER BY ts",
            arrayOf(since.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Sample(
                        ts = c.getLong(0),
                        level = c.getFloat(1),
                        currentMa = if (c.isNull(2)) null else c.getInt(2),
                        tempC = c.getFloat(3),
                        voltageMv = c.getInt(4),
                        plugged = c.getInt(5) == 1,
                        screenOn = c.getInt(6) == 1,
                        sleepFrac = if (c.isNull(7)) 0f else c.getFloat(7),
                    )
                )
            }
        }

    fun pruneSamples(olderThan: Long) {
        writableDatabase.delete("samples", "ts < ?", arrayOf(olderThan.toString()))
    }

    fun insertSession(s: Session) {
        writableDatabase.insert("sessions", null, ContentValues().apply {
            put("charging", if (s.charging) 1 else 0)
            put("start_ts", s.startTs)
            put("end_ts", s.endTs)
            put("start_level", s.startLevel)
            put("end_level", s.endLevel)
            put("mah", s.mah)
            put("max_temp", s.maxTempC)
            put("avg_temp", s.avgTempC)
            put("screen_on_ms", s.screenOnMs)
            put("screen_off_ms", s.screenOffMs)
            put("screen_on_drop", s.screenOnDrop)
            put("screen_off_drop", s.screenOffDrop)
            put("plug_type", s.plugType)
            put("min_temp", s.minTempC)
            put("deep_sleep_ms", s.deepSleepMs)
        })
        _changes.value++
    }

    fun sessions(limit: Int = 200): List<Session> =
        readableDatabase.rawQuery(
            """SELECT id, charging, start_ts, end_ts, start_level, end_level, mah, max_temp, avg_temp,
                screen_on_ms, screen_off_ms, screen_on_drop, screen_off_drop, plug_type, min_temp, deep_sleep_ms
               FROM sessions ORDER BY end_ts DESC LIMIT $limit""", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Session(
                        id = c.getLong(0),
                        charging = c.getInt(1) == 1,
                        startTs = c.getLong(2),
                        endTs = c.getLong(3),
                        startLevel = c.getFloat(4),
                        endLevel = c.getFloat(5),
                        mah = c.getFloat(6),
                        maxTempC = c.getFloat(7),
                        minTempC = if (c.isNull(14)) c.getFloat(8) else c.getFloat(14),
                        avgTempC = c.getFloat(8),
                        screenOnMs = c.getLong(9),
                        screenOffMs = c.getLong(10),
                        screenOnDrop = c.getFloat(11),
                        screenOffDrop = c.getFloat(12),
                        plugType = c.getString(13) ?: "",
                        deepSleepMs = c.getLong(15),
                    )
                )
            }
        }

    fun clearAll() {
        writableDatabase.delete("sessions", null, null)
        writableDatabase.delete("samples", null, null)
        _changes.value++
    }

    companion object {
        @Volatile private var instance: HistoryDb? = null
        fun get(context: Context): HistoryDb =
            instance ?: synchronized(this) { instance ?: HistoryDb(context).also { instance = it } }
    }
}
