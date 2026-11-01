#!/usr/bin/env bash
# Construit les images dans le démon Docker de Minikube et déploie l'application avec Helm.
# (En mode GitOps, c'est Argo CD qui applique le chart à partir du dépôt : voir deploy/argocd.)
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
TAG="${TAG:-0.1.0-SNAPSHOT}"

eval "$(minikube -p pme docker-env)"
(cd "$ROOT" && mvn -B -q -DskipTests -Dimage.registry=pme -Dimage.tag="$TAG" package jib:dockerBuild)

helm upgrade --install pme-app "$DIR/helm/pme-app" -n pme --set global.imageRegistry=pme --set global.imageTag="$TAG" --wait --timeout 5m
kubectl -n pme get pods
