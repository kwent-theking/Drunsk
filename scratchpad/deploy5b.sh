#!/bin/bash
# Drunsk deploy step 5b: place patched main.py with backup, restart bot, verify
set -e
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
F="$V/main.py"
STAMP=$(date +%Y%m%d-%H%M%S)

echo ===MD5_BEFORE
md5sum "$F"
echo ===BACKUP
cp -p "$F" "$F.bak-drunsk-$STAMP"
echo ===PLACE
cp /root/drunsk-patch/main_deploy.py "$F"
chown pelican:pelican "$F"
chmod 644 "$F"
md5sum "$F"
echo ===COMPILE_ON_SERVER
python3 -m py_compile "$F" && echo compiled_ok
echo ===RESTART
docker restart db2fddda-5124-4ee0-bba8-a014edb5553c >/dev/null
sleep 3
docker ps --format "{{.Names}} {{.Status}}" | grep db2fddda || true
echo ===TRACEBACK_WATCH
sleep 25
if docker logs --since 40s db2fddda-5124-4ee0-bba8-a014edb5553c 2>&1 | grep -qi "traceback"; then
  echo "TRACEBACK FOUND — logs:"
  docker logs --since 50s db2fddda-5124-4ee0-bba8-a014edb5553c 2>&1 | tail -30
  exit 3
fi
echo "no traceback"
docker logs --since 40s db2fddda-5124-4ee0-bba8-a014edb5553c 2>&1 | tail -6
echo ===DONE
