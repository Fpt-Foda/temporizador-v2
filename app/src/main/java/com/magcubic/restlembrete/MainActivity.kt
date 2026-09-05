package com.magcubic.restlembrete

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var txtWarn: TextView
    private lateinit var txtRest: TextView
    private lateinit var txtStatusPermissao: TextView
    private lateinit var txtStatusServico: TextView
    private lateinit var txtTempoUso: TextView
    private lateinit var txtTempoRestante: TextView
    private lateinit var txtHudSize: TextView
    private lateinit var txtHudMargin: TextView

    private lateinit var txtClockMode: TextView
    private lateinit var txtClockDur: TextView
    private lateinit var txtHudMode: TextView
    private lateinit var txtHudDur: TextView

    private val stepMinutes = 10
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            atualizarStatus()
            handler.postDelayed(this, 1000)
        }
    }

    private fun criarBotaoTv(
        texto: String,
        corPadrao: Int = Color.parseColor("#2A2A2A"),
        corFoco: Int = Color.parseColor("#FFD700")
    ): Button {
        val normal = GradientDrawable().apply {
            setColor(corPadrao)
            cornerRadius = 12f
            setStroke(2, Color.parseColor("#555555"))
        }

        val focado = GradientDrawable().apply {
            setColor(corFoco)
            cornerRadius = 12f
            setStroke(4, Color.WHITE)
        }

        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focado)
            addState(intArrayOf(android.R.attr.state_pressed), focado)
            addState(intArrayOf(), normal)
        }

        return Button(this).apply {
            text = texto
            background = states
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(20, 12, 20, 12)

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setTextColor(Color.BLACK)
                    scaleX = 1.05f
                    scaleY = 1.05f
                } else {
                    setTextColor(Color.WHITE)
                    scaleX = 1.0f
                    scaleY = 1.0f
                }
            }
            setTextColor(Color.WHITE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 25, 35, 35)
        }

        val titulo = TextView(this).apply {
            text = "⚙️ Descanso do Projetor"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 15)
        }

        // --- PAINEL DE STATUS ---
        val painelStatus = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(25, 20, 25, 20)
        }

        txtStatusPermissao = TextView(this).apply { textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, 2, 0, 2) }
        txtStatusServico = TextView(this).apply { textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, 2, 0, 2) }
        txtTempoUso = TextView(this).apply { textSize = 14f; setTextColor(Color.YELLOW); setPadding(0, 2, 0, 2) }
        txtTempoRestante = TextView(this).apply { textSize = 14f; setTextColor(Color.GREEN); setPadding(0, 2, 0, 2) }

        painelStatus.addView(txtStatusPermissao)
        painelStatus.addView(txtStatusServico)
        painelStatus.addView(txtTempoUso)
        painelStatus.addView(txtTempoRestante)

        // --- TEMPOS DE USO E DESCANSO ---
        val labelWarn = TextView(this).apply { text = "Tempo máximo ligado até avisar:"; textSize = 14f; setTextColor(Color.WHITE); setPadding(0, 15, 0, 5) }
        txtWarn = TextView(this).apply { textSize = 18f; setTextColor(Color.CYAN); gravity = Gravity.CENTER; setPadding(20, 5, 20, 5) }
        val linhaWarn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(criarBotaoTv(" - ").apply { setOnClickListener { ajustarWarn(-stepMinutes) } })
            addView(txtWarn)
            addView(criarBotaoTv(" + ").apply { setOnClickListener { ajustarWarn(stepMinutes) } })
        }

        val labelRest = TextView(this).apply { text = "Tempo de descanso exigido:"; textSize = 14f; setTextColor(Color.WHITE); setPadding(0, 10, 0, 5) }
        txtRest = TextView(this).apply { textSize = 18f; setTextColor(Color.CYAN); gravity = Gravity.CENTER; setPadding(20, 5, 20, 5) }
        val linhaRest = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(criarBotaoTv(" - ").apply { setOnClickListener { ajustarRest(-stepMinutes) } })
            addView(txtRest)
            addView(criarBotaoTv(" + ").apply { setOnClickListener { ajustarRest(stepMinutes) } })
        }

        // --- AJUSTE DE POSIÇÃO (MARGEM DO LIMITE DA TELA) ---
        val labelMargin = TextView(this).apply { text = "Distância das Bordas da Tela (Margem):"; textSize = 14f; setTextColor(Color.WHITE); setPadding(0, 15, 0, 5) }
        txtHudMargin = TextView(this).apply { textSize = 16f; setTextColor(Color.YELLOW); gravity = Gravity.CENTER; setPadding(15, 5, 15, 5) }
        val linhaMargin = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(criarBotaoTv("0px (Colado)").apply { setOnClickListener { setMargin(0) } })
            addView(criarBotaoTv(" -5 ").apply { setOnClickListener { adjustMargin(-5) } })
            addView(txtHudMargin)
            addView(criarBotaoTv(" +5 ").apply { setOnClickListener { adjustMargin(5) } })
            addView(criarBotaoTv("25px").apply { setOnClickListener { setMargin(25) } })
        }

        // --- TAMANHO DA FONTE DOS RELÓGIOS ---
        val labelHudSize = TextView(this).apply { text = "Tamanho da fonte dos Relógios:"; textSize = 14f; setTextColor(Color.WHITE); setPadding(0, 10, 0, 5) }
        txtHudSize = TextView(this).apply { textSize = 16f; setTextColor(Color.YELLOW); gravity = Gravity.CENTER; setPadding(15, 5, 15, 5) }
        val linhaHudSize = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(criarBotaoTv("P (10)").apply { setOnClickListener { setHudSize(10f) } })
            addView(criarBotaoTv(" - ").apply { setOnClickListener { adjustHudSize(-1f) } })
            addView(txtHudSize)
            addView(criarBotaoTv(" + ").apply { setOnClickListener { adjustHudSize(1f) } })
            addView(criarBotaoTv("G (18)").apply { setOnClickListener { setHudSize(18f) } })
        }

        // --- CONFIGURAÇÃO: HORA ATUAL COM 5M, 10M, 15M, 30M, 1H, 3H, 6H, 8H, 12H ---
        val labelClock = TextView(this).apply { text = "🕒 HORA ATUAL (Canto Esquerdo):"; textSize = 14f; setTextColor(Color.CYAN); setPadding(0, 15, 0, 5) }
        txtClockMode = TextView(this).apply { textSize = 14f; setTextColor(Color.WHITE); setPadding(0, 2, 0, 5) }

        val scrollHorizClock = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val linhaClockMode = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            fun addOpcao(nome: String, min: Int) {
                addView(criarBotaoTv(nome).apply {
                    setOnClickListener { setClockInterval(min) }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(4, 0, 4, 0) }
                })
            }

            addOpcao("OFF", 0)
            addOpcao("Sempre", -1)
            addOpcao("5m", 5)
            addOpcao("10m", 10)
            addOpcao("15m", 15)
            addOpcao("30m", 30)
            addOpcao("1h", 60)
            addOpcao("3h", 180)
            addOpcao("6h", 360)
            addOpcao("8h", 480)
            addOpcao("12h", 720)
        }
        scrollHorizClock.addView(linhaClockMode)

        txtClockDur = TextView(this).apply { textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, 5, 0, 5) }
        val linhaClockDur = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(criarBotaoTv("Duração: -5s").apply { setOnClickListener { adjustClockDur(-5) } })
            addView(criarBotaoTv("+5s").apply { setOnClickListener { adjustClockDur(5) } })
            addView(txtClockDur)
        }

        // --- CONFIGURAÇÃO: TEMPO RESTANTE (DIREITA) ---
        val labelHud = TextView(this).apply { text = "⏳ TEMPO RESTANTE (Canto Direito) — [Auto Fixo nos últimos 5 min]:"; textSize = 14f; setTextColor(Color.CYAN); setPadding(0, 15, 0, 5) }
        txtHudMode = TextView(this).apply { textSize = 14f; setTextColor(Color.WHITE); setPadding(0, 2, 0, 5) }

        val scrollHorizHud = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val linhaHudMode = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            fun addOpcaoHud(nome: String, min: Int) {
                addView(criarBotaoTv(nome).apply {
                    setOnClickListener { setHudInterval(min) }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(4, 0, 4, 0) }
                })
            }

            addOpcaoHud("OFF", 0)
            addOpcaoHud("Sempre", -1)
            addOpcaoHud("5m", 5)
            addOpcaoHud("10m", 10)
            addOpcaoHud("15m", 15)
            addOpcaoHud("30m", 30)
            addOpcaoHud("1h", 60)
            addOpcaoHud("2h", 120)
        }
        scrollHorizHud.addView(linhaHudMode)

        txtHudDur = TextView(this).apply { textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, 5, 0, 5) }
        val linhaHudDur = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(criarBotaoTv("Duração: -5s").apply { setOnClickListener { adjustHudDur(-5) } })
            addView(criarBotaoTv("+5s").apply { setOnClickListener { adjustHudDur(5) } })
            addView(txtHudDur)
        }

        // --- BOTÕES PRINCIPAIS DE AÇÃO ---
        val btnPermissao = criarBotaoTv("1. Checar / Permitir Sobreposição").apply {
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this@MainActivity)) {
                        Toast.makeText(this@MainActivity, "✅ Permissão JÁ concedida!", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        } catch (_: Exception) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }
                    }
                }
            }
        }

        val btnIniciar = criarBotaoTv("2. Iniciar / Garantir Monitoramento").apply {
            setOnClickListener {
                val serviceIntent = Intent(this@MainActivity, MonitorService::class.java)
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                Toast.makeText(this@MainActivity, "🚀 Monitoramento Ativo!", Toast.LENGTH_SHORT).show()
                atualizarStatus()
            }
        }

        val btnTeste10s = criarBotaoTv(
            "⚡ Testar Aviso em 8 Segundos",
            corPadrao = Color.parseColor("#801515"),
            corFoco = Color.parseColor("#FF5252")
        ).apply {
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "⚠️ Ative a sobreposição primeiro!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                Toast.makeText(this@MainActivity, "⏳ O aviso vai aparecer em 8 segundos...", Toast.LENGTH_SHORT).show()
                val serviceIntent = Intent(this@MainActivity, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_TRIGGER_TEST
                }
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
            }
        }

        val btnZerarMemoria = criarBotaoTv("🔄 Zerar Contador de Uso Manualmente").apply {
            setOnClickListener {
                Prefs.setUsedSeconds(this@MainActivity, 0L)
                Toast.makeText(this@MainActivity, "Contador de uso zerado!", Toast.LENGTH_SHORT).show()
                atualizarStatus()
            }
        }

        val btnAtualizar = criarBotaoTv("⬆️ Procurar Atualização").apply {
            setOnClickListener { verificarAtualizacao(mostrarResultado = true) }
        }

        val btnLembretes = criarBotaoTv(
            "🔔 Pessoas e Lembretes",
            corPadrao = Color.parseColor("#244A66"),
            corFoco = Color.parseColor("#53C8FF")
        ).apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, ReminderActivity::class.java)) }
        }

        val btnMs = criarBotaoTv("M-s", corPadrao = Color.parseColor("#33442B"), corFoco = Color.parseColor("#8BC34A")).apply {
            fun atualizarNome() {
                text = if (Prefs.isMsEnabled(this@MainActivity)) "M-s" else "M-s"
            }
            atualizarNome()
            setOnClickListener {
                val ligado = !Prefs.isMsEnabled(this@MainActivity)
                Prefs.setMsEnabled(this@MainActivity, ligado)
                if (ligado) {
                    VolumeLimiterService.enforceLimit(this@MainActivity)
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("M-s ativo")
                        .setMessage("Permissão de M-s requerida para usar em qualquer tela.")
                        .setNegativeButton("Depois", null)
                        .setPositiveButton("Abrir M-s") { _, _ ->
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        .show()
                } else {
                    Toast.makeText(this@MainActivity, "M-s desligado. Controle normal liberado.", Toast.LENGTH_SHORT).show()
                }
                atualizarNome()
            }
        }

        val btnConfigMs = criarBotaoTv("Config M-s", corPadrao = Color.parseColor("#33442B"), corFoco = Color.parseColor("#8BC34A")).apply {
            setOnClickListener { abrirConfigMs() }
        }

        fun aplicarMargem(btn: Button) {
            btn.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
        }

        aplicarMargem(btnPermissao)
        aplicarMargem(btnIniciar)
        aplicarMargem(btnTeste10s)
        aplicarMargem(btnZerarMemoria)
        aplicarMargem(btnAtualizar)
        aplicarMargem(btnLembretes)
        aplicarMargem(btnMs)
        aplicarMargem(btnConfigMs)

        layout.addView(titulo)
        layout.addView(painelStatus)
        layout.addView(labelWarn)
        layout.addView(linhaWarn)
        layout.addView(labelRest)
        layout.addView(linhaRest)

        layout.addView(labelMargin)
        layout.addView(linhaMargin)
        layout.addView(labelHudSize)
        layout.addView(linhaHudSize)

        layout.addView(labelClock)
        layout.addView(txtClockMode)
        layout.addView(scrollHorizClock)
        layout.addView(linhaClockDur)

        layout.addView(labelHud)
        layout.addView(txtHudMode)
        layout.addView(scrollHorizHud)
        layout.addView(linhaHudDur)

        layout.addView(btnPermissao)
        layout.addView(btnIniciar)
        layout.addView(btnTeste10s)
        layout.addView(btnZerarMemoria)
        layout.addView(btnLembretes)
        layout.addView(btnMs)
        layout.addView(btnConfigMs)
        layout.addView(btnAtualizar)

        scroll.addView(layout)
        setContentView(scroll)

        atualizarTextos()
        handler.postDelayed({ verificarAtualizacao(mostrarResultado = false) }, 1_500)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun verificarAtualizacao(mostrarResultado: Boolean) {
        UpdateChecker.check(this) { resultado ->
            when (resultado) {
                is UpdateChecker.CheckResult.Available -> {
                    AlertDialog.Builder(this)
                        .setTitle("Atualização disponível")
                        .setMessage("A versão ${resultado.update.version} está pronta para baixar.")
                        .setNegativeButton("Agora não", null)
                        .setPositiveButton("Baixar") { _, _ ->
                            Toast.makeText(this, "Baixando atualização...", Toast.LENGTH_SHORT).show()
                            UpdateChecker.downloadAndInstall(this, resultado.update) { download ->
                                when (download) {
                                    UpdateChecker.DownloadResult.StartedInstaller -> Unit
                                    is UpdateChecker.DownloadResult.Failed -> Toast.makeText(this, download.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .show()
                }
                UpdateChecker.CheckResult.UpToDate -> {
                    if (mostrarResultado) Toast.makeText(this, "Você já está na versão mais recente.", Toast.LENGTH_SHORT).show()
                }
                UpdateChecker.CheckResult.NotConfigured -> {
                    if (mostrarResultado) Toast.makeText(this, "Atualização ainda não foi configurada.", Toast.LENGTH_LONG).show()
                }
                is UpdateChecker.CheckResult.Failed -> {
                    if (mostrarResultado) Toast.makeText(this, resultado.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun abrirConfigMs() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 10)
        }
        val valor = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        fun atualizar() { valor.text = "Limite: ${Prefs.getMsLimit(this@MainActivity)}%" }
        val linha = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun botao(texto: String, delta: Int) = criarBotaoTv(texto).apply {
            setOnClickListener {
                Prefs.setMsLimit(this@MainActivity, Prefs.getMsLimit(this@MainActivity) + delta)
                VolumeLimiterService.enforceLimit(this@MainActivity)
                atualizar()
            }
        }
        linha.addView(botao("-10", -10))
        linha.addView(botao("-1", -1))
        linha.addView(botao("+1", 1))
        linha.addView(botao("+10", 10))
        val estadoMs = criarBotaoTv("").apply {
            fun atualizarEstado() {
                text = if (Prefs.isMsEnabled(this@MainActivity)) "Desligar M-s" else "Ligar M-s"
            }
            atualizarEstado()
            setOnClickListener {
                val ligado = !Prefs.isMsEnabled(this@MainActivity)
                Prefs.setMsEnabled(this@MainActivity, ligado)
                if (ligado) VolumeLimiterService.enforceLimit(this@MainActivity)
                atualizarEstado()
                Toast.makeText(
                    this@MainActivity,
                    if (ligado) "M-s ligado." else "M-s desligado. Controle normal liberado.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        box.addView(valor)
        box.addView(linha)
        box.addView(estadoMs)
        atualizar()
        AlertDialog.Builder(this)
            .setTitle("Config M-s")
            .setMessage("Escolha o máximo real. A escala M-s pode ir até 100%, mas o valor real para neste limite.")
            .setView(box)
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun setMargin(margin: Int) {
        Prefs.setHudMargin(this, margin)
        atualizarTextos()
        notificarHudUpdate()
    }

    private fun adjustMargin(delta: Int) {
        setMargin(Prefs.getHudMargin(this) + delta)
    }

    private fun setHudSize(size: Float) {
        Prefs.setHudTextSize(this, size)
        atualizarTextos()
        notificarHudUpdate()
    }

    private fun adjustHudSize(delta: Float) {
        setHudSize(Prefs.getHudTextSize(this) + delta)
    }

    private fun setClockInterval(min: Int) {
        Prefs.setClockMode(this, min)
        atualizarTextos()
        notificarHudUpdate()
    }

    private fun adjustClockDur(delta: Int) {
        Prefs.setClockDuration(this, Prefs.getClockDuration(this) + delta)
        atualizarTextos()
    }

    private fun setHudInterval(min: Int) {
        Prefs.setHudMode(this, min)
        atualizarTextos()
        notificarHudUpdate()
    }

    private fun adjustHudDur(delta: Int) {
        Prefs.setHudDuration(this, Prefs.getHudDuration(this) + delta)
        atualizarTextos()
    }

    private fun notificarHudUpdate() {
        val serviceIntent = Intent(this, MonitorService::class.java).apply {
            action = MonitorService.ACTION_UPDATE_HUD
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun formatarIntervalo(minutos: Int): String {
        return when {
            minutos == 0 -> "[ DESLIGADO ]"
            minutos == -1 -> "[ SEMPRE LIGADO ]"
            minutos < 60 -> "A cada ${minutos}m"
            else -> {
                val h = minutos / 60
                val m = minutos % 60
                if (m == 0) "A cada ${h}h" else "A cada ${h}h${m}m"
            }
        }
    }

    private fun ajustarWarn(deltaMinutes: Int) {
        val atualMinutos = (Prefs.getWarnHours(this) * 60).roundToInt()
        val novoMinutos = (atualMinutos + deltaMinutes).coerceIn(stepMinutes, 24 * 60)
        Prefs.setWarnHours(this, novoMinutos / 60f)
        atualizarTextos()
        notificarHudUpdate()
    }

    private fun ajustarRest(deltaMinutes: Int) {
        val atualMinutos = (Prefs.getRestHours(this) * 60).roundToInt()
        val novoMinutos = (atualMinutos + deltaMinutes).coerceIn(stepMinutes, 24 * 60)
        Prefs.setRestHours(this, novoMinutos / 60f)
        atualizarTextos()
        notificarHudUpdate()
    }

    private fun atualizarTextos() {
        txtWarn.text = formatarHoras(Prefs.getWarnHours(this))
        txtRest.text = formatarHoras(Prefs.getRestHours(this))
        txtHudSize.text = "${Prefs.getHudTextSize(this).toInt()} sp"
        txtHudMargin.text = "${Prefs.getHudMargin(this)} px"

        // Modo Hora Atual
        val cMode = Prefs.getClockMode(this)
        txtClockMode.text = "Modo Atual: ${formatarIntervalo(cMode)} por ${Prefs.getClockDuration(this)}s"
        txtClockDur.text = "   Duração na tela: ${Prefs.getClockDuration(this)} segundos"

        // Modo Tempo Restante
        val hMode = Prefs.getHudMode(this)
        txtHudMode.text = "Modo Atual: ${formatarIntervalo(hMode)} por ${Prefs.getHudDuration(this)}s"
        txtHudDur.text = "   Duração na tela: ${Prefs.getHudDuration(this)} segundos"
    }

    private fun atualizarStatus() {
        val temPermissao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        txtStatusPermissao.text = if (temPermissao) "• Sobreposição: ✅ AUTORIZADO" else "• Sobreposição: ❌ PENDENTE"
        txtStatusPermissao.setTextColor(if (temPermissao) Color.GREEN else Color.RED)

        val servicoAtivo = isServiceRunning(MonitorService::class.java)
        txtStatusServico.text = if (servicoAtivo) "• Monitor Segundo Plano: ✅ ATIVO" else "• Monitor Segundo Plano: ⏸ PARADO"
        txtStatusServico.setTextColor(if (servicoAtivo) Color.GREEN else Color.YELLOW)

        val usedSeconds = Prefs.getUsedSeconds(this)
        val uHoras = usedSeconds / 3600
        val uMin = (usedSeconds % 3600) / 60
        val uSec = usedSeconds % 60
        txtTempoUso.text = String.format("• Uso Acumulado: %02dh %02dm %02ds", uHoras, uMin, uSec)

        val warnSeconds = (Prefs.getWarnHours(this) * 3600).toLong()
        val remainingSeconds = (warnSeconds - usedSeconds).coerceAtLeast(0L)
        val rHoras = remainingSeconds / 3600
        val rMin = (remainingSeconds % 3600) / 60
        val rSec = remainingSeconds % 60
        txtTempoRestante.text = String.format("• Tempo Restante: %02dh %02dm %02ds", rHoras, rMin, rSec)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun formatarHoras(h: Float): String {
        val totalMinutos = (h * 60).roundToInt()
        val horas = totalMinutos / 60
        val minutos = totalMinutos % 60
        return if (minutos == 0) "${horas}h" else "${horas}h${minutos}min"
    }
}
