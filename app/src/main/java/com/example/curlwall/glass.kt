package com.example.curlwall

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.min

/**
 * Liquid-glass surface: translucent fill, specular reflection at the top,
 * soft bounce light at the bottom and a gradient rim.
 */
class GlassDrawable(
    ctx: Context,
    private val radiusDp: Float,
    private val dark: Boolean,
    private val fillColor: Int? = null
) : Drawable() {

    private val d = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hi = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d * 1.1f }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d * 1.6f; color = Color.argb(22, 0, 0, 0) }
    private val rect = RectF()
    private val hiRect = RectF()
    private val loRect = RectF()
    private val path = Path()

    override fun onBoundsChange(b: Rect) {
        rect.set(b)
        rect.inset(d * 0.7f, d * 0.7f)
        val h = rect.height()
        hiRect.set(rect.left, rect.top, rect.right, rect.top + h * 0.52f)
        loRect.set(rect.left, rect.bottom - h * 0.34f, rect.right, rect.bottom)
        val hiA = if (dark) 80 else 215
        hi.shader = LinearGradient(0f, hiRect.top, 0f, hiRect.bottom, Color.argb(hiA, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        val loA = if (dark) 46 else 150
        lo.shader = LinearGradient(0f, loRect.top, 0f, loRect.bottom, Color.argb(0, 255, 255, 255), Color.argb(loA, 255, 255, 255), Shader.TileMode.CLAMP)
        val rimColors = if (dark)
            intArrayOf(Color.argb(150, 255, 255, 255), Color.argb(26, 255, 255, 255), Color.argb(90, 255, 255, 255))
        else
            intArrayOf(Color.argb(255, 255, 255, 255), Color.argb(34, 0, 0, 0), Color.argb(170, 255, 255, 255))
        rim.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, rimColors, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        fill.color = fillColor ?: if (dark) Color.argb(26, 255, 255, 255) else Color.argb(150, 255, 255, 255)
    }

    override fun draw(c: Canvas) {
        if (rect.isEmpty) return
        val r = min(radiusDp * d, min(rect.width(), rect.height()) / 2f)
        if (!dark) c.drawRoundRect(rect.left, rect.top + d * 1.6f, rect.right, rect.bottom + d * 1.6f, r, r, shadow)
        c.drawRoundRect(rect, r, r, fill)
        c.save()
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        c.clipPath(path)
        c.drawRect(hiRect, hi)
        c.drawRect(loRect, lo)
        c.restore()
        c.drawRoundRect(rect, r, r, rim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

fun onColor(bg: Int): Int = if (ColorUtils.calculateLuminance(bg) > 0.62) Color.rgb(20, 20, 24) else Color.WHITE

private fun glassThumb(c: Canvas, x: Float, y: Float, r: Float, d: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = Color.argb(40, 0, 0, 0)
    c.drawCircle(x, y + d * 1.5f, r + d, p)
    p.shader = LinearGradient(x, y - r, x, y + r, Color.WHITE, Color.rgb(214, 218, 228), Shader.TileMode.CLAMP)
    c.drawCircle(x, y, r, p)
    p.shader = LinearGradient(x, y - r, x, y, Color.argb(210, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
    c.drawOval(RectF(x - r * 0.62f, y - r * 0.92f, x + r * 0.62f, y - r * 0.1f), p)
}

class GlassSlider(
    ctx: Context,
    private val min: Int,
    private val max: Int,
    start: Int,
    private val dark: Boolean,
    private val accent: Int,
    private val onChange: (Int) -> Unit
) : View(ctx) {

    var value = start.coerceIn(min, max)
        private set
    private val d = ctx.resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (44 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val pad = 18 * d
        val cy = height / 2f
        val span = width - 2 * pad
        val frac = (value - min).toFloat() / (max - min).coerceAtLeast(1)
        val x = pad + span * frac
        val half = 3.5f * d
        track.color = if (dark) Color.argb(48, 255, 255, 255) else Color.argb(36, 0, 0, 0)
        c.drawRoundRect(pad, cy - half, width - pad, cy + half, half, half, track)
        fillP.color = accent
        c.drawRoundRect(pad, cy - half, x, cy + half, half, half, fillP)
        gloss.shader = LinearGradient(0f, cy - half, 0f, cy, Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawRoundRect(pad, cy - half, x, cy, half, half, gloss)
        glassThumb(c, x, cy, 13 * d, d)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        val pad = 18 * d
        val f = ((e.x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        val nv = (min + f * (max - min)).toInt()
        if (nv != value) {
            value = nv
            invalidate()
            onChange(nv)
        }
        return true
    }
}

class GlassSwitch(
    ctx: Context,
    start: Boolean,
    private val dark: Boolean,
    private val accent: Int,
    private val onChange: (Boolean) -> Unit
) : View(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private var checked = start
    private var prog = if (start) 1f else 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setOnClickListener {
            checked = !checked
            ValueAnimator.ofFloat(prog, if (checked) 1f else 0f).apply {
                duration = 260
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener { prog = it.animatedValue as Float; invalidate() }
                start()
            }
            onChange(checked)
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((58 * d).toInt(), (36 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val off = if (dark) Color.argb(56, 255, 255, 255) else Color.argb(46, 0, 0, 0)
        val trackRect = RectF(2 * d, 4 * d, width - 2 * d, height - 4 * d)
        val rr = trackRect.height() / 2f
        p.shader = null
        p.color = ColorUtils.blendARGB(off, accent, prog)
        c.drawRoundRect(trackRect, rr, rr, p)
        p.shader = LinearGradient(0f, trackRect.top, 0f, trackRect.centerY(), Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawRoundRect(trackRect.left, trackRect.top, trackRect.right, trackRect.centerY(), rr, rr, p)
        p.shader = null
        val r = 13.5f * d
        val x = trackRect.left + rr + (trackRect.width() - 2 * rr) * prog
        glassThumb(c, x, height / 2f, r, d)
    }
}

class Swatch(
    ctx: Context,
    private val pal: Palette,
    private val selected: Boolean,
    private val dark: Boolean
) : View(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((54 * d).toInt(), (54 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = 21 * d
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, intArrayOf(pal.c1, pal.c2, pal.c3), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = LinearGradient(0f, cy - r, 0f, cy + r * 0.1f, Color.argb(150, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawOval(RectF(cx - r * 0.78f, cy - r * 0.95f, cx + r * 0.78f, cy - r * 0.05f), p)
        p.shader = LinearGradient(0f, cy + r * 0.3f, 0f, cy + r, Color.argb(0, 255, 255, 255), Color.argb(80, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawOval(RectF(cx - r * 0.6f, cy + r * 0.35f, cx + r * 0.6f, cy + r * 0.95f), p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = if (dark) Color.argb(110, 255, 255, 255) else Color.argb(200, 255, 255, 255)
        c.drawCircle(cx, cy, r, p)
        if (selected) {
            p.strokeWidth = 2.4f * d
            p.color = if (dark) Color.WHITE else Color.rgb(20, 20, 24)
            c.drawCircle(cx, cy, r + 5 * d, p)
        }
    }
}
