# w14-13 — `tools/smoke/migrate_test.py` : mise à jour en place scriptée sur l'émulateur (installer N-1, peupler confiance/PIN/activation/bibliothèque/lots, installer N, comparer ; rétrogradation refusée)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT
> **Groupe : W14-e** (vague W14, tranche S2) · prérequis : w14-07 fusionné (squelette), w14-10 souhaité (HELLO TCP) · porte : `python3 -m unittest discover -s tools/tests -p 'test_migrate.py' && python3 tools/smoke/migrate_test.py --dry-run`
> **Jauge : ≈ 350 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 14e (outils) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 4.2. Parcours P-28, P-29, P-30. Branche `claude/sonnet-w14-13`. Rapport : `docs/agent-reports/sonnet-w14-13.md`.

## Objectif
Prouver sur `cbconnect_tv` (Android TV 34 arm64, `emulator-5580`) et `cbconnect_phone` (`emulator-5582`) qu'une **mise à jour par-dessus** (même signature, `versionCode` supérieur) conserve tout l'état utilisateur des deux applications, par les deux chemins d'installation de la TV (`adb install -r` et `POST /api/update/install` avec PIN), et qu'une rétrogradation est refusée (409) sans rien casser.

## Pourquoi (preuves)
- R-03 : la TV a été **réinstallée** (pas mise à jour) ⇒ `trusted_phones.txt` perdu, PIN changé (`R/TvPrefs.kt:11-15`), `allowBackup=false` ; il faut que la **mise à jour** soit prouvée sûre et que la consigne « jamais désinstaller » tienne.
- `R/UpdateInstaller.kt` : `code < installedCode()` ⇒ 409 en release ; même signataire exigé (400) ; `POST /api/update/install` (`name`, réservé au PIN : `TrustRegistry.tokenMayCall`).
- APK précédentes disponibles : `~/CastBridge-release/` (ex. `CastBridge-TV-0.14.18-beta-verrouillee-armeabi-v7a-release.apk`, `CastBridge-phone-1.2.31-beta.apk`) ; pour l'émulateur arm64 il faut l'APK **arm64** ou universelle de N-1 : si absente, la construire depuis le tag/commit (le script le dit, ne la construit pas).
- Dépôt d'APK sur la TV : `PUT /upload/<nom>.apk?offset=0&total=<taille>` puis `POST /api/apk/install?names=` (`docs/HANDOFF.md` § 5) ou `cbdev install` (SSH 2223) ; l'émulateur accepte `USER_ACTION_NOT_REQUIRED` (API 31+) pour une mise à jour du même signataire.

## Fichiers possédés
Nouveaux : `tools/smoke/migrate_test.py`, `tools/smoke/checks/migration.py` (pas enregistrés : `migrate_tv`, `migrate_phone`, `downgrade_refused`, modes `{"emu"}`), `tools/smoke/lib/fingerprint.py` (empreinte d'état), `tools/tests/test_migrate.py` (empreintes et diff sur fixtures ; faux `adb`). **Hors zone** : `tools/smoke/core/**`, autres `checks/`.

## Étapes
1. `fingerprint.py` : empreinte TV = `GET /api/apk` (versions), `/api/activation` (`label`, `locked`), `/api/connections` (téléphones), `/api/library` (noms, tailles, catégories), `/api/lots` (ids), `/api/rental` (état), + sur l'émulateur `run-as castbridge.receiver ls -R files shared_prefs` (noms de fichiers, tailles) ; empreinte téléphone = `run-as castbridge.sender ls -R files shared_prefs` + `dumpsys package castbridge.sender | grep version`. Comparaison champ à champ, diff lisible.
2. `migrate_test.py --tv-old A --tv-new B --phone-old C --phone-new D [--dry-run]` : (a) `install` N-1 des deux ; (b) **peupler** : association (HELLO TCP via fausse TV impossible ici : utiliser le chemin **PIN** ; si w14-10 a livré le transport côté téléphone, aussi la confiance), 2 fichiers reçus (via `phone_copy` ou `PUT /upload`), activation de TEST (`tools/rental-test` : clé de bureau de TEST), 1 lot, 1 réglage ; (c) empreinte avant ; (d) installer N : TV par `adb install -r` **puis** sur une seconde passe par `/api/update/install` (deux empreintes) ; téléphone `adb install -r` ; (e) empreinte après ⇒ égalité (sauf versions) ; (f) `POST /api/update/install` de N-1 ⇒ 409 et empreinte inchangée.
3. Enregistrer les 3 pas dans le registre w14-07 pour que `smoke.py --tv emu --only P-28,P-29,P-30` les lance ; budget : 4 min.
4. Tests : empreinte depuis fixtures JSON, diff, faux `adb` qui renvoie des versions.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c 'pm uninstall\|pm clear' tools/smoke/migrate_test.py tools/smoke/checks/migration.py` ⇒ 0 (une mise à jour ne désinstalle jamais ; l'état initial se fabrique sur un AVD **neuf** : `--wipe-data` au démarrage de l'émulateur par w14-07 `--boot --wipe`, émulateur seulement) ; `--dry-run` imprime le plan complet.

## Cas limites
APK N-1 arm64 absente ⇒ SKIP motivé avec la commande de construction ; `run-as` refusé sur une APK release (non debuggable) ⇒ l'empreinte se limite aux routes HTTP (le dire) ; activation de TEST : jamais la clé de production.

## À ne pas faire
Aucune exécution sur une TV ou un téléphone réels ; aucune clé réelle ; aucun secret dans les fixtures ; ne pas modifier `tools/rental-test/`.

## Rapport
`STATUT`, plan `--dry-run`, champs de l'empreinte, limites (`run-as`, APK N-1), ce qui reste humain (P-28 sur GaiaOS : point 1 de la liste humaine).
