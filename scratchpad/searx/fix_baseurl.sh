#!/bin/bash
# Remove SEARXNG_BASE_URL: it makes SearXNG 308-redirect to the public domain,
# which the gate's fetch would follow back through Caddy and 404.
# Убираем SEARXNG_BASE_URL: он заставляет SearXNG редиректить на публичный домен.
set -e
cd /opt/searxng
cp .env .env.bak-$(date +%s)
sed -i '/^SEARXNG_BASE_URL=/d' .env
echo "=== .env now:"; sed 's/\(GATE_KEY=\).*/\1***/' .env
docker compose up -d
sleep 6
echo "=== HTML search (no base_url redirect expected):"
docker exec searxng-gate sh -c "wget -S -qO- 'http://searxng-core:8091/search?q=test&format=json' --header='X-Forwarded-For: 127.0.0.1'" 2>&1 | head -c 500; echo
