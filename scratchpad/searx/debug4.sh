#!/bin/bash
echo ===HTML_SEARCH
docker exec searxng-gate sh -c "wget -qO- 'http://searxng-core:8091/search?q=test' --header='X-Forwarded-For: 127.0.0.1'" 2>&1 | head -c 300; echo
echo ===JSON_SEARCH_VERBOSE
docker exec searxng-gate sh -c "wget -S -qO- 'http://searxng-core:8091/search?q=test&format=json' --header='X-Forwarded-For: 127.0.0.1'" 2>&1 | head -c 400; echo
echo ===CONFIG_LOAD_CHECK
docker exec searxng-core sh -c "python3 -c \"
import yaml
d=yaml.safe_load(open('/etc/searxng/settings.yml'))
print('search.formats =', d.get('search',{}).get('formats'))
print('server keys =', list(d.get('server',{}).keys()))
\"" 2>&1
echo ===EFFECTIVE_FORMATS_VIA_APP
docker exec searxng-core sh -c "python3 -c \"
from searx import settings_loader
s = settings_loader.load_settings(load_user_settings=True)[0]
print('formats:', s['search']['formats'])
\"" 2>&1 | tail -3
echo ===CORE_LOGS
docker logs --tail 12 searxng-core 2>&1 | grep -v "^\[INFO\]" | head -12
