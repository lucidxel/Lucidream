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

class Glass(private val r: Float, private val dark: Boolean, private val strong: Boolean = false) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    override fun draw(c: Canvas) {
        val b = RectF(bounds).apply { inset(1.5f, 1.5f) }
        fill.color = if (dark) (if (strong) 0x52FFFFFF else 0x1FFFFFFF) else (if (strong) 0xFFFFFFFF.toInt() else 0xB8FFFFFF.toInt())
        c.drawRoundRect(b, r, r, fill)
        val top = if (dark) 0x99FFFFFF.toInt() else 0xFFFFFFFF.toInt()
        val bot = if (dark) 0x44FFFFFF else 0x2E000000
        line.shader = LinearGradient(0f, b.top, 0f, b.bottom, intArrayOf(top, 0x00FFFFFF, 0x00FFFFFF, bot),
            floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
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
    private val hair by lazy { if (dark) 0x22FFFFFF else 0x14000000 }
    private lateinit var preview: View
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun gd(c1: Int, c2: Int, r: Int) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c1, c2))
        .apply { cornerRadius = dp(r).toFloat() }

    private fun pair(s: String): Pair<Int, Int> {
        val p = s.split("-"); val a = Color.parseColor("#" + p[0]); return a to (if (p.size > 1) Color.parseColor("#" + p[1]) else a)
    }

    private fun header(t: String, parent: LinearLayout) = parent.addView(TextView(this).apply {
        text = t.uppercase(); textSize = 12f; setTextColor(sub); letterSpacing = 0.08f; typeface = medium
        setPadding(dp(16), dp(26), 0, dp(8))
    })

    private fun group(parent: LinearLayout): LinearLayout {
        val g = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = Glass(dp(30).toFloat(), dark)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        parent.addView(g, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)); return g
    }

    private fun divider(p: LinearLayout) = p.addView(View(this).apply { setBackgroundColor(hair) },
        LinearLayout.LayoutParams(MATCH_PARENT, 2))

    private fun segmented(parent: LinearLayout, rows: List<List<String>>, key: String) {
        val tabs = ArrayList<TextView>()
        fun refresh() {
            val cur = prefs.getInt(key, 0)
            tabs.forEachIndexed { i, t ->
                t.background = if (i == cur) Glass(dp(15).toFloat(), dark, true) else null
                t.setTextColor(if (i == cur) (if (dark) Color.WHITE else ink) else sub)
            }
        }
        val per = rows[0].size
        rows.forEachIndexed { ri, names ->
            val seg = LinearLayout(this).apply {
                background = GradientDrawable().apply { setColor(if (dark) 0x26FFFFFF else 0x12000000); cornerRadius = dp(19).toFloat() }
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            names.forEachIndexed { k, n ->
                val idx = ri * per + k
                val t = TextView(this).apply {
                    text = n; textSize = 14f; gravity = Gravity.CENTER; setPadding(0, dp(11), 0, dp(11)); typeface = medium
                    setOnClickListener { prefs.edit().putInt(key, idx).apply(); refresh(); preview.invalidate() }
                }
                tabs.add(t); seg.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            }
            parent.addView(seg, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(if (ri == 0) 10 else 6); bottomMargin = dp(8) })
        }
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(64), dp(16), dp(130))
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
                p.color = Config.fgFor(b1, b2)
                val size = prefs.getFloat("size", 1f); val cover = prefs.getFloat("cover", 0.66f).coerceAtMost(1f)
                val rf = floatArrayOf(0f, 0.2f, 0.38f, 0.5f)[prefs.getInt("round", 0).coerceIn(0, 3)]
                val rows = 5; val cols = 9; val cw = width / cols.toFloat()
                for (r in 0 until rows) {
                    val k = r / rows.toFloat()
                    val sc = (1f - 0.75f * k * (0.5f + cover)) * size
                    p.alpha = ((1f - k * k * 1.1f).coerceIn(0.05f, 1f) * 255).toInt()
                    val y = height - dp(18) - r * cw * (0.8f - 0.25f * k)
                    val h = cw * 0.36f * sc
                    for (i in 0 until cols) { val x = cw * (i + 0.5f); cv.drawRoundRect(x - h, y - h, x + h, y + h, rf * 2 * h, rf * 2 * h, p) }
                }
            }
        }
        fun setPreviewBg() {
            val (a, b) = curBg()
            preview.background = LayerDrawable(arrayOf(gd(a, b, 34), Glass(dp(34).toFloat(), true)))
            preview.invalidate()
        }
        setPreviewBg()
        root.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, dp(190)).apply { topMargin = dp(22) })

        // ---------- colours
        header("Background", root)
        val cg = group(root)
        val swatches = ArrayList<View>()
        fun refresh() {
            val (a, b) = curBg()
            swatches.forEach { v ->
                val (x, y) = v.tag as Pair<Int, Int>
                val sel = x == a && y == b
                v.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(x, y)).apply {
                    shape = GradientDrawable.OVAL
                    if (sel) setStroke(dp(3), accent) else setStroke(dp(1), if (dark) 0x44FFFFFF else 0x2E000000)
                }
                v.scaleX = if (sel) 1.1f else 1f; v.scaleY = v.scaleX
            }
        }
        fun section(name: String, items: String, last: Boolean = false) {
            cg.addView(TextView(this).apply { text = name; textSize = 13f; setTextColor(sub); typeface = medium; setPadding(dp(2), dp(10), 0, dp(2)) })
            val row = LinearLayout(this).apply { setPadding(0, dp(4), 0, dp(10)) }
            items.split(" ").forEach { s ->
                val pr = pair(s)
                val v = View(this).apply { tag = pr }
                v.setOnClickListener { prefs.edit().putInt("bg", pr.first).putInt("bg2", pr.second).apply(); setPreviewBg(); refresh() }
                swatches.add(v)
                row.addView(FrameLayout(this).apply { addView(v, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER)) },
                    LinearLayout.LayoutParams(dp(54), dp(54)))
            }
            cg.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
            if (!last) divider(cg)
        }
        section("Neutrals", "FFFFFF F2F2F7 D1D1D6 8E8E93 3A3A3C 000000")
        section("Pastel", "FFD6E0 FFC8DD FFDAB9 FFE5B4 FFF3B0 F2E4D8 C8F2D8 B8F0E0 C9E6FF BDD7FF E0D4FF F0C8FF")
        section("Vivid", "0A84FF 5E5CE6 BF5AF2 FF375F FF453A FF9F0A FFD60A 30D158 64D2FF 63E6E2 AC8E68 FF6482")
        section("Deep", "1B2A41 2D1B3D 143D33 4A1D2A 0F3D4A 3B3A1C")
        section("Gradients", "FF9A8B-FF6A88 2193B0-6DD5ED 00C9A7-845EC2 FFECD2-FCB69F E0C3FC-8EC5FC 0F2027-2C5364 " +
            "41295A-2F0743 D4FC79-96E6A1 F6D365-FDA085 8E2DE2-4A00E0 232526-414345 E0EAFC-CFDEF3", true)
        refresh()

        // ---------- motion / shape / unlock
        header("Motion", root)
        segmented(group(root), listOf(listOf("Wave", "Breathe", "Drift", "Flow"), listOf("Ripple", "Sway", "Cascade", "Orbit")), "anim")
        header("Corners", root)
        segmented(group(root), listOf(listOf("Sharp", "Soft", "Round", "Circle")), "round")
        header("Unlock animation", root)
        segmented(group(root), listOf(listOf("Down", "Up", "Zoom", "Dissolve")), "uanim")

        // ---------- settings
        header("Settings", root)
        val sg = group(root)
        fun slider(label: String, key: String, mn: Float, mx: Float, def: Float, fmt: (Float) -> String) {
            val head = LinearLayout(this)
            val vt = TextView(this).apply { textSize = 15f; setTextColor(sub) }
            head.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(ink) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            head.addView(vt)
            val cur = prefs.getFloat(key, def); vt.text = fmt(cur)
            val track = GradientDrawable().apply { setColor(if (dark) 0x33FFFFFF else 0x22000000); cornerRadius = dp(10).toFloat(); setSize(0, dp(16)) }
            val fillD = ClipDrawable(GradientDrawable().apply { setColor(accent); cornerRadius = dp(10).toFloat(); setSize(0, dp(16)) }, Gravity.START, ClipDrawable.HORIZONTAL)
            val sb = SeekBar(this).apply {
                max = 1000; progress = ((cur - mn) / (mx - mn) * 1000).toInt()
                progressDrawable = LayerDrawable(arrayOf(track, fillD)).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
                thumb = LayerDrawable(arrayOf(
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x1A000000); setSize(dp(42), dp(42)) },
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE); setSize(dp(42), dp(42)) }
                )).apply { setLayerInset(1, dp(4), dp(4), dp(4), dp(4)) }
                splitTrack = false; thumbOffset = 0; setPadding(dp(21), 0, dp(21), 0)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                        val v = mn + (mx - mn) * p / 1000f
                        prefs.edit().putFloat(key, v).apply(); vt.text = fmt(v); preview.invalidate()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            sg.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
            sg.addView(sb, LinearLayout.LayoutParams(MATCH_PARENT, dp(54)))
        }
        slider("Curl height", "cover", 0.4f, 1.8f, 0.66f) { "${(it * 100).toInt()}%" }
        slider("Speed", "speed", 0.3f, 2.5f, 1f) { "×" + "%.1f".format(it) }
        slider("Square size", "size", 0.7f, 1.3f, 1f) { "${(it * 100).toInt()}%" }
        slider("Edge blur", "blur", 0f, 1f, 0.35f) { if (it < 0.02f) "Off" else "${(it * 100).toInt()}%" }

        fun toggle(label: String, key: String, def: Boolean) {
            divider(sg)
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(ink) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(Switch(this).apply {
                isChecked = prefs.getBoolean(key, def)
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.parseColor("#34C759"), if (dark) 0x44FFFFFF else 0x33000000))
                setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(key, c).apply() }
            })
            sg.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
        }
        toggle("Unlock animation", "unlock", true)
        toggle("Tilt parallax", "tilt", true)
        toggle("Smooth 60 FPS", "fps60", false)

        val btn = TextView(this).apply {
            text = "Set wallpaper"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = medium
            background = LayerDrawable(arrayOf(gd(Color.parseColor("#2A8CFF"), Color.parseColor("#0A5FE0"), 30), Glass(dp(30).toFloat(), true)))
            isClickable = true
            setOnClickListener {
                try {
                    startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, CurlWallpaperService::class.java)))
                } catch (e: Exception) { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            }
        }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(if (dark) Color.parseColor("#0B0B0F") else Color.parseColor("#EDEDF2"))
            addView(ScrollView(this@MainActivity).apply { isVerticalScrollBarEnabled = false; addView(root) })
            addView(btn, FrameLayout.LayoutParams(MATCH_PARENT, dp(58), Gravity.BOTTOM).apply { setMargins(dp(20), 0, dp(20), dp(28)) })
        })
    }
}
