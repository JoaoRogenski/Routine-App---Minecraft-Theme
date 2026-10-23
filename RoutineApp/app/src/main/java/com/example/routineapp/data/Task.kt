package com.example.routineapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class TaskStatus { PENDING, DONE }
enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY }
enum class TaskSource { MANUAL, CHAT }

/**
 * Date and time are stored as local wall-clock values (epoch day + minute of day),
 * so "14h" stays 14h after a timezone change; alarms are recomputed from them.
 */
@Entity(tableName = "tasks", indices = [Index("epochDay")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val epochDay: Long,
    /** Minutes since midnight; null = no time (all-day). */
    val startMinute: Int? = null,
    val durationMinutes: Int? = null,
    val category: String? = null,
    /** 0 = baixa, 1 = normal, 2 = alta. */
    val priority: Int = 1,
    val location: String? = null,
    val notes: String? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    val status: TaskStatus = TaskStatus.PENDING,
    val completedAt: Long? = null,
    val notifyEnabled: Boolean = true,
    val nagEnabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val source: TaskSource = TaskSource.MANUAL,
    /** Bumped whenever date/time changes; alarms carry it so stale ones are ignored. */
    val scheduleVersion: Int = 0,
    /** How many insistent alerts already fired for the current scheduleVersion. */
    val nagCount: Int = 0,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val startTime: LocalTime? get() = startMinute?.let { LocalTime.of(it / 60, it % 60) }
    val start: LocalDateTime? get() = startTime?.let { date.atTime(it) }
    val endMinute: Int? get() = startMinute?.let { it + (durationMinutes ?: 0) }
}
