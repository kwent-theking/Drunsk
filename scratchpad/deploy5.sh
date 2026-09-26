#!/bin/bash
# Drunsk deploy step 5: patch bot main.py with !паспорт, restart, verify
set -e
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
F="$V/main.py"

echo ===MD5_BEFORE
md5sum "$F"

echo ===BACKUP
cp -p "$F" "$F.bak-drunsk-$(date +%Y%m%d-%H%M%S)"

echo ===PATCH
python3 /root/drunsk-patch/patch_passport.py "$F"

echo ===PY_COMPILE
python3 -m py_compile "$F"
echo compiled_ok

echo ===RESTART
docker restart "$V" >/dev/null 2>&1 || docker restart db2fddda-5124-4ee0-bba8-a014edb5553c >/dev/null
sleep 3
docker ps --format "{{.Names}} {{.Status}}" | grep db2fddda || true

echo ===TRACEBACK_WATCH
sleep 25
if docker logs --since 30s db2fddda-5124-4ee0-bba8-a014edb5553c 2>&1 | grep -qi "traceback"; then
  echo "TRACEBACK FOUND — logs:"
  docker logs --since 40s db2fddda-5124-4ee0-bba8-a014edb5553c 2>&1 | tail -30
  exit 3
fi
echo "no traceback"
docker logs --since 30s db2fddda-5124-4ee0-bba8-a014edb5553c 2>&1 | tail -5
echo ===DONE
