# w17-03 — Cœur : la demande de location `castbridge-rent-request-v1` (format, code court, file de la TV avec refus, nonce, acquittement, expiration, rapprochement) et ses vecteurs

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (canal de demande : la demande ne doit jamais valoir un droit ; enfant ; essai ; rejeu) · statut : PRÊT (pendant le gel : cœur seul)
> **Groupe : W17a-3** (vague W17a, cœur) · prérequis : w17-01 fusionné ; w16-04 (`PilotRules.Choice`) **souhaité**, sinon validation locale du choix · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.store.*' && python3 tools/activation/verify_vectors.py --only store`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · audit Opus : **oui**

**Vague 17a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W17 § 4.1, § 4.2, § 8.1 D-W17-3/7/8. Branche `claude/sonnet-w17-03`. Rapport : `docs/agent-reports/sonnet-w17-03.md`. **Principe non négociable** : une demande est une intention ; elle ne crée, ne prolonge, ne signe rien.

## Pourquoi (preuves)
- `C/trust/TrustRegistry.kt` (`installId`, 16 hex : DESIGN-W4:41) ; `C/lots/RentalEngine.kt:37-52` (`RentalStatus`, `contract.period`, `usable`) ; `C/lots/RentalPolicy.kt:23-28` (libre / inconnu refusés) ; `C/lots/RentalLines.kt:14` (`MAX_CONCURRENT`) ; `C/util/SafeFile` (écriture avec `.bak`, cf. `RentalLedger.save`, `C/lots/RentalLedger.kt:217`) ; `C/net/JsonLite`.
- W16 § 1.1 (choix : `defaut`, jours 1/3/7/14, heures 1/3/6/12/24/48/96), § 1.4 (un contrat par bouquet, prolongation même unité) ; `C/lots/PilotRules.kt` (w16-04) **si présent**.
- `tools/activation/verify_vectors.py` (rejeu Python des vecteurs, section additive comme w16-06).

## Fichiers possédés
Nouveaux `C/store/RentRequest.kt`, `C/store/RentRequests.kt`, `C/store/RentRequestStore.kt` (persistance via `QueueStore` existant, `C/lots/DeliveryQueue.kt` : interface réutilisée, pas modifiée), `CT/store/RentRequestTest.kt`, `CT/store/RentRequestsTest.kt`, `CT/store/StoreVectorsTest.kt`, `tools/activation/store-vectors.json` (`castbridge-store-vectors-v1`), `tools/activation/verify_vectors.py` (**section additive** `store`, derrière `--only store`). **Hors zone** : `C/store/{StoreCatalog,StoreView,StoreApi}.kt`, `C/lots/**`, `S/**`, `R/**`.

## Étapes
1. **Rouge** : `RentRequestTest` : forme canonique (lignes triées, `\n`, pas d'espace), `parse` strict (champs obligatoires, `tv` 16 hex, `bundle` `[a-z0-9][a-z0-9-]{0,63}`, `choice` ∈ grammaire `defaut|[0-9]{1,3}j|[0-9]{1,3}h`, `kind`, `period ≥ 0`, `nonce` 8 hex, `at`, `origin`), refus `MALFORMED` avec phrase FR ; code court `shortCode(alias)` = `<ALIAS>-<CHOIX>-<4 Crockford>` déterministe (vecteur figé) ; `choiceLabel()` = libellés **exacts** W16 § 1.3 (« 12 heures d'utilisation », « 7 jours », « Sans durée précise : 30 jours ») sans conversion.
2. `RentRequestsTest` : `create(facts)` refuse `TRIAL_TV`, `KID_PROFILE`, `FREE_BUNDLE`, `UNKNOWN_FAMILY`, `OVER_LIMIT` (≥ `maxConcurrent` contrats utilisables), `SAME_BUNDLE_OTHER_UNIT` (contrat en cours sur ce bouquet avec une autre unité ⇒ seule la prolongation **même unité** passe, `kind=extend` + `period` du contrat), `PILOT_ENDED` (si `pilotEndMs` connu et dépassé et aucun prix : W17 ne connaît pas de prix ⇒ refus), `DUPLICATE` (même bouquet + choix en attente), `QUEUE_FULL` (20) ; accepte sinon et persiste `PENDING` ; `ack(nonce, ACCEPTED|REFUSED)` idempotent (même réponse deux fois, nonce inconnu ⇒ `UNKNOWN`) ; `expire(nowMs)` : `PENDING` > 7 j ⇒ `EXPIRED` ; `reconcile(rentals)` : une demande `ACCEPTED` dont un contrat utilisable couvre le bouquet ⇒ `FULFILLED` ; `pendingBundles()` pour `StoreView.Facts.pendingRequests` ; liste bornée : ≤ 50 acquittées gardées 30 j ; fichier corrompu ⇒ file vide + `degraded` (jamais une exception) ; **aucune méthode ne retourne un droit, une clé, une activation** (test par réflexion : aucun type `Right`, `Activation`, `RentalContract` dans les signatures publiques de `RentRequests`).
3. `RentRequest` (data class + `canonical()`, `parse()`, `shortCode()`, `choiceLabel()`), `RentRequests(store: QueueStore, now: () -> Long, random: () -> String /* nonce injecté */)`, états `PENDING, ACCEPTED, REFUSED, EXPIRED, FULFILLED`, `Facts(trialTv, kidActive, family: LotFamily?, rentals: List<RentalStatus>, maxConcurrent, pilotEndMs: Long?, origin)` ; refus = `enum Refusal` + phrase `StoreTexts` (si w17-02 non fusionné : phrases locales marquées `TODO(w17-02)` et reprises d'une ligne).
4. Vecteurs `store-vectors.json` : 12 entrées (canonique, code court, parse refusés ×4, séquence create/ack/expire/reconcile avec `now` figé, enfant, essai, doublon, file pleine) ; `StoreVectorsTest` rejoue et **échoue si le fichier committé diffère** de ce que le code produit (patron `RentalVectorsTest`) ; `verify_vectors.py --only store` rejoue canonique + code court en Python (SHA-256, Crockford).
5. KDoc en français en tête de `RentRequests` : le modèle de menace (§ 4.2 : surface = nuisance bornée) et ce que la demande n'est pas.
6. **Vert** : porte ; `:core:test` complet ; liste de contrôle d'audit dans le rapport (points 1-8 : droit jamais créé, enfant, essai, nonce, bornes, corruption, phrases, vecteurs).

## Critères d'acceptation
Porte verte ; ≥ 28 tests rouges puis verts ; `tools/activation/store-vectors.json` rejoué Kotlin **et** Python ; `grep -n "Right\|Activation\|RentalContract" android/core/src/main/kotlin/castbridge/core/store/RentRequests.kt` ne cite ces types qu'en **lecture** (`RentalStatus` en entrée) ; audit Opus **vert** avant fusion.

## Cas limites
`period` donné avec `kind=new` ⇒ `MALFORMED` ; prolongation d'un contrat `EXPIRED` ⇒ refus « terminée : relouer » (nouvelle demande `kind=new`) ; horloge TV reculée (`now` plus petit qu'à la création) ⇒ jamais d'expiration anticipée (`expire` compare au max vu, injecté) ; deux `create` dans la même milliseconde ⇒ nonces distincts (aléa injecté, test).

## À ne pas faire
Aucune signature, aucun HMAC prétendu « preuve » (la TV ne signe pas) ; aucun prix ; aucune I/O hors `QueueStore` ; ne pas toucher `C/lots/**` ni `PilotRules`.

## Rapport
`STATUT`, sorties rouge/vert, liste de contrôle d'audit remplie, le vecteur du code court (exemple), question : D-W17-8 (dépôt des demandes nées sur le téléphone) et expiration 7 j confirmées ?
