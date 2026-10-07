# Conception W8 — transport multivoie bas niveau (Bluetooth + Wi-Fi + USB en même temps)

> Document de conception (Fable, 2026-10-02). **Aucun code n'est modifié par ce document.** L'exécution se fera par les cahiers `docs/agent-briefs/sonnet-w8-NN-*.md` (index : `SONNET-WAVE8-INDEX.md`), sur ordre explicite du coordinateur.
>
> Demande du propriétaire (2026-10-02) : « conçois un dispositif de transmission des données bas niveau, pour un débit maximal exploitant toutes les technos dispo, comme le Bluetooth, Wi-Fi, USB lorsqu'ils sont tous disposés ».
>
> Périmètre : la couche **TRANSPORT en vrac** entre CastBridge (téléphone) et CastBridge-TV : vidéos, lots, packs de contenu, déplacements de bibliothèque. La découverte, l'association, le cycle de vie de la liaison et le protocole de synchronisation appartiennent à **W7** (`DESIGN-W7-PLUG-AND-PLAY-SYNC.md`, **en cours d'écriture par un autre agent, fichier absent au moment de cette conception** : § 7 définit l'interface que W8 attend et la note comme dépendance).

## 0. Règle d'honnêteté (à lire d'abord)

1. **Les radios ne s'additionnent pas comme on l'espère.** Le Wi-Fi du réseau et le Wi-Fi Direct passent par **la même puce radio du téléphone** (et souvent de la TV) : les faire marcher « en même temps » les fait **se partager le temps d'antenne** (souvent sur le même canal) : la somme est au mieux égale au meilleur des deux, souvent inférieure. Le Bluetooth et le Wi-Fi 2,4 GHz partagent **la même antenne** sur la plupart des TV bon marché (coexistence BT/Wi-Fi) : un flux RFCOMM à plein régime peut coûter 10 à 30 % au Wi-Fi 2,4 GHz. L'agrégation n'est donc **pas** « 1 + 1 = 2 » : c'est « démarrer tout de suite, accélérer dès qu'une voie plus rapide est prête, ne jamais s'arrêter quand une voie tombe, et ne jamais être plus lent que la meilleure voie seule » (garde § 4.9).
2. **Le goulot est presque toujours le disque de la TV** (clé exFAT mesurée entre 1,4 et 9,5 Mo/s sur la TV de référence, eMMC interne 10-40 Mo/s), puis la puce Wi-Fi de la TV (souvent un module USB 2,4 GHz 1×1, `UsbHardware.kt:47-51` le dit déjà quand il partage le bus avec la clé). Aucune voie en plus ne dépasse le disque : le moteur **mesure** (`WriteStats`, `PartAssembler.kt:14-26`) et le **dit** (`TransferHost.note`, `TransferHost.kt:90-95`).
3. **Les chiffres de ce document sont des ordres de grandeur** tirés de mesures publiques et des notes du projet (`docs/TRANSFER.md` § 1, `docs/agent-briefs/multipath-transfer.md` § « Réponses du coordinateur »). Les seuls chiffres qui comptent sont ceux du banc (`tools/transfer-bench`) **sur la vraie TV** : aucune mesure réelle n'existe encore (`docs/HANDOFF.md:39`). Les cahiers prévoient la mesure **avant** tout investissement coûteux (USB AOA, Wi-Fi Direct).
4. **Ce qui existe déjà est bon et reste** : le moteur `core/xfer` (manifeste, blocs, carte de blocs persistante, assembleur en place, vol de travail, contre-pression) est le socle. W8 **l'étend** (voies dynamiques, santé des voies, chiffrement, voie Bluetooth dédiée, pilotage « instantané puis accéléré », diagnostic) et ne le réécrit pas.

## 1. État des lieux vérifié dans le code (branche `integration/agents`)

| Élément | Fichier : lignes | Constat |
|---|---|---|
| Manifeste, blocs 1-8 Mio, tranches 256 Kio, carte de blocs hex, compression par extension | `core/xfer/Blocks.kt:11-43, 52-78, 81-91` | OK. `Manifest.id` = SHA-256(nom, taille, taille de bloc) tronqué : reprise par identité de fichier ; aucune information secrète |
| Interface `Lane` (`slow`, `maxWorkers`, `allowedWorkers`, `send`, `sent`) ; `UsbLane` vide ; `LaneSwitches.wifiDirect = false` | `core/xfer/Lane.kt:66-85` | Pas de santé de voie, pas d'ajout/retrait à chaud, pas d'identifiant numérique de voie |
| `KController` (K = 1..8 connexions, +8 % pour monter, repli sur « occupé ») ; `WifiLane` (HTTP/1.1 persistant, `transferTo` zéro-copie, gzip conditionnel) ; `WifiDirectLane` ; `BluetoothLane` (HTTP par tranches via un `SocketChannel` fourni) | `core/xfer/Lanes.kt:13-34, 57-137, 140-143, 149-188` | La voie Bluetooth parle **HTTP** à travers le tunnel (mux v2) : jamais branchée dans l'app (`docs/HANDOFF.md:39`) |
| `Scheduler` : vol de travail, voie lente par la fin, doublon des derniers blocs, mise à l'écart 1,5 s ×2 ≤ 30 s après 3 échecs, 4 refus = bloc corrompu fatal | `core/xfer/Scheduler.kt:12-23, 96-114, 116-159` | **Liste de voies figée au constructeur** (`lanes: List<Lane>`, `run()` crée les threads une fois : `Scheduler.kt:60-62`) : c'est la limite principale à lever |
| `PartAssembler` : fichier préalloué `.cbx/<id>.data`, écriture positionnée `FileChannel`, SHA-256 au fil de l'eau, `.state` (carte + empreintes) persisté au plus toutes les 1 s, relecture complète à `finish`, renommage atomique | `core/xfer/PartAssembler.kt:36-41, 76-106, 109-138, 144-170, 198-205, 222-231` | Sûr après coupure de courant (la relecture rattrape un bloc marqué mais non écrit) ; **pas de fsync avant la persistance de l'état** (coût : renvoi de ≤ 1 s de blocs) |
| `TransferHost` : 3 sessions, `maxStreams = maxHttpThreads − 2` = 6, contre-pression `maxInflight = writeBps × 3 s` borné à 2..6 blocs, balayage des orphelins | `core/xfer/TransferHost.kt:19-23, 64-73, 78-83` ; `ReceiverServer.kt:103, 656-657` | OK. 429 `busy` + `retryMs` |
| Routes `/api/transfer/{caps,begin,chunk,state,finish,abort}` ; `Connection: close` sur erreur ; dédoublonnage « même nom + même taille = déjà là » | `ReceiverServer.kt:294-296, 592-708` ; `TransferHost.kt:48` (`finalSizeOf(m.name) == m.size`) | Même garde d'essai que tout : `routeGuard` → 403 `{"error":…,"trial":true}` (`ReceiverServer.kt:279-280`, `TrialPolicy.kt:31-45`) |
| `TransferClient` : caps → begin → state(hashes) → ordonnanceur → finish, 6 tours au plus, sondage `state` toutes les 2 s pour `writeBps`/`note` | `core/xfer/TransferClient.kt:120-160` | OK |
| `HttpConn` : TCP_NODELAY, SO_SNDBUF 1 Mio, chien de garde 20 s (60 s Bluetooth) | `core/xfer/HttpConn.kt:29-37, 84-94` | OK |
| Banc `TransferBench` (réseau seul / réseau + disque, `--simulate`) | `core/xfer/TransferBench.kt` ; `tools/transfer-bench/run.sh` | À étendre : voies multiples simulées |
| Branchement téléphone : `UploadService.runFast` = **une** `WifiLane` ; jamais en mode « lire pendant l'envoi » | `sender/UploadService.kt:153-156, 181-210` | Les blocs n'arrivent pas dans l'ordre : W8 § 4.5 règle ce point |
| Envoi classique séquentiel (`PUT /upload/<name>?offset=`, `.part`) et Bluetooth CBT1 (`ResumableBtUpload`) | `core/tv/TvClient.kt:190`, `ReceiverServer.kt:489-530`, `core/tv/BtProtocol.kt:22-29, 286-372` | Repli pour les TV anciennes ; CBT1 reste pour les lots par Bluetooth (`core/lots/LotPush.kt:169-190`) |
| File du téléphone : un fichier à la fois, Wi-Fi sinon Bluetooth | `core/tv/TransferQueue.kt:12-32` ; `sender/TransferQueue.kt:52-53` | Choix binaire Wi-Fi / Bluetooth : W8 remplace par « toutes les voies » |
| Lots : `HttpLotTransport` (POST 512 Kio par appel, offset confirmé), `Cbt1LotTransport` ; livraison différée si un envoi est en cours | `core/lots/LotPush.kt:101-160, 169-190` ; `sender/LotsRuntime.kt:116-127, 186-205` | Un lot de 500 Mo = 1 000 requêtes HTTP : W8 § 7.3 passe les lots par le moteur en vrac |
| Tunnel Bluetooth « API v2 » : un lien RFCOMM par TV, trames `type u8 | flux u16 | longueur u16 ≤ 16 Kio`, 4 flux, 256 Ko de file par flux, écritures sérialisées | `core/tunnel/Mux.kt:12-25, 82-173` ; `core/tunnel/LinkPool.kt:20, 151` | Un transfert en vrac par ce tunnel **étrangle** les requêtes de l'API (télécommande HTTP, parental) : W8 § 5.3 lui donne son **propre** service RFCOMM |
| Wi-Fi Direct : TV propriétaire de groupe `192.168.49.1`, créé sur CBTN si autorisé ou si la TV n'a aucun réseau | `core/tv/WifiDirect.kt`, `BtProtocol.kt:398-432`, `receiver/TvService.kt:277-291`, `receiver/WifiDirectGroup.kt`, `sender/WifiDirectScreen.kt` | Existe, jamais essayé avec le moteur multivoie |
| Clé USB : import TV (`UsbImport.copyAll`, même nom + taille = ignoré, `.part` repris), analyse `/sys` (vitesse négociée, bus partagé avec le Wi-Fi), FAT32 = 4 Gio − 1 refusé | `core/tv/UsbImport.kt:51-96`, `UsbHardware.kt:57-115`, `Volumes.kt:20-22, 380-417` | Chemin « physique » existant ; pas de manifeste de reprise sur la clé |
| Chiffrement déjà disponible dans `core` : AES/GCM (JCA), HMAC-SHA256, Ed25519 **en Kotlin pur** (vérification) ; **pas de X25519** (prévu par W4, non livré) | `core/owner/LotKeys.kt:12-43`, `core/update/Ed25519.kt:1-12`, `PROTECTION-TV-FABLE § 1` | Base de la dérivation de clés de session (§ 5.4) |
| Identifiants : jeton `cbk_` + 64 hex (256 bits, 12 h), **stocké haché** sur la TV, envoyé en clair dans `X-CB-Token` sur le LAN ; PIN jamais dans une URL | `core/trust/TrustRegistry.kt:153-166`, `Credentials.kt:15-38`, `docs/BT-PLUG-AND-PLAY.md:24-25, 130` | Le jeton est le seul secret partagé réutilisable (§ 5.4) |
| Services premier plan : téléphone `dataSync` (`UploadService.kt:82`), TV `connectedDevice` (`TvService.kt:154`) ; `minSdk 26`, `targetSdk 35` (`sender/build.gradle.kts:14`, `receiver/build.gradle.kts:14`) | — | Pas de type `connectedDevice` côté téléphone : à ajouter pour l'USB (§ 8.5) |
| Garde W6 : `SendGuard.check(target)` **avant d'enfiler** tout envoi de média ; TV refuse `/api/transfer` en essai/mode réduit | `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.7 (ligne 206 : `SEND_FILES_TO_TV` couvre « multi-chemins ») ; cahier w6-17 | W8 ne crée **aucun** chemin qui contourne la garde (§ 7.4) |

Ce qui **n'existe pas** et que W8 apporte : voies dynamiques (ajout/retrait en cours de transfert), santé et statistiques par voie, garde « jamais plus lent que la meilleure voie seule », ordre des blocs pour la lecture pendant l'envoi, voie Bluetooth dédiée au vrac (hors tunnel API), trames binaires pour les flux bruts (Bluetooth, USB accessoire), chiffrement de bout en bout de session, lots par le moteur en vrac, diagnostic par voie, banc à fausses voies, pistes USB mesurées.

## 2. Catalogue des voies (débits réels, contraintes, verdict)

Cibles : TV Android/Google TV/Fire TV/GaiaOS, API 28-34, 32 bits, 1 Go de RAM, eMMC 10-40 Mo/s en écriture, clés exFAT/FAT32 (limite 4 Gio sur FAT32) ; téléphones Android 8-14. Débits « réels » = débit utile (goodput) mesuré ou typique, **pas** le débit radio annoncé.

| Voie | Débit utile réaliste | Disponibilité / détection | Permissions, coût UX | Mise en route | Énergie | Sécurité du lien | Play Services | Fiabilité sur matériel bon marché | **Verdict W8** |
|---|---|---|---|---|---|---|---|---|---|
| **Bluetooth classique RFCOMM** (BR/EDR, 2-3 Mbit/s air) | **0,1-0,25 Mo/s** (mesuré dans ce projet, `docs/TRANSFER.md` § 1) ; 0,3-0,35 au mieux sur de bonnes puces | Déjà en place (appairage, 4 services RFCOMM `BtProtocol.kt:72-78`) ; détection = `LinkDriver` (W7) | Aucune nouvelle : `BLUETOOTH_CONNECT` déjà accordé | 1-3 s (`connect()` ; verrou `BtConnectLock`) | Faible | Lien appairé **chiffré** par Android (clé d'appairage) ; pair identifié par l'adresse du socket | Non | Bonne ; une liaison à la fois par TV (`LinkPool`) ; le mux v2 étrangle les autres flux | **Oui, dès 8a** : voie de **démarrage instantané** et de **dernier recours**. Contribution au débit ≤ 2 % à côté du Wi-Fi ; **mise en veille** (frames vides) dès qu'une voie Wi-Fi dépasse 2 Mo/s (coexistence 2,4 GHz, § 4.9) |
| **L2CAP CoC (BLE, 2M PHY)** (`BluetoothAdapter.listenUsingL2capChannel`, API 29+) | 0,1-0,15 Mo/s sur Android (1,4 Mbit/s air avec 2M PHY, rarement disponible sur les puces BT 4.x des TV) | API 29+ des deux côtés ; TV de référence inconnue | Aucune nouvelle | 1-2 s | Faible | Chiffré (LE Secure Connections) si appairé | Non | Médiocre (piles BLE des TV peu éprouvées) | **Non** : aucun gain sur RFCOMM, deux piles à maintenir. BLE GATT (≤ 50 Ko/s) : **non** |
| **Wi-Fi infrastructure (LAN, TCP)** | 2,4 GHz n 1×1 (le cas des TV bon marché) : **2-7 Mo/s** ; 5 GHz ac des deux côtés : **12-40 Mo/s** ; le flux TCP seul en tire souvent 40-60 % : d'où K connexions (`KController`) | mDNS/IP connues (`TvDiscovery`, `LinkInfo.ips`) ; **isolation client** sur certains routeurs/box = téléphone↔TV impossible (détectée : sonde 4 s qui échoue alors que la TV répond en Bluetooth) ; band steering = adresse qui change (déjà géré par `resolve`) | Aucune | 0,2-1 s si l'adresse est connue ; 2-5 s via mDNS | Moyenne (verrou Wi-Fi haute perf existant `UploadService.kt:284-292`) | **HTTP en clair** (jeton rejouable sur le LAN, `docs/BT-PLUG-AND-PLAY.md:130`) → chiffrement § 5.4 | Non | Bonne ; pertes radio compensées par K | **Oui (voie principale, existe)** : étendre (stats, chiffrement, join/leave) |
| **Wi-Fi Direct** (TV = propriétaire de groupe ; téléphone client via `WifiNetworkSpecifier` API 29+) | Lien direct sans saut par la box : **3-8 Mo/s** sur 2,4 GHz 1×1, 10-30 Mo/s si 5 GHz des deux côtés. **Ne s'ajoute pas** au LAN (même radio téléphone) | Existe (`WifiDirectGroup.kt`, CBTN) ; le téléphone **perd son Internet Wi-Fi** le temps du transfert sauf concurrence STA+STA (API 31, rare) ; la TV garde son réseau si GO et STA concourants (fréquent, pas garanti) | `NEARBY_WIFI_DEVICES`/localisation déjà déclarées ; boîte système sur le téléphone à la jonction | **8-20 s** (création du groupe ≤ 8 s `TvService.kt:275`, jonction 5-10 s) | Élevée | WPA2 du groupe (mot de passe transmis par Bluetooth appairé) + chiffrement § 5.4 | Non | Moyenne : jamais essayé ici ; les GO des TV bon marché sont capricieux | **Oui, comme ALTERNATIVE au LAN** (pas de box, isolation client, TV sans réseau), **jamais en même temps que le LAN** (§ 4.9). Activée seulement après l'essai matériel du cahier w8-17 |
| **Wi-Fi Aware (NAN)** (`FEATURE_WIFI_AWARE`, API 26+) | Comparable au Wi-Fi Direct quand il existe | Pratiquement **aucune TV** ne l'offre (puce + micrologiciel dédiés ; quelques téléphones haut de gamme seulement) | Localisation/NEARBY | 5-10 s | Élevée | Chiffré (PMK) | Non | Inconnue | **Non** : seulement une ligne « Wi-Fi Aware : non disponible » dans le diagnostic (`hasSystemFeature`), aucune voie |
| **USB accessoire (AOA)** : TV hôte USB, téléphone en mode accessoire, points de terminaison bulk | USB 2.0 : **20-35 Mo/s** réels (au-dessus de tout disque de TV bon marché) ; USB 3 inutile ici | TV : `FEATURE_USB_HOST` + port **libre** (la clé USB occupe souvent le seul port : **BLOQUÉ, question D-W8-1**) ; GaiaOS : pile hôte inconnue. Téléphone : accepte les requêtes vendeur AOA (quasi tous) | TV : boîte « Autoriser CastBridge-TV à accéder à l'appareil USB » (une fois, filtre `device_filter` possible) ; téléphone : boîte « Autoriser … à accéder à l'accessoire ». Un câble à brancher à la TV | 2-4 s après branchement | Nulle (charge en prime) | Lien physique ; chiffrement § 5.4 appliqué quand même (uniformité) | Non | Inconnue : à mesurer | **Peut-être : pointe de recherche de 2 jours d'abord** (w8-12) sur la TV de référence ; **pas une ligne de produit avant la mesure**. Si ça marche : la voie la plus stable et la seule qui ne dépend d'aucune radio |
| **Partage de connexion USB (RNDIS/NCM)** : téléphone = carte Ethernet USB, TV = hôte | Même ordre : 20-30 Mo/s (IP sur USB 2.0) | TV : pilote `rndis_host`/`cdc_ncm` **et** que le framework monte `usb0` comme Ethernet (les boxes Android TV avec dongle Ethernet USB le font ; GaiaOS : inconnu) ; téléphone : l'utilisateur **active lui-même** « Partage de connexion USB » (aucune API publique) | Réglage système manuel sur le téléphone ; rien sur la TV | 3-6 s (DHCP) | Nulle | Lien physique + § 5.4 | Non | Inconnue | **Oui si le pointage le confirme, car ZÉRO nouveau code de transport** : un lien IP apparaît, la voie LAN existante marche dessus (`/api/net` de la TV montre l'interface). Pointe de 0,5 jour dans w8-12 ; diagnostic « lien USB détecté » |
| **ADB / MTP / endpoints bulk « façon adb »** | — | adb exige un client adb côté TV et le débogage USB du téléphone ; MTP exige un initiateur MTP sur la TV (absent) | Inacceptable pour un utilisateur | — | — | — | — | — | **Non** |
| **Clé USB physique** (téléphone OTG → clé → TV) | Écriture de la clé sur le téléphone 10-30 Mo/s, puis lecture sur la TV 10-30 Mo/s, plus **le trajet** | Existe (`UsbImporter`, déplacement de bibliothèque, règle « même nom + taille = déjà là ») | Aucune | Manuel | Nulle | Clé en clair (sauf lots déjà scellés par TV, W4) | Non | Bonne | **Déjà là, hors agrégation** (ce n'est pas une voie temps réel). W8 ajoute en option (w8-21) un manifeste `.cbx` sur la clé pour **finir par le réseau ce que la clé a commencé** (et inversement) |

**Sous-ensemble à implémenter d'abord (8a-8c)** : Wi-Fi LAN (existe) + Bluetooth RFCOMM dédié (démarrage instantané, repli) + Wi-Fi Direct **alternatif** après essai ; lien IP sur USB (tethering) **si** la pointe réussit, sans code de transport propre. **Ne pas perdre de temps** : L2CAP/BLE, Wi-Fi Aware, ADB/MTP, Nearby Connections (Play Services absent des TV GaiaOS/Fire). **Décider après mesure** : USB AOA.

## 3. Architecture

```
TÉLÉPHONE (CastBridge)                                                 TV (CastBridge-TV)
┌───────────────────────────────┐                                      ┌─────────────────────────────────┐
│ UploadService / LotDelivery / │  BulkTransfer.send(...)              │ ReceiverServer (NanoHTTPD)       │
│ MoveToTv  (W6 SendGuard ✔)   │────────────┐                         │  /api/transfer/* v1 (inchangé)   │
└───────────────────────────────┘            ▼                         │  /api/transfer/{session,lanes}   │
┌───────────────────────────────────────────────────────────────┐      │  chunk v2 (corps CBX chiffré)    │
│ BulkTransfer (core/xfer/BulkTransfer.kt)  façade + état        │      └───────────┬─────────────────────┘
│  ├ TransferClient (existant, étendu)                           │                  │
│  ├ Scheduler (existant) + LaneSet dynamique + LaneHealth       │      ┌───────────▼─────────────────────┐
│  ├ LaneBringup : « instantané puis accéléré » (§ 6.2)          │      │ TransferHost (existant, v2)      │
│  └ SessionKeys (HKDF, AEAD records)                            │      │  sessions, voies, stats, quotas  │
└──────┬────────────┬───────────────┬───────────────┬───────────┘      │  PartAssembler (existant)        │
       │            │               │               │                  └───────────▲─────────────────────┘
 BulkBtLane     WifiLane       WifiDirectLane    (UsbIpLane =               ▲       │
 CBX sur RFCOMM HTTP K=1..8    HTTP K=1..4        WifiLane sur usb0)         │  BulkStreamServer (CBX)
 service …0005  LAN            192.168.49.1       si tethering                │  BtServer : service …0005
       │            │               │               │                        │
       └────────────┴───────────────┴───────────────┴────────────────────────┘
                      LinkSnapshot fourni par W7 (adresses, jeton, état des liens)
```

Principes :
- **Un seul moteur**, plusieurs **encapsulations** : HTTP/1.1 persistant pour tout ce qui est IP (LAN, Wi-Fi Direct, USB tethering), trames binaires **CBX** pour tout ce qui est un flux d'octets brut (RFCOMM dédié, USB accessoire). Les deux aboutissent au **même** `TransferHost`/`PartAssembler` sur la TV.
- **Une voie = un lecteur + des ouvriers bornés** (threads dédiés, files bornées : § 5.7). Aucune voie ne détient un bloc « en otage » : doublon de fin (existant), unité adaptative (§ 4.3).
- **La TV commande le rythme** (`writeBps`, `queued`, 429) ; le téléphone commande la **composition** (quelles voies, combien d'ouvriers).
- **Tout est additif et négocié** (`caps.version` 1 → 2) : une TV v1 reçoit exactement ce qu'elle reçoit aujourd'hui ; un téléphone ancien ne voit rien de nouveau.

## 4. Ordonnancement et agrégation

### 4.1 Modèle « pull » (existant, conservé)
Chaque ouvrier de chaque voie **demande** le prochain bloc dès qu'il est libre (`Scheduler.take`, `Scheduler.kt:96-106`) : la voie rapide en fait mécaniquement plus ; aucune tête de file ne bloque. Conservé tel quel.

### 4.2 Voies dynamiques (`LaneSet`) — nouveau
```
class LaneSet { add(lane) ; remove(id, drain=true) ; snapshot() ; onChange(listener) }
Scheduler.run() :
  for lane in laneSet.snapshot(): spawn workers(lane)
  laneSet.onChange { added -> spawn workers(added) ; removed -> mark stop ; its in-flight blocks are requeued at the FRONT }
  loop until done/failed/cancelled : wait(100 ms)
```
- `add` pendant un transfert : les ouvriers démarrent, prennent dans la même file : « accélérer progressivement ».
- `remove(drain=true)` : l'ouvrier finit le bloc en cours (≤ unité adaptative) puis s'arrête ; `drain=false` (voie morte) : `abort` immédiat, blocs renvoyés devant.
- Chaque voie reçoit un **identifiant numérique de session** attribué par la TV (`/api/transfer/lanes`, § 6.1) : indispensable aux nonces (§ 5.4).

### 4.3 Unité adaptative par voie — nouveau
Le bloc (1-8 Mio, unité de **vérification et de reprise**) reste fixe par manifeste ; l'**unité d'envoi** d'une voie est `u = clamp(256 Kio, blockSize, rate_ewma × 2 s)` arrondie au multiple de 256 Kio : une voie à 200 Ko/s envoie des tranches de 256 Kio (reprise au milieu d'un bloc : `PartAssembler.writeSlice`, existant), une voie à 10 Mo/s envoie des blocs entiers. Généralise `slow` (`Lane.kt:70`) : plus de booléen, une unité.

### 4.4 Estimation par voie (EWMA) et santé — nouveau (`LaneHealth`)
Par voie : `bps_ewma` (α = 0,2 par fenêtre de 1,5 s, comme `WifiLane.sample`), `rtt_ewma` (délai entre fin d'envoi du corps et réponse), `err_streak`, `busy_count`, `bytes_ok`, `bytes_wasted` (doublons perdus, blocs abandonnés). **Score** = `bps_ewma × (1 − 0,25 × min(err_streak, 3)) `; une voie à `score < 5 % du total` pendant 20 s et `> 2 voies actives` est **mise en veille** (connexion gardée, pas de bloc) ; **réadmise** par sondage (1 bloc toutes les 30 s puis ×2 ≤ 5 min). Mise à l'écart sur erreurs : règle existante (3 échecs, 1,5 s ×2 ≤ 30 s, `Scheduler.kt:146-153`).

### 4.5 Ordre des blocs et lecture pendant l'envoi — nouveau (`OrderPolicy`)
- Mode **vrac** (défaut) : file = blocs manquants dans l'ordre, voies rapides par le début, voies lentes par la fin (existant).
- Mode **progressif** (`progressive = true`) : la file est **ordonnée par position**, **toutes** les voies prennent par le début (tête = position de lecture + avance), les voies lentes prennent à `playhead + lead + 32 Mio` (loin devant, jamais derrière la lecture). Les premiers 2 Mio (`Progressive.MIN_BOOTSTRAP`) et, pour un MP4 *faststart*, l'atome `moov` (position lue par `Mp4Atoms.layout`, `Progressive.kt:147-176`) sont **prioritaires absolus** (envoyés par la voie la plus rapide, dupliqués sur la seconde si > 1 voie). Un MP4 à `moov` en fin : comme aujourd'hui, pas de progressif (`UploadService.kt:112-120`) ; **pas de réécriture faststart sur le téléphone en W8** (coût CPU/batterie et stockage temporaire d'un fichier entier : décision D-W8-3).
- La lecture progressive côté TV lit `.cbx/<id>.data` **par position** : `GrowingStream` (`Progressive.kt:34-91`) attend aujourd'hui `name.part` qui grossit ; W8 ajoute un lecteur **par carte de blocs** (`SparseGrowingStream` : bloque jusqu'à ce que `map.has(bloc(pos))`). La TV publie la position **contiguë** disponible (`contiguous` dans `state`) pour `Progressive.reachableMs`.

### 4.6 Priorités et équité
- **Trames de contrôle avant le vrac** : sur IP, le contrôle (`begin/state/finish`, `/api/info`, télécommande HTTP) a ses **propres** connexions et la TV réserve 2 threads HTTP (`chunking ≥ maxStreams → 429`, `ReceiverServer.kt:656`) : existant, conservé. Sur Bluetooth, le vrac a **son propre service RFCOMM** (§ 5.3) : la télécommande (CBTR, service …0001) et le tunnel API (…0004) ne partagent plus la liaison avec lui.
- **Entre transferts** : la file du téléphone reste **un fichier à la fois** (`TransferQueueModel`, `TransferQueue.kt:13`), les lots attendent la fin d'un envoi (`LotsRuntime.kt:127`) : la TV n'y gagne rien à deux envois simultanés. Côté TV, `maxSessions = 3` (plusieurs téléphones) : les flux (`maxStreams`) sont **partagés à parts égales** entre sessions actives (`per_session = max(1, maxStreams / sessions_actives)`), annoncé dans `state.maxStreams` (nouveau calcul, même champ).
- **Plafond pour l'usage interactif** : réglage téléphone « Limiter le débit du transfert » (éteint par défaut ; 2, 5, 10 Mo/s) via un seau à jetons dans `Scheduler` ; DSCP AF21 (`setTrafficClass(0x48)`) sur les sockets de vrac, **effet dépendant de la box** (documenté comme « meilleur effort »). La télécommande Bluetooth n'est pas affectée (canal séparé).

### 4.7 Contre-pression et goulot disque (existant, précisé)
- La TV mesure `writeBps` (temps passé dans `write`) et borne les octets en vol à ≈ 3 s de disque (`TransferHost.maxInflight`). Conservé.
- **Nouveau** : le téléphone lit `writeBps` dans **chaque** réponse de bloc (déjà présent dans le JSON `ok`) et plafonne `KController.max` à `ceil(writeBps × 1,3 / bps_par_connexion)` : on n'ouvre pas 8 connexions pour nourrir une clé à 2 Mo/s (moins de contention radio, moins de CPU TV).
- **Nouveau** : `fsync` (`force(false)`) toutes les 32 Mio ou 5 s **avant** la persistance de `.state` : après une coupure de courant, les blocs marqués sont réellement sur le disque, `finish` ne renvoie rien (coût mesuré par le banc ; désactivable si > 10 % de perte sur clé lente).

### 4.8 Pause, reprise, idempotence (existant, complété)
- Reprise par carte de blocs (`BlockMap` + `.state`), identifiant de transfert stable, « `already` » idempotent, reprise au milieu d'un bloc par tranches : existant.
- **Nouveau** : `pause()`/`resume()` explicites sur `BulkTransfer` (les ouvriers finissent leur unité, les connexions sont gardées 60 s puis fermées) ; changement de voie sans perte (§ 4.2) ; une session TV survit à la perte de **toutes** les voies pendant `orphanPartMaxAgeMs` (24 h, 7 j sur clé USB).

### 4.9 Garde « jamais plus lent que la meilleure voie seule » — nouveau
Toutes les 10 s : `best = max(bps_ewma par voie)`, `total = Σ bps_ewma`. Si `total < 0,9 × best` pendant 2 fenêtres : **retirer la voie de plus faible score** (sondage subtractif) ; recommencer. Règles fixes en plus :
1. **Jamais LAN + Wi-Fi Direct en même temps** sur le téléphone (même radio) : si les deux sont joignables, LAN d'abord ; Wi-Fi Direct seulement si LAN absent ou `bps_ewma(LAN) < 1 Mo/s` pendant 20 s, et alors LAN est retiré.
2. **Bluetooth en veille** dès qu'une voie Wi-Fi 2,4 GHz dépasse 2 Mo/s (coexistence d'antenne) ; réveillé si le Wi-Fi tombe sous 0,5 Mo/s ou disparaît. Sur 5 GHz (`WifiInfo.frequency ≥ 5000` côté téléphone ; côté TV inconnu) la veille n'est pas appliquée.
3. Si le disque borne (`writeBps < total × 0,8`), on **n'ajoute** aucune voie.

## 5. Format de fil, intégrité, chiffrement, zéro-copie, threads

### 5.1 Deux encapsulations, un seul contenu
- **IP (LAN, Wi-Fi Direct, USB tethering)** : HTTP/1.1 persistant existant (`PUT /api/transfer/chunk?id&idx[&slice]`), **inchangé en v1**. En v2 chiffré : corps = suite d'**enregistrements CBX** (§ 5.4), en-têtes `X-CB-Lane: <laneId>`, `X-CB-Enc: cbx1`, `X-CB-Sha256` inchangé (empreinte du **clair**).
- **Flux brut (RFCOMM dédié, USB accessoire)** : trames **CBX** ci-dessous, sans HTTP.

Pourquoi ne pas tout passer en binaire ? Sur IP, l'en-tête HTTP d'un bloc de 1 Mio pèse ≈ 300 o (0,03 %) et la TV a déjà toute l'authentification, la garde d'essai et les quotas sur ces routes (`ReceiverServer.kt:279-296`). Un second serveur binaire sur IP n'apporterait rien et doublerait la surface d'attaque.

### 5.2 Trame CBX (flux brut)
Tout en **grand-boutiste**. En-tête fixe de **24 octets** :

| Décalage | Taille | Champ | Valeur |
|---|---|---|---|
| 0 | 4 | magie | `"CBXF"` |
| 4 | 1 | version | `1` |
| 5 | 1 | type | `0x01 HELLO`, `0x02 HELLO_OK`, `0x03 ERR`, `0x10 CHUNK`, `0x11 CHUNK_ACK`, `0x20 STATE_REQ`, `0x21 STATE`, `0x30 PING`, `0x31 PONG`, `0x40 BYE` |
| 6 | 1 | drapeaux | bit0 = chiffré (payload = enregistrements AEAD), bit1 = gzip (clair, jamais pour les médias), bit2 = dernier de l'unité |
| 7 | 1 | voie (`laneId`) | attribué par la TV (1..255) ; 0 avant attribution |
| 8 | 8 | transfert | 8 premiers octets de `Manifest.id` (hex → octets) ; 0 pour HELLO/PING |
| 16 | 4 | bloc (`idx`) | index de bloc ; `0xFFFFFFFF` hors CHUNK |
| 20 | 2 | tranche (`k`) | index de tranche de 256 Kio dans le bloc ; `0xFFFF` = bloc entier |
| 22 | 2 | longueur / 256 | longueur du payload en multiples de 256 o (max 16 Mio) ; pour les types de contrôle : longueur exacte dans les 2 premiers octets du payload |

Payload :
- `CHUNK` : `u32 crc32c(clair)` **si non chiffré**, puis les octets (ou les enregistrements chiffrés § 5.4) ; **le SHA-256 du bloc est envoyé une fois** dans `HELLO`/`STATE_REQ` (liste des empreintes connues) ou dans le premier `CHUNK` du bloc (`X` = 32 o en tête de payload quand bit3 « avec empreinte » est levé).
- `CHUNK_ACK` : `u8 statut` (0 ok, 1 already, 2 corrupt, 3 busy, 4 unknown transfer, 5 no space, 6 refused/trial, 7 bad), `u32 done`, `u64 writeBps`, `u32 queued`, `u16 retryMs`.
- `HELLO` (téléphone→TV) : `u8 credLen | credential (jeton ou PIN, « ------ » pour un téléphone de confiance exactement comme CBT1 : BtProtocol.kt:52)`, `u16 nameLen | name`, `u64 size`, `u32 blockSize`, `u8 wantEnc`, `u8 flags`, `32 o nonce_phone`. **Sur RFCOMM, le pair est l'adresse du socket** (règle `docs/BT-PLUG-AND-PLAY.md:23`), le champ `credential` suit la règle CBT1 (`trusted` → ignoré). `HELLO_OK` : `u8 laneId`, `u8 encOn`, `u8 cipher (1 = AES-256-GCM, 2 = ChaCha20-Poly1305)`, `32 o nonce_tv`, `u16 mapLen | carte hex`, `u64 writeBps`, `u16 maxUnitKiB`.
- `ERR` : `u8 code` (mêmes codes que CBT1 `ERR_*` + `ERR_TRIAL = 13`, `BtProtocol.kt:80-98`), `u16 msgLen | message FR`.
- Toute trame mal formée ferme la liaison (jamais de resynchronisation « au hasard »).

### 5.3 Service RFCOMM dédié « CastBridge Bulk »
UUID `7c5e3b9a-4d2f-4c61-9b0e-cb0000000005` (cinquième service, même schéma que `BtProtocol.kt:72-78` ; **numéro pris depuis : `…0005` est le canal propriétaire (`OwnerFrames.SERVICE_UUID`), `…0006` est réservé à W7 et `…0007` est la passerelle Internet (R-28) ; W8 étant abandonné, s'il est repris, prendre `…0008` dans la table `BtProtocol.SERVICES`**), socket **sécurisé** (appairés seulement), **une** liaison par téléphone, admis selon la même règle que le service fichiers (`acceptFile`/`TrialPolicy.btFileAllowed` pour l'essai, `trusted`) ; tourne dans `BtServer` (nouveau `BtTunnelBridge`-like `BtBulkBridge` : `handle` → `BulkStreamServer.serve(link, host)`). Avantage : la liaison du mux v2 reste libre pour l'API et le parental ; le vrac a sa propre file et son propre chien de garde (60 s). Une TV ancienne n'a pas le service : le téléphone ne crée **pas** de voie Bluetooth (pas de repli par le mux : un transfert multivoie par le tunnel API étranglerait la télécommande HTTP ; CBT1 reste pour les lots de petite taille comme aujourd'hui).

### 5.4 Intégrité et chiffrement
- **Transport** : CRC32C (`java.util.zip.CRC32C`, API 33+ / JDK 9 ; **implémentation Kotlin pure en table** dans `core` pour API 26-32, 300-500 Mo/s sur ARMv7 : négligeable) par trame **non chiffrée** ; la **balise AEAD** remplace le CRC quand la trame est chiffrée.
- **Bout en bout** : SHA-256 par bloc + racine `SHA-256(concat des empreintes)` vérifiée à `finish` (existant : `Manifest.root`, `PartAssembler.finish`). C'est un **arbre de hauteur 1** : suffisant (le téléphone renvoie un bloc, pas un fichier) ; un Merkle complet n'apporterait que la preuve partielle, inutile ici.
- **Clés de session** (v1, W8) : le seul secret partagé est le **jeton de téléphone de confiance** (256 bits, `TrustRegistry.kt:153`) que le téléphone **présente déjà** en clair (`X-CB-Token`) et que la TV vérifie par hachage. Dérivation : `K = HKDF-SHA256(IKM = jeton (octets du hex), salt = nonce_phone ‖ nonce_tv, info = "cbx-session-v1" ‖ transferId ‖ 0x00)` → 32 o clé + 4 o préfixe de nonce (`HKDF` = HMAC-SHA256 déjà utilisé, `LotKeys.kt:12`). Avec le **PIN** (téléphone non de confiance) : `IKM = PIN` est trop faible → pas de chiffrement de session, **affiché « non chiffré »** (le PIN donne déjà tous les droits sur le LAN : rien de nouveau n'est exposé). **Honnêteté** : aucune confidentialité persistante (forward secrecy) ; qui a le jeton peut dériver K, mais qui a le jeton peut déjà tout faire. **v2 (après W4)** : échange **X25519 éphémère** authentifié par `HMAC(jeton, transcription)` : confidentialité persistante ; W4 doit livrer la courbe en Kotlin pur (`KeyAgreement "XDH"` n'existe qu'à l'API 33). **Rotation** : nouvelle session (nouveaux nonces, nouvelle K) tous les 2^31 enregistrements, 1 Tio, ou 12 h (la vie du jeton) ; un changement de jeton (renouvellement à mi-vie) ne rompt pas la session en cours.
- **Enregistrements AEAD** : le payload d'un `CHUNK` (ou le corps HTTP v2) est découpé en **enregistrements de 64 Kio** (`len u16 | ciphertext | tag 16 o`) : la TV déchiffre et **n'écrit un enregistrement sur le disque qu'après vérification de sa balise** (JCA/Conscrypt met tout en tampon jusqu'à `doFinal` en GCM : avec des enregistrements de 64 Kio le tampon est de 64 Kio + 16, jamais un bloc de 8 Mio). Surcoût : 16 o / 64 Kio = 0,02 %.
- **Nonces (uniques sur toutes les voies)** : 12 o = `préfixe 4 o (dérivé, par session)` ‖ `laneId u8` ‖ `compteur u56 par voie, jamais remis à zéro dans la session` ; la TV **rejette** tout (laneId, compteur) déjà vu (fenêtre anti-rejeu par voie de 1 024). Un bloc renvoyé (nouvel essai, doublon) utilise un **nouveau** compteur : jamais de réutilisation.
- **AAD** = les 24 octets d'en-tête CBX (ou, en HTTP v2, la chaîne `id|idx|slice|laneId|record`).
- **Choix du chiffre** (mesuré, pas supposé) : `AES/GCM/NoPadding` (JCA, partout) contre `ChaCha20-Poly1305` (JCA, API 28+). Sur un Cortex-A53 en **32 bits** (armeabi-v7a, pas d'instructions AES) : AES-GCM bitslicé NEON ≈ 30-60 Mo/s par cœur, ChaCha20-Poly1305 NEON ≈ 80-150 Mo/s ; sur Cortex-A55/A72 en 64 bits avec extensions crypto : AES-GCM 300-900 Mo/s. Les deux dépassent tout disque de TV bon marché (≤ 40 Mo/s). **Règle** : à la première session, la TV chiffre 4 Mio avec chacun (≤ 0,3 s), **garde le plus rapide** dans ses préférences, l'annonce dans `caps` ; si le meilleur fait **moins de 1,5 × writeBps** du volume cible, la TV annonce `encrypt = "degraded"` et le téléphone **affiche** « chiffrement désactivé : TV trop lente » plutôt que de ralentir la copie en silence (décision D-W8-2 : par défaut **chiffré quand c'est gratuit**, intégrité seule sinon, toujours visible).
- **Compression** : règle existante par extension (`Compression.worthTrying`, `Blocks.kt:81-91`), jamais pour les médias ; en v2 la compression précède le chiffrement (gzip du clair, puis AEAD).

### 5.5 Zéro-copie et mémoire
- Téléphone : `FileChannel.transferTo` vers la socket (existant, `Blocks.kt:22-25`, `Lanes.kt:111`) **quand la voie n'est pas chiffrée** ; chiffrée : lecture dans un `ByteBuffer` direct de 64 Kio **par ouvrier** (réutilisé, jamais alloué par bloc), `Cipher.update(ByteBuffer, ByteBuffer)`, écriture socket. Pas de `ByteArray` de 8 Mio.
- TV : écriture positionnée `FileChannel.write(bb, pos)` par tampon de 256 Kio (existant) ; v2 : tampon direct de 64 Kio + 16 par flux ; SHA-256 au fil de l'eau (existant). **Mémoire bornée** : `maxStreams × 2 × 64 Kio` + files de la voie Bluetooth (256 Ko) : < 2 Mio au total.
- Sockets IP : `TCP_NODELAY` (déjà), `SO_SNDBUF` 1 Mio téléphone (déjà), `SO_RCVBUF` TV 512 Kio (NanoHTTPD : à poser dans `createClientHandler`, `ReceiverServer.kt:84`), `SO_KEEPALIVE`, `setTrafficClass(0x48)`. RFCOMM : écrire par 16-64 Kio (le `BluetoothSocket` d'Android segmente lui-même), ne jamais appeler `flush` par petit paquet.

### 5.6 Modèle de threads
Par voie : **un lecteur** (réponses/ACK) + **N ouvriers** (`allowedWorkers`, N = 1 sur flux brut, K sur HTTP), files bornées (≤ 2 unités en vol par ouvrier, ≤ 256 Ko de trames en attente sur RFCOMM : la voie ralentit, rien ne s'empile). L'ordonnanceur ne possède **aucun** thread sauf le minuteur ; les ouvriers sont des daemons nommés `xfer-<voie>-<n>` (existant). Côté TV : NanoHTTPD (`BoundedRunner`, `ReceiverServer.kt:179`) pour IP ; **un** thread par liaison RFCOMM de vrac (lecture de trames → `PartAssembler`), réponses écrites par le même thread (RFCOMM est bidirectionnel ; les ACK sont petits).

## 6. Plan de contrôle

### 6.1 Négociation au départ (additif)
1. `GET /api/transfer/caps` (existant) → v2 répond `{"version":2,"maxStreams":6,"slice":262144,"encrypt":"on|degraded|off","cipher":"aes-gcm|chacha","bulkBt":true,"usbIp":["192.168.42.129"],"wifiDirect":{"ssid":…}}`. Une TV v1 : `version:1` → chemin actuel (`WifiLane` seule, non chiffré) ; 404 → envoi classique (existant, `TransferClient.kt:122`).
2. `POST /api/transfer/begin` (existant) → carte, `writeBps`, `maxStreams` (part équitable).
3. **Nouveau** `POST /api/transfer/session?id=` corps `{nonce_phone, wantEnc}` → `{sessionId, nonce_tv, cipher, encOn}` ; dérivation § 5.4.
4. **Nouveau** `POST /api/transfer/lanes?id=&session=` corps `{kind:"wifi|direct|bt|usb", mtu, enc}` → `{laneId}` ; `DELETE … ?lane=` à la sortie. Sur flux brut, `HELLO`/`HELLO_OK` font 2-4 en une trame.
5. Les blocs partent ; `state` (existant) porte en plus `lanes:[{id,kind,bytes,bps,rtt,err}]`, `contiguous`, `encOn`.

### 6.2 « Instantané puis accéléré » (`LaneBringup`, téléphone)
```
t0      snapshot = LinkSnapshot (W7)                       // adresses LAN connues, lien BT vivant, groupe WD, lien USB
t0      si BT vivant et TV bulkBt : ouvrir BulkBtLane     // 1-3 s : premiers blocs en ≤ 3 s, l'utilisateur voit la barre bouger
t0      si LAN connue : sonde 1 s puis WifiLane K=2        // 0,2-1 s ; sinon mDNS 2-5 s
t0+2 s  si aucune voie IP : CBTN « veux Wi-Fi Direct » (existant) → jonction (8-20 s) → WifiDirectLane (LAN retiré s'il apparaissait)
t0      si lien USB IP (usb0 avec adresse dans caps.usbIp ou /api/net) : WifiLane liée à ce Network (Network.bindSocket)
en continu : garde § 4.9, veille BT quand le Wi-Fi > 2 Mo/s, retrait des voies mortes, réadmission par sondage
```
Chaque étape est un **événement**, pas une attente : le transfert n'attend jamais une voie pour commencer avec une autre. W7 fournit les adresses et les transitions de lien (§ 7.1) ; W8 ne découvre rien lui-même.

### 6.3 Sortie d'une voie
Propre : `DELETE lanes` ou `BYE` ; brutale : chien de garde (20 s IP, 60 s RFCOMM) → `remove(drain=false)`, blocs en vol requeués **devant**, nonce counters conservés (jamais réutilisés), réouverture par la courbe de `LinkPool` (1,5 s, 3 s, …) sans jamais deux `connect()` Bluetooth à la fois (`BtConnectLock`, existant).

### 6.4 Vitesse d'écriture annoncée
Dans **chaque** `CHUNK_ACK`/réponse HTTP (`writeBps`, `queued` : existant côté HTTP, ajouté côté CBX) et dans `state`. Le téléphone en déduit le plafond K (§ 4.7) et le message « c'est le disque qui limite » (existant).

## 7. Interfaces avec les autres conceptions

### 7.1 W7 (plug and play, synchronisation) — **dépendance, fichier absent**
W8 attend de W7 (ou, s'il n'existe pas à l'exécution, un adaptateur mince sur `TvLinkManager`/`LinkDriver`, `sender/TvLink.kt:113-131`) :
```kotlin
data class LinkSnapshot(val tv: String, val lanBase: String?, val directBase: String?, val btAddress: String?, val btAlive: Boolean,
                        val usbBase: String?, val credential: () -> String?, val trusted: Boolean, val wifiGhz: Int?)
interface LinkSet { fun snapshot(): LinkSnapshot; fun onChange(l: (LinkSnapshot) -> Unit); fun requestWifiDirect(): Boolean /* CBTN */ }
```
W8 renvoie à W7 des `TransportEvent(lane, up/down, bps)` pour l'affichage « Connectée par … » et l'hystérésis (`LinkMachine`). **W8 ne possède pas** : découverte, appairage, HELLO, jetons, jonction Wi-Fi Direct, décision de route pour les **petites** requêtes (`LinkPlanner`). Si W7 nomme autrement ces objets, le cahier w8-07 s'aligne sur W7 (contrat : les champs ci-dessus, pas les noms).

### 7.2 W6 (`SendGuard`)
`BulkTransfer.send(…, verdict: SendGuard.Verdict.Allowed)` : le type du paramètre **oblige** l'appelant à avoir passé la garde (cahier w6-17) ; aucune autre entrée dans le moteur. Un 403 `{"trial":true}` de la TV ou un `ERR_TRIAL` CBX est remonté comme `Refused(M-TV-REFUSED)` (catalogue `PhoneGateTexts`), **jamais** comme « liaison coupée », et déclenche `syncNow` (contrat w6-17 § 5).

### 7.3 W4/W5 (lots scellés par TV)
Les lots sont des fichiers opaques déjà chiffrés par TV (boîte v2 à venir) : le transport n'en sait rien. W8 remplace les 512 Kio par requête de `HttpLotTransport.send` (`LotPush.kt:135-159`) par `BulkTransfer` vers `/api/transfer` **quand `caps.version ≥ 1`**, puis `POST /api/lots/install?name=` (existant) adopte le fichier reçu dans le dossier de réception (`LotsHub.adopt`, `BtServer.kt:123`). Repli : chemin actuel. `Cbt1LotTransport` reste pour les TV sans service de vrac.

### 7.4 Essai / mode réduit
La TV ferme `/api/transfer` et le vrac Bluetooth en essai (`TrialPolicy.routeAllowed`, `btFileAllowed`) : le service …0005 applique **la même** règle (`acceptFile`) et répond `ERR_TRIAL` avec `TrialPolicy.BT_MESSAGE`. Le moteur rend `Result.Refused(message)` distinct de `Failed` : l'interface affiche le message, ne réessaie pas.

### 7.5 Tunnel distant (SSH, assistance) : **séparé**, inchangé. Il ne porte jamais de vrac W8.

## 8. Intégration, compatibilité, limites Android

### 8.1 Garder / étendre / remplacer (par fichier)
| Fichier | Sort |
|---|---|
| `core/xfer/Blocks.kt` | **Garder** ; ajouter `Manifest.idBytes` (8 o pour CBX) |
| `core/xfer/Lane.kt` | **Étendre** : `laneId: Int`, `kind`, `unitBytes()`, `health: LaneHealth`, `idle(Boolean)` ; **supprimer** `slow` (remplacé par l'unité) et `UsbLane` vide (remplacé par « voie IP sur usb0 » + pointe AOA) |
| `core/xfer/Lanes.kt` | **Garder** `KController`, `WifiLane`, `WifiDirectLane` ; `BluetoothLane` (HTTP par tunnel) **remplacée** par `BulkBtLane` (CBX) ; `ChunkClient.outcome` : 403 `trial` → `Refused` |
| `core/xfer/Scheduler.kt` | **Étendre** : `LaneSet`, `OrderPolicy`, garde § 4.9, seau à jetons, plafond K par disque ; conserver vol de travail, doublon de fin, écart |
| `core/xfer/PartAssembler.kt` | **Étendre** : enregistrements AEAD, fsync cadencé, `contiguous()`, `SparseGrowingStream` |
| `core/xfer/TransferHost.kt`, `TransferClient.kt` | **Étendre** : sessions/voies/stats, part équitable, `Refused` |
| `core/xfer/HttpConn.kt` | **Garder** ; `setTrafficClass` |
| **Nouveaux** `core/xfer/{LaneSet,LaneHealth,OrderPolicy,CbxFrame,Crc32c,SessionKeys,AeadRecords,BulkStream,BulkBtLane,BulkTransfer,LaneBringup,LaneStats,FakeLane}.kt` | § 3-6 |
| `core/tv/ReceiverServer.kt` (§ transfert) | **Étendre** (routes `session`, `lanes`, corps chiffré, stats) |
| `core/tv/BtProtocol.kt` | **Étendre** : `BULK_SERVICE_UUID`, `ERR_*` réutilisés ; CBT1/CBTN/CBTH/CBTR **inchangés** |
| `receiver/BtServer.kt`, nouveau `receiver/BtBulkBridge.kt` | **Étendre** : cinquième service |
| `sender/UploadService.kt`, `TransferQueue.kt`, `BtUploadService.kt` | `runFast` → `BulkTransfer` ; la file ne choisit plus Wi-Fi **ou** Bluetooth ; `BtUploadService` (CBT1) reste pour les TV anciennes |
| `core/lots/LotPush.kt`, `sender/LotsRuntime.kt` | `BulkLotTransport` (nouvelle implémentation de `LotTransport`) |
| `core/xfer/TransferBench.kt`, `tools/transfer-bench` | **Étendre** : `--lanes`, scénarios |
| `core/tv/TvClient.kt` (`ResumableUpload`) | **Garder** (repli TV v0) |

### 8.2 Compatibilité
| Téléphone \ TV | TV sans `/api/transfer` | TV v1 (aujourd'hui) | TV v2 (W8) |
|---|---|---|---|
| Ancien (sans `xfer`) | `PUT /upload` séquentiel | idem | idem (routes v1 intactes) |
| v1 (aujourd'hui) | `PUT /upload` | `WifiLane` seule | `WifiLane` seule, non chiffré (`caps.version` lu, routes v1 servies) |
| v2 (W8) | `PUT /upload` | `WifiLane` seule (K adaptatif, stats locales) | tout |

### 8.3 Essai, mode réduit, parental : § 7.2, 7.4. Rien de nouveau n'est ouvert ; `tools/routes/routes.txt` reçoit les deux routes ajoutées (règle w1-06).

### 8.4 Thermique et batterie (téléphone)
`PowerManager.thermalHeadroom`/`currentThermalStatus` (API 29+) : statut ≥ `SEVERE` → K plafonné à 2 et chiffrement maintenu (le disque borne de toute façon) ; batterie < 15 % non branchée → pas de Wi-Fi Direct (coût radio), transfert maintenu sur ce qui existe ; verrou de réveil 6 h existant (`UploadService.kt:279-282`). TV : rien (secteur).

### 8.5 Restrictions d'arrière-plan (téléphone)
Service premier plan `dataSync` existant ; pour une voie USB (accessoire) il faudra le type `connectedDevice` (Android 14 exige la cohérence des types) : à ajouter **seulement** si la pointe AOA aboutit. `CompanionDeviceManager` : non requis (les sockets RFCOMM sortants vers un appareil appairé restent permis en premier plan) ; Android 14+ : un `startForegroundService` depuis l'arrière-plan est déjà traité (w3-02). Pas de WorkManager (règle du projet).

### 8.6 Ce qui peut rendre l'agrégat plus lent que la meilleure voie seule — et la garde
| Cause | Garde |
|---|---|
| LAN + Wi-Fi Direct sur la même radio | Jamais ensemble (§ 4.9.1) |
| Bluetooth qui gêne le Wi-Fi 2,4 GHz (antenne partagée) | Veille BT au-dessus de 2 Mo/s (§ 4.9.2) |
| Voie lente qui garde les derniers blocs | Doublon de fin (existant) + unité adaptative |
| Trop de connexions pour un disque lent (contention, CPU TV) | K plafonné par `writeBps` (§ 4.7) |
| Chiffrement sur TV trop lente | Mode dégradé **visible** (§ 5.4) |
| Sondage subtractif général | `total < 0,9 × best` → retrait de la plus faible (§ 4.9) |
| Deux téléphones | Part équitable des flux (§ 4.6) |

## 9. Débits attendus par scénario (hypothèses explicites)

| # | Scénario | Hypothèses | Aujourd'hui | W8 | Gain réel | 1 Go en |
|---|---|---|---|---|---|---|
| S1 | Maison, box 2,4 GHz n, TV eMMC (20 Mo/s) | TV Wi-Fi 1×1 ; téléphone à 5 m | K adaptatif : 4-6 Mo/s | 4-6 Mo/s + BT en veille | **≈ 0 % de débit** ; démarrage ≤ 3 s au lieu de 3-6 s ; pas de « en attente du réseau » quand le Wi-Fi hoquette | 3-4 min |
| S2 | Box 5 GHz ac des deux côtés, TV eMMC | TV 2×2 ac (rare, TV récentes) | 12-20 Mo/s (disque borne) | 15-20 Mo/s (disque) | fsync + K plafonné : moins de rejet 429, 0-10 % | ≈ 1 min |
| S3 | Clé exFAT lente (1,4-9,5 Mo/s mesurés) | la TV de référence | = disque | = disque | **0 %** ; message « c'est le disque » (existant) ; recommandation de clé plus rapide | 2-12 min |
| S4 | **Pas de réseau Wi-Fi** (village, coupure box) | BT 0,2 Mo/s ; WD 3-8 Mo/s 2,4 GHz | Bluetooth CBT1 : 0,15-0,25 Mo/s | BT à t0, **Wi-Fi Direct à t0+15 s** : 3-8 Mo/s | **×15 à ×40** ; c'est LE cas où W8 change tout | 2-6 min au lieu de 1,2-2 h |
| S5 | Box avec isolation client / hôtel | idem S4 | « TV introuvable » puis Bluetooth | S4 | idem | idem |
| S6 | Câble USB (tethering ou AOA) **si la pointe réussit** | 20-30 Mo/s ; disque 20 Mo/s | — | disque : 15-20 Mo/s ; indépendant des radios | fiabilité, zéro radio | ≈ 1 min |
| S7 | Wi-Fi qui bat (voisinage chargé) | pertes 5-10 % | K monte/descend, pauses « Waiting » | voies en veille/réveil, BT comble les trous | continuité, −30 % de durée totale observée sur simulation, à confirmer | — |
| S8 | Deux téléphones vers la même TV | 2 sessions | partage non contrôlé | parts égales de flux | équité | — |

Lecture honnête : **le débit de pointe ne bouge pas** (S1-S3 : disque ou puce Wi-Fi) ; **W8 gagne sur la robustesse, le démarrage et les cas sans réseau (S4-S5)**. Si le propriétaire veut du débit brut sur S3, la réponse est une **clé plus rapide**, pas une voie de plus.

## 10. Séquences

**Démarrage** : `caps` → `begin` → `session` → `BulkBtLane.HELLO` (laneId 1) ∥ `WifiLane` `lanes` (laneId 2) → blocs → … → `finish` (relecture, renommage) → `commit`.

**Ajout d'une voie** : W7 signale `directBase` → `LaneBringup` : si LAN absent ou lent → `lanes{kind:direct}` → laneId 3 → ouvriers → garde § 4.9 retire le LAN si présent.

**Perte d'une voie** : chien de garde → `remove(drain=false)` → blocs devant → les autres voies continuent ; `LinkPool` rouvre (courbe) → `lanes` à nouveau (nouveau laneId, nouveaux compteurs) → `add`.

**Reprise après coupure de courant de la TV** : TV redémarre → `TransferHost` vide → téléphone reçoit 404 (`SessionLost`, existant) → `begin` → `PartAssembler.open` **relit `.state`** (blocs fsyncés) → carte renvoyée → seuls les blocs manquants partent → `finish` relit tout.

**Reprise après redémarrage du téléphone** : même `Manifest.id` → `begin` → carte → `state?hashes=1` précharge les empreintes (existant).

**Fichier > 4 Gio sur FAT32 (option D-W8-5, cahier w8-13)** : `begin` répond 413 `split` ; le téléphone envoie `N = ceil(size / (4 Gio − 1 Mio))` transferts `name.cbxsplit/0000N.bin` + `index.json` ; la TV expose **un** fichier logique (`SplitFile` : lecteur concaténé pour `/stream` et le lecteur), la bibliothèque le liste une fois, la suppression supprime le dossier. Sans ce cahier : refus actuel avec message « formatez la clé en exFAT ».

## 11. Diagnostic

- `GET /api/transfer/state` : `lanes:[{id,kind,bytes,bps,rtt,err,benched,idle}]`, `contiguous`, `encOn`, `cipher`, `writeBps`, `queued` (aucun secret, pas d'adresse Bluetooth : `Diagnostics.scrub` reste la règle).
- Téléphone : écran « Voies du transfert » (dans `TvTransferScreen`) : par voie, débit instantané/moyen, RTT, erreurs, état (active/veille/écartée), ce qui borne (disque/Wi-Fi), chiffrement ; bouton « Copier le rapport » (texte sans secret).
- Journal structuré (logcat, 1 ligne/2 s, étiquette `CbxXfer`) : `xfer id=… lanes=wifi:5.1M/12ms/0 bt:idle total=5.1M disk=6.0M K=3 enc=aes`.
- TV : ligne d'état existante (`setStatus("1-bt", …)`) : « Réception : 5,1 Mo/s (Wi-Fi 5,0 + Bluetooth 0,1) ».

## 12. Plan de test et banc

### 12.1 JVM (`core`, hors ligne) — `FakeLane(bandwidthBps, latencyMs, jitterMs, lossPct, disconnectAt: List<Long>, reconnectAfterMs)`
Propriétés (tests paramétrés, graine fixe, ≥ 200 itérations) :
1. **Aucune corruption** : pour toute combinaison de 1-5 fausses voies et de pannes aléatoires, le fichier assemblé a la racine attendue (`finish` → `Done`).
2. **Convergence** : avec Σ débits < disque simulé, débit agrégé ≥ **85 %** de Σ sur un fichier de 64 Mio (temps simulé par horloge injectée, pas d'attente réelle).
3. **Reprise** : voies tuées à des instants aléatoires, TV « redémarrée » (nouveau `TransferHost`, même dossier) à un instant aléatoire : terminaison, ≤ 2 blocs renvoyés par incident.
4. **Pas d'interblocage** : chaque test borné à 20 s réels ; retrait de **toutes** les voies puis réadmission.
5. **Mémoire bornée** : octets en vol côté TV ≤ `maxInflight` à tout instant (compteur assertif), tampons ≤ 2 Mio.
6. **Garde** : une fausse voie qui divise le débit de l'autre par 2 quand elle est active (modèle « même radio ») est retirée en ≤ 25 s simulées.
7. **Crypto** : vecteurs HKDF/AEAD/nonces figés (`xfer-vectors.json`), rejet du rejeu, deux voies ne produisent jamais le même nonce (test exhaustif sur 10^6 trames).
8. **Trames** : codage/décodage CBX, trames malformées → fermeture, CRC32C vecteurs connus (`123456789` → `0xE3069283`).
Commande : `cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.*'`.

### 12.2 Vraie TV (propriétaire)
Fichiers : 1 Mo, 100 Mo, 2 Go (aléatoires, `tools/transfer-bench/run.sh --gen`). Pour chaque scénario S1, S3, S4 (+ S6 si câble) :
```sh
tools/transfer-bench/run.sh --tv http://IP:8765 --size 100M --lanes auto        # tableau par voie, réseau seul / réseau + disque
curl -s -H "X-CB-Token: …" "http://IP:8765/api/transfer/state?id=<id>"           # voies, writeBps, contiguous
adb -s <tv> logcat -s CbxXfer CastBridgeBT                                        # TV ; idem téléphone
adb -s <tv> shell reboot   # à 40 % d'un 2 Go : reprise ≤ 2 blocs renvoyés, finish OK
```
Seuils de réussite : agrégat ≥ 0,9 × meilleure voie seule (**jamais** en dessous) ; S4 : premier bloc confirmé ≤ 3 s, Wi-Fi Direct actif ≤ 25 s ; 100 Mo sur S1 sans aucun « en attente » quand le Wi-Fi est coupé 5 s ; chiffrement : débit « réseau + disque » chiffré ≥ 0,9 × non chiffré, sinon mode dégradé annoncé ; 2 Go : relecture `finish` < 2 min sur eMMC ; mémoire TV (`dumpsys meminfo`) stable à ± 10 Mio.

### 12.3 Pire cas et dégradation
| Cas | Comportement attendu |
|---|---|
| Bluetooth seul | 0,15-0,25 Mo/s, tranches 256 Kio, reprise au milieu d'un bloc, message « liaison réduite » (W7) ; le transfert n'abandonne jamais seul |
| Wi-Fi seul | comme aujourd'hui + stats |
| Voie qui bat (flapping) | hystérésis : réadmission par sondage, pas de tempête de `connect()` (`LinkPool`) |
| Stockage TV presque plein | `begin` 507 avec message existant ; en cours : `DiskFail` → 507 → `Failed` clair, `.cbx` conservé 24 h |
| FAT32 et > 4 Gio | refus clair ou découpage (D-W8-5) |
| TV à 1 Go qui swappe | mémoire bornée (§ 5.5) ; K ≤ 6 ; pas de tampon de bloc entier |
| Essai / mode réduit | `Refused` avec le message du catalogue, aucun nouvel essai |

## 13. Effort et coût

| Sous-vague | Contenu | Agent·jours | Modèles |
|---|---|---|---|
| 8a cœur (JVM) | voies dynamiques, santé, ordre, CBX, crypto, flux brut, façade, hôte v2, banc à fausses voies | ≈ 16 | sonnet |
| 8b TV | routes v2, service RFCOMM de vrac, pointes USB, (découpage FAT32 option) | ≈ 7 (+3 option) | sonnet |
| 8c téléphone | `UploadService` → `BulkTransfer`, lots, diagnostic, Wi-Fi Direct + coexistence | ≈ 9 | sonnet |
| 8d banc, docs, campagne, CI, (manifeste clé USB option) | ≈ 3 (+2 option) | haiku (sonnet pour w8-21) |
| **Total** | 21 cahiers | **≈ 35 j** (+5 options) | |

Ce que l'on n'achète pas : L2CAP/BLE, Wi-Fi Aware, Nearby, réécriture faststart, Merkle complet, serveur binaire sur IP, bibliothèque crypto native.

## 14. Risques

| Risque | Probabilité | Parade |
|---|---|---|
| Aucune mesure réelle : les ordres de grandeur sont faux sur la TV de référence | moyenne | le banc tourne **avant** 8c ; les gardes sont adaptatives (mesurées), pas calibrées |
| Wi-Fi Direct instable sur TV bon marché | élevée | reste **désactivé** tant que w8-17 n'a pas 3 transferts de 100 Mo réussis ; LAN d'abord |
| Coexistence BT/Wi-Fi pire que prévu | moyenne | veille BT (§ 4.9.2), seuil réglable, mesurable au banc (Wi-Fi seul vs Wi-Fi + BT) |
| Chiffrement trop lent en 32 bits | faible | micro-banc + mode dégradé visible |
| W7 absent ou interface différente | élevée (fichier absent) | adaptateur mince sur `TvLinkManager` dans w8-07 ; contrat = champs, pas noms |
| USB AOA impossible (pas de port libre, pile hôte GaiaOS) | élevée | pointe bornée à 2 j, décision D-W8-1 avant tout |
| Service RFCOMM de plus : `already at opened state` (pile BT du S21+) | moyenne | même `BtConnectLock`/`LinkPool` ; validé par w8-11 sur matériel |
| Régression de l'envoi classique | faible | routes v1 intactes, tests `MultipathServerTest` conservés, campagne w8-20 |
| Mémoire TV | faible | bornes § 5.5, test 12.1-5, `dumpsys meminfo` |

## 15. Décisions du propriétaire

| # | Question | Recommandation | Statut |
|---|---|---|---|
| **D-W8-1** | Les TV cibles ont-elles un **port USB libre** (hôte) une fois la clé branchée, et quel connecteur (A/C) ? Autorise-t-on l'utilisateur à brancher son téléphone à la TV ? | Pointe w8-12 sur la TV de référence ; si un seul port : abandonner AOA, garder le tethering en option | **BLOQUÉ** (fait matériel) |
| D-W8-2 | Chiffrer le vrac sur le LAN par défaut ? | **Oui quand c'est gratuit** (disque borne), dégradé visible sinon ; jamais avec le PIN seul | décidé par l'architecte, renversable |
| D-W8-3 | Réécrire les MP4 `moov` en fin pour lire pendant l'envoi ? | **Non en W8** (CPU/batterie, fichier temporaire) ; message existant | décidé |
| D-W8-4 | Bluetooth : service RFCOMM dédié au vrac ou passage par le tunnel API v2 ? | **Dédié** (…0005) : télécommande et parental intacts | décidé |
| D-W8-5 | Découpage des fichiers > 4 Gio sur FAT32 (lecteur concaténé) ? | **Option** w8-13, après tout le reste ; alternative gratuite : conseiller exFAT | à trancher (cas rare : films 4K) |
| D-W8-6 | Plafond de débit réglable par l'utilisateur ? | Oui, éteint par défaut (2/5/10 Mo/s) | décidé |
| D-W8-7 | Garder `BluetoothLane` HTTP par tunnel comme repli pour une TV v1 sans service …0005 ? | **Non** (étrangle l'API) ; Wi-Fi seule sur TV v1 | décidé |

## 16. Ce que cette conception n'a pas pu vérifier
- Aucun débit réel (pas de TV dans cette session ; `docs/HANDOFF.md:39` confirme l'absence de mesure).
- `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` absent : l'interface § 7.1 est une proposition.
- La pile USB hôte de GaiaOS, le nombre de ports, le comportement AOA du S21+ : D-W8-1.
- La vitesse réelle de `AES/GCM` et `ChaCha20-Poly1305` sur la TV 32 bits (micro-banc prévu).
- La disponibilité de `ChaCha20-Poly1305` dans le fournisseur JCA des TV API 28 bon marché (Conscrypt est embarqué depuis Android 9 ; repli AES-GCM sinon).
- Le comportement du GO Wi-Fi Direct de la TV de référence pendant qu'elle reste cliente de la box.
