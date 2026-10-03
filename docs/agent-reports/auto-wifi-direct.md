# Wi-Fi Direct automatique quand seul le Bluetooth relie le téléphone et la TV (R-14)

**Demande du propriétaire (2026-10-03)** : « dans le système plug and play, quand seul le bluetooth est le réseau disponible, il faut auto-configurer wifi-direct, pour démarrer le transfert des fichiers en toute transparence des utilisateurs ».

**Statut** : code + tests JVM faits (branche `claude/auto-wifi-direct`, 2 commits, non poussée). **Rien n'a été essayé sur un appareil** (Mac occupé : ni émulateur, ni adb). Le Wi-Fi Direct ne se teste pas sur la JVM : la liste « À confirmer sur le S21+ et la TV » en fin de rapport est obligatoire avant de fermer R-14. Audit Opus à suivre (secrets par Bluetooth, comportement radio).

Abréviations : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`.

## 1. Ce que voit l'usager

- Téléphone et TV sans réseau commun (ou box qui isole ses clients), lien de confiance Bluetooth actif. L'usager fait « Copier sur la TV » (ou « Copier et lire », « Déplacer », plusieurs fichiers en file) d'un fichier de 200 Mo.
- La carte « File d'attente des envois » affiche d'abord, en orange : « Attention · Bluetooth seulement : lent · Wi-Fi Direct en préparation… », puis en vert : « OK · Par Wi-Fi Direct (automatique) ». Aucune boîte de dialogue sur Android 13+ (une seule autorisation la première fois, voir § 3.1), aucune sur la TV.
- Si quelque chose manque : orange « Bluetooth seulement : lent » et une phrase qui dit pourquoi (autorisation refusée, Wi-Fi de la TV éteint, 3 essais ratés…). Rouge seulement si ni Wi-Fi ni Bluetooth ne marche (sémantique de `C/ux/TvSignal.kt`).
- Le Bluetooth reste le lien de contrôle et de repli ; la carte « Passerelle Bluetooth » est inchangée.
- Réglage (Réglages › Connexion) : « Wi-Fi Direct automatique (si seul le Bluetooth est disponible) », **activé par défaut**. Aucun nouvel écran.

## 2. Ce qui existait (preuves)

| Élément | Où | Constat |
|---|---|---|
| Groupe créé par la TV sur CBTN `WANT_WIFI_DIRECT` | `R/TvService.kt` `linkInfo` (avant : l. 306-321) | seulement si `wd_enabled` (MENU) **ou** TV sans aucun réseau : une TV sur un Wi-Fi qui isole ses clients ne créait **jamais** le groupe |
| Mot de passe | `R/WifiDirectGroup.kt` (avant : l. 31-32) | **persistant** dans les préférences (`wd_pass`), 10 caractères, nom de réseau fixe `DIRECT-CB-CastBridge` pour toutes les TV |
| HELLO | `R/TvService.kt` `helloHandler` → `linkInfo(false)` | renvoyait le mot de passe du groupe à tout téléphone de confiance dès qu'un groupe existait |
| Jonction téléphone | `S/BtUploadService.kt` `joinWifiDirect` | `WifiNetworkSpecifier` (boîte système à chaque fois), seulement dans le service Bluetooth ; « Copier et lire », chemin ordonné (R-08), déplacement : perdus sur ce chemin (le service Bluetooth n'envoie qu'une copie simple) |
| Route de contrôle | `C/tv/BtProtocol.kt` `LinkPlanner.plan` + `S/TvLink.kt` (`canJoinWifiDirect = API≥29`) | **piège** : dès que le HELLO portait un groupe, la session prenait la route `Direct` (192.168.49.1) **sans** avoir rejoint le groupe ⇒ base injoignable, les envois de la file échouaient |
| Permission TV | `R/PlayerActivity.kt` | `NEARBY_WIFI_DEVICES` demandée seulement depuis le MENU « Wi-Fi Direct : activer » |
| Permission téléphone | `S/AndroidManifest.xml` | `NEARBY_WIFI_DEVICES` absente |

Les cahiers w7-09 / w7-21 (`RoutePolicy`, `DiscoveryPlanner`) n'ont jamais été réalisés : rien de ces classes n'existe dans le code. Ce travail en fait la part « masse + Wi-Fi Direct » (`C/link/`), sans le reste de W7. Le multivoie W8 reste abandonné (aucune agrégation de radios).

## 3. Réponses aux questions de conception

### 3.1 Mécanisme de jonction sans boîte de dialogue

| Mécanisme | Android | Permission (téléphone) | Boîte | Choix |
|---|---|---|---|---|
| `WifiP2pManager.connect(ch, WifiP2pConfig.Builder().setNetworkName(ssid).setPassphrase(pass).build())` (le téléphone connaît déjà le nom et le mot de passe : pas d'invitation WPS, donc aucune boîte, ni sur le téléphone ni sur la TV propriétaire du groupe) | **API 29+** | API 33+ : `NEARBY_WIFI_DEVICES` (`neverForLocation`) ; API 29-32 : `ACCESS_FINE_LOCATION` | aucune | **API 33+** (`WdJoin.method`) |
| `WifiNetworkSpecifier` (`requestNetwork` + `bindProcessToNetwork`) | API 29+ | `CHANGE_NETWORK_STATE` (normale) | **boîte système « Se connecter à l'appareil ? » à chaque groupe** (l'approbation retenue par Android porte sur le point d'accès ; le nom et l'adresse du groupe changent à chaque fois) | **API 29-32** seulement : la voie P2P y exigerait la localisation, refusée par conception (DESIGN-W7 § permissions) |
| Invitation WPS/PBC (`connect` vers une adresse d'appareil) | toutes | idem P2P | **boîte « Accepter l'invitation » sur la TV** (télécommande) | rejetée |
| Android 8-9 | — | — | — | pas de jonction : Bluetooth (`Why.PHONE_TOO_OLD`) |

- **Quand demander** : juste à temps, une fois. `BulkRoute.decide` rend `AskOnce(PERMISSION)` au premier gros envoi si l'app est devant ; `AutoWifiDirectAsker` (posé une fois dans `MainActivity`) lance la demande système ; refus ⇒ `KEY_PERM_ASKED` ⇒ plus jamais redemandée automatiquement, Bluetooth avec l'explication « Autorisation « Appareils à proximité » refusée… Réglages de l'app › Autorisations ». App pas devant ⇒ jamais de demande, Bluetooth (`Why.BACKGROUND`). La même autorisation est ajoutée à la demande Bluetooth (`S/BtPermission.kt`) : même groupe « Appareils à proximité », une seule réponse.
- **Wi-Fi du téléphone éteint** : le P2P est éteint aussi et une app ne peut pas l'allumer (API 29+). `AskOnce(PHONE_WIFI)` ouvre une fois le panneau Wi-Fi (`Settings.Panel.ACTION_WIFI`) ; ensuite Bluetooth avec explication.
- **S21+ (Android 14, One UI)** : chemin `P2P_CONNECT`. Si CastBridge a déjà « Appareils à proximité » (accordée pour le Bluetooth), Android devrait accorder `NEARBY_WIFI_DEVICES` **sans boîte** à la demande (même groupe de permissions) : **à confirmer sur l'appareil** (étape H-2). One UI ajoute parfois un bandeau « Wi-Fi Direct connecté » : à observer.
- **TV GaiaOS (Android 14, Amlogic, puce Wi-Fi USB)** : la TV doit avoir `FEATURE_WIFI_DIRECT` (dépend du pilote de la puce USB : **inconnu**, étape H-1) et `NEARBY_WIFI_DEVICES` (désormais demandée au premier lancement avec le Bluetooth, `R/PlayerActivity.kt`). Le Wi-Fi de la TV doit être **allumé** (pas forcément connecté) : sinon `wd.err=wifi_off` et la phrase « Le Wi-Fi de la TV est éteint ». **La puce Wi-Fi partage le bus USB avec la clé** : une copie vers la clé USB par Wi-Fi Direct fait passer les octets deux fois sur ce bus ; attendre un débit plus faible qu'en mémoire interne (R-06, R-11 l'ont déjà observé pour le LAN), à mesurer (étape H-6).

### 3.2 Comment voyagent les identifiants

- **Seulement par CBTN sur le lien RFCOMM appairé** (`listenUsingRfcommWithServiceRecord` : chiffré et authentifié, donc appareil appairé, `R/BtServer.kt`). Le téléphone de confiance envoie `NO_PIN` : son jeton ne voyage pas ; la TV le reconnaît par l'adresse Bluetooth de la socket (`trusted(peer)` = registre **et** appairage, `R/TvService.btTrusted`). Sinon il faut le PIN (chemin manuel inchangé). Contrôle **avant** tout appel : `C/tv/BtProtocol.kt` `pinProblem` puis `negotiateFlags(peer, flags)` ; test `anUntrustedPeerWithoutThePinGetsNoCredentials`.
- **Jamais en clair dans l'air** : le Wi-Fi Direct est WPA2-PSK avec ce mot de passe ; le mot de passe ne passe que par RFCOMM chiffré.
- **Frais par groupe** : `WifiDirect.groupPassphrase()` = 16 caractères tirés par `SecureRandom` dans un alphabet de 56 (≈ 93 bits) ; nom de réseau aléatoire par groupe (`DIRECT-CB-xxxxxx`) pour que deux TV voisines ne se confondent pas.
- **Jamais persisté** : en mémoire (`WifiDirectGroup.active`) le temps du groupe ; l'ancienne préférence `wd_pass` est effacée au démarrage ; côté téléphone il ne vit que dans l'effet `WdClient.Effect.Join` (aucun état ne le garde, test `joinSuccessThenIdleRelease`).
- **Jamais journalisé** : `LinkInfo.toString`, `WdClient.Event.Creds.toString`, `Effect.Join.toString` masquent le mot de passe (test `linkInfoNeverPrintsThePassphrase…`) ; aucun `Log` ne l'écrit. Le groupe automatique n'affiche rien sur l'écran de la TV (le groupe du MENU continue d'afficher son mot de passe : il se tape à la main).
- **HELLO** : le mot de passe d'un groupe automatique n'est **jamais** dans la réponse HELLO (`linkInfo(null, 0)`), seulement dans la réponse CBTN au téléphone qui l'a demandé.
- **Durée de vie** : le téléphone quitte le groupe après **30 s de file vide** (`WdClient.IDLE_RELEASE_MS`) et envoie CBTN `WD_RELEASE` ; la TV supprime le groupe automatique dès qu'il est rendu, qu'aucun client n'y est plus (après 45 s de délai de jonction), ou après 30 s sans usage si le nombre de clients est inconnu, ou 10 min si un téléphone associé est muet (`WdGroupLease`, contrôlé toutes les 5 s par `transferTick`). Jamais pendant une réception. Groupe non persistant (`enablePersistentMode(false)`).

### 3.3 Une seule radio sur le téléphone (STA + P2P)

- **Ce qui est connaissable** : rien de public ne dit si la puce fait STA + P2P en même temps (`WifiManager.isStaApConcurrencySupported` concerne le point d'accès, pas le P2P ; `isStaConcurrencyForLocalOnlyConnectionsSupported`, API 31, concerne le spécificateur). En pratique les téléphones récents (dont le S21+) gardent leur Wi-Fi et rejoignent le groupe en concurrence, éventuellement en multicanal (débit partagé dans le temps si les canaux diffèrent). Sur API 29-30, une connexion par spécificateur **remplace** le Wi-Fi principal le temps de l'envoi (les autres apps passent par les données mobiles).
- **Règle appliquée** (`BulkRoute.decide`, testée) : **jamais à la place d'une route LAN qui répond** (`lanAlive && !isolated` ⇒ `UseLan`, avant toute autre règle) ; Wi-Fi Direct seulement quand la route de la session n'est pas un LAN qui répond (aucun LAN commun, ou `isolated`). Le téléphone envoie alors `WD_LAN_UNREACHABLE` : la TV, même connectée à son Wi-Fi, accepte de créer le groupe (son Wi-Fi ne sert pas à ce téléphone) ; sans ce drapeau la règle d'avant reste (MENU ou TV sans réseau). **Risque côté TV** : sur une puce sans concurrence STA + P2P, créer le groupe peut couper le Wi-Fi de la TV le temps de l'envoi (autres téléphones du LAN gênés) ; à observer (étape H-5).
- Le multivoie (additionner les radios) est abandonné (DESIGN-W8) : rien de tel n'est fait.

### 3.4 Après la jonction : l'adresse de la TV

- Propriétaire du groupe = la TV, adresse **192.168.49.1** (serveur DHCP P2P d'Android ; `C/tv/WifiDirect.GROUP_OWNER_IP`, `R/WifiDirectGroup.kt` le dit sur son écran). Le téléphone prend `WifiP2pInfo.groupOwnerAddress` (`requestConnectionInfo`, interrogé chaque seconde jusqu'à `groupFormed && !isGroupOwner`), sinon l'adresse dite par la TV (`wd.ip`), sinon 192.168.49.1 ; littéral IPv4 privé seulement (`WdAddress.base`, testé).
- Sonde `GET /api/hello` (réponse contenant `castbridge-tv`), 3 essais (`WdClient.PROBE_TRIES`), puis la file passe `http://192.168.49.1:8765` à `UploadService` comme hôte : le **même** envoi qu'en Wi-Fi (reprise, transfert rapide, chemin ordonné R-08 pour « Copier et lire », preuves de déplacement R-12, rangement R-13, contrôle des doublons R-12). Le contrôle (HELLO, jetons) continue par Bluetooth.
- Routage : en P2P, l'interface du groupe est un réseau local d'Android ; les sockets de l'app atteignent 192.168.49.0/24 sans `bindProcessToNetwork` (**à confirmer**, étape H-3). En spécificateur (API 29-32), l'app est liée au réseau du groupe le temps de l'envoi, puis déliée.
- Le groupe tombe pendant la copie (`requestConnectionInfo` toutes les 2 s, ou `onLost`) : échec compté (backoff), la file remet le fichier à sa place (`BulkRoute.rerouteAfterLoss`, au plus 2 fois) ; la décision suivante re-joint, ou passe par le Bluetooth après 3 échecs. Reprise : sur le même chemin Wi-Fi, l'envoi reprend où il en était (session `xfer`/`.part`) ; si la décision passe au Bluetooth CBT1, une copie commencée en « transfert rapide » (`.cbx`) **repart de zéro** (formats différents, limite connue).

### 3.5 La décision, pure, dans `C/link/`

- `BulkRoute.decide(Facts)` → `UseLan` / `UseWd(start)` / `UseBt(why)` / `Wait(why)` / `AskOnce(what)` / `NoRoute`. Ordre : LAN vivant ⇒ LAN ; groupe déjà rejoint ⇒ WD (même petit fichier) ; pas de Bluetooth ⇒ rien ; TV d'essai ⇒ BT ; < 5 Mo ⇒ BT (`MIN_WD_BYTES`) ; réglage éteint ⇒ BT ; Android < 10 ⇒ BT ; Wi-Fi de la TV éteint / TV sans WD (`wd.cap=0`) ⇒ BT ; pause d'échecs ⇒ BT ; Wi-Fi du téléphone éteint ⇒ demander une fois ; permission : accordée/inutile ⇒ WD, en cours ⇒ attendre, jamais demandée ⇒ demander (app devant) sinon BT, refusée ⇒ BT.
- `WdBackoff` : 3 échecs en moins de 10 min ⇒ Bluetooth 10 min, puis un nouvel essai ; un succès efface ; des échecs espacés de plus de 10 min ne s'additionnent pas. Jamais de boucle.
- `BulkLine.of` : vert « Par Wi-Fi Direct (automatique) » (groupe monté), vert « Par le Wi-Fi (réseau commun) », vert « Par Bluetooth » (petit fichier : pas une dégradation), orange « Bluetooth seulement : lent » (préparation, échec, refus) avec la cause, rouge « TV injoignable : ni Wi-Fi ni Bluetooth ».
- `WdClient` : automate pur (horloge des événements) ; `WdGroupLease` : bail du groupe de la TV ; `WdJoin` : mécanisme et permission par version.
- Protocole (`C/tv/BtProtocol.kt`, additif, compatible dans les deux sens) : drapeaux CBTN `WD_RELEASE = 2`, `WD_LAN_UNREACHABLE = 4` (une ancienne TV les ignore) ; `LinkInfo` `wd.cap=1|0` et `wd.err=<mot connu>` (`WifiDirect.Err` : `wifi_off`, `unsupported`, `permission`, `trial`, `policy`, `failed`) ; `negotiateFlags(peer, flags)` côté TV ; `mayStartWifiDirect(…, phoneCannotReachLan, trial)` : jamais en version d'essai.

### 3.6 Le câblage (mince)

- **Téléphone** : `S/TransferQueue.kt` `runOneInner` (lien de confiance) demande `AutoWifiDirect.bulkBase(session, taille)` quand la route n'est pas un LAN ; une adresse ⇒ `UploadService` sur 192.168.49.1 avec tous les drapeaux de l'élément ; sinon la route de la session (passerelle Bluetooth = mux, ou CBT1). `BtUploadService.start(…, allowWifiDirect = false)` depuis la file : le service Bluetooth ne monte plus de groupe ni de boîte système de lui-même (le canal Bluetooth manuel de `TvHub` garde l'ancien comportement). `S/AutoWifiDirect.kt` : exécutant de `WdClient` (CBTN par `AndroidBtTransport`, `WifiP2pManager` ou spécificateur, sonde, départ, `WD_RELEASE`). `S/TvLink.kt` : `canJoinWifiDirect = { false }` pour la route de **contrôle** (corrige le piège du § 2). Réglage dans `S/ConnectScreens.kt` (section Connexion), ligne d'état dans la carte de file (`S/TvHome.kt`), manifeste : `NEARBY_WIFI_DEVICES` `neverForLocation`.
- **TV** : `R/TvService.kt` `linkInfo(peer, flags)` (CBTN) et `wdLeaseCheck()` ; jeton HTTP ⇒ `wd.touch()` ; `R/WifiDirectGroup.kt` : groupe automatique (nom aléatoire, rien d'affiché), mot de passe frais, cause d'échec, nombre de clients ; `R/BtServer.kt` passe le pair et les drapeaux ; `NEARBY_WIFI_DEVICES` demandée au premier lancement (Android 13+).
- **Chemin code PIN** (TV nommée, sans lien de confiance) : inchangé (il passe par la découverte LAN ; pas de Bluetooth de confiance pour demander un groupe).

## 4. Tests

- `CT/link/AutoWifiDirectTest.kt` (27 tests) : table de décision (LAN jamais remplacé, seuil, réglage, essai, Android ancien, TV sans WD, Wi-Fi éteint des deux côtés, permission juste à temps / attente / refus / arrière-plan), backoff (3 échecs ⇒ 10 min, fenêtre, succès), re-routage borné, ligne d'état (niveaux), mécanisme par version, adresse du groupe, automate en temps simulé (succès puis départ après 30 s de file vide, refus de jonction, délai, identifiants refusés, TV sans groupe avec sa cause, délai de la demande, sonde muette ×3, groupe perdu en cours puis re-jonction, jamais deux demandes, rendu depuis tout état, backoff nourri par l'automate), bail du groupe TV (groupe du MENU intact, délai de jonction, téléphone parti, rendu, jamais pendant une réception, client muet 10 min), politique TV (LAN injoignable, essai), secrets (16 caractères, frais, nom unique, jamais dans `toString`), CBTN réel sur flux en mémoire (drapeaux et pair transmis, pair non autorisé sans PIN ⇒ rien, ancienne négociation booléenne).
- **Rouge d'abord** : 17/27 en échec **par assertion** contre des ébauches qui compilaient (`decide` rendant toujours `UseBt(SMALL)`, `reduce` immobile, `shouldRemove` toujours faux), puis 27/27 verts ; `BtLinkTest` et `WifiDirectTest` (existants) verts.
- `:core:test` complet (3 137 tests, 0 échec), `:sender:compileDebugKotlin`, `:receiver:compileDebugKotlin` : verts (§ 6).

## 5. À confirmer sur le S21+ et la TV réels (le JVM ne le peut pas)

Préconditions : TV GaiaOS avec CastBridge-TV de cette branche (build verrouillé), S21+ avec CastBridge de cette branche, lien de confiance établi, **aucun réseau commun** (TV sans Wi-Fi connecté mais **Wi-Fi allumé** ; téléphone sur données mobiles ou sur un autre Wi-Fi), Bluetooth des deux côtés.

1. **H-1 TV capable** : `adb shell pm list features | grep wifi.direct` sur la TV ; après le premier lancement, `dumpsys package castbridge… | grep NEARBY_WIFI` = granted. Attendu : `android.hardware.wifi.direct` présent. Sinon : la ligne doit dire « Cette TV ne propose pas le Wi-Fi Direct » et le fichier part par Bluetooth.
2. **H-2 permission S21+** : CastBridge ayant déjà « Appareils à proximité », lancer « Copier sur la TV » d'une vidéo de 200 Mo. Noter s'il y a une boîte de permission (attendu : aucune, sinon une seule fois). Refaire : jamais de boîte.
3. **H-3 transparence** : pendant la copie, aucune boîte sur le téléphone ni sur la TV ; la carte passe d'orange « Wi-Fi Direct en préparation… » à vert « Par Wi-Fi Direct (automatique) » en **≤ 15 s** ; `logcat -s AutoWifiDirect` montre `Requesting -> Joining -> Probing -> Up` ; débit affiché **≥ 2 Mo/s** (attendu bien plus) ; fichier complet et lu sur la TV. Confirme aussi le routage vers 192.168.49.1 sans liaison du processus.
4. **H-4 « Copier et lire »** du même type de fichier : la lecture démarre sur la TV pendant la copie (R-08) par Wi-Fi Direct.
5. **H-5 Wi-Fi conservé** : S21+ connecté à un Wi-Fi où la TV n'est pas : il reste connecté à son Wi-Fi pendant la copie (Internet du téléphone toujours là) ; TV connectée à une box qui isole ses clients : noter si la TV perd son Wi-Fi pendant le groupe.
6. **H-6 clé USB** : même copie vers la clé USB de la TV : noter le débit (bus USB partagé avec la puce Wi-Fi).
7. **H-7 fin** : 30 s après la dernière copie, le groupe disparaît (`logcat` TV, l'icône Wi-Fi Direct de la barre d'état s'éteint ; le téléphone n'a plus d'interface `p2p-…` : `adb shell ip addr`).
8. **H-8 refus** : désinstaller/réinstaller CastBridge, refuser « Appareils à proximité » : la copie part par Bluetooth avec « Autorisation « Appareils à proximité » refusée… » ; aucune nouvelle demande aux copies suivantes.
9. **H-9 perte** : couper le Wi-Fi de la TV à 30 % : la file reprend (re-jonction ou Bluetooth), jamais de boucle ; après 3 échecs, « nouvel essai dans 10 minutes ».
10. **H-10 Wi-Fi éteint** : Wi-Fi du téléphone éteint : le panneau Wi-Fi s'ouvre une fois ; Wi-Fi de la TV éteint : « Le Wi-Fi de la TV est éteint… », envoi par Bluetooth.
11. **H-11 secrets** : `logcat` complet des deux appareils pendant H-3 : aucun mot de passe de 16 caractères, aucun `wd.pass`.
12. **H-12 (non bloquant) WPS par bouton** : sur une TV où la jonction par mot de passe échouerait, vérifier qu'un WPS « bouton » (PBC) côté propriétaire du groupe n'est possible **que** par l'acceptation à l'écran de la TV (boîte « Invitation Wi-Fi Direct », touche OK de la télécommande) : ce n'est donc pas une voie « sans action » ; elle reste écartée.
13. **H-13 Android 10-12 (si un tel téléphone est disponible)** : téléphone connecté à un Wi-Fi sans la TV : la copie commence par Bluetooth le temps des deux sondes (≈ 3 s), puis la boîte « Se connecter à l'appareil ? » ; pendant la copie, une page web dans le navigateur **et** dans CastBridge (Réglages › À propos, contact serveur) se charge encore (seuls les sockets vers 192.168.49.x passent par le groupe) ; depuis « Ouvrir avec » (app pas à l'écran) : Bluetooth tout de suite, aucune attente.

## 6. Commandes et résultats

```
cd android
ANDROID_HOME=$HOME/Library/Android/sdk bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline … :core:test --tests 'castbridge.core.link.AutoWifiDirectTest'   # rouge : 27 tests, 17 en échec (assertions)
… :core:test --tests 'castbridge.core.link.AutoWifiDirectTest' --tests 'castbridge.core.BtLinkTest' --tests 'castbridge.core.WifiDirectTest'           # vert
… :sender:compileDebugKotlin :receiver:compileDebugKotlin                                                                                          # BUILD SUCCESSFUL
… :core:test                                                                                                                                        # complet : BUILD SUCCESSFUL, 3 137 tests, 0 échec (365 classes)
```

## 8. Audit Opus (MERGEABLE AVEC CORRECTIFS) et correctifs (3ᵉ commit)

Sécurité des identifiants jugée correcte (`pinProblem` avant `negotiateFlags`, rien dans le HELLO ni les journaux). Correctifs :

| Point | Défaut | Correctif | Test (rouge par assertion avant, vert après) |
|---|---|---|---|
| I-1 | le HELLO listait toutes les adresses site-local, donc 192.168.49.1 dès que le groupe existait ; `LinkPlanner.plan` en faisait une `Route.Lan` ⇒ `lanAlive` vrai, « Par le Wi-Fi (réseau commun) », la file sautait le Wi-Fi Direct, et la route de contrôle mourait avec le groupe | `C/link/WdHost.kt` `HelloIps.lanOnly` (pur) : jamais 192.168.49.x ni une adresse du groupe courant ; appliqué par la TV (`TvWdHost.answer`) ET par le téléphone (`LinkPlanner.plan`, `BulkRoute.lanRoute` pour `lanAlive` et la file) | `groupAddressesAreNeverLanAddresses`, `aPhoneInsideTheGroupNeverTurnsTheControlRouteIntoLan49`, `theTvNeverAnnouncesItsGroupAddressInHello` |
| I-2 | le bail comptait `bt.busy`, vrai aussi avec la télécommande CBTR ouverte ⇒ groupe jamais supprimé | `LeaseBusy.count` : seules les réceptions HTTP (les seules qui passent par le groupe) ; le jeton HTTP ne « touche » plus le groupe (il pouvait venir du LAN) | `anOpenRemoteNeverKeepsTheGroupAlive` |
| I-3 | Android 10-12 : attente ~80 s depuis « Ouvrir avec » ; `bindProcessToNetwork` coupait tout l'Internet de l'app ; un faux négatif de la sonde LAN pouvait faire quitter le Wi-Fi du téléphone | `decide` : chemin `NETWORK_SPECIFIER` et app pas à l'écran ⇒ `UseBt(BACKGROUND)` ; Wi-Fi connecté ⇒ deux sondes LAN négatives espacées de 3 s (`BulkRoute.lanConfirmedDead`) sinon `UseBt(LAN_UNCONFIRMED)` ; plus de liaison du processus : `C/net/BoundRoute.kt` ne lie que les sockets vers 192.168.49.x (`Network.openConnection`/`bindSocket`, via `TvClient`, `TransferClient`, `HttpConn.tcp`) | `theSystemDialogPathNeedsTheAppVisible`, `leavingAWifiNeedsTwoSpacedNegativeLanProbes`, `onlyTheUploadSocketIsBoundToTheGroupNetwork` |
| mineur | WD_RELEASE d'un autre téléphone supprimait le groupe partagé | bail PAR téléphone (`TvWdHost`, `WdGroupLease.Facts.holders`) : le groupe part quand plus aucun bail n'est actif ; un tiers qui n'a rien demandé ne fait rien | `aReleaseFromAnotherPhoneNeverRemovesTheSharedGroup` |
| mineur | deux CBTN simultanés (un fil par lien) lançaient deux `start()` | création sérialisée (verrou) : le second reçoit le MÊME groupe | `twoSimultaneousRequestsGetTheSameGroup` |
| mineur | TV d'essai : `claimed()` avant le contrôle d'essai | essai contrôlé avant tout bail | `neverOnATrialEvenWhenAGroupExists` |
| mineur | le groupe du MENU changeait de mot de passe à chaque démarrage | le groupe du MENU garde son mot de passe enregistré (`wd_pass`) ; seul le groupe AUTOMATIQUE est éphémère (jamais écrit) | `theOwnersMenuGroupIsNeverRemovedByTheLease` (bail), relu dans `R/WifiDirectGroup.kt` |
| mineur | repli de 10 min sur l'horloge murale, fenêtre comptée depuis le dernier échec | horloge monotone (`SystemClock.elapsedRealtime`, aussi pour `lostSince`), fenêtre depuis le PREMIER échec | `backoffWindowCountsFromTheFirstFailure` |
| mineur | code mort `isolated` côté téléphone | supprimé | — |
| doc | WPS par bouton | possible seulement par l'acceptation à l'écran de la TV : H-12 | — |

Résultat : `AutoWifiDirectAuditTest` 11/14 rouges par assertion contre des ébauches reproduisant le comportement d'avant (adresse du groupe acceptée, télécommande comptée, release global, création non sérialisée, essai après le bail, pas de règle Android 10-12, une sonde suffit, liaison de tout le processus, fenêtre depuis le dernier échec), puis 41/41 verts dans `castbridge.core.link.*` ; câblage TV réduit à `TvWdHost` (testé avec un faux pilote de groupe). Le côté TV de la décision n'est plus dans `R/TvService.kt` mais dans `C/link/WdHost.kt`.

`:core:test` complet après correctifs : 3 150 tests, 2 échecs : (1) `TestWatchdogGuardTest` (mon `await()` sans borne dans `twoSimultaneousRequestsGetTheSameGroup`, corrigé : `await(5 s)`) ; (2) `TvFoldersTest.theFolderFollowsRenameDeleteAndTheBin` (`SocketException: Unexpected end of file` dans `AgentRig.call`, client HTTP du test lui-même, sans lien avec ce chantier ; vert au premier essai complet). Relance ciblée : `TestWatchdogGuardTest`, `castbridge.core.library.agent.*` (dont `TvFoldersTest`), `castbridge.core.link.*` : vert. `:sender:compileDebugKotlin`, `:receiver:compileDebugKotlin` : verts.

## 7. Limites connues

- API 29-32 : une boîte système par groupe (pas de voie sans boîte sans la localisation).
- Copie commencée en « transfert rapide » par Wi-Fi Direct puis basculée sur CBT1 : repart de zéro.
- Le partage depuis une autre activité que `MainActivity` (ex. « Ouvrir avec ») ne peut pas présenter la demande d'autorisation : après 30 s d'attente, Bluetooth ; la demande se fera au prochain envoi depuis l'app.
- La TV ne sait pas si sa puce fait STA + P2P en concurrence : risque de coupure de son Wi-Fi le temps du groupe (§ 3.3).
- Isolation des clients : déduite simplement (même /24 annoncé par la TV, pas de réponse) pour l'explication ; la décision ne dépend que de « route LAN qui répond ou non ».
