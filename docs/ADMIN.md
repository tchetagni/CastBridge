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
en-tête `X-CB-Pin: 123456` (recommandé) ou paramètre `?pin=123456`.
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

### Bibliothèque (TV, téléphone, page web)

- **Sur la TV** : bouton « Bibliothèque (OK) » de l'écran d'attente, ou touche bleue / GUIDE / signet / « menu du contenu » de la télécommande, ou MENU > Bibliothèque.
  En quittant une vidéo (RETOUR, fin du fichier, arrêt depuis le téléphone), la TV revient à la bibliothèque. Grille de cartes au D-pad (cadre bleu épais et
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
  une consommation de RAM plus élevée au repos pour une TV à ~330 Mo de RAM disponible ; `androidx.recyclerview` ajoute environ 0,4 Mo (voir le rapport de la branche).

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
- **Téléphone** : app CastBridge > CastBridge TV > Bluetooth > « Passerelle SSH Bluetooth » : choisir la TV appairée, Démarrer (service de premier plan). Le
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
