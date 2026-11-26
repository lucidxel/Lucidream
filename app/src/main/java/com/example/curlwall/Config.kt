package com.example.curlwall

import android.content.Context
import android.graphics.Color
import kotlin.math.min

object Config {
    fun prefs(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun fgFor(bg: Int): Int {
        val lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255
        if (lum < 0.45) return Color.WHITE
        val hsv = FloatArray(3)
        Color.colorToHSV(bg, hsv)
        hsv[1] = min(1f, hsv[1] * 1.3f)
        hsv[2] = 0.12f
        return Color.HSVToColor(hsv)
    }
}
