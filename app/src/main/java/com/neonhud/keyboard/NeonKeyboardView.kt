package com.neonhud.keyboard

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** type: 0=char 1=shift 2=delete 3=symbols toggle 4=enter 5=space */
class Key(val label: String, val out: String = label, val w: Float = 1f, val type: Int = 0)

class NeonKeyboardView(ctx: Context, private val l: Listener) : View(ctx) {
    interface Listener { fun onText(t: String); fun onDelete(); fun onEnter() }

    private class Fx(val x: Float, val y: Float, val color: Int, val t0: Long) {
        val angles = FloatArray(8) { (it * PI / 4 + Random.nextFloat() * 0.4).toFloat() }
    }

    private val CYAN = 0xFF00F0FF.toInt()
    private val MAGENTA = 0xFFFF2BD6.toInt()
    private val PURPLE = 0xFF9D5CFF.toInt()
    private val LIME = 0xFFB6FF00.toInt()

    private var shift = false
    private var symbols = false
    private var rows = build()
    private var bottomInset = 0
    private val down = HashMap<Int, Key>()
    private val fxs = ArrayList<Fx>()
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.5f) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(3f)
        maskFilter = BlurMaskFilter(dp(6f), BlurMaskFilter.Blur.NORMAL)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
    }
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textSize = dp(10f); color = CYAN
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f) }
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); strokeCap = Paint.Cap.ROUND
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setOnApplyWindowInsetsListener { _, ins ->
            val b = ins.getInsets(WindowInsets.Type.navigationBars()).bottom
            if (b != bottomInset) { bottomInset = b; requestLayout() }
            ins
        }
    }

    private fun ch(c: Char) = Key(if (shift) c.uppercase() else c.toString())

    private fun build(): List<List<Key>> =
        if (!symbols) listOf(
            "qwertyuiop".map { ch(it) },
            "asdfghjkl".map { ch(it) },
            listOf(Key("⇧", w = 1.5f, type = 1)) + "zxcvbnm".map { ch(it) } + Key("⌫", w = 1.5f, type = 2),
            listOf(Key("?123", w = 1.5f, type = 3), Key(","), Key("SPACE", " ", 4f, 5), Key("."), Key("↵", w = 1.5f, type = 4))
        ) else listOf(
            "1234567890".map { Key(it.toString()) },
            "@#\$%&-+()/".map { Key(it.toString()) },
            "*\"':;!?".map { Key(it.toString()) } + Key("⌫", w = 1.5f, type = 2),
            listOf(Key("ABC", w = 1.5f, type = 3), Key("₹"), Key("SPACE", " ", 4f, 5), Key("."), Key("↵", w = 1.5f, type = 4))
        )

    private fun colorFor(k: Key) = when (k.type) {
        1, 2 -> MAGENTA; 3 -> LIME; 4 -> PURPLE; 5 -> PURPLE; else -> CYAN
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (dp(280f) + bottomInset).toInt())
    }

    private fun layout(): List<Pair<RectF, Key>> {
        val out = ArrayList<Pair<RectF, Key>>()
        val top = dp(22f); val pad = dp(6f); val gap = dp(5f)
        val availH = height - bottomInset - top - pad
        val rowH = (availH - gap * 3) / 4
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.w.toDouble() }.toFloat()
            val unit = (width - 2 * pad - gap * (row.size - 1)) / total
            var x = pad
            val y = top + ri * (rowH + gap)
            for (k in row) {
                val kw = unit * k.w
                out.add(RectF(x, y, x + kw, y + rowH) to k)
                x += kw + gap
            }
        }
        return out
    }

    private fun chamfer(r: RectF, c: Float) = Path().apply {
        moveTo(r.left + c, r.top); lineTo(r.right, r.top); lineTo(r.right, r.bottom - c)
        lineTo(r.right - c, r.bottom); lineTo(r.left, r.bottom); lineTo(r.left, r.top + c); close()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF04060D.toInt())
        c.drawText("[ NEON//HUD ]  SYSTEM ONLINE", dp(10f), dp(15f), hud)
        val now = SystemClock.uptimeMillis()
        for ((r, k) in layout()) {
            val col = colorFor(k)
            val pressed = down.containsValue(k) || (k.type == 1 && shift)
            val path = chamfer(r, dp(7f))
            fill.color = if (pressed) (col and 0x00FFFFFF) or 0x77000000 else 0xFF0B1020.toInt()
            c.drawPath(path, fill)
            glow.color = col; glow.alpha = if (pressed) 255 else 110
            c.drawPath(path, glow)
            stroke.color = col
            c.drawPath(path, stroke)
            text.textSize = if (k.label.length > 1) dp(13f) else dp(20f)
            text.color = if (pressed) Color.WHITE else col
            c.drawText(k.label, r.centerX(), r.centerY() - (text.ascent() + text.descent()) / 2, text)
        }
        val it = fxs.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val p = (now - f.t0) / 500f
            if (p >= 1f) { it.remove(); continue }
            val a = ((1 - p) * 255).toInt()
            ring.color = f.color; ring.alpha = a
            c.drawCircle(f.x, f.y, dp(10f) + p * dp(55f), ring)
            spark.color = f.color; spark.alpha = a
            for (ang in f.angles) {
                val r0 = dp(8f) + p * dp(45f); val r1 = r0 + dp(10f) * (1 - p)
                c.drawLine(f.x + cos(ang) * r0, f.y + sin(ang) * r0, f.x + cos(ang) * r1, f.y + sin(ang) * r1, spark)
            }
        }
        if (fxs.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun keyAt(x: Float, y: Float): Key? = layout().firstOrNull { it.first.contains(x, y) }?.second

    private val repeat = object : Runnable {
        override fun run() {
            if (down.values.any { it.type == 2 }) { l.onDelete(); handler.postDelayed(this, 50) }
        }
    }

    private fun press(k: Key, x: Float, y: Float) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        fxs.add(Fx(x, y, colorFor(k), SystemClock.uptimeMillis()))
        when (k.type) {
            0 -> { l.onText(k.out); if (shift) { shift = false; rows = build() } }
            1 -> { shift = !shift; rows = build() }
            2 -> { l.onDelete(); handler.postDelayed(repeat, 400) }
            3 -> { symbols = !symbols; shift = false; rows = build() }
            4 -> l.onEnter()
            5 -> l.onText(" ")
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i); val y = e.getY(i)
                val k = keyAt(x, y)
                if (k != null) { down[e.getPointerId(i)] = k; press(k, x, y) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> down.remove(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> down.clear()
        }
        invalidate()
        return true
    }
}
