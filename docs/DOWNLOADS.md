# Téléchargements sur la TV (aria2)

La TV télécharge elle-même : un lien web (HTTP/HTTPS, FTP, SFTP, plusieurs connexions), un torrent (lien `magnet:` ou fichier
`.torrent`) ou un fichier Metalink. On pilote depuis le téléphone, la télécommande ou la page web ; les fichiers terminés
arrivent dans la bibliothèque. Le téléphone peut être éteint pendant ce temps.

## Pour l'utilisateur

- **Téléphone** : CastBridge > CastBridge TV > (PIN accepté) > **Téléchargements sur la TV**. Collez un lien (le type est
  reconnu tout seul), ou « .torrent » pour envoyer un fichier. Depuis le navigateur : **Partager > Télécharger sur la TV**
  (liens), et les liens `magnet:` / fichiers `.torrent` s'ouvrent directement avec CastBridge. Chaque téléchargement montre
  sa progression, sa vitesse, le temps restant et le nombre de sources ; Pause / Reprendre / Supprimer ; « Fichiers » pour
  choisir les fichiers d'un torrent. Section **Terminés** : « Regarder sur la TV ».
- **TV** : MENU > **Téléchargements** (tout se fait avec les flèches et OK) ; « Ajouter un lien » ouvre le clavier de la TV.
  Un message « Téléchargement terminé : … » s'affiche quand un fichier arrive.
- **Page web** `http://TV:8765/` : section « Téléchargements ».
- La première fois, un avertissement demande de ne télécharger **que ce qu'on a le droit de télécharger**. CastBridge ne
  propose aucun catalogue ni moteur de recherche de contenus.

## Architecture

```
téléphone / page web / écran TV ──(HTTP, PIN)──> ReceiverServer ──ApiExtension──> DownloadManager ──JSON-RPC 127.0.0.1──> aria2c
                                                                                   │  (liste des téléchargements,      (processus natif
                                                                                   │   règles d'espace, clé USB,         supervisé)
                                                                                   │   rangement dans la bibliothèque)
                                                                                   └── VolumeRegistry / StoragePolicy (volumes de l'app)
```

| Élément | Fichier | Rôle |
|---|---|---|
| Moteur | `receiver/src/main/jniLibs/<abi>/libaria2c.so` | aria2 1.37.0 compilé par `tools/build-aria2-android.sh` |
| Supervision | `core/.../dl/Aria2Supervisor.kt` | lance aria2, attend que son RPC réponde, le relance s'il meurt (délai croissant, abandon après 6 arrêts en 10 min), arrêt propre (`saveSession` + `shutdown`, puis forcé, et toujours `waitFor` : pas de zombie) |
| Client RPC | `core/.../dl/Aria2Rpc.kt` | `addUri`, `addTorrent`, `addMetalink`, `tellActive/Waiting/Stopped/Status`, `forcePause`, `unpause`, `forceRemove`, `changePosition`, `changeOption`, `changeGlobalOption`, `getGlobalStat`, `getFiles`, `getPeers`, `getServers`, `purgeDownloadResult`, `removeDownloadResult`, `saveSession`, `shutdown` |
| Gestionnaire | `core/.../dl/DownloadManager.kt` | sa propre liste (source de vérité, `filesDir/aria2/downloads.json`), aria2 n'est que l'exécutant : une tâche perdue par aria2 (plantage avant l'écriture de sa session) est relancée dans le même dossier et reprend grâce au fichier `.aria2` |
| Hôte Android | `receiver/.../TvDownloads.kt` | une instance par processus, **indépendante de l'activité** ; verrous Wi-Fi/réveil et service de premier plan `DownloadService` uniquement pendant un téléchargement |
| Écran TV | `receiver/.../DownloadsActivity.kt` | liste navigable à la télécommande |
| Téléphone | `sender/.../DownloadsScreen.kt`, `ShareToTvActivity.kt`, `core/.../dl/DownloadsClient.kt` | écran Compose, cible de partage |

### Où vont les fichiers

Pendant le téléchargement : `<volume>/.cb-downloads/<id>/` (dossier caché : la bibliothèque ne liste que les fichiers du
haut du volume, donc ni fichier à moitié écrit ni fichier de contrôle `.aria2` n'y apparaît). À la fin : vidéos, musiques,
sous-titres, images et APK sont **déplacés** en haut du même volume (renommage instantané, même système de fichiers ; un
nom déjà pris devient « nom (2).ext ») ; les autres fichiers d'un torrent (`.nfo`, `.txt`…) restent dans le dossier caché
et sont effacés avec « Supprimer le fichier ». Un torrent partagé après la fin (option) n'est rangé qu'à la fin du partage.

### Place disque (même règle que les envois)

- Avant d'ajouter : taille connue par `HEAD` (puis `GET Range: bytes=0-0`) pour HTTP(S), par le `.torrent`, par `xl=`
  d'un magnet ou par le Metalink. Le volume doit garder **1 Go libre à la fin**, en comptant ce que les téléchargements déjà
  en cours y écriront encore. Volume = cible de stockage de l'app (« auto » : la clé USB d'abord) ; s'il est trop plein, un
  autre volume est choisi (message) ; sinon refus clair (« Pas assez de place : ce téléchargement fait 4,2 Go et la TV doit
  garder 1,0 Go libre… »). Le dossier choisi par le sélecteur système (SAF) n'est jamais utilisé (pas de chemin pour aria2).
- Magnet / lien vers un `.torrent` : aria2 s'arrête après les métadonnées (`--pause-metadata`) ; la TV vérifie alors la
  taille réelle, garde le volume, **en change** (`changeOption dir`) ou met en pause « pas assez de place ».
- Pendant : toutes les 2 s, par volume et par ordre de priorité, un téléchargement qui ferait passer le volume sous 1 Go
  est mis en pause (« En pause : pas assez de place ») et reprend seul quand la place revient (+256 Mo de marge pour ne
  pas osciller). Taille inconnue (FTP/SFTP) : pause dès que le volume lui-même passe sous 1 Go.
- Clé retirée : les téléchargements qui y étaient passent « En pause : clé USB retirée » (ou sont marqués ainsi si aria2 a
  déjà échoué en écriture) et **reprennent à son retour**, dans le même dossier.

### Options d'aria2 (1 Go de RAM, clé USB lente)

| Option | Pourquoi |
|---|---|
| `--enable-rpc --rpc-listen-all=false --rpc-listen-port=6800` (autre port libre si occupé) | RPC sur 127.0.0.1 uniquement |
| secret RPC aléatoire (24 octets) à chaque démarrage, dans un fichier de conf `0600` **effacé dès qu'aria2 répond** | jamais sur la ligne de commande (`/proc/<pid>/cmdline`), jamais journalisé (les lignes d'aria2 sont en plus nettoyées) |
| `--rpc-allow-origin-all=false`, `--rpc-max-request-size=8M` | pas d'accès depuis une page web ; un `.torrent` de 4 Mo en base64 passe |
| `--input-file` / `--save-session` = `filesDir/aria2/aria2.session`, `--save-session-interval=30` | reprise après redémarrage de l'app ou de la TV |
| `--continue=true` | reprend les fichiers partiels |
| `--max-concurrent-downloads=2` (1 à 3, réglable) | la clé USB lente se partage mal ; chaque tâche coûte des tampons |
| `--max-connection-per-server=8 --split=8 --min-split-size=4M` | débit HTTP multi-connexions, sans découper les petits fichiers |
| `--stream-piece-selector=inorder` | en HTTP/FTP les morceaux sont écrits près du début : exFAT/FAT ne remplissent pas de zéros un grand trou (voir limites) |
| `--file-allocation=none` | exFAT n'a pas `fallocate` ; `prealloc` écrirait tout le fichier deux fois sur une clé à 2-15 Mo/s |
| `--disk-cache=8M` | écritures plus grosses et moins nombreuses sur la clé, pour 8 Mo de RAM |
| `--check-certificate=true --ca-certificate=filesDir/aria2/cacert.pem --min-tls-version=TLSv1.2` | le magasin de certificats de la TV (`AndroidCAStore`, système + utilisateur) exporté en PEM à chaque démarrage : OpenSSL n'a pas d'autre magasin sur Android |
| `--seed-ratio=0.0 --seed-time=0` (défaut) ; si « partager » est activé : `1.0` / `120` min | pas de partage après la fin par défaut ; `seed-time=0` arrête tout de suite |
| `--bt-max-peers=30` (défaut aria2 : 55) | moins de sockets et de mémoire |
| `--enable-dht=true --dht-file-path=filesDir/aria2/dht.dat --enable-dht6=false --bt-enable-lpd=false` | DHT IPv4 pour les magnets ; IPv6 rarement utile derrière une box |
| `--bt-save-metadata=true --rpc-save-upload-metadata=true` | métadonnées d'un magnet et `.torrent` envoyé conservés pour reprendre |
| `--pause-metadata=true` | contrôle de la place avant le vrai téléchargement |
| `--max-overall-upload-limit=512K` (réglable) | ne pas saturer la voie montante du foyer |
| `--max-tries=10 --retry-wait=15 --connect-timeout=30` | le Wi-Fi d'une TV va et vient |
| (pas de `--async-dns`) | une version Android d'aria2 résout par défaut avec le résolveur d'Android (netd : suit les changements de réseau et le DNS privé) ; c-ares reste compilé (`--async-dns=true --async-dns-server=` possible) |
| `--stop-with-process=<pid de l'app>` | aria2 s'arrête seul si l'app meurt : jamais d'orphelin |
| `--console-log-level=warn --show-console-readout=false --summary-interval=0` | seulement les avertissements dans logcat |
| `--allow-overwrite=false --auto-file-renaming=true --no-netrc` | pas d'écrasement, pas de fichier de mots de passe implicite |

### Sécurité

- aria2 n'écoute que sur 127.0.0.1 ; tout passe par l'API CastBridge, **derrière le PIN** (`/api/downloads/*`).
- Options transmises par un client : **liste blanche** (`Aria2Config.TASK` / `GLOBAL`) avec contrôle des valeurs ; refus de
  `dir`, `on-*` (commandes), chemins (`log`, `conf-path`, `input-file`, `save-session`, `index-out`, `load-cookies`…) et de
  tout caractère de contrôle (un saut de ligne injecterait une option dans le fichier de session).
- Le dossier de chaque téléchargement est calculé par la TV à partir de ses volumes ; le client ne donne jamais de chemin
  (seulement `volume=auto` ou un identifiant de volume vérifié).
- Liens acceptés : `http`, `https`, `ftp`, `sftp`, `magnet` ; refus de `file:`, `content:`… et des adresses de la TV
  elle-même (`localhost`, `127.*`, `::1`), y compris après redirection lors de la mesure de taille.
- Les identifiants d'un lien SFTP/FTP (`user:mot-de-passe@`) ne sont jamais affichés.
- Metalink : `DOCTYPE`/entités refusés. `.torrent` : 4 Mo au plus.

## API (`/api/downloads`, en-tête `X-CB-Pin`)

| Méthode, route | Paramètres | Réponse |
|---|---|---|
| `GET /api/downloads` | | `engine` (`available`, `running`, `message`, `version`), `warningAccepted`, `warning`, `global` (vitesses, limites, partage), `tasks[]` (`id`, `name`, `state`, `label`, `total`, `done`, `down`, `up`, `eta`, `connections`, `seeders`, `volumeLabel`, `error`, `canPause`, `canResume`, `files`), `done[]` (`files` = noms dans la bibliothèque) |
| `POST /api/downloads/accept` | | avertissement lu (mémorisé) |
| `POST /api/downloads/add` | `url`, `volume` (option), `opt.<option>` | `{ok,id,note}` ; 409 `warning`, 507 `space`, 503 `engine`, 400 lien/option refusés |
| `POST /api/downloads/upload` | `name` (`.torrent`/`.metalink`/`.meta4`) ou `kind`, corps = le fichier (≤ 4 Mo) | idem |
| `POST /api/downloads/pause`, `resume`, `remove` | `id` ; `files=1` pour effacer les fichiers | |
| `POST /api/downloads/pauseall`, `resumeall`, `clear` | | |
| `POST /api/downloads/priority` | `id`, `move=top|up|down|bottom` | seulement en file d'attente |
| `POST /api/downloads/options` | `id`, `opt.max-download-limit=500K`… | liste blanche |
| `GET /api/downloads/files`, `POST /api/downloads/select` | `id` ; `files=1,3` | fichiers d'un torrent |
| `GET /api/downloads/peers` | `id` | sources (IP, vitesses) |
| `GET/POST /api/downloads/settings` | `downLimit`, `upLimit` (octets/s ou `512K`), `seeding`, `maxConcurrent` | |
| `GET /api/downloads/about` | | texte de licence |

```sh
curl -H "X-CB-Pin: 123456" -X POST "http://TV:8765/api/downloads/accept"
curl -H "X-CB-Pin: 123456" -X POST "http://TV:8765/api/downloads/add?url=https%3A%2F%2Fexample.org%2Ffilm.mkv"
curl -H "X-CB-Pin: 123456" -X POST --data-binary @film.torrent "http://TV:8765/api/downloads/upload?name=film.torrent"
curl -H "X-CB-Pin: 123456" "http://TV:8765/api/downloads"
```

## Points d'accroche (fusion avec les autres branches)

Tout le code est dans des fichiers nouveaux ; les seules modifications de fichiers existants sont :

1. `core/.../tv/Device.kt` : `ApiExtension` gagne deux méthodes par défaut (`wantsBody`, `handleBody`) et une fonction
   `then()` pour chaîner deux extensions.
2. `core/.../tv/ReceiverServer.kt` : lecture du corps (≤ 4 Mo) pour les routes d'extension qui le demandent (`extBody`),
   constante `MAX_EXT_BODY`.
3. `receiver/.../PlayerActivity.kt` : une ligne pour démarrer `TvDownloads` et chaîner son extension, une entrée de MENU.
   Dans un service d'arrière-plan (TvService) : `val dl = TvDownloads.start(ctx, registry) { cibleDeStockage }` puis
   `extension = autre.then(dl.manager.apiExtension)` ; `TvDownloads.start` peut être rappelé (activité recréée) : il suit
   le nouveau registre de volumes.
4. `receiver/AndroidManifest.xml` : `DownloadsActivity`, `DownloadService`, permissions `FOREGROUND_SERVICE(_DATA_SYNC)`,
   `POST_NOTIFICATIONS`. `receiver/build.gradle.kts` : `packaging.jniLibs.useLegacyPackaging = true`, tâche `buildAria2`.
5. `sender/.../TvHub.kt` : `extra = { DownloadsEntry(it); AdminPanel(it) }`. `sender/AndroidManifest.xml` : `ShareToTvActivity`.
6. `core/.../resources/castbridge/admin.html` : une `<section id="dl">` et un `<script>` autonome à la fin.

## Tests

`gradle :core:test` : client JSON-RPC (vrai HTTP contre un faux aria2), états, règle d'espace, liste blanche, liens,
bencode/Metalink, gestionnaire complet (faux aria2), relais API derrière le PIN, supervision (redémarrage, arrêt propre).
Contre un **vrai** aria2 (même source, compilé pour l'ordinateur) :
`ARIA2C=/chemin/aria2c gradle :core:test --rerun --tests '*Aria2IntegrationTest*'` (téléchargement HTTP réel rangé dans la
bibliothèque, pause, options, suppression, arrêt propre ; vérifie aussi que toutes les options de la ligne de commande existent).

## Compiler le moteur

```sh
export ANDROID_NDK_HOME=~/Library/Android/sdk/ndk/27.3.13750724     # r26 ou plus récent
tools/build-aria2-android.sh                    # ou : cd android && gradle :receiver:buildAria2
```

Le script télécharge uniquement les archives **sources officielles** (aria2, OpenSSL, zlib, expat, c-ares, libssh2), vérifie
leur **SHA-256 épinglé** (arrêt au moindre écart), compile pour API 26 `armeabi-v7a` et `arm64-v8a`, lie tout
statiquement (seules `libc`, `libm`, `libdl` d'Android restent dynamiques), supprime les symboles et écrit
`android/receiver/src/main/jniLibs/<abi>/libaria2c.so` ainsi que `tools/aria2-android-build.txt` (versions, tailles,
SHA-256 des binaires). Sans ces fichiers, l'app compile et fonctionne : elle affiche « Moteur de téléchargement non inclus
dans cette version ». Aucun binaire tiers (Termux ou autre) n'est utilisé.

Pourquoi `libaria2c.so` : depuis `targetSdk` 29, une app ne peut exécuter un fichier que depuis `nativeLibraryDir` (le
stockage de données est `noexec`) ; le gestionnaire de paquets n'y extrait que des `lib*.so`, et seulement avec
`useLegacyPackaging = true` (`extractNativeLibs="true"`), ce qui vaut aussi pour libVLC (installé plus gros, APK plus petit).

### Binaires livrés et tailles (NDK r27d, 2026-09-30)

| | armeabi-v7a | arm64-v8a |
|---|---|---|
| `libaria2c.so` (sans symboles) | 6,2 Mo (3,1 Mo compressé dans l'APK) | 8,5 Mo |
| APK release `:receiver` avant (libs non compressées) | 40,9 Mo | 53,6 Mo |
| APK release `:receiver` après (libs compressées + aria2) | 23,7 Mo | 27,3 Mo |

Attention à l'espace **installé** : avec `useLegacyPackaging`, les bibliothèques (libVLC 39 Mo, aria2 6,2 Mo…) sont
extraites à l'installation en plus de l'APK : environ 70 Mo occupés en armeabi-v7a contre ~41 Mo avant (+29 Mo sur une
mémoire interne de ~300 Mo libres). C'est le prix de l'exécution d'un binaire natif (tout ou rien par APK).

La procédure est reproductible (sources et options épinglées) mais le binaire n'est pas identique octet pour octet
d'une compilation à l'autre : aria2 y inscrit la date de compilation (`aria2c --version`).

Vérifié sur un téléphone Android 14 (32 bits activé) avec le binaire armeabi-v7a : `--version` (fonctions : Async DNS,
BitTorrent, GZip, HTTPS, Metalink, SFTP ; OpenSSL, c-ares, libssh2, expat, zlib), téléchargement HTTPS réel à 8
connexions avec vérification du certificat (SHA-256 du fichier reçu correct), refus sans magasin de certificats,
récupération des métadonnées d'un magnet (Big Buck Bunny, licence libre) par DHT/trackers ; puis l'app de test installée :
aria2 démarré depuis `nativeLibraryDir` et téléchargement ajouté par l'API (le téléphone a été débranché avant la fin du
test ; il reste peut-être l'app de test `castbridge.receiver.dltest` à désinstaller : `adb uninstall castbridge.receiver.dltest`).

## Licence (obligations GPL)

aria2 est sous **GPL-2.0-or-later** (avec l'exception OpenSSL de ses auteurs). Distribuer l'APK qui contient
`libaria2c.so`, c'est distribuer aria2 : il faut fournir la **source correspondante** complète. Elle est constituée de
`tools/build-aria2-android.sh` (URL officielles et SHA-256 de chaque archive, options de compilation) et des archives
qu'il désigne (aria2 : <https://github.com/aria2/aria2/releases/tag/release-1.37.0>). Garder ce script avec chaque APK
publié, et joindre ces archives (ou une offre écrite de les fournir) si l'APK est distribué hors de ce dépôt.
L'écran « À propos » de la TV, du téléphone et de la page web le rappelle. Autres licences : OpenSSL (Apache-2.0),
c-ares (MIT), libssh2 (BSD-3-Clause), expat (MIT), zlib (zlib).
