# Commande Express — démarrage local

## Prérequis

- Docker Desktop avec le moteur Linux démarré
- Java 21 et Maven
- PowerShell (pour le test des dépendances Docker)

## Démarrer les dépendances Docker

Dans un terminal à la racine du projet :

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d --wait
```

Compose crée PostgreSQL, Redis, Kafka, RabbitMQ et Mailpit, puis attend qu’ils soient prêts. PostgreSQL conserve ses données dans le volume `postgres-data` et crée automatiquement les bases `service_commande`, `service_stock`, `service_notification`, `service_catalogue` et `service_utilisateurs` au premier démarrage. Mailpit sert de serveur SMTP local sans TLS ni authentification pour le service notification; son interface est disponible sur <http://localhost:8025>.

Les identifiants locaux par défaut sont `postgres/postgres` pour PostgreSQL et `appuser/app_password` pour RabbitMQ. Ils peuvent être remplacés dans `.env`. Ce sont des valeurs de développement à changer avant tout déploiement partagé. Les services Java lancés directement sur l’hôte doivent recevoir les mêmes valeurs, par exemple dans PowerShell :

```powershell
$env:DB_PASSWORD = 'postgres'
$env:RABBITMQ_USERNAME = 'appuser'
$env:RABBITMQ_PASSWORD = 'app_password'
```

## Charger la configuration centralisée

Les services Spring importent leur configuration depuis `config-repo` via Config Server lorsqu’il est disponible. Démarre le serveur dans un terminal :

```powershell
mvn -pl config-server spring-boot:run
```

Puis démarre les autres services dans des terminaux séparés. Les valeurs PostgreSQL et RabbitMQ de `config-repo` correspondent aux conteneurs Docker; le mot de passe PostgreSQL a le même défaut local `postgres`.

## Vérifier l’infrastructure et RabbitMQ

```powershell
.\scripts\test-infrastructure.ps1
```

Ce script démarre les conteneurs si nécessaire, vérifie les cinq bases PostgreSQL, puis publie et récupère un événement dans un échange temporaire RabbitMQ. Le test ne modifie pas la file des notifications de commande. Les conteneurs restent actifs après le test.

Pour vérifier uniquement des conteneurs déjà démarrés :

```powershell
.\scripts\test-infrastructure.ps1 -SkipStart
```

## Exécuter les tests Java

```powershell
mvn test
```

Ces tests automatisés utilisent les dépendances isolées prévues par les profils de test. Le script `test-infrastructure.ps1` vérifie séparément les vrais conteneurs PostgreSQL et RabbitMQ.

## Démonstration V6

Les scénarios de validation Feign nominale, d’indisponibilité distante (retry et circuit breaker) et de rejeu idempotent sont décrits dans [`postman/V6-demonstration.md`](postman/V6-demonstration.md). La création `POST /orders` exige maintenant l’en-tête `Idempotency-Key`.

## Arrêter les conteneurs

```powershell
docker compose stop
```

Les données PostgreSQL restent dans le volume. Pour supprimer les conteneurs **et effacer les données de la base**, utiliser explicitement :

```powershell
docker compose down -v
```
