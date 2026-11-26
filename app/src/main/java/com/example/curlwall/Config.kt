package com.example.curlwall

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

enum class WStyle(val label: String) {
    CURL("Curl"), FLIP("Flip"), RIPPLE("Ripple"), CASCADE("Cascade"),
    SPIRAL("Spiral"), DISSOLVE("Dissolve"), IRIS("Iris"), WAVE("Wave")
}

class Palette(val name: String, val c1: Int, val c2: Int, val c3: Int, val accent: Int)

object Palettes {
    val all = listOf(
        Palette("Aurora", Color.parseColor("#6C5CE7"), Color.parseColor("#00CEC9"), Color.parseColor("#55EFC4"), Color.parseColor("#6C5CE7")),
        Palette("Sunset", Color.parseColor("#FF7E5F"), Color.parseColor("#FD3A84"), Color.parseColor("#FEB47B"), Color.parseColor("#FF5E62")),
        Palette("Ocean", Color.parseColor("#1E3C72"), Color.parseColor("#2193B0"), Color.parseColor("#6DD5ED"), Color.parseColor("#1CB5E0")),
        Palette("Mint", Color.parseColor("#11998E"), Color.parseColor("#38EF7D"), Color.parseColor("#B2FEFA"), Color.parseColor("#11C28A")),
        Palette("Grape", Color.parseColor("#4A00E0"), Color.parseColor("#8E2DE2"), Color.parseColor("#FF6FD8"), Color.parseColor("#8E2DE2")),
        Palette("Rose", Color.parseColor("#FF9A9E"), Color.parseColor("#FAD0C4"), Color.parseColor("#FBC2EB"), Color.parseColor("#FF6B8B")),
        Palette("Citrus", Color.parseColor("#FF512F"), Color.parseColor("#F7971E"), Color.parseColor("#FFD200"), Color.parseColor("#FF9F1C")),
        Palette("Mono", Color.parseColor("#232526"), Color.parseColor("#414345"), Color.parseColor("#BDC3C7"), Color.parseColor("#7A8499")),
        Palette("Neon", Color.parseColor("#3333FF"), Color.parseColor("#FF00CC"), Color.parseColor("#00F0FF"), Color.parseColor("#FF2BD6")),
        Palette("Forest", Color.parseColor("#0B3D2E"), Color.parseColor("#134E5E"), Color.parseColor("#71B280"), Color.parseColor("#2E9E6A"))
    )
}

object Config {
    const val PREFS = "lucidream"
    const val K_STYLE = "style"
    const val K_PALETTE = "palette"
    const val K_COLS = "cols"
    const val K_SPEED = "speed"
    const val K_BLUR = "blurOn"
    const val K_BLURAMT = "blurAmt"
    const val K_GAP = "gap"
    const val K_RADIUS = "radius"
    const val K_UNLOCK = "unlock"
    const val K_TOUCH = "touch"
    const val K_SHEEN = "sheen"
    const val K_EDGE = "edge"
    const val K_PHOTO = "photo"
    const val K_PHOTOVER = "photoVer"
    const val K_REPLAY = "replay"

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    class Snap(p: SharedPreferences) {
        val style: WStyle = WStyle.values()[p.getInt(K_STYLE, 0).coerceIn(0, WStyle.values().size - 1)]
        val palette: Int = p.getInt(K_PALETTE, 0).coerceIn(0, Palettes.all.size - 1)
        val cols: Int = p.getInt(K_COLS, 10)
        val speed: Int = p.getInt(K_SPEED, 100).coerceAtLeast(30)
        val blurOn: Boolean = p.getBoolean(K_BLUR, true)
        val blurAmt: Int = p.getInt(K_BLURAMT, 70)
        val gap: Int = p.getInt(K_GAP, 2)
        val radius: Int = p.getInt(K_RADIUS, 20)
        val unlock: Boolean = p.getBoolean(K_UNLOCK, true)
        val touch: Boolean = p.getBoolean(K_TOUCH, true)
        val sheen: Boolean = p.getBoolean(K_SHEEN, true)
        val edge: Boolean = p.getBoolean(K_EDGE, true)
        val photo: Boolean = p.getBoolean(K_PHOTO, false)
    }
}
