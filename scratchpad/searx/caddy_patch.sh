#!/bin/bash
# Add /search route to Caddyfile (idempotent, with backup), reload Caddy.
# Добавляет роут /search в Caddyfile (идемпотентно, с бэкапом), перезагружает Caddy.
set -e
CF=/etc/caddy/Caddyfile
if grep -q "handle /search\*" "$CF"; then
  echo "caddy already patched"
else
  cp "$CF" "$CF.bak-searxng-$(date +%s)"
  python3 - <<'PYEOF'
path = '/etc/caddy/Caddyfile'
text = open(path, encoding='utf-8').read()
anchor = '    handle /drunsk* {'
assert anchor in text, 'drunsk anchor missing'
route = '''    # SearXNG-гейт: Anthropic Messages API поверх SearXNG для web_search в DSH.
    # Ключ обязателен; сам SearXNG наружу не торчит.
    handle /search* {
        reverse_proxy 127.0.0.1:8092
    }

'''
text = text.replace(anchor, route + anchor, 1)
open(path, 'w', encoding='utf-8').write(text)
print('caddy patched')
PYEOF
fi
caddy validate --config "$CF" >/dev/null 2>&1 && echo "caddy config valid"
systemctl reload caddy
sleep 1
systemctl is-active caddy
