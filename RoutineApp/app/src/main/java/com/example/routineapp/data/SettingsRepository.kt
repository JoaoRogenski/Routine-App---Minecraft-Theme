package com.example.routineapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val name: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Notification 5 hours before each timed task. */
    val remindersEnabled: Boolean = true,
    /** If a task is created less than 5h before it starts, notify right away. */
    val notifyImmediatelyWhenLate: Boolean = true,
    val nagEnabled: Boolean = true,
    val nagRepeatMinutes: Int = 10,
    /** Total insistent alerts per task occurrence (1 = no repeat). */
    val nagMaxAlerts: Int = 3,
    val phoneCallEnabled: Boolean = false,
    val phoneNumber: String = "",
    /** Backend (see README.md) used for AI parsing and phone calls. */
    val backendUrl: String = "",
    val backendToken: String = "",
    val aiEnabled: Boolean = false,
)

/** SharedPreferences-backed settings, exposed as a StateFlow. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = state.asStateFlow()
    val current: AppSettings get() = state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(state.value)
        prefs.edit()
            .putString("name", s.name)
            .putString("theme", s.theme.name)
            .putBoolean("remindersEnabled", s.remindersEnabled)
            .putBoolean("notifyImmediatelyWhenLate", s.notifyImmediatelyWhenLate)
            .putBoolean("nagEnabled", s.nagEnabled)
            .putInt("nagRepeatMinutes", s.nagRepeatMinutes)
            .putInt("nagMaxAlerts", s.nagMaxAlerts)
            .putBoolean("phoneCallEnabled", s.phoneCallEnabled)
            .putString("phoneNumber", s.phoneNumber)
            .putString("backendUrl", s.backendUrl)
            .putString("backendToken", s.backendToken)
            .putBoolean("aiEnabled", s.aiEnabled)
            .apply()
        state.value = s
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            name = prefs.getString("name", d.name)!!,
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", d.theme.name)!!) }.getOrDefault(d.theme),
            remindersEnabled = prefs.getBoolean("remindersEnabled", d.remindersEnabled),
            notifyImmediatelyWhenLate = prefs.getBoolean("notifyImmediatelyWhenLate", d.notifyImmediatelyWhenLate),
            nagEnabled = prefs.getBoolean("nagEnabled", d.nagEnabled),
            nagRepeatMinutes = prefs.getInt("nagRepeatMinutes", d.nagRepeatMinutes),
            nagMaxAlerts = prefs.getInt("nagMaxAlerts", d.nagMaxAlerts),
            phoneCallEnabled = prefs.getBoolean("phoneCallEnabled", d.phoneCallEnabled),
            phoneNumber = prefs.getString("phoneNumber", d.phoneNumber)!!,
            backendUrl = prefs.getString("backendUrl", d.backendUrl)!!,
            backendToken = prefs.getString("backendToken", d.backendToken)!!,
            aiEnabled = prefs.getBoolean("aiEnabled", d.aiEnabled),
        )
    }

    companion object {
        /** E.164, e.g. +5511999998888. */
        fun isValidPhone(number: String) = Regex("^\\+[1-9]\\d{7,14}$").matches(number)
    }
}
