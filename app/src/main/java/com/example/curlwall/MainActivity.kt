package com.example.curlwall

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
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

    private fun shape(color: Int, r: Int, stroke: Int = 0, sc: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); if (stroke > 0) setStroke(dp(stroke), sc)
    }

    private fun card(title: String, parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.parseColor("#E6FFFFFF"), 28, 1, Color.parseColor("#14000000"))
            setPadding(dp(18), dp(16), dp(18), dp(18))
            elevation = dp(2).toFloat()
        }
        c.addView(TextView(this).apply {
            text = title; textSize = 13f; setTextColor(Color.parseColor("#8A8A8E")); letterSpacing = 0.04f
            setPadding(0, 0, 0, dp(10))
        })
        parent.addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        return c
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(56), dp(18), dp(40))
        }
        root.addView(TextView(this).apply {
            text = "Curl"; textSize = 36f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#111111"))
        })
        root.addView(TextView(this).apply {
            text = "Живые обои"; textSize = 15f; setTextColor(Color.parseColor("#8A8A8E"))
        })

        preview = object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(cv: Canvas) {
                val bg = prefs.getInt("bg", Color.WHITE)
                val fg = Config.fgFor(bg)
                val size = prefs.getFloat("size", 1f)
                val cover = prefs.getFloat("cover", 0.66f)
                val rows = 5; val cols = 9
                val cw = width / cols.toFloat()
                for (r in 0 until rows) {
                    val k = r / rows.toFloat()
                    val sc = (1f - 0.75f * k * (0.5f + cover)) * size
                    val a = (1f - k * k * 1.1f).coerceIn(0.05f, 1f)
                    p.color = fg; p.alpha = (a * 255).toInt()
                    val y = height - dp(14) - r * cw * (0.8f - 0.25f * k)
                    val h = cw * 0.36f * sc
                    for (i in 0 until cols) {
                        val x = cw * (i + 0.5f)
                        cv.drawRoundRect(x - h, y - h, x + h, y + h, 2f, 2f, p)
                    }
                }
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, dp(170)).apply { topMargin = dp(16) })
        preview.background = shape(prefs.getInt("bg", Color.WHITE), 28, 1, Color.parseColor("#1A000000"))
        preview.clipToOutline = true

        val bgCard = card("ФОН", root)
        val colors = listOf("#FFFFFF", "#000000", "#E5E5EA", "#8E8E93", "#3A3A3C",
            "#FFD6E0", "#FFDAB9", "#FFF3B0", "#C8F2D8", "#C9E6FF", "#E0D4FF", "#F2E4D8")
        val swatches = ArrayList<View>()
        fun refreshSwatches() {
            val cur = prefs.getInt("bg", Color.WHITE)
            swatches.forEach { v ->
                val col = v.tag as Int
                v.background = if (col == cur) shape(col, 22, 3, Color.parseColor("#0A84FF"))
                else shape(col, 22, 1, Color.parseColor("#26000000"))
            }
        }
        colors.chunked(6).forEach { rowColors ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            rowColors.forEach { hex ->
                val col = Color.parseColor(hex)
                val v = View(this).apply { tag = col }
                v.setOnClickListener {
                    prefs.edit().putInt("bg", col).apply()
                    preview.background = shape(col, 28, 1, Color.parseColor("#1A000000"))
                    preview.invalidate(); refreshSwatches()
                }
                swatches.add(v)
                row.addView(v, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
            }
            bgCard.addView(row)
        }
        refreshSwatches()

        val animCard = card("АНИМАЦИЯ", root)
        val names = listOf("Волна", "Дыхание", "Дрейф", "Поток")
        val seg = LinearLayout(this).apply {
            background = shape(Color.parseColor("#1F767680"), 16); setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val tabs = ArrayList<TextView>()
        fun refreshTabs() {
            val cur = prefs.getInt("anim", 0)
            tabs.forEachIndexed { i, t ->
                t.background = if (i == cur) shape(Color.WHITE, 13) else null
                t.setTextColor(if (i == cur) Color.BLACK else Color.parseColor("#555555"))
                t.elevation = if (i == cur) dp(2).toFloat() else 0f
            }
        }
        names.forEachIndexed { i, n ->
            val t = TextView(this).apply {
                text = n; textSize = 13f; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { prefs.edit().putInt("anim", i).apply(); refreshTabs() }
            }
            tabs.add(t); seg.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        animCard.addView(seg); refreshTabs()

        val setCard = card("ПАРАМЕТРЫ", root)
        fun slider(label: String, key: String, min: Float, max: Float, def: Float, fmt: (Float) -> String) {
            val head = LinearLayout(this)
            val l = TextView(this).apply { text = label; textSize = 16f; setTextColor(Color.parseColor("#111111")) }
            val vTxt = TextView(this).apply { textSize = 15f; setTextColor(Color.parseColor("#8A8A8E")) }
            head.addView(l, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); head.addView(vTxt)
            val cur = prefs.getFloat(key, def); vTxt.text = fmt(cur)
            val sb = SeekBar(this).apply {
                this.max = 100; progress = ((cur - min) / (max - min) * 100).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0A84FF"))
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                        val v = min + (max - min) * p / 100f
                        prefs.edit().putFloat(key, v).apply(); vTxt.text = fmt(v); preview.invalidate()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            setCard.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            setCard.addView(sb)
        }
        slider("Высота загиба", "cover", 0.4f, 0.95f, 0.66f) { "${(it * 100).toInt()}%" }
        slider("Скорость", "speed", 0.3f, 2.5f, 1f) { "×" + "%.1f".format(it) }
        slider("Размер квадратов", "size", 0.7f, 1.3f, 1f) { "${(it * 100).toInt()}%" }

        fun toggle(label: String, key: String, def: Boolean) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = label; textSize = 16f; setTextColor(Color.parseColor("#111111")) },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(Switch(this).apply {
                isChecked = prefs.getBoolean(key, def)
                setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(key, c).apply() }
            })
            setCard.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        }
        toggle("Наклон телефона", "tilt", true)
        toggle("Плавность 60 FPS", "fps60", false)

        root.addView(Button(this).apply {
            text = "Установить обои"; isAllCaps = false; textSize = 17f; setTextColor(Color.WHITE)
            background = shape(Color.parseColor("#111111"), 30)
            setOnClickListener {
                try {
                    startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, CurlWallpaperService::class.java)))
                } catch (e: Exception) {
                    startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                }
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(58)).apply { topMargin = dp(22) })

        setContentView(ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#F2F2F7"), Color.parseColor("#E4E8F5")))
            addView(root)
        })
    }
}
