package com.example.routineapp.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.routineapp.MainActivity
import com.example.routineapp.R
import com.example.routineapp.data.Task
import com.example.routineapp.ui.Formats

class Notifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        val sys = context.getSystemService(NotificationManager::class.java)
        sys.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "Lembretes", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Aviso 5 horas antes de cada tarefa" }
        )
        sys.createNotificationChannel(
            NotificationChannel(CH_NAG, "Cobrança de tarefas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerta insistente quando uma tarefa chega no horário e continua pendente"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 600, 800, 600, 800)
            }
        )
        sys.createNotificationChannel(
            NotificationChannel(CH_INFO, "Avisos do app", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Falhas de ligação e outros avisos" }
        )
    }

    fun canPost(): Boolean = nm.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun canUseFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun reminder(task: Task) {
        val time = task.startTime?.let { Formats.time(it) } ?: return
        post(reminderId(task.id), NotificationCompat.Builder(context, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle("Você tem uma tarefa às $time: ${task.title}")
            .setContentIntent(openTask(task.id))
            .setAutoCancel(true)
            .addAction(0, "Concluir", action(task.id, ActionReceiver.DONE)))
    }

    fun nag(task: Task, repeatMinutes: Int) {
        val time = task.startTime?.let { Formats.time(it) } ?: ""
        val full = PendingIntent.getActivity(context, nagId(task.id),
            Intent(context, NagActivity::class.java).putExtra(EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CH_NAG)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle("Agora: ${task.title}")
            .setContentText("Era para às $time e ainda está pendente.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            // the next repeat (if any) takes over, so one alert never rings forever
            .setTimeoutAfter(repeatMinutes * 60_000L)
            .addAction(0, "Concluir", action(task.id, ActionReceiver.DONE))
            .addAction(0, "Adiar $repeatMinutes min", action(task.id, ActionReceiver.SNOOZE))
            .addAction(0, "Parar", action(task.id, ActionReceiver.STOP))
            .build()
        n.flags = n.flags or android.app.Notification.FLAG_INSISTENT
        postBuilt(nagId(task.id), n)
    }

    fun info(id: Int, title: String, text: String) = post(id, NotificationCompat.Builder(context, CH_INFO)
        .setSmallIcon(R.drawable.ic_stat_task).setContentTitle(title).setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true))

    fun cancel(taskId: Long) {
        nm.cancel(reminderId(taskId))
        nm.cancel(nagId(taskId))
    }

    private fun post(id: Int, b: NotificationCompat.Builder) = postBuilt(id, b.build())

    private fun postBuilt(id: Int, n: android.app.Notification) {
        if (!canPost()) {
            Log.w(TAG, "Notification $id dropped: permission denied or notifications disabled")
            return
        }
        try {
            nm.notify(id, n)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification $id failed", e)
        }
    }

    private fun openTask(taskId: Long) = PendingIntent.getActivity(context, reminderId(taskId),
        Intent(context, MainActivity::class.java).putExtra(EXTRA_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(taskId: Long, what: String) = PendingIntent.getBroadcast(context,
        nagId(taskId) * 4 + ActionReceiver.ACTIONS.indexOf(what),
        Intent(context, ActionReceiver::class.java).setAction(what).putExtra(EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val TAG = "RoutineNotify"
        const val CH_REMINDER = "reminders"
        const val CH_NAG = "nag"
        const val CH_INFO = "info"
        const val EXTRA_TASK_ID = "taskId"
        // shortcut: ids assume < 1 billion tasks
        fun reminderId(taskId: Long) = (taskId * 2).toInt()
        fun nagId(taskId: Long) = (taskId * 2 + 1).toInt()
    }
}
