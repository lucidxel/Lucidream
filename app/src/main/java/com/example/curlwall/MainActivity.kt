package com.example.curlwall

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.*
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import kotlin.math.sin

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

/** Material 3 Expressive style slider: thick segmented track with a vertical bar handle. */
class M3Slider(ctx: Context, private val accent: Int, private val trackBg: Int) : View(ctx) {
    var progress = 0f
    var onChange: (Float) -> Unit = {}
    private var pressedNow = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    private fun seg(cv: Canvas, l: Float, t: Float, r: Float, b: Float, rl: Float, rr: Float) {
        if (r - l < 1f) return
        val path = Path()
        path.addRoundRect(RectF(l, t, r, b), floatArrayOf(rl, rl, rr, rr, rr, rr, rl, rl), Path.Direction.CW)
        cv.drawPath(path, p)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val cy = height / 2f
        val th = 20 * d
        val hw = (if (pressedNow) 3f else 5f) * d
        val hh = 44 * d
        val gap = 6 * d
        val x0 = 4 * d
        val x1 = w - 4 * d
        val hx = x0 + (x1 - x0) * progress
        val full = th / 2f
        val small = 3 * d
        p.color = accent
        seg(cv, x0, cy - th / 2, hx - hw / 2 - gap, cy + th / 2, full, small)
        p.color = trackBg
        seg(cv, hx + hw / 2 + gap, cy - th / 2, x1, cy + th / 2, small, full)
        if (x1 - (hx + hw / 2 + gap) > th * 1.6f) {
            p.color = accent
            cv.drawCircle(x1 - th / 2, cy, 2.5f * d, p)
        }
        p.color = accent
        cv.drawRoundRect(hx - hw / 2, cy - hh / 2, hx + hw / 2, cy + hh / 2, hw / 2, hw / 2, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pressedNow = true
                val x0 = 4 * d
                val x1 = width - 4 * d
                progress = ((e.x - x0) / (x1 - x0)).coerceIn(0f, 1f)
                onChange(progress)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressedNow = false; invalidate()
                if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}

/** Little pixel mascot for the easter egg. */
class Clawd(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val u = 8f * ctx.resources.displayMetrics.density
    private val body = listOf(intArrayOf(2, 0, 9, 4), intArrayOf(0, 1, 2, 2), intArrayOf(11, 1, 2, 2),
        intArrayOf(3, 4, 1, 2), intArrayOf(5, 4, 1, 2), intArrayOf(7, 4, 1, 2), intArrayOf(9, 4, 1, 2))
    override fun onDraw(cv: Canvas) {
        val t = SystemClock.uptimeMillis() / 1000.0
        val ox = (width - 13 * u) / 2f
        val oy = (height - 6 * u) / 2f + (sin(t * 2.2) * u * 0.25).toFloat()
        p.color = Color.parseColor("#D97757")
        for (r in body) cv.drawRect(ox + r[0] * u, oy + r[1] * u, ox + (r[0] + r[2]) * u, oy + (r[1] + r[3]) * u, p)
        val blink = (t % 4.0) < 0.12
        p.color = Color.parseColor("#1A1A1A")
        val eh = if (blink) 0.4f else 2f
        for (x in intArrayOf(4, 8)) cv.drawRect(ox + x * u, oy + 1 * u + (2f - eh) * u / 2f, ox + (x + 1) * u, oy + 1 * u + (2f - eh) * u / 2f + eh * u, p)
        postInvalidateOnAnimation()
    }
}

private class Sw(val a: Int, val b: Int, val g: Int, val cols: IntArray?)

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

    private fun header(t: String, parent: LinearLayout): TextView {
        val tv = TextView(this).apply {
            text = t.uppercase(); textSize = 12f; setTextColor(sub); letterSpacing = 0.08f; typeface = medium
            setPadding(dp(16), dp(26), 0, dp(8))
        }
        parent.addView(tv); return tv
    }

    private fun group(parent: LinearLayout): LinearLayout {
        val g = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = Glass(dp(30).toFloat(), dark)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        parent.addView(g, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)); return g
    }

    private fun divider(p: LinearLayout) = p.addView(View(this).apply { setBackgroundColor(hair) },
        LinearLayout.LayoutParams(MATCH_PARENT, 2))

    private fun segmented(parent: LinearLayout, rows: List<List<String>>, key: String, def: Int = 0) {
        val tabs = ArrayList<TextView>()
        fun refresh() {
            val cur = prefs.getInt(key, def)
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
                    text = n; textSize = 13f; gravity = Gravity.CENTER; setPadding(0, dp(11), 0, dp(11)); typeface = medium
                    maxLines = 1
                    setOnClickListener { prefs.edit().putInt(key, idx).apply(); refresh(); preview.invalidate() }
                }
                tabs.add(t); seg.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            }
            parent.addView(seg, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(if (ri == 0) 10 else 6); bottomMargin = dp(8) })
        }
        refresh()
    }

    private fun toggle(parent: LinearLayout, label: String, key: String, def: Boolean) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(ink) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply {
            isChecked = prefs.getBoolean(key, def)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.parseColor("#34C759"), if (dark) 0x44FFFFFF else 0x33000000))
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(key, c).apply() }
        })
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pageBg = if (dark) Color.parseColor("#0B0B0F") else Color.parseColor("#EDEDF2")
        window.setBackgroundDrawable(ColorDrawable(pageBg))
        window.statusBarColor = pageBg
        window.navigationBarColor = pageBg
        window.decorView.systemUiVisibility = if (dark) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(64), dp(16), dp(150))
        }
        root.addView(TextView(this).apply {
            text = "Lucidream"; textSize = 36f; setTextColor(ink); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "Live Wallpaper"; textSize = 14f; setTextColor(sub); gravity = Gravity.CENTER; letterSpacing = 0.05f
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        fun curBg() = prefs.getInt("bg", Color.WHITE).let { it to prefs.getInt("bg2", it) }

        // ---------- phone-mockup preview
        preview = object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val clip = Path()
            fun sm(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
            override fun onDraw(cv: Canvas) {
                val (b1, b2) = curBg()
                val fg = Config.fg(prefs)
                val size = prefs.getFloat("size", 1f); val cover = prefs.getFloat("cover", 0.66f)
                val rf = floatArrayOf(0f, 0.2f, 0.38f, 0.5f)[prefs.getInt("round", 0).coerceIn(0, 3)]
                val ph = height - dp(8).toFloat(); val pw = ph * 0.5f
                val l = (width - pw) / 2f; val t = dp(4).toFloat()
                val rect = RectF(l, t, l + pw, t + ph); val rad = pw * 0.15f
                clip.reset(); clip.addRoundRect(rect, rad, rad, Path.Direction.CW)
                cv.save(); cv.clipPath(clip)
                p.style = Paint.Style.FILL
                val gi = prefs.getInt("grad", -1)
                if (gi in Config.GRADS.indices) {
                    Config.drawGrad(cv, l, t, l + pw, t + ph, Config.GRADS[gi], prefs.getInt("gstyle", 0), SystemClock.uptimeMillis() / 1000f)
                } else {
                    p.shader = LinearGradient(0f, t, 0f, t + ph, b1, b2, Shader.TileMode.CLAMP)
                    cv.drawRect(rect, p); p.shader = null
                }
                val c = pw / 6.2f; val Rc = ph * cover; val D = Rc * 2.2f; val yBase = t + ph + c * 0.9f
                val h = c * 0.34f * size; val cx = l + pw / 2f; val TH = 1.12f
                var j = -1
                while (j < 80) {
                    val s = j * c; val sN = (s.coerceAtLeast(0f) / (Rc * TH)).coerceIn(0f, 1f)
                    if (s / Rc > TH) break
                    val g = 1f - 0.72f * Math.pow(sN.toDouble(), 0.9).toFloat()
                    val a0 = (1f - Math.pow(sN.toDouble(), 1.6).toFloat() * 0.55f) * (1f - sm(0.82f, 1f, sN))
                    for (i in 0 until 7) {
                        val hv = ((i * 7 + j * 13 + 100) % 10) / 10f
                        if (hv < 0.05f + 0.5f * sN * sN) continue
                        val a = a0 * (0.55f + 0.45f * (((i * 3 + j * 5 + 100) % 7) / 7f))
                        if (a < 0.02f) continue
                        val x0 = l + (i - 0.5f) * c
                        fun pr(ps: Float): Pair<Float, Float> {
                            val th = ps.coerceAtLeast(0f) / Rc
                            val y = if (ps < 0f) ps else Rc * Math.sin(th.toDouble()).toFloat()
                            val z = if (ps < 0f) 0f else Rc * (1f - Math.cos(th.toDouble()).toFloat())
                            val f = D / (D + z); return f to (yBase - y * f)
                        }
                        val (f1, yt) = pr(s + h * g); val (f2, yb) = pr(s - h * g)
                        val f = (f1 + f2) / 2f
                        val x1 = cx + (x0 - h * g - cx) * f; val x2 = cx + (x0 + h * g - cx) * f
                        p.color = fg; p.alpha = (a.coerceIn(0f, 1f) * 255).toInt()
                        val r = rf * (x2 - x1)
                        cv.drawRoundRect(x1, yt, x2, yb, r, r, p)
                    }
                    j++
                }
                p.color = fg; p.alpha = 235; p.textSize = pw * 0.2f; p.textAlign = Paint.Align.CENTER; p.typeface = medium
                cv.drawText("9:41", cx, t + pw * 0.42f, p)
                p.textSize = pw * 0.065f; p.alpha = 150
                cv.drawText("Friday, November 27", cx, t + pw * 0.54f, p)
                cv.restore()
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(1).toFloat()
                p.color = if (dark) 0x44FFFFFF else 0x33000000
                cv.drawRoundRect(rect, rad, rad, p)
                p.strokeWidth = dp(3).toFloat() / 1.5f
                p.shader = LinearGradient(0f, t, 0f, t + ph, intArrayOf(0xEEFFFFFF.toInt(), 0x22FFFFFF, 0x22FFFFFF, 0x88FFFFFF.toInt()),
                    floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
                cv.drawRoundRect(rect, rad, rad, p); p.shader = null
                p.style = Paint.Style.FILL
                if (gi in Config.GRADS.indices) postInvalidateOnAnimation()
            }
        }
        fun setPreviewBg() { preview.invalidate() }
        root.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, dp(340)).apply { topMargin = dp(18) })

        // ---------- colours
        header("Background", root)
        val cg = group(root)
        val swatches = ArrayList<View>()
        var gradViews = listOf<View>()
        fun refresh() {
            val curG = prefs.getInt("grad", -1)
            val (a, b) = curBg()
            swatches.forEach { v ->
                val s = v.tag as Sw
                val sel = if (s.g >= 0) curG == s.g else (curG < 0 && s.a == a && s.b == b)
                val d = if (s.cols != null) GradientDrawable(GradientDrawable.Orientation.TL_BR, s.cols)
                else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(s.a, s.b))
                d.shape = GradientDrawable.OVAL
                if (sel) d.setStroke(dp(3), accent) else d.setStroke(dp(1), if (dark) 0x44FFFFFF else 0x2E000000)
                v.background = d
                v.scaleX = if (sel) 1.1f else 1f; v.scaleY = v.scaleX
            }
            gradViews.forEach { it.visibility = if (curG >= 0) View.VISIBLE else View.GONE }
        }
        fun swatch(sw: Sw): View {
            val v = View(this).apply { tag = sw }
            v.setOnClickListener {
                prefs.edit().putInt("bg", if (sw.cols != null) sw.cols[0] else sw.a)
                    .putInt("bg2", if (sw.cols != null) sw.cols[0] else sw.b).putInt("grad", sw.g).apply()
                setPreviewBg(); refresh()
            }
            swatches.add(v)
            return FrameLayout(this).apply { addView(v, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER)) }
        }
        fun section(name: String, items: String, last: Boolean = false) {
            cg.addView(TextView(this).apply { text = name; textSize = 13f; setTextColor(sub); typeface = medium; setPadding(dp(2), dp(10), 0, dp(2)) })
            val row = LinearLayout(this).apply { setPadding(0, dp(4), 0, dp(10)) }
            items.split(" ").forEach { s ->
                val pr = pair(s)
                row.addView(swatch(Sw(pr.first, pr.second, -1, null)), LinearLayout.LayoutParams(dp(54), dp(54)))
            }
            cg.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
            divider(cg)
        }
        section("Neutrals", "FFFFFF F2F2F7 D1D1D6 8E8E93 3A3A3C 000000")
        section("Mono gradients", "FFFFFF-8E8E93 FFFFFF-000000 F2F2F7-D1D1D6 D1D1D6-3A3A3C 8E8E93-000000 3A3A3C-000000 000000-3A3A3C 000000-FFFFFF 8E8E93-FFFFFF 000000-8E8E93 D1D1D6-FFFFFF 3A3A3C-8E8E93")
        section("Pastel", "FFD6E0 FFC8DD FFDAB9 FFE5B4 FFF3B0 F2E4D8 C8F2D8 B8F0E0 C9E6FF BDD7FF E0D4FF F0C8FF")
        section("Vivid", "0A84FF 5E5CE6 BF5AF2 FF375F FF453A FF9F0A FFD60A 30D158 64D2FF 63E6E2 AC8E68 FF6482")
        section("Deep", "1B2A41 2D1B3D 143D33 4A1D2A 0F3D4A 3B3A1C")
        cg.addView(TextView(this).apply { text = "Gradients"; textSize = 13f; setTextColor(sub); typeface = medium; setPadding(dp(2), dp(10), 0, dp(2)) })
        val gcol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(10)) }
        Config.GRADS.chunked(10).forEachIndexed { ri, chunk ->
            val row = LinearLayout(this)
            chunk.forEachIndexed { k, cs -> row.addView(swatch(Sw(0, 0, ri * 10 + k, cs)), LinearLayout.LayoutParams(dp(54), dp(54))) }
            gcol.addView(row)
        }
        cg.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(gcol) })

        // gradient style (visible only while a gradient is selected)
        val gh = header("Gradient style", root)
        val gg = group(root)
        segmented(gg, listOf(listOf("Volume mesh", "Edge", "Gradient")), "gstyle")
        gradViews = listOf(gh, gg)
        refresh()

        // ---------- motion / shape
        header("Motion", root)
        segmented(group(root), listOf(listOf("Cascade", "Flow", "Wave"), listOf("Ripple", "Breathe", "Orbit")), "anim", 2)
        header("Corners", root)
        segmented(group(root), listOf(listOf("Sharp", "Soft", "Round", "Circle")), "round")

        // ---------- unlock (toggle above the styles)
        header("Unlock animation", root)
        val ug = group(root)
        toggle(ug, "Play on unlock", "unlock", true)
        divider(ug)
        segmented(ug, listOf(listOf("Down", "iOS", "Zoom", "Dissolve")), "uanim")

        // ---------- tilt (toggle above the styles)
        header("Tilt", root)
        val tg = group(root)
        toggle(tg, "Tilt effect", "tilt", true)
        divider(tg)
        segmented(tg, listOf(listOf("Fold", "Parallax")), "tmode")

        // ---------- settings
        header("Settings", root)
        val sg = group(root)
        fun slider(label: String, key: String, mn: Float, mx: Float, def: Float, fmt: (Float) -> String) {
            val head = LinearLayout(this)
            val vt = TextView(this).apply { textSize = 15f; setTextColor(sub) }
            head.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(ink) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            head.addView(vt)
            val cur = prefs.getFloat(key, def); vt.text = fmt(cur)
            val sl = M3Slider(this, accent, if (dark) 0x33FFFFFF else 0x1F000000).apply {
                progress = ((cur - mn) / (mx - mn)).coerceIn(0f, 1f)
                onChange = { v ->
                    val x = mn + (mx - mn) * v
                    prefs.edit().putFloat(key, x).apply(); vt.text = fmt(x); preview.invalidate()
                }
            }
            sg.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
            sg.addView(sl, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)))
        }
        slider("Curl height", "cover", 0.4f, 1.8f, 0.66f) { "${(it * 100).toInt()}%" }
        slider("Speed", "speed", 0.3f, 2.5f, 1f) { "×" + "%.1f".format(it) }
        slider("Square size", "size", 0.7f, 1.3f, 1f) { "${(it * 100).toInt()}%" }
        slider("Edge blur", "blur", 0f, 1f, 0.35f) { if (it < 0.02f) "Off" else "${(it * 100).toInt()}%" }
        divider(sg)
        toggle(sg, "Smooth 60 FPS", "fps60", false)

        // ---------- easter egg
        root.addView(Clawd(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(70)).apply { topMargin = dp(56) })
        root.addView(TextView(this).apply {
            text = "limits... made by p1xeltech"; textSize = 11f; setTextColor(sub); gravity = Gravity.CENTER; alpha = 0.8f
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })

        // ---------- floating button
        val btn = TextView(this).apply {
            text = "Set wallpaper"; textSize = 18f; gravity = Gravity.CENTER; typeface = medium
            setTextColor(if (dark) Color.WHITE else ink)
            background = LayerDrawable(arrayOf(
                gd(if (dark) Color.parseColor("#1C1C1E") else Color.WHITE, if (dark) Color.BLACK else Color.parseColor("#F2F2F7"), 30),
                Glass(dp(30).toFloat(), dark)))
            elevation = dp(6).toFloat()
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
            setBackgroundColor(pageBg)
            addView(ScrollView(this@MainActivity).apply { isVerticalScrollBarEnabled = false; addView(root) })
            addView(btn, FrameLayout.LayoutParams(MATCH_PARENT, dp(58), Gravity.BOTTOM).apply { setMargins(dp(20), 0, dp(20), dp(28)) })
        })
    }
}
