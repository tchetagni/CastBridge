# Administrer CastBridge TV à distance (guide pour humains et agents)

CastBridge TV (module `:receiver`) est une app Android TV qui expose une API HTTP JSON sur le port **8765**.
Tout reste dans le stockage privé de l'app (`getExternalFilesDir("videos")`) : aucune permission de stockage,
aucun contournement des protections d'Android.

## 1. Trouver la TV

- mDNS : service `_castbridge._tcp`, attribut TXT `role=receiver` (le serveur PC annonce `role=server`, à ignorer).
  - macOS : `dns-sd -B _castbridge._tcp` puis `dns-sd -L "<nom>" _castbridge._tcp`
  - Linux : `avahi-browse -rt _castbridge._tcp`
- Ou l'adresse IP affichée sur l'écran d'attente de la TV (`192.168.x.y:8765`).
- Wi-Fi Direct (sans routeur) : la TV est à `192.168.49.1:8765` une fois connecté à son réseau (voir §6).
- Vérification sans PIN : `curl http://TV:8765/api/hello` -> `{"app":"castbridge-tv","v":"0.3","pinRequired":true}`

## 2. Obtenir le PIN

Un code à 6 chiffres est généré au premier lancement de l'app et **affiché sur l'écran d'attente de la TV**.
Il n'existe volontairement aucun moyen de le lire à distance. Il est conservé dans les préférences privées de l'app
et n'est jamais écrit dans les journaux.

Toutes les routes sauf `GET /` (page web) et `GET /api/hello` exigent le PIN :
en-tête `X-CB-Pin: 123456` **uniquement** (le paramètre `?pin=` n'est plus accepté : il finissait dans les journaux et permettait le CSRF).
La TV refuse aussi (`403 Hôte non autorisé`) toute requête dont l'en-tête `Host` n'est pas une adresse IP privée/locale ou `localhost` (anti DNS-rebinding) : utilisez l'adresse IP de la TV, pas un nom DNS.
Après **5 échecs** depuis une même IP, cette IP est verrouillée **60 s** (même le bon PIN est refusé pendant ce temps).
Un agent qui reçoit `401` ne doit pas réessayer en boucle : voir `retryAfter`.

```sh
export TV=http://192.168.0.117:8765 PIN=123456
alias tv='curl -sS -H "X-CB-Pin: $PIN"'
```

## 3. Routes (JSON, encodage UTF-8)

Les noms de fichiers sont sans `/`, `\`, ni `.part` final, 200 caractères max. Les erreurs ont la forme
`{"error":"..."}`.

| Méthode et route | Effet | Réponse (200) |
|---|---|---|
| `GET /` | page web d'administration (sans PIN, ne contient aucune donnée) | HTML |
| `GET /api/hello` | identification (sans PIN) | `{"app","v","pinRequired"}` |
| `GET /api/info` | fichiers (y compris ceux en cours d'envoi), espace, lecteur | `{"files":[{"name","size","received","complete"}],"free","used","quota":octets,"player":{"state":"idle\|playing\|buffering\|paused\|ended\|error","name","pos":ms,"dur":ms}}` (`size` = taille finale ; `received < size` tant que l'envoi n'est pas fini) |
| `GET /api/sysinfo` | infos appareil | `{"model","android","ip","battery":%\|null,"charging":bool\|null,"uptime":ms,"app","volume":0-100\|null,"pssMb","memAvailMb","memTotalMb","lowMemory"}` |
| `POST /api/volume?pct=0..100` | volume média de la TV | comme `sysinfo` |
| `POST /api/restart` | redémarre **l'app** (pas la TV) | `{"restarting":true}` |
| `GET /api/part?name=` | octets déjà reçus d'un envoi | `{"name","length","done":bool}` |
| `PUT /upload/<nom>?offset=N&total=T[&target=auto\|internal\|<idVolume>]` | ajoute les octets `[N,T)` (corps = octets restants, `Content-Length` obligatoire) ; `target` choisit le volume de **ce** transfert (au premier octet ; sinon le réglage de la TV) | `{"name","length","done"}` |
| `POST /api/reset?name=` | efface le `.part` | idem `part` |
| `POST /api/play?name=&pos=ms` | lit un fichier ; s'il est encore en cours d'envoi, démarre en flux dès que assez de données sont arrivées (sinon `409 {"error":"buffering","received","needed","size"}`) | comme `info` |
| `POST /api/playurl?url=&title=&pos=ms` | lit un lien `http(s)` sans rien stocker (« Lire en direct » du lecteur du téléphone : le petit serveur du téléphone ou un lien web) ; `400` pour tout autre schéma | comme `info` |
| `GET\|HEAD /stream/<nom>` | le fichier (ou le `.part` en cours) avec `Range` 206/416, `Content-Length` = taille **finale** ; lit bloquant jusqu'à l'arrivée des octets manquants (coupure propre après 30 s) | octets |
| `GET /api/storage` / `POST /api/storage?deleteAfterPlay=&evictPlayed=&quotaMb=&minFreeAfterMb=` | quota et politique de stockage (`minFreeAfterMb` : espace à garder libre après un transfert, 1024 par défaut), volumes (mémoire interne, clé USB, dossier SAF) | `{"used","free","quota","quotaMb","deleteAfterPlay","evictPlayed","minFreeMb","minFreeAfterMb","target","primary","volumes":[{"id","label","kind","fs","removable","writable","present","free","total","used","quota","writeBps","warnings","formatAdvice"}],"move","warnings"}` |
| `POST /api/storage/target?value=auto\|internal\|<idVolume>` | où vont les nouveaux fichiers (jamais un chemin) | comme `GET /api/storage` |
| `GET /api/storage/check?name=&size=&dur=ms[&volume=auto\|internal\|<id>]` | pré-vérification avant envoi : volume prévu, espace restant **après** le transfert, avertissements, ou refus précis (FAT32 > 4 Go, « il resterait 640 Mo, il en faut 1 Go »...) | `{"ok":true,"volume","label","fs","as","warnings","free","remaining","freeAfter","minFreeAfter","options":[{"id","label","kind","free","freeAfter","ok"}]}` ou `{"ok":false,"status":413\|507\|503,"error","message","options"}` |
| `POST /api/storage/move?name=&to=<idVolume\|saf>` `POST /api/storage/move/cancel` | déplace un fichier fini entre volumes (copie vérifiée puis suppression de la source ; refusé si en lecture) | `{"moving":true,"move":{...}}`, état dans `GET /api/storage` |
| `POST /api/storage/rescan[?measure=1]` | re-détecte les volumes (et re-mesure le débit d'écriture) | comme `GET /api/storage` |
| `POST /api/storage/saf/pick` | ouvre le sélecteur de dossier **sur l'écran de la TV** (quelqu'un doit valider) | `{"message"}` |
| `POST /api/storage/open-settings` | ouvre les réglages de stockage de la TV, si elle en a ; ne formate jamais | `{"opened":bool,"message"}` |
| `POST /api/pause` `POST /api/resume` `POST /api/stop` | contrôle | comme `info` |
| `POST /api/seek?pos=ms` | saut | comme `info` |
| `POST /api/delete?name=` | supprime fichier et `.part` | comme `info` |
| `POST /api/rename?name=&to=` | renomme (409 si la cible existe) | comme `info` |
| `GET /api/library` | bibliothèque : fichiers finis, plus récents d'abord, avec métadonnées | `{"files":[{"name","title","size","mtime","volume","volumeLabel","kind","type":"video\|audio\|other","durationMs","resumeMs","watched","playedAt","hasThumb","duplicate","playing"}],"count"}` |
| `GET /api/thumb?name=[&volume=]` | miniature JPEG (~320 px) ; la première demande lance sa fabrication | `200` image, `202` pas encore prête (redemander plus tard), `404` impossible (pas une vidéo, fichier illisible) |
| `POST /api/library/watched?name=&watched=1\|0` | marque vu / non vu (efface la position de reprise) | comme `library` |
| `GET /api/player/tracks` | pistes et réglages de la lecture en cours | `{"playing":true,"audio":[{"id","name"}],"audioId","subtitles":[...],"subtitleId","subtitleFiles":[noms],"subDelayMs","audioDelayMs","subScale","rate","aspect","aspects","chapters":[{"name","timeMs"}],"chapter","titles","title","hw","video":{"codec","width","height","fps","bitrate","decoder"},"audioCodec","eqPreset","eqPresets","repeat","queue","queueIndex"}` ou `{"playing":false}` |
| `POST /api/player/audio?id=` · `/subtitle?id=` (`-1` = désactivés) ou `?file=<nom>` · `/subdelay?ms=\|delta=` · `/audiodelay?ms=\|delta=` (pas de 50 ms, ±30 s max) · `/subsize?value=25..400` (%) · `/rate?value=0.5..2` · `/aspect?value=auto\|16:9\|4:3\|fill\|crop` · `/chapter?index=\|delta=±1` · `/title?index=` · `/hw?value=auto\|on\|off` · `/eq?preset=-1..N` | réglages du lecteur (mémorisés par fichier) | comme `tracks` ; `409` rien en lecture ou impossible, `400` valeur invalide |
| `POST /api/player/subfile?name=` | active un fichier de sous-titres **stocké sur la TV** (envoyé comme un fichier ordinaire) | comme `tracks` |
| `POST /api/playlist?names=a.mp4/b.mkv&start=0&repeat=off\|all\|one` · `/api/player/next` · `/api/player/prev` · `/api/player/repeat?value=` | lecture à la suite (noms séparés par `/`), fichier suivant/précédent, répétition | comme `info` (`playlist` : `{"items","index","repeat"}`) |
| `GET /api/background` | service d'arrière-plan : démarrage avec la TV, autorisations utiles à la lecture à distance | `{"autostart","overlay","fullScreenIntent","notifications","screenVisible","sdk"}` |
| `POST /api/autostart?enabled=1\|0` | « Démarrer avec la TV » | comme `background` |
| `POST /api/overlay-permission` | ouvre sur la TV le réglage « Afficher par-dessus les autres apps » (seulement si l'écran CastBridge est affiché) | `{"opened","message"}` |
| `POST /api/tv/open[?screen=home\|library\|player\|games\|quiz]` (corps JSON facultatif `{"screen":…}`) | **ouvre CastBridge-TV devant l'application affichée** (YouTube…) : direct, sinon notification « Demandes du téléphone », sinon accessibilité ; chaque essai est vérifié (`docs/REMOTE.md`, « Ouvrir CastBridge-TV depuis le téléphone ») | `{"opened","how":"direct\|fullscreen\|accessibility\|already\|none","needs":"overlay"\|null}` (+ `"already":true`) |
| `GET /api/net` | **Internet de la TV** (même état que le badge en haut à droite de l'écran) : par quel chemin la TV atteint réellement Internet, d'après le test 204 (jamais « un lien est actif ») | `{"state":"checking\|wifi\|ethernet\|phone\|none","label","working","link":"wifi\|ethernet\|other\|none","direct":{"ok","ms"},"gateway":{"connected","ok","ms","alsoAvailable"},"checkedAt"}` |
| `GET /api/connections` | **Connexions de la TV** (la barre d'icônes de l'écran) : une entrée par appareil ou mode actif, étiquettes seulement (jamais de jeton, PIN ni adresse Bluetooth) | `{"connections":[{"kind","technology","secondary","label","state","since","count","latencyMs"}],"visible","hidden","now"}` (`kind` : internet, phone, remote, ssh, gateway, cast, usb, wifi_direct, quiz, chess, download, parental, update ; `technology` : ethernet, wifi_lan, wifi_direct, usb, ssh_lan, bluetooth_tunnel, ssh_bluetooth, bluetooth, none ; `state` : connected, connecting, degraded, error ; `since` en ms epoch) |
| `GET /api/usb` | état de l'import USB et volumes détectés | `{"running","message","volumes":[chemins]}` |
| `POST /api/usb/import` | copie les vidéos des clés détectées (dossier de l'app sur la clé) | comme `usb` |

### Stockage et mémoire (TV modeste)

- **Quota** du dossier vidéos : par défaut min(50 % de « utilisé + libre », 8 Go) ; réglable (`quotaMb`). Un envoi qui ne tient pas (quota, ou moins de
  1 Go libre sur le volume à la fin du transfert) est refusé **avant** toute écriture : `507 {"error":"quota exceeded"|"not enough space","message":"…"}`.
- **Éviction** (option, désactivée par défaut) : si le quota est plein, suppression des plus anciens fichiers **déjà lus**, jamais celui en cours de lecture.
  **Supprimer après lecture** (option, désactivée) : un fichier lu jusqu'au bout est effacé.
- Les `.part` (et leurs `.meta`) abandonnés depuis plus de 24 h sont supprimés au démarrage.
- libVLC n'est créé qu'au premier `play` et libéré à l'arrêt, à la fin, ou sur `onTrimMemory` ; tampons d'E/S de 64 Ko ; 8 connexions HTTP au plus ;
  `/api/info` relit le dossier au plus une fois par seconde. `GET /api/sysinfo` donne `pssMb` (mémoire de l'app) et `memAvailMb`.
- Constantes regroupées dans `TvProfile` (module `:core`).

### Démarrage avec la TV, service d'arrière-plan

- **Architecture** : tout ce qui n'est pas l'affichage tourne dans `TvService`, un service de premier plan (notification discrète « CastBridge TV actif ») : serveur
  HTTP et page web, envois/téléchargements, API bibliothèque, volumes et clé USB (événements de montage), Bluetooth (fichiers + tunnel SSH), Wi-Fi Direct, SSH,
  installation d'APK, mDNS. `PlayerActivity` n'est plus que l'écran (attente, bibliothèque, lecture libVLC) : elle se lie au service, s'enregistre comme lecteur, et
  peut être fermée sans couper le serveur. Un seul serveur (celui du service) : pas de conflit sur le port 8765 si l'écran et le service démarrent ensemble (nouvel
  essai toutes les 3 s si le port est occupé). Le service redémarre s'il est tué (`START_STICKY`) ; l'écran libère libVLC dès qu'il n'est plus visible (`onStop`) et
  sur `onTrimMemory`. Verrou de veille **partiel** (et verrou Wi-Fi) **seulement pendant un transfert** (envoi HTTP, Bluetooth, import USB, déplacement).
- **Type de service** : `connectedDevice` (le service dialogue avec le téléphone par le réseau et le Bluetooth ; prérequis Android 14 satisfait par
  `CHANGE_WIFI_MULTICAST_STATE`, permission normale). Pas `dataSync` : Android 15 interdit de le démarrer depuis `BOOT_COMPLETED` et le limite à 6 h par jour.
- **Démarrage automatique** : `BootReceiver` (`BOOT_COMPLETED`, `QUICKBOOT_POWERON` de certains firmwares TV, et `MY_PACKAGE_REPLACED` pour repartir après une mise
  à jour par Wi-Fi) démarre `TvService` si l'option **« Démarrer avec la TV »** est active (par défaut ; MENU de la TV, page web, `POST /api/autostart`).
  Android 14 autorise le démarrage d'un service de premier plan depuis `BOOT_COMPLETED` (exemption documentée) pour ce type. `LOCKED_BOOT_COMPLETED` n'est pas utilisé :
  les réglages de l'app (PIN...) sont dans le stockage chiffré, lisible seulement après le premier déverrouillage (sur une TV sans code, c'est immédiat).
- **Lecture demandée à distance, écran fermé** : Android 10+ interdit à une app en arrière-plan d'ouvrir son écran. Ordre essayé (`LaunchPolicy`) : écran déjà affiché ->
  lecture directe ; autorisation « **Afficher par-dessus les autres apps** » accordée -> l'écran s'ouvre seul et lit (la réponse attend jusqu'à 6 s) ; sinon notification
  (plein écran si `USE_FULL_SCREEN_INTENT` est utilisable) « le téléphone demande de lire une vidéo » ; la demande est gardée 2 min et jouée à l'ouverture de l'écran ;
  l'API répond `409 {"needsForeground":true,"message":"…"}` et le téléphone affiche le message (« ouvrez CastBridge TV sur la TV »). MENU > « Lecture à distance :
  autoriser l'affichage par-dessus les autres apps » ou `POST /api/overlay-permission` ouvre le réglage (avec replis, sans planter s'il n'existe pas sur GaiaOS).
- **Écrans de réglages** (stockage, options développeur, sélecteur de dossier, sources inconnues) : ouverts **depuis l'écran au premier plan** ; si l'écran n'est pas
  affiché, l'API répond un message clair (« ouvrez l'app CastBridge TV avec la télécommande, puis réessayez ») au lieu d'un lancement silencieusement bloqué.
  La confirmation d'installation d'un APK passe par le même mécanisme (écran affiché, sinon notification).
- **Fonctionne écran fermé** : envois/téléchargements, SSH et tunnel Bluetooth, API bibliothèque et miniatures, déplacements, installation d'APK (la confirmation
  demande l'écran), page web. **Ne fonctionne pas** écran fermé : la lecture elle-même (il faut l'écran), les dialogues.
- **Réglages GaiaOS éventuellement nécessaires** (inconnus sans la TV) : un gestionnaire de « démarrage automatique » ou d'« applications autorisées au démarrage »,
  l'optimisation de batterie / économie d'énergie (exclure CastBridge TV), « Afficher par-dessus les autres apps ». **Important** : Android ne délivre pas
  `BOOT_COMPLETED` à une app installée qui n'a **jamais été ouverte** (état « arrêté ») : ouvrez CastBridge TV une fois après l'installation ; certains firmwares le
  bloquent aussi après un « arrêt forcé ». Si la TV passe en veille profonde, aucun service ne tourne (la TV n'est plus joignable) : c'est matériel.

### Échange de fichiers à débit maximal (téléphone <-> TV)

- **Règle des 1 Go** (`TvProfile.minFreeAfterTransfer`, 1 Gio par défaut, 0 = désactivée) : un transfert téléphone -> TV n'est accepté que si, **à la fin**, le volume de
  destination garde au moins 1 Go libre : `libre - reste_à_recevoir >= 1 Go`. Évaluée sur le volume choisi par la politique de stockage existante ; en cible `auto`,
  un volume qui ne la respecte pas est écarté et le suivant est essayé ; sinon **refus avant tout octet** (`/api/storage/check` puis `PUT`, `507`) avec un message précis :
  « Espace insuffisant : il resterait 640 Mo sur Mémoire interne après le transfert, il en faut 1 Go : libérez 384 Mo ou branchez la clé USB. » Une reprise est jugée
  sur ce qui reste à recevoir. Cette règle remplace la réserve de 100 Mo **pour ces transferts** ; déplacements, Bluetooth et import USB gardent la réserve de 100 Mo.
  Sur la TV d'Esaie (≈ 300 Mo libres en interne), cela veut dire : **les envois vont sur la clé USB** (58 Go) ; sans clé, ils sont refusés avec ce message.
- **Téléphone -> TV** : app > CastBridge TV > Wi-Fi > « Échange de fichiers » : tout type de fichier (les non-vidéos apparaissent sous « Autres fichiers » de la
  bibliothèque), choix du volume de destination pour ce transfert (`target=`), espace restant prévu après le transfert pour chaque volume, débit instantané et moyen,
  temps restant, reprise automatique. Débit : un seul flux HTTP continu (`setFixedLengthStreamingMode`, pas de limitation), lectures de 512 Ko côté téléphone,
  verrou Wi-Fi `LOW_LATENCY` (API 29+) + `HIGH_PERF`, wakelock partiel. Côté TV : la socket remplit un bloc de **256 Ko** avant chaque écriture disque (moins d'appels
  sur FUSE/clé), **aucun fsync par bloc** ; sur une clé amovible, `fsync` tous les 64 Mo et à la fin (données sur le support si on la retire). RAM : 256 Ko par envoi
  actif (8 connexions HTTP au plus = 2 Mo au pire).
- **Connexions parallèles par plages : non livrées, volontairement.** La conception étudiée (fichier pré-alloué, 2 à 4 segments `PUT` par offset, carte des segments
  persistée pour la reprise) casse l'invariant « le `.part` est un préfixe contigu » dont dépendent la lecture pendant l'envoi, la reprise par `length`, le Bluetooth et
  le déplacement ; et elle n'apporterait rien ici : la destination réelle est la clé USB, mesurée à **~2,2 Mo/s en écriture**, qu'un seul flux Wi-Fi sature déjà (et des
  écritures entrelacées en plusieurs endroits d'un fichier exFAT sur une clé sont plus lentes, pas plus rapides). Le débit mesuré est affiché avec, le cas échéant,
  « débit limité par l'écriture de la clé ». À reconsidérer seulement si des mesures sur la TV montrent un flux unique nettement sous le débit d'écriture du volume.
- **TV -> téléphone** : « Télécharger sur le téléphone » (bibliothèque) ou liste « De la TV vers le téléphone » : `GET /stream/<nom>` avec `Range: bytes=<déjà reçu>-`,
  reprise automatique après coupure (et au prochain lancement du même fichier), service de premier plan, enregistrement dans **Téléchargements/CastBridge**
  (MediaStore, masqué aux autres apps tant qu'il n'est pas complet ; dossier de l'app sur Android 8-9) ou dans un **dossier choisi** (SAF). Débit instantané/moyen.

### Lecteur (libVLC) : pistes, sous-titres, décalages, vitesse, format, chapitres

- **Sur la TV**, pendant la lecture : **MENU** ouvre « Réglages de lecture » (listes au D-pad) : piste audio, sous-titres (pistes du fichier, fichiers `.srt/.ass/.ssa/.vtt/.sub`
  du même nom à côté de la vidéo, ex. `Film.srt`, `Film.fr.srt`), décalage des sous-titres et de l'audio (±50 ms / ±500 ms, remise à 0), taille des sous-titres, vitesse
  0,5x à 2x, format d'image (auto, 16:9, 4:3, remplir, rogner), chapitres et titres, liste de lecture et répétition, égaliseur (préréglages libVLC), décodage
  (automatique = MediaCodec avec repli logiciel de libVLC, matériel forcé, logiciel), informations techniques (codec, définition, images/s, débit, décodage, audio),
  puis « Options générales ». Touches : OK/lecture-pause, gauche/droite ±10 s, haut/bas ±60 s (inchangés), **INFO** = infos, **SOUS-TITRES** et **AUDIO** = piste
  suivante, **SUIVANT/PRÉCÉDENT** ou **CHAÎNE +/-** = chapitre (ou fichier de la liste). Barre de progression en bas (titre, position, durée, temps restant, part déjà
  reçue pendant un envoi) après chaque saut ou pause, masquée après 4 s.
- **Téléphone** : écran « en lecture » > « Réglages » (mêmes réglages, + « Ajouter depuis le téléphone » pour envoyer un fichier de sous-titres et l'activer).
- **Mémorisation par fichier** (`PlayerPrefs`, dans `library.db`) : piste audio, sous-titres (piste ou fichier), décalages, taille, vitesse, format ; réappliqués à
  l'ouverture suivante du même fichier (nom + taille).
- **Mémoire (TV 32 bits)** : le moteur de sous-titres de libVLC reste désactivé (`--no-spu`) sauf pour un fichier qui en a besoin (choix mémorisé, fichier de
  sous-titres à côté, ou sous-titre choisi) : le lecteur est alors recréé avec lui **à la même position**. Le lecteur reste paresseux (créé au `play`, libéré à l'arrêt,
  à la fin, sur `onTrimMemory`). Changer la taille des sous-titres ou le décodage recrée aussi le lecteur (libVLC 3 ne les change pas à chaud).
- **Décodage** : « automatique » demande MediaCodec et laisse libVLC basculer seul en logiciel s'il refuse le format ; si la lecture échoue quand même (matériel forcé,
  ou erreur), **un** nouvel essai est fait en logiciel à la même position. L'API Java de libVLC 3 ne dit pas quel décodeur a réellement été retenu : les infos
  techniques indiquent le mode demandé.
- **Listes de lecture** : bibliothèque TV > MENU sur une carte > « Lire la section à la suite », ou `POST /api/playlist`. Répétition : non / toute la liste / ce fichier.
- **Non livré** : miniature d'aperçu pendant l'avance rapide (il faudrait un second décodeur en parallèle : exclu sur cette TV) ; contrôle des réglages de lecture depuis
  la page web (l'API est prête).

### Écran d'accueil (TV) et accueil du téléphone

- **TV, accueil façon « lanceur média »** : fond flouté tiré de la miniature de la vidéo sélectionnée (réduite une fois à 48x27 px puis étirée : flou sans calcul par image), qui dérive lentement ;
  horloge ; petit bandeau « ● Prêt à recevoir · code 83•••• » (OK dessus : code complet pendant 10 s ; pendant un envoi : « Réception de Film : 45 % ») ; rangées
  « Reprendre », « Récemment ajoutés », « Sur la clé USB », « Toutes les vidéos », « Autres fichiers », puis tuiles « Toute la bibliothèque », « Connexion &
  réglages », « Aide ». La carte qui a le focus grossit avec un halo bleu et le texte au-dessus des rangées la décrit (reprendre à…, durée, clé ou TV). Fondus entre
  l'accueil, la bibliothèque et la lecture. RETOUR sur l'accueil quitte l'écran (le service continue).
- **Plus de jargon sur l'écran principal** : adresse IP, port, PIN complet, SSH, Bluetooth, Wi-Fi Direct, stockage sont dans **MENU > Connexion & réglages** (infos
  en langage courant à gauche, toutes les options à droite). Messages en bandeau animé : « Vidéo reçue ✓ », « Clé USB branchée : 57 Go libres », « Clé USB retirée… ».
- **Lecteur** : barre fine de progression, titre, temps écoulé / total / restant, sur un dégradé, qui se masque seule après 4 s.
- **Mémoire** : pas de vidéo en fond ; le fond est la miniature déjà en cache, réduite une fois (quelques ko) ; une rangée = une
  `RecyclerView` ; bitmaps RGB_565, 4 Mo au plus.
- **Téléphone, onglet CastBridge TV** : accueil par tâches (ouvert au lancement depuis 2026-10-03) : « Copier sur la TV » (ancien « Envoyer une vidéo »), « Regarder sur la TV » (télécommande si une vidéo joue, sinon la bibliothèque),
  « Bibliothèque de la TV », « Échanger des fichiers » ; carte « Sur la TV » (lecture en cours) ; rangée « Reprendre sur la TV » ; progression d'envoi en grand avec
  temps restant et débit ; **assistant de première connexion** (trouver la TV sur le Wi-Fi, saisir son code une seule fois, vérifié aussitôt). Tout le reste
  (adresse manuelle, Bluetooth, Wi-Fi Direct, passerelle SSH, stockage, APK) est sous **« Avancé »** (l'écran d'avant, inchangé).

### Bibliothèque (TV, téléphone, page web)

- **Sur la TV** : les rangées de l'accueil, et la grille complète par la tuile « Toute la bibliothèque » (ou touche bleue / GUIDE / signet / « menu du contenu »).
  En quittant une vidéo (RETOUR, fin du fichier, arrêt depuis le téléphone), la TV revient à l'accueil (rangée « Reprendre »). Grille de cartes au D-pad (cadre bleu épais et
  agrandissement de la carte qui a le focus) : miniature, titre sur deux lignes, durée, barre de reprise, badge « VU », badge « Clé »/« Interne ». Sections
  « Reprendre » (commencé, pas fini, dernier lu d'abord), « Récemment ajoutés » (12 plus récents, si la bibliothèque en a plus de 6), « Toutes » (par titre), « Autres
  fichiers » (documents, APK...). OK = lire (« Reprendre à 12:34 » ou « Depuis le début » si une position est mémorisée) ; MENU ou OK maintenu = actions (lire depuis le
  début, marquer vu/non vu, déplacer vers la clé/l'interne, renommer, supprimer avec confirmation ; « Installer » pour un APK). RETOUR = écran d'accueil (PIN, adresse).
  Les actions passent par l'API HTTP de la TV elle-même (boucle locale) : mêmes règles que le téléphone.
- **Reprise** : la position est enregistrée à la pause, à l'arrêt, en quittant l'app et en changeant de fichier, normalisée (`LibraryLogic.resumeFrom` : rien si moins de 10 s
  ou dans les 30 dernières secondes / 95 %, et alors le fichier est « vu ») ; la fin du fichier le marque vu. Clé = nom stocké + taille (un renommage suit, une suppression
  oublie). Fichier `library.db` dans le stockage privé de l'app, 2000 entrées au plus.
- **Miniatures** : JPEG ~320 px, fabriquées en arrière-plan **une à la fois** et seulement quand rien ne joue (un seul décodage à la fois sur cette TV) : `MediaMetadataRetriever`
  (image réduite directement, API 27+ ; pochette pour l'audio) puis, en cas d'échec, libVLC (instance jetable, filtre `scene`, décodage logiciel, 8 s au plus). Cache disque dans
  le dossier cache de l'app, **20 Mo au plus** (LRU, clé = nom + taille + date de modification), rien en RAM côté serveur ; l'écran TV garde au plus 3 Mo de bitmaps RGB_565.
  Un fichier impossible à miniaturiser est marqué et n'est pas réessayé (`404`).
- **Téléphone** : onglet CastBridge TV > Wi-Fi > « Bibliothèque de la TV » : mêmes sections et actions (toucher = lire, appui long = actions), miniatures chargées à la
  demande, cache mémoire borné à 8 Mo. **Page web** : section « Bibliothèque » en grille.
- Choix techniques : l'écran TV est en vues Android classiques + une `RecyclerView` (seules les cartes visibles existent). Compose aurait ajouté plusieurs Mo à l'APK et
  une consommation de RAM plus élevée au repos pour une TV à ~330 Mo de RAM disponible ; mesuré : APK armeabi-v7a release (R8) 40,76 -> 40,91 Mo pour toute la version 0.5 (+0,16 Mo, `androidx.recyclerview` compris), debug 42,77 -> 43,64 Mo.

### Lire pendant l'envoi

Quand le fichier ne tient pas entièrement sur la TV, ou pour démarrer plus vite : la TV joue le `.part` en croissance via `http://127.0.0.1:8765/stream/<nom>`
(jeton aléatoire propre à l'exécution, valable seulement en local). Le premier `PUT` écrit `<nom>.meta` (taille totale) pour annoncer le bon `Content-Length`.
Si la lecture rattrape l'envoi, libVLC se met en pause tampon (`state:"buffering"`) et reprend seul ; si le téléphone quitte le réseau, la lecture continue
sur les octets déjà reçus puis attend, et l'envoi reprend au retour du téléphone. L'avance (« encore X min sans réseau ») est affichée sur la TV et le téléphone.
Limites : un MP4 dont l'index `moov` est en fin de fichier ne peut pas démarrer avant la fin (le téléphone le détecte et bascule en préchargement complet) ;
la position atteignable en avance rapide est bornée à ce qui a été reçu.

**Stockage sur clé USB** : voir `docs/STORAGE.md` (stratégie, matrice de cas, FAT32, retrait à chaud, formatage). `/api/info` porte maintenant `volume` et `duplicate` par fichier, `volumes[]` et
`target`. `503 {"error":"volume removed"}` pendant un envoi = la clé est retirée : réessayer, la reprise se fait à son retour. `413` = fichier trop gros pour la cible (FAT32) : ne pas réessayer.

Codes : `400` paramètre invalide, `401` PIN faux ou IP verrouillée, `404`, `405` (utiliser POST), `409` mauvais
offset (le corps donne `length`), `413` fichier trop gros pour le volume, `503` volume retiré/indisponible, `501` non supporté sur cet appareil, `507` espace insuffisant (moins de **1 Go** libre
après l'envoi, voir « Échange de fichiers » ; le corps donne `message`).

### Barre d'icônes de statut de la TV (toujours visible)

Les connexions et les modes de CastBridge-TV ne sont plus annoncés par des bandeaux fugitifs : une **barre d'icônes permanente** (colonne en haut à droite, sous l'horloge, sur l'accueil, les réglages et par-dessus une vidéo) montre une icône par connexion ou mode actif, tant qu'il est actif. Elle remplace les anciens badges séparés (Internet, passerelle, SSH) : une seule colonne, rien qui se chevauche. Chaque icône = un pictogramme (le « quoi ») + une petite marque de technologie en bas à droite (le « comment »), et éventuellement une seconde marque plus petite en bas à gauche.

| Icône | Signification | Marque de technologie |
|---|---|---|
| Wi-Fi / Ethernet / sans Internet / passerelle | **Internet** de la TV (voir ci-dessous) | (le pictogramme est la technologie) |
| Téléphone | un téléphone de confiance connecté (un seul par téléphone, même en Bluetooth + Wi-Fi : la meilleure technologie est affichée, l'autre en petit) | Bluetooth, Wi-Fi, Wi-Fi Direct |
| Télécommande | la télécommande du téléphone est utilisée | Bluetooth, Wi-Fi |
| Clé à molette | sessions **SSH** (nombre dans l'étiquette) | Wi-Fi (réseau local) ou Bluetooth (tunnel) |
| Passerelle | le téléphone partage son Internet à la TV | Bluetooth |
| Clé USB | une clé USB est branchée | USB |
| Wi-Fi Direct | le groupe Wi-Fi Direct de la TV est actif | Wi-Fi Direct |
| Quiz / Échecs | un joueur connecté à la salle (une icône par joueur) | Wi-Fi |
| Téléchargements | un téléchargement est en cours | — |
| Bouclier | mode enfant (contrôle parental) actif | — |

États : **connecté** (plein) ; **connexion…** (estompé + point doré) ; **reconnexion** (estompé + point doré + libellé « reconnexion » : une coupure brève ne retire pas l'icône, elle n'est retirée qu'après 20 s d'absence stable, et reprend son « depuis » si la liaison revient avant) ; **erreur** (liseré rouge + point rouge, jamais cachée par le « +N »). L'état n'est jamais porté par la seule couleur.
- Au plus **6 icônes**, puis une pastille « +N ». L'ordre est stable (Internet, téléphones, télécommande, SSH, passerelle… puis par ordre d'arrivée) et ne change ni selon l'état ni selon l'arrivée.
- Le libellé français court (nom du téléphone, tronqué à 24 caractères, sans caractère de contrôle) n'apparaît que sur une icône **nouvelle ou qui vient de changer** (4 s, avec une brève animation d'apparition), **ayant le focus**, ou quand le panneau est ouvert. Plus de bandeau « X connecté » : l'apparition de l'icône suffit. Les avertissements (refus, erreur, « nouvelle application ») restent des messages ponctuels.
- **Télécommande** : la barre ne prend jamais le focus pendant une vidéo ni dans les autres écrans. Sur l'**accueil**, HAUT depuis l'en-tête (« prêt · code ») atteint la barre ; **OK** ouvre le panneau **« Connexions »** : pour chaque connexion, nom, technologie (et la seconde), « depuis N min », latence quand elle est connue, état ; actions : **« Retirer ce téléphone »** (avec la confirmation habituelle ; « Annuler » est sélectionné) et **« Arrêter SSH »**. BAS/RETOUR rend le focus à l'accueil.
- Logique pure et testée : `core/.../status/StatusIcons.kt` (`StatusIconModel`, `StatusIconsTest`). Alimentée par le service : Internet (`NetStateTracker`), téléphones (CBTH et jeton HTTP, bail de 10 min renouvelé à chaque signe de vie), télécommande, SSH (`SshPolicy.onSessions`, tunnel Bluetooth compté à part), puis relevé toutes les 5 s de la passerelle, de Wi-Fi Direct, des clés USB, des téléchargements, du mode enfant et des salles Quiz/Échecs. Le passage par le tunnel API Bluetooth (branche `claude/bt-everything`) se branche en appelant `phoneSeen(adresse, Tech.BLUETOOTH_TUNNEL)` : aucune dépendance aujourd'hui.
- Limites connues : un téléphone n'ayant parlé qu'en Bluetooth (sans HTTP) reste affiché pendant le bail (10 min) après son départ ; la technologie d'une requête HTTP est « Wi-Fi » (l'adresse source n'est pas transmise au contrôle du jeton ; `Tech.fromIp` reconnaît 192.168.49.x = Wi-Fi Direct pour le jour où elle le sera). Pas de pastille « mise à jour disponible » ni « diffusion » pour l'instant (types prévus dans le modèle).
- Confidentialité : étiquettes seulement à l'écran et dans `/api/connections` ; ni PIN, ni jeton, ni adresse Bluetooth ; aucune nouvelle télémétrie.

#### Icône « Internet » (toujours visible)

L'icône Internet de la barre (anciennement un badge à part) dit **par où** la TV a vraiment Internet :
« Internet : Wi-Fi », « Internet : Ethernet », « Internet : via le téléphone » (passerelle Bluetooth, voir ci-dessous) ou, en rouge, « Pas d'Internet ». Au démarrage : « vérification… » (estompée).
- Le badge se fonde sur le test 204 existant (`connectivitycheck.gstatic.com`), pas sur l'état du lien : un Wi-Fi « connecté » sans Internet n'est jamais affiché comme connecté.
- **Sonde coupée par défaut** (réglage `netProbe`, désactivé) : la TV n'envoie plus périodiquement ce test 204 à un tiers. L'état vient alors des rappels système (`ConnectivityManager`, capacité `NET_CAPABILITY_VALIDATED`) et de l'état de la passerelle, sans trafic sortant : « Internet : Wi-Fi » si le système a validé le réseau ; sans validation, le badge reste « vérification… » (« Wi-Fi connecté · Internet non vérifié » dans le résumé), jamais « Pas d'Internet » en rouge ; « Pas d'Internet » seulement s'il n'y a aucun réseau. La latence n'est plus mesurée (`direct.ms` = 0 dans `/api/net`). Le vrai test 204 ne part que sur action manuelle (écran « Tests Internet »), si `netProbe` est activé, ou tant que l'assistance à distance est activée (conditions acceptées) : elle a besoin de savoir si Internet est joignable.
- Règle : réseau de la TV d'abord s'il répond, sinon passerelle du téléphone si elle répond, sinon « Pas d'Internet ». Quand les deux répondent, le badge montre le réseau de la TV ; la passerelle disponible reste visible dans « Internet » (réglages) et dans `/api/net` (`gateway.alsoAvailable`).
- Anti-clignotement (Wi-Fi instable) : 1 succès = en ligne tout de suite, 2 échecs de suite = hors ligne ; un changement de chemin attend 8 s d'affichage minimum (sauf la sortie de « Pas d'Internet »).
- Cadence : 60 s tant qu'Internet marche ; 10 s, puis 15 s, puis 30 s (plafond) tant qu'il n'y en a pas ; nouveau test aussitôt (1,5 s) après une perte/retour de réseau Android ou la connexion/déconnexion du téléphone-passerelle. Aucune télémétrie nouvelle : l'évènement `connectivity_check` est inchangé.
- Logique pure : `core/.../net/NetState.kt` (testée par `NetStateTest`).
- Le badge n'est affiché que dans l'écran principal de CastBridge-TV (accueil, bibliothèque, lecteur, réglages), pas dans les écrans de jeux/quiz/apprendre.

## 4. Envoyer un fichier, reprise incluse

```sh
F=film.mp4; N=$(basename "$F"); T=$(stat -f%z "$F" 2>/dev/null || stat -c%s "$F")
# reprise : demander où en est la TV
OFF=$(tv "$TV/api/part?name=$N" | sed -E 's/.*"length":([0-9]+).*/\1/')
tail -c +$((OFF+1)) "$F" | tv -X PUT --data-binary @- -H "Content-Type: application/octet-stream" \
  "$TV/upload/$N?offset=$OFF&total=$T"
```

Si la connexion tombe, relancer les mêmes commandes : l'envoi repart de `length`. Le fichier apparaît sous son nom
définitif quand `length == total`. Un `409` signifie que l'offset envoyé n'est pas celui de la TV : reprendre à
`length`. Le même `.part` peut être repris par le canal Bluetooth ou par la page web.

Commandes courantes :

```sh
tv $TV/api/info                                  # inventaire + espace libre
tv -X POST "$TV/api/play?name=$N"                # lecture
tv -X POST "$TV/api/volume?pct=30"               # volume
tv -X POST "$TV/api/rename?name=a.mp4&to=b.mp4"
tv -X POST "$TV/api/delete?name=b.mp4"
tv $TV/api/sysinfo
```

## 5. Autres canaux (résumé)

- **Page web** : `http://TV:8765/` depuis un navigateur ; envoi par morceaux de 4 Mo, repris seul.
- **Bluetooth (RFCOMM)** : appairer le téléphone à la TV, envoi depuis l'app téléphone (onglet CastBridge TV > Bluetooth).
  Protocole `BtProtocol` (module `:core`) : `CBT1` + PIN + nom + taille, reprise au `.part` existant. Non pilotable en HTTP.
- **Clé USB / OTG** : touche MENU de la télécommande > « USB… » (scan des volumes, ou sélecteur de dossier système si la TV en
  a un), ou `POST /api/usb/import`. Le scan des volumes lit uniquement le **dossier de l'app sur la clé**
  (`<clé>/Android/data/castbridge.receiver/files/`), qu'un PC peut remplir ; un dossier quelconque de la clé n'est lisible que
  via le sélecteur système.

### Bluetooth : le lien le plus rapide possible

Mesure réelle (Mac -> TV, CBT1 sur RFCOMM, écriture synchrone) : **110 ko/s**, intégrité SHA-256 correcte. Le Bluetooth classique plafonne vers 100 à 250 ko/s en
pratique : un film de 1,5 Go y prend 2 à 4 heures. D'où deux leviers :

1. **Le Bluetooth sert de canal de contrôle, les données passent en Wi-Fi quand c'est possible** (comme Quick Share). L'app du téléphone ouvre le service Bluetooth
   habituel et envoie `CBTN` + PIN (même vérification et même verrouillage que CBT1) ; la TV répond ses adresses IP et, si son groupe Wi-Fi Direct existe ou peut être
   créé, son nom de réseau et son mot de passe. Puis, dans l'ordre (`LinkPlanner`) :
   - **(i) réseau commun** : une adresse de la TV répond (`GET /api/hello`, 1,5 s) -> envoi HTTP habituel, reprenable, **plusieurs Mo/s** (sur cette TV, limité par
     l'écriture de la clé USB, ~2,2 Mo/s) ;
   - **(ii) Wi-Fi Direct de la TV** : le téléphone rejoint le groupe `DIRECT-CB-…` (Android affiche une demande de validation **sur le téléphone**), puis même envoi HTTP
     vers `192.168.49.1:8765`, plusieurs Mo/s. La TV ne crée ce groupe à la demande d'un téléphone que si le propriétaire a activé Wi-Fi Direct, ou si la TV n'a **aucun**
     réseau (un groupe peut perturber son propre Wi-Fi) ;
   - **(iii) point d'accès local du téléphone (LocalOnlyHotspot) : non livré** : la TV devrait rejoindre ce réseau, et une app Android ne peut le faire que par
     `WifiNetworkSpecifier`, qui exige une **validation à l'écran de la TV** (et `WifiNetworkSuggestion` n'est ni immédiat ni garanti sans Internet). Ce ne serait pas
     automatique : documenté plutôt que livré ;
   - sinon **Bluetooth (CBT1)**, comme avant. Une TV plus ancienne répond `ERR_MAGIC` à `CBTN` : le téléphone passe directement en CBT1. Un envoi HTTP qui échoue
     6 fois de suite sans progresser rebascule en Bluetooth (le fichier reprend là où il en était : même `.part`).
   Tout reste authentifié (PIN), aucune adresse n'est acceptée autrement qu'en IPv4 littérale (jamais un nom à résoudre). L'écran d'envoi Bluetooth du téléphone
   affiche le lien utilisé (« Lien : Wi-Fi Direct »...).
2. **Bluetooth pur optimisé** : côté TV, les petits paquets RFCOMM sont regroupés dans un tampon de **256 Ko** avant chaque écriture disque (au lieu d'une écriture par
   paquet), progression signalée tous les 256 Ko ; en cas de coupure, le tampon est vidé sur le disque (le `.part` garde tout ce qui est arrivé, test
   `BtLinkTest.bufferedReceiveKeeps…`). Côté téléphone, écritures de 64 Ko, aucun `flush` par bloc. Pas d'acquittements fenêtrés : RFCOMM est déjà fiable et ordonné.

**Étudié, non livré (à décider sur mesures réelles)** : plusieurs sockets RFCOMM en parallèle sur le même fichier : elles partagent le même lien radio ACL entre les
deux appareils, le débit total ne dépasse pas celui d'une seule (au mieux on masque une latence de traitement), pour une reprise et un réassemblage nettement plus
complexes ; **L2CAP CoC sur BLE 2M PHY** (API 29+) : ~150 à 400 ko/s en théorie, dépend du contrôleur Bluetooth de la TV (inconnu), et n'apporterait qu'un facteur
1,5 à 2 là où le Wi-Fi apporte un facteur 10 à 30. Débits attendus : RFCOMM ~100-250 ko/s, L2CAP/2M ~150-400 ko/s, Wi-Fi Direct ou réseau commun plusieurs Mo/s.
**Compatibilité** : CBT1 est inchangé (l'outil Mac `tools/cbt-rfcomm/main.swift` d'Esaie continue de fonctionner) ; `CBTN` est une requête en plus, sur le même service.

## 6. Wi-Fi Direct

Sur la TV : MENU > « Wi-Fi Direct : activer » (désactivé par défaut : créer un groupe peut perturber le Wi-Fi de la TV).
La TV affiche le nom du réseau (`DIRECT-CB-CastBridge`), le mot de passe et le PIN. Connecter le téléphone ou l'ordinateur à ce
réseau, puis utiliser `http://192.168.49.1:8765`.

## 7. Limites (non négociables)

- L'app tourne avec **son propre uid Android**, sans root : elle ne peut ni redémarrer la TV, ni changer les réglages système,
  ni écrire hors de son stockage privé, ni lire celui des autres apps. `POST /api/restart` ne relance que l'application.
- Le volume peut être ignoré par une TV à volume fixe (HDMI-CEC / ampli externe).
- La batterie est `null` sur une TV (pas de batterie).
- Sans PIN valide, aucun accès aux fichiers. Le PIN n'est jamais journalisé. Ne pas exposer le port 8765 sur Internet.
- **Accès SSH / shell** : voir §9 (désactivé par défaut, clés publiques seulement, réseau local ou tunnel Bluetooth).

## 8. Pour un agent (checklist)

1. Découvrir la TV (mDNS ou IP) puis `GET /api/hello`.
2. Demander le PIN à l'humain (il est sur l'écran de la TV). Ne jamais le deviner.
3. Envoyer `X-CB-Pin` sur chaque appel ; sur `401` ne pas insister (verrouillage 60 s).
4. Avant un gros envoi : `GET /api/info` (`free`), puis `PUT /upload` avec reprise sur `length`.
5. Toujours confirmer avec l'humain avant `delete`, `rename` ou `restart`.

## 9. SSH (administration par shell)

Serveur SSH embarqué (Apache MINA SSHD, module `:sshd`), **désactivé par défaut** : MENU de la TV > « SSH : activer », ou `POST /api/ssh/enable[?minutes=N]`
(derrière le PIN). Port **2222**, authentification **par clé publique uniquement** (`POST /api/ssh/key?key=<ligne authorized_keys>`, `POST /api/ssh/key/remove?fp=`,
`GET /api/ssh`), clients du réseau local seulement, verrouillage 60 s après 5 échecs, pas de redirection de ports, arrêt automatique après 30 min sans session
(`idleMinutes`). Le shell tourne avec l'uid de l'app (pas de root) ; SFTP/SCP confinés au dossier de l'app. L'empreinte de la clé d'hôte s'affiche sur l'écran
d'attente de la TV : vérifiez-la à la première connexion.

```sh
curl -sS -H "X-CB-Pin: $PIN" -X POST "$TV/api/ssh/key" --data-urlencode "key=$(cat ~/.ssh/id_ed25519.pub)" -G
curl -sS -H "X-CB-Pin: $PIN" -X POST "$TV/api/ssh/enable?minutes=60"
ssh -p 2222 tv@192.168.0.117          # le nom d'utilisateur est libre
```

## 10. SSH sans réseau (Bluetooth)

Pour garder l'accès SSH quand le téléphone/l'ordinateur et la TV **ne partagent aucun réseau**. Principe : un **tunnel d'octets** par Bluetooth ; SSH passe
dedans **inchangé de bout en bout** (chiffrement, clé d'hôte, authentification par clé : rien n'est déchiffré ni contourné en chemin).

```
Termux / ordinateur --TCP--> téléphone 127.0.0.1:2222 --RFCOMM « CastBridge SSH »--> TV --TCP--> 127.0.0.1:2222 (serveur SSH)
ordinateur Linux (ProxyCommand) --------------RFCOMM « CastBridge SSH »--> TV --TCP--> 127.0.0.1:2222
```

- **TV** : quand SSH est activé, un **second service RFCOMM** « CastBridge SSH » (UUID `7c5e3b9a-4d2f-4c61-9b0e-cb0000000002`, distinct du service fichiers `…0001`)
  accepte les appareils **appairés** ; chaque connexion ouvre une connexion TCP vers `127.0.0.1:2222` et relaie dans les deux sens (tampons de 32 Ko, deux fils,
  fermeture des deux côtés dès que l'un se termine), **2 connexions simultanées au plus**. Le service s'arrête avec SSH (désactivation ou délai d'inactivité).
  Bandeau sur l'écran d'attente : « SSH par Bluetooth : prêt » / « connecté (nom de l'appareil) » ; `GET /api/ssh` -> `bluetooth: {listening, active:[noms]}`.
- **Verrouillage par appareil** : toutes les connexions tunnelisées arrivent de `127.0.0.1` ; sans précaution, 5 échecs d'un appareil verrouilleraient tous les
  autres. Le tunnel choisit son port source local, l'inscrit dans un registre « port local -> `bt:<adresse>` » **avant** de se connecter, et le serveur SSH compte
  les échecs par cette identité (`PeerRegistry`, tests `BtTunnelSshTest.lockoutIsPerBluetoothDevice`).
- **Téléphone** : app CastBridge > CastBridge TV > Bluetooth > « Passerelle Bluetooth » (SSH et API, §11) : choisir la TV appairée, Démarrer (service de premier plan). Le
  téléphone écoute sur `127.0.0.1:2222` ; option **désactivée par défaut** « Exposer aussi sur le réseau local / point d'accès du téléphone » (avertissement :
  tout appareil de ces réseaux atteint alors le SSH de la TV, qui exige toujours une clé). Puis :
  - depuis Termux sur le téléphone : `ssh -p 2222 tv@127.0.0.1` ;
  - depuis un ordinateur connecté au point d'accès du téléphone (option cochée) : `ssh -p 2222 tv@<adresse du téléphone affichée>`.
- **Ordinateur Linux** (pile BlueZ, appairé avec la TV) : `tools/bt-ssh-bridge.py` (bibliothèque standard Python : `socket.AF_BLUETOOTH`/`BTPROTO_RFCOMM`) comme
  `ProxyCommand` :
  ```sh
  ssh -o ProxyCommand="python3 tools/bt-ssh-bridge.py AA:BB:CC:DD:EE:FF" tv@castbridge
  ```
  Le canal RFCOMM est trouvé par `--channel N`, sinon `sdptool`, sinon en sondant les canaux 1 à 30 (le serveur SSH parle le premier : « SSH-… ») ; il est mis en cache
  dans `~/.cache/castbridge-bt-ssh.json`. **macOS / Windows** : Python n'y a pas de socket RFCOMM dans sa bibliothèque standard : passer par la passerelle du téléphone
  (point d'accès du téléphone + option d'exposition).
- **Débit attendu** : celui du Bluetooth classique, environ **100 à 300 ko/s** (moins à travers les murs) : confortable pour un shell ou des commandes, **lent pour
  SFTP/scp** (un fichier de 100 Mo prend 6 à 15 min) : pour les gros fichiers, utiliser le Wi-Fi ou l'envoi Bluetooth de l'app.
- **Non validé sans la TV** : ouverture du second service RFCOMM par GaiaOS (deux services simultanés), débit réel, comportement si le téléphone et la TV sont
  aussi connectés en audio Bluetooth.

## 11. Tout par Bluetooth (API de la TV sans Wi-Fi)

Besoin : le Wi-Fi n'est pas fiable, mais tout ce que le téléphone ou un ordinateur fait par HTTP (bibliothèque, envoi reprenable, APK et installation,
télécommande, contrôle parental, clé SSH, capture d'écran…) doit marcher **à l'identique** par Bluetooth. Principe : le **même tunnel d'octets** que pour SSH (§10),
mais vers l'API HTTP de la TV. Rien n'est réécrit côté TV : le serveur HTTP (port 8765) fait **toujours toute la vérification** (en-tête `X-CB-Pin`, jeton
`X-CB-Token`, code parental, règles par route) ; le tunnel ne fait que porter les octets.

```
curl / app téléphone --TCP--> 127.0.0.1:18765 --RFCOMM « CastBridge API »--> TV --TCP--> 127.0.0.1:8765 (API HTTP, inchangée)
                              (téléphone : passerelle ; Mac : cbt-rfcomm proxy ; Linux : bt-ssh-bridge.py --service api)
```

**Services RFCOMM de la TV** (sockets sécurisés, appareils appairés seulement) : `…0001` fichiers (CBT1/CBTN/CBTH/CBTR/passerelle), `…0002` SSH (octets SSH bruts),
`…0003` **API** (UUID `7c5e3b9a-4d2f-4c61-9b0e-cb0000000003`, nom SDP « CastBridge API »). Le service API répond d'abord **1 octet d'état** (0 = ok, 1 trop de liaisons,
2 appareil non autorisé, 3 serveur local arrêté, 4 erreur interne), puis relaie du HTTP brut. Le service SSH reste sans octet d'état (compatibilité avec `ssh`, `bt-ssh-bridge.py`).

**Ports locaux sur le téléphone** : SSH `127.0.0.1:2222`, API `127.0.0.1:18765`. L'API n'est **jamais** exposée sur le réseau (seul le SSH offre l'option « réseau local »).

**Liaison partagée (v2).** Quatrième service `…0004` (« CastBridge API v2 ») : une seule liaison RFCOMM par téléphone, réutilisée par toutes les requêtes HTTP (trames, garde-vivant, réessais bornés) ; le service `…0003` reste pour les anciens téléphones. Détail : `docs/BT-PLUG-AND-PLAY.md`, « Liaison API persistante ».

### Qui peut ouvrir une liaison API (règle de confiance)
1. L'appareil doit être **appairé** (Android l'affirme par le socket sécurisé ; l'adresse n'est jamais celle que l'appareil « annonce »).
2. Et **soit** l'interrupteur de la TV « API par Bluetooth » est actif (**actif par défaut** : le service fichiers accepte déjà un appareil appairé avec le bon code ;
   MENU > Administration > « Couper l'API par Bluetooth », ou `POST /api/bluetooth/tunnel/disable`), **soit** l'appareil est un **téléphone de confiance**
   (`docs/BT-PLUG-AND-PLAY.md`) : coupée, l'API reste donc joignable par les téléphones déjà approuvés, jamais par un autre appareil appairé.
3. Puis le serveur HTTP demande, comme en Wi-Fi, le **code de la TV** (installation fraîche : un appareil appairé avec le bon code fonctionne ; un téléphone de confiance
   utilise son jeton, qui n'ouvre toujours pas `/api/ssh*`, `/api/apk/install`, `/api/update/install` : PIN seulement).

### Téléphones synchronisés : 8 au plus
Une TV se synchronise avec **8 téléphones au plus** (`TrustRegistry.MAX_PHONES`). Un 9e téléphone n'entre qu'après le retrait d'un existant, choisi par le propriétaire sur l'écran « Téléphones synchronisés » de la TV (menu de la TV, ou « Connexions »). Ce n'est pas l'administration : le PIN d'administration, ses jetons et ses routes ne changent pas (un téléphone de confiance ne peut toujours pas ouvrir `/api/ssh`, l'installation d'APK ni l'activation). Retirer un téléphone lui coupe ses jetons aussitôt. Un téléphone bloqué (3 refus ou annulations) ne peut pas rouvrir l'écran de remplacement pendant 10 minutes. Détails, protocole et tests : `docs/BT-PLUG-AND-PLAY.md`.

### Verrouillage par appareil
Toutes les liaisons arrivent de `127.0.0.1` ; sans précaution, 5 codes faux d'un appareil verrouilleraient le Wi-Fi et tous les autres appareils. Le tunnel inscrit « port local
-> `bt:<adresse>` » **avant** de se connecter ; le serveur HTTP (`ReceiverServer`, paramètre `peers`) voit alors cette connexion venir d'une adresse virtuelle stable par appareil
(`240.77.x.y`, jamais routée) et `PinGuard` compte les échecs par `bt:<adresse>`. Un appareil verrouillé (60 s après 5 échecs) ne gêne ni les autres appareils Bluetooth, ni le Wi-Fi.
Tests : `BtApiTunnelTest.pinLockoutIsPerBluetoothDevice`, `anotherDeviceCannotUseTheLockedDevicesIdentity`.

### Gros transferts, liaisons, délais
- `PUT /upload/<nom>?offset=&total=` (reprenable : le `.part` de la TV est le même que pour le Wi-Fi et CBT1) et `Range` sur `/stream/…` passent tels quels (test : 6 Mo coupés au
  milieu puis repris, lecture `Range`). **Contre-pression** : un tampon fixe de 16 Ko par sens, écritures bloquantes : un disque lent de la TV ralentit l'envoi, rien ne s'accumule.
- Jusqu'à **4 liaisons simultanées** (HTTP ouvre plusieurs connexions). Silence > **30 s** : liaison fermée (comme `BtServer`) ; **10 min** pendant qu'un transfert ou un déplacement
  tourne sur la TV. Quand la TV est pleine, la liaison **muette depuis plus de 60 s** est fermée pour faire de la place : **jamais d'emplacement bloqué**.
- Débit : celui du Bluetooth classique (≈ 100 à 300 ko/s). Un film se copie mieux par Wi-Fi ; la bibliothèque, la télécommande, les réglages et un APK de 30 Mo (≈ 2 à 5 min) sont adaptés.

### Messages d'erreur
Chaque refus ou échec produit **une ligne de journal INFO** (étiquettes `CastBridgeApiBt`, `CastBridgeSSH` côté TV ; `CastBridgeSshGw` côté téléphone ; jamais de code, jeton ni clé) **et** un
message en français : sur la TV (MENU > Connexion & réglages : « API par Bluetooth », « SSH par Bluetooth », et `GET /api/bluetooth/tunnel`, `GET /api/ssh` -> `bluetooth.lastError`),
et sur le panneau « Passerelle Bluetooth » du téléphone.

### Depuis le téléphone (CastBridge)
Onglet CastBridge TV > Bluetooth > **Passerelle Bluetooth** : choisir la TV, cocher API et/ou SSH, Démarrer. Quand le téléphone n'a **aucun** chemin IP vers la TV
(ni réseau commun ni Wi-Fi Direct), l'app démarre elle-même la passerelle API (app au premier plan) et l'utilise comme route `BluetoothTunnel`
(`LinkPlanner`, `PhoneLink`, `TvDiscovery`) : bibliothèque, télécommande, réglages parentaux, assistant, envoi de fichier ou d'APK (HTTP reprenable) et son installation
fonctionnent alors par Bluetooth. L'envoi de fichier simple garde le chemin CBT1 (inchangé).

### Depuis un Mac (sans téléphone) : `tools/cbt-rfcomm`
```sh
swiftc -O -framework IOBluetooth tools/cbt-rfcomm/main.swift -o cbt-rfcomm      # une fois ; Mac appairé avec la TV
./cbt-rfcomm proxy AA:BB:CC:DD:EE:FF 18765 api &          # 127.0.0.1:18765 -> API de la TV (aucun code saisi ici)
curl -H "X-CB-Pin: $PIN" http://127.0.0.1:18765/api/hello
CB_PIN=$PIN tools/cbt-rfcomm/bt-api.sh upload film.mp4     # envoi reprenable ; relancer = reprise
CB_PIN=$PIN tools/cbt-rfcomm/bt-api.sh upload CastBridge-TV.apk
CB_PIN=$PIN tools/cbt-rfcomm/bt-api.sh install CastBridge-TV.apk
CB_PIN=$PIN tools/cbt-rfcomm/bt-api.sh ssh-key ~/.ssh/id_ed25519.pub     # active SSH et inscrit la clé, sans réseau
./cbt-rfcomm proxy AA:BB:CC:DD:EE:FF 2222 ssh &; ssh -p 2222 tv@127.0.0.1
```
Linux : `python3 tools/bt-ssh-bridge.py AA:BB:CC:DD:EE:FF --service api --listen 18765` (même usage de `curl`).

### Diagnostic de l'échec observé (SSH par Bluetooth fermé avant la bannière)
Constat sur le matériel : passerelle « Active », 0 connexion, 0 ko / 0 ko, **aucune ligne** `CastBridgeSshGw`, mais la pile RFCOMM du téléphone journalise une liaison vers la TV
« fermée par le pair ». Lecture du code : (1) le téléphone ne journalisait **rien** quand la liaison s'ouvrait puis se fermait aussitôt (chemin « succès » silencieux) ; (2) la TV fermait
la liaison **sans dire pourquoi** à quatre endroits (emplacements pleins, serveur SSH injoignable, exception, arrêt) ; (3) un emplacement pouvait rester pris indéfiniment (liaison dont
le téléphone avait disparu sans fermeture RFCOMM : aucune limite de silence, le sshd garde 15 min une session déjà authentifiée ; avec `maxConnections = 2`, deux liaisons zombies
fermaient ensuite toutes les nouvelles **sans bruit**) ; (4) `connect()` Bluetooth sans délai. La cause exacte sur la TV du propriétaire ne peut pas être établie sans ses journaux :
la liaison s'ouvre côté téléphone (donc le service est publié et le jumelage valide) et la TV la referme avant tout octet, ce qui désigne **emplacements pleins/zombies** ou
**serveur SSH injoignable** ; les deux sont maintenant visibles et corrigés (journal TV `ssh: link from … refused: <raison>`, message sur le panneau du téléphone « la TV a fermé la
liaison avant toute donnée : … », éviction de la liaison muette, délai de 16 min, délai de connexion de 20 s, nouvel essai avec un socket neuf).

