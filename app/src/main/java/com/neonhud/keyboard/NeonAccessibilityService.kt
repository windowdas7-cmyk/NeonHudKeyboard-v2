package com.neonhud.keyboard

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class NeonAccessibilityService : AccessibilityService() {
    companion object { var instance: NeonAccessibilityService? = null }

    private var overlay: EffectOverlay? = null
    private var reader: TouchReader? = null

    override fun onServiceConnected() {
        instance = this
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = EffectOverlay(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        wm.addView(v, lp)
        overlay = v

        val b = wm.currentWindowMetrics.bounds
        val w = minOf(b.width(), b.height()); val h = maxOf(b.width(), b.height())
        reader = TouchReader(w, h, object : TouchReader.Callback {
            override fun down(s: Int, x: Float, y: Float) { v.post { v.down(s, x, y) } }
            override fun move(s: Int, x: Float, y: Float) { v.post { v.move(s, x, y) } }
            override fun up(s: Int) { v.post { v.up(s) } }
        }).also { it.start() }
    }

    fun demo() { overlay?.post { overlay?.demo() } }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        reader?.stop()
        overlay?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        instance = null
        super.onDestroy()
    }
}
