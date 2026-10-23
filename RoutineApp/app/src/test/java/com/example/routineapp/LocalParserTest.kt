package com.example.routineapp

import com.example.routineapp.assistant.Command
import com.example.routineapp.assistant.LocalParser
import com.example.routineapp.assistant.TaskDraft
import com.example.routineapp.data.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LocalParserTest {
    // Thursday, 22 Oct 2026, 17:40
    private val now = LocalDateTime.of(2026, 10, 22, 17, 40)

    private fun draft(text: String): TaskDraft {
        val c = LocalParser.parse(text, now)
        assertTrue("expected Create for '$text' but got $c", c is Command.Create)
        return (c as Command.Create).drafts.single()
    }

    @Test fun fullTask() {
        val c = LocalParser.parse("Tenho prova de matemática na sexta-feira, dia 23, às 9h. Preciso revisar funções antes. Reserve uma hora para isso.", now)
        val (d, review) = (c as Command.Create).drafts
        assertEquals("Prova de matemática", d.title)
        assertEquals(LocalDate.of(2026, 10, 23), d.date)
        assertEquals(9 * 60, d.startMinute)
        assertNull(d.durationMinutes) // the hour is for the review, not the exam
        assertNull(d.description)
        assertEquals("Revisar funções", review.title)
        assertEquals(60, review.durationMinutes)
        assertNull(review.date) // "antes" is not a date: the assistant must ask
        assertNull(review.startMinute)
    }

    @Test fun plainExtraSentenceStaysDescription() {
        val d = draft("Dentista amanhã às 14h. Preciso levar os exames.")
        assertEquals("Dentista", d.title)
        assertEquals("Preciso levar os exames.", d.description)
    }

    @Test fun durationForMainTask() {
        val d = draft("Reunião com o time amanhã às 10h. Reserve duas horas para isso.")
        assertEquals(120, d.durationMinutes)
        assertNull(d.description)
    }

    @Test fun tomorrowWithTime() {
        val d = draft("Me lembra de pagar a conta de internet amanhã às 18h.")
        assertEquals("Pagar a conta de internet", d.title)
        assertEquals(LocalDate.of(2026, 10, 23), d.date)
        assertEquals(18 * 60, d.startMinute)
    }

    @Test fun relativeExpressions() {
        assertEquals(LocalDate.of(2026, 10, 24), draft("estudar depois de amanhã").date)
        assertEquals(LocalDate.of(2026, 11, 5), draft("entregar relatório daqui a duas semanas").date)
        assertEquals(LocalDate.of(2026, 10, 23), draft("reunião sexta às 10h").date)
        assertEquals(LocalDate.of(2026, 10, 26), draft("reunião segunda que vem às 10h").date)
        assertEquals(LocalDate.of(2026, 10, 26), draft("dentista na segunda às 14:30").date)
        assertEquals(LocalDate.of(2026, 11, 2), draft("consulta dia 2 às 8h").date)
        assertEquals(LocalDate.of(2026, 12, 25), draft("ceia 25/12 às 20h").date)
        assertEquals(LocalDate.of(2026, 11, 15), draft("viagem 15 de novembro").date)
    }

    @Test fun timeFormats() {
        assertEquals(14 * 60 + 30, draft("dentista amanhã 14h30").startMinute)
        assertEquals(15 * 60, draft("café amanhã às 3 da tarde").startMinute)
        assertEquals(12 * 60, draft("almoço amanhã ao meio-dia").startMinute)
        assertEquals(20 * 60, draft("jantar amanhã às 20").startMinute)
        val d = draft("ligar pra mãe daqui a 2 horas")
        assertEquals(LocalDate.of(2026, 10, 22), d.date)
        assertEquals(19 * 60 + 40, d.startMinute)
        assertEquals("Ligar pra mãe", d.title)
    }

    @Test fun durationPriorityRecurrence() {
        val d = draft("estudar inglês amanhã às 19h por 2 horas, urgente")
        assertEquals(120, d.durationMinutes)
        assertEquals(2, d.priority)
        assertEquals("Estudar inglês", d.title)
        val r = draft("academia toda segunda às 7h")
        assertEquals(Recurrence.WEEKLY, r.recurrence)
        assertEquals(LocalDate.of(2026, 10, 26), r.date)
        assertEquals(Recurrence.DAILY, draft("tomar remédio todos os dias às 8h").recurrence)
    }

    @Test fun missingTimeAndDate() {
        val d = draft("comprar pão")
        assertNull(d.date); assertNull(d.startMinute)
        val e = draft("lavar o carro amanhã")
        assertEquals(LocalDate.of(2026, 10, 23), e.date); assertNull(e.startMinute)
    }

    @Test fun ambiguousWeekdayAndDay() {
        // 23/10/2026 is a Friday
        assertTrue(LocalParser.parse("prova na quinta, dia 23", now) is Command.Clarify)
        assertTrue(LocalParser.parse("algo dia 31/11", now) is Command.Clarify)
        // said on a Thursday, "sexta que vem" may be tomorrow or the 30th
        assertTrue(LocalParser.parse("reunião sexta que vem às 10h", now) is Command.Clarify)
    }

    @Test fun bareWhenAnswersQuestion() {
        assertEquals(Command.When(LocalDate.of(2026, 10, 23), 15 * 60, false), LocalParser.parse("amanhã às 15h", now))
        assertEquals(Command.When(null, 9 * 60, false), LocalParser.parse("às 9h", now))
        assertEquals(Command.When(null, null, true), LocalParser.parse("sem horário", now))
    }

    @Test fun otherIntents() {
        val c = LocalParser.parse("concluí a prova de matemática", now) as Command.Complete
        assertEquals("prova de matemática", c.ref.title)
        val d = LocalParser.parse("apaga a reunião de sexta", now) as Command.Delete
        assertEquals("reunião", d.ref.title)
        assertEquals(LocalDate.of(2026, 10, 23), d.ref.date)
        val r = LocalParser.parse("muda o dentista para sábado às 10h", now) as Command.Reschedule
        assertEquals("dentista", r.ref.title)
        assertEquals(LocalDate.of(2026, 10, 24), r.date)
        assertEquals(600, r.startMinute)
        val l = LocalParser.parse("o que eu tenho amanhã?", now) as Command.ListTasks
        assertEquals(LocalDate.of(2026, 10, 23), l.date)
        assertEquals(Command.Reply("GREETING"), LocalParser.parse("oi!", now))
    }
}
