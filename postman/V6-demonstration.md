# Démonstration V6 — résilience et idempotence

## Préparation

Démarrer Eureka, Catalogue, Utilisateurs, Stock, PostgreSQL et `service-commande` selon le README. Vérifier que l’API Commande répond sur `http://localhost:8083`. Choisir un utilisateur actif et un produit `DISPONIBLE` avec une quantité de stock suffisante.

Valeurs à adapter dans les requêtes :

```text
userId = 1
productId = 1
quantity = 1
```

## 1. Parcours nominal

Envoyer `POST http://localhost:8083/orders` avec ces en-têtes et ce corps :

```http
Content-Type: application/json
Idempotency-Key: demo-v6-order-001
```

```json
{
  "userId": 1,
  "lignes": [{ "productId": 1, "quantite": 1 }]
}
```

Attendu : `201 Created`, en-tête `Idempotency-Replayed: false`, commande confirmée après validation Feign et réservation du stock.

## 2. Rejeu idempotent

Renvoyer exactement le même `POST`, avec **la même clé et le même corps**. Attendu : `200 OK`, le même identifiant de commande et `Idempotency-Replayed: true`. Le stock n’est réservé qu’une fois. Le même en-tête avec un corps différent doit répondre `409 Conflict`.

La clé est enregistrée sur la commande avec une contrainte unique en base. Chaque client doit générer une clé stable par opération logique de création et réutiliser cette clé lorsqu’il rejoue une requête après un délai d’attente.

## 3. Indisponibilité d’un service distant et circuit ouvert

1. Arrêter `service-utilisateurs` dans IntelliJ.
2. Envoyer trois créations avec des clés différentes et des corps valides. Les validations Feign sont retentées une fois; les réponses attendues sont `503 Service Unavailable` et aucune commande ne doit être créée.
3. La protection du client Utilisateurs ouvre son circuit après le seuil configuré. Une requête suivante reçoit rapidement `503`, sans tentative réseau.
4. Redémarrer `service-utilisateurs`. Après la période d’ouverture configurée (10 secondes), refaire une création avec une nouvelle clé : elle doit fonctionner.

Le même exercice peut être répété en arrêtant `service-catalogue`; les appels Utilisateurs et Catalogue ont des circuits séparés. Une indisponibilité avec délai de réponse est bornée par `FEIGN_CONNECT_TIMEOUT_MS` (1000 ms par défaut) et `FEIGN_READ_TIMEOUT_MS` (2000 ms par défaut).

## Réglages

Les appels de validation sont configurés à deux tentatives maximum (appel initial compris), avec 200 ms entre tentatives; seules les erreurs transitoires Feign (timeout, 429 et 5xx) sont retentées. Les autres erreurs, par exemple un 404 de référence, ne sont pas retentées. Les circuits Utilisateurs et Catalogue sont indépendants: fenêtre de cinq appels, seuil minimal de trois appels et ouverture au seuil de 50% de défaillances; un circuit ouvert reste ouvert 10 secondes. Les appels Feign ont un timeout de connexion de 1000 ms et de lecture de 2000 ms par défaut. Les variables `FEIGN_CONNECT_TIMEOUT_MS`, `FEIGN_READ_TIMEOUT_MS`, `FEIGN_RETRY_MAX_ATTEMPTS`, `FEIGN_RETRY_WAIT`, `FEIGN_CIRCUIT_BREAKER_WINDOW`, `FEIGN_CIRCUIT_BREAKER_MIN_CALLS`, `FEIGN_CIRCUIT_BREAKER_FAILURE_RATE` et `FEIGN_CIRCUIT_BREAKER_OPEN_WAIT` permettent d’ajuster ces valeurs.

Le contrôle automatique de ces comportements se trouve dans `service-commande/src/test/java/com/ecommerce/commande/clients/FeignResilienceExecutorTest.java` et les scénarios d’idempotence dans `service-commande/src/test/java/com/ecommerce/commande/services/OrderServiceTest.java`.
