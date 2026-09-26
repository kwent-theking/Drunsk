#!/bin/bash
# Drunsk deploy step 3: npm install for relay
cd /opt/drunsk-relay
npm install --omit=dev --no-audit --no-fund 2>&1 | tail -5
echo ===VERSIONS
node -e "console.log('ws', require('ws/package.json').version); console.log('mysql2', require('mysql2/package.json').version)" 2>&1
