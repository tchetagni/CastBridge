# CastBridge-TV Web (LG webOS) : prototype 0.1.0-beta

> **Statut (2026-10-17)** : prototype livré, **jamais essayé sur une TV LG réelle**. Ce qui est vérifié : les tests automatiques, le service sur un Mac (curl, Bonjour de macOS) et l'application dans Chrome (sans interface) avec le code du service, une vraie vidéo et un téléphone simulé : 17 contrôles de bout en bout. Ce qui reste à jouer sur une TV LG est listé au § 6.4.

## 1. Pourquoi webOS

Les TV LG (webOS) ne sont pas des TV Android : l'APK de CastBridge-TV ne s'y installe pas. webOS exécute des **applications web** (HTML, CSS, JavaScript) accompagnées, si besoin, d'un **service Node.js**. CastBridge-TV Web utilise les deux pour que le téléphone CastBridge voie la TV LG comme une CastBridge-TV minimale : le téléphone la découvre sur le Wi-Fi, y envoie un lien, et pilote la lecture.

Le code est dans `webos/castbridge-tv-web/`, hors Gradle. Les modules Android (`android/`) ne sont pas touchés : le téléphone n'a **aucune modification** à subir.

## 2. Périmètre de la 0.1.0-beta

| Dans la 0.1.0 | Hors périmètre (réponse 404 du service) |
|---|---|
| Découverte mDNS `_castbridge._tcp` (le téléphone la liste) | Copie de fichiers, stockage, bibliothèque, miniatures |
| Lecture d'un lien http(s) envoyé par le téléphone (`POST /api/playurl`) : « Lire en direct », rien n'est copié | Bluetooth, USB, SSH, installation d'APK |
| Contrôle : pause, reprise, arrêt, position | Activation, verrouillage, édition d'essai, code PIN |
| État du lecteur (`GET /api/info`) : état, titre, position, durée | Quiz, jeux, Apprendre, téléchargements, télécommande |
| Accueil sur la TV (nom, adresse, attente), toast au début d'une lecture, télécommande de la TV | Volume (`/api/volume`, `/api/sysinfo`), listes de lecture, sous-titres, photos |

Le téléphone affichera quand même, pour cette TV, des boutons que la 0.1.0 ne sert pas (copier sur la TV, bibliothèque…) : ils échouent par un 404, sans autre effet. Seule « Lire en direct » est prévue.

## 3. Architecture : une application et un service

```
 Téléphone CastBridge ── mDNS _castbridge._tcp ──▶  service (Node.js, services.json)
                      ── HTTP 8765 /api/… ───────▶     │  ▲
                                                       │  │ Luna : state (l'app pousse l'état, reçoit les commandes en attente)
                                       events (abonnement : les commandes)
                                                       ▼  │
                                                  application web (index.html, <video> plein écran)
```

- **Service** (`webos/castbridge-tv-web/service/`, id `com.sticm.castbridge.tv.service`) : répondeur mDNS pur Node (`dgram`), serveur HTTP sur le port 8765, pont Luna avec l'app. Écrit pour Node 8 (pas de `??`, de `?.`, de modules ES ; une garde des tests le vérifie), sans aucun paquet npm. `require('webos-service')` est chargé dans un `try/catch` : hors TV, le service tourne seul (`--standalone`).
- **Application** (`index.html`, `app.css`, `app.js`, id `com.sticm.castbridge.tv`) : accueil et lecteur `<video>`, JavaScript ES5 (lisible par les TV webOS anciennes), sans bibliothèque. Elle appelle Luna par `webOS.service.request` si `webOSTV.js` est présent, sinon par `PalmServiceBridge` (toujours disponible dans une application web webOS), sinon elle s'affiche en « mode navigateur ».

Logique pure, testable sans réseau, dans `service/lib/` : `mdns.js` (paquets), `routes.js` (routes HTTP), `player.js` (état du lecteur), `bridge.js` (commandes, présence, état rapporté), `simulator.js` (lecteur simulé), `netutil.js` (adresses), `args.js`. Entrées/sorties à part : `http.js`, `responder.js`, `service.js`.

| Fichier | Rôle |
|---|---|
| `appinfo.json` | id, version `0.1.0` (webOS exige `X.Y.Z`), titre, icônes, fond sombre, résolution 1920x1080 |
| `icon.png` (80x80), `largeIcon.png` (130x130) | couleur unie, générées par `node tools/webos/make-icons.js` |
| `service/services.json`, `service/package.json` | déclaration du service et de ses méthodes Luna (`events`, `state`, `status`) |
| `tests/run.js`, `tests/app.test.js` | 47 tests sans dépendance |
| `tools/webos/package.sh` | tests, puis `.ipk` par `ares-package` si le CLI est installé, sinon la marche à suivre |

## 4. Contrat compatible avec le téléphone

Source de vérité : le code de la CastBridge-TV Android (`android/core/src/main/kotlin/castbridge/core/tv/` : `TvClient.kt`, `TvInfo.kt`, `ReceiverServer.kt`) et, côté téléphone, `TvDiscovery.kt` (découverte) et `CastSession.kt` (lecture en direct, suivi de l'état).

### 4.1 Annonce mDNS

| Élément | Valeur |
|---|---|
| Type | `_castbridge._tcp` |
| Nom d'instance (nom affiché par le téléphone) | `CastBridge-TV Web` (`--name` en mode autonome) |
| SRV | port 8765, hôte `castbridge-tv-web-<6 caractères hexadécimaux de l'adresse MAC>.local` |
| TXT | `role=receiver`, `v=0.2` ; jamais `locked=1` (pas d'activation ici) |
| Enregistrements | PTR et TXT 4500 s, SRV et A 120 s (IPv4 seulement) |

Annonce au démarrage (à 0, 1 et 3 s), puis chaque minute ; surveillance des adresses toutes les 20 s (Wi-Fi ou câble qui change : nouvelle adhésion au groupe et nouvelle annonce) ; « au revoir » (TTL 0) à l'arrêt. Le répondeur réagit aux questions PTR, SRV, TXT, A, ANY et à l'énumération `_services._dns-sd._udp.local`, applique la suppression des réponses connues, répond en unicast « legacy » aux demandeurs hors du port 5353. Il partage le port 5353 avec le démon du système (`reuseAddr`). Vérifié sur un Mac : `dns-sd -B _castbridge._tcp local.` liste « CastBridge-TV Web » à côté des CastBridge-TV réelles, `dns-sd -L` rend le port 8765 et le TXT, `dns-sd -G v4` l'adresse ; une vraie CastBridge-TV répond à la même requête avec la même structure.

### 4.2 Routes HTTP (port 8765)

| Route | Réponse |
|---|---|
| `GET /api/hello` | `200 {"app":"castbridge-tv","v":"0.7","pinRequired":false,"platform":"webos","build":"0.1.0-beta"}` |
| `GET /api/info` | `200` : `{"files":[],"free":0,"used":0,"quota":0,"player":{"state":"idle","name":null,"pos":0,"dur":0}}` |
| `POST /api/playurl?url=&title=&pos=` | `200` + info ; `400 {"error":"http(s) url required"}` ; `409 {"error":"foreground","message":"La TV doit être au premier plan : ouvrez CastBridge-TV Web sur la TV."}` |
| `POST /api/pause`, `/api/resume`, `/api/stop` | `200` + info |
| `POST /api/seek?pos=` | `200` + info (`pos` en ms) |
| tout autre chemin | `404 {"error":"not found"}` ; mauvaise méthode `405 {"error":"use GET"}` ou `{"error":"use POST"}` |
| toute requête portant un en-tête `Origin` | `403` : un navigateur ne peut pas lancer une lecture depuis une page web du même Wi-Fi (le téléphone et curl n'en envoient pas) |

Règles de `playurl`, copiées de la CastBridge-TV Android : lien `http` ou `https` (schéma insensible à la casse), 4096 caractères au plus, sans espace ; titre rogné, 200 caractères au plus, sans `/`, `\` ni NUL et sans point initial, sinon « Depuis le téléphone » ; position en millisecondes, entier, ramenée à 0 si négative ou illisible. Les paramètres sont lus décodés (`%20`, UTF-8). Chaque réponse est un JSON UTF-8 avec `Connection: close` : le client Android garde ses connexions en réserve et ne rejoue pas un POST lancé sur une connexion que le serveur a fermée ; sans connexion persistante ce cas n'existe pas. Le journal du service ne contient jamais les paramètres d'une requête (un lien du téléphone peut porter un jeton).

### 4.3 L'état du lecteur

`player.state` vaut `idle`, `buffering`, `playing`, `paused`, `ended` ou `error` : le vocabulaire que `CastSession.poll()` du téléphone comprend (« Mise en mémoire tampon sur la TV… », « Lecture terminée », « La TV n'a pas pu lire ce fichier »). `name` est le titre reçu, tel quel : le téléphone compare ce nom à celui qu'il a envoyé. `pos` et `dur` sont en **millisecondes** ; `dur` vaut 0 si la durée est inconnue. Juste après une commande, le service répond l'état attendu (pause rend déjà `paused`, comme la CastBridge-TV Android) ; le premier rapport de l'app le confirme. Entre deux rapports (une seconde), la position avance toute seule pendant la lecture. Si l'app n'a plus donné signe de vie depuis 10 s, le lecteur est au repos (`idle`) et `playurl` répond 409.

### 4.4 Écarts avec la CastBridge-TV Android

1. **Pas de code PIN, pas de jeton de confiance** : `pinRequired:false`, aucune route ne répond 401. La CastBridge-TV Android exige le code à 6 chiffres (`X-CB-Pin`) ou le jeton d'un téléphone de confiance (`X-CB-Token`). Le téléphone demande pourtant un code à la première connexion : **saisir `000000`** (affiché sur l'accueil de la TV) ; il est ignoré. N'importe quel appareil du Wi-Fi peut donc lancer une lecture (voir D-WEB-3).
2. **Pas d'activation ni de verrou** : ni `locked` dans `hello`, ni `locked=1` dans le TXT.
3. **`info` minimal** : les cinq clés que `TvInfo.parse` lit, rien de plus (pas de `target`, `volumes`, `playlist`, `playbackPriority`, `rejections`).
4. **`hello`** : `v` est le niveau d'API imité (`0.7`, celui de la CastBridge-TV Android), pas la version de l'application ; `platform` et `build` sont des clés en plus.
5. **Routes absentes** : `/api/sysinfo` (le téléphone l'interroge pour le volume : il ignore le 404), `/api/volume`, `/api/library`, `/api/storage…`, `/api/activation`, `/stream/…`, etc.
6. **Nom** : « CastBridge-TV Web » (la CastBridge-TV Android annonce « CastBridge TV <modèle> »).
7. **Au premier plan seulement** : voir § 7.

## 5. Pont entre l'application et le service (Luna)

Service `luna://com.sticm.castbridge.tv.service` :

| Méthode | Rôle |
|---|---|
| `events` (abonnement) | première réponse `{event:"hello", seq, name, version, port}` ; puis une réponse `{event:"command", cmd, seq, …}` par commande du téléphone : `play` (`url`, `title`, `pos`), `pause`, `resume`, `stop`, `seek` (`pos`) |
| `state` | l'app pousse `{state, name, pos, dur, ack}` chaque seconde (et à chaque changement) ; la réponse `{seq, resync, commands}` rend les commandes qu'elle n'a pas encore appliquées |
| `status` | diagnostic : nom, port, adresses, HTTP, mDNS |

Les commandes sont **numérotées** (`seq`) et l'app répond avec `ack`, le numéro de la dernière qu'elle a appliquée. Conséquences : une commande arrivée par l'abonnement puis dans la réponse de `state` n'est appliquée qu'une fois ; un rapport d'état antérieur à une commande est ignoré (l'état ne revient pas en arrière) ; si le service redémarre (`seq` repart de 0), `resync` dit à l'app de se recaler ; une commande de plus de 15 s n'est plus rejouée à une app qui revient. Le service s'appuie sur `message.isSubscription`, `message.uniqueToken`, `service.register(nom, appel, annulation)` et `service.activityManager.create('keepAlive', …)` de `webos-service` : **API non essayées sur une TV** (§ 6.4).

## 6. Tester

### 6.1 Tests automatiques

```bash
node webos/castbridge-tv-web/tests/run.js      # 47 tests, code de sortie 0 si tout est vert (écrits pour Node 8 et suivants ; lancés avec Node 26)
python3 tools/release/check_versions.py        # 0 erreur : appinfo.json = tvweb.versionName sans « -beta »
```

Les tests couvrent : paquets mDNS (réponse à une requête PTR, nom d'instance, TXT, suppression des réponses connues, paquets malformés), routes (hello, info conforme à `TvInfo.parse`, playurl avec paramètres encodés, 404, 405, 409), état du lecteur, pont, lecteur simulé, un vrai serveur HTTP sur 127.0.0.1, le démarrage complet de l'app sur un faux DOM et un faux Luna, la cohérence des identifiants et des versions, et deux gardes de syntaxe (Node 8 pour le service, ES5 pour l'app).

### 6.2 Sur un Mac ou un PC : mode autonome

```bash
node webos/castbridge-tv-web/service/service.js --standalone            # HTTP 8765 + mDNS, lecteur simulé
# options : --name "TV Salon"  --port 8765  --ip 192.168.1.20  --no-mdns  --verbose
curl -s http://127.0.0.1:8765/api/hello
curl -s http://127.0.0.1:8765/api/info
curl -s -X POST 'http://127.0.0.1:8765/api/playurl?url=http%3A%2F%2F192.168.1.20%3A8089%2Fmedia%2Fa.mp4&title=Film.mp4&pos=3000'
curl -s -X POST http://127.0.0.1:8765/api/pause   # puis /api/resume, /api/seek?pos=60000, /api/stop
```

Le lecteur simulé tient la place de l'app : 400 ms de chargement, puis la position avance en temps réel. **Avec le vrai téléphone**, sur le même Wi-Fi que le Mac : ouvrir CastBridge, la TV « CastBridge-TV Web » apparaît dans la liste ; saisir `000000` si le code est demandé ; ouvrir une vidéo et « Lire en direct ». Résultat attendu (à constater avec le téléphone) : le téléphone suit la lecture, la pause et la position répondent ; aucune image n'apparaît, c'est un simulateur. Un Wi-Fi « invité » qui isole les appareils les uns des autres empêche la découverte.

Pour voir l'écran de l'application dans un navigateur : ouvrir `webos/castbridge-tv-web/index.html` (mode navigateur : pas de service) ; `index.html?demo=<lien encodé>` lit directement un lien.

### 6.3 Sur une TV LG en mode développeur

1. Compte LG développeur (webostv.developer.lge.com). Sur la TV, installer l'application **Developer Mode** (LG Content Store), s'y connecter, activer « Dev Mode Status » et « Key Server » ; la TV redémarre ; noter son adresse IP et la phrase secrète.
2. Sur le Mac : `npm install -g @webos-tools/cli`, puis `ares-setup-device` (ajouter la TV : nom `tv`, adresse IP, port 9922, utilisateur `prisoner`) et `ares-novacom --device tv --getkey` (phrase secrète).
3. `bash tools/webos/package.sh` : lance les tests, empaquette (`ares-package --no-minify`) et écrit `build/webos/com.sticm.castbridge.tv_0.1.0_all.ipk`. Sans le CLI, il imprime cette procédure.
4. `ares-install --device tv build/webos/com.sticm.castbridge.tv_0.1.0_all.ipk` puis `ares-launch --device tv com.sticm.castbridge.tv`.
5. Déboguer : `ares-inspect --device tv --app com.sticm.castbridge.tv --open`, et pour le service `ares-inspect --device tv --service com.sticm.castbridge.tv.service --open`.

### 6.4 À vérifier à la première mise sur une TV LG

1. L'accueil montre l'adresse IP, « Service actif » et le point vert (le pont Luna et `webos-service` fonctionnent).
2. Le téléphone voit « CastBridge-TV Web » : le port 5353 est partagé avec le démon mDNS de la TV (sinon le journal du service dit « mDNS : … » et il reste la connexion par adresse manuelle) et `os.networkInterfaces()` rend l'adresse.
3. Le port 8765 de la TV est joignable depuis le téléphone (`curl http://<adresse>:8765/api/hello`).
4. « Lire en direct » joue une vidéo MP4 H.264/AAC du téléphone ; pause, reprise, position depuis le téléphone ; la durée et la position remontent.
5. Le toast webOS apparaît au début de la lecture (sinon : `requiredPermissions` à ajouter à `appinfo.json`, nom à chercher dans la documentation LG ; `appinfo.json` n'en déclare aucune pour l'instant, et l'application ne dépend pas du toast : son échec est ignoré).
6. Le service reste vivant après avoir quitté l'application (`keepAlive`), et l'application rouverte se rabonne.
7. La version de webOS, la version de Node.js du service (`node -v` dans le journal) et les formats lus (MP4, MKV, HEVC) sont notés ici.

## 7. Limites connues

- **Au premier plan seulement.** Une application web webOS ne joue pas en arrière-plan : si CastBridge-TV Web n'est pas ouverte (ou ne signale plus rien depuis 10 s), `playurl` répond 409 et le téléphone affiche « La TV doit être au premier plan : ouvrez CastBridge-TV Web sur la TV ». Quand une autre application passe devant, la lecture se met en pause (événement `visibilitychange`, à confirmer sur TV). Le service, lui, peut rester actif sans l'application (`keepAlive`) et continuer d'annoncer la TV ; il ne démarre qu'à la première ouverture de l'application après un redémarrage de la TV (à notre connaissance, webOS ne lance pas une application tierce au démarrage).
- **Aucun stockage.** `free`, `used` et `quota` valent 0 ; rien n'est copié ni gardé. Le téléphone doit rester allumé, sur le même Wi-Fi, pendant toute la lecture (il sert le fichier).
- **Formats** : ceux du lecteur HTML5 de la TV (MP4 H.264/AAC sûr ; MKV, AVI, HEVC selon le modèle). Pas de sous-titres, pas de photos, pas de listes de lecture.
- **Mode développeur** : la session du mode développeur expire (environ 50 heures au moment de la rédaction, à confirmer) ; il faut la prolonger dans l'application Developer Mode, sinon l'application ne se lance plus tant que la session n'est pas rétablie. Une TV hors du mode développeur n'accepte aucun paquet.
- **LG Content Store** : aucune publication n'est faite ni préparée (compte vendeur, validation LG, pays : D-WEB-2). Sans le Content Store, l'installation reste réservée aux TV du propriétaire en mode développeur.
- **Plusieurs téléphones** : tous peuvent appeler ; la dernière lecture demandée l'emporte. **Plusieurs TV LG** sur le même réseau : même nom d'instance (D-WEB-5).
- **Pas de code** : voir § 4.4 et D-WEB-3.
- webOS : code écrit pour tourner dès webOS TV 3 (app ES5, sans variable CSS) et Node 8 (service) ; aucune version n'a été essayée.

## 8. Décisions ouvertes

| Réf. | Question | Pistes |
|---|---|---|
| **D-WEB-1** | Identifiant et domaine de l'application | `com.sticm.castbridge.tv` (éditeur « STI-CM »), par analogie avec le domaine `sti-cm.com` du serveur ; l'id du service doit en rester le préfixe (`….service`, exigé par `ares-package`). L'id est définitif une fois publié : à confirmer ou changer **avant** tout envoi au Content Store |
| **D-WEB-2** | Pays du LG Content Store | Pays visés à la soumission (Cameroun, pays francophones d'Afrique ?), disponibilité du Content Store par pays et par modèle, conditions du compte vendeur, validation LG d'une application qui annonce et sert des ports sur le réseau local. Tant que non décidé : mode développeur seulement |
| **D-WEB-3** | Poignée de main `hello` et confiance | La CastBridge-TV Android : `pinRequired:true`, code à 6 chiffres, jetons de téléphones de confiance, activation. Ici : rien (`pinRequired:false`, code `000000` ignoré). Options : (a) code aléatoire affiché sur l'accueil, gardé dans le dossier de données du service (un code par TV, jamais redemandé), vérifié sur `X-CB-Pin`, avec blocage après essais comme `PinGuard` ; (b) (a) plus jetons de confiance ; (c) statu quo pendant la phase d'essai (jusqu'au 2026-12-31). Recommandation : (a) avant tout usage hors du réseau du propriétaire |
| **D-WEB-4** | Lancer l'application depuis le service | Quand un lien arrive alors que l'application est fermée, le service pourrait la lancer (`luna://com.webos.applicationManager/launch`) au lieu de répondre 409. À essayer sur TV (permissions du service) |
| **D-WEB-5** | Nom de la TV | Nom fixe « CastBridge-TV Web » : deux TV LG se disputeraient le nom d'instance. Prévoir un nom saisi dans l'application ou tiré du modèle (`luna://com.webos.service.tv.systemproperty/getSystemInfo`) |

## 9. Version et étiquette

`version.properties` : `tvweb.versionName=0.1.0-beta`, `tvweb.versionCode=1`. Étiquette `tvweb-<version>` (`tvweb-0.1.0-beta`), posée à la main (`git tag -a`) : `tag-plan.sh` ne la gère pas encore (`docs/RELEASES.md` § 13). `appinfo.json` et `service/package.json` portent la même version **sans** le suffixe `-beta` (`0.1.0`, webOS exige `X.Y.Z`) ; `tools/release/check_versions.py` contrôle `appinfo.json`, un test contrôle les constantes et `package.json`. Le paquet `.ipk` n'est pas publié sur le serveur (`/telecharger` ne le connaît pas).
