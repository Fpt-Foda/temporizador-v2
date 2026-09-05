package com.magcubic.restlembrete

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.UUID

/** Tela simples para administrar nomes e lembretes no próprio projetor. */
class ReminderActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private var firstButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(35, 25, 35, 35) }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(content)
        })
        redraw()
    }

    private fun label(text: String, size: Float = 15f, color: Int = Color.WHITE) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); setPadding(0, 8, 0, 8)
    }

    private fun button(text: String, color: Int = Color.parseColor("#2A2A2A")) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        setBackgroundColor(color); isFocusable = true; isFocusableInTouchMode = true
        setOnFocusChangeListener { _, focused ->
            setBackgroundColor(if (focused) Color.parseColor("#FFD700") else color)
            setTextColor(if (focused) Color.BLACK else Color.WHITE)
        }
        setOnKeyListener { view, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val direction = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
                else -> return@setOnKeyListener false
            }
            val next = view.focusSearch(direction)
            if (next != null && next !== view) { next.requestFocus(); true } else false
        }
    }

    private fun addFull(view: android.view.View) {
        content.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 5, 0, 5) })
    }

    private fun redraw() {
        content.removeAllViews()
        firstButton = null
        addFull(label("🔔 Pessoas e Lembretes", 23f, Color.CYAN))
        addFull(label("Os lembretes só aparecem se o monitoramento estiver ligado naquele horário.", 13f, Color.LTGRAY))

        val report = ReminderStore.usageReport(this)
        addFull(label("📊 Relatório de uso", 18f, Color.YELLOW))
        addFull(label("Geral hoje: ${formatDuration(report.generalToday)}\nGeral neste mês: ${formatDuration(report.generalMonth)}", 15f, Color.WHITE))
        val best = report.peopleMonth.maxByOrNull { it.second }
        if (best != null && best.second > 0) addFull(label("Quem mais usou no mês: ${best.first.name} (${formatDuration(best.second)})", 15f, Color.CYAN))
        addFull(button("⌨ Teclado próprio: ${if (useCustomKeyboard()) "LIGADO" else "DESLIGADO"}", Color.parseColor("#40506A")).apply {
            setOnClickListener {
                getSharedPreferences("projector_reminders", MODE_PRIVATE).edit()
                    .putBoolean("use_custom_keyboard", !useCustomKeyboard()).apply()
                redraw()
            }
        })
        addFull(button("+ Adicionar lembrete", Color.parseColor("#245B2B")).apply {
            firstButton = this
            setOnClickListener { addReminder() }
        })
        addFull(button("+ Adicionar pessoa").apply { setOnClickListener { addPerson() } })

        addFull(label("Pessoas", 18f, Color.YELLOW))
        val people = ReminderStore.people(this)
        if (people.isEmpty()) addFull(label("Nenhuma pessoa criada. Lembretes Gerais funcionam para todos.", 13f, Color.LTGRAY))
        people.forEach { person ->
            val today = report.peopleToday.firstOrNull { it.first.id == person.id }?.second ?: 0L
            val month = report.peopleMonth.firstOrNull { it.first.id == person.id }?.second ?: 0L
            addFull(button("${person.name}    • Remover", Color.parseColor("#353535")).apply {
                text = "${person.name} — hoje ${formatDuration(today)} • mês ${formatDuration(month)}\nToque para remover"
                setOnClickListener {
                    AlertDialog.Builder(this@ReminderActivity).setTitle("Remover ${person.name}?")
                        .setMessage("Os lembretes dessa pessoa não tocarão até você mudar ou apagar eles.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Remover") { _, _ -> ReminderStore.removePerson(this@ReminderActivity, person.id); redraw() }.show()
                }
            })
        }

        addFull(label("Lembretes", 18f, Color.YELLOW))
        val reminders = ReminderStore.reminders(this)
        if (reminders.isEmpty()) addFull(label("Nenhum lembrete ainda.", 13f, Color.LTGRAY))
        reminders.forEach { reminder ->
            val time = "%02d:%02d".format(reminder.hour, reminder.minute)
            val days = daysText(reminder.days)
            addFull(button("$time  •  ${reminder.title}\n${ReminderStore.personName(this, reminder.personId)} • $days\nOK: opções", Color.parseColor("#353535")).apply {
                gravity = Gravity.START
                setOnClickListener {
                    AlertDialog.Builder(this@ReminderActivity).setTitle(reminder.title)
                        .setMessage("Escolha uma ação para este lembrete.")
                        .setNegativeButton("Apagar") { _, _ -> ReminderStore.removeReminder(this@ReminderActivity, reminder.id); redraw() }
                        .setNeutralButton("Testar aviso") { _, _ -> OverlayHelper.showReminder(this@ReminderActivity, reminder.title) {} }
                        .setPositiveButton("Editar") { _, _ -> addReminder(reminder) }
                        .show()
                }
            })
        }
        addFull(button("◀ Voltar ao Temporizador").apply { setOnClickListener { finish() } })
        firstButton?.post { firstButton?.requestFocus() }
    }

    private fun addPerson() {
        showNameEditor("Nome da pessoa") { name ->
            ReminderStore.addPerson(this, name)
            redraw()
        }
    }

    private fun addReminder(existing: ProjectorReminder? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(35, 10, 35, 0) }
        var reminderTitle = existing?.title.orEmpty()
        val nameButton = button(if (reminderTitle.isEmpty()) "✎ Digitar nome do lembrete" else "✎ $reminderTitle", Color.parseColor("#303C54"))
        nameButton.setOnClickListener {
            showNameEditor("Nome do lembrete", reminderTitle) { typed ->
                reminderTitle = typed
                nameButton.text = "✎ $typed"
            }
        }
        box.addView(nameButton)
        var hour = existing?.hour ?: 20
        var minute = existing?.minute ?: 0
        var personId: String? = existing?.personId
        var days = existing?.days ?: (1..7).toSet()
        val time = label("", 20f, Color.CYAN).apply { gravity = Gravity.CENTER }
        fun updateTime() { time.text = "Horário: %02d:%02d".format(hour, minute) }
        val line = LinearLayout(this).apply { gravity = Gravity.CENTER }
        line.addView(button("Hora −").apply { setOnClickListener { hour = (hour + 23) % 24; updateTime() } })
        line.addView(button("Hora +").apply { setOnClickListener { hour = (hour + 1) % 24; updateTime() } })
        line.addView(button("Min −").apply { setOnClickListener { minute = (minute + 55) % 60; updateTime() } })
        line.addView(button("Min +").apply { setOnClickListener { minute = (minute + 5) % 60; updateTime() } })
        updateTime(); box.addView(time); box.addView(line)
        val personButton = button("")
        fun updatePerson() { personButton.text = "Para: ${ReminderStore.personName(this, personId)}" }
        personButton.setOnClickListener {
            val options = listOf("Geral") + ReminderStore.people(this).map { it.name }
            AlertDialog.Builder(this).setTitle("Para quem é este lembrete?").setItems(options.toTypedArray()) { _, index ->
                personId = if (index == 0) null else ReminderStore.people(this)[index - 1].id
                updatePerson()
            }.show()
        }
        updatePerson(); box.addView(personButton)
        val daysButton = button("")
        fun updateDays() { daysButton.text = "Dias: ${daysText(days)}" }
        daysButton.setOnClickListener {
            val names = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
            val checked = BooleanArray(7) { index -> (index + 1) in days }
            AlertDialog.Builder(this).setTitle("Escolha os dias").setMultiChoiceItems(names, checked) { _, index, selected -> checked[index] = selected }
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("OK") { _, _ ->
                    val chosen = (1..7).filter { checked[it - 1] }.toSet()
                    days = if (chosen.isEmpty()) (1..7).toSet() else chosen
                    updateDays()
                }.show()
        }
        updateDays(); box.addView(daysButton)
        val duration = EditText(this).apply {
            hint = "Por quantos dias? Vazio = sem fim"; inputType = InputType.TYPE_CLASS_NUMBER
            if ((existing?.endsAt ?: 0L) > 0L) {
                val remaining = ((existing!!.endsAt - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(1L)
                setText(remaining.toString())
            }
        }
        box.addView(duration)
        AlertDialog.Builder(this).setTitle(if (existing == null) "Novo lembrete" else "Editar lembrete").setView(box).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val title = reminderTitle.trim()
                if (title.isNotEmpty()) {
                    val count = duration.text.toString().toIntOrNull()
                    val endsAt = if (count != null && count > 0) Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, count) }.timeInMillis else 0L
                    val saved = ProjectorReminder(existing?.id ?: UUID.randomUUID().toString(), title, hour, minute, personId, days, endsAt)
                    if (existing == null) ReminderStore.addReminder(this, saved) else ReminderStore.updateReminder(this, saved)
                    redraw()
                }
            }.show()
    }

    /** Teclado próprio para o projetor: funciona só com as setas e tem acentos. */
    private fun useCustomKeyboard() = getSharedPreferences("projector_reminders", MODE_PRIVATE)
        .getBoolean("use_custom_keyboard", true)

    private fun showNameEditor(title: String, initial: String = "", onSave: (String) -> Unit) {
        if (useCustomKeyboard()) {
            showTextEditor(title, initial, onSave)
            return
        }
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(initial)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ -> onSave(input.text.toString()) }.show()
    }

    private fun showTextEditor(title: String, initial: String = "", onSave: (String) -> Unit) {
        var upper = true
        var capsLock = false
        var firstKey: Button? = null
        val keyButtons = mutableListOf<Pair<String, Button>>()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val preview = label(initial, 24f, Color.CYAN).apply {
            minHeight = 70
            setBackgroundColor(Color.parseColor("#222222"))
            setPadding(18, 12, 18, 12)
        }
        box.addView(preview)
        fun setText(value: String) { preview.text = value.take(40) }
        lateinit var shiftButton: Button
        lateinit var lockButton: Button
        fun refreshKeyboard() {
            val showUpper = upper || capsLock
            keyButtons.forEach { (key, button) -> button.text = if (showUpper) key.uppercase() else key.lowercase() }
            shiftButton.text = if (upper && !capsLock) "⇧ Próxima: MAIÚSCULA" else "⇧ Próxima: minúscula"
            lockButton.text = if (capsLock) "⇪ TRAVAR: LIGADO" else "⇪ Travar maiúsculas"
        }
        fun addKey(value: String) {
            val showUpper = upper || capsLock
            setText(preview.text.toString() + if (showUpper) value.uppercase() else value.lowercase())
            if (!capsLock) upper = false
            refreshKeyboard()
        }
        fun addRow(keys: List<String>) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            keys.forEach { key ->
                val keyButton = button(key).apply {
                    textSize = 13f
                    minWidth = 0
                    setPadding(5, 10, 5, 10)
                    setOnClickListener { addKey(key) }
                }
                if (firstKey == null) firstKey = keyButton
                keyButtons.add(key to keyButton)
                row.addView(keyButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2, 3, 2, 3) })
            }
            box.addView(row)
        }
        addRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"))
        addRow(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ç"))
        addRow(listOf("z", "x", "c", "v", "b", "n", "m", "á", "é", "í"))
        addRow(listOf("à", "â", "ã", "ê", "ó", "ô", "õ", "ú", "ü", "-"))
        val commands = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun command(text: String, action: () -> Unit) = button(text, Color.parseColor("#40506A")).apply { setOnClickListener { action() } }
        shiftButton = command("") { upper = !upper; refreshKeyboard() }
        lockButton = command("") { capsLock = !capsLock; if (capsLock) upper = true; refreshKeyboard() }
        commands.addView(shiftButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f))
        commands.addView(lockButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f))
        commands.addView(command("Espaço") { setText(preview.text.toString() + " "); if (!capsLock) upper = true; refreshKeyboard() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        commands.addView(command("⌫") { setText(preview.text.toString().dropLast(1)) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, .7f))
        box.addView(commands)
        refreshKeyboard()
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(box)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = preview.text.toString().trim()
                if (value.isNotEmpty()) { dialog.dismiss(); onSave(value) }
            }
            firstKey?.post { firstKey?.requestFocus() }
        }
        dialog.show()
    }

    private fun daysText(days: Set<Int>): String {
        if (days.size == 7) return "Todos os dias"
        val names = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
        return days.sorted().joinToString(", ") { names[it - 1] }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}min" else "${m}min"
    }
}
