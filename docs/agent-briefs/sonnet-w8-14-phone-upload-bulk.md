# w8-14 — CastBridge (téléphone) : `UploadService` sur `BulkTransfer`, « instantané puis accéléré » (`LaneBringup`), voies Android (Wi-Fi, Bluetooth de vrac, lien USB), thermique et batterie

**Vague 8c · Effort L (≈ 3-4 j) · Modèle : sonnet · Statut PRÊT (après 8a, w8-10, w8-11).** Conception : § 3, 6.2, 6.3, 7.1, 8.4, 8.5. Branche `claude/sonnet-w8-14`. Rapport : `docs/agent-reports/sonnet-w8-14.md`.

## Objectif
Que l'envoi d'un fichier démarre en ≤ 3 s sur ce qui est disponible (Bluetooth), gagne les voies Wi-Fi / Wi-Fi Direct / USB au fur et à mesure qu'elles sont prêtes, survive à la perte de n'importe laquelle, et que la file du téléphone ne choisisse plus « Wi-Fi **ou** Bluetooth ».

## Pourquoi (preuves)
- `UploadService.runFast` : une seule `WifiLane`, jamais en progressif (`sender/UploadService.kt:153-156, 181-210`).
- `TransferQueue` : `if (viaBt) BtUploadService.start(…) else UploadService.start(…)` (`sender/TransferQueue.kt:52-53`).
- Liens connus : `TvLinkManager` (`sender/TvLink.kt:113-131, 192-206`), `BtSshGatewayService.apiBase` (`sender/BtSshGateway.kt:143-171`), `LinkPool`/`BtConnectLock` (`core/tunnel/LinkPool.kt:20, 151`), `AndroidBtTransport.connect` (`TvLink.kt:64-74`).
- Verrous Wi-Fi et de réveil existants (`UploadService.kt:278-293`) ; type de service `dataSync` (`UploadService.kt:82`).

## Fichiers possédés
`S/{UploadService,TransferQueue,TransferQueueService,BtUploadService,FastTransfer}.kt`, nouveaux `S/xfer/{LaneBringup,AndroidLinkSet,PowerGuards}.kt`. **Hors zone** : `S/TvLink.kt`, `S/gate/**` (W6/W7), `S/LotsRuntime.kt`/`MoveToTv.kt` (w8-15), `S/TvTransferScreen.kt`/`TvScreen.kt` (w8-16), `S/WifiDirectScreen.kt` (w8-17), `C/**`.

## Étapes
1. `AndroidLinkSet : LinkSet` (contrat w8-07 § 1) : `snapshot()` à partir de `TvLinkManager` (adresse LAN connue, jeton vivant, lien Bluetooth, groupe Wi-Fi Direct joint, interface `usb0` du téléphone **ou** `caps.usbIp` de la TV), `onChange` branché sur `TvLinkManager.state`, `ConnectivityManager` (réseau par défaut **et** réseaux non par défaut : `NetworkRequest` avec `TRANSPORT_USB`/`TRANSPORT_WIFI` sans `NET_CAPABILITY_INTERNET`), diffusions ACL ; `requestWifiDirect()` = CBTN existant (`BtProtocol.negotiate`, via `TvLinkManager` ou `BtUploadService` : réutiliser, ne pas réécrire). **Si W7 a livré un `LinkSet`, l'utiliser et supprimer la version locale** (le dire).
2. `LaneFactory` Android : `wifi(base)` = `WifiLane` avec `HttpConn.tcp` **lié au bon `Network`** (`network.bindSocket(socket)` pour Wi-Fi Direct et USB, sinon le routage par défaut du téléphone enverrait vers Internet) ; `bt(address)` = `BulkBtLane` dont `connect` ouvre `createRfcommSocketToServiceRecord(BULK_SERVICE_UUID)` **sous `BtConnectLock`** et avec les délais de `LinkPool` (1,5 s, 3 s, 3 essais) ; `direct(base)` = `WifiDirectLane` sur le `Network` du groupe ; `usb(base)` = `WifiLane` sur le `Network` USB.
3. `LaneBringup` : séquence § 6.2 (t0 BT si lien vivant et `caps.bulkBt` ; t0 LAN si adresse ; t0+2 s sans voie IP → `requestWifiDirect()` ; USB si adresse) ; chaque étape = événement vers `LaneSet` ; respecte la garde 8a (jamais LAN + Direct ; BT en veille au-dessus de 2 Mo/s) via `wifiGhz` (`WifiInfo.frequency`).
4. `UploadService.runJob` : `runFast` → `BulkTransfer.send(…, verdict, AndroidLinkSet, factory, Options(progressive = job.progressive, encrypt = FastTransfer.encrypt(ctx), capBps = FastTransfer.cap(ctx)))` ; **le progressif passe par le moteur** quand `caps.version ≥ 2` (ordre 8a, `/stream` 8b), sinon comportement actuel ; `Result.Unsupported` → envoi classique (existant) ; `Result.Refused(msg, trial)` → `State.Failed(msg)` **et** `PhoneGateTexts.refusalFromTv` si w6-17 est là. Progression/notification/`notice` inchangées (`onState`).
5. `TransferQueue`/`TransferQueueService` : plus de `viaBt` ; un seul chemin `UploadService` (qui porte toutes les voies) ; `BtUploadService` (CBT1) **conservé** et utilisé seulement si `caps` est absent **et** qu'aucune adresse IP n'existe (TV ancienne, Bluetooth seul) : le dire dans un commentaire.
6. `PowerGuards` : `PowerManager.currentThermalStatus ≥ THERMAL_STATUS_SEVERE` (API 29+) → `KController.limit(2)` via `Options` ; batterie < 15 % non branchée (`BatteryManager`) → `LaneBringup` ne demande pas Wi-Fi Direct ; journal d'une ligne à chaque garde.
7. `FastTransfer` : préférences `fast` (existante), `encrypt` (défaut vrai), `cap` (0/2/5/10 Mo/s).
8. Essai sur matériel (téléphone + TV de référence) : S1 (Wi-Fi maison), S4 (Wi-Fi du téléphone coupé : premier bloc par Bluetooth ≤ 3 s, puis Wi-Fi Direct si w8-17 l'a activé, sinon Bluetooth seul), coupure du Wi-Fi 5 s en plein envoi (aucun « en attente », les blocs partent par Bluetooth) ; captures/journal masqué dans le rapport.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin   # compile
grep -n "viaBt" android/sender/src/main/kotlin/castbridge/sender/TransferQueue.kt | wc -l   # 0
grep -n "BulkTransfer.send" android/sender/src/main/kotlin/castbridge/sender/UploadService.kt | wc -l   # 1
grep -n "bindSocket" android/sender/src/main/kotlin/castbridge/sender/xfer/*.kt | wc -l   # ≥ 2
```
Observable : journal `CbxXfer` (w8-16, ou `Log.i(TAG)` en attendant) montre `lane up bt` puis `lane up wifi` ; `GET /api/transfer/state` sur la TV liste 2 voies ; TV v1 → une voie Wi-Fi, comportement d'aujourd'hui.

## Cas limites
Jeton renouvelé en plein envoi (relu à chaque requête, existant) ; TV redécouverte à une autre adresse (`resolve`, existant : la voie Wi-Fi est retirée puis rajoutée) ; `Network` USB qui disparaît (débranché) : voie retirée, aucun plantage ; Android 14 : `startForegroundService` depuis l'arrière-plan (déjà traité par w3-02) ; progressif avec `moov` en fin : message existant, pas de progressif.

## À ne pas faire
Ne pas réimplémenter la découverte, le HELLO, la jonction Wi-Fi Direct (réutiliser) ; ne pas contourner `SendGuard` (le `Verdict` vient de l'appelant, w6-17) ; ne pas enlever `BtUploadService` ; pas de WorkManager ; aucun jeton ni adresse Bluetooth dans le journal.

## Rapport
`STATUT`, `LinkSet` utilisé (W7 ou local), essais matériels (scénarios, durées, premier bloc), compilation, ce qui reste pour w8-16/w8-17.
