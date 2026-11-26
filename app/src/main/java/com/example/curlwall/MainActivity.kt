package com.example.curlwall

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs by lazy { Config.prefs(this) }
    private lateinit var preview: View
    private val accent = Color.parseColor("#0A84FF")
    private val ink = Color.parseColor("#111114")
    private val sub = Color.parseColor("#6E6E73")

    private fun shape(color: Int, r: Int, stroke: Int = 0, sc: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); if (stroke > 0) setStroke(dp(stroke), sc)
    }

    private fun glass(r: Int = 32) = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xD9FFFFFF.toInt(), 0x80FFFFFF.toInt())).apply {
        cornerRadius = dp(r).toFloat(); setStroke(dp(1), 0xCCFFFFFF.toInt())
    }

    private fun blob(color: Int, size: Int, x: Int, y: Int) = View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, color and 0x00FFFFFF)).apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = dp(size) / 2f
        }
        layoutParams = FrameLayout.LayoutParams(dp(size), dp(size)).apply { leftMargin = dp(x); topMargin = dp(y) }
    }

    private fun card(title: String, parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = glass()
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        c.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(sub); letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(dp(2), 0, 0, dp(12))
        })
        parent.addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
        return c
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(60), dp(18), dp(48))
        }
        root.addView(TextView(this).apply {
            text = "Lucidream"; textSize = 38f; setTextColor(ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); letterSpacing = -0.02f
        })
        root.addView(TextView(this).apply { text = "Живые обои"; textSize = 16f; setTextColor(sub) })

        preview = object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(cv: Canvas) {
                val fg = Config.fgFor(prefs.getInt("bg", Color.WHITE))
                val size = prefs.getFloat("size", 1f); val cover = prefs.getFloat("cover", 0.66f)
                val rows = 5; val cols = 9; val cw = width / cols.toFloat()
                for (r in 0 until rows) {
                    val k = r / rows.toFloat()
                    val sc = (1f - 0.75f * k * (0.5f + cover)) * size
                    p.color = fg; p.alpha = ((1f - k * k * 1.1f).coerceIn(0.05f, 1f) * 255).toInt()
                    val y = height - dp(16) - r * cw * (0.8f - 0.25f * k)
                    val h = cw * 0.36f * sc
                    for (i in 0 until cols) { val x = cw * (i + 0.5f); cv.drawRoundRect(x - h, y - h, x + h, y + h, 3f, 3f, p) }
                }
            }
        }
        preview.background = shape(prefs.getInt("bg", Color.WHITE), 32, 2, 0xCCFFFFFF.toInt())
        root.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, dp(180)).apply { topMargin = dp(18) })

        // background colours
        val bgCard = card("ФОН", root)
        val colors = listOf(
            "#FFFFFF", "#F2F2F7", "#D1D1D6", "#8E8E93", "#3A3A3C", "#000000",
            "#FFD6E0", "#FFC8DD", "#FFDAB9", "#FFE5B4", "#FFF3B0", "#F2E4D8",
            "#C8F2D8", "#B8F0E0", "#C9E6FF", "#BDD7FF", "#E0D4FF", "#F0C8FF",
            "#1B2A41", "#2D1B3D", "#143D33", "#4A1D2A", "#0F3D4A", "#3B3A1C")
        val swatches = ArrayList<View>()
        fun refresh() {
            val cur = prefs.getInt("bg", Color.WHITE)
            swatches.forEach { v ->
                val col = v.tag as Int
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(col)
                    if (col == cur) setStroke(dp(3), accent) else setStroke(dp(1), 0x33000000)
                }
                v.scaleX = if (col == cur) 1.08f else 1f; v.scaleY = v.scaleX
            }
        }
        colors.chunked(6).forEach { rc ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            rc.forEach { hex ->
                val col = Color.parseColor(hex)
                val v = View(this).apply { tag = col }
                v.setOnClickListener {
                    prefs.edit().putInt("bg", col).apply()
                    preview.background = shape(col, 32, 2, 0xCCFFFFFF.toInt()); preview.invalidate(); refresh()
                }
                swatches.add(v)
                row.addView(FrameLayout(this).apply { addView(v, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER)) },
                    LinearLayout.LayoutParams(0, dp(52), 1f))
            }
            bgCard.addView(row)
        }
        refresh()

        // animation segmented
        val animCard = card("АНИМАЦИЯ", root)
        val seg = LinearLayout(this).apply { background = shape(0x1F767680, 20); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        val tabs = ArrayList<TextView>()
        fun refreshTabs() {
            val cur = prefs.getInt("anim", 0)
            tabs.forEachIndexed { i, t ->
                t.background = if (i == cur) shape(Color.WHITE, 16, 1, 0x14000000) else null
                t.setTextColor(if (i == cur) ink else sub)
            }
        }
        listOf("Волна", "Дыхание", "Дрейф", "Поток").forEachIndexed { i, n ->
            val t = TextView(this).apply {
                text = n; textSize = 14f; gravity = Gravity.CENTER; setPadding(0, dp(11), 0, dp(11))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setOnClickListener { prefs.edit().putInt("anim", i).apply(); refreshTabs() }
            }
            tabs.add(t); seg.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        animCard.addView(seg); refreshTabs()

        // sliders / toggles
        val sc = card("ПАРАМЕТРЫ", root)
        fun slider(label: String, key: String, mn: Float, mx: Float, def: Float, fmt: (Float) -> String) {
            val head = LinearLayout(this)
            val vt = TextView(this).apply { textSize = 15f; setTextColor(sub) }
            head.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(ink) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            head.addView(vt)
            val cur = prefs.getFloat(key, def); vt.text = fmt(cur)
            val track = shape(0x26000000, 8).apply { setSize(0, dp(14)) }
            val fill = ClipDrawable(shape(accent, 8).apply { setSize(0, dp(14)) }, Gravity.START, ClipDrawable.HORIZONTAL)
            val sb = SeekBar(this).apply {
                max = 1000; progress = ((cur - mn) / (mx - mn) * 1000).toInt()
                progressDrawable = LayerDrawable(arrayOf(track, fill)).apply {
                    setId(0, android.R.id.background); setId(1, android.R.id.progress) }
                thumb = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dp(1), 0x33000000); setSize(dp(32), dp(32)) }
                splitTrack = false; thumbOffset = 0; setPadding(dp(16), 0, dp(16), 0)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                        val v = mn + (mx - mn) * p / 1000f
                        prefs.edit().putFloat(key, v).apply(); vt.text = fmt(v); preview.invalidate()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            sc.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
            sc.addView(sb, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        }
        slider("Высота загиба", "cover", 0.4f, 0.95f, 0.66f) { "${(it * 100).toInt()}%" }
        slider("Скорость", "speed", 0.3f, 2.5f, 1f) { "×" + "%.1f".format(it) }
        slider("Размер квадратов", "size", 0.7f, 1.3f, 1f) { "${(it * 100).toInt()}%" }

        fun toggle(label: String, key: String, def: Boolean) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(ink) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(Switch(this).apply {
                isChecked = prefs.getBoolean(key, def)
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.parseColor("#34C759"), 0x33000000))
                setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(key, c).apply() }
            })
            sc.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)))
        }
        toggle("Улёт при разблокировке", "unlock", true)
        toggle("Наклон телефона", "tilt", true)
        toggle("Плавность 60 FPS", "fps60", false)

        root.addView(TextView(this).apply {
            text = "Установить обои"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#2C2C2E"), Color.parseColor("#000000"))).apply { cornerRadius = dp(30).toFloat() }
            isClickable = true
            setOnClickListener {
                try {
                    startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, CurlWallpaperService::class.java)))
                } catch (e: Exception) { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(60)).apply { topMargin = dp(24) })

        setContentView(FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#EEF2FF"), Color.parseColor("#FBEFFA"), Color.parseColor("#E8F6F0")))
            addView(blob(0x99A8C8FF.toInt(), 380, -120, 40))
            addView(blob(0x99F7B8E0.toInt(), 360, 160, 380))
            addView(blob(0x99B5F0D8.toInt(), 340, -100, 760))
            addView(ScrollView(this@MainActivity).apply { isVerticalScrollBarEnabled = false; addView(root) })
        })
    }
}
