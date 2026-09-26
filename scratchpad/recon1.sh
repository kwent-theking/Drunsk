#!/bin/bash
# Drunsk recon 1: bot volume, env, caddy, mysql, /opt
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
echo ===VOL
ls -la "$V" | head -30
echo ===ENV
sed -E 's/(PASS|TOKEN|SECRET)([A-Z_]*=).*/\1\2***REDACTED***/I' "$V/.env" 2>/dev/null
echo ===CADDY
cat /etc/caddy/Caddyfile 2>/dev/null
echo ===MYSQL
which mysql mariadb 2>/dev/null
systemctl is-active mariadb 2>/dev/null; systemctl is-active mysql 2>/dev/null
echo ===OPT
ls /opt/ 2>&1
echo ===CAMSRELAY
systemctl is-active cams-relay 2>&1; cat /etc/systemd/system/cams-relay.service 2>/dev/null | head -30
