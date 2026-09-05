package com.magcubic.restlembrete

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(35, 25, 35, 35) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#121212")); addView(content) })
        redraw()
    }

    private fun label(text: String, size: Float = 15f, color: Int = Color.WHITE) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); setPadding(0, 8, 0, 8)
    }

    private fun button(text: String, color: Int = Color.parseColor("#2A2A2A")) = Button(this).apply {
        this.text = text; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        setBackgroundColor(color); isFocusable = true; isFocusableInTouchMode = true
    }

    private fun addFull(view: android.view.View) {
        content.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 5, 0, 5) })
    }

    private fun redraw() {
        content.removeAllViews()
        addFull(label("🔔 Pessoas e Lembretes", 23f, Color.CYAN))
        addFull(label("Os lembretes só aparecem se o monitoramento estiver ligado naquele horário.", 13f, Color.LTGRAY))

        val report = ReminderStore.usageReport(this)
        addFull(label("📊 Relatório de uso", 18f, Color.YELLOW))
        addFull(label("Geral hoje: ${formatDuration(report.generalToday)}\nGeral neste mês: ${formatDuration(report.generalMonth)}", 15f, Color.WHITE))
        val best = report.peopleMonth.maxByOrNull { it.second }
        if (best != null && best.second > 0) addFull(label("Quem mais usou no mês: ${best.first.name} (${formatDuration(best.second)})", 15f, Color.CYAN))
        addFull(button("+ Adicionar lembrete", Color.parseColor("#245B2B")).apply { setOnClickListener { addReminder() } })
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
            addFull(button("$time  •  ${reminder.title}\n${ReminderStore.personName(this, reminder.personId)} • $days\nToque para remover", Color.parseColor("#353535")).apply {
                gravity = Gravity.START
                setOnClickListener {
                    AlertDialog.Builder(this@ReminderActivity).setTitle("Apagar lembrete?")
                        .setMessage(reminder.title).setNegativeButton("Cancelar", null)
                        .setPositiveButton("Apagar") { _, _ -> ReminderStore.removeReminder(this@ReminderActivity, reminder.id); redraw() }.show()
                }
            })
        }
        addFull(button("◀ Voltar ao Temporizador").apply { setOnClickListener { finish() } })
    }

    private fun addPerson() {
        val input = EditText(this).apply { hint = "Nome, por exemplo: Vô"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Adicionar pessoa").setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ -> ReminderStore.addPerson(this, input.text.toString()); redraw() }.show()
    }

    private fun addReminder() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(35, 10, 35, 0) }
        val name = EditText(this).apply { hint = "Ex.: Hora do remédio"; setSingleLine(true) }
        box.addView(name)
        var hour = 20
        var minute = 0
        var personId: String? = null
        var days = (1..7).toSet()
        val time = label("Horário: 20:00", 20f, Color.CYAN).apply { gravity = Gravity.CENTER }
        fun updateTime() { time.text = "Horário: %02d:%02d".format(hour, minute) }
        val line = LinearLayout(this).apply { gravity = Gravity.CENTER }
        line.addView(button("Hora −").apply { setOnClickListener { hour = (hour + 23) % 24; updateTime() } })
        line.addView(button("Hora +").apply { setOnClickListener { hour = (hour + 1) % 24; updateTime() } })
        line.addView(button("Min −").apply { setOnClickListener { minute = (minute + 55) % 60; updateTime() } })
        line.addView(button("Min +").apply { setOnClickListener { minute = (minute + 5) % 60; updateTime() } })
        box.addView(time); box.addView(line)
        val personButton = button("Para: Geral")
        fun updatePerson() { personButton.text = "Para: ${ReminderStore.personName(this, personId)}" }
        personButton.setOnClickListener {
            val options = listOf("Geral") + ReminderStore.people(this).map { it.name }
            AlertDialog.Builder(this).setTitle("Para quem é este lembrete?").setItems(options.toTypedArray()) { _, index ->
                personId = if (index == 0) null else ReminderStore.people(this)[index - 1].id
                updatePerson()
            }.show()
        }
        box.addView(personButton)
        val daysButton = button("Dias: Todos os dias")
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
        box.addView(daysButton)
        val duration = EditText(this).apply {
            hint = "Por quantos dias? Vazio = sem fim"; inputType = InputType.TYPE_CLASS_NUMBER
        }
        box.addView(duration)
        AlertDialog.Builder(this).setTitle("Novo lembrete").setView(box).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val title = name.text.toString().trim()
                if (title.isNotEmpty()) {
                    val count = duration.text.toString().toIntOrNull()
                    val endsAt = if (count != null && count > 0) Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, count) }.timeInMillis else 0L
                    ReminderStore.addReminder(this, ProjectorReminder(UUID.randomUUID().toString(), title, hour, minute, personId, days, endsAt))
                    redraw()
                }
            }.show()
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
