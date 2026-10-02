# w7-25 — CI W7 (vecteurs sync/keyring, tests `castbridge.core.link.*`, contrôles de source), vérification de la table OEM

**Vague 7d · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT (après 7a-7c fusionnées).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 5.3, § 9. Branche `claude/sonnet-w7-25`. Rapport : `docs/agent-reports/sonnet-w7-25.md`.

## Objectif
(1) `.github/workflows/tools.yml` : `python3 tools/activation/verify_vectors.py` couvre déjà les nouveaux vecteurs (vérifier) ; ajouter `tools/tests/test_link_test_scripts.py`, `test_routes.py`, `test_backup_rules.py` s'ils n'y sont pas ; (2) `.github/workflows/android.yml` : `:core:test --tests 'castbridge.core.link.*'` explicite + **contrôles de source** W7 : (a) aucun `import android` dans `C/link/` ; (b) aucune chaîne « Réparer la connexion », « Synchronisation », « TV a changé d'identité » hors `LinkTexts`/`SelfTest`/`ActivationScreenState` dans `S/` et `R/` ; (c) aucun `startDiscovery(` hors `S/TvLink.kt` (`BtFinder`) et `OL/TvBluetooth.kt` ; (d) aucun `ACTION_REQUEST_DISCOVERABLE` hors `R/TvPermissions.kt` ; (e) aucun `requestPermissions(` dans `R/ActivationActivity.kt`/`R/PlayerActivity.kt` ; (f) `grep -rn 'Scrub\|scrub' S/link/JournalShare.kt` ≥ 1 ; scripts dans `tools/checks/check_w7_sources.sh` + test Python ; (3) **table OEM** (`C/link/OemBattery.kt`) : un test Python `tools/tests/test_oem_table.py` qui lit la table (export JSON par un test Kotlin `OemBatteryTest.writeJson` ou parsing simple) et vérifie que chaque entrée a `label`, `fallbackAction` non vide, et que les composants cités existent dans une liste de référence publique **documentée** dans le fichier (sources : documentation des fabricants ou dépôts libres, citées par URL, « non garanti ») ; (4) `docs/COORDINATION.md` § CI : nouvelles lignes.

## Pourquoi (preuves)
- `.github/workflows/{tools,android,release}.yml` (w1-07, w6-23 : même structure) ; `docs/COORDINATION.md` § CI ; `tools/requirements-dev.txt`.
- Cahiers w7-08 (`OemBattery`), w7-19/w7-20 (règles de source), w7-11/w7-24 (tests Python).

## Fichiers possédés
`.github/workflows/tools.yml`, `.github/workflows/android.yml`, nouveaux `tools/checks/check_w7_sources.sh`, `tools/tests/test_check_w7_sources.py`, `tools/tests/test_oem_table.py`, `docs/COORDINATION.md` (§ CI seulement), `tools/requirements-dev.txt` (si besoin). **Hors zone** : code Kotlin (si `OemBatteryTest.writeJson` manque, lire la table par regex et le dire).

## Étapes
1. `check_w7_sources.sh` : les six contrôles, sortie claire, code de retour ; tolérant aux fichiers absents (cahier non fusionné ⇒ « SKIP ») ; test Python avec un arbre temporaire (cas pass/fail).
2. Workflows : étapes ajoutées, `--offline` conservé, pas de secret.
3. Table OEM : test + liste de référence ; chaque composant cité relié à sa source.
4. `COORDINATION.md` § CI : lignes W7.

## Critères d'acceptation
```sh
bash tools/checks/check_w7_sources.sh                                   # 0 sur integration/agents après fusion 7a-7c
python3 -m unittest discover -s tools/tests -p 'test_check_w7_sources.py'   # vert
python3 -m unittest discover -s tools/tests -p 'test_oem_table.py'          # vert
grep -c 'check_w7_sources' .github/workflows/android.yml                    # ≥ 1
```

## Cas limites
Cahier 7c non fusionné ⇒ contrôles `SKIP` sans échec ; table OEM vide ⇒ test échoue (volontaire).

## À ne pas faire
Pas d'exécution d'émulateur en CI ; pas de secret ; ne pas modifier `release.yml`.

## Rapport
`STATUT`, liste des contrôles et leur résultat sur la branche, sources OEM citées.
