# w3-01 — Table de routes en cœur et découpage de `ReceiverServer`

**Vague 3 · Effort L (≈ 4 j) · Statut PRÊT** (après fusion de w2-07 et w1-06). Branche `claude/sonnet-w3-01`. Rapport : `docs/agent-reports/sonnet-w3-01.md`.

## Objectif
1. Une `RouteTable` (cœur) déclare chaque route `Route(method, path|prefix, scope: TrialScope, auth: PIN|TOKEN|PUBLIC|LOOPBACK, handler)` ; `ReceiverServer.route()` devient une recherche ; `TvExtraRoutes` (receiver) **enregistre** ses routes dans la même table ; `TrialPolicy.routeAllowed` est dérivé des `scope` (la liste écrite à la main devient la source de vérité de la table, puis disparaît).
2. `ReceiverServer` (1 284 lignes, constructeur à 17 collaborateurs) perd `UploadHandler`, `StorageApi`, `StreamHandler`, `PlaylistController`, `ListingCache` ; les collaborateurs sont regroupés dans `ServerHooks`.
3. Un test cœur affirme que **chaque** route enregistrée a un `scope` et une `auth`, et que la table couvre `tools/routes/routes.txt` (w1-06).

## Pourquoi (preuves)
- `C/tv/ReceiverServer.kt:266-432` : chaîne `if` + `when` de 167 lignes, 36 littéraux `/api/` ; `:36-78` constructeur à 17 collaborateurs ; 8 champs `@Volatile` ; `:484-734` transfert, `:840-1125` stockage, `:1126-1197` streaming, `:197-250` playlist, `:469` HTML admin en ligne.
- Second routeur `R/TvService.kt:772-866` (devenu `R/TvExtraRoutes.kt` après w2-07) : ≈ 40 routes sans table, `startsWith` sans contrôle de méthode sur plusieurs branches.
- Gardes (bon point à conserver) : ordre `routeGuard :275 → publicRoutes :276 → denied :277 → extension :279-282`.
- Audit : AR-3, AR-5, TE-3.

## Fichiers possédés
`C/tv/ReceiverServer.kt`, nouveaux `C/tv/RouteTable.kt`, `C/tv/UploadHandler.kt`, `C/tv/StorageApi.kt`, `C/tv/StreamHandler.kt`, `C/tv/PlaylistController.kt`, `C/tv/ListingCache.kt`, `C/tv/ServerHooks.kt`, `R/TvExtraRoutes.kt`, `C/owner/TrialPolicy.kt` (dérivation depuis la table), `android/core/src/test/kotlin/castbridge/core/tv/**`, `android/core/src/test/kotlin/castbridge/core/TvHardeningTest.kt`, `android/core/src/test/kotlin/castbridge/core/owner/TrialRoutesTest.kt`. **Hors zone** : `TvService.kt` (hors `TvExtraRoutes`), `RentalHub.api`, `LotsHub.api`, `FoldersApi`, `LearnApi` (ils restent des `ApiExtension` et sont **déclarés** dans la table par leur préfixe + scope).

## Étapes
1. Écrire `RouteTable` + `Route` ; port de `route()` **sans changer une seule réponse** (même codes, mêmes JSON, même ordre des gardes) ; chaque branche du `when` devient une entrée ; les extensions (`ApiExtension.then`) sont enregistrées par préfixe avec leur scope.
2. `TrialPolicy.routeAllowed(path)` = `RouteTable.scopeOf(path) == OPEN_IN_TRIAL` ; la table est initialisée en cœur (routes du cœur) et complétée par le receiver au démarrage ; `TrialRoutesTest` passe sur la table (plus de listes manuelles) et exige la couverture de `routes.txt`.
3. Extraire les classes (déplacement pur), `ServerHooks` data class pour les 17 lambdas.
4. Conserver `HostGuard`/PIN en en-tête (w1-03) et `loopbackStream` (`:273`).
5. Tests existants (`ReceiverServerTest*`, `TvHardeningTest`, `TrialRoutesTest`, `TransferTest`…) verts sans modification de leurs assertions (sauf adaptation de construction).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test       # suite complète verte
wc -l android/core/src/main/kotlin/castbridge/core/tv/ReceiverServer.kt   # ≤ 500
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.TrialRoutesTest'   # vert, basé sur RouteTable
python3 tools/routes/list_routes.py --check     # 0 (routes.txt inchangé ou mis à jour volontairement)
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (si SDK)
```
Observable (campagne) : aucun changement de comportement ; `curl` des 20 routes principales donne les mêmes codes qu'avant (script `tools/device/api-smoke.sh` de w3-14 si disponible).

## Cas limites
- Routes à méthode multiple (`GET` + `POST` sur le même chemin) : une entrée par méthode.
- Les extensions ont leurs propres sous-routes : la table ne connaît que le préfixe + scope ; le test de couverture accepte un préfixe pour ses sous-routes.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; aucun changement de réponse HTTP ; pas de renommage de route ; textes en français inchangés.

## Rapport
`STATUT`, nombre de routes dans la table, classes extraites, lignes avant/après, résultat des tests.
