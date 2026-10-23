package com.example.routineapp.domain

import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskDao
import com.example.routineapp.data.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer
import java.time.LocalDate

/** Single entry point for task changes: persists first, then (re)schedules alarms. */
class TaskRepository(
    private val dao: TaskDao,
    private val scheduler: AlarmScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay())

    fun observe(id: Long): Flow<Task?> = dao.observe(id)

    suspend fun get(id: Long): Task? = dao.get(id)

    suspend fun pendingFrom(day: LocalDate): List<Task> = dao.pendingFrom(day.toEpochDay())

    suspend fun findDuplicate(t: Task): Task? = dao.onDay(t.epochDay).firstOrNull {
        it.id != t.id && it.status == TaskStatus.PENDING &&
            normalize(it.title) == normalize(t.title) && it.startMinute == t.startMinute
    }

    suspend fun conflicts(t: Task): List<Task> = dao.onDay(t.epochDay).filter {
        it.id != t.id && it.status == TaskStatus.PENDING && ReminderRules.overlaps(it, t)
    }

    suspend fun create(t: Task): Task {
        val now = clock()
        val saved = t.copy(id = 0, createdAt = now, updatedAt = now)
        val id = dao.insert(saved)
        return saved.copy(id = id).also { scheduler.schedule(it, justChanged = true) }
    }

    suspend fun update(t: Task): Task {
        val old = dao.get(t.id) ?: error("Tarefa não encontrada")
        val moved = old.epochDay != t.epochDay || old.startMinute != t.startMinute
        val saved = t.copy(
            createdAt = old.createdAt,
            updatedAt = clock(),
            scheduleVersion = if (moved) old.scheduleVersion + 1 else old.scheduleVersion,
            nagCount = if (moved) 0 else old.nagCount,
        )
        dao.update(saved)
        scheduler.schedule(saved, justChanged = moved || old.notifyEnabled != saved.notifyEnabled)
        return saved
    }

    /** Recurring tasks: the done occurrence is kept as a separate record and the task moves to the next date. */
    suspend fun complete(id: Long): Task? {
        val t = dao.get(id) ?: return null
        if (t.status == TaskStatus.DONE) return t
        val now = clock()
        val next = ReminderRules.nextOccurrence(t.date, t.recurrence)
        scheduler.cancel(id)
        return if (next == null) {
            t.copy(status = TaskStatus.DONE, completedAt = now, updatedAt = now).also { dao.update(it) }
        } else {
            dao.insert(t.copy(id = 0, status = TaskStatus.DONE, completedAt = now, updatedAt = now,
                recurrence = com.example.routineapp.data.Recurrence.NONE))
            val moved = t.copy(epochDay = next.toEpochDay(), updatedAt = now,
                scheduleVersion = t.scheduleVersion + 1, nagCount = 0)
            dao.update(moved)
            scheduler.schedule(moved, justChanged = false)
            moved
        }
    }

    suspend fun reopen(id: Long): Task? {
        val t = dao.get(id) ?: return null
        val saved = t.copy(status = TaskStatus.PENDING, completedAt = null, updatedAt = clock(),
            scheduleVersion = t.scheduleVersion + 1, nagCount = 0)
        dao.update(saved)
        scheduler.schedule(saved, justChanged = false)
        return saved
    }

    suspend fun delete(id: Long): Task? {
        val t = dao.get(id) ?: return null
        scheduler.cancel(id)
        dao.delete(t)
        return t
    }

    /** Called right before an insistent alert is shown; makes duplicate deliveries a no-op. */
    suspend fun markNagFired(t: Task): Task =
        t.copy(nagCount = t.nagCount + 1).also { dao.update(it) }

    suspend fun snoozeNag(id: Long, minutes: Int) {
        val t = dao.get(id) ?: return
        if (t.status == TaskStatus.PENDING) scheduler.scheduleNagAt(t, clock() + minutes * 60_000L)
    }

    /** Stops further automatic alerts for this occurrence. */
    suspend fun stopNag(id: Long, maxAlerts: Int) {
        val t = dao.get(id) ?: return
        scheduler.cancel(id)
        dao.update(t.copy(nagCount = maxOf(t.nagCount, maxAlerts)))
    }

    suspend fun rescheduleAll() {
        dao.allPending().forEach { scheduler.schedule(it, justChanged = false) }
    }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "").replace(Regex("\\s+"), " ")
    }
}
