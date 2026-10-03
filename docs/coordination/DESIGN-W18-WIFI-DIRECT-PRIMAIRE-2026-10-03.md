# Conception W18 — Wi-Fi Direct primaire : la TV est le point d'accès, le téléphone s'y branche tout seul dès qu'il a le PIN

> Document de conception (Fable, architecte, 2026-10-03). **Aucun code n'est modifié par ce document.** L'exécution se fait par les cahiers `docs/agent-briefs/sonnet-w18-NN-*.md` (index : `SONNET-WAVE18-INDEX.md`), sur ordre explicite du coordinateur, **après** le test terrain du § 7. Branche de référence : `integration/agents` (HEAD `79516fb0`) et la branche non fusionnée `claude/auto-wifi-direct` (`82e7dff3`, `61fc8f89` : R-14). Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = tests cœur, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OL/` = `android/ownerlib/…`. Les fichiers cités ont été lus le 2026-10-03 ; les faits Android sont ceux de la documentation publique des API 26-35 ; **les faits sur la puce Wi-Fi USB de la TV sont inconnus** (§ 7).
>
> **Décision du propriétaire (2026-10-03, verbatim)** : « dans 85% des cas, il n'aura pas de point d'accès. le cast et la copie par Wifi s'avèrera donc fondamental et ceci doit être transparent depuis l'app phone une fois que celle-ci aura le PIN. Cela doit être totalement plug and play ». Et : « Je ne veux pas me reposer sur le hotspot du téléphone. Uniquement sur le hotspot de la TV par Wi-Fi Direct sans achat de nouveau matériel ».
>
> **Exigence ajoutée (2026-10-03)** : « le Wi-Fi Direct doit pouvoir être activé depuis l'app phone quand le lien Bluetooth et le PIN sont établis entre le phone et la TV » ⇒ un **bouton « Wi-Fi Direct »** sur la fiche de la TV (visible dès que le lien Bluetooth est établi **et** que le PIN/jeton de cette TV est valide) demande à la TV de créer son groupe par Bluetooth, le rejoint, affiche l'état (vert/orange/rouge) et le débit mesuré ; il est **aussi** le test terrain de 10 minutes (§ 7). Il est réalisé **tout de suite** par un exécutant (branche `claude/wd-manual-button`) : W18 le prend comme **brique existante** ; l'automatique (`BulkRoute`/`WdPolicy`) reste l'usage normal, le bouton est le contournement manuel et le diagnostic.
>
> **Lecture appliquée** : dans 85 % des foyers il n'y a ni box ni routeur ; le téléphone et la TV n'ont que le Bluetooth et le Wi-Fi de leurs puces. Le Wi-Fi Direct **n'est plus un repli** du Bluetooth réservé aux gros envois (R-14) : c'est **la voie principale de données et de cast** (copie, copie-et-lecture, déplacement, file, « Lire en direct », serveur média du téléphone `:8089`, télécommande, Quiz avec les téléphones joueurs, synchro lots/Apprendre, synchro parentale/confiance). **La TV est le point d'accès** (propriétaire du groupe P2P), jamais le téléphone. Premier contact = taper le PIN affiché sur la TV ; sessions suivantes = zéro geste. Hotspot du téléphone, routeur de voyage, adaptateur USB Wi-Fi/Ethernet : **écartés par décision du propriétaire**, non étudiés ici.

## 0. En vingt lignes

1. **Amorçage sans point d'accès** : le téléphone qui n'a que le PIN ne peut pas rejoindre un groupe Wi-Fi Direct de la TV par le PIN seul sans affaiblir la sécurité (un secret de 6 chiffres dans une poignée de main WPA2 se casse hors ligne en secondes ; le WPS-PIN est à 8 chiffres, cassé, et impose une boîte « Accepter » sur la TV). Le premier contact passe donc par **un canal qui prouve la présence physique et chiffre** : l'appairage Bluetooth existant (« Ajouter ma TV », comparaison de code Android) **ou** le QR affiché par la TV. Le PIN tapé sur le téléphone **remplace le « Autoriser » à la télécommande** (D-W18-4) : la TV accorde la confiance à un téléphone appairé qui présente le bon PIN (compté par `PinGuard`, par adresse Bluetooth). La boîte de comparaison de code Android, elle, ne peut pas être supprimée par une application : c'est le seul geste « système » qui reste, et il est le même que pour un casque Bluetooth.
2. **Secret de groupe par TV, persistant** (D-W18-2) : la TV tire une fois un nom `DIRECT-CB-<6>` et un mot de passe de 16 caractères (≈ 93 bits, `WifiDirect.groupPassphrase`, existant), les garde dans son stockage chiffré, et les remet **une fois** à chaque téléphone de confiance par le lien RFCOMM appairé (CBTN/HELLO, existant) ou par le QR. Le téléphone les range dans sa fiche de TV (`PinBook`, clé `wd.<tvId>`). Reconnexion = `WifiP2pManager.connect(nom + mot de passe)` : **aucune boîte** sur Android 13+, **une seule fois pour toujours** sur Android 10-12 (l'approbation du `WifiNetworkSpecifier` est mémorisée par Android pour un même SSID/BSSID : c'est le gain direct des identifiants persistants), aucun Bluetooth nécessaire.
3. **La TV tient son groupe allumé** tant que CastBridge-TV est à l'écran (D-W18-3) : dans 85 % des foyers sa radio Wi-Fi ne sert à rien d'autre. Le téléphone se branche à l'ouverture de l'app, à une action (copie, cast, télécommande) ou à l'arrivée d'une diffusion Bluetooth ; **jamais** de balayage de fond (batterie). Budget : **4-8 s** d'un téléphone déjà fourni en identifiants (association WPA2 + DHCP), **8-17 s** si le groupe doit être créé à la demande par Bluetooth (chemin R-14, gardé en secours) ; valeurs **à mesurer** (§ 7).
4. **Modèle de routes** : `LAN > WD ≥ BT` pour **tous** les usages (contrôle, synchro, masse, cast, télécommande, Quiz), WD préféré dès qu'aucun LAN commun ne répond ; le Bluetooth reste le plan de contrôle de secours et la voie des petits lots quand le Wi-Fi est éteint.
5. **Variante matérielle recommandée** : groupe P2P autonome, TV propriétaire (192.168.49.1), identifiants persistants (**a + c-persistant**) ; **variante de secours logicielle sur la même puce** : SoftAP de la TV (`startLocalOnlyHotspot`, API 26+) si la puce sait faire point d'accès mais pas propriétaire de groupe P2P ; **dernier recours** : Bluetooth optimisé (§ 9), avec ses limites dites (pas de cast vidéo fluide : 100-300 ko/s).
6. **Fait bloquant** : tout repose sur la puce Wi-Fi USB de la TV (Amlogic T950, module AIC partagé avec la clé USB) : sait-elle être propriétaire de groupe (ou point d'accès), à quel débit, sans tuer son Wi-Fi/Ethernet ni l'E/S de la clé ? **Test terrain de 10 minutes d'abord** (§ 7), avec l'application existante ; les cahiers coûteux (18b, 18c) **ne démarrent pas** sans ce fait.
7. **Effort** : 13 cahiers (≈ 23 agent·jours, ≈ 18 $), dont 8 pendant le gel (cœur pur, harnais, kit de test, docs), 4 après le gel (câblage TV et téléphone), 1 conditionnel (Bluetooth optimisé). 3 audits Opus obligatoires (identifiants, radios TV, radios téléphone).

## 1. Ce qui existe (vérifié) et ce qui en survit

| Brique | Où | Constat | Sort en W18 |
|---|---|---|---|
| Groupe P2P de la TV | `R/WifiDirectGroup.kt` (branche R-14 : nom aléatoire par groupe, mot de passe frais 16 car., `enablePersistentMode(false)`, `removeGroup` avant `createGroup`, `clients()`), `R/TvService.kt` `linkInfo(peer, flags)`, `wdLeaseCheck()` | fonctionne sur CBTN seulement ; mot de passe **jamais persisté** (volontaire en R-14) | **gardé** ; devient persistant (D-W18-2) et « toujours allumé à l'écran » (D-W18-3) ; le bail (`WdGroupLease`) ne supprime plus le groupe par défaut |
| Décision pure téléphone | `C/link/BulkRoute.kt` (`decide`, `WdBackoff`, `BulkLine`), `C/link/WdClient.kt` (automate Requesting → Joining → Probing → Up, 27 tests) | masse ≥ 5 Mo seulement ; LAN jamais remplacé ; `IDLE_RELEASE_MS` 30 s | **gardé comme fondation** ; `BulkRoute` est enveloppé par `WdPolicy` (tous les usages), `WdClient` par `WdSession` (jonction directe sans CBTN quand les identifiants sont connus) |
| Exécutant téléphone | `S/AutoWifiDirect.kt` (CBTN, `WifiP2pManager.connect` 33+, spécificateur 29-32, sonde `/api/hello`, départ, `WD_RELEASE`) | `bindProcessToNetwork` sur 29-32 (**défaut d'audit** : lie tout le processus) | gardé ; les sockets de 29-32 passent par `network.socketFactory`/`openConnection` **par requête** |
| Route de contrôle | `S/TvLink.kt` `canJoinWifiDirect = { false }` (R-14, corrige le piège « 192.168.49.1 sans jonction ») | juste mais trop court : une fois le groupe **monté**, le contrôle doit pouvoir y passer | `LinkPlanner` reçoit `wdUp` : `Direct` seulement si `WdSession.Up` |
| Protocole BT | `C/tv/BtProtocol.kt` (CBTN `WANT_WIFI_DIRECT`, `WD_RELEASE`, `WD_LAN_UNREACHABLE`, `LinkInfo` `wd.cap`, `wd.err`), `C/trust/HelloHandler.kt` | additif, compatible | gardé ; ajout `wd.name`, `wd.pass` **persistants** dans la réponse CBTN **et** HELLO d'un pair de confiance (D-W18-2), `wd.id` |
| Confiance | `C/trust/{PairFlow,PairingSession,TrustRegistry,HelloHandler}.kt`, `docs/BT-PLUG-AND-PLAY.md` | « Ajouter ma TV » = appairage SSP + fenêtre « Autoriser » 2 min | gardé ; **nouveau** : HELLO portant le PIN ⇒ confiance sans fenêtre (`TrustByPin`, D-W18-4) |
| PIN | `R/TvPrefs.kt:11-15` (6 chiffres, créé une fois), `C/tv/Security.kt` `PinGuard` (5 échecs / 60 s par IP), `docs/ADMIN.md` § 2 (en-tête `X-CB-Pin`, jamais en URL) | sémantique à garder | **inchangée** : le PIN prouve la présence devant l'écran ; il n'est jamais une clé Wi-Fi |
| Fiche de TV | `C/trust/PinBook.kt` (R-10 : code par identité de TV, `tvId`, alias, `castbridge_pins.xml` hors sauvegarde), `SavedTv` (`C/trust/PhoneLink.kt:23`) | | **gardé** : reçoit `wd.<tvId>` = `nom\tmot de passe` (aucune nouvelle persistance) |
| Cast « Lire en direct » | `S/player/CastSession.kt:125-165` (`playUrl` vers la TV), `:297-308` `serve()` (`Upnp.localIp()` = **première IPv4 de site, `wlan*` d'abord**), `MediaServer` `:8089`, TV `ReceiverServer` `/api/playurl` (http(s) seulement) | l'adresse du téléphone n'est pas choisie **vers la TV** : sur un groupe P2P (`p2p-wlan0-0`, 192.168.49.x) elle peut être fausse | `LocalAddress.toward(tvIp)` (socket UDP connectée, adresse source) |
| Quiz | `R/QuizHub.kt:169` `joinUrl` = `TvService.localIp()` (première IPv4 de site) | sur un groupe seul : 192.168.49.1, juste ; avec LAN **et** groupe : ambigu | `joinUrls` : une URL par interface, le QR affiche celle de la route du téléphone qui a ouvert la salle |
| Télécommande | `S/RemoteController.kt` (`RemoteTv(host, btAddress)`, routes Bluetooth CBTR/HID) | choisit `host` ou Bluetooth | `host` = base de la route active (`WdSession.Up` ⇒ 192.168.49.1) |
| Signalétique | `C/ux/TvSignal.kt` (VERT marche, ORANGE dégradé, ROUGE ne peut pas, NOIR inactif par choix) | | reprise telle quelle (§ 6.4) |
| Multivoie | `DESIGN-W8` abandonné (une seule puce) | | inchangé : **aucune** agrégation |
| W7 | `DESIGN-W7` § 4.1 (rang 5 WD en isolation, rang 6 QR), § 4.3, § 5.2 ; cahiers w7-09/12/16/17/21 non exécutés | WD classé « isolation détectée » | **amendés** (en-tête « Amendement » de chaque cahier) : WD = voie principale sans LAN, pas seulement en isolation |
| Audit Opus de R-14 (en attente) | contrôle pollué par 192.168.49.1 ; groupe jamais retiré avec une télécommande ouverte ; liaison du processus sur 10-12 | | les trois sont **absorbés** par w18-02 (route), w18-07 (bail : une télécommande ouverte compte comme usage ; groupe persistant), w18-08 (sockets par réseau) |
| **Bouton « Wi-Fi Direct »** (branche `claude/wd-manual-button`, en cours) | fiche de la TV (téléphone) : visible si lien Bluetooth établi **et** PIN/jeton valide ; CBTN `WANT_WIFI_DIRECT` ⇒ jonction (`AutoWifiDirect`/`WdClient` de R-14) ⇒ état vert/orange/rouge + débit mesuré (20 Mo, `discard=1`) + faits (GO supporté ? SSID masqué, 192.168.49.1 joignable ? LAN de la TV conservé ?) | brique **manuelle et diagnostic** ; ne décide rien d'automatique | **gardée telle quelle** ; W18 la nourrit : `WdSession` (w18-02) expose le même état, `WdDiag` (w18-04) les mêmes faits, `WdCredentials` (w18-01) fait que le second appui n'a plus besoin du Bluetooth ; c'est l'outil du test terrain § 7 |

## 2. Le problème d'amorçage sans point d'accès

Situation : téléphone jamais vu de cette TV, aucun appairage Bluetooth, aucun routeur, aucun QR encore montré ; l'usager a sous les yeux le PIN à 6 chiffres de l'écran TV (« code 77•••• »). Il faut (1) **trouver** la TV, (2) **rejoindre** son groupe, (3) **ne laisser entrer personne d'autre**.

```
                 trouver la TV          rejoindre le groupe            confiance
  (a) découverte P2P   ✔ (id, nom)      ✘ (aucun identifiant)          ✘
  (b) WPS PIN          ✔ (pairs P2P)    ~ (8 chiffres, boîte TV)        ✘ (WPS cassé)
  (c) PSK dérivée PIN  ✔                ✔                               ✘ (brute force hors ligne : secondes)
  (d) appairage BT     ✔ (SDP)          ✔ (identifiants par RFCOMM)     ✔ (SSP + PIN)
  (e) NFC/son/BLE      ✘/~/~            ✘                               ✘ (pas sur cette TV)
  (f) QR écran TV      ✔                ✔ (identifiants dans le QR)     ✔ (présence physique)
```

### 2.1 Options, une à une (faits Android 29-35)

**(a) Découverte de service P2P** (`WifiP2pDnsSdServiceInfo.newInstance("castbridge", "_castbridge._tcp", txt)` côté TV + `addLocalService` ; téléphone `setDnsSdResponseListeners` + `addServiceRequest` + `discoverServices`). Faits : disponible depuis l'API 16 ; téléphone : `NEARBY_WIFI_DEVICES` (33+, `neverForLocation`) ou `ACCESS_FINE_LOCATION` (29-32) ; Wi-Fi allumé ; une TV **propriétaire de groupe répond** aux requêtes de service (GAS) : le téléphone voit « CastBridge-TV Salon · id 3f9a… » **sans appairage** en 2-10 s (mesures publiques ; fragile : requêtes perdues, à relancer). Ce que ça donne : la **présence** et l'**identité** (id 8 hex = `InstallKey` W7, nom), donc « Votre TV est à portée », le choix entre deux TV, et le déclencheur de reconnexion. Ce que ça ne donne pas : **aucun identifiant** : on sait qui est là, pas comment entrer. Coût : la TV doit avoir enregistré le service (une ligne, `addLocalService`) ; le TXT ne porte **ni MAC ni mot de passe** (règle W7 § 4.1 rang 3). **Retenu pour trouver, pas pour entrer.**

**(b) WPS PIN** (`WifiP2pConfig.wps.setup = WpsInfo.KEYPAD|DISPLAY`, `wps.pin`). Faits : le PIN WPS a **8 chiffres** (7 + somme de contrôle) ; le PIN de la TV en a **6** : il faudrait soit un second code à l'écran (contre « une seule chose à taper »), soit dériver 8 de 6 (le PIN WPS devient devinable à 10⁶ au lieu de 10⁷). Le protocole WPS-PIN est **cassé** (vérification en deux moitiés : ≈ 11 000 essais ; attaques « pixie dust » hors ligne sur de nombreux micrologiciels). Côté TV, Android affiche une **boîte système d'invitation** (« … souhaite se connecter ») à chaque demande P2P par WPS vers un propriétaire de groupe ; une application non système ne peut pas l'accepter à sa place ⇒ **télécommande obligatoire**, exactement ce qu'on veut éviter. `WpsInfo` est déprécié pour l'infrastructure depuis l'API 28 et toléré en P2P ; des constructeurs le retirent. **Rejeté.**

**(c) Mot de passe de groupe dérivé du PIN** (par exemple `PBKDF2(PIN, sel = id de la TV)` comme passphrase WPA2-PSK). Faits : la poignée de main WPA2 à 4 messages se capture en l'air ; vérifier une candidate coûte une PBKDF2-4096 + quelques HMAC ; **10⁶ candidates** = quelques secondes sur un GPU, quelques minutes sur un portable, **hors ligne** : aucun `PinGuard` ne voit rien. Combiné à un secret de forte entropie tenu par la TV, le mot de passe redevient fort… mais le téléphone doit alors **obtenir ce secret**, ce qui est exactement le problème de départ. **Rejeté** (noté comme tel : aucun cahier ne doit le ressusciter « pour simplifier »).

**(d) L'appairage Bluetooth comme amorçage sûr, une fois** (existant). Parcours « Ajouter ma TV » (`C/trust/PairFlow.kt`, `docs/BT-PLUG-AND-PLAY.md`) : téléphone : (1) « Ajouter ma TV » (2) la TV dans la liste (une seule : prise d'office) ; Android : boîte **« Associer ? code 123456 »** sur le téléphone **et** sur la TV (comparaison numérique SSP, un « OK » de chaque côté : la TV à la télécommande) ; puis, aujourd'hui, la TV demande **« Autoriser ce téléphone ? »** dans sa fenêtre « Ajouter un téléphone » (télécommande, 2 min, « Refuser » présélectionné). Soit **2 touches téléphone + 2 validations télécommande**. Ce que le PIN peut remplacer : **le « Autoriser »** (D-W18-4) : un pair **appairé** (socket RFCOMM sécurisé, `R/BtServer.kt`) qui envoie HELLO avec le PIN exact reçoit la confiance sans fenêtre : le PIN prouve qu'il est devant l'écran, l'appairage prouve que le lien est le sien ; `PinGuard` compte les faux PIN **par adresse Bluetooth** (5 ⇒ 60 s, existant pour CBT1/CBTN). Ce que le PIN **ne peut pas** remplacer : la **comparaison numérique Android** ; une application ne peut ni la confirmer (`setPairingConfirmation` = `BLUETOOTH_PRIVILEGED`) ni la contourner ; c'est la même boîte que pour une enceinte : connue des usagers. Donc **1 touche téléphone (+ choix de la TV) + PIN tapé + 1 « OK » sur chaque appareil**. Les identifiants Wi-Fi Direct voyagent ensuite dans la réponse HELLO/CBTN sur ce lien chiffré (R-14, existant). Trouver la TV sans appairage : `startDiscovery` + `fetchUuidsWithSdp` (`S/TvLink.kt:367-387`, `BLUETOOTH_SCAN neverForLocation` 12+ ; sur Android 10-11 la découverte classique exige la localisation : **repli** = la TV rend visible et l'usager l'associe depuis les réglages Bluetooth, ou QR). **Retenu comme chemin principal.**

**(e) NFC, son, BLE.** NFC : la TV n'en a pas. Son (modulation ultrasonore type « Nearby Audio ») : haut-parleurs TV et bruit de pièce = taux d'échec élevé, décodage à écrire des deux côtés, aucune garantie de confidentialité (tout ce qui s'entend se capte) ⇒ ne transporterait qu'un **indice**, pas un secret. BLE : annonce de présence possible **si** le module de la TV annonce en BLE (inconnu, D-W7-1 : non en v1) ; un canal GATT pourrait porter une **PAKE** (SPAKE2 sur le PIN : le PIN devient une clé forte sans brute force hors ligne, un essai par exécution, `PinGuard` applicable) : c'est la seule construction cryptographiquement propre « PIN seul, sans appairage », mais elle exige un annonceur BLE sur la TV, une bibliothèque PAKE auditée (aucune dans le projet), et le même geste de découverte. **Non en W18** ; noté comme piste si une TV à BLE est mesurée (W7 § 13).

**(f) QR sur l'écran de la TV** (W7 § 4.1 rang 6, `castbridge://tv?id=&bt=&ip=&port=&pub=&sas=`). **Étendu** : `&wd=<nom>&wp=<mot de passe>` (D-W18-5). Le téléphone scanne (caméra, `DeepLinkTv` de w7-17) et rejoint le groupe **sans Bluetooth, sans PIN, sans boîte** (API 33+ ; une seule boîte, mémorisée, sur 10-12). Preuve de présence physique : il faut voir l'écran. Risques : une photo de l'écran vaut le mot de passe ⇒ le QR n'est affiché **que sur demande** (MENU › Connexion › « Montrer le QR », et au premier démarrage sans téléphone de confiance, 10 min, comme la fenêtre W7 § 4.2), jamais sur l'accueil ; « Changer le mot de passe Wi-Fi Direct » re-tire le secret (les téléphones de confiance le réapprennent au prochain HELLO). **Retenu comme chemin zéro saisie** et comme **seul chemin** pour une TV sans Bluetooth ou un téléphone dont l'appairage échoue.

### 2.2 Recommandation (g) : hybride, geste minimal

```
PREMIÈRE FOIS (téléphone inconnu)                                    sessions suivantes : § 3 (zéro geste)
 ┌─────────────────────────────────────────────────────────────────┐
 │ Téléphone : « Ajouter ma TV »                                   │
 │   ├─ Bluetooth OK (85 %+ des téléphones)  ─► chemin (d)         │
 │   │     TV trouvée (SDP) ─► boîte Android « Associer ? » (2 OK) │
 │   │     ─► « Code de la TV : [ _ _ _ _ _ _ ] » ─► HELLO + PIN   │
 │   │     ─► confiance + jeton + identifiants Wi-Fi Direct        │
 │   │     ─► jonction P2P silencieuse ─► « Salon · par Wi-Fi »    │
 │   └─ « Scanner le QR de la TV » ─────────► chemin (f)           │
 │         TV : MENU › Connexion › Montrer le QR                   │
 │         ─► jonction P2P ─► HELLO HTTP (jeton par CBSX W7, ou    │
 │            PIN tapé si la TV est ancienne) ─► « Salon · Wi-Fi » │
 └─────────────────────────────────────────────────────────────────┘
```

Séquence d'écrans et textes (français, un par issue) :

| # | Téléphone (CastBridge) | TV (CastBridge-TV) |
|---|---|---|
| 1 | Accueil sans TV : **« Ajouter ma TV »** (bouton unique) ; dessous, petit : « ou scanner le QR affiché par la TV » | Accueil : « Prêt · code 77•••• » (inchangé) |
| 2 | « Recherche de la TV… » (SDP 6 s) ; plusieurs TV ⇒ liste par nom | — |
| 3 | Boîte Android « Associer avec CastBridge-TV Salon ? 384 112 » **OK** | Boîte Android « Associer avec Galaxy S21+ ? 384 112 » **OK** (télécommande) |
| 4 | **« Tapez le code affiché sur la TV »** `[ 7 7 _ _ _ _ ]` · « Il est en bas de l'écran de la TV. » (erreur : « Code refusé. Il reste 4 essais. » / « Trop d'essais : nouvel essai dans 60 s. ») | bandeau 10 s : « Galaxy S21+ est maintenant votre téléphone de confiance » (plus de fenêtre « Autoriser ») |
| 5 | « Liaison Wi-Fi avec la TV… » (4-8 s) puis carte verte **« Salon connectée · Par Wi-Fi Direct »** ; sur la fiche de la TV, le bouton **« Wi-Fi Direct »** (brique `wd-manual-button`) reste disponible : un appui = demande du groupe par Bluetooth + jonction + état + débit (contournement manuel si l'automatique n'a pas démarré, et diagnostic) | icône Wi-Fi Direct verte : « 1 téléphone » |
| 5′ | Android 10-12, première fois seulement : boîte Android « Se connecter à DIRECT-CB-k7m2qx ? » **Se connecter** (puis plus jamais) | — |
| 6 | Si la jonction échoue : orange « Salon connectée · Par Bluetooth (lent) · Wi-Fi Direct : <cause> » + action unique (« Allumer le Wi-Fi », « Autoriser Appareils à proximité », « Réessayer ») | — |

Le texte de l'étape 4 reprend `CredentialDecision`/`LinkText` existants ; les refus suivent `TvAuthReply` (`BadPin`, `Locked(retryAfterSec)`).

## 3. Après le premier contact : reconnexion à zéro geste

### 3.1 Identifiants persistants

- **TV** : `WdCredentials(name, pass, id)` tirés une fois (`WifiDirect.groupNetworkName()`, `groupPassphrase()`), rangés dans `TvPrefs` (stockage chiffré, `docs/ADMIN.md` § « réglages dans le stockage chiffré »), jamais journalisés, jamais dans mDNS/DNS-SD/`/api/connections`. Le groupe est créé **avec ces identifiants** (`WifiP2pConfig.Builder().setNetworkName(name).setPassphrase(pass)`, `enablePersistentMode(true)` : Android garde aussi le groupe dans sa liste persistante, ce qui accélère la re-création après redémarrage ; si la puce refuse le mode persistant, la création à identifiants fixes suffit). Le nom reste identique ⇒ le BSSID aussi (adresse P2P de l'interface) ⇒ l'approbation Android 10-12 est réutilisée.
- **Téléphone** : `PinBook` écrit `wd.<tvId>` = `name\tpass` à la réception (HELLO/CBTN/QR), à côté du code (`pin.<tvId>`), sous le même toit : hors sauvegarde (`castbridge_pins.xml` déjà exclu, R-10), effacé par « Oublier la TV ». Le mot de passe ne traverse jamais un `toString`, un journal ni une URL (tests existants de R-14 étendus).
- **Rotation** : « Retirer ce téléphone » sur la TV ⇒ **nouveau secret** (D-W18-7) ; les autres téléphones de confiance le réapprennent au prochain HELLO Bluetooth ou au QR ; un téléphone absent voit `JOIN_DENIED` ⇒ « Le mot de passe Wi-Fi de la TV a changé : rapprochez-vous (Bluetooth) ou scannez le QR » (jamais de boucle : `WdBackoff`).

### 3.2 Qui déclenche la jonction

| Déclencheur | Jonction | Pourquoi |
|---|---|---|
| Ouverture de l'app (premier plan) | oui, si `wd.<tvId>` connu et pas de LAN qui répond en 2,5 s | c'est la session de l'usager |
| Action : copie, « Lire en direct », télécommande, Quiz, « Envoyer à la TV » | oui (et attend ≤ 20 s avant le repli Bluetooth) | l'action en a besoin |
| **Bouton « Wi-Fi Direct »** de la fiche de la TV (brique `wd-manual-button`) | oui, **toujours** : identifiants connus ⇒ jonction directe ; sinon CBTN par Bluetooth (exige lien Bluetooth + PIN/jeton valides : le bouton n'est visible que dans ce cas) ; affiche état + débit + faits | demande du propriétaire : activation explicite depuis l'app ; contournement et diagnostic |
| Diffusion `ACL_CONNECTED` Bluetooth, présence CDM (W7) | oui, une tentative | la TV vient d'apparaître |
| Découverte de service P2P (§ 2.1 a) | **seulement** pendant que l'app est devant et qu'aucune route n'est montée (fenêtres de 10 s, toutes les 30 s, 5 min max) | voir « Salon est à portée » ; coût mesurable |
| Arrière-plan | **non** (aucune jonction, aucun balayage) ; le job 15 min existant ne fait qu'une étape de `LinkDriver` par Bluetooth | batterie, règles Android 14 |

### 3.3 Téléphone sur données mobiles ou sur un autre Wi-Fi (concurrence STA + P2P)

- Données mobiles : aucune interaction ; le groupe est un réseau **local** (pas de `NET_CAPABILITY_INTERNET`) : Android garde le cellulaire comme réseau par défaut, les autres applications ne voient rien. Les sockets de CastBridge vers 192.168.49.0/24 suivent la route de l'interface `p2p-…` (**à confirmer** sans liaison de processus : H-3 de R-14 ; sinon `Network.socketFactory` du réseau P2P obtenu par `requestNetwork` sur `TRANSPORT_WIFI` + spécificateur).
- Autre Wi-Fi (15 % avec box, ou voisin) : les puces récentes (S21+, Android 14) font STA + P2P en concurrence, mono- ou multi-canal (débit partagé dans le temps si les canaux diffèrent : la TV peut fixer `setGroupOperatingBand(GROUP_OWNER_BAND_5GHZ)` API 29 si sa puce est bi-bande, sinon 2,4 GHz canal 1/6/11) ; Android 10-12 par spécificateur : le réseau P2P **remplace** le Wi-Fi principal le temps de la liaison (Internet par cellulaire). One UI : bandeau « Wi-Fi Direct connecté », sans action. Règle inchangée (`BulkRoute`) : **jamais à la place d'un LAN qui répond**.
- Android 14 : rien de nouveau pour P2P ; `NEARBY_WIFI_DEVICES` déjà demandée avec « Appareils à proximité » (même groupe de permissions que le Bluetooth : accordée **sans boîte** si le Bluetooth l'est déjà, à confirmer H-2).

### 3.4 Budget de temps (à mesurer, § 7)

| Chemin | Étapes | Typique attendu | Plage inconnue |
|---|---|---|---|
| Identifiants connus, groupe TV allumé | balayage P2P ciblé 1-3 s + WPA2 < 1 s + DHCP 1-2 s + sonde `/api/hello` < 0,5 s | **4-8 s** | 3-15 s selon la puce |
| Identifiants connus, groupe à (re)créer par CBTN | connexion RFCOMM 2-6 s + `createGroup` 1-3 s + jonction 4-8 s | **8-17 s** | 6-25 s |
| Première fois (d) | appairage SSP 10-20 s + PIN (usager) + HELLO 1 s + jonction 4-8 s | **≈ 30 s** hors saisie | — |
| Bascule TV A → TV B (un seul groupe par téléphone) | `removeGroup`/`cancelConnect` 1-2 s + jonction 4-8 s | **6-10 s** | — |

### 3.5 Plusieurs téléphones, plusieurs TV

- **TV = propriétaire, plusieurs clients** : Android accepte couramment 4-8 clients par groupe ; tous les téléphones de confiance partagent le même secret ; la TV voit `clientList` (existant `clients()`), affiche « 2 téléphones ».
- **Un téléphone, deux TV** : **une seule connexion P2P à la fois** (règle Android) ; `WdSession` est **par TV** mais un seul est `Up` : changer de TV cible (`TvLinkManager.makeDefault`, écran de la TV) ⇒ quitter puis rejoindre (6-10 s) ; les noms `DIRECT-CB-<6>` distincts évitent toute confusion ; la découverte de service dit lesquelles sont à portée.
- **Deux TV dans la même pièce** : deux groupes sur des canaux différents (choix d'Android) ; aucun conflit d'adresse pour le téléphone (il n'en voit qu'un) ; `192.168.49.1` n'est **jamais** un alias d'identité (DESIGN-TV-CONTEXT § 1.1) : la TV est identifiée par `id`/`installId` à la sonde `/api/hello`.

## 4. Variantes « la TV est le point d'accès », sur la même puce

| Critère | (a) Groupe P2P autonome, TV propriétaire | (b) SoftAP de la TV : `WifiManager.startLocalOnlyHotspot` (API 26+) | (c) Groupe recréé à la demande (R-14) vs persistant |
|---|---|---|---|
| Compatibilité puce AIC USB | exige le mode **GO** dans le pilote (`iw list` : `P2P-GO` dans les combinaisons) ; `FEATURE_WIFI_DIRECT` déclaré par la plateforme GaiaOS ? **inconnu** | exige le mode **AP** (hostapd) : plus courant que GO sur les modules USB ; `FEATURE_WIFI` suffit à l'API, mais `startLocalOnlyHotspot` échoue (`ERROR_GENERIC`/`ERROR_INCOMPATIBLE_MODE`) si le pilote ne sait pas | même pilote dans les deux cas |
| Identifiants | choisis par l'app (nom + mot de passe, API 29+) ⇒ **persistants, remis une fois** | **tirés par Android à chaque démarrage**, non choisissables hors application système (API 30+ `SoftApConfiguration` = `@SystemApi`) ⇒ à **remettre à chaque session** (Bluetooth ou QR), donc jamais « zéro geste » sans Bluetooth | persistant : jonction directe ; à la demande : Bluetooth obligatoire à chaque session |
| Permission TV | `NEARBY_WIFI_DEVICES` (33+, déjà au manifeste) | idem **et** la **localisation activée** sur l'appareil pour API 26-32 (une TV n'a souvent pas ce réglage) | — |
| Friction premier appairage | § 2.2 | identique, plus une remise d'identifiants par session | persistant : moindre |
| Jonction téléphone | `WifiP2pManager.connect` sans boîte (33+) ; spécificateur mémorisé (10-12) | réseau Wi-Fi ordinaire : `WifiNetworkSpecifier` ⇒ **boîte à chaque fois** (SSID aléatoire = jamais mémorisée) ou réglages Wi-Fi à la main | — |
| Durée de vie | tant que l'app la tient ; survit à l'arrière-plan de l'activité (service) | **se ferme** quand l'application cliente perd le premier plan ou meurt ; une seule app à la fois | persistant : allumé à l'écran ; à la demande : bail 30 s-10 min |
| Effet sur le Wi-Fi/Ethernet de la TV | STA + GO : dépend des combinaisons d'interfaces de la puce (souvent **une seule** sur USB ⇒ le Wi-Fi STA de la TV tombe le temps du groupe ; Ethernet intact) | STA + AP : même question ; de plus Android **refuse** LOHP si le partage de connexion est actif | persistant aggrave l'effet dans les 15 % avec box ⇒ règle : groupe persistant **seulement si** la TV n'a pas de LAN ou si la puce fait la concurrence (mesure H-5) |
| Clé USB | bus USB 2.0 partagé (`UsbHardware.sharesBusWithWifi`) : les octets passent deux fois ; le débit disque (1,4-9,5 Mo/s mesurés) borne de toute façon | idem | idem |
| Débit attendu | 1×1 n 2,4 GHz : 2-8 Mo/s ; ac 5 GHz (si AIC8800D) : 10-20 Mo/s radio, borné par la clé | même radio | même radio |
| Surprises API | GO : adresse fixe 192.168.49.1 ; `createGroup` `BUSY` après un ancien groupe (géré) ; dialogues P2P système **absents** pour une jonction par identifiants | SSID/mot de passe différents à chaque fois ; adresse DHCP variable ; fermeture silencieuse | — |

**Recommandation (D-W18-1)** : **(a) + persistant**, ordre d'essai **a → b → Bluetooth optimisé** (§ 9). (b) n'est retenue que si le test terrain montre « AP oui, GO non » ; elle garde alors le Bluetooth comme porteur d'identifiants à chaque session (chemin R-14 inchangé, 8-17 s) et le QR comme alternative.

## 5. Tout passe par la liaison, de façon transparente

### 5.1 Modèle de routes

```
                      usage →   CONTRÔLE (HELLO, jeton, /api/info)   SYNCHRO (W7 CBSY)   MASSE (copie, lots)   CAST (/api/playurl + :8089)   TÉLÉCOMMANDE   QUIZ
 LAN commun qui répond          1                                     1                   1                     1                             1              1
 Wi-Fi Direct monté (Up)        2 (si pas de LAN)                     2                   2                     2  (seule voie sans LAN)       2              2
 Bluetooth RFCOMM/mux           3 (toujours gardé comme secours)      3                   3 (< 5 Mo, ou WD KO)   ✘ vidéo · ✔ audio/photo (§ 9) 3 (CBTR)       ✘
```
`WdPolicy.route(use, facts)` (pur, `C/link/`) enveloppe `BulkRoute.decide` (gardé pour la masse) et `LinkPlanner.plan` (contrôle) : une **seule** table, testée, consommée par `TvLink` (contrôle), `TransferQueue` (masse), `CastSession` (cast), `RemoteController`, `QuizHub`/`QuizSync`, `LotPush`, `ParentalSync`.

### 5.2 Ce qui marche déjà sur le groupe et ce qui doit changer

| Fonction | Aujourd'hui | À changer |
|---|---|---|
| Copie / copie-et-lecture / déplacement / file | passent par `UploadService` sur `http://192.168.49.1:8765` dès que `AutoWifiDirect.bulkBase` répond (R-14) : reprise, transfert rapide, R-08, R-12, R-13 inclus | seuil 5 Mo **supprimé** quand WD est `Up` (déjà le cas) ; WD tenté pour **tout** envoi sans LAN, pas seulement ≥ 5 Mo (D-W18-1) |
| Contrôle (HELLO HTTP, `/api/info`, jeton) | Bluetooth seulement (R-14 a interdit `Direct`) | `Direct` autorisé **quand** `WdSession.Up` ; repli BT à la perte |
| « Lire en direct » | `TvClient(base).playUrl(url)` ; `serve()` = `Upnp.localIp()` (première IPv4 `wlan*`) | `LocalAddress.toward(tvIp)` : adresse source de la route vers la TV (socket UDP `connect` sans trafic) ; `ServerService` écoute déjà sur `0.0.0.0:8089` ; le message « Pas d'adresse Wi-Fi : … même réseau » devient « Aucune liaison Wi-Fi avec la TV : copie ou Bluetooth » |
| UPnP/SSDP (DLNA) | multicast sur le réseau par défaut | **non utilisé** pour CastBridge-TV sur le groupe (le téléphone connaît l'adresse du propriétaire) ; l'onglet DLNA reste pour les autres TV |
| Télécommande | `host` ou Bluetooth | `host` = base de la route active ; CBTR gardé si WD tombe |
| Quiz (téléphones joueurs par le serveur de la TV) | `joinUrl` = première IPv4 de site de la TV | `joinUrls()` (une par interface : `wlan0`, `eth0`, `p2p-…`) ; le QR de la salle montre l'URL du **réseau du téléphone qui a ouvert** la salle, et la page `/quiz` répond sur toutes ; les joueurs rejoignent le groupe de la TV **avec les identifiants** (téléphones de confiance) ou via le QR Wi-Fi `WIFI:` (invité : `WifiDirect.wifiUri`, existant) **affiché sur demande** dans l'écran Quiz (D-W18-8) |
| Lots / Apprendre / Boutique / parental | `LotPush` par base HTTP ou CBT1 ; `ParentalSync` CBTP | base = route active ; rien d'autre |
| mDNS | `NsdManager` ne publie/écoute que sur le réseau par défaut : **ne traverse pas** le groupe de façon fiable | la découverte sur le groupe = adresse connue + sonde `/api/hello` (`id`) ; DNS-SD P2P pour la présence |

### 5.3 Cycle de vie

```
 TV : CastBridge-TV à l'écran ──► groupe UP (identifiants persistants) ──► reste UP tant que l'app est au premier plan
      app en fond/veille TV ──► groupe retiré après 2 min sans client (jamais pendant une réception, un cast, une télécommande ouverte, une salle Quiz)
      TV sur un LAN et puce sans concurrence (H-5 rouge) ──► groupe à la demande seulement (CBTN), bail R-14

 Téléphone : Off ─(déclencheur § 3.2)─► Joining ─► Probing ─► Up ─(file vide, pas de cast, pas de télécommande, app en fond ≥ 2 min)─► Leave ─► Off
                                                   │ perte (onLost / sonde ×3)
                                                   ▼
                                           Lost ─► re-jonction immédiate ×1, puis courbe `LinkMachine` (2→60 s) ; 3 échecs/10 min ⇒ BT 10 min (`WdBackoff`)
```
- **Cast en cours et groupe perdu** : la TV lit depuis `:8089` : la lecture **s'arrête** (tampon de quelques secondes) ; le téléphone re-joint (4-8 s) et renvoie `playUrl` à la position connue (`RemoteClock`, existant) ; au-delà de 20 s, message « Liaison Wi-Fi perdue avec la TV : rapprochez-vous » et proposition « Copier sur la TV et lire ».
- **Copie et groupe perdu** : existant (`rerouteAfterLoss` ≤ 2, reprise `.part`/`.cbx`).
- **Écran du téléphone éteint** pendant une copie : FGS `dataSync` existant ; la connexion P2P survit (Android ne la coupe pas en Doze tant qu'un service la tient) ; après la copie, départ à 2 min.
- **Batterie** : P2P client au repos ≈ Wi-Fi associé (≈ 1-2 %/h écran éteint si l'app reste `Up` : d'où le départ à 2 min sans usage) ; aucune jonction de fond.

### 5.4 Ligne d'état honnête (sémantique `TvSignal`)

| Niveau | Téléphone (carte de la TV) | TV (icône Wi-Fi Direct) |
|---|---|---|
| **VERT** ● | « Salon connectée · Par Wi-Fi Direct » / « Par le Wi-Fi de la maison » / « Par Bluetooth » (petit fichier : pas une dégradation) | « Wi-Fi Direct · 1 téléphone » |
| **ORANGE** ▲ | « Par Bluetooth seulement (lent) · Wi-Fi Direct : <cause> » + action unique ; « Liaison Wi-Fi en préparation… » | « Wi-Fi Direct prêt · aucun téléphone » ; « Wi-Fi de la TV éteint : Wi-Fi Direct impossible » (action : Paramètres › Réseau) |
| **ROUGE** ■ | « Salon injoignable : ni Wi-Fi ni Bluetooth » | « Wi-Fi Direct : échec (code n) » |
| **NOIR** ○ | — | « Wi-Fi Direct désactivé » (choix du propriétaire, MENU) ; **Internet absent reste NOIR** |

## 6. Sécurité

| Sujet | Règle W18 |
|---|---|
| Transport des identifiants | **uniquement** par un canal authentifié : RFCOMM appairé (chiffré par la pile, pair = adresse du socket **et** confiance/PIN), QR (présence physique), ou `CBSX` W7 une fois livré ; **jamais** en HTTP clair sur un LAN (le jeton y est déjà en clair, limite connue `BT-PLUG-AND-PLAY.md:130` : on n'y ajoute pas un secret Wi-Fi) ; jamais dans HELLO d'un pair non de confiance (1 octet d'erreur, existant) |
| Secret | 16 caractères, alphabet 56, ≈ 93 bits (`WifiDirect.groupPassphrase`) ; WPA2-PSK : brute force hors ligne hors de portée ; par TV ; rotation sur « Retirer ce téléphone » / « Changer le mot de passe » |
| Rejeu | poignée de main WPA2 avec nonces ; l'API HTTP exige jeton ou PIN comme partout ; un jeton refusé n'est jamais rejoué (`CredentialGate`) |
| Voisin qui rejoint le groupe | impossible sans le secret ; un téléphone de confiance volé = déjà couvert (« Retirer » ⇒ rotation) ; un invité Quiz par QR `WIFI:` voit le groupe : il n'accède qu'aux routes **sans PIN** (`/quiz/*`, `/api/hello`, `GET /`), exactement comme un invité sur la box aujourd'hui ; proposer la rotation à la fin de la salle si des invités sont entrés (D-W18-8) |
| Brute force du PIN par la nouvelle surface | DNS-SD P2P n'expose que `id`, nom, version : aucun secret, aucune route ; l'API HTTP sur 192.168.49.1 n'est joignable que par les membres du groupe ; `PinGuard` 5/60 s **par IP** y reste ; côté Bluetooth, le HELLO avec PIN (D-W18-4) est compté **par adresse Bluetooth** par le même `PinGuard` (clé `bt:<adresse>`) ; un pair non appairé n'a pas de socket sécurisé : aucun essai possible |
| Journaux | aucun mot de passe, nom de groupe masqué à 3 caractères, adresses tronquées (`Redact.scrub`) ; tests de `toString` existants étendus à `WdCredentials`, `TrustByPin` |
| TV d'essai | R-14 refusait le groupe (`wd.err=trial`) : **à inverser** (D-W18-6) : sans point d'accès, l'essai n'aurait aucune voie de données ; `TrialPolicy` garde ses listes de routes, le transport ne change pas les droits |
| Profils enfants, parental | le lien n'est pas un droit : `PhoneGate` W6, `ParentalModel` inchangés ; un enfant avec le téléphone de confiance a ce qu'il a déjà par Bluetooth |
| PIN (`docs/ADMIN.md`) | sémantique **inchangée** : 6 chiffres, `X-CB-Pin`, 5 échecs/60 s, jamais en URL ni journal ; il prouve la présence, il n'est **jamais** dérivé en clé |

## 7. Réalité matérielle : le test terrain de 10 minutes, AVANT les cahiers coûteux

**Fait bloquant (B-W18-1)** : la puce Wi-Fi USB de la TV GaiaOS (Amlogic T950, module AIC, bus partagé avec la clé) sait-elle être **propriétaire de groupe P2P** (ou **point d'accès**), à quel **débit**, et avec quels **effets de bord** (Wi-Fi/Ethernet de la TV, E/S de la clé) ? Sans ce fait, **aucun cahier 18b/18c ne démarre** ; 18a (cœur pur) peut avancer pendant le gel car il sert aux trois issues (GO, AP, Bluetooth).

### 7.1 Protocole (propriétaire, ≈ 10 min)

Deux outils, au choix : **(A) le bouton « Wi-Fi Direct »** de la fiche de la TV (branche `claude/wd-manual-button`, dès qu'elle est installée sur le S21+ et la TV : un appui = groupe demandé par Bluetooth, jonction, état, débit sur 20 Mo, faits F1/F4/F5/F6/F9 affichés en français) ; **(B) l'application existante** (TV : MENU › Wi-Fi Direct ; téléphone : onglet Wi-Fi Direct puis copie) si la branche n'est pas encore sur les appareils. Le tableau ci-dessous est écrit pour (B) ; avec (A), les minutes 1-6 se réduisent à **un appui et une lecture d'écran**, et les minutes 0 et 7-10 restent (SSH : F2, F3, F9, F11).

Préconditions : TV allumée, CastBridge-TV à l'écran, clé USB branchée, Wi-Fi de la TV **allumé** (pas forcément connecté) ; S21+ avec CastBridge, Wi-Fi allumé, données mobiles ; lien Bluetooth de confiance établi (pour A) ; Mac en SSH sur la TV (relais `nc` par le téléphone, mémoire « Relais téléphone → TV ») ou `adb`.

| Min | Geste | Lire |
|---|---|---|
| 0-1 | SSH TV : `pm list features \| grep -E "wifi.direct\|wifi$"` ; `dumpsys wifip2p \| head -40` ; `iw list 2>/dev/null \| grep -A 12 "interface combinations"` ; `ls /sys/class/net/` ; `cat /sys/bus/usb/devices/*/product` ; `dmesg \| grep -i -E "aic\|p2p\|hostapd" \| tail -20` | **F1** `android.hardware.wifi.direct` présent ? **F2** combinaisons : `P2P-GO`, `AP`, nombre d'interfaces simultanées (`#{managed} <= 1, #{P2P-GO} <= 1` = concurrence STA+GO) ; **F3** modèle du module (AIC8800x ?) |
| 1-3 | TV : MENU › Connexion & réglages › **Wi-Fi Direct : activer** | **F4** l'écran affiche « réseau DIRECT-CB-CastBridge · mot de passe · 192.168.49.1:8765 » (= `createGroup` OK) ou « échec de création du groupe (code n) » / « non pris en charge » ; SSH : `ip addr show \| grep -A3 p2p` (interface `p2p-wlan0-0` avec 192.168.49.1) |
| 3-4 | Téléphone : onglet **Wi-Fi Direct** › saisir nom + mot de passe › Connecter (boîte Android « Se connecter ? ») ; chronomètre | **F5** temps jusqu'à « Connecté » (s) ; `adb shell ip addr \| grep p2p` sur le téléphone |
| 4-6 | Téléphone : onglet CastBridge TV (adresse 192.168.49.1, PIN) › **Copier** une vidéo de 200 Mo vers la **mémoire interne** ; lire le débit affiché ; puis la même vers la **clé USB** | **F6** Mo/s téléphone→TV interne ; **F7** Mo/s vers la clé (bus partagé) ; TV : la lecture d'une vidéo depuis la clé pendant la copie saccade-t-elle ? |
| 6-7 | Téléphone : bibliothèque TV › **Télécharger** un fichier de 100 Mo | **F8** Mo/s TV→téléphone |
| 7-8 | Si la TV a un LAN (Ethernet ou box) : SSH `ip addr show eth0 wlan0` pendant le groupe ; `ping -c 3 <box>` | **F9** le Wi-Fi/Ethernet de la TV est-il resté (adresse, ping) ? |
| 8-9 | TV : MENU › Wi-Fi Direct : désactiver, puis activer ; téléphone : reconnecter | **F10** temps de re-jonction ; le mot de passe a-t-il changé (aujourd'hui : oui, frais par groupe) |
| 9-10 | Si **F4 rouge** seulement : `adb shell cmd wifi start-softap CBTEST wpa2 motdepasse123 2>&1` (ou `cmd wifi start-lohs`) puis `ip addr show \| grep -A3 "ap0\|swlan"` | **F11** la puce sait-elle faire **point d'accès** (variante b) ? |

Noter aussi : chaleur du module USB, messages `dmesg` pendant la copie (réinitialisations USB = contention de bus).

### 7.2 Table de décision

| F1 feature | F4 GO | F6 interne | F7 clé | F9 LAN gardé | F11 AP | Verdict | Suite |
|---|---|---|---|---|---|---|---|
| oui | oui | ≥ 3 Mo/s | ≥ 1,5 Mo/s | oui ou sans objet | — | **VERT : variante (a) persistante** | lancer 18b (w18-07, 08, 09, 10) |
| oui | oui | ≥ 3 Mo/s | ≥ 1,5 Mo/s | **non** | — | VERT avec réserve | (a) persistante **seulement sans LAN** ; à la demande (R-14) si la TV a un LAN (`WdPolicy` le décide) |
| oui | oui | 1-3 Mo/s | < 1,5 Mo/s | — | — | ORANGE | (a) quand même (toujours ≥ 5× le Bluetooth) ; le disque/bus borne : afficher la note existante (`TransferHost.note`) ; cast HD depuis `:8089` ≈ 1-3 Mo/s suffit pour 720p (≈ 0,5 Mo/s) |
| oui | **non** (échec/`BUSY` persistant/`UNSUPPORTED`) | — | — | — | **oui** | ORANGE : variante **(b)** | w18-07 en version SoftAP (identifiants remis à chaque session par Bluetooth/QR) ; w18-08 inchangé (réseau Wi-Fi ordinaire par spécificateur) |
| oui/non | non | — | — | — | **non** | **ROUGE** | 18c : Bluetooth optimisé (§ 9) ; dire au propriétaire ce qui ne sera pas possible (cast vidéo) ; (a)/(b) restent **dans le code** pour les autres TV du parc (`wd.cap`) |
| — | oui | < 1 Mo/s | — | — | — | ROUGE pour le cast, ORANGE pour la copie | copie par WD (toujours mieux que BT), cast = « Copier et lire » seulement |

Les seuils (3 ; 1,5 ; 1 Mo/s) sont des **ordres de grandeur** : la copie classique en Wi-Fi mesurée sur cette TV (`docs/TRANSFER.md` § 1) donne 1,4-9,5 Mo/s selon la clé ; 720p ≈ 2-4 Mbit/s ≈ 0,25-0,5 Mo/s.

### 7.3 Petit diagnostic additif (cœur pur + mince)

- `C/link/WdDiag.kt` : `data class WdFacts(feature, permission, wifiOn, p2pIface, goActive, clients, apCapable, staConcurrent, lanUp, usbSharesBus, lastCreateError, lastThroughputBps, lastJoinMs)` + `WdDiag.lines(facts): List<Line(level, text)>` en français (« Wi-Fi Direct : cette TV peut être propriétaire de groupe », « Le module Wi-Fi partage le bus USB avec la clé : débit partagé », « Dernière copie par Wi-Fi Direct : 4,2 Mo/s », « Point d'accès : non essayé »), `WdDiag.verdict(facts)` = la ligne de la table § 7.2. Testé (une ligne par fait, aucune issue sans phrase).
- TV : `GET /api/diag/wifi-direct` (PIN ou jeton ; **aucun secret** : ni nom complet ni mot de passe) ⇒ JSON de `WdFacts` + lignes ; écran **Connexion & réglages › « Diagnostic Wi-Fi Direct »** (D-pad, « Copier le rapport » existant) ; relevé mince : `PackageManager`, `WifiP2pManager.requestGroupInfo`, lecture de `/sys/class/net`, `UsbHardware` (existant) ; **pas de `iw`** (binaire absent sur certaines TV : on lit sysfs).
- Téléphone : **le bouton « Wi-Fi Direct »** de la fiche de la TV (brique `wd-manual-button`) est l'écran de mesure : il envoie 20 Mo vers `/api/transfer/begin?discard=1` (mode « réseau seul » existant, `ReceiverServer.kt:939`), affiche Mo/s, état et faits ; W18 lui ajoute l'envoi de la mesure dans `WdFacts.lastThroughputBps` via `/api/diag/wifi-direct` (POST, jeton) pour que la TV s'en souvienne (w18-04 côté route, w18-08 côté appel). L'onglet « Wi-Fi Direct » manuel (nom + mot de passe tapés) reste pour un Mac ou un téléphone sans Bluetooth.

## 8. Décisions

**Prises par l'architecte (renversables, avec recommandation)**

| id | Décision | Recommandation |
|---|---|---|
| D-W18-1 | Wi-Fi Direct = voie principale pour tous les usages sans LAN ; la TV est le point d'accès (variante a, ordre a → b → Bluetooth) | oui (décision du propriétaire, mise en forme) |
| D-W18-2 | Secret de groupe **persistant par TV**, remis une fois par canal authentifié, rangé dans `PinBook` (téléphone) et `TvPrefs` chiffré (TV) ; renverse « frais par groupe » de R-14 | oui : c'est la condition du zéro geste et de la boîte unique sur Android 10-12 |
| D-W18-3 | Groupe **allumé tant que CastBridge-TV est à l'écran** quand la TV n'a pas de LAN (ou si H-5 montre la concurrence) ; sinon à la demande (R-14) | oui, sous réserve F9 |
| D-W18-4 | Le PIN tapé sur le téléphone, envoyé dans HELLO sur RFCOMM **appairé**, remplace la fenêtre « Autoriser » ; la comparaison numérique Android reste | oui ; la fenêtre reste disponible pour un téléphone sans PIN (invité du foyer) |
| D-W18-5 | Le QR de la TV porte aussi `wd`/`wp` ; affiché sur demande et au premier démarrage sans téléphone de confiance (10 min) ; « Changer le mot de passe » | oui |
| D-W18-6 | TV d'essai : Wi-Fi Direct **autorisé** (renverse `wd.err=trial`) | oui |
| D-W18-7 | « Retirer ce téléphone » ⇒ rotation du secret | oui (coût : les autres téléphones réapprennent au prochain HELLO) |
| D-W18-8 | Quiz : invités par QR `WIFI:` du groupe, affiché sur demande dans l'écran Quiz ; rotation proposée à la fin | oui |
| D-W18-9 | Android 10-12 : spécificateur (boîte mémorisée) plutôt que la localisation | oui (règle W7 « aucune localisation ») |
| D-W18-10 | Aucune agrégation de radios ; aucune autre voie que WD, BT, LAN existant | oui (W8 abandonné ; matériel neuf exclu) |
| D-W18-11 | Le bouton « Wi-Fi Direct » de la fiche de la TV (brique `wd-manual-button`) **reste** après W18 : contournement manuel et diagnostic ; l'automatique est l'usage normal ; le bouton n'est jamais nécessaire pour qu'une copie ou un cast parte | oui (décision du propriétaire, mise en forme) |

**BLOQUÉ (faits à établir par le propriétaire, § 7)** : **B-W18-1** F1/F4 (GO possible ?) et F6-F8 (débits) ; **B-W18-2** F9 (STA + GO : concurrence) ; **B-W18-3** F7 et `dmesg` (contention du bus USB) ; **B-W18-4** F11 (AP possible ?) seulement si F4 est rouge.

## 9. Plan de secours sans nouveau matériel : Bluetooth optimisé (18c, conditionnel)

Déclenché si le verdict § 7.2 est ROUGE (ni GO ni AP), ou pour les autres TV du parc sans Wi-Fi Direct (`wd.cap=0`). Débit RFCOMM mesuré : **100-300 ko/s** ; rien de logiciel ne le dépasse (modulation dans la puce, BLE absent sur la TV).

| Optimisation | Principe | Gain réel | Où |
|---|---|---|---|
| Envoi **séquentiel ordonné** | une seule liaison CBT1/mux à la fois ; file par priorité (R-09) : « Copier et lire » avant les copies, petits lots avant les vidéos | pas de partage de la liaison entre deux envois (chacun finit plus tôt) | `C/tv/QueueRules` (existant), `BtLane` |
| **Compression** | gzip par bloc **seulement** pour les extensions compressibles (texte, lots non compressés : `Blocks.compressible`, existant) ; jamais pour mp4/mp3/jpg/zip | 2-5× sur les lots Apprendre/Quiz ; 0 sur les médias | `C/xfer/Blocks.kt` |
| **Reprise** | tranches de 256 Kio, offset confirmé (`.part`, CBT1 existant) ; reprise au milieu d'un bloc (`writeSlice`) | aucune perte à la coupure | existant + `BluetoothLane` à **brancher** (`docs/TRANSFER.md` § 7) |
| **Priorité du contrôle** | pendant un envoi CBT1, les touches de télécommande (CBTR) et le HELLO passent par **une autre liaison** ; l'envoi marque une pause de 300 ms quand une trame de contrôle attend (`Mux` : flux prioritaire) | télécommande réactive pendant une copie | `C/tunnel/Mux.kt` (additif : drapeau `urgent`) |
| **Pré-transcodage** sur le téléphone (opt-in) | `MediaCodec` : vidéo > 50 Mo ⇒ proposition « Envoyer en qualité réduite (480p, ≈ 60 Mo pour 10 min) : 5 min par Bluetooth au lieu de 25 » ; transcodage en FGS `dataSync`, fichier temporaire supprimé après envoi ; **jamais** sans accord, jamais pour les lots signés | 3-6× moins d'octets ; coût CPU/batterie (≈ 1× la durée de la vidéo sur un S21+, plus sur un Tecno) | `S/transcode/` nouveau, `C/link/BtPlan.kt` (décision pure : quand proposer) |
| **Copie nocturne** | « Envoyer cette nuit » : `JobScheduler` chargeur + inactif + Bluetooth ; FGS `dataSync` ; la TV doit être allumée (dire : « laissez la TV allumée ») | une vidéo de 1,5 Go passe en ≈ 2 h sans que l'usager attende | `S/TransferQueue` (déjà persistée R-09) + un job |
| **Déduplication** | empreinte déjà livrée (R-12 `ContentIndex`) : ne jamais renvoyer un contenu que la TV a déjà, même renommé | 100 % sur les doublons | existant |
| **Cast par Bluetooth** | `/api/playurl` vers le tunnel mux (`127.0.0.1:18765` ⇒ `:8089` du téléphone) : **audio** (128 kbit/s = 16 ko/s : fluide) et **photos** (quelques secondes par image) ; **vidéo** : refusée avec la phrase honnête et le remplacement « Copier sur la TV et lire » | ce que le Bluetooth sait faire | `CastPlan` (existant) |

**Ce que le Bluetooth ne pourra pas faire, à dire tel quel** : lire en direct une vidéo (même 480p ≈ 1 Mbit/s = 125 ko/s : à la limite, saccadé ; 720p/HD : impossible) ; copier un film de 1,5 Go en moins de ≈ 1 h 30 ; faire jouer les téléphones au Quiz par le serveur de la TV (les joueurs n'ont pas de Bluetooth vers la TV : le Quiz reste **sur la TV** avec la télécommande, et le téléphone de confiance comme seul joueur par le tunnel).

## 10. Risques

| # | Risque | Prob. | Impact | Parade |
|---|---|---|---|---|
| R1 | La puce AIC USB ne fait pas GO (ni AP) | moyen | **bloquant** pour 85 % des foyers | test § 7 d'abord ; 18c Bluetooth ; dire la limite au propriétaire |
| R2 | GO possible mais débit < 1 Mo/s (USB 2.0 partagé, 2,4 GHz 1×1, pilote) | moyen | cast impossible, copie lente mais 5-10× le BT | mesurer F6-F8 ; bande 5 GHz si bi-bande ; copie-et-lecture plutôt que direct |
| R3 | STA + GO impossibles : la TV perd sa box pendant le groupe (15 % des foyers) | élevé sur USB | mises à jour/activation par la box coupées le temps du groupe | D-W18-3 : persistant **seulement sans LAN** ; à la demande sinon |
| R4 | Jonction `connect(nom+mot de passe)` refusée par certains téléphones (OEM, Android 10-12) | faible-moyen | boîte à chaque fois ou Bluetooth | spécificateur mémorisé ; QR ; `WdBackoff` ; ligne orange avec cause |
| R5 | Sockets vers 192.168.49.1 non routés sans liaison de réseau (H-3) | faible | envois qui échouent | `Network.socketFactory` par requête (jamais `bindProcessToNetwork`) |
| R6 | `NsdManager`/SSDP sur le groupe | certain | découverte LAN muette | adresse connue + `/api/hello` ; DNS-SD P2P pour la présence |
| R7 | Secret persistant volé (téléphone perdu, QR photographié) | faible | accès au groupe, pas aux routes PIN/jeton | rotation (D-W18-5/7) ; `PinGuard` ; jeton 12 h |
| R8 | GaiaOS n'affiche pas certaines boîtes P2P ou refuse `createGroup` après veille (`BUSY`) | moyen | groupe absent au réveil | `removeGroup` avant `createGroup` (existant) ; re-création à l'arrivée au premier plan ; diagnostic |
| R9 | Régression des parcours R-01…R-14 par le changement de route de contrôle | moyen | copies qui échouent | `WdPolicy` pur et testé ; harnais `WdJourneyTest` ; un seul cahier à la fois sur `S/TvLink.kt`, `R/TvService.kt` (gel R2) ; audits Opus |
| R10 | Batterie du téléphone (client P2P tenu) | faible | 1-2 %/h écran éteint | départ à 2 min sans usage ; aucune jonction de fond |
| R11 | Deux TV dans la pièce, mauvaise cible | faible | envoi vers la mauvaise TV | identité par `id` à la sonde, jamais par 192.168.49.1 (DESIGN-TV-CONTEXT § 1.1) |

## 11. Ce qu'il faut construire, dans l'ordre, autour du gel

```
 Propriétaire : TEST TERRAIN § 7 (10 min)  ──────────────────────────────┐
                                                                          ▼
 18a (gel, cœur pur, JVM) : w18-01 WdCredentials ─► w18-02 WdPolicy/WdSession ─► w18-06 harnais
                            w18-03 TrustByPin ──────┘                     w18-04 WdDiag (+ route) ─► w18-11 kit terrain (haiku)
                            w18-05 CastRoute/LocalAddress                 w18-12 docs (haiku)
                                                                          │
 verdict VERT/ORANGE(a ou b) ─────────────────────────────────────────────┤ verdict ROUGE ─► 18c : w18-13 Bluetooth optimisé
                                                                          ▼
 18b (après la sortie du gel, ou exception « correctif terrain R-14 ») : w18-07 TV ─► w18-08 téléphone runtime ─► w18-09 écrans ∥ w18-10 cast/quiz/remote
 Audits Opus obligatoires : w18-01, w18-07, w18-08 ; échantillons : w18-02, 03, 10.
```

Ce qui survit tel quel de `claude/wd-manual-button` (bouton) : l'écran, son appel CBTN, sa mesure sur 20 Mo, ses faits affichés ; W18 ne fait que **remplacer sa source d'état** par `WdSession`/`WdDiag` (w18-08/09 : la fiche lit `WdLine`) et lui donner la jonction directe sans Bluetooth quand les identifiants sont connus. Ce qui survit tel quel de `claude/auto-wifi-direct` : `C/link/BulkRoute.kt` (enveloppé), `C/link/WdClient.kt` (automate réutilisé par `WdSession` pour le chemin à la demande), `C/tv/WifiDirect.kt` (générateurs, `Err`), `C/tv/BtProtocol.kt` (drapeaux, `LinkInfo`), `R/WifiDirectGroup.kt` (création, `clients()`, erreurs), `S/AutoWifiDirect.kt` (effets : CBTN, `connect`, sonde), `AutoWifiDirectTest` (27 tests, à garder verts), les textes de ligne d'état. Ce qui change : persistance des identifiants (TV et téléphone), bail, route de contrôle, sockets par réseau sur 10-12, seuil 5 Mo, essai, QR, HELLO + PIN. **La branche est fusionnée d'abord** (après ses trois correctifs d'audit, portés par w18-02/07/08 ou par un correctif terrain séparé si le coordinateur préfère) : W18 se construit dessus, pas à côté.

Cahiers, modèles, coûts, matrice de propriété : `docs/agent-briefs/SONNET-WAVE18-INDEX.md`.

## 12. Ce qui n'a pas pu être vérifié

- Tout ce qui touche la puce de la TV (§ 7) : feature, GO, AP, concurrence, débits, bus USB, chaleur.
- Que le S21+ route vers 192.168.49.0/24 sans liaison de réseau (H-3 de R-14) ; que `NEARBY_WIFI_DEVICES` est accordée sans boîte quand « Appareils à proximité » l'est (H-2).
- Que l'approbation du `WifiNetworkSpecifier` est bien réutilisée par Android 10-12 pour un même SSID/BSSID sur les téléphones du parc (documenté ; non mesuré ici).
- La durée réelle d'une jonction P2P (4-8 s est une attente, pas une mesure) et celle d'une bascule entre deux TV.
- Le nombre de clients P2P que GaiaOS accepte sur un propriétaire de groupe (Quiz avec 4-6 téléphones).
- Le comportement de `createGroup` après la veille de la TV (`BUSY`) et la survie du groupe quand CastBridge-TV passe en arrière-plan sous GaiaOS.
