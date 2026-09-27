'use strict';
// Drunsk relay selftest / Самотест ретранслятора Drunsk
// Usage / Использование:
//   DB_USER=... DB_PASS=... DB_NAME=drunsk_test node selftest.js [wss://host/drunsk]
// Без URL поднимает локальный сервер сам и тестирует через ws://127.0.0.1.
// Without URL spins up a local server itself and tests via ws://127.0.0.1.

const crypto = require('crypto');
const { spawn } = require('child_process');
const WebSocket = require('ws');
const mysql = require('mysql2/promise');

const GUILD = process.env.DRUNSK_GUILD_ID || '1534856710739595265';
const EXTERNAL_URL = process.argv[2];
const LOCAL_PORT = parseInt(process.env.DRUNSK_TEST_PORT || '18790', 10);
const sha256 = s => crypto.createHash('sha256').update(s).digest('hex');

let passed = 0, failed = 0;
function check(name, cond, extra) {
  if (cond) { passed++; console.log('  PASS', name); }
  else { failed++; console.log('  FAIL', name, extra !== undefined ? JSON.stringify(extra) : ''); }
}

const db = mysql.createPool({
  host: process.env.DB_HOST || '127.0.0.1',
  port: parseInt(process.env.DB_PORT || '3306', 10),
  user: process.env.DB_USER, password: process.env.DB_PASS,
  database: process.env.DB_NAME || 'drunsk_test',
  waitForConnections: true, connectionLimit: 4,
  supportBigNumbers: true, bigNumberStrings: true,
});

// Selftest DROPS and recreates tables — never point it at the live bot DB.
// Самотест ПЕРЕСОЗДАЁТ таблицы — никогда не направлять его на живую базу бота.
if (!(process.env.DB_NAME || 'drunsk_test').includes('test')) {
  console.error('refusing: DB_NAME must contain "test"');
  process.exit(2);
}

async function reset() {
  for (const t of ['drunsk_chat', 'drunsk_msg', 'drunsk_tx', 'drunsk_pair_codes', 'drunsk_passports', 'users', 'user_names']) {
    await db.query(`DROP TABLE IF EXISTS ${t}`).catch(() => {});
  }
  await db.query(`CREATE TABLE users (
    user_id bigint NOT NULL, balance bigint DEFAULT 0, bought_custom tinyint DEFAULT 0,
    is_hidden tinyint DEFAULT 0, hide_balance tinyint DEFAULT 0, reminders tinyint DEFAULT 1,
    remind_only_online tinyint DEFAULT 0, last_daily bigint DEFAULT 0, last_burmalda bigint DEFAULT 0,
    notified_daily tinyint DEFAULT 1, notified_burmalda tinyint DEFAULT 1, dm_rewards tinyint DEFAULT 1,
    guild_id bigint NOT NULL DEFAULT 0, account_opened tinyint DEFAULT 0, PRIMARY KEY (guild_id, user_id))`);
  await db.query(`CREATE TABLE user_names (user_id bigint NOT NULL, name varchar(64), avatar varchar(512) NULL, updated_at bigint DEFAULT 0)`);
}

// --- ws client helper ------------------------------------------------------
let idSeq = 1;
function connect(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    ws.pending = new Map();
    ws.pushes = [];
    ws.on('message', d => {
      const m = JSON.parse(d.toString());
      if (m.id !== undefined && ws.pending.has(m.id)) { const r = ws.pending.get(m.id); ws.pending.delete(m.id); r(m); }
      else ws.pushes.push(m);
    });
    ws.req = (type, fields, timeoutMs = 5000) => new Promise((res, rej) => {
      const id = idSeq++;
      const to = setTimeout(() => { ws.pending.delete(id); rej(new Error('timeout ' + type)); }, timeoutMs);
      ws.pending.set(id, m => { clearTimeout(to); res(m); });
      ws.send(JSON.stringify({ id, type, ...fields }));
    });
    ws.on('open', () => resolve(ws));
    ws.on('error', reject);
  });
}

async function pairAndAuth(ws, nick, userId, discordName) {
  // simulate the bot: user types !паспорт <code> in Discord, bot sets user_id
  const pr = await ws.req('pair_request', { mcNick: nick });
  if (!pr.ok) throw new Error('pair_request failed: ' + pr.reason);
  await db.query(
    'UPDATE drunsk_pair_codes SET user_id = ? WHERE code_hash = ? AND guild_id = ?',
    [userId, sha256(pr.code), GUILD]);
  const paired = await new Promise((res, rej) => {
    const to = setTimeout(() => rej(new Error('paired push timeout')), 8000);
    const iv = setInterval(() => {
      const p = ws.pushes.find(m => m.type === 'paired');
      if (p) { clearInterval(iv); clearTimeout(to); res(p); }
    }, 100);
  });
  const h = await ws.req('hello', { mcNick: nick, token: paired.token });
  if (!h.ok) throw new Error('hello failed: ' + h.reason);
  if (discordName) {
    await db.query('INSERT INTO user_names (user_id, name, updated_at) VALUES (?, ?, ?)', [userId, discordName, Date.now()]);
  }
  return { code: pr.code, token: paired.token };
}

async function setBalance(userId, amount) {
  await db.query('INSERT INTO users (guild_id, user_id, balance) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE balance = VALUES(balance)',
    [GUILD, userId, amount]);
}
async function getBal(userId) {
  const [r] = await db.query('SELECT balance FROM users WHERE guild_id = ? AND user_id = ?', [GUILD, userId]);
  return r.length ? Number(r[0].balance) : null;
}

// --- tests -----------------------------------------------------------------
async function main() {
  await reset();
  let server = null, url;
  if (EXTERNAL_URL) {
    url = EXTERNAL_URL;
  } else {
    server = spawn(process.execPath, [__dirname + '/server.js'], {
      env: { ...process.env, DRUNSK_PORT: String(LOCAL_PORT), DRUNSK_GUILD_ID: GUILD },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    server.stdout.on('data', d => process.env.DRUNSK_VERBOSE && console.log('[relay]', d.toString().trim()));
    server.stderr.on('data', d => console.log('[relay:err]', d.toString().trim()));
    url = `ws://127.0.0.1:${LOCAL_PORT}/drunsk`;
    await new Promise(res => setTimeout(res, 1500));
  }

  console.log('url:', url);

  // 1. hello without token is rejected / hello без токена отбивается
  {
    const ws = await connect(url);
    const r = await ws.req('hello', { mcNick: 'Ghost', token: 'deadbeef' });
    check('hello w/o valid token rejected', r.ok === false && r.reason === 'unauthorized', r);
    const r2 = await ws.req('me', {});
    check('requests before auth rejected', r2.ok === false && r2.reason === 'unauthorized', r2);
    ws.close();
  }

  // 2. full pairing flow: code -> bot confirm -> paired push -> hello ok
  //    полный флоу привязки: код -> подтверждение бота -> пуш paired -> hello ok
  const wsKwent = await connect(url);
  const wsBelmo = await connect(url);
  const wsPlayer = await connect(url);
  const K_ID = '1365702494138793986', B_ID = '1533644237210517586', P_ID = '999000111222';
  const pk = await pairAndAuth(wsKwent, 'kwent', K_ID, 'kwent');
  check('pair code is 6 digits', /^\d{6}$/.test(pk.code), pk.code);
  const pb = await pairAndAuth(wsBelmo, 'Belmo', B_ID, 'Belmo');
  const pp = await pairAndAuth(wsPlayer, 'PlayerOne', P_ID, 'Игрок');
  check('pairing works for 3 users', !!(pk.token && pb.token && pp.token));

  // 3. re-pair of taken nick rejected / повторная привязка занятого ника отбивается
  {
    const ws2 = await connect(url);
    const r = await ws2.req('pair_request', { mcNick: 'kwent' });
    check('re-pair of taken nick rejected', r.ok === false && r.reason === 'already_paired', r);
    const r2 = await ws2.req('pair_request', { mcNick: 'bad nick!!' });
    check('bad nick rejected', r2.ok === false && r2.reason === 'bad_nick', r2);
    ws2.close();
  }

  // 4. me / я
  await setBalance(K_ID, 10000);
  {
    const r = await wsKwent.req('me', {});
    check('me returns balance', r.ok && r.balance === 10000 && r.userId === String(K_ID), r);
    check('me flags owner', r.ok && r.owner === true, r);
  }

  // 5. list with presence / список с онлайном
  {
    const r = await wsPlayer.req('list', {});
    check('list returns 3 passports', r.ok && r.passports.length === 3, r.passports && r.passports.length);
    check('list marks online', r.ok && r.passports.every(p => p.online === true), r.passports);
  }

  // 6. transfer: insufficient, then ok; money conserved atomically
  //    перевод: не хватает, потом успешный; деньги сохраняются атомарно
  await setBalance(P_ID, 500);
  {
    const r1 = await wsPlayer.req('transfer', { toNick: 'kwent', amount: 99999 });
    check('transfer w/o funds rejected', r1.ok === false && r1.reason === 'not_enough', r1);
    const r2 = await wsPlayer.req('transfer', { toNick: 'kwent', amount: 0 });
    check('zero transfer rejected', r2.ok === false, r2);
    const r3 = await wsPlayer.req('transfer', { toNick: 'kwent', amount: 200 });
    check('transfer ok', r3.ok && r3.newBalance === 300, r3);
    check('receiver credited', (await getBal(K_ID)) === 10200, await getBal(K_ID));
    const pushes = wsKwent.pushes.filter(m => m.type === 'balance_changed');
    check('receiver notified live', pushes.length >= 1, pushes);
    const r4 = await wsPlayer.req('transfer', { toNick: 'PlayerOne', amount: 10 });
    check('self transfer rejected', r4.ok === false && r4.reason === 'self_transfer', r4);
    const r5 = await wsPlayer.req('transfer', { toNick: 'NoSuchGuy', amount: 10 });
    check('transfer to unknown rejected', r5.ok === false && r5.reason === 'no_passport', r5);
  }

  // 7. concurrent transfers: conservation under race / параллельные переводы: сохранение
  {
    await setBalance(P_ID, 1000);
    const before = (await getBal(P_ID)) + (await getBal(K_ID)) + (await getBal(B_ID));
    const reqs = [];
    for (let i = 0; i < 10; i++) reqs.push(wsPlayer.req('transfer', { toNick: 'kwent', amount: 50 }));
    await Promise.all(reqs);
    const after = (await getBal(P_ID)) + (await getBal(K_ID)) + (await getBal(B_ID));
    check('money conserved under 10 concurrent transfers', before === after, { before, after });
    check('payer drained correctly', (await getBal(P_ID)) === 500, await getBal(P_ID));
  }

  // 8. casino: money conservation across many rounds / лудка: сохранение денег
  {
    await setBalance(P_ID, 100000);
    let wins = 0, games = 0;
    for (let i = 0; i < 60; i++) {
      const game = ['coin', 'dice', 'roulette'][i % 3];
      const pick = game === 'dice' ? (i % 2 ? 'over' : 'under') : (game === 'roulette' ? (i % 2 ? 'red' : 'black') : undefined);
      const r = await wsPlayer.req('casino', { game, bet: 10, pick });
      if (r.ok) { games++; if (r.win) wins++; }
      await new Promise(res => setTimeout(res, 40)); // stay under the rate limit / не упираться в рейт-лимит
    }
    check('casino rounds played', games === 60, games);
    check('casino wins and losses both occur', wins > 0 && wins < 60, wins);
    const bal = await getBal(P_ID);
    const [txSum] = await db.query('SELECT COALESCE(SUM(amount),0) s FROM drunsk_tx WHERE guild_id = ? AND from_user = ? AND kind IN ("coin","dice","roulette")', [GUILD, P_ID]);
    check('casino ledger matches balance', 100000 + Number(txSum[0].s) === bal, { bal, ledger: Number(txSum[0].s) });
    const rBad = await wsPlayer.req('casino', { game: 'slots', bet: 10 });
    check('unknown game rejected', rBad.ok === false && rBad.reason === 'bad_game', rBad);
    const rBig = await wsPlayer.req('casino', { game: 'coin', bet: 99999999 });
    check('huge bet rejected', rBig.ok === false, rBig);
  }

  // 9. roulette exact number payout x14 / рулетка: точное число x14
  {
    await setBalance(P_ID, 10000);
    let hit = false;
    for (let attempt = 0; attempt < 300 && !hit; attempt++) {
      const n = attempt % 15;
      const r = await wsPlayer.req('casino', { game: 'roulette', bet: 1, pick: n });
      if (r.ok && r.win) {
        hit = true;
        check('roulette exact pays x14', r.payout === 14 && r.detail.startsWith('spin=' + n), r);
      }
      await new Promise(res => setTimeout(res, 40));
    }
    check('roulette exact number hit within sweep', hit);
  }

  // 10. hide_balance / is_hidden respected for NON-owner viewers
  //     (Belmo is an owner too — owners see everything, so PlayerOne looks at Belmo)
  //     скрытый баланс/паспорт не виден НЕ-владельцам (Бельмо тоже владелец —
  //     владельцы видят всё, поэтому смотрит PlayerOne на Belmo)
  {
    // Belmo's users row may not exist yet (lazy create on read) — the UPDATE
    // below would silently affect 0 rows without this. / Строки Belmo в users
    // может ещё не быть (ленивое создание) — без этого UPDATE молча промахнётся.
    await setBalance(B_ID, 555);
    await db.query('UPDATE users SET hide_balance = 1 WHERE guild_id = ? AND user_id = ?', [GUILD, B_ID]);
    const r = await wsPlayer.req('list', {});
    const belmoRow = r.passports.find(p => p.mcNick === 'Belmo');
    check('hide_balance hides amount in list', belmoRow && belmoRow.balance === null && belmoRow.hidden === true, belmoRow);
    const rp = await wsPlayer.req('passport', { mcNick: 'Belmo' });
    check('hide_balance hides amount in passport', rp.ok && rp.balance === null, rp);
    await db.query('UPDATE users SET hide_balance = 0, is_hidden = 1 WHERE guild_id = ? AND user_id = ?', [GUILD, B_ID]);
    const r2 = await wsPlayer.req('list', {});
    check('is_hidden removes from others list', !r2.passports.find(p => p.mcNick === 'Belmo'), r2.passports.length);
    const rp2 = await wsPlayer.req('passport', { mcNick: 'Belmo' });
    check('is_hidden blocks passport view', rp2.ok === false && rp2.reason === 'hidden', rp2);
    const rk = await wsKwent.req('passport', { mcNick: 'Belmo' });
    check('owner sees hidden passport', rk.ok && rk.balance !== null, rk);
    await db.query('UPDATE users SET is_hidden = 0 WHERE guild_id = ? AND user_id = ?', [GUILD, B_ID]);
  }

  // 11. history / история
  {
    const r = await wsPlayer.req('history', { limit: 10 });
    check('history returns entries', r.ok && r.history.length === 10, r.history && r.history.length);
    const kinds = new Set(['transfer', 'coin', 'dice', 'roulette']);
    check('history entries well-formed',
      r.ok && r.history.every(h => kinds.has(h.kind) && Number.isInteger(h.amount) && h.at > 0));
  }

  // 12. presence push on join/leave / пуш онлайна при входе-выходе
  {
    wsKwent.pushes.length = 0;
    const ws3 = await connect(url);
    await pairAndAuth(ws3, 'TempGuy', '555000111222', null);
    await new Promise(res => setTimeout(res, 400));
    check('presence push on join', wsKwent.pushes.some(m => m.type === 'presence' && m.online.includes('tempguy')), wsKwent.pushes);
    ws3.close();
    await new Promise(res => setTimeout(res, 600));
    const last = wsKwent.pushes.filter(m => m.type === 'presence').pop();
    check('presence push on leave', last && !last.online.includes('tempguy'), last);
  }

  // 13. rate limit / ограничение частоты
  {
    const ws4 = await connect(url);
    await pairAndAuth(ws4, 'Spammer', '777000111222', null);
    // responses with an id land in pending, pushes in pushes — a rate-limited
    // request is answered with {id, ok:false, reason:'rate_limited'}
    // ответы с id уходят в pending; rate_limited приходит как {id, ok:false}
    const seen = [];
    const origHandler = ws4.onmessage;
    ws4.on('message', d => {
      const m = JSON.parse(d.toString());
      if (m.reason === 'rate_limited') seen.push(m);
    });
    for (let i = 0; i < 200; i++) {
      ws4.send(JSON.stringify({ id: 9000 + i, type: 'me' }));
    }
    await new Promise(res => setTimeout(res, 800));
    check('rate limiter kicks in', seen.length > 0, seen.length);
    ws4.close();
  }

  // 14. token theft: wrong token for paired nick rejected / чужой токен отбит
  {
    const ws5 = await connect(url);
    const r = await ws5.req('hello', { mcNick: 'kwent', token: pk.token.slice(0, 32) + 'x'.repeat(32) });
    check('wrong token rejected', r.ok === false && r.reason === 'unauthorized', r);
    ws5.close();
  }

  // 15. DM: send, live push, history, validation
  //     ЛС: отправка, живой пуш, история, валидация
  {
    wsKwent.pushes.length = 0;
    const r1 = await wsPlayer.req('dm_send', { to: 'kwent', text: 'привет друн' });
    check('dm_send ok', r1.ok && r1.at > 0, r1);
    await new Promise(res => setTimeout(res, 200));
    const pushed = wsKwent.pushes.filter(m => m.type === 'dm');
    check('dm pushed live to recipient', pushed.length === 1 && pushed[0].from === 'PlayerOne' && pushed[0].text === 'привет друн', pushed);
    const r2 = await wsKwent.req('dm_send', { to: 'PlayerOne', text: 'здарова' });
    check('dm reply ok', r2.ok, r2);
    await new Promise(res => setTimeout(res, 200));
    const r3 = await wsKwent.req('dm_history', { peer: 'PlayerOne' });
    check('dm_history returns both messages oldest-first',
      r3.ok && r3.messages.length === 2 && r3.messages[0].text === 'привет друн' && r3.messages[1].from === 'kwent', r3.messages);
    const r4 = await wsPlayer.req('dm_send', { to: 'PlayerOne', text: 'сам себе' });
    check('self dm rejected', r4.ok === false && r4.reason === 'self_dm', r4);
    const r5 = await wsPlayer.req('dm_send', { to: 'kwent', text: '' });
    check('empty dm rejected', r5.ok === false && r5.reason === 'bad_text', r5);
    const r6 = await wsPlayer.req('dm_send', { to: 'kwent', text: 'x'.repeat(401) });
    check('too long dm rejected', r6.ok === false && r6.reason === 'bad_text', r6);
    const r7 = await wsPlayer.req('dm_send', { to: 'NoSuchGuy', text: 'эй' });
    check('dm to unknown rejected', r7.ok === false && r7.reason === 'no_passport', r7);
  }

  // 16. global chat: send, broadcast, history, validation
  //     общий чат: отправка, рассылка, история, валидация
  {
    wsBelmo.pushes.length = 0;
    const r1 = await wsPlayer.req('chat_send', { text: 'привет всем' });
    check('chat_send ok', r1.ok && r1.at > 0, r1);
    await new Promise(res => setTimeout(res, 200));
    const pushed = wsBelmo.pushes.filter(m => m.type === 'chat');
    check('chat broadcast to all', pushed.length === 1 && pushed[0].from === 'PlayerOne' && pushed[0].text === 'привет всем', pushed);
    const r2 = await wsKwent.req('chat_send', { text: 'здарова' });
    check('chat reply ok', r2.ok, r2);
    await new Promise(res => setTimeout(res, 200));
    const r3 = await wsKwent.req('chat_history', {});
    check('chat_history returns both', r3.ok && r3.messages.length === 2 && r3.messages[0].text === 'привет всем', r3.messages);
    const r4 = await wsPlayer.req('chat_send', { text: '' });
    check('empty chat rejected', r4.ok === false && r4.reason === 'bad_text', r4);
    const r5 = await wsPlayer.req('chat_send', { text: 'x'.repeat(401) });
    check('too long chat rejected', r5.ok === false && r5.reason === 'bad_text', r5);
  }

  // 17. admin: stats, kick, mute, not-admin rejection
  //     админка: статистика, кик, мут, отказ не-админу
  {
    const r1 = await wsKwent.req('admin_stats', {});
    check('admin stats for owner', r1.ok && r1.online >= 3 && r1.passports >= 3, r1);
    const r2 = await wsPlayer.req('admin_stats', {});
    check('admin stats rejected for non-admin', r2.ok === false && r2.reason === 'not_admin', r2);
    const r3 = await wsPlayer.req('admin_kick', { nick: 'kwent' });
    check('admin kick rejected for non-admin', r3.ok === false && r3.reason === 'not_admin', r3);
    // mute PlayerOne, then chat should be rejected
    const r4 = await wsKwent.req('admin_mute', { nick: 'PlayerOne', durationMs: 60000 });
    check('admin mute ok', r4.ok, r4);
    const r5 = await wsPlayer.req('chat_send', { text: 'я в муте' });
    check('muted chat rejected', r5.ok === false && r5.reason === 'muted', r5);
    const r6 = await wsKwent.req('admin_unmute', { nick: 'PlayerOne' });
    check('admin unmute ok', r6.ok, r6);
    const r7 = await wsPlayer.req('chat_send', { text: 'размучен' });
    check('unmuted chat ok', r7.ok, r7);
  }

  for (const w of [wsKwent, wsBelmo, wsPlayer]) w.close();
  await db.end();
  if (server) server.kill();

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch(e => { console.error('SELFTEST CRASH', e); process.exit(2); });
