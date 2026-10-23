package com.example.routineapp.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskStatus
import com.example.routineapp.domain.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class CalendarMode { WEEK, AGENDA }

/** A save that hit a time conflict, waiting for the user's decision. */
data class ConflictPrompt(val task: Task, val conflicts: List<Task>)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repo: TaskRepository) : ViewModel() {
    val weekStart = MutableStateFlow(startOfWeek(LocalDate.now()))
    val mode = MutableStateFlow(CalendarMode.WEEK)
    val conflict = MutableStateFlow<ConflictPrompt?>(null)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    val tasks: StateFlow<List<Task>> = weekStart
        .flatMapLatest { repo.observeRange(it, it.plusDays(6)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ticks every 30s so the "now" line and overdue states stay current. */
    val now: StateFlow<Long> = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    fun observe(id: Long) = repo.observe(id)

    fun shiftWeek(weeks: Long) { weekStart.value = weekStart.value.plusWeeks(weeks) }
    fun goToday() { weekStart.value = startOfWeek(LocalDate.now()) }
    fun goTo(date: LocalDate) { weekStart.value = startOfWeek(date) }

    fun toggleDone(task: Task) = run(if (task.status == TaskStatus.DONE) "Tarefa reaberta" else "Tarefa concluída") {
        if (task.status == TaskStatus.DONE) repo.reopen(task.id) else repo.complete(task.id)
    }

    fun delete(task: Task) = run("Tarefa excluída") { repo.delete(task.id) }

    /** Creates or updates; time conflicts are surfaced instead of saved silently. */
    fun save(task: Task, force: Boolean = false) = run(null) {
        if (!force) {
            val clash = repo.conflicts(task)
            if (clash.isNotEmpty()) { conflict.value = ConflictPrompt(task, clash); return@run }
        }
        conflict.value = null
        if (task.id == 0L) repo.create(task) else repo.update(task)
        messages.send(if (task.id == 0L) "Tarefa criada" else "Tarefa atualizada")
        goTo(task.date)
    }

    private fun run(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                success?.let { messages.send(it) }
            } catch (e: Exception) {
                messages.send("Não foi possível salvar: ${e.message ?: "erro desconhecido"}")
            }
        }
    }

    companion object {
        /** Weeks start on Sunday, like Google Calendar in pt-BR. */
        fun startOfWeek(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
}
