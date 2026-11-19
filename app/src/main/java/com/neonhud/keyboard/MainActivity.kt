package com.neonhud.keyboard

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val bg = 0xFF05070F.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(px(20), px(56), px(20), px(56))
        }
        fun title(s: String, size: Float, col: Int) = TextView(this).apply {
            text = s; textSize = size; setTextColor(col); typeface = Typeface.MONOSPACE
            setShadowLayer(14f, 0f, 0f, col)
        }
        fun neonBtn(label: String, col: Int, onClick: () -> Unit) = Button(this).apply {
            text = label; setTextColor(col); isAllCaps = false; typeface = Typeface.MONOSPACE
            background = GradientDrawable().apply {
                setColor(0xFF0B1020.toInt()); setStroke(px(2), col); cornerRadius = px(6).toFloat()
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) }
        }
        root.addView(title("NEON//HUD", 30f, 0xFF00F0FF.toInt()))
        root.addView(title("Keyboard + पूरी स्क्रीन पर उंगली के इफ़ेक्ट", 13f, 0xFFFF2BD6.toInt()))
        status = TextView(this).apply {
            textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.MONOSPACE
            setPadding(0, px(16), 0, 0)
        }
        root.addView(status)
        root.addView(neonBtn("1. Keyboard चालू करें", 0xFF00F0FF.toInt()) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(neonBtn("2. Keyboard चुनें", 0xFF9D5CFF.toInt()) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(neonBtn("3. Accessibility चालू करें", 0xFFFF2BD6.toInt()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(neonBtn("4. Shizuku अनुमति दें", 0xFFB6FF00.toInt()) {
            try {
                if (!Shizuku.pingBinder()) {
                    Toast.makeText(this, "पहले Shizuku ऐप में Start दबाएँ", Toast.LENGTH_LONG).show()
                    packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { startActivity(it) }
                } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(1)
                } else Toast.makeText(this, "अनुमति पहले से मिली है", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                Toast.makeText(this, "Shizuku नहीं मिला: इंस्टॉल करें", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(neonBtn("इफ़ेक्ट टेस्ट (डेमो)", 0xFF00F0FF.toInt()) {
            val s = NeonAccessibilityService.instance
            if (s == null) Toast.makeText(this, "पहले Accessibility चालू करें", Toast.LENGTH_SHORT).show() else s.demo()
        })
        root.addView(EditText(this).apply {
            hint = "यहाँ टाइप करके keyboard टेस्ट करें"
            setHintTextColor(0xFF667799.toInt()); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(24) }
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val sz = try {
            when {
                !Shizuku.pingBinder() -> "बंद (Start करें)"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "चालू + अनुमति OK"
                else -> "चालू, अनुमति बाकी"
            }
        } catch (e: Throwable) { "नहीं मिला" }
        status.text = "Accessibility: " + (if (NeonAccessibilityService.instance != null) "चालू" else "बंद") +
            "\nShizuku: " + sz
    }
}
