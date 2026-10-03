# w19-07 — Cœur : une seule identité de fichier `(sha256, taille)`, un plan de rangement versionné et haché, un accord sur les lots `(feature, scope, version, sha256)`, et des formats sur disque avant-compatibles (fixtures gelées : mise à jour N→N+1→N sans perte)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus en échantillon (formats persistés) · statut : PRÊT (indépendant)
> **Groupe : W19-S2** · prérequis : aucun (interfaces locales pour w19-06) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.sync.*' --tests 'castbridge.core.tv.Filing*' --tests 'castbridge.core.tv.*Dedup*' --tests 'castbridge.core.lots.*' --tests 'castbridge.core.quiz.QuizLots*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 19 · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W19 § 1.1 (S-7, S-9, S-11), § 1.3 (lignes index, rangement, lots), § 1.4 (RS-18, RS-20, RS-28). Branche `claude/sonnet-w19-07`. Rapport : `docs/agent-reports/sonnet-w19-07.md`. Règle W15 R5 : aucun `S/`, `R/`.

## Objectif
(1) `C/sync/FileIdentity.kt` : `data class FileIdentity(sha256: String, size: Long)` + `partialKey(size, edgesSha)` ; `DedupDecision` (R-12) et `TvDedupe` passent par ce type (nom et date = attributs) ; `ContentIndex` expose `root(): String` = sha256 des `(sha256,taille)` triés, **tenu incrémentalement** (jamais recalculé à la demande) ⇒ source `LibraryRootSource` pour w19-06. (2) `FilingPlan.VERSION` (entier, 3 aujourd'hui : à fixer par lecture des règles R-13) + `FilingPlan.settingsHash(on, lang, rulesVersion)` ⇒ `FilingHashSource` ; `FilingAgreement.compare(phoneHash, tvHash, tvFilingVersion): Agreement(Same | OlderTv(text) | NewerTv(text) | Off)` avec phrases françaises (« Le rangement de la TV suit une autre règle (version 2) : les dossiers peuvent différer »). (3) `C/sync/LotAgreement.kt` : `plan(phone: List<LotMeta>, tv: LotManifest, caps): Plan(send: List<LotMeta>, remove: List<LotId>, keep: List<LotId>, unknownFeature: List<LotMeta>, text)` par `(feature, scope, version, sha256)` ; feature inconnue de la TV ⇒ `LOT_REJECTED` attendu, **jamais** envoyé à l'aveugle ; Quiz : `QuizLots.Index.contentHash` recoupé (ids de questions stables). (4) **Fixtures de formats avant-compatibles** : `CT/sync/FormatForwardCompatTest.kt` + `android/core/src/test/resources/compat/formats/<format>/<version>.txt` pour `PinBook` (`castbridge_pins.xml` sous forme clé/valeur), `trusted_phones.txt` (somme en dernière ligne), `.filing`, sidecar `.cbx.meta` (avec et sans `root`), file d'envoi JSON (R-09, avec et sans `identity`), coffre de capacités `cap.<tvId>` : chaque lecteur **garde les clés inconnues** à la réécriture et relit un fichier écrit par la version précédente ; un fichier **plus récent** (clés en plus) relu par le lecteur courant ne perd rien.

## Pourquoi (preuves)
- R-12 : `TvDedupe.alreadyThere` comparait nom + taille ; `DedupDecision` et `ContentIndex` existent mais sans type d'identité partagé ; R-13 : le téléphone et la TV avaient deux rangements.
- `C/lots/LotManifest.kt:64-83` : charge canonique triée par `(feature, scope, version)` et hachée ; `C/quiz/QuizLots.kt:172-188` : `Index(scope, version, count, contentHash, hashes)`, `questionHash` stable ⇒ les briques de l'accord existent, pas l'accord.
- `docs/HANDOFF.md` 2026-10-02 : « l'identité d'un fichier reste son NOM unique (clé plate) » côté `.filing` : W19 ne change pas la clé, il ajoute l'identité de contenu à côté.
- S-7 : P-28/P-29 (mise à jour en place) ne sont couverts que par des tests de migration scriptés jamais écrits (`migrate_test.py` absent).

## Fichiers possédés
Nouveaux : `C/sync/FileIdentity.kt`, `C/sync/FilingAgreement.kt`, `C/sync/LotAgreement.kt`, `CT/sync/FileIdentityTest.kt`, `CT/sync/FilingAgreementTest.kt`, `CT/sync/LotAgreementTest.kt`, `CT/sync/FormatForwardCompatTest.kt`, `android/core/src/test/resources/compat/formats/**`. Zones additives : `C/tv/ContentIndex.kt` (`root()` incrémental, ≤ 40 lignes), `C/tv/FilingPlan.kt` (`VERSION`, `settingsHash`, ≤ 15 lignes), `C/tv/DedupDecision.kt` ou fichier équivalent de R-12 (type `FileIdentity`, ≤ 20 lignes, **sans** changer une décision). **Hors zone** : `C/tv/ReceiverServer.kt`, `C/xfer/*`, `C/trust/PinBook.kt` (lu pour la fixture), `S/`, `R/`.

## Étapes
1. **Rouge** : `FileIdentityTest` (égalité, `partialKey`, `root()` change à l'ajout et revient à la même valeur après ajout+retrait, ordre indépendant) ; `FilingAgreementTest` (table) ; `LotAgreementTest` (v3 vs v2 ⇒ `send`, même version autre sha ⇒ `send` + texte « lot différent », TV sans la fonction ⇒ `unknownFeature`, 500 lots < 1 s) ; `FormatForwardCompatTest` (chaque fixture : relue ; réécrite ⇒ clés inconnues conservées ; diff des clés vide).
2. Implémenter ; fixtures écrites **par le code courant** puis enrichies à la main d'une clé inconnue `x-future=1`.
3. **Vert** : porte ; `:core:test` complet ; `DedupDecisionTest`, `FilingPlanTest`, `ContentIndexServerTest` verts.

## Critères d'acceptation
- `root()` jamais recalculé par parcours complet après la construction (compteur dans le test).
- Aucune décision de R-12/R-13 modifiée (les tests existants passent sans édition).
- Chaque format persisté partagé entre versions a sa fixture et son test de clés inconnues.

## Cas limites
Index vide ⇒ `root` constant documenté ; fichier de même sha mais tailles différentes (impossible : testé comme erreur) ; lot `version` égal, `bytes` différent ⇒ `send` ; fixture corrompue ⇒ lecteur rend vide **et** signale, jamais d'exception.

## À ne pas faire
Changer la clé plate de `.filing` ; recalculer un hash de fichier dans `root()` ; toucher `ReceiverServer` ; déplacer un fichier.

## Rapport
RAPPORT + `SYMBIOSE: cap=dedup,filing2,lots1 (existantes, documentées) · proto=inchangé · reason=LOT_REJECTED · deux écrans=FilingAgreementTest (texte) + LotAgreementTest (plan)`.
