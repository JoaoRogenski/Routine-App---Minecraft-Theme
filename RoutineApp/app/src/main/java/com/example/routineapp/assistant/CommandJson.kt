package com.example.routineapp.assistant

import com.example.routineapp.data.Recurrence
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

class InvalidCommandException(message: String) : Exception(message)

/**
 * Validates the AI's JSON. Everything coming from the model is untrusted:
 * unknown actions, bad dates or ids we don't know are rejected, never "fixed up".
 *
 * Shape: {"action":"create|complete|delete|reschedule|list|when|clarify|reply",
 *  "tasks":[{title,date:"YYYY-MM-DD"|null,start:"HH:MM"|null,durationMinutes,description,category,
 *           priority:0-2,location,notes,recurrence:"NONE|DAILY|WEEKLY|MONTHLY"}],
 *  "target":{"id":123|null,"title":..., "date":...}, "date":..., "start":..., "allDay":bool, "text":"..."}
 */
object CommandJson {
    fun parse(json: JSONObject, today: LocalDate, knownIds: Set<Long>): Command {
        return when (val action = json.optString("action")) {
            "create" -> {
                val arr = json.optJSONArray("tasks") ?: throw InvalidCommandException("tasks ausente")
                if (arr.length() !in 1..10) throw InvalidCommandException("quantidade de tarefas inválida")
                Command.Create((0 until arr.length()).map { draft(arr.getJSONObject(it), today) })
            }
            "complete" -> Command.Complete(ref(json, today, knownIds))
            "delete" -> Command.Delete(ref(json, today, knownIds))
            "reschedule" -> Command.Reschedule(ref(json, today, knownIds), date(json, "date", today), time(json, "start"))
            "list" -> Command.ListTasks(date(json, "date", today))
            "when" -> Command.When(date(json, "date", today), time(json, "start"), json.optBoolean("allDay", false))
            "clarify" -> Command.Clarify(text(json, "text", 500) ?: throw InvalidCommandException("pergunta vazia"))
            "reply" -> Command.Reply(text(json, "text", 1000) ?: throw InvalidCommandException("resposta vazia"))
            else -> throw InvalidCommandException("ação desconhecida: ${action.take(30)}")
        }
    }

    private fun draft(o: JSONObject, today: LocalDate): TaskDraft {
        val title = text(o, "title", 200) ?: throw InvalidCommandException("título vazio")
        val duration = if (o.isNull("durationMinutes") || !o.has("durationMinutes")) null else o.getInt("durationMinutes")
        if (duration != null && duration !in 1..24 * 60) throw InvalidCommandException("duração inválida")
        val priority = if (o.isNull("priority") || !o.has("priority")) null else o.getInt("priority")
        if (priority != null && priority !in 0..2) throw InvalidCommandException("prioridade inválida")
        val recurrence = text(o, "recurrence", 10)?.let {
            runCatching { Recurrence.valueOf(it) }.getOrElse { throw InvalidCommandException("recorrência inválida") }
        } ?: Recurrence.NONE
        return TaskDraft(
            title = title, date = date(o, "date", today), startMinute = time(o, "start"), durationMinutes = duration,
            description = text(o, "description", 1000), category = text(o, "category", 40), priority = priority,
            location = text(o, "location", 200), notes = text(o, "notes", 1000), recurrence = recurrence,
            allDay = o.optBoolean("allDay", false),
        )
    }

    private fun ref(json: JSONObject, today: LocalDate, knownIds: Set<Long>): TaskRef {
        val o = json.optJSONObject("target") ?: return TaskRef()
        val id = if (o.isNull("id") || !o.has("id")) null else o.getLong("id")
        if (id != null && id !in knownIds) throw InvalidCommandException("tarefa inexistente")
        return TaskRef(id, text(o, "title", 200), date(o, "date", today))
    }

    private fun text(o: JSONObject, key: String, max: Int): String? {
        if (!o.has(key) || o.isNull(key)) return null
        val v = o.get(key) as? String ?: throw InvalidCommandException("$key deve ser texto")
        return v.trim().take(max).ifBlank { null }
    }

    private fun date(o: JSONObject, key: String, today: LocalDate): LocalDate? {
        val s = text(o, key, 10) ?: return null
        val d = runCatching { LocalDate.parse(s) }.getOrElse { throw InvalidCommandException("data inválida: $s") }
        if (d.isBefore(today.minusYears(1)) || d.isAfter(today.plusYears(5))) throw InvalidCommandException("data fora do intervalo")
        return d
    }

    private fun time(o: JSONObject, key: String): Int? {
        val s = text(o, key, 5) ?: return null
        val t = runCatching { LocalTime.parse(s.padStart(5, '0')) }.getOrElse { throw InvalidCommandException("horário inválido: $s") }
        return t.hour * 60 + t.minute
    }

    fun tasksArray(items: List<com.example.routineapp.data.Task>) = JSONArray().apply {
        items.forEach {
            put(JSONObject().put("id", it.id).put("title", it.title).put("date", it.date.toString())
                .put("start", it.startTime?.toString() ?: JSONObject.NULL))
        }
    }
}
