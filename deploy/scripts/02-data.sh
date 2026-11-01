#!/usr/bin/env bash
# Secrets générés aléatoirement (jamais commités), PostgreSQL, RabbitMQ et Keycloak.
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
NS=pme

secret_basic_auth() { # nom utilisateur
  if ! kubectl -n "$NS" get secret "$1" >/dev/null 2>&1; then
    kubectl -n "$NS" create secret generic "$1" --type=kubernetes.io/basic-auth \
      --from-literal=username="$2" --from-literal=password="$(openssl rand -base64 24)"
    kubectl -n "$NS" label secret "$1" cnpg.io/reload=true
  fi
}

for db in community notification shop keycloak; do
  secret_basic_auth "pg-$db" "$db"
done
secret_basic_auth keycloak-admin admin

kubectl apply -f "$DIR/platform/postgres.yaml"
kubectl -n "$NS" wait --for=condition=Ready cluster/pme-pg --timeout=300s

kubectl apply -f "$DIR/platform/rabbitmq.yaml"
kubectl -n "$NS" wait --for=condition=AllReplicasReady rabbitmqcluster/pme-rabbit --timeout=300s

kubectl -n "$NS" create configmap keycloak-realm --from-file=realm-pme.json="$DIR/platform/realm-pme.json" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "$DIR/platform/keycloak.yaml"
kubectl -n "$NS" rollout status deploy/keycloak --timeout=300s

kubectl apply -f "$DIR/platform/monitoring.yaml"
echo "Données et sécurité prêtes."
