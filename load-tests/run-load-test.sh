#!/usr/bin/env bash
# Test de montée en charge (soutenance) : JMeter en mode non graphique + rapport HTML.
# Pendant le test, observer dans Grafana la latence p95, le taux d'erreurs et le HPA
# (kubectl -n pme get hpa -w).
set -euo pipefail
cd "$(dirname "$0")"
HOST="${HOST:-pme.local}"
THREADS="${THREADS:-200}"
DURATION="${DURATION:-240}"   # < durée de vie du jeton (300 s)
OUT="results/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

TOKEN=$(curl -sk "https://$HOST/auth/realms/pme/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=pme-demo -d username=carol -d password="${CAROL_PASSWORD:-Carol-Demo-2026}" \
  | jq -r .access_token)

jmeter -n -t pme-load.jmx -Jhost="$HOST" -Jthreads="$THREADS" -Jrampup=60 -Jduration="$DURATION" \
  -Jtoken="$TOKEN" -l "$OUT/results.jtl" -e -o "$OUT/report"
echo "Rapport : $OUT/report/index.html"
