package com.example.routineapp.ui.calendar

import com.example.routineapp.ui.withClick
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.lerp
import com.example.routineapp.ui.theme.Mc
import com.example.routineapp.ui.theme.McButton
import com.example.routineapp.ui.theme.ShadowText
import com.example.routineapp.ui.theme.XpBar
import com.example.routineapp.ui.theme.bevel
import com.example.routineapp.ui.theme.panel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskStatus
import com.example.routineapp.domain.ReminderRules
import com.example.routineapp.ui.Formats
import com.example.routineapp.ui.PermissionBanner
import com.example.routineapp.ui.theme.CategoryPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val HOUR_HEIGHT = 56.dp
private val GUTTER = 44.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel, openTaskId: Long?, onTaskOpened: () -> Unit) {
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val conflict by vm.conflict.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var creatingOn by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(openTaskId) {
        if (openTaskId != null) { selectedId = openTaskId; onTaskOpened() }
    }

    val today = LocalDate.now()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(Formats.monthYear(weekStart.plusDays(3)), style = MaterialTheme.typography.titleLarge.merge(ShadowText)) },
                actions = {
                    val small = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    McButton(onClick = vm::goToday, contentPadding = small) { Text("Hoje") }
                    McButton(onClick = { vm.shiftWeek(-1) }, contentPadding = small,
                        modifier = Modifier.semantics { contentDescription = "Semana anterior" }) { Text("<") }
                    McButton(onClick = { vm.shiftWeek(1) }, contentPadding = small,
                        modifier = Modifier.semantics { contentDescription = "Próxima semana" }) { Text(">") }
                    McButton(onClick = { vm.mode.value = if (mode == CalendarMode.WEEK) CalendarMode.AGENDA else CalendarMode.WEEK },
                        contentPadding = small, modifier = Modifier.padding(end = 8.dp)) {
                        Text(if (mode == CalendarMode.WEEK) "Lista" else "Semana")
                    }
                },
            )
        },
        floatingActionButton = {
            McButton(onClick = { creatingOn = if (today in weekStart..weekStart.plusDays(6)) today else weekStart },
                primary = true, modifier = Modifier.size(60.dp).semantics { contentDescription = "Nova tarefa" },
                contentPadding = PaddingValues(0.dp)) {
                Text("+", fontSize = 30.sp)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PermissionBanner()
            val done = tasks.count { it.status == TaskStatus.DONE }
            XpBar(level = done, progress = if (tasks.isEmpty()) 0f else done / tasks.size.toFloat(),
                caption = if (tasks.isEmpty()) "Nenhuma tarefa nesta semana" else "$done de ${tasks.size} tarefas concluídas na semana",
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
            Column(Modifier.padding(horizontal = 8.dp).fillMaxSize().panel().padding(3.dp)) {
                if (mode == CalendarMode.WEEK) WeekView(weekStart, tasks, now, today) { selectedId = it.id }
                else AgendaView(weekStart, tasks, now, today, onOpen = { selectedId = it.id }, onToggle = vm::toggleDone)
            }
        }
    }

    selectedId?.let { id ->
        TaskSheet(vm, id, now,
            onDismiss = { selectedId = null },
            onEdit = { editing = it; selectedId = null })
    }
    if (editing != null || creatingOn != null) {
        TaskEditor(
            initial = editing,
            defaultDate = creatingOn ?: today,
            onDismiss = { editing = null; creatingOn = null },
            onSave = { vm.save(it); editing = null; creatingOn = null },
        )
    }
    conflict?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.conflict.value = null },
            title = { Text("Horário em conflito") },
            text = { Text("Nesse horário você já tem " + c.conflicts.joinToString { "“${it.title}” (${it.startMinute?.let(Formats::time)})" } +
                ". Quer salvar “${c.task.title}” mesmo assim?") },
            confirmButton = { TextButton(onClick = withClick { vm.save(c.task, force = true) }) { Text("Salvar assim mesmo") } },
            dismissButton = { TextButton(onClick = withClick { vm.conflict.value = null }) { Text("Cancelar") } },
        )
    }
}

fun isOverdue(t: Task, now: Long) = ReminderRules.isOverdue(t, now, ZoneId.systemDefault())

@Composable
fun taskColors(t: Task, now: Long): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when {
        t.status == TaskStatus.DONE -> cs.surfaceVariant.copy(alpha = 0.6f) to cs.onSurfaceVariant.copy(alpha = 0.7f)
        isOverdue(t, now) -> Mc.Redstone to Color.White
        else -> {
            CategoryPalette[Math.floorMod((t.category ?: "").lowercase().hashCode(), CategoryPalette.size)]
        }
    }
}


fun statusLabel(t: Task, now: Long) = when {
    t.status == TaskStatus.DONE -> "Concluída"
    isOverdue(t, now) -> "Atrasada"
    else -> "Pendente"
}

@Composable
private fun WeekView(weekStart: LocalDate, tasks: List<Task>, now: Long, today: LocalDate, onOpen: (Task) -> Unit) {
    val days = (0L..6L).map { weekStart.plusDays(it) }
    val untimed = tasks.filter { it.startMinute == null }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(weekStart) { scroll.scrollTo(with(density) { (HOUR_HEIGHT * 7).roundToPx() }) }

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Spacer(Modifier.width(GUTTER))
        days.forEach { d ->
            val isToday = d == today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Formats.weekdayShort(d), style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.size(32.dp)
                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${d.dayOfMonth}", fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    if (untimed.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("dia\ntodo", Modifier.width(GUTTER).padding(start = 4.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 12.sp)
            days.forEach { d ->
                Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    untimed.filter { it.date == d }.forEach { t -> TaskBlock(t, now, Modifier.fillMaxWidth().height(22.dp), onOpen) }
                }
            }
        }
    }
    HorizontalDivider()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Column(Modifier.width(GUTTER)) {
            (0..23).forEach { h ->
                Box(Modifier.height(HOUR_HEIGHT).fillMaxWidth()) {
                    if (h > 0) Text("${h}h", Modifier.align(Alignment.TopEnd).offset(y = (-7).dp).padding(end = 6.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        days.forEach { d ->
            BoxWithConstraints(
                Modifier.weight(1f).height(HOUR_HEIGHT * 24).drawBehind {
                    val step = HOUR_HEIGHT.toPx()
                    for (h in 0..23) drawLine(gridColor, Offset(0f, h * step), Offset(size.width, h * step), 1f)
                    drawLine(gridColor, Offset(0f, 0f), Offset(0f, size.height), 1f)
                }
            ) {
                val dayTasks = tasks.filter { it.date == d && it.startMinute != null }
                val lanes = assignLanes(dayTasks)
                val colWidth = maxWidth
                dayTasks.forEach { t ->
                    val (lane, laneCount) = lanes.getValue(t.id)
                    val start = t.startMinute!!
                    val minutes = (t.durationMinutes ?: 30).coerceAtLeast(25).coerceAtMost(24 * 60 - start)
                    TaskBlock(t, now,
                        Modifier.offset(x = colWidth / laneCount * lane, y = HOUR_HEIGHT * (start / 60f))
                            .width(colWidth / laneCount).height(HOUR_HEIGHT * (minutes / 60f)).padding(1.dp),
                        onOpen)
                }
                if (d == today) {
                    val nowTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
                    val y = HOUR_HEIGHT * ((nowTime.hour * 60 + nowTime.minute) / 60f)
                    val red = MaterialTheme.colorScheme.error
                    Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(red))
                    Box(Modifier.offset(x = (-4).dp, y = y - 5.dp).size(10.dp).background(red))
                }
            }
        }
    }
}

/** Side-by-side lanes for overlapping tasks: id -> (lane, lanes in its cluster). */
fun assignLanes(tasks: List<Task>): Map<Long, Pair<Int, Int>> {
    val sorted = tasks.sortedWith(compareBy({ it.startMinute }, { -(it.durationMinutes ?: 30) }))
    val result = mutableMapOf<Long, Pair<Int, Int>>()
    var cluster = mutableListOf<Pair<Task, Int>>()
    var clusterEnd = -1
    val laneEnds = mutableListOf<Int>()
    fun flush() {
        val count = (cluster.maxOfOrNull { it.second } ?: -1) + 1
        cluster.forEach { (t, lane) -> result[t.id] = lane to count }
        cluster = mutableListOf(); laneEnds.clear()
    }
    for (t in sorted) {
        val s = t.startMinute!!
        val e = s + (t.durationMinutes ?: 30).coerceAtLeast(25)
        if (s >= clusterEnd) flush()
        var lane = laneEnds.indexOfFirst { it <= s }
        if (lane == -1) { lane = laneEnds.size; laneEnds += e } else laneEnds[lane] = e
        cluster += t to lane
        clusterEnd = maxOf(clusterEnd, e)
    }
    flush()
    return result
}

@Composable
private fun TaskBlock(t: Task, now: Long, modifier: Modifier, onOpen: (Task) -> Unit) {
    val (bg, fg) = taskColors(t, now)
    val done = t.status == TaskStatus.DONE
    Column(
        modifier.bevel(bg, lerp(bg, Color.White, 0.35f), lerp(bg, Color.Black, 0.4f), outline = null).clickable { onOpen(t) }
            .semantics { contentDescription = "${t.title}, ${t.startMinute?.let(Formats::time) ?: "sem horário"}, ${statusLabel(t, now)}" }
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        Text(t.title, color = fg, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            textDecoration = if (done) TextDecoration.LineThrough else null)
    }
}

@Composable
private fun AgendaView(
    weekStart: LocalDate, tasks: List<Task>, now: Long, today: LocalDate,
    onOpen: (Task) -> Unit, onToggle: (Task) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
        (0L..6L).map { weekStart.plusDays(it) }.forEach { d ->
            val dayTasks = tasks.filter { it.date == d }
            item(key = "h$d") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(Formats.date(d, today).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (d == today) Text("  · hoje", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (dayTasks.isEmpty()) item(key = "e$d") {
                Text("Nada marcado", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(dayTasks, key = { it.id }) { t -> AgendaRow(t, now, onOpen, onToggle) }
        }
    }
}

@Composable
private fun AgendaRow(t: Task, now: Long, onOpen: (Task) -> Unit, onToggle: (Task) -> Unit) {
    val (bg, _) = taskColors(t, now)
    val done = t.status == TaskStatus.DONE
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(horizontal = 8.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = { onToggle(t) },
            modifier = Modifier.semantics { contentDescription = if (done) "Reabrir ${t.title}" else "Concluir ${t.title}" })
        Box(Modifier.size(20.dp).bevel(bg, lerp(bg, Color.White, 0.35f), lerp(bg, Color.Black, 0.4f), outline = null))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            val sub = listOfNotNull(
                t.startMinute?.let { s -> Formats.time(s) + (t.durationMinutes?.let { " – " + Formats.time((s + it).coerceAtMost(1439)) } ?: "") } ?: "Dia todo",
                t.location, t.category,
            ).joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isOverdue(t, now)) Text("Atrasada", Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error)
        if (t.priority == 2 && !done) Box(Modifier.padding(end = 8.dp).size(8.dp).background(MaterialTheme.colorScheme.error)
            .semantics { contentDescription = "Prioridade alta" })
    }
}
