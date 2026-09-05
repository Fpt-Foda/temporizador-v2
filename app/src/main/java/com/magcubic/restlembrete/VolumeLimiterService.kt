package com.magcubic.restlembrete

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Serviço opcional do M-s. Ele recebe apenas as teclas de volume, consome-as e
 * mostra uma barra própria. Assim a barra do Android não aparece junto.
 */
class VolumeLimiterService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var volumeOverlay: View? = null
    private var overlayAdded = false
    private val hideOverlay = Runnable { removeOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        enforceLimit(this)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Prefs.isMsEnabled(this)) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        // Consome DOWN e UP: o projetor não mostra sua barra original por trás.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            // A barra anda de 1 em 1; o volume real anda mais devagar conforme
            // o limite. Com M-s em 50%, 100% na barra equivale a 50% de som.
            changeVisibleVolume(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
        }
        return true
    }

    private fun changeVisibleVolume(delta: Int) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val limit = Prefs.getMsLimit(this)
        val currentVisible = Prefs.getMsVisibleVolume(this).let { saved ->
            if (saved >= 0) saved else realToVisible(streamToPercent(audio.getStreamVolume(AudioManager.STREAM_MUSIC), audio), limit)
        }
        val visible = (currentVisible + delta).coerceIn(0, 100)
        Prefs.setMsVisibleVolume(this, visible)

        // Não é uma barra "falsa": ela representa 0..100% do limite escolhido.
        // Ex.: limite 50% -> posição 80% da barra toca em 40% do projetor.
        val realPercent = (visible * limit / 100f).roundToInt()
        setRealVolume(audio, realPercent)
        showOverlay(visible, limit)
    }

    private fun streamToPercent(index: Int, audio: AudioManager): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return (index * 100f / max).roundToInt().coerceIn(0, 100)
    }

    private fun realToVisible(realPercent: Int, limit: Int): Int {
        return (realPercent * 100f / limit.coerceAtLeast(1)).roundToInt().coerceIn(0, 100)
    }

    private fun setRealVolume(audio: AudioManager, percent: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val index = (max * percent / 100f).roundToInt().coerceIn(0, max)
        // Sem FLAG_SHOW_UI: só a barra M-s aparece, sem duplicar a do Android.
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }

    private fun showOverlay(visible: Int, limit: Int) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 18, 30, 18)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E91B1B1B"))
                cornerRadius = 18f
                setStroke(2, Color.parseColor("#777777"))
            }
        }
        val label = TextView(this).apply {
            text = "M-s  ${visible}%"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = visible
            progressDrawable.setTint(Color.parseColor("#53C8FF"))
            layoutParams = LinearLayout.LayoutParams(420, 18).apply { topMargin = 10 }
        }
        root.addView(label)
        root.addView(bar)

        removeOverlay()
        volumeOverlay = root
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
        }
        try {
            wm.addView(root, params)
            overlayAdded = true
            handler.removeCallbacks(hideOverlay)
            handler.postDelayed(hideOverlay, 1800)
        } catch (_: Exception) {
            overlayAdded = false
        }
    }

    private fun removeOverlay() {
        handler.removeCallbacks(hideOverlay)
        val view = volumeOverlay ?: return
        if (overlayAdded) {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
            } catch (_: Exception) {
            }
        }
        volumeOverlay = null
        overlayAdded = false
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        fun enforceLimit(context: Context) {
            if (!Prefs.isMsEnabled(context)) return
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val limitIndex = (max * Prefs.getMsLimit(context) / 100f).roundToInt()
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) > limitIndex) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, limitIndex, 0)
            }
            if (Prefs.getMsVisibleVolume(context) < 0) {
                Prefs.setMsVisibleVolume(context, (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt())
            }
        }
    }
}
