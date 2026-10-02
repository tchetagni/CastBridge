# w5-12 — CastBridge (téléphone) : passerelle de la boutique (client HTTP, demande de la TV en cache, sondage des commandes, lots scellés, livraison activation + lots + bons de jetons, relais catalogue / bon en attente / rapport de dépenses)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (FakeShopApi ou préprod 5b)
> **Groupe : W5c-1** (vague W5c) · prérequis : w5-01, w5-02 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*RentalDelivery*' && cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui · découpage proposé : voir ROUTAGE § 2.3

**Vague 5c · Effort L (≈ 4 j) · Modèle : sonnet · Statut PRÊT (après w5-01, w5-02 ; serveur de préprod 5b ou `FakeShopApi` ; w5-11 en parallèle).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 4.5, § 5.2, § 6.4, § 6.5, § 15. Branche `claude/sonnet-w5-12`. Rapport : `docs/agent-reports/sonnet-w5-12.md`.

## Objectif
`ShopRuntime` (singleton du téléphone, comme `LotsRuntime`) : (1) `HttpShopApi` (routes de w5-07/08 avec le jeton d'appareil, `Idempotency-Key`, erreurs typées) ; (2) **cache de la demande de la TV** (`TvShopCache` : à chaque liaison, `GET /api/activation/request`, `GET /api/activation/proof`, `GET /api/rental`, `GET /api/tokens/report`, `GET /api/shop/state`) ; (3) **commandes** : création, sondage (`GET /orders/{ref}` à l'ouverture, toutes les 15 min pendant 72 h si l'app est ouverte, et par la tâche périodique), notification locale « Commande CB-… confirmée » ; (4) **téléchargement des lots scellés** (Wi-Fi seulement par défaut, `Range`/`If-Range`, SHA-256, dans `files/shop/sealed/<contrat>/`, compté dans le quota 100 Mo) ; (5) **livraison à la TV** : activation de location (`POST /api/activation/install`) **puis** `RentalDelivery.deliver`, puis `POST /api/tokens/install` pour les bons de jetons, puis `POST /api/shop/catalog` (catalogue + grille signés) ; accusé `delivered` ; (6) **relais** : bon en attente de la TV (`GET /api/shop/pending-voucher` → `redeem` → résultat vers la TV), rapport de dépenses (`GET /api/tokens/report` → `POST /shop/tokens/report` → `POST /api/tokens/install` des nouveaux bons + accusé) ; (7) `RentalDeliveryActivity` devient « Livraison avancée » (⋯).

## Pourquoi (preuves)
- `C/lots/RentalDelivery.kt:77-139` (`deliver` : `GET /api/rental` d'abord ⇒ il faut installer l'activation **avant**) ; `S/LotsRuntime.kt` (stock, quota, transports Bluetooth/Wi-Fi, `JobScheduler` 12 h, règle Wi-Fi seulement, `DeliveryQueue`) ; `S/TvLink.kt:97+` (`TvLinkManager`, jeton de téléphone de confiance, `credentialForBase`) ; `S/ActivateTvActivity.kt:78-115` (`ActivationSend` Bluetooth/Wi-Fi d'une activation : **réutiliser**) ; `S/RentalDeliveryActivity.kt` ; `C/lots/LotSync.kt` (`HttpLotRemote` : Range/If-Range : modèle de téléchargement) ; `S/OrdersRuntime.kt` (sondage d'ordres : si simple, écouter `rights.refresh` pour déclencher un `refresh`).
- w5-01 `ShopApi`, `ShopOrder` ; w5-02 `TokenSync` ; routes TV de w5-16 (`/api/shop/*`, `/api/tokens/*`, `/api/activation/proof`) : coder contre la spécification de la conception ; tant que w5-16 n'est pas fusionné, un `FakeTv` dans les tests.

## Fichiers possédés
Nouveaux `S/shop/ShopRuntime.kt`, `S/shop/HttpShopApi.kt`, `S/shop/TvShopCache.kt`, `S/shop/ShopDelivery.kt`, `S/shop/ShopSyncJob.kt`, `S/shop/PendingVoucherRelay.kt`, `CT/lots/RentalDeliveryTest.kt` ; modifiés `C/lots/RentalDelivery.kt` (étape « activation d'abord » optionnelle : `deliverWithActivation(activationToken, …)` qui appelle `POST /api/activation/install` puis `deliver` ; `TvTransport` gagne `postText(path, body)` additif), `S/LotsRuntime.kt` (dossier `sealed/` dans le quota, éviction jamais pendant un contrat actif), `S/RentalDeliveryActivity.kt` (titre « Livraison avancée », avertissement). **Hors zone** : `S/shop/{ShopScreen,ShopPayScreen,ShopOrdersScreen,ShopTokensScreen,VoucherEntry,ShopTexts}.kt` (w5-11 : implémenter `ShopRuntimeView` **ici**, dans `ShopRuntime`), `S/MainActivity.kt`, `C/shop/**`, `C/tokens/**`, `R/**`, `backend/`.

## Étapes
1. `HttpShopApi(base = ServerUrl, token = deviceToken) : ShopApi` ; `Idempotency-Key` = UUID gardé avec la commande locale (rejoué tant que la réponse n'est pas reçue) ; délais 20 s ; erreurs `{code, messageFr}` → `ShopError`.
2. `TvShopCache` : par TV (adresse/jeton), fichier `files/shop/tv-<code>.json` : demande v2, preuve, contrats, résumé des jetons, état de la boutique TV, `installKeyProtection`, `capturedAt` ; rafraîchi à chaque session `TvLinkManager` ; `ShopRequest` construit à partir du cache (w5-01).
3. `ShopRuntime` (implémente `ShopRuntimeView` de w5-11) : état persistant `files/shop/orders.json` (`ShopOrder` JSON, `SafeFile`) ; `order(item, payment)` : `quote` puis `order` ; `redeem(code)` ; `refresh()` ; `deliver(ref)` ; `receipt(ref)` ; catalogue : `ServerBundleCatalog.fetch` + `GET /api/v1/catalog/prices` (vérifiés, anti-retour, gardés dans `files/shop/`) ; **jamais au démarrage** : à l'ouverture de l'onglet et par la tâche.
4. Téléchargement des lots : `fulfilment.rental.lots[]` → `.part` repris, SHA-256 attendu = celui du **scellé** (fourni par le serveur) ; Wi-Fi seulement sauf réglage existant « réseau facturé autorisé » de `LotsRuntime` ; quota : `LotStore.fit` ; nettoyage à la fin du contrat + 7 j.
5. `ShopDelivery.deliver(ref)` : `TvLinkManager` session ou Bluetooth (`ActivationSend` pour l'activation ; `RentalDelivery.deliverWithActivation` par Wi-Fi ; par Bluetooth CBT1 : activation via trame `ACTIVATION`, lots via le transport CBT1 de `LotsRuntime` + preuve, comme aujourd'hui) ; puis bons de jetons `POST /api/tokens/install` (texte de l'enveloppe) ; puis `POST /api/shop/catalog` ; accusé `delivered(ref, tvAck)` ; états visibles ; `DeliveryQueue` existante pour la reprise (ou une file dédiée minimale **persistante** si l'intégration est trop lourde : le dire).
6. `PendingVoucherRelay` : à chaque session TV : `GET /api/shop/pending-voucher` → si présent et en ligne : `redeem` avec la demande de **la TV** ; résultat : `POST /api/shop/pending-voucher/result` (succès ⇒ la TV efface ; échec ⇒ message FR) ; rapport de dépenses : `GET /api/tokens/report` → `reportTokens` → `POST /api/tokens/install` (bons) + `POST /api/tokens/ack?seq=`.
7. `ShopSyncJob` (`JobScheduler`, réseau quelconque pour les **petits** appels (commandes, rapports), non facturé pour les lots) : toutes les 15 min s'il existe une commande `AWAITING_PAYMENT|PAID` de moins de 72 h, sinon 12 h ; jamais pendant un envoi vers la TV.
8. Tests JVM : `RentalDeliveryTest` (activation d'abord ; `409` conditions non acceptées ⇒ message « acceptez les conditions sur la TV ») ; machine d'états de `ShopRuntime` avec `FakeShopApi` + faux transport TV (bon ⇒ `FULFILLED` ⇒ livré ; espèces ⇒ attente ⇒ confirmation ⇒ livré ; coupure pendant le téléchargement ⇒ reprise ; TV hors de portée ⇒ file ; bon en attente de la TV relayé ; rapport de dépenses accusé).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.RentalDeliveryTest' --tests 'castbridge.core.lots.*'   # vert
cd android && gradle --offline :sender:compileDebugKotlin   # compile (SDK)
grep -n 'activation/install' android/core/src/main/kotlin/castbridge/core/lots/RentalDelivery.kt   # ≥ 1 (activation d'abord)
grep -rn 'Livraison avancée' android/sender/src/main/kotlin/castbridge/sender/RentalDeliveryActivity.kt   # 1
grep -rn 'startup\|onCreate.*refreshCatalog' android/sender/src/main/kotlin/castbridge/sender/shop/ShopRuntime.kt   # 0 hit (jamais au démarrage)
```
Observable (émulateur TV + téléphone, préprod ou faux serveur) : commande avec bon → lots téléchargés → « Livrer à la TV » → `GET /api/rental` de la TV montre le contrat `ACTIVE` → Apprendre ouvre une leçon louée ; achat de jetons → `GET /api/tokens/report` de la TV montre le solde.

## Cas limites
- La TV refuse l'activation (`409` conditions d'assistance non acceptées) : état « Acceptez les conditions sur la TV puis réessayez » (pas d'échec silencieux).
- `fulfilment.needsFreshRequest` (w5-07) : relire la TV puis `GET /orders/{ref}` à nouveau.
- Contrat déjà sur la TV (renouvellement) : l'activation s'installe, aucun lot à renvoyer (`ALREADY_ON_TV`).
- Deux téléphones pour la même TV : la seconde livraison est idempotente (TV : « clé déjà en place »).

## À ne pas faire
Pas de commit sur les branches partagées ; aucun téléchargement de lot sur réseau facturé sans le réglage existant ; aucun montant en dur ; ne pas sceller quoi que ce soit sur le téléphone (plus de maître) ; ne pas toucher `S/focal/**` ni les écrans de w5-11 ; français.

## Rapport
`STATUT`, implémentation de `ShopRuntimeView`, routes TV réellement appelées (pour w5-16), fichiers persistants (pour w5-20), ce qui dépend de la préprod.
