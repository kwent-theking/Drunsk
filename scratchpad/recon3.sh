#!/bin/bash
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
set -a; . "$V/.env" 2>/dev/null; set +a
echo ===GUILD
grep -nE "GUILD_ID|guild_id =|1534856710739595265" "$V/main.py" | head -10
echo ===CURRENCY_NAME
grep -noE "(др[аy][^\"']{0,12}|монет[^\"']{0,8})" "$V/main.py" | head -20
echo ===GET_BALANCE
sed -n '490,520p' "$V/main.py"
echo ===PAY_CMD
sed -n '4452,4480p' "$V/main.py"
echo ===WS_AVAIL
ls /opt/phantom-relay/node_modules | head -20
node -e "require('/opt/phantom-relay/node_modules/ws'); console.log('ws ok')" 2>&1
node -e "require('mysql2'); console.log('mysql2 global ok')" 2>&1
echo ===DOM
grep -n "друнск" /etc/hosts 2>/dev/null; echo; python3 -c "print('xn--d1amilgk.online'.encode('idna'))" 2>/dev/null || true
echo ===UFW
ufw status | head -20
echo ===BOT_CONTAINER
docker ps --format "{{.Names}} {{.Status}}" | head
