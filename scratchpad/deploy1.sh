#!/bin/bash
# Drunsk deploy step 1: check grants, prepare dirs
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
set -a; . "$V/.env" 2>/dev/null; set +a
echo ===GRANTS
mysql -h 127.0.0.1 -u "$DB_USER" -p"$DB_PASS" -e "SHOW GRANTS FOR CURRENT_USER();" 2>&1 | sed "s/$DB_PASS/***/"
echo ===CREATE_TEST_DB
mysql -h 127.0.0.1 -u "$DB_USER" -p"$DB_PASS" -e "CREATE DATABASE IF NOT EXISTS drunsk_test;" 2>&1
echo ===WHOAMI_ROOT_MYSQL
mysql -e "SELECT CURRENT_USER();" 2>&1 | head -3
echo ===PORTS_FREE
ss -tlnp | grep -E "8790|18790" || echo "ports free"
echo ===DISK
df -h / | tail -1
