package com.example.routineapp

import com.example.routineapp.assistant.Command
import com.example.routineapp.assistant.CommandJson
import com.example.routineapp.assistant.InvalidCommandException
import com.example.routineapp.data.Recurrence
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class CommandJsonTest {
    private val today = LocalDate.of(2026, 10, 22)
    private fun parse(s: String) = CommandJson.parse(JSONObject(s), today, setOf(7L))

    @Test fun validCreate() {
        val c = parse("""{"action":"create","tasks":[{"title":"Prova","date":"2026-10-23","start":"09:00","priority":2,"recurrence":"WEEKLY"},
            {"title":"Revisar funções","date":"2026-10-22","start":"20:00","durationMinutes":60}]}""") as Command.Create
        assertEquals(2, c.drafts.size)
        assertEquals(540, c.drafts[0].startMinute)
        assertEquals(Recurrence.WEEKLY, c.drafts[0].recurrence)
        assertEquals(60, c.drafts[1].durationMinutes)
    }

    @Test fun rejectsUntrustedValues() {
        listOf(
            """{"action":"drop_table"}""",
            """{"action":"create","tasks":[{"title":"","date":"2026-10-23"}]}""",
            """{"action":"create","tasks":[{"title":"x","date":"2026-13-40"}]}""",
            """{"action":"create","tasks":[{"title":"x","start":"25:00"}]}""",
            """{"action":"create","tasks":[{"title":"x","priority":9}]}""",
            """{"action":"create","tasks":[{"title":"x","date":"2099-01-01"}]}""",
            """{"action":"create","tasks":[{"title":"x","recurrence":"HOURLY"}]}""",
            """{"action":"delete","target":{"id":999}}""",
        ).forEach { json -> assertThrows(json, InvalidCommandException::class.java) { parse(json) } }
    }

    @Test fun knownTargetAccepted() {
        val c = parse("""{"action":"complete","target":{"id":7}}""") as Command.Complete
        assertEquals(7L, c.ref.id)
    }
}
