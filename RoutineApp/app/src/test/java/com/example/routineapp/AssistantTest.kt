package com.example.routineapp

import com.example.routineapp.assistant.Assistant
import com.example.routineapp.data.AppSettings
import com.example.routineapp.data.TaskSource
import com.example.routineapp.data.TaskStatus
import com.example.routineapp.domain.TaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AssistantTest {
    private val dao = FakeDao()
    private val scheduler = FakeScheduler()
    private val repo = TaskRepository(dao, scheduler)
    private var settings = AppSettings(name = "Ana")
    private var notificationsOk = true
    // Thursday 22/10/2026 17:40
    private val assistant = Assistant(repo, { settings }, backend = null, notificationsAllowed = { notificationsOk },
        clock = { LocalDateTime.of(2026, 10, 22, 17, 40) }, zone = { ZoneId.of("America/Sao_Paulo") })
    private val tasks get() = dao.rows.value.values.toList()

    private suspend fun say(s: String) = assistant.handle(s).text

    @Test fun createsTaskAndConfirmsSavedData() = runTest {
        val r = say("Tenho prova de matemática na sexta-feira, dia 23, às 9h.")
        val t = tasks.single()
        assertEquals("Prova de matemática", t.title)
        assertEquals(LocalDate.of(2026, 10, 23), t.date)
        assertEquals(9 * 60, t.startMinute)
        assertEquals(TaskSource.CHAT, t.source)
        assertTrue(r, r.startsWith("Fechado, Ana! Registrei “Prova de matemática” para amanhã (sexta-feira, 23 de outubro), às 9h."))
        assertTrue(r, r.contains("Vou te lembrar às 4h."))
        assertTrue(r, r.contains("eu te cobro"))
        assertTrue(scheduler.scheduled.containsKey(t.id))
    }

    @Test fun asksForMissingDateThenCreates() = runTest {
        val q = say("comprar presente da Maria")
        assertTrue(q, q.startsWith("Para quando"))
        assertTrue(tasks.isEmpty())
        say("sábado às 10h")
        assertEquals(LocalDate.of(2026, 10, 24), tasks.single().date)
        assertEquals("Comprar presente da Maria", tasks.single().title)
    }

    @Test fun resetForgetsPendingQuestion() = runTest {
        say("comprar presente da Maria")
        assistant.reset()
        say("sábado às 10h")
        assertTrue(tasks.none { it.title == "Comprar presente da Maria" })
    }

    @Test fun noTimeCreatesAllDayAndSaysSo() = runTest {
        val r = say("lavar o carro amanhã")
        assertTrue(r, r.contains("sem horário"))
        // a follow-up time applies to that task
        say("às 15h")
        assertEquals(15 * 60, tasks.single().startMinute)
    }

    @Test fun pastDateIsNotScheduled() = runTest {
        val r = say("reunião 20/10 às 10h")
        assertTrue(r, r.contains("já passou"))
        assertTrue(tasks.isEmpty())
        say("dia 27 às 10h")
        assertEquals(LocalDate.of(2026, 10, 27), tasks.single().date)
    }

    @Test fun lessThanFiveHoursNotifiesRightAway() = runTest {
        val r = say("ligar pro João hoje às 19h")
        assertTrue(r, r.contains("já te mandei o lembrete"))
    }

    @Test fun ambiguousDayAsksFirst() = runTest {
        val r = say("prova na quinta, dia 23, às 9h")
        assertTrue(r, r.contains("Qual dia vale"))
        assertTrue(tasks.isEmpty())
    }

    @Test fun examWithReviewCreatesBothAndAsksWhenToReview() = runTest {
        val r = say("Tenho prova de matemática na sexta-feira, dia 23, às 9h. Preciso revisar funções antes. Reserve uma hora para isso.")
        assertTrue(r, r.contains("Registrei “Prova de matemática” para amanhã"))
        assertTrue(r, r.contains("Para quando é “Revisar funções”?"))
        assertEquals(1, tasks.size)
        val r2 = say("amanhã às 7h")
        assertTrue(r2, r2.contains("Registrei “Revisar funções” para amanhã (sexta-feira, 23 de outubro), às 7h por 1h"))
        val review = tasks.single { it.title == "Revisar funções" }
        assertEquals(LocalDate.of(2026, 10, 23), review.date)
        assertEquals(7 * 60, review.startMinute)
        assertEquals(60, review.durationMinutes)
    }

    @Test fun duplicateIsRefused() = runTest {
        say("dentista amanhã às 14h")
        val r = say("dentista amanhã às 14h")
        assertTrue(r, r.contains("Não registrei de novo"))
        assertEquals(1, tasks.size)
    }

    @Test fun conflictNeedsConfirmation() = runTest {
        say("dentista amanhã às 14h")
        val r = say("reunião amanhã às 14h15")
        assertTrue(r, r.contains("Quer registrar"))
        assertEquals(1, tasks.size)
        say("sim")
        assertEquals(2, tasks.size)
        say("aula amanhã às 14h")
        say("não")
        assertEquals(2, tasks.size)
    }

    @Test fun completeAndDeleteByChat() = runTest {
        say("dentista amanhã às 14h")
        say("academia amanhã às 7h")
        val c = say("concluí o dentista")
        assertTrue(c, c.contains("concluída"))
        assertEquals(TaskStatus.DONE, tasks.single { it.title == "Dentista" }.status)
        val q = say("apaga a academia")
        assertTrue(q, q.contains("sim ou não"))
        assertEquals(2, tasks.size)
        say("sim")
        assertEquals(1, tasks.size)
    }

    @Test fun rescheduleByChat() = runTest {
        say("dentista amanhã às 14h")
        val r = say("muda o dentista para sábado às 10h")
        val t = tasks.single()
        assertTrue(r, r.startsWith("Pronto"))
        assertEquals(LocalDate.of(2026, 10, 24), t.date)
        assertEquals(10 * 60, t.startMinute)
        assertEquals(1, t.scheduleVersion)
    }

    @Test fun multipleMatchesAskWhichOne() = runTest {
        say("reunião com cliente amanhã às 9h")
        say("reunião de equipe amanhã às 16h")
        val r = say("concluí a reunião")
        assertTrue(r, r.contains("Responda com o número"))
        say("2")
        assertEquals(TaskStatus.DONE, tasks.single { it.title == "Reunião de equipe" }.status)
    }

    @Test fun blockedNotificationsAreReported() = runTest {
        notificationsOk = false
        val r = say("dentista amanhã às 14h")
        assertTrue(r, r.contains("notificações estão bloqueadas"))
    }

    @Test fun listsTasks() = runTest {
        say("dentista amanhã às 14h")
        val r = say("o que eu tenho amanhã?")
        assertTrue(r, r.contains("14h — Dentista"))
    }
}
