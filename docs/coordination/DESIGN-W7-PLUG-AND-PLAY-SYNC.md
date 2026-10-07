# Conception W7 — Plug and play et synchronisation native entre CastBridge (téléphone) et CastBridge-TV

> Document de conception (Fable, architecte, 2026-10-02). **Aucun code n'est modifié par ce document** ; l'exécution se fait par les cahiers `docs/agent-briefs/sonnet-w7-NN-*.md` (index : `SONNET-WAVE7-INDEX.md`), sur ordre explicite du coordinateur. Branche de référence : `integration/agents` (HEAD `3fcd2c9`). Tous les fichiers et lignes cités ont été vérifiés par lecture le 2026-10-02. Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`.
>
> **Demande du propriétaire (2026-10-02)** : « optimise le plug and play entre l'app phone et l'app TV pour une synchro native ». Objectif : une fois les deux applications installées, l'utilisateur ne fait (presque) rien : elles se trouvent, s'associent, restent liées et gardent **tout** synchronisé (activation/preuve, lots et locations, bibliothèque, transferts, rapports parentaux, boutique/jetons, télécommande, passerelle d'assistance), sans code, sans adresse IP, sans étape manuelle ; robuste sur TV modestes (32 bits, 1 Go) et téléphones Android 8-14, dans les conditions réelles du Cameroun (coupures de courant, DHCP qui change, TV souvent hors ligne, gestionnaires de batterie agressifs, isolation des clients Wi-Fi sur certains routeurs, redémarrages de TV).

## 0. En dix lignes

1. **Un seul cerveau par côté** : sur le téléphone un `LinkManager` (cœur pur, états `NO_TV / DISCOVERING / PAIRING / LINKED_BT / LINKED_LAN / LINKED_BOTH / DEGRADED / OFFLINE`) qui **enveloppe** la `LinkMachine`/`LinkDriver` existantes (hystérésis, politique d'essais, jeton) et y ajoute la couche **routes** et la couche **synchronisation** ; sur la TV un `LinkHost` (état par téléphone) et un **`TvBeacon`** toujours allumé (canal propriétaire `…0005` + nouveau canal de synchronisation `…0006`), démarré par `TvApp`/`BootReceiver` **même TV verrouillée**, indépendant de `TvService`.
2. **Découverte en couches, sans code** : SDP Bluetooth (existant) → mDNS `_castbridge._tcp` **v2** avec TXT (`id` haché, `proto`, `caps`, `port`) → adresses mémorisées re-vérifiées → **Wi-Fi Direct automatique** quand l'isolation des clients est détectée → QR sur l'écran de la TV en dernier recours. **Pas de BLE en v1** (aucune TV du parc ne l'annonce de façon fiable, aucune API côté 32 bits/GaiaOS vérifiée) : décision D-W7-1.
3. **Confiance par identité, plus par adresse** : chaque téléphone et chaque TV portent une **clé X25519 statique** (TV = `InstallKey` de W4, téléphone = nouvelle `PhoneKey`) ; le registre de confiance de la TV garde `(adresse Bluetooth, clé publique du téléphone)` ; le téléphone **épingle** la clé de la TV (TOFU sur lien sécurisé, comme `TvProof` W6). Les jetons 12 h existants restent le moyen d'accès HTTP ; **la perte du jeton n'exige plus de ré-association** : la clé le remplace par un nouveau HELLO. Réinstallation du téléphone ⇒ **ré-adoption** : code de comparaison à 4 chiffres (SAS tiré de la poignée de main) + **code parental/foyer** saisi sur le téléphone + un seul « Autoriser » à la télécommande.
4. **Fenêtre d'association** : s'ouvre **toute seule** à chaque démarrage de l'accueil tant qu'**aucun** téléphone n'est de confiance (première installation, après réinitialisation), 10 min, « Refuser » présélectionné ; sinon elle s'ouvre **sur un « toc » du téléphone** (frappe signée sur `…0005`/`/api/knock`) qui affiche « <téléphone> demande à s'associer » ; jamais ouverte en permanence pour des inconnus ; 3 refus = 10 min de blocage (existant).
5. **Protocole de synchronisation versionné (`CBSY`, v1)** : mêmes trames sur le canal RFCOMM `…0006` et sur HTTP `/api/sync/*` (long-poll) : `HELLO` (capacités + **empreintes par domaine**), `DIGEST`, `PULL`, `DELTA`, `ACK`, `NOTIFY`, `PING/PONG`, `BULK` (délégation au moteur de transfert existant). Huit **domaines** avec numéro de séquence et application idempotente : `act` (activation/preuve W6), `lots` (lots, locations), `lib` (index de bibliothèque), `xfer` (file et grand livre des transferts), `par` (rapports parentaux), `shop` (jetons, bons), `set` (réglages, profil), `ico` (icônes d'état). Le téléphone **apprend un changement de la TV en < 3 s** quand il est lié (NOTIFY), sinon au prochain contact.
6. **Canal chiffré de bout en bout `CBSX`** (poignée de main X25519 « XX » à 3 messages, HKDF-SHA256, AES-256-GCM, clés par session, secret éphémère donc **confidentialité persistante**), identique sur Bluetooth (par-dessus le chiffrement RFCOMM) et sur le Wi-Fi local (ce qui ferme la limite « jeton en clair sur le LAN » de `BT-PLUG-AND-PLAY.md:130`). Le HTTP de masse (upload, `/api/transfer`) **reste** tel quel en v1 (jeton), pour ne rien casser.
7. **Cycle de vie économe** : aucun service au premier plan permanent côté téléphone ; `JobScheduler` 15 min (existant) + diffusions système + **association Companion Device Manager (optionnelle, Android 8+)** pour survivre à Doze et aux tueurs de batterie, demandée **une fois** pendant l'association ; sur la TV, `TvBeacon` léger (deux sockets d'écoute, aucun fil actif) ; reprise après redémarrage de la TV, changement d'IP ou coupure de courant sans action de l'utilisateur.
8. **UX** : première liaison en **3 touches** (« Ajouter ma TV » → comparer le code Android → « Autoriser » à la télécommande), indicateur permanent de liaison, **un seul bouton « Réparer la connexion »** qui lance un diagnostic guidé (Bluetooth ? permission ? même réseau ? isolation ? TV en veille ? heure ?) et dit **une phrase** et **une action** ; chorégraphie des permissions Android 12-14 **une fois par processus**, plus jamais en boucle ; D-pad sur la TV.
9. **Observabilité** : `LinkJournal` (anneau de 500 événements, sans secret, exportable) sur les deux appareils, écran « Connexion » commun, auto-test, et **plan de mesure terrain** (temps de liaison, reconnexion, latence de synchro, batterie/h, histogramme des échecs) avec commandes `adb`/`curl` et seuils.
10. **Effort** : 25 cahiers en 4 sous-vagues, ≈ 57 agent·jours ; **7 décisions** prises ici avec recommandation ; **2 BLOQUÉ** (code foyer vs code parental ; mesures sur la vraie TV avant de figer les délais Bluetooth).

## 1. État des lieux vérifié (à ne pas refaire)

| Brique | Où (vérifié) | Ce qui existe | Manque / défaut |
|---|---|---|---|
| Machine d'états du lien (téléphone) | `C/trust/LinkMachine.kt:21-42` (états), `:128-147` (`reduce`), `:193-215` (`nextAttempt`) ; `C/trust/LinkDriver.kt:129-154` (`step`), `:169-197` (garde-vivant et repli de route) | hystérésis 2 confirmations/3 s, courbes 2→60 s / 1→15 min, jeton renouvelé à mi-vie, repli LAN→WD→BT, détection « TV réinitialisée » (`installId`), `Retry.Never` | pas d'état « découverte », pas de « lié par les deux », pas de synchro ; la route BT-tunnel n'est prise que si la passerelle tourne et au premier plan (`S/TvLink.kt:136`) |
| Boucle Android | `S/TvLink.kt:97-305` (`TvLinkManager`), `:251-260` (boucle), `:264-289` (diffusions), `S/LinkAndroid.kt:176-224` (`LinkJobService` 15 min persistant, `LinkWakeReceiver`) | une étape = `driver.step()`, réveils ACL/bond/adaptateur/écran/réseau | `recoverKnownTv` (`S/TvLink.kt:153-173`) gardée par `TvBluetooth.permitted` (SCAN + localisation < 31, `OL/TvBluetooth.kt:31-37`) alors que la boucle n'exige que CONNECT (`S/TvHub.kt:79-80`) : la reprise après réinstallation **ne tourne pas** sans SCAN |
| HELLO / confiance (TV) | `C/trust/HelloHandler.kt:36-54`, `C/trust/TrustRegistry.kt:35-150` (jetons 12 h hachés, `installId`), `C/trust/PairingSession.kt:21-117` (fenêtre 2 min, 1 téléphone, 3 refus = 10 min), `C/tv/BtProtocol.kt:44-70` (CBTH) | identité = adresse Bluetooth du socket appairé | aucune clé par téléphone : un téléphone réinstallé **avec la même adresse** est repris, mais une adresse qui change (téléphone neuf) = inconnu ; aucun chiffrement au-dessus du RFCOMM ni du HTTP LAN |
| Services RFCOMM de la TV | `…0001` fichiers/CBT*/CBTH/CBTP (`R/BtServer.kt:77`), `…0002` SSH **et** passerelle Internet (même UUID : `C/tv/BtProtocol.kt:74` vs `C/gateway/BtGateway.kt:46` ; **corrigé le 2026-10-07, R-28 : la passerelle est sur `…0007`, table `BtProtocol.SERVICES` ; `…0006` reste réservé à la synchronisation de ce document**), `…0003` API v1, `…0004` API v2 mux (`R/BtApiControl.kt:162-171`), `…0005` propriétaire (`R/OwnerBtHost.kt:42`) | tous démarrés par `TvService.onPermissionsReady` (`R/TvService.kt:409-418`) ; propriétaire aussi en TV verrouillée (`:195`) | **tout dépend de `TvService`** et de `BLUETOOTH_ADVERTISE` ; la TV n'annonce rien tant que l'activité n'a pas demandé la permission (`R/ActivationActivity.kt:180-187`, `R/PlayerActivity.kt:667-673`) |
| Découverte | mDNS `_castbridge._tcp.` TXT `role=receiver`, `v=0.2` (`R/TvService.kt:960-979`) ; `S/TvDiscovery.kt:27-99` (résolution série) ; SDP : préfixe d'UUID `7c5e3b9a-…-cb00000000` (`OL/TvBluetooth.kt:29`, `S/TvLink.kt:320-330`) | | TXT sans identité ni version de protocole ; aucune re-résolution après changement d'IP hors `restart()` ; `LinkInfo` ne liste que les IPv4 de site (`R/TvService.kt:278-281`) ; **pas de BLE, pas de CDM, pas de WorkManager** (vérifié par grep) |
| Wi-Fi Direct | `R/WifiDirectGroup.kt`, `R/TvService.kt:277-292` (groupe créé sur CBTN si `wd_enabled` ou pas de réseau), `S/BtUploadService.kt:140-163` (`WifiNetworkSpecifier`) | | jamais automatique en cas d'isolation des clients ; pas de détection de cette isolation |
| Tunnel API Bluetooth | `C/tunnel/Mux.kt:15-25` (trames `type u8 \| flux u16 \| long u16`), `C/tunnel/LinkPool.kt:20-154` (gap 1,5 s, 3 essais, PING 15 s, fermeture 45 s, `BtConnectLock`), `C/tunnel/TcpTunnel.kt:181-224` | un lien partagé par TV | démarré seulement par `ensureApi` au premier plan (`S/BtSshGateway.kt:171-175`, `S/TvLink.kt:136`) ; aucune notification TV→téléphone possible (le mux ne porte que des flux ouverts par le téléphone, `Mux.kt:123`) |
| Transfert | `C/xfer/*` (blocs 1-8 Mio, SHA-256, reprise `.cbx/<id>.state`, `WifiLane` K 1-8, `BluetoothLane` 256 Kio **non branchée**), `S/TransferQueue.kt:80-118` (attend 60 s `LinkUi.Connected`, choisit BT si `base == null`), `S/UploadService.kt`, `S/BtUploadService.kt:59-117` | | file **en mémoire seulement** (`S/TransferQueue.kt:27`) ; sur la TV l'état d'un transfert n'est qu'une **ligne de statut** `1-bt` écrasée par le suivant (`R/BtServer.kt:113-127`) : c'est le bug « reste à prêt à recevoir » |
| Lots / livraison | `C/lots/DeliveryQueue.kt:10-22,100-133,231-262` (PENDING→SENT→CONFIRMED, manifeste TV = vérité), `C/lots/LotPush.kt:19-99` (routes `/api/lots/*`, CBT1 lot + preuve) | idempotent, persistant, reprise | la confirmation d'un envoi Bluetooth attend un `GET /api/lots` ultérieur (pas de NOTIFY) |
| Parental | `C/parental/ParentalSync.kt:51-61` (CBTP), `:125-185` (`ReportInbox` idempotent par id), `S/ParentalInbox.kt:62-65,113` (tâche 15 min) | tirage par le téléphone, ack après stockage | Bluetooth seulement (W6 ajoute le Wi-Fi) ; 15 min de latence |
| Activation par Bluetooth | `C/owner/OwnerChannel.kt:21-40` (CBTO, DEVICE_INFO/ACTIVATION, RESULT `[ok]`+texte), `R/OwnerBtHost.kt:29-34` (`ActivationCenter.stage`), `R/ActivationCenter.kt:49-51` (`BuildConfig.TRUSTED_KEYS`), `C/owner/Activation.kt:143` (« Activation signée par une clé inconnue de cet appareil »), `C/owner/ActivationScreenState.kt:43-44` (« Refusée par la TV : … ») | | le résultat n'a **ni code de raison ni action** ; les clés de confiance sont **compilées** dans la TV : une clé de téléphone (kid `35662fecbf07dbdf`, `HANDOFF.md:26`) absente du build = refus sans issue |
| État d'activation vu du téléphone | `S/ActivateTvActivity.kt:56-75` (`GET /api/activation` toutes les 4 s, **LAN seulement**, écran ouvert seulement) | | « la TV est activée mais le téléphone ne le constate pas » : rien ne pousse l'état ; en Bluetooth seul, rien n'est lu |
| PIN de la TV | `R/TvPrefs.kt:11-15` (créé une fois, **jamais renouvelé**), `R/HomeScreen.kt:76,104,125` (« code 77•••• ») | | réinstallation = nouveau PIN ; le PIN n'est utile qu'aux appareils non de confiance |
| Permissions TV | `R/ActivationActivity.kt:178-192` (demande à **chaque** `onResume`), `R/PairActivity.kt:153-161,187-196,239` (fenêtre fermée 8 s après `onStop`, rouverte + popup « visible » à chaque retour) | | boucles de dialogues observées sur l'émulateur : causes identifiées |
| Horloge, preuve, porte | `C/owner/Keys.kt:81-155` (`TvClock`) ; W6 (`TvProof`, `PhoneGate`, `PhoneSync`, `InstallSigner`) et W4 (`InstallKey`, `X25519`, `KeystoreWrapper`) : **conçus, non exécutés** (aucun fichier, aucun rapport `w4/w5/w6` dans `docs/agent-reports/`) | | W7 **dépend** de w4-01 (`X25519.kt`, `InstallKey.kt`) et réutilise `PhoneSync` (w6-02) : voir § 12 |
| Télémétrie / journal | `docs/TELEMETRY.md:55-60` (clés interdites : ip, mac, ssid, token, pin…), `C/trust/Diagnostics.kt:22-147` (11 étapes, `Redact.scrub` `:45-53`), `C/tunnel/TunnelJournal.kt` (journal rotatif 24 Ko du tunnel) | | pas de journal de **liaison** structuré, aucun événement `link_*` |

## 2. Les neuf problèmes du terrain : cause racine et correctif

| # | Observation | Cause racine (code) | Correctif W7 (cahier) |
|---|---|---|---|
| 1 | La TV n'annonçait pas le service `…0005` avant le démarrage de `TvService` + `BLUETOOTH_ADVERTISE` ; puis la clé est refusée « signataire inconnu » et le téléphone n'affiche que « Refusée par la TV : … » | `OwnerBtHost.start()` n'est appelé que par `TvService.startCore/onPermissionsReady` (`R/TvService.kt:182,195,411`) ; permissions demandées par des activités seulement ; `TRUSTED_KEYS` compilées (`R/ActivationCenter.kt:49-51`) ; `RESULT` = octet + texte libre (`C/owner/OwnerChannel.kt:36-40`) ; `sendOutcome` sans action (`C/owner/ActivationScreenState.kt:44`) | **`TvBeacon`** démarré par `TvApp.onCreate` et `BootReceiver` (w7-12) ; permissions demandées **une fois** par une `TvPermissions` centrale (w7-12) ; `RESULT` v2 **structuré** (`reason=`, `kid=`, `knownKids=`, `tvVersion=`) rétrocompatible (w7-07) ; catalogue de messages avec action (« Cette TV ne connaît pas la clé qui a signé (kid …) : mettez CastBridge-TV à jour » / « émettez la clé avec l'outil de bureau ») (w7-22) ; **message signé `keyring`** (ajout d'une clé de confiance par une clé portant `REGISTRY`) pour ne plus recompiler les TV (w7-07, décision D-W7-4) |
| 2 | TV activée, le téléphone ne le constate pas | aucun canal TV→téléphone ; lecture LAN seulement, écran ouvert seulement (`S/ActivateTvActivity.kt:59-75`) | domaine `act` du protocole de synchronisation : la TV émet `NOTIFY act` dès `ActivationCenter` change ; le téléphone l'applique (`PhoneSync.proofOk` W6) et affiche « <TV> activée en production » (w7-04, w7-13, w7-18) |
| 3 | TV déjà connectée, app téléphone réinstallée (ré-association, jeton perdu) | reprise existante conditionnée à SCAN/localisation (`S/TvLink.kt:158` vs `S/TvHub.kt:79`) ; identité = adresse BT seulement | permissions harmonisées (CONNECT suffit, w7-16) ; identité par **clé** (w7-06, w7-14) ; **ré-adoption** guidée : SAS 4 chiffres + code foyer + un « Autoriser » (w7-14, w7-19) |
| 4 | Le téléphone ne joint pas la TV en IP alors que le Mac le peut (isolation des clients ; TV `.121` → autre adresse, téléphone `.108`) | IP mémorisées seulement, sonde 1,2 s (`S/LinkAndroid.kt:75-82`) ; pas de re-résolution par mDNS après changement ; isolation non détectée ; WD manuel | `DiscoveryPlanner` (w7-09) : re-résolution par **HELLO Bluetooth** (la TV dit ses IP du moment) → mDNS v2 → IP mémorisées ; **inférence d'isolation** : « même sous-réseau, BT vivant, 3 sondes LAN échouées » ⇒ état `ISOLATED` ⇒ **Wi-Fi Direct automatique** (w7-21) ou mux Bluetooth ; message exact dans « Réparer la connexion » (w7-19) |
| 5 | SSH de dev demande « installer des apps inconnues » à la main | `REQUEST_INSTALL_PACKAGES` exige un réglage système par application (manifeste TV `:17`) ; GaiaOS ignore `startActivity` depuis un fil HTTP (`HANDOFF.md:191`) | hors cœur du plug and play ; w7-15 ajoute dans « Connexion » de la TV un bouton **« Autoriser les mises à jour »** qui ouvre `ACTION_MANAGE_UNKNOWN_APP_SOURCES` **depuis l'activité visible**, et `/api/update/prepare` qui demande à l'écran de l'ouvrir |
| 6 | « Prêt à recevoir · code 77•••• », PIN qui change après réinstallation ⇒ saisie manuelle | PIN créé une fois (`R/TvPrefs.kt:11-15`) ; un téléphone de confiance n'en a pas besoin, mais après réinstallation **de la TV** le registre est perdu ⇒ `installId` neuf ⇒ « TV réinitialisée » ⇒ ré-association | **réinstallation de la TV** = nouvelle `InstallKey` ⇒ téléphone : « La TV a changé d'identité » (W6 M-IDENTITY-CHANGED) ⇒ un bouton « Confirmer » ⇒ fenêtre auto-ouverte (aucun téléphone de confiance) ⇒ **une** validation à la télécommande, zéro code (w7-14, w7-14, w7-19). Le PIN reste l'issue de secours pour les appareils non de confiance (Mac, navigateur) |
| 7 | Progression du transfert perdue sur l'écran TV (« reste à prêt à recevoir ») ; Bluetooth lent | statut `1-bt` = chaîne écrasée (`R/BtServer.kt:113-127`) ; RFCOMM ≈ 100-250 Ko/s par nature | **`TransferLedger`** (w7-10) sur la TV : un enregistrement par transfert (nom, reçu/total, voie, état, reprise possible) persisté, affiché jusqu'à fin ou abandon, publié dans le domaine `xfer` ; **politique de voie** (w7-21) : jamais de masse par Bluetooth si une voie Wi-Fi existe ou peut être créée (WD auto), ETA affichée, Bluetooth = plan de contrôle + petits lots |
| 8 | Écran du téléphone verrouillé pendant les tests ADB | réglage de test (`stay_on_while_plugged_in=3`, mémoire) | plan de test (w7-24) ; pour l'utilisateur : rien n'exige l'écran allumé (JobScheduler + CDM) |
| 9 | Dialogues de permission / « rendre visible » en boucle | `R/ActivationActivity.kt:178-192` (chaque `onResume`), `R/PairActivity.kt:157-162,239` (fenêtre fermée à 8 s puis rouverte avec popup) | `TvPermissions` : une demande par processus, détection « ne plus demander », explication à l'écran avec D-pad ; « visible » demandé **seulement** à l'ouverture manuelle de la fenêtre ou au premier démarrage sans téléphone de confiance, **jamais** au retour d'un `onStop` (w7-12, w7-14) |

## 3. Architecture

### 3.1 Vue d'ensemble

```
TÉLÉPHONE (CastBridge)                                         TV (CastBridge-TV)
┌────────────────────────────────────────────┐                ┌──────────────────────────────────────────┐
│ Écrans : TvHome, TvPairScreen, Connexion   │                │ Écrans : HomeScreen, PairActivity,       │
│  (indicateur, « Réparer la connexion »)    │                │  Connexion (diag), ActivationActivity     │
├────────────────────────────────────────────┤                ├──────────────────────────────────────────┤
│ LinkRuntime (S/link/)  ← Android glue      │                │ TvBeacon (R/) : …0005 owner, …0006 sync, │
│  triggers, JobScheduler, CDM, FGS rules    │                │  démarré par TvApp + BootReceiver,        │
├────────────────────────────────────────────┤                │  TV verrouillée ou non                    │
│ LinkManager (C/link/)  ← cœur pur          │  CBSX/CBSY     ├──────────────────────────────────────────┤
│  états, DiscoveryPlanner, RoutePolicy,     │◄──RFCOMM 0006─►│ LinkHost (C/link/) : état par téléphone, │
│  SecureSession (CBSX), SyncClient          │◄──HTTP /api/sync►│ SyncHost, domaines (act, lots, lib, xfer, │
│  enveloppe LinkDriver/LinkMachine (existant)│                │  par, shop, set, ico), NOTIFY             │
├────────────────────────────────────────────┤                ├──────────────────────────────────────────┤
│ Domaines (stores) : ProofStore(W6),        │                │ Sources : ActivationCenter, LotsHub,      │
│  LotsRuntime, TransferQueue(persisté),     │  masse : HTTP  │  RentalHub, Library/LibraryStore,         │
│  ParentalInbox, ShopRuntime(W5), Prefs     │◄─/api/transfer►│  TransferLedger, ParentalHub, ShopHub(W5),│
│                                            │  CBT1, WD      │  TvPrefs, StatusIconModel                 │
├────────────────────────────────────────────┤                ├──────────────────────────────────────────┤
│ LinkJournal (C/link/) · SelfTest           │                │ LinkJournal · SelfTest                    │
└────────────────────────────────────────────┘                └──────────────────────────────────────────┘
Transports : Bluetooth RFCOMM sécurisé (contrôle + synchro + petits lots) · Wi-Fi LAN HTTP (synchro + masse) ·
Wi-Fi Direct HTTP (masse quand le LAN est isolé) · tunnel mux …0004 (HTTP par Bluetooth, dernier recours, inchangé)
```

**Règles d'architecture** : (1) toute logique est dans `C/link/` (pur, testé avec faux transports et fausse horloge), Android ne fait que les questions/réponses au système ; (2) **rien d'existant n'est réécrit** : `LinkMachine`/`LinkDriver`/`PairFlow`/`TrustRegistry`/`HelloHandler`/`Mux`/`LinkPool`/`DeliveryQueue`/`ReportInbox`/`xfer` sont **enveloppés ou étendus de façon additive** ; (3) un ancien téléphone avec une TV neuve, et l'inverse, continuent de marcher (chaque extension est signalée par une capacité ; l'absence = comportement d'aujourd'hui).

### 3.2 Composants nouveaux (cœur `C/link/`)

| Fichier | Rôle |
|---|---|
| `LinkManager.kt` | machine d'états de haut niveau (§ 5), par TV ; consomme `LinkDriver.Step` (états fins) + `RouteTable` + `SyncClient` ; produit `LinkSnapshot` (état, route(s), âge de synchro par domaine, prochaine action) |
| `DiscoveryPlanner.kt` | ordre et délais des méthodes de découverte, fusion des candidats, inférence d'isolation, cache d'adresses avec re-vérification (§ 4) |
| `RoutePolicy.kt` | notation des routes (LAN > WD > BT-mux > BT-seul) par usage (contrôle / synchro / masse), caps de débit, bascule et retour sans écran |
| `SecureSession.kt` (`CBSX`) | poignée de main X25519 XX, HKDF, AES-GCM, compteur de nonce, SAS (§ 6) |
| `SyncFrames.kt`, `SyncCodec.kt` | trames `CBSY`, codage binaire compact, versions et capacités (§ 7) |
| `SyncEngine.kt`, `SyncDomain.kt`, `DeltaLog.kt` | moteur commun (séquences, empreintes, delta/ack, idempotence, conflits) et interface d'un domaine |
| `domains/*.kt` | adaptateurs : `ActDomain`, `LotsDomain`, `LibDomain`, `XferDomain`, `ParDomain`, `ShopDomain`, `SetDomain`, `IcoDomain` |
| `Identity.kt` | `PhoneKey`, `TvIdentity` (clé épinglée + empreinte lisible `xxxx-xxxx`), `TrustRecordV2` |
| `Readoption.kt` | ré-adoption (SAS + code foyer + un « Autoriser ») ; politique de fenêtre d'association |
| `TransferLedger.kt` | grand livre des transferts (TV) et file persistée (téléphone) |
| `LinkJournal.kt`, `SelfTest.kt`, `LinkTexts.kt` | journal circulaire, auto-test en une phrase, textes français (un par issue, aucun ailleurs) |

### 3.3 Ce qui est gardé / étendu / remplacé (fichiers)

| Garder tel quel | Étendre (additif) | Remplacer (ne plus utiliser pour le lien) |
|---|---|---|
| `C/trust/{LinkMachine,LinkDriver,PairFlow,ResilientCall,Credentials,Storm,TrustFiles,PhonePresence}.kt` ; `C/tunnel/{Mux,LinkPool,TcpTunnel}.kt` ; `C/xfer/**` ; `C/lots/{DeliveryQueue,LotPush,TvLotStore}.kt` ; `C/parental/ParentalSync.kt` ; `C/tv/BtProtocol.kt` (CBT1/CBTN/CBTH/CBTP inchangés) | `C/trust/TrustRegistry.kt` (ligne `K` : clé publique du téléphone) ; `C/trust/HelloHandler.kt` (capacité `sync`, `pub`) ; `C/tv/BtProtocol.kt` (`HelloInfo` : `pub=`, `caps=`, `act=` ; constante `SYNC_SERVICE_UUID …0006`) ; `C/owner/{OwnerFrames,OwnerChannel}.kt` (`RESULT` v2 structuré, trame `KNOCK` 11, `KEYRING` 12) ; `C/owner/Envelope.kt` (type `keyring`) ; `C/trust/Diagnostics.kt` (étapes LAN/isolation/synchro) ; `R/TvService.kt` (délègue au `TvBeacon`, branche `SyncHost` ; **ne démarre plus** `OwnerBtHost`) ; `R/BtServer.kt` (statuts → `TransferLedger`) ; `S/TvLink.kt` (`TvLinkManager` devient une façade sur `LinkRuntime`, API publique conservée : `state`, `credentialFor`, `poke`, `retryNow`, `pair`, `forget`) ; `S/LinkAndroid.kt` (permissions harmonisées, CDM) ; `S/TransferQueue.kt` (persistance) ; `S/ActivateTvActivity.kt` (résultat structuré) | `S/TvDiscovery.kt` (absorbé par `DiscoveryPlanner` + `NsdDiscovery` v2 ; le fichier reste pour DLNA/mDNS brut, non supprimé) ; les lectures ponctuelles de `GET /api/activation` par le téléphone (remplacées par le domaine `act`) ; la ligne de statut `1-bt` comme seule mémoire des transferts |

## 4. A — Découverte et association « zéro configuration »

### 4.1 Stratégie en couches (ordre, délais, arrêt au premier succès **par usage**)

| Rang | Méthode | Préconditions | Délai max | Ce qu'elle rapporte | Décision |
|---|---|---|---|---|---|
| 1 | **Adresses mémorisées** (`SavedTv.lastIps`, port) re-vérifiées par `GET /api/hello` (sonde 1,2 s, 2 essais) | Wi-Fi/Ethernet actif | 2,5 s | route LAN immédiate | gardée (existant, `S/LinkAndroid.kt:75-82`) |
| 2 | **Bluetooth SDP** : appareils appairés offrant un UUID `7c5e3b9a-…-cb00000000xx` (`OL/TvBluetooth.kt:29`) puis **HELLO** `CBTH` sur `…0001` (existant) qui renvoie **les IP du moment** + `pub` + `caps` | Bluetooth allumé + CONNECT | 6 s (connect RFCOMM) | identité, jeton, IP fraîches, capacités | gardée, étendue : HELLO devient la **re-résolution d'adresse** privilégiée (une TV qui a changé d'IP est retrouvée sans mDNS) |
| 3 | **mDNS/NSD `_castbridge._tcp` v2** : TXT `id=<8 hex = SHA-256(InstallKey pub)[0:4]>`, `proto=1`, `caps=sync,xfer,wd`, `port=8765`, `v=<version TV>`, `role=receiver` | Wi-Fi + `MulticastLock` | 4 s (puis en continu tant que l'écran de liaison est ouvert) | candidats LAN avec identité → **auto-liaison** si `id` = TV épinglée | nouveau (w7-14 TV, w7-17 téléphone) ; **jamais** de code d'appareil ni d'adresse MAC dans le TXT |
| 4 | **Toc** (`KNOCK`) : le téléphone frappe sur `…0005` (Bluetooth) ou `POST /api/knock` (LAN) avec sa clé publique signée au nonce ; la TV affiche « <nom> demande à s'associer » | TV joignable mais inconnue du téléphone, ou téléphone inconnu de la TV | 1 s | ouverture de la fenêtre d'association **par le propriétaire** (un bouton) | nouveau (w7-06, w7-14) |
| 5 | **Wi-Fi Direct** (groupe créé par la TV sur CBTN `WANT_WIFI_DIRECT`, existant `R/TvService.kt:284-289`) | isolation détectée (§ 4.3) ou aucun LAN commun ; Android ≥ 10 côté téléphone | 10 s | route WD pour la masse | **automatique** en cas d'isolation (w7-21) ; manuel sinon (inchangé) |
| 6 | **QR sur l'écran de la TV** (`PairActivity`, « Ma TV n'apparaît pas ») : `castbridge://tv?id=<8 hex>&bt=<adresse BT>&ip=<ip>&port=8765&pub=<x25519 b64url>&sas=<4 chiffres>` | caméra du téléphone | — | tout ce qu'il faut pour se lier **sans Bluetooth** (TV sans Bluetooth, box) | nouveau (w7-14, w7-19) : le QR vaut **preuve de présence physique** : il remplace la comparaison de code Android |
| 7 | **SSDP/DLNA** (existant onglet DLNA, `S/MainActivity.kt`) | — | — | indice « une TV est là » (fabricant) | **non utilisé** pour CastBridge-TV (pas d'identité) ; reste pour la télécommande intelligente |
| — | **BLE** (annonce de présence à identifiant tournant) | annonceur BLE sur la TV | — | — | **D-W7-1 : non en v1.** Aucune TV du parc ne l'offre de façon vérifiée (la TV de référence GaiaOS 32 bits n'expose pas d'annonceur : aucun code, aucune mesure) ; les téléphones < 12 exigeraient la localisation pour scanner (contraire à « aucune localisation ») ; le SDP classique + mDNS + toc couvrent le besoin. Réévaluer quand une TV à BLE est mesurée (§ 13) |

**Ordre d'essai côté téléphone au démarrage (TV connue)** : 1 (2,5 s) **en parallèle de** 2 (6 s) ; le premier qui répond fixe la route de contrôle ; 3 tourne en fond 4 s si 1 a échoué ; 5 seulement si `ISOLATED` ; 4/6 seulement si la TV est inconnue ou a changé d'identité. **Pas de balayage Bluetooth classique (`startDiscovery`) automatique** : il casse les connexions RFCOMM (`S/TvLink.kt:68`) et coûte ; il ne tourne que dans « Ajouter ma TV » (existant `BtFinder`).

### 4.2 Politique de la fenêtre d'association (TV) — décision D-W7-2

| Situation | Fenêtre | Visible (popup système) |
|---|---|---|
| **Aucun téléphone de confiance** (première installation, après réinitialisation du registre, après « Oublier tous ») | **auto-ouverte** à chaque arrivée sur l'accueil, **10 min**, bandeau « Ouvrez CastBridge sur votre téléphone et touchez « Ajouter ma TV » » | demandée **une fois** par démarrage de l'application (300 s) ; refus mémorisé pour la session |
| Au moins un téléphone de confiance | fermée ; s'ouvre **2 min** sur un `KNOCK` (dialogue « <nom> demande à s'associer » → Autoriser/Refuser, Refuser présélectionné) ou manuellement (tuile « Ajouter un téléphone », inchangé) | seulement à l'ouverture manuelle |
| Ré-adoption (téléphone connu par sa clé mais adresse ou jeton perdus, ou TV réinstallée) | dialogue direct, **sans** fenêtre : « Est-ce bien votre téléphone ? Code **4712** » → Autoriser | non |
| 3 refus | blocage 10 min de **cette** clé/adresse (existant `PairingSession.kt:108-111`) ; le compteur se **remet à zéro** à la fin du blocage (correctif) | — |

Un inconnu ne peut donc jamais entrer sans (a) une action physique du propriétaire à la télécommande **et** (b) la comparaison du code (Android SSP pour le Bluetooth, SAS `CBSX` pour le LAN/QR).

### 4.3 Inférence « isolation des clients » (pure, testée)

`DiscoveryPlanner.isolation(obs)` renvoie `ISOLATED` quand, pendant une même fenêtre de 20 s : (1) le HELLO Bluetooth a réussi et rapporte ≥ 1 IPv4 **du même sous-réseau** que le téléphone (`/24` par défaut, masque réel si connu) ; (2) ≥ 3 sondes `GET /api/hello` vers cette IP ont échoué ; (3) le téléphone a un réseau validé. `SUSPECTED` avec (1)+(2) sans (3). Effet : route de masse = Wi-Fi Direct (si possible) sinon mux Bluetooth ; texte : « Votre box sépare les appareils du Wi-Fi (isolation des clients). CastBridge passe par Wi-Fi Direct ; pour un transfert plus rapide, désactivez l'isolation dans la box ou branchez la TV en Ethernet. » Faux positifs tolérés (on ne perd qu'un peu de débit).

### 4.4 Amorçage de confiance et identité (D-W7-3)

- **Clés** : TV = `InstallKey` X25519 de w4-01/w4-03 (**prérequis** ; repli : si W4 n'est pas fusionné, w7-06 crée la graine dans `files/link/install_x25519` par `SafeFile`, protection « logicielle » signalée comme W6 le prévoit). Téléphone = `PhoneKey` X25519 dans `files/link/phone_x25519` (Keystore Android non requis : la clé ne vaut que pour **cette** installation ; un téléphone volé et déverrouillé a déjà le jeton : limite connue `BT-PLUG-AND-PLAY.md:131`).
- **Registre TV v2** : ligne `K\t<adresse>\t<pub b64url>\t<empreinte>` en plus de `P` ; un téléphone est reconnu par **adresse OU clé** (`TrustRegistry.find(address, pub)`) ; un fichier v1 se charge sans clé (clé apprise au prochain HELLO sécurisé).
- **TOFU sur le téléphone** : la clé publique de la TV est épinglée à la première liaison **sécurisée** (RFCOMM appairé, ou `CBSX` dont le SAS a été comparé, ou QR) ; empreinte `xxxx-xxxx` (8 premiers hex de SHA-256(pub), groupés) affichée dans « À propos » de la TV et dans « Connexion » du téléphone ; une clé différente ⇒ `IDENTITY_CHANGED` (W6) ⇒ confirmation explicite.
- **Jetons** : inchangés (12 h, hachés, 4 par téléphone, renouvellement à mi-vie). **Nouveau** : un HELLO présenté par un téléphone dont la **clé** est connue mais l'adresse non (téléphone neuf, adresse changée) obtient un jeton **après ré-adoption** ; un téléphone dont l'adresse est connue mais sans clé (ancien registre) obtient un jeton et sa clé est inscrite.
- **Ré-adoption** (réinstallation du téléphone = nouvelle `PhoneKey`) : le téléphone demande `READOPT` (trame `CBSX` avec l'ancienne empreinte s'il la connaît encore — il ne la connaît pas après réinstallation : alors c'est un **nouveau** téléphone) ; la TV affiche « Est-ce bien votre téléphone ? Code 4712 » (SAS = 4 chiffres dérivés du hachage de la poignée de main, affiché aussi sur le téléphone) ; **si** le téléphone a saisi le **code foyer** (D-W7-5, BLOQUÉ : = code parental existant, ou nouveau code ?), la TV l'accepte après le seul « Autoriser » **sans** fenêtre ni comparaison de code Android (le SAS suffit) ; sinon parcours normal (fenêtre + Android SSP).
- **Anti-rejeu / MITM** : nonce par poignée de main, clés de session éphémères, authentification mutuelle par clés statiques (XX), SAS comparé pour la première rencontre hors Bluetooth, `seq` strictement croissant par domaine et par session, trames AEAD avec compteur ; un ancien jeton refusé n'est jamais rejoué (`CredentialGate`, existant).

## 5. B — Cycle de vie de la liaison

### 5.1 `LinkManager` (téléphone), un par TV

```
            ┌──────────┐  Ajouter ma TV / QR / toc    ┌─────────┐  Done (PairFlow)  ┌────────────────┐
            │  NO_TV   │ ───────────────────────────► │ PAIRING │ ────────────────► │  DISCOVERING   │◄───┐
            └──────────┘                              └─────────┘                   └────────────────┘    │
                  ▲   Oublier                                                      LAN ok │ BT ok │ les deux   │ perte totale > 40 s
                  │                                                     ┌──────────────┼────────┼───────┐     │ (ou Bluetooth éteint ET pas de LAN)
                  │                                                     ▼              ▼        ▼       │     │
            ┌──────────┐                                        ┌────────────┐  ┌───────────┐ ┌─────────────┐ │
            │ OFFLINE  │◄── TV absente > 10 min ou appli en fond ──│ LINKED_LAN │  │ LINKED_BT │ │ LINKED_BOTH │─┘
            └──────────┘    sans aucune route                   └────────────┘  └───────────┘ └─────────────┘
                  │  réveil (diffusion, job, ouverture)                 ▲   LAN mort, BT vivant   │
                  └──────────────────────────► DISCOVERING             └──── DEGRADED ◄──────────┘
                                                              (BT seul alors que la TV a une IP : masse ralentie, WD tenté)
```

| État | Signification | Routes | Synchro | Essais |
|---|---|---|---|---|
| `NO_TV` | aucune TV enregistrée | — | — | jamais |
| `DISCOVERING` | TV connue, aucune route confirmée | sondes § 4.1 | — | courbe `LinkMachine` (2→60 s / 1→15 min) |
| `PAIRING` | `PairFlow`/QR/ré-adoption en cours | BT ou LAN+SAS | — | géré par `PairFlow` |
| `LINKED_BT` | contrôle + synchro par `…0006` ; masse par CBT1/mux ; la TV n'a **aucune** IP | BT | NOTIFY temps réel | garde-vivant PING 20 s |
| `LINKED_LAN` | contrôle + synchro + masse par HTTP (`/api/sync/wait` long-poll 25 s) ; Bluetooth non utilisé (éteint, ou TV sans Bluetooth) | LAN/WD | NOTIFY temps réel | garde-vivant = long-poll |
| `LINKED_BOTH` | les deux ; **synchro par LAN**, Bluetooth gardé comme **secours** (PING 60 s seulement) | LAN + BT | temps réel | — |
| `DEGRADED` | BT vivant mais LAN attendu et mort (`wifiExpected`, existant `LinkMachine.kt:134`) | BT (+ WD si isolation) | temps réel par BT | sonde LAN toutes 15 s (existant) |
| `OFFLINE` | rien ne répond depuis 10 min, ou appli en arrière-plan sans route | — | file locale | job 15 min + diffusions |

Correspondance avec `LinkMachine.LinkState` (gardée pour les textes et l'hystérésis) : `Connected(LAN/DIRECT)` ⇒ `LINKED_LAN` ou `LINKED_BOTH` selon le PING BT ; `Connected(BLUETOOTH)` ⇒ `LINKED_BT` ; `Degraded` ⇒ `DEGRADED` ; `Reconnecting/TvUnreachable/Connecting` ⇒ `DISCOVERING` ; `TvForgotMe/Denied/CredentialExpired…` ⇒ `DISCOVERING` avec `blocker` (texte existant) ; `NoTv` ⇒ `NO_TV`. **Aucun nouveau texte d'état fin** : `LinkText` reste la source.

### 5.2 Transport, bascule, garde-vivant

- **Plan de contrôle** = la première route vivante parmi LAN, BT ; **plan de masse** = `RoutePolicy.forBulk(bytes)` : LAN > WD > mux BT (fichiers < 5 Mo ou lots) > CBT1 (fichiers, reprise par `.part`, existant) ; au-dessus de 50 Mo sans route Wi-Fi : **demander** (« 80 Mo par Bluetooth ≈ 8 min : envoyer quand même ? Astuce : Wi-Fi Direct ») sauf réglage « toujours envoyer ».
- **Bascule sans écran** (existant `LinkDriver.kt:182-193`, gardé) : LAN mort → BT avec le même jeton ; LAN revenu → repris. W7 ajoute : la **session `CBSX` suit la bascule** (re-poignée de main sur la nouvelle route, < 300 ms sur LAN, < 2 s sur BT), les `seq` des domaines continuent (pas de resynchro complète).
- **Garde-vivant** : LAN = long-poll 25 s (+ `GET /api/info` existant toutes 15 s au premier plan, 5 min en fond) ; BT = PING `CBSY` 20 s au premier plan, 60 s en `LINKED_BOTH`, **aucun** en fond (la TV ferme à 90 s de silence ; la reprise est un nouveau `connect`, 1,5 s de garde `LinkPool`).
- **Courbes** : inchangées (`LinkMachine.Config`), gigue ±25 % ; **limiteur** existant (12 HELLO/min téléphone, 10/min/pair + 40/min TV).

### 5.3 Batterie et arrière-plan (téléphone)

- **Aucun service au premier plan permanent** (règle gardée, `BT-PLUG-AND-PLAY.md:88`). Les FGS existants restent liés à une tâche (envoi, télécommande, passerelle) avec leur type (`dataSync`, `connectedDevice`, manifeste `:144-160`) ; sur Android 14 un FGS `connectedDevice` ne démarre depuis l'arrière-plan **que** sur diffusion `ACL_CONNECTED`/`BOND_STATE_CHANGED` (exemption) ou avec une association **CDM**.
- **Companion Device Manager (D-W7-6, recommandé OUI)** : pendant « Ajouter ma TV », après l'approbation, le téléphone demande `CompanionDeviceManager.associate(AssociationRequest.Builder().addDeviceFilter(BluetoothDeviceFilter(adresse)).setSingleDevice(true))` (API 26+) : **un** dialogue système « Associer CastBridge-TV à CastBridge ? ». Gains : `REQUEST_COMPANION_RUN_IN_BACKGROUND` (26+, exemption Doze), `startObservingDevicePresence` (31+ : réveil quand la TV apparaît, sans diffusion), démarrage de FGS depuis l'arrière-plan autorisé (31+), `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`. Refus de l'utilisateur = tout marche comme aujourd'hui (job 15 min). Coût : 0 dépendance (API plateforme). **À mesurer** sur Tecno/Infinix/Itel (HiOS/XOS) et Xiaomi (MIUI) : la présence CDM y est parfois ignorée ⇒ le job reste la garantie.
- **Tueurs de batterie OEM** : écran « Connexion » → carte « Laisser CastBridge tourner en arrière-plan » avec lien profond par fabricant (table figée `OemBattery.kt`, testée : Samsung « Applications jamais mises en veille », Xiaomi « Démarrage automatique » + « Aucune restriction », Tecno/Infinix/Itel « Gestion automatique » + « Application protégée », Huawei, Oppo/Realme, Vivo ; repli `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), affichée **seulement** si le journal montre ≥ 2 réveils de job manqués de plus de 30 min.
- **Notifications** : aucune notification permanente ; une notification **silencieuse** par événement utile (« <TV> activée en production », « Transfert repris », « La TV a changé d'identité : confirmez ») ; libellé des FGS existants inchangé.

### 5.4 TV : démarrage, veille, redémarrage

- **`TvBeacon`** (`R/TvBeacon.kt`) : objet de processus démarré dans `TvApp.onCreate` et par `BootReceiver` (déjà `BOOT_COMPLETED`/`QUICKBOOT`/`MY_PACKAGE_REPLACED`, `R/TvService.kt:1037-1042`, `autostart` par défaut vrai) ; il ouvre `…0005` (propriétaire) et `…0006` (synchro) **dès que** `BLUETOOTH_CONNECT` est accordée (≥ 31) ou immédiatement (< 31), TV verrouillée ou non ; il publie mDNS v2 dès qu'un réseau existe (même verrouillée : TXT `caps` sans `sync` tant que le cœur ne tourne pas, pour que le téléphone trouve la TV et porte la clé). Il **n'a besoin d'aucun service au premier plan** (sockets d'écoute bloquantes sur deux fils démon) ; `TvService` reste pour le reste et **branche** `SyncHost` sur le beacon quand il démarre.
- **Veille de la TV** : la plupart des TV coupent le Wi-Fi/Bluetooth en veille (hors « réveil réseau ») : la liaison tombe, le téléphone passe `OFFLINE` sans alarme ; au réveil, `BootReceiver` n'est pas rejoué mais le processus survit généralement ; si le système l'a tué, `autostart` ne relance **qu'au démarrage** : W7 ajoute un `JobScheduler` TV périodique 15 min (`setPersisted`) qui appelle `TvBeacon.ensure()` (coût nul si déjà là). **HDMI-CEC** : aucune API applicative pour allumer une TV depuis le téléphone ⇒ **non** (D-W7-1 bis) ; la TV allumée par la télécommande suffit.
- **Changement d'IP/port/PIN** : IP = HELLO et mDNS (§ 4.1) ; port fixe 8765 (`ReceiverServer.kt:1229`) ; PIN = sans effet pour un téléphone de confiance ; `installId` conservé (registre) ; `InstallKey` conservée (Keystore ou fichier).
- **Multi-TV** : `SavedTvs` (existant) ; un `LinkManager` par TV, **un seul actif** (TV par défaut ou TV cible de l'écran, `TvLinkManager.makeDefault`) + les autres en `OFFLINE` surveillé par mDNS (0 coût) ; confiance par TV ; **multi-téléphone** : chaque parent est un téléphone de confiance (registre, jusqu'à 8 ; les rapports parentaux restent aux détenteurs W6) ; **profils enfants** : vivent sur la TV (W6 § 3.9), inchangé.

## 6. C — Canal sécurisé `CBSX` (spécification exacte)

- **Primitives** : X25519 (w4-01 `C/owner/X25519.kt`, pur), HKDF-SHA256 (`javax.crypto.Mac` HmacSHA256, disponible Android 8), AES-256-GCM (`javax.crypto.Cipher` « AES/GCM/NoPadding », Android 8+, 32 bits inclus), SHA-256. **Aucune bibliothèque ajoutée.**
- **Clés statiques** : `s_tv` (InstallKey), `s_ph` (PhoneKey). **Éphémères** : `e_tv`, `e_ph` par session.
- **Poignée de main (modèle Noise XX, 3 messages, transcript `h`)** :
  1. téléphone → TV : `"CBSX" | u8 ver=1 | e_ph(32) | u16 len | caps(UTF-8)` ; `h = SHA256("castbridge-sync-v1" | msg1)`.
  2. TV → téléphone : `e_tv(32) | ENC_{k1}(s_tv(32) | u16 len | infos TV)` où `k1 = HKDF(ECDH(e_ph,e_tv), salt=h, info="k1")` ; `h ← SHA256(h | msg2)`. `infos TV` = `id=<8 hex>\nname=…\nver=…\ncaps=…\nact=<digest>`.
  3. téléphone → TV : `ENC_{k2}(s_ph(32) | u16 len | preuve)` où `k2 = HKDF(ECDH(e_ph,e_tv) | ECDH(e_ph,s_tv), salt=h, info="k2")` ; `preuve` = `pub` du téléphone déjà incluse, plus `readopt=1` et `household=<HMAC_{k2}(code foyer)>` le cas échéant ; `h ← SHA256(h | msg3)`.
  - **Clés de session** : `(k_c2s, k_s2c) = HKDF(ECDH(e_ph,e_tv) | ECDH(e_ph,s_tv) | ECDH(s_ph,e_tv), salt=h, info="session", 64 octets)`. Confidentialité persistante : les statiques seules ne permettent pas de déchiffrer un enregistrement.
  - **SAS** = `(u32 BE de SHA256("sas" | h)) mod 10000`, affiché « 47 12 » des deux côtés quand il faut une première rencontre sans Bluetooth appairé (LAN, QR : le QR porte `sas` **et** `pub`, donc la comparaison est implicite : le téléphone vérifie que `s_tv` reçu = `pub` du QR).
- **Trames chiffrées** : `u16 len | nonce(12) = u32 direction | u64 compteur | AES-GCM(k_dir, nonce, aad = "CBSY1", texte clair = trame CBSY)` ; compteur strictement croissant par direction ; tag 16 octets ; une trame hors ordre ferme la session. Limite 64 Kio par trame ; les gros corps passent en `BULK` (HTTP, existant).
- **Sur Bluetooth** : `CBSX` par-dessus le socket RFCOMM sécurisé de `…0006` (double chiffrement, négligeable sur 32 bits : AES-GCM ≈ 20-40 Mo/s en logiciel, la synchro pèse quelques Ko). **Sur LAN** : `POST /api/sync/hs1|hs2|hs3` puis `POST /api/sync/frame` (corps = trame chiffrée) et `GET /api/sync/wait?sid=&timeout=25` (long-poll, réponse = trames chiffrées en file). L'identité HTTP n'est plus le jeton mais la session (`sid` = 16 hex dérivés de `h`) ; le jeton reste pour toutes les **autres** routes (inchangées).
- **Vecteurs** : `tools/activation/sync-vectors.json` (poignées de main avec graines fixes, clés attendues, SAS, trames, refus : clé statique inattendue, compteur rejoué, tag faux) rejoués en Kotlin et Python (w7-03, w7-11).

## 7. C — Protocole de synchronisation `CBSY` v1

### 7.1 Trames (après `CBSX`, dans l'AEAD)

| Type | Nom | Charge utile | Sens |
|---|---|---|---|
| 1 | `HELLO` | `u8 proto=1 \| u16 caps bits \| u8 n \| n × (u8 dom \| u32 seq \| 8 oct. digest)` | les deux (premier message de chaque côté) |
| 2 | `DIGEST` | idem sans caps | réponse / périodique (toutes 5 min au premier plan) |
| 3 | `PULL` | `u8 dom \| u32 sinceSeq \| u16 max` | demandeur |
| 4 | `DELTA` | `u8 dom \| u32 fromSeq \| u32 toSeq \| u8 flags(1=snapshot, 2=more) \| u16 n \| n × entrée(u16 len \| octets)` | détenteur |
| 5 | `ACK` | `u8 dom \| u32 seq` | receveur |
| 6 | `NOTIFY` | `u8 dom \| u32 seq \| 8 oct. digest` | détenteur, dès qu'un changement est **persisté** (coalescé 250 ms) |
| 7 | `PING` / 8 `PONG` | `u32 echo` | les deux |
| 9 | `BULK` | `u8 dom \| u16 len \| descripteur (nom, taille, sha256, voie proposée)` | annonce qu'un gros objet est à transférer **par le moteur de masse** (HTTP `/api/transfer`, CBT1, lots) ; la synchro ne porte jamais > 64 Kio |
| 10 | `KNOCK` | `u16 len \| nom du téléphone` | téléphone inconnu (sur `…0005` c'est la trame CBTO 11 ; ici pour le LAN) |
| 11 | `READOPT` | `u8 flags \| 32 oct. HMAC code foyer (facultatif)` | téléphone |
| 12 | `ERROR` | `u8 code \| u16 len \| texte` | les deux ; codes : 1 version, 2 domaine inconnu, 3 refusé, 4 trop grand, 5 occupé |

Versionnage : `proto` dans `HELLO` ; un domaine inconnu est ignoré (ERROR 2 non fatal) ; une capacité absente = fonction absente. Un **ancien** téléphone n'ouvre jamais `…0006` ni `/api/sync` : rien ne change pour lui. Une **ancienne** TV : le téléphone voit `caps` sans `sync` dans le HELLO CBTH (ou l'absence du service) et retombe sur les lectures ponctuelles d'aujourd'hui (`GET /api/activation`, `GET /api/lots`, CBTP), **sans message d'erreur**, avec la mention « synchro simple (TV à mettre à jour) » dans « Connexion ».

### 7.2 Domaines

| dom | Nom | Détenteur (vérité) | Entrée (JSON compact, ≤ 4 Kio) | Poussé / tiré | Latence visée (lié) | Hors ligne |
|---|---|---|---|---|---|---|
| 1 `act` | activation / preuve | **TV** | `{state, label, endsAt, degraded, trial, kid, proofSeq}` + **preuve `TvProof` W6 à la demande** (`PULL act` avec nonce dans l'entrée) | NOTIFY TV→tél. ; le téléphone re-tire la preuve (10 min de validité) | < 3 s | le téléphone garde la dernière preuve (cache 14 j W6) |
| 2 `lots` | lots, locations | **TV** pour l'état installé (`TvManifest`), **téléphone** pour la file | `{id, version, sha, edition, rentalEnd, rejected}` | NOTIFY TV ⇒ `DeliveryQueue.reconcile` (existant) immédiat ; le téléphone pousse par `BULK` (lots) | < 3 s (fin de l'attente de `GET /api/lots`) | file persistée (existant) |
| 3 `lib` | index de bibliothèque | **TV** | `{name, size, mtime, folder, kind, thumb?}` par fichier ; snapshot ≤ 2 000 entrées, delta par événements `add/remove/rename` | NOTIFY | < 3 s | cache téléphone (`files/link/lib-<tv>.json`) : navigation hors ligne en lecture |
| 4 `xfer` | transferts | **TV** (grand livre : reçu/total/voie/état) ; **téléphone** (file d'envoi) | `{id, name, total, done, lane, state, err}` | NOTIFY des deux côtés | < 1 s (progression 1 Hz) | grand livre TV persisté (`files/link/xfer.json`), file téléphone persistée |
| 5 `par` | rapports parentaux | **TV** (outbox) | `{reportId}` seulement : le corps reste CBTP/`holder/pull` (W6) | NOTIFY ⇒ le téléphone tire aussitôt (plus d'attente de 15 min) | < 5 s | existant (outbox 14 j) |
| 6 `shop` | jetons, bons, commandes (W5) | **TV** pour `tokens/report`, **téléphone** pour `tokens/install`, bons | `{tokensSeq, walletState, pendingVouchers}` | NOTIFY | < 5 s | existant W5 |
| 7 `set` | réglages, profil, nom | **TV** (`TvPrefs` visibles), **téléphone** (réglages de liaison) | `{name, wdEnabled, autostart, lang, quota}` | NOTIFY | < 3 s | — |
| 8 `ico` | icônes d'état (`StatusIconModel`) | **TV** | `{kind, tech, label, until}` | NOTIFY coalescé 1 s | < 2 s | — |

**Règles** : (1) `seq` par domaine et par détenteur, strictement croissant, persisté ; (2) `digest` = 8 premiers octets de SHA-256 de l'état canonique (tri des entrées) ; digests égaux ⇒ rien à faire ; (3) `DELTA` depuis `sinceSeq` ; si le détenteur n'a plus l'historique (`DeltaLog` borné à 500 entrées ou 7 jours) ⇒ `snapshot` ; (4) **application idempotente** : chaque entrée porte une clé (`id`/`name`) et un `seq` ; appliquer deux fois = même état ; (5) **conflits** : un domaine a **un** détenteur par champ ; les deux côtés n'écrivent jamais le même champ (`xfer` : la TV détient `done/state`, le téléphone `queue`) ; en cas d'écriture concurrente malgré tout, **le détenteur gagne** et l'autre re-tire ; (6) **budgets** : Bluetooth ≤ 8 Kio/s pour la synchro en moyenne (lissage), `lib` snapshot différé si > 64 Kio tant qu'une route Wi-Fi est attendue ; LAN sans cap ; (7) **priorités** : `act` > `xfer` > `lots` > `par` > `shop` > `ico` > `lib` > `set`.

### 7.3 Réponse à « à quelle vitesse le téléphone remarque-t-il ? »

| Événement sur la TV | Lié (LAN ou BT) | Non lié |
|---|---|---|
| Clé acceptée / essai terminé / mode réduit | < 3 s (NOTIFY `act`) puis preuve re-tirée | au prochain contact (ouverture de l'app, job 15 min, diffusion ACL) : HELLO porte `act=<digest>` ⇒ PULL immédiat |
| Lot installé / refusé | < 3 s | idem |
| Transfert reçu / interrompu | < 1 s | idem |
| Rapport parental prêt | < 5 s | 15 min (job existant) |
| Icône (téléphone connecté, USB) | < 2 s | — |

## 8. D — Expérience utilisateur (textes français, source unique `LinkTexts`)

### 8.1 Première liaison (3 touches, Bluetooth)

| Étape | Téléphone | TV |
|---|---|---|
| 0 | Installation. Onglet « CastBridge TV » (désormais **onglet par défaut** quand aucune TV n'est liée : D-W7-7, aujourd'hui l'onglet 0 est DLNA `S/MainActivity.kt:131`) : « **Trouvons votre TV** · Allumez la TV et ouvrez CastBridge-TV. » Bouton **[Ajouter ma TV]** (touche 1) | Première ouverture : accueil avec bandeau « **Prêt à être associé** · Sur votre téléphone, ouvrez CastBridge et touchez « Ajouter ma TV » » (fenêtre auto-ouverte 10 min, § 4.2) |
| 1 | Si Android ≥ 12 : dialogue système « Appareils à proximité » précédé d'une ligne « CastBridge a besoin du Bluetooth pour trouver votre TV, jamais pour vous localiser » ; puis liste « TV trouvées » : la TV CastBridge-TV apparaît en premier (SDP/mDNS) → **[Lier]** (touche 2) | — |
| 2 | Dialogue Android « Associer à CastBridge TV ? Code 123456 » → **[Associer]** (touche 3) | même dialogue → **[Associer]** à la télécommande |
| 3 | « **Validez sur la TV** · La TV demande « Autoriser ce téléphone ? ». Choisissez Autoriser. » | « **Autoriser « Galaxy de Papa » à piloter cette TV ?** [Refuser] [Autoriser] » (Refuser présélectionné) → Autoriser |
| 4 | (facultatif, CDM) « Associer CastBridge-TV à CastBridge ? » **[Autoriser]** — expliqué : « pour que la connexion reprenne toute seule même quand l'écran est éteint » | — |
| 5 | « **Salon est liée ✓** · Elle se connectera toute seule, sans code. » son court + animation de 600 ms ; synchro initiale : « Synchronisation… 3/8 » (barre) | icône téléphone dans la barre d'état ; ligne « Téléphone connecté : Galaxy de Papa » (existant) |

Variante **sans Bluetooth** (TV ou téléphone sans BT, Bluetooth cassé) : « Ma TV n'apparaît pas » → « **Scanner le code sur la TV** » (TV : tuile « Ajouter un téléphone » → QR) → liaison LAN/WD, SAS implicite → Autoriser sur la TV. Variante **réinstallation du téléphone** : la TV apparaît avec « connue par cette TV ? » → « Ré-adopter » → code foyer (si D-W7-5) → TV « Est-ce bien votre téléphone ? Code 47 12 » → Autoriser.

### 8.2 Indicateur permanent et messages d'échec

- **Puce** en haut de l'onglet CastBridge TV et dans la barre de titre des écrans qui parlent à la TV : `● Salon · Wi-Fi` / `● Salon · Bluetooth (réduit)` / `◐ Reconnexion…` / `○ Hors de portée` / `✕ Action requise` ; toucher ⇒ écran « Connexion ».
- **Un seul bouton « Réparer la connexion »** (quand `✕` ou `○` depuis > 1 min) qui lance `SelfTest` et affiche **une phrase + une action** :

| Diagnostic (ordre) | Phrase | Action unique |
|---|---|---|
| Bluetooth éteint | « Le Bluetooth du téléphone est éteint. » | Activer le Bluetooth |
| Permission manquante / bloquée | « CastBridge n'a pas l'autorisation « Appareils à proximité ». » | Autoriser (ou Ouvrir les réglages si « ne plus demander ») |
| Réseau différent | « Le téléphone est sur « Maison-5G », la TV sur « Maison » : ce ne sont pas les mêmes. Le Bluetooth prend le relais, plus lent. » (SSID lus localement, jamais envoyés) | Ouvrir le Wi-Fi |
| Isolation des clients | § 4.3 | Utiliser Wi-Fi Direct |
| TV en veille / éteinte | « Salon ne répond ni en Wi-Fi ni en Bluetooth : elle est probablement éteinte ou en veille. » | Réessayer |
| CastBridge-TV fermée (SDP absent, mDNS absent, IP répond mais `/api/hello` non) | « La TV répond mais CastBridge-TV n'est pas ouvert. Ouvrez-le sur la TV. » | Réessayer |
| Heure | « L'heure du téléphone (ou de la TV) a sauté de plus de 45 jours : vérifiez-la. » | Vérifier l'heure |
| Identité changée | « Salon ne signe plus avec la même clé (réinstallée ?). Comparez l'empreinte 7f3a-91c2 avec « À propos » de la TV. » | Confirmer la TV |
| TV réinitialisée / téléphone retiré / refusé | textes existants `LinkText.untrustedAdvice`/`refused` | Ré-adopter / Réassocier |
| Tueur de batterie (≥ 2 réveils manqués) | « Votre téléphone (Tecno) coupe CastBridge en arrière-plan : autorisez-le à tourner. » | Ouvrir le réglage (lien OEM) |
| Tout va bien | « La connexion fonctionne (Wi-Fi, 24 ms). » | Fermer |

### 8.3 Chorégraphie des permissions (Android 12-14, téléphone) et TV

- **Juste à temps, une fois par processus** : `BLUETOOTH_CONNECT` (+ `SCAN` uniquement dans « Ajouter ma TV ») au premier toucher de « Ajouter ma TV » ; `POST_NOTIFICATIONS` (33+) **après** la première liaison réussie (« pour vous prévenir quand la TV est activée ou qu'un transfert reprend »), plus au démarrage de l'onglet DLNA (`S/MainActivity.kt:201-202`, à retirer) ; `NEARBY_WIFI_DEVICES` (33+) seulement avant Wi-Fi Direct ; **jamais** de localisation (le `ACCESS_FINE_LOCATION` d'`ownerlib` ≤ 30 reste pour le balayage classique < 31 dans l'outil propriétaire seulement).
- **« Ne plus demander »** : détecté (`shouldShowRequestPermissionRationale` faux après refus) ⇒ carte explicative + bouton « Ouvrir les réglages de l'app » (`ACTION_APPLICATION_DETAILS_SETTINGS`) ; existant dans `rememberBtPermission` (`S/BtPermission.kt:37-65`), généralisé.
- **TV** (`TvPermissions`, w7-12) : `BLUETOOTH_CONNECT`+`ADVERTISE` (31+) et `POST_NOTIFICATIONS` (33+) demandées **une fois par processus** depuis l'activité visible (accueil ou activation), écran d'explication D-pad « CastBridge-TV utilise le Bluetooth pour que votre téléphone la trouve » avec [Autoriser] [Plus tard] ; `ACTION_REQUEST_DISCOVERABLE` selon § 4.2 seulement ; `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS`, `MANAGE_UNKNOWN_APP_SOURCES` **jamais** en chaîne : chacune depuis son écran, sur action.
- **Accessibilité D-pad** (TV) : tous les dialogues W7 ont un focus initial sur le bouton sûr (Refuser/Plus tard), ≥ 48 dp, anneau de focus (`PairActivity.kt:88-99`), lecture à 3 m (≥ 24 sp), code SAS en 40 sp.

## 9. E — Diagnostic et observabilité

- **`LinkJournal`** (`C/link/LinkJournal.kt`, les deux apps) : anneau de **500** événements `{t, kind, route, code, ms, note}` persisté (`files/link/journal.bin`, ≤ 64 Kio, écriture ≤ 1/s), **sans secret** (`Redact.scrub` existant + liste noire de `TELEMETRY.md:55-58` : ip entière, mac, ssid, token, pin) ; kinds : `discover.*`, `route.up/down`, `hs.ok/fail`, `sync.<dom>.delta/notify/conflict`, `xfer.*`, `job.run/missed`, `perm.*`, `self.test`. Export : « Copier le rapport » / « Partager » (texte) sur les deux appareils ; sur la TV aussi `GET /api/link/journal` (PIN ou jeton).
- **Écran « Connexion »** (téléphone `S/link/ConnectionScreen.kt`, TV page dans « Connexion & réglages » + `R/LinkDiagActivity.kt`) : état, routes avec latence, identité de la TV (empreinte), âge par domaine (« activation : à jour », « bibliothèque : il y a 2 min », « rapports : 3 en attente »), CDM/tueur de batterie, derniers événements, boutons « Réparer la connexion », « Copier le rapport », « Ré-adopter / Réassocier », « Oublier ».
- **`SelfTest`** : fonction pure `SelfTest.run(obs) → Verdict(sentence, action)` sur les observations (§ 8.2), testée exhaustivement (chaque verdict a un cas).
- **Télémétrie (opt-in existant)** : événements agrégés **sans identifiant** : `link_time_to_link {route, ms_bucket}`, `link_reconnect {ms_bucket}`, `link_fail {reason}` (raison = code du `SelfTest`, jamais de SSID/IP), `sync_latency {dom, ms_bucket}` ; conformes aux clés interdites.

### 9.1 Plan de mesure terrain (TV de référence GaiaOS 32 bits + S21+/Android 15 + un Tecno/Infinix Android 11-13)

| Mesure | Comment | Seuil de réussite |
|---|---|---|
| Temps de première liaison (3 touches) | chronomètre + `adb logcat -s LinkRuntime` sur le téléphone, ligne `link.first_linked ms=` ; TV : `adb -s <tv> logcat -s TvBeacon,LinkHost` | ≤ 45 s de « Ajouter ma TV » à « liée » ; 0 saisie de code hors comparaison Android |
| Reconnexion après redémarrage de la TV | `adb -s <tv> reboot` ; téléphone en veille écran éteint ; mesurer `route.up` dans le journal | ≤ 60 s après que l'accueil TV est affiché (BT) ; ≤ 30 s si LAN |
| Reconnexion après changement d'IP | sur la box : réserver une autre IP, ou `adb -s <tv> shell svc wifi disable && svc wifi enable` | ≤ 20 s, zéro écran ; journal : `discover.hello_ips` puis `route.up lan` |
| Coupure de courant TV (débrancher 30 s) | journal des deux côtés | TV : `TvBeacon` up ≤ 20 s après l'accueil ; téléphone : `OFFLINE → LINKED_*` sans action |
| Isolation des clients | activer l'isolation sur la box de test (ou AP invité) | état `ISOLATED` ≤ 25 s ; WD monté ≤ 15 s (Android ≥ 10) ; message exact |
| Latence de synchro `act` | poser une clé par la TV (`curl -H "X-CB-Pin: $PIN" -X POST http://TV:8765/api/activation/install --data-binary @activation`) ; chronométrer la notification téléphone | ≤ 3 s lié LAN, ≤ 5 s lié BT |
| Latence `xfer` | `curl` d'un fichier de 20 Mo sur `/api/upload` ; observer la carte du téléphone | progression ≤ 1 s de retard ; après `adb -s <tv> shell am force-stop castbridge.receiver` en plein transfert, l'entrée reste visible « interrompu, reprise possible » et reprend |
| Batterie / h (téléphone) | `adb shell dumpsys batterystats --reset`, 2 h en arrière-plan lié BT, `adb shell dumpsys batterystats --charged castbridge.sender` | ≤ 1 %/h en `LINKED_BT` au repos ; ≤ 0,3 %/h `OFFLINE` ; 0 wakelock > 1 min |
| Jobs manqués (tueur OEM) | `adb shell dumpsys jobscheduler \| grep -A5 castbridge.sender` ; journal `job.missed` | ≤ 1 manqué/24 h après réglage OEM ; carte OEM affichée sinon |
| Histogramme des échecs | `adb shell run-as castbridge.sender cat files/link/journal.txt \| sort \| uniq -c` (export texte) sur 48 h | aucune raison « inconnue » ; chaque raison a sa phrase |
| TV 32 bits : CPU/mémoire du beacon | `adb -s <tv> shell top -n 1 \| grep castbridge` au repos lié | ≤ 1 % CPU, ≤ +3 Mo RSS vs aujourd'hui |
| Débit par voie | banc existant `tools/transfer-bench` + CBT1 20 Mo | LAN ≥ 3 Mo/s, WD ≥ 2 Mo/s, BT 100-250 Ko/s (information, non bloquant) |

Commandes communes : relais Mac → téléphone → TV (mémoire « Relais téléphone → TV » : `nc` sur le téléphone en ADB) ; `adb shell settings put global stay_on_while_plugged_in 3` pendant les tests seulement.

## 10. F — Compatibilité et risques

### 10.1 Matrice

| Sujet | Android 8-9 | 10-11 | 12-13 | 14 | TV 32 bits / GaiaOS | Box/TV sans BT |
|---|---|---|---|---|---|---|
| Bluetooth RFCOMM sécurisé | `BLUETOOTH`/`ADMIN` install | idem | `BLUETOOTH_CONNECT` runtime | idem | `CONNECT`+`ADVERTISE` runtime (≥ 31 seulement : GaiaOS 9-11 = install) | **LAN + QR** (§ 4.1 rang 6) |
| Balayage BT classique (Ajouter ma TV) | localisation **non demandée** : balayage seulement d'appareils appairés + `fetchUuidsWithSdp` ; découverte d'un inconnu via `ACTION_REQUEST_DISCOVERABLE` côté TV + liste système | idem | `SCAN` `neverForLocation` | idem | — | — |
| mDNS/NSD | OK (MulticastLock) | OK | OK | OK (API 34 `NsdManager` moderne, résolution `registerServiceInfoCallback` en option) | OK | OK |
| Wi-Fi Direct client (`WifiNetworkSpecifier`) | **non** (API 29+) ⇒ BT mux | OK | OK (`NEARBY_WIFI_DEVICES` 33+) | OK | groupe créé par la TV (existant) | — |
| CDM | API 26+ (association) | + | + `startObservingDevicePresence` 31+ | + | sans objet | sans objet |
| FGS depuis l'arrière-plan | libre | restreint (10+) ; exemptions ACL/CDM | types obligatoires (34 : déclarés, manifeste `:144-160`) | idem | `connectedDevice` (existant) | — |
| AES-GCM / HKDF / X25519 | `Cipher` AES/GCM OK ; X25519 pur (w4-01) | OK | OK | OK | OK (logiciel, mesuré ≥ 20 Mo/s attendu) | OK |
| IPv6 | sondes IPv4 seulement (`LinkInfo` filtre `isSiteLocalAddress`) ; **inchangé** en v1 (ULA/link-local ignorées ; mDNS peut renvoyer une IPv6 : filtrée) | | | | | |
| VPN sur le téléphone | la sonde LAN peut échouer (trafic routé) ⇒ `SelfTest` : « Un VPN est actif : désactivez-le pour joindre la TV en Wi-Fi ; le Bluetooth fonctionne. » (`NetworkCapabilities.TRANSPORT_VPN`) | | | | | |
| Portail captif / hotspot | téléphone en point d'accès pour la TV : LAN = `192.168.43.x`/`192.168.x` : fonctionne (la TV a une IP du téléphone) ; portail captif : `/api/hello` n'est pas affecté (local) | | | | | |
| Double bande | SSID identiques, bandes différentes : même LAN en général ; SSID différents ⇒ message « réseau différent » (§ 8.2) | | | | | |
| Coupure de courant | TV : `BootReceiver` + job 15 min ; registre `trusted_phones.txt` atomique + `.bak` (existant) ; `DeltaLog`/`TransferLedger` par `SafeFile` | | | | | |

### 10.2 Interactions

- **Protection (protect-01..09)** : la porte de classe d'appareil et l'intégrité ne changent pas ; `TvBeacon` tourne aussi sur une TV « PAS_TV » bloquée ? **Non** : il n'écoute que si la porte laisse passer (sinon seul `…0005` pour l'assistance). Le canal `CBSX` ne remplace pas la preuve `TvProof` (W6) : il la **transporte** ; la preuve reste signée par la clé d'installation Ed25519 (W6) et vérifiée hors ligne.
- **Passerelle d'assistance (REMOTE-TUNNEL-TV)** : inchangée ; le domaine `set` publie l'état « assistance : connectée / hors ligne » pour l'écran du téléphone ; le téléphone-passerelle (`BtGatewayService`) reste manuel/PIN en v1 (W6 `INTERNET_GATEWAY_FOR_TV`), mais `LinkManager` propose « Partager Internet avec la TV » quand `set.net = none` et que le téléphone a Internet (une touche, D-W7-8 : **auto** seulement pour les mises à jour et l'activation, jamais pour le trafic général).
- **Budget TV 10 Mo / 32 bits / 1 Go** : le code W7 (cœur ≈ 60 Ko dex) est hors budget « lots » ; aucun fil actif en repos ; `DeltaLog` ≤ 128 Kio par domaine ; `lib` snapshot ≤ 2 000 entrées.
- **W4** : `InstallKey` partagée (même clé X25519 pour le chiffrement des locations et l'identité `CBSX` : acceptable, usages séparés par `info` HKDF distincts ; W6 garde une clé Ed25519 distincte pour signer). **W5** : domaine `shop` = les lectures de `TvShopCache` (w5-12) deviennent des entrées poussées. **W6** : `PhoneSync` (w6-02) est **nourri** par le domaine `act` (plus de sondage) ; `ProofSync` (w6-16) demande la preuve via `PULL act` quand lié, ou par `…0005`/`/api/activation/proof` sinon (inchangé).

### 10.3 Risques

| Risque | Mitigation |
|---|---|
| R1 — W4 non fusionné (pas de `X25519.kt`, `InstallKey`) | w7-03 porte sa propre `X25519` **si** absente (fichier `C/link/X25519Lite.kt`, vecteurs RFC 7748) ; `InstallKey` repli fichier ; à réconcilier à la fusion de W4 (un seul fichier gagne) |
| R2 — Durées Bluetooth réelles (connect 2-10 s, libération 1,5 s) non mesurées | **BLOQUÉ B2** : w7-24 mesure d'abord sur la vraie TV ; les constantes sont dans `LinkConfig` (injectables) |
| R3 — CDM ignoré par certains OEM | le job 15 min reste ; carte OEM ; mesure w7-24 |
| R4 — Le mux `…0004` et `…0006` ouvrent 2 liaisons RFCOMM par téléphone | les deux partagent `BtConnectLock` ; `…0006` est ouvert d'abord ; `…0004` seulement à la demande (HTTP par BT) ; cible v2 : porter HTTP dans `CBSY` BULK (hors W7) |
| R5 — Régression des écrans existants qui lisent `TvLinkManager.state` | API publique conservée (façade) ; tests de source (grep) ; campagne w7-24 |
| R6 — Dialogue CDM perçu comme intrusif | optionnel, expliqué, une seule fois ; refus = rien ne casse |
| R7 — mDNS absent sur certaines box (multicast filtré) | rang 2 (HELLO) et rang 1 (cache) suffisent ; QR en secours |

## 11. Séquences (résumé ; détail dans les cahiers)

1. **Première liaison** : § 8.1 ; en fin : `CBSX` sur `…0006` → `HELLO` des deux côtés → `PULL` de chaque domaine (snapshots `act`, `lots`, `xfer`, `set`, `ico`, puis `lib` si LAN) → CDM (option) → `LINKED_*`.
2. **Reconnexion quotidienne** : ouverture de l'app ou diffusion ACL ⇒ `DISCOVERING` ⇒ cache IP (2,5 s) ∥ HELLO BT (6 s) ⇒ `CBSX` (session nouvelle, < 2 s) ⇒ `HELLO` avec digests ⇒ seuls les domaines dont le digest diffère sont tirés (en général 0-1) ⇒ `LINKED_*` en < 10 s.
3. **Redémarrage de la TV** : `BootReceiver` ⇒ `TvBeacon` (≤ 20 s après l'accueil) ⇒ mDNS v2 ⇒ le téléphone (job/ACL/mDNS) refait 2 ; `seq` et `DeltaLog` persistés ⇒ deltas seulement.
4. **Réinstallation du téléphone** : aucune TV enregistrée ⇒ mDNS v2 / SDP montrent « Salon (connue ?) » ⇒ « Ré-adopter » ⇒ `CBSX` + `READOPT` (+ HMAC code foyer) ⇒ TV : dialogue SAS ⇒ Autoriser ⇒ registre : nouvelle clé liée à l'adresse ⇒ jeton ⇒ synchro complète (snapshots).
5. **Réinstallation de la TV** : nouvelle `InstallKey` + registre vide ⇒ fenêtre auto-ouverte ⇒ téléphone : `IDENTITY_CHANGED` ⇒ « Confirmer la TV » (empreinte) ⇒ HELLO `requestTrust` ⇒ Autoriser ⇒ synchro : le téléphone **repousse** ce qu'il détient (lots en file, activation gardée en copie : `CARRY_ACTIVATION_FOR_TV` W6) ; les domaines TV repartent de `seq` 0 (le téléphone détecte `installId` ≠ et jette ses caches TV).
6. **Changement de box/IP** : LAN mort ⇒ `DEGRADED` (BT) ⇒ HELLO rapporte les nouvelles IP ⇒ sonde ⇒ `LINKED_BOTH` ; sans BT : mDNS v2 en fond (4 s toutes les 60 s au premier plan) ⇒ `LINKED_LAN`.
7. **Renouvellement de jeton** : inchangé (mi-vie, `LinkDriver.kt:161`) ; `CBSX` n'expire pas (session liée au socket) ; une session LAN inactive 10 min est fermée côté TV.
8. **Multi-TV** : TV cible = liaison active ; les autres : mDNS passif ; changer de TV cible ⇒ `LinkManager` de l'autre TV passe actif (séquence 2).
9. **Chemins d'erreur** : chaque issue de `SelfTest` (§ 8.2) ; refus propriétaire ⇒ `Retry.Never` (existant) ; version de protocole inconnue ⇒ « synchro simple » ; trame AEAD invalide ⇒ session fermée + journal + reprise (1 fois) puis message.

## 12. Changements par rapport aux conceptions antérieures

1. `docs/BT-PLUG-AND-PLAY.md` : « identité = adresse Bluetooth » → **adresse ou clé** ; « jeton en clair sur le LAN » (`:130`) → synchro chiffrée `CBSX` (le reste du HTTP inchangé) ; « fenêtre ouverte par le propriétaire seulement » → **auto-ouverte sans téléphone de confiance** et sur **toc** ; le document est remplacé par `docs/PLUG-AND-PLAY-SYNC.md` (w7-23), l'ancien restant comme historique.
2. **W6** : `PhoneSync`/`ProofSync` ne sondent plus : ils consomment le domaine `act` ; `GET /api/activation/proof?nonce=` reste pour une TV sans `sync`. `TvProof` inchangé. D-W6 inchangées.
3. **W5** : `TvShopCache` (w5-12) lit via le domaine `shop` quand il existe ; routes inchangées.
4. **W4** : `InstallKey` sert aussi d'identité `CBSX` ; `DEVICE_INFO` inchangé.
5. `sonnet-w2-09` (`TvReachability`, « Dépannage ») : **absorbé** par `SelfTest`/« Réparer la connexion » (w7-08, w7-19) ; ne pas l'exécuter tel quel. `sonnet-w2-07` (découpe de `TvService`) : compatible ; w7-12 retire `OwnerBtHost` de `TvService`, w2-07 doit relire. `sonnet-w3-02` (FGS type) : compatible ; `TvBeacon` n'est pas un FGS.
6. `bt-tunnel-keepalive` : inchangé ; `…0004` devient voie de masse HTTP par BT, pas plan de contrôle.
7. `smart-remote`/`bt-remote` : inchangés ; l'étiquette « Wi-Fi » fausse (`C/remote/RemoteClient.kt:109-113`) est corrigée par `RoutePolicy.label` (w7-21).

## 13. Décisions

**Prises par l'architecte (renversables)** : **D-W7-1** pas de BLE ni de CEC en v1 ; **D-W7-2** fenêtre d'association auto-ouverte 10 min sans téléphone de confiance, sinon sur toc/manuel ; **D-W7-3** identité par clés X25519 (TV = InstallKey W4, téléphone = PhoneKey fichier), TOFU + empreinte ; **D-W7-4** message signé `keyring` (portée `REGISTRY`, clé de secours ou bureau) pour ajouter une clé de confiance sans recompiler, **et** `RESULT` structuré ; **D-W7-6** CDM optionnel, demandé une fois ; **D-W7-7** onglet « CastBridge TV » par défaut tant qu'aucune TV n'est liée ; **D-W7-8** partage d'Internet proposé (une touche), automatique seulement pour activation/mise à jour.

**BLOQUÉ (faits propriétaire)** : **D-W7-5** le « code foyer » de la ré-adoption est-il le **code parental** existant (recommandé : oui, un seul code à retenir ; absent ⇒ ré-adoption = parcours normal avec un « Autoriser ») ou un nouveau code ? **B2** mesures Bluetooth sur la vraie TV avant de figer `LinkConfig` (w7-24 d'abord, en parallèle de 7a).

## 14. Effort

| Sous-vague | Contenu | Cahiers | Effort |
|---|---|---|---|
| 7a cœur (JVM) | LinkManager, découverte, routes, CBSX, CBSY, moteur et domaines, identité/ré-adoption, résultat structuré + keyring, journal/auto-test, grand livre, vecteurs Python | w7-01…w7-11 | ≈ 22 j |
| 7b TV | TvBeacon + permissions, SyncHost + routes, association v2 (hello v2, toc, fenêtre auto, QR, SAS), écran Connexion + grand livre | w7-12…w7-15 | ≈ 12 j |
| 7c téléphone | LinkRuntime (+ CDM), découverte, SyncClient + stores, première liaison + permissions + Réparer, écran Connexion, politique de voie + file persistée, résultat d'activation | w7-16…w7-22 | ≈ 18 j |
| 7d docs, tests, CI | PLUG-AND-PLAY-SYNC.md + HANDOFF, campagne terrain + scripts de mesure, CI + table OEM | w7-23…w7-25 | ≈ 5 j |

Total ≈ 57 agent·jours ; modèles : 21 cahiers `sonnet`, 4 `haiku` (w7-11, w7-23, w7-24, w7-25). Index et matrice de propriété : `docs/agent-briefs/SONNET-WAVE7-INDEX.md`.
