# Démarrage des TP V2 et V3 — Commande Express

Les configurations centralisées sont dans `config-repo/`. Les noms de service et endpoints suivent le projet : `service-catalogue` expose `/products` et `service-utilisateurs` expose `/users`. Les autres services sont enregistrés dans Eureka et routés par la Gateway; leurs endpoints métier restent à implémenter.

## Préparation

À la première initialisation de PostgreSQL, `docker-compose.yml` monte `docker/postgres/init-databases.sql` pour créer une base séparée par service. Si le volume de données PostgreSQL existe déjà, les scripts d'initialisation ne sont pas rejoués automatiquement : créez alors les bases manquantes manuellement ou réinitialisez le volume si ses données peuvent être supprimées.

La configuration PostgreSQL commune utilise `DB_USERNAME`, `DB_PASSWORD` et `DB_PORT`. Copiez `.env.example` vers `.env` puis choisissez un mot de passe privé. Compose publie PostgreSQL sur le port hôte 5433 par défaut, ce qui permet de le faire coexister avec une installation PostgreSQL Windows sur 5432. Pour IntelliJ, définissez ces mêmes variables dans les configurations d'exécution des services. Pour utiliser le PostgreSQL Windows existant, réglez `DB_PORT=5432` et saisissez son mot de passe existant; aucun mot de passe par défaut n'est fourni par le projet.

Depuis la racine du projet, démarrez les dépendances :

```powershell
docker compose up -d postgres redis kafka
```

Le Config Server lit le dépôt Git local `config-repo/` avec `file:../config-repo`. Les fichiers de configuration sont commités dans ce dépôt local sur sa branche `main` avant le démarrage du Config Server. Si tu remplaces l’URI par un dépôt Git distant, mets à jour `CONFIG_REPO_URI`.

## Ordre de démarrage

1. `service-discovery` — port 8761
2. `config-server` — port 8888
3. `service-catalogue` — port 8081
4. `service-utilisateurs` — port 8082
5. `service-commande` — port 8083
6. `service-stock` — port 8084
7. `service-notification` — port 8085
8. `api-gateway` — port 8080

## Vérification

Importez `postman/Commande-Express-V2-V3.postman_collection.json` dans Postman et exécutez les requêtes dans l'ordre. Les requêtes Gateway vérifient les endpoints existants de Catalogue et Utilisateurs. Les autres microservices n'ont pas encore de contrôleurs métier dans le dépôt.

- Eureka : `http://localhost:8761/`
- Config Server : `http://localhost:8888/service-catalogue/default`
- Gateway statique : `http://localhost:8080/products` et `http://localhost:8080/users`
- Gateway dynamique : `http://localhost:8080/service-catalogue/products` et `http://localhost:8080/service-utilisateurs/users`
