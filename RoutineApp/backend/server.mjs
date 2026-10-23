// Routine App backend: keeps AI and telephony secrets off the phone.
// Zero dependencies, Node 18+. Config via environment variables (see .env.example).
import http from 'node:http';
import crypto from 'node:crypto';
import { pathToFileURL } from 'node:url';

const env = process.env;
const MAX_BODY = 32 * 1024;

// ---------- helpers ----------

export function extractJson(text) {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) throw new Error('modelo não retornou JSON');
  return JSON.parse(text.slice(start, end + 1));
}

export function xmlEscape(s) {
  return String(s).replace(/[<>&'"]/g, c => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;' }[c]));
}

export const isE164 = n => /^\+[1-9]\d{7,14}$/.test(n);

function tokenOk(header) {
  if (!env.APP_TOKEN) return false; // refuse everything until a token is configured
  const given = Buffer.from(String(header || '').replace(/^Bearer /, ''));
  const want = Buffer.from(env.APP_TOKEN);
  return given.length === want.length && crypto.timingSafeEqual(given, want);
}

/** Remembers call keys for 24h so a retried alarm never rings twice. */
export class CallGuard {
  constructor(maxPerHour = 10, now = () => Date.now()) { this.seen = new Map(); this.recent = []; this.max = maxPerHour; this.now = now; }
  check(key) {
    const t = this.now();
    for (const [k, at] of this.seen) if (t - at > 86_400_000) this.seen.delete(k);
    this.recent = this.recent.filter(at => t - at < 3_600_000);
    if (this.seen.has(key)) return 'duplicate';
    if (this.recent.length >= this.max) return 'rate_limited';
    this.seen.set(key, t); this.recent.push(t);
    return 'ok';
  }
}

// ---------- AI ----------

const SYSTEM_PROMPT = `Você é o interpretador de um app de agenda em português do Brasil.
Converta a mensagem do usuário em UM objeto JSON (sem texto fora do JSON) com este formato:
{"action": "create|complete|delete|reschedule|list|when|clarify|reply",
 "tasks": [{"title": str, "date": "YYYY-MM-DD"|null, "start": "HH:MM"|null, "durationMinutes": int|null,
            "description": str|null, "category": str|null, "priority": 0|1|2|null, "location": str|null,
            "notes": str|null, "recurrence": "NONE|DAILY|WEEKLY|MONTHLY"}],
 "target": {"id": int|null, "title": str|null, "date": "YYYY-MM-DD"|null},
 "date": "YYYY-MM-DD"|null, "start": "HH:MM"|null, "allDay": bool, "text": str}
Regras:
- "create": uma entrada em tasks por compromisso mencionado (ex.: prova + revisão = 2 tarefas). Títulos curtos.
- Calcule datas relativas ("amanhã", "sexta que vem", "daqui a duas semanas") a partir de "now" e do fuso informado.
- NUNCA invente data, horário, local ou duração que o usuário não disse. Campo desconhecido = null.
- Se o dia for ambíguo (ex.: dia do mês não bate com o dia da semana) ou faltar algo essencial que não dá para deduzir, use "clarify" com uma pergunta curta em "text".
- Se a tarefa não tem data, deixe date null (o app pergunta). Se não tem horário, start null.
- "complete"/"delete"/"reschedule": preencha target.id com o id da lista de tarefas fornecida quando tiver certeza; senão target.title.
- "reschedule": novos valores em "date"/"start".
- "when": a mensagem só informa data/hora respondendo a uma pergunta anterior (ver pendingTask/histórico).
- "list": perguntas sobre a agenda; "date" se for um dia específico.
- "reply": conversa sem ação; "text" curto e amigável.`;

async function callAi(input) {
  const user = JSON.stringify({
    now: input.now, weekday: input.weekday, timezone: input.timezone,
    tasks: input.tasks, pendingTask: input.pendingTask, history: input.history, message: input.message,
  });
  const provider = (env.AI_PROVIDER || 'anthropic').toLowerCase();
  const timeout = AbortSignal.timeout(25_000);
  if (provider === 'anthropic') {
    const res = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST', signal: timeout,
      headers: { 'content-type': 'application/json', 'x-api-key': env.AI_API_KEY, 'anthropic-version': '2023-06-01' },
      body: JSON.stringify({
        model: env.AI_MODEL || 'claude-haiku-5-5', max_tokens: 1024, system: SYSTEM_PROMPT,
        messages: [{ role: 'user', content: user }],
      }),
    });
    if (!res.ok) throw new Error(`IA respondeu ${res.status}`);
    const data = await res.json();
    return extractJson(data.content.filter(b => b.type === 'text').map(b => b.text).join(''));
  }
  // any OpenAI-compatible API (OpenAI, Ollama, LM Studio, OpenRouter...)
  const res = await fetch(`${(env.AI_BASE_URL || 'https://api.openai.com/v1').replace(/\/$/, '')}/chat/completions`, {
    method: 'POST', signal: timeout,
    headers: { 'content-type': 'application/json', authorization: `Bearer ${env.AI_API_KEY || ''}` },
    body: JSON.stringify({
      model: env.AI_MODEL, response_format: { type: 'json_object' },
      messages: [{ role: 'system', content: SYSTEM_PROMPT }, { role: 'user', content: user }],
    }),
  });
  if (!res.ok) throw new Error(`IA respondeu ${res.status}`);
  const data = await res.json();
  return extractJson(data.choices[0].message.content);
}

// ---------- telephony (Twilio) ----------

const guard = new CallGuard(Number(env.CALLS_PER_HOUR || 10));
const telephonyConfigured = () => Boolean(env.TWILIO_ACCOUNT_SID && env.TWILIO_AUTH_TOKEN && env.TWILIO_FROM);

async function placeCall(to, taskTitle) {
  const say = `Olá! Aqui é o seu assistente de rotina. Está na hora de: ${taskTitle}. Abra o aplicativo para concluir ou adiar.`;
  const twiml = `<Response><Say language="pt-BR">${xmlEscape(say)}</Say><Pause length="1"/><Say language="pt-BR">${xmlEscape(say)}</Say></Response>`;
  const auth = Buffer.from(`${env.TWILIO_ACCOUNT_SID}:${env.TWILIO_AUTH_TOKEN}`).toString('base64');
  const res = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${env.TWILIO_ACCOUNT_SID}/Calls.json`, {
    method: 'POST', signal: AbortSignal.timeout(15_000),
    headers: { authorization: `Basic ${auth}`, 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ To: to, From: env.TWILIO_FROM, Twiml: twiml }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`Twilio recusou a ligação (${data.message || res.status})`);
  return data.sid;
}

// ---------- HTTP ----------

function send(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}

async function readJson(req) {
  let size = 0; const chunks = [];
  for await (const c of req) { size += c.length; if (size > MAX_BODY) throw new Error('corpo muito grande'); chunks.push(c); }
  return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
}

export const server = http.createServer(async (req, res) => {
  try {
    if (!tokenOk(req.headers.authorization)) return send(res, 401, { error: 'Token do servidor inválido' });

    if (req.method === 'GET' && req.url === '/v1/health')
      return send(res, 200, { ok: true, ai: Boolean(env.AI_API_KEY || env.AI_BASE_URL), telephony: telephonyConfigured() });

    if (req.method === 'POST' && req.url === '/v1/parse') {
      if (!env.AI_API_KEY && !env.AI_BASE_URL) return send(res, 503, { error: 'IA não configurada no servidor' });
      const input = await readJson(req);
      if (typeof input.message !== 'string' || !input.message.trim()) return send(res, 400, { error: 'mensagem vazia' });
      // the app validates every field again before touching data
      return send(res, 200, { command: await callAi(input) });
    }

    if (req.method === 'POST' && req.url === '/v1/call') {
      if (!telephonyConfigured()) return send(res, 503, { error: 'Telefonia não configurada no servidor' });
      const { to, taskTitle, idempotencyKey } = await readJson(req);
      if (!isE164(to)) return send(res, 400, { error: 'Número inválido' });
      const allowed = (env.ALLOWED_NUMBERS || '').split(',').map(s => s.trim()).filter(Boolean);
      if (allowed.length && !allowed.includes(to)) return send(res, 403, { error: 'Número não autorizado neste servidor' });
      if (typeof idempotencyKey !== 'string' || !idempotencyKey) return send(res, 400, { error: 'idempotencyKey ausente' });
      const verdict = guard.check(idempotencyKey);
      if (verdict === 'duplicate') return send(res, 200, { status: 'duplicate' });
      if (verdict === 'rate_limited') return send(res, 429, { error: 'Limite de ligações por hora atingido' });
      const sid = await placeCall(to, String(taskTitle || 'sua tarefa').slice(0, 120));
      return send(res, 200, { status: 'queued', sid });
    }

    send(res, 404, { error: 'não encontrado' });
  } catch (e) {
    console.error(e.message); // no request bodies in logs: they hold personal data
    send(res, 502, { error: e.message || 'erro interno' });
  }
});

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(env.PORT || 8787);
  server.listen(port, () => console.log(`Routine backend on :${port} (AI: ${env.AI_PROVIDER || 'anthropic'}, telephony: ${telephonyConfigured()})`));
}
