'use strict';
// Drunsk relay / Ретранслятор Drunsk
// Virtual passports + shared-economy bridge to Drunbot's MySQL (s1_okak) + transfers + casino.
// Виртуальные паспорта + мост к общей экономике Друнбота (MySQL s1_okak) + переводы + лудка.
//
// Listens ONLY on 127.0.0.1:8790, exposed as wss://<domain>/drunsk through Caddy.
// Слушает ТОЛЬКО 127.0.0.1:8790, наружу — wss://<домен>/drunsk через Caddy.
//
// Money lives in the bot's `users` table (atomic UPDATE ... balance = balance + ?),
// so a transfer made from Minecraft and one made from Discord never race destructively.
// Деньги живут в таблице бота `users` (атомарный UPDATE), перевод из Minecraft
// и перевод из Discord не затирают друг друга.

const http = require('http');
const crypto = require('crypto');
const { WebSocketServer } = require('ws');
const mysql = require('mysql2/promise');

// --- config / настройка ---------------------------------------------------
const PORT = parseInt(process.env.DRUNSK_PORT || '8790', 10);
const HOST = process.env.DRUNSK_HOST || '127.0.0.1';
const GUILD_ID = process.env.DRUNSK_GUILD_ID || '1534856710739595265';
const OWNER_IDS = (process.env.DRUNSK_OWNERS || '1365702494138793986,1533644237210517586')
  .split(',').map(s => s.trim()).filter(Boolean);
const CURRENCY = process.env.DRUNSK_CURRENCY || 'чекушек';
const DB = {
  host: process.env.DB_HOST || '127.0.0.1',
  port: parseInt(process.env.DB_PORT || '3306', 10),
  user: process.env.DB_USER,
  password: process.env.DB_PASS,
  database: process.env.DB_NAME || 's1_okak',
  waitForConnections: true,
  connectionLimit: 8,
  enableKeepAlive: true,
  // Discord IDs are 64-bit: as JS doubles they lose precision
  // (…793986 -> …794000). Strings only. / Discord ID 64-битные:
  // как JS-double теряют точность. Только строки.
  supportBigNumbers: true,
  bigNumberStrings: true,
};
const MAX_MSG_BYTES = 64 * 1024;
const PAIR_CODE_TTL_MS = 10 * 60 * 1000;   // 10 minutes / 10 минут
const PAIR_POLL_MS = 1500;                  // poll for bot confirmations / опрос подтверждений бота
const HEARTBEAT_MS = 30 * 1000;
// Private clan relay, so the limit is generous; it only exists to keep one
// buggy client from melting the DB. The limited response MUST carry the
// request id, otherwise the mod waits for an answer forever.
// Приватный клановый релей: лимит щедрый, он только от сломанного клиента.
// Ответ об ограничении ОБЯЗАН нести id запроса, иначе мод ждёт вечно.
const RATE_LIMIT_PER_SEC = 30;

const log = (...a) => console.log(new Date().toISOString(), ...a);

// --- db --------------------------------------------------------------------
const pool = mysql.createPool(DB);

async function ensureSchema() {
  // Only our own drunsk_* tables get DDL: the bot holds metadata locks on its
  // tables, DDL there would hang until the bot restarts (learned the hard way).
  // DDL только по своим drunsk_*: на таблицах бота висит metadata lock.
  await pool.query(`CREATE TABLE IF NOT EXISTS drunsk_passports (
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    mc_nick VARCHAR(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, user_id),
    UNIQUE KEY mc_nick (guild_id, mc_nick)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  // Код одноразовый и живёт 10 минут; в БД лежит его sha256 (бот хэширует
  // введённое, чтобы никто не мог перебрать коды и угнать привязку).
  await pool.query(`CREATE TABLE IF NOT EXISTS drunsk_pair_codes (
    code_hash CHAR(64) NOT NULL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    mc_nick VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    user_id BIGINT NULL,
    confirmed_at BIGINT NULL,
    KEY by_guild_pending (guild_id, user_id, confirmed_at)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  await pool.query(`CREATE TABLE IF NOT EXISTS drunsk_tx (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    from_user BIGINT NOT NULL,
    to_user BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    detail VARCHAR(64) NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    KEY by_from (guild_id, from_user, id),
    KEY by_to (guild_id, to_user, id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  await pool.query(`CREATE TABLE IF NOT EXISTS drunsk_msg (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    from_user BIGINT NOT NULL,
    to_user BIGINT NOT NULL,
    body VARCHAR(400) NOT NULL,
    created_at BIGINT NOT NULL,
    KEY by_thread (guild_id, from_user, to_user, id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  await pool.query(`CREATE TABLE IF NOT EXISTS drunsk_chat (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    from_user BIGINT NOT NULL,
    body VARCHAR(400) NOT NULL,
    created_at BIGINT NOT NULL,
    KEY by_time (guild_id, id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
}

// --- helpers ---------------------------------------------------------------
const sha256 = s => crypto.createHash('sha256').update(s).digest('hex');
const randToken = () => crypto.randomBytes(32).toString('hex');
const randCode = () => String(crypto.randomInt(100000, 1000000)); // 6 digits
const isOwner = userId => OWNER_IDS.includes(String(userId));
const validNick = n => typeof n === 'string' && /^[A-Za-z0-9_]{1,16}$/.test(n);
const validAmount = a => Number.isInteger(a) && a > 0 && a <= 1e12;

function send(ws, obj) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj));
}
function fail(ws, id, reason) { send(ws, { id, ok: false, reason }); }
function ok(ws, id, data) { send(ws, { id, ok: true, ...(data || {}) }); }

async function getBalance(guildId, userId) {
  const [rows] = await pool.query(
    'SELECT balance, hide_balance, is_hidden FROM users WHERE guild_id = ? AND user_id = ?',
    [guildId, userId]);
  if (rows.length === 0) {
    // mirror bot behaviour: lazily create the row / как у бота: лениво создаём строку
    await pool.query(
      'INSERT IGNORE INTO users (guild_id, user_id, balance, bought_custom, is_hidden) VALUES (?, ?, 0, 0, 0)',
      [guildId, userId]);
    return { balance: 0, hide_balance: 0, is_hidden: 0 };
  }
  return rows[0];
}

async function discordName(userId) {
  const [rows] = await pool.query(
    'SELECT name FROM user_names WHERE user_id = ? ORDER BY updated_at DESC LIMIT 1', [userId]);
  return rows.length ? rows[0].name : null;
}

async function nickToPassport(mcNick) {
  const [rows] = await pool.query(
    'SELECT guild_id, user_id, mc_nick, created_at FROM drunsk_passports WHERE guild_id = ? AND mc_nick = ?',
    [GUILD_ID, mcNick]);
  return rows.length ? rows[0] : null;
}

// --- pairing / привязка ----------------------------------------------------
// Mod asks for a code, player types !паспорт <код> in Discord. The bot patch
// writes user_id into drunsk_pair_codes; we poll and push `paired` to the mod.
// Мод просит код, игрок пишет !паспорт <код> в Discord. Патч бота проставляет
// user_id; мы опрашиваем таблицу и пушим `paired` моду.
const waitingPairs = new Map(); // mcNick(lower) -> { ws, token, code, expiresAt }

async function handlePairRequest(ws, msg) {
  const nick = msg.mcNick;
  if (!validNick(nick)) return fail(ws, msg.id, 'bad_nick');
  const existing = await nickToPassport(nick);
  if (existing) {
    // already paired; the mod should have a token — refuse re-pair silently
    // уже привязан; повторная привязка только через !паспорт сброс
    return fail(ws, msg.id, 'already_paired');
  }
  const code = randCode();
  const token = randToken();
  const now = Date.now();
  // Бот при `!паспорт <код>` считает sha256(код) и проставляет user_id в строку
  // с таким code_hash. Хэш кода без ника — бот не знает ник и не должен.
  await pool.query(
    'INSERT INTO drunsk_pair_codes (code_hash, guild_id, mc_nick, created_at, expires_at) VALUES (?, ?, ?, ?, ?) ' +
    'ON DUPLICATE KEY UPDATE mc_nick = VALUES(mc_nick), created_at = VALUES(created_at), expires_at = VALUES(expires_at), user_id = NULL, confirmed_at = NULL',
    [sha256(code), GUILD_ID, nick, now, now + PAIR_CODE_TTL_MS]);
  // one pending pair per nick / одна ожидающая привязка на ник
  waitingPairs.set(nick.toLowerCase(), { ws, token, code, mcNick: nick, expiresAt: now + PAIR_CODE_TTL_MS });
  ok(ws, msg.id, { code, expiresInMs: PAIR_CODE_TTL_MS, currency: CURRENCY });
}

async function pollPairConfirmations() {
  if (waitingPairs.size === 0) return;
  let rows;
  try {
    [rows] = await pool.query(
      'SELECT code_hash, mc_nick, user_id FROM drunsk_pair_codes WHERE guild_id = ? AND user_id IS NOT NULL AND confirmed_at IS NULL LIMIT 20',
      [GUILD_ID]);
  } catch (e) { log('pair poll error', e.message); return; }
  for (const row of rows) {
    const key = String(row.mc_nick).toLowerCase();
    const waiting = waitingPairs.get(key);
    if (waiting && sha256(waiting.code) === row.code_hash) {
      const now = Date.now();
      try {
        await pool.query(
          'INSERT INTO drunsk_passports (guild_id, user_id, mc_nick, token_hash, created_at, updated_at) ' +
          'VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), token_hash = VALUES(token_hash), updated_at = VALUES(updated_at)',
          [GUILD_ID, row.user_id, waiting.mcNick, sha256(waiting.token), now, now]);
        await pool.query('UPDATE drunsk_pair_codes SET confirmed_at = ? WHERE code_hash = ?', [now, row.code_hash]);
        send(waiting.ws, { type: 'paired', token: waiting.token, userId: String(row.user_id) });
        log('paired', waiting.mcNick, '->', String(row.user_id));
      } catch (e) {
        send(waiting.ws, { type: 'error', reason: 'db_error', detail: e.message });
      }
      waitingPairs.delete(key);
    } else if (waiting && Date.now() > waiting.expiresAt) {
      send(waiting.ws, { type: 'pair_expired' });
      waitingPairs.delete(key);
    }
  }
  // evict expired waiters / чистим протухших
  for (const [key, w] of waitingPairs) {
    if (Date.now() > w.expiresAt) { send(w.ws, { type: 'pair_expired' }); waitingPairs.delete(key); }
  }
}

// --- handlers / обработчики ------------------------------------------------
async function handleMe(ws, msg, sess) {
  const u = await getBalance(GUILD_ID, sess.userId);
  const name = await discordName(sess.userId);
  ok(ws, msg.id, {
    userId: String(sess.userId), mcNick: sess.mcNick, discordName: name,
    balance: Number(u.balance), since: sess.since, owner: isOwner(sess.userId),
    currency: CURRENCY,
  });
}

async function handleList(ws, msg, sess) {
  const mine = isOwner(sess.userId);
  const [rows] = await pool.query(
    `SELECT p.user_id, p.mc_nick, p.created_at, u.balance, u.hide_balance, u.is_hidden
     FROM drunsk_passports p LEFT JOIN users u ON u.guild_id = p.guild_id AND u.user_id = p.user_id
     WHERE p.guild_id = ? ORDER BY p.mc_nick LIMIT 500`, [GUILD_ID]);
  const names = new Map();
  if (rows.length) {
    const [un] = await pool.query(
      `SELECT n.user_id, n.name FROM user_names n JOIN (
         SELECT user_id, MAX(updated_at) m FROM user_names GROUP BY user_id
       ) t ON t.user_id = n.user_id AND t.m = n.updated_at WHERE n.user_id IN (${rows.map(() => '?').join(',')})`,
      rows.map(r => r.user_id));
    for (const r of un) names.set(String(r.user_id), r.name);
  }
  const out = rows
    .filter(r => mine || !r.is_hidden)
    .map(r => {
      const hidden = !mine && !!r.hide_balance;
      return {
        userId: String(r.user_id), mcNick: r.mc_nick,
        discordName: names.get(String(r.user_id)) || null,
        balance: hidden ? null : Number(r.balance || 0),
        since: Number(r.created_at), hidden,
        online: onlineNicks.has(r.mc_nick.toLowerCase()),
      };
    });
  ok(ws, msg.id, { passports: out });
}

async function handlePassport(ws, msg, sess) {
  const target = validNick(msg.mcNick) ? msg.mcNick : sess.mcNick;
  const p = await nickToPassport(target);
  if (!p) return fail(ws, msg.id, 'no_passport');
  const u = await getBalance(GUILD_ID, p.user_id);
  const mine = isOwner(sess.userId) || String(sess.userId) === String(p.user_id);
  if (!mine && u.is_hidden) return fail(ws, msg.id, 'hidden');
  const name = await discordName(p.user_id);
  ok(ws, msg.id, {
    userId: String(p.user_id), mcNick: p.mc_nick, discordName: name,
    balance: (!mine && u.hide_balance) ? null : Number(u.balance),
    since: Number(p.created_at), owner: isOwner(p.user_id),
    online: onlineNicks.has(p.mc_nick.toLowerCase()),
  });
}

async function handleTransfer(ws, msg, sess) {
  const toNick = msg.toNick;
  const amount = msg.amount;
  if (!validNick(toNick)) return fail(ws, msg.id, 'bad_nick');
  if (!validAmount(amount)) return fail(ws, msg.id, 'bad_amount');
  if (toNick.toLowerCase() === sess.mcNick.toLowerCase()) return fail(ws, msg.id, 'self_transfer');
  const target = await nickToPassport(toNick);
  if (!target) return fail(ws, msg.id, 'no_passport');
  if (String(target.user_id) === String(sess.userId)) return fail(ws, msg.id, 'self_transfer');

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const [rows] = await conn.query(
      'SELECT balance FROM users WHERE guild_id = ? AND user_id = ? FOR UPDATE',
      [GUILD_ID, sess.userId]);
    const bal = rows.length ? Number(rows[0].balance) : 0;
    if (bal < amount) { await conn.rollback(); return fail(ws, msg.id, 'not_enough'); }
    // ensure the receiver's row exists INSIDE this connection: calling
    // getBalance() here would grab a second pooled connection per transaction
    // and starve the pool under concurrency (10 parallel transfers deadlocked
    // with limit 8).
    // строка получателя заводится ВНУТРИ этого соединения: getBalance() здесь
    // взял бы второе соединение из пула на транзакцию и при параллельных
    // переводах исчерпал бы пул (10 переводов вешали всё при лимите 8).
    await conn.query(
      'INSERT IGNORE INTO users (guild_id, user_id, balance, bought_custom, is_hidden) VALUES (?, ?, 0, 0, 0)',
      [GUILD_ID, target.user_id]);
    await conn.query('UPDATE users SET balance = balance - ? WHERE guild_id = ? AND user_id = ?',
      [amount, GUILD_ID, sess.userId]);
    await conn.query('UPDATE users SET balance = balance + ? WHERE guild_id = ? AND user_id = ?',
      [amount, GUILD_ID, target.user_id]);
    await conn.query('INSERT INTO drunsk_tx (guild_id, from_user, to_user, amount, kind, detail, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
      [GUILD_ID, sess.userId, target.user_id, amount, 'transfer', '', Date.now()]);
    // Read the new balance INSIDE the transaction: after commit, pool.query
    // would need a second connection while this one is still held, and N
    // concurrent transfers would deadlock the pool (each holds one, waits for
    // a free one). / Новый баланс читаем ВНУТРИ транзакции: после commit
    // pool.query потребовал бы второе соединение при занятом первом — N
    // параллельных переводов намертво вешали пул.
    const [nb] = await conn.query('SELECT balance FROM users WHERE guild_id = ? AND user_id = ?',
      [GUILD_ID, sess.userId]);
    await conn.commit();
    ok(ws, msg.id, { to: target.mc_nick, amount, newBalance: Number(nb[0].balance) });
    notifyBalance(target.user_id);
  } catch (e) {
    await conn.rollback();
    fail(ws, msg.id, 'db_error');
    log('transfer error', e.message);
  } finally {
    conn.release();
  }
}

// --- casino / лудка --------------------------------------------------------
// Server-authoritative: the roll happens here, money moves in one transaction.
// Сервер крутит всё сам: бросок здесь, деньги в одной транзакции.
const CASINO = {
  coin: { min: 1, max: 100000, winChance: 0.48, multiplier: 2 },
  dice: { min: 1, max: 100000, winChance: 0.49, multiplier: 2 },
};

async function handleCasino(ws, msg, sess) {
  const game = msg.game;
  const bet = msg.bet;
  if (!CASINO[game] && game !== 'roulette') return fail(ws, msg.id, 'bad_game');
  if (!validAmount(bet)) return fail(ws, msg.id, 'bad_amount');
  const cfg = game === 'roulette' ? { min: 1, max: 100000 } : CASINO[game];
  if (bet < cfg.min || bet > cfg.max) return fail(ws, msg.id, 'bad_bet');

  let win = false, payout = 0, detail = '';
  if (game === 'coin') {
    win = crypto.randomInt(0, 1000) < 480;
    detail = win ? 'heads' : 'tails';
    payout = win ? bet * 2 : 0;
  } else if (game === 'dice') {
    const pick = msg.pick === 'under' ? 'under' : 'over';
    let roll;
    do { roll = crypto.randomInt(1, 101); } while (roll === 50); // no push / без ничьей
    win = pick === 'over' ? roll > 50 : roll < 50;
    detail = String(roll) + ':' + pick;
    payout = win ? bet * 2 : 0;
  } else {
    // roulette: 0..14, 0 green x14; red = odd 1..13, black = even 2..14 → x2
    const spin = crypto.randomInt(0, 15);
    const pick = msg.pick;
    let mult = 0;
    if (Number.isInteger(pick) && pick >= 0 && pick <= 14 && pick === spin) mult = 14;
    else if (pick === 'red' && spin !== 0 && spin % 2 === 1) mult = 2;
    else if (pick === 'black' && spin !== 0 && spin % 2 === 0) mult = 2;
    win = mult > 0;
    detail = 'spin=' + spin + ',pick=' + pick;
    payout = bet * mult;
  }

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const [rows] = await conn.query(
      'SELECT balance FROM users WHERE guild_id = ? AND user_id = ? FOR UPDATE',
      [GUILD_ID, sess.userId]);
    const bal = rows.length ? Number(rows[0].balance) : 0;
    if (bal < bet) { await conn.rollback(); return fail(ws, msg.id, 'not_enough'); }
    const delta = payout - bet;
    if (delta !== 0) {
      await conn.query('UPDATE users SET balance = balance + ? WHERE guild_id = ? AND user_id = ?',
        [delta, GUILD_ID, sess.userId]);
    }
    await conn.query('INSERT INTO drunsk_tx (guild_id, from_user, to_user, amount, kind, detail, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
      [GUILD_ID, sess.userId, sess.userId, delta, game, detail.slice(0, 64), Date.now()]);
    // balance read inside the transaction — same pool-starvation reason as
    // in handleTransfer / баланс читаем внутри транзакции — та же причина,
    // что в handleTransfer
    const [nb] = await conn.query('SELECT balance FROM users WHERE guild_id = ? AND user_id = ?',
      [GUILD_ID, sess.userId]);
    await conn.commit();
    ok(ws, msg.id, {
      game, bet, win, payout, detail,
      newBalance: Number(nb[0].balance),
    });
  } catch (e) {
    await conn.rollback();
    fail(ws, msg.id, 'db_error');
    log('casino error', e.message);
  } finally {
    conn.release();
  }
}

async function handleHistory(ws, msg, sess) {
  const limit = Number.isInteger(msg.limit) ? Math.min(Math.max(msg.limit, 1), 50) : 20;
  const [rows] = await pool.query(
    `SELECT t.from_user, t.to_user, t.amount, t.kind, t.detail, t.created_at,
            pf.mc_nick from_nick, pt.mc_nick to_nick
     FROM drunsk_tx t
     LEFT JOIN drunsk_passports pf ON pf.guild_id = t.guild_id AND pf.user_id = t.from_user
     LEFT JOIN drunsk_passports pt ON pt.guild_id = t.guild_id AND pt.user_id = t.to_user
     WHERE t.guild_id = ? AND (t.from_user = ? OR t.to_user = ?)
     ORDER BY t.id DESC LIMIT ${limit}`, [GUILD_ID, sess.userId, sess.userId]);
  ok(ws, msg.id, {
    history: rows.map(r => ({
      from: r.from_nick || String(r.from_user), to: r.to_nick || String(r.to_user),
      amount: Number(r.amount), kind: r.kind, detail: r.detail, at: Number(r.created_at),
      mine: String(r.from_user) === String(sess.userId),
    })),
  });
}

// --- DM / ЛС -----------------------------------------------------------------
// In-game messages between clan members. Stored in drunsk_msg (100 last per
// thread are served), pushed live to the recipient's sockets.
// Сообщения между друнами из игры. Хранятся в drunsk_msg (последние 100 на
// тред), получателю доставляются пушем.
const DM_MAX_LEN = 400;
const DM_HISTORY_LIMIT = 100;

function pushDm(toUserId, payload) {
  const set = userSockets.get(String(toUserId));
  if (!set) return;
  for (const s of set) send(s, { type: 'dm', ...payload });
}

function isMuted(nick) {
  const exp = mutedNicks.get(nick.toLowerCase());
  if (exp && exp > Date.now()) return true;
  if (exp) mutedNicks.delete(nick.toLowerCase());
  return false;
}

async function handleDmSend(ws, msg, sess) {
  const toNick = msg.to;
  const text = typeof msg.text === 'string' ? msg.text.trim() : '';
  if (!validNick(toNick)) return fail(ws, msg.id, 'bad_nick');
  if (isMuted(sess.mcNick)) return fail(ws, msg.id, 'muted');
  if (!text) return fail(ws, msg.id, 'bad_text');
  if (text.length > DM_MAX_LEN) return fail(ws, msg.id, 'bad_text');
  if (toNick.toLowerCase() === sess.mcNick.toLowerCase()) return fail(ws, msg.id, 'self_dm');
  const target = await nickToPassport(toNick);
  if (!target) return fail(ws, msg.id, 'no_passport');
  if (String(target.user_id) === String(sess.userId)) return fail(ws, msg.id, 'self_dm');

  const now = Date.now();
  const body = text.slice(0, DM_MAX_LEN);
  await pool.query(
    'INSERT INTO drunsk_msg (guild_id, from_user, to_user, body, created_at) VALUES (?, ?, ?, ?, ?)',
    [GUILD_ID, sess.userId, target.user_id, body, now]);

  const payload = { from: sess.mcNick, to: target.mc_nick, text: body, at: now };
  pushDm(target.user_id, payload);
  // echo back to own other sockets / эхо на свои остальные сокеты
  const mine = userSockets.get(String(sess.userId));
  if (mine) for (const s of mine) if (s !== ws) send(s, { type: 'dm', ...payload });
  ok(ws, msg.id, { at: now });
}

async function handleDmHistory(ws, msg, sess) {
  const peerNick = msg.peer;
  if (!validNick(peerNick)) return fail(ws, msg.id, 'bad_nick');
  const peer = await nickToPassport(peerNick);
  if (!peer) return fail(ws, msg.id, 'no_passport');
  const [rows] = await pool.query(
    `SELECT from_user, to_user, body, created_at FROM drunsk_msg
     WHERE guild_id = ? AND ((from_user = ? AND to_user = ?) OR (from_user = ? AND to_user = ?))
     ORDER BY id DESC LIMIT ${DM_HISTORY_LIMIT}`,
    [GUILD_ID, sess.userId, peer.user_id, peer.user_id, sess.userId]);
  rows.reverse(); // oldest first / старые первыми
  ok(ws, msg.id, {
    messages: rows.map(r => ({
      from: String(r.from_user) === String(sess.userId) ? sess.mcNick : peer.mc_nick,
      to: String(r.to_user) === String(sess.userId) ? sess.mcNick : peer.mc_nick,
      text: r.body,
      at: Number(r.created_at),
    })),
  });
}

// --- global chat / общий чат -------------------------------------------------
// Messages visible to all online clan members. Stored in drunsk_chat (last 50).
// Сообщения видимы всем онлайн-друзьям. Хранятся в drunsk_chat (последние 50).
const CHAT_HISTORY_LIMIT = 50;

function broadcastChat(payload) {
  for (const client of wss.clients) {
    if (client.readyState === client.OPEN) send(client, { type: 'chat', ...payload });
  }
}

async function handleChatSend(ws, msg, sess) {
  const text = typeof msg.text === 'string' ? msg.text.trim() : '';
  if (isMuted(sess.mcNick)) return fail(ws, msg.id, 'muted');
  if (!text) return fail(ws, msg.id, 'bad_text');
  if (text.length > 400) return fail(ws, msg.id, 'bad_text');
  const now = Date.now();
  const body = text.slice(0, 400);
  await pool.query(
    'INSERT INTO drunsk_chat (guild_id, from_user, body, created_at) VALUES (?, ?, ?, ?)',
    [GUILD_ID, sess.userId, body, now]);
  const payload = { from: sess.mcNick, text: body, at: now };
  broadcastChat(payload);
  ok(ws, msg.id, { at: now });
}

async function handleChatHistory(ws, msg, sess) {
  const [rows] = await pool.query(
    `SELECT c.from_user, c.body, c.created_at, p.mc_nick
     FROM drunsk_chat c LEFT JOIN drunsk_passports p
       ON p.guild_id = c.guild_id AND p.user_id = c.from_user
     WHERE c.guild_id = ? ORDER BY c.id DESC LIMIT ${CHAT_HISTORY_LIMIT}`,
    [GUILD_ID]);
  rows.reverse();
  ok(ws, msg.id, {
    messages: rows.map(r => ({
      from: r.mc_nick || String(r.from_user),
      text: r.body,
      at: Number(r.created_at),
    })),
  });
}

// --- admin / админка ----------------------------------------------------------
// Admins: kwentgames + _Belmo (from OWNER_IDS). Admin panel: kick, mute, stats.
// Админы: kwentgames + _Belmo (из OWNER_IDS). Админ-панель: кик, мут, статистика.
const ADMIN_NICKS = new Set(['kwentgames', '_belmo']);
const mutedNicks = new Map(); // nick(lower) -> expiresAt

function isAdmin(sess) {
  return isOwner(sess.userId) || ADMIN_NICKS.has(sess.mcNick.toLowerCase());
}

async function handleAdminKick(ws, msg, sess) {
  if (!isAdmin(sess)) return fail(ws, msg.id, 'not_admin');
  const target = msg.nick;
  if (!validNick(target)) return fail(ws, msg.id, 'bad_nick');
  const key = target.toLowerCase();
  const set = onlineNicks.get(key);
  if (!set || set.size === 0) return fail(ws, msg.id, 'not_online');
  for (const s of set) {
    send(s, { type: 'error', reason: 'kicked' });
    s.close(1000, 'kicked by admin');
  }
  ok(ws, msg.id, { kicked: target });
}

async function handleAdminMute(ws, msg, sess) {
  if (!isAdmin(sess)) return fail(ws, msg.id, 'not_admin');
  const target = msg.nick;
  if (!validNick(target)) return fail(ws, msg.id, 'bad_nick');
  const durationMs = Number.isInteger(msg.durationMs) ? msg.durationMs : 300000; // 5 min default
  mutedNicks.set(target.toLowerCase(), Date.now() + durationMs);
  ok(ws, msg.id, { muted: target, until: Date.now() + durationMs });
}

async function handleAdminUnmute(ws, msg, sess) {
  if (!isAdmin(sess)) return fail(ws, msg.id, 'not_admin');
  const target = msg.nick;
  if (!validNick(target)) return fail(ws, msg.id, 'bad_nick');
  mutedNicks.delete(target.toLowerCase());
  ok(ws, msg.id, { unmuted: target });
}

async function handleAdminStats(ws, msg, sess) {
  if (!isAdmin(sess)) return fail(ws, msg.id, 'not_admin');
  const [passportCount] = await pool.query(
    'SELECT COUNT(*) c FROM drunsk_passports WHERE guild_id = ?', [GUILD_ID]);
  const [msgCount] = await pool.query(
    'SELECT COUNT(*) c FROM drunsk_msg WHERE guild_id = ?', [GUILD_ID]);
  const [chatCount] = await pool.query(
    'SELECT COUNT(*) c FROM drunsk_chat WHERE guild_id = ?', [GUILD_ID]);
  ok(ws, msg.id, {
    online: onlineNicks.size,
    passports: Number(passportCount[0].c),
    dms: Number(msgCount[0].c),
    chats: Number(chatCount[0].c),
    muted: [...mutedNicks.entries()].filter(([, exp]) => exp > Date.now()).map(([n]) => n),
  });
}

const HANDLERS = {
  me: handleMe,
  list: handleList,
  passport: handlePassport,
  transfer: handleTransfer,
  casino: handleCasino,
  history: handleHistory,
  dm_send: handleDmSend,
  dm_history: handleDmHistory,
  chat_send: handleChatSend,
  chat_history: handleChatHistory,
  admin_kick: handleAdminKick,
  admin_mute: handleAdminMute,
  admin_unmute: handleAdminUnmute,
  admin_stats: handleAdminStats,
};

// --- sessions / сессии -----------------------------------------------------
const onlineNicks = new Map(); // nick(lower) -> Set<ws>
const userSockets = new Map(); // userId(string) -> Set<ws>

function notifyBalance(userId) {
  const set = userSockets.get(String(userId));
  if (!set) return;
  for (const s of set) send(s, { type: 'balance_changed' });
}

const server = http.createServer((req, res) => {
  if (req.url === '/health') { res.writeHead(200); res.end('ok'); return; }
  res.writeHead(404); res.end();
});

const wss = new WebSocketServer({ server, path: '/drunsk', maxPayload: MAX_MSG_BYTES });

wss.on('connection', (ws, req) => {
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });
  ws.rate = { tokens: RATE_LIMIT_PER_SEC, at: Date.now() };
  let sess = null;

  ws.on('message', async (data) => {
    let msg;
    try { msg = JSON.parse(data.toString()); } catch { return; }
    if (!msg || typeof msg !== 'object') return;

    // token bucket / ведро токенов
    const now = Date.now();
    ws.rate.tokens = Math.min(RATE_LIMIT_PER_SEC, ws.rate.tokens + (now - ws.rate.at) / 1000 * RATE_LIMIT_PER_SEC);
    ws.rate.at = now;
    if (ws.rate.tokens < 1) {
      if (msg.id !== undefined) fail(ws, msg.id, 'rate_limited');
      else send(ws, { type: 'error', reason: 'rate_limited' });
      return;
    }
    ws.rate.tokens -= 1;

    const type = msg.type;
    if (type === 'hello') {
      const nick = msg.mcNick;
      const token = typeof msg.token === 'string' ? msg.token : '';
      if (!validNick(nick)) return fail(ws, msg.id, 'bad_nick');
      const [rows] = await pool.query(
        'SELECT user_id, created_at FROM drunsk_passports WHERE guild_id = ? AND mc_nick = ? AND token_hash = ?',
        [GUILD_ID, nick, sha256(token)]);
      if (rows.length === 0) return fail(ws, msg.id, 'unauthorized');
      sess = { userId: String(rows[0].user_id), mcNick: nick, since: Number(rows[0].created_at) };
      ws.sess = sess;
      const key = nick.toLowerCase();
      if (!onlineNicks.has(key)) onlineNicks.set(key, new Set());
      onlineNicks.get(key).add(ws);
      if (!userSockets.has(sess.userId)) userSockets.set(sess.userId, new Set());
      userSockets.get(sess.userId).add(ws);
      log('auth ok:', nick);
      ok(ws, msg.id, { userId: sess.userId, owner: isOwner(sess.userId), currency: CURRENCY });
      broadcastPresence();
      return;
    }
    if (type === 'pair_request') return handlePairRequest(ws, msg);
    if (type === 'ping') return ok(ws, msg.id, { t: msg.t || Date.now() });

    if (!sess) return fail(ws, msg.id, 'unauthorized');
    const handler = HANDLERS[type];
    if (!handler) return fail(ws, msg.id, 'unknown_type');
    try {
      await handler(ws, msg, sess);
    } catch (e) {
      fail(ws, msg.id, 'internal_error');
      log('handler error', type, e.message);
    }
  });

  ws.on('close', () => {
    if (ws.sess) {
      const key = ws.sess.mcNick.toLowerCase();
      const set = onlineNicks.get(key);
      if (set) { set.delete(ws); if (set.size === 0) onlineNicks.delete(key); }
      const uset = userSockets.get(ws.sess.userId);
      if (uset) { uset.delete(ws); if (uset.size === 0) userSockets.delete(ws.sess.userId); }
      log('disconnected:', ws.sess.mcNick);
      broadcastPresence();
    }
  });
});

// presence snapshot to everyone (cheap, only on change) / снимок онлайна всем
let lastPresence = '';
function broadcastPresence() {
  const list = [...onlineNicks.keys()].sort().join(',');
  if (list === lastPresence) return;
  lastPresence = list;
  const payload = JSON.stringify({ type: 'presence', online: list ? list.split(',') : [] });
  for (const client of wss.clients) {
    if (client.readyState === client.OPEN) client.send(payload);
  }
}

// heartbeat / сердцебиение
const hb = setInterval(() => {
  for (const client of wss.clients) {
    if (!client.isAlive) { client.terminate(); continue; }
    client.isAlive = false;
    client.ping();
  }
}, HEARTBEAT_MS);
const pairTimer = setInterval(() => { pollPairConfirmations().catch(e => log('poll error', e.message)); }, PAIR_POLL_MS);

server.listen(PORT, HOST, () => {
  log(`drunsk-relay listening on ${HOST}:${PORT}, guild ${GUILD_ID}`);
});

async function main() {
  await ensureSchema();
  log('schema ready / схема готова');
}
main().catch(e => { log('FATAL schema', e.message); process.exit(1); });

process.on('SIGTERM', () => { clearInterval(hb); clearInterval(pairTimer); server.close(() => process.exit(0)); });
