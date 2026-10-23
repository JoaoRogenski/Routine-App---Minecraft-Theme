package com.example.routineapp.domain

import com.example.routineapp.data.Task

interface AlarmScheduler {
    /** Replaces every pending alarm of this task with the current plan. */
    fun schedule(task: Task, justChanged: Boolean)
    /** Snooze: one insistent alert at [at], replacing any pending one. */
    fun scheduleNagAt(task: Task, at: Long)
    fun cancel(taskId: Long)
}
