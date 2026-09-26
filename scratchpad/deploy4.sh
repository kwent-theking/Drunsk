#!/bin/bash
# Drunsk deploy step 4: env file, systemd, caddy route
set -e
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
set -a; . "$V/.env"; set +a

cat > /etc/drunsk-relay.env <<EOF
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=$DB_USER
DB_PASS=$DB_PASS
DB_NAME=$DB_NAME
DRUNSK_PORT=8790
DRUNSK_HOST=127.0.0.1
DRUNSK_GUILD_ID=1534856710739595265
DRUNSK_OWNERS=1365702494138793986,1533644237210517586
EOF
chmod 600 /etc/drunsk-relay.env
chown root:drunskrelay /etc/drunsk-relay.env

# Caddy: insert /drunsk route before the botpanel block, idempotent
if ! grep -q "handle /drunsk" /etc/caddy/Caddyfile; then
  cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.bak-drunsk-$(date +%s)
  python3 - <<'PYEOF'
path = '/etc/caddy/Caddyfile'
text = open(path, encoding='utf-8').read()
anchor = '    handle /botpanel* {'
route = '''    # Ретранслятор мода Drunsk: паспорта, баланс, переводы, лудка.
    # WebSocket, поэтому без буферизации и с длинными таймаутами.
    handle /drunsk* {
        reverse_proxy 127.0.0.1:8790
    }

'''
assert anchor in text, 'anchor missing'
text = text.replace(anchor, route + anchor, 1)
open(path, 'w', encoding='utf-8').write(text)
print('caddy patched')
PYEOF
else
  echo 'caddy already patched'
fi

systemctl daemon-reload
systemctl enable --now drunsk-relay 2>&1 | tail -1
sleep 2
systemctl is-active drunsk-relay
journalctl -u drunsk-relay -n 5 --no-pager
caddy validate --config /etc/caddy/Caddyfile 2>&1 | tail -2
systemctl reload caddy
sleep 2
systemctl is-active caddy
echo ===HEALTH
curl -s http://127.0.0.1:8790/health; echo
curl -sk -o /dev/null -w "%{http_code}\n" https://127.0.0.1/drunsk
