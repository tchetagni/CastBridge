# Inventaire des relais téléphone / TV / monde extérieur (CastBridge, branche integration/agents)

## 0. Cadre, légendes, limites

- Dépôt : `/Users/letcheta/Library/CloudStorage/OneDrive-Personnel/CastBridge`, branche `integration/agents`, HEAD `c5590e40`, arbre propre. Versions (`version.properties`) : téléphone 1.2.52-beta (82), TV 0.14.44-beta (114), serveur 1.2.4 (8, déployé le 2026-10-07 d'après `docs/HANDOFF.md:20`).
- Lecture seule : rien compilé, rien exécuté, aucun appareil. Les négations (« aucun appelant », « aucune TV ne … ») viennent de `grep` sur `android/`, `backend/`, `server-play/`, `tools/`.
- Préfixes (chemins absolus, racine `<R>` = `/Users/letcheta/Library/CloudStorage/OneDrive-Personnel/CastBridge`) :
  - `S/` = `<R>/android/sender/src/main/kotlin/castbridge/sender/` (téléphone)
  - `R/` = `<R>/android/receiver/src/main/kotlin/castbridge/receiver/` (TV)
  - `C/` = `<R>/android/core/src/main/kotlin/castbridge/core/` (cœur partagé)
  - `OL/` = `<R>/android/ownerlib/src/main/kotlin/castbridge/owner/`
  - `B/` = `<R>/backend/src/main/java/castbridge/server/`
  - `D/` = `<R>/docs/`, `T/` = `<R>/tools/`
  - `SM` = `<R>/android/sender/src/main/AndroidManifest.xml`, `RM` = `<R>/android/receiver/src/main/AndroidManifest.xml`
- États : **LIVRÉ** (en production ou éprouvé sur matériel, preuve dans les docs) ; **FUSIONNÉ** (dans la branche, compile, tests JVM, pas ou partiellement éprouvé sur matériel) ; **INERTE** (code fusionné mais chaîne incomplète : rien n'arrive à destination) ; **CONÇU** (document seulement, aucun code).

## 1. Lecture d'ensemble

1. Il existe un seul vrai « tuyau » TV vers Internet par le téléphone : le proxy SOCKS5 sur RFCOMM (M1). Tout appel TV vers le serveur passe par `Routes` (réseau propre d'abord, puis ce tuyau) : heartbeat, télémétrie, plantages, signalements, mises à jour APK, questions de quiz, portefeuille, ticket et session du quiz en ligne, tunnel d'assistance. Exceptions qui n'utilisent PAS `Routes` : aria2 (téléchargements), `TvLotFetcher` (bouton « Mettre à jour les lots Langues »).
2. Le téléphone est coursier de contenu signé pour : lots Apprendre/Quiz/Langues, packs de questions, locations scellées. Il est émetteur de commandes pour : activation, installation d'APK, téléchargements, télécommande, envoi de fichiers.
3. La chaîne « ordres différés signés serveur → téléphone → TV » est INERTE : serveur éteint en production, TV non câblée, téléphone sans appairage effectif (M7).
4. Côté téléphone, tout ce qui relève du « partage d'Internet vers la TV » est manuel et non persistant. La seule bascule automatique concerne l'autre sens (API de la TV via le téléphone, M2).
5. Les conceptions W7 (CBSY/CBSX), W21 (coursier scellé de télémétrie) et W23B (avis d'activation scellé, reçu signé, relais discret) ne sont pas codées (M18).

## 2. Fiches par mécanisme

### M1. Partage d'Internet du téléphone à la TV (service `BtGatewayService`, passerelle « Internet »)
- **Direction** : TV → téléphone → Internet (puis retour). Au niveau de l'application TV seulement, pas une route système.
- **Transport** : RFCOMM sécurisé, service `…0002` « CastBridge Internet » (`C/gateway/BtGateway.kt:46`, `R/BtGatewayHost.kt:43`). Trames CBG1 (type u8, flux u16, longueur u32 ; fenêtre 256 Kio par flux, 32 flux, PING, DIAG ping/trace : `C/gateway/BtGateway.kt:28-51`). TV : proxy SOCKS5 `127.0.0.1:1080` (`R/BtGatewayHost.kt:164`, `C/gateway/BtGateway.kt:243-253`). Téléphone : `Exit` ouvre de vraies sockets TCP (`S/BtGatewayService.kt:116-124`).
- **Sécurité** :
  - Appairage Android (RFCOMM chiffré) puis HELLO `CBG1` + PIN, ou `------` pour un téléphone de confiance (`R/BtGatewayHost.kt:30-34`, `C/trust/TrustRegistry.kt:259`). PIN faux compté sous la clé unique `bt-gateway` (non par appareil). Un seul téléphone à la fois (`C/gateway/BtGateway.kt:266`).
  - Au-dessus, TLS de bout en bout TV ↔ serveur (`C/connect/Routes.kt:7-14`, aucun trust personnalisé) : le téléphone voit nom d'hôte, port et volume, pas le contenu.
  - Réserves factuelles : seul le bouclage est refusé (`S/BtGatewayService.kt:117-119`) ; le réseau local du téléphone et tous les ports sont joignables (`D/REMOTE-TUNNEL-TV.md:51`). Aucun test de réseau facturé ni plafond d'octets : le partage utilise le réseau par défaut du téléphone, donc éventuellement les données mobiles. Le SOCKS de la TV est sans authentification (`C/gateway/BtGateway.kt:322`) : tout processus de la TV peut l'utiliser.
- **Reprise** : boucle de reconnexion 1 s → 15 s tant que le service tourne (`S/BtGatewayService.kt:56-83`) ; arrêt si PIN refusé (`:79`). `START_NOT_STICKY` (`:39-50`), aucune persistance, pas de redémarrage automatique. Côté TV, `Routes` retient la voie « passerelle » 10 min (`C/connect/Routes.kt:15-16,33`).
- **Démarrage** : uniquement par l'interrupteur « Partager l'Internet du téléphone avec la TV » (Avancé > Bluetooth, `S/TvHub.kt:289-305`), actif seulement avec un PIN ou jeton utilisable (`:301`). Aucun déclencheur automatique, aucune demande venant de la TV. Côté TV le serveur démarre à chaque démarrage déverrouillé (`R/TvService.kt:545-546`) ; une TV verrouillée ne le démarre pas (`:266`).
- **Discrétion** : notification permanente, canal « Internet partagé avec la TV » (LOW), texte « La TV utilise l'Internet du téléphone », action « Arrêter » (`S/BtGatewayService.kt:45,66,126-135`). TV : ligne d'état « Internet via le téléphone (nom BT du téléphone) » (`R/BtGatewayHost.kt:83`). Journaux : `Log.i "Internet via <nom BT>"` côté TV (`C/gateway/BtGateway.kt:268`, `R/BtGatewayHost.kt:34`). Télémétrie `gateway_session` émise des deux côtés (`S/BtGatewayService.kt:74`, `R/BtGatewayHost.kt:87`), sous consentement « usage ». Le heartbeat porte `btGateway` (`C/device/DeviceReport.kt:123-172`).
- **État** : FUSIONNÉ (ancien : commit `b4a43a62`). Débit réel « non mesuré » (`D/coordination/DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md:22`).
- **Docs** : `D/REMOTE-TUNNEL-TV.md` §5, `D/TELEMETRY.md:62-64`, `D/QUIZ.md:269`.

### M2. Passerelle Bluetooth « API 18765 + SSH 2222 » (`BtSshGatewayService`, dans `S/BtSshGateway.kt`)
- **Direction** : Mac (adb forward), Termux, ou clients du téléphone lui-même → téléphone `127.0.0.1` → TV (RFCOMM) → TV `127.0.0.1:8765` / `:2222`.
- **Transport** : ports locaux `2222` (SSH) et `18765` (API) (`S/BtSshGateway.kt:143,145`). RFCOMM vers `…0002` (SSH, octets bruts), `…0003` (API v1, une liaison par requête), `…0004` (API v2, liaison partagée, trames OPEN/DATA/CLOSE/PING ; `C/tv/BtProtocol.kt:79-85`). Côté téléphone `C/tunnel/TunnelGateway.kt:27`, `LinkPool.kt:20`. Côté TV `R/BtApiControl.kt:41-53`, `R/BtSshBridge.kt:12-25`, `R/BtTunnelBridge.kt:25-115`, `C/tunnel/TcpTunnel.kt`.
- **Sécurité** :
  - Tunnel d'octets : la TV fait toute la vérification (PIN `X-CB-Pin`, jeton `X-CB-Token`, code parental ; SSH par clé autorisée).
  - Admission TV : appareil appairé ET (« API par Bluetooth » actif, défaut ON, OU téléphone de confiance) (`R/BtApiControl.kt:35-39`). Échecs PIN comptés par appareil `bt:<adresse>`.
  - L'API n'est jamais exposée au réseau ; le SSH ne l'est que sur option explicite avec avertissement (`S/BtSshGateway.kt:234-239`).
  - HTTP en clair entre client et TV : le téléphone relais peut lire PIN et jeton. SSH est chiffré de bout en bout.
  - `adb forward` donne au Mac un accès complet au téléphone (`D/REMOTE-TUNNEL-BT.md:46-47`).
- **Reprise** : `LinkPool` (verrou par TV `BtConnectLock`, ≥ 1,5 s après fermeture, 3 essais avec gigue, pause 8 s, PING 15 s, fermeture après 45 s d'inutilité ; `D/BT-PLUG-AND-PLAY.md:127-137`). Pas de persistance ; `START_NOT_STICKY`.
- **Démarrage** :
  - Bouton « Passerelle Bluetooth » en un appui (API + SSH, boucle locale ; `S/BtGatewayCard.kt:177-194`, `C/ux/BtGatewayView.kt:86`).
  - Bascule automatique, API seule, quand la TV est absente du Wi-Fi depuis 8 s (`C/ux/BtGatewayView.kt:153`, `S/BtGatewayCard.kt:93-127`). Réglage ON par défaut (`:51-63`), mais seulement tant que l'onglet « CastBridge TV » est affiché (`D/agent-briefs/sonnet-btgw-01-tv-tunnel-on-demand.md`, point 2 : non fait).
  - Démarrage automatique par `ensureApi` quand la liaison de confiance n'a pas de route IP et que l'app est au premier plan (`S/TvLink.kt:139-145`, `S/BtSshGateway.kt:177`).
  - Écran manuel Avancé > Bluetooth (`S/BtSshGateway.kt:190-245`).
  - Route de dernier recours de tous les clients TV du téléphone (`S/TvLink.kt:126-129`, `C/tv/BtProtocol.kt:437-467`, `C/trust/PhoneLink.kt:76`).
- **Discrétion** : notification « CastBridge — Passerelle Bluetooth vers <nom TV> : 127.0.0.1 », canal « Passerelle Bluetooth » (LOW), action « Arrêter » (`S/BtSshGateway.kt:71,125-132`). Barre `GatewayStrip` sur les autres onglets (`S/BtGatewayCard.kt:268`). Carte avec lignes à copier ; le code est toujours `<code>` (`C/ux/BtGatewayView.kt:104`). Journaux : `Log.i "CastBridgeSshGw"` (`S/BtSshGateway.kt:116`) sans PIN ; TV : `Log.i "link from <adresse BT>"` (`R/BtTunnelBridge.kt:89`) et lignes `TcpTunnel` avec l'adresse du pair.
- **État** : FUSIONNÉ, éprouvé en partie sur matériel le 2026-10-03 (`/api/hello` 200 en 0,8 s par liaison partagée ; SSH refusé car éteint côté TV : `D/agent-reports/bt-gateway-access.md`). Non fait : SSH à la demande, bascule en arrière-plan, mode PIN avec deux TV.
- **Variantes** :
  - Mac : `T/remote/tv-tunnel.sh up|status|down` (adb forward 2222/18765).
  - Mac sans téléphone : `T/cbt-rfcomm/`, `T/bt-ssh-bridge.py` (Linux).
  - Relais manuel du propriétaire : `adb forward` + `nc` dans le shell du téléphone vers l'app « CastBridge Dev » de la TV, port 2223 (`D/HANDOFF.md:102-103`, `D/DEV-BRIDGE.md`) ; documentation seulement.
- **Docs** : `D/REMOTE-TUNNEL-BT.md`, `D/ADMIN.md:360-420`, `D/BT-PLUG-AND-PLAY.md`.

### M3. TV → serveur : `ServerLink` + `Routes` (heartbeat, télémétrie, plantages, signalements, mise à jour APK, quiz) — pistes 5 et 10
- **Direction** : TV → serveur `https://bridge.sti-cm.com`, directement d'abord ; si ça échoue et qu'un téléphone partage son Internet, via M1 (`R/TvConnect.kt:85`, `C/connect/Routes.kt:31-52`). Le téléphone n'est que transporteur d'octets TLS.
- **Calendrier** (`C/connect/ServerLink.kt:151-175`) : `tick` chaque minute ; heartbeat 15 min (`:178-202`, répond `blocked`, `channel`, `checkUpdate`, `heartbeatSeconds`) ; télémétrie 15 min (`:217-229`) ; plantages (`:204-214`) ; signalements de contenu (`:232-245`) ; recherche d'APK au démarrage puis 12 h (`:250-284`) ; questions du quiz 1 fois par jour (`:340-359`) ; packs de questions 6 h (`:367-373`).
- **Sécurité** :
  - HTTPS imposé (`C/connect/ConnectState.kt:20-46`) ; jeton d'appareil Bearer.
  - APK : manifeste signé Ed25519, taille et SHA-256, téléchargement repris par `.part` avec `Range` et `If-Range` (`C/update/UpdateClient.kt:71,89-136`).
  - Rien ne part avant l'écran d'information (`needsConsent`, `ServerLink.kt:152`). Essentiel (heartbeat, erreurs, installations de MAJ) toujours ; « usage » sur consentement (`D/TELEMETRY.md:19-32`).
- **Reprise / persistance** : réglages et jeton dans les préférences `castbridge_connect` (`R/TvConnect.kt:41-45`) ; `telemetry/events.jsonl` borné à 2 Mo, événements idempotents par UUID (`D/TELEMETRY.md:48,62-64`) ; 5 plantages (`R/TvConnect.kt:87`) ; installation d'APK après lecture seulement (`R/TvConnect.kt:182-189`), nécessite « Installer des apps inconnues » (`R/UpdateInstaller.kt:86,137,184-193`).
- **Discrétion** : aucune notification dédiée côté TV ; état dans « Connexion & réglages » (« Connecté (via la passerelle Bluetooth du téléphone) », `ServerLink.kt:186`). Messages d'erreur nettoyés des chemins et noms de fichiers (`C/connect/Routes.kt:57-63`). Sondes périodiques `connectivitycheck.gstatic.com` vers un tiers, y compris via le téléphone, dès que les conditions d'usage sont acceptées (`R/TvService.kt:576-583`, `R/TvNetDiag.kt:19-26`).
- **TV verrouillée (non activée)** : aucun contact serveur (`R/TvService.kt:264-266` ; `TvConnect.start` n'est appelé qu'à `:308`).
- **État** : FUSIONNÉ ; serveur LIVRÉ (`/api/v1/updates`, `/dl/`, `/api/v1/events/batch`).
- **Docs** : `D/API-SERVER.md:1-125`, `D/TELEMETRY.md`.
- **Réponse à la piste 10** : la TV envoie directement, ou par la passerelle quand elle existe ; aucun coursier (voir I-7). Seuls les signalements de contenu ont aussi une voie via le téléphone (M11).

### M4. Licences et portefeuille NDEM/MBOKO — piste 4
- **Direction** : TV → serveur DIRECT en HTTPS (`WalletClient`), avec repli par M1 ; jamais relayé par un code du téléphone.
- **Fichiers** : `R/wallet/WalletHub.kt:217-228` (`RoutesTransport` : `link.routes.call`, `Authorization: Bearer <jeton d'appareil>`), `:101` (client), `:197-204` (identité : code d'appareil, activation choisie, signataire d'installation), `:133` (synchronisation à l'ouverture, après chaque opération, toutes les 15 min). Cache d'instantané signé `cbw1` en lecture seule (`:96`). Clés d'idempotence rejouées après coupure (`:181-184`). L'en-tête `Date` du serveur sert une seule fois à rejouer la preuve `BIND_PROOF` (`C/wallet/ui/WalletClient.kt:76-89`).
- **Sécurité** : TLS + jeton d'appareil + preuve de possession Ed25519 de la clé d'installation (`castbridge-wallet-bind-v1`). Le serveur ouvre licence et poste quand une activation de production est présentée dans `wallet/sync` (registrar ON par défaut : `D/DEPLOIEMENT-SERVEUR-1.2.1.md:13-16`).
- **Téléphone** : aucun code portefeuille. Pas de route locale `/api/wallet`, pas d'import de bon (`VoucherKeys(KeyRing(emptyList()), emptyMap())`, `WalletHub.kt:96`) : « bon poussé par le téléphone » et « les téléphones lisent le solde de la TV » = CONÇU (`D/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` § 4, § 6).
- **Activation** (la clé `cbx1`) : jamais téléchargée, elle vient du propriétaire (M12).
- **État** : FUSIONNÉ ; serveur LIVRÉ (licences et portefeuille allumés depuis 1.2.0 : `D/DEPLOIEMENT-SERVEUR-1.2.0-PORTEFEUILLE.md:104-141`) ; TV du propriétaire enregistrée (`D/HANDOFF.md:91`).

### M5. Quiz en ligne — piste 9
- **Règle du propriétaire** (`D/coordination/DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md:5-14`, I-1 à I-5 aux lignes 18-22) : seule une TV activée et connectée à Internet joue en ligne. Les téléphones sont des joueurs locaux relayés par leur TV et ne parlent jamais au service. La passerelle Bluetooth compte comme « Internet » et sert de liaison de référence (EDGE, 40 kbps).
- **Direction / transport** : TV → `castbridge-play` en SSE + POST sur HTTPS (`C/quiz/online/PlayHttpTransport.kt`), proxy SOCKS quand `routes.lastVia == GATEWAY` (`R/quiz/PlayHub.kt:163`). Ticket `cbp1` demandé à l'API par `Routes` (`:109-122`). Téléphones ↔ TV : page `/quiz` en HTTP local (`R/QuizHub.kt:126-132,154-156`, `R/quiz/PlayHub.kt:136`).
- **Sécurité** : ticket `cbp1` à usage unique (Ed25519) + activation `cbx1` + preuve de possession de la clé d'installation à chaque `create` et `join` (`D/PLAY-PROTOCOL.md:133-146`). Aucun navigateur (`PLAY_WEB=0`).
- **Reprise** : `resume{roomId, token, lastSeq}`, anneau de 50 événements, hôte perdu = pause puis abandon à 60 s.
- **Discrétion** : tuile « Partie Internet » et raisons dites (`C/quiz/online/PlayTvScreens.kt:36-47`). Interrupteur « Quiz en ligne » dans le menu TV (`R/PlayerActivity.kt:786-788`) ; défaut compilé ON depuis la décision du 2026-10-04 (`PlayTvScreens.kt:17`).
- **État** : TV FUSIONNÉE ; service de jeu en staging privé seulement (`127.0.0.1:7091`, aucune route nginx `/play/`, pas de clé de ticket : `D/HANDOFF.md:382`, `D/PLAY-OPS.md:3-5` « écrit, jamais exécuté »). Aucune variable `CASTBRIDGE_PLAY_*` dans l'override de production (`D/DEPLOIEMENT-SERVEUR-1.2.0-PORTEFEUILLE.md:104-141`) : la tuile affiche donc « service indisponible ». Échecs en ligne : relais sans serveur (`D/CHESS.md:128-135`).

### M6. Assistance à distance — piste 11
- **Mécanisme codé** : tunnel SSH inverse permanent, la TV ouvre `-R 127.0.0.1:<port propre>:127.0.0.1:2223` vers `bridge.sti-cm.com:2200` (compte `cbtunnel`) ; experts via `ssh -J cbexpert@…`. Décision du propriétaire du 2026-10-02 : toujours actif, sans consentement par session (`D/REMOTE-TUNNEL.md:3,8`, `D/REMOTE-TUNNEL-TV.md:3,6`).
- **Chemin réseau** : TV directe, sinon SOCKS de la passerelle du téléphone, sans bascule en cours de session (`R/TunnelHub.kt:140-145,149,165-177` ; `D/REMOTE-TUNNEL-TV.md:50-58`). Le téléphone ne voit que du SSH chiffré.
- **Sécurité** :
  - Clé ed25519 propre à la TV (`files/tunnel/client/tunnel_key`) ; empreinte de la clé d'hôte épinglée ; liste d'experts signée hors ligne (portée REGISTRY) revérifiée toutes les 15 min.
  - sshd du tunnel sur `127.0.0.1:2223` seulement (`R/TunnelHub.kt:50,189-201`), distinct du SSH utilisateur (2222).
  - Quatre portes : conditions d'usage acceptées sur la TV, TV déverrouillée avec activation comptante, Internet, serveur actif.
- **Reprise** : attente 5 s → 10 min avec gigue ; 403 = pause 24 h ; 404 ou refus = au moins 1 h (`D/REMOTE-TUNNEL-TV.md:16-20`). Journal local `files/tunnel/journal.log` (24 ko x2, jamais de clé, jeton ni code ; seul l'identifiant d'expert est noté).
- **Discrétion** : silencieux au quotidien. Transparence : ligne « Assistance à distance : connectée / hors ligne / en attente d'acceptation » (`C/tunnel/TunnelJournal.kt`), journal dans « À propos », paragraphe de confidentialité (`C/tunnel/TunnelTerms.kt:46`). Le texte légal est signalé « à valider par un juriste » (`TunnelTerms.kt:8-17`). Le téléphone affiche les conditions avant l'envoi de clé (`S/ActivateTvActivity.kt:133-135`, `TunnelTerms.PHONE_NOTE:40`).
- **État** : TV FUSIONNÉE, jamais essayée sur vraie TV ni contre vrai OpenSSH (`D/REMOTE-TUNNEL-TV.md:3,74`). Serveur `castbridge.tunnel.enabled=false` par défaut et absent de l'override de production : routes 404 ; une TV qui a accepté les conditions réessaie l'enrôlement au moins toutes les heures.
- **Autre conception (non codée)** : `D/REMOTE-MANAGEMENT.md` (WireGuard `wg0` sur le serveur, `assist-broker`, consentement par session de 30 min, billet signé, téléphone « passerelle » en phase 1). Seul le serveur WireGuard existe (UDP 51821 ouvert, aucun poste) : CONÇU seulement.

### M7. Ordres différés signés serveur → téléphone → TV — piste 2
- **Direction** : administrateur → serveur (`/admin/orders`, `/api/v1/admin/orders`) → téléphone (messager : `GET /api/v1/orders?since=`, `POST /orders/pair`, `POST /orders/acks`, jeton d'appareil) → TV (trames Bluetooth 16 à 21, jeton `cbx1` type `order`, clé `policy`).
- **Fichiers** :
  - Serveur : `B/orders/OrderController.java:23-58`, `OrderService.java` (`on()` ligne 65), migration V60.
  - Cœur : `C/policy/PolicyEngine.kt`, `OrderFrames.kt:16-30,44-71`, `OrderCourier.kt:25-92`, `OrderTransport.kt:17-64`.
  - Téléphone : `S/OrdersRuntime.kt:25-102` (jobs : synchronisation 6 h, livraison 30 min, file `orders/queue.json`) ; `S/PhoneConnect.kt:44` (le seul appelant).
  - TV : `R/PolicyHub.kt:19-38`.
- **Sécurité** : enveloppe `cbx1`, liste fermée de 14 actions, signature vérifiée par la TV (portée, cible, séquence par clé, fenêtre, liste blanche), idempotence (même jeton = même accusé), aucune donnée personnelle. Limites : les accusés ne sont pas signés (`D/ORDRES.md:139`) ; ordres signés mais non chiffrés.
- **Reprise** : `OrderQueue` persistante, reprise par bloc (`NEED id|offset`), séquence strictement croissante par clé.
- **Discrétion** : le téléphone n'a ni écran, ni notification, ni réglage (`S/OrdersRuntime.kt:19-22`). Le journal « À propos > Politiques appliquées » et l'avis d'usage de `D/ORDRES.md:136` ne sont pas implémentés.
- **État : INERTE.** Preuves :
  - `R/PolicyHub.kt` n'est appelé nulle part (`init`, `onOwnerFrame`, `PolicyGate`). `D/coordination/DESIGN-W23B-…:1` le confirme.
  - `OwnerChannelServer` ne traite que `DEVICE_INFO_REQUEST` et `ACTIVATION` ; tout autre type reçoit `RESULT(0) "Non pris en charge par cette TV"` (`C/owner/OwnerChannel.kt:38-39`). `OwnerFrames` ne définit que les types 1 à 10.
  - `OrdersRuntime.learnTvCode` (`S/OrdersRuntime.kt:42`) n'a aucun appelant : `pairedCodes()` reste vide, `pair()` n'est jamais appelé, `deliverNow` ne livre à personne.
  - `StreamOrderLink` écrit le « CBTO » mais ne lit pas l'écho « CBTO » de la TV (`C/policy/OrderTransport.kt:51-63` ; `C/owner/OwnerChannel.kt:32` l'envoie). Un `read()` lirait « CBT » comme trame et lèverait « liaison fermée ». À confirmer par test (`OrderTransportTest` ne simule pas l'écho).
  - Serveur : `CASTBRIDGE_ORDERS_ENABLED` absent de l'override de production (404). Dette connue : `OrderService.release` interblocage MySQL (`D/HANDOFF.md:111`).
- **Docs** : `D/ORDRES.md` (§9 trames, §12 limites, §13 intégration restante), `D/agent-reports/deferred-orders.md`.

### M8. Lots Apprendre / Quiz / Langues — piste 6
- **Voie principale** : serveur → téléphone → TV.
  - Téléphone : `S/LotsRuntime.kt:149-177` (`syncNow` : catalogue signé, `HttpLotRemote` avec jeton d'appareil, Wi-Fi seulement par défaut, `Range`, SHA-256, signature, contrôle du contenu par le consommateur TV).
  - File `DeliveryQueue` persistante par TV (`lots/delivery-queue.json`, `LotsRuntime.kt:66`) ; `deliver` (`:242-268`).
  - Déclencheurs : synchronisation toutes les 12 h (job `4431`), livraison toutes les 30 min (job `4433`) ou immédiate (`4432`, `:293-310`), `ACL_CONNECTED` du Bluetooth de la TV et démarrage du téléphone (`LotsTriggerReceiver`, `:334-344`), session de liaison (`S/TvLink.kt:143`).
  - Boutons : « Envoyer à la TV », « Télécharger et envoyer » (`S/LotsScreen.kt:83,110,196`). Pas de bouton « Envoyer à la TV » dans `S/TvHome.kt`.
- **Transport phone → TV** : HTTP `/api/lots/*` (Wi-Fi LAN, Wi-Fi Direct) ou CBT1 Bluetooth, lot puis preuve (`S/LotsRuntime.kt:213-220` ; `C/lots/LotPush.kt`). Le tunnel API Bluetooth n'est pas utilisé ici.
- **Sécurité** : la TV re-vérifie signature du catalogue, taille, SHA-256, `minAppVersion`, retour en arrière (téléphone jamais cru) ; budget TV 10 Mo, téléphone 100 Mo ; PIN ou jeton ; `/api/activation/install` exclu des jetons (`C/trust/TrustRegistry.kt:259-262`).
- **Reprise** : octet exact confirmé par la TV (taille du `.part` TV), une entrée par lot, la dernière version remplace, le manifeste de la TV fait foi (`D/LOTS.md:80-93`). Livraison uniquement vers la TV par défaut (`S/LotsRuntime.kt:202`).
- **Discrétion** : aucune notification, uniquement l'écran « Données hors ligne » ; journaux sans URL ni jeton (`:172`).
- **Voie directe TV → serveur (Langues)** : bouton « Mettre à jour les lots Langues », seulement quand le système TV dit Internet VALIDATED, jamais planifié (`R/LanguesHub.kt:45-60`, `C/lots/TvLotFetcher.kt:21-94`). Sans proxy : elle ne profite pas de M1 (I-3).
- **État** : FUSIONNÉ (« Non vérifié hors JVM », `D/LOTS.md:163`). Docs : `D/LOTS.md`, `D/DOWNLOADS.md` (cadre aria2, hors sujet pour les lots).

### M9. Packs de questions Quiz (relais par le téléphone)
- **Direction** : serveur → téléphone → TV (`C/quiz/QuizPackRelay.kt` : `GET /api/quiz/packs/status`, `POST /api/quiz/packs/push?info=…`, TV : `R/QuizHub.kt:111`, `R/TvService.kt:339`).
- **Déclencheur** : automatique à l'ouverture de l'écran Quiz du téléphone, si la TV est trouvée par mDNS et qu'un PIN ou jeton existe (`S/QuizScreen.kt:78-91`).
- **Sécurité** : la TV contrôle taille, SHA-256, signature serveur et contenu. HTTP LAN en clair.
- **Doublon** : la TV tire aussi ses packs et questions seule (`ServerLink.syncQuiz` et `refillQuizPacks`, voir I-4).
- **Docs** : `D/QUIZ.md:262-288`.

### M10. Locations scellées : propriétaire → téléphone → TV
- **Fichiers** : `S/RentalDeliveryActivity.kt:25-97`, `C/lots/RentalDelivery.kt`, route TV `/api/rental/*` (`R/TvService.kt:341`).
- **Direction** : l'utilisateur fournit des fichiers `.lot`, `catalog.json` et la ligne « produit@période », reçus du propriétaire par un canal humain (message, partage) ; le téléphone les envoie en HTTP Wi-Fi seulement (`HttpTvTransport`).
- **Reprise** : envoi par offset, un lot déjà présent n'est pas renvoyé. Aucun reçu signé.
- **État** : FUSIONNÉ.

### M11. Signalements de contenu TV → téléphone → serveur ; rapports parentaux TV → téléphone
- **Signalements** : la TV expose `GET /api/content/reports` et `POST /api/content/reports/ack` (`C/content/ContentFeedback.kt:75-100`) ; le téléphone les tire quand l'écran TV est ouvert (`S/TvScreen.kt:124`, HTTP LAN + PIN) et les téléverse (`POST /api/v1/content/reports`). Doublon avec le téléversement direct de la TV (`ServerLink.flushReports`), sans risque : déduplication par `id` et `dedupeKey` (`C/content/ContentReport.kt:84,117`).
- **Rapports parentaux** : pris par le téléphone du parent en Bluetooth CBTP, signés par la TV, boîte de réception bornée ; aucun serveur (`S/ParentalInbox.kt`, `D/PARENTAL.md`). Job de 15 min seulement sur un téléphone désigné ; notification « Rapports du contrôle parental » avec écran verrouillé privé (`:95-110`).

### M12. Activation de la TV par le téléphone, et demande d'appareil — piste 3
- **Direction** : propriétaire → (message, e-mail, QR, fichier) → téléphone → TV. Sens inverse pour la demande d'appareil : TV → téléphone → propriétaire (feuille de partage Android).
- **Bluetooth (canal propriétaire)** : service `…0005` « CastBridge Owner » (`C/owner/OwnerFrames.kt:13`, `R/OwnerBtHost.kt:21-60`), trames CBTO `DEVICE_INFO_REQUEST` (5), `DEVICE_INFO` (6), `ACTIVATION` (8) ; client `OL/TvBluetooth.kt:112-130` (appaire seul au besoin, vérifie le service). La TV ne fait que placer la clé dans l'écran d'activation : le propriétaire confirme sur la TV « Valider la clé » (`R/OwnerBtHost.kt:31-36`, `R/ActivationCenter.kt:128`). Démarré même TV verrouillée (`R/TvService.kt:265`).
- **Wi-Fi** : le téléphone choisit d'abord le LAN (TV liée, ou TV `locked=1` trouvée par mDNS et touchée par l'utilisateur ; adresses privées seulement) puis Bluetooth en repli (`S/ActivateTvActivity.kt:88-125`, `C/owner/ActivationSend.kt:21-45,84-95`). TV verrouillée : une seule route HTTP `POST /api/activation/install` (`C/tv/activation/LockedActivationApi.kt:53-101`, `R/TvService.kt:217-257`) : réseau local, en-tête `Host` local, code de connexion obligatoire (jamais le jeton), conditions d'usage acceptées vérifiées AVANT le code (409), 16 Kio, 10 essais / 10 min / adresse, plafond global 20 codes faux / 10 min, code régénéré après activation.
- **Sécurité** : la clé est signée et liée au code d'appareil de cette TV (3 vérifications identiques à la saisie). Limites dites : code à 6 chiffres, TV non authentifiée par le téléphone (HTTP, aucun TLS ni empreinte), compteurs en mémoire (`D/TV-ACTIVATION-CLE-USB.md:39-44`).
- **Demande d'appareil** : `GET /api/tv/device-request` (cinq clés seulement), lue par `S/TvDeviceRequestActivity.kt` si téléphone de confiance ou code connu ; `S/ActivateTvActivity.kt:182-199` la lit aussi par Bluetooth ou `/api/activation/request` et la partage. Contenu non secret : code, k, empreintes salées, clés publiques (`D/TV-DEMANDE-APPAREIL.md`).
- **Reprise** : aucune file, envoi unique. L'état (activée ou non) est relu par `GET /api/activation` toutes les 4 s, écran ouvert et LAN seulement (`S/ActivateTvActivity.kt:61-77`).
- **Discrétion** : aucune notification ; la TV ouvre son écran d'activation à la réception (`R/OwnerBtHost.kt:36`) ; textes d'erreur sans clé. Aucun `Log.*` du téléphone ne cite clé, licence, PIN ou jeton ; seules deux mentions « never log tokens or codes » (`S/OrdersRuntime.kt:74`, `S/LotsRuntime.kt:172`).
- **Notification d'activation au serveur** : seul `wallet/sync` (TV directe, M4) la porte. Le client TV de `POST /api/v1/activations/report` n'existe pas ; l'avis scellé via le téléphone (W23B) est CONÇU.
- **Variante propriétaire** : console (`OL/ConsoleActivity.kt`, signe `ik`) émet la clé et l'envoie par le même canal. Docs : `D/TV-ACTIVATION-CLE-USB.md`, `D/ACTIVATION-TOOLS.md`, `D/TV-DEMANDE-APPAREIL.md`, `D/coordination/ADDENDUM-W6-PREUVE-TV-LIEN-CLE-2026-10-02.md`.
- **État** : Bluetooth LIVRÉ (« voie qui marche déjà », `D/HANDOFF.md:82`) ; Wi-Fi FUSIONNÉ (« rien essayé sur une vraie TV », `D/TV-ACTIVATION-CLE-USB.md:91`).

### M13. Mises à jour APK de la TV — piste 5
- **Voie A, automatique** : M3, `ServerLink.checkUpdate` → `GET /api/v1/updates/tv/latest` puis `GET /dl/tv/<apk>` (`B/updates/ReleaseService.java:260`). Réseau propre, sinon passerelle M1. Soumis à : consentement, appareil non bloqué, `canInstall` (faux sur la TV de référence CVTE, installation à confirmer à l'écran, `D/HANDOFF.md:227`), pas pendant une lecture sauf action de l'utilisateur.
- **Voie B, téléphone → TV** : `UpdatePanel` (`S/TvHub.kt:198-286`) : l'utilisateur choisit un APK dans le stockage du téléphone, envoi HTTP Wi-Fi puis `installApks` ; même signature exigée par Android, confirmation à l'écran TV. Le téléphone ne va pas chercher l'APK TV sur le serveur ; la TV doit être déverrouillée (API).
- **Voie C** : clé USB `Download/` ; aria2 « Installer cette application » (`R/LibraryScreen.kt:177`).
- **Clarification** : `D/DOWNLOADS.md`, `R/TvDownloads.kt` et `R/DownloadService` (TV) sont le moteur aria2 des téléchargements utilisateur (réseau propre de la TV, ni `Routes` ni passerelle) ; ce n'est pas le canal de mise à jour. `S/DownloadService.kt` copie un fichier de la TV vers le téléphone.
- **Phone** : se met à jour seul, notification « Mise à jour prête — Installer » (`S/PhoneUpdater.kt:134-143`, canal « Mises à jour », IMPORTANCE_DEFAULT).

### M14. Envoi de fichiers téléphone → TV — piste 7
- **File** : `S/TransferQueue.kt` (modèle `C/tv/TransferQueue.kt:126`). Persistante (`filesDir/transfer-queue.json`, `:78`), ancrage durable de la source (`S/SourceAnchoring.kt`, `C/tv/SourceAnchor.kt` : `PERSISTED`, `MEDIASTORE`, `CACHE`, `VOLATILE`, « à repartager »), déduplication par contenu avant envoi (`:463-534`), attente de la TV jusqu'à 60 s (`:607`), `WAIT_FOR_TV` borné (`:410`), reprise par octet confirmé par la TV. Une seule copie à la fois (`UploadService.slot`).
- **Voies** (`S/TransferQueue.kt:292-345`) : Wi-Fi LAN HTTP (`UploadService`, transfert rapide `/api/transfer/*` en blocs hachés) ; Wi-Fi Direct automatique quand seul Bluetooth relie (`C/link/BulkRoute.kt:91-179`, `S/AutoWifiDirect.kt`) ; Bluetooth CBT1 (`S/BtUploadService.kt`). `BluetoothLane`, `WifiDirectLane`, `UsbLane` : non branchées (`C/xfer/Lane.kt:88`, `D/TRANSFER.md:4,7`).
- **Sécurité** : HTTP en clair + PIN/jeton ; Wi-Fi Direct en WPA2 avec phrase fraîche de 16 caractères transmise par le lien Bluetooth appairé et jamais écrite (`D/BT-PLUG-AND-PLAY.md:83`). Aucun reçu signé : la TV vérifie SHA-256 de chaque bloc et du tout.
- **Discrétion** : notifications avec nom de fichier (voir §4).
- **État** : FUSIONNÉ, correctifs de terrain R-19 à R-22 « NON MESURÉ sur appareil » (`D/HANDOFF.md:18-48`).

### M15. Télécommande — piste 8
- **Wi-Fi d'abord** (HTTP keep-alive, `sid` et `seq` pour l'idempotence), puis secours Bluetooth CBTR (service `…0001`, `"CBTR"+code`) (`S/RemoteController.kt:159-189`), HID clavier désactivé par défaut (`S/BtHidRemote.kt`), relais vers le service fabricant CVTE ws://127.0.0.1:8125 par la TV (`D/REMOTE.md`). Pas d'usage du tunnel API Bluetooth pour la télécommande (CBTR indépendant, `BtConnectLock` partagé).
- **Sécurité** : PIN ou jeton sur HTTP clair ; `"CBTR"+code` par RFCOMM appairé.
- **Reprise** : `RemoteSession` renvoie ce qui n'a pas eu de réponse ; un appui de plus de 3 s n'est pas rejoué.
- **Discrétion** : `RemoteService` (canal « Télécommande TV », LOW) ; notification persistante avec boutons.
- **Docs** : `D/REMOTE.md`, `D/REMOTE-VENDOR-CVTE.md`.

### M16. Liaison de confiance (HELLO, jeton, `PinBook`) — piste 12
- **Fichiers** :
  - `S/TvLink.kt:99-312` (`TvLinkManager`), `C/trust/LinkDriver.kt:64-185` (une étape = observer, parler à la TV, `step()` ligne 152, jeton renouvelé à mi-vie ligne 184, `credential()` ligne 95), `C/trust/PhoneLink.kt:23-31,76-80` (`SavedTv.lastIps`, plan de route avec `tunnelBase`), `C/trust/HelloHandler.kt:56`.
  - TV : `C/trust/TrustRegistry.kt` (jeton aléatoire 256 bits, haché SHA-256, 12 h : ligne 51 ; `MAX_PHONES = 8` : ligne 208).
  - Téléphone : `S/PinStore.kt:46`, `C/trust/PinBook.kt:47` (un enregistrement par TV, indépendant de l'IP et de l'écran).
- **Transport** : HELLO `CBTH` sur RFCOMM `…0001` appairé (le pair est identifié par l'adresse du socket) ; ensuite HTTP avec `X-CB-Token`. Routes : LAN, Wi-Fi Direct, Bluetooth seul, puis tunnel API (`C/tv/BtProtocol.kt:437-467`).
- **Sécurité** : le jeton n'ouvre ni `/api/ssh*`, ni `/api/apk/install`, ni `/api/update/install`, ni `/api/activation/install` (`C/trust/TrustRegistry.kt:259-262`). Aucun BLE ni GATT dans le code (grep vide) ; seulement RFCOMM et le profil HID.
- **Reprise** : états `LinkMachine`, courbes 1,5 s → 60 s au premier plan et 1 → 15 min en arrière-plan, job de 15 min (`LinkJobService`), reprise après réinstallation du téléphone (`S/TvLink.kt:164-196`). Une TV qui a dit « je ne vous connais pas » n'est plus sollicitée automatiquement.
- **Doublon d'identité** : `installId` aléatoire de la TV dans le HELLO (TV réinitialisée distinguée d'un téléphone retiré).
- **État** : FUSIONNÉ. Docs : `D/BT-PLUG-AND-PLAY.md` ; W7 (clé par téléphone, CBSY/CBSX) CONÇU.

### M17. Relais mineurs
- **Agent bibliothèque** : noms de fichiers de la TV, lus par le téléphone, partent vers `POST /api/v1/library/suggest` (jeton d'appareil) seulement après un consentement distinct (`C/library/agent/NamingModel.kt:99-113`) ; serveur « sans clé, aucun appel externe » (`D/HANDOFF.md:241`).
- **`GET /api/learn/events`** : route TV « pour un futur téléversement » sans consommateur (`C/learn/LearnApi.kt:20,58`).
- **Contenus libres** : le téléphone télécharge l'archive CC BY-SA depuis le serveur et la TV exporte la sienne sur USB ; aucun transit TV → téléphone (`S/FreeContentScreen.kt:97`, `R/FreeContentExport.kt`).

### M18. CONÇU seulement (aucun code)
- Coursier scellé de télémétrie (TV → téléphone → serveur, lots chiffrés toutes les 4 h, `/api/v1/events/relay`, `SealedOutbox`, `CourierQueue`) : `D/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` §0 point 2. `SealedOutbox`, `CourierQueue`, `events/relay` : aucune occurrence dans `android/`, `backend/`, `server-play/`.
- Avis d'activation `actnotice` et reçu signé, enregistrement via le téléphone, relais discret par l'historique : `D/coordination/DESIGN-W23B-NOTIFICATION-ACTIVATION-LICENCE-PORTEFEUILLE-2026-10-04.md` (règles du propriétaire, lignes 6-12).
- Plug-and-play W7 (`TvBeacon`, canal de synchronisation `…0006`, `CBSX` chiffré de bout en bout) : `D/coordination/DESIGN-W7-PLUG-AND-PLAY-SYNC.md`.
- Cohérence W19 (`/api/sync/state`, enveloppe de raison) : `D/coordination/DESIGN-W19-SYMBIOSE-PHONE-TV-2026-10-03.md`.
- WireGuard et `assist-broker` : `D/REMOTE-MANAGEMENT.md`.
- Bons hors ligne poussés par le téléphone, `/api/wallet` local : W22.

## 3. Choix de réseau, `BoundRoute`, données mobiles — piste 13

- `C/net/BoundRoute.kt:1-41` : lie seulement les sockets vers le préfixe du groupe Wi-Fi Direct à son réseau (`Network.openConnection` / `bindSocket`). Utilisé uniquement par la voie automatique de la file, en Android 10-12 avec `WifiNetworkSpecifier` (`S/AutoWifiDirect.kt:461-481,483-491,499-513`). Android 13+ utilise `WifiP2pManager.connect` sans lier (`:443-454`). Le reste de l'app garde son Internet : c'est le but déclaré (audit I-3 de R-14).
- Décision de voie : `C/link/BulkRoute.kt:153-179` (jamais remplacer un LAN qui répond ; moins de 5 Mio = Bluetooth ; 3 échecs en 10 min = Bluetooth 10 min ; sur 10-12, jamais quitter un Wi-Fi sans deux sondes LAN négatives espacées de 3 s ; au plus 2 changements de voie par fichier).
- Aucune règle « données mobiles » dans le code d'envoi. Les synchronisations de lots demandent un réseau non facturé par défaut (`JobInfo.NETWORK_TYPE_UNMETERED`, `S/LotsRuntime.kt:135-140,293-300`). M1 ne regarde pas la facturation (I-5).
- Les chemins manuels `S/BtUploadService.kt:155-178` (`bindProcessToNetwork`, lignes 164 et 176) et `S/WifiDirectScreen.kt:46,59` lient TOUT le processus du téléphone au réseau du groupe (sans Internet) pendant la jonction (voir I-6).

## 4. Notifications et journaux — piste 14

**Notifications émises par le téléphone** (canal, importance, texte) :

| Opération | Canal / importance | Texte, visibilité |
|---|---|---|
| M1 partage d'Internet | « Internet partagé avec la TV », LOW, en cours | « Partage d'Internet avec la TV… » puis « La TV utilise l'Internet du téléphone », bouton Arrêter (`S/BtGatewayService.kt:126-135`) |
| M2 passerelle | « Passerelle Bluetooth », LOW, en cours | « Passerelle Bluetooth vers <nom TV> : 127.0.0.1 », bouton Arrêter (`S/BtSshGateway.kt:125-132`) |
| Envoi Wi-Fi | « Envoi vers la TV », LOW | texte d'avancement + pourcentage (`S/UploadService.kt:378-385`) |
| Envoi Bluetooth | « Envoi Bluetooth », LOW | « Envoi Bluetooth de <nom du fichier> · % » (`S/BtUploadService.kt:56,189-198`) |
| File d'envoi | « File d'attente des envois », LOW | « CastBridge : file d'envoi vers la TV », « <nom du fichier> » (`S/TransferQueueService.kt:80-87`) |
| Échec ou refus | « Envois refusés par la TV », DEFAULT | « CastBridge : « <nom du fichier> » n'est pas parti » ; « N fichiers à repartager » (`S/CopyReport.kt:70-103`) |
| Télécommande | « Télécommande TV », LOW | boutons de volume (`S/RemoteService.kt:100`) |
| Téléchargement TV → téléphone | « Téléchargements depuis la TV », LOW | « Téléchargement de <nom> » (`S/DownloadService.kt:55,176`) |
| Mise à jour | « Mises à jour », DEFAULT | « Mise à jour prête — Installer » (`S/PhoneUpdater.kt:134-143`) |
| Rapports parentaux | « Rapports du contrôle parental », DEFAULT, écran verrouillé PRIVÉ | texte masqué (`S/ParentalInbox.kt:95-110`) |
| Suggestions, diffusion | « Suggestions de rangement » (LOW), « Diffusion » (LOW) | — |
| **Aucune notification** | ordres (M7), lots (M8), activation (M12), portefeuille, licences | volontairement silencieux ou écran seulement |

**TV** : « CastBridge TV actif » (IMPORTANCE_MIN, `R/TvService.kt:190`) ; « Réceptions en cours » (LOW, « Réception : <titre> », `:159,771-790`) ; « Demandes du téléphone » (HIGH avec intention plein écran, utilisée pour ouvrir un écran depuis l'arrière-plan, `:817-826`) ; « Téléchargements » (LOW, `R/TvDownloads.kt:149-181`) ; l'app « CastBridge Dev » a sa propre notification MIN. Le service ouvre l'écran d'activation à la réception d'une clé (`R/OwnerBtHost.kt:36`).

**Journaux sensibles côté téléphone** (44 appels `Log.*` dans `sender`) :
- Noms de fichiers : `S/UploadService.kt:93`, `S/TransferQueue.kt:505`, `S/player/CastSession.kt:86,177`.
- Noms de TV : `S/TvLink.kt:181,187` (nom Bluetooth + code de refus), `S/TvDiscovery.kt:151` (nom mDNS).
- PIN en extra d'`Intent` de service interne : `S/BtGatewayService.kt:150-151`, `S/BtUploadService.kt:254`.
- Aucun `Log.*` n'imprime clé d'activation, licence, jeton ou PIN. `CopyJournal` (préférences) garde noms de fichiers et causes techniques, sans chemin ni PIN (`S/CopyReport.kt:12-15`).

**Côté TV** : `R/BtTunnelBridge.kt:89` écrit « link from <adresse BT> » ; `C/tunnel/TcpTunnel.kt:99-240` écrit l'adresse du pair ; `C/gateway/BtGateway.kt:268` écrit le nom BT du téléphone. Journal du tunnel sans clé, jeton ni code ; une ligne INFO par requête refusée « jamais PIN, jeton, corps » (`R/TvService.kt:359-360`).

## 5. Tableau récapitulatif

| # | Mécanisme | Direction | Transport | Sécurité | Reprise | Discrétion | État |
|---|---|---|---|---|---|---|---|
| M1 | Internet partagé | TV → tél → Internet | RFCOMM `…0002` + SOCKS5 `127.0.0.1:1080` | appairage + HELLO PIN/NO_PIN ; TLS TV↔serveur de bout en bout ; SOCKS TV non authentifié | reconnexion 1→15 s tant que service ; pas de persistance | notification permanente ; manuel | FUSIONNÉ |
| M2 | Passerelle API/SSH | Mac, Termux, app → tél `127.0.0.1` → TV | RFCOMM `…0002/3/4` | TV vérifie PIN, jeton, clé SSH ; HTTP clair vu du téléphone | LinkPool 3 essais, PING 15 s | notification + barre ; bascule auto si onglet TV affiché | FUSIONNÉ (partiel sur matériel) |
| M3 | Heartbeat, télémétrie, MAJ, quiz | TV → serveur (direct, repli M1) | HTTPS | jeton d'appareil, Ed25519 + SHA-256 sur APK, consentement | `.part`, file 2 Mo, UUID | silencieux ; sondes vers Google | FUSIONNÉ (serveur LIVRÉ) |
| M4 | Portefeuille et licences | TV → serveur (direct, repli M1) | HTTPS | jeton + preuve de clé d'installation | idempotence, cache signé | UI TV seulement | FUSIONNÉ (serveur LIVRÉ) |
| M5 | Quiz en ligne | TV ↔ `castbridge-play` ; téléphones via TV | HTTPS SSE+POST ; LAN `/quiz` | ticket `cbp1` + activation + preuve | `resume` | tuile TV | TV FUSIONNÉE, service non public |
| M6 | Assistance à distance | TV → serveur → experts | SSH inverse, direct ou SOCKS M1 | clé TV, empreinte hôte, experts signés, conditions | 5 s→10 min | silencieux, journal local | FUSIONNÉ, serveur éteint |
| M7 | Ordres différés | serveur → tél → TV | HTTPS puis RFCOMM CBTO, trames 16-21 | `cbx1` signé, liste fermée ; accusés non signés | file persistante, reprise par bloc | invisible (aucune UI) | INERTE |
| M8 | Lots | serveur → tél → TV (+ TV direct Langues) | HTTPS ; HTTP LAN ou CBT1 | catalogue signé revérifié par la TV | `DeliveryQueue`, offset TV | écran seulement | FUSIONNÉ |
| M9 | Packs quiz | serveur → tél → TV | HTTPS ; HTTP LAN | signature serveur revérifiée | re-lancé à l'ouverture | message d'écran | FUSIONNÉ |
| M10 | Locations scellées | propriétaire → tél → TV | fichiers ; HTTP LAN | scellé, vérifié par la TV | offset | écran | FUSIONNÉ |
| M11 | Signalements, rapports parentaux | TV → tél (→ serveur) | HTTP LAN ; CBTP | `dedupeKey` ; signature TV | ack après stockage | parental : notif privée | FUSIONNÉ |
| M12 | Activation, demande d'appareil | propriétaire → tél → TV ; TV → tél → propriétaire | RFCOMM `…0005` ; HTTP LAN `/api/activation/install` | signature + code d'appareil ; PIN, plafonds | aucun | écran TV ; aucune notif téléphone | BT LIVRÉ ; Wi-Fi FUSIONNÉ |
| M13 | MAJ APK TV | serveur → TV ; tél → TV | M3 ; HTTP LAN | Ed25519 ; même signature APK | `.part` ; non | confirmation à l'écran | FUSIONNÉ |
| M14 | Envoi de fichiers | tél → TV | HTTP LAN / Wi-Fi Direct / CBT1 | PIN ou jeton ; SHA-256 par bloc | file persistante, offset TV | notifications avec noms | FUSIONNÉ |
| M15 | Télécommande | tél → TV | HTTP LAN ; CBTR ; HID | PIN ou jeton | `sid`/`seq` | notification persistante | FUSIONNÉ |
| M16 | Liaison de confiance | tél ↔ TV | RFCOMM `…0001` CBTH | appairage + approbation TV ; jeton 12 h | courbes + job 15 min | écran « Ajouter un téléphone » | FUSIONNÉ |
| M17 | Agent bibliothèque et divers | TV → tél → serveur | HTTPS | consentement distinct | — | écrans | FUSIONNÉ |
| M18 | Coursier scellé, avis d'activation, CBSY, WireGuard, bons | divers | — | — | — | — | CONÇU |

## 6. Incohérences relevées

- **I-1 UUID RFCOMM `…0002` utilisé deux fois.** SSH (`C/tv/BtProtocol.kt:81`) et passerelle Internet (`C/gateway/BtGateway.kt:46`). La TV démarre les deux : la passerelle à chaque démarrage (`R/TvService.kt:545-546`), le SSH quand il est activé (`R/SshControl.kt:42-45`). Même constat dans `D/coordination/DESIGN-W7-PLUG-AND-PLAY-SYNC.md:27`, non corrigé. `D/ADMIN.md:403` place la passerelle sur `…0001`. Risque non vérifié sur matériel : un téléphone qui demande `…0002` peut tomber sur le mauvais service.
- **I-2 Ordres : documents et code divergent.** `D/ORDRES.md:108` et `C/policy/OrderFrames.kt:7` disent « service `…0004` » (c'est l'API v2, `C/tv/BtProtocol.kt:85`) ; le code compose `OwnerFrames.SERVICE_UUID` = `…0005` (`S/OrdersRuntime.kt:69`). `D/ORDRES.md` annonce qu'un ancien appareil « ignore » le type inconnu ; le code répond `RESULT(0)` (`C/owner/OwnerChannel.kt:38-39`, `C/owner/OwnerFrames.kt:22`). Plus l'inertie décrite en M7 (trois ruptures : TV, `learnTvCode`, écho CBTO).
- **I-3 Deux familles de chemins TV vers serveur.** Avec `Routes` : `ServerLink`, portefeuille, jeu, tunnel. Sans `Routes` ni proxy : `LanguesHub.fetcher` (`R/LanguesHub.kt:57-60`) et aria2. Quatre définitions de « la TV a Internet » : `WalletHub.networkUp` (`R/wallet/WalletHub.kt:121`), `PlayHub.hasInternet` (passerelle ou INTERNET non validé, `R/quiz/PlayHub.kt:63-67`), `LanguesHub.hasInternet` (INTERNET + VALIDATED, sans la passerelle, `:45-47`), `TunnelConnectivity.choose` (`C/tunnel/TunnelBackoff.kt:66`).
- **I-4 Quatre voies pour les données Quiz.** (a) `ServerLink.syncQuiz` TV directe, (b) `refillQuizPacks` TV directe (`C/connect/ServerLink.kt:340-373`), (c) `QuizPackRelay` par le téléphone (`S/QuizScreen.kt:78-91`), (d) lots `quiz` poussés (M8). `D/LOTS.md:155` dit que (a) et (b) sont un héritage à remplacer par des lots poussés.
- **I-5 Partage d'Internet sans garde de coût.** Pas de test réseau facturé, pas de plafond d'octets, pas de démarrage ni de persistance automatiques (`S/BtGatewayService.kt:116-124`, `S/TvHub.kt:289-305`), alors que M3, M4, M5, M6 en dépendent. Les mises à jour d'APK (41 Mo) passent par ce tuyau (100-300 ko/s).
- **I-6 Deux méthodes de liaison au Wi-Fi Direct.** Per-socket `BoundRoute` (file automatique) contre `bindProcessToNetwork` (écran manuel Bluetooth `S/BtUploadService.kt:164,176`, écran Wi-Fi Direct `S/WifiDirectScreen.kt:46,59`). La seconde coupe l'Internet de l'app (M1, jobs d'ordres et de lots, télémétrie) pendant la jonction.
- **I-7 Télémétrie : conception contre code.** W21 décide « jamais par la passerelle Bluetooth » et « remise scellée au téléphone toutes les 4 h » ; le code envoie toutes les 15 min via `Routes`, passerelle comprise (`C/connect/ServerLink.kt:217-229`), aucun coursier. `gateway_session` est compté des deux côtés (`S/BtGatewayService.kt:74`, `R/BtGatewayHost.kt:87`).
- **I-8 Assistance à distance : deux documents contradictoires.** `D/REMOTE-MANAGEMENT.md` (WireGuard, consentement par session de 30 min, « jamais de porte dérobée permanente », phone en passerelle) contre `D/REMOTE-TUNNEL*.md` (SSH inverse, toujours actif, sans consentement par session). WireGuard reste ouvert sur le serveur (UDP 51821), sans client. Port `2223` utilisé à la fois par l'app « CastBridge Dev » (liaison par défaut toutes interfaces, `devbridge/.../DevService.kt:21`) et par le sshd du tunnel en boucle locale (`R/TunnelHub.kt:50`) : conflit probable sur la TV de développement. Trois serveurs SSH distincts sur la TV (2222 utilisateur, 2223 tunnel, 2223 dev).
- **I-9 Plusieurs mécanismes d'authentification pour le même pair.** PIN `X-CB-Pin`, jeton `X-CB-Token` (12 h), `------` en Bluetooth, clé `bt-gateway` partagée contre `bt:<adresse>` par appareil (API), exclusions de `tokenMayCall`, activation Wi-Fi par PIN seul contre Bluetooth par appairage seul, ticket + activation + preuve (jeu), jeton d'appareil + preuve (portefeuille), clé SSH + liste d'experts (tunnel).
- **I-10 Portefeuille : la conception promet plus que le code.** `/api/wallet` local lisible par les téléphones, bon poussé par le téléphone : absents ; `VoucherKeys` vide (`R/wallet/WalletHub.kt:96`).
- **I-11 Notification d'activation : promesse du propriétaire non tenue.** « Les codes transiteront par le téléphone » (`D/coordination/DESIGN-W23B-…:6`) : aucun client TV d'avis ni de `activations/report` ; seul `wallet/sync` TV directe porte l'activation.
- **I-12 Révocations : aucun chemin vers la TV.** `/api/v1/revocations` est publié (`CASTBRIDGE_LICENSES_PUBLIC_ROUTES=true`) mais seul `castbridge-play` le lit ; le receiver n'a aucun code de révocation (grep « revoc|revok » vide hors confiance téléphone et statut du jeu) ; l'action d'ordre `revocation.add` est inerte (M7).
- **I-13 Asymétrie des bascules.** La bascule automatique Bluetooth sert seulement l'API de la TV vers le téléphone, et seulement avec l'onglet TV affiché (`S/BtGatewayCard.kt:93-127`) ; le partage d'Internet vers la TV est manuel. La TV ne peut pas demander le partage.
- **I-14 Discrétion hétérogène.** Les notifications d'envoi et d'échec affichent des noms de fichiers sans visibilité privée, alors que `ParentalInbox` passe en privé sur l'écran verrouillé. Les ordres sont conçus invisibles mais le journal de transparence exigé par `D/ORDRES.md:136` n'existe pas ; les conditions d'usage (`C/tunnel/TunnelTerms.kt`) ne mentionnent que le tunnel.
- **I-15 Commentaires périmés.** `C/owner/OwnerFrames.kt:13` dit « after the API service 0003 » (le suivant est `0004`) ; `R/TvNetDiag.kt:19` dit « jamais périodique » alors que `R/TvService.kt:576-583` sonde périodiquement dès l'acceptation des conditions.
- **I-16 Transfert de fichiers : trois voies jamais branchées** (`BluetoothLane`, `WifiDirectLane`, `UsbLane`) alors que `D/TRANSFER.md` les décrit comme des voies.

## 7. Ce que la TV ne peut PAS obtenir quand elle n'a pas Internet et que le téléphone en a

Cas A = le téléphone a activé « Partager l'Internet du téléphone » (M1) ; cas B = le téléphone a Internet mais ne partage pas (réglage par défaut).

| Besoin | Cas A (partage actif) | Cas B (pas de partage) |
|---|---|---|
| Mise à jour APK de la TV | oui, `ServerLink` (si `canInstall`, hors lecture) | non ; seule voie : APK choisi à la main (Wi-Fi, TV déverrouillée) ou clé USB ; le téléphone ne va pas chercher l'APK TV sur le serveur |
| Clé d'activation (licence) | ne vient pas d'Internet (offline) | idem ; le téléphone relaie la clé fournie par le propriétaire |
| Portefeuille, soldes, ouverture de licence/poste au serveur | oui (`wallet/sync`) | non ; bons hors ligne via téléphone et avis d'activation relayé : CONÇU |
| Questions et packs du Quiz | oui | oui par `QuizPackRelay` (écran Quiz du téléphone ouvert, LAN) et par lots poussés |
| Lots Apprendre, Quiz, Langues | la TV ne télécharge jamais Apprendre ni Quiz ; Langues : bouton TV caché (pas de proxy, I-3) | oui par push du téléphone (LAN ou CBT1) |
| Quiz en ligne | impossible en pratique (service non public) | non (règle : Internet de la TV obligatoire) |
| Télémétrie, plantages | oui | non : file locale de 2 Mo (les plus anciens partent) ; coursier CONÇU |
| Signalements de contenu | oui | oui via le hand-off du téléphone (LAN + PIN) |
| Ordres signés du serveur | non (inerte) | non (inerte) |
| Révocations de clés ou de postes | non (aucun code TV) | non |
| Heure de confiance | non : `TvClock` n'accepte aucune heure externe ; `serverTime` du heartbeat ignoré (`C/device/DeviceClient.kt:39,45`) ; le téléphone n'en fournit pas | non |
| Assistance à distance | techniquement possible mais serveur éteint | non |
| Téléchargements aria2 (HTTP, torrent) | non (réseau propre seulement) | non ; le téléphone peut seulement lui envoyer un lien (`ShareToTvActivity`) |
| TV verrouillée (non activée) | rien : pas de passerelle, pas de `ServerLink` | rien ; seuls l'envoi de clé (BT ou Wi-Fi) et la clé USB fonctionnent |
| Autres applications de la TV | non : le SOCKS ne sert que le code de CastBridge-TV | non |

Conséquence probable non vérifiée : une horloge TV fausse fait échouer la validation TLS vers le serveur même par M1 (`D/coordination/DESIGN-W20-AMENDEMENT-…` impose déjà « Vérifiez l'heure de la TV » avant de jouer).

## 8. Points d'accroche existants et réserves

- Points d'accroche déjà en place : `Routes` (tout appel TV → serveur) ; routes HTTP de la TV pour le contenu (`/api/lots/*`, `/api/quiz/packs/*`, `/api/rental/*`, `/api/content/reports`, `R/TvService.kt:333-342`) ; canal propriétaire RFCOMM `…0005` avec trames extensibles (types 11 à 15 et 22 à 255 libres) ; `LinkDriver` comme source de session et de jeton ; `JobScheduler` côté téléphone (ordres, lots, parental, liaison).
- Réserves : aucune mesure sur matériel dans cette lecture ; les affirmations « inerte », « non câblé », « sans appelant » reposent sur `grep` du dépôt (code `integration/agents` uniquement, pas les branches d'agents) ; le contenu réel de l'override de production n'a pas été relevé sur l'hôte (seulement la documentation de déploiement) ; `D/LEARN-REVIEW.md` et les fiches de contenu ne concernent pas ces mécanismes et n'ont pas été lus.
