package com.example.routineapp.ui.settings

import com.example.routineapp.ui.withClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import com.example.routineapp.ui.theme.McButton
import com.example.routineapp.ui.theme.McPanel
import com.example.routineapp.ui.theme.ShadowText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routineapp.app
import com.example.routineapp.data.AppSettings
import com.example.routineapp.data.SettingsRepository
import com.example.routineapp.data.Task
import com.example.routineapp.data.ThemeMode
import com.example.routineapp.ui.calendar.SwitchRow
import com.example.routineapp.ui.openBatterySettings
import com.example.routineapp.ui.openExactAlarmSettings
import com.example.routineapp.ui.openFullScreenSettings
import com.example.routineapp.ui.rememberNotificationPermissionRequest
import com.example.routineapp.ui.rememberPermissionState
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.app
    val s by app.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val (perms, refresh) = rememberPermissionState()
    val requestNotifications = rememberNotificationPermissionRequest(refresh)
    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }
    /** Changes that affect alarms re-plan every pending task. */
    fun updateAndReschedule(f: (AppSettings) -> AppSettings) {
        app.settings.update(f)
        scope.launch { app.tasks.rescheduleAll() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Configurações", style = MaterialTheme.typography.titleLarge.merge(ShadowText)) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Section("Perfil") {
                OutlinedTextField(s.name, { v -> app.settings.update { it.copy(name = v.take(40)) } }, Modifier.fillMaxWidth(),
                    label = { Text("Seu nome ou apelido") }, singleLine = true,
                    supportingText = { Text("O assistente usa seu nome de vez em quando.") })
            }

            Section("Aparência") {
                val options = listOf(ThemeMode.SYSTEM to "Automático", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(selected = s.theme == mode, onClick = withClick { app.settings.update { it.copy(theme = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size, baseShape = RoundedCornerShape(0.dp))) { Text(label) }
                    }
                }
            }

            Section("Lembretes") {
                SwitchRow("Aviso 5 horas antes", s.remindersEnabled,
                    description = "Uma notificação simples: \"Você tem uma tarefa às 14h: …\"") { v ->
                    updateAndReschedule { it.copy(remindersEnabled = v) }
                }
                SwitchRow("Avisar na hora se faltar menos de 5h", s.notifyImmediatelyWhenLate, enabled = s.remindersEnabled,
                    description = "Para tarefas criadas em cima da hora.") { v -> app.settings.update { it.copy(notifyImmediatelyWhenLate = v) } }
                McButton(onClick = {
                    if (!perms.notifications) { toast("Notificações bloqueadas — libere em Permissões abaixo"); return@McButton }
                    val t = LocalDateTime.now().plusHours(5)
                    app.notifier.reminder(Task(id = 0, title = "teste de notificação", epochDay = t.toLocalDate().toEpochDay(),
                        startMinute = t.hour * 60 + t.minute, createdAt = 0, updatedAt = 0))
                    toast("Notificação de teste enviada")
                }) { Text("Testar notificação") }
            }

            Section("Cobrança no horário") {
                Text("Se uma tarefa chegar no horário e continuar pendente, o celular toca como um alarme, " +
                    "com tela cheia e botões para concluir, adiar ou parar.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow("Cobrar tarefas pendentes", s.nagEnabled) { v -> updateAndReschedule { it.copy(nagEnabled = v) } }
                Text("Repetir a cada", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { m ->
                        FilterChip(selected = s.nagRepeatMinutes == m, enabled = s.nagEnabled,
                            onClick = withClick { updateAndReschedule { it.copy(nagRepeatMinutes = m) } }, label = { Text("$m min") })
                    }
                }
                Text("Número máximo de alertas", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { n ->
                        FilterChip(selected = s.nagMaxAlerts == n, enabled = s.nagEnabled,
                            onClick = withClick { updateAndReschedule { it.copy(nagMaxAlerts = n) } }, label = { Text(if (n == 1) "Só 1" else "$n") })
                    }
                }
                McButton(onClick = {
                    if (!perms.notifications) { toast("Notificações bloqueadas — libere em Permissões abaixo"); return@McButton }
                    val t = LocalDateTime.now()
                    app.notifier.nag(Task(id = 0, title = "Teste de cobrança", epochDay = t.toLocalDate().toEpochDay(),
                        startMinute = t.hour * 60 + t.minute, createdAt = 0, updatedAt = 0), 1)
                    toast(if (perms.fullScreen) "Alarme de teste disparado" else "Alarme disparado (sem tela cheia: veja a notificação)")
                }) { Text("Testar alarme") }
            }

            Section("Ligação telefônica real") {
                Text("Opcional. Uma ligação de verdade precisa de um servidor com uma conta de telefonia (ex.: Twilio), " +
                    "que pode cobrar por chamada e exige internet no horário. Veja o README do projeto. " +
                    "O alarme local continua funcionando mesmo se a ligação falhar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var phone by remember { mutableStateOf(s.phoneNumber) }
                val phoneValid = SettingsRepository.isValidPhone(phone)
                OutlinedTextField(phone, { v -> phone = v.filter { it.isDigit() || it == '+' }.take(16)
                    if (phone.isEmpty() || SettingsRepository.isValidPhone(phone)) app.settings.update { it.copy(phoneNumber = phone) } },
                    Modifier.fillMaxWidth(), label = { Text("Telefone com DDI e DDD") }, singleLine = true,
                    placeholder = { Text("+5511999998888") },
                    isError = phone.isNotEmpty() && !phoneValid,
                    supportingText = { Text(if (phone.isNotEmpty() && !phoneValid) "Use o formato +55 DDD número, sem espaços" else "Formato internacional (E.164)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                val ready = SettingsRepository.isValidPhone(s.phoneNumber) && s.backendUrl.isNotBlank()
                SwitchRow("Ligar quando a cobrança disparar", s.phoneCallEnabled && ready, enabled = ready,
                    description = if (ready) "Uma ligação no primeiro alerta de cada tarefa." else "Preencha o telefone e o servidor abaixo para ativar.") { v ->
                    app.settings.update { it.copy(phoneCallEnabled = v) }
                }
                McButton(enabled = ready, onClick = {
                    scope.launch {
                        try {
                            app.backend.requestCall(s.phoneNumber, "teste do Routine App", "test-${System.currentTimeMillis()}")
                            snackbar.showSnackbar("Ligação solicitada ao servidor. Aguarde tocar.")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Falha: ${e.message}")
                        }
                    }
                }) { Text("Testar ligação") }
            }

            Section("Servidor e inteligência artificial") {
                Text("Sem servidor o assistente usa um modo básico que entende comandos comuns. " +
                    "Com o servidor, uma IA interpreta mensagens mais livres. As chaves ficam só no servidor.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(s.backendUrl, { v -> app.settings.update { it.copy(backendUrl = v.trim()) } }, Modifier.fillMaxWidth(),
                    label = { Text("Endereço do servidor") }, placeholder = { Text("https://meu-servidor.exemplo.com") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(s.backendToken, { v -> app.settings.update { it.copy(backendToken = v.trim()) } }, Modifier.fillMaxWidth(),
                    label = { Text("Token de acesso (APP_TOKEN)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                SwitchRow("Usar IA no assistente", s.aiEnabled && s.backendUrl.isNotBlank(), enabled = s.backendUrl.isNotBlank()) { v ->
                    app.settings.update { it.copy(aiEnabled = v) }
                }
                McButton(enabled = s.backendUrl.isNotBlank(), onClick = {
                    scope.launch {
                        try {
                            val h = app.backend.health()
                            snackbar.showSnackbar("Conectado. IA: ${if (h.optBoolean("ai")) "configurada" else "não configurada"} · " +
                                "Telefonia: ${if (h.optBoolean("telephony")) "configurada" else "não configurada"}")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Falha: ${e.message}")
                        }
                    }
                }) { Text("Testar conexão") }
            }

            Section("Permissões") {
                PermissionRow("Notificações", "Para lembretes e cobranças aparecerem.", perms.notifications, requestNotifications)
                PermissionRow("Alarmes exatos", "Para avisar no minuto certo. Sem ela o Android pode atrasar os avisos.",
                    perms.exactAlarms) { context.openExactAlarmSettings() }
                PermissionRow("Tela cheia", "Para a cobrança abrir por cima da tela de bloqueio, como uma chamada. " +
                    "Sem ela aparece só uma notificação.", perms.fullScreen) { context.openFullScreenSettings() }
                PermissionRow("Sem restrição de bateria", "Recomendado: alguns celulares atrasam alarmes de apps economizando bateria.",
                    perms.batteryUnrestricted) { context.openBatterySettings() }
            }

            Section("Sobre") {
                val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                Text("Routine App $version", style = MaterialTheme.typography.bodyLarge)
                Text("Suas tarefas ficam salvas só neste aparelho. Os avisos são reprogramados automaticamente " +
                    "após reiniciar o celular ou mudar o fuso horário.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    McPanel(Modifier.fillMaxWidth(), title = title) { content() }
}

@Composable
private fun PermissionRow(name: String, why: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (granted) "Concedida" else "Não concedida", style = MaterialTheme.typography.labelMedium,
                color = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
        }
        if (!granted) TextButton(onClick = withClick(onFix)) { Text("Conceder") }
    }
}
