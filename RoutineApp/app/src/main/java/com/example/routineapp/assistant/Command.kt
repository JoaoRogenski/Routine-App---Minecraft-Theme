package com.example.routineapp.assistant

import com.example.routineapp.data.Recurrence
import java.time.LocalDate

/** Structured result of interpreting one chat message (from the local parser or the AI backend). */
data class TaskDraft(
    val title: String,
    val date: LocalDate? = null,
    val startMinute: Int? = null,
    val durationMinutes: Int? = null,
    val description: String? = null,
    val category: String? = null,
    val priority: Int? = null,
    val location: String? = null,
    val notes: String? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    /** User explicitly said there is no time ("sem horário"). */
    val allDay: Boolean = false,
)

/** Which existing task the user is talking about. */
data class TaskRef(val id: Long? = null, val title: String? = null, val date: LocalDate? = null) {
    val isEmpty get() = id == null && title.isNullOrBlank() && date == null
}

sealed interface Command {
    data class Create(val drafts: List<TaskDraft>) : Command
    data class Complete(val ref: TaskRef) : Command
    data class Delete(val ref: TaskRef) : Command
    data class Reschedule(val ref: TaskRef, val date: LocalDate?, val startMinute: Int?) : Command
    data class ListTasks(val date: LocalDate?) : Command
    /** Only a date and/or time ("às 15h", "amanhã"): completes the pending question. */
    data class When(val date: LocalDate?, val startMinute: Int?, val allDay: Boolean) : Command
    data class Clarify(val question: String) : Command
    data class Reply(val text: String) : Command
}
