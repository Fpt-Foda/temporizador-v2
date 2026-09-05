package com.magcubic.restlembrete

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class ReminderPerson(val id: String, val name: String)

data class ProjectorReminder(
    val id: String,
    val title: String,
    val hour: Int,
    val minute: Int,
    /** null significa que é geral, para qualquer pessoa. */
    val personId: String?,
    /** Dias do Calendar: domingo=1 até sábado=7. */
    val days: Set<Int>,
    /** 0 significa sem data final. */
    val endsAt: Long = 0L
)

/** Guarda somente nomes e horários no próprio aparelho, sem conta nem internet. */
object ReminderStore {
    private const val FILE = "projector_reminders"
    private const val KEY_PEOPLE = "people"
    private const val KEY_REMINDERS = "reminders"
    private const val KEY_ACTIVE_PERSON = "active_person"
    private const val KEY_LAST_PREFIX = "last_shown_"
    private const val KEY_USAGE_DATES = "usage_dates"
    private const val KEY_USAGE_GENERAL_PREFIX = "usage_general_"
    private const val KEY_USAGE_PERSON_PREFIX = "usage_person_"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun people(context: Context): List<ReminderPerson> = try {
        val array = JSONArray(prefs(context).getString(KEY_PEOPLE, "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(ReminderPerson(item.getString("id"), item.getString("name")))
            }
        }
    } catch (_: Exception) { emptyList() }

    fun addPerson(context: Context, name: String) {
        val clean = name.trim().take(30)
        if (clean.isEmpty()) return
        val list = people(context).toMutableList()
        list.add(ReminderPerson(UUID.randomUUID().toString(), clean))
        savePeople(context, list)
    }

    fun removePerson(context: Context, id: String) {
        savePeople(context, people(context).filterNot { it.id == id })
        if (activePersonId(context) == id) setActivePerson(context, null)
    }

    private fun savePeople(context: Context, people: List<ReminderPerson>) {
        val array = JSONArray()
        people.forEach { person -> array.put(JSONObject().put("id", person.id).put("name", person.name)) }
        prefs(context).edit().putString(KEY_PEOPLE, array.toString()).apply()
    }

    fun reminders(context: Context): List<ProjectorReminder> = try {
        val array = JSONArray(prefs(context).getString(KEY_REMINDERS, "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val dayArray = item.optJSONArray("days") ?: JSONArray()
                val days = buildSet { for (d in 0 until dayArray.length()) add(dayArray.getInt(d)) }
                add(ProjectorReminder(
                    id = item.getString("id"), title = item.getString("title"),
                    hour = item.getInt("hour"), minute = item.getInt("minute"),
                    personId = item.optString("personId").takeIf { it.isNotEmpty() },
                    days = if (days.isEmpty()) (1..7).toSet() else days,
                    endsAt = item.optLong("endsAt", 0L)
                ))
            }
        }
    } catch (_: Exception) { emptyList() }

    fun addReminder(context: Context, reminder: ProjectorReminder) {
        saveReminders(context, reminders(context) + reminder)
    }

    /** Substitui um lembrete mantendo o mesmo identificador. */
    fun updateReminder(context: Context, reminder: ProjectorReminder) {
        saveReminders(context, reminders(context).map { if (it.id == reminder.id) reminder else it })
    }

    fun removeReminder(context: Context, id: String) {
        saveReminders(context, reminders(context).filterNot { it.id == id })
    }

    private fun saveReminders(context: Context, reminders: List<ProjectorReminder>) {
        val array = JSONArray()
        reminders.forEach { reminder ->
            array.put(JSONObject()
                .put("id", reminder.id)
                .put("title", reminder.title)
                .put("hour", reminder.hour)
                .put("minute", reminder.minute)
                .put("personId", reminder.personId ?: "")
                .put("days", JSONArray(reminder.days.toList()))
                .put("endsAt", reminder.endsAt)
            )
        }
        prefs(context).edit().putString(KEY_REMINDERS, array.toString()).apply()
    }

    fun activePersonId(context: Context): String? = prefs(context).getString(KEY_ACTIVE_PERSON, null)
    fun setActivePerson(context: Context, id: String?) = prefs(context).edit().putString(KEY_ACTIVE_PERSON, id).apply()

    fun personName(context: Context, id: String?): String =
        if (id == null) "Geral" else people(context).firstOrNull { it.id == id }?.name ?: "Pessoa removida"

    fun shouldShow(context: Context, reminder: ProjectorReminder, now: Calendar): Boolean {
        if (reminder.personId != null && reminder.personId != activePersonId(context)) return false
        if (reminder.hour != now.get(Calendar.HOUR_OF_DAY) || reminder.minute != now.get(Calendar.MINUTE)) return false
        if (now.get(Calendar.DAY_OF_WEEK) !in reminder.days) return false
        if (reminder.endsAt > 0L && now.timeInMillis > reminder.endsAt) return false

        val occurrence = "%04d%02d%02d%02d%02d".format(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
            reminder.hour, reminder.minute
        )
        return prefs(context).getString(KEY_LAST_PREFIX + reminder.id, "") != occurrence
    }

    fun markShown(context: Context, reminder: ProjectorReminder, now: Calendar) {
        val occurrence = "%04d%02d%02d%02d%02d".format(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
            reminder.hour, reminder.minute
        )
        prefs(context).edit().putString(KEY_LAST_PREFIX + reminder.id, occurrence).apply()
    }

    /** Soma no relatório geral e, quando alguém foi escolhido, também no relatório daquela pessoa. */
    fun addUsageSecond(context: Context, personId: String?) {
        val date = dateKey(Calendar.getInstance())
        val prefs = prefs(context)
        val editor = prefs.edit()
        editor.putLong(KEY_USAGE_GENERAL_PREFIX + date, prefs.getLong(KEY_USAGE_GENERAL_PREFIX + date, 0L) + 1L)
        if (personId != null) {
            val key = KEY_USAGE_PERSON_PREFIX + personId + "_" + date
            editor.putLong(key, prefs.getLong(key, 0L) + 1L)
        }
        val dates = usageDates(context).toMutableSet()
        if (dates.add(date)) editor.putString(KEY_USAGE_DATES, JSONArray(dates.sorted()).toString())
        editor.apply()
    }

    data class UsageReport(
        val generalToday: Long,
        val generalMonth: Long,
        val peopleToday: List<Pair<ReminderPerson, Long>>,
        val peopleMonth: List<Pair<ReminderPerson, Long>>
    )

    fun usageReport(context: Context): UsageReport {
        val now = Calendar.getInstance()
        val today = dateKey(now)
        val month = today.take(7)
        val allDates = usageDates(context)
        val prefs = prefs(context)
        fun total(prefix: String, matching: (String) -> Boolean) = allDates.filter(matching).sumOf { prefs.getLong(prefix + it, 0L) }
        val people = people(context)
        return UsageReport(
            generalToday = prefs.getLong(KEY_USAGE_GENERAL_PREFIX + today, 0L),
            generalMonth = total(KEY_USAGE_GENERAL_PREFIX) { it.startsWith(month) },
            peopleToday = people.map { it to prefs.getLong(KEY_USAGE_PERSON_PREFIX + it.id + "_" + today, 0L) },
            peopleMonth = people.map { person -> person to allDates.filter { it.startsWith(month) }.sumOf { date -> prefs.getLong(KEY_USAGE_PERSON_PREFIX + person.id + "_" + date, 0L) } }
        )
    }

    private fun usageDates(context: Context): List<String> = try {
        val array = JSONArray(prefs(context).getString(KEY_USAGE_DATES, "[]"))
        List(array.length()) { array.getString(it) }
    } catch (_: Exception) { emptyList() }

    private fun dateKey(calendar: Calendar): String = String.format(
        Locale.US, "%04d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH)
    )
}
