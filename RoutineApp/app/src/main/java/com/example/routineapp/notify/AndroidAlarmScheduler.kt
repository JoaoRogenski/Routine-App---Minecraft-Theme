package com.example.routineapp.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.routineapp.data.SettingsRepository
import com.example.routineapp.data.Task
import com.example.routineapp.domain.AlarmScheduler
import com.example.routineapp.domain.ReminderRules
import java.time.ZoneId

class AndroidAlarmScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) : AlarmScheduler {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    override fun schedule(task: Task, justChanged: Boolean) {
        val plan = ReminderRules.plan(task, System.currentTimeMillis(), ZoneId.systemDefault(),
            settings.current, justChanged)
        cancelAlarm(pending(task.id, AlarmReceiver.REMINDER, Intent()))
        cancelAlarm(pending(task.id, AlarmReceiver.NAG, Intent()))
        // background reschedules (app start, boot) must not wipe notifications the user hasn't seen
        if (justChanged) notifier.cancel(task.id)
        plan.reminderAt?.let { set(it, intent(task, AlarmReceiver.REMINDER)) }
        plan.nagAt?.let { set(it, intent(task, AlarmReceiver.NAG)) }
        if (plan.notifyNow) notifier.reminder(task)
    }

    override fun scheduleNagAt(task: Task, at: Long) {
        cancelAlarm(intent(task, AlarmReceiver.NAG))
        set(at, intent(task, AlarmReceiver.NAG))
    }

    override fun cancel(taskId: Long) {
        // extras are ignored by PendingIntent matching, so a bare intent with the same action/code cancels it
        cancelAlarm(pending(taskId, AlarmReceiver.REMINDER, Intent()))
        cancelAlarm(pending(taskId, AlarmReceiver.NAG, Intent()))
        notifier.cancel(taskId)
    }

    private fun intent(task: Task, kind: String) = pending(task.id, kind, Intent()
        .putExtra(Notifier.EXTRA_TASK_ID, task.id)
        .putExtra(AlarmReceiver.EXTRA_VERSION, task.scheduleVersion)
        .putExtra(AlarmReceiver.EXTRA_SEQ, task.nagCount))

    private fun pending(taskId: Long, kind: String, base: Intent): PendingIntent {
        val code = if (kind == AlarmReceiver.REMINDER) Notifier.reminderId(taskId) else Notifier.nagId(taskId)
        return PendingIntent.getBroadcast(context, code,
            base.setClass(context, AlarmReceiver::class.java).setAction(kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancelAlarm(pi: PendingIntent) {
        am.cancel(pi)
        pi.cancel()
    }

    private fun set(at: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else {
                // without the exact-alarm permission Android may delay this by several minutes
                Log.w(Notifier.TAG, "Exact alarms not allowed, using inexact alarm")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            Log.e(Notifier.TAG, "Alarm scheduling failed", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
