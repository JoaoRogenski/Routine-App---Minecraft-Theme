package com.example.routineapp.assistant

import com.example.routineapp.data.Recurrence
import com.example.routineapp.data.AppSettings
import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskSource
import com.example.routineapp.data.TaskStatus
import com.example.routineapp.domain.ReminderRules
import com.example.routineapp.domain.TaskRepository
import com.example.routineapp.ui.Formats
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class AssistantReply(val text: String, val tasks: List<Task> = emptyList(), val error: Boolean = false)

/**
 * Conversation engine: interprets a message (AI backend or local rules), validates it against the
 * real task store and only then changes data. Replies describe what was actually saved.
 */
class Assistant(
    private val tasks: TaskRepository,
    private val settings: () -> AppSettings,
    private val backend: BackendClient?,
    private val notificationsAllowed: () -> Boolean = { true },
    private val clock: () -> LocalDateTime = LocalDateTime::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    // conversation state
    private var pendingDraft: TaskDraft? = null
    private val queue = ArrayDeque<TaskDraft>()
    private var pendingConfirm: (suspend () -> AssistantReply)? = null
    private var choices: List<Task> = emptyList()
    private var choiceAction: (suspend (Task) -> AssistantReply)? = null
    private var lastTaskId: Long? = null
    private val history = ArrayDeque<Pair<String, String>>()
    private var nameUsed = false

    private val now get() = clock()
    private val today get() = now.toLocalDate()

    suspend fun handle(message: String): AssistantReply {
        val reply = try {
            process(message.trim())
        } catch (e: Exception) {
            AssistantReply("Algo deu errado e nada foi alterado (${e.message ?: e.javaClass.simpleName}). Pode tentar de novo?", error = true)
        }
        remember("user", message); remember("assistant", reply.text)
        return reply
    }

    /** Forgets the conversation (pending questions, context); tasks already saved stay. */
    fun reset() {
        pendingDraft = null; queue.clear(); pendingConfirm = null
        choices = emptyList(); choiceAction = null; lastTaskId = null
        history.clear(); nameUsed = false
    }

    private fun remember(role: String, text: String) {
        history.addLast(role to text.take(500))
        while (history.size > 8) history.removeFirst()
    }

    private suspend fun process(message: String): AssistantReply {
        val low = LocalParser.strip(message).trim().trimEnd('.', '!')
        pendingConfirm?.let { action ->
            pendingConfirm = null
            if (YES.matches(low)) return action()
            if (NO.matches(low)) { pendingDraft = null; queue.clear(); return AssistantReply("Tudo bem, não mexi em nada.") }
        }
        choiceAction?.let { action ->
            val list = choices
            choiceAction = null; choices = emptyList()
            low.toIntOrNull()?.let { n -> list.getOrNull(n - 1)?.let { return action(it) } }
        }
        val (command, note) = interpret(message)
        val reply = execute(command)
        return if (note != null) reply.copy(text = "$note\n\n${reply.text}") else reply
    }

    private suspend fun interpret(message: String): Pair<Command, String?> {
        val s = settings()
        if (s.aiEnabled && backend != null && backend.isConfigured) {
            try {
                val upcoming = tasks.pendingFrom(today.minusDays(7)).take(60)
                val body = JSONObject()
                    .put("message", message)
                    .put("now", now.withNano(0).toString())
                    .put("weekday", LocalParser.weekdayName(today.dayOfWeek))
                    .put("timezone", zone().id)
                    .put("tasks", CommandJson.tasksArray(upcoming))
                    .put("pendingTask", pendingDraft?.let { JSONObject().put("title", it.title)
                        .put("date", it.date?.toString() ?: JSONObject.NULL)
                        .put("start", it.startMinute?.let { m -> "%02d:%02d".format(m / 60, m % 60) } ?: JSONObject.NULL) }
                        ?: JSONObject.NULL)
                    .put("history", JSONArray().apply { history.forEach { put(JSONObject().put("role", it.first).put("text", it.second)) } })
                val json = backend.parse(body)
                return CommandJson.parse(json.getJSONObject("command"), today, upcoming.map { it.id }.toSet()) to null
            } catch (e: Exception) {
                return LocalParser.parse(message, now) to "(A IA não respondeu: ${e.message}. Usei o modo básico.)"
            }
        }
        return LocalParser.parse(message, now) to null
    }

    private suspend fun execute(c: Command): AssistantReply = when (c) {
        is Command.Reply -> when (c.text) {
            "GREETING" -> AssistantReply("Oi${name()}! Me conta o que você precisa fazer e quando — eu anoto e te lembro.")
            "HELP" -> AssistantReply(HELP)
            "UNKNOWN" -> AssistantReply("Não entendi bem. Tente algo como \"me lembra de pagar a conta amanhã às 18h\".")
            else -> AssistantReply(c.text)
        }
        is Command.Clarify -> AssistantReply(c.question)
        is Command.Create -> createAll(c.drafts)
        is Command.When -> {
            val draft = pendingDraft
            val last = lastTaskId?.let { tasks.get(it) }
            when {
                draft != null -> {
                    pendingDraft = null
                    continueQueue(createDraft(draft.copy(date = c.date ?: draft.date,
                        startMinute = c.startMinute ?: draft.startMinute, allDay = c.allDay || draft.allDay)))
                }
                last != null && last.status == TaskStatus.PENDING && !c.allDay -> reschedule(last, c.date, c.startMinute)
                else -> AssistantReply("Para qual tarefa? Me diga o que precisa fazer e quando.")
            }
        }
        is Command.Complete -> withTask(c.ref, "concluir") { t ->
            val done = tasks.complete(t.id) ?: return@withTask notFound()
            lastTaskId = done.id
            val next = if (t.recurrence != Recurrence.NONE) " A próxima ficou para ${Formats.relativeDate(done.date, today)}." else ""
            AssistantReply("Boa! Marquei “${t.title}” como concluída e cancelei os avisos dela.$next")
        }
        is Command.Delete -> withTask(c.ref, "excluir") { t ->
            pendingConfirm = {
                tasks.delete(t.id)
                if (lastTaskId == t.id) lastTaskId = null
                AssistantReply("Excluí “${t.title}”. Não vou mais te avisar sobre ela.")
            }
            AssistantReply("Excluir “${t.title}” (${whenText(t)})? Responda sim ou não.")
        }
        is Command.Reschedule -> withTask(c.ref, "mudar") { t -> reschedule(t, c.date, c.startMinute) }
        is Command.ListTasks -> list(c.date)
    }

    private suspend fun createAll(drafts: List<TaskDraft>): AssistantReply {
        queue.clear()
        queue.addAll(drafts.drop(1))
        return continueQueue(createDraft(drafts.first()))
    }

    /** After one draft is handled, keep going through the rest unless we're waiting for the user. */
    private suspend fun continueQueue(first: AssistantReply): AssistantReply {
        var reply = first
        while (queue.isNotEmpty() && pendingDraft == null && pendingConfirm == null) {
            val next = createDraft(queue.removeFirst())
            reply = AssistantReply(reply.text + "\n\n" + next.text, reply.tasks + next.tasks, reply.error || next.error)
        }
        return reply
    }

    private suspend fun createDraft(d: TaskDraft, conflictOk: Boolean = false): AssistantReply {
        var date = d.date
        if (date == null) {
            val start = d.startMinute
            if (start != null && now.toLocalTime().toSecondOfDay() / 60 < start) date = today
            else {
                pendingDraft = d
                return AssistantReply(if (start != null) "Para qual dia é “${d.title}”? Hoje às ${Formats.time(start)} já passou."
                    else "Para quando é “${d.title}”? Pode me dizer o dia e, se tiver, o horário.")
            }
        }
        if (isPast(date, d.startMinute)) {
            pendingDraft = d.copy(date = null)
            return AssistantReply("${Formats.date(date, today).replaceFirstChar { it.uppercase() }}" +
                "${d.startMinute?.let { " às " + Formats.time(it) } ?: ""} já passou. Para quando devo marcar “${d.title}”?")
        }
        val stamp = System.currentTimeMillis()
        val task = Task(
            title = d.title, description = d.description, epochDay = date.toEpochDay(), startMinute = d.startMinute,
            durationMinutes = d.durationMinutes, category = d.category, priority = d.priority ?: 1, location = d.location,
            notes = d.notes, recurrence = d.recurrence, createdAt = stamp, updatedAt = stamp, source = TaskSource.CHAT,
        )
        tasks.findDuplicate(task)?.let {
            lastTaskId = it.id
            return AssistantReply("Você já tem “${it.title}” ${whenText(it)}. Não registrei de novo para não duplicar.", listOf(it))
        }
        if (!conflictOk) {
            val clash = tasks.conflicts(task)
            if (clash.isNotEmpty()) {
                pendingConfirm = { continueQueue(createDraft(d.copy(date = date), conflictOk = true)) }
                return AssistantReply("Nesse horário você já tem ${clash.joinToString { "“${it.title}” (${timeRange(it)})" }}. " +
                    "Quer registrar “${d.title}” mesmo assim? Responda sim ou não.")
            }
        }
        val saved = tasks.create(task)
        lastTaskId = saved.id
        val extras = buildString {
            if (saved.recurrence != Recurrence.NONE) append(" Repete ${RECURRENCE_TEXT[saved.recurrence]}.")
            saved.description?.let { append(" Anotei também: \"$it\"") }
        }
        return AssistantReply("Fechado${name()}! Registrei “${saved.title}” para ${whenText(saved)}.$extras${reminderText(saved)}", listOf(saved))
    }

    private suspend fun reschedule(t: Task, date: LocalDate?, start: Int?, conflictOk: Boolean = false): AssistantReply {
        val newDate = date ?: t.date
        val newStart = start ?: t.startMinute
        if (isPast(newDate, newStart)) return AssistantReply("Esse horário já passou. Me diga um dia e horário futuros para “${t.title}”.")
        val moved = t.copy(epochDay = newDate.toEpochDay(), startMinute = newStart)
        if (!conflictOk) {
            val clash = tasks.conflicts(moved)
            if (clash.isNotEmpty()) {
                pendingConfirm = { reschedule(t, date, start, conflictOk = true) }
                return AssistantReply("Nesse horário você já tem ${clash.joinToString { "“${it.title}” (${timeRange(it)})" }}. " +
                    "Mudo “${t.title}” mesmo assim? Responda sim ou não.")
            }
        }
        val saved = tasks.update(moved)
        lastTaskId = saved.id
        return AssistantReply("Pronto, “${saved.title}” agora está para ${whenText(saved)}.${reminderText(saved)}", listOf(saved))
    }

    private suspend fun list(date: LocalDate?): AssistantReply {
        val items = if (date != null) tasks.pendingFrom(date).filter { it.date == date }
            else tasks.pendingFrom(today).filter { it.date <= today.plusDays(7) }
        val label = date?.let { Formats.relativeDate(it, today) } ?: "os próximos 7 dias"
        if (items.isEmpty()) return AssistantReply("Nada pendente para $label. 🎉")
        return AssistantReply("Pendentes para $label:\n" + items.joinToString("\n") { t ->
            "• " + (if (date == null) Formats.date(t.date, today) + ", " else "") + (t.startTime?.let { Formats.time(it) } ?: "sem horário") + " — " + t.title
        }, items)
    }

    private suspend fun withTask(ref: TaskRef, verb: String, action: suspend (Task) -> AssistantReply): AssistantReply {
        val candidates = when {
            ref.id != null -> listOfNotNull(tasks.get(ref.id))
            ref.isEmpty -> listOfNotNull(lastTaskId?.let { tasks.get(it) })
            else -> match(tasks.pendingFrom(today.minusDays(30)), ref)
        }
        return when (candidates.size) {
            0 -> notFound(ref)
            1 -> action(candidates.single())
            else -> {
                choices = candidates.take(5)
                choiceAction = action
                AssistantReply("Achei mais de uma. Qual devo $verb?\n" +
                    choices.mapIndexed { i, t -> "${i + 1}) ${t.title} — ${whenText(t)}" }.joinToString("\n") +
                    "\nResponda com o número.", choices)
            }
        }
    }

    private fun match(list: List<Task>, ref: TaskRef): List<Task> {
        val byDate = if (ref.date != null) list.filter { it.date == ref.date } else list
        val words = ref.title?.let { TaskRepository.normalize(it).split(" ").filter { w -> w.length >= 3 && w !in STOP } }.orEmpty()
        if (words.isEmpty()) return if (ref.title.isNullOrBlank()) byDate else emptyList()
        val titles = byDate.associateWith { TaskRepository.normalize(it.title) }
        val full = byDate.filter { t -> words.all { it in titles.getValue(t) } }
        return full.ifEmpty { byDate.filter { t -> words.any { it in titles.getValue(t) } } }
    }

    private fun notFound(ref: TaskRef? = null) = AssistantReply(
        if (ref?.title != null) "Não encontrei nenhuma tarefa pendente parecida com “${ref.title}”."
        else "Não encontrei essa tarefa. Pode me dizer o nome dela?")

    private fun isPast(date: LocalDate, start: Int?): Boolean =
        if (start == null) date.isBefore(today) else date.atTime(start / 60, start % 60).isBefore(now)

    private fun whenText(t: Task) = Formats.relativeDate(t.date, today) +
        (t.startTime?.let { ", às ${Formats.time(it)}" } ?: " (sem horário)") +
        (t.durationMinutes?.let { " por ${Formats.duration(it)}" } ?: "")

    private fun timeRange(t: Task) = t.startMinute?.let { s ->
        Formats.time(s) + (t.durationMinutes?.let { "–" + Formats.time((s + it).coerceAtMost(24 * 60 - 1)) } ?: "")
    } ?: "sem horário"

    /** Only promises what will actually happen given settings and permissions. */
    private fun reminderText(t: Task): String {
        if (t.startMinute == null) return " Como está sem horário, não vou mandar lembrete nem cobrar. Se quiser, me diga a hora (ex.: \"às 15h\")."
        if (!notificationsAllowed()) return " Atenção: as notificações estão bloqueadas, então não consigo te avisar. Libere em Configurações."
        val s = settings()
        val nowMillis = now.atZone(zone()).toInstant().toEpochMilli()
        val plan = ReminderRules.plan(t, nowMillis, zone(), s, justChanged = true)
        return buildString {
            plan.reminderAt?.let {
                val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone())
                append(" Vou te lembrar às ${Formats.time(at.toLocalTime())}" +
                    (if (at.toLocalDate() != t.date) " de ${Formats.relativeDate(at.toLocalDate(), today)}" else "") + ".")
            }
            if (plan.notifyNow) append(" Como falta menos de 5 horas, já te mandei o lembrete.")
            if (plan.nagAt != null) append(" Se ainda estiver pendente na hora, eu te cobro.")
        }
    }

    private fun name(): String {
        val n = settings().name.trim()
        if (n.isEmpty() || nameUsed) return ""
        nameUsed = true
        return ", $n"
    }

    companion object {
        private val YES = Regex("^(sim|s|pode|pode sim|ok|isso|confirmo|confirma|claro|beleza|bora|manda|quero|registra|exclui|apaga|muda)( .*)?$")
        private val NO = Regex("^(nao|n|deixa|deixa pra la|cancela|esquece|nem)( .*)?$")
        private val STOP = setOf("que", "para", "pra", "com", "uma", "das", "dos", "tarefa", "minha", "meu")
        private val RECURRENCE_TEXT = mapOf(Recurrence.DAILY to "todo dia", Recurrence.WEEKLY to "toda semana",
            Recurrence.MONTHLY to "todo mês")
        private const val HELP = "Eu transformo seus recados em tarefas no calendário. Exemplos:\n" +
            "• \"Me lembra de pagar a internet amanhã às 18h\"\n" +
            "• \"Dentista sexta que vem às 14h30 por 1 hora\"\n" +
            "• \"Academia toda segunda às 7h\"\n" +
            "• \"O que eu tenho amanhã?\"\n" +
            "• \"Concluí o dentista\" / \"Apaga a reunião de sexta\" / \"Muda o dentista para sábado às 10h\"\n" +
            "Digite /clear para limpar a conversa.\n" +
            "Aviso 5 horas antes e, se a tarefa continuar pendente na hora, eu te cobro."
    }
}
