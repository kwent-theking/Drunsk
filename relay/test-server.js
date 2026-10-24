'use strict';
/**
 * Drunsk protocol smoke server (no Minecraft, no database, no file reads).
 * Тестовый server-side компонент: проверяет безопасный handshake клиента Drunsk,
 * фиксирует только заявленный Sodium presence и возвращает результат чата.
 */
const http = require('http');
const crypto = require('crypto');
const { WebSocketServer } = require('ws');

const HOST = process.env.DRUNSK_TEST_HOST || '127.0.0.1';
const PORT = Number.parseInt(process.env.DRUNSK_TEST_PORT || '8791', 10);
const PROTOCOL = 'drunsk-test/1';
const MAX_TEXT = 400;
const MAX_PAYLOAD = 16 * 1024;
const sessions = new Map();
const nonceFor = () => crypto.randomBytes(16).toString('base64url');
const validNick = n => typeof n === 'string' && /^[A-Za-z0-9_]{1,16}$/.test(n);
const validVersion = v => typeof v === 'string' && v.length <= 32 && /^[A-Za-z0-9._+\- ]+$/.test(v);
function send(ws, value) { if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(value)); }
function fail(ws, id, reason) { send(ws, { id, ok: false, reason }); }
function ok(ws, id, value) { send(ws, { id, ok: true, ...value }); }

const server = http.createServer((req, res) => {
  if (req.url === '/health') { res.writeHead(200, { 'content-type': 'text/plain' }); res.end('ok'); return; }
  res.writeHead(404); res.end();
});
const wss = new WebSocketServer({ server, path: '/drunsk-test', maxPayload: MAX_PAYLOAD });

wss.on('connection', ws => {
  const challenge = nonceFor();
  let session = null;
  send(ws, { type: 'hello_challenge', protocol: PROTOCOL, challenge });

  ws.on('message', raw => {
    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { return fail(ws, null, 'invalid_json'); }
    if (!msg || typeof msg !== 'object' || Array.isArray(msg)) return fail(ws, msg && msg.id, 'invalid_message');

    if (msg.type === 'hello') {
      if (session) return fail(ws, msg.id, 'already_handshaken');
      const nick = msg.mcNick;
      const clientVersion = msg.drunskClientVersion;
      const mcVersion = msg.mcVersion;
      const sodium = msg.sodium;
      if (!validNick(nick) || !validVersion(clientVersion) || mcVersion !== '26.2') return fail(ws, msg.id, 'unsupported_client');
      if (!sodium || typeof sodium !== 'object' || typeof sodium.present !== 'boolean') return fail(ws, msg.id, 'missing_sodium_presence');
      if (sodium.version !== null && sodium.version !== undefined && !validVersion(sodium.version)) return fail(ws, msg.id, 'bad_sodium_version');
      // The challenge is echoed, but no secret is accepted from the client: this is
      // a local protocol smoke test, not authentication. Production auth stays in server.js.
      if (msg.challenge !== challenge) return fail(ws, msg.id, 'bad_challenge');
      session = { id: crypto.randomUUID(), nick, sodiumPresent: sodium.present, sodiumVersion: sodium.version || null };
      sessions.set(session.id, session);
      return ok(ws, msg.id, {
        type: 'handshake_result', protocol: PROTOCOL, sessionId: session.id,
        mcVersion: '26.2', sodium: { present: session.sodiumPresent, version: session.sodiumVersion },
        message: `Drunsk handshake OK: Sodium ${session.sodiumPresent ? 'present' : 'missing'}`,
      });
    }
    if (msg.type === 'ping') return ok(ws, msg.id, { type: 'pong', t: msg.t ?? null });
    if (msg.type === 'chat_send') {
      if (!session) return fail(ws, msg.id, 'handshake_required');
      if (typeof msg.text !== 'string') return fail(ws, msg.id, 'bad_text');
      const text = msg.text.trim();
      if (!text || text.length > MAX_TEXT) return fail(ws, msg.id, 'bad_text');
      return ok(ws, msg.id, {
        type: 'chat_result', accepted: true, from: session.nick, text,
        result: `Тестовый чат: ${session.nick}: ${text}`,
        sodiumPresent: session.sodiumPresent,
      });
    }
    return fail(ws, msg.id, 'unknown_type');
  });
  ws.on('close', () => { if (session) sessions.delete(session.id); });
});

server.listen(PORT, HOST, () => console.log(`Drunsk test server listening on ws://${HOST}:${PORT}/drunsk-test`));
process.on('SIGTERM', () => server.close(() => process.exit(0)));
