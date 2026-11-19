package com.neonhud.keyboard

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** पूरी स्क्रीन का पारदर्शी overlay: उंगली जहाँ है वहीं neon trail, ripple और edge glow। */
class EffectOverlay(ctx: Context) : View(ctx) {
    private class P(val x: Float, val y: Float, val t: Long)
    private class Trail(val color: Int, val edge: Int, val x0: Float, val y0: Float, val t0: Long) {
        val pts = ArrayList<P>()
        var hx = x0; var hy = y0; var endT = 0L
    }

    private val CYAN = 0xFF00F0FF.toInt()
    private val MAGENTA = 0xFFFF2BD6.toInt()
    private val PURPLE = 0xFF9D5CFF.toInt()
    private val LIME = 0xFFB6FF00.toInt()
    private val live = arrayOfNulls<Trail>(16)
    private val dead = ArrayList<Trail>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val LIFE = 450f

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun fade(col: Int, a: Float) = (col and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    fun down(s: Int, x: Float, y: Float) {
        val e = dp(28f)
        val w = width.toFloat(); val h = height.toFloat()
        // edge: 1=बायाँ(Back) 2=दायाँ(Back) 3=नीचे(Home/Recents) 4=ऊपर(Notification)
        val edge = when {
            x < e -> 1; x > w - e -> 2; y > h - e * 1.6f -> 3; y < e -> 4; else -> 0
        }
        val col = when (edge) { 1, 2 -> MAGENTA; 3 -> LIME; 4 -> PURPLE; else -> CYAN }
        val t = Trail(col, edge, x, y, SystemClock.uptimeMillis())
        t.pts.add(P(x, y, t.t0))
        live[s.coerceIn(0, 15)] = t
        invalidate()
    }

    fun move(s: Int, x: Float, y: Float) {
        val t = live[s.coerceIn(0, 15)] ?: return
        t.hx = x; t.hy = y
        t.pts.add(P(x, y, SystemClock.uptimeMillis()))
        if (t.pts.size > 80) t.pts.removeAt(0)
        invalidate()
    }

    fun up(s: Int) {
        val i = s.coerceIn(0, 15)
        val t = live[i] ?: return
        t.endT = SystemClock.uptimeMillis()
        dead.add(t); live[i] = null
        invalidate()
    }

    fun demo() {
        val w = width.toFloat(); val h = height.toFloat()
        val s = 15
        down(s, w * 0.2f, h * 0.7f)
        var i = 1
        val step = object : Runnable {
            override fun run() {
                if (i > 24) { up(s); return }
                val f = i / 24f
                move(s, w * (0.2f + 0.6f * f), h * (0.7f - 0.4f * f) + (sin(f * 6.28f) * dp(40f)))
                i++; postDelayed(this, 16)
            }
        }
        postDelayed(step, 16)
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        for (t in live) if (t != null) drawTrail(c, t, now, 1f, true)
        val it = dead.iterator()
        while (it.hasNext()) {
            val t = it.next()
            val k = 1f - (now - t.endT) / LIFE
            if (k <= 0f) { it.remove(); continue }
            drawTrail(c, t, now, k, false)
        }
        if (dead.isNotEmpty() || live.any { it != null }) postInvalidateOnAnimation()
    }

    private fun drawTrail(c: Canvas, t: Trail, now: Long, k: Float, isLive: Boolean) {
        // शुरुआती ripple + चिंगारियाँ
        val rp = (now - t.t0) / 550f
        if (rp < 1f) {
            p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = dp(2.5f)
            p.color = fade(t.color, (1 - rp) * k)
            c.drawCircle(t.x0, t.y0, dp(12f) + rp * dp(70f), p)
            for (i in 0..7) {
                val a = (i * PI / 4).toFloat()
                val r0 = dp(10f) + rp * dp(55f); val r1 = r0 + dp(10f) * (1 - rp)
                c.drawLine(t.x0 + cos(a) * r0, t.y0 + sin(a) * r0, t.x0 + cos(a) * r1, t.y0 + sin(a) * r1, p)
            }
        }
        // edge glow (Back / Home-Recents / Notification पर उंगली के पास)
        if (t.edge != 0) {
            val ex = when (t.edge) { 1 -> 0f; 2 -> width.toFloat(); else -> t.hx }
            val ey = when (t.edge) { 3 -> height.toFloat(); 4 -> 0f; else -> t.hy }
            val gx = if (t.edge == 1 || t.edge == 2) ex else t.hx
            val gy = if (t.edge == 3) ey else if (t.edge == 4) ey else t.hy
            val r = dp(170f)
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(gx, gy, r, fade(t.color, 0.55f * k), 0, Shader.TileMode.CLAMP)
            c.drawCircle(gx, gy, r, p)
            p.shader = null
        }
        // trail
        while (t.pts.size > (if (isLive) 1 else 0) && now - t.pts[0].t > LIFE) t.pts.removeAt(0)
        p.style = Paint.Style.STROKE
        for (i in 1 until t.pts.size) {
            val a = t.pts[i - 1]; val b = t.pts[i]
            val f = (1f - ((now - b.t) / LIFE).coerceIn(0f, 1f)) * k
            p.strokeWidth = dp(16f) * f + dp(2f); p.color = fade(t.color, 0.25f * f)
            c.drawLine(a.x, a.y, b.x, b.y, p)
            p.strokeWidth = dp(7f) * f + dp(1.5f); p.color = fade(t.color, 0.7f * f)
            c.drawLine(a.x, a.y, b.x, b.y, p)
            p.strokeWidth = dp(2f) * f + dp(0.5f); p.color = fade(Color.WHITE, 0.9f * f)
            c.drawLine(a.x, a.y, b.x, b.y, p)
        }
        // उंगली के नीचे चमकता head
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(t.hx, t.hy, dp(36f), fade(t.color, 0.8f * k), 0, Shader.TileMode.CLAMP)
        c.drawCircle(t.hx, t.hy, dp(36f), p)
        p.shader = null
        p.color = fade(Color.WHITE, 0.95f * k)
        c.drawCircle(t.hx, t.hy, dp(4f), p)
    }
}
