#!/usr/bin/env bash
# Installe les briques de plateforme (versions épinglées, à mettre à jour via Renovate/Dependabot).
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"

ENVOY_GATEWAY_VERSION=v1.6.5
CERT_MANAGER_VERSION=v1.18.2
CNPG_CHART_VERSION=0.29.0
RABBITMQ_OPERATOR_VERSION=v2.23.2
KPS_CHART_VERSION=77.0.0

helm repo add jetstack https://charts.jetstack.io
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# 1. Envoy Gateway : implémentation de Gateway API (remplace Ingress NGINX, retiré en mars 2026)
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm --version "$ENVOY_GATEWAY_VERSION" \
  -n envoy-gateway-system --create-namespace --wait

# 2. cert-manager avec la prise en charge de Gateway API (certificats TLS pour les listeners HTTPS)
helm upgrade --install cert-manager jetstack/cert-manager --version "$CERT_MANAGER_VERSION" \
  -n cert-manager --create-namespace --wait \
  --set crds.enabled=true \
  --set config.apiVersion=controller.config.cert-manager.io/v1alpha1 \
  --set config.kind=ControllerConfiguration \
  --set config.enableGatewayAPI=true

# 3. Opérateur PostgreSQL CloudNativePG
helm upgrade --install cnpg cnpg/cloudnative-pg --version "$CNPG_CHART_VERSION" \
  -n cnpg-system --create-namespace --wait

# 4. Opérateur RabbitMQ (officiel, maintenu par l'équipe RabbitMQ)
kubectl apply -f "https://github.com/rabbitmq/cluster-operator/releases/download/${RABBITMQ_OPERATOR_VERSION}/cluster-operator.yml"
kubectl -n rabbitmq-system rollout status deploy/rabbitmq-cluster-operator --timeout=180s

# 5. Observabilité : Prometheus, Alertmanager, Grafana
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack --version "$KPS_CHART_VERSION" \
  -n monitoring --create-namespace --wait \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set prometheus.prometheusSpec.ruleSelectorNilUsesHelmValues=false

# 6. Autorité de certification interne, Gateway HTTPS et limitation de débit
kubectl apply -f "$DIR/platform/issuers.yaml"
kubectl -n cert-manager wait --for=condition=Ready certificate/pme-root-ca --timeout=120s
kubectl apply -f "$DIR/platform/gateway.yaml"
echo "Plateforme installée."
