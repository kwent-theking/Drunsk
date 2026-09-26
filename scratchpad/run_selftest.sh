#!/bin/bash
# Drunsk selftest against drunsk_test DB, local ws server
V=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c
set -a; . "$V/.env" 2>/dev/null; set +a
cd /opt/drunsk-relay
export DB_NAME=drunsk_test
# The bot's .env points at the docker bridge (172.18.0.1); the MySQL grant is
# for 127.0.0.1. / В .env бота docker-мост, а грант MySQL выписан на 127.0.0.1.
export DB_HOST=127.0.0.1
export DRUNSK_VERBOSE=1
node selftest.js 2>&1
