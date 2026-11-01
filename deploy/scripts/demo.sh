#!/usr/bin/env bash
# Démonstration de bout en bout (soutenance) : troc + notification, commande, abonnement.
# Utilise le client "pme-demo" (Direct Access Grant), réservé à la démonstration.
set -euo pipefail
HOST="${HOST:-pme.local}"
CA="$(mktemp)"
kubectl -n cert-manager get secret pme-root-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > "$CA"
CURL=(curl -sS --cacert "$CA")

token() { # utilisateur mot_de_passe
  "${CURL[@]}" "https://$HOST/auth/realms/pme/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=pme-demo -d username="$1" -d password="$2" | jq -r .access_token
}

ALICE=$(token alice "${ALICE_PASSWORD:-Alice-Demo-2026}")
BOB=$(token bob "${BOB_PASSWORD:-Bob-Demo-2026}")

echo "== 1. Sans jeton : 401 attendu"
"${CURL[@]}" -o /dev/null -w "%{http_code}\n" -X POST "https://$HOST/api/community/listings" -H 'Content-Type: application/json' -d '{}'

echo "== 2. Bob déclare ses centres d'intérêt (FIGURINE, orc)"
"${CURL[@]}" -X PUT "https://$HOST/api/notifications/preferences/me" -H "Authorization: Bearer $BOB" \
  -H 'Content-Type: application/json' -d '{"categories":["FIGURINE"],"keywords":["orc"],"enabled":true}' | jq .

echo "== 3. Alice publie une annonce de troc"
"${CURL[@]}" -X POST "https://$HOST/api/community/listings" -H "Authorization: Bearer $ALICE" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Figurine Orc édition festival","category":"FIGURINE","type":"EXCHANGE","keywords":["orc"]}' | jq .

sleep 2
echo "== 4. Bob a reçu une notification (traitement asynchrone via RabbitMQ)"
"${CURL[@]}" "https://$HOST/api/notifications/me" -H "Authorization: Bearer $BOB" | jq .

echo "== 5. Bob tente de modérer : 403 attendu"
ID=$("${CURL[@]}" "https://$HOST/api/community/listings" | jq -r '.content[0].id')
"${CURL[@]}" -o /dev/null -w "%{http_code}\n" -X POST "https://$HOST/api/community/listings/$ID/moderation" \
  -H "Authorization: Bearer $BOB" -H 'Content-Type: application/json' -d '{"reason":"test"}'

echo "== 6. Alice achète 2 figurines exclusives Evil Ed"
"${CURL[@]}" -X POST "https://$HOST/api/shop/cart/items" -H "Authorization: Bearer $ALICE" \
  -H 'Content-Type: application/json' -d '{"productId":"11111111-1111-1111-1111-111111111101","quantity":2}' | jq .
"${CURL[@]}" -X POST "https://$HOST/api/shop/orders" -H "Authorization: Bearer $ALICE" | jq .

echo "== 7. Alice s'abonne au fanzine numérique"
"${CURL[@]}" -X POST "https://$HOST/api/shop/subscriptions" -H "Authorization: Bearer $ALICE" \
  -H 'Content-Type: application/json' -d '{"plan":"DIGITAL"}' | jq .
