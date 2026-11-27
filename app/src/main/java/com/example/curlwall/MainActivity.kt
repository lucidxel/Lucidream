package com.example.curlwall

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

/** Liquid-glass surface: translucent fill + thin outline bright at top and bottom edges. */
class Glass(private val r: Float, private val dark: Boolean, private val strong: Boolean = false) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    override fun draw(c: Canvas) {
        val b = RectF(bounds).apply { inset(1.5f, 1.5f) }
        fill.color = if (dark) (if (strong) 0x40FFFFFF else 0x1FFFFFFF) else (if (strong) 0xFFFFFFFF.toInt() else 0xB8FFFFFF.toInt())
        c.drawRoundRect(b, r, r, fill)
        val top = if (dark) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt()
        val bot = if (dark) 0x40FFFFFF else 0x30000000
        line.shader = LinearGradient(0f, b.top, 0f, b.bottom, intArrayOf(top, 0x00FFFFFF, 0x00FFFFFF, bot),
            floatArrayOf(0f, 0.28f, 0.72f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(b, r, r, line)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("x") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs by lazy { Config.prefs(this) }
    private val dark by lazy { (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES }
    private val accent = Color.parseColor("#0A84FF")
    private val ink by lazy { if (dark) Color.WHITE else Color.parseColor("#111114") }
    private val sub by lazy { if (dark) Color.parseColor("#9A9AA2") else Color.parseColor("#6E6E73") }
    private lateinit var preview: View

    private fun bgDraw(c1: Int, c2: Int, r: Int) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c1, c2))
        .apply { cornerRadius = dp(r).toFloat() }

    private fun pair(s: String): Pair<Int, Int> {
        val p = s.split("-"); val a = Color.parseColor("#" + p[0]); return a to (if (p.size > 1) Color.parseColor("#" + p[1]) else a)
    }

    private fun card(title: String, parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = Glass(dp(32).toFloat(), dark)
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        c.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(sub); letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(dp(2), 0, 0, dp(10))
        })
        parent.addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
        return c
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(64), dp(18), dp(48))
        }
        root.addView(TextView(this).apply {
            text = "Lucidream"; textSize = 36f; setTextColor(ink); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "Live Wallpaper"; textSize = 14f; setTextColor(sub); gravity = Gravity.CENTER; letterSpacing = 0.05f
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        fun curBg() = prefs.getInt("bg", Color.WHITE).let { it to prefs.getInt("bg2", it) }
        preview = object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(cv: Canvas) {
                val (b1, b2) = curBg()
                val fg = Config.fgFor(b1, b2)
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
        fun setPreviewBg() {
            val (a, b) = curBg()
            preview.background = LayerDrawable(arrayOf(bgDraw(a, b, 32), Glass(dp(32).toFloat(), true)))
            preview.invalidate()
        }
        setPreviewBg()
        root.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, dp(180)).apply { topMargin = dp(22) })

        // ---- colours
        val bgCard = card("BACKGROUND", root)
        val swatches = ArrayList<View>()
        fun refresh() {
            val (a, b) = curBg()
            swatches.forEach { v ->
                val (x, y) = v.tag as Pair<Int, Int>
                val sel = x == a && y == b
                v.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(x, y)).apply {
                    shape = GradientDrawable.OVAL
                    if (sel) setStroke(dp(3), accent) else setStroke(dp(1), if (dark) 0x44FFFFFF else 0x33000000)
                }
                v.scaleX = if (sel) 1.1f else 1f; v.scaleY = v.scaleX
            }
        }
        fun section(name: String, items: String) {
            bgCard.addView(TextView(this).apply { text = name; textSize = 13f; setTextColor(sub); setPadding(dp(2), dp(10), 0, dp(4)) })
            items.split(" ").chunked(6).forEach { rc ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
                rc.forEach { s ->
                    val pr = pair(s)
                    val v = View(this).apply { tag = pr }
                    v.setOnClickListener {
                        prefs.edit().putInt("bg", pr.first).putInt("bg2", pr.second).apply(); setPreviewBg(); refresh()
                    }
                    swatches.add(v)
                    row.addView(FrameLayout(this).apply { addView(v, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER)) },
                        LinearLayout.LayoutParams(0, dp(52), 1f))
                }
                repeat(6 - rc.size) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(52), 1f)) }
                bgCard.addView(row)
            }
        }
        section("Neutrals", "FFFFFF F2F2F7 D1D1D6 8E8E93 3A3A3C 000000")
        section("Pastel", "FFD6E0 FFC8DD FFDAB9 FFE5B4 FFF3B0 F2E4D8 C8F2D8 B8F0E0 C9E6FF BDD7FF E0D4FF F0C8FF")
        section("Vivid", "0A84FF 5E5CE6 BF5AF2 FF375F FF453A FF9F0A FFD60A 30D158 64D2FF 63E6E2 AC8E68 FF6482")
        section("Deep", "1B2A41 2D1B3D 143D33 4A1D2A 0F3D4A 3B3A1C")
        section("Gradients", "FF9A8B-FF6A88 2193B0-6DD5ED 00C9A7-845EC2 FFECD2-FCB69F E0C3FC-8EC5FC 0F2027-2C5364 " +
            "41295A-2F0743 D4FC79-96E6A1 F6D365-FDA085 8E2DE2-4A00E0 232526-414345 E0EAFC-CFDEF3")
        refresh()

        // ---- animation styles (two rows of four)
        val animCard = card("MOTION", root)
        val tabs = ArrayList<TextView>()
        fun refreshTabs() {
            val cur = prefs.getInt("anim", 0)
            tabs.forEachIndexed { i, t ->
                t.background = if (i == cur) Glass(dp(16).toFloat(), dark, true) else null
                t.setTextColor(if (i == cur) (if (dark) Color.WHITE else ink) else sub)
            }
        }
        listOf(listOf("Wave", "Breathe", "Drift", "Flow"), listOf("Ripple", "Sway", "Cascade", "Orbit")).forEachIndexed { ri, names ->
            val seg = LinearLayout(this).apply {
                background = GradientDrawable().apply { setColor(if (dark) 0x26FFFFFF else 0x14000000); cornerRadius = dp(20).toFloat() }
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            names.forEachIndexed { k, n ->
                val idx = ri * 4 + k
                val t = TextView(this).apply {
                    text = n; textSize = 14f; gravity = Gravity.CENTER; setPadding(0, dp(11), 0, dp(11))
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setOnClickListener { prefs.edit().putInt("anim", idx).apply(); refreshTabs() }
                }
                tabs.add(t); seg.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            }
            animCard.addView(seg, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { if (ri > 0) topMargin = dp(8) })
        }
        refreshTabs()

        // ---- settings
        val sc = card("SETTINGS", root)
        fun slider(label: String, key: String, mn: Float, mx: Float, def: Float, fmt: (Float) -> String) {
            val head = LinearLayout(this)
            val vt = TextView(this).apply { textSize = 15f; setTextColor(sub) }
            head.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(ink) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            head.addView(vt)
            val cur = prefs.getFloat(key, def); vt.text = fmt(cur)
            val track = GradientDrawable().apply { setColor(if (dark) 0x33FFFFFF else 0x22000000); cornerRadius = dp(10).toFloat(); setSize(0, dp(16)) }
            val fillD = ClipDrawable(GradientDrawable().apply { setColor(accent); cornerRadius = dp(10).toFloat(); setSize(0, dp(16)) },
                Gravity.START, ClipDrawable.HORIZONTAL)
            val sb = SeekBar(this).apply {
                max = 1000; progress = ((cur - mn) / (mx - mn) * 1000).toInt()
                progressDrawable = LayerDrawable(arrayOf(track, fillD)).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
                thumb = LayerDrawable(arrayOf(
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE); setSize(dp(36), dp(36)) },
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(1), 0x33000000); setSize(dp(36), dp(36)) }))
                splitTrack = false; thumbOffset = 0; setPadding(dp(18), 0, dp(18), 0)
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
            sc.addView(sb, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)))
        }
        slider("Curl height", "cover", 0.4f, 0.95f, 0.66f) { "${(it * 100).toInt()}%" }
        slider("Speed", "speed", 0.3f, 2.5f, 1f) { "×" + "%.1f".format(it) }
        slider("Square size", "size", 0.7f, 1.3f, 1f) { "${(it * 100).toInt()}%" }
        slider("Edge blur", "blur", 0f, 1f, 0.35f) { if (it < 0.02f) "Off" else "${(it * 100).toInt()}%" }

        fun toggle(label: String, key: String, def: Boolean) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(ink) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(Switch(this).apply {
                isChecked = prefs.getBoolean(key, def)
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.parseColor("#34C759"), if (dark) 0x44FFFFFF else 0x33000000))
                setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(key, c).apply() }
            })
            sc.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, dp(54)))
        }
        toggle("Unlock fly-away", "unlock", true)
        toggle("Tilt parallax", "tilt", true)
        toggle("Smooth 60 FPS", "fps60", false)

        root.addView(TextView(this).apply {
            text = "Set wallpaper"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = LayerDrawable(arrayOf(bgDraw(Color.parseColor("#2A8CFF"), Color.parseColor("#0A5FE0"), 30), Glass(dp(30).toFloat(), true)))
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
            setBackgroundColor(if (dark) Color.parseColor("#0B0B0F") else Color.parseColor("#EDEDF2"))
            addView(ScrollView(this@MainActivity).apply { isVerticalScrollBarEnabled = false; addView(root) })
        })
    }
}
