#!/usr/bin/env bash
# Expérimentations de la Phase 2.2 (KAN-20), exécutées automatiquement sur le cluster de démo.
#   bash deploy/scripts/experiments.sh            # E1, E2, E3, E5 (environ 5 min)
#   FULL=1 bash deploy/scripts/experiments.sh     # + jeton expiré (E3) et alerte PmeServiceDown (E5) : environ 10 min de plus
# Prérequis : plateforme déployée, minikube tunnel actif, pme.local dans /etc/hosts.
# Le tableau final est aussi enregistré dans ~/pme-experiences-<date>.txt (à envoyer pour le dossier).
set -uo pipefail
HOST="${HOST:-pme.local}"
NS=pme
OUT="$HOME/pme-experiences-$(date +%Y%m%d-%H%M).txt"
CA="$(mktemp)"
kubectl -n cert-manager get secret pme-root-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > "$CA"
CURL=(curl -sS --max-time 20 --cacert "$CA")
RESULTS=()
PF_PIDS=()
cleanup() { for p in "${PF_PIDS[@]:-}"; do kill "$p" 2>/dev/null; done; rm -f "$CA"; }
trap cleanup EXIT

record() { # id essai attendu observé verdict
  RESULTS+=("$1 | $2 | $3 | $4 | $5")
  printf '  %-3s %-45s attendu: %-28s observé: %s [%s]\n' "$1" "$2" "$3" "$4" "$5"
}
verdict() { [ "$1" = "$2" ] && echo OK || echo KO; }
now_ms() { local t="${EPOCHREALTIME/,/.}"; echo $(( ${t%.*} * 1000 + 10#${t#*.} / 1000 )); }  # sans date +%3N (non géré partout)
token() { # utilisateur mot_de_passe
  "${CURL[@]}" "https://$HOST/auth/realms/pme/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=pme-demo -d username="$1" -d password="$2" | jq -r '.access_token // empty'
}
code() { "${CURL[@]}" -o /dev/null -w '%{http_code}' "$@"; }
publish() { # jeton titre -> id de l'annonce
  "${CURL[@]}" -X POST "https://$HOST/api/community/listings" -H "Authorization: Bearer $1" \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"$2\",\"category\":\"FIGURINE\",\"type\":\"EXCHANGE\",\"keywords\":[\"orc\"]}" | jq -r .id
}
notif_count() { "${CURL[@]}" "https://$HOST/api/notifications/me?size=1" -H "Authorization: Bearer $1" | jq -r .totalElements; }
queue_depth() { # nom de file
  kubectl -n "$NS" exec pme-rabbit-server-0 -c rabbitmq -- rabbitmqctl list_queues -q name messages 2>/dev/null \
    | awk -v q="$1" '$1 == q { print $2 }'
}

reachable() { curl -sk -o /dev/null --max-time 5 "https://$HOST/api/shop/products"; }
ensure_up() { # attend que la Gateway réponde (60 s), sinon arrête le script
  for _ in $(seq 1 30); do reachable && return 0; sleep 2; done
  echo; echo "https://$HOST ne répond plus : vérifiez la fenêtre du tunnel (sudo -v puis minikube tunnel -p pme --cleanup) et relancez."
  exit 1
}
ensure_up
ALICE=$(token alice Alice-Demo-2026); BOB=$(token bob Bob-Demo-2026); MODO=$(token modo 'Modo-Demo-2026!')
[ -n "$ALICE" ] && [ -n "$BOB" ] && [ -n "$MODO" ] || { echo "Impossible d'obtenir les jetons : la plateforme répond-elle sur https://$HOST ?"; exit 1; }

echo "===== E1 - Gateway API, Envoy Gateway, cert-manager ====="
c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://$HOST/api/shop/products")
record E1 "HTTP -> HTTPS" "301" "$c" "$(verdict "$c" 301)"
issuer=$(echo | openssl s_client -connect "$HOST:443" -servername "$HOST" -CAfile "$CA" 2>/dev/null | openssl x509 -noout -issuer 2>/dev/null | sed 's/^issuer=//')
record E1 "Émetteur du certificat" "PME Local Root CA" "${issuer:-aucun}" "$(echo "$issuer" | grep -qi 'PME' && echo OK || echo KO)"
if echo | openssl s_client -connect "$HOST:443" -servername "$HOST" -tls1_1 -cipher 'DEFAULT@SECLEVEL=0' >/dev/null 2>&1; then t11="accepté"; else t11="refusé"; fi
if echo | openssl s_client -connect "$HOST:443" -servername "$HOST" -tls1_2 >/dev/null 2>&1; then t12="accepté"; else t12="refusé"; fi
record E1 "TLS 1.1 / TLS 1.2" "refusé / accepté" "$t11 / $t12" "$([ "$t11/$t12" = "refusé/accepté" ] && echo OK || echo KO)"
burst=$(seq 3000 | xargs -P 100 -I{} curl -sk -o /dev/null -w '%{http_code}\n' --max-time 20 "https://$HOST/api/shop/products" | sort | uniq -c | awk '{printf "%s×%s ", $1, $2}')
record E1 "Rafale de 3000 requêtes (100 en parallèle)" "429 au-delà de 1000 req/s" "$burst" "$(echo "$burst" | grep -q '429' && echo OK || echo 'seuil non atteint')"

ensure_up
echo "===== E2 - RabbitMQ ====="
before=$(notif_count "$BOB"); t0=$(now_ms)
publish "$ALICE" "E2 délai orc $(date +%s)" >/dev/null
delay="> 10 s"
for _ in $(seq 1 100); do
  if [ "$(notif_count "$BOB")" -gt "$before" ]; then delay="$(( $(now_ms) - t0 )) ms"; break; fi
  sleep 0.1
done
record E2 "Délai publication -> notification" "< 2 s" "$delay" "$([[ "$delay" =~ ms$ ]] && [ "${delay% ms}" -lt 2000 ] && echo OK || echo KO)"

RMQ_USER=$(kubectl -n "$NS" get secret pme-rabbit-default-user -o jsonpath='{.data.username}' | base64 -d)
RMQ_PASS=$(kubectl -n "$NS" get secret pme-rabbit-default-user -o jsonpath='{.data.password}' | base64 -d)
kubectl -n "$NS" port-forward svc/pme-rabbit 15672:15672 >/dev/null 2>&1 & PF_PIDS+=($!); sleep 4
dlq_before=$(queue_depth notification.listing-published.dlq); dlq_before=${dlq_before:-0}
curl -s -u "$RMQ_USER:$RMQ_PASS" -H 'Content-Type: application/json' -X POST \
  http://localhost:15672/api/exchanges/%2F/community.events/publish \
  -d '{"properties":{"content_type":"application/json"},"routing_key":"listing.published","payload":"{\"listingId\":\"pas-un-uuid\"}","payload_encoding":"string"}' >/dev/null
dlq_after=$dlq_before
for _ in $(seq 1 30); do dlq_after=$(queue_depth notification.listing-published.dlq); [ "${dlq_after:-0}" -gt "$dlq_before" ] && break; sleep 1; done
record E2 "Message empoisonné" "en DLQ après 3 tentatives" "DLQ : $dlq_before -> ${dlq_after:-?}" "$([ "${dlq_after:-0}" -gt "$dlq_before" ] && echo OK || echo KO)"

before=$(notif_count "$BOB")
kubectl -n "$NS" scale deploy/notification-service --replicas=0 >/dev/null
kubectl -n "$NS" wait --for=delete pod -l app.kubernetes.io/name=notification-service --timeout=90s >/dev/null 2>&1
for i in 1 2 3; do publish "$ALICE" "E2 consommateur arrêté $i $(date +%s)" >/dev/null; done
sleep 3; waiting=$(queue_depth notification.listing-published)
kubectl -n "$NS" scale deploy/notification-service --replicas=2 >/dev/null
kubectl -n "$NS" rollout status deploy/notification-service --timeout=180s >/dev/null
after=$before
for _ in $(seq 1 60); do after=$(notif_count "$BOB"); [ "$after" -ge $((before + 3)) ] && break; sleep 1; done
record E2 "Consommateur arrêté, 3 publications" "3 en attente puis consommés" "${waiting:-?} en attente, $((after - before)) notifications reçues" "$([ "${waiting:-0}" -ge 3 ] && [ $((after - before)) -ge 3 ] && echo OK || echo KO)"
record E2 "Perte d'un nœud RabbitMQ (3 nœuds)" "0 message perdu" "non exécuté (1 nœud sur le poste de démo)" "-"

ensure_up
echo "===== E3 - Keycloak et Spring Security ====="
c=$(code -X POST "https://$HOST/api/community/listings" -H 'Content-Type: application/json' -d '{}')
record E3 "Annonce sans jeton" "401" "$c" "$(verdict "$c" 401)"
# Un caractère au milieu de la signature (le dernier caractère base64url contient des bits de remplissage)
i=$(( ${#BOB} - 20 )); ch="${BOB:$i:1}"
BAD="${BOB:0:$i}$([ "$ch" = A ] && echo B || echo A)${BOB:$((i + 1))}"
c=$(code -X POST "https://$HOST/api/community/listings" -H "Authorization: Bearer $BAD" -H 'Content-Type: application/json' -d '{}')
record E3 "Jeton à la signature altérée" "401" "$c" "$(verdict "$c" 401)"
LID=$(publish "$ALICE" "E3 annonce d'Alice $(date +%s)")
c=$(code -X POST "https://$HOST/api/community/listings/$LID/moderation" -H "Authorization: Bearer $BOB" -H 'Content-Type: application/json' -d '{"reason":"Essai E3"}')
record E3 "Modération par un membre" "403" "$c" "$(verdict "$c" 403)"
c=$(code -X DELETE "https://$HOST/api/community/listings/$LID" -H "Authorization: Bearer $BOB")
record E3 "Retrait de l'annonce d'un autre membre" "403" "$c" "$(verdict "$c" 403)"
c=$(code -X POST "https://$HOST/api/community/listings/$LID/moderation" -H "Authorization: Bearer $MODO" -H 'Content-Type: application/json' -d '{"reason":"Essai E3"}')
record E3 "Modération par modo" "200" "$c" "$(verdict "$c" 200)"
c=$(code "https://$HOST/auth/admin/")
record E3 "Console d'administration Keycloak" "404 (non routée)" "$c" "$(verdict "$c" 404)"
claims=$(echo "$BOB" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -c '{iss, aud, roles: .realm_access.roles}' 2>/dev/null)
record E3 "Contenu du jeton de Bob" "iss public, aud pme-api, rôle member" "${claims:-illisible}" "$(echo "$claims" | grep -q pme-api && echo OK || echo KO)"
ensure_up
for _ in 1 2 3 4 5 6; do token carol mauvais-mot-de-passe >/dev/null; done
locked=$(token carol Carol-Demo-2026)
record E3 "6 mots de passe erronés pour carol" "compte bloqué" "$([ -z "$locked" ] && echo 'bon mot de passe refusé (bloqué)' || echo 'non bloqué')" "$([ -z "$locked" ] && echo OK || echo KO)"
if [ "${FULL:-0}" = 1 ]; then
  echo "  (attente de l'expiration du jeton : 5 min 10 s)"; OLD=$(token bob Bob-Demo-2026); sleep 310
  c=$(code -X POST "https://$HOST/api/community/listings" -H "Authorization: Bearer $OLD" -H 'Content-Type: application/json' -d '{}')
  record E3 "Jeton expiré" "401" "$c" "$(verdict "$c" 401)"
else
  record E3 "Jeton expiré" "401" "non exécuté (FULL=1)" "-"
fi

ensure_up
echo "===== E5 - Prometheus et Grafana ====="
kubectl -n monitoring port-forward svc/monitoring-kube-prometheus-prometheus 9090:9090 >/dev/null 2>&1 & PF_PIDS+=($!); sleep 4
prom() { curl -s --get http://localhost:9090/api/v1/query --data-urlencode "query=$1"; }
up=$(prom 'up{job=~".+-service"}' | jq -r '[.data.result[] | select(.value[1]=="1") | .metric.job] | unique | join(", ")')
record E5 "Cibles Prometheus UP" "3 services" "${up:-aucune}" "$([ "$(echo "$up" | tr ',' '\n' | grep -c service)" -ge 3 ] && echo OK || echo KO)"
p95=$(prom 'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[15m])))' | jq -r '.data.result[0].value[1] // empty')
record E5 "Requête PromQL p95 (15 min)" "valeur calculée" "${p95:+$(awk -v v="$p95" 'BEGIN{printf "%.0f ms", v*1000}')}" "$([ -n "$p95" ] && echo OK || echo KO)"
rules=$(prom 'count(ALERTS) or vector(0)' >/dev/null; curl -s http://localhost:9090/api/v1/rules | jq -r '[.data.groups[].rules[] | select(.name|startswith("Pme")) | .name] | join(", ")')
record E5 "Règles d'alerte chargées" "PmeServiceDown, ..." "${rules:-aucune}" "$(echo "$rules" | grep -q PmeServiceDown && echo OK || echo KO)"
if [ "${FULL:-0}" = 1 ]; then
  kubectl -n "$NS" scale deploy/shop-service --replicas=0 >/dev/null; start=$(date +%s); fired="non déclenchée en 5 min"
  for _ in $(seq 1 60); do
    if prom 'ALERTS{alertname="PmeServiceDown",alertstate="firing"}' | jq -e '.data.result | length > 0' >/dev/null; then fired="$(( $(date +%s) - start )) s"; break; fi
    sleep 5
  done
  kubectl -n "$NS" scale deploy/shop-service --replicas=2 >/dev/null
  record E5 "Alerte PmeServiceDown (shop arrêté)" "~2 min (+ intervalle de collecte)" "$fired" "$([[ "$fired" =~ s$ ]] && echo OK || echo KO)"
else
  record E5 "Alerte PmeServiceDown" "~2 min" "non exécuté (FULL=1)" "-"
fi

echo "===== E1 (fin) - Renouvellement du certificat ====="
kubectl -n "$NS" delete secret pme-tls --wait=true >/dev/null 2>&1
start=$(date +%s); recreated="non recréé en 120 s"
for _ in $(seq 1 120); do
  if kubectl -n "$NS" get secret pme-tls >/dev/null 2>&1; then recreated="$(( $(date +%s) - start )) s"; break; fi
  sleep 1
done
record E1 "Recréation automatique de pme-tls" "< 30 s" "$recreated" "$([[ "$recreated" =~ ^[0-9]+\ s$ ]] && [ "${recreated% s}" -lt 30 ] && echo OK || echo KO)"
echo "  (attente du rechargement du certificat par Envoy)"; ensure_up
record E6 "Bascule PostgreSQL (3 instances)" "< 30 s, reconnexion" "non exécuté (mémoire du poste de démo)" "-"

{
  echo "Expérimentations Phase 2.2 - $(date '+%d/%m/%Y %H:%M') - $(minikube version --short 2>/dev/null) - $(kubectl version 2>/dev/null | grep Server)"
  echo "Exp | Essai | Attendu | Observé | Verdict"
  printf '%s\n' "${RESULTS[@]}"
} > "$OUT"
echo
echo "Résultats enregistrés dans $OUT"
echo "Pour les envoyer : cat $OUT"
