package com.poco.autoclicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFF10151D.toInt())
        }
        fun label(s: String, size: Float = 16f) = TextView(this).apply {
            text = s; textSize = size; setTextColor(0xFFF2F5F8.toInt())
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
        }
        fun button(s: String, action: () -> Unit) = Button(this).apply {
            text = s; setOnClickListener { action() }
        }
        layout.addView(label("POCO AUTO CLICKER", 24f))
        layout.addView(label("Плавающий автокликер для игр и приложений"))
        layout.addView(button("1. Разрешить отображение поверх окон") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        layout.addView(button("2. Включить службу специальных возможностей") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(button("3. Открыть настройки автокликера") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(label("После включения службы плавающая панель появится автоматически. Если она не появилась, выключи и снова включи службу.", 14f))
        layout.addView(label("400 CPS — целевая настройка, а не гарантия: Android ограничивает скорость жестов.", 13f))
        setContentView(layout)
    }
}
