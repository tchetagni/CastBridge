# w7-18 — CastBridge (téléphone) : `SyncClient` (CBSX + CBSY sur `…0006` et `/api/sync/*`), magasins de domaines, notifications, branchements lots / parental / preuve

**Vague 7c · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (après 7a/7b ; en parallèle de w7-16 : contrats `LinkRuntime`).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 6, § 7, § 11, § 2 (problème 2). Branche `claude/sonnet-w7-18`. Rapport : `docs/agent-reports/sonnet-w7-18.md`.

## Objectif
(1) `S/link/SyncClient.kt` : ouvre la session de synchro sur la route de contrôle (`…0006` par `AndroidBtTransport`-like sous `BtConnectLock` ; ou HTTP `hs1/hs2/hs3` + `frame` + long-poll `wait`), `Handshake(initiator = true, expectedPeerPub = épinglée)`, `SecureSession`, `SyncEngine(side = PHONE)` avec les 8 domaines ; `HELLO` ⇒ `pending()` ⇒ PULL/DELTA/ACK ; `NOTIFY` ⇒ PULL ; PING selon la phase ; bascule de route = nouvelle session, `seq` continus ; (2) `S/link/DomainStores.kt` : persistance par domaine (`files/link/<tv-id>/<dom>.txt`, `SafeFile`) + `DeltaLog` ; (3) branchements : `act` ⇒ `PhoneSync`/`ProofStore` (W6, **si présents** ; sinon `S/link/ActState.kt` minimal : état + libellé, et notification « <TV> activée en production ») ; `lots` ⇒ `LotsRuntime` (`DeliveryQueue.reconcile` immédiat) ; `par` ⇒ `ParentalInbox.sync` immédiat ; `xfer` ⇒ carte de progression (w7-21 lit le store) ; `shop` ⇒ `TvShopCache` W5 si présent ; `set`/`ico` ⇒ écran Connexion (w7-20) ; (4) `S/link/SyncNotifications.kt` : notifications silencieuses (canal « Liaison TV ») pour `act` (changement d'état), `xfer` (reprise/interruption), identité changée ; (5) TOFU : première session sécurisée ⇒ épingle `peerPub` ; `UNEXPECTED_STATIC` ⇒ `IDENTITY_CHANGED` (dialogue par w7-19).

## Pourquoi (preuves)
- `S/TvLink.kt:131-137` (`onSession` : livraison des lots une fois par connexion, passerelle au premier plan) ; `S/LotsRuntime.kt:135-143,257-260` (`requestDelivery`, `reconcile` après `GET /api/lots`) ; `S/ParentalInbox.kt:62-65,113` (tâche 15 min) ; `S/ActivateTvActivity.kt:56-75` (sondage `GET /api/activation` à remplacer par le domaine `act` : fichier à w7-22, qui lira `DomainStores.act`).
- `C/tunnel/LinkPool.kt:151-154` (`BtConnectLock`), `S/BtSshGateway.kt:93-120` (`dial` sécurisé, garde 20 s) : même façon d'ouvrir `…0006`.
- `C/link/{SecureSession,SyncFrames,SyncCodec,SyncEngine,domains/*}.kt` (7a), `C/owner/{PhoneSync,ProofCache}.kt` (W6, si fusionnés).

## Fichiers possédés
Nouveaux : `S/link/SyncClient.kt`, `S/link/SyncTransports.kt` (BT + HTTP), `S/link/DomainStores.kt`, `S/link/SyncNotifications.kt`, `S/link/ActState.kt`, `S/link/TvPinStore.kt` (clé épinglée par TV, `files/link/<id>/tv_pub`). Modifiés (crochets) : `S/LotsRuntime.kt` (`onLotsDelta → reconcile + requestDelivery`), `S/ParentalInbox.kt` (`onPendingIds → sync now`), `S/gate/ProofSync.kt` **si existe** (w6-16). **Hors zone** : `S/TvLink.kt`, `S/link/LinkRuntime.kt` (w7-16), `S/ActivateTvActivity.kt` (w7-22), écrans (w7-19/20), `S/TransferQueue*.kt` (w7-21).

## Signatures à respecter (contrat pour w7-19, w7-20, w7-21, w7-22)
```kotlin
object SyncClient { fun ensure(tv: SavedTv, route: LinkPlanner.Route); fun close(tv: SavedTv); fun state(tv: SavedTv): SyncState /* NONE, HANDSHAKE, LIVE(route, sinceMs), SIMPLE(reason) */
    fun pull(tv: SavedTv, dom: Dom); fun requestProof(tv: SavedTv, nonce: String): Deferred<String?> ; fun ageOf(tv: SavedTv, dom: Dom): Long? ; val events: SharedFlow<SyncEvent> /* ActChanged, LotsChanged, XferChanged, ParPending, IdentityChanged(old,new), Simple */ }
object DomainStores { fun act(tv: SavedTv): ActState?; fun lib(tv: SavedTv): List<Map<String,String>>; fun xfer(tv: SavedTv): List<Map<String,String>>; fun set(tv: SavedTv): Map<String,String>; fun ico(tv: SavedTv): List<Map<String,String>> }
```

## Étapes
1. Transports : BT = socket RFCOMM sécurisé `…0006` sous `BtConnectLock.of(address)`, lecture bloquante sur un fil `cb-sync` ; HTTP = `HttpURLConnection` (comme `HttpLite`), long-poll avec `timeout=25`, relance immédiate, arrêt en arrière-plan sauf `LINKED_*` depuis < 5 min.
2. Poignée de main : `expectedPeerPub = TvPinStore.get(tv)` ; `null` ⇒ TOFU **seulement** sur BT appairé ou QR (SAS connu) ; LAN sans épingle ⇒ `SyncState.SIMPLE("identité non confirmée")` + événement pour que w7-19 propose « Confirmer la TV » (affiche le SAS à comparer avec « À propos »).
3. Engine + stores + crochets ; `Dom.ACT` : `installId` ≠ mémorisé ⇒ `reset()` des stores de cette TV.
4. Ancienne TV (`caps` sans `CAP_SYNC` ou service absent) ⇒ `SIMPLE` : repli sur les lectures existantes (`GET /api/activation` 1×/liaison, `GET /api/lots` à la livraison, CBTP 15 min) **sans** message d'erreur ; libellé « synchro simple (TV à mettre à jour) » exposé pour w7-20.
5. Notifications : une par changement réel (digest différent), regroupées par TV, jamais de contenu parental.
6. Tests : JVM `SyncClientLogicTest` (logique de choix TOFU/SIMPLE extraite dans `C/link/SyncPolicy.kt` **nouveau**, pur) ; `:sender` compile ; appareil : poser une clé sur la TV ⇒ notification < 3 s ; couper le Wi-Fi ⇒ session BT reprend, `seq` continus (journal `SYNC_DELTA` sans snapshot).

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin && gradle --offline :core:test --tests 'castbridge.core.link.*'
grep -rn '/api/activation"' android/sender/src/main/kotlin/castbridge/sender/link/ | wc -l   # 0 (plus de sondage dans le client de synchro)
python3 tools/tests/test_backup_rules.py
```

## Cas limites
TV verrouillée (`act` seul) ⇒ `ActState.locked` ⇒ w7-22 affiche « sans clé » ; 2 TV ⇒ 2 sessions au plus (TV active + une passive sur LAN si peu coûteuse ? **non** : une seule session, TV active) ; trame invalide ⇒ fermer, journal, une reprise, puis `SIMPLE("liaison chiffrée impossible")`.

## À ne pas faire
Ne jamais envoyer de jeton/PIN dans `CBSY` ; pas d'écran ; ne pas modifier `LinkPool`/`Mux` ; pas de sondage périodique de `/api/activation` quand `LIVE`.

## Rapport
`STATUT`, latences mesurées (`act`, `lots`, `xfer`), comportement `SIMPLE` avec une TV ancienne, signatures.
