package com.example.curlwall

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val TH = 1.12f
private const val TWO_PI = (2 * PI).toFloat()

private fun hash(a: Int, b: Int): Float {
    val v = sin(a * 127.1 + b * 311.7) * 43758.5453
    return (v - floor(v)).toFloat()
}

private fun sm(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

class CurlWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = CurlEngine()

    private class Ring(val x: Float, val y: Float, val t: Long)

    inner class CurlEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val runner = Runnable { drawFrame() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        private val path = Path()
        private var frameMs = 33L
        private var bg = Color.WHITE
        private var fg = Color.BLACK
        private var anim = 0
        private var cover = 0.66f
        private var speed = 1f
        private var sizeMul = 1f
        private var tiltOn = true
        private val flow = floatArrayOf(14f, 0f, 6f, 34f)
        private val waveM = floatArrayOf(1f, 0.4f, 0.5f, 1.4f)
        private val driftM = floatArrayOf(1f, 0.4f, 2.2f, 0.6f)
        private val breathM = floatArrayOf(1f, 3f, 1f, 0.6f)

        private fun loadCfg() {
            val p = Config.prefs(this@CurlWallpaperService)
            bg = p.getInt("bg", Color.WHITE)
            fg = Config.fgFor(bg)
            paint.color = fg
            anim = p.getInt("anim", 0).coerceIn(0, 3)
            cover = p.getFloat("cover", 0.66f)
            speed = p.getFloat("speed", 1f)
            sizeMul = p.getFloat("size", 1f)
            tiltOn = p.getBoolean("tilt", true)
            if (!tiltOn) tiltTarget = 0f
            frameMs = if (p.getBoolean("fps60", false)) 16L else 33L
        }

        private var visible = false

        private var k = 1f
        private var W = 400f
        private var H = 800f
        private var c = 66f
        private var cols = 8

        private var S = 0f
        private var vS = 0f
        private var X = 0f
        private var vX = 0f
        private var energy = HashMap<Long, Float>()
        private var nextEnergy = HashMap<Long, Float>()
        private val rings = ArrayList<Ring>()
        private var pOn = false
        private var pDown = false
        private var px = -9999f
        private var py = -9999f
        private var tilt = 0f
        private var tiltTarget = 0f

        private val t0 = SystemClock.uptimeMillis()
        private var lastT = t0

        private val qx = floatArrayOf(-1f, 1f, 1f, -1f)
        private val qy = floatArrayOf(-1f, -1f, 1f, 1f)

        private val sensors by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(true)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            k = width / 400f
            W = 400f
            H = height / k
            c = max(48f, W / 6f)
            cols = ceil(W / c).toInt() + 3
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                loadCfg()
                lastT = SystemClock.uptimeMillis()
                if (tiltOn) sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                    sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
                handler.removeCallbacks(runner)
                handler.post(runner)
            } else {
                sensors.unregisterListener(this)
                handler.removeCallbacks(runner)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            sensors.unregisterListener(this)
            handler.removeCallbacks(runner)
            super.onSurfaceDestroyed(holder)
        }

        override fun onTouchEvent(e: MotionEvent) {
            val x = e.x / k
            val y = e.y / k
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pDown = true; pOn = true; px = x; py = y
                    rings.add(Ring(x, y, SystemClock.uptimeMillis()))
                }
                MotionEvent.ACTION_MOVE -> if (pDown) {
                    vS += -(y - py) * 0.5f
                    vX += (x - px) * 0.3f
                    px = x; py = y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pDown = false; pOn = false; px = -9999f; py = -9999f
                }
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            tiltTarget = (-event.values[0] / 9.8f).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (canvas != null) render(canvas)
            } catch (_: Exception) {
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                }
            }
            handler.removeCallbacks(runner)
            if (visible) handler.postDelayed(runner, frameMs)
        }

        private fun render(canvas: Canvas) {
            canvas.drawColor(bg)
            canvas.save()
            canvas.scale(k, k)

            val now = SystemClock.uptimeMillis()
            val t = (now - t0) / 1000f * speed
            val dt = min(0.05f, (now - lastT) / 1000f)
            lastT = now

            S += (flow[anim] * speed + vS) * dt
            X += vX * dt
            val damp = 0.15.pow(dt.toDouble()).toFloat()
            vS *= damp
            vX *= damp
            tilt += (tiltTarget - tilt) * min(1f, dt * 3f)

            val Rc = H * cover * (1f + 0.03f * sin(t * 0.25f))
            val D = Rc * 2.2f
            val yBase = H + c * 0.9f
            val cx = W / 2f
            val h = c * 0.34f * sizeMul
            val base = floor(S / c).toInt()
            val frac = S - base * c
            val Xt = X + tilt * c * 1.2f
            val colShift = floor(Xt / c).toInt()
            val xFrac = ((Xt % c) + c) % c

            rings.removeAll { now - it.t >= 2800 }
            nextEnergy.clear()

            var j = -3
            while (true) {
                val s0 = j * c - frac
                if (s0 / Rc > TH) break
                val rowId = j + base
                val sN = min(1f, max(0f, s0) / (Rc * TH))

                for (i in -1 until cols) {
                    val col = i - colShift
                    val id = rowId * 1000L + col
                    val hh = hash(rowId, col)
                    val h2 = hash(col + 7, rowId + 3)
                    val h3 = hash(rowId + 11, col + 5)

                    val thr = 0.05f + 0.5f * sN * sN
                    val vis = sm(thr, thr + 0.25f, hh)
                    val wave = 0.5f + 0.5f * sin(t * 0.55f * waveM[anim] - rowId * 0.45f + col * 0.35f)
                    val own = 0.5f + 0.5f * sin(t * (0.25f + h2 * 0.6f) + h3 * TWO_PI)
                    val lum = 0.15f + 0.85f * (0.55f * wave + 0.45f * own)
                    val fade = 1f - sN.pow(1.6f) * 0.55f
                    var a = vis * (if (sN < 0.08f) 1f - (1f - lum) * sN / 0.08f * 0.7f else lum) * fade
                    a *= 1f - sm(0.82f, 1f, sN)

                    val sp = 0.25f + h2 * 0.9f
                    val amp = (0.04f + h3 * 0.14f) * driftM[anim]
                    val mx = sin(t * sp + hh * TWO_PI) * c * amp
                    val ms = cos(t * sp * 0.8f + h3 * TWO_PI) * c * amp
                    val br = 1f + 0.1f * breathM[anim] * sin(t * (0.3f + h3 * 0.7f) + h2 * TWO_PI)

                    val x0 = i * c + xFrac - c * 0.5f + mx
                    val sc0 = s0 + ms

                    val th0 = max(0f, sc0) / Rc
                    val y0 = if (sc0 < 0f) sc0 else Rc * sin(th0)
                    val z0 = if (sc0 < 0f) 0f else Rc * (1f - cos(th0))
                    val f0 = D / (D + z0)
                    val sx0 = cx + (x0 - cx) * f0
                    val sy0 = yBase - y0 * f0

                    var tg = 0f
                    var dxn = 0f
                    var dyn = 0f
                    if (pOn) {
                        val dx = sx0 - px
                        val dy = sy0 - py
                        val d = max(0.001f, hypot(dx, dy))
                        tg = exp(-(d * d) / (2f * 100f * 100f)) * 0.8f
                        dxn = dx / d; dyn = dy / d
                    }
                    for (r in rings) {
                        val age = (now - r.t) / 1000f
                        val dx = sx0 - r.x
                        val dy = sy0 - r.y
                        val d = max(0.001f, hypot(dx, dy))
                        val kk = (d - age * 480f) / 80f
                        val w = 0.6f * exp(-age * 1.5f) * exp(-kk * kk)
                        if (w > tg) { tg = w; dxn = dx / d; dyn = dy / d }
                    }
                    val e0 = energy[id] ?: 0f
                    val e = e0 + (tg - e0) * (if (tg > e0) 0.18f else 0.06f)
                    if (e > 0.003f) nextEnergy[id] = e

                    if (a < 0.015f && e < 0.05f) continue

                    val cxp = x0 + dxn * e * c * 0.22f
                    val csp = sc0 - dyn * e * c * 0.22f
                    val lift = e * c * 0.45f
                    val g = (1f - 0.72f * sN.pow(0.9f)) * br * (1f + 0.12f * e)

                    path.reset()
                    for (q in 0 until 4) {
                        val qxp = cxp + qx[q] * h * g
                        val qsp = csp + qy[q] * h * g
                        val th = max(0f, qsp) / Rc
                        val yy = if (qsp < 0f) qsp else Rc * sin(th)
                        val zz = (if (qsp < 0f) 0f else Rc * (1f - cos(th))) - lift
                        val f = D / (D + zz)
                        val sx = cx + (qxp - cx) * f
                        val sy = yBase - yy * f
                        if (q == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                    }
                    path.close()
                    val al = min(1f, a + e * 0.5f * (if (a > 0.02f) 1f else 0.6f))
                    paint.alpha = (al.coerceIn(0f, 1f) * 255f).toInt()
                    canvas.drawPath(path, paint)
                }
                j++
            }
            val tmp = energy; energy = nextEnergy; nextEnergy = tmp
            canvas.restore()
        }
    }
}
