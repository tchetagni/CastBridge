# w6-23 — CI : vecteurs de preuve (Python, Java), contrôle « aucun haché super-admin » sur les APK distribuables, tests des règles de sauvegarde et des routes, § CI de COORDINATION

**Vague 6e · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT (après w6-10, w6-11, w6-15, w6-19 fusionnés).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 4.3, § 5 (tests). Branche `claude/sonnet-w6-23`. Rapport : `docs/agent-reports/sonnet-w6-23.md`.

## Objectif
(1) `.github/workflows/tools.yml` : job `vectors` rejoue aussi `proof-vectors.json` (déjà via `verify_vectors.py` : vérifier la sortie « proof: N cas ») ; `python-tools` inclut `test_check_no_superadmin.py`, `test_backup_rules.py`, `test_routes.py` ; (2) `.github/workflows/android.yml` : après `assembleDebug`, `tools/release/check_no_superadmin.sh` sur les APK produits (build ordinaire ⇒ doit passer) ; (3) `.github/workflows/release.yml` : même contrôle sur les APK non signés (`NON-VERROUILLEE-NE-PAS-DISTRIBUER`) ; (4) `docs/COORDINATION.md` § « Flux CI » : les lignes ajoutées ; (5) `tools/requirements-dev.txt` inchangé sauf besoin.

## Pourquoi (preuves)
- `docs/COORDINATION.md` § « Flux CI » (trois workflows), `.github/workflows/{tools,android,release}.yml` (modifiés localement au 2026-10-02 : lire l'état sur `integration/agents`), `tools/release/check_no_superadmin.sh` (w6-19), `tools/activation/verify_vectors.py` (w6-10).

## Fichiers possédés
`.github/workflows/{tools,android,release}.yml`, `docs/COORDINATION.md` (§ CI), `tools/requirements-dev.txt`. **Hors zone** : tout le reste.

## Étapes
1. Lire les workflows ; ajouter les étapes (sans changer les déclencheurs ni les délais).
2. `android.yml` : étape `Vérifier l'absence de haché super-admin` (`bash tools/release/check_no_superadmin.sh android/sender/build/outputs/apk/debug/*.apk`).
3. `release.yml` : idem sur les artefacts.
4. COORDINATION § CI.

## Critères d'acceptation
```sh
grep -n 'check_no_superadmin' .github/workflows/android.yml .github/workflows/release.yml   # ≥ 2
grep -n 'test_check_no_superadmin\|test_backup_rules\|test_routes' .github/workflows/tools.yml   # ≥ 1
python3 -c "import yaml,sys;[yaml.safe_load(open(f)) for f in ['.github/workflows/tools.yml','.github/workflows/android.yml','.github/workflows/release.yml']];print('yaml ok')"
```

## Cas limites
Script absent (w6-19 non fusionné) ⇒ l'étape est `continue-on-error: false` mais le cahier attend w6-19 : ne pas lancer avant.

## À ne pas faire
Pas de secret dans les workflows ; pas de signature en CI (docs/RELEASES.md) ; pas de déploiement.

## Rapport
`STATUT`, étapes ajoutées, durée CI avant/après si mesurable.
