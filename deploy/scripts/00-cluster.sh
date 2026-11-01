#!/usr/bin/env bash
# Crée le cluster Kubernetes local de démonstration.
# Calico est requis : le CNI par défaut de Minikube n'applique pas les NetworkPolicies.
set -euo pipefail

minikube start \
  --profile pme \
  --kubernetes-version=v1.34.0 \
  --cpus=4 --memory=12g --disk-size=40g \
  --cni=calico \
  --addons=metrics-server

kubectl apply -f "$(dirname "$0")/../platform/namespace.yaml"
echo "Cluster prêt. Dans un autre terminal, lancez : minikube tunnel --profile pme"
