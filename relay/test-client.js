'use strict';
/** No-Minecraft protocol test client. Uses only in-memory messages. */
const WebSocket = require('ws');
const url = process.env.DRUNSK_TEST_URL || 'ws://127.0.0.1:8791/drunsk-test';
const ws = new WebSocket(url);
let id = 0;
const request = payload => new Promise((resolve, reject) => {
  const requestId = ++id;
  const timer = setTimeout(() => reject(new Error('timeout')), 2000);
  const listener = raw => { const msg = JSON.parse(raw); if (msg.id !== requestId) return; clearTimeout(timer); ws.off('message', listener); resolve(msg); };
  ws.on('message', listener); ws.send(JSON.stringify({ id: requestId, ...payload }));
});
ws.once('message', async raw => {
  const challenge = JSON.parse(raw.toString()).challenge;
  try {
    const handshake = await request({ type: 'hello', challenge, mcNick: 'TestPlayer', drunskClientVersion: 'test-1', mcVersion: '26.2', sodium: { present: true, version: '0.7-test' } });
    if (!handshake.ok || !handshake.sodium.present) throw new Error('handshake failed');
    const chat = await request({ type: 'chat_send', text: 'server-side smoke' });
    if (!chat.ok || !chat.accepted) throw new Error('chat failed');
    console.log(JSON.stringify({ handshake: handshake.message, chat: chat.result }));
    ws.close();
  } catch (e) { console.error(e.message); process.exitCode = 1; ws.close(); }
});
ws.on('error', e => { console.error(e.message); process.exitCode = 1; });
