# w14-15 — CI et porte locale : `tools/checks/gate.sh` (J + lint en < 3 min), job `journeys` dans `android.yml`, job `smoke-tools` dans `tools.yml`, rappel dans `docs/COORDINATION.md` § CI
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet si le YAML ne se charge pas après une correction · statut : PRÊT
> **Groupe : W14-f** (vague W14, tranche S3) · prérequis : w14-01…06 fusionnés (les tests existent), w14-07 (tests Python de la fumée) · porte : `python3 -c "import yaml,glob;[yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')]" && bash -n tools/checks/gate.sh`
> **Jauge : ≈ 80 k jetons entrée / 6 k sortie** (effort S) · audit Opus : non

**Vague 14f (CI) · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W14` § 5, § 2.3. Modèles : `.github/workflows/android.yml` (job « Build APK », Gradle 8.14.3, JDK 17, `timeout-minutes: 30`), `.github/workflows/tools.yml` (Python 3.12, `unittest discover`), `tools/agents/gradle-lock.sh`. Branche `claude/sonnet-w14-15`. Rapport : `docs/agent-reports/sonnet-w14-15.md`.

## Objectif
Que la couche J et le lint tournent **à chaque push** et en une commande locale ; que les tests Python de la fumée tournent en CI ; que la fumée elle-même (émulateur) reste **locale** (aucun émulateur en CI : décision, pas d'oubli).

## Fichiers possédés
Nouveau : `tools/checks/gate.sh`. Zones : `.github/workflows/android.yml` (**ajout** d'un job `journeys` après `build`, sans modifier `build`), `.github/workflows/tools.yml` (**ajout** d'une étape `smoke-tools`), `docs/COORDINATION.md` (**ajout** de 6 lignes dans la section « CI » existante). **Hors zone** : `release.yml`, `backend.yml`, tout autre fichier.

## Étapes (mécaniques)
1. `tools/checks/gate.sh` (exact) :
```bash
#!/usr/bin/env bash
# Porte locale W14 : parcours JVM + lints, < 3 min. Usage : tools/checks/gate.sh [--full]
set -euo pipefail
cd "$(dirname "$0")/../../android"
FILTER=(--tests 'castbridge.core.journey.*' --tests 'castbridge.core.lint.*')
[ "${1:-}" = "--full" ] && FILTER=()
exec ../tools/agents/gradle-lock.sh gradle --offline :core:test "${FILTER[@]}"
```
2. `android.yml` : job `journeys` (`runs-on: ubuntu-24.04`, `timeout-minutes: 15`, mêmes étapes `checkout`/`setup-java`/`gradle` que `build`), commande `cd android && gradle --stacktrace :core:test --tests 'castbridge.core.journey.*' --tests 'castbridge.core.lint.*'`, artefact `android/core/build/reports/tests/test` nommé `journeys-report`. `needs: build` **non** (parallèle).
3. `tools.yml` : étape `- run: python3 -m unittest discover -s tools/tests -p 'test_smoke*.py' -p 'test_migrate.py' -p 'test_release_gate.py'` (si `unittest` n'accepte qu'un `-p` : trois lignes).
4. `docs/COORDINATION.md` § CI : lignes « `journeys` (android.yml) : parcours JVM + lint de pureté à chaque push ; `tools.yml` : tests des scripts de fumée ; la fumée sur émulateur et la liste humaine restent **locales** (`tools/smoke/`, `docs/test-plans/`) ; porte locale : `tools/checks/gate.sh` ».

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c 'journeys' .github/workflows/android.yml` ≥ 2 ; `grep -c 'test_smoke' .github/workflows/tools.yml` ≥ 1 ; `grep -c 'gate.sh' docs/COORDINATION.md` ≥ 1 ; `chmod +x tools/checks/gate.sh` (fichier exécutable dans git : `git ls-files -s tools/checks/gate.sh` commence par `100755`).

## À ne pas faire
Aucun émulateur en CI ; ne pas modifier le job `build` ; aucun secret ; ne pas lancer Gradle (pas nécessaire ici).

## Rapport
`STATUT`, extraits YAML ajoutés, sortie des deux commandes de la porte.
