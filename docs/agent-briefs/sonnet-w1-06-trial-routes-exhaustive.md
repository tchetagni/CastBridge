# w1-06 — Liste blanche d'essai prouvée exhaustive (table des routes générée depuis le code)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W1-A** (vague W1) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*TrialRoutes*'`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 1 · Effort S (≈ 4 h) · Statut PRÊT.** Branche `claude/sonnet-w1-06`. Rapport : `docs/agent-reports/sonnet-w1-06.md`.

## Objectif
Chaque route HTTP réellement servie par CastBridge-TV est **classée** (ouverte ou fermée en essai) par un test qui échoue dès qu'une route nouvelle n'est pas classée ; `/api/learn/packs/import` est fermé en essai.

## Pourquoi (preuves)
- `android/core/src/main/kotlin/castbridge/core/owner/TrialPolicy.kt:31-44` : liste blanche `EXACT` + `PREFIXES` + `DENIED_UNDER_ALLOWED`, « default deny » — bonne conception.
- `android/core/src/test/kotlin/castbridge/core/owner/TrialRoutesTest.kt` : listes `allowed` (36) et `denied` (54) écrites à la main ; **49 routes présentes dans le code n'y figurent pas** (extraction par `grep 'path == "/api/'` sur `ReceiverServer.kt` + `TvService.kt` : `/api/gateway*`, `/api/content/reports*`, `/api/restart`, `/api/lots/remove`, `/api/downloads/*`, `/api/ssh/disable`, `/api/storage/*`, `/api/learn/packs/install`, …). Certaines sont **autorisées** en essai par préfixe sans avoir été voulues.
- `android/core/src/main/kotlin/castbridge/core/learn/LearnApi.kt:56` : `/api/learn/packs/import` importe un pack depuis le stockage de la TV, sous le préfixe autorisé `/api/learn`.
- Audit : SE-8, TE-3.

## Fichiers possédés
`C/owner/TrialPolicy.kt`, `android/core/src/test/kotlin/castbridge/core/owner/TrialRoutesTest.kt`, nouveaux `tools/routes/list_routes.py`, `tools/routes/routes.txt`, `tools/tests/test_routes.py`. **Hors zone** : `ReceiverServer.kt` (w1-03), `TvService.kt` (w1-04), `LearnApi.kt`, tout routeur.

## Étapes
1. `tools/routes/list_routes.py` (stdlib) : parcourt `android/core/src/main/kotlin/castbridge/core/**/*.kt` et `android/receiver/src/main/kotlin/castbridge/receiver/*.kt`, extrait les littéraux `"/api/..."`, `"/upload"`, `"/stream"`, `"/quiz"`, `"/chess"`, `"/"` comparés à `path`/`uri` (motifs : `path == "`, `path.startsWith("`, `uri == "`, `when (path)` branches, `"…" ->`), dédoublonne, trie, écrit `tools/routes/routes.txt` (une route par ligne, `#` commentaires). Option `--check` : échoue si le fichier committé diffère.
2. Pour chaque route de `routes.txt`, décider dans `TrialRoutesTest` : ajouter la route à `allowed` ou `denied` en suivant l'intention documentée (`docs/TRIAL-EDITION.md` § 15 : streaming, appairage/télécommande, activation, locations, lots, Apprendre, Sudoku, aides de connexion ; **fermés** : fichiers, bibliothèque, stockage, dossiers, corbeille, téléchargements, envois, transferts, USB, SSH, APK, captures, quiz, échecs). Proposition pour les orphelines : `/api/gateway*` **ouvert** (passerelle Internet du téléphone = streaming), `/api/content/reports*` ouvert (signalements), `/api/restart` ouvert, `/api/net`, `/api/autostart`, `/api/background`, `/api/overlay-permission` ouverts ; `/api/lots/remove`, `/api/lots/part`, `/api/lots/priority` ouverts (livraison de lots) ; `/api/learn/packs/import` et `/api/learn/packs/install` **fermés** ; `/api/downloads/*`, `/api/ssh/*`, `/api/storage/*`, `/api/quiz/*`, `/api/chess/*`, `/api/sudoku/cmd`(ouvert), `/api/server/*` (ouvert : contact serveur) … Toute route fermée par défaut qui devrait rester fermée n'exige **aucun** changement de `TrialPolicy`.
3. Nouveau test `everyServedRouteIsClassified` : lit `tools/routes/routes.txt` (via le chemin du dépôt, comme `ActivationVectorsTest` lit `tools/activation/`) et exige `allowed ∪ denied ⊇ routes`.
4. `TrialPolicy.DENIED_UNDER_ALLOWED += "/api/learn/packs/import", "/api/learn/packs/install"`.
5. `tools/tests/test_routes.py` : `list_routes.py --check` passe ; `routes.txt` contient au moins `/api/hello`, `/api/library`, `/stream`.

## Critères d'acceptation
```sh
python3 tools/routes/list_routes.py --check                                   # 0
python3 -m unittest discover -s tools/tests -p 'test_routes.py'              # vert
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.TrialRoutesTest'   # vert, nombre de routes classées = nombre de lignes de routes.txt
```
Observable (campagne, étape 12) : `curl -o /dev/null -w '%{http_code}' -H 'X-CB-Pin: …' http://TV:8765/api/learn/packs/import` → 403 en essai.

## Cas limites
- Routes paramétrées (`/stream/<nom>`, `/upload/<nom>`) : classer le préfixe.
- Routes des extensions (`RentalHub.api`, `LotsHub.api`, `FoldersApi`) construites par concaténation : si l'extraction les rate, les ajouter à la main dans `routes.txt` sous un commentaire `# manuel` et le signaler.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas modifier les routeurs ; ne pas ouvrir une route fermée sans l'écrire dans le rapport ; textes en français.

## Rapport
`STATUT`, nombre de routes extraites / classées, liste des routes dont la classification est un **choix** (à confirmer par le coordinateur), sorties des commandes.
