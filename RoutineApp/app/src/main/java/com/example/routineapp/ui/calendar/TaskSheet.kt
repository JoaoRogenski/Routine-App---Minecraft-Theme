package com.example.routineapp.ui.calendar

import com.example.routineapp.ui.withClick
import com.example.routineapp.ui.theme.McButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.routineapp.data.Recurrence
import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskSource
import com.example.routineapp.data.TaskStatus
import com.example.routineapp.ui.Formats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val RECURRENCE_LABELS = mapOf(Recurrence.NONE to "Não repete", Recurrence.DAILY to "Todo dia",
    Recurrence.WEEKLY to "Toda semana", Recurrence.MONTHLY to "Todo mês")
val PRIORITY_LABELS = listOf("Baixa", "Normal", "Alta")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(vm: CalendarViewModel, id: Long, now: Long, onDismiss: () -> Unit, onEdit: (Task) -> Unit) {
    val task by vm.observe(id).collectAsState(initial = null)
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val t = task
        if (t == null) {
            Text("Esta tarefa não existe mais.", Modifier.padding(24.dp))
            return@ModalBottomSheet
        }
        val done = t.status == TaskStatus.DONE
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(Formats.date(t.date).replaceFirstChar { it.uppercase() } + (t.startMinute?.let { s ->
                " · " + Formats.time(s) + (t.durationMinutes?.let { " – " + Formats.time((s + it).coerceAtMost(1439)) } ?: "")
            } ?: " · dia todo"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(statusLabel(t, now)) })
                if (t.priority != 1) AssistChip(onClick = {}, label = { Text("Prioridade ${PRIORITY_LABELS[t.priority].lowercase()}") })
                if (t.recurrence != Recurrence.NONE) AssistChip(onClick = {}, label = { Text(RECURRENCE_LABELS.getValue(t.recurrence)) })
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                McButton(onClick = { vm.toggleDone(t) }, modifier = Modifier.weight(1f), primary = true) {
                    Icon(if (done) Icons.Default.Refresh else Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (done) "Reabrir" else "Concluir")
                }
                McButton(onClick = { onEdit(t) }) { Icon(Icons.Default.Edit, "Editar") }
                McButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Excluir") }
            }
            Spacer(Modifier.height(16.dp))
            Detail("Descrição", t.description)
            Detail("Local", t.location)
            Detail("Categoria", t.category)
            Detail("Observações", t.notes)
            Detail("Avisos", listOfNotNull(
                if (t.startMinute == null) "Sem horário: sem lembrete nem cobrança" else null,
                if (t.startMinute != null) (if (t.notifyEnabled) "Lembrete 5h antes" else "Sem lembrete") else null,
                if (t.startMinute != null) (if (t.nagEnabled) "Cobrança no horário" else "Sem cobrança") else null,
            ).joinToString(" · "))
            t.completedAt?.let { Detail("Concluída em", stamp(it)) }
            Detail("Registro", (if (t.source == TaskSource.CHAT) "Criada pelo assistente" else "Criada manualmente") +
                " em ${stamp(t.createdAt)} · alterada em ${stamp(t.updatedAt)}")
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir tarefa?") },
            text = { Text("“${task?.title}” será apagada e os avisos dela cancelados. Para registrar que você fez, use Concluir.") },
            confirmButton = {
                TextButton(onClick = withClick { task?.let(vm::delete); confirmDelete = false; onDismiss() }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = withClick { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private val STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private fun stamp(millis: Long) = STAMP.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun Detail(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
