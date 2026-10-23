package com.example.routineapp.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.routineapp.ui.theme.Mc
import com.example.routineapp.ui.theme.McButton
import com.example.routineapp.ui.theme.PixelArt
import com.example.routineapp.ui.theme.PixelIcon
import com.example.routineapp.ui.theme.ShadowText
import com.example.routineapp.ui.theme.panel
import com.example.routineapp.ui.theme.slot
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routineapp.data.Task
import com.example.routineapp.ui.Formats

private val SUGGESTIONS = listOf(
    "Me lembra de pagar a internet amanhã às 18h",
    "O que eu tenho amanhã?",
    "Academia toda segunda às 7h",
    "Ajuda",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onOpenTask: (Task) -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) { listState.animateScrollToItem(messages.size) }
    val send = { vm.send(input); input = "" }

    val userName = vm.userName().ifBlank { "Você" }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Assistente", style = MaterialTheme.typography.titleLarge.merge(ShadowText)) })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(messages, key = { it.id }) { ChatLine(it, userName, onOpenTask) }
                item {
                    AnimatedVisibility(busy) {
                        Text("<Assistente> pensando…", Modifier.background(Color.Black.copy(alpha = 0.55f)).padding(8.dp),
                            style = MaterialTheme.typography.bodyLarge.merge(ShadowText).copy(color = Mc.ChatGray))
                    }
                }
            }
            if (messages.size == 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUGGESTIONS.forEach { s -> McButton(onClick = { vm.send(s) }) { Text(s) } }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ex.: dentista sexta às 14h") },
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.6f), unfocusedContainerColor = Color.Black.copy(alpha = 0.6f),
                        focusedBorderColor = Color.White, unfocusedBorderColor = Mc.ChatGray,
                        cursorColor = Color.White, focusedPlaceholderColor = Mc.ChatGray, unfocusedPlaceholderColor = Mc.ChatGray,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                Spacer(Modifier.size(8.dp))
                McButton(onClick = send, enabled = input.isNotBlank() && !busy, primary = true,
                    modifier = Modifier.height(56.dp)) { Text("Enviar") }
            }
        }
    }
}

/** One line of block-game chat: "<name> text" on a translucent black band, names in chat colours. */
@Composable
private fun ChatLine(m: ChatMessage, userName: String, onOpenTask: (Task) -> Unit) {
    val name = if (m.fromUser) userName else "Assistente"
    val nameColor = when { m.fromUser -> Mc.ChatYellow; m.error -> Mc.ChatRed; else -> Mc.ChatGreen }
    Column(Modifier.fillMaxWidth()) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = nameColor)) { append("<$name> ") }
                append(m.text)
            },
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge.merge(ShadowText),
        )
        m.tasks.take(5).forEach { t -> TaskCard(t, onOpenTask) }
    }
}

/** Task the message touched, shown as an item in an inventory slot. */
@Composable
private fun TaskCard(t: Task, onOpen: (Task) -> Unit) {
    Row(
        Modifier.padding(top = 6.dp).widthIn(max = 340.dp).panel().clickable { onOpen(t) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).slot(), contentAlignment = Alignment.Center) {
            PixelIcon(PixelArt.clock, PixelArt.clockColors, 22.dp)
        }
        Spacer(Modifier.size(10.dp))
        Column {
            Text(t.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(Formats.date(t.date) + (t.startTime?.let { " · " + Formats.time(it) } ?: " · sem horário"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
