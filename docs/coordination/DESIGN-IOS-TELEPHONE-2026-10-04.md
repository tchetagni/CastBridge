# Conception iOS : CastBridge sur iPhone (le téléphone parle à CastBridge-TV)

> Document de conception (architecte, 2026-10-04). **Aucun code n'est modifié par ce document.** Exécution par les cahiers `docs/agent-briefs/sonnet-wios-NN-*.md` (iPhone, répertoire neuf `ios/`) et `docs/agent-briefs/sonnet-wios-tv-NN-*.md` (TV et cœur Kotlin), index `docs/agent-briefs/SONNET-WAVE-IOS-INDEX.md`, sur ordre du coordinateur, dans les règles du gel W15 (cœur pur d'abord, câblage mince après, audit Opus sur confiance/PIN/identifiants). Branche de référence `integration/agents` (HEAD `3484fbb1`). Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `I/` = `ios/` (neuf).
>
> **Demande du propriétaire (2026-10-04, verbatim)** : « peux-tu me produire une version iOS de l'application phone ».
>
> **Lecture appliquée** : produire **CastBridge pour iPhone**, compagnon de **CastBridge-TV**, qui respecte les règles du produit (85 % des foyers sans point d'accès, PIN pour lier un téléphone à une TV, 8 téléphones au plus par TV, aucun téléphone ne parle au service de jeu, noms « CastBridge » et « CastBridge-TV »), **sans promettre ce qu'Apple interdit** aux applications tierces : ni Bluetooth classique RFCOMM (réservé aux accessoires MFi), ni Wi-Fi Direct (aucune API publique), ni service de fond permanent, ni installation d'APK.
>
> **Sources lues le 2026-10-04** : `docs/HANDOFF.md` (§ 0 et en-tête), `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` (entier), `DESIGN-W19-SYMBIOSE-PHONE-TV-2026-10-03.md` (§ 0-1), `DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` (en-tête, I-1…I-4), `DESIGN-W21` (coursier), `docs/ADMIN.md` (§ 1-8 : **c'est la documentation de l'API HTTP de la TV**, port 8765), `docs/TRANSFER.md` (§ 2-4), `docs/BT-PLUG-AND-PLAY.md` (sécurité, jetons, 8 téléphones), `docs/ACTIVATION-FORMAT.md` (§ 0-3), `docs/OWNER-CONSOLE.md` (§ 1-4), `docs/TRIAL-EDITION.md` (passages « téléphone », option A), `docs/PLAY-PROTOCOL.md` (en-tête), `docs/QUIZ.md` (page `/quiz`), `docs/ORDRES.md` (transport), l'index W18 et W19 (statuts), le code `C/tv/Security.kt` (`Pin.LENGTH`), `C/tv/ReceiverServer.kt` (`/api/hello`), `C/xfer/Blocks.kt`, `C/trust/TrustRegistry.kt`, `C/tv/WifiDirect.kt` (`wifiUri`), `R/TvService.kt` (mDNS), `S/` (inventaire). **Les faits Apple** viennent de la documentation publique connue de l'architecte (iOS 16 à 26) ; **le SDK iOS 27 installé sur le Mac n'a pas été lu** (aucun réseau, aucune compilation) : toute API marquée « iOS 26+ » ou « à vérifier sur SDK 27 » doit être confirmée par le premier cahier qui l'utilise.

## 0. En quinze lignes

1. **Verdict** : une version iPhone **est faisable**, comme **compagnon Wi-Fi** de CastBridge-TV ; elle **ne peut pas** être une copie de l'app Android : 6 fichiers RFCOMM et 11 fichiers Wi-Fi Direct de `S/` n'ont **aucun équivalent** autorisé sur iOS.
2. **Ce qu'iOS ne peut pas faire** : Bluetooth classique (copie CBT1, passerelle Internet de la TV, télécommande clavier HID, SSH par Bluetooth, mode porteur par trame `ACTIVATION`) ; créer ou rejoindre un groupe Wi-Fi Direct **en tant que pair P2P** ; tourner en fond comme un service ; s'auto-mettre à jour ; relayer des APK.
3. **Ce qu'iOS peut faire** : rejoindre le groupe Wi-Fi Direct de la TV **comme un client Wi-Fi ordinaire** (la TV propriétaire de groupe se comporte comme un point d'accès WPA2 ; c'est déjà ainsi qu'un Mac s'y connecte, `docs/ADMIN.md` § 6) grâce à `NEHotspotConfiguration` (SSID `DIRECT-CB-…` + mot de passe reçus par **QR** affiché par la TV) ; découvrir la TV par Bonjour `_castbridge._tcp` (déjà annoncé par la TV) ; parler l'API HTTP existante (port 8765) ; envoyer photos et vidéos avec reprise et progression (Live Activity) ; télécommander la lecture ; jouer au Quiz par la page `/quiz` de la TV.
4. **Les trois situations** : même Wi-Fi (fonctionne dès aujourd'hui côté TV) ; **sans point d'accès** (85 %) : fonctionne **si et seulement si** la puce de la TV sait être propriétaire de groupe (fait bloquant **B-W18-1**, test terrain W18 § 7 non fait) **et** si la TV publie ses identifiants persistants par QR (cahiers W18 w18-01/w18-07 **non exécutés**) ; **aucun réseau commun** : impossible sur iPhone, dit tel quel.
5. **Stratégie recommandée** : **Swift natif** (SwiftUI + paquet `CastBridgeKit`) réécrivant un **sous-ensemble défini** du cœur (≈ 3 200 lignes Kotlin concernées sur 63 067), avec **vecteurs de test partagés** JSON générés par le cœur Kotlin (même méthode que `tools/activation/*-vectors.json` et `tools/wallet/*-vectors.json`). Kotlin Multiplatform rejeté pour v1 (217 fichiers sur 428 importent `java.*`/`javax.*`, 104 utilisent `synchronized`/`@Volatile` : refonte ≥ 25 agent·jours qui toucherait la TV, le serveur de jeu et le gel) ; Compose Multiplatform/Flutter rejetés (77 des 90 fichiers de `S/` importent `android.*`).
6. **MVP (TestFlight)** : lier par PIN (même Wi-Fi ou groupe de la TV après QR), envoyer photo/vidéo depuis l'app **et** la feuille de partage, voir la progression, télécommande de base (lecture, pause, avance, volume, suivant), bibliothèque de la TV en lecture, Quiz par la page de la TV. **Pas en v1** : boutique, lots, Apprendre, contrôle parental, échecs, coursier, activation de TV, Bluetooth.
7. **Changements TV (additifs, versionnés)** : **wios-tv-01** vecteurs partagés + TV factice exécutable pour le simulateur ; **wios-tv-02** appairage HTTP par PIN avec clé d'appareil Ed25519 (`pin-http-v1`, compté dans les **8**) ; **wios-tv-03** QR « Ajouter un iPhone » (adresse + identité + identifiants Wi-Fi Direct), TXT mDNS additif, iPhones dans l'écran « Téléphones » ; **wios-tv-04** service BLE GATT (conditionnel : la TV sait-elle annoncer en BLE ? inconnu) ; **wios-tv-05** activation par HTTP en état verrouillé (conditionnel, décision du propriétaire). Prérequis : w18-01, w18-07, w19-02.
8. **Étapes Apple du propriétaire** (jamais d'identifiant Apple demandé ni stocké) : compte déjà présent dans Xcode (une identité « Apple Development » est dans le trousseau, aucune « Apple Distribution ») ; créer la fiche App Store Connect, laisser Xcode signer automatiquement, archiver et téléverser, tester en **TestFlight interne**, répondre aux questionnaires (chiffrement, confidentialité, âge).
9. **Effort / coût** : v1 = 14 cahiers (11 iPhone + 3 TV), **≈ 24 agent·jours**, **≈ 21-26 $** d'agents (prix des index précédents, non vérifiés), ≈ **8-10 jours ouvrés** de calendrier avec les builds sérialisés ; v2 conditionnel ≈ 4 $ de plus côté TV.
10. **Décisions** : 15 (§ 9), dont 4 à trancher par le propriétaire : **D-IOS-2** (iPhone sans activation propre, débloqué par la TV activée : imposé par la règle App Store 3.1.1), **D-IOS-6** (iOS 16 minimum), **D-IOS-7** (identifiant et nom), **D-IOS-8** (mode de distribution).
11. **Risques majeurs** : puce TV sans mode propriétaire de groupe (alors **aucun** iPhone dans 85 % des foyers), refus App Review (relecteur sans TV : prévoir un **mode démonstration** et une vidéo), copies longues interrompues en fond (iOS suspend l'app), photos iCloud téléchargées par données mobiles, HEIC/HEVC non lus par la TV.
12. **Ce que le Mac fait seul** : `swift test` du paquet, build et tests XCTest/XCUITest sur simulateur iOS 27 (iPhone 17/18), tests d'intégration contre **la vraie `ReceiverServer` Kotlin** lancée sur 127.0.0.1. **Ce qui exige un vrai iPhone** : Wi-Fi Direct de la TV, invite « réseau local », débits, fond, Live Activity sur l'écran verrouillé, BLE, extension de partage avec une vidéo de 2 Go.
13. **Ce qui exige le propriétaire** : signature, appareil en « Mode développeur », fiche App Store Connect, téléversement, TestFlight, questionnaires, essais avec la TV (3 séances d'≈ 1 h).
14. **Non vérifié** : SDK iOS 27 (non lu), comportement d'iOS sur un réseau sans Internet, BLE de la TV, GO de la puce de la TV, disponibilité du nom « CastBridge » sur l'App Store, disponibilité de l'App Store/TestFlight au Cameroun, part des iPhone chez les clients.
15. **Ordre** : wios-tv-01 ∥ wios-01 → wios-02 ∥ wios-03 ∥ wios-tv-02 → wios-04 ∥ wios-05 → wios-06 → wios-07 ∥ wios-08 ∥ wios-09 ∥ wios-tv-03 → wios-10 ∥ wios-11 → propriétaire (TestFlight) ; détails § 8.

## 1. Ce qui existe (vérifié, avec chiffres)

| Élément | Constat (2026-10-04) | Conséquence iOS |
|---|---|---|
| App téléphone `S/` | **90 fichiers, 18 948 lignes** Kotlin ; 56 fichiers Compose ; **77 fichiers / 507 lignes `import android.*`** (`android.content` 167, `android.app` 79, `android.os` 65, `android.net` 59, `android.bluetooth` 30, `android.provider` 29) ; Media3 dans 7 fichiers | l'interface ne se transpose pas : elle est collée aux API Android |
| RFCOMM dans `S/` | **6 fichiers** ouvrent des sockets Bluetooth : `TvLink`, `BtUploadService`, `BtGatewayService`, `BtSshGateway`, `RemoteController`, `OrdersRuntime` ; 33 fichiers mentionnent le Bluetooth | **aucun équivalent** (Bluetooth classique = MFi seulement) |
| Wi-Fi Direct dans `S/` | **11 fichiers** (`AutoWifiDirect` 539 lignes, `WifiDirectScreen`, `WdManualCard`, …) | **aucun équivalent P2P** ; remplacé par « rejoindre le réseau de la TV comme client » |
| Cœur `C/` | **428 fichiers, 63 067 lignes**, 340 fichiers de tests ; aucun `import android` (JVM pur) ; 217 fichiers importent `java.*`/`javax.*` ; 140 fichiers / 12 727 lignes sans aucune dépendance JVM détectée | réutilisable **comme spécification et comme générateur de vecteurs**, pas comme binaire iOS (§ 4) |
| API HTTP de la TV | port **8765**, ≈ 95 routes (`/api/*`, `/upload/*`, `/stream/*`, `/quiz/*`), JSON UTF-8, `X-CB-Pin` ou `X-CB-Token`, jamais en URL ; `Host` = adresse IP privée sinon 403 ; `PinGuard` 5 échecs / 60 s par IP ; documentée dans `docs/ADMIN.md` § 3, `docs/TRANSFER.md` § 3 | **directement utilisable** par un iPhone sur le même réseau (aucun changement TV pour la copie et la télécommande) |
| `/api/hello` | `{"app":"castbridge-tv","v":…,"pinRequired":…}` : **pas d'identité de TV** (w19-02 l'ajoute, PRÊT, non exécuté) | identité par `id` nécessaire pour plusieurs TV (wios-tv-02 l'ajoute si w19-02 n'est pas fusionné) |
| PIN | `Pin.LENGTH = 6` (`C/tv/Security.kt:9`) ; la consigne du propriétaire dit « 4 à 8 chiffres » | l'iPhone lit la longueur annoncée (`pinLen`, défaut 6) et accepte 4 à 8 (D-IOS-13) |
| Jeton du téléphone | 256 bits, 12 h, délivré **uniquement** par HELLO Bluetooth (`CBTH`) à un pair de confiance ; renouvelé par Bluetooth (`BT-PLUG-AND-PLAY.md` § Sécurité) | **aucun chemin HTTP** pour obtenir un jeton : un iPhone ne peut aujourd'hui qu'envoyer le PIN à chaque requête et **n'est pas compté** dans les 8 ⇒ wios-tv-02 obligatoire pour v1 |
| 8 téléphones | `TrustRegistry.MAX_PHONES = 8`, `trust(adresse, nom)`, `replace` atomique, écran TV `PhonesActivity` | les iPhone entrent dans **le même** registre (clé `key:<kid>` au lieu d'une adresse Bluetooth) |
| mDNS | la TV annonce `_castbridge._tcp`, TXT `role=receiver` (`R/TvService.kt:1078`), sur le réseau par défaut seulement | Bonjour iOS (`NWBrowser`) la trouve sur la box ; **pas** sur le groupe Wi-Fi Direct (inutile : adresse fixe 192.168.49.1) |
| QR et `WIFI:` | générateur `C/quiz/QrCode.kt` ; `WifiDirect.wifiUri(ssid, pass)` = `WIFI:T:WPA;S:…;P:…;;` (`C/tv/WifiDirect.kt:52`) ; QR `castbridge://tv?…` **conçu** (W7 § 4.1 rang 6, W18 D-W18-5) mais **non implémenté** | briques TV prêtes ; l'appareil photo d'iOS sait rejoindre un réseau depuis un QR `WIFI:` |
| Wi-Fi Direct de la TV | aujourd'hui : manuel (MENU), mot de passe **frais par groupe** ; W18 (persistant, allumé à l'écran, QR étendu) : **cahiers w18-01 et w18-07 non exécutés**, test terrain § 7 **non fait** | la voie « sans point d'accès » de l'iPhone dépend de W18 |
| BLE de la TV | **aucun** code BLE dans `R/` ; W18 § 9 : « BLE absent sur la TV » ; W7 D-W7-1 : inconnu | remise d'identifiants par BLE = **conditionnelle** (wios-tv-04 commence par une sonde) |
| Quiz | joueurs locaux par la page `/quiz` de la TV (SSE + POST), **sans PIN** ; amendement W20 I-2 : « aucun téléphone ne se connecte jamais au service de jeu » | l'iPhone joue par la page `/quiz` de **sa** TV (vue web), aucune règle neuve |
| Activation | `cbx1`, Ed25519 pur RFC 8032, 109 vecteurs (`tools/activation/test-vectors.json`) ; le téléphone Android a **sa propre activation** (`subject=phone`, `ANDROID_ID`) et l'option A « rien n'est gratuit sans clé » | sur iOS, une clé qui débloque l'app hors achat intégré est **interdite** (règle 3.1.1) : D-IOS-2 |
| Mac | Xcode **27.0** (27A266a), Swift **6.4**, SDK iOS **27.0**, **un seul** environnement de simulation (iOS 27.0 : iPhone 17e, 17 Pro Max, 18 Pro, 18 Pro Max, Air), 12 cœurs, 32 Go (le commentaire de `tools/agents/gradle-lock.sh` dit 8 Go : périmé), **une** identité « Apple Development » au nom du propriétaire (identifiant d'équipe non recopié ici), **aucune** « Apple Distribution » | build et tests simulateur possibles ; tests sur iOS < 27 impossibles sans télécharger d'autres environnements (réseau, propriétaire) |

## 2. Matrice honnête des fonctions : Android aujourd'hui, iPhone demain

Légende : **POSSIBLE** (même résultat pour l'usager), **DÉGRADÉ** (possible avec une limite dite), **IMPOSSIBLE** (interdit ou sans API publique pour une app tierce). Version : v1 = première TestFlight, v2/v3 = § 5.

| # | Fonction du téléphone | Android (aujourd'hui) | iPhone | API iOS exacte et limite | Version |
|---|---|---|---|---|---|
| F1 | Lier un téléphone à une TV (PIN) | « Ajouter ma TV » : appairage Bluetooth SSP + PIN/« Autoriser », jeton par HELLO RFCOMM | **POSSIBLE** (par Wi-Fi) | PIN saisi dans l'app, envoyé une fois à `POST /api/pair/pin` (neuf, wios-tv-02) avec la clé publique Ed25519 de l'iPhone (`CryptoKit` `Curve25519.Signing`, clé dans le **trousseau**, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) ; jeton 12 h renouvelé par signature d'un défi, **sans** Bluetooth ; compté dans les 8 | v1 |
| F2 | Trouver la TV sur la box | mDNS `NsdManager` + UPnP | **POSSIBLE** | `NWBrowser` (`Network`) sur `_castbridge._tcp` ; **`NSBonjourServices`** = `["_castbridge._tcp"]` et **`NSLocalNetworkUsageDescription`** obligatoires ; invite système « réseau local » (iOS 14+) à la première recherche ; refus ⇒ tout le LAN échoue en silence (`NWPath` `unsatisfied` / erreur `-65555`) : l'app doit le détecter et renvoyer vers Réglages › CastBridge › Réseau local ; connexion par **adresse IPv4 littérale** (la TV refuse un `Host` non IP) | v1 |
| F3 | Joindre la TV sans point d'accès (Wi-Fi Direct) | `WifiP2pManager.connect(nom + mot de passe)` sans boîte (Android 13+) | **DÉGRADÉ** | **pas de P2P** ; l'iPhone rejoint le groupe de la TV **comme client Wi-Fi WPA2** : `NEHotspotConfiguration(ssid: "DIRECT-CB-…", passphrase:, isWEP: false)`, `joinOnce = false`, capacité **Hotspot Configuration** (entitlement `com.apple.developer.networking.HotspotConfiguration`, disponible aux comptes payants) ; **une invite système** « CastBridge souhaite rejoindre le réseau Wi-Fi … » à la première configuration ; l'iPhone **quitte** alors la box s'il y était (une seule association Wi-Fi) ; ne marche **pas** dans le simulateur ; exige que la TV soit propriétaire de groupe (B-W18-1) **et** expose SSID + mot de passe (QR v1, BLE v2) | v1 (QR) |
| F4 | Envoyer une vidéo / photo (« Copier sur la TV et lire ») | `UploadService`, transfert par blocs, file persistée, R-08…R-17 | **POSSIBLE** (au premier plan) / **DÉGRADÉ** (en fond) | `PhotosPicker` (iOS 16) ou `UIDocumentPicker` ; protocole TV existant `/api/transfer/*` (blocs 1-8 Mio, SHA-256) ou `/upload` simple ; premier plan : `URLSession` normal ; en fond : `beginBackgroundTask` (≈ 30 s), **`URLSession` d'arrière-plan** (`background(withIdentifier:)`, un `uploadTask(fromFile:)` par bloc écrit en fichier temporaire, exécuté par le système, planification **non garantie**, relances de l'app limitées par iOS) et, **iOS 26+ à vérifier sur SDK 27**, `BGContinuedProcessingTask` (tâche lancée par l'usager, progression système, interruptible) | v1 |
| F5 | « Lire en direct » (la TV lit depuis le téléphone, `:8089`) | `MediaServer` + service de premier plan | **DÉGRADÉ** | serveur HTTP local par `NWListener` **seulement app ouverte** (suspendu quelques secondes après le passage en fond) ; écran à garder allumé (`isIdleTimerDisabled`) ; même adresse source vers la TV (W18 `LocalAddress.toward`) | v2 |
| F6 | Copier des fichiers quelconques vers la TV | sélecteur SAF, « Échange de fichiers » | **POSSIBLE** | `UIDocumentPickerViewController` (Fichiers, iCloud Drive, clés USB-C branchées à l'iPhone) ; accès à sécurité limitée (`startAccessingSecurityScopedResource`) | v1 |
| F7 | Feuille de partage « Envoyer à la TV » / « Ouvrir avec » | `ShareToTvActivity`, `OpenWithActivity` | **DÉGRADÉ** | **extension de partage** (`NSExtension` `com.apple.share-services`) : mémoire ≈ 120 Mo, durée courte ⇒ l'extension **ne copie pas** : elle dépose une référence/copie dans le conteneur **App Group** et met l'envoi en file (ou lance une tâche `URLSession` d'arrière-plan à conteneur partagé) ; « Ouvrir avec » = types de documents (`CFBundleDocumentTypes`, `LSSupportsOpeningDocumentsInPlace`) | v1 (partage), v2 (ouvrir avec) |
| F8 | Gros fichiers (2-4 Go) | reprise par blocs, `.part`, sidecar | **DÉGRADÉ** | reprise identique (protocole TV) ; vidéo de la photothèque **optimisée iCloud** ⇒ téléchargement préalable par `PHAssetResourceManager` (peut consommer des **données mobiles** : demander) ; copie temporaire dans le conteneur (espace ×2 au pire) ; FAT32 > 4 Go refusé par la TV (`/api/storage/check`) | v1 (≤ 4 Go conseillé) |
| F9 | Progression de la copie (notification) | notification de service de premier plan, `TransferStatusLine` | **DÉGRADÉ** | **Live Activity** (`ActivityKit`, iOS 16.1+, extension Widget, `NSSupportsLiveActivities`) mise à jour **tant que l'app tourne** ; sans serveur de notifications (aucun en v1), elle se fige quand l'app est suspendue (`staleDate` + texte « ouvrez CastBridge pour continuer ») ; notification locale de fin/échec (`UNUserNotificationCenter`) quand la session d'arrière-plan réveille l'app | v1 |
| F10 | Télécommande de la lecture de la TV | HTTP (`RemoteController`), Bluetooth CBTR, clavier HID (`BtHidRemote`), infrarouge, app du fabricant | **POSSIBLE** (HTTP) / **IMPOSSIBLE** (HID, IR, CBTR) | routes existantes `/api/pause|resume|stop|seek|volume|player/*|playlist` ; `BluetoothHidDevice` n'existe pas sur iOS ; pas d'émetteur IR | v1 (base), v2 (pistes, sous-titres) |
| F11 | Bibliothèque de la TV (parcourir, lire, miniatures) | `TvLibraryScreen` | **POSSIBLE** | `GET /api/library`, `/api/thumb` (202 = réessayer), `POST /api/play` | v1 (lecture seule + lire) |
| F12 | TV → téléphone (télécharger) | `DownloadService`, `Range` | **POSSIBLE** / **DÉGRADÉ** en fond | `URLSession` `downloadTask` avec reprise (`Range` via `/stream/<nom>`), enregistrement dans Fichiers ou Photos (`NSPhotoLibraryAddUsageDescription`) | v2 |
| F13 | Compagnon Quiz (jouer depuis le téléphone) | page `/quiz` de la TV, QR de salle | **POSSIBLE** | **déjà possible aujourd'hui depuis Safari** sur le même réseau ; dans l'app : `WKWebView` limité à l'origine de la TV (pas de navigation libre) ; aucun appel au service de jeu (I-2) | v1 |
| F14 | Ouvrir le Quiz / échecs / sudoku sur la TV | `POST /api/quiz/open`, `/api/chess/open` | **POSSIBLE** | mêmes routes (PIN/jeton) | v1 (Quiz), v3 (jeux) |
| F15 | Boutique, location, lots Apprendre/Quiz, Langues | `LotsRuntime` (100 Mo → 500 Mo décidé), `RentalDeliveryActivity`, `StoreApi` | **DÉGRADÉ** (lots libres) / **RISQUE App Review** (payants) | contenu numérique payant consommé via l'app ⇒ règle 3.1.1 (achat intégré) sauf exceptions étroites ; lots **libres** (CC BY-SA Langues) possibles ; lots payants : **non** tant qu'une analyse App Store n'est pas faite | v3 (libres), payants : décision |
| F16 | Paquets de langues | lots Langues (46 lots libres) | **POSSIBLE** (libres) | téléchargement serveur → iPhone → TV par HTTP (`URLSession`), quota local à décider | v3 |
| F17 | Console du propriétaire | gabarit indistribuable, clés de signature | **EXCLU** (consigne) | jamais dans l'app iOS ; aucun code superadmin, aucune clé privée, aucun canal propriétaire | — |
| F18 | Coursier de télémétrie / d'ordres | job de fond 15 min/12 h, `GET /api/tele/outbox`, trames Bluetooth d'ordres | **DÉGRADÉ** | à l'ouverture de l'app + `BGAppRefreshTask` (opportuniste, quelques fois par jour **au mieux**, jamais garanti) ; routes HTTP seulement (les trames Bluetooth d'ordres sont inaccessibles) ; ajoute des appels Internet ⇒ étiquettes de confidentialité à revoir | v3 |
| F19 | Passerelle Bluetooth (Internet du téléphone vers la TV) | `BtGatewayService` (SOCKS par RFCOMM) | **IMPOSSIBLE** | aucun RFCOMM ; partage de connexion iOS non pilotable par une app (et écarté par le propriétaire pour la voie de données) | — |
| F20 | SSH par Bluetooth, tunnel API par Bluetooth | `BtSshGateway` | **IMPOSSIBLE** | aucun RFCOMM | — |
| F21 | Wi-Fi Direct (créer, découvrir des pairs P2P, DNS-SD P2P) | `WifiP2pManager` | **IMPOSSIBLE** | aucune API ; « Wi-Fi Aware » (framework annoncé pour iOS 26, **à vérifier**) n'est pas Wi-Fi Direct et exigerait que la TV le parle (`android.hardware.wifi.aware` inconnu) : piste v3, **non promise** | — |
| F22 | Remise d'identifiants par Bluetooth | HELLO/CBTN sur RFCOMM | **REMPLACÉ** | **QR** (v1) ; **BLE GATT** via `CoreBluetooth` (rôle central) si la TV sait être périphérique BLE (v2, conditionnel) ; `AccessorySetupKit` (iOS 18) envisageable pour une découverte BLE/SSID plus douce, **à vérifier** | v1 / v2 |
| F23 | Fonctionnement en fond | services de premier plan (`dataSync`, `connectedDevice`), jobs 15 min | **DÉGRADÉ** | l'app est **suspendue** quelques secondes après le passage en fond ; restent : `URLSession` d'arrière-plan, `BGAppRefreshTask`, `BGProcessingTask` (charge + inactif), `BGContinuedProcessingTask` (iOS 26+, à vérifier) ; **aucune** reconnexion, recherche ou serveur permanent | v1 (dit à l'usager) |
| F24 | Activer une TV (mode porteur) | `ActivateTvActivity`, trame `ACTIVATION` par Bluetooth | **DÉGRADÉ** (conditionnel) | Bluetooth impossible ; par HTTP seulement si la TV verrouillée accepte `POST /api/activation/install` sur LAN/Wi-Fi Direct (wios-tv-05, décision) ; vérification `cbx1` locale par `CryptoKit` (Ed25519) contre les 109 vecteurs | v2 (décision) |
| F25 | Contrôle parental (boîte de rapports, réglages) | `Parental*` (≈ 2 000 lignes) | **POSSIBLE** | routes HTTP existantes ; clé de signature des rapports dans le trousseau | v3 |
| F26 | Assistant de bibliothèque, classement | `agent/*`, SAF | **DÉGRADÉ** | Photos/Fichiers seulement, pas d'accès au système de fichiers global | v3 |
| F27 | Mises à jour de l'app | `PhoneUpdater` (APK vérifié) | **IMPOSSIBLE** (par l'app) | App Store / TestFlight uniquement ; règle 2.5.2 : aucun code exécutable téléchargé | — |
| F28 | Relais d'APK de la TV | dossier `Download` de la clé | **EXCLU** | risque 2.5.2 ; reste un geste Mac/Android | — |
| F29 | DLNA vers d'autres TV, télécommande d'autres marques | `Upnp`, `SmartRemote` | **DÉGRADÉ** | SSDP multicast exige l'entitlement **Multicast Networking** (demande à Apple, accordée au cas par cas) ; hors périmètre CastBridge-TV | hors feuille de route |
| F30 | Jouer en ligne (service de jeu, portefeuille) | jamais (règle W20 I-2, W22 J4) | **INTERDIT par le produit** | l'app iOS n'appelle **jamais** `castbridge-play` ni l'API portefeuille ; test de source (§ 7) | — |

**Bilan chiffré** (verdict principal de chaque ligne) : sur 30 lignes, **11 POSSIBLE** (F1, F2, F4, F6, F10, F11, F12, F13, F14, F16, F25), **11 DÉGRADÉ** (F3, F5, F7, F8, F9, F15, F18, F23, F24, F26, F29), **1 REMPLACÉ** (F22), **4 IMPOSSIBLE** (F19, F20, F21, F27), **3 EXCLU/INTERDIT** par consigne (F17, F28, F30). Tout ce qui est « IMPOSSIBLE » l'est par la plateforme, pas par manque d'effort.

## 3. La liaison : comment un iPhone joint la TV

### 3.1 Les trois situations réelles

```
 (A) MÊME WI-FI (≈ 15 % des foyers : box)            (B) PAS DE POINT D'ACCÈS (≈ 85 %)                    (C) AUCUN RÉSEAU COMMUN
 ┌────────┐   Wi-Fi   ┌─────┐   Wi-Fi/Eth  ┌────┐     ┌────────┐  WPA2 client   ┌──────────────────────┐       ┌────────┐      ┌────┐
 │ iPhone │──────────►│ box │◄─────────────│ TV │     │ iPhone │───────────────►│ TV = propriétaire de │       │ iPhone │  ✘   │ TV │
 └────────┘           └─────┘              └────┘     └────────┘ SSID DIRECT-CB-│ groupe 192.168.49.1  │       └────────┘      └────┘
  Bonjour _castbridge._tcp ─► IP:8765                  ▲ identifiants par   └──────────────────────┘        ni Wi-Fi commun, ni groupe de la TV
  /api/hello ─► PIN ─► jeton                            │ QR (v1) / BLE (v2)                                 (puce sans GO ni AP, Wi-Fi TV éteint)
  aucun changement TV pour la copie                    /api/hello ─► PIN ─► jeton                           ⇒ IMPOSSIBLE sur iPhone (pas de RFCOMM)
```

| Situation | Découverte | Jonction | Authentification | Changements TV | Délai attendu (non mesuré) |
|---|---|---|---|---|---|
| **A** même Wi-Fi | Bonjour (2 s) ; sinon saisie de l'IP affichée par la TV | aucune | PIN une fois ⇒ jeton (wios-tv-02) ; en **v0 démo** : PIN à chaque requête (existant) | aucun pour copier/télécommander ; wios-tv-02 pour la règle des 8 | 2-4 s |
| **B** sans point d'accès | QR affiché par la TV : `castbridge://tv?v=1&id=<8 hex>&ip=192.168.49.1&port=8765&wd=<SSID>&wp=<mot de passe>` (W18 D-W18-5 étendu) ; ou QR `WIFI:` lu par l'appareil photo d'iOS (puis l'app trouve 192.168.49.1) | `NEHotspotConfiguration` ⇒ invite iOS « Rejoindre » (1 fois) ⇒ association WPA2 + DHCP | PIN une fois ⇒ jeton | **w18-01 + w18-07** (groupe persistant, allumé à l'écran) + **wios-tv-03** (QR « Ajouter un iPhone ») ; BLE : wios-tv-04 (v2) | 5-12 s (première fois, hors saisie) ; 3-8 s ensuite (iOS rejoint seul un réseau connu) |
| **B′** variante SoftAP de la TV (si verdict W18 « AP oui, GO non ») | QR **à chaque session** (identifiants tirés par Android à chaque démarrage) | idem, invite à chaque nouveau SSID | idem | w18-07 version (b) | 10-20 s, un scan par session |
| **C** aucun réseau | — | — | — | aucun possible | **impossible** : texte « Votre iPhone ne peut joindre cette TV que par Wi-Fi. Allumez le Wi-Fi Direct de la TV (MENU › Connexion) ou reliez-les à la même box. » |

**Ce que la TV doit exposer pour l'iPhone (situation B)** : (1) un groupe Wi-Fi Direct **allumé** quand l'iPhone arrive (D-W18-3 : tant que CastBridge-TV est à l'écran et sans LAN ; D-IOS-5 étend : aussi quand un iPhone de confiance existe), car l'iPhone **ne peut pas** demander la création du groupe par Bluetooth (CBTN) ; (2) un **SSID et un mot de passe persistants** (D-W18-2), sinon chaque session exige un nouveau QR ; (3) le QR sur demande (MENU › Connexion › « Ajouter un iPhone »), jamais sur l'accueil (une photo de l'écran vaut le mot de passe, W18 § 2.1 f) ; (4) l'API HTTP joignable sur 192.168.49.1:8765 (existant).

**Pourquoi le QR d'abord et pas le BLE** : le QR n'exige **aucun** matériel inconnu (la TV sait déjà dessiner un QR), prouve la présence physique devant l'écran, et l'appareil photo d'iOS le comprend nativement ; le BLE exige que le contrôleur Bluetooth de la TV sache **annoncer** en BLE (`BluetoothLeAdvertiser`, `isMultipleAdvertisementSupported`), ce que personne n'a mesuré, et une poignée de main cryptographique neuve (audit). Le BLE apporterait seulement « sans viser l'écran » et « demander à la TV d'allumer son groupe ».

### 3.2 Première liaison d'un iPhone (situation B, parcours v1)

```
 iPhone (CastBridge)                                  CastBridge-TV
 ─────────────────────                                ───────────────
 « Ajouter ma TV »                                    MENU › Connexion › « Ajouter un iPhone »
   ├─ Bonjour 3 s : rien (pas de box)                   └─ QR castbridge://tv?v=1&id=3f9a…&ip=192.168.49.1&port=8765&wd=DIRECT-CB-k7m2qx&wp=•••• (10 min)
   ├─ « Scannez le QR affiché par la TV » (caméra : NSCameraUsageDescription)
   ├─ NEHotspotConfiguration(DIRECT-CB-k7m2qx, ••••) ─► invite iOS « Rejoindre » ─► association WPA2, DHCP 192.168.49.x
   ├─ GET http://192.168.49.1:8765/api/hello ────────► {"app","v","id":"3f9a…","pinLen":6,"pair":["pin-http-v1"]}
   ├─ id du QR == id de hello ? sinon « Ce n'est pas la TV scannée »
   ├─ « Tapez le code affiché sur la TV » [ _ _ _ _ _ _ ]
   ├─ POST /api/pair/pin  X-CB-Pin  {name:"iPhone de …", pub:<Ed25519 32 o b64>, platform:"ios"} ─►  PinGuard (IP) ; TrustRegistry.trust("key:<kid>", nom)
   │                                                    ├─ plein (8) ⇒ 409 {"error":"full","phones":[noms]} ⇒ « Retirez un téléphone sur la TV »
   │◄──────────────────────────── 200 {"token","ttlMs","tvId","tvName"}  ⇐ bandeau TV « iPhone de … est maintenant de confiance »
   └─ trousseau : par tvId {clé privée, jeton, SSID, mot de passe Wi-Fi, dernière IP} ; carte verte « Salon · Par le Wi-Fi de la TV »

 sessions suivantes : iOS rejoint seul DIRECT-CB-k7m2qx (réseau connu) ou l'app le redemande ; jeton vivant ⇒ rien ;
 jeton expiré ⇒ GET /api/pair/challenge ⇒ POST /api/pair/renew {kid, signature(défi‖tvId)} ⇒ nouveau jeton, sans PIN.
```

**Pourquoi une clé d'appareil** : sur Android, le jeton se renouvelle par HELLO Bluetooth (le lien appairé prouve l'identité). L'iPhone n'a pas ce lien : sans clé, il faudrait retaper le PIN toutes les 12 h ou garder le PIN (ce que fait aujourd'hui `PinBook`, toléré mais moins bon). Une clé Ed25519 propre à l'iPhone, gardée dans le trousseau, rend le renouvellement silencieux, révocable (« Retirer ce téléphone » sur la TV efface la clé publique) et compté dans les 8. La TV vérifie avec l'Ed25519 Kotlin pur déjà présent (`C/update/Ed25519.kt`). L'enclave sécurisée d'Apple ne sait faire que P-256 : refusé pour garder **une seule** courbe de signature côté TV.

### 3.3 Changements TV (additifs, versionnés, rétrocompatibles)

| Brief | Changement | Où | Version / compatibilité | Gel | Audit Opus |
|---|---|---|---|---|---|
| **wios-tv-01** | vecteurs partagés Kotlin → Swift (`tools/ios-vectors/*.json`) ; **TV factice exécutable** (`FakeTvMain` : vraie `ReceiverServer` sur 127.0.0.1, PIN de test) pour les tests du simulateur | `C/` tests + `tools/ios-vectors/` + une tâche Gradle `JavaExec` | aucun changement de comportement | permis (cœur/test) | non |
| **wios-tv-02** | appairage HTTP `pin-http-v1` : `POST /api/pair/pin`, `GET /api/pair/challenge`, `POST /api/pair/renew` ; `/api/hello` additif (`id`, `pinLen`, `pair`) ; clés `key:<kid>` dans `TrustRegistry` (même plafond de 8) ; `PinGuard` par IP ; autorisé en essai, refusé en état verrouillé | `C/trust/PinPairing.kt` (neuf, pur), `C/tv/PairRoutes.kt` (neuf), une ligne de délégation dans `ReceiverServer` | une TV ancienne répond 404 ⇒ l'iPhone passe en mode PIN par requête **interne seulement** (v0) ; un ancien téléphone Android ignore les champs neufs de `hello` | permis (cœur pur + une ligne) | **obligatoire** |
| **wios-tv-03** | écran TV « Ajouter un iPhone » (QR v1) ; ligne iPhone dans « Téléphones » (nom, « iPhone · Wi-Fi », retrait = révocation) ; TXT mDNS additif `id`, `pair` (aucun secret) ; groupe Wi-Fi Direct gardé allumé si un iPhone de confiance existe (D-IOS-5) | `R/` (écrans, `TvService` zone mDNS) | additif ; dépend de w18-07 pour `wd`/`wp` persistants (sans w18-07 : QR sans `wd`, situation A seulement) | **après le gel** ou exception du propriétaire | échantillon |
| **wios-tv-04** (v2, conditionnel) | sonde BLE (faits) puis, si possible, service GATT « CastBridge-TV » : lecture d'identité, poignée de main X25519 (`C/owner/X25519.kt` existant) + confirmation par PIN (HMAC), remise chiffrée (AES-GCM) de SSID/mot de passe, commande « allumer le groupe » | `C/ble/` (pur) + `R/BleHandoff.kt` | additif ; TV sans BLE ⇒ sonde « non », rien d'autre | après le gel | **obligatoire** |
| **wios-tv-05** (v2, décision) | activation d'une TV verrouillée par HTTP (`POST /api/activation/install` en état `Locked`, LAN/Wi-Fi Direct seulement, borné) pour le mode porteur iPhone | `C/owner/FeatureGate.kt` (liste blanche, test `theLockedSurfaceIsExactlyTheActivationSurface` modifié **volontairement**) | additif | décision du propriétaire | **obligatoire** |

**Builds TV** : toujours verrouillés (`-PrequireActivation=true`), copiés dans le `Download` de la clé USB par le coordinateur (règles du propriétaire) ; aucun cahier de cette vague ne produit de build TV déverrouillé.

### 3.4 Sécurité de la liaison iPhone

| Sujet | Règle |
|---|---|
| PIN | tapé une fois ; envoyé **une seule fois** dans `X-CB-Pin` (jamais en URL, jamais journalisé, jamais stocké sur l'iPhone après l'appairage en v1) ; `PinGuard` 5 échecs / 60 s par IP (existant) ; sur la box il circule **en clair** (limite déjà connue d'Android, `BT-PLUG-AND-PLAY.md:130`) ; sur le groupe de la TV il est protégé par WPA2 |
| Jeton | 256 bits, 12 h, en `X-CB-Token`, haché côté TV (existant) ; en clair sur la box (même limite) ; un jeton refusé n'est jamais rejoué (`CredentialGate`, règle portée en Swift) |
| Clé d'appareil | Ed25519, trousseau `AfterFirstUnlockThisDeviceOnly` (non sauvegardée, non synchronisée iCloud) ; réinstallation ⇒ nouvel iPhone (nouveau PIN) ; signature sur `castbridge-pair-renew-v1\n<tvId>\n<défi>` (domaine séparé) ; défi aléatoire 32 o, usage unique, 2 min, horloge monotone de la TV |
| Mot de passe Wi-Fi Direct | reçu par QR (présence physique) ou BLE chiffré (v2) ; gardé dans le trousseau ; jamais affiché après coup, jamais dans un journal, un `description`, une URL de journal ; rotation sur « Retirer ce téléphone » (D-W18-7) ⇒ l'iPhone voit l'échec d'association ⇒ « Le mot de passe Wi-Fi de la TV a changé : scannez le nouveau QR » |
| 8 téléphones | même `TrustRegistry` ; un iPhone de plus que 8 ⇒ refus explicite, jamais d'éviction silencieuse |
| Service de jeu, portefeuille | aucune adresse Internet dans `I/` en v1 (test de source) |
| Confusion de TV | l'`id` du QR doit égaler l'`id` de `/api/hello` ; 192.168.49.1 n'est jamais une identité (DESIGN-TV-CONTEXT § 1.1) |
| TLS | non en v1 (la TV n'a pas de certificat) ; v3 possible : certificat auto-signé de la TV épinglé par empreinte transmise dans le QR |

## 4. Stratégie de code

### 4.1 Les trois options, chiffrées

| Critère | (a) **Swift natif** + sous-ensemble du cœur + vecteurs partagés | (b) **Kotlin Multiplatform** de `C/` | (c) **UI multiplateforme** (Compose Multiplatform, Flutter) |
|---|---|---|---|
| Ce qui est réutilisé | la **spécification** (docs + code Kotlin lu) et les **vecteurs** générés par Kotlin ; aucun binaire partagé | le code du cœur compilé pour iOS (Kotlin/Native) | (CMP) une partie des 56 fichiers Compose ; (Flutter) rien |
| Ce qui bloque | rien de technique ; risque de divergence filaire (parade : vecteurs + TV factice) | **217 / 428 fichiers** importent `java.*`/`javax.*` : `java.io` 138 fichiers (276 lignes, `File` ×85, `IOException` ×88), `java.security` 64 (`MessageDigest` ×48, `SecureRandom` ×24), `java.net` 45 (`HttpURLConnection` ×18, `Socket` ×10), `java.util.concurrent` 31, `java.util.Base64` 30, `java.time` 23 (67 lignes), `java.nio` 12, `java.util.zip` 11, `javax.crypto` 8 (24 lignes : AES-GCM, HMAC), `javax.net` 3 ; **104 fichiers / 693 lignes** `synchronized`/`@Volatile`, 46 fichiers `Thread`, 103 fichiers `String.format`/`.format(`, 27 fichiers `System.currentTimeMillis` ; seuls **140 fichiers / 12 727 lignes** (20 %) sont candidats directs à `commonMain` | les écrans appellent directement les API Android (**77 / 90 fichiers**, 507 lignes d'import) : Bluetooth, Wi-Fi P2P, services, MediaStore, SAF, Media3 ; il faudrait **réécrire** toutes les couches plateforme de toute façon |
| Taille du chantier | sous-ensemble Kotlin concerné ≈ **3 200 lignes** (`TvClient` 456, `TransferClient` 180, `Blocks` 93, `HttpConn` 147, `Scheduler` 169, `TransferProgress` 184, `WriteFailureClassifier` 78, `PinBook` 323, `Credentials` 51, `LinkMachine` 242, `LinkText` 144, `TvSignal` 163, `TransferStatusLine` 46, `TvEndpointResolver` 86, `DedupDecision` 145, `JsonLite` 96, `Envelope` 107, `Ed25519` 110, …) ⇒ ≈ **2 500-3 500 lignes Swift** de logique + ≈ **3 000-4 000 lignes** SwiftUI/extensions + ≈ 2 000 lignes de tests | refonte d'**≈ 290 fichiers** (`expect/actual` pour fichiers, hachage, aléa, HTTP, temps, verrous), passage de `:core` (module JVM consommé par `:receiver`, `:sender`, `:sshd`, `server-play`) au plugin KMP, Kotlin/Native sur le Mac ; **≥ 25-35 agent·jours** avant la première ligne d'écran iOS | CMP : écrans à refaire à 60-70 % + couches plateforme iOS natives quand même ; Flutter : 100 % à refaire en Dart + canaux natifs pour NEHotspot, ActivityKit, extension de partage |
| Risque pour Android et la TV | **nul** (répertoire neuf `ios/`, TV seulement par cahiers additifs) | **élevé** : touche le cœur de la TV et du serveur de jeu pendant le gel W15 ; R-01…R-18 à re-prouver | faible pour Android si séparé, mais deux bases d'UI |
| Fonctions Apple (NEHotspot, ActivityKit, extension de partage, tâches de fond, trousseau) | **natives, directes** | via Swift de toute façon (interop) | via ponts natifs |
| Taille de l'app, App Review | petite (≈ 5-10 Mo estimé), conforme | + runtime Kotlin/Native (≈ +5-10 Mo estimé) | + moteur (Flutter ≈ +10-20 Mo estimé) |
| Builds sur ce Mac | `swift test` (secondes) + `xcodebuild` | Gradle KMP + Xcode (deux chaînes lourdes, verrou commun) | Gradle + Xcode, ou Flutter SDK (à installer : réseau) |
| Parité filaire | prouvée par vecteurs + tests d'intégration contre la vraie `ReceiverServer` | automatique pour le code partagé | dépend de la couche |
| Coût v1 estimé | **≈ 24 agent·jours, 21-26 $** | ≈ 25-35 agent·jours de refonte **plus** ≈ 15 agent·jours d'iOS : **≈ 40-50 agent·jours, 40-60 $** | ≈ 30-40 agent·jours, 30-45 $ |

**Recommandation (D-IOS-1) : (a) Swift natif.** Le produit iOS v1 n'a besoin que de 5 % du cœur (client HTTP de la TV, transfert par blocs, appairage, état de liaison, textes) ; le reste du cœur (Quiz, Apprendre, lots, parental, portefeuille, activation, tunnel) tourne **sur la TV** ou n'a pas de sens sur iPhone. La parité est garantie par la méthode déjà éprouvée dans le dépôt : **le Kotlin produit des vecteurs JSON, le Swift les rejoue** (comme `tools/activation/verify_vectors.py` rejoue les 109 vecteurs écrits par Kotlin), plus des tests d'intégration du client Swift contre **la vraie `ReceiverServer` Kotlin** lancée sur le Mac. KMP reste une option **v3** pour un sous-ensemble pur (textes, décisions) si la double maintenance pèse, sur mesure.

### 4.2 Organisation du code iOS (`ios/`)

```
ios/
├── CastBridgeKit/                   paquet SwiftPM, logique pure (Foundation, CryptoKit, Network) — testé par `swift test` sur macOS ET par xcodebuild sur simulateur
│   ├── Package.swift
│   ├── Sources/CBCore/              Json, Hex, Sha256, Ed25519 (CryptoKit), Vectors (chargeur)
│   ├── Sources/CBTv/                TvClient, TvCredential, TvReason, TvHello, Library, Player
│   ├── Sources/CBTransfer/          Manifest/Blocks, BlockMap, TransferEngine, SimpleUpload, RetryPolicy, StatusLine
│   ├── Sources/CBLink/              QrPayload, TvEndpoint, LinkState, TvSignalText (français)
│   ├── Sources/CBPair/              PinPairing (pin-http-v1), DeviceKey, TvBook (trousseau abstrait)
│   └── Tests/…                      un dossier par module ; vecteurs lus depuis tools/ios-vectors/
├── CastBridge/                      app SwiftUI (cible iOS) : écrans, Info.plist, PrivacyInfo.xcprivacy, Localizable.xcstrings (fr)
├── CastBridgeShare/                 extension de partage
├── CastBridgeActivity/              extension Widget (Live Activity de copie)
├── CastBridgeUITests/               XCUITest (simulateur, TV factice)
├── CastBridge.xcodeproj             projet à dossiers synchronisés (Xcode 16+), aucune équipe ni profil écrits en dur
└── docs/                            README de build, plan de test humain, notes App Review (sans secret)
```

### 4.3 Ce que le Mac fait, ce qui exige un iPhone, ce qui exige le propriétaire

| Activité | Mac seul (agent) | Simulateur iOS 27 (agent) | Vrai iPhone | Propriétaire |
|---|---|---|---|---|
| Logique, vecteurs, crypto (`swift test`) | ✔ | ✔ | — | — |
| Client HTTP contre la vraie `ReceiverServer` (TV factice 127.0.0.1) | ✔ | ✔ (le simulateur partage le réseau du Mac ; boucle locale hors invite « réseau local ») | — | — |
| Écrans, navigation, accessibilité, français | — | ✔ (XCUITest) | à confirmer | relecture |
| Photos : `xcrun simctl addmedia` | — | ✔ | ✔ photos réelles HEIC/HEVC, iCloud | — |
| Extension de partage | — | ✔ (petits fichiers) | ✔ vidéo 2 Go, mémoire | — |
| Live Activity | — | ✔ affichage | ✔ écran verrouillé, Dynamic Island | — |
| Bonjour sur la box, invite « réseau local » | — | partiel (réseau du Mac, pas d'invite fidèle) | ✔ | geste « Autoriser » |
| `NEHotspotConfiguration` (groupe de la TV) | — | ✘ (non pris en charge) | ✔ | TV allumée, QR |
| BLE (`CoreBluetooth`) | — | ✘ (pas de Bluetooth dans le simulateur) | ✔ | TV |
| Fond (`URLSession` d'arrière-plan, tâches) | — | partiel, peu fidèle | ✔ | — |
| Débits réels | — | — | ✔ | mesure avec la TV |
| Signature, installation sur appareil | ✘ (aucun build signé par un agent : D-IOS-12) | — | — | ✔ (Xcode, « Mode développeur » de l'iPhone) |
| Archive, téléversement, TestFlight | ✘ | — | — | ✔ |
| Tests sur iOS 16-26 | ✘ (un seul environnement installé : iOS 27.0) | — | ✔ (iPhone du propriétaire, version inconnue) | télécharger d'autres environnements si voulu |

## 5. MVP et feuille de route

### 5.1 v1 (première TestFlight interne) : le plus petit produit utile

1. **Ajouter ma TV** : recherche Bonjour (situation A) ; « Scanner le QR de la TV » (situation B, rejoint le groupe) ; « Saisir l'adresse » (secours) ; PIN ⇒ jeton (`pin-http-v1`) ; plusieurs TV, une par défaut ; « Oublier cette TV ».
2. **Envoyer** : depuis l'app (Photos, Fichiers) **et** la feuille de partage (« CastBridge-TV ») ; choix *Copier sur la TV et lire* / *Copier sur la TV* (vocabulaire de `UiTexts`) ; vérification d'espace (`/api/storage/check`) ; doublons (`/api/have`) ; reprise ; file persistée ; photo HEIC convertie en JPEG par défaut (D-IOS-14).
3. **Progression** : barre dans l'app, **Live Activity**, notification de fin/échec ; ligne d'état unique (sémantique `TvSignal` / `TransferStatusLine`) ; échec visible avec raison, réessais bornés (S-1, S-2 de W19).
4. **Télécommande de base** : lecture/pause, ±10 s/±30 s, barre de position, volume, arrêt, suivant/précédent ; état 1 Hz par `/api/info`.
5. **Bibliothèque de la TV** : liste avec miniatures, « Lire sur la TV ».
6. **Quiz** : « Ouvrir le Quiz sur la TV » + jouer par la page `/quiz` dans une vue web limitée à la TV.
7. **Mode démonstration** (pour App Review et les essais sans TV) : TV simulée dans l'app, clairement marquée « Démonstration ».

**Explicitement PAS en v1** : boutique, location, lots, Langues, Apprendre, contrôle parental, échecs/jeux, téléchargement TV → iPhone, « Lire en direct », pistes/sous-titres, activation d'une TV, coursier/télémétrie, BLE, tout appel Internet, tout achat, toute clé de déblocage, iPad optimisé (fonctionne en mode iPhone).

### 5.2 Feuille de route

| Version | Contenu | Dépend de | Effort estimé |
|---|---|---|---|
| **v0** (interne, propriétaire seul) | v1 sans wios-tv-02 : PIN envoyé à chaque requête, **non compté dans les 8** ⇒ jamais en TestFlight externe (D-IOS-15) | rien côté TV | inclus dans v1 |
| **v1** | § 5.1 | wios-tv-01/02/03, w18-01, w18-07 (situation B), w19-02 (souhaitable) | ≈ 24 agent·jours |
| **v2** | BLE (si la TV sait), « Lire en direct » (app ouverte), téléchargement TV → iPhone, pistes/sous-titres/playlist, « Ouvrir avec », export vidéo « compatible » H.264 720p, activation d'une TV par HTTP (si décidé), `BGContinuedProcessingTask` | wios-tv-04/05, décisions | ≈ 15 agent·jours (estimé) |
| **v3** | lots libres et Langues, contrôle parental, échecs/sudoku à distance, coursier (`BGAppRefreshTask`), TLS épinglé, iPad, éventuel KMP d'un sous-ensemble pur | analyse App Store des contenus, étiquettes de confidentialité revues | ≈ 20 agent·jours (estimé) |

## 6. Côté Apple

### 6.1 Identité de l'app (D-IOS-7)

- **Identifiant de paquet proposé** : `com.sti-cm.castbridge` (domaine du serveur du propriétaire, ordre inverse ; le trait d'union est permis) ; extensions `com.sti-cm.castbridge.share`, `com.sti-cm.castbridge.activity` ; groupe d'apps `group.com.sti-cm.castbridge`. L'Android reste `castbridge.sender` (aucune contrainte de correspondance).
- **Nom affiché** : « CastBridge » (règle des noms). Le nom sur l'App Store est **unique au monde** : s'il est pris (non vérifié), « CastBridge – pour CastBridge-TV » en nom de fiche, « CastBridge » sous l'icône.
- **Capacités** : App Groups, Hotspot Configuration ; (v2) Access Wi-Fi Information si l'app doit lire le SSID courant (autorisé pour un réseau qu'elle a configuré). **Pas** de notifications distantes, **pas** de Multicast Networking en v1.

### 6.2 Ce que seul le propriétaire fait (aucun identifiant Apple demandé, écrit ou stocké par un agent)

1. **Xcode › Réglages › Comptes** : vérifier que son compte (équipe payante) est présent (une identité « Apple Development » l'est déjà dans le trousseau du Mac).
2. **Signature automatique** : ouvrir `ios/CastBridge.xcodeproj`, choisir **son** équipe dans « Signing & Capabilities » pour les trois cibles ; Xcode crée l'App ID, le groupe d'apps, les profils, et le certificat **Apple Distribution** au premier archivage (gérés par Apple). Alternative manuelle (developer.apple.com › Identifiers / Certificates / Profiles) : non nécessaire.
3. **iPhone** : le brancher, accepter « Faire confiance », activer **Réglages › Confidentialité et sécurité › Mode développeur** (redémarrage), lancer depuis Xcode (build de développement).
4. **App Store Connect › Apps › « + » Nouvelle app** : plateforme iOS, nom, langue principale **français**, identifiant `com.sti-cm.castbridge`, SKU libre (ex. `castbridge-ios`), accès complet.
5. **Archiver** : Product › Archive (schéma Release, appareil « Any iOS Device ») › Distribute App › App Store Connect › Upload.
6. **Conformité du chiffrement** : réponse portée par `ITSAppUsesNonExemptEncryption = NO` dans l'Info.plist en v1 (§ 6.4) ; plus de question à chaque build.
7. **TestFlight interne** : App Store Connect › TestFlight › Test interne › groupe « Propriétaire » (jusqu'à 100 membres ayant un rôle dans l'équipe), aucune revue Apple ; installer l'app **TestFlight** sur l'iPhone ; un build vit **90 jours**. **Externe** (pilote, lien public, jusqu'à 10 000 testeurs) : exige la **revue bêta** d'Apple (≈ 1-2 jours, non garanti).
8. **Questionnaires de la fiche** (avant la revue publique) : étiquettes de confidentialité, classification par âge, coordonnées de revue, notes au relecteur + vidéo de démonstration.

### 6.3 Risques App Review (revue publique ; la revue bêta externe applique les mêmes règles, allégées)

| Règle | Risque pour CastBridge | Parade |
|---|---|---|
| **2.1** complétude / **4.2** fonctionnalité minimale | le relecteur n'a pas de CastBridge-TV : app « vide » ⇒ rejet probable | **mode démonstration** (TV simulée) + vidéo d'un vrai usage dans les notes ; dire que la TV est un produit séparé sur Android TV |
| **3.1.1** achats intégrés | une clé d'activation ou un code qui **débloque l'app** hors achat intégré est interdit ; une boutique de lots payants aussi | **D-IOS-2** : aucune activation propre de l'iPhone, aucun prix, aucun lien d'achat ; fonctions ouvertes **parce que** l'iPhone est lié à une TV activée (dépendance matérielle, règle **3.1.4**) ; lots payants hors v1/v2 |
| **5.1.1 / 5.1.2** données | invite réseau local, caméra, photos | phrases en français précises (§ 6.5) ; aucune collecte en v1 ⇒ « Données non collectées » |
| **2.5.2** code exécutable | relais d'APK | exclu |
| **5.2.3** partage illégal | « copier des films vers la TV » | formulation : « vos photos et vidéos personnelles », aucune source de contenus tiers |
| **2.3** métadonnées | captures, description en français | captures du mode démonstration, marquées |
| **4.0** conception | vue web du Quiz | vue web limitée à l'origine de la TV, intégrée, pas de navigation libre |
| Nom | « Cast » proche d'une marque connue (non vérifié) | nom de fiche descriptif si contesté |

### 6.4 Chiffrement à l'export (D-IOS-11)

v1 n'utilise que : SHA-256 (intégrité), signatures Ed25519 (authentification) par `CryptoKit` (fourni par iOS), aucune confidentialité propre (HTTP clair sur le réseau local, WPA2 assuré par iOS). Ces usages relèvent des exemptions courantes (authentification, intégrité, chiffrement fourni par le système). **Recommandation** : `ITSAppUsesNonExemptEncryption = NO` en v1. **v2** (BLE : AES-GCM pour remettre le mot de passe Wi-Fi) : **réexaminer** avant le premier build qui l'embarque (probablement toujours exempté au titre du chiffrement grand public par les bibliothèques d'Apple, mais **non vérifié** ; la France demande parfois une déclaration). La revue juridique reportée au 2027-01-01 ne dispense pas de répondre au questionnaire d'Apple, qui bloque le téléversement.

### 6.5 Confidentialité, manifeste, âge, secrets

- **Phrases d'autorisation (français)** : `NSLocalNetworkUsageDescription` = « CastBridge cherche votre CastBridge-TV sur le Wi-Fi et lui envoie vos photos et vidéos. Rien ne quitte votre réseau local. » ; `NSCameraUsageDescription` = « Pour scanner le QR affiché par votre CastBridge-TV. » ; `NSPhotoLibraryUsageDescription` = « Pour choisir les photos et vidéos à copier sur votre TV. » (avec `PhotosPicker`, l'accès complet n'est pas nécessaire) ; `NSPhotoLibraryAddUsageDescription` (v2).
- **ATS** : `NSAppTransportSecurity › NSAllowsLocalNetworking = YES` (réseau local en HTTP) ; **jamais** `NSAllowsArbitraryLoads`. Le comportement d'ATS pour une **adresse IPv4 littérale** et pour la vue web doit être confirmé par wios-02 (test contre la TV factice sur 127.0.0.1 et sur appareil).
- **Manifeste `PrivacyInfo.xcprivacy`** : `NSPrivacyTracking = false`, aucun domaine de pistage, aucune donnée collectée (v1) ; API à raison déclarée : `UserDefaults` (CA92.1), horloge depuis le démarrage pour mesurer des durées (35F9.1, si `systemUptime`/horloge monotone), espace disque (E174.1, si l'app vérifie la place avant une copie temporaire), horodatage de fichiers (C617.1 / 3B52.1 selon l'usage) ; à ajuster au code réel par wios-11.
- **Âge** : aucune communication entre usagers hors Quiz local de la TV, aucun contenu tiers, pas de navigation web libre ⇒ classification la plus basse au questionnaire actuel (à remplir par le propriétaire).
- **Aucun secret dans l'app** : ni PIN, ni jeton, ni clé privée de signature d'activation, ni code superadmin, ni clé d'API serveur ; seules des clés **publiques** si la vérification `cbx1` est ajoutée (v2) ; test de source dans la porte (§ 7).

## 7. Tests et intégration continue

| Couche | Outil | Contenu | Où |
|---|---|---|---|
| Vecteurs partagés | Kotlin écrit `tools/ios-vectors/{tv-api,transfer,qr,pair,signal-texts}-vectors.json` (wios-tv-01) ; un test Kotlin vérifie qu'ils sont à jour ; Swift les rejoue | identifiants de transfert, tailles de bloc, empreintes de bloc et racine, formats QR (valides et invalides), messages de `pin-http-v1` (signatures déterministes Ed25519 sur clés de **test**), textes français des états | Mac (`gradle` sous verrou + `swift test`) |
| Activation (v2) | `tools/activation/test-vectors.json` (109 vecteurs existants) rejoués par `CryptoKit` | parité Ed25519 RFC 8032 et forme canonique `cbx1` | Mac |
| Unitaires Swift | XCTest (ou Swift Testing si disponible et stable avec Swift 6.4 : à vérifier) | logique pure, machine d'état de transfert, réessais bornés, trousseau simulé | Mac + simulateur |
| Intégration | XCTest contre **TV factice** = vraie `ReceiverServer` Kotlin (`FakeTvMain`, wios-tv-01) sur 127.0.0.1 | appairage, copie 50 Mo avec coupure et reprise, doublon, refus 507, 429, télécommande, bibliothèque | simulateur |
| Interface | XCUITest | parcours v1, mode démonstration, accessibilité (Dynamic Type, VoiceOver sur les boutons principaux) | simulateur |
| Porte de livraison iOS | `tools/ios/check.sh` (wios-11) | `swift test`, `xcodebuild test` simulateur sous `tools/agents/gradle-lock.sh` (un seul build lourd à la fois sur le Mac), recherche de secrets dans `ios/`, aucune URL Internet dans `ios/` en v1, aucune référence `castbridge-play`/portefeuille, présence des clés Info.plist et du manifeste | Mac |
| Humain (vrai iPhone + TV) | plan `ios/docs/TEST-HUMAIN.md` (wios-10) : parcours **P-IOS-1…P-IOS-12** | A : box ; B : groupe de la TV par QR ; refus du réseau local ; 9e téléphone ; copie 2 Go écran allumé ; passage en fond pendant une copie ; photo iCloud ; HEIC ; vidéo HEVC lue par la TV ? ; Live Activity ; partage depuis Photos ; Quiz à 2 iPhone | propriétaire, 3 séances d'≈ 1 h |

**Limites du simulateur à dire dans chaque rapport** : pas de Bluetooth, pas de `NEHotspotConfiguration`, invite réseau local non fidèle, fond non fidèle, un seul environnement iOS 27.0. Aucune intégration continue distante n'existe pour iOS (pas de Mac dans le nuage du projet) : la porte tourne sur ce Mac.

## 8. Cahiers, ordre, parallélisme, coût

Index : `docs/agent-briefs/SONNET-WAVE-IOS-INDEX.md`. Règles : fichiers possédés **disjoints** ; tout `xcodebuild`/`swift test`/`gradle` passe par `tools/agents/gradle-lock.sh` (un seul build lourd à la fois ; jusqu'à 3 agents écrivent en parallèle, les builds attendent leur tour) ; aucun agent ne signe, n'installe sur un appareil ni ne téléverse.

```
 ordre 1 :  wios-tv-01 (vecteurs + TV factice)      ∥  wios-01 (squelette ios/, paquet, projet, porte minimale)
 ordre 2 :  wios-02 (client TV)  ∥  wios-03 (transfert)  ∥  wios-tv-02 (pin-http-v1 côté TV, audit Opus)
 ordre 3 :  wios-04 (découverte, QR, groupe de la TV)  ∥  wios-05 (appairage, trousseau, audit Opus)
 ordre 4 :  wios-06 (app : écrans, plist, manifeste, démo)
 ordre 5 :  wios-07 (envoi, file, Live Activity, fond)  ∥  wios-08 (extension de partage)  ∥  wios-09 (télécommande, bibliothèque, Quiz)  ∥  wios-tv-03 (TV : QR, Téléphones, mDNS ; après gel)
 ordre 6 :  wios-10 (plan humain, notes App Review, guide propriétaire)  ∥  wios-11 (porte complète, manifeste ajusté)
 ordre 7 :  PROPRIÉTAIRE : signature, archive, TestFlight interne, séances P-IOS
 v2 :       wios-tv-04 (BLE, conditionnel)  ∥  wios-tv-05 (activation HTTP, décision)
 prérequis TV situation B : w18-01, w18-07 (W18) ; souhaitable : w19-02
```

| id | Modèle | Effort | Jauge (k entrée / sortie) | Audit Opus | Coût estimé |
|---|---|---|---|---|---|
| wios-tv-01 | sonnet | M | 400 / 20 | non | 1,0 $ |
| wios-01 | sonnet | S | 200 / 15 | non | 0,6 $ |
| wios-02 | sonnet | M | 400 / 25 | échantillon | 1,1 $ + 0,4 $ |
| wios-03 | sonnet | L | 550 / 30 | **oui** (perte de données) | 1,4 $ + 0,8 $ |
| wios-tv-02 | sonnet | M | 450 / 22 | **oui** | 1,1 $ + 0,8 $ |
| wios-04 | sonnet | M | 400 / 22 | échantillon | 1,0 $ + 0,4 $ |
| wios-05 | sonnet | M | 400 / 22 | **oui** | 1,0 $ + 0,8 $ |
| wios-06 | sonnet | L | 600 / 35 | non | 1,6 $ |
| wios-07 | sonnet | L | 600 / 35 | échantillon | 1,6 $ + 0,4 $ |
| wios-08 | sonnet | M | 400 / 22 | non | 1,0 $ |
| wios-09 | sonnet | M | 400 / 22 | non | 1,0 $ |
| wios-tv-03 | sonnet | M | 450 / 22 | échantillon | 1,1 $ + 0,4 $ |
| wios-10 | haiku | S | 150 / 15 | non | 0,2 $ |
| wios-11 | haiku | S | 150 / 12 | non | 0,2 $ |
| **v1** | | | | | **≈ 17,9 $** agents + audits ; + reprises 15-45 % (Swift/Xcode moins rodés dans ce dépôt) ⇒ **≈ 21-26 $** |
| wios-tv-04 (v2) | sonnet | L | 550 / 30 | **oui** | 1,4 $ + 0,8 $ |
| wios-tv-05 (v2) | sonnet | S | 250 / 12 | **oui** | 0,6 $ + 0,8 $ |

Prix repris des index précédents, **non vérifiés** (sonnet 2/10 $ par million de jetons, opus 4/20 $, haiku 0,8/4 $). Agent·jours : S = 0,5, M = 1,5, L = 2,5 ⇒ v1 = 3 × 0,5 + 8 × 1,5 + 3 × 2,5 = **21 agent·jours**, + 15 % de reprises ≈ **24**. Calendrier : 6 ordres, builds sérialisés, ≈ **8-10 jours ouvrés** si les ordres s'enchaînent, plus le temps du propriétaire (≈ 2 h de mise en place Apple, 3 séances d'≈ 1 h). Abonnement Apple Developer : déjà payé (99 $/an, renouvellement à prévoir).

## 9. Décisions

| id | Décision | Qui | Recommandation |
|---|---|---|---|
| **D-IOS-1** | Swift natif + `CastBridgeKit` + vecteurs partagés (option a) | architecte | **oui** (§ 4.1) |
| **D-IOS-2** | L'iPhone **n'a pas d'activation propre** : ses fonctions s'ouvrent quand il est lié (PIN) à une TV **activée** (essai ou production) ; aucun code, prix ou achat dans l'app iOS. Renverse, **pour iOS seulement**, « droits propres du téléphone, sans héritage » (TRIAL-EDITION § option A, point 4) | **propriétaire** | **oui** : sinon rejet App Store quasi certain (3.1.1) ; l'essai de la TV suffit à essayer |
| **D-IOS-3** | Appairage HTTP `pin-http-v1` avec clé d'appareil Ed25519 et jeton 12 h, compté dans les 8 | architecte | **oui** |
| **D-IOS-4** | Identifiants Wi-Fi Direct remis par **QR** (v1) ; BLE seulement si la sonde TV dit oui (v2) ; saisie manuelle du SSID/mot de passe en secours | architecte | **oui** |
| **D-IOS-5** | La TV garde son groupe Wi-Fi Direct allumé, CastBridge-TV à l'écran et sans LAN, **aussi** quand un iPhone de confiance existe (l'iPhone ne peut pas le demander) | architecte | **oui** (étend D-W18-3 ; sous réserve du fait F9 de W18) |
| **D-IOS-6** | Version minimale **iOS 16.0** (iPhone 8 et plus récents ; parc d'occasion), Live Activity dès 16.1, `BGContinuedProcessingTask` si iOS 26+ ; alternative iOS 17 (moins de code conditionnel, perd iPhone 8/X) | **propriétaire** | **iOS 16.0** |
| **D-IOS-7** | Identifiant `com.sti-cm.castbridge`, nom « CastBridge » (repli de fiche si pris) | **propriétaire** | **oui** |
| **D-IOS-8** | Distribution : TestFlight interne ⇒ TestFlight externe (pilote, lien public) ⇒ App Store **non listée** (lien direct, revue Apple) ⇒ publique plus tard | **propriétaire** | **oui** |
| **D-IOS-9** | Quiz compagnon v1 = page `/quiz` de la TV dans une vue web limitée ; aucun protocole neuf | architecte | **oui** |
| **D-IOS-10** | **Aucun appel Internet** en v1 (ni serveur, ni télémétrie, ni coursier, ni service de jeu) | architecte | **oui** (confidentialité « aucune donnée », règle W20 I-2 tenue par construction) |
| **D-IOS-11** | `ITSAppUsesNonExemptEncryption = NO` en v1 ; réexamen avant BLE | architecte (réponse du propriétaire dans App Store Connect) | **oui** |
| **D-IOS-12** | Agents : builds simulateur seulement (`CODE_SIGNING_ALLOWED=NO`) ; tout build signé, installation, archive, téléversement par le propriétaire | architecte | **oui** |
| **D-IOS-13** | Longueur du PIN lue dans `/api/hello` (`pinLen`, défaut 6), saisie 4 à 8 chiffres acceptée | architecte | **oui** (le code TV dit 6, la consigne dit 4-8 : la TV fait foi) |
| **D-IOS-14** | Photos HEIC converties en JPEG par défaut (réglage) ; vidéos HEVC envoyées telles quelles en v1, export « compatible » H.264 en v2 si P-IOS montre que la TV ne lit pas | architecte | **oui** |
| **D-IOS-15** | v0 (PIN à chaque requête, hors des 8) réservée à l'iPhone du propriétaire, jamais en TestFlight externe | architecte | **oui** |

## 10. Risques

| # | Risque | Prob. | Impact | Parade |
|---|---|---|---|---|
| R1 | La puce Wi-Fi de la TV ne sait être ni propriétaire de groupe ni point d'accès (B-W18-1) | moyen | **bloquant** : aucun iPhone dans 85 % des foyers | test terrain W18 § 7 **avant** wios-04 sur appareil ; dire la limite ; situation A seulement |
| R2 | W18 (w18-01, w18-07) non exécuté ⇒ pas d'identifiants persistants ni de QR | élevé (état actuel) | situation B en mode manuel (MENU, mot de passe frais, saisie dans Réglages iOS) | ordonner w18-01/07 avant wios-tv-03 ; v0 manuel en attendant |
| R3 | Rejet App Review (2.1/4.2 : pas de TV chez le relecteur) | élevé | retard de la revue publique | mode démonstration, vidéo, notes ; TestFlight interne non concerné |
| R4 | Rejet 3.1.1 si une clé ou un achat apparaît | élevé si D-IOS-2 refusée | rejet | D-IOS-2 |
| R5 | Usager qui refuse « réseau local » | moyen | rien ne marche, en silence | détection + écran d'aide vers Réglages |
| R6 | iOS se comporte mal sur un réseau sans Internet (avertissement, bascule, oubli) | moyen | liaison instable sur le groupe de la TV | `joinOnce = false` ; mesure P-IOS-3 ; texte d'aide ; données mobiles gardent Internet |
| R7 | Copie longue interrompue quand l'app passe en fond | élevé | copie qui s'arrête | dire « gardez CastBridge ouvert » ; écran allumé pendant la copie ; `URLSession` d'arrière-plan par blocs ; `BGContinuedProcessingTask` (iOS 26+, à vérifier) ; reprise au retour |
| R8 | Vidéos iCloud « optimisées » téléchargées par données mobiles | moyen | facture de données | demander avant ; option « Wi-Fi seulement » impossible sans Internet Wi-Fi ⇒ avertissement clair |
| R9 | TV qui ne lit pas HEVC / HEIC | moyen | fichier copié illisible | HEIC → JPEG par défaut ; P-IOS-9 pour HEVC ; export H.264 en v2 |
| R10 | Divergence filaire Kotlin ↔ Swift | moyen | copies refusées | vecteurs + TV factice + matrice W19 (ajouter un persona iOS en v2) |
| R11 | Nom « CastBridge » déjà pris sur l'App Store | inconnu | fiche à renommer | repli D-IOS-7 |
| R12 | PIN et jeton en clair sur la box | faible | voisin malveillant sur la même box | limite existante d'Android ; PIN envoyé une seule fois ; TLS épinglé en v3 |
| R13 | Double maintenance téléphone Android / iPhone | certain | coût continu | sous-ensemble petit, vecteurs, porte iOS ; nouvelles routes TV additives seulement |
| R14 | Part des iPhone chez les clients faible (non mesurée) | inconnu | rapport coût / usage | v1 petite ; mesurer au pilote avant v2 |
| R15 | Un seul environnement de simulation (iOS 27) | certain | régressions iOS 16-18 invisibles | iPhone du propriétaire ; environnements supplémentaires si le propriétaire les télécharge |
| R16 | API d'iOS 26/27 mal connues de l'architecte (SDK non lu) | moyen | un cahier bute sur une API | premier cahier qui l'utilise vérifie dans le SDK local et le dit au rapport ; repli iOS 16 |
| R17 | La TV accepte peu de clients sur son groupe (4-8 selon la puce, inconnu) | moyen | 9e appareil refusé par la radio | même plafond de 8 téléphones ; mesure W18 |
| R18 | Contrôleur Bluetooth de la TV sans BLE périphérique | élevé (W18 § 9) | pas de v2 BLE | QR reste la voie ; wios-tv-04 s'arrête à la sonde |

## 11. Ce qui n'a pas pu être vérifié

- Le **SDK iOS 27** installé (aucune lecture d'en-têtes, aucune compilation) : `BGContinuedProcessingTask`, `AccessorySetupKit` (Wi-Fi), « Wi-Fi Aware », comportement d'ATS sur une IPv4 littérale, Swift Testing avec Swift 6.4.
- Le comportement réel d'iOS sur le groupe Wi-Fi Direct de la TV (association d'un client non P2P au propriétaire de groupe GaiaOS, invite, maintien sans Internet, retour automatique au réseau connu) : **aucun iPhone n'a été branché**.
- Tout ce qui touche la puce Wi-Fi et le contrôleur Bluetooth de la TV (GO, AP, BLE, nombre de clients, débits) : faits B-W18-1…4 toujours ouverts.
- Que la TV décode HEVC (vidéos d'iPhone par défaut) et HEIC.
- La disponibilité du nom « CastBridge » sur l'App Store, la disponibilité de l'App Store et de TestFlight au Cameroun (à voir dans App Store Connect › Disponibilité), la part des iPhone chez les clients.
- Que l'iPhone du propriétaire existe et sa version d'iOS.
- Les prix des modèles d'agents (repris des index, non vérifiés).
- Les règles App Review citées le sont de mémoire de leur texte public ; leur version en vigueur au 2026-10-04 n'a pas été relue (aucun réseau).
