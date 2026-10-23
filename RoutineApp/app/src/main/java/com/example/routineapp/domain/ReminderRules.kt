package com.example.routineapp.domain

import com.example.routineapp.data.AppSettings
import com.example.routineapp.data.Recurrence
import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskStatus
import java.time.LocalDate
import java.time.ZoneId

/** Pure scheduling rules; AndroidAlarmScheduler only applies what these return. */
object ReminderRules {
    const val LEAD_HOURS = 5L
    private const val HOUR = 3_600_000L
    /** A nag missed (device off) is still delivered if less than this late. */
    private const val MISSED_NAG_WINDOW = 2 * HOUR
    /** Repeats are not revived (e.g. after a reboot) for occurrences older than this. */
    private const val STALE_NAG = 12 * HOUR

    data class Plan(val reminderAt: Long? = null, val notifyNow: Boolean = false, val nagAt: Long? = null)

    fun startMillis(task: Task, zone: ZoneId): Long? =
        task.start?.atZone(zone)?.toInstant()?.toEpochMilli()

    /**
     * @param justChanged the task was just created/edited by the user (allows an immediate
     * notification when it's already inside the 5h window). False for boot/timezone reschedules.
     */
    fun plan(task: Task, now: Long, zone: ZoneId, s: AppSettings, justChanged: Boolean): Plan {
        if (task.status != TaskStatus.PENDING) return Plan()
        val start = startMillis(task, zone) ?: return Plan()

        var reminderAt: Long? = null
        var notifyNow = false
        if (s.remindersEnabled && task.notifyEnabled && start > now) {
            val at = start - LEAD_HOURS * HOUR
            if (at > now) reminderAt = at
            else notifyNow = justChanged && s.notifyImmediatelyWhenLate
        }

        var nagAt: Long? = null
        if (s.nagEnabled && task.nagEnabled && task.nagCount < s.nagMaxAlerts) {
            nagAt = when {
                task.nagCount > 0 -> if (now - start < STALE_NAG) now + s.nagRepeatMinutes * 60_000L else null
                start > now -> start
                now - start < MISSED_NAG_WINDOW -> now + 5_000
                else -> null
            }
        }
        return Plan(reminderAt, notifyNow, nagAt)
    }

    fun isOverdue(task: Task, now: Long, zone: ZoneId): Boolean {
        if (task.status != TaskStatus.PENDING) return false
        val start = startMillis(task, zone)
        return if (start != null) start < now
        else task.date.isBefore(LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone))
    }

    /** Guards against alarms for a task that was completed, deleted, rescheduled or already alerted. */
    fun shouldFireNag(task: Task?, version: Int, seq: Int, s: AppSettings): Boolean =
        task != null && task.status == TaskStatus.PENDING && task.scheduleVersion == version &&
            task.nagCount == seq && s.nagEnabled && task.nagEnabled

    fun shouldFireReminder(task: Task?, version: Int, s: AppSettings): Boolean =
        task != null && task.status == TaskStatus.PENDING && task.scheduleVersion == version &&
            s.remindersEnabled && task.notifyEnabled

    fun nextOccurrence(date: LocalDate, r: Recurrence): LocalDate? = when (r) {
        Recurrence.NONE -> null
        Recurrence.DAILY -> date.plusDays(1)
        Recurrence.WEEKLY -> date.plusWeeks(1)
        Recurrence.MONTHLY -> date.plusMonths(1)
    }

    /** Point tasks count as 30 min so "close" tasks are also flagged. */
    fun overlaps(a: Task, b: Task): Boolean {
        if (a.epochDay != b.epochDay) return false
        val aStart = a.startMinute ?: return false
        val bStart = b.startMinute ?: return false
        val aEnd = aStart + (a.durationMinutes ?: 30)
        val bEnd = bStart + (b.durationMinutes ?: 30)
        return aStart < bEnd && bStart < aEnd
    }
}
