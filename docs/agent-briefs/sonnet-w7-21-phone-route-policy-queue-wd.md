# w7-21 — CastBridge (téléphone) : politique de voie pour les transferts (LAN > Wi-Fi Direct > Bluetooth), Wi-Fi Direct automatique en isolation, file d'envoi persistée, ETA, carte de progression nourrie par `xfer`

> **Amendement (Fable, 2026-10-03, DESIGN-W18)** : les points (1) et (2) de ce cahier sont **remplacés** : la voie est choisie par `C/link/WdPolicy.route(BULK)` (w18-02, qui enveloppe `BulkRoute` de la branche R-14) et la jonction par `WdRuntime` (w18-08 : identifiants persistants ⇒ jonction directe `WifiP2pManager.connect` sur 33+ ; `WifiNetworkSpecifier` **seulement** sur Android 10-12, approbation mémorisée) ; `WifiDirectAuto`/`BtUploadService.kt:140-163` ne sont plus à extraire. Le Wi-Fi Direct est tenté pour **tout** envoi sans LAN (plus de seuil 5 Mo quand les identifiants sont connus), pas seulement en isolation. La file persistée (R-09, déjà faite), l'ETA et la carte nourrie par `xfer` (points 3-6) restent à exécuter tels quels ; le dialogue « > 50 Mo par Bluetooth » devient `C/link/BtPlan` (w18-13) quand le Bluetooth est la seule voie. Lire `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 5.1, § 9.

**Vague 7c · Effort M (≈ 2,5 j) · Modèle : sonnet · Statut PRÊT (après 7a/7b ; en parallèle de w7-16/18 : contrats `LinkRuntime.routeTable()`, `DomainStores.xfer`).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 5.2, § 4.3, § 2 (problème 7). Branche `claude/sonnet-w7-21`. Rapport : `docs/agent-reports/sonnet-w7-21.md`.

## Objectif
(1) `TransferQueue.runOne` choisit la voie par `RoutePolicy.forUse(BULK, routeTable, bytes, isolation)` au lieu de « BT si `base == null` » ; (2) `WifiDirectAuto` : quand `Isolation.ISOLATED` (ou `RepairAction.USE_WIFI_DIRECT`), demande le groupe à la TV (CBTN `WANT_WIFI_DIRECT`, existant) et le rejoint (`WifiNetworkSpecifier`, code existant de `BtUploadService.kt:140-163` extrait dans `S/link/WifiDirectAuto.kt`), Android ≥ 10, `NEARBY_WIFI_DEVICES` 33+ via `PermissionFlow` (w7-19) ; (3) > 50 Mo sans voie Wi-Fi ⇒ dialogue « 80 Mo par Bluetooth ≈ 8 min : envoyer quand même ? » (texte `LinkTexts`, ETA `RoutePolicy.etaMs`) sauf préférence « toujours envoyer » ; (4) file **persistée** (`TransferQueueModel.encode/decode` w7-10, `files/link/queue.txt`, `SafeFile`), rechargée au démarrage de `TransferQueueService`, URI illisible ⇒ `FAILED` avec texte ; (5) la carte de progression de `TvHome` lit **aussi** `DomainStores.xfer` (grand livre TV : progression vue du côté TV, « interrompu, reprise possible ») ; (6) étiquettes de voie corrigées (`RoutePolicy.label`) dans `BtUploadService.route` et `UploadService`.

## Pourquoi (preuves)
- `S/TransferQueue.kt:80-118` (`runOne` : attend `LinkUi.Connected` 60 s, `base == null` ⇒ BT), `:27` (mémoire) ; `S/BtUploadService.kt:59-117` (négociation CBTN, `LinkPlanner.plan`, Lan → Direct → Bluetooth), `:140-163` (WD client), `:92` (`BluetoothTunnel` exclu) ; `S/UploadService.kt:102-104` (hôte), `:153-157,181-210` (`FastTransfer`/repli) ; `S/TvHome.kt:185-219` (carte combinée).
- `C/link/{RoutePolicy,RouteTableImpl,DiscoveryPlanner}.kt` (w7-09), `C/tv/TransferQueue.kt` (w7-10), `C/link/TransferLedger.kt` (lecture par `xfer`).

## Fichiers possédés
Modifiés : `S/TransferQueue.kt`, `S/TransferQueueService.kt`, `S/UploadService.kt` (étiquettes, `credential` inchangé), `S/BtUploadService.kt` (extraction WD, étiquette), `S/FastTransfer.kt` (lecture de la voie), `S/WifiDirectScreen.kt` (utilise `WifiDirectAuto`). Nouveaux : `S/link/WifiDirectAuto.kt`, `S/link/BulkRoute.kt` (choix + dialogue ETA), `S/link/XferCard.kt` (composable de progression fusionnant file locale + `xfer` TV ; **appelé par w7-19** dans `TvHome` à la place du bloc `:185-219` — contrat), `S/link/QueueStore.kt`. **Hors zone** : `S/TvHome.kt` (w7-19), `S/TvLink.kt`, `S/link/{LinkRuntime,SyncClient}.kt`, `C/xfer/**`.

## Signatures à respecter
```kotlin
object BulkRoute { fun choose(ctx: Context, bytes: Long): LinkPlanner.Route?; fun confirmIfSlow(activity: Activity, bytes: Long, route: LinkPlanner.Route, onDecide: (Boolean) -> Unit) }
object WifiDirectAuto { fun start(ctx: Context, tv: SavedTv, onReady: (base: String?) -> Unit); fun stop(ctx: Context); val state: StateFlow<WdState> /* OFF, JOINING, UP(base), FAILED(reason) */ }
@Composable fun XferCard(tv: SavedTv)
```

## Étapes
1. `runOne` : `BulkRoute.choose` ; `null` ⇒ attente (jusqu'à 60 s, existant) puis repli CBT1 si BT vivant ; `Direct` ⇒ `WifiDirectAuto` puis `UploadService` sur `192.168.49.1` ; `BluetoothTunnel` ⇒ `UploadService` sur `127.0.0.1:18765` **seulement** si < 5 Mo (lots, images) ; `Bluetooth` ⇒ `BtUploadService` (CBT1).
2. `WifiDirectAuto` : `BtProtocol.negotiate(wantWifiDirect = true)` (existant) ⇒ `LinkInfo.wd*` ⇒ `WifiNetworkSpecifier` ⇒ `bindProcessToNetwork` **le temps de l'envoi** puis libération ; état exposé ; échec ⇒ `FAILED("Wi-Fi Direct non disponible sur ce téléphone/cette TV")` ⇒ `BulkRoute` repasse en BT ; `RouteTable.probe(Direct, alive)`.
3. Dialogue ETA : seuil 50 Mo, texte avec taille et durée arrondie ; préférence `castbridge_link.always_send_bt`.
4. Persistance : `QueueStore` ; `TransferQueueService.onCreate` recharge ; chaque changement ⇒ sauvegarde (≤ 1/s) ; `RUNNING` ⇒ `WAITING` au rechargement.
5. `XferCard` : fusion file locale (`TransferQueueModel`) + `DomainStores.xfer(tv)` (progression TV, états `INTERRUPTED/DONE`), vitesse et ETA, « reprise automatique » ; étiquettes par `RoutePolicy.label`.
6. Tests : logique pure dans `C/link/RoutePolicy` (déjà) + `CT/link/BulkRouteTest`? (le choix est pur : tester la fonction extraite `BulkRoute.decide(table, bytes, isolation, prefs)` placée dans `C/link/BulkDecision.kt` **nouveau**) ; `:sender` compile ; appareil : isolation activée sur la box ⇒ WD monté ≤ 15 s et envoi 20 Mo ≥ 2 Mo/s ; `am force-stop` pendant un envoi en file de 3 fichiers ⇒ au redémarrage les 2 restants reprennent.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin && gradle --offline :core:test --tests 'castbridge.core.link.*'
grep -n 'base == null' android/sender/src/main/kotlin/castbridge/sender/TransferQueue.kt | wc -l   # 0
grep -rn '"Wi-Fi"' android/sender/src/main/kotlin/castbridge/sender/BtUploadService.kt android/sender/src/main/kotlin/castbridge/sender/UploadService.kt | wc -l   # 0 (étiquettes par RoutePolicy.label)
```

## Cas limites
Android 8-9 (pas de `WifiNetworkSpecifier`) ⇒ WD jamais proposé, BT avec ETA ; TV sans WD (`wd` absent de `LinkInfo`) ⇒ idem ; envoi en cours quand la voie meurt ⇒ reprise existante (`ResumableUpload`/CBT1 `.part`) sur la nouvelle voie **sans** redémarrer de zéro (vérifier avec le `.part` : même nom, même taille).

## À ne pas faire
Ne pas toucher au moteur `xfer` ; pas de nouveau FGS ; pas de WD automatique sans isolation détectée (il perturbe le Wi-Fi de la TV : `LinkPlanner.mayStartWifiDirect`).

## Rapport
`STATUT`, débits mesurés par voie (émulateur = information seulement), comportement de reprise, signatures.
