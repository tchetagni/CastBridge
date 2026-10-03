# w7-16 — CastBridge (téléphone) : `LinkRuntime` (boucle, réveils, JobScheduler, règles FGS, association Companion Device Manager), façade `TvLinkManager`

> **Amendement (Fable, 2026-10-03, DESIGN-W18)** : `LinkRuntime` **héberge** `WdRuntime` (w18-08) et lui transmet ses déclencheurs (premier plan, action, `ACL_CONNECTED`, présence CDM) selon `C/link/WdTriggers` (w18-02) ; **aucune** jonction Wi-Fi Direct en arrière-plan (le job 15 min ne fait qu'une étape Bluetooth). La route de contrôle `Direct` n'est prise que si `WdRuntime.isUp(tv)` (`LinkPlanner2`). Le bouton « Wi-Fi Direct » de la fiche (branche `claude/wd-manual-button`) reste un déclencheur explicite. Lire `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 3.2, § 5.3 ; le reste du cahier est inchangé.

**Vague 7c · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (après 7a et 7b fusionnées).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 5.1-5.3, § 2 (problème 3, 8), D-W7-6. Branche `claude/sonnet-w7-16`. Rapport : `docs/agent-reports/sonnet-w7-16.md`.

## Objectif
(1) `S/link/LinkRuntime.kt` : un `LinkManager` (w7-01) par TV enregistrée, **un actif**, boucle unique (dormir `nextInMs` ou réveil), triggers existants + mDNS v2 + CDM, exécution des `DiscoverAction` (w7-09) par `S/link/DiscoveryAndroid.kt` (sonde IP, HELLO BT, NSD v2 de w7-17, Wi-Fi Direct de w7-21), `RouteTableImpl` nourri par les sondes, `LinkJournal` persisté (`files/link/journal.txt`) ; (2) `TvLinkManager` (`S/TvLink.kt`) devient une **façade** : `state`, `credentialFor`, `credentialForBase`, `poke`, `retryNow`, `pair`, `forget`, `makeDefault`, `requestReassociate`, `diagnose`, `recoverKnownTv` conservés **à l'identique** pour les écrans existants ; nouveau `snapshot: StateFlow<LinkSnapshot>` ; (3) permissions harmonisées : `recoverKnownTv` exige **CONNECT seulement** ; (4) `S/link/CdmAssociation.kt` : association CDM après une liaison réussie (une fois, explication `LinkTexts`), `startObservingDevicePresence` (31+), réception `CompanionDeviceService` (31+) ⇒ `Trigger.ACL_CONNECTED` ; (5) `LinkJobService` : inchangé (15 min) + compteur de jobs manqués (`JOB_MISSED` si > 30 min de retard) ; (6) règles FGS : aucun nouveau FGS ; `BtSshGatewayService.ensureApi` appelé aussi en **arrière-plan** uniquement sur `ACL_CONNECTED` (exemption) ou si CDM associé.

## Pourquoi (preuves)
- `S/TvLink.kt:97-305` (`TvLinkManager` : `init :113`, `onSession :131`, `recoverKnownTv :153-173` gardée par `TvBluetooth.permitted :158`, `loop :251-260`, `registerTriggers :264-289`, `pair :297-304`) ; `S/LinkAndroid.kt:49-103` (`AndroidLinkEnv`), `:176-224` (`LinkJobService` 7101/7102, `LinkWakeReceiver`) ; `S/TvHub.kt:79-80` (`hasBtPermission` = CONNECT) vs `OL/TvBluetooth.kt:31-37` (SCAN + localisation) ; `S/PhoneConnect.kt:40-41,53-55` (init, premier plan).
- Manifeste téléphone `:133-141` (job + récepteur), `:144-160` (FGS types) ; aucun CDM/WorkManager/BLE dans l'app (vérifié).
- `C/link/{LinkManager,RouteTableImpl,DiscoveryPlanner,LinkJournal,Identity}.kt` (7a).

## Fichiers possédés
Nouveaux : `S/link/LinkRuntime.kt`, `S/link/DiscoveryAndroid.kt`, `S/link/CdmAssociation.kt`, `S/link/LinkCompanionService.kt` (31+), `S/link/PhoneKeyStore.kt` (`files/link/phone_x25519`, `SafeFile`, exclu des sauvegardes), `S/link/LinkJournalPhone.kt`. Modifiés : `S/TvLink.kt` (façade), `S/LinkAndroid.kt`, `S/PhoneConnect.kt` (`LinkRuntime.init`), `android/sender/src/main/AndroidManifest.xml` (`LinkCompanionService`, permissions `REQUEST_COMPANION_RUN_IN_BACKGROUND`, `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`, `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`), `android/sender/src/main/res/xml/backup_rules.xml` + `data_extraction_rules.xml` (exclure `files/link/`). **Hors zone** : `S/TvPairScreen.kt`, `S/TvHome.kt`, `S/MainActivity.kt` (w7-19), `S/TransferQueue*.kt`, `S/UploadService.kt`, `S/BtUploadService.kt` (w7-21), `S/link/SyncClient.kt` (w7-18), `S/link/NsdDiscoveryV2.kt` (w7-17), `OL/**` (w7-22).

## Signatures à respecter (contrat pour w7-17, w7-18, w7-19, w7-20, w7-21)
```kotlin
object LinkRuntime { fun init(ctx: Context); val snapshot: StateFlow<LinkSnapshot>; fun active(): LinkManager?; fun forTv(address: String): LinkManager?
    fun wake(t: Trigger); fun setForeground(on: Boolean); fun routeTable(): RouteTable; fun journal(): LinkJournal; fun phoneKey(): StaticKey
    fun onCandidates(c: List<Candidate>)   // nourri par w7-17 (mDNS v2, QR, toc)
    fun onSyncRoute(route: LinkPlanner.Route, alive: Boolean) /* w7-18 */ ; fun observations(): Observations /* pour SelfTest, w7-19/20 */ }
object CdmAssociation { fun offerOnce(activity: Activity, tv: SavedTv, onDone: (Boolean) -> Unit); fun associated(ctx: Context, address: String): Boolean }
```

## Étapes
1. `LinkRuntime.init` depuis `CastBridgeApp.onCreate` ; migration : `SavedTvs` existant lu tel quel ; clé du téléphone créée au premier besoin.
2. Boucle : `snapshot = active.step(trigger)` → publie → exécute les `DiscoverAction` en parallèle bornée (2 fils IO) → `routeTable.probe` → `onRouteProbe` ; en arrière-plan, **une** étape par réveil (job/récepteur) comme aujourd'hui.
3. `TvLinkManager` : chaque fonction délègue ; `state` (`LinkUi`) dérivé de `snapshot` (même mapping que `publish` `S/TvLink.kt:240-248`) ; `recoverKnownTv` : condition `hasBtPermission(ctx)` ; test de source : tous les appels existants compilent sans changement (`grep -rn 'TvLinkManager\.' S/ | wc -l` identique avant/après).
4. CDM : `offerOnce` appelée par w7-19 à la fin de `PairFlow` ; `AssociationRequest` avec `BluetoothDeviceFilter` sur l'adresse ; API 33+ : `AssociationInfo`, `CompanionDeviceManager.startObservingDevicePresence(address)` ; `LinkCompanionService.onDeviceAppeared` ⇒ `wake(ACL_CONNECTED)` ; refus ⇒ `cdm=no` mémorisé, jamais redemandé (bouton dans « Connexion », w7-20).
5. Jobs manqués : comparer `now - lastRun` à 15 min + 30 min ⇒ `JOB_MISSED` dans le journal (nourrit `SelfTest.OEM_KILL`).
6. Tests : JVM pour la façade (mapping `snapshot → LinkUi`) avec `LinkManager` factice ; `:sender` compile ; appareil : app tuée (`am force-stop`) puis `ACL_CONNECTED` ⇒ étape exécutée ; CDM refusé ⇒ aucun crash, job seul ; CDM accepté (Android 12+) ⇒ `onDeviceAppeared` reçu TV allumée.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin && gradle --offline :core:test --tests 'castbridge.core.link.*'
grep -rn 'TvBluetooth.permitted' android/sender/src/main/kotlin/castbridge/sender/TvLink.kt | wc -l   # 0
grep -c 'foregroundServiceType' android/sender/src/main/AndroidManifest.xml   # inchangé (aucun FGS ajouté)
python3 tools/tests/test_backup_rules.py   # files/link exclu
```
Observable (S21+ + TV de référence) : TV éteinte/rallumée, téléphone écran éteint ⇒ journal `ROUTE_UP` ≤ 60 s après l'accueil TV ; changement d'IP de la TV ⇒ `ROUTE_UP lan` ≤ 20 s sans écran.

## Cas limites
Android 8-9 : pas de `startObservingDevicePresence` ⇒ association seule (exemption Doze) ; plusieurs TV : une association par TV ; adaptateur BT absent ⇒ `LinkRuntime` tourne en LAN seul ; `files/link/` corrompu ⇒ clé régénérée (⇒ ré-adoption) + journal.

## À ne pas faire
Pas de WorkManager ; pas de service au premier plan permanent ; pas de `startDiscovery` hors « Ajouter ma TV » ; pas de texte hors `LinkTexts`/`LinkText` ; ne pas casser l'API publique de `TvLinkManager`.

## Rapport
`STATUT`, liste des appels `TvLinkManager.*` existants (inchangés), comportement CDM par version Android testée, signatures finales.
