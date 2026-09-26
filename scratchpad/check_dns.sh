#!/bin/bash
echo ===DNS
getent hosts xn--d1amilgk.online || echo "no DNS for punycode domain"
getent hosts 31.77.147.126.sslip.io || echo "no DNS for sslip"
echo ===CURL_SNI
curl -sk --resolve xn--d1amilgk.online:443:127.0.0.1 -o /dev/null -w "sni-punycode: %{http_code}\n" https://xn--d1amilgk.online/drunsk
curl -sk --resolve 31.77.147.126.sslip.io:443:127.0.0.1 -o /dev/null -w "sni-sslip: %{http_code}\n" https://31.77.147.126.sslip.io/drunsk
echo ===CERTS
ls /var/lib/caddy/.local/share/caddy/certificates/ 2>/dev/null
