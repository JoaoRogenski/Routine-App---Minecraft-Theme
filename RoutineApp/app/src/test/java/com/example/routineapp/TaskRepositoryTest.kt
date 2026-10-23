package com.example.routineapp

import com.example.routineapp.data.Recurrence
import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskStatus
import com.example.routineapp.domain.TaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskRepositoryTest {
    private val dao = FakeDao()
    private val scheduler = FakeScheduler()
    private val repo = TaskRepository(dao, scheduler) { 1_000L }
    private val day = LocalDate.of(2026, 10, 23)
    private fun task(title: String = "Prova", minute: Int? = 9 * 60, recurrence: Recurrence = Recurrence.NONE) =
        Task(title = title, epochDay = day.toEpochDay(), startMinute = minute, recurrence = recurrence, createdAt = 0, updatedAt = 0)

    @Test fun createPersistsThenSchedules() = runTest {
        val t = repo.create(task())
        assertEquals(t, dao.get(t.id))
        assertEquals(t, scheduler.scheduled[t.id])
        assertEquals(1_000L, t.createdAt)
    }

    @Test fun editingTimeBumpsVersionAndReschedules() = runTest {
        val t = repo.markNagFired(repo.create(task()))
        val moved = repo.update(t.copy(startMinute = 10 * 60))
        assertEquals(1, moved.scheduleVersion)
        assertEquals(0, moved.nagCount)
        assertEquals(10 * 60, scheduler.scheduled[t.id]!!.startMinute)
        // title-only edits keep the version, so already scheduled alarms stay valid
        assertEquals(1, repo.update(moved.copy(title = "Prova final")).scheduleVersion)
    }

    @Test fun completeCancelsAlarmsAndKeepsRecord() = runTest {
        val t = repo.create(task())
        repo.complete(t.id)
        assertNull(scheduler.scheduled[t.id])
        assertTrue(t.id in scheduler.cancelled)
        val done = dao.get(t.id)!!
        assertEquals(TaskStatus.DONE, done.status)
        assertNotNull(done.completedAt)
    }

    @Test fun completingRecurringTaskKeepsDoneCopyAndMovesOn() = runTest {
        val t = repo.create(task("Academia", recurrence = Recurrence.WEEKLY))
        val next = repo.complete(t.id)!!
        assertEquals(day.plusWeeks(1), next.date)
        assertEquals(TaskStatus.PENDING, next.status)
        assertEquals(1, next.scheduleVersion)
        assertEquals(next, scheduler.scheduled[t.id])
        val doneCopy = dao.rows.value.values.single { it.id != t.id }
        assertEquals(TaskStatus.DONE, doneCopy.status)
        assertEquals(day, doneCopy.date)
        assertEquals(Recurrence.NONE, doneCopy.recurrence)
    }

    @Test fun deleteRemovesRowAndAlarms() = runTest {
        val t = repo.create(task())
        repo.delete(t.id)
        assertNull(dao.get(t.id))
        assertNull(scheduler.scheduled[t.id])
        assertTrue(t.id in scheduler.cancelled)
    }

    @Test fun duplicatesAndConflicts() = runTest {
        repo.create(task("Prova de Matemática"))
        assertNotNull(repo.findDuplicate(task("prova de matematica ")))
        assertNull(repo.findDuplicate(task("prova de matematica", minute = 15 * 60)))
        assertEquals(1, repo.conflicts(task("Dentista", minute = 9 * 60 + 15)).size)
        assertTrue(repo.conflicts(task("Dentista", minute = 11 * 60)).isEmpty())
    }

    @Test fun nagFiredStopAndSnooze() = runTest {
        val t = repo.create(task())
        assertEquals(1, repo.markNagFired(t).nagCount)
        repo.snoozeNag(t.id, 10)
        assertEquals(1_000L + 600_000L, scheduler.snoozes[t.id])
        repo.stopNag(t.id, maxAlerts = 3)
        assertEquals(3, dao.get(t.id)!!.nagCount)
        assertNull(scheduler.snoozes[t.id])
        repo.complete(t.id)
        repo.snoozeNag(t.id, 10)
        assertFalse("no snooze for a completed task", t.id in scheduler.snoozes)
    }

    @Test fun rescheduleAllOnlyTouchesPending() = runTest {
        val a = repo.create(task("A"))
        val b = repo.create(task("B", minute = 11 * 60))
        repo.complete(b.id)
        scheduler.scheduled.clear()
        repo.rescheduleAll()
        assertEquals(setOf(a.id), scheduler.scheduled.keys)
    }
}
