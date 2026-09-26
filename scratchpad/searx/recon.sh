#!/bin/bash
# SearXNG recon: OS, docker, ports, resources, DNS/CF status, official compose URLs
echo ===OS
. /etc/os-release; echo "$PRETTY_NAME"
uname -r
echo ===DOCKER
docker --version 2>&1
docker compose version 2>&1
docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}' 2>&1
echo ===RESOURCES
free -m | head -3
df -h / /var/lib/docker 2>/dev/null | tail -2
nproc
echo ===PORTS_IN_USE
ss -tlnp | awk '{print $4, $6}' | grep -E ':8080|:8091|:8123|:8788|:8790|:443|:80' | sort -u
echo ===SEARXNG_EXISTS
ls -la /opt/searxng 2>&1 | head -5
docker ps -a --format '{{.Names}}' | grep -i searx || echo "no searxng containers"
echo ===DOMAIN_NS
for d in xn--d1amilgk.online kivess.space; do
  echo "-- $d"
  getent hosts "$d" || echo "  (no A via getent)"
  dig +short NS "$d" 2>/dev/null || host -t NS "$d" 2>/dev/null || echo "  (no dig/host)"
done
echo ===OFFICIAL_COMPOSE_URLS
for u in \
  "https://raw.githubusercontent.com/searxng/searxng/master/container/docker-compose.yml" \
  "https://raw.githubusercontent.com/searxng/searxng/master/container/.env.example" \
  "https://raw.githubusercontent.com/searxng/searxng-docker/master/docker-compose.yaml" \
  "https://raw.githubusercontent.com/searxng/searxng-docker/master/.env" ; do
  printf '%s -> %s\n' "$u" "$(curl -s -o /dev/null -w '%{http_code}' -m 20 "$u")"
done
echo ===CADDY_ROUTE_PRESENT
grep -nE 'handle (/|_)?(search|searx)' /etc/caddy/Caddyfile 2>/dev/null || echo "no search route yet"
echo ===CLOUDFLARED
which cloudflared 2>&1 || echo "cloudflared not installed (will run as container)"
echo ===DONE
