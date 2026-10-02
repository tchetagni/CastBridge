# Plan de stabilisation : fonctions multimédias et de synchronisation (W15, 2026-10-02)

> Document de conception (Fable, architecte, 2026-10-02). **Aucun code n'est modifié par ce document.** Exécution par les cahiers `docs/agent-briefs/sonnet-w15-NN-*.md` (index : `SONNET-WAVE15-INDEX.md`, qui ordonne aussi W13 et W14), sur ordre explicite du coordinateur. Branche de référence : `integration/agents` (HEAD `7e2ca0d`). Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OD/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`.
>
> **Demande du propriétaire** : « stabilise toutes les fonctions multimédias et de synchronisation ». Contexte : « il y a trop de régression » ; **gel des fonctionnalités** en vigueur ; la stabilisation passe avant toute nouvelle vague. Déjà conçu : W13 (aucun blocage silencieux), W14 (barrière anti-régression, en cours de conception par un autre agent Fable : **référencée, jamais dupliquée**), W7 (plug and play, non lancée), W8 (abandonnée). Fusionnés aujourd'hui : `fix-notv-cold-start`, `fix-trusted-phone`. En cours : `diag-receiver-progress` (Opus).
>
> **Méthode** : lecture seule du dépôt par six relectures de code parallèles (transfert téléphone, réception et cycle de vie TV, liaison/confiance/Bluetooth, lecteurs et streaming, bibliothèque/USB/téléchargements/lots/parental, tests/CI/outils), recoupées avec `docs/HANDOFF.md` § 9-10, `AUDIT-PROJET-2026-10-02.md`, `DESIGN-W7` § 1-2, `DESIGN-W13` § 1-2, `DESIGN-W14` § 1, `PARCOURS-CRITIQUES.md`, et une lecture **passive** du téléphone du propriétaire (`adb logcat -d`, `dumpsys notification`, `dumpsys package` ; aucune installation, aucun réglage, rien sur la TV). Aucun `gradle` lancé. Toute ligne citée a été relue ; « hyp. » = hypothèse non prouvée par le code.

## 0. En douze lignes

1. **114 fonctions** inventoriées (§ 1) : 18 transfert téléphone, 21 réception et cycle de vie TV, 24 liaison/confiance, 29 lecteurs et streaming, 22 bibliothèque/USB/téléchargements/lots/parental. Maturité : 41 stables, 46 fragiles, 7 cassées ou manquantes, 20 jamais vérifiées sur la vraie TV.
2. **108 défauts** au registre (§ 2) : **8 P0** (perte de données, plantage, blocage sans message), **39 P1**, 61 P2 ; 7 de terrain (R-01…R-05 + 2 relevés ce soir dans le logcat), 95 par lecture du code avec une loupe de risque, 6 d'outillage (tests instables, échecs Python).
3. **Cause racine de R-04** (TV figée sur « Prêt à recevoir ») : le transfert rapide écrit dans `.cbx/` que `receiving()` n'examine jamais (`C/tv/Volumes.kt:298`, `C/tv/ReceiverServer.kt:914-941`) ; la carte ne connaît que le `.part` classique et le statut Bluetooth `1-bt` ; aucun push vers l'écran, relecture toutes les 4 s et seulement à l'accueil (T-01).
4. **Cause racine probable de R-01** (copie figée, puce verte, PIN redemandé) : trois mécanismes se cumulent : la clé de `PinStore` ne retrouve pas la TV enregistrée (`S/TvLink.kt:199-208`, L-01), la route Bluetooth-tunnel fait un HELLO par minute et la TV ne garde que 4 jetons, donc un transfert long perd son jeton après ≈ 4 min (`C/trust/LinkDriver.kt:201-208`, `TrustRegistry.kt:99-100`, L-02), et la puce mesure le jeton de la boucle, pas celui du transfert (L-03). W13 traite la **présentation** ; W15 traite les **causes**.
5. **Trois pertes de données possibles** : « Déplacer » supprime l'original après un faux positif de reprise ou de dédoublonnage sur le chemin classique (X-03) ; `Mover` reprend une copie coupée sans vérifier le milieu puis supprime la source (B-02) ; la corbeille purge tout si l'horloge de la TV saute (B-03).
6. **Deux plantages** : `SecurityException` non attrapée à la reprise d'un envoi (X-15) ; `startForeground` non protégé dans `TransferQueueService` + aucun `onTimeout` pour les services `dataSync` (X-04).
7. **Plan en quatre tranches** (§ 3) : **S0** « arrêter l'hémorragie » = 6 cahiers sur les 8 P0, chacun **test rouge d'abord** ; **S1** observabilité et porte = 3 cahiers (tests déterministes, banc d'endurance, docs alignées ; la porte J/F/H est W14, les codes support sont W13) ; **S2** durcissement par domaine = 8 cahiers ; **S3** endurance = 1 cahier d'exécution (`docs/test-plans/ENDURANCE.md`).
8. **Règles** : tout correctif commence par un test qui échoue sur `integration/agents` ; **au plus 2 cahiers risqués en parallèle** et **un seul** à la fois sur `C/tv/ReceiverServer.kt`, `C/trust/LinkDriver.kt`, `S/UploadService.kt`, `S/TvLink.kt` ; aucune fusion de fonctionnalité ; audit Opus sur tout diff de transfert, liaison, confiance, cycle de vie.
9. **Ordre fusionné** (index W15) : W14-S1 (harnais J) en parallèle de W15-S0-a → W15-S0-b → W13-S1 rebasée → W15-S1 → W14-S2 (fumée) → W15-S2 → W13-S2 → W15-S3 (endurance) → **sortie de stabilisation** → W7.
10. **Critères de sortie** (§ 5) : 0 P0 ouvert ; 5 passages verts consécutifs de la porte complète (J + F `--tv fake`) sur 5 commits distincts ; 3 nuits d'endurance conformes aux seuils (§ 4) ; liste humaine 12/12 deux fois de suite ; dérive mémoire TV < 15 % sur 8 h.
11. **Vagues en attente** (§ 6) : W7, W8, W10 (branchement app), W11, W12 (écrans) attendent la sortie ; peuvent continuer **cœur seul, non branché** : W4-4a/4b, W5 cœur, W6 cœur, W9 (outils de contenu), W10 serveur/outils, W12 `settings-core`.
12. **Décisions du propriétaire** (§ 7) : durée du gel (recommandé : jusqu'à la sortie, ≈ 3 semaines), éviter « Déplacer » et « Ranger ma bibliothèque » sur de gros fichiers tant que S0-b n'est pas livrée, tests sur vrais appareils à réaliser par lui (liste), sort de `UsbMigration`, authentification du serveur média du téléphone.

## 1. Inventaire des fonctions

Colonnes : **Mat.** = stable / fragile / cassée / **NV** (non vérifié sur la vraie TV GaiaOS 32 bits) ; **Preuve** = tests JVM (`CT/`) ; aucun test n'existe dans `:sender` ni `:receiver` (vérifié : pas de dossier `src/test`). **Panne** = ce que voit l'utilisateur quand ça casse. **Déf.** = identifiants du registre § 2.

### 1.1 Transfert côté téléphone (CastBridge → CastBridge-TV)

| id | Fonction | Propriétaire | Aujourd'hui | Mat. | Preuve | Panne | Déf. |
|---|---|---|---|---|---|---|---|
| F-01 | Copie LAN classique | `S/UploadService.kt:156` → `C/tv/TvClient.kt:194-297` | PUT `/upload` en flux, reprise par `GET /api/part` | stable (cœur) / fragile (service) | TransferTest, InFlightTest | « En attente du réseau » sans fin ; « lancement impossible » | X-03, X-04, X-15 |
| F-02 | Transfert rapide multivoie | `S/UploadService.kt:155,181-210` → `C/xfer/TransferClient.kt:120` | `begin`, blocs SHA-256, `finish` | stable JVM / **NV** | MultipathTransferTest (33), MultipathServerTest (18) | « voie mise à l'écart » pour toujours | X-06, T-01, T-02 |
| F-03 | Repli classique (TV ancienne) | `TransferClient.kt:127`, `UploadService.kt:205` | `caps` absent ou 501 ⇒ classique | stable | MultipathServerTest.anOldTv… | 429/503 sur `caps` ⇒ repli muet | X-12 |
| F-04 | Réglage « Transfert rapide » | `S/TvScreen.kt:201`, `S/FastTransfer.kt:8` | préférence, actif par défaut | stable | aucun | — | — |
| F-05 | Envoi Bluetooth | `S/TvHub.kt:127`, `S/TransferQueue.kt:91` → `S/BtUploadService.kt:59` | négociation, LAN/WD sinon RFCOMM CBT1 | fragile | InFlightTest.btUpload…, Bt*Test | file « n'a pas démarré » pendant la validation Wi-Fi Direct | X-05, X-18 |
| F-06 | Déplacer (suppression de l'original) | `UploadService.kt:166,216-232` → `S/MoveToTv.kt:25-71` | info TV nom + taille + `complete` puis suppression | **fragile** | aucun | perte d'original ; un seul des N déplacements supprimé | X-03, X-07 |
| F-07 | File d'attente multi-fichiers | `S/TransferQueue.kt:39`, modèle `C/tv/TransferQueue.kt` | un à la fois, sondage 500 ms | fragile | TransferQueueTest (5, modèle) | élément marqué fait/échoué à tort | X-01, X-02, X-09 |
| F-08 | Reprise après coupure | `TvClient.kt:232-295`, `TransferClient.kt:126-157` | backoff 0,5→5 s, re-résolution mDNS | stable | InFlightTest, MultipathServerTest.resume* | tourne sans limite hors Wi-Fi | X-04, X-17 |
| F-09 | Dédoublonnage | `S/TransferQueue.kt:88` → `C/tv/TvInfo.kt:11` | même nom + complet + même taille | stable (file) / **cassée** (classique) | tv/TvDedupeTest (4) | copie « réussie » sans octet envoyé | X-03 |
| F-10 | Pré-vol stockage | `TvClient.kt:78,245-265` | `/api/storage/check` : FAT32, 1 Go libre | stable (classique) / absent (rapide) | TransferTest (6) | rapide : refus seulement au `begin` | X-19, T-17 |
| F-11 | Notification de progression | `UploadService.kt:255-276` (id 2), `TransferQueueService.kt:53` (id 9), `BtUploadService.kt:174` (id 3) | % en Long, 1/s | fragile | CopyProgressTest (texte) | notification orpheline ; doublon file + envoi ; disparition muette (W13 F1) | X-10 |
| F-12 | Annulation | `UploadService.kt:71,362`, `TransferQueue.kt:47` | drapeau + `stopSelf` | fragile | MultipathTransferTest.cancelStopsPromptly | le suivant échoue « annulé » ; rien nettoyé côté TV | X-02, X-16 |
| F-13 | « Ouvrir avec » | `S/OpenWithActivity.kt:49-155` | Copier / Déplacer / Lire ; route file ou PIN | fragile | SendChoiceTest (6) | 2ᵉ envoi PIN pendant un autre : ignoré | X-01, R-02 |
| F-14 | Partage de lien / torrent | `S/ShareToTvActivity.kt:27` | téléchargement côté TV | stable | aucun | `readBytes()` avant contrôle de taille | X-14 |
| F-15 | Lecture pendant l'envoi / autoPlay | `UploadService.kt:112-143,167-173`, `C/tv/Progressive.kt` | `play()` 1/s dès le seuil | fragile | ProgressiveTest (23) | relances concurrentes ; jeton figé | X-08, X-11 |
| F-16 | Échange multi-fichiers + destination | `S/TvTransferScreen.kt:92-104` | enchaînement dans un `LaunchedEffect` | fragile | aucun | fermer la boîte arrête la série | X-13 |
| F-17 | Envoi + installation d'APK | `S/TvHub.kt:224-258` | même enchaînement puis `installApks` | fragile | aucun | série bloquée si renommage auto | X-13 |
| F-18 | Passage DLNA → TV | `S/DlnaHandoff.kt:63` | UploadService puis `handoffBytes` | **NV** | ProgressiveTest.handoff | — | — |

### 1.2 Réception, cycle de vie et état côté TV (CastBridge-TV)

| id | Fonction | Propriétaire | Aujourd'hui | Mat. | Preuve | Panne | Déf. |
|---|---|---|---|---|---|---|---|
| F-19 | Serveur HTTP NanoHTTPD :8765 | `R/TvService.kt:241-273`, `C/tv/ReceiverServer.kt:87,245`, `C/tv/Storage.kt:173-185` | 8 threads + file 32, socket 15 s | stable, saturable | SecurityTest, TvHardeningTest | API muette quand le pool est plein | T-15 |
| F-20 | Réception `/upload` classique | `ReceiverServer.kt:560-662` | ajout au `.part`, `Meta`, commit par rename | stable | ReceiverTest, UxTest, MultiVolumeTest | 409 offset puis reprise | T-03, T-16 |
| F-21 | Réception rapide par blocs | `ReceiverServer.kt:666-781`, `C/xfer/TransferHost.kt`, `PartAssembler.kt` | `.cbx/<id>.data` préalloué, SHA par bloc, relecture au `finish` | **fragile** | MultipathServerTest | aucune progression à l'écran ; 429 définitif | T-01, T-02, T-04, T-05 |
| F-22 | Réception Bluetooth CBT1 | `R/BtServer.kt:100-133`, `C/tv/BtProtocol.kt:191-233` | `.part` interne, statut texte `1-bt` | fragile | BtProtocolTest, BtLinkTest | progression écrasée ; BT mort s'il s'allume après le service | T-06, T-08 |
| F-23 | Carte de réception (« Prêt à recevoir ») | `R/HomeScreen.kt:108,123-127`, `R/PlayerActivity.kt:436-440` | relue toutes les 4 s, accueil visible seulement | **cassée** (LAN rapide) | UxTest (classique seulement) | R-04 | T-01, T-10, T-11 |
| F-24 | Notification de premier plan | `R/TvService.kt:151-168` | `connectedDevice`, texte figé | stable / **NV** Android 14 | — | texte jamais mis à jour | T-07 |
| F-25 | Démarrage au boot | `R/TvService.kt:1039-1044`, `C/tv/Background.kt:10-19`, manifeste :77-86 | BOOT/QUICKBOOT/REPLACED ⇒ `startForegroundService` | **NV** | BackgroundTest | service absent ou mort à 10 s | T-07 |
| F-26 | Veille / réveil | `R/TvService.kt:569-588` | wake lock seulement si « busy » (sondé 5 s) | **NV** | BackgroundTest | copie figée écran éteint | T-09 |
| F-27 | Sonde réseau | `R/TvService.kt:437-485` | NetworkCallback, sonde 204 à la demande | stable | NetStateTest | — | — |
| F-28 | Rangement par catégorie à la fin | `ReceiverServer.kt:174-189,652,773`, `C/tv/Volumes.kt:260-272` | rename atomique même volume, jamais en lecture | stable / **NV** exFAT | FilingTest, FilingServerTest | fichier laissé à plat (valide) | B-05, B-06 |
| F-29 | Règle « 1 Go libre après » | `Storage.kt:44,58`, `ReceiverServer.kt:581,705` | `minFree = max(réserve, 1 Gio)` | stable sauf BT/USB | StorageTest, TvHardeningTest | BT et import USB : 100 Mo seulement | T-08 |
| F-30 | Choix du volume (clé prioritaire) | `Volumes.kt:451-505` | amovibles par espace libre, puis SAF, puis interne ; FAT32 ≤ 4 Gio−1 | stable | VolumesTest, MultiVolumeTest | FS inconnu traité comme illimité | T-05 |
| F-31 | Import USB | `R/UsbImporter.kt:88-115`, `C/tv/UsbImport.kt:50-100` | copie vers l'interne avec reprise | stable | UsbImportTest | pas de statut, pas de rangement | B-09, B-10 |
| F-32 | Verrou PIN | `C/tv/Security.kt:27-53`, `ReceiverServer.kt:523-542` | 5 échecs/IP ⇒ 60 s ; PIN absent compté | stable, trop strict | SecurityTest | verrou intempestif | T-12 (= W13 D-W13-2) |
| F-33 | Régénération du PIN | `R/TvPrefs.kt:12-14` | **absente** | manquante | — | PIN qui a fuité irrévocable | T-20 |
| F-34 | Annonce mDNS | `R/TvService.kt:962-981` | NSD `_castbridge._tcp` | stable | — | échec seulement journalisé | L-08 |
| F-35 | Capture d'écran API | `R/TvService.kt:812-814`, `R/ScreenCapture.kt:33-45` | PixelCopy de l'activité | stable | — | image noire sur SurfaceView (hyp.) | P-21 |
| F-36 | Horloge TV | `C/owner/Keys.kt:105-176`, `R/ActivationCenter.kt:28` | plancher monotone, saut > 45 j douté | stable | ClockRollbackTest | cliquet irréversible < 45 j | L-17 |
| F-37 | Balayages périodiques | `R/TvService.kt:181,215,233-236,740` | 15 min location, 15 s stockage, 5 s icônes | fragile après `restartApp` | — | boucles doublées | T-14 |
| F-38 | Gestionnaire de crash | `R/TvConnect.kt:198-205` | enregistre puis chaîne | stable | TelemetryTest | — | — |
| F-39 | `allowBackup=false` | manifeste :56 | PIN et confiance non sauvegardés | stable | — | — | — |

### 1.3 Liaison téléphone ↔ TV, confiance, PIN, Bluetooth, tunnel, horloge

| id | Fonction | Propriétaire | Aujourd'hui | Mat. | Preuve | Panne | Déf. |
|---|---|---|---|---|---|---|---|
| F-40 | Appairage Bluetooth guidé | `C/trust/PairFlow.kt:53-116`, `S/TvLink.kt:302-309`, `C/trust/PairingSession.kt:74-117`, `R/PairActivity.kt:186-196` | bond, HELLO `requestTrust`, fenêtre 150 s, approbation 60 s | stable | PairFlowTest (12), LinkDriverTest (33), TrustTest (30) | « Association impossible » ; non annulable | L-12, L-13, L-14 |
| F-41 | HELLO / jeton 12 h | `C/trust/HelloHandler.kt:36-54`, `TrustRegistry.kt:39,95-116`, `C/tv/BtProtocol.kt:160-174,263-280` | jeton 256 bits haché, ≤ 4 par téléphone | stable | TrustTest, HelloCompatTest | éviction après 4 HELLO | L-02, L-07 |
| F-42 | Renouvellement à mi-vie | `C/trust/PhoneLink.kt:88`, `LinkDriver.kt:174,262-275` | HELLO à 6 h, essais bornés | stable | LinkDriverTest:178-207 | — | — |
| F-43 | Reprise `recoverKnownTv` | `S/TvLink.kt:155-175` | HELLO sans fenêtre si registre vide | fragile | aucun | ne couvre pas « TV réinstallée » | L-10 |
| F-44 | Réassocier | `LinkDriver.kt:128-134`, `TvLink.kt:226-231` | garde la TV, retire jeton et session (corrigé `cf47cc0`) | stable | LinkDriverTest:60,83 | course mineure | L-13 |
| F-45 | Oublier | `LinkDriver.kt:137-140`, `TvLink.kt:214-220` | hors fil principal | stable | — | ne retire ni le registre TV ni le bond | — |
| F-46 | Découverte mDNS | `S/TvDiscovery.kt:27-99` | NSD, résolutions sérialisées par instance | **fragile** (15 instances) | aucun | TV absente, doublons « (2) » | L-08 |
| F-47 | Résolution d'IP via HELLO | `PhoneLink.kt:76-77`, `LinkDriver.kt:182-199`, `BtProtocol.kt:423-431` | HELLO rapporte les IP du moment | stable | LinkDriverTest:235, BtLinkTest | sonde séquentielle 1,2 s par IP | — |
| F-48 | Chemin PIN (saisie, clés) | `S/PinStore.kt:19-42`, `TvLink.kt:194-208`, `TvHome.kt:127`, `TvScreen.kt:77-82` | clé = nom, `host:port` ou `bt:addr` ; jeton d'abord | **cassée** (R-01) | aucun | PIN demandé à un téléphone de confiance | L-01, L-23 |
| F-49 | Verrou PIN TV (routes jeton) | `Security.kt:27-53`, `TrustRegistry.kt:202-203` | 4 routes réservées au PIN | stable | TrustTest:347, CredentialTest | 127.0.0.1 partagé par le tunnel | L-15 |
| F-50 | Puce d'état, hystérésis | `C/trust/LinkMachine.kt:83-88,128-188` | 2 confirmations, 3 s, grâce 40 s | stable | LinkMachineTest (15) | ne mesure pas le secret du transfert | L-03 |
| F-51 | Garde-vivant | `LinkDriver.kt:182-210`, `S/LinkAndroid.kt:85-93` | `GET /api/info` 1,5 s ×2 ; 15 s / 300 s / tâche 15 min | stable Wi-Fi | LinkDriverTest | Bluetooth : HELLO par minute | L-02 |
| F-52 | États Degraded / Bluetooth seul | `LinkMachine.kt:134,170`, `LinkDriver.kt:201-208` | repli et retour Wi-Fi | stable | LinkDriverTest:235,367 | route tunnel jamais testée | L-02 |
| F-53 | Tunnel API Bluetooth v1/v2 | `C/tunnel/LinkPool.kt:20-145`, `Mux.kt`, `S/BtSshGateway.kt:82-120` | liaison partagée, ping 15 s, fermeture 45 s, repli v1 10 min | fragile / **NV** S21+ | BtApiTunnelTest (12), BtMuxTunnelTest (12) | « Bluetooth lent » | L-09, L-16 |
| F-54 | Passerelle Internet Bluetooth | `R/BtGatewayHost.kt:38-61` | SOCKS 1080 | fragile | GatewayTest (6) | tombe après quelques secondes (HANDOFF § 9) | L-05, L-15 |
| F-55 | Télécommande Bluetooth CBTR | `BtProtocol.kt:184-190`, `S/RemoteController.kt:169-174` | sous `BtConnectLock` | stable | BtRemoteTest (39) | — | — |
| F-56 | Propagation d'activation | `R/ActivationCenter.kt`, `R/TvService.kt:171-186`, `S/ActivateTvActivity.kt:59-75` | sondage 4 s, écran ouvert, LAN seulement | **fragile** (R-05) | aucun | « TV activée mais le téléphone ne le voit pas » | L-11 |
| F-57 | Horloge TV / ClockDoubt | `C/owner/Keys.kt:105-176`, `C/lots/RentalEngine.kt:69-76` | max vu, saut avant > 45 j douté ; **aucune synchro** téléphone → TV | stable sur le papier | tests lots/owner | cliquet < 45 j irréversible ; jetons TV sur horloge murale | L-17 |
| F-58 | Permissions Bluetooth 12-14 | `S/BtPermission.kt:27-65`, `OD/TvBluetooth.kt:31-37`, `R/ActivationActivity.kt:178-187`, `R/PairActivity.kt:175-179` | téléphone : re-test sans redemande ; TV : demande à chaque `onResume` | téléphone stable / TV **cassée** (R-05) | aucun | dialogues en boucle | L-06, L-14 |
| F-59 | Diagnostic de liaison | `C/trust/Diagnostics.kt`, `S/TvPairScreen.kt:107` | 11 étapes, `Redact.scrub` | stable | DiagnosticsTest (11) | — | — |
| F-60 | Tunnel d'administration (SSH inversé) | `C/tunnel/TunnelMachine.kt:75-130`, `R/TunnelHub.kt:106-114` | backoff 5 s → 10 min | stable | TunnelMachineTest (16), TunnelLogicTest (15) | réveil perdu | L-18 |
| F-61 | Lien serveur, état Internet | `C/net/NetState.kt`, `C/connect/ServerLink.kt` | debounce ; verrou tenu pendant le réseau | stable | NetStateTest (15), ConnectTest (9) | gel d'interface (hyp.) | L-19 |
| F-62 | Registre de confiance TV | `C/trust/TrustFiles.kt:20-29`, `TrustRegistry.kt:123-150` | checksum, `.bak`, exclu des sauvegardes | stable sauf trou | HelloCompatTest:102, TrustTest:84 | perte totale entre deux renommages | L-20 |
| F-63 | Persistance téléphone (TV, jetons) | `S/LinkAndroid.kt:106-125`, `S/TvLink.kt:54-57` | SharedPreferences `apply()` | stable | TrustTest:390-406 (mémoire) | perte si processus tué (hyp.) ; TV homonymes confondues | L-21 |

### 1.4 Lecteurs, streaming, télécommande, miniatures

| id | Fonction | Propriétaire | Aujourd'hui | Mat. | Preuve | Panne | Déf. |
|---|---|---|---|---|---|---|---|
| F-64 | Lecteur TV libVLC 3.6.5 | `R/PlayerActivity.kt:267-325,345-355` | créé à la demande, libéré à l'arrêt | stable / **NV** | aucun | écran noir, « Lecture impossible » | P-05, P-06 |
| F-65 | Codecs matériels 32 bits | `PlayerActivity.kt:848-852,305-319`, `receiver/build.gradle.kts:43-48` | MediaCodec puis repli logiciel sur erreur | **NV** | aucun | saccades HEVC logiciel sur ARMv7 | P-06 |
| F-66 | Pistes audio | `R/PlayerExtras.kt:81,110`, `C/tv/PlayerFeatures.kt:127-167` | choix mémorisé par fichier | stable | PlayerFeaturesTest (8) | id de piste instable si fichier remplacé | — |
| F-67 | Sous-titres externes | `PlayerFeatures.kt:170-187`, `PlayerExtras.kt:56,84,153` | même dossier, `addSlave`, moteur à la demande | fragile | PlayerFeaturesTest (partiel) | accents illisibles ; perdus après déplacement | P-01, P-02, P-03, P-03b |
| F-68 | Sous-titres intégrés | `PlayerExtras.kt:161-163` | `spuTracks` | **NV** | aucun | 1ᵉʳ choix relance le fichier | — |
| F-69 | Seek et touches | `PlayerActivity.kt:1077-1080,933-944` | ±10 s / ±60 s, borné par `reachableMs` | stable | aucun | — | — |
| F-70 | Reprise de position (TV) | `PlayerActivity.kt:638-643`, `C/tv/LibraryStore.kt:45` | à la pause, `onPause`, arrêt | **fragile** | LibraryTest | coupure de courant = position perdue | P-04 |
| F-71 | Lecture pendant l'envoi | `C/tv/Progressive.kt:30-104`, `ReceiverServer.kt:1334-1348` | `GrowingStream`, `.meta`, `playIncomplete` | stable | ProgressiveTest (23) | tampon sans fin si l'envoi meurt | P-06, P-07, P-12 |
| F-72 | Lecture depuis clé SAF | `PlayerActivity.kt:881-904` | descripteur | **NV** | aucun | « envoi complet avant lecture » | — |
| F-73 | Lecteur téléphone Media3 1.5.1 | `S/player/PlaybackService.kt`, `PlayerActivity.kt` | MediaLibraryService, PiP | stable | PhonePlayerTest (16) | notification media3 résiduelle (vue ce soir) | P-08, P-09, F-07 |
| F-74 | « Ouvrir avec » (Telegram) | `S/player/Media.kt:60-100`, `OpenWithActivity` | VIEW / SEND | fragile | PhonePlayerTest (types) | droit URI perdu (hyp.) | P-09, R-02 |
| F-75 | Reprise de position (téléphone) | `PlaybackService.kt:146-188` | 5 s / 10 s | stable | ResumeBook | ≤ 10 s de recul | — |
| F-76 | Cast DLNA vers TV tierce | `S/Upnp.kt:21-48,78-82`, `C/upnp/Soap.kt:38-45` | SSDP + SOAP | **NV** matériel tiers | SoapTest | « aucune TV trouvée » | P-19, P-20 |
| F-77 | Serveur `/media/<id>` du téléphone | `S/MediaServer.kt:23-68`, `S/ServerService.kt:26-50` | NanoHTTPD 8089, Range 206/416 | stable Range / **cassée** sécurité | aucun | lecture sans PIN par un voisin ; collision d'id | P-10, P-10b |
| F-78 | Cast live vers CastBridge-TV | `ReceiverServer.kt:491-499`, `PlayerActivity.kt:906-929`, `S/player/CastSession.kt:120-152` | `/api/playurl` | stable | CopyProgressTest, PhonePlayerTest | « TV injoignable, nouvel essai » sans fin (vu ce soir) | P-11, P-18, F-06 |
| F-79 | Télécommande dans l'app | `S/RemoteService.kt:30-125` | 15 touches/s | stable | RemoteTest (33) | « TV injoignable » | — |
| F-80 | Réception des touches TV | `R/RemoteHub.kt:236-282,130-138` | `dispatchKeyEvent`, réflexion `mViews` | **NV** | aucun | dialogues sans touches | P-13b |
| F-81 | Service d'accessibilité TV | `R/RemoteAccessibilityService.kt:21-111` | D-pad global 13+, nœuds 8-12 | **NV** | aucun | latence, nœuds non recyclés | P-13 |
| F-82 | Télécommande HID / stratégies | `C/remote/hid/*`, `C/remote/smart/*` | marques | **NV** | smart/* (5) | pas de diagnostic | — |
| F-83 | Miniatures TV | `R/Thumbnailer.kt:29-88`, `C/tv/LibraryStore.kt:107,170` | MMR puis libVLC 8 s, cache 20 Mo, 1 worker | stable | LibraryStoreTest | échec mémorisé pour toujours ; deux libVLC | P-14, B-12 |
| F-84 | Visionneuse d'images / miniatures téléphone | `S/player/ImageViewer.kt`, `PhoneLibrary.kt:228` | LruCache 300 entrées | stable | PhonePlayerTest | OOM entrée de gamme | P-15 |
| F-85 | Statistiques de lecture | `PlayerActivity.kt:365-401`, `S/player/PlaybackStats.kt` | sans nom de fichier | stable | aucun | — | — |
| F-86 | Audio Langues (TV) | `R/LanguesActivity.kt:192-196`, `R/LanguesHub.kt:18-19,79-83` | `MediaPlayer` ; lot média non livré | **cassée** bout en bout | LanguesTest (texte) | « Audio non disponible » | P-16 |
| F-87 | Sons du Quiz | `R/QuizSound.kt:16-113` | SoundPool 3 flux | stable | aucun | silence voulu | — |
| F-88 | Vidéo dans les leçons | `R/LearnHub.kt:160-169` | `playVideo` | fragile | aucun | chemin non validé | P-17 |
| F-89 | Transcodage `RoutePlanner` | `C/transcode/Transcode.kt` | **code mort** | — | CoreTest | — | — |
| F-90 | Jeu d'écran mirroring | — | **n'existe pas** (grep vide) | — | — | — | — |
| F-91 | Lecture d'URL `/api/playurl` | `ReceiverServer.kt:491-499` | http(s) ≤ 4096 | stable | aucun | clé de reprise commune | P-11 |
| F-92 | Captures d'écran (téléphone → TV) | `R/ScreenCapture.kt:33-45` | PixelCopy | stable | aucun | bitmap recyclé pendant la copie (hyp.) | P-21 |

### 1.5 Bibliothèque, rangement, corbeille, USB, téléchargements, lots, parental

| id | Fonction | Propriétaire | Aujourd'hui | Mat. | Preuve | Panne | Déf. |
|---|---|---|---|---|---|---|---|
| F-93 | Index de la bibliothèque | `ReceiverServer.kt:901-957`, `Volumes.kt:296-310` | relu des fichiers, cache 1 s | fragile | MultiVolumeTest, StorageTest | lenteur O(N²) sur grosse clé | B-01, B-13 |
| F-94 | Dossiers virtuels / catégories | `C/tv/Folders.kt:25-144`, `C/tv/Library.kt:146` | `folders.db` atomique | stable | TvFoldersTest (14) | purge des dossiers d'une clé absente | B-07 |
| F-95 | Rangement à la réception | `C/tv/Filing.kt:83,144`, `Volumes.kt:260-272` | rename même volume | stable / **NV** exFAT | FilingTest (15), FilingServerTest (17) | sous-titre séparé | B-06 |
| F-96 | Index `.filing` | `Filing.kt:222-327`, `UsbStore.kt:81` | `AtomicFile`, `adopt()` borné | fragile | FilingTest | échec de lecture + `put` = index écrasé | B-05 |
| F-97 | « Ranger ma bibliothèque » | `Filing.kt:189`, `ReceiverServer.kt:812,846` | plan simulé, 500 par appel | fragile | FilingServerTest | sature les 8 threads (O(N²)) | B-01 |
| F-98 | Déplacement entre volumes (Mover) | `C/tv/Mover.kt:58-138`, `ReceiverServer.kt:1235-1283` | `.part`, marqueur, bords 1 Mo, suppression | **fragile** | MultiVolumeTest:430-535 | source supprimée, copie corrompue | B-02, B-11 |
| F-99 | Corbeille 30 j | `C/library/agent/TvTrash.kt:34-179` | dossier caché, id = horloge | **fragile** | TvAgentTest:57-137 | tout purgé si l'horloge saute | B-03, B-18 |
| F-100 | Renommage | `ReceiverServer.kt:428-451`, `Volumes.kt:282-294` | verrous, refus en lecture | stable | — | sous-titre orphelin | B-06 |
| F-101 | Suppression dure | `ReceiverServer.kt:459-475` | toutes les copies, pas de corbeille | fragile | aucun | les deux copies partent | B-08 |
| F-102 | Vue téléphone de la bibliothèque TV | `S/TvLibraryScreen.kt:108-114`, `C/library/agent/TvOps.kt` | `/api/library` toutes les 5 s | fragile | — | coût serveur | B-01 |
| F-103 | Volumes multiples, clé prioritaire | `Volumes.kt:318-400`, `R/AndroidVolumes.kt:53-95` | `VolumeRegistry`, test d'écriture réel | stable | VolumesTest (25), MultiVolumeTest (39) | — | — |
| F-104 | Migration USB | `C/tv/UsbMigration.kt:18-128` | **jamais instanciée** (doc contraire) | **cassée** (absente) | UsbMigrationTest (12) | fonction documentée inexistante | B-04 |
| F-105 | Détection du débranchement | `R/TvService.kt:743-756,695-725`, `Volumes.kt:358-390` | diffusions MEDIA_*, rescan 15 s | stable | MultiVolumeTest:248-360 | retrait retardé par un listage | B-13 |
| F-106 | Téléchargements aria2 TV | `C/dl/Aria2Supervisor.kt`, `Aria2Config.kt:46-103`, `R/TvDownloads.kt:41-160` | HTTP/FTP, DHT coupé, 2 simultanés | fragile / **NV** 32 bits | DownloadManagerTest (19), DownloadLogicTest (24), Aria2IntegrationTest (1) | état perdu après coupure ; wake lock tenu | B-10b, B-15 |
| F-107 | Téléchargements TV → téléphone | `S/DownloadService.kt:36-185` | `dataSync`, `ResumableDownload`, wake lock 6 h | fragile | — | 2ᵉ téléchargement ignoré en silence | B-14 |
| F-108 | Livraison des lots téléphone → TV | `C/lots/DeliveryQueue.kt:77-263`, `LotPush.kt:102-190`, `S/LotsRuntime.kt:224-245`, `R/LotsHub.kt` | PENDING→SENT→CONFIRMED, reprise | stable / **NV** Bluetooth réel | LotsDeliveryTest (24), LotsPhoneTest (27), LotsTvTest (18) | SENT Bluetooth attend 30 min | B-16 |
| F-109 | Rapports parentaux TV → téléphone | `C/parental/ParentalSync.kt:25-104`, `ParentalReports.kt:125-185`, `S/ParentalInbox.kt:39-123` | CBTP, ack après stockage, tâche 15 min | stable / **NV** | ParentalReportsTest (41) | gel d'interface sur le verrou ; rapports rejetés acquittés | B-17 |
| F-110 | Dédoublonnage bibliothèque | `ReceiverServer.kt:931-934`, `C/library/agent/Duplicates.kt:36-85` | nom + taille + empreinte 64 Ko | stable | TvDedupeTest, TvAgentTest | — | — |
| F-111 | Classement des séries | `S/SeriesClassifying.kt:11-45`, `C/library/agent/SeriesClassifier` | dossiers virtuels | stable | SeriesClassifierTest, TvFoldersTest | — | — |
| F-112 | Sûreté des noms | `ReceiverServer.safeName:1435`, `UsbStore.kt` (`UsbPaths`), `Volumes.kt:113-135` | refus `/`, `..`, canonique | stable | MultiVolumeTest, PathologicalNamesTest | — | — |
| F-113 | Miniatures : cache et échecs | `LibraryStore.kt:107-140` | LRU 20 Mo, échec permanent | fragile | LibraryStoreTest | vignette jamais retentée | B-12 |
| F-114 | Noms Unicode / FAT32 | `Volumes.kt:113-135`, `Filing.kt:163-170` | 250 octets, pas de NFC pour les fichiers | stable | FilingTest | NFC/NFD coexistants (interne) | B-19 |

**Bilan de maturité** : stables 41 · fragiles 46 · cassées ou manquantes 7 (F-09, F-23, F-33, F-48, F-77, F-86, F-104) · **NV** 20 (dont tout le chemin libVLC, le boot et la veille GaiaOS, le tunnel Bluetooth v2 sur le S21+, exFAT/FAT32 réels, le DLNA tiers). Rappel structurel (W14 § 1.2) : **0 test dans `:sender` et `:receiver`**, la CI ne compile ces modules que par `assembleDebug`.

## 2. Registre des défauts

Priorités : **P0** = perte de données, plantage, ou blocage sans message ; **P1** = fonction cassée dans une situation courante ; **P2** = le reste. Colonnes : **Cause** = hypothèse de cause racine ; **Correctif** = minimal ; **Test** = ce qui l'aurait attrapé (J = parcours JVM du harnais W14, U = test unitaire du cœur, F = fumée W14, H = humain). Les identifiants reprennent ceux des six relectures (X = transfert téléphone, T = TV, L = liaison, P = lecteurs, B = bibliothèque, Q = outillage, F-0x = terrain).

### 2.1 Terrain (jour du 2026-10-02)

| id | Symptôme | Cause (fichier:ligne) | Pri. | Couvert par |
|---|---|---|---|---|
| R-01 | copie figée, puce verte, PIN à ressaisir | L-01 + L-02 + L-03 (§ 2.3) ; présentation : W13 F1-F6 | **P0** | w15-02, w15-03 ; W13 S1 |
| R-02 | « Aucune TV ajoutée » avec TV connectée par le code | corrigé `9f6777f` (`SendChoices.decide`) ; reste `QUEUE_NO_LINK` (W13) | P1 (résiduel) | W13 w13-07 ; W14 J-19 |
| R-03 | téléphone de confiance perdu, « Réassocier » détruisait la TV | corrigé `cf47cc0` ; reste L-10 (aucun remède automatique après réinstallation TV) | P1 (résiduel) | w15-11 |
| R-04 | TV figée sur « Prêt à recevoir » pendant une copie | **T-01** : `.cbx/` invisible de `receiving()` ; statut `1-bt` seulement ; pas de push | **P0** | w15-01 |
| R-05 | `connect()` BT en rafale, activation non propagée, BT lent, permissions en boucle | L-04, L-11, L-09, L-06 | P1 | w15-11, w15-12, w15-17 |
| F-06 | logcat 17:07-17:21 : `CastSession` « En attente du réseau : TV injoignable » pendant la copie puis « TV injoignable, nouvel essai… » en lecture, en boucle | P-18 (reconnexion sans fin) + TV qui disparaît du LAN par intermittence (T-09 veille / L-08 mDNS ; hyp.) | P1 | w15-15, w15-17 |
| F-07 | `dumpsys notification` : notification `castbridge.sender` id 1001, canal `default_channel_id`, groupe `media3_group_key`, 2 actions, persistante après lecture | notification par défaut de Media3 (canal non nommé en français, jamais retirée quand rien ne joue) | P2 | w15-15 |

### 2.2 Transfert côté téléphone (X)

| id | Pri. | Où | Scénario | Cause | Correctif | Test |
|---|---|---|---|---|---|---|
| X-15 | **P0** | `S/UploadService.kt:235`, `C/tv/TvClient.kt:280-294` | reprise après coupure : `openFileDescriptor` lève `SecurityException` (droit `content://` perdu quand `TransferQueueService` fait `stopSelf()` sans `startId`, `TransferQueueService.kt:38`) ⇒ sort du `thread("upload")` ⇒ **plantage** | seules `IOException`/`HttpError` sont attrapées | `catch (SecurityException)` ⇒ `Failed(« accès au fichier perdu »)` ; `stopSelf(lastStartId)` ; revérifier `busy()` | U : `ResumableUpload` avec `openAt` qui lève ⇒ `Failed` |
| X-03 | **P0** | `TvClient.kt:13,269`, `ReceiverServer.kt:141-147,578,880`, `UploadService.kt:222` | (a) un fichier **fini** homonyme de taille différente ⇒ `partJson` dit `done:true` ⇒ `Done` sans octet, autoPlay lance l'ancien ; (b) un `.part` homonyme d'un autre contenu est repris (le serveur ne compare pas `total`) ⇒ en Déplacer, `checkMoved` voit `complete && size == local` ⇒ **original supprimé** | `/api/part` ne porte que le nom ; aucune empreinte avant suppression | `size=total` sur `/api/part`, 409 si le `Meta` diffère ; Déplacer : suppression seulement après preuve (racine SHA du rapide ou octets réellement envoyés = taille) | U : InFlightTest « `.part` homonyme d'un autre fichier jamais repris » ; J : P-14 |
| X-04 | **P0** | `TvClient.kt:213,287-289`, `TransferClient.kt:128`, `UploadService.kt:185,284-292`, `TransferQueueService.kt:49`, manifeste :144-156 | `giveUpAfter = Int.MAX_VALUE`, 403/429/5xx et `IOException` rejoués sans fin ; services `dataSync` sans `onTimeout()` (targetSdk 35) ⇒ après 6 h cumulées Android 15 **tue l'app** ; `startForeground` non protégé dans `TransferQueueService` ⇒ **plantage** depuis l'arrière-plan | aucune borne, aucun `onTimeout`, un `try/catch` manquant | `onTimeout` ⇒ `stopSelf` + `Failed(« reprendra »)` ; borne « 30 min sans progrès » ⇒ `Failed` + Reprendre ; `try/catch` `startForeground` | U : horloge factice + limite ; J : P-17 |
| X-01 | P1 | `UploadService.kt:74,358` | 2ᵉ `start()` pendant un envoi : fichier perdu sans message, `_state = Idle` écrase l'état du 1ᵉʳ ⇒ `TransferQueue.watch` marque « interrompu » | un seul travail, pas d'id dans `State` | refuser (`false` + message) ou tout faire passer par la file ; `jobId` dans `State`, vérifié par `watch` | U : coordinateur avec deux `start()` |
| X-02 | P1 | `UploadService.kt:71,145,174,300`, `TvClient.kt:289/293` | annulation ⇒ `stopSelf` immédiat, le thread écrit encore jusqu'à 5 s après ⇒ l'élément **suivant** est marqué « annulé » ; notification 2 reposée en `ongoing` | écriture tardive non filtrée | génération de travail (`if (gen != current) return`) ; `nm.cancel(NOTIF)` dans `finish`/`onDestroy` | U : travail fantôme après annulation |
| X-05 | P1 | `TransferQueue.kt:140`, `BtUploadService.kt:44,153-154,216` | file BT : 20 s sans état pendant la négociation / Wi-Fi Direct (≤ 50 s) ⇒ « n'a pas démarré », suivant perdu | aucun état publié avant le 1ᵉʳ octet | publier `Waiting(0, total, « négociation… »)` dès `run()` | U : `watch` avec faux service lent |
| X-06 | P1 | `C/xfer/Scheduler.kt:64,89,145-153` | toutes les voies échouent pour une raison non fatale (source illisible, 500/503 par bloc) ⇒ « voie mise à l'écart » pour toujours | aucune issue globale | compteur de mises à l'écart sans bloc confirmé (10) ⇒ `Failed` ; `IOException` de la source fatale | U : MultipathTransferTest « source qui échoue toujours » |
| X-07 | P1 | `UploadService.kt:223`, `S/MainActivity.kt:137`, `TransferQueue.kt:89,91` | Déplacer N fichiers en arrière-plan : une seule case `_moveReady` ⇒ un seul original retiré ; BT ignore `move` ; « déjà là » + move ne supprime pas | état à une valeur, en mémoire | liste persistante de `MoveRequest` traitée une à une | U : file de `MoveRequest` |
| X-08 | P2 | `UploadService.kt:141,170,219`, `BtUploadService.kt:129` | `job.pin` figé alors que l'envoi utilise `credential()` ⇒ « lancement impossible » après renouvellement | — | `credential()` partout | U : variante InFlightTest.tokenExpiring |
| X-09 | P2 | `TransferQueue.kt:68-76` | `pumping` non atomique ⇒ élément WAITING pour toujours, service au premier plan sans fin | fenêtre entre test et affectation | `AtomicBoolean.compareAndSet` + relecture | U : stress du pump |
| X-10 | P2 | `UploadService.kt:255-298` | notification orpheline, doublon 2 + 9 | `finish` sans `cancel` | voir X-02 | F : `dumpsys notification` |
| X-11 | P2 | `UploadService.kt:139`, `TvClient.kt:150` | `play()` lancé chaque seconde, chaque appel ≤ 12 s ⇒ relances concurrentes à 0 | pas de garde | `AtomicBoolean` « un en vol » | U |
| X-12 | P2 | `TransferClient.kt:41,48,127`, `TransferHost.kt:50` | `caps` 429/503 ⇒ repli muet ; `begin` 429 ⇒ échec définitif en anglais ; classique ignore `Retry-After` | — | 429/503 = attente avec backoff | U |
| X-13 | P2 | `TvTransferScreen.kt:98-104`, `TvHub.kt:232-258` | séries dans Compose : fermer la boîte arrête tout ; APK bloquée si renommage auto | logique dans l'écran | sortir la série dans la file | U après extraction |
| X-14 | P2 | `ShareToTvActivity.kt:99-100` | `readBytes()` avant le contrôle 4 Mo ⇒ OOM | — | lecture bornée | U |
| X-16 | P2 | `TransferClient.kt:70` (`abort` jamais appelé), classique sans nettoyage | « Annuler » ne libère ni session ni `.part` | voulu pour la reprise | `abort` à l'annulation explicite (cf. T-02) | J : P-15 |
| X-17 | P2 | `TransferClient.kt:122,162` | `waitThen { run() }` récursif ⇒ pile qui grandit pendant une coupure longue | — | boucle `while` | U |
| X-18 | P2 | `BtUploadService.kt:39,85-89,149` | pas de WakeLock/WifiLock ; `bindProcessToNetwork` détourne tout le processus ; `ACTION_CANCEL` sans `stopSelf` | — | verrous ; `network.bindSocket` ; `stopSelf` | H |
| X-19 | P2 | `UploadService.kt:100-101,195` | `statSize = -1` ⇒ « illisible » ; rapide sans `onCheck` ⇒ pas d'avertissement FAT32 | — | transmettre `onCheck` | U |
| X-20 | P2 | grep vide `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | aucune exemption, file en mémoire perdue à la mort du processus | — | persister la file (W7 w7-21 la prévoit) ; explication « réglage batterie » (W13 `PHONE_BG_KILLED`) | H |
| X-21 | P2 (hyp.) | `TransferQueue.kt:92` | `startForegroundService` depuis une coroutine en arrière-plan (2ᵉ fichier) | — | vérifier sur appareil, écran éteint | H |
| X-22 | P2 | `C/xfer/HttpConn.kt:17,27,82-93` | chien de garde 20 s coupe un disque lent sans 429 ; `dead` non volatile ; thread mort = surveillance perdue | — | `@Volatile`, seuil ≥ 60 s si `writeBps` bas | U |
| X-23 | P2 | `S/agent/AgentAuto.kt:55` | renommage auto : `settle` retombe sur l'origine quand le nom propre existe (`.part` précédent) ⇒ reprise perdue, `.part` orphelin | — | reconnaître son propre `.part` | U |
| X-24 | P3 | `OpenWithActivity.kt:165`, manifeste :162-179 | branche `ACTION_SEND` morte ; `takePersistableUriPermission` échoue en VIEW | — | nettoyage | — |

### 2.3 Liaison, confiance, PIN, Bluetooth, tunnel, horloge (L)

| id | Pri. | Où | Scénario | Cause | Correctif | Test |
|---|---|---|---|---|---|---|
| L-01 | **P0** | `S/TvLink.kt:199-208`, `S/PinStore.kt:22`, `S/TvDiscovery.kt:34`, `S/TvScreen.kt:77` | la clé d'écran (« X (Bluetooth) », IP sans port, URL Wi-Fi Direct, nom NSD « (2) », TV homonymes, `127.0.0.1:port` du tunnel) ne correspond à rien dans `savedFor` ⇒ jeton introuvable ⇒ PIN vide ou ancien ⇒ 401 ⇒ R-01 | résolution de clé par égalité stricte dans un écran Android, sans test | fonction pure `C/trust/PinKeys.kt` (nom prévu par W14 § 3.3) : `resolve(key, saved)` tolérante (préfixes, hôte sans port, URL, tunnel ⇒ TV par défaut, dédoublonnage par adresse BT) + `keysOf(tv)` ; `PinStore` et `TvLink.savedFor` l'appellent ; W13 w13-08 `putAll` la réutilise | U : table « une clé par origine d'écran ⇒ un jeton » ; J : P-07, P-08 |
| L-02 | **P0** (hyp. forte) | `C/trust/LinkDriver.kt:201-208`, `TrustRegistry.kt:99-100`, `S/UploadService.kt:124` | route `BluetoothTunnel` traitée comme « Bluetooth seul » : HELLO toutes les 60 s + à chaque `forceCheck` ; chaque HELLO émet un jeton, la TV n'en garde que 4 ⇒ le jeton capturé par un transfert long devient `bad token` après ≈ 4 min ⇒ copie figée, puce verte | garde-vivant Bluetooth = HELLO émetteur de jeton | garde-vivant par `env.check(base, jeton)` à travers le tunnel ; HELLO seulement à mi-vie ; TV : `issueToken` idempotent (réutilise le jeton s'il lui reste > 50 % de vie) | U : LinkDriverTest avec `tunnelBase` : 30 min simulées ⇒ ≤ 2 jetons, jeton de t0 accepté à t0+20 min ; J : P-10 |
| L-03 | P1 | `LinkDriver.kt:182-199,207,213`, `LinkAndroid.kt:91` | la puce valide le jeton de session, pas celui du transfert ; `Alive` quand le limiteur bloque ; 403/429/5xx = `OK` | — | `tokenRejected` appelé par tout 401 d'un transfert ; `Alive` différé ; 5xx = `UNREACHABLE` (W13 `LinkHealth` fait la puce) | U : faux TV 503 ⇒ pas `Connected` > 2 cycles |
| L-04 | P1 | `S/TvLink.kt:71,259`, `OD/TvBluetooth.kt:125`, `LinkDriver.kt:142,215`, `BtProtocol.kt:271` | `connect()` RFCOMM sans `BtConnectLock` ni délai pour HELLO/PairFlow/activation ; `step` `@Synchronized` tient le verrou pendant `connect` ⇒ `adopt`/`forget` gelés ; boucle sans `try/catch` ⇒ une exception non-IO tue la coroutine | verrou réservé au tunnel et à la télécommande | `BtConnectLock.of(addr)` + garde 15-20 s dans `AndroidBtTransport.connect` ; pause de la boucle pendant `pair()` et `TvBluetooth.with` ; `try/catch` autour de `step` ; lecture avec délai | U : transport factice comptant les `connect` simultanés (max 1) ; H : P-26 |
| L-05 | P1 | `R/BtServer.kt:75,90-98`, `R/OwnerBtHost.kt:37,47,55`, `R/BtGatewayHost.kt:47` | adaptateur éteint au démarrage ou pile Bluetooth redémarrée ⇒ `accept()` en erreur ⇒ boucle arrêtée pour toujours ; aucun récepteur `ACTION_STATE_CHANGED` | — | récepteur STATE_ON ⇒ relance ; ré-écoute comme `BtTunnelBridge.kt:73-84` | U (TV simulée) ; H |
| L-06 | P1 | `R/ActivationActivity.kt:178-187`, `R/PairActivity.kt:152,175-179` | `requestPermissions` à chaque `onResume` ⇒ boucle (R-05) | pas de drapeau « déjà demandé » | drapeau sauvegardé, demande sur action ; modèle `S/BtPermission.kt:38-52` | fonction pure « faut-il redemander » (U) ; F : compte de dialogues (P-35) |
| L-07 | P1 (hyp.) | `TrustRegistry.kt:95-132`, `TrustFiles.kt:22` | `issueToken` fait `fsync` **sous le verrou** que `verifyToken` prend à chaque requête ⇒ requêtes authentifiées gelées pendant un gros upload sur la même eMMC | persistance sous moniteur | persister hors verrou, regroupée ; ou ne pas persister les jetons | U : persistance lente 200 ms ⇒ `verifyToken` < 5 ms |
| L-08 | P1 | `S/TvDiscovery.kt:36-96` (15 instances) | résolutions parallèles ⇒ `FAILURE_ALREADY_ACTIVE` ⇒ service abandonné ; résolution qui ne rappelle jamais ⇒ `resolving` bloqué ; IPv6 sans crochets ; doublons « (2) » | une instance par écran | singleton applicatif à compteur de références, re-essai ×3, délai 10 s, IPv4 seulement, file vidée au `restart` | U : file de résolution extraite (pure) |
| L-09 | P1 | `C/tunnel/LinkPool.kt:31,98-107,134-139` | panne passagère au premier contact ⇒ repli v1 mémorisé 10 min (un RFCOMM par requête = « lent ») ; fermeture après 45 s ⇒ reconnexion de plusieurs secondes | repli sur délai, pas sur refus explicite | v1 seulement sur SDP absent/refus ; `dialShared` rappelé à 60 s ; liaison gardée 3-5 min quand un transfert la veut | U : BtMuxTunnelTest échec transitoire puis succès |
| L-10 | P1 | `S/TvLink.kt:156`, `HelloHandler.kt:33-34`, `LinkMachine.kt:201` | TV réinstallée ⇒ `TvForgotMe`, `Retry.Never` ; `recoverKnownTv` inapplicable ⇒ il faut Réassocier + ouvrir la fenêtre TV à la main | conception (W7 n° 6) | TV : registre vide + bonds existants ⇒ fenêtre ouverte au démarrage ; téléphone : `HINT_OTHER_INSTALL` ⇒ bouton « Reconnecter » lançant `PairFlow` | U : « registre vide ouvre la fenêtre » ; J : P-02 |
| L-11 | P1 | `R/TvService.kt:196,216`, `S/ActivateTvActivity.kt:59-75` | TV verrouillée : ni `BtServer` ni HELLO ⇒ `SERVICE_ABSENT` ; téléphone : sondage 4 s LAN, écran ouvert | — | HELLO même verrouillée ; signal après déverrouillage ⇒ `poke()` (W7 domaine `act` à terme) | U ; J : P-25 |
| L-12 | P2 | `R/PairActivity.kt:161,167`, `PairingSession.kt:55,110` | fermer l'écran TV pendant une demande = refus compté ⇒ 3 = blocage 10 min | `close` ⇒ `decision=false` | `close` pendant `Asking` = TIMEOUT non compté | U : PairingSessionTest |
| L-13 | P2 | `S/TvPairScreen.kt:163-166`, `TvLink.kt:226-229` | « Annuler » n'arrête pas `PairFlow` (≤ 150 s + 5 min) ; `prepareReassociate` asynchrone peut effacer le jeton neuf | — | drapeau `cancelled` ; appel synchrone | U : PairFlowTest annulation |
| L-14 | P2 | `S/LinkAndroid.kt:133-135`, `TvLink.kt:71`, `BtSshGateway.kt:105`, `OD/TvBluetooth.kt:119` | `cancelDiscovery` (SCAN) dans le même `runCatching` que `createBond` ⇒ « Association impossible » avec CONNECT accordé ; `SecurityException` laisse la socket ouverte ; `RejectedExecutionException` après `onDestroy` | — | `runCatching` séparés ; `finally` ; verrou | U : `PairEnv` sans SCAN |
| L-15 | P2 | `R/BtGatewayHost.kt:33`, `ReceiverServer.kt:525` | verrou PIN partagé (`"bt-gateway"`, `127.0.0.1` via tunnel) ⇒ un téléphone verrouille tous les autres | clé = IP | clé = adresse Bluetooth du pair | U : deux pairs |
| L-16 | P2 | `C/tunnel/Mux.kt:24,39,123,145` | tête de ligne bloquante (un flux lent bloque PONG ⇒ liaison tuée à 45 s) ; trame jusqu'à 65535 sans comparer à `MAX_PAYLOAD` ; réutilisation d'id | — | plafonner, fermer le flux fautif, `lastRx` sur lecture réelle | U : consommateur lent + flux rapide |
| L-17 | P1 | `C/owner/Keys.kt:149-165`, `R/ActivationCenter.kt:28`, `R/RentalHub.kt:41`, `TrustRegistry.kt:37,120` | saut avant < 45 j cru et ratché ⇒ `BEHIND` irréversible quand l'heure redevient juste ; deux `TvClock` ; recalage NTP ⇒ `purge` de tous les jetons ⇒ 401 en plein transfert | cliquet sans retour ; jetons sur horloge murale | borner un saut non confirmé ; « l'heure est juste » aussi pour BEHIND ; un seul `TvClock` ; jetons sur `elapsedRealtime` + marge | U : +30 j puis retour ⇒ suspension levable |
| L-18 | P2 | `R/TunnelHub.kt:106-114` | `wake()` entre `step()` et `wait` ⇒ réveil perdu (≤ 10 min, 1 h en REVOKED) | — | drapeau testé sous le moniteur | U : TunnelMachineTest |
| L-19 | P2 | `C/connect/ServerLink.kt:141-178,250-312` | HTTP sous `synchronized(lock)` ⇒ `setConsent`/`myData` gelés | — | copier l'état, réseau dehors | U |
| L-20 | P1 | `C/trust/TrustFiles.kt:23-28`, `TrustRegistry.kt:135` | coupure entre `file→bak` et `tmp→file` ⇒ pas de principal ; `load() ?: return` ignore `.bak` ⇒ **tous les téléphones oubliés**, nouvel `installId` | deux renommages, pas de secours | `load` tente `loadBackup()` ; ou `tmp→file` direct | U : dossier avec `.bak` seul (HelloCompatTest:102) |
| L-21 | P2 | `S/TvLink.kt:54-57`, `PhoneLink.kt:127-130`, `LinkDriver.kt:249-253` | `apply()` asynchrone ; `addressChanges` par **nom** ⇒ deux TV « Android TV » basculent la TV enregistrée et effacent le jeton | — | `commit()` à l'adoption ; exiger `installId` | U : TrustTest:403 avec homonymes |
| L-22 | P2 | `LinkMachine.kt:83,185,211`, `TvHome.kt:65-77`, `TvScreen.kt:88-91`, `BtServer.kt:43,94` | cadence empilée (garde-vivant 15 s + `/api/info` 2 s + 1 s) ; horodatages muraux ; TV n'accepte que 4 liaisons ⇒ 5ᵉ fermée ⇒ `StaleBond` à tort pendant des transferts (hyp.) | — | une seule sonde partagée (W13 `HealthProbe`) ; `elapsedRealtime` ; 6 liaisons | U |
| L-23 | P2 | `TvHome.kt:18`, `DlnaHandoff.kt:50`, `CastSheet.kt:89`, `ConnectScreens.kt:321`, `ParentalScreen.kt:79`, `ShareToTvActivity.kt:66`, `OpenWithActivity.kt:58-71` | jeton lu une fois, sans observer `state` ⇒ vide pendant un renouvellement | — | `rememberCredential(key)` | banc Compose (absent) |

### 2.4 Réception, cycle de vie et état côté TV (T)

| id | Pri. | Où | Scénario | Cause | Correctif | Test |
|---|---|---|---|---|---|---|
| T-01 | **P0** | `PartAssembler.kt:33-35,166,224-229`, `Volumes.kt:298`, `ReceiverServer.kt:914-941,767`, `PlayerActivity.kt:438`, `HomeScreen.kt:108`, `TvService.kt:611-614`, `StatusIcons.kt:6-20` | copie LAN rapide : blocs dans `.cbx/<id>.data` (nom caché, pas de `.part`/Meta avant `finish`) ⇒ `receiving()` vide ⇒ `status()` retombe sur `1-bt` ⇒ « Prêt à recevoir » ; relecture 4 s accueil seulement, pas de push ; relecture SHA au `finish` (secondes à minutes sur clé) sans rien à l'écran | la carte ne connaît que `.part` + Meta et le statut Bluetooth | `TransferHost.progress()` (nom, reçu = Σ blocs cochés, taille, `finishing`) fusionné dans `receiving()` ; `statusesChanged` ⇒ `home.refreshStatus()` ; tick 1 s pendant une réception ; état « vérification » | U : MultipathServerTest `begin` + 2/5 blocs ⇒ `receiving()` = (nom, 2 Mio, 5 Mio) ; J : P-11, P-36 ; F : `/api/screenshot` |
| T-02 | P1 (≈ P0) | `TransferHost.kt:21,50`, `ReceiverServer.kt:263`, `TransferClient.kt:47-49,121-122` | `sweep` seulement au démarrage et au branchement d'une clé ; le téléphone n'appelle jamais `abort` ; `maxSessions = 3` ⇒ après 3 envois interrompus, **tout `begin` répond 429** « too many transfers » (anglais) jusqu'au redémarrage de l'app TV ; `setLength` réel sur exFAT ⇒ espace occupé 7 j ⇒ 507 | aucun balayage en marche | `sweep(30 min)` toutes les 5 min + éviction de la plus ancienne non `finishing` dans `begin` ; `abort` téléphone à l'annulation ; texte français | U : 3 `begin` + horloge +31 min ⇒ 4ᵉ accepté |
| T-03 | P1 | `ReceiverServer.kt:573-578,703`, `PartAssembler.kt:165-166` | `/upload` classique et `/api/transfer` du même nom ne s'excluent pas ⇒ `finish` fait `part.delete()` puis rename **sur un `.part` ouvert en écriture** | `uploadCounted` ne teste pas `transfers.hasName` | 409 si `hasName` ; refuser au lieu de supprimer | U : `begin` + bloc, PUT `/upload` ⇒ 409 |
| T-04 | P1 | `TransferClient.kt:30`, `ReceiverServer.kt:747,794` | relecture `finish` > 60 s ⇒ le téléphone relance ; 2ᵉ `finish` lit un canal fermé ⇒ `DiskFail` ⇒ **500** ; un thread bloqué par essai | `sess` lu hors verrou | revérifier la session après le verrou ; `done` si le final existe à la bonne taille ; `finish` asynchrone (202 + `state`) | U : deux `finish` concurrents ⇒ jamais 500 |
| T-05 | P1 (hyp.) | `PartAssembler.kt:229`, `TransferHost.kt:59` | `setLength` sur vfat/FAT32 remplit de zéros ⇒ `begin` de 4 Go ≈ 200 s sous verrou ⇒ API muette ; EFBIG ⇒ 507 au lieu de 413 | préallocation | pas de préallocation hors ext4/f2fs ; EFBIG ⇒ 413 | U + H (clé FAT32) |
| T-06 | P1 | `R/BtServer.kt:113-127` | statut `1-bt` écrasé par l'envoi suivant ; part du R-04 Bluetooth | chaîne unique | `ReceiveCard.of(transfers)` (W14 § 3.3) alimentée par T-01 | J : P-13 |
| T-07 | P1 (hyp.) | `TvService.kt:142,153-159` ; manifeste :77-86 | `startForeground` qui lève est avalé ⇒ `ForegroundServiceDidNotStartInTimeException` à 10 s ⇒ app tuée, START_STICKY boucle ; `onCreate` fait `startCore()` (registre, `cleanOrphans`, `recoverMove` SHA, `resyncFiling`) sur le fil principal ⇒ ANR au boot avec une clé lente | — | échec ⇒ `stopSelf` + statut ; `startCore` sur `bg` ; `startService` si au premier plan | H : P-31 ; F : reboot émulateur |
| T-08 | P2 | `TvService.kt:216`, `BtProtocol.kt:203-211,229`, `UsbImport.kt:50-100` | Bluetooth et import USB : interne forcé, minimum 100 Mo, pas de Filing/fsync/Meta, `final.delete()` avant rename (non atomique, fichier en lecture) | chemins parallèles | passer par `StoragePolicy.plan` + `fileReceived` | U |
| T-09 | P2 | `ReceiverServer.kt:114`, `TvService.kt:569-583` | `activeTransfers()` ne compte que les requêtes ⇒ wake lock relâché entre deux blocs ⇒ copie figée écran éteint (hyp.) | compteur instantané | `+ transfers.active()` | H : P-16 nuit |
| T-10 | P2 | `HomeScreen.kt:124,130` | `refreshStatus()` sur le fil principal ⇒ `listing()` + `listFiles()` de la clé toutes les 4 s ⇒ saccades, ANR | E/S UI | sur l'exécuteur `io` | H |
| T-11 | P2 | `PlayerActivity.kt:438` | vieux `.part` orphelin avec Meta affiché « 37 % » figé 24 h / 7 j | pas de filtre de fraîcheur | `mtime` < 30 s ou `RateMeter` actif | U |
| T-12 | P2 | `Security.kt:44-46`, `ReceiverServer.kt:535-536` | PIN absent compté ⇒ `/favicon.ico` d'un navigateur verrouille le LAN (NAT) | — | **W13 D-W13-2** (w13-05) | U |
| T-13 | P2 | `PartAssembler.kt:95-97`, `MultipathServerTest:204-212` | contre-pression inopérante (`queued()` ≤ 256 Kio) ; le test l'injecte | — | documenter ou mesurer réellement | — |
| T-14 | P2 | `TvService.kt:951-958,983-996` | `restartApp` ne retire ni `transferTick`, `iconTick`, `rentalTick`, `activationWatch` ⇒ boucles doublées ; `multicastLock` non remis | — | `stopCore` symétrique | U (compteur) |
| T-15 | P2 | `Storage.kt:26`, `ReceiverServer.kt:108` | 8 threads, keep-alive 15 s : 6 voies + `/api/info` 1 s + `/stream` ⇒ API en retard, lecture saccadée (hyp.) | — | `Connection: close` sur les chunks ; threads ≥ `maxStreams` + 4 | H |
| T-16 | P2 | `Volumes.kt:248-253` | `commit` interne sans fsync ; `delete` puis `rename` non atomique | — | `force(true)` ; rename direct | U |
| T-17 | P2 | `ReceiverServer.kt:1196-1206` | `/api/storage/check` 200 avec `ok:false` ; ignore les sessions `.cbx` | conception | documenter ; inclure `.cbx` | U |
| T-18 | P2 | `ReceiverServer.kt:452-475` | `/api/delete`/`reset` ne suppriment pas la session rapide ; `delete` attend la fin d'un upload (verrou) | — | invalider la session | U |
| T-19 | P2 | `R/OwnerBtHost.kt:20,45-49` | sans délai de lecture ⇒ un téléphone figé bloque l'activation | — | `soTimeout` | U |
| T-20 | P2 | `R/TvPrefs.kt:12-14` | PIN jamais renouvelable | fonction absente | bouton « Nouveau code » (TV) | H |

### 2.5 Lecteurs, streaming, télécommande, miniatures (P)

| id | Pri. | Où | Scénario | Cause | Correctif | Test |
|---|---|---|---|---|---|---|
| P-04 | P1 | `R/PlayerActivity.kt:191,638-643,931,949` | position sauvée seulement à la pause/arrêt/`onPause` ⇒ coupure de courant pendant un film = position perdue | pas de tick | tick 15-30 s dans `onPlaying` ⇒ `db.onStopped` | H ; U sur la politique |
| P-05 | P1 | `PlayerActivity.kt:345-355` | `mp.stop()` synchrone sur le fil principal ; sur `/stream` d'un envoi en attente (≤ 30 s `GrowingStream`) ⇒ gel, ANR sur 1 Go | libVLC 3 `stop` bloquant | exécuteur dédié + verrou | H |
| P-02 | P1 | `ReceiverServer.kt:1235-1281,428-448`, `PlayerExtras.kt:56` | déplacement entre volumes ou renommage : le `.srt` reste ⇒ plus de sous-titres | fichiers compagnons ignorés | helper « compagnons » (`SubtitleFinder.find`) dans Mover, rename, corbeille, delete, Filing (nom final après collision) | U : FilingServerTest film + srt + collision + déplacement |
| P-01 | P1 (hyp.) | `PlayerActivity.kt:269-275`, `S/player/Media.kt:44-46` | `.srt` Windows-1252 ⇒ accents illisibles (TV et téléphone) | pas de détection d'encodage | `SubtitleCharset.detect` (UTF-8 strict sinon CP1252) + `--subsdec-encoding` | U |
| P-10 | P1 | `S/MediaServer.kt:17-21,28-29,55` | serveur du téléphone sans authentification, id = `hashCode` 32 bits (collision ⇒ mauvais fichier), toutes interfaces, `items` jamais vidé ; descripteur non positionnable ⇒ 500 | — | id 128 bits aléatoire + jeton de session ; `unregister` ; interface Wi-Fi seule ; 416/500 propres | U : deux URI en collision ⇒ ids distincts |
| P-18 | P1 | `S/player/CastSession.kt:272-278`, `S/ServerService.kt:38-41` | `IOException` ⇒ nouvel essai sans fin ; jamais FAILED ; wake lock 4 h tenu ; **vu ce soir** (F-06) | — | FAILED après ≈ 60 s d'échecs + message | U : `poll()` avec client qui lève |
| P-06 | P2 | `Progressive.kt:38-72`, `PlayerActivity.kt:305-319` | envoi avorté ⇒ `http-reconnect` sans fin en « buffering » ; 404 ⇒ repli **logiciel** sur une erreur réseau | repli indistinct | repli seulement si fichier complet ; délai global 2 min + message | U : ProgressiveTest envoi interrompu |
| P-07 | P2 | `ReceiverServer.kt:1334-1348` | `/api/play` d'un MP4 incomplet moov en fin non refusé côté TV (vérifié côté téléphone seulement) | — | `Mp4Atoms.layout` dans `playIncomplete` ⇒ 409 | U |
| P-12 | P2 | `Progressive.kt:60-82`, `Storage.kt:26` | chaque connexion libVLC retient un thread ≤ 30 s ⇒ pool plein, API « gelée » pendant la lecture en envoi | — | ≤ 2 lecteurs `/stream` par fichier, 503 au-delà | U : 3 lecteurs concurrents |
| P-03 / P-03b | P2 | `PlayerActivity.kt:886,912`, `PlayerFeatures.kt:171-182` | sous-titres non cherchés en progressif/SAF ; `.txt` proposé comme sous-titre | — | dossier du volume ; `isSubtitle` dans `find` | U |
| P-08 | P2 (hyp.) | `S/player/PlaybackService.kt:93-125`, manifeste :231-238 | tout contrôleur accepté (file lisible/modifiable par une autre app) | pas d'`onConnect` | restreindre au paquet, Auto, système | — |
| P-09 | P2 (hyp.) | `S/player/PlayerActivity.kt:160-165` | droit URI Telegram lié à l'activité ⇒ échec après fermeture | — | copie en cache pour URI externes | H |
| P-11 | P2 | `ReceiverServer.kt:491-499`, `PlayerActivity.kt:906-916` | titre « Depuis le téléphone », `total = 0` ⇒ clé de reprise « 0:Depuis le téléphone » partagée ⇒ reprise/pistes croisées, entrées parasites | — | ne rien enregistrer si `total == 0` ; hash d'URL | U |
| P-13 / P-13b | P2 | `R/RemoteAccessibilityService.kt:38-74`, `R/RemoteHub.kt:93-100,130-138` | nœuds non recyclés < 33 ; `onMain(1500)` bloque le thread HTTP ; réflexion `mViews` refusée ⇒ dialogues sans touches | — | `AccessibilityNodeInfoCompat` ; référence explicite au dialogue | H |
| P-14 | P2 | `R/Thumbnailer.kt:60-88`, `TvService.kt:209` | miniature libVLC (≤ 8 s) + lecture ⇒ deux libVLC sur 1 Go | — | `play()` annule le worker | H |
| P-15 | P2 | `S/player/PhoneLibrary.kt:228,248` | LruCache 300 bitmaps ≈ 120 Mo ⇒ OOM | taille en entrées | `sizeOf` en octets | U |
| P-16 | P2 | `R/LanguesActivity.kt:192-196`, `R/LanguesHub.kt:79-83` | `MediaPlayer` non libéré sur échec, `prepare()` synchrone, pas d'`AudioFocus` ; lot média non livré | — | `try/finally`, `prepareAsync` | H |
| P-17 | P2 | `R/LearnHub.kt:163-166`, `R/LanguesHub.kt:81-82` | chemin de vidéo de leçon sans `safeName` | — | `safeName`, rejet `..` | U |
| P-19 / P-20 | P2 (hyp.) | `S/Upnp.kt:26-31,64-88` | SSDP sur le réseau par défaut (mobile) ; `describe` séquentiel 8 s × N ; `Seek` REL_TIME seulement | — | `bindSocket` Wi-Fi ; parallèle ; ABS_TIME en repli | U |
| P-21 | P2 | `S/ServerService` type `dataSync`, `CastSession.kt:137,223`, `S/TvPlayerSettings.kt:60-61`, `R/ScreenCapture.kt:37-44`, aucune `MediaSession` TV | plafond 6 h Android 15 (hyp.) ; MIME `video/mp4` pour `.mkv` ; `readBytes()` sans plafond ; bitmap recyclé pendant `PixelCopy` ; pas de focus audio TV | — | points individuels | — |

### 2.6 Bibliothèque, rangement, corbeille, USB, téléchargements, lots, parental (B)

| id | Pri. | Où | Scénario | Cause | Correctif | Test |
|---|---|---|---|---|---|---|
| B-02 | **P0** (hyp. exFAT) | `C/tv/Mover.kt:89,93-105,114,123`, `Volumes.kt:248-250` | copie de 20 Go vers la clé, coupure de courant ⇒ taille du `.part` persistée mais blocs intermédiaires non ⇒ reprise à cette taille, vérification des **bords seulement** (1 Mo) ⇒ source **supprimée**, milieu corrompu | reprise sans preuve ; `UsbMigration` fait un SHA complet, pas `Mover` | à la reprise, comparer les 8 derniers Mo et tronquer ; fsync tous les 64 Mo avec octets garantis (`Meta`) ; SHA complet avant suppression ≥ 100 Mo (annulable) | U : MultiVolumeTest « `.part` de bonne taille, milieu corrompu, jamais validé » |
| B-03 | **P0** | `C/library/agent/TvTrash.kt:76-78,107,168` | horloge TV à 1970 (sans RTC) ⇒ id < 10 chiffres ⇒ fichier invisible, irrestaurable, jamais purgé ; horloge corrigée après ⇒ écart > 30 j ⇒ **corbeille entière supprimée** au prochain `list`/`put`/`restore` | purge sur horloge murale | ne jamais purger si horloge suspecte (< 2020 ou saut > 1 j) ; séquence monotone + dernière observation ; regex `\d{1,14}` | U : TvAgentTest saut 1970 → 2026 ⇒ rien supprimé |
| B-05 | P1 | `Filing.kt:231-242,319-325` | `.filing` illisible (E/S USB, ligne corrompue) ⇒ `runCatching` global ⇒ `loaded=true` sans `adopt()` ⇒ le `put` suivant **écrase l'index** ⇒ fichiers rangés invisibles, doublons renvoyés | tout-ou-rien | échec de lecture ⇒ `adopt()` + `.filing.bak` ; `runCatching` par ligne | U : FilingTest tronqué / `%zz` ⇒ fichiers visibles après `put` |
| B-01 | P1 | `Volumes.kt:219-225,233,130,164-168`, `ReceiverServer.kt:812-880,944-952` | `diskName` fait `dir.list()` à chaque appel sur exFAT ⇒ `/api/library` = N listages de N entrées (3 000 fichiers ⇒ 9 M), sondé toutes les 5 s par le téléphone et par l'écran TV ; « Ranger » refait le plan ⇒ 8 threads saturés | O(N²) par FUSE | cache des noms réels par volume (invalidé), `libraryItems()` en cache 1-2 s | U : 5 000 fichiers, compteur de `listFiles` plafonné |
| B-06 | P1 | `Mover.kt`, `TvTrash.kt:98-117`, `ReceiverServer.kt:428-475`, `Filing.classify` | sous-titres orphelins au déplacement, renommage, corbeille, suppression ; à la réception, collision « (2) » sépare vidéo et `.srt` | = P-02 | un seul cahier « compagnons » | U |
| B-07 | P1 | `Folders.kt:96-105`, `TvService.kt:249` | clé retirée + `GET /api/folders` ⇒ dossiers virtuels des fichiers de la clé **purgés pour toujours** | `prune` sur les présents | purger seulement les noms absents de tous les volumes connus ; id de volume par entrée | U : TvFoldersTest volume absent |
| B-08 | P1 | `ReceiverServer.kt:459-475` | « supprimer le doublon » supprime **les deux** copies ; ignore `streamUse`, envoi en cours, sous-titres ; pas de corbeille | — | exiger `volume` si `duplicate`, `busyReason`, corbeille | U |
| B-09 | P1 | `DownloadManager.kt:323-333`, `UsbImport.kt:107-114`, `Volumes.kt:296-310` | téléchargement/import homonyme d'un fichier rangé ⇒ fichier à plat qui **masque** l'entrée rangée | `uniqueDest` local au volume | `nameTaken` global + `fileReceived` | U : FilingServerTest |
| B-10 | P2 | `UsbImport.kt:65-87` | reprise sans empreinte, `renameTo` sans fsync, fichier déjà rangé réimporté à chaque branchement | — | `findFinalOrOrigin` | U |
| B-10b | P1 | `DownloadManager.kt:157-161,132-144`, `LibraryStore.kt:93-97` | `writeText` + `renameTo` sans fsync ⇒ coupure ⇒ état vide ⇒ **tâches et historique perdus**, données cachées orphelines | pas d'`AtomicFile` | `AtomicFile.write` ; scan des dossiers sans tâche | U |
| B-11 | P1 (hyp.) | `ReceiverServer.kt:231`, `Filing.kt:307`, `TvService.kt:242` | `resyncFiling` (≤ 50 000 entrées) dans le constructeur du serveur, sur le fil principal (= T-07) | — | sur `bg` | H |
| B-12 | P2 | `LibraryStore.kt:140`, `Thumbnailer.kt:62` | échec de vignette permanent ; une instance libVLC par fichier | — | retenter après délai | U |
| B-13 | P2 | `Volumes.kt:358-379`, `ReceiverServer.kt:901` | `refresh()`/`markRemoved` `@Synchronized` ⇒ retrait retardé par un listage FUSE ; `listing()` en troupeau | — | listage hors verrou | U |
| B-14 | P1 | `S/DownloadService.kt:49,178,48` | 2ᵉ téléchargement ignoré en silence (`_progress` écrasé) ; `ACTION_CANCEL` sans `stopSelf` ; pas d'`onTimeout` | = X-01 | file ; `onTimeout` | U après extraction |
| B-15 | P2 | `R/TvDownloads.kt:104-118`, `Aria2Supervisor.kt:75-79`, `DownloadManager.kt:167-310` | QUEUED + moteur FAILED ⇒ wake lock tenu pour toujours ; RPC sous verrou ; `copyTo`+`delete` non atomique | — | relance depuis FAILED ; RPC hors verrou | U |
| B-16 | P2 | `DeliveryQueue.kt:118-120`, `LotsRuntime.kt:226-228`, `TvLotStore.kt:224-240` | SENT Bluetooth revient PENDING ; `hasWork` ignore SENT ⇒ confirmation à 30 min ; `.lot.zip` sans preuve visible pour toujours | — | SENT dans `hasWork` ; adoption | U |
| B-17 | P2 | `S/ParentalInbox.kt:46,72`, `S/ParentalData.kt:38-41`, `ParentalSync.kt:211` | `init`/`sync` sur le même verrou ⇒ gel de l'onglet pendant une connexion BT ; rapports rejetés **acquittés** après rotation de clé ⇒ perdus | — | verrou séparé ; ne pas acquitter un rejet | U |
| B-18 | P2 | `TvTrash.kt:138`, `TvOps.kt` (`moveTimeoutMs`), `Mover.kt:98`, `UsbMigration.kt:122` | corbeille hors quota ; restauration à la racine ; déplacement attend 6 h si la TV redémarre ; `alive()` par bloc ; SHA non annulable | — | points individuels | U |
| B-19 | P2 | `Volumes.kt:113-135`, `Folders.kt:39` | pas de NFC pour les noms de fichiers ⇒ NFC/NFD coexistants sur ext4 | — | normaliser | U |
| B-04 | P1 (doc) | `C/tv/UsbMigration.kt`, `docs/STORAGE.md:182` | « Déplacer les contenus vers la clé » documenté, **jamais branché** ; `candidates()` ignore les dossiers | — | décision propriétaire : brancher via `Mover` ou corriger la doc | — |

### 2.7 Outillage, tests, CI (Q)

| id | Pri. | Où | Constat | Correctif | 
|---|---|---|---|---|
| Q-01 | P1 | `CT/ReceiverTest.kt:81-88`, `TvClient.kt:232-296` | `insufficientStorageIsFatal` : `sleep = {}` + `giveUpAfter = Int.MAX_VALUE` ⇒ **boucle serrée** si la réponse n'est pas 507 (503 « volume removed », port encore occupé) ; c'est aussi le trou produit X-04 | borne `giveUpAfter` + délai global dans le test ; port lié directement |
| Q-02 | P2 | `CT/DeviceTest.kt:23,27,31,59-64` | `ServerSocket(0).use { localPort }` puis rebind : course TOCTOU ⇒ `BindException` sous deux Gradle | `ReceiverServer.start(port=0)` + `boundPort` (comme `TvSshServerTest`) |
| Q-03 | P2 | `CT/ParentalTest.kt:484-540`, `C/parental/ParentalApi.kt:54,64,192-195` | `pinInTheUrlIsRefused…` : même motif de port + keep-alive JDK + corps POST rejeté avant lecture (hyp.) | port 0 ; `Connection: close` dans le client de test |
| Q-04 | P2 | `tools/tests/test_content_tools.py:246-292`, `content/langues-media/` (ignoré par git) | 2 des 3 échecs Python sont **locaux** : `os.walk` voit 7 mp4 ignorés par git ; le 3ᵉ (70 %) vient probablement de packs sans compétence (non exécuté) | exclure les chemins `git check-ignore` ; déclarer les médias ; mesurer le 70 % |
| Q-05 | P2 | `.github/workflows/android.yml`, `tools.yml` | CI : ni `check_versions.py`, ni `list_routes.py --check`, ni `content-map` ; aucun test `:sender`/`:receiver` | w15-09 (docs) + W14 w14-15 (CI, référence) |
| Q-06 | P2 | `docs/CHANGELOG.md`, `version.properties` | CHANGELOG arrêté à téléphone 1.2.29 / TV 0.14.17 alors que 1.2.32 (62) / 0.14.19 (64) sont publiées | w15-09 |

**Comptes** : P0 = 8 (X-15, X-03, X-04, L-01, L-02, T-01, B-02, B-03) ; P1 = 39 ; P2 = 61 (dont 1 P3). Total 108.

### 2.8 Les dix pires risques (ordre de traitement)

1. **T-01** TV figée sur « Prêt à recevoir » (R-04) : chaque copie LAN rapide, tous les jours.
2. **L-01 + L-02** copie figée avec puce verte (R-01) : clé de PIN introuvable et éviction du jeton par le garde-vivant Bluetooth.
3. **X-03 + B-02** suppression d'un original ou d'une source sans preuve (Déplacer, Mover) : perte définitive.
4. **X-15 + X-04** plantages (SecurityException, `startForeground`, `onTimeout`) et attentes sans fin.
5. **B-03** corbeille purgée par un saut d'horloge TV (TV hors ligne sans RTC : le cas camerounais).
6. **T-02** 429 définitif après trois envois interrompus : la TV ne reçoit plus rien jusqu'au redémarrage.
7. **B-05 + L-20** index `.filing` ou registre de confiance perdus par un défaut de lecture ou une coupure entre deux renommages.
8. **L-04 + L-05 + L-06** Bluetooth : `connect()` sans verrou, services TV non relancés, permissions en boucle (R-05).
9. **B-01 + T-10 + B-11** O(N²) sur la clé exFAT, E/S sur le fil principal : lenteur et ANR de la TV 32 bits.
10. **P-04 + P-05** position de lecture perdue à la coupure, `stop()` bloquant : l'expérience « film du soir » sur une TV à 1 Go.

## 3. Plan de stabilisation

Règles valables pour toutes les tranches : **(R1)** tout correctif commence par un test qui **échoue** sur `integration/agents` (sortie rouge puis verte citées dans le rapport) ; **(R2)** au plus **2 cahiers risqués** (transfert, liaison, confiance, cycle de vie) en parallèle, fichiers disjoints, **un seul** à la fois sur `C/tv/ReceiverServer.kt`, `C/trust/LinkDriver.kt`, `S/UploadService.kt`, `S/TvLink.kt` ; **(R3)** aucune fusion de fonctionnalité pendant le gel, seuls les cahiers W13/W14/W15 et les correctifs terrain passent ; **(R4)** audit Opus de tout diff touchant ces zones ; **(R5)** les fichiers hors zone d'un cahier sont **gelés** (lecture seule) ; **(R6)** la TV du propriétaire n'est jamais la cible d'un script (W14 D-W14-3) ; **(R7)** après chaque fusion risquée : `:core:test` complet + `compileDebugKotlin` des deux apps + (dès que W14 le livre) fumée `--tv fake`.

### 3.1 S0 : arrêter l'hémorragie (les 8 P0 + 3 P1 proches)

| Cahier | Modèle | Défauts | Hypothèse de cause racine | Correctif minimal | Test rouge d'abord | Gelé (ne pas toucher) |
|---|---|---|---|---|---|---|
| **w15-01** progression de réception TV + balayage des sessions | sonnet, audit Opus | T-01, T-02, T-06 (partie) | `receiving()` ne voit ni `.cbx` ni les sessions ; `sweep` jamais en marche | `TransferHost.progress()` + `sweep` périodique + éviction dans `begin` ; `receiving()` fusionne ; push `statusesChanged` ⇒ accueil ; tick 1 s pendant réception | MultipathServerTest : 2/5 blocs ⇒ `receiving()` non vide ; 3 `begin` + 31 min ⇒ 4ᵉ accepté ; UxTest texte de carte | `PartAssembler` (format `.cbx`), `/upload` classique, `BtServer` |
| **w15-02** résolution des clés de PIN (`PinKeys`) | sonnet, audit Opus | L-01, L-23 (partie) | égalité stricte des clés d'écran | `C/trust/PinKeys.resolve/keysOf` pur ; `PinStore.get` et `TvLink.savedFor/credentialForBase` l'appellent | `PinKeysTest` : table d'une clé par origine d'écran ⇒ jeton trouvé ; `credentialForBase(127.0.0.1)` ⇒ TV par défaut | `LinkDriver`, `TrustRegistry`, écrans (W13 w13-08/09 les suivent) |
| **w15-03** garde-vivant Bluetooth sans éviction de jeton | sonnet, audit Opus | L-02, L-03 (partie), L-07 | route tunnel = HELLO par minute ; 4 jetons max ; fsync sous verrou | garde-vivant `check` via tunnel, HELLO à mi-vie ; `issueToken` idempotent > 50 % de vie ; persistance hors verrou | LinkDriverTest `tunnelBase` : ≤ 2 jetons en 30 min, jeton de t0 accepté à t0+20 min ; TrustTest persistance lente | `LinkMachine` (hystérésis), `HelloHandler` wire, `BtProtocol` |
| **w15-04** services d'envoi : plus de plantage, plus d'attente infinie | sonnet, audit Opus | X-15, X-04, X-01, X-02, X-05, X-16 (abort) | exceptions non attrapées, bornes absentes, état global sans id | `catch SecurityException` ; `giveUpAfter`/« 30 min sans progrès » ; `onTimeout` ×3 services ; `try/catch startForeground` ; génération de travail + refus du 2ᵉ `start` ; `Waiting` BT dès `run()` ; `abort` à l'annulation | InFlightTest : `openAt` lève ⇒ `Failed` ; horloge factice ⇒ `Failed` après borne ; coordinateur deux `start()` ; travail fantôme ; `watch` service lent | `TransferClient`/`Lanes` (W13 w13-04), `ReceiverServer` |
| **w15-05** aucune suppression sans preuve (Déplacer, Mover) | sonnet, audit Opus | X-03, B-02, X-07 | reprise et dédoublonnage par nom seul ; vérification des bords | `/api/part?size=` + 409 sur `Meta` différent ; `partJson` ne dit `done` qu'à taille égale ; Déplacer : preuve (racine SHA ou octets envoyés = taille) ; file persistante de `MoveRequest` ; Mover : 8 derniers Mo comparés à la reprise, SHA complet avant suppression ≥ 100 Mo | InFlightTest « `.part` homonyme jamais repris », « final homonyme autre taille ≠ Done » ; MultiVolumeTest « milieu corrompu jamais validé » ; file `MoveRequest` | format `.cbx`, `UsbMigration` |
| **w15-06** persistance : horloge, index, registre | sonnet | B-03, B-05, L-20, B-10b | purge sur horloge murale ; lecture tout-ou-rien ; deux renommages | corbeille : jamais de purge sous horloge suspecte, séquence monotone ; `.filing` : échec ⇒ `adopt()` + `.bak`, par ligne ; `TrustFiles.load` ⇒ `.bak` ; `AtomicFile` pour l'état des téléchargements | TvAgentTest saut 1970→2026 ; FilingTest tronqué ; HelloCompatTest `.bak` seul ; DownloadManagerTest fichier vide | formats de fichiers (additifs seulement), `RentalLedger` |

Ordre : **S0-a** = w15-01 ∥ w15-02 ∥ w15-03 ∥ w15-04 (quatre zones disjointes ; R2 impose au plus deux « risqués » en même temps : w15-01 + w15-03 d'abord, puis w15-02 + w15-04) ; **S0-b** (après fusion de S0-a) = w15-05 ∥ w15-06. Effort ≈ 9 agent·jours. Résultat : R-01 et R-04 reproductibles en JVM et corrigés à la cause ; plus de perte d'original ; plus de plantage connu.

### 3.2 S1 : observabilité et porte (sans dupliquer W13/W14)

- La **porte** (harnais J, fumée F, liste H, candidate, retour arrière, `REGRESSIONS.md`) est **W14** ; les **codes support, `BlockerLog`, puce à trois vérités** sont **W13**. W15 ne les refait pas. W15-S1 livre ce qui manque aux deux :
- **w15-07** (sonnet, effort S) tests déterministes : Q-01, Q-02, Q-03 (port 0 et `boundPort`, borne de `giveUpAfter`, `Connection: close`), `LearnLotsTest` tolérant à un contenu local divergent (message clair au lieu d'un rouge), 20 passes vertes consécutives exigées.
- **w15-08** (sonnet, effort M) **banc d'endurance** `tools/soak/` (Python 3.12 stdlib) : boucles de copies LAN/BT contre la fausse TV du Mac (W14 w14-10) ou la TV émulée, coupures aléatoires, relevés mémoire/handles, seuils de `docs/test-plans/ENDURANCE.md`, rapport d'une page. Expose côté TV `GET /api/transfer/sessions` (lecture seule, derrière PIN/jeton) pour compter les sessions vivantes.
- **w15-09** (haiku, effort S) alignement documentaire : CHANGELOG (1.2.32 / 0.14.19), HANDOFF § 0, § 9 (liste NV de cet inventaire), `docs/STORAGE.md:182` (UsbMigration non branchée, selon décision D-W15-4), `TRANSFER.md` (429, abort), `REGRESSIONS.md` : lignes R-01…R-05 + F-06/F-07 avec leurs tests ; `tools/tests` : exclusion des chemins ignorés par git (Q-04).

### 3.3 S2 : durcissement par domaine (P1 restants, P2 groupés)

| Cahier | Modèle | Domaine | Défauts | Gelé |
|---|---|---|---|---|
| **w15-10** | sonnet, audit Opus | moteur de transfert (téléphone + TV) | X-06, X-09, X-10, X-12, X-17, X-22, X-08, X-11, T-03, T-04, T-05, T-16, T-17, T-18 | format `.cbx`, W13 `Blocker` (additif) |
| **w15-11** | sonnet, audit Opus | liaison et confiance (téléphone) | L-04, L-10 (téléphone), L-12, L-13, L-14, L-21, L-22 | `TrustRegistry` (w15-03), écrans W13 |
| **w15-12** | sonnet | découverte et tunnel | L-08, L-09, L-16, L-18, L-19 | `LinkDriver` |
| **w15-13** | sonnet | lecteur TV | P-04, P-05, P-06, P-07, P-11, P-12, P-14, P-16, P-17 | `Progressive` wire |
| **w15-14** | sonnet | fichiers compagnons et sous-titres | P-01, P-02, P-03, P-03b, B-06 | — |
| **w15-15** | sonnet | lecteur et streaming téléphone | P-10, P-10b, P-18, F-07, P-08, P-09, P-15, P-19, P-20, P-21 | protocole DLNA |
| **w15-16** | sonnet | bibliothèque, performance, espace de noms | B-01, B-07, B-08, B-09, B-10, B-12, B-13, B-15, B-16, B-17, B-18 | `Filing` (w15-06) |
| **w15-17** | sonnet, audit Opus | cycle de vie et notifications TV | T-07, T-08, T-09, T-10, T-11, T-14, T-15, T-19, L-05, L-06, L-11, L-17, B-11 | `ReceiverServer` routes |

Ordre : w15-10 + w15-17 (risqués) d'abord, puis w15-11 + w15-12, puis w15-13/14/15/16 (quatre non risqués, en parallèle). Effort ≈ 14 agent·jours.

### 3.4 S3 : endurance

- **w15-18** (haiku, exécution + rapport) : trois nuits selon `docs/test-plans/ENDURANCE.md`, sur fausse TV + émulateur (coordinateur) **et** une nuit sur les vrais appareils (propriétaire, § 7). Rapport `docs/agent-reports/soak-<date>.md` ; tout dépassement de seuil ouvre une ligne de `REGRESSIONS.md` et un cahier correctif (règle R1).

## 4. Plan d'endurance (résumé ; détail dans `docs/test-plans/ENDURANCE.md`)

| Scénario | Cibles | Durée | Seuils |
|---|---|---|---|
| copies LAN 1 Mo × 500, 100 Mo × 60, 2 Go × 4 (rapide puis classique) | fausse TV, TV émulée, puis vraie TV (propriétaire) | 8 h | 0 blocage sans texte ; 0 plantage ; ≥ 99 % de succès ; fichiers SHA-256 identiques ; `.cbx` = 0 orphelin à la fin |
| copies avec coupures aléatoires (Wi-Fi off 5-60 s, `kill` TV émulée, PIN tourné 1 fois) | idem | 4 h | reprise ≤ 60 s après retour ; jamais de 0 % ; jamais de 429 définitif |
| Bluetooth 2 Mo × 50 avec Wi-Fi coupé | vrais appareils | 2 h | ≥ 100 Ko/s ; % monotone ; carte TV avec % |
| lecteur : seek aléatoire × 500, pause/reprise, fin de fichier, reprise de position | TV émulée puis vraie TV | 2 h | 0 ANR, position restaurée ± 15 s, PSS stable |
| liaison : reboot TV × 10, changement d'IP × 5, TV éteinte 10 min × 5 | émulateur + vraie TV | 4 h | puce verte ≤ 90 s après retour, ≤ 2 jetons par heure, 0 « Aucune TV » |
| fuites : `dumpsys meminfo` toutes les 10 min, `ls /proc/<pid>/fd` (émulateur), threads | TV émulée, téléphone | toute la nuit | PSS TV dérive < 15 % ; fd < 200 ; threads < 80 ; notifications résiduelles = 0 à la fin |

## 5. Critères de sortie de la stabilisation

1. **0 P0 ouvert** au registre, et chaque P0 fermé par un test rouge-puis-vert cité dans `REGRESSIONS.md`.
2. **5 passages verts consécutifs** de la porte complète (W14 : J complet < 2 min + F `--tv fake` < 10 min) sur 5 commits distincts de `integration/agents`, sans relance.
3. **Trois nuits d'endurance** conformes à tous les seuils du § 4 (deux sur fausse TV/émulateur, une sur les vrais appareils).
4. **Liste humaine 12/12** (`CHECKLIST-HUMAINE-LIVRAISON.md`) deux livraisons de suite, sur la TV de référence.
5. Tests instables : **20 passes** consécutives de `:core:test :sshd:test` sans rouge.
6. Aucun P1 des familles « perte de données » ou « plantage » ouvert ; les P1 restants ont un cahier S2 planifié.

Tant que ces six points ne sont pas réunis, le gel reste en vigueur.

## 6. Vagues en attente et ce qui peut continuer

| Vague | État | Décision pendant le gel | Pourquoi |
|---|---|---|---|
| W4 (4a enveloppe, 4b dégradé, 4c vente) | w4-01/02/11 fusionnés (cœur) | **cœur seul** peut continuer (aucun fichier `S/`, `R/`) ; branchement app **attend** | 4b touche `ReceiverServer`/routes (zone S0) |
| W5 (boutique, jetons) | w5-02/03 fusionnés (cœur, non branchés) | idem cœur seul | — |
| W6 (parental, PhoneGate, preuve TV) | w6-01/03/04/05 fusionnés (cœur) | idem ; w6-17 `SendGuard` **attend** W13 S1 | branche les services d'envoi |
| **W7** (plug and play, 25 cahiers) | non lancée | **attend la sortie** ; ses cahiers reçoivent un parcours J obligatoire (W14) et se rebasent sur w15-02/03 (`PinKeys`, garde-vivant tunnel) | touche exactement les fichiers chauds |
| W8 (multivoie bas niveau) | abandonnée (propriétaire) | inchangé | — |
| W9 (chaîne de lots génératifs) | outils seulement | **peut continuer** (`tools/content-gen`, contenu) | aucun code app |
| W10 (boutique trois familles) | conçue | serveur et outils (w10-05/06/13/14/16) **peuvent continuer** ; TV/téléphone (w10-08…12) **attendent** | écrans |
| W11 (navigation allégée) | conçue | **attend** | écrans des deux apps |
| W12 (réglages signés) | conçue | w12-01 `settings-core`, w12-03/04/05/10 serveur **peuvent continuer** ; w12-06/07/09 (runtime apps) **attendent** | — |
| W13 | 12 cahiers prêts | **S1 après W15-S0** (w13-04/07/08/09 se rebasent sur w15-02/04) ; S2 après W15-S1 | même objectif, mêmes fichiers |
| W14 | en conception | **S1 en parallèle de W15-S0-a** (harnais J, zones disjointes : `CT/journey/`, `C/trust/HomeLinkView`, `C/xfer/XferTexts`) ; S2 fumée avant W15-S2 | la porte doit exister avant S2 |

## 7. Décisions pour le propriétaire (recommandation en gras)

| # | Question | Recommandation |
|---|---|---|
| D-W15-1 | Durée du gel | **jusqu'aux critères de sortie § 5**, estimée à 3 semaines (S0 ≈ 1 semaine, S1+S2 ≈ 1,5, S3 ≈ 0,5) ; point d'étape chaque vendredi dans HANDOFF § 0 |
| D-W15-2 | Usage pendant S0 | **éviter « Déplacer vers la TV » et le déplacement entre volumes de gros fichiers** tant que w15-05 n'est pas livrée (risque X-03/B-02) ; copier, puis supprimer à la main après lecture |
| D-W15-3 | Tests sur vrais appareils réalisés par le propriétaire | **oui, trois moments** : (1) après S0 : liste humaine points 3, 4, 6, 7, 9 (copie avec carte TV, coupure TV, PIN faux, Bluetooth) ; (2) après S2 : liste complète 12/12 ; (3) S3 : une nuit d'endurance « vrais appareils » (`ENDURANCE.md` § 6 : lancer le script, dormir, lire le rapport) |
| D-W15-4 | `UsbMigration` jamais branchée (B-04) | **retirer la mention de `STORAGE.md`** et garder le Mover ; brancher plus tard si besoin |
| D-W15-5 | Serveur média du téléphone sans authentification (P-10) | **corriger en S2** (jeton de session) ; en attendant ne pas caster sur un Wi-Fi public |
| D-W15-6 | Émulateur arm64 comme substitut de la TV 32 bits pour l'endurance | **oui** (= Q-W14-1) ; le 32 bits reste la nuit « vrais appareils » |
| D-W15-7 | Téléphone réel pour la fumée et l'endurance | **oui en lecture + `adb install -r`**, jamais `pm clear`/`uninstall` (= B-W14-1) |
| D-W15-8 | Jetons TV : les persister sur disque ou non (L-07) | **ne plus persister** (un jeton perdu se refait par HELLO) ; si refus, persister hors verrou |
| D-W15-9 | Logcat de l'incident R-01 (B-W13-1) | fournir `adb logcat -d -s TvLink:I UploadService:I CastSession:I` dès que le cas se reproduit ; ce soir le logcat montre déjà le cas F-06 |

## 8. Ce que cette conception n'a pas pu vérifier

- Aucun test exécuté, aucun APK construit : les lignes citées sont lues, pas exécutées ; les causes « hyp. » (L-02 route tunnel réellement prise par le S21+, T-05 comportement de `setLength` sur vfat, B-02 persistance partielle sur exFAT, T-07 `startForeground` sur GaiaOS) demandent les vrais appareils.
- Le journal de la TV reste illisible depuis SSH (uid différent) : R-04 est expliqué par le code, pas confirmé par un logcat TV ; `diag-receiver-progress` (Opus, en cours) doit le confirmer ou l'infirmer avant de lancer w15-01.
- Le logcat du téléphone de ce soir couvre 17:06-17:28 (app 1.2.31 puis 1.2.32) : il montre F-06 et F-07, pas l'incident R-01 du matin.
- Les jauges de jetons et d'effort des cahiers sont estimées.
- W14 n'a pas encore de cahiers écrits : les noms `w14-NN` cités viennent de sa conception § 6 ; l'index W15 s'y réfère par ces noms et sera à relire quand ils existeront.
