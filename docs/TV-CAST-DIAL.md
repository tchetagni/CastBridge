# CastBridge-TV et YouTube : diffusion par DIAL

> Demande du propriétaire : « faire que l'app TV se comporte comme un Chromecast pour que l'app YouTube puisse y partager ses vidéos ». Ce document dit ce qui est livré, ce qui est impossible, comment tester. Aucun secret ici.

## Ce que ça fait

CastBridge-TV annonce la TV sur le réseau local comme **récepteur DIAL** (DIAL 2.1, Netflix/Google). L'application YouTube du téléphone, qui utilise DIAL pour les téléviseurs non Chromecast, la liste alors comme cible de diffusion (nom « CastBridge-TV » suivi de 4 caractères stables, par exemple « CastBridge-TV 3F2A »). Quand on choisit la TV, YouTube envoie un lancement ; CastBridge-TV ouvre l'application **YouTube TV déjà installée** (`com.google.android.youtube.tv`) avec les données d'appairage ; YouTube TV se connecte ensuite elle-même à l'application du téléphone par Internet, et la lecture est celle de YouTube TV. CastBridge-TV ne lit ni ne voit la vidéo.

Réglage : menu de la TV, « Diffusion YouTube vers cette TV (DIAL) » (clé `cast.dial`, **actif par défaut**). Le désactiver arrête le répondeur et le serveur sans redémarrer le service.

## Ce que ça ne peut pas faire

- **Pas de Google Cast** : le vrai protocole (CastV2, authentification de l'appareil) exige des certificats Google. Pas d'envoi d'un onglet Chrome, pas de recopie d'écran Android, pas d'application qui exige un récepteur Cast certifié.
- Fonctionne seulement avec les applications qui **gèrent DIAL** et dont l'application TV est installée. Ici : la seule entrée est `YouTube` (table fermée).
- Si YouTube TV n'est pas installée sur la TV : la TV répond 404 pour `/apps/YouTube` (le téléphone ne la liste donc pas) et journalise « Application YouTube TV introuvable sur cette TV ».
- Arrêt (`DELETE`) : YouTube TV ne peut pas être fermée par CastBridge-TV (pas ce droit). L'arrêt renvoie à l'accueil de CastBridge-TV. L'état annoncé « running » signifie « lancé par DIAL et non arrêté depuis » : l'état réel de YouTube TV n'est pas lisible.
- Lancement depuis l'arrière-plan : Android 10+ limite le démarrage d'une activité par un service ; l'autorisation « afficher par-dessus les autres apps » de CastBridge-TV (déjà demandée pour la lecture à distance) aide. À confirmer sur la TV réelle.

## Préalables

- Le téléphone et la TV sont sur le **même réseau Wi-Fi/LAN**, et **tous deux ont Internet** (YouTube TV et l'application du téléphone se rejoignent par les serveurs de YouTube).
- Un téléphone relié seulement au groupe Wi-Fi Direct de la TV n'a pas Internet : YouTube ne peut pas diffuser.
- Repli manuel, toujours possible : YouTube TV, Réglages, « Associer avec un code TV » ; saisir le code dans YouTube sur le téléphone (Réglages, Regarder sur la télévision, Saisir le code).

## Fonctionnement technique

Code pur : `android/core/src/main/kotlin/castbridge/core/cast/dial/` (`DialRules`, `DialRouter`, `DialHttpServer`, `Ssdp`/`SsdpServer`). Câblage Android : `android/receiver/.../DialHost.kt`, démarré par `TvService` (`applyDial()`).

1. **SSDP** : écoute UDP 239.255.255.250:1900, répond aux `M-SEARCH` (`MAN: "ssdp:discover"`) dont le `ST` est `urn:dial-multiscreen-org:service:dial:1`, `ssdp:all` ou `upnp:rootdevice`, après un délai aléatoire dans `[0, MX[` (MX borné à 5 s) ; réponse avec `LOCATION`, `ST`, `USN`, `CACHE-CONTROL: max-age=1800`. Annonces `NOTIFY ssdp:alive` toutes les 15 min et `ssdp:byebye` à l'arrêt, pour les trois cibles (rootdevice, uuid, DIAL). Un `MulticastLock` est tenu tant que DIAL tourne.
2. **HTTP** (port fixe 30765, sinon un port libre ; jamais 8008/8009/8765) : `GET /dd.xml` (description UPnP, en-tête `Application-URL`, nom, fabricant, modèle, UDN stable par installation) ; `GET /apps/YouTube` (état `running`/`stopped`) ; `POST /apps/YouTube` (lancement, `201` + `Location: …/apps/YouTube/run`) ; `GET`/`DELETE /apps/YouTube/run`. Codes : 400, 403, 404, 405, 413, 415, 429, 431, 501, 503.
3. **Lancement** : le corps du formulaire (`pairingCode`, `theme`, `v`, `t`…) devient la requête de l'URL fixe `https://www.youtube.com/tv?<corps>`, ouverte par un Intent `VIEW` avec le paquet explicite `com.google.android.youtube.tv` et `FLAG_ACTIVITY_NEW_TASK`. C'est la méthode connue des serveurs DIAL ; **seul un essai sur la TV réelle confirme** que YouTube TV l'accepte.

## Sécurité (règles obligatoires, testées)

- Seules les sources du réseau local sont servies : 127/8, 10/8, 172.16/12, 192.168/16, 169.254/16, ::1, fc00::/7, fe80::/10. Les autres sont **ignorées sans réponse** (SSDP et HTTP).
- `Host` doit être exactement `ip:port` d'une adresse de la TV (anti DNS-rebinding), un seul en-tête.
- **Toute** requête portant un `Origin` autre que `package:com.google.android.youtube` est refusée (403) : un navigateur ne peut pas lancer d'application.
- Corps ≤ 4 Ko (413 avant lecture), paramètres ≤ 1 Ko, jeu de caractères strict (`A-Za-z0-9 _ . ~ * , + -` et `%XX`, clés `A-Za-z0-9_.-`), pas de `\r\n` ni de `%0d %0a %00`, pas de `:` ni `/` (aucune injection de schéma ou de chemin), ASCII seulement ; `Content-Type` `text/plain` ou `application/x-www-form-urlencoded` (415 sinon).
- 6 lancements par minute au plus (429 + `Retry-After`).
- Table des applications fermée : `YouTube` seulement (sensible à la casse) ; tout autre nom, 404.
- Journal : jamais le code d'appairage ; seuls les noms des paramètres (`pairingCode=***`).
- Connexions : une requête par connexion, délai de lecture 5 s, 4 fils au plus, en-têtes ≤ 8 Ko.

## Dépannage

- **La TV n'apparaît pas dans YouTube** : même réseau ? Beaucoup de routeurs filtrent le multicast (IGMP snooping, « isolation AP / isolation des clients ») : désactiver l'isolation du Wi-Fi invité ou brancher la TV et le téléphone sur le même SSID. Un VPN actif sur le téléphone masque la TV. Ouvrir YouTube après avoir allumé le Wi-Fi.
- **Veille et batterie de la TV** : certaines TV coupent le Wi-Fi en veille profonde ; le `MulticastLock` n'est tenu que quand le service tourne. Exclure CastBridge-TV des économies d'énergie (déjà demandé au premier démarrage).
- **La TV apparaît mais rien ne se lance** : YouTube TV installée ? (`adb shell pm list packages | grep youtube.tv`). Regarder `adb logcat -s CbDial`. Vérifier la permission « afficher par-dessus les autres apps ».
- **Test depuis un Mac** sur le même LAN : `curl -i http://IP_TV:30765/dd.xml` (doit répondre 200 avec `Application-URL`) ; une requête avec `-H 'Origin: http://x'` doit répondre 403.

## Test du propriétaire (TV réelle + téléphone)

1. Menu de la TV : vérifier « Diffusion YouTube vers cette TV (DIAL) : oui ».
2. TV et téléphone sur le même Wi-Fi, avec Internet ; YouTube TV installée sur la TV.
3. Sur le téléphone, ouvrir YouTube, lancer une vidéo, toucher l'icône de diffusion : « CastBridge-TV xxxx » doit être listée.
4. La choisir : YouTube TV s'ouvre sur la TV et joue la vidéo ; changer de vidéo depuis le téléphone ; déconnecter : retour à CastBridge-TV.
5. Menu de la TV : désactiver ; l'icône de diffusion ne doit plus lister la TV (attendre 1 min).
6. Si l'étape 3 ou 4 échoue : noter laquelle, et le message de `adb logcat -s CbDial` ; utiliser le repli manuel (code TV).

Parcours critique : P-61 dans `docs/test-plans/PARCOURS-CRITIQUES.md`.
