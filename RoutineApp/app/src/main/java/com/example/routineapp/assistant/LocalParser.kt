package com.example.routineapp.assistant

import com.example.routineapp.data.Recurrence
import java.text.Normalizer
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Rule-based pt-BR interpreter, used when no AI backend is configured or it fails.
 * One main task per message; a "preciso X antes" sentence becomes a second task, other sentences the description.
 */
object LocalParser {
    private val WEEKDAYS = listOf("segunda", "terca", "quarta", "quinta", "sexta", "sabado", "domingo")
    private val MONTHS = listOf("janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho",
        "agosto", "setembro", "outubro", "novembro", "dezembro")
    private val NUMBERS = mapOf("um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "quatro" to 4,
        "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9, "dez" to 10, "onze" to 11,
        "doze" to 12, "quinze" to 15, "vinte" to 20, "trinta" to 30, "quarenta" to 40)
    private val NUM = "(\\d{1,3}|${NUMBERS.keys.joinToString("|")})"
    private val WD = "(segunda|terca|quarta|quinta|sexta|sabado|domingo)"
    private val PREP = "(?:(?:na|no|nesta|neste|nessa|nesse|para|pra|ate|em|de|do|da|a|o)\\s+)?"

    private fun num(s: String) = s.toIntOrNull() ?: NUMBERS.getValue(s)

    fun strip(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

    fun parse(text: String, now: LocalDateTime): Command {
        val src = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
        val low = strip(src)
        if (low.isBlank()) return Command.Reply("Pode mandar sua tarefa, por exemplo: \"pagar a conta amanhã às 18h\".")

        if (Regex("^(oi|ola|bom dia|boa tarde|boa noite|e ai|opa|hey)\\b[\\s!.,?]*\\S{0,12}[!.?]*$").matches(low))
            return Command.Reply("GREETING")
        if (Regex("\\b(ajuda|o que voce faz|como funciona|como usar)\\b").containsMatchIn(low))
            return Command.Reply("HELP")

        val listIntent = Regex("\\b(o que (eu )?tenho|quais (sao )?(as )?(minhas )?(tarefas|compromissos)|minha agenda|" +
            "mostr[ae]|list[ae]|tenho algo|tenho alguma coisa)\\b").containsMatchIn(low)
        if (listIntent) return Command.ListTasks(Scan(src, low, now).also { it.parseDateTime() }.date)

        val complete = Regex("^(?:eu\\s+)?(?:ja\\s+)?(conclui|concluir|conclua|terminei|terminar|finalizei|finalizar|fiz|feito|feita|" +
            "marca(?:r)?(?: [^,]*?)? como (?:feit[ao]|concluid[ao]))\\b")
        val delete = Regex("^(?:pode\\s+)?(apaga|apague|apagar|exclui|exclua|excluir|remove|remova|remover|" +
            "cancela|cancele|cancelar|desmarca|desmarque|desmarcar|deleta|delete|deletar)\\b")
        val reschedule = Regex("^(?:pode\\s+)?(muda|mude|mudar|remarca|remarque|remarcar|adia|adie|adiar|transfere|" +
            "transfira|passa|passe|move|mova|altera|altere|reagenda|reagende|troca|troque)\\b")

        complete.find(low)?.let { return Command.Complete(ref(src, low, it.range.last + 1, now)) }
        delete.find(low)?.let { return Command.Delete(ref(src, low, it.range.last + 1, now)) }
        reschedule.find(low)?.let { m ->
            val split = Regex("\\s(?:para|pra|pro)\\s").findAll(low).lastOrNull { it.range.first > m.range.last }
            val left = if (split != null) ref(src.substring(0, split.range.first), low.substring(0, split.range.first), m.range.last + 1, now)
                else TaskRef()
            val target = Scan(src, low, now, from = split?.range?.last ?: (m.range.last + 1)).also { it.parseDateTime() }
            if (target.date == null && target.startMinute == null)
                return Command.Clarify("Para quando devo mudar? Diga o novo dia e/ou horário.")
            return Command.Reschedule(left, target.date, target.startMinute)
        }

        // a bare date/time ("amanhã às 15h", "sem horário") answers the previous question
        val scanAll = Scan(src, low, now).also { it.parseDateTime() }
        if (scanAll.ambiguity != null) return Command.Clarify(scanAll.ambiguity!!)
        val leftover = scanAll.title()
        if (leftover.isBlank() || Regex("^(sem horario|o dia todo|dia todo|qualquer hora)$").matches(strip(leftover)))
            return if (scanAll.date != null || scanAll.startMinute != null || scanAll.allDay)
                Command.When(scanAll.date, scanAll.startMinute, scanAll.allDay)
            else Command.Reply("UNKNOWN")

        // pick the first sentence carrying a date or time as the task; the rest is description
        val sentences = Regex("[^.!?\\n]+[.!?]*").findAll(src).map { it.range }.toList()
        val main = sentences.firstOrNull { r ->
            Scan(src.substring(r.first, r.last + 1), low.substring(r.first, r.last + 1), now)
                .also { it.parseDateTime() }.let { it.date != null || it.startMinute != null }
        } ?: (0 until src.length)
        val scan = Scan(src.substring(main.first, main.last + 1), low.substring(main.first, main.last + 1), now)
            .also { it.parseDateTime(); it.parseExtras() }
        if (scan.ambiguity != null) return Command.Clarify(scan.ambiguity!!)
        val title = scan.title()
        if (title.isBlank()) return Command.Reply("UNKNOWN")
        var mainDuration = scan.duration
        val extras = mutableListOf<TaskDraft>()
        val descParts = mutableListOf<String>()
        for (r in sentences) {
            if (r == main) continue
            val s = src.substring(r.first, r.last + 1).trim()
            val ls = strip(s)
            val sc = Scan(s, ls, now).also { it.parseDateTime(); it.parseExtras() }
            val t = sc.title()
            when {
                // "Reserve uma hora para isso": duration for the task just mentioned
                sc.duration != null && SELF_REF.matches(strip(t)) ->
                    if (extras.isNotEmpty()) extras[extras.lastIndex] = extras.last().copy(durationMinutes = sc.duration)
                    else mainDuration = sc.duration
                PREP_TASK.containsMatchIn(ls) && sc.ambiguity == null &&
                    (RELATIVE.containsMatchIn(ls) || sc.date != null || sc.startMinute != null || sc.duration != null) -> {
                    val extraTitle = t.replace(Regex("\\s+(?:antes|depois)\\b.*$", RegexOption.IGNORE_CASE), "").trim()
                    // no date unless stated: the assistant asks when instead of guessing what "antes" means
                    if (extraTitle.isNotBlank()) extras += TaskDraft(title = extraTitle, date = sc.date, startMinute = sc.startMinute,
                        durationMinutes = sc.duration, priority = sc.priority)
                    else descParts += s
                }
                else -> descParts += s
            }
        }
        return Command.Create(listOf(TaskDraft(
            title = title, date = scan.date, startMinute = scan.startMinute, durationMinutes = mainDuration,
            description = descParts.joinToString(" ").ifBlank { null }, priority = scan.priority,
            recurrence = scan.recurrence, allDay = scan.allDay,
        )) + extras)
    }

    private val PREP_TASK = Regex("^(?:eu\\s+)?(?:preciso|tenho que|tenho de|devo|vou ter que)\\s+\\w+")
    private val RELATIVE = Regex("\\b(?:antes|depois)\\b")
    private val SELF_REF = Regex("^(?:(?:para|pra|nisso|com)\\s*)?(?:isso|ela|ele|essa|esse|isto)?$")

    private fun ref(src: String, low: String, from: Int, now: LocalDateTime): TaskRef {
        val s = Scan(src, low, now, from).also { it.parseDateTime() }
        val title = s.title().replace(Regex("^(?:(?:a|o|minha|meu)\\s+)?(?:tarefa|compromisso|lembrete)?\\s*(?:de\\s+)?", RegexOption.IGNORE_CASE), "")
            .trim().ifBlank { null }
        return TaskRef(title = title, date = s.date)
    }

    /** Works on the original text and its accent-free lowercase copy, which have equal lengths (NFC input). */
    private class Scan(src: String, low: String, val now: LocalDateTime, from: Int = 0) {
        val orig = StringBuilder(src).also { for (i in 0 until minOf(from, it.length)) it.setCharAt(i, ' ') }
        val text = StringBuilder(low).also { for (i in 0 until minOf(from, it.length)) it.setCharAt(i, ' ') }
        val today: LocalDate = now.toLocalDate()
        var date: LocalDate? = null
        var startMinute: Int? = null
        var duration: Int? = null
        var priority: Int? = null
        var recurrence = Recurrence.NONE
        var allDay = false
        var ambiguity: String? = null

        /** Finds [re] in the not-yet-consumed text and blanks the match so it's not reused or kept in the title. */
        fun take(re: String): MatchResult? {
            // match on a snapshot: MatchResult reads groups lazily from its input, which we blank below
            val m = Regex(re).find(text.toString()) ?: return null
            for (i in m.range) { text.setCharAt(i, ' '); orig.setCharAt(i, ' ') }
            return m
        }

        fun parseDateTime() {
            take("\\b(?:sem horario|o dia (?:todo|inteiro)|dia todo)\\b")?.let { allDay = true }

            take("\\b(?:daqui a|daqui|em|dentro de)\\s+$NUM\\s+(minutos?|min|horas?)\\b")?.let {
                val n = num(it.groupValues[1]).toLong()
                val t = if (it.groupValues[2].startsWith("h")) now.plusHours(n) else now.plusMinutes(n)
                date = t.toLocalDate(); startMinute = t.hour * 60 + t.minute
            }
            take("\\b(?:daqui a|daqui|em|dentro de)\\s+$NUM\\s+(dias?|semanas?|mes|meses)\\b")?.let {
                val n = num(it.groupValues[1]).toLong()
                date = when (it.groupValues[2].first()) {
                    'd' -> today.plusDays(n); 's' -> today.plusWeeks(n); else -> today.plusMonths(n)
                }
            }
            if (date == null) take("\\b${PREP}depois de amanha\\b")?.let { date = today.plusDays(2) }
            if (date == null) take("\\b${PREP}amanha\\b")?.let { date = today.plusDays(1) }
            if (date == null) take("\\b${PREP}hoje\\b")?.let { date = today }

            val recurring = take("\\b(?:todo dia|todos os dias|diariamente|toda semana|todas as semanas|semanalmente|" +
                "todo mes|todos os meses|mensalmente)\\b")
            recurrence = when {
                recurring == null -> Recurrence.NONE
                recurring.value.startsWith("di") || recurring.value.contains("dia") -> Recurrence.DAILY
                recurring.value.contains("seman") -> Recurrence.WEEKLY
                else -> Recurrence.MONTHLY
            }
            val everyWeekday = take("\\b(?:toda|todo|todas as|todos os)\\s+${WD}s?(?:[- ]feiras?)?\\b")
            val weekdayMatch = everyWeekday ?: take("\\b$PREP(?:proxim[ao]\\s+)?$WD(?:[- ]feira)?(?:\\s+que vem)?\\b")
            if (everyWeekday != null) recurrence = Recurrence.WEEKLY
            val weekday = weekdayMatch?.let { DayOfWeek.of(WEEKDAYS.indexOf(it.groupValues[1]) + 1) }

            var explicit: LocalDate? = null
            take("\\b$PREP(?:dia\\s+)?(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")?.let {
                val y = it.groupValues[3].takeIf { y -> y.isNotEmpty() }?.toInt()?.let { y -> if (y < 100) 2000 + y else y } ?: today.year
                explicit = safeDate(y, it.groupValues[2].toInt(), it.groupValues[1].toInt())
            }
            if (explicit == null) take("\\b$PREP(?:dia\\s+)?(\\d{1,2})\\s+de\\s+(${MONTHS.joinToString("|")})(?:\\s+de\\s+(\\d{4}))?\\b")?.let {
                val y = it.groupValues[3].toIntOrNull() ?: today.year
                explicit = safeDate(y, MONTHS.indexOf(it.groupValues[2]) + 1, it.groupValues[1].toInt())
            }
            if (explicit == null) take("\\b${PREP}dia\\s+(\\d{1,2})\\b")?.let {
                val d = it.groupValues[1].toInt()
                // day without month: this month if still ahead, otherwise next month
                val base = if (d >= today.dayOfMonth) today else today.plusMonths(1)
                explicit = safeDate(base.year, base.monthValue, d)
            }
            if (ambiguity != null) return

            date = when {
                explicit != null && weekday != null && explicit!!.dayOfWeek != weekday -> {
                    ambiguity = "Fiquei em dúvida: dia ${explicit!!.dayOfMonth}/${explicit!!.monthValue} cai em " +
                        "${weekdayName(explicit!!.dayOfWeek)}, mas você disse ${weekdayName(weekday)}. Qual dia vale?"
                    return
                }
                explicit != null -> explicit
                weekday != null -> {
                    val next = today.with(TemporalAdjusters.next(weekday))
                    // "sexta que vem" on a Thursday: tomorrow or next week? Ask instead of guessing.
                    val sameWeek = next.isBefore(today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)))
                    if (everyWeekday == null && sameWeek && Regex("proxim|que vem").containsMatchIn(weekdayMatch!!.value)) {
                        ambiguity = "Você quer dizer ${weekdayName(weekday)} desta semana (${next.dayOfMonth}/${next.monthValue}) " +
                            "ou da próxima (${next.plusWeeks(1).dayOfMonth}/${next.plusWeeks(1).monthValue})?"
                        return
                    }
                    next
                }
                else -> date
            }

            parseTime()
        }

        private fun parseTime() {
            take("\\b${PREP}(?:as\\s+)?meio[- ]dia(?:\\s+e\\s+meia)?\\b")?.let {
                startMinute = 12 * 60 + if (it.value.contains("meia")) 30 else 0
            }
            if (startMinute == null) take("\\b${PREP}meia[- ]noite\\b")?.let { startMinute = 0 }
            // durations first so "por 2h" isn't read as a start time
            take("\\b(?:por|durante|reserv\\w*|leva\\w*|dura\\w*)\\s+(?:umas?\\s+)?$NUM\\s*(h|horas?|min|minutos?)(?:\\s+e\\s+meia)?\\b")?.let {
                val n = num(it.groupValues[1])
                duration = if (it.groupValues[2].startsWith("h")) n * 60 + (if (it.value.endsWith("meia")) 30 else 0) else n
            } ?: take("\\b(?:por|durante)\\s+meia hora\\b")?.let { duration = 30 }
            if (startMinute != null) return

            val period = "(?:\\s+(?:da|de)\\s+(manha|tarde|noite|madrugada))?"
            val m = take("\\b(?:as|a|ao|pelas|para as|pras|a partir das)?\\s*(\\d{1,2}):(\\d{2})(?:h|hs)?$period\\b")
                ?: take("\\b(?:as|a|ao|pelas|para as|pras|a partir das)?\\s*(\\d{1,2})\\s*(?:h|hs|horas?)\\s*(\\d{2})?$period\\b")
                ?: take("\\b(?:as|pelas|para as|pras|a partir das)\\s+(\\d{1,2})()(?:\\s*e\\s*meia)?$period\\b")
                ?: return
            var h = m.groupValues[1].toInt()
            var min = m.groupValues[2].toIntOrNull() ?: if (m.value.contains("e meia")) 30 else 0
            when (m.groupValues[3]) {
                "tarde", "noite" -> if (h < 12) h += 12
                "manha", "madrugada" -> if (h == 12) h = 0
            }
            if (h > 23 || min > 59) { ambiguity = "Não entendi o horário \"${m.value.trim()}\". Pode repetir? (ex.: 14h30)"; return }
            startMinute = h * 60 + min
        }

        fun parseExtras() {
            take("\\b(?:urgente|importante|prioridade alta|alta prioridade|prioritari[ao])\\b")?.let { priority = 2 }
            if (priority == null) take("\\b(?:baixa prioridade|prioridade baixa|sem pressa)\\b")?.let { priority = 0 }
        }

        private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = try {
            LocalDate.of(y, m, d)
        } catch (e: DateTimeException) {
            ambiguity = "A data $d/$m não existe. Qual dia você quis dizer?"; null
        }

        /** What's left after removing date/time pieces and filler like "me lembra de". */
        fun title(): String {
            var o = orig.toString()
            var l = text.toString()
            val filler = Regex("^[\\s,.;:!-]*(?:(?:me|nos)\\s+)?(?:lembr\\w*(?:-me)?|anot\\w*|agend\\w*|marqu\\w*|marc\\w*|registr\\w*|" +
                "cri[ae]r?|coloc\\w*|adicion\\w*|bot[ae]|preciso|precisa|tenho que|tenho de|tenho|vou|devo|nao (?:posso )?esquec\\w*|" +
                "quero|sobre)\\b(?:\\s+(?:de|que|da|do|a|o|uma?)\\b)?(?:\\s+(?:tarefa|lembrete|compromisso)\\b(?:\\s+(?:de|para|pra)\\b)?)?")
            while (true) {
                val m = filler.find(l) ?: break
                if (m.value.isBlank()) break
                o = o.substring(m.range.last + 1); l = l.substring(m.range.last + 1)
            }
            // drop prepositions left dangling at the end ("pagar conta na")
            val tail = Regex("(?:[\\s,.;:!?-]+|\\b(?:na|no|para|pra|de|do|da|em|as|a|o|e|com|ate|dia)\\s*)+$")
            tail.find(l)?.let { o = o.substring(0, it.range.first) }
            return o.replace(Regex("\\s*,(\\s*,)+"), ",").replace(Regex("\\s+"), " ")
                .trim(' ', ',', '.', ';', ':', '-', '!', '?')
                .replace(Regex("\\s+,"), ",")
                .replaceFirstChar { it.uppercase() }
        }
    }

    fun weekdayName(d: DayOfWeek) = listOf("segunda-feira", "terça-feira", "quarta-feira", "quinta-feira",
        "sexta-feira", "sábado", "domingo")[d.value - 1]
}
