#!/usr/bin/env bash
# Déploiement complet en une commande (poste de démo, y compris Windows + WSL 2 + Docker Desktop) :
#   bash deploy/scripts/up.sh
# Enchaîne cluster -> plateforme -> données -> application, ouvre le tunnel, déclare pme.local et lance la démo.
# Relançable : les étapes déjà faites sont réappliquées sans dommage.
set -euo pipefail
SCRIPTS="$(cd "$(dirname "$0")" && pwd)"
step() { echo; echo "===== $* ====="; }

step "0/5 Vérification des outils"
for tool in docker minikube kubectl helm mvn java jq openssl; do
  command -v "$tool" >/dev/null || { echo "Outil manquant : $tool"; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker ne répond pas : lancez Docker Desktop (intégration WSL activée)."; exit 1; }
echo "Mot de passe Ubuntu demandé une seule fois (tunnel réseau et fichier /etc/hosts) :"
sudo -v
# Garde l'autorisation sudo active pendant tout le script (le build dépasse le délai de sudo).
( while true; do sudo -n true 2>/dev/null; sleep 60; done ) &
KEEPALIVE=$!
trap 'kill $KEEPALIVE 2>/dev/null || true' EXIT

step "1/5 Cluster Minikube"
if minikube -p pme status >/dev/null 2>&1; then echo "Cluster déjà démarré."; else bash "$SCRIPTS/00-cluster.sh"; fi

step "2/5 Plateforme (Gateway, cert-manager, opérateurs, monitoring)"
bash "$SCRIPTS/01-platform.sh"

step "3/5 Données et sécurité (PostgreSQL, RabbitMQ, Keycloak)"
bash "$SCRIPTS/02-data.sh"

step "4/5 Application (build des images + Helm)"
bash "$SCRIPTS/03-app.sh"

step "5/5 Accès réseau"
sudo -v
if ! pgrep -f "minikube tunnel" >/dev/null; then
  # stdin fermé : le tunnel ne peut pas demander de mot de passe dans ce terminal
  nohup minikube tunnel -p pme < /dev/null > "$HOME/pme-tunnel.log" 2>&1 &
  echo "Tunnel lancé en arrière-plan (journal : ~/pme-tunnel.log)."
  echo "S'il échoue, lancez-le dans un 2e terminal : minikube tunnel -p pme"
fi
IP=""
for _ in $(seq 1 60); do
  IP=$(kubectl -n envoy-gateway-system get svc -l gateway.envoyproxy.io/owning-gateway-name \
        -o jsonpath='{.items[0].status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
  [ -n "$IP" ] && break
  sleep 5
done
[ -n "$IP" ] || { echo "Pas d'adresse pour la Gateway : voir ~/pme-tunnel.log"; exit 1; }
if grep -qE '[[:space:]]pme\.local$' /etc/hosts; then
  sudo sed -i -E "s/^.*[[:space:]]pme\.local$/$IP pme.local/" /etc/hosts
else
  echo "$IP pme.local" | sudo tee -a /etc/hosts >/dev/null
fi
echo "pme.local -> $IP"

echo
echo "Plateforme prête. Démonstration :"
bash "$SCRIPTS/demo.sh"
