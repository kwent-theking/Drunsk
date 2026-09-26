#!/bin/bash
echo ===WITH_XFF_FROM_GATE_CONTAINER
docker exec searxng-gate sh -c "wget -qO- --header='X-Forwarded-For: 10.0.0.5' 'http://searxng-core:8091/search?q=fabric+api&format=json'" 2>&1 | head -c 400; echo
echo ===WITH_REALIP
docker exec searxng-gate sh -c "wget -qO- --header='X-Real-IP: 10.0.0.5' 'http://searxng-core:8091/search?q=fabric+api&format=json'" 2>&1 | head -c 400; echo
echo ===LOGS_AFTER
docker logs --tail 6 searxng-core 2>&1
