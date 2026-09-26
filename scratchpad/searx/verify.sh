#!/bin/bash
# End-to-end verification of the search stack from the VPS itself.
# Сквозная проверка поискового стека с самой ВПС.
set -u
GATE_KEY=$(grep '^GATE_KEY=' /opt/searxng/.env | cut -d= -f2)
BASE=https://xn--d1amilgk.online/search

echo ===1_HEALTH_VIA_CADDY
curl -sk "$BASE/health"; echo

echo ===2_MESSAGES_NO_KEY_401
curl -sk -o /dev/null -w "%{http_code}\n" -X POST "$BASE/messages" \
  -H 'content-type: application/json' -d '{"messages":[{"role":"user","content":"hi"}]}'

echo ===3_MESSAGES_WRONG_KEY_401
curl -sk -o /dev/null -w "%{http_code}\n" -X POST "$BASE/messages" \
  -H "x-api-key: wrongwrongwrongwrongwrongwrongwrongwrong" -H 'content-type: application/json' \
  -d '{"messages":[{"role":"user","content":"hi"}]}'

echo ===4_MESSAGES_EXACT_DSH_SHAPE
# Ровно то тело, что шлёт dsh-web-search-deepseek (проверено по его исходнику).
RESP=$(curl -sk -m 30 -X POST "$BASE/messages" \
  -H "x-api-key: $GATE_KEY" \
  -H 'anthropic-version: 2023-06-01' \
  -H 'content-type: application/json' \
  -H 'accept: application/json' \
  -d '{"model":"deepseek-v4-flash","max_tokens":4096,"messages":[{"role":"user","content":[{"type":"text","text":"Perform a web search for the query: minecraft 1.21 fabric api"}]}],"tools":[{"type":"web_search_20250305","name":"web_search","max_uses":5}]}')
echo "$RESP" | python3 -c "
import json,sys
r=json.load(sys.stdin)
blocks=r.get('content',[])
kinds=[b.get('type') for b in blocks]
res=[b for b in blocks if b.get('type')=='web_search_tool_result']
items=res[0].get('content',[]) if res else []
urls=[i.get('url') for i in items if i.get('type')=='web_search_result']
cites=[c.get('url') for b in blocks if b.get('type')=='text' for c in b.get('citations',[])]
print('block kinds:',kinds)
print('result items:',len(items))
print('first urls:',urls[:3])
print('citations:',len(cites))
print('VERDICT:','PASS' if res and items and urls else 'FAIL')
"
echo ===5_SEARXNG_NOT_PUBLIC
ss -tlnp | grep -E ':8091|:8080.*searx' && echo "ERROR: searxng port exposed" || echo "searxng not exposed (good)"
curl -sk -o /dev/null -w "direct_searxng_8091: %{http_code}\n" -m 5 "http://31.77.147.126:8091/" || true
echo ===DONE
