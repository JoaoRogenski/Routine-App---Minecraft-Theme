package com.example.routineapp

import com.example.routineapp.data.AppSettings
import com.example.routineapp.data.Recurrence
import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskStatus
import com.example.routineapp.domain.ReminderRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderRulesTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val s = AppSettings()
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun task(date: LocalDate, minute: Int?, status: TaskStatus = TaskStatus.PENDING, nagCount: Int = 0) =
        Task(id = 1, title = "x", epochDay = date.toEpochDay(), startMinute = minute, status = status,
            nagCount = nagCount, createdAt = 0, updatedAt = 0)

    private val friday = LocalDate.of(2026, 10, 23)

    @Test fun reminderIsExactlyFiveHoursBefore() {
        val p = ReminderRules.plan(task(friday, 14 * 60), millis(friday.atTime(8, 0)), zone, s, justChanged = true)
        assertEquals(millis(friday.atTime(9, 0)), p.reminderAt)
        assertFalse(p.notifyNow)
        assertEquals(millis(friday.atTime(14, 0)), p.nagAt)
    }

    @Test fun reminderCrossesMidnight() {
        val p = ReminderRules.plan(task(friday, 2 * 60), millis(friday.minusDays(1).atTime(12, 0)), zone, s, true)
        assertEquals(millis(friday.minusDays(1).atTime(21, 0)), p.reminderAt)
    }

    @Test fun lessThanFiveHoursNotifiesNowOnlyWhenJustCreated() {
        val now = millis(friday.atTime(12, 0))
        val p = ReminderRules.plan(task(friday, 14 * 60), now, zone, s, justChanged = true)
        assertNull(p.reminderAt); assertTrue(p.notifyNow)
        // a reboot reschedule must not produce a retroactive notification
        assertFalse(ReminderRules.plan(task(friday, 14 * 60), now, zone, s, justChanged = false).notifyNow)
        assertFalse(ReminderRules.plan(task(friday, 14 * 60), now, zone, s.copy(notifyImmediatelyWhenLate = false), true).notifyNow)
    }

    @Test fun pastDoneAndUntimedTasksGetNothing() {
        val now = millis(friday.atTime(20, 0))
        assertEquals(ReminderRules.Plan(), ReminderRules.plan(task(friday.minusDays(2), 9 * 60), now, zone, s, true))
        assertEquals(ReminderRules.Plan(), ReminderRules.plan(task(friday.plusDays(1), 9 * 60, TaskStatus.DONE), now, zone, s, true))
        assertEquals(ReminderRules.Plan(), ReminderRules.plan(task(friday.plusDays(1), null), now, zone, s, true))
    }

    @Test fun nagRepeatsAreBounded() {
        val start = friday.atTime(14, 0)
        val afterFirst = ReminderRules.plan(task(friday, 14 * 60, nagCount = 1), millis(start.plusSeconds(1)), zone, s, false)
        assertEquals(millis(start.plusSeconds(1)) + 10 * 60_000, afterFirst.nagAt)
        assertNull(ReminderRules.plan(task(friday, 14 * 60, nagCount = 3), millis(start.plusMinutes(25)), zone, s, false).nagAt)
        assertNull(ReminderRules.plan(task(friday, 14 * 60), millis(start), zone, s.copy(nagEnabled = false), false).nagAt)
    }

    @Test fun missedNagAfterRebootFiresSoonButNotForOldTasks() {
        val start = friday.atTime(14, 0)
        val now = millis(start.plusMinutes(30))
        assertEquals(now + 5_000, ReminderRules.plan(task(friday, 14 * 60), now, zone, s, false).nagAt)
        assertNull(ReminderRules.plan(task(friday, 14 * 60), millis(start.plusHours(5)), zone, s, false).nagAt)
    }

    @Test fun staleAlarmsAreIgnored() {
        val t = task(friday, 14 * 60).copy(scheduleVersion = 2, nagCount = 1)
        assertTrue(ReminderRules.shouldFireNag(t, version = 2, seq = 1, s = s))
        assertFalse("rescheduled", ReminderRules.shouldFireNag(t, version = 1, seq = 1, s = s))
        assertFalse("duplicate delivery", ReminderRules.shouldFireNag(t, version = 2, seq = 0, s = s))
        assertFalse("completed", ReminderRules.shouldFireNag(t.copy(status = TaskStatus.DONE), 2, 1, s))
        assertFalse("deleted", ReminderRules.shouldFireNag(null, 2, 1, s))
        assertFalse(ReminderRules.shouldFireReminder(t, version = 1, s = s))
    }

    @Test fun overdue() {
        val now = millis(friday.atTime(15, 0))
        assertTrue(ReminderRules.isOverdue(task(friday, 14 * 60), now, zone))
        assertFalse(ReminderRules.isOverdue(task(friday, 16 * 60), now, zone))
        assertFalse(ReminderRules.isOverdue(task(friday, 14 * 60, TaskStatus.DONE), now, zone))
        assertFalse("untimed today is not late yet", ReminderRules.isOverdue(task(friday, null), now, zone))
        assertTrue(ReminderRules.isOverdue(task(friday.minusDays(1), null), now, zone))
    }

    @Test fun timezoneChangeKeepsWallClock() {
        val t = task(friday, 14 * 60)
        val sp = ReminderRules.startMillis(t, ZoneId.of("America/Sao_Paulo"))!!
        val manaus = ReminderRules.startMillis(t, ZoneId.of("America/Manaus"))!!
        assertEquals(3_600_000L, manaus - sp)
    }

    @Test fun recurrenceAndOverlap() {
        assertEquals(LocalDate.of(2026, 11, 30), ReminderRules.nextOccurrence(LocalDate.of(2026, 10, 31), Recurrence.MONTHLY))
        assertEquals(friday.plusWeeks(1), ReminderRules.nextOccurrence(friday, Recurrence.WEEKLY))
        assertNull(ReminderRules.nextOccurrence(friday, Recurrence.NONE))
        val a = task(friday, 14 * 60).copy(durationMinutes = 60)
        assertTrue(ReminderRules.overlaps(a, task(friday, 14 * 60 + 30)))
        assertTrue("close point tasks", ReminderRules.overlaps(task(friday, 9 * 60), task(friday, 9 * 60 + 15)))
        assertFalse(ReminderRules.overlaps(a, task(friday, 15 * 60)))
    }
}
