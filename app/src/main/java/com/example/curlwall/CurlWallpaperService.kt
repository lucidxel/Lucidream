package com.example.curlwall

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val PIF = Math.PI.toFloat()

private fun smooth(x: Float): Float {
    val v = x.coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}

private fun easeOutBack(x: Float): Float {
    val v = x.coerceIn(0f, 1f)
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = v - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

class CurlWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = CurlEngine()

    inner class CurlEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener, SensorEventListener {

        private val ctx: Context = this@CurlWallpaperService
        private val prefs = Config.prefs(ctx)
        private var cfg = Config.Snap(prefs)
        private val density = ctx.resources.displayMetrics.density
        private val choreo = Choreographer.getInstance()
        private val keyguard = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        private val sensors = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val handler = Handler(Looper.getMainLooper())

        // surface + bitmaps
        private var w = 0
        private var h = 0
        private var bg: Bitmap? = null
        private var blurBmp: Bitmap? = null
        private var sharpShader: BitmapShader? = null
        private var blurShader: BitmapShader? = null
        private var baseColor = Color.BLACK

        // paints
        private val sharpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val sheenPaint = Paint()

        // math helpers
        private val cam = Camera()
        private val tileM = Matrix()
        private val shaderM = Matrix()

        // grid
        private var cols = 0
        private var rows = 0
        private var tw = 0f
        private var th = 0f
        private var delays = FloatArray(0)
        private var hover = FloatArray(0)
        private var target = FloatArray(0)
        private var rnd = FloatArray(0)

        // animation state
        private var visible = false
        private var frameQueued = false
        private var dirty = true
        private var animating = false
        private var animStartMs = 0L
        private var animTotalMs = 1700f
        private var pendingUnlock = false
        private var lastNs = 0L
        private var tiltX = 0f

        // per-tile transform scratch
        private var rx = 0f
        private var ry = 0f
        private var rz = 0f
        private var sc = 1f
        private var dx = 0f
        private var dy = 0f
        private var al = 1f
        private var pvx = 0f
        private var pvy = 0f
        private var shadeK = 0f

        private val frameCb = Choreographer.FrameCallback { ns -> onFrame(ns) }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> pendingUnlock = true
                    Intent.ACTION_USER_PRESENT -> {
                        if (!cfg.unlock) {
                            pendingUnlock = false
                        } else if (visible) {
                            pendingUnlock = false
                            startAnim()
                        } else {
                            pendingUnlock = true
                        }
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(this)
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            // System broadcasts need an explicit export flag on Android 13+, otherwise they are not delivered reliably.
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, f)
            }
            cam.setLocation(0f, 0f, -10f)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            choreo.removeFrameCallback(frameCb)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
            sensors.unregisterListener(this)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            rebuildBackground()
            buildGrid()
            dirty = true
            requestFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            choreo.removeFrameCallback(frameCb)
            frameQueued = false
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            updateSensors()
            if (v) {
                if (isPreview) {
                    if (cfg.unlock) startAnim()
                } else if (pendingUnlock && !keyguard.isKeyguardLocked) {
                    pendingUnlock = false
                    if (cfg.unlock) startAnim()
                }
                dirty = true
                requestFrame()
            } else {
                choreo.removeFrameCallback(frameCb)
                frameQueued = false
                lastNs = 0L
            }
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
            val old = cfg
            cfg = Config.Snap(p)
            if (key == Config.K_REPLAY) {
                if (visible || isPreview) startAnim()
                return
            }
            if (key == Config.K_PALETTE || key == Config.K_PHOTO || key == Config.K_PHOTOVER) rebuildBackground()
            if (key == Config.K_STYLE || key == Config.K_COLS) buildGrid()
            if (key == Config.K_SPEED) animTotalMs = 1700f * 100f / cfg.speed
            if (key == Config.K_SHEEN) updateSensors()
            if (isPreview && cfg.unlock && (old.style != cfg.style || old.cols != cfg.cols)) startAnim()
            dirty = true
            requestFrame()
        }

        // ---------- touch ----------

        override fun onTouchEvent(e: MotionEvent) {
            if (!cfg.touch || hover.isEmpty()) return
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> updateTarget(e.x, e.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> java.util.Arrays.fill(target, 1f)
            }
            requestFrame()
        }

        // Most launchers do not forward raw touches to wallpapers, only "tap" commands.
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (cfg.touch && hover.isNotEmpty() &&
                (action == WallpaperManager.COMMAND_TAP || action == WallpaperManager.COMMAND_DROP)
            ) {
                updateTarget(x.toFloat(), y.toFloat())
                requestFrame()
                handler.postDelayed({
                    java.util.Arrays.fill(target, 1f)
                    requestFrame()
                }, 420)
            }
            return null
        }

        private fun updateTarget(x: Float, y: Float) {
            val radius = max(tw, th) * 2.6f
            for (r in 0 until rows) for (c in 0 until cols) {
                val cx = (c + 0.5f) * tw
                val cy = (r + 0.5f) * th
                val d = hypot(cx - x, cy - y)
                target[r * cols + c] = smooth(d / radius)
            }
        }

        // ---------- sensors ----------

        private fun updateSensors() {
            sensors.unregisterListener(this)
            if (visible && cfg.sheen) {
                val acc = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                if (acc != null) sensors.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
            }
        }

        override fun onSensorChanged(e: SensorEvent) {
            val nx = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
            val d = nx - tiltX
            tiltX += d * 0.08f
            if (abs(d) > 0.012f) {
                dirty = true
                requestFrame()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        // ---------- animation ----------

        private fun startAnim() {
            animTotalMs = 1700f * 100f / cfg.speed
            animStartMs = System.nanoTime() / 1_000_000L
            animating = true
            lastNs = 0L
            requestFrame()
        }

        private fun requestFrame() {
            if (!visible || frameQueued || w == 0) return
            frameQueued = true
            choreo.postFrameCallback(frameCb)
        }

        private fun onFrame(ns: Long) {
            frameQueued = false
            if (!visible || w == 0 || hover.isEmpty()) return
            val dtMs = if (lastNs == 0L) 16f else ((ns - lastNs) / 1_000_000f).coerceIn(1f, 50f)
            lastNs = ns
            var active = false
            var t = 1f
            if (animating) {
                val el = (ns / 1_000_000L - animStartMs).toFloat()
                t = el / animTotalMs
                if (t >= 1f) {
                    animating = false
                    t = 1f
                } else {
                    active = true
                }
            }
            val k = 1f - exp(-dtMs / 85f)
            for (i in hover.indices) {
                val d = target[i] - hover[i]
                if (abs(d) > 0.003f) {
                    hover[i] += d * k
                    active = true
                } else {
                    hover[i] = target[i]
                }
            }
            render(t)
            dirty = false
            if (active) {
                requestFrame()
            } else {
                lastNs = 0L
            }
        }

        private fun render(t: Float) {
            val holder = surfaceHolder
            var c: Canvas? = null
            try {
                c = holder.lockCanvas() ?: return
                drawScene(c, t)
            } catch (_: Exception) {
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c) } catch (_: Exception) {}
                }
            }
        }

        // ---------- scene ----------

        private fun drawScene(c: Canvas, t: Float) {
            c.drawColor(baseColor)
            val sh = sharpShader ?: return
            val bs = blurShader
            val gapPx = cfg.gap * density
            val rad = min(tw, th) * cfg.radius / 100f
            val rw = max(1f, tw - gapPx)
            val rh = max(1f, th - gapPx)
            val span = 0.42f
            val blurOn = cfg.blurOn && bs != null
            val blurAmt = cfg.blurAmt / 100f
            val bb = blurBmp
            val kx = if (bb != null) w.toFloat() / bb.width else 1f
            val ky = if (bb != null) h.toFloat() / bb.height else 1f
            edgePaint.strokeWidth = 1.2f * density

            for (r in 0 until rows) for (col in 0 until cols) {
                val i = r * cols + col
                var p = hover[i]
                if (animating) {
                    val a = ((t - delays[i] * (1f - span)) / span).coerceIn(0f, 1f)
                    p = min(p, smooth(a))
                }
                if (p <= 0.004f) continue
                val tx = col * tw + gapPx / 2f
                val ty = r * th + gapPx / 2f

                shaderM.setTranslate(-tx, -ty)
                sh.setLocalMatrix(shaderM)
                sharpPaint.shader = sh

                if (p >= 0.999f) {
                    sharpPaint.alpha = 255
                    c.save()
                    c.translate(tx, ty)
                    c.drawRoundRect(0f, 0f, rw, rh, rad, rad, sharpPaint)
                    c.restore()
                    continue
                }

                val q = 1f - p
                styleTransform(i, p, q, rw, rh)
                if (al <= 0.01f) continue

                cam.save()
                cam.rotateX(rx)
                cam.rotateY(ry)
                cam.getMatrix(tileM)
                cam.restore()
                tileM.preTranslate(-pvx, -pvy)
                tileM.postTranslate(pvx, pvy)
                if (rz != 0f) tileM.postRotate(rz, pvx, pvy)
                if (sc != 1f) tileM.postScale(sc, sc, pvx, pvy)
                tileM.postTranslate(tx + dx, ty + dy)

                c.save()
                c.concat(tileM)
                sharpPaint.alpha = (al * 255f).toInt().coerceIn(0, 255)
                c.drawRoundRect(0f, 0f, rw, rh, rad, rad, sharpPaint)

                if (blurOn && bs != null) {
                    // Soft blur: cross-fade to a heavily blurred copy while the tile is curling / vanishing.
                    val mix = (blurAmt * Math.pow(q.toDouble(), 0.55).toFloat() * 1.15f).coerceIn(0f, 1f)
                    if (mix > 0.01f) {
                        shaderM.setScale(kx, ky)
                        shaderM.postTranslate(-tx, -ty)
                        bs.setLocalMatrix(shaderM)
                        blurPaint.shader = bs
                        blurPaint.alpha = (al * mix * 255f).toInt().coerceIn(0, 255)
                        c.drawRoundRect(0f, 0f, rw, rh, rad, rad, blurPaint)
                    }
                }
                if (shadeK > 0f) {
                    shadePaint.color = Color.argb((q * 120f * shadeK).toInt().coerceIn(0, 255), 0, 0, 0)
                    c.drawRoundRect(0f, 0f, rw, rh, rad, rad, shadePaint)
                }
                if (cfg.edge) {
                    val a = (sin(p * PIF) * 170f * al).toInt().coerceIn(0, 255)
                    edgePaint.color = Color.argb(a, 255, 255, 255)
                    c.drawRoundRect(0f, 0f, rw, rh, rad, rad, edgePaint)
                }
                c.restore()
            }
            sharpPaint.alpha = 255
            if (cfg.sheen) drawSheen(c)
        }

        private fun styleTransform(i: Int, p: Float, q: Float, rw: Float, rh: Float) {
            rx = 0f; ry = 0f; rz = 0f; sc = 1f; dx = 0f; dy = 0f; al = 1f
            pvx = rw / 2f; pvy = rh / 2f; shadeK = 0f
            when (cfg.style) {
                WStyle.CURL -> {
                    ry = -112f * q
                    rx = 14f * q
                    pvx = 0f
                    al = (p * 4f).coerceAtMost(1f)
                    dx = -rw * 0.1f * q
                    shadeK = 1f
                }
                WStyle.FLIP -> {
                    rx = 150f * q
                    sc = 0.85f + 0.15f * p
                    al = (p * 2.5f).coerceAtMost(1f)
                    shadeK = 0.7f
                }
                WStyle.RIPPLE -> {
                    sc = easeOutBack(p)
                    al = (p * 3f).coerceAtMost(1f)
                }
                WStyle.CASCADE -> {
                    dy = -rh * 2.6f * q
                    rx = 40f * q
                    pvy = rh
                    al = p
                    shadeK = 0.5f
                }
                WStyle.SPIRAL -> {
                    rz = (if (i % 2 == 0) 210f else -210f) * q
                    sc = p
                    al = (p * 2f).coerceAtMost(1f)
                }
                WStyle.DISSOLVE -> {
                    sc = 0.45f + 0.55f * p
                    al = p
                    dx = (rnd[i] - 0.5f) * rw * 0.9f * q
                    dy = ((rnd[i] * 7.3f) % 1f - 0.5f) * rh * 0.9f * q
                }
                WStyle.IRIS -> {
                    sc = p
                    rz = 90f * q
                    al = sqrt(p)
                }
                WStyle.WAVE -> {
                    dx = -rw * 3.5f * q
                    dy = sin(q * PIF) * rh * 0.6f
                    ry = 35f * q
                    al = (p * 2f).coerceAtMost(1f)
                    shadeK = 0.4f
                }
            }
        }

        private fun drawSheen(c: Canvas) {
            val wf = w.toFloat()
            val hf = h.toFloat()
            // top reflection
            sheenPaint.shader = LinearGradient(0f, 0f, 0f, hf * 0.22f, Color.argb(56, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, wf, hf * 0.22f, sheenPaint)
            // bottom reflection
            sheenPaint.shader = LinearGradient(0f, hf * 0.88f, 0f, hf, Color.argb(0, 255, 255, 255), Color.argb(40, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawRect(0f, hf * 0.88f, wf, hf, sheenPaint)
            // moving band that follows device tilt
            val cx = wf * (0.5f + tiltX * 0.9f)
            val band = wf * 0.35f
            sheenPaint.shader = LinearGradient(
                cx - band, 0f, cx + band, hf * 0.5f,
                intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(44, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, wf, hf, sheenPaint)
            sheenPaint.shader = null
        }

        // ---------- grid / background ----------

        private fun buildGrid() {
            if (w <= 0 || h <= 0) return
            cols = cfg.cols.coerceIn(4, 30)
            tw = w / cols.toFloat()
            rows = max(1, (h / tw).roundToInt())
            th = h / rows.toFloat()
            val n = cols * rows
            hover = FloatArray(n) { 1f }
            target = FloatArray(n) { 1f }
            rnd = FloatArray(n) { Random.nextFloat() }
            delays = FloatArray(n)
            val asp = w.toFloat() / h
            var mn = Float.MAX_VALUE
            var mx = -Float.MAX_VALUE
            for (r in 0 until rows) for (c in 0 until cols) {
                val i = r * cols + c
                val nx = (c + 0.5f) / cols
                val ny = (r + 0.5f) / rows
                val v = when (cfg.style) {
                    WStyle.CURL -> (1f - ny) * 0.65f + (1f - nx) * 0.35f
                    WStyle.FLIP -> (nx + ny) * 0.5f + rnd[i] * 0.12f
                    WStyle.RIPPLE -> hypot((nx - 0.5f) * asp, ny - 1f)
                    WStyle.CASCADE -> ny * 0.85f + rnd[i] * 0.15f
                    WStyle.SPIRAL -> {
                        val ang = (atan2(ny - 0.5f, nx - 0.5f) + PIF) / (2f * PIF)
                        hypot((nx - 0.5f) * asp, ny - 0.5f) + ang * 0.25f
                    }
                    WStyle.DISSOLVE -> rnd[i]
                    WStyle.IRIS -> hypot((nx - 0.5f) * asp, ny - 0.5f)
                    WStyle.WAVE -> nx * 0.8f + (sin(ny * PIF * 2f) + 1f) * 0.08f
                }
                delays[i] = v
                mn = min(mn, v)
                mx = max(mx, v)
            }
            val range = max(0.0001f, mx - mn)
            for (i in 0 until n) delays[i] = (delays[i] - mn) / range
        }

        private fun rebuildBackground() {
            if (w <= 0 || h <= 0) return
            val pal = Palettes.all[cfg.palette]
            val photo = if (cfg.photo) loadPhoto() else null
            val bmp = photo ?: generate(pal)
            baseColor = if (photo != null) Color.rgb(8, 8, 10) else Color.rgb(
                (Color.red(pal.c1) * 0.22f).toInt(), (Color.green(pal.c1) * 0.22f).toInt(), (Color.blue(pal.c1) * 0.22f).toInt()
            )
            bg = bmp
            sharpShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            var cur = bmp
            val bw = max(2, w / 24)
            val bh = max(2, h / 24)
            while (cur.width > bw * 2 && cur.height > bh * 2) {
                cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            }
            blurBmp = Bitmap.createScaledBitmap(cur, bw, bh, true)
            blurShader = BitmapShader(blurBmp!!, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        private fun loadPhoto(): Bitmap? {
            return try {
                val f = File(ctx.filesDir, "photo.jpg")
                if (!f.exists()) return null
                val src = BitmapFactory.decodeFile(f.absolutePath) ?: return null
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val s = max(w.toFloat() / src.width, h.toFloat() / src.height)
                val m = Matrix()
                m.postScale(s, s)
                m.postTranslate((w - src.width * s) / 2f, (h - src.height * s) / 2f)
                Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
                src.recycle()
                out
            } catch (_: Throwable) {
                null
            }
        }

        // Rich, detailed gradient scene (the detail makes the blur on curling tiles clearly visible).
        private fun generate(pal: Palette): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val wf = w.toFloat()
            val hf = h.toFloat()
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, wf, hf, intArrayOf(pal.c1, pal.c2, pal.c3), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawPaint(p)
            val rr = Random(11)
            val cols3 = intArrayOf(pal.c1, pal.c2, pal.c3)
            for (k in 0 until 6) {
                val cx = rr.nextFloat() * wf
                val cy = rr.nextFloat() * hf
                val r = wf * (0.45f + rr.nextFloat() * 0.6f)
                val col = cols3[k % 3]
                p.shader = RadialGradient(cx, cy, r, Color.argb(120, Color.red(col), Color.green(col), Color.blue(col)), Color.argb(0, Color.red(col), Color.green(col), Color.blue(col)), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, p)
            }
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = wf * 0.0035f
            for (k in 0 until 14) {
                p.color = Color.argb(46, 255, 255, 255)
                c.drawCircle(wf * 0.82f, hf * 0.22f, wf * (0.12f + 0.085f * k), p)
            }
            for (k in 0 until 10) {
                p.color = Color.argb(34, 0, 0, 0)
                c.drawCircle(wf * 0.1f, hf * 0.82f, wf * (0.1f + 0.1f * k), p)
            }
            p.style = Paint.Style.FILL
            val dots = (wf * hf / 700f).toInt()
            for (k in 0 until dots) {
                val light = rr.nextBoolean()
                p.color = Color.argb(8 + rr.nextInt(18), if (light) 255 else 0, if (light) 255 else 0, if (light) 255 else 0)
                val x = rr.nextFloat() * wf
                val y = rr.nextFloat() * hf
                c.drawRect(x, y, x + 2.5f, y + 2.5f, p)
            }
            return bmp
        }
    }
}
