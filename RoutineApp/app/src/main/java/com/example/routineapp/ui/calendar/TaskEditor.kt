package com.example.routineapp.ui.calendar

import com.example.routineapp.ui.withClick
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.routineapp.ui.theme.McButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.routineapp.data.Recurrence
import com.example.routineapp.data.Task
import com.example.routineapp.ui.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val DURATIONS = listOf(null, 15, 30, 45, 60, 90, 120, 180)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditor(initial: Task?, defaultDate: LocalDate, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    val base = initial ?: Task(title = "", epochDay = defaultDate.toEpochDay(), createdAt = 0, updatedAt = 0)
    var title by remember { mutableStateOf(base.title) }
    var description by remember { mutableStateOf(base.description.orEmpty()) }
    var date by remember { mutableStateOf(base.date) }
    var start by remember { mutableStateOf(base.startMinute) }
    var duration by remember { mutableStateOf(base.durationMinutes) }
    var category by remember { mutableStateOf(base.category.orEmpty()) }
    var priority by remember { mutableStateOf(base.priority) }
    var location by remember { mutableStateOf(base.location.orEmpty()) }
    var notes by remember { mutableStateOf(base.notes.orEmpty()) }
    var recurrence by remember { mutableStateOf(base.recurrence) }
    var notify by remember { mutableStateOf(base.notifyEnabled) }
    var nag by remember { mutableStateOf(base.nagEnabled) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (initial == null) "Nova tarefa" else "Editar tarefa") },
                    navigationIcon = { IconButton(onClick = withClick(onDismiss)) { Icon(Icons.Default.Close, "Fechar") } },
                    actions = {
                        TextButton(onClick = withClick {
                            if (title.isBlank()) { titleError = true; return@withClick }
                            onSave(base.copy(
                                title = title.trim(), description = description.trim().ifBlank { null },
                                epochDay = date.toEpochDay(), startMinute = start,
                                durationMinutes = if (start == null) null else duration,
                                category = category.trim().ifBlank { null }, priority = priority,
                                location = location.trim().ifBlank { null }, notes = notes.trim().ifBlank { null },
                                recurrence = recurrence, notifyEnabled = notify, nagEnabled = nag,
                            ))
                        }) { Text("Salvar") }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(title, { title = it; titleError = false }, Modifier.fillMaxWidth(), label = { Text("Título") },
                    isError = titleError, supportingText = if (titleError) {{ Text("Dê um nome para a tarefa") }} else null,
                    singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    McButton(onClick = { pickDate = true }, Modifier.weight(1f)) { Text(Formats.date(date)) }
                    McButton(onClick = { pickTime = true }) { Text(start?.let(Formats::time) ?: "Sem horário") }
                    if (start != null) IconButton(onClick = withClick { start = null }) { Icon(Icons.Default.Close, "Remover horário") }
                }
                if (start != null) {
                    Text("Duração", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DURATIONS.forEach { d ->
                            FilterChip(selected = duration == d, onClick = withClick { duration = d },
                                label = { Text(d?.let(Formats::duration) ?: "Pontual") })
                        }
                    }
                }
                Text("Prioridade", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PRIORITY_LABELS.forEachIndexed { i, label ->
                        SegmentedButton(selected = priority == i, onClick = withClick { priority = i },
                            shape = SegmentedButtonDefaults.itemShape(i, PRIORITY_LABELS.size, baseShape = RoundedCornerShape(0.dp))) { Text(label) }
                    }
                }
                Text("Repetição", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Recurrence.entries.forEach { r ->
                        FilterChip(selected = recurrence == r, onClick = withClick { recurrence = r }, label = { Text(RECURRENCE_LABELS.getValue(r)) })
                    }
                }
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descrição") })
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("Local") }, singleLine = true)
                OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("Categoria (ex.: estudos, trabalho)") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Observações") })
                SwitchRow("Lembrete 5 horas antes", notify, enabled = start != null) { notify = it }
                SwitchRow("Cobrar no horário se estiver pendente", nag, enabled = start != null) { nag = it }
                if (start == null) Text("Sem horário a tarefa não gera lembrete nem cobrança.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (pickDate) {
        // DatePicker works with UTC midnight millis
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = withClick {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = withClick { pickDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = (start ?: 9 * 60) / 60, initialMinute = (start ?: 0) % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = withClick { start = state.hour * 60 + state.minute; pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = withClick { pickTime = false }) { Text("Cancelar") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}
