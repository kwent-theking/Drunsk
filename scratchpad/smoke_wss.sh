#!/bin/bash
# Smoke test of the LIVE wss endpoint (no DB writes): unauthorized hello must
# be rejected cleanly, health must answer. / Смоук живого wss (без записи в БД).
cd /opt/drunsk-relay
node - <<'EOF'
const WebSocket = require('ws');
const url = process.env.WSS_URL || 'wss://xn--d1amilgk.online/drunsk';
const ws = new WebSocket(url);
const to = setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 15000);
ws.on('open', () => {
  console.log('ws open:', url);
  ws.send(JSON.stringify({ id: 1, type: 'hello', mcNick: 'SmokeTest', token: 'bogus' }));
});
ws.on('message', d => {
  console.log('reply:', d.toString());
  clearTimeout(to);
  ws.close();
  process.exit(0);
});
ws.on('error', e => { console.log('ws error:', e.message); clearTimeout(to); process.exit(2); });
EOF
