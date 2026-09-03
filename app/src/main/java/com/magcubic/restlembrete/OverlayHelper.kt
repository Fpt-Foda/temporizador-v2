package com.magcubic.restlembrete

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object OverlayHelper {

    private var hudRightView: TextView? = null
    private var hudLeftView: TextView? = null

    private fun tocarAlerta() {
        val tom = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        tom.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1_200)
        Handler(Looper.getMainLooper()).postDelayed({ tom.release() }, 1_300)
    }

    private fun criarBotaoTv(
        context: Context,
        texto: String,
        corPadrao: Int = Color.parseColor("#262626"),
        corFoco: Int = Color.parseColor("#FFD700")
    ): Button {
        val normal = GradientDrawable().apply {
            setColor(corPadrao)
            cornerRadius = 14f
            setStroke(2, Color.parseColor("#555555"))
        }

        val focado = GradientDrawable().apply {
            setColor(corFoco)
            cornerRadius = 14f
            setStroke(5, Color.WHITE)
        }

        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focado)
            addState(intArrayOf(android.R.attr.state_pressed), focado)
            addState(intArrayOf(), normal)
        }

        return Button(context).apply {
            text = texto
            background = states
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(30, 16, 30, 16)

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setTextColor(Color.BLACK)
                    scaleX = 1.08f
                    scaleY = 1.08f
                } else {
                    setTextColor(Color.WHITE)
                    scaleX = 1.0f
                    scaleY = 1.0f
                }
            }
            setTextColor(Color.WHITE)
        }
    }

    // --- HUD DIREITO (TEMPO RESTANTE) ---
    fun showOrUpdateHudRight(context: Context, texto: String, textSizeSp: Float, marginPx: Int) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = marginPx
                y = marginPx
            }

            if (hudRightView == null) {
                hudRightView = TextView(context).apply {
                    setBackgroundColor(Color.parseColor("#E6000000"))
                    setTextColor(Color.WHITE)
                    typeface = Typeface.MONOSPACE
                    setPadding(16, 6, 16, 6)
                }
                wm.addView(hudRightView, params)
            } else {
                wm.updateViewLayout(hudRightView, params)
            }

            hudRightView?.apply {
                text = texto
                textSize = textSizeSp
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeHudRight(context: Context) {
        try {
            if (hudRightView != null) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(hudRightView)
                hudRightView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- HUD ESQUERDO (HORA ATUAL REAL) ---
    fun showOrUpdateHudLeft(context: Context, texto: String, textSizeSp: Float, marginPx: Int) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = marginPx
                y = marginPx
            }

            if (hudLeftView == null) {
                hudLeftView = TextView(context).apply {
                    setBackgroundColor(Color.parseColor("#E6000000"))
                    setTextColor(Color.WHITE)
                    typeface = Typeface.MONOSPACE
                    setPadding(16, 6, 16, 6)
                }
                wm.addView(hudLeftView, params)
            } else {
                wm.updateViewLayout(hudLeftView, params)
            }

            hudLeftView?.apply {
                text = texto
                textSize = textSizeSp
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeHudLeft(context: Context) {
        try {
            if (hudLeftView != null) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(hudLeftView)
                hudLeftView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- TELA CHEIA DE AVISO ---
    fun show(
        context: Context,
        titulo: String,
        mensagem: String,
        isTest: Boolean = false,
        onSimDesligar: () -> Unit,
        onNaoIgnorar: () -> Unit,
        onAdiarMinutos: (minutos: Int) -> Unit,
        onFecharTeste: () -> Unit = {}
    ) {
        try {
            tocarAlerta()
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                flags,
                PixelFormat.TRANSLUCENT
            )

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.BLACK)
                setPadding(40, 30, 40, 30)
            }

            val tituloView = TextView(context).apply {
                text = titulo
                setTextColor(Color.RED)
                textSize = 36f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }

            val mensagemView = TextView(context).apply {
                text = mensagem
                setTextColor(Color.WHITE)
                textSize = 21f
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 35)
            }

            root.addView(tituloView)
            root.addView(mensagemView)

            wm.addView(root, params)

            val blink = ObjectAnimator.ofFloat(tituloView, "alpha", 1f, 0.2f).apply {
                duration = 500
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
            }
            blink.start()

            val closeAction: () -> Unit = {
                blink.cancel()
                try {
                    wm.removeView(root)
                } catch (_: Exception) {}
            }

            root.isFocusableInTouchMode = true
            root.requestFocus()
            root.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    closeAction()
                    if (isTest) onFecharTeste() else onSimDesligar()
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
