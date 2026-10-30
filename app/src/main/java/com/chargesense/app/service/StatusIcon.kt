package com.chargesense.app.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import com.chargesense.app.data.BatterySnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/** Draws the live value (level, current or temperature) as the notification's status-bar icon. */
object StatusIcon {
    const val APP_ICON = 0
    const val LEVEL = 1
    const val CURRENT = 2
    const val TEMPERATURE = 3

    private const val SIZE = 96
    private var cacheKey: String? = null
    private var cacheIcon: IconCompat? = null

    /** Null means: use the regular app icon. */
    fun create(s: BatterySnapshot, mode: Int): IconCompat? {
        val top: String
        val bottom: String
        when (mode) {
            LEVEL -> { top = "${s.levelPct.roundToInt()}"; bottom = "%" }
            CURRENT -> {
                val ma = s.currentMa ?: return null
                // Status-bar icons are tiny: show amps with one decimal above 1 A.
                if (abs(ma) >= 1000) { top = "%.1f".format(abs(ma) / 1000f); bottom = if (ma > 0) "+A" else "−A" }
                else { top = "${abs(ma)}"; bottom = if (ma > 0) "+mA" else "−mA" }
            }
            TEMPERATURE -> { top = "${s.temperatureC.roundToInt()}"; bottom = "°C" }
            else -> return null
        }
        val key = "$mode|$top|$bottom"
        if (key == cacheKey) cacheIcon?.let { return it }

        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Shrink the main number until it fits the width.
        paint.textSize = 64f
        while (paint.measureText(top) > SIZE - 4 && paint.textSize > 20f) paint.textSize -= 2f
        c.drawText(top, SIZE / 2f, 62f, paint)
        paint.textSize = 30f
        while (paint.measureText(bottom) > SIZE - 4 && paint.textSize > 12f) paint.textSize -= 2f
        c.drawText(bottom, SIZE / 2f, 92f, paint)

        return IconCompat.createWithBitmap(bmp).also {
            cacheKey = key
            cacheIcon = it
        }
    }
}
