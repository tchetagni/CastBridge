# w6-13 — CastBridge-TV : collecte « toute la TV » branchée (écran, tranches, lancements, Sudoku, connexions), magasin parental chiffré au repos, rapports v2 émis

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (w6-12 en parallèle)
> **Groupe : W6c-1** (vague W6c) · prérequis : w6-05, w6-06, w6-07 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin && python3 -m unittest discover -s tools/tests -p 'test_backup_rules.py'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non

**Vague 6c · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après w6-05, w6-06, w6-07 fusionnés ; w6-12 en parallèle fournit `installSigner()` : coder contre `ActivationCenter.installSigner()` et, s'il manque, `null` = HMAC seul).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.2, § 2.3, § 2.4, § 2.5. Branche `claude/sonnet-w6-13`. Rapport : `docs/agent-reports/sonnet-w6-13.md`.

## Objectif
(1) `ParentalHub` alimente `ScreenSegments` (dans le `tick` 15 s via `ForegroundWatcher.screenOn`), `UsageSlots` (dans `accountApp`, tranche courante), lancements (dans `onForeground`), `ConnectionLog` (nouveau `ParentalHub.note(kind, label)` appelé par `TvService` à la connexion d'un téléphone (nom de confiance), par `UsbImporter` (libellé du volume), `SshControl` (ouverture), `TunnelHub` (session d'assistance)), `AppInventoryDiff` (dans `superviseTick`), `SessionTracker` pour `SudokuActivity` (type `SUDOKU`), `DailyAggregates.roll` au changement de jour ; (2) `R/ParentalStore.kt` : fichiers `files/parental/*.json` par `SafeFile` (w1-02), **chiffrés** (`SecretWrapper` w4-01 sous une clé de magasin enveloppée par `KeystoreWrapper` w4-03 ; repli : clé dérivée de la clé d'installation, état « logicielle ») ; migration idempotente depuis `castbridge_parental_reports` ; (3) `ParentalReports` reçoit `signer = { ActivationCenter.installSigner() }` et les `Inputs` v2 (`ReportV2`) ; `snapshot` servi et **journalisé** dans `TvJournal` ; (4) `Category`/`categoryOf` : `SudokuActivity` ⇒ `GAMES` si w5-18 ne l'a pas fait.

## Pourquoi (preuves)
- `R/ParentalHub.kt:93-121` (`init` : création des magasins, `rp.extras`), `:124-131` (`journalExtras`), `:340-374` (`tick`), `:419-425` (`superviseTick`), `:428-443` (`onForeground`), `:446-458` (`accountApp`) ; `R/ForegroundWatcher.kt:69` (`screenOn`) ; `R/TvService.kt:216` (`BtServer(... trusted = ::btTrusted ...)`), connexions de téléphones : `TvService.trust`/`btHello` ; `R/UsbImporter.kt`, `R/SshControl.kt`, `R/TunnelHub.kt` (points d'appel : une ligne chacun) ; `C/parental/Collect.kt` (w6-05), `C/parental/ReportV2.kt` (w6-07), `C/parental/Holders.kt` (w6-06) ; `R/KeystoreWrapper.kt` (w4-03), `C/crypto/SecretWrapper.kt` (w4-01), `C/owner/SafeFile.kt`.

## Fichiers possédés
`R/ParentalHub.kt`, nouveau `R/ParentalStore.kt`, `R/ForegroundWatcher.kt`, `R/TvService.kt` (lignes d'appel `ParentalHub.note(...)` et câblage `signer`), `R/UsbImporter.kt`, `R/SshControl.kt`, `R/TunnelHub.kt` (une ligne chacun), `R/SudokuActivity.kt` (si un crochet y est nécessaire), `android/receiver/src/main/res/xml/{backup_rules,data_extraction_rules}.xml` (exclure `files/parental/`). **Hors zone** : `R/ParentalActivity.kt`, `R/ParentalUi.kt`, `R/PlayerActivity.kt`, `R/HomeScreen.kt`, `R/KeyBadgeOverlay.kt` (w6-14), `R/BtServer.kt`, `C/parental/ParentalApi.kt` (w6-15), `R/ActivationCenter.kt`, `R/RentalHub.kt` (w6-12), tout `C/parental/*.kt`.

## Étapes
1. `ParentalStore` : `open(ctx)` ⇒ `KvStore` chiffré (clé AES-256 aléatoire, enveloppée ; fichiers `recips.json`, `outbox.json`, `journal.json`, `slots.json`, `screen.json`, `conn.json`, `agg.json`) ; `migrateFromPrefs()` une fois (drapeau) ; `protectionLabel()`.
2. `ParentalHub.init` : magasins w6-05 sur `ParentalStore` ; `ParentalReports(signer = …, inputs = …)` ; `TvJournal` 800/14 j ; `rp.onSnapshotServed = { name -> journal.record(EventType.CONNECTION, null, "<name> a consulté l'état en direct") }`.
3. `tick` : `screen.observe(ForegroundWatcher.screenOn(c), now)` ; changement de jour ⇒ `aggregates.roll(...)`, `flushNow()` ; sinon `flushIfDue(now)` sur chaque structure ; Sudoku dans `kind`/`type`/`title` (« Sudoku »).
4. `accountApp` : `slots.add(day, pkg, label, minuteOfDay, dt)` ; `onForeground` : `slots.launch(day, pkg)` quand `pkg` change et n'est pas essentiel.
5. `superviseTick` : `inventory.apply(AppCatalog.launcherApps(c))` ⇒ retraits ⇒ `TvJournal` (« Application retirée : … »).
6. `note(kind, label)` : public, thread-safe (poste sur `main`), nettoie le libellé (w6-05) ; appels : `TvService` (téléphone connecté : nom de confiance, **jamais** l'adresse), `UsbImporter` (volume), `SshControl` (« SSH »), `TunnelHub` (« Assistance à distance ») ; **aucun** appel depuis le tunnel vers le parental dans l'autre sens.
7. XML de sauvegarde : `files/parental/` exclu.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin   # compile
grep -c 'ParentalHub.note(' android/receiver/src/main/kotlin/castbridge/receiver/*.kt   # ≥ 4
grep -rn 'TvConnect\|DeviceClient' android/receiver/src/main/kotlin/castbridge/receiver/Parental*.kt   # 0 hit
python3 -m unittest discover -s tools/tests -p 'test_backup_rules.py'   # vert (après ajout de files/parental/)
```
Observable (émulateur TV, surveillance active) : après 10 min avec YouTube ouvert puis le Sudoku, « Envoyer un rapport maintenant » produit un `daily` v2 contenant `screen.onMin`, `apps[].slots` non nuls, `games.sudoku.n = 1`, `connections` avec le téléphone ; `adb shell run-as <pkg> cat files/parental/outbox.json` est **illisible** (chiffré).

## Cas limites
TV sans `UsageStatsManager`/accès refusé : `slots` vide, `quality.unavailable` contient `apps` ; écran éteint : aucune écriture (vérifier avec `adb logcat` que `SafeFile` n'écrit pas) ; `KeystoreWrapper` indisponible : repli logiciel + `protectionLabel()` ; migration à partir d'une TV sans données : aucun fichier créé avant le premier détenteur.

## À ne pas faire
Pas de nouvelle permission ; pas de wakelock ; aucune lecture du contenu des fenêtres ; ne pas toucher aux fichiers de w6-14/w6-15 ; aucune donnée parentale vers le serveur ou le tunnel.

## Rapport
`STATUT`, points d'appel exacts, taille des fichiers après une journée d'émulateur, état de protection sur la TV de référence (à vérifier par le coordinateur).
