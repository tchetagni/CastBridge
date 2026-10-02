# w14-03 — Cœur : parcours J-12…J-18 (copie LAN petite/grosse, Bluetooth, annulation, redémarrage TV, essai sans boucle, plus de place) avec progression des deux côtés
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus en échantillon · statut : PRÊT
> **Groupe : W14-b** (vague W14, tranche S1) · prérequis : w14-01 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.TransferJourneyTest'`
> **Jauge : ≈ 350 k jetons entrée / 22 k sortie** (effort M) · audit Opus : échantillon

**Vague 14b (cœur) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 3.2 (J-12…J-18), § 1.1 (R-04). Parcours P-11…P-21. Branche `claude/sonnet-w14-03`. Rapport : `docs/agent-reports/sonnet-w14-03.md`.

## Objectif
Figer par sept parcours que **chaque copie montre une progression monotone sur le téléphone ET sur la TV**, finit par un texte final (jamais une disparition muette), survit à l'annulation et au redémarrage de la TV, et que l'essai / le manque de place sont refusés **une fois, avant le premier octet**, sans boucle.

## Pourquoi (preuves)
- R-04 : côté TV, l'état d'un transfert Bluetooth est une ligne écrasée (`R/BtServer.kt:113-127`) ; côté LAN, `TransferHost.stateJson`/`GET /api/transfer/state` existent (`C/xfer/TransferHost.kt`, `C/tv/ReceiverServer.kt`) mais aucun test ne les lit **pendant** une copie.
- Boucle 403 d'essai : `C/tv/TvClient.kt:256-259,282-289` (« En pause : HTTP 403… reprise automatique ») ; `C/xfer/TransferClient.kt:38-43` (texte générique).
- Reprise par carte de blocs après redémarrage : `C/xfer/*` (`.cbx/<id>.state`), `Outcome.SessionLost` (`C/xfer/Lanes.kt:45`), `attempts > 6` (`TransferClient.kt:148`).
- Pré-vol stockage : `C/tv/TvClient.kt:70-71` (`StorageCheck.status` 507 dans un corps 200), règle « 1 Go libre après ».

## Fichiers possédés
Nouveau : `CT/journey/TransferJourneyTest.kt`. **Hors zone** : harnais, `C/**` (voir règle `@Ignore` de w14-02).

## Étapes
1. J-12 (`lanSmallProgressBothSides`) : `send(small, fast=true)` ; pendant l'envoi (crochet `onProgress` du `UploadRun`), lire `tv.transferState(id)` à chaque échantillon ⇒ champ `received` **croissant** et ≤ `total` ; téléphone : `samples` monotone, dernier = `(total,total)` ; `receivedFiles()` = 1, taille exacte, nom propre ; dernière `Notice.final` et `progress == 100`.
2. J-13 (`lanBigMultiLaneVerifiedAndFiled`) : `send(big)` avec `lanes K=4` ; SHA-256 du fichier reçu = source ; aucun `.cbx` restant sous `tv.dir` ; `tv.transferState(id)` final = terminé.
3. J-14 (`bluetoothLaneProgressAndMoveDisabled`) : `phone` sans route IP (`FakeEnv.wifiUp=false`, session `btOnly`) ⇒ `SendChoices.decide(sendFacts()).moveEnabled == false` et `note == MOVE_NO_BT` ; `send(small)` par `BluetoothLane` (si non branchée dans `PhoneSim` : envoi CBT1 via `tv.bt`) ⇒ fichier reçu, `samples` monotone ; **TV** : un état de réception lisible pendant l'envoi (`tv.notices()` ou état `bt`) — si rien n'existe côté cœur : `@Ignore("REGRESSION R-04 ouverte : statut 1-bt écrasé, R/BtServer.kt:113-127")`.
4. J-15 (`cancelCleansBothSides`) : `send(big)` ; à 30 % `cancel(run)` ⇒ `finalState == "Cancelled"`, dernière `Notice.final`, `tv.transferState(id)` = aborted ou null, aucun `.cbx`, `receivedFiles()` vide.
5. J-16 (`tvRestartResumesFromBlockMap`) : `send(big)` ; à 30 % `tv.restart()` ; `clock.advance(5_000)` ; `run.await()` ⇒ fichier complet, `samples` ne repasse **jamais** sous le dernier `sent` d'avant redémarrage de plus d'un bloc, aucune `Notice` sans texte, nombre de `begin` ≤ 2.
6. J-17 (`trialRefusedOnceNoLoop`) : `tv.setTrial(true)` ; `send(small)` ⇒ nombre de requêtes vers `/upload`/`/api/transfer/*` après le premier 403 == 0 ; `finalState == "Failed"` ; texte contient « essai ». Si la boucle existe encore : `@Ignore("REGRESSION … F4 W13")`, ligne REGRESSIONS.
7. J-18 (`storageFullRefusedBeforeFirstByte`) : `tv.fill(freeBytes = 1 Gio)` ; `send(big)` ⇒ 0 octet envoyé (compteur du `TvSim`), texte contient « place ».
8. Instrumentation nécessaire mais absente du harnais (compteur de requêtes par route, `received` du transfert) : extension privée dans le test + note pour w14-01.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c '@Test' CT/journey/TransferJourneyTest.kt` ⇒ 7 ; durée < 30 s (fichier de 64 Mio en mémoire ou dossier temporaire, jamais committé) ; au plus 2 `@Ignore` motivés ; `:core:test` complet vert.

## Cas limites
`restart()` change le port : le `TransferClient` doit ré-résoudre la base (comme `UploadService.kt:102-104` sur changement d'IP) ; si le cœur ne le fait pas, le test le documente (`@Ignore` + REGRESSIONS, parcours P-18). Débit : avec `sleep` injecté, les « 30 % » se lisent dans `samples`, pas au chronomètre.

## À ne pas faire
Aucune modification de `C/xfer/**` ni de `C/tv/**` ; aucun fichier binaire dans le dépôt ; aucun test dépendant de la vitesse de la machine.

## Rapport
`STATUT`, table J-xx → résultat, mesures (durée, octets), régressions ouvertes avec fichier:ligne, besoins pour le harnais.
