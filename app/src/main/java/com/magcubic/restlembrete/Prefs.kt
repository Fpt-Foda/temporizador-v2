package com.magcubic.restlembrete

import android.content.Context

object Prefs {
    private const val FILE = "rest_prefs"

    private const val KEY_USED_SECONDS = "used_seconds"
    private const val KEY_LAST_HEARTBEAT = "last_heartbeat"
    private const val KEY_MINIMIZE_COUNT = "minimize_count"
    private const val KEY_SNOOZE_UNTIL = "snooze_until"
    private const val KEY_WARN_HOURS = "warn_hours"
    private const val KEY_REST_HOURS = "rest_hours"

    // Relógio e Cronômetro (Modos: 0 = Desligado, -1 = Sempre Ligado, >0 = Intervalo em Minutos)
    private const val KEY_CLOCK_MODE = "clock_mode"       // Intervalo em min (ex: 10 = a cada 10min)
    private const val KEY_CLOCK_DURATION = "clock_dur"   // Duração na tela em seg (padrão: 10s)
    private const val KEY_HUD_MODE = "hud_mode"           // Intervalo em min (ex: 30 = a cada 30min)
    private const val KEY_HUD_DURATION = "hud_dur"       // Duração na tela em seg (padrão: 10s)

    // Ajuste visual
    private const val KEY_HUD_TEXT_SIZE = "hud_text_size"
    private const val KEY_HUD_MARGIN_OFFSET = "hud_margin_offset" // Distância da borda em px (0 = colado)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getUsedSeconds(ctx: Context): Long = prefs(ctx).getLong(KEY_USED_SECONDS, 0L)
    fun setUsedSeconds(ctx: Context, value: Long) = prefs(ctx).edit().putLong(KEY_USED_SECONDS, value.coerceAtLeast(0L)).apply()
    fun addUsedSeconds(ctx: Context, add: Long) = setUsedSeconds(ctx, getUsedSeconds(ctx) + add)

    fun getLastHeartbeat(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_HEARTBEAT, 0L)
    fun setLastHeartbeat(ctx: Context, value: Long) = prefs(ctx).edit().putLong(KEY_LAST_HEARTBEAT, value).apply()

    fun getMinimizeCount(ctx: Context): Int = prefs(ctx).getInt(KEY_MINIMIZE_COUNT, 0)
    fun setMinimizeCount(ctx: Context, value: Int) = prefs(ctx).edit().putInt(KEY_MINIMIZE_COUNT, value).apply()

    fun getSnoozeUntil(ctx: Context): Long = prefs(ctx).getLong(KEY_SNOOZE_UNTIL, 0L)
    fun setSnoozeUntil(ctx: Context, value: Long) = prefs(ctx).edit().putLong(KEY_SNOOZE_UNTIL, value).apply()

    fun getWarnHours(ctx: Context): Float = prefs(ctx).getFloat(KEY_WARN_HOURS, 4f)
    fun setWarnHours(ctx: Context, value: Float) = prefs(ctx).edit().putFloat(KEY_WARN_HOURS, value).apply()

    fun getRestHours(ctx: Context): Float = prefs(ctx).getFloat(KEY_REST_HOURS, 1f)
    fun setRestHours(ctx: Context, value: Float) = prefs(ctx).edit().putFloat(KEY_REST_HOURS, value).apply()

    // Configuração Hora Atual (Esquerda): 0=Off, -1=Sempre, >0=A cada X minutos
    fun getClockMode(ctx: Context): Int = prefs(ctx).getInt(KEY_CLOCK_MODE, 0)
    fun setClockMode(ctx: Context, value: Int) = prefs(ctx).edit().putInt(KEY_CLOCK_MODE, value).apply()

    fun getClockDuration(ctx: Context): Int = prefs(ctx).getInt(KEY_CLOCK_DURATION, 10)
    fun setClockDuration(ctx: Context, sec: Int) = prefs(ctx).edit().putInt(KEY_CLOCK_DURATION, sec.coerceIn(3, 60)).apply()

    // Configuração Tempo Restante (Direita): 0=Off, -1=Sempre, >0=A cada X minutos
    fun getHudMode(ctx: Context): Int = prefs(ctx).getInt(KEY_HUD_MODE, 0)
    fun setHudMode(ctx: Context, value: Int) = prefs(ctx).edit().putInt(KEY_HUD_MODE, value).apply()

    fun getHudDuration(ctx: Context): Int = prefs(ctx).getInt(KEY_HUD_DURATION, 10)
    fun setHudDuration(ctx: Context, sec: Int) = prefs(ctx).edit().putInt(KEY_HUD_DURATION, sec.coerceIn(3, 60)).apply()

    // Tamanho da fonte e Margem da borda
    fun getHudTextSize(ctx: Context): Float = prefs(ctx).getFloat(KEY_HUD_TEXT_SIZE, 12f)
    fun setHudTextSize(ctx: Context, size: Float) = prefs(ctx).edit().putFloat(KEY_HUD_TEXT_SIZE, size.coerceIn(8f, 32f)).apply()

    // Margem em pixels até o limite da tela (0 = encostado no limite)
    fun getHudMargin(ctx: Context): Int = prefs(ctx).getInt(KEY_HUD_MARGIN_OFFSET, 0)
    fun setHudMargin(ctx: Context, margin: Int) = prefs(ctx).edit().putInt(KEY_HUD_MARGIN_OFFSET, margin.coerceIn(0, 100)).apply()

}
