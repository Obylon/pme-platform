#!/usr/bin/env bash
# Test de montée en charge (soutenance) : JMeter en mode non graphique + rapport HTML.
# Pendant le test, observer dans Grafana la latence p95, le taux d'erreurs et le HPA
# (kubectl -n pme get hpa -w).
set -euo pipefail
cd "$(dirname "$0")"
HOST="${HOST:-pme.local}"
THREADS="${THREADS:-200}"
DURATION="${DURATION:-240}"   # < durée de vie du jeton (300 s)
JMETER="${JMETER:-$(command -v jmeter || true)}"
[ -x "$HOME/apache-jmeter-5.6.3/bin/jmeter" ] && JMETER="${JMETER_HOME:-$HOME/apache-jmeter-5.6.3}/bin/jmeter"
"$JMETER" --version 2>/dev/null | grep -q "5\." || { echo "Apache JMeter 5.x introuvable (attendu dans ~/apache-jmeter-5.6.3)"; exit 1; }
OUT="results/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

TOKEN=$(curl -sk "https://$HOST/auth/realms/pme/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=pme-demo -d username=carol -d password="${CAROL_PASSWORD:-Carol-Demo-2026}" \
  | jq -r .access_token)

"$JMETER" -n -t pme-load.jmx -Jhost="$HOST" -Jthreads="$THREADS" -Jrampup=60 -Jduration="$DURATION" \
  -Jtoken="$TOKEN" -l "$OUT/results.jtl" -e -o "$OUT/report"
grep -q . "$OUT/results.jtl" || { echo "Aucun résultat : le test n'a pas tourné"; exit 1; }
echo "Rapport : $OUT/report/index.html"
