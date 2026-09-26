'use strict';
// SearXNG gate: Anthropic Messages-compatible front for SearXNG.
// Прослойка: отдаёт SearXNG наружу в формате Anthropic Messages API.
//
// Why not raw SearXNG JSON: the DSH web_search provider POSTs
// {baseURL}/messages with the native web_search_20250305 tool and requires
// web_search_tool_result blocks in the reply (verified against
// dsh-web-search-deepseek/lib/index.js). Raw /search?format=json cannot be
// consumed by it, and publishing SearXNG would hand out a free search proxy.
// Почему не сырой JSON: провайдер web_search в DSH шлёт POST {baseURL}/messages
// с нативным инструментом web_search_20250305 и требует блоки
// web_search_tool_result; сырой /search?format=json ему не подходит, а
// публиковать SearXNG — значит раздать бесплатный поисковый прокси.

const http = require('http');
const crypto = require('crypto');

const KEY = process.env.GATE_KEY || '';
const SEARXNG_URL = (process.env.SEARXNG_URL || 'http://searxng-core:8091').replace(/\/$/, '');
const PORT = parseInt(process.env.GATE_PORT || '8092', 10);
const RATE_PER_MIN = parseInt(process.env.GATE_RATE_PER_MIN || '120', 10);
const MAX_RESULTS = parseInt(process.env.GATE_MAX_RESULTS || '12', 10);
const TIMEOUT_MS = parseInt(process.env.GATE_TIMEOUT_MS || '12000', 10);

if (!KEY || KEY.length < 32) {
  console.error('GATE_KEY missing or shorter than 32 chars; refusing to start');
  process.exit(1);
}

const log = (...a) => console.log(new Date().toISOString(), ...a);

// --- rate limit: sliding window per key ------------------------------------
const hits = new Map();
function rateLimited(key) {
  const now = Date.now();
  const window = hits.get(key) || [];
  const fresh = window.filter(t => now - t < 60_000);
  fresh.push(now);
  hits.set(key, fresh);
  return fresh.length > RATE_PER_MIN;
}
setInterval(() => {
  const now = Date.now();
  for (const [k, v] of hits) {
    const fresh = v.filter(t => now - t < 60_000);
    if (fresh.length) hits.set(k, fresh); else hits.delete(k);
  }
}, 120_000).unref();

function sendJson(res, status, body) {
  const buf = Buffer.from(JSON.stringify(body));
  res.writeHead(status, {
    'content-type': 'application/json',
    'content-length': buf.length,
    'cache-control': 'no-store',
  });
  res.end(buf);
}

// DSH surfaces error.error.message, so failures use the Anthropic error shape.
function sendError(res, status, type, message) {
  sendJson(res, status, { type: 'error', error: { type, message } });
}

function apiKeyOf(req) {
  const h = req.headers['x-api-key'];
  if (typeof h === 'string' && h.length) return h.trim();
  const auth = req.headers['authorization'];
  if (typeof auth === 'string' && auth.toLowerCase().startsWith('bearer ')) return auth.slice(7).trim();
  return '';
}

function readBody(req, limit = 256 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', c => {
      size += c.length;
      if (size > limit) { reject(new Error('body_too_large')); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

/** The provider wraps the query in a fixed sentence; fall back to raw user text. */
function queryOf(body) {
  const messages = Array.isArray(body.messages) ? body.messages : [];
  const texts = [];
  for (const m of messages) {
    if (m && m.role !== 'user') continue;
    if (typeof m?.content === 'string') texts.push(m.content);
    else if (Array.isArray(m?.content)) {
      for (const b of m.content) if (b && b.type === 'text' && typeof b.text === 'string') texts.push(b.text);
    }
  }
  const joined = texts.join('\n');
  const marker = 'Perform a web search for the query:';
  const at = joined.lastIndexOf(marker);
  const query = (at >= 0 ? joined.slice(at + marker.length) : joined).trim();
  return query.slice(0, 400);
}

async function searx(query, signal) {
  const url = `${SEARXNG_URL}/search?q=${encodeURIComponent(query)}&format=json&pageno=1`;
  // SearXNG's botdetection wants a client IP even with the limiter off; the
  // gate is the only caller, so a fixed internal address is fine.
  // botdetection SearXNG хочет IP клиента даже при выключенном limiter; гейт —
  // единственный вызывающий, фиксированного внутреннего адреса достаточно.
  const res = await fetch(url, {
    signal,
    // manual: a redirect here means SearXNG misconfiguration (e.g. base_url);
    // following it would loop through Caddy back into the gate.
    // manual: редирект означает рассинхрон конфига SearXNG (например base_url) —
    // переход по нему ушёл бы через Caddy обратно в гейт.
    redirect: 'manual',
    headers: { accept: 'application/json', 'x-forwarded-for': '127.0.0.1' },
  });
  if (res.status >= 300 && res.status < 400) {
    throw Object.assign(new Error(`searxng unexpected redirect ${res.status} -> ${res.headers.get('location')}`), { status: 502 });
  }
  if (!res.ok) throw Object.assign(new Error(`searxng HTTP ${res.status}`), { status: 502 });
  return res.json();
}

/** Normalize SearXNG hits into Anthropic web_search_result items + citations. */
function toBlocks(json) {
  const results = Array.isArray(json?.results) ? json.results : [];
  const seen = new Set();
  const items = [];
  const citations = [];
  for (const r of results) {
    if (items.length >= MAX_RESULTS) break;
    const url = typeof r?.url === 'string' ? r.url.trim() : '';
    if (!url || seen.has(url)) continue;
    seen.add(url);
    const item = { type: 'web_search_result', url };
    const title = typeof r.title === 'string' ? r.title.trim() : '';
    if (title) item.title = title;
    // page_age maps to publishedAt in the provider; keep it a non-empty string.
    const date = typeof r.publishedDate === 'string' ? r.publishedDate.trim() : '';
    if (date) item.page_age = date;
    items.push(item);
    const snippet = typeof r.content === 'string' ? r.content.trim().slice(0, 600) : '';
    if (snippet) citations.push({ url, cited_text: snippet });
  }
  const answer = typeof json?.answer === 'string' && json.answer.trim() ? json.answer.trim() : '';
  const summary = answer
    || `${items.length} result(s) from SearXNG. Snippets are attached as citations; fetch a URL for full text.`;
  return [
    { type: 'text', text: summary, ...(citations.length ? { citations } : {}) },
    { type: 'web_search_tool_result', tool_use_id: 'drunsk_searxng', content: items },
  ];
}

function messageEnvelope(blocks, model) {
  return {
    id: 'msg_' + crypto.randomBytes(12).toString('hex'),
    type: 'message',
    role: 'assistant',
    model: model || 'searxng-gate',
    content: blocks,
    stop_reason: 'end_turn',
    stop_sequence: null,
    usage: { input_tokens: 1, output_tokens: 1 },
  };
}

const server = http.createServer(async (req, res) => {
  const url = req.url.split('?')[0];

  if (req.method === 'GET' && url.endsWith('/health')) {
    return sendJson(res, 200, { ok: true, upstream: SEARXNG_URL, ratePerMin: RATE_PER_MIN });
  }

  if (req.method !== 'POST' || !url.endsWith('/messages')) {
    return sendError(res, 404, 'not_found_error', `no route for ${req.method} ${url}`);
  }

  const key = apiKeyOf(req);
  // Constant-time compare; an empty key must not short-circuit into a match.
  const given = Buffer.from(key || '');
  const want = Buffer.from(KEY);
  if (given.length !== want.length || !crypto.timingSafeEqual(given, want)) {
    return sendError(res, 401, 'authentication_error', 'invalid api key');
  }
  if (rateLimited(key)) {
    return sendError(res, 429, 'rate_limit_error', `more than ${RATE_PER_MIN} searches/min`);
  }

  let body;
  try {
    body = JSON.parse(await readBody(req));
  } catch (e) {
    return sendError(res, 400, 'invalid_request_error', 'body is not valid JSON');
  }
  if (body?.stream === true) {
    return sendError(res, 400, 'invalid_request_error', 'streaming is not supported by this gate');
  }

  const query = queryOf(body);
  if (!query) {
    return sendError(res, 400, 'invalid_request_error', 'no query found in messages');
  }

  try {
    const json = await searx(query, AbortSignal.timeout(TIMEOUT_MS));
    const blocks = toBlocks(json);
    log('search', JSON.stringify(query), '->', blocks[1].content.length, 'results');
    sendJson(res, 200, messageEnvelope(blocks, body.model));
  } catch (e) {
    const msg = e?.name === 'TimeoutError' ? `searxng timeout after ${TIMEOUT_MS}ms` : String(e?.message || e);
    log('search failed', JSON.stringify(query), msg);
    sendError(res, e?.status || 502, 'api_error', msg);
  }
});

server.listen(PORT, '0.0.0.0', () => log(`gate listening on :${PORT}, upstream ${SEARXNG_URL}`));
