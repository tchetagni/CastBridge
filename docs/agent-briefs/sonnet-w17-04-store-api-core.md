# w17-04 — Cœur : routes TV `/api/store*` (vitrine, réception des catalogues signés, demandes, acquittement), stockage `files/store/`, liste blanche d'essai, classement des routes

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (routes derrière PIN ; anti-retour) · statut : PRÊT (pendant le gel : cœur seul ; `TrialPolicy.kt` et `routes.txt` sont aussi dans la zone de w10-08, **non lancé** : si w10-08 est lancé avant, se rebaser)
> **Groupe : W17a-4** (vague W17a, cœur) · prérequis : w17-01, w17-02, w17-03 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.store.*' --tests 'castbridge.core.owner.*Routes*' && python3 tools/tests/test_routes.py`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 17a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W17 § 3.2, § 4.3, § 5, § 8.1 D-W17-4/9. Branche `claude/sonnet-w17-04`. Rapport : `docs/agent-reports/sonnet-w17-04.md`.

## Objectif
Une `ApiExtension` pure (`StoreApi`) que la TV branchera d'une ligne (w17-08) et que le harnais J branchera dans `TvSim` (w17-05) : `GET /api/store` (vitrine JSON = `StoreView.json` + `enabled`, `catalogAt`, `kidActive`, `trial`, `pending`), `POST /api/store/catalog` (corps JSON `{"lots": …, "bundles": …, "works": …?}` ≤ 512 Ko : chaque document vérifié avec les clés publiques injectées, anti-retour `generatedAt`, écrit atomiquement dans `files/store/`), `GET /api/store/requests`, `POST /api/store/requests/ack`, `POST /api/store/request` (dépôt d'une demande `origin=phone`, D-W17-8). Drapeau éteint ⇒ **404** sur tout `/api/store*` (comportement actuel inchangé).

## Pourquoi (preuves)
- `C/tv/Device.kt:54-62` (`ApiExtension`, `wantsBody`, `handleBody`, `then`) ; `C/lots/LotPush.kt:59-91` (`TvLotApi` : patron exact d'une extension avec corps, erreurs `{"error":…}`) ; `C/lots/RentalApi.kt:50-75` (`statusJson`).
- `C/lots/LotManifest.kt` (`verify` du catalogue de lots avec clés), `C/lots/SignedBundleCatalog.kt:46-68` (`verify(json, keys, notOlderThan)`, `generatedAtOf`) ; W10 `SignedWorksCatalog` **si présent** (sinon `works` ignoré avec `refused.works = "non pris en charge"`).
- `C/owner/TrialPolicy.kt:31-45` (`EXACT`, `PREFIXES`, `DENIED_UNDER_ALLOWED`, `routeAllowed`) ; `tools/routes/routes.txt` + `tools/tests/test_routes.py` (chaque route classée, w1-06) ; `C/tv/ReceiverServer.kt` `MAX_EXT_BODY` (**vérifier** la valeur : si < 512 Ko, le corps des catalogues est **borné par elle** et le rapport le dit).
- `C/lots/TvLotStore.kt:102` (`manifest()`), `C/lots/RentalLedger.kt` (`status(activations)`) : faits injectés par des lambdas, jamais lus directement.

## Fichiers possédés
Nouveaux `C/store/StoreApi.kt`, `C/store/StoreFiles.kt` (chemins, écriture atomique `SafeFile`, lecture tolérante), `CT/store/StoreApiTest.kt`, `CT/store/StoreFilesTest.kt`, `CT/owner/TrialRoutesStoreTest.kt` ; modifiés `C/owner/TrialPolicy.kt` (**listes seulement** : `EXACT` + `/api/store`, `/api/store/catalog` ; `DENIED_UNDER_ALLOWED` + `/api/store/requests`, `/api/store/requests/ack`, `/api/store/request` ; `PREFIXES` + `/api/store`), `tools/routes/routes.txt` (5 lignes classées). **Hors zone** : `C/tv/ReceiverServer.kt`, `R/**`, `S/**`, `C/store/{StoreCatalog,StoreView,RentRequest*}.kt`.

## Étapes
1. **Rouge** : `StoreApiTest` (extension appelée directement, sans HTTP, comme `RentalApiTest`) : drapeau éteint ⇒ `null` (404 par la chaîne) ; `GET /api/store` ⇒ JSON parsable par `StoreView.parse`, `catalogAt` nul sans catalogue ; `POST /api/store/catalog` avec deux documents signés de TEST (clé de test générée dans le test, comme `LotManifestTest`) ⇒ `accepted: [lots, bundles]`, fichiers écrits ; même envoi ⇒ `accepted: []` (rien de plus récent), **jamais** une erreur ; document plus ancien ⇒ `refused.lots = "Le téléphone propose un catalogue plus ancien…"` et fichier **inchangé** ; signature fausse ⇒ refusé, fichier inchangé ; corps > 512 Ko ⇒ 413 ; `GET /api/store/requests` liste `PENDING` ; `ack` idempotent ; `POST /api/store/request` ⇒ `create(origin=phone)` avec les mêmes refus que la TV (enfant, essai…) ; fichier `files/store/lots-catalog.json` corrompu au démarrage ⇒ vitrine « Boutique vide », pas d'exception.
2. `TrialRoutesStoreTest` : `/api/store`, `/api/store/catalog` **ouverts** en essai ; `/api/store/requests`, `/api/store/requests/ack`, `/api/store/request` **fermés** (403) ; `test_routes.py` vert.
3. `StoreApi(files: StoreFiles, keys: List<String>, facts: () -> StoreView.Facts /* sans store */, requests: RentRequests, enabled: () -> Boolean)` : le `Store` (w17-01) est **reconstruit** à chaque `GET` depuis les fichiers (≤ 5 ms, mesuré dans le test sur les fixtures : assertion < 50 ms) ou mis en cache par `generatedAt` (au choix, documenté) ; réponses d'erreur `{"error": "<FR>"}` ; `JsonLite` seulement.
4. `StoreFiles` : `lots-catalog.json`, `bundles-catalog.json`, `works-catalog.json`, `requests.json` sous un dossier injecté ; écriture `SafeFile` (tmp + rename + `.bak`) ; `generatedAtOf(doc)` pour l'anti-retour ; bornes de taille (`StoreCatalog.MAX_*`) appliquées **avant** la vérification de signature (économie CPU sur la TV).
5. **Vert** : porte ; `:core:test` complet.

## Critères d'acceptation
Porte verte ; ≥ 18 tests rouges puis verts ; `python3 tools/tests/test_routes.py` vert ; `grep -n "/api/store" tools/routes/routes.txt | wc -l` = 5 ; diff de `TrialPolicy.kt` ≤ 6 lignes (listes seulement).

## Cas limites
Deux `POST /api/store/catalog` concurrents (verrou de `StoreFiles`, le second voit le `generatedAt` du premier) ; catalogue des bouquets sans catalogue de lots (accepté : mode dégradé `StoreCatalog`) ; `works` présent sans W10 (ignoré, dit dans la réponse) ; clés publiques vides ⇒ tout refusé « Aucune clé de vérification » (fail closed, même phrase que `SignedBundleCatalog`).

## À ne pas faire
Aucun téléchargement depuis Internet ; aucune route sans PIN/téléphone de confiance ; ne pas modifier `ReceiverServer` ; pas de texte d'écran (phrases dans `StoreTexts`) ; ne pas toucher `R/`, `S/`.

## Rapport
`STATUT`, sorties rouge/vert, valeur réelle de `MAX_EXT_BODY` et conséquence, temps de reconstruction mesuré, question : D-W17-9 (clé USB) laissée à non.
