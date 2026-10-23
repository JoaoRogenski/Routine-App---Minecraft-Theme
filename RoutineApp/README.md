# Routine

Assistente pessoal Android: você escreve recados no chat, eles viram tarefas no calendário semanal,
chega um lembrete 5 horas antes e, se a tarefa continuar pendente no horário, o celular cobra com um
alarme em tela cheia (e, opcionalmente, uma ligação real).

Kotlin · Jetpack Compose · Material 3 · Room · AlarmManager · minSdk 26 / targetSdk 36.

## Compilar e instalar

```bash
./gradlew assembleDebug          # APK em app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # com o celular conectado (depuração USB ligada)
./gradlew testDebugUnitTest      # 46 testes unitários
cd backend && node --test        # 3 testes do servidor
```

Requer JDK 17+ e Android SDK 36.1. `core-ktx` e `lifecycle` estão fixados em versões que compilam com o SDK 36.1
(as mais novas exigem SDK 37).

## Estrutura

```
app/src/main/java/com/example/routineapp/
  App.kt                 container de dependências
  data/                  Task (Room), DAO, banco, configurações (SharedPreferences)
  domain/                ReminderRules (regras puras de lembrete/cobrança), TaskRepository (toda mudança passa aqui)
  notify/                AlarmManager, receivers (alarme, ações, boot/fuso), notificações, tela de cobrança
  assistant/             Assistant (conversa), LocalParser (pt-BR por regras), CommandJson (valida saída da IA), BackendClient
  ui/                    calendário (semana + lista), chat, configurações, permissões
backend/                 servidor opcional para IA e ligações (ver backend/README.md)
```

## O que funciona

- **3 abas**: Configurações, Calendário, Assistente.
- **Calendário**: semana (dom–sáb) com horas, faixa "dia todo", linha do horário atual, navegação e "Hoje";
  visão em lista com caixa de concluir; cores por categoria; pendente / concluída (riscada) / atrasada (vermelho);
  sobreposições lado a lado; detalhes com concluir/reabrir/editar/excluir (com confirmação); criação manual;
  aviso de conflito antes de salvar.
- **Tarefas**: título, descrição, data, início, duração, categoria, prioridade, local, observações,
  recorrência (diária/semanal/mensal), status, avisos, datas de criação/alteração, origem (manual/chat).
  Concluir uma recorrente guarda o registro e move a tarefa para a próxima data.
- **Assistente** (mesmo banco do calendário): cria, conclui, exclui (pede confirmação), remarca e lista tarefas.
  Entende "amanhã", "depois de amanhã", "sexta", "daqui a 2 semanas", "dia 23", "23/10", "15 de novembro",
  "às 9h", "14h30", "3 da tarde", "meio-dia", "por 2 horas", "urgente", "toda segunda".
  Pergunta quando falta a data, quando a data já passou, quando dia do mês e dia da semana não batem,
  quando "sexta que vem" é ambíguo, quando há conflito de horário e quando há mais de uma tarefa parecida.
  Recusa duplicatas. Só confirma depois de salvar, e só promete o lembrete/cobrança que realmente vai acontecer.
- **Lembrete 5h antes**: alarme exato; tarefa criada com menos de 5h → avisa na hora (configurável);
  nada retroativo; reprogramado ao editar, ao abrir o app, após reiniciar o celular e ao mudar data/fuso.
- **Cobrança no horário (alarme local)**: notificação de alarme insistente (som + vibração) em tela cheia,
  estilo chamada, com Concluir / Adiar / Parar / Abrir. Repete a cada N minutos até um máximo configurável.
  Antes de tocar, relê a tarefa: concluída, excluída, remarcada ou já alertada → não toca.
- **Configurações**: nome, tema claro/escuro/automático, lembretes, cobrança, telefone (validação E.164),
  servidor/IA, status de cada permissão com atalho para conceder, testes de notificação/alarme/ligação.

## Depende de configuração externa

- **IA**: precisa do backend com chave de API (Anthropic ou compatível com OpenAI). Sem ele, ou se falhar,
  o assistente usa o modo básico e avisa.
- **Ligação telefônica real**: precisa do backend + conta Twilio (paga). Implementado mas **não testado
  com uma conta real** — não há credenciais neste ambiente. O alarme local funciona independente disso.

## Testes executados

- 46 testes unitários (JVM), todos passando: interpretação de datas/horas, ambiguidades, cálculo das 5h,
  atraso, versões/alarmes obsoletos, cobranças duplicadas e limite de repetições, conclusão/exclusão/edição
  cancelando ou reagendando, recorrência, conflitos, duplicatas, validação do JSON da IA, fluxos do chat.
- Backend: 3 testes (`node --test`) + teste manual das rotas (token inválido, IA/telefonia não configuradas).
- Celular real (Android 15): app abre sem crash, chat criou a tarefa e ela apareceu no calendário,
  alarmes de lembrete e cobrança registrados no AlarmManager nos horários certos.
- **Não testado**: no celular, a versão com alarme exato automático e reprogramação ao abrir o app (o aparelho desconectou antes); disparo real da cobrança em tela cheia e reinício do aparelho; teste de migração do Room
  (só existe a versão 1 do esquema).

## Permissões no Android

| Permissão | Para quê |
|---|---|
| Notificações | lembretes e cobranças (pedida no app) |
| Alarmes exatos | avisar no minuto certo (automática no Android 13+; no 12, conceder em Configurações) |
| Tela cheia | cobrança por cima da tela de bloqueio (Android 14+ pode exigir liberar) |
| Iniciar com o sistema | refazer alarmes após reiniciar |
| Internet | só para o backend (IA/ligação) |

Recomendado: tirar o app da otimização de bateria (alguns fabricantes atrasam ou matam alarmes).

## Limitações

- O histórico do chat fica só na memória (as tarefas ficam salvas).
- Modo básico entende uma tarefa por mensagem; frases extras viram descrição. A IA separa várias tarefas.
- Sem sincronização entre aparelhos e sem integração com o Google Calendar.
- `USE_EXACT_ALARM` é aceita na Play Store só para apps de alarme/agenda; para publicar, revisar essa política.
