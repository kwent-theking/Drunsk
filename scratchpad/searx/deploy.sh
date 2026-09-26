#!/bin/bash
# Deploy SearXNG + gate on the VPS. Idempotent: re-runnable, preserves .env.
# Деплой SearXNG + gate на ВПС. Идемпотентный: .env не перезатирает.
set -e
cd /opt/searxng

# --- secrets: generate once, never print the SearXNG secret again -----------
if [ ! -f .env ]; then
  GATE_KEY=$(openssl rand -hex 32)
  cat > .env <<EOF
SEARXNG_VERSION=latest
SEARXNG_PORT=8091
SEARXNG_BASE_URL=https://xn--d1amilgk.online/search
GATE_KEY=$GATE_KEY
GATE_RATE_PER_MIN=120
GATE_MAX_RESULTS=12
GATE_TIMEOUT_MS=12000
EOF
  chmod 600 .env
  SECRET=$(openssl rand -hex 32)
  sed -i "s/__SECRET_KEY__/$SECRET/" core-config/settings.yml
  echo "GATE_KEY (save this, shown once): $GATE_KEY"
else
  echo ".env exists, keeping it"
fi
grep -q "__SECRET_KEY__" core-config/settings.yml && { echo "ERROR: settings.yml still has placeholder"; exit 2; } || true

docker compose up -d
sleep 6
docker compose ps --format 'table {{.Name}}\t{{.Status}}'

echo ===LOCAL_GATE_HEALTH
curl -s http://127.0.0.1:8092/health; echo
echo ===NOT_EXPOSED_CHECK
ss -tlnp | grep -E ':8091' && { echo "ERROR: 8091 exposed on host"; exit 3; } || echo "8091 not on host (good)"
