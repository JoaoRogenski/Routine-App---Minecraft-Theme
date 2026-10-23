package com.example.routineapp.notify

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.routineapp.MainActivity
import com.example.routineapp.app
import com.example.routineapp.ui.Formats
import com.example.routineapp.ui.theme.Mc
import com.example.routineapp.ui.theme.McButton
import com.example.routineapp.ui.theme.RoutineAppTheme
import com.example.routineapp.ui.theme.ShadowText
import com.example.routineapp.ui.theme.dirtBackground
import kotlinx.coroutines.launch

/** Full-screen, call-like alert. It is not a phone call: the sound comes from the insistent notification. */
class NagActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val id = intent.getLongExtra(Notifier.EXTRA_TASK_ID, -1)
        val app = app
        setContent {
            RoutineAppTheme(darkTheme = true) {
                val task by app.tasks.observe(id).collectAsState(initial = null)
                val repeat = app.settings.current.nagRepeatMinutes
                // block-game "game over" screen: red wash over the world, big shadowed title, stacked stone buttons
                Column(
                    Modifier.fillMaxSize().dirtBackground(dark = true)
                        .background(Brush.verticalGradient(listOf(Color(0x99A00000), Color(0xCC500000))))
                        .systemBarsPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Hora da tarefa!", style = MaterialTheme.typography.displaySmall.merge(ShadowText),
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(task?.title ?: "", style = MaterialTheme.typography.headlineSmall.merge(ShadowText).copy(color = Mc.ChatYellow),
                        textAlign = TextAlign.Center)
                    task?.startTime?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("Marcada para ${Formats.time(it)}", style = MaterialTheme.typography.bodyLarge.merge(ShadowText), fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(48.dp))
                    val w = Modifier.width(280.dp)
                    McButton(onClick = { act(ActionReceiver.DONE, id) }, modifier = w, primary = true) { Text("Concluir tarefa") }
                    Spacer(Modifier.height(10.dp))
                    McButton(onClick = { act(ActionReceiver.SNOOZE, id) }, modifier = w) { Text("Adiar $repeat min") }
                    Spacer(Modifier.height(10.dp))
                    McButton(onClick = {
                        startActivity(Intent(this@NagActivity, MainActivity::class.java)
                            .putExtra(Notifier.EXTRA_TASK_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        act(ActionReceiver.STOP, id)
                    }, modifier = w) { Text("Abrir tarefa") }
                    Spacer(Modifier.height(10.dp))
                    McButton(onClick = { act(ActionReceiver.STOP, id) }, modifier = w) { Text("Parar alerta") }
                }
            }
        }
    }

    private fun act(action: String, id: Long) {
        lifecycleScope.launch {
            ActionReceiver.handle(app, action, id)
            finish()
        }
    }
}
