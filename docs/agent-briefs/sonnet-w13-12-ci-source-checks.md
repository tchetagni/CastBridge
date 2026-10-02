# w13-12 — CI et contrôles de source W13 : `tools/checks/check_w13_sources.sh`, test Python, branchement dans `android.yml`/`tools.yml`
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : aucune (script de greps donné) · statut : PRÊT
> **Groupe : W13-d** (vague W13, tranche S2) · prérequis : w13-01 … w13-10 fusionnés · porte : `bash tools/checks/check_w13_sources.sh && python3 -m unittest tools.tests.test_check_w13_sources`
> **Jauge : ≈ 100 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 13d (CI) · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W13` § 3.7, § 4.1. Branche `claude/sonnet-w13-12`. Rapport : `docs/agent-reports/sonnet-w13-12.md`.

## Objectif
Un script de contrôles de source (exit ≠ 0 au premier écart) et son test Python, branchés dans les workflows existants, pour que les invariants W13 survivent aux vagues suivantes (W7 en particulier).

## Pourquoi (preuves)
- Workflows : `.github/workflows/tools.yml` (job `python-tools`), `.github/workflows/android.yml` (`:core:test`) ; description : `docs/COORDINATION.md` § « Flux CI ».
- Modèle de script de contrôles : W7 prévoit `tools/checks/check_w7_sources.sh` (w7-25, non exécuté) : **même emplacement, même style**, sans dépendre de W7.
- Invariants à figer (voir cahiers) : w13-01/03 (aucun `import android` dans `C/link/`), w13-04 (aucun `catch` muet, aucune chaîne « HTTP 403 » dans `TvClient.kt`), w13-05 (`"code":"` dans `ReceiverServer.kt` ≥ 16 ; `link/blockers` dans `routes.txt`), w13-08 (aucun `pin=` dans `RepairDeepLink.kt` ; `castbridge_pins` exclu des sauvegardes), w13-09 (`OpenWithActivity.kt` : « Aucune TV ajoutée » gardé par `isEmpty()`), w13-02 (aucun texte français de blocage hors `BlockerTexts.kt`/`LinkText.kt` dans `C/link/`, `C/tv/TvClient.kt`, `C/xfer/`).

## Fichiers possédés
Nouveaux : `tools/checks/check_w13_sources.sh`, `tools/tests/test_check_w13_sources.py` ; `.github/workflows/tools.yml` (une étape dans `python-tools`), `.github/workflows/android.yml` (une étape avant Gradle), `docs/COORDINATION.md` (§ CI : une ligne). **Hors zone** : tout le reste.

## Étapes
1. Script : une fonction `check "<libellé>" <commande grep>` par invariant ; sortie lisible (`OK`/`ÉCART : …`) ; `set -euo pipefail` ; chemins relatifs à la racine du dépôt.
2. Test Python : exécute le script sur l'arbre courant (vert) ; puis sur une copie temporaire où un invariant est violé (ex. ajoute `import android.os.Build` dans un fichier de `C/link/`) ⇒ exit ≠ 0 avec le libellé.
3. Workflows : étape `Contrôles de source W13` ; `docs/COORDINATION.md` : une ligne dans la liste des jobs.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -n 'check_w13_sources' .github/workflows/*.yml` ⇒ 2 lignes ; le script liste ≥ 8 contrôles.

## À ne pas faire
Pas de dépendance Python nouvelle ; ne pas modifier les tests existants ; ne pas toucher `release.yml`.

## Rapport
`STATUT`, liste des contrôles, durée du script.
