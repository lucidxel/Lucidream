package com.example.curlwall

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import kotlin.math.*

object Config {
    fun prefs(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    val GRADS: List<IntArray> = listOf(
        "F3A2C6 119DA4 2D142C", "A7A8F5 0838E8 0B0708", "6B0F0A F5ECE4 2A5CA8", "C27DF2 050505 B7D5FA",
        "9C8FE8 F74E0C B32406", "F7B2E8 F67B5A F39618", "04060A 4A2511 A6C8F0", "F8C918 160504 73B3FA",
        "F6B028 0933D6 2D261D", "380754 D95B14 9BB7C9", "D93B9C F85A14 DCE4F7", "2998C4 2E1E12 B8B5C4",
        "1D5FD6 4A9BE0 E88B68", "081442 148F2E E86B24", "3E568C A8A5A0 4A659E", "04040A 0A2652 59A6E6",
        "1B0605 D9380C D4A3F7", "8CC4F5 3B180B E8E6E3", "040624 16B8F3 B8F2E6", "F2A2D4 F26B38 9ED8E8"
    ).map { s -> s.split(" ").map { Color.parseColor("#$it") }.toIntArray() }

    private fun lum(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0

    private fun tint(bg: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(bg, hsv)
        hsv[1] = min(1f, hsv[1] * 1.3f)
        hsv[2] = 0.12f
        return Color.HSVToColor(hsv)
    }

    fun fgFor(bg: Int, bg2: Int = bg): Int =
        if ((lum(bg) + lum(bg2)) / 2 < 0.45) Color.WHITE else tint(bg)

    fun fgFromColors(cs: IntArray): Int =
        if (cs.map { lum(it) }.average() < 0.45) Color.WHITE else tint(cs[0])

    /** Square colour for the currently selected background. */
    fun fg(p: SharedPreferences): Int {
        val g = p.getInt("grad", -1)
        if (g in GRADS.indices) return fgFromColors(GRADS[g])
        val b = p.getInt("bg", Color.WHITE)
        return fgFor(b, p.getInt("bg2", b))
    }

    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Animated 3-colour gradient. style: 0 = volume mesh, 1 = edge, 2 = linear. */
    fun drawGrad(cv: Canvas, l: Float, t: Float, r: Float, b: Float, cs: IntArray, style: Int, time: Float) {
        val w = r - l
        val h = b - t
        if (style == 2) {
            val ang = 1.2f + 0.6f * sin(time * 0.15f)
            val dx = cos(ang)
            val dy = sin(ang)
            val len = hypot(w, h) * 0.5f
            val sh = sin(time * 0.35f) * len * 0.5f
            val mx = l + w / 2 + dx * sh
            val my = t + h / 2 + dy * sh
            gp.shader = LinearGradient(mx - dx * len, my - dy * len, mx + dx * len, my + dy * len, cs, null, Shader.TileMode.MIRROR)
            cv.drawRect(l, t, r, b, gp)
            gp.shader = null
            return
        }
        gp.shader = null
        gp.color = cs[1]
        cv.drawRect(l, t, r, b, gp)
        val big = max(w, h)
        val per = 2 * (w + h)
        val n = 4
        for (i in 0 until n) {
            val cx: Float
            val cy: Float
            val col: Int
            val rad: Float
            if (style == 0) {
                val ph = time * 0.18f + i * 1.7f
                cx = l + w * (0.5f + 0.45f * sin(ph * 1.1f + i))
                cy = t + h * (0.5f + 0.45f * cos(ph * 0.9f + i * 2f))
                col = cs[(i * 2) % 3]
                rad = big * 0.75f
            } else {
                val d = ((time * 0.03f + i.toFloat() / n) % 1f) * per
                when {
                    d < w -> { cx = l + d; cy = t }
                    d < w + h -> { cx = r; cy = t + d - w }
                    d < 2 * w + h -> { cx = r - (d - w - h); cy = b }
                    else -> { cx = l; cy = b - (d - 2 * w - h) }
                }
                col = cs[if (i % 2 == 0) 0 else 2]
                rad = big * 0.55f
            }
            gp.shader = RadialGradient(cx, cy, rad,
                intArrayOf(col, (col and 0x00FFFFFF) or 0x66000000, col and 0x00FFFFFF),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            cv.drawRect(l, t, r, b, gp)
        }
        gp.shader = null
    }
}
