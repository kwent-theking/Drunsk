#!/bin/bash
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
set -a; . "$V/.env" 2>/dev/null; set +a
echo ===DRUNSK_TABLES_IN_PROD
mysql -h 127.0.0.1 -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SHOW TABLES LIKE 'drunsk%'; SELECT COUNT(*) passports FROM drunsk_passports; SELECT COUNT(*) codes FROM drunsk_pair_codes;" 2>&1
echo ===RELAY_STATUS
systemctl is-active drunsk-relay
echo ===BOT_COMMANDS_CHECK
docker logs --since 20m db2fddda-5124-4ee0-bba8-a014edb5553c 2>&1 | grep -iE "паспорт|error|traceback" | head -10 || echo "clean"
