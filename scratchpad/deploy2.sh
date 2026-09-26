#!/bin/bash
# Drunsk deploy step 2: create test DB + grants, relay dirs, user
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
set -a; . "$V/.env" 2>/dev/null; set +a
echo ===CREATE_TEST_DB
mysql -e "CREATE DATABASE IF NOT EXISTS drunsk_test CHARACTER SET utf8mb4; GRANT ALL PRIVILEGES ON drunsk_test.* TO '$DB_USER'@'127.0.0.1'; FLUSH PRIVILEGES;" 2>&1
echo ===CHECK
mysql -h 127.0.0.1 -u "$DB_USER" -p"$DB_PASS" drunsk_test -e "SELECT 1;" 2>&1
echo ===SERVICE_USER
id drunskrelay 2>/dev/null || useradd --system --home /opt/drunsk-relay --shell /usr/sbin/nologin drunskrelay
echo ===DIRS
mkdir -p /opt/drunsk-relay
echo ok
