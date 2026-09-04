package com.magcubic.restlembrete

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // Depois de uma atualização, só religa o monitor: não altera o uso acumulado.
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            return
        }

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val now = System.currentTimeMillis()
            val lastHeartbeat = Prefs.getLastHeartbeat(context)

            if (lastHeartbeat > 0) {
                val offSeconds = ((now - lastHeartbeat) / 1000).coerceAtLeast(0)
                val warnSeconds = (Prefs.getWarnHours(context) * 3600).toLong()
                val restSeconds = (Prefs.getRestHours(context) * 3600).toLong()

                if (restSeconds > 0) {
                    val recoveryRatio = warnSeconds.toDouble() / restSeconds.toDouble()
                    val recoveredUsedSeconds = (offSeconds * recoveryRatio).toLong()
                    val currentUsed = Prefs.getUsedSeconds(context)
                    val newUsed = (currentUsed - recoveredUsedSeconds).coerceAtLeast(0L)
                    Prefs.setUsedSeconds(context, newUsed)
                }
            }

            Prefs.setLastHeartbeat(context, now)
            Prefs.setMinimizeCount(context, 0)
            Prefs.setSnoozeUntil(context, 0L)
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }
    }
}
