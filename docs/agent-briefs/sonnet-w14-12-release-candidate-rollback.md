# w14-12 — Discipline de remise : étage « candidate », `handover.sh` qui refuse sans rapport de fumée ni liste humaine, plan de retour arrière, `docs/RELEASES.md` § 15-16
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet si une ancre « avant » est introuvable deux fois · statut : PRÊT
> **Groupe : W14-f** (vague W14, tranche S3) · prérequis : w14-07 fusionné (format de `REPORT.md`) · porte : `bash -n tools/release/candidate.sh tools/release/handover.sh tools/release/rollback-plan.sh && python3 -m unittest discover -s tools/tests -p 'test_release_gate.py'`
> **Jauge : ≈ 120 k jetons entrée / 10 k sortie** (effort S) · audit Opus : non

**Vague 14f (outils/docs) · Effort S (≈ 0,75 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W14` § 4.1 (voies, candidate), § 4.3 (retour arrière), D-W14-6. Références : `docs/RELEASES.md` § 7-10, § 12-13 ; `docs/coordination/VERSIONING-DEVOPS-2026-10-02.md` § 3, § 13 ; `tools/release/check_versions.py`, `sha256sums.sh`, `tag-plan.sh` (modèles de scripts en simulation par défaut) ; `tools/tests/test_release_tools.py` (faux outils par `PATH`). Branche `claude/sonnet-w14-12`. Rapport : `docs/agent-reports/sonnet-w14-12.md`.

## Objectif
Trois scripts bash (simulation par défaut, `--apply` pour agir) et deux sections de `docs/RELEASES.md` :
- `candidate.sh <tv|phone> [N]` : construit la candidate **verrouillée** (`-PrequireActivation=true -PtrustedKeysFile=…` lu depuis l'environnement, jamais en dur) avec `-Pcastbridge.versionName=<version.properties>-rc.N`, **sans** toucher `version.properties`, signe comme aujourd'hui, dépose dans `~/CastBridge-release/candidates/`, imprime les commandes d'installation **émulateur** puis **téléphone** (`adb install -r`), et rappelle : « jamais sur la TV avant la liste humaine ».
- `handover.sh <apk-tv> <apk-phone> --smoke <REPORT.md> --human <livraison-<version>.md>` : refuse (code 1, message) si le rapport de fumée n'est pas `PASS`, s'il date de plus de 24 h ou d'un autre commit (`commit:` dans le rapport vs `git rev-parse --short HEAD`), si l'arbre est sale, si la liste humaine n'a pas `12/12` (ou `verdict: LIVRER` avec accord noté), si `check_versions.py --apk` échoue ; sinon imprime le plan de copie (`Download/` de la clé par SSH, `SHA256SUMS`) et, avec `--apply`, l'exécute via les commandes existantes de `docs/RELEASES.md` § 8.
- `rollback-plan.sh <app> <version-précédente>` : imprime le plan de retour arrière (APK précédente sur la clé ; si la TV a déjà la version N : `git worktree add ../cb-old tv-<N-1>` + build verrouillé avec `-Pcastbridge.versionCode=<prochain code libre>` et nom `<N-1>-beta.1`, `check_versions.py`, `SHA256SUMS`, ligne `docs/REGRESSIONS.md`) ; `--apply` crée le worktree seulement.

## Fichiers possédés
Nouveaux : `tools/release/candidate.sh`, `tools/release/handover.sh`, `tools/release/rollback-plan.sh`, `tools/tests/test_release_gate.py`. `docs/RELEASES.md` : **ajout** de `## 15. Étage « candidate »` et `## 16. Barrière de remise (`handover.sh`) et retour arrière` à la fin du fichier (aucune ligne existante modifiée sauf un renvoi d'une ligne à la fin du § 7 : « Voir § 15-16 pour la candidate et la barrière »). **Hors zone** : `version.properties`, `check_versions.py`, `.github/**`.

## Étapes (mécaniques)
1. Lire `tools/release/tag-plan.sh` (structure `--apply`, messages) et `tools/release/sha256sums.sh` ; copier leur en-tête (set -euo pipefail, usage, simulation par défaut).
2. Écrire les trois scripts avec les contrôles listés, chaque refus = une ligne commençant par `REFUS :` ; chaque commande exécutée est d'abord imprimée.
3. `test_release_gate.py` : dépôt git temporaire + faux `gradle`/`adb`/`ssh`/`git` dans `PATH` (comme `test_release_tools.py`) ; cas : rapport PASS récent ⇒ plan imprimé, code 0 ; rapport FAIL ⇒ code 1 ; rapport d'un autre commit ⇒ 1 ; arbre sale ⇒ 1 ; liste humaine 11/12 sans accord ⇒ 1 ; `rollback-plan.sh` imprime un code `versionCode` strictement supérieur au courant.
4. `docs/RELEASES.md` § 15 : table voies (beta/stable/candidate/test) de `DESIGN-W14` § 4.1 recopiée ; § 16 : usage des trois scripts, règle « APK précédente gardée sur la clé jusqu'à deux livraisons réussies », renvoi à `docs/test-plans/CHECKLIST-HUMAINE-LIVRAISON.md` et `docs/REGRESSIONS.md`.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c '^## 15\.' docs/RELEASES.md` ⇒ 1 ; `grep -c '^## 16\.' docs/RELEASES.md` ⇒ 1 ; `grep -n 'trustedKeysFile=' tools/release/candidate.sh | grep -v '\$' | wc -l` ⇒ 0 (jamais de chemin de clé en dur) ; `grep -c 'REFUS :' tools/release/handover.sh` ≥ 5.

## À ne pas faire
Ne pas modifier `version.properties` ni les scripts existants ; aucun secret ; aucun `--apply` exécuté par l'agent ; pas de construction d'APK.

## Rapport
`STATUT`, sorties de `--help` des trois scripts, cas de test et résultats, lignes ajoutées à `docs/RELEASES.md` (numéros).
