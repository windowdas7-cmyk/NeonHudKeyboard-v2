package com.neonhud.keyboard

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Shizuku (shell अनुमति) से टचस्क्रीन के raw events पढ़ता है ताकि हर ऐप/सिस्टम जेस्चर में उंगली की जगह पता चले। */
class TouchReader(private val screenW: Int, private val screenH: Int, private val cb: Callback) {
    interface Callback {
        fun down(s: Int, x: Float, y: Float)
        fun move(s: Int, x: Float, y: Float)
        fun up(s: Int)
    }

    @Volatile private var running = true
    @Volatile private var proc: Process? = null
    private val ws = Regex("\\s+")

    fun start() { Thread { loop() }.apply { isDaemon = true; start() } }
    fun stop() { running = false; try { proc?.destroy() } catch (_: Throwable) {} }

    private fun loop() {
        while (running) {
            try {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) runOnce()
            } catch (_: Throwable) {}
            try { Thread.sleep(3000) } catch (_: InterruptedException) {}
        }
    }

    private fun sh(vararg cmd: String): Process {
        val m = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, arrayOf(*cmd), null, null) as Process
    }

    private fun maxOf(line: String): Int =
        Regex("max (\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun runOnce() {
        val info = sh("getevent", "-lp")
        val txt = info.inputStream.bufferedReader().readText()
        try { info.destroy() } catch (_: Throwable) {}

        var dev: String? = null
        var cur: String? = null
        var cx = 0; var cy = 0; var mx = 0; var my = 0
        for (raw in txt.lines()) {
            val t = raw.trim()
            if (t.startsWith("add device")) { cur = t.substringAfter(": ").trim(); cx = 0; cy = 0 }
            else if (t.contains("ABS_MT_POSITION_X")) cx = maxOf(t)
            else if (t.contains("ABS_MT_POSITION_Y")) {
                cy = maxOf(t)
                if (dev == null && cur != null && cx > 0 && cy > 0) { dev = cur; mx = cx; my = cy }
            }
        }
        if (dev == null) return

        val pr = sh("getevent", "-l", dev)
        proc = pr
        val rd = pr.inputStream.bufferedReader()
        val xs = IntArray(16); val ys = IntArray(16)
        val act = BooleanArray(16); val dirty = BooleanArray(16)
        val nd = BooleanArray(16); val nu = BooleanArray(16)
        var slot = 0
        fun sx(v: Int) = v * screenW / (mx + 1f)
        fun sy(v: Int) = v * screenH / (my + 1f)

        while (running) {
            val line = rd.readLine() ?: break
            val parts = line.trim().split(ws).toMutableList()
            if (parts.isNotEmpty() && parts[0].endsWith(":")) parts.removeAt(0)
            if (parts.size < 2) continue
            when (parts[1]) {
                "ABS_MT_SLOT" -> slot = parts[2].toLong(16).toInt().coerceIn(0, 15)
                "ABS_MT_TRACKING_ID" ->
                    if (parts[2].equals("ffffffff", true)) nu[slot] = true else nd[slot] = true
                "ABS_MT_POSITION_X" -> { xs[slot] = parts[2].toLong(16).toInt(); dirty[slot] = true }
                "ABS_MT_POSITION_Y" -> { ys[slot] = parts[2].toLong(16).toInt(); dirty[slot] = true }
                "SYN_REPORT" -> {
                    for (s in 0..15) {
                        if (nu[s]) { if (act[s]) cb.up(s); act[s] = false }
                        else if (nd[s]) { act[s] = true; cb.down(s, sx(xs[s]), sy(ys[s])) }
                        else if (dirty[s] && act[s]) cb.move(s, sx(xs[s]), sy(ys[s]))
                        nu[s] = false; nd[s] = false; dirty[s] = false
                    }
                }
            }
        }
        for (s in 0..15) if (act[s]) cb.up(s)
    }
}
