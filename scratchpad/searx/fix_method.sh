#!/bin/bash
# Fix SearXNG method POST->GET in place (keep real secret), redeploy gate.
# Правка method POST->GET по месту (secret не трогаем), перезапуск.
set -e
sed -i 's/method: "POST"/method: "GET"/' /opt/searxng/core-config/settings.yml
grep -n 'method:' /opt/searxng/core-config/settings.yml
cd /opt/searxng
docker compose restart core gate
sleep 6
docker compose ps --format 'table {{.Name}}\t{{.Status}}'
