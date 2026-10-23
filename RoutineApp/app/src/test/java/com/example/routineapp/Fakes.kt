package com.example.routineapp

import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskDao
import com.example.routineapp.data.TaskStatus
import com.example.routineapp.domain.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDao : TaskDao {
    val rows = MutableStateFlow<Map<Long, Task>>(emptyMap())
    private var nextId = 1L
    override fun observeRange(fromDay: Long, toDay: Long): Flow<List<Task>> =
        rows.map { m -> m.values.filter { it.epochDay in fromDay..toDay } }
    override fun observe(id: Long): Flow<Task?> = rows.map { it[id] }
    override suspend fun get(id: Long) = rows.value[id]
    override suspend fun onDay(day: Long) = rows.value.values.filter { it.epochDay == day }
    override suspend fun pendingFrom(fromDay: Long) = rows.value.values
        .filter { it.status == TaskStatus.PENDING && it.epochDay >= fromDay }.sortedWith(compareBy({ it.epochDay }, { it.startMinute }))
    override suspend fun allPending() = rows.value.values.filter { it.status == TaskStatus.PENDING }
    override suspend fun insert(task: Task): Long {
        val id = nextId++
        rows.value = rows.value + (id to task.copy(id = id)); return id
    }
    override suspend fun update(task: Task) { rows.value = rows.value + (task.id to task) }
    override suspend fun delete(task: Task) { rows.value = rows.value - task.id }
}

class FakeScheduler : AlarmScheduler {
    /** taskId -> last scheduled task snapshot (absent = cancelled / never scheduled). */
    val scheduled = mutableMapOf<Long, Task>()
    val snoozes = mutableMapOf<Long, Long>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(task: Task, justChanged: Boolean) { scheduled[task.id] = task }
    override fun scheduleNagAt(task: Task, at: Long) { snoozes[task.id] = at }
    override fun cancel(taskId: Long) { scheduled.remove(taskId); snoozes.remove(taskId); cancelled += taskId }
}
