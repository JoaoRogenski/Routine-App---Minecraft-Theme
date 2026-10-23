package com.example.routineapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.routineapp.ui.withClick
import com.example.routineapp.ui.theme.Mc
import com.example.routineapp.ui.theme.PixelArt
import com.example.routineapp.ui.theme.PixelIcon
import com.example.routineapp.ui.theme.ShadowText
import com.example.routineapp.ui.theme.dirtBackground
import com.example.routineapp.ui.theme.slot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.routineapp.data.ThemeMode
import com.example.routineapp.notify.Notifier
import com.example.routineapp.ui.calendar.CalendarScreen
import com.example.routineapp.ui.calendar.CalendarViewModel
import com.example.routineapp.ui.chat.ChatScreen
import com.example.routineapp.ui.chat.ChatViewModel
import com.example.routineapp.ui.settings.SettingsScreen
import com.example.routineapp.ui.theme.RoutineAppTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { SETTINGS("Configurações"), CALENDAR("Calendário"), ASSISTANT("Assistente") }

class MainActivity : ComponentActivity() {
    /** Task to show, coming from a notification tap. */
    private val openTask = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        val app = app
        // alarms may be stale (permission just granted, missed broadcast); re-planning is idempotent
        lifecycleScope.launch { app.tasks.rescheduleAll() }
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            RoutineAppTheme(darkTheme = dark) {
                var tab by rememberSaveable { mutableStateOf(Tab.CALENDAR) }
                val openTaskId by openTask.collectAsStateWithLifecycle()
                if (openTaskId != null) tab = Tab.CALENDAR
                val calendarVm = viewModel { CalendarViewModel(app.tasks) }
                val chatVm = viewModel { ChatViewModel(app.assistant) { app.settings.current.name } }
                Scaffold(
                    containerColor = Color.Transparent,
                    modifier = Modifier.dirtBackground(dark),
                    bottomBar = { Hotbar(tab) { tab = it } },
                ) { padding ->
                    // status bar and bottom bar insets applied once here, so no screen draws under the system icons
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                        when (tab) {
                            Tab.SETTINGS -> SettingsScreen()
                            Tab.CALENDAR -> CalendarScreen(calendarVm, openTaskId) { openTask.value = null }
                            Tab.ASSISTANT -> ChatScreen(chatVm) { t ->
                                calendarVm.goTo(t.date)
                                openTask.value = t.id
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifier.EXTRA_TASK_ID, -1) ?: -1
        if (id > 0) openTask.value = id
    }
}

/** Bottom navigation drawn as a block-game hotbar: item slots, the selected one framed in white. */
@Composable
private fun Hotbar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).navigationBarsPadding()
            .padding(vertical = 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val isSel = t == selected
            Column(
                Modifier.selectable(isSel, role = Role.Tab, onClick = withClick { onSelect(t) }).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(56.dp)
                        .border(3.dp, if (isSel) Color.White else Color.Transparent)
                        .padding(3.dp).slot(Mc.Slot),
                    contentAlignment = Alignment.Center,
                ) {
                    when (t) {
                        Tab.SETTINGS -> PixelIcon(PixelArt.pickaxe, PixelArt.pickaxeColors, 30.dp)
                        Tab.CALENDAR -> PixelIcon(PixelArt.clock, PixelArt.clockColors, 30.dp)
                        Tab.ASSISTANT -> PixelIcon(PixelArt.book, PixelArt.bookColors, 30.dp)
                    }
                }
                Text(t.label, Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelMedium.merge(ShadowText).copy(color = if (isSel) Mc.ChatYellow else Color.White))
            }
        }
    }
}
