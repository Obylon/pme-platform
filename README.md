# petitemaisonepouvante.com – plateforme v1 (POC)

Prototype de la plateforme de La Petite Maison de l'Épouvante : espace communautaire de troc avec notifications,
boutique en ligne et abonnement au fanzine. Projet individuel CESI – bloc « Superviser et assurer le développement
des applications logicielles ».

## Suivi du projet

- Backlog : Jira, projet KAN (epics KAN-1 à KAN-3, user stories KAN-4 à KAN-15) – https://tfontep.atlassian.net/browse/KAN-1
- Pipeline : onglet Actions du dépôt
- Dossier de conception (phases 1 à 3, soutenance) : Google Drive « projet individuel Cesi »

## Architecture

| Composant | Rôle | Technologie |
|---|---|---|
| Gateway | Entrée unique HTTPS, redirection HTTP→HTTPS, limitation de débit | Gateway API + Envoy Gateway, cert-manager |
| Keycloak | Authentification OIDC, rôles `member` / `moderator` / `admin` | Keycloak 26 |
| community-service | Annonces de troc / don, modération, événement `listing.published` | Spring Boot 4.1, Java 21 |
| notification-service | Centres d'intérêt, notifications asynchrones, DLQ | Spring Boot 4.1, Spring AMQP |
| shop-service | Catalogue, panier, commandes (stock concurrent), abonnements | Spring Boot 4.1 |
| PostgreSQL | Une base et un rôle par service | CloudNativePG |
| RabbitMQ | Échange `community.events`, files quorum + DLQ | RabbitMQ Cluster Operator |
| Observabilité | Métriques, tableaux de bord, alertes SLO | Prometheus, Grafana (kube-prometheus-stack) |

```
Client ──HTTPS──> Envoy Gateway ──> /auth/realms/*      Keycloak
                                ├─> /api/community/*    community-service ──publie──> RabbitMQ
                                ├─> /api/notifications/* notification-service <──consomme──┘
                                └─> /api/shop/*         shop-service
                  chaque service : JWT validé (JWKS Keycloak) + base PostgreSQL dédiée
```

## Structure du dépôt

```
pme-common/            sécurité JWT (rôles Keycloak) et gestion d'erreurs RFC 9457 partagées
community-service/     US-101 à US-104
notification-service/  US-105, US-106
shop-service/          US-201 à US-204, US-301, US-302
deploy/scripts/        00-cluster → 01-platform → 02-data → 03-app, demo.sh
deploy/platform/       Gateway, CA, PostgreSQL, RabbitMQ, Keycloak (+ realm), alertes Prometheus
deploy/helm/pme-app/   chart des 3 services (pods durcis, HPA, PDB, NetworkPolicies, ServiceMonitor)
deploy/argocd/         application GitOps
load-tests/            plan JMeter + script d'exécution
.github/workflows/     pipeline CI/CD DevSecOps
```

## Tests

```bash
mvn verify          # unitaires (Surefire) + intégration Testcontainers + acceptation Cucumber (Failsafe)
```

- **Unitaires** (`*Test`) : règles métier (propriété des annonces, matching des notifications, stock, renouvellement).
- **Intégration** (`*IT`) : API avec PostgreSQL et RabbitMQ réels (Docker requis), règles 401/403, flux asynchrone,
  DLQ, vente concurrente du dernier exemplaire.
- **Acceptation** (`src/test/resources/features/*.feature`) : critères Gherkin du backlog, exécutés par Cucumber.
- Couverture fusionnée : `*/target/site/jacoco-merged/index.html`.

## Déploiement local (Minikube)

Prérequis : Docker, Minikube, kubectl, Helm 3, Java 21, Maven 3.9, jq ; 4 CPU et 12 Go de RAM (sous WSL 2 : fichier .wslconfig).

En une commande (Linux, macOS, ou Windows avec WSL 2 + Docker Desktop) :

```bash
bash deploy/scripts/up.sh   # cluster, plateforme, données, application, tunnel, pme.local, démo
```

Étape par étape :

```bash
./deploy/scripts/00-cluster.sh           # MINIKUBE_MEMORY=9g et MINIKUBE_CPUS=4 par défaut
minikube tunnel --profile pme            # dans un autre terminal (laisser ouvert)
./deploy/scripts/01-platform.sh
./deploy/scripts/02-data.sh
./deploy/scripts/03-app.sh
kubectl -n envoy-gateway-system get svc  # EXTERNAL-IP de la Gateway (127.0.0.1 avec le pilote Docker)
echo "<EXTERNAL-IP> pme.local" | sudo tee -a /etc/hosts
./deploy/scripts/demo.sh
```

Grafana : `kubectl -n monitoring port-forward svc/monitoring-grafana 3000:80` (identifiants dans le secret
`monitoring-grafana`). Tableaux de bord à importer : 4701 (JVM Micrometer) et 19004 (Spring Boot).

Console d'administration Keycloak (non exposée publiquement) : `kubectl -n pme port-forward svc/keycloak 8080`.

## Test de charge

```bash
./load-tests/run-load-test.sh                     # 200 utilisateurs, 4 min
kubectl -n pme get hpa -w                         # observer la montée en charge horizontale
```

## Pipeline CI/CD

Secrets et variables GitHub à configurer :

| Nom | Type | Usage |
|---|---|---|
| `SONAR_TOKEN` | secret | Analyse SonarCloud / SonarQube (étape ignorée si absent) |
| `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY` | variables | Projet SonarCloud |
| `SONAR_HOST_URL` | variable (optionnelle) | SonarQube auto-hébergé |
| `NVD_API_KEY` | secret | Accélère OWASP Dependency-Check (clé gratuite NIST) |

| Indicateur | Outils dans le pipeline |
|---|---|
| I1 Couverture et réussite des tests | JUnit 5, Testcontainers, Cucumber, JaCoCo → SonarQube |
| I2 Dette technique et duplication | SonarQube (quality gate) |
| I3 Vulnérabilités | Gitleaks, SonarQube, Dependency-Check, Trivy (images + manifestes), OWASP ZAP |
| I4 Performance et disponibilité | JMeter, Prometheus / Grafana, règles d'alerte SLO |

## Comptes de démonstration

Valeurs de test pour l'environnement local uniquement (realm `deploy/platform/realm-pme.json`) :
`alice`, `bob`, `carol` (membres) et `modo` (modérateur). Le client `pme-demo` (direct grant) sert à la
démonstration et aux tests de charge ; il est à supprimer en production.

## Limites connues de la v1

Voir le plan de remédiation : perte possible d'événement si RabbitMQ est indisponible au moment de la publication
(Transactional Outbox à mettre en place), paiement simulé, limitation de débit globale et non par IP, DAST non
authentifié.
