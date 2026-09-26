#!/bin/bash
GATE_KEY=$(grep '^GATE_KEY=' /opt/searxng/.env | cut -d= -f2)
BASE=https://xn--d1amilgk.online/search
echo ===RAW_RESPONSE
curl -sk -m 30 -X POST "$BASE/messages" \
  -H "x-api-key: $GATE_KEY" \
  -H 'anthropic-version: 2023-06-01' \
  -H 'content-type: application/json' -H 'accept: application/json' \
  -d '{"model":"deepseek-v4-flash","max_tokens":4096,"messages":[{"role":"user","content":[{"type":"text","text":"Perform a web search for the query: minecraft fabric api"}]}],"tools":[{"type":"web_search_20250305","name":"web_search","max_uses":5}]}' \
  | head -c 2000; echo
echo ===GATE_LOGS
docker logs --tail 25 searxng-gate 2>&1
echo ===SEARXNG_DIRECT_JSON_FROM_GATE_CONTAINER
docker exec searxng-gate wget -qO- 'http://searxng-core:8091/search?q=test&format=json' 2>&1 | head -c 600; echo
