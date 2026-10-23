package com.example.routineapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routineapp.assistant.Assistant
import com.example.routineapp.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String, val tasks: List<Task> = emptyList(), val error: Boolean = false)

// shortcut: chat history lives in memory only (tasks themselves are persisted); persist it if users ask for history
class ChatViewModel(private val assistant: Assistant, val userName: () -> String) : ViewModel() {
    private var nextId = 0L
    val messages = MutableStateFlow(listOf(greeting()))
    val busy = MutableStateFlow(false)

    fun send(text: String) {
        if (text.isBlank() || busy.value) return
        if (text.trim().equals("/clear", ignoreCase = true)) {
            assistant.reset()
            messages.value = listOf(greeting())
            return
        }
        messages.update { it + ChatMessage(nextId++, true, text.trim()) }
        busy.value = true
        viewModelScope.launch {
            // parsing + DB work off the main thread, so the UI never freezes while the assistant thinks
            val reply = withContext(Dispatchers.Default) { assistant.handle(text) }
            messages.update { it + ChatMessage(nextId++, false, reply.text, reply.tasks, reply.error) }
            busy.value = false
        }
    }

    private fun greeting() = ChatMessage(nextId++, false,
        "Oi${userName().takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}! Me conta o que você precisa fazer e quando. " +
            "Eu registro no calendário, te lembro 5 horas antes e cobro se ficar pendente.")
}
