package com.example.routineapp

import android.app.Application
import com.example.routineapp.assistant.Assistant
import com.example.routineapp.assistant.BackendClient
import com.example.routineapp.data.AppDatabase
import com.example.routineapp.data.SettingsRepository
import com.example.routineapp.domain.TaskRepository
import com.example.routineapp.notify.AndroidAlarmScheduler
import com.example.routineapp.notify.Notifier
import com.example.routineapp.ui.ClickSound

/** Manual DI: the app is small enough that a container of lazies is all we need. */
class App : Application() {
    val settings by lazy { SettingsRepository(this) }
    val notifier by lazy { Notifier(this) }
    val scheduler by lazy { AndroidAlarmScheduler(this, settings, notifier) }
    val tasks by lazy { TaskRepository(AppDatabase.build(this).taskDao(), scheduler) }
    val backend by lazy { BackendClient(settings) }
    val clickSound by lazy { ClickSound(this) }
    val assistant by lazy { Assistant(tasks, { settings.current }, backend, notificationsAllowed = { notifier.canPost() }) }

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        clickSound // load the sample now; SoundPool loads async
    }
}

val android.content.Context.app: App get() = applicationContext as App
