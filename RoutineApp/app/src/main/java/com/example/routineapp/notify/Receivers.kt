package com.example.routineapp.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.routineapp.App
import com.example.routineapp.app
import com.example.routineapp.data.AppSettings
import com.example.routineapp.data.Task
import com.example.routineapp.domain.ReminderRules
import com.example.routineapp.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { block() } catch (e: Exception) { Log.e(Notifier.TAG, "Receiver failed", e) }
        finally { pending.finish() }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        val app = context.app
        val id = intent.getLongExtra(Notifier.EXTRA_TASK_ID, -1)
        val version = intent.getIntExtra(EXTRA_VERSION, -1)
        val seq = intent.getIntExtra(EXTRA_SEQ, -1)
        // always re-read the task: it may have been completed, deleted or moved since scheduling
        val task = app.tasks.get(id)
        val s = app.settings.current
        when (intent.action) {
            REMINDER -> if (ReminderRules.shouldFireReminder(task, version, s))
                app.notifier.reminder(task!!)
            NAG -> if (ReminderRules.shouldFireNag(task, version, seq, s)) {
                val fired = app.tasks.markNagFired(task!!)
                app.scheduler.schedule(fired, justChanged = false) // next repeat, bounded by nagMaxAlerts
                app.notifier.nag(fired, s.nagRepeatMinutes)
                if (seq == 0 && s.phoneCallEnabled) requestCall(app, fired, s)
            } else Log.i(Notifier.TAG, "Stale nag ignored for task $id")
        }
    }

    private suspend fun requestCall(app: App, task: Task,
                                    s: AppSettings) {
        if (!SettingsRepository.isValidPhone(s.phoneNumber)) return
        try {
            app.backend.requestCall(s.phoneNumber, task.title, "${task.id}-${task.scheduleVersion}")
        } catch (e: Exception) {
            app.notifier.info(Notifier.nagId(task.id) + 1_000_000, "A ligação não pôde ser feita",
                "${e.message}. O alarme local continua ativo.")
        }
    }

    companion object {
        const val REMINDER = "com.example.routineapp.REMINDER"
        const val NAG = "com.example.routineapp.NAG"
        const val EXTRA_VERSION = "version"
        const val EXTRA_SEQ = "seq"
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        val app = context.app
        val id = intent.getLongExtra(Notifier.EXTRA_TASK_ID, -1)
        handle(app, intent.action, id)
    }

    companion object {
        const val DONE = "com.example.routineapp.DONE"
        const val SNOOZE = "com.example.routineapp.SNOOZE"
        const val STOP = "com.example.routineapp.STOP"
        val ACTIONS = listOf(DONE, SNOOZE, STOP)

        suspend fun handle(app: App, action: String?, id: Long) {
            val s = app.settings.current
            when (action) {
                DONE -> app.tasks.complete(id)
                SNOOZE -> app.tasks.snoozeNag(id, s.nagRepeatMinutes)
                STOP -> app.tasks.stopNag(id, s.nagMaxAlerts)
            }
            app.notifier.cancel(id)
        }
    }
}

/** Alarms are lost on reboot and computed in local time, so rebuild them on these events. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        Log.i(Notifier.TAG, "Rescheduling all after ${intent.action}")
        context.app.tasks.rescheduleAll()
    }
}
