package com.example.curlwall

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import kotlin.math.max

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private var dark = false
    private var accent = Color.parseColor("#6C5CE7")
    private var topInset = 0
    private var bottomInset = 0

    private lateinit var root: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var scrim: View
    private lateinit var sheet: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private var primaryBtn: TextView? = null
    private var sheetOpen = false
    private var sheetHeight = 0

    private val textColor get() = if (dark) Color.rgb(245, 245, 247) else Color.rgb(17, 17, 20)
    private val subColor get() = if (dark) Color.argb(150, 245, 245, 247) else Color.argb(140, 17, 17, 20)
    private val bgColor get() = if (dark) Color.rgb(10, 10, 14) else Color.rgb(242, 242, 246)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val serif: Typeface = Typeface.create("serif", Typeface.NORMAL)
    private val serifItalic: Typeface = Typeface.create("serif", Typeface.ITALIC)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Config.prefs(this)
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        accent = Palettes.all[Config.Snap(prefs).palette].accent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        buildUi()
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(bgColor) }

        // Title block, dead centre
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "Lucidream"
            typeface = serif
            textSize = 46f
            letterSpacing = 0.015f
            setTextColor(textColor)
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "Live Wallpaper"
            typeface = serifItalic
            textSize = 16f
            letterSpacing = 0.08f
            setTextColor(subColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        titleBlock.addView(title)
        titleBlock.addView(sub)
        root.addView(titleBlock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        titleBlock.alpha = 0f
        titleBlock.translationY = dp(14).toFloat()
        titleBlock.animate().alpha(1f).translationY(0f).setDuration(700).setInterpolator(DecelerateInterpolator(2f)).start()

        // Bottom dock
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        primaryBtn = glassButton("Set Wallpaper", accent) { setWallpaper() }
        val customize = glassButton("Customize", null) { openSheet() }
        dock.addView(primaryBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        dock.addView(customize, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        root.addView(dock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = dp(24); rightMargin = dp(24); bottomMargin = dp(28)
        })

        // Scrim
        scrim = View(this).apply {
            setBackgroundColor(Color.argb(if (dark) 150 else 90, 0, 0, 0))
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { closeSheet() }
        }
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Sheet
        sheetHeight = (resources.displayMetrics.heightPixels * 0.82f).toInt()
        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassDrawable(
                this@MainActivity, 34f, dark,
                if (dark) Color.argb(244, 24, 24, 30) else Color.argb(246, 250, 250, 252)
            )
            translationY = sheetHeight.toFloat()
        }
        val handle = View(this).apply {
            setBackgroundColor(if (dark) Color.argb(90, 255, 255, 255) else Color.argb(60, 0, 0, 0))
        }
        sheet.addView(handle, LinearLayout.LayoutParams(dp(40), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) })

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(14), dp(18), dp(6))
        }
        val hTitle = TextView(this).apply {
            text = "Customize"
            typeface = serif
            textSize = 26f
            setTextColor(textColor)
        }
        val done = glassButton("Done", null, small = true) { closeSheet() }
        header.addView(hTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(done)
        sheet.addView(header)

        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(24))
        }
        scroll.addView(content)
        sheet.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sheetHeight, Gravity.BOTTOM))

        buildContent()
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = b.top
            bottomInset = b.bottom
            (dock.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(28) + bottomInset
            dock.requestLayout()
            content.setPadding(dp(18), dp(4), dp(18), dp(24) + bottomInset)
            insets
        }
    }

    // ---------------------------------------------------------------- sheet

    private fun openSheet() {
        if (sheetOpen) return
        sheetOpen = true
        scrim.visibility = View.VISIBLE
        scrim.animate().alpha(1f).setDuration(260).start()
        sheet.animate().translationY(0f).setDuration(420).setInterpolator(DecelerateInterpolator(2.2f)).start()
    }

    private fun closeSheet() {
        if (!sheetOpen) return
        sheetOpen = false
        scrim.animate().alpha(0f).setDuration(220).withEndAction { scrim.visibility = View.GONE }.start()
        sheet.animate().translationY(sheetHeight.toFloat()).setDuration(320).setInterpolator(DecelerateInterpolator(1.6f)).start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (sheetOpen) closeSheet() else super.onBackPressed()
    }

    // ---------------------------------------------------------------- settings content

    private fun buildContent() {
        content.removeAllViews()
        val s = Config.Snap(prefs)

        section("Style")
        val styleScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4)) }
        for (st in WStyle.values()) {
            val sel = st == s.style
            val chip = glassButton(st.label, if (sel) accent else null, small = true) {
                putInt(Config.K_STYLE, st.ordinal)
                rebuildKeepScroll()
            }
            styleRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
        }
        styleScroll.addView(styleRow)
        content.addView(styleScroll)

        section("Colors")
        val palScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val palRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4)) }
        Palettes.all.forEachIndexed { idx, pal ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(Swatch(this, pal, idx == s.palette, dark))
            col.addView(TextView(this).apply {
                text = pal.name
                textSize = 11f
                setTextColor(subColor)
                gravity = Gravity.CENTER
            })
            col.setOnClickListener {
                putInt(Config.K_PALETTE, idx)
                accent = pal.accent
                primaryBtn?.background = GlassDrawable(this, 999f, dark, accentFill(accent))
                primaryBtn?.setTextColor(onColor(accent))
                rebuildKeepScroll()
            }
            palRow.addView(col, LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        palScroll.addView(palRow)
        content.addView(palScroll)

        section("Tiles")
        card().also { c ->
            sliderRow(c, "Grid density", 5, 24, s.cols, { "${it} columns" }) { putInt(Config.K_COLS, it) }
            sliderRow(c, "Gap", 0, 8, s.gap, { "$it dp" }) { putInt(Config.K_GAP, it) }
            sliderRow(c, "Corner radius", 0, 50, s.radius, { "$it%" }) { putInt(Config.K_RADIUS, it) }
        }

        section("Motion")
        card().also { c ->
            sliderRow(c, "Animation speed", 50, 200, s.speed, { "$it%" }) { putInt(Config.K_SPEED, it) }
            switchRow(c, "Animate on unlock", s.unlock) { putBool(Config.K_UNLOCK, it) }
            switchRow(c, "Touch interaction", s.touch) { putBool(Config.K_TOUCH, it) }
            val replay = glassButton("Replay animation", null, small = true) {
                prefs.edit().putLong(Config.K_REPLAY, System.currentTimeMillis()).apply()
                Toast.makeText(this, "Visible when the wallpaper is on screen", Toast.LENGTH_SHORT).show()
            }
            c.addView(replay, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        section("Blur")
        card().also { c ->
            switchRow(c, "Blur curling & vanishing tiles", s.blurOn) { putBool(Config.K_BLUR, it) }
            sliderRow(c, "Blur strength", 0, 100, s.blurAmt, { "$it%" }) { putInt(Config.K_BLURAMT, it) }
        }

        section("Glass")
        card().also { c ->
            switchRow(c, "Glass reflections & tilt sheen", s.sheen) { putBool(Config.K_SHEEN, it) }
            switchRow(c, "Tile edge highlight", s.edge) { putBool(Config.K_EDGE, it) }
        }

        section("Background")
        card().also { c ->
            switchRow(c, "Use my photo", s.photo) {
                if (it && !File(filesDir, "photo.jpg").exists()) {
                    pickPhoto()
                } else {
                    putBool(Config.K_PHOTO, it)
                }
            }
            val pick = glassButton("Choose photo…", null, small = true) { pickPhoto() }
            c.addView(pick, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
    }

    private fun rebuildKeepScroll() {
        val y = scroll.scrollY
        buildContent()
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun section(name: String) {
        content.addView(TextView(this).apply {
            text = name.uppercase()
            textSize = 12f
            letterSpacing = 0.14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(subColor)
            setPadding(dp(6), dp(20), 0, dp(6))
        })
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
            background = GlassDrawable(
                this@MainActivity, 24f, dark,
                if (dark) Color.argb(20, 255, 255, 255) else Color.argb(170, 255, 255, 255)
            )
        }
        content.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return c
    }

    private fun sliderRow(parent: LinearLayout, label: String, min: Int, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        val l = TextView(this).apply { text = label; textSize = 15f; setTextColor(textColor) }
        val v = TextView(this).apply { text = fmt(value); textSize = 14f; setTextColor(accent) }
        top.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(v)
        parent.addView(top)
        parent.addView(GlassSlider(this, min, max, value, dark, accent) {
            v.text = fmt(it)
            onChange(it)
        })
    }

    private fun switchRow(parent: LinearLayout, label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        row.addView(TextView(this).apply { text = label; textSize = 15f; setTextColor(textColor) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(GlassSwitch(this, value, dark, accent, onChange))
        parent.addView(row)
    }

    private fun accentFill(a: Int) = Color.argb(205, Color.red(a), Color.green(a), Color.blue(a))

    private fun glassButton(text: String, tint: Int?, small: Boolean = false, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = if (small) 14f else 16f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (tint != null) onColor(tint) else textColor)
            val ph = if (small) 16 else 22
            val pv = if (small) 10 else 16
            setPadding(dp(ph), dp(pv), dp(ph), dp(pv))
            background = GlassDrawable(this@MainActivity, 999f, dark, tint?.let { accentFill(it) })
            isClickable = true
            setOnClickListener { onClick() }
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }
                false
            }
        }
    }

    // ---------------------------------------------------------------- prefs / actions

    private fun putInt(k: String, v: Int) = prefs.edit().putInt(k, v).apply()
    private fun putBool(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()

    private fun setWallpaper() {
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, CurlWallpaperService::class.java)
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                Toast.makeText(this, "Open your wallpaper settings and pick Lucidream", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pickPhoto() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(i, "Choose photo"), REQ_PHOTO)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PHOTO) return
        if (resultCode != RESULT_OK) {
            rebuildKeepScroll()
            return
        }
        val uri = data?.data ?: return
        Thread {
            try {
                val dm = resources.displayMetrics
                val target = max(dm.widthPixels, dm.heightPixels)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) {
                    File(filesDir, "photo.jpg").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                    prefs.edit().putBoolean(Config.K_PHOTO, true).putLong(Config.K_PHOTOVER, System.currentTimeMillis()).apply()
                }
            } catch (_: Exception) {
            }
            runOnUiThread { rebuildKeepScroll() }
        }.start()
    }

    companion object {
        private const val REQ_PHOTO = 41
    }
}
