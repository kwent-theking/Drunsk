#!/bin/bash
# Drunsk recon 2: economy schema, bot DB usage, relay samples, node
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
set -a; . "$V/.env" 2>/dev/null; set +a
echo ===TABLES
mysql -h 127.0.0.1 -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SHOW TABLES;" 2>/dev/null
echo ===USERS_SCHEMA
mysql -h 127.0.0.1 -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SHOW CREATE TABLE users\G" 2>/dev/null | head -40
echo ===SCHEMA_ALL
mysql -h 127.0.0.1 -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SELECT TABLE_NAME, GROUP_CONCAT(COLUMN_NAME) cols FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' GROUP BY TABLE_NAME;" 2>/dev/null
echo ===PHANTOM_RELAY
ls /opt/phantom-relay | head; cat /etc/systemd/system/phantom-relay.service 2>/dev/null
echo ===NODE
node --version 2>&1; npm --version 2>&1
echo ===BOT_BALANCE_FUNCS
grep -nE "CREATE TABLE|def (get_balance|set_balance|add_balance|transfer|pay)" "$V/main.py" | head -40
echo ===BOT_CURRENCY
grep -niE "валюта|currency|монет|дрyнск|coin" "$V/main.py" | head -20
