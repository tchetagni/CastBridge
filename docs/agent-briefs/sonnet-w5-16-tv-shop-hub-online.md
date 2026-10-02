# w5-16 — CastBridge-TV : `ShopHub` (routes `/api/shop/*`, `/api/tokens/*`, `GET /api/activation/proof`), `ShopStore` (cache signé), `ShopClient` (TV en ligne : mêmes routes serveur), `RentalOnlineInstaller`, télémétrie

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT **amendé W6** (pas de route proof sans nonce)
> **Groupe : W5d-1** (vague W5d) · prérequis : w5-01, w5-02, w5-04 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*RentalApi*' && cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui

**Vague 5d · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après w5-01, w5-02, w5-04 ; serveur 5b en préprod ou `FakeShopApi` ; w5-15 en parallèle).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3.4 (a), § 4.5 (étapes 2, 7), § 4.7, § 5.2, § 6.4, § 6.5, § 9, § 15. Branche `claude/sonnet-w5-16`. Rapport : `docs/agent-reports/sonnet-w5-16.md`.

## Objectif
(1) **Routes TV** (derrière PIN / jeton de téléphone de confiance, chaînées dans `TvService`) : `GET /api/shop/catalog` (fichiers signés gardés), `POST /api/shop/catalog` (corps = `{bundles, prices}` : vérifiés avec `UpdateKeys.PUBLIC_KEYS`, anti-retour, gardés), `GET /api/shop/state`, `GET /api/shop/pending-voucher`, `POST /api/shop/pending-voucher/result`, `GET /api/shop/receipts`, `POST /api/shop/receipts` (le téléphone dépose un reçu), `GET /api/tokens/report`, `POST /api/tokens/install` (enveloppe `tokens` : `TokenGrant.verify` puis `TokenWallet.credit`), `POST /api/tokens/ack?seq=`, `GET /api/activation/proof` (`ActivationProof.select`) ; (2) `ShopStore` (implémente l'interface de w5-15) ; (3) `ShopClient` : **quand la TV a Internet** (`Routes` : réseau direct ou passerelle Bluetooth du téléphone) et **seulement sur action de l'utilisateur** : `quote`, `order`, `redeem`, `get`, `me`, `reportTokens` avec le jeton d'appareil de la TV (`HttpShopApi` cœur ? **non** : le client HTTP cœur n'existe pas ; écrire `R/shop/ShopClient.kt` sur `HttpURLConnection` comme `UpdateClient`) ; (4) `RentalOnlineInstaller` : installe une activation de location reçue en ligne (`ActivationCenter` voie unique `ActivationReceiver`), télécharge les lots scellés **dans le budget 10 Mo** (`TvLotStore` ; refus clair avant), les passe à `RentalApi` comme s'ils venaient du téléphone (fichier `.part` + `install`) ; (5) télémétrie : `shop` dans `TV_FEATURES`, événements `shop_order{kind}`, `tokens_spend{item}` (consentement statistiques).

## Pourquoi (preuves)
- `R/TvService.kt:244-253` (chaîne `.then(...)`), `:258` (garde essai/réduit : w5-04 a classé les routes), `:840` (`GET /api/activation` : ajouter `shop` et `tokens` résumés) ; `C/tv/Device.kt:54` (`ApiExtension`) ; `R/RentalHub.kt:71-79` (`/api/activation/request|install` : ajouter `proof`) ; `R/LotsHub.kt` (budget, `TvLotStore`) ; `C/lots/RentalApi.kt` (`/api/rental/install?name&contract` lit `.part`) ; `C/connect/Routes.kt` (réseau direct puis passerelle), `C/update/UpdateClient.kt:54` (client HTTP minimal) ; `C/telemetry/Telemetry.kt:16,25-51`, `B/telemetry/EventCatalog.java` (id et événements : les deux listes doivent être d'accord).
- w5-01 (`ShopRequest`, `ShopCatalog`, `ShopPolicy`), w5-02 (`TokenGrant`, `TokenWallet`, `TokenSync`), w5-04 (`ActivationProof`, routes classées), w5-07/08 (JSON serveur), w5-15 (`ShopStore`).

## Fichiers possédés
Nouveaux `R/shop/ShopHub.kt`, `R/shop/ShopStore.kt`, `R/shop/ShopClient.kt`, `R/shop/RentalOnlineInstaller.kt` ; modifiés `R/TvService.kt`, `R/RentalHub.kt`, `R/LotsHub.kt`, `C/lots/RentalApi.kt` (**additif** : `installSealedFile(file, catalogJson, contract)` réutilisé par la route et par l'installeur en ligne), `CT/lots/RentalApiTest.kt`, `C/telemetry/Telemetry.kt`, `B/telemetry/EventCatalog.java`. **Hors zone** : `R/shop/{ShopActivity,ShopViews,VoucherKeypad,ShopTexts}.kt` (w5-15), `R/tokens/**` (w5-17 : `TokenHub` fournit le `TokenWallet` ; d'ici là, `ShopHub` l'instancie avec un `WalletKeyProvider` provisoire **mémoire** et le signale), `R/ParentalHub.kt`, `C/shop/**`, `C/tokens/**`, `S/**`, `backend/` autre que `EventCatalog.java`.

## Étapes
1. `ShopStore` : fichiers `files/shop/{bundles.json, prices.json, receipts.jsonl, pending-voucher.txt, orders.json}` (`SafeFile`) ; vérification des catalogues (signature, `generatedAt` non antérieur) ; `state()` = en ligne ? (`Routes.hasInternet()` instantané, sans sonde : état du dernier appel), essai/réduit (`ActivationCenter.restriction()`), enfant (`ParentalHub.kidHomeActive()`), conditions (w5-19 : `ShopTerms` si présent), `installKeyProtection`.
2. `ShopHub.api(ctx): ApiExtension` : routes ci-dessus ; corps bornés (catalogues ≤ 1 Mo chacun, enveloppe ≤ 8 Ko) ; JSON strict ; erreurs FR ; **`POST /api/tokens/install`** : `TokenGrant.verify(token, KeyRing(TRUSTED_KEYS), revocations, empreintes, installPub, lastGrant, TvClock.now)` ⇒ `TokenWallet.credit` ; réponse `{credited, balance}` ; `GET /api/tokens/report` ⇒ `TokenWallet.report()` ; `POST /api/tokens/ack`.
3. `RentalHub` : `GET /api/activation/proof` ⇒ `{kind, counting, licenseId, seatId, token}` (PIN requis ; jamais en essai ? **si** : l'essai a besoin de sa preuve pour la fenêtre d'essai : autorisé en essai, w5-04 l'a classé).
4. `ShopClient` : construit `ShopRequest` (demande v2 `ActivationCenter.requestText()`, preuve, `GET /api/rental` interne, résumé du porte-jetons), appelle le serveur (`ServerUrl`, jeton d'appareil de `TvConnect`), 20 s, une seule requête à la fois, **jamais** en tâche de fond ; `order(item)` : si pas d'Internet ⇒ `USE_PHONE` ; `submitVoucher` : en ligne ⇒ `redeem` ⇒ exécution immédiate (`RentalOnlineInstaller` ou `tokens/install` interne) ; hors ligne ⇒ `pending-voucher.txt` ; `checkOrder(ref)` ⇒ `get` ⇒ si `FULFILLED` ⇒ installation ; `me()` à l'ouverture de la Boutique si en ligne (bons de jetons en attente, reçus).
5. `RentalOnlineInstaller.install(fulfilment)` : activation ⇒ `ActivationReceiver` (même vérification qu'un fichier/Bluetooth ; conditions d'assistance exigées comme pour `/api/activation/install`) ; lots : vérifier le budget (`TvLotStore.usedBytes + Σ bytes ≤ TV_MAX_BYTES`, sinon « il manque X Mo : retirez … » **avant** tout téléchargement) ; téléchargement `Range` dans `files/lots/.in/<name>.part` ; `RentalApi.installSealedFile` ; accusé `delivered` ; télémétrie.
6. `TvService` : `.then(ShopHub.api(this))` ; `GET /api/activation` ajoute `"shop": {catalogAt, pendingVoucher: bool}`, `"tokens": {onTv, lastGrant, state}` ; `TvConnect` : brancher le `WalletSummaryProvider` de w5-10 si fusionné.
7. Télémétrie : `TV_FEATURES += "shop"` ; événements `shop_order{kind}`, `tokens_spend{item}` (props sans identifiant) dans Kotlin **et** Java (`EventCatalog`), tests existants des catalogues.
8. Tests : `RentalApiTest.installSealedFile` ; tests de routes (`TvHardeningTest` style : PIN requis, corps borné, essai : `tokens/install` fermé, `shop/state` ouvert) ; `TokenGrant` refusé si `install` ≠.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.RentalApiTest' --tests 'castbridge.core.telemetry.*' --tests 'castbridge.core.owner.TrialRoutesTest' --tests 'castbridge.core.owner.DegradedRoutesTest'   # vert
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (SDK)
cd backend && ./mvnw -q -o test -Dtest='TelemetryApiTest'   # vert (catalogue aligné)
python3 tools/routes/list_routes.py | grep -c '/api/shop\|/api/tokens\|/api/activation/proof'   # = nombre de routes classées par w5-04
grep -n 'ShopHub.api' android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt   # 1
grep -rn 'Thread\|Timer\|JobScheduler' android/receiver/src/main/kotlin/castbridge/receiver/shop/ShopClient.kt | grep -v 'TvExecutors'   # 0 hit (aucune tâche de fond ; exécuteur partagé seulement)
```
Observable (TV de référence + préprod) : taper un bon de test en ligne ⇒ location installée sans téléphone ; `GET /api/rental` montre le contrat ; bon tapé hors ligne ⇒ en attente ⇒ relayé par le téléphone (w5-12) ; `POST /api/tokens/install` d'un bon de test ⇒ solde ; `tokens/install` en essai ⇒ 403 avec le message de la politique.

## Cas limites
- `TRUSTED_KEYS` compilées sans la clé publique du serveur : l'activation de location est refusée `UNKNOWN_KEY` : message explicite « clé du serveur inconnue de cette version de CastBridge-TV : mettez à jour » (et le signaler dans le rapport : risque n° 1 de la conception).
- Lots scellés > budget : rien n'est téléchargé ; la location (activation) est **quand même** installée (elle vaut pour le téléphone qui livrera plus tard).
- Internet par la passerelle Bluetooth du téléphone (`Routes`) : les appels passent ; les lots (Mo) passent aussi mais lentement : afficher l'ETA (réutiliser l'estimation de w2-09 si disponible).
- Horloge TV douteuse (`AHEAD`) : un bon de jetons `NOT_YET_VALID`/`WINDOW_CLOSED` ⇒ message « Vérifiez l'heure de la TV » et le bon est **gardé** pour une nouvelle tentative (pas perdu).

## À ne pas faire
Pas de commit sur les branches partagées ; aucun appel serveur au démarrage ni en tâche de fond ; ne pas contourner `ActivationReceiver` ni `RentalApi` ; aucun lot hors budget ; ne pas toucher `R/shop/ShopActivity.kt` ; français.

## Rapport
`STATUT`, routes TV exactes et JSON (pour w5-12, w5-20), comportement avec/sans clé serveur dans `TRUSTED_KEYS`, ce qui a été testé sur la vraie TV.
