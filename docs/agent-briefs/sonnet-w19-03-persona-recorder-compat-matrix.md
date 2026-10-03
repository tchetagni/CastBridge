# w19-03 — Outils + tests : enregistreur de « personas » de version (`tools/compat/record_persona.py`), transcriptions gelées par étiquette, rejoueurs `TranscriptTv` / `TranscriptPhone`, matrice de compatibilité `CompatMatrixTest` (vrai client HEAD × TV enregistrées, téléphones enregistrés × vraie TV HEAD)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune (outils, tests, fixtures ; aucun secret : masquage vérifié) · statut : PRÊT (après w19-02)
> **Groupe : W19-S1** · prérequis : w19-02 fusionné (`Caps.impliedBy`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.compat.Compat*' --tests 'castbridge.core.compat.Transcript*'` puis `python3 -m pytest -q tools/tests/test_record_persona.py`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 2,5 j) · audit Opus : non

**Vague 19 · Effort L · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W19 § 1.1 (S-6, S-10), § 2.3, § 4.1, D-W19-6. Branche `claude/sonnet-w19-03`. Rapport : `docs/agent-reports/sonnet-w19-03.md`. Règle R8 : la TV du propriétaire n'est jamais la cible d'un script (un `GET /api/hello` en lecture seule par le coordinateur, hors cahier, sert seulement à recouper une persona).

## Objectif
(1) `tools/compat/record_persona.py` (bibliothèque standard) : `--tag tv-0.14.25-beta` ⇒ `git worktree add .compat/<tag> <tag>` (jamais `main`) ⇒ copie du **shim** `tools/compat/shim/PersonaMain.kt` et de l'**adaptateur** `tools/compat/adapters/<tag>.kt` (≤ 40 lignes : construction de `ReceiverServer` et du client **à cette étiquette**) dans `core/src/test/kotlin/castbridge/compat/` du worktree ⇒ `gradle --offline :core:testClasses` ⇒ lancement de `PersonaMain` (TV de l'étiquette sur loopback, client de la même étiquette, 14 échanges canoniques DESIGN § 4.1) à travers un **tee TCP** du script qui écrit les octets bruts ⇒ `android/core/src/test/resources/compat/<tag>/NN-<nom>.http` + `manifest.json` (étiquette, sha du commit, date, `protocol` observé, caps observées/impliquées, drapeau `closeWithoutDrain` par échange, sha256 de chaque fichier, `origin: recorded|handwritten`) ⇒ **masquage** (`<PIN>`, `<TOKEN>`, adresses) et **refus d'écrire** si une valeur ressemblant à un secret subsiste ; `--handwritten` : gabarit vide à remplir à la main si le worktree ne compile pas hors ligne (dit dans le rapport) ; `--verify` : rejoue les sha256. (2) Personas à enregistrer **maintenant** (D-W19-6) : `tv-0.14.26-beta`, `tv-0.14.25-beta`, `tv-0.14.24-beta`, `phone-1.2.39-beta`, `phone-1.2.38-beta` ; `tv-0.14.22-beta` meilleur effort. (3) `CT/compat/TranscriptTv.kt` : NanoHTTPD port 0 qui répond **octet pour octet** à une requête reconnue par `(méthode, chemin, paramètres clés)` et reproduit `closeWithoutDrain` ; requête inconnue ⇒ 404 + **échec** « le client HEAD appelle une route que cette TV n'a pas » (S-10). (4) `CT/compat/TranscriptPhone.kt` : rejoue les requêtes enregistrées d'un téléphone contre la vraie `ReceiverServer` HEAD et vérifie `expect.json` par échange (classe de statut ; champs que l'ancien analyseur lit : `error`/`message`, `length`, `done`, `retryMs`, `id`, `v`, `pinRequired`). (5) `CT/compat/CompatMatrixTest.kt` : paramétré sur les personas présentes : **B** (client HEAD × chaque TV persona : parcours P-05 code, P-11 copie 1 Mio, P-39 trois fichiers, refus `NAME_TAKEN`, 429, `verifying`) et **C** (chaque téléphone persona × TV HEAD) ; fenêtre DESIGN § 2.3 : persona dans P-3…P+1 ⇒ **vert exigé** ; au-delà ⇒ « meilleur effort » (jaune toléré, jamais rouge sur `hello`/code/copie simple). (6) `tools/tests/test_record_persona.py` : masquage, manifeste, `--verify`, refus sur secret, `--handwritten`.

## Pourquoi (preuves)
- Étiquettes présentes : `tv-0.14.22…26-beta`, `phone-1.2.38/39-beta` (`git tag`, 2026-10-03) : les versions du terrain sont reconstructibles.
- `core` est un module JVM pur (`docs/HANDOFF.md` « Reprendre dans une session cloud ») : un worktree à une étiquette compile sans SDK Android **si** le cache Gradle a ses dépendances (risque 1 du DESIGN § 8).
- `CT/journey/TvSim.kt:143` : construction de `ReceiverServer` en test = modèle de l'adaptateur.
- `android/core/src/test/resources/naming/frozen*.tsv` + `.sha256` : le dépôt gèle déjà des fixtures par somme : même mécanisme.

## Fichiers possédés
Nouveaux : `tools/compat/record_persona.py`, `tools/compat/shim/PersonaMain.kt`, `tools/compat/adapters/<tag>.kt` (un par étiquette), `tools/compat/README.md` (≤ 40 lignes, français), `tools/tests/test_record_persona.py`, `android/core/src/test/resources/compat/<tag>/*` (fixtures), `CT/compat/TranscriptTv.kt`, `CT/compat/TranscriptPhone.kt`, `CT/compat/Persona.kt` (lecture du manifeste), `CT/compat/CompatMatrixTest.kt`, `CT/compat/TranscriptSelfTest.kt`. **Hors zone** : tout `C/` (si un échange révèle un trou : `QUESTION:`), `tools/release/*` (w19-05), `S/`, `R/`.

## Étapes
1. `TranscriptSelfTest` rouge : une persona **de HEAD** enregistrée par le shim contre `ReceiverServer` HEAD, rejouée par `TranscriptTv`, donne au client HEAD exactement les mêmes résultats que la vraie TV (auto-cohérence) ; secret dans un `.http` ⇒ le test de masquage échoue.
2. Shim + script + tee ; enregistrer `tv-0.14.26-beta` d'abord (adaptateur le plus proche de HEAD), puis 25, 24, les deux téléphones, 22 en dernier ; pour chaque étiquette : noter dans le manifeste les options de construction et les routes absentes (404 enregistrés).
3. Rejoueurs ; `expect.json` par persona téléphone (ce que **cet** analyseur lit : à établir par `git show <tag>:…TvClient.kt`).
4. `CompatMatrixTest` paramétré ; fenêtre ; rapport de matrice imprimé (grille ASCII) dans la sortie du test.
5. **Vert** : porte ; durée totale de la matrice < 3 min.

## Critères d'acceptation
- 5 personas enregistrées (`origin: recorded`) ou, pour chacune manquante, la raison exacte (erreur de compilation hors ligne) et une persona `handwritten` ; aucune valeur de PIN/jeton dans `resources/compat/`.
- Matrice verte pour tous les couples dans la fenêtre ; les écarts hors fenêtre listés (jaune) avec le parcours concerné.
- `--verify` détecte une fixture modifiée (sha256).

## Cas limites
Étiquette dont `ReceiverServer` exige un PIN ⇒ PIN de test `000000` masqué ; échange qui dépend de l'heure (`retryAfter`) ⇒ champ normalisé dans `expect.json` ; port 0 dans les en-têtes `Host` ⇒ normalisé ; persona TV **sans** `/api/have` ⇒ le client HEAD ne doit **pas** l'appeler (S-10, grâce à `FeatureGate` de w19-02).

## À ne pas faire
Modifier un fichier à l'étiquette (le worktree est jetable) ; pousser ; enregistrer contre un appareil ; écrire une persona de mémoire sans la marquer `handwritten`.

## Rapport
RAPPORT + grille de la matrice (ASCII) + `PERSONAS: <tag>=recorded|handwritten(raison)` + `SYMBIOSE: cap=— · proto=— · reason=— · deux écrans=—`.
