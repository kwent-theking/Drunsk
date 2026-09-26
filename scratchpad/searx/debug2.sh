#!/bin/bash
echo ===SEARXNG_LOGS
docker logs --tail 30 searxng-core 2>&1
echo ===LISTEN_PORT_INSIDE
docker exec searxng-core sh -c 'ss -tlnp 2>/dev/null || netstat -tlnp 2>/dev/null || cat /proc/net/tcp | head' 2>&1 | head -15
echo ===PROBE_ROUTES_FROM_CORE
for p in 8080 8091; do
  echo "-- port $p /healthz:"; docker exec searxng-core wget -qO- "http://127.0.0.1:$p/healthz" 2>&1 | head -c 200; echo
  echo "-- port $p /search?format=json:"; docker exec searxng-core wget -qO- "http://127.0.0.1:$p/search?q=test&format=json" 2>&1 | head -c 200; echo
done
echo ===SETTINGS_IN_CONTAINER
docker exec searxng-core cat /etc/searxng/settings.yml 2>&1 | head -40
