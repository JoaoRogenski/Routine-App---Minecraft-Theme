# Backend (opcional)

Servidor mínimo (Node 18+, sem dependências) que guarda os segredos fora do celular.
O app funciona sem ele: o assistente usa o modo básico e a cobrança usa o alarme local.

| Endpoint | Função |
|---|---|
| `GET /v1/health` | diz se IA e telefonia estão configuradas |
| `POST /v1/parse` | manda a mensagem para a IA e devolve um comando JSON (o app revalida tudo) |
| `POST /v1/call` | faz uma ligação real via Twilio, com bloqueio de duplicadas (24h) e limite por hora |

Todas as rotas exigem `Authorization: Bearer <APP_TOKEN>`.

## Rodar

```bash
cp .env.example .env      # preencha APP_TOKEN e o que for usar
node --env-file=.env server.mjs
node --test               # testes
```

O Android bloqueia HTTP sem TLS: publique o servidor com **HTTPS** (Render, Fly.io, Railway, uma VPS com Caddy,
ou um túnel como `cloudflared tunnel --url http://localhost:8787` para testes).
No app: Configurações → Servidor → endereço + token → "Testar conexão" → ative "Usar IA".

## IA

- `AI_PROVIDER=anthropic` + `AI_API_KEY` (modelo padrão `claude-haiku-5-5`, troque com `AI_MODEL`).
- `AI_PROVIDER=openai` para qualquer API compatível com OpenAI: OpenAI, OpenRouter, Ollama
  (`AI_BASE_URL=http://localhost:11434/v1`), LM Studio.

## Ligações reais (Twilio)

1. Crie uma conta Twilio, compre um número com voz e (na conta trial) verifique o seu celular.
2. Preencha `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` e `ALLOWED_NUMBERS=+55...` (só o seu).
3. No app: cadastre o telefone no formato `+5511999998888`, ative "Ligar quando a cobrança disparar", "Testar ligação".

Cada ligação é paga. O app pede a ligação só no **primeiro** alerta de cada tarefa, no horário, a partir do
próprio celular (que confere antes se a tarefa ainda está pendente) — por isso precisa de internet nessa hora.
"Ligação solicitada" significa que a Twilio aceitou o pedido, não que você atendeu.
