package com.poco.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import kotlin.math.max

class ClickAccessibilityService : AccessibilityService() {
    companion object { const val ACTION_SHOW = "com.poco.autoclicker.SHOW" }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var panel: LinearLayout? = null
    private var picker: FrameLayout? = null
    private var running = false
    private var selectingPoint = false
    private var cpsTarget = 10
    private var clicks = 0L
    private var points = mutableListOf<Pair<Float, Float>>()
    private var pointIndex = 0
    private var counter: TextView? = null
    private var speedLabel: TextView? = null
    private var actualLabel: TextView? = null
    private var inFlight = false
    private var windowStartMs = 0L
    private var windowClicks = 0
    private var actualCps = 0
    private val loop = object : Runnable { override fun run() { if (running) tapNext() } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        // Accessibility services are started/bound by Android after the user enables them.
        showPanel()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopLoop() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW) showPanel()
        return START_STICKY
    }

    private fun showPanel() {
        if (panel != null) return
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            setBackgroundColor(0xEE18212D.toInt())
        }
        p.addView(TextView(this).apply {
            text = "POCO CLICKER TURBO"; textSize = 16f; setTextColor(Color.WHITE)
        })
        speedLabel = TextView(this).apply {
            setTextColor(0xFF65E6A2.toInt()); text = "Целевая скорость: $cpsTarget CPS"
        }
        p.addView(speedLabel)
        p.addView(SeekBar(this).apply {
            max = 999; progress = cpsTarget - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, user: Boolean) {
                    cpsTarget = value + 1
                    speedLabel?.text = "Целевая скорость: $cpsTarget CPS"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        counter = TextView(this).apply {
            text = "Точек: ${points.size} | Всего: $clicks"; setTextColor(Color.WHITE)
        }
        p.addView(counter)
        actualLabel = TextView(this).apply {
            text = "Фактическая скорость: 0 CPS"; setTextColor(0xFFB9C7D8.toInt())
        }
        p.addView(actualLabel)
        fun btn(text: String, action: () -> Unit) {
            p.addView(Button(this).apply { this.text = text; setOnClickListener { action() } })
        }
        btn("Выбрать точку касанием") { beginPointSelection() }
        btn("Старт / Пауза") { if (running) stopLoop() else startLoop() }
        btn("Удалить последнюю точку") {
            if (points.isNotEmpty()) points.removeAt(points.lastIndex)
            updateStats()
        }
        btn("Очистить точки и счётчик") {
            stopLoop(); points.clear(); clicks = 0; pointIndex = 0; actualCps = 0
            updateStats()
        }
        btn("Закрыть панель") { stopLoop(); removePanel() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 12; y = 120 }
        panel = p
        try { wm?.addView(p, params) } catch (_: Exception) { panel = null }
    }

    private fun beginPointSelection() {
        stopLoop()
        if (panel != null) {
            try { wm?.removeView(panel) } catch (_: Exception) {}
            panel = null
        }
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x2200A86B)
            isClickable = true
            addView(TextView(this@ClickAccessibilityService).apply {
                text = "Коснись нужного места на экране"
                textSize = 18f; setTextColor(Color.WHITE)
                setBackgroundColor(0xCC18212D.toInt())
                setPadding(24, 18, 24, 18)
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ))
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val location = IntArray(2)
                    getLocationOnScreen(location)
                    points.add(Pair(event.rawX, event.rawY))
                    selectingPoint = false
                    removePicker()
                    showPanel()
                    updateStats()
                    true
                } else true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        picker = overlay
        selectingPoint = true
        try { wm?.addView(overlay, params) } catch (_: Exception) {
            picker = null; selectingPoint = false; showPanel()
        }
    }

    private fun removePicker() {
        picker?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        picker = null
    }

    private fun startLoop() {
        if (points.isEmpty()) {
            val dm = resources.displayMetrics
            points.add(Pair(dm.widthPixels / 2f, dm.heightPixels / 2f))
            updateStats()
        }
        running = true
        if (!inFlight) handler.post(loop)
    }

    private fun stopLoop() {
        running = false
        handler.removeCallbacks(loop)
    }

    private fun tapNext() {
        if (!running || inFlight || points.isEmpty()) return
        val (x, y) = points[pointIndex % points.size]
        pointIndex++
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()
        inFlight = true
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { finishTap(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { finishTap(false) }
        }, null)
        if (!accepted) finishTap(false)
    }

    private fun finishTap(completed: Boolean) {
        inFlight = false
        if (completed) {
            clicks++
            val now = System.currentTimeMillis()
            if (windowStartMs == 0L) windowStartMs = now
            windowClicks++
            val elapsed = now - windowStartMs
            if (elapsed >= 1000L) {
                actualCps = (windowClicks * 1000L / max(1L, elapsed)).toInt()
                windowClicks = 0
                windowStartMs = now
                actualLabel?.text = "Фактическая скорость: $actualCps CPS"
            }
            updateStats()
        }
        if (running) {
            // Accessibility gestures are asynchronous; the system may impose its own minimum interval.
            val delay = max(0L, (1000.0 / cpsTarget).toLong() - 1L)
            handler.postDelayed(loop, delay)
        }
    }

    private fun updateStats() {
        counter?.text = "Точек: ${points.size} | Всего кликов: $clicks"
    }

    private fun removePanel() {
        panel?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    override fun onDestroy() {
        stopLoop()
        removePicker()
        removePanel()
        super.onDestroy()
    }
}
