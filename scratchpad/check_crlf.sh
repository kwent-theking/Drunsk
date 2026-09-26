#!/bin/bash
F=/var/lib/pelican/volumes/db2fddda-5124-4ee0-bba8-a014edb5553c/main.py
md5sum "$F"
python3 - "$F" <<'EOF'
import sys
data = open(sys.argv[1], 'rb').read()
print('crlf:', data.count(b'\r\n'))
print('bare_lf:', data.replace(b'\r\n', b'').count(b'\n'))
print('size:', len(data))
EOF
# also check the other copy
md5sum /opt/drunbot/main.py 2>/dev/null || echo "no /opt/drunbot copy"
