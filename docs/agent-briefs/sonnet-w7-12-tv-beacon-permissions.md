# w7-12 — CastBridge-TV : `TvBeacon` toujours allumé (canaux `…0005` et `…0006`, mDNS v2), `TvPermissions` sans boucle, démarrage/redémarrage

> **Amendement (Fable, 2026-10-03, DESIGN-W18)** : `TvBeacon` publie **aussi** le service DNS-SD **P2P** (`WdDnsSd`, w18-07 : `_castbridge._tcp` avec `id`, nom, version ; jamais MAC ni mot de passe) quand le groupe Wi-Fi Direct persistant est allumé, et `TvPermissions` demande `NEARBY_WIFI_DEVICES` (33+) avec le Bluetooth, une fois. La règle « allumé à l'écran » du groupe (D-W18-3) appartient à `TvService`/`WifiDirectGroup` (w18-07), pas au beacon. Lire `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 3.1, § 5.3 ; le reste du cahier est inchangé.

**Vague 7b · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (après 7a fusionnée).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 2 (problèmes 1, 9), § 4.2, § 5.4, § 8.3. Branche `claude/sonnet-w7-12`. Rapport : `docs/agent-reports/sonnet-w7-12.md`.

## Objectif
(1) `R/TvBeacon.kt` : objet de processus qui ouvre le canal propriétaire `…0005` (existant `OwnerBtHost`) et le canal de synchronisation `…0006` (serveur RFCOMM sécurisé ; le traitement des liens est délégué à `SyncHost` de w7-13 par `TvBeacon.attachSync(handler)`), publie mDNS v2 (`MdnsTxt`, w7-09) dès qu'un réseau existe, **TV verrouillée ou non**, démarré par `TvApp.onCreate`, `BootReceiver` et un `JobScheduler` TV de 15 min (`ensure()`), sans service au premier plan ; (2) `R/TvPermissions.kt` : une demande par processus de `BLUETOOTH_CONNECT`/`ADVERTISE` (31+) et `POST_NOTIFICATIONS` (33+) avec écran d'explication D-pad ; `ACTION_REQUEST_DISCOVERABLE` **seulement** selon `PairingWindowPolicy` (w7-06) ; (3) `TvService` ne démarre plus `OwnerBtHost` et appelle `TvBeacon.ensure()` ; `register()` mDNS déplacé dans le beacon ; (4) correction des boucles de `ActivationActivity`/`PairActivity`/`PlayerActivity`.

## Pourquoi (preuves)
- `R/TvService.kt:182,195,411` (`startOwnerChannel` lié au service), `:960-979` (mDNS v1), `:1037-1042` (`BootReceiver`, `autostart`), `R/OwnerBtHost.kt:38-57` ; `R/ActivationActivity.kt:178-192` (permissions à chaque `onResume`, `makeVisible` 300 s) ; `R/PlayerActivity.kt:667-679` (une fois par bind) ; `R/PairActivity.kt:153-162,187-196,239` (fenêtre fermée à 8 s puis rouverte avec popup).
- Manifeste TV `:22` (`BLUETOOTH_ADVERTISE`), `:73-86` (service `connectedDevice`, `BootReceiver` exporté).
- `C/trust/Storm.kt`, `C/link/PairingWindowPolicy.kt` (w7-06), `C/link/MdnsTxt.kt` (w7-09), `C/link/LinkJournal.kt` (w7-08).

## Fichiers possédés
Nouveaux : `R/TvBeacon.kt`, `R/TvPermissions.kt`, `R/BeaconJob.kt`, `R/LinkJournalTv.kt` (instance + `TextStore` `SafeFile` dans `files/link/journal.txt`). Modifiés : `R/TvApp.kt`, `R/TvService.kt` (retrait de `startOwnerChannel`/`register`, appel `TvBeacon.ensure()`, exposition `ownerStatus()` via le beacon), `R/ActivationActivity.kt`, `R/PlayerActivity.kt` (permissions + entrée de menu « Connexion : diagnostic » qui ouvre `LinkDiagActivity`, classe créée par w7-15), `R/OwnerBtHost.kt` (démarré par le beacon, `knownKids`/`tvVersion`/`onKnock` de w7-07), `android/receiver/src/main/AndroidManifest.xml` (déclare `BeaconJob`, `LinkDiagActivity`). **Hors zone** : `R/PairActivity.kt` (w7-14), `R/BtServer.kt` (w7-15), `R/ActivationCenter.kt`, `R/LotsHub.kt`, `R/RentalHub.kt`, `R/ParentalHub.kt` (w7-13).

## Signatures à respecter (contrat pour w7-13, w7-14, w7-15)
```kotlin
object TvBeacon { fun ensure(ctx: Context); fun attachSync(h: SyncLinkHandler?); fun state(): BeaconState /* owner: String, sync: String, mdns: String, started: Long */
    fun publishMdns(ctx: Context, caps: Int); fun onKnock(name: String, pub: ByteArray?) /* relayé à PairActivity via un StateFlow */ ; val knocks: StateFlow<Knock?> }
fun interface SyncLinkHandler { fun serve(peer: String, peerName: String?, input: InputStream, output: OutputStream) }
object TvPermissions { fun needed(ctx: Context): List<String>; fun requestOnce(activity: Activity, why: String, onDone: (granted: Boolean) -> Unit); fun askVisibleOnce(activity: Activity, seconds: Int); fun reset() /* tests */ }
```

## Étapes
1. `TvBeacon.ensure` : idempotent ; ouvre `…0005` (réutilise `OwnerBtHost`) et `…0006` (`listenUsingRfcommWithServiceRecord("CastBridge Sync", SYNC_SERVICE_UUID)`, 4 liens, fil démon) quand `BLUETOOTH_CONNECT` est accordée ; sans permission : état « permission manquante », et `TvPermissions` est sollicité par l'activité visible ; réécoute automatique sur `BluetoothAdapter.ACTION_STATE_CHANGED` (récepteur dynamique) ; mDNS v2 via `NsdManager` avec `MdnsTxt.encode(id = Identity.fingerprint(installPub).take(8), proto = 1, caps, port = 8765, version)` où `installPub` vient de `KeystoreWrapper`/`InstallKey` si présents, sinon de `files/link/install_x25519` (créé avec `StaticKey.generate`, `SafeFile`, journal « protection logicielle ») ; `caps` sans `CAP_SYNC` tant que `attachSync(null)`.
2. `BootReceiver` (dans `TvService.kt:1037`) : appelle `TvBeacon.ensure(ctx)` **avant** `TvService.start` ; `BeaconJob` périodique 15 min `setPersisted` ; `TvApp.onCreate` : `ensure`.
3. `TvPermissions.requestOnce` : mémoire de processus (`asked`) + détection « ne plus demander » ⇒ écran d'explication avec [Autoriser] [Plus tard] (focus sur Plus tard), texte de `LinkTexts` ; `ActivationActivity.onResume` et `PlayerActivity.requestRuntimePermissions` passent par là (plus de redemande) ; `askVisibleOnce` : une fois par processus (`PairingWindowPolicy.onHome(trustedCount, visibleAskedThisProcess)`).
4. `TvService` : retirer `startOwnerChannel` (délègue), `register()`/`unregister` mDNS déplacés ; `ownerStatus()` lit le beacon ; aucune autre modification de comportement.
5. Journal : `LinkJournalTv` ; événements `ROUTE_UP/DOWN` (écoutes ouvertes/fermées), `PERM`, `PAIR`.
6. Tests : JVM pour la logique extraite (état du beacon, politique de demande) ; compilation `:receiver` ; essai émulateur TV : au démarrage sans `TvService`, `sdptool`/téléphone voit `…0005` et `…0006` ; refuser la permission deux fois ⇒ plus de dialogue, écran d'explication affiché ; `adb -s <tv> reboot` ⇒ beacon up ≤ 20 s après l'accueil.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin && gradle --offline :core:test --tests 'castbridge.core.link.*'
grep -n 'startOwnerChannel\|registerService' android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt | wc -l   # 0
grep -n 'requestPermissions(' android/receiver/src/main/kotlin/castbridge/receiver/ActivationActivity.kt android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt | wc -l   # 0 (tout passe par TvPermissions)
```
Observable (émulateur TV + TV de référence) : APK verrouillé fraîchement installé, `TvService` jamais démarré ⇒ le téléphone (`TvBluetooth.pairedTvs`) voit la TV « sure » ; journal `files/link/journal.txt` sans adresse complète ; aucune boucle de dialogue sur refus.

## Cas limites
TV sans adaptateur Bluetooth (box) ⇒ beacon = mDNS seulement, état « pas de Bluetooth sur cette TV » ; Bluetooth éteint ⇒ réécoute à l'allumage ; `MY_PACKAGE_REPLACED` ⇒ `ensure` ; porte de classe d'appareil (protect-02) « PAS_TV » ⇒ seul `…0005` ouvert.

## À ne pas faire
Pas de service au premier plan pour le beacon ; pas de `ACTION_REQUEST_DISCOVERABLE` hors `askVisibleOnce` ; ne pas modifier `PairActivity.kt` (w7-14) ni `BtServer.kt` (w7-15).

## Rapport
`STATUT`, état du beacon par situation (verrouillée / permission refusée / BT éteint / box), captures, ce qui n'a pas pu être essayé sur la vraie TV.
