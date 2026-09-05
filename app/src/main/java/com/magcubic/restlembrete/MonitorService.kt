package com.magcubic.restlembrete

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import kotlin.math.roundToInt

class MonitorService : Service() {

    companion object {
        const val ACTION_TRIGGER_TEST = "com.magcubic.restlembrete.ACTION_TRIGGER_TEST"
        const val ACTION_UPDATE_HUD = "com.magcubic.restlembrete.ACTION_UPDATE_HUD"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var warningShowing = false; private var testPending = false
    private var sessionReady = false
    private var reminderShowing = false
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var clockVisibleUntilMs = 0L
    private var hudVisibleUntilMs = 0L
    private var lastClockTriggerMinute = -1
    private var lastHudTriggerMinute = -1

    private val tick = object : Runnable {
        override fun run() {
            // Adiciona 1 segundo de uso a cada segundo rodando
            Prefs.addUsedSeconds(this@MonitorService, 1L)
            ReminderStore.addUsageSecond(this@MonitorService, if (sessionReady) ReminderStore.activePersonId(this@MonitorService) else null)
            Prefs.setLastHeartbeat(this@MonitorService, System.currentTimeMillis())

            checkState()
            updateHudState()
            checkReminders()

            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        if (Prefs.getLastHeartbeat(this) == 0L) {
            Prefs.setLastHeartbeat(this, System.currentTimeMillis())
        }
        handler.post(tick)
        askWhoIsUsing()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_TEST) { if (warningShowing || testPending) return START_STICKY; testPending = true
            handler.postDelayed({
                if (!warningShowing) showWarningOverlay(isTest = true) else testPending = false
            }, 8_000); return START_STICKY
        } else if (intent?.action == ACTION_UPDATE_HUD) {
            updateHudState()
        }

        val usedSeconds = Prefs.getUsedSeconds(this)
        val warnSeconds = (Prefs.getWarnHours(this) * 3600).toLong()
        if (usedSeconds >= warnSeconds && !warningShowing) {
            showWarningOverlay(isTest = false)
        }

        return START_STICKY
    }

    private fun checkState() { if (testPending) return
        val now = System.currentTimeMillis()
        val snoozeUntil = Prefs.getSnoozeUntil(this)
        if (snoozeUntil > now) return

        val usedSeconds = Prefs.getUsedSeconds(this)
        val warnSeconds = (Prefs.getWarnHours(this) * 3600).toLong()

        if (usedSeconds >= warnSeconds && !warningShowing) {
            showWarningOverlay(isTest = false)
        }
    }

    private fun askWhoIsUsing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            sessionReady = true
            return
        }
        OverlayHelper.showPersonPicker(this, ReminderStore.people(this)) { personId ->
            ReminderStore.setActivePerson(this, personId)
            sessionReady = true
        }
    }

    /** Só toca se o projetor/app estiver ligado neste minuto. Avisos atrasados são ignorados. */
    private fun checkReminders() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!power.isInteractive || !sessionReady || reminderShowing || warningShowing) return
        val now = Calendar.getInstance()
        val reminder = ReminderStore.reminders(this).firstOrNull { ReminderStore.shouldShow(this, it, now) } ?: return
        ReminderStore.markShown(this, reminder, now)
        reminderShowing = true
        OverlayHelper.showReminder(this, reminder.title) { reminderShowing = false }
    }

    private fun updateHudState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }

        val nowMs = System.currentTimeMillis()
        val totalUsedSeconds = Prefs.getUsedSeconds(this)
        val currentMinute = (totalUsedSeconds / 60).toInt()

        val textSize = Prefs.getHudTextSize(this)
        val marginPx = Prefs.getHudMargin(this)

        // 1. LÓGICA DO RELÓGIO DE HORA ATUAL (ESQUERDA)
        val clockMode = Prefs.getClockMode(this)
        var shouldShowClock = false

        if (clockMode == -1) {
            shouldShowClock = true
        } else if (clockMode > 0) {
            if (currentMinute > 0 && currentMinute % clockMode == 0 && currentMinute != lastClockTriggerMinute) {
                lastClockTriggerMinute = currentMinute
                val durSec = Prefs.getClockDuration(this)
                clockVisibleUntilMs = nowMs + (durSec * 1000L)
            }
            if (nowMs < clockVisibleUntilMs) {
                shouldShowClock = true
            }
        }

        if (shouldShowClock) {
            val horaAtual = timeFormat.format(Date())
            OverlayHelper.showOrUpdateHudLeft(this, "🕒 $horaAtual", textSize, marginPx)
        } else {
            OverlayHelper.removeHudLeft(this)
        }

        // 2. LÓGICA DO TEMPO RESTANTE (DIREITA)
        val warnSeconds = (Prefs.getWarnHours(this) * 3600).toLong()
        val remainingSeconds = (warnSeconds - totalUsedSeconds).coerceAtLeast(0L)
        val hudMode = Prefs.getHudMode(this)
        var shouldShowHud = false

        if (remainingSeconds in 1..300) {
            shouldShowHud = true
        } else if (hudMode == -1) {
            shouldShowHud = true
        } else if (hudMode > 0) {
            if (currentMinute > 0 && currentMinute % hudMode == 0 && currentMinute != lastHudTriggerMinute) {
                lastHudTriggerMinute = currentMinute
                val durSec = Prefs.getHudDuration(this)
                hudVisibleUntilMs = nowMs + (durSec * 1000L)
            }
            if (nowMs < hudVisibleUntilMs) {
                shouldShowHud = true
            }
        }

        if (shouldShowHud) {
            val remHours = remainingSeconds / 3600
            val remMin = (remainingSeconds % 3600) / 60
            val texto = if (remainingSeconds in 1..300) {
                String.format("⚠️ %02dm", remMin)
            } else if (remainingSeconds > 0) {
                String.format("⏳ %02dh %02dm", remHours, remMin)
            } else {
                val extraSeconds = totalUsedSeconds - warnSeconds
                val extraMin = extraSeconds / 60
                "⚠️ EXCEDIDO (+${extraMin}m)"
            }

            OverlayHelper.showOrUpdateHudRight(this, texto, textSize, marginPx)
        } else {
            OverlayHelper.removeHudRight(this)
        }
    }

    private fun showWarningOverlay(isTest: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        warningShowing = true

        val warnHours = Prefs.getWarnHours(this)
        val restHours = Prefs.getRestHours(this)

        val warnTotalMinutos = (warnHours * 60).roundToInt()
        val warnHorasInt = warnTotalMinutos / 60
        val warnMinInt = warnTotalMinutos % 60
        val warnTexto = if (warnMinInt == 0) "${warnHorasInt}H" else "${warnHorasInt}H ${warnMinInt}MIN"

        val restTotalMinutos = (restHours * 60).roundToInt()
        val restHorasInt = restTotalMinutos / 60
        val restMinInt = restTotalMinutos % 60
        val restTexto = when {
            restHorasInt == 0 -> "${restMinInt}min"
            restMinInt == 0 -> "${restHorasInt}h"
            else -> "${restHorasInt}h ${restMinInt}min"
        }

        val titulo = "⚠️ PROJETOR LIGADO HÁ $warnTexto ⚠️"
        val mensagem = "⚠️ DESCANSO OBRIGATÓRIO: $restTexto ⚠️\n\nDesligue o projetor agora para ele descansar."

        OverlayHelper.show(
            this,
            titulo = titulo,
            mensagem = mensagem,
            isTest = isTest,
            onSimDesligar = { onUserChoseSimDesligar() },
            onNaoIgnorar = { onUserChoseNaoIgnorar() },
            onAdiarMinutos = { minutos -> onUserChoseAdiar(minutos) },
            onFecharTeste = { onUserChoseFecharTeste() }
        )
    }

    private fun onUserChoseSimDesligar() {
        // SIM: Fecha o aviso para a pessoa desligar no controle.
        // Se ela continuar usando, dá 3 minutos de tolerância antes de avisar novamente,
        // garantindo que todo o tempo de uso continue sendo acumulado sem ser burlado!
        warningShowing = false
        Prefs.setSnoozeUntil(this, System.currentTimeMillis() + (3 * 60 * 1000L))
    }

    private fun onUserChoseNaoIgnorar() {
        // NÃO: Fecha o aviso por 4 horas para você continuar usando sem interrupções,
        // mantendo todo o tempo acumulado para cobrar o descanso proporcional correto depois.
        warningShowing = false
        Prefs.setSnoozeUntil(this, System.currentTimeMillis() + 4 * 3600 * 1000)
    }

    private fun onUserChoseAdiar(minutos: Int) {
        warningShowing = false
        Prefs.setSnoozeUntil(this, System.currentTimeMillis() + (minutos * 60 * 1000L))
    }

    private fun onUserChoseFecharTeste() { testPending = false
        warningShowing = false
    }

    private fun buildNotification(): Notification {
        val channelId = "monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Monitor do Projetor", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Monitorando descanso do projetor")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        OverlayHelper.removeHudRight(this)
        OverlayHelper.removeHudLeft(this)

        try {
            val restartIntent = Intent(applicationContext, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
