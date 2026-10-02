# w15-17 — Cycle de vie de CastBridge-TV : démarrage sûr, Bluetooth relancé, permissions sans boucle, wake lock pendant les sessions, E/S hors fil principal, `restartApp` propre, horloge unique
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (`TvService` : boot, premier plan, verrous) · statut : PRÊT
> **Groupe : W15-S2-a** (vague W15, tranche S2, risqué ; `R/TvService.kt` : un seul cahier à la fois, après w15-01) · prérequis : w15-01 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Background*' --tests 'castbridge.core.tv.TvLifecycleTest' --tests 'castbridge.core.owner.*Clock*' --tests '*Trust*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 2 j) · audit Opus : oui

**Vague 15 S2 (TV + cœur) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-17`. Rapport : `docs/agent-reports/sonnet-w15-17.md`. Règle : test rouge d'abord ; les décisions (faut-il redemander une permission ? faut-il tenir le wake lock ? que faire si `startForeground` échoue ?) sont des fonctions pures de `C/tv/Background.kt`/`TvLifecycle`, testées ; l'activité et le service les appliquent.

## Défauts traités (preuves `PLAN-STABILISATION` § 2.3-2.4)
T-07 (`R/TvService.kt:142,153-159` : `startForeground` avalé ⇒ mort à 10 s ; `startCore` sur le fil principal au boot) · T-09 (`:569-583`, `ReceiverServer.kt:114` : wake lock relâché entre deux blocs) · T-10 (`R/HomeScreen.kt:124,130` E/S UI ; reste après w15-01 : autres lectures) · T-11 reliquat · T-14 (`:951-958,983-996` `restartApp` : ticks doublés, `multicastLock`) · T-15 (`Storage.kt:26`, `ReceiverServer.kt:108` : pool 8 threads, keep-alive) · T-19 (`R/OwnerBtHost.kt:20,45-49` sans `soTimeout`) · L-05 (`R/BtServer.kt:75,90-98`, `R/OwnerBtHost.kt:37,47,55`, `R/BtGatewayHost.kt:47` : Bluetooth jamais relancé) · L-06 (`R/ActivationActivity.kt:178-187`, `R/PairActivity.kt:152,175-179` : permissions à chaque `onResume`) · L-11 (`TvService.kt:196,216` : TV verrouillée sans HELLO) · L-17 (`C/owner/Keys.kt:149-165`, `R/ActivationCenter.kt:28`, `R/RentalHub.kt:41`, `TrustRegistry.kt:37,120` : cliquet, deux `TvClock`, jetons purgés au recalage NTP) · B-11 (`ReceiverServer.kt:231` `resyncFiling` dans le constructeur) · T-08 (`TvService.kt:216`, `BtProtocol.kt:203-211,229` : Bluetooth ignore la politique de stockage ; `delete` avant rename).

## Fichiers possédés
`R/TvService.kt`, `R/TvApp.kt`, `R/BtServer.kt`, `R/OwnerBtHost.kt`, `R/BtGatewayHost.kt`, `R/ActivationActivity.kt` (zone permissions), `R/PairActivity.kt` (zone permissions et `onStop`), `R/ActivationCenter.kt` (zone `TvClock`), `R/RentalHub.kt` (zone `TvClock`), `R/AndroidManifest.xml` (récepteur `ACTION_STATE_CHANGED`), `C/tv/Background.kt` (fonctions pures : `PermissionPolicy`, `WakePolicy`, `ForegroundPolicy`), nouveau `CT/tv/TvLifecycleTest.kt`, `C/tv/Storage.kt` (zone pool), `C/tv/ReceiverServer.kt` (**zones** `init` différé `:231`, `maxHttpThreads`), `C/tv/BtProtocol.kt` (zone réception fichier `:191-233` : `StoragePolicy.plan` + `fileReceived`), `C/owner/Keys.kt` (`TvClock` : borne et confirmation BEHIND), `C/trust/TrustRegistry.kt` (zone `purge` : tolérance ± 1 j au recalage), `CT/BackgroundTest.kt`, `CT/owner/*Clock*`. **Hors zone** : `HomeScreen`/`PlayerActivity` (w15-01, w15-13), `LinkDriver`, `TransferHost`, `S/**`.

## Étapes (test rouge, correctif, vert)
1. `TvLifecycleTest` : `PermissionPolicy.shouldAsk(alreadyAskedThisProcess, denied, userAction)` (jamais deux fois par processus sans action) ; `WakePolicy.hold(activeRequests, activeSessions, playing)` ; `ForegroundPolicy.onStartForegroundFailed(reason)` ⇒ `StopSelf` ; `restartApp` ⇒ ensemble de ticks vide après `stopCore` (compteur).
2. `TvService` : `startCore` sur `bg` (le `startForeground` reste immédiat dans `onCreate`) ; échec ⇒ `stopSelf` + statut persistant lu par l'accueil ; `stopCore` retire **tous** les ticks et `multicastLock` ; `transferTick` tient le wake lock tant que `transfers.active() > 0` ; récepteur `BluetoothAdapter.ACTION_STATE_CHANGED` ⇒ `onPermissionsReady()` ; `BtServer`/`OwnerBtHost`/`BtGatewayHost` ré-écoutent après `accept()` en erreur (5 essais, 2 s, modèle `BtTunnelBridge.kt:73-84`) ; `OwnerBtHost` `soTimeout 20 s` ; TV verrouillée : `BtServer` démarre en mode « HELLO seulement » (fichiers refusés `ERR_LOCKED`).
3. Activités : `PermissionPolicy` ; `PairActivity.onStop` ⇒ fenêtre fermée en `TIMEOUT` (w15-11 fournit `PairingSession.close(asTimeout = true)`).
4. `ReceiverServer.init` : `cleanOrphans`/`recoverMove`/`resyncFiling` lancés sur `bg` après construction, routes répondant 503 « démarrage… » pendant ≤ 30 s ; pool : `maxHttpThreads = max(8, maxStreams + 4)`, `Connection: close` sur les réponses `chunk`.
5. `BtProtocol` réception : volume par `StoragePolicy.plan`, règle 1 Go, `fileReceived` (rangement), rename sans `delete` préalable.
6. `TvClock` : un seul `TvClock` partagé (`TvApp`), saut avant non confirmé borné à 7 j (au-delà : `AHEAD` existant), « l'heure est juste » aussi pour BEHIND (lève la suspension, n'allonge aucun droit) ; `TrustRegistry.purge` tolère ± 1 j (test : recalage NTP de −2 h ne purge pas).
7. Vert : porte ; `:receiver:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; ≥ 10 tests nouveaux rouges puis verts ; `grep -n "requestPermissions" R/ActivationActivity.kt R/PairActivity.kt` : chaque appel gardé par `PermissionPolicy` ; `grep -c "TvClock(" android/receiver/src/main` = 1 ; `grep -n "ACTION_STATE_CHANGED" R/` non vide.

## À ne pas faire
Pas de `WorkManager` ni de nouvelle dépendance ; ne pas changer l'ordre « verrouillée ⇒ pas de HTTP » (le HELLO seul est ajouté) ; ne pas toucher `HomeScreen`, `PlayerActivity`, `LinkDriver` ; pas de texte W13.

## Rapport
`STATUT`, table défaut ⇒ test, liste des ticks avant/après `restartApp`, à valider sur la vraie TV GaiaOS : boot (liste H point 2), Bluetooth coupé/rallumé, permissions (point 12).
