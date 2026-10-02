# Lecture fluide pendant une copie : diagnostic et « la lecture d'abord »

> Branche `claude/fluid-playback-during-copy` (depuis `integration/agents` @ fc4ccca), 2026-10-02. Rapport du propriétaire : « la lecture lague
> lors d'une copie ». Aucun changement sur les appareils : relevés en lecture seule (SSH TV uid dev, `adb` téléphone), aucune copie lancée.

## 1. Ce que fait vraiment le code aujourd'hui (préalable indispensable)

- **« Copier sur la TV et lire »** (`CastSession.copy`, sender) démarre `UploadService` avec `progressive = false`. Avec « Transfert rapide »
  activé (défaut, `FastTransfer.enabled` = true), c'est le **multivoie** qui part : jusqu'à **6 connexions** (`TransferHost.maxStreams` =
  `maxHttpThreads − 2`), blocs de 4-8 Mio, écrits dans `.cbx/<id>.data`, **invisibles** de `/api/info` jusqu'au `finish`. Conséquence :
  `info.file(name)` reste nul jusqu'à la fin, `Handoff.copyReady` n'est vrai qu'à `received == total` : **la TV ne lit qu'après la copie
  complète** ; pendant la copie, c'est le **téléphone** qui lit.
- La TV ne lit un fichier **en cours d'arrivée** que sur le chemin **classique** (`PUT /upload`, une connexion, ajout séquentiel au `.part`,
  `playIncomplete` → `/stream/` → `GrowingStream`) : transfert rapide désactivé, volume SAF (501 → repli classique), TV sans protocole.
- Donc « ça lague pendant une copie » sur la TV = (i) une vidéo de la **bibliothèque** lue depuis la clé/eMMC pendant qu'une copie y écrit,
  ou (ii) la lecture d'un `.part` classique qui grandit sur le **même** support que l'écriture.

## 2. Relevés en lecture seule (2026-10-02 ~22 h, aucune copie en cours connue)

| Appareil | Fait relevé | Lecture |
|---|---|---|
| TV | 4 × Cortex-A53 (`CPU part 0xd03`) en ARMv7, **`Features: … aes pmull sha1 sha2`** | SHA-256 accéléré matériellement (Conscrypt/BoringSSL) : le hachage par bloc n'est **pas** le gros consommateur |
| TV | Android 14, noyau **5.15.137** (Amlogic) | exFAT du noyau 5.15 : pas de « valid size », pas de fichier creux |
| TV | clé **Kingston DataTraveler 3.0** en **exFAT**, montée **`dirsync`**, ordonnanceur `mq-deadline`, `rotational=1` | toute opération de répertoire synchrone ; une seule file d'E/S lente |
| TV | RAM 981 Mo, **33 Mo libres**, cache 272 Mo, **swap zram 425/605 Mo utilisés** | peu de cache de pages : chaque lecture du lecteur va au disque, compression zram = CPU |
| TV | PSI `io some avg300 = 15,8 %`, `full avg300 = 10,4 %` ; `cpu some ≈ 13 %` ; `top` : 68 % user, 48 % sys, 268 % idle | E/S déjà en attente notable ; CPU loin de la saturation |
| TV | `vm.dirty_ratio 20`, `dirty_background_ratio 5` | rinçage en arrière-plan dès ~13 Mo sales |
| Téléphone | Galaxy S21+ (SM-G996U), 8 cœurs, `castbridge.sender` 2,3 % CPU, charge 2,3 | le téléphone n'est pas limitant en CPU |

`top` côté TV ne montre que les processus de l'uid dev (les autres sont masqués) : la répartition par processus reste à mesurer (§ 4).

## 3. Causes classées

| Rang | Cause | Statut | Preuves (code + relevés) |
|---|---|---|---|
| 1 | **B — contention d'E/S sur le support lu par le lecteur** | **Probable (cause principale)** | `PartAssembler.open` faisait `setLength(size)` : sur exFAT/FAT (noyau 5.15) cela **écrit des zéros sur toute la taille** avant le 1er bloc (2 Go de plus sur la clé, minutes d'E/S saturées) ; carte d'état réécrite **chaque seconde** par renommage sous `dirsync` ; relecture **intégrale** au `finish` (2 Go lus pendant que le lecteur lit) + `force(true)` ; fsync périodique tous les 64 Mio sur clé (chemin classique) ; lecteur avec `--file-caching=400` (400 ms d'avance seulement) ; RAM saturée (cache de pages minuscule) ; PSI io déjà à 10-16 %. |
| 2 | **A — CPU de la TV** | **Probable, secondaire** | 6 threads HTTP de réception à **priorité normale** (rien ne baisse leur priorité, `grep setThreadPriority` : seul `ForegroundWatcher`) face au décodeur libVLC et à l'UI ; coût noyau exFAT/USB/FUSE (48 % sys), kswapd/zram ; notification + puce d'accueil repeintes **chaque seconde** sur le thread principal (`TvService.receptionNotifier`) ; `--no-drop-late-frames --no-skip-frames` : un retard CPU se voit en ralenti au lieu d'images sautées. **Réfuté** : SHA-256 (accéléré `sha2`), AES (aucun chiffrement sur le LAN, W8 en sommeil), JSON/regex (négligeable). Décodage logiciel : `hw_mode` « auto » par défaut, inconnu par fichier. |
| 3 | **D — réseau plus lent que la vidéo** | **Possible, seulement en copie+lecture classique** | Voir l'arithmétique ci-dessous. Sans objet en multivoie (la TV ne lit qu'à la fin). |
| 4 | **C — ordre / sous-alimentation** | **Réfuté dans le code actuel** | Classique : ajout séquentiel (`FileOutputStream(…, true)`), `GrowingStream` lit ≤ longueur = préfixe contigu. Multivoie : les blocs arrivent presque dans l'ordre (`Scheduler.take` prend en tête, fenêtre ≈ K blocs) mais le fichier n'est **pas** lisible avant `finish`. Risque latent (un jour un fichier par blocs lu en grandissant) **fermé** : `GrowingStream` est désormais borné par un `available` (préfixe contigu) et `BlockMap.leading()` / `contiguous` existent. |
| 5 | **E — téléphone** | **Peu probable** | S21+ peu chargé (2,3 % pour l'app). Seul cas : si le « lag » est sur l'écran du téléphone (il lit pendant la copie multivoie et relit le fichier deux fois : hachage puis envoi). À vérifier par le protocole. |

**Arithmétique D.** Débit vidéo b = taille / durée (ffprobe). Débit de copie c = vitesse de la notification. Wi-Fi 2,4 GHz 1×1 d'une TV :
2-6 Mo/s réels ; 5 GHz 1×1 : 8-15 Mo/s. Vidéos du S21+ : 1080p30 ≈ 17 Mbit/s = 2,1 Mo/s ; 1080p60 ≈ 3,5 Mo/s ; 4K30 ≈ 6 Mo/s ;
4K60 ≈ 8 Mo/s ; un film de 2 Go / 2 h ≈ 0,28 Mo/s. Si b > c, avec l'avance initiale L = 30 s du relais (`Handoff.copyReady`), la lecture
cale après T = L·b / (b − c) : b = 6, c = 4 Mo/s → 90 s puis « Mise en mémoire tampon… » en boucle, **quel que soit le CPU**.
La politique l'annonce : raison `underrun-risk` dans `/api/info`.

## 4. Protocole de mesure (pendant une copie + lecture, 2 minutes)

Lancer la scène qui lague, puis, depuis le Mac (tout est en lecture seule ; ne jamais écrire le PIN dans un fichier partagé) :

```sh
# TV : charge, pression CPU/E/S/mémoire, débit de la clé (sda) et de l'eMMC, swap, toutes les 5 s pendant 2 min
ssh -p 2223 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 tv@192.168.0.121 \
  'for i in $(seq 1 24); do date +%T; cat /proc/loadavg; cat /proc/pressure/cpu /proc/pressure/io /proc/pressure/memory;
   grep -E " sda | mmcblk0 " /proc/diskstats; grep -E "pswpin|pswpout" /proc/vmstat; top -b -n 1 | sed -n 3,4p; sleep 5; done' > tv-mesure.txt
# TV : état de la politique et du lecteur (champ additif)
curl -s -H "X-CB-Pin: $PIN" http://192.168.0.121:8765/api/info | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["player"]); print(d["playbackPriority"])'
# Téléphone
adb -s RFCR313ABNF shell dumpsys cpuinfo | head -15
adb -s RFCR313ABNF shell top -b -n 1 -m 10
# Débit vidéo du fichier (sur le Mac)
ffprobe -v error -show_entries format=duration,size,bit_rate -show_entries stream=codec_name,width,height,avg_frame_rate -of default=nw=1 video.mp4
```

Lire `diskstats` : champs 6 (secteurs lus), 10 (secteurs écrits), 13 (ms d'E/S) ; Δsecteurs × 512 / 5 s = Mo/s ; Δ(ms d'E/S) / 5000 = taux
d'occupation. Noter aussi la vitesse affichée par la notification du téléphone et si la TV affiche « Mise en mémoire tampon… ».

| Observation pendant le lag | Cause |
|---|---|
| occupation `sda` (ou `mmcblk0`) ≈ 100 %, PSI `io full` > 20 %, CPU idle > 100 % | **B** |
| PSI `cpu some` > 40 %, idle < 50 %, `iow` faible | **A** |
| bandeau « Mise en mémoire tampon… » et vitesse de copie < débit vidéo (`underrun-risk` dans `playbackPriority`) | **D** |
| bandeau alors que la vitesse de copie > débit vidéo | **C** (ne devrait plus arriver) |
| le lag est sur l'écran du téléphone, `castbridge.sender` > 50 % | **E** |

Contre-épreuve : même vidéo sans copie (lag disparu ⇒ copie en cause), puis copie mise en pause.

## 5. Ce qui change : une politique unique, `castbridge.core.xfer.PlaybackPriority`

Table **pure** (`PlaybackPriority.decide`) + porteur (`PlaybackGovernor` : cache 500 ms, rythmeur partagé, file de travaux reportés), interrogés par
**tous** les chemins de réception de `ReceiverServer`. Actif quand le lecteur TV est `playing` ou `buffering` ; en pause, arrêt, fin : retour
immédiat aux valeurs d'avant (toutes les valeurs « au repos » sont celles du code précédent).

| Pendant la lecture | Copie qui ne nourrit pas la vidéo | Copie qui nourrit une lecture en cours d'arrivée |
|---|---|---|
| Priorité des threads réception/hachage/écriture | `THREAD_PRIORITY_BACKGROUND` (port `ThreadPriorityPort`, Android dans `receiver/ReceivePriority.kt`), rétablie à chaque fin de requête, même en erreur | normale (c'est sa ligne de vie) |
| Débit de réception | plafonné à ½ `writeBps` mesuré, borné 1-6 Mo/s (3 Mo/s tant que non mesuré) ; `writeBps` désormais mesuré aussi sur le chemin classique | aucun plafond |
| Connexions multivoie | 2 au plus : les autres reçoivent 429 `busy` (`retryMs` 1000), le `KController` du téléphone recule (contre-pression existante) | — |
| fsync périodique (clé, classique) | tous les 4 × 64 Mio (borné) ; fsync dû après une coupure : **reporté** et exécuté à l'arrêt de la lecture (`DeferredWork`) | idem |
| Vérification finale (multivoie) | relecture **cadencée à 12 Mo/s** et en arrière-plan : plus lente, **jamais sautée** | — |
| Carte d'état multivoie | sauvegardée toutes les 10 s au lieu de 1 s | — |
| Progression (notification + puce d'accueil) | repeinte toutes les 2,5 s au lieu de 1 s (le dernier octet toujours annoncé) | idem |

Et aussi :
- **Pas de préallocation sur FAT/exFAT/NTFS/inconnu** (`PartAssembler.preallocates`) : fini les 2 Go de zéros avant le premier bloc ; le
  fichier grandit bloc par bloc (le noyau ne remplit que les petits trous de la fenêtre de ≤ K blocs). ext4/f2fs gardent `setLength` (creux, gratuit).
- **Lecteur** : `:file-caching` 2 s (au lieu de 400 ms) quand une copie est en cours à l'ouverture ; `:network-caching` 3 s (au lieu de 1,2 s)
  pour un fichier en cours d'arrivée ; un lien direct garde 1,2 s.
- **`/stream/` patient** : `GrowingStream` attend tant que la copie vit (jusqu'à 2 min) au lieu de couper à 30 s, et ne lit **jamais** au-delà
  du préfixe contigu (`available`).
- **Tête d'abord** : `PlaybackPriority.admitAhead` (fenêtre 32 Mio après le préfixe contigu), `BlockMap.leading()`, et `contiguous` dans
  `/api/transfer/state`, prêts pour le jour où un fichier par blocs sera lu en grandissant (aujourd'hui l'ordonnanceur prend déjà en tête).
- **Double `finish`** (le téléphone abandonne une relecture longue après 60 s et redemande) : le second appel revérifie la session sous le
  verrou et répond `done` si le premier a conclu (au lieu d'un 500 sur un fichier fermé).
- **Observabilité** : `/api/info` → `playbackPriority: {on, reasons[player:playing|buffering, growing, feeds-playback, copy:background,
  underrun-risk], backgroundThreads, maxStreams, receiveCapBps, progressEveryMs, syncEveryBytes, verifyReadBps, deferred}` (lecture seule, sans secret).
- Choix assumé : le **rangement à la réception** n'est pas reporté (un renommage sur le même volume, coût négligeable ; le reporter le ferait
  plus tard sous un lecteur éventuel, avec plus de risque que de gain).

## 6. Garanties conservées

- Rien n'est déclaré complet avant vérification : le `finish` multivoie relit et compare **tous** les blocs comme avant (seulement cadencé),
  puis `force(true)`, puis renomme ; le commit classique fait toujours son fsync avant le renommage (`FileStore.commit`), en lecture ou non.
- Un fichier de données plus court que le manifeste (sans préallocation, fin perdue) donne des **blocs à renvoyer** (`Finish.Corrupt`), jamais
  une fin ni un 500 ; la carte n'est jamais en avance sur le disque (une carte plus ancienne = blocs renvoyés).
- Aucune suppression nouvelle ; Mover/MoveProof inchangés ; le fsync reporté est borné (≤ 256 Mio non forcés sur une clé arrachée en pleine
  lecture, contre 64 Mio avant) et rattrapé à l'arrêt.

## 7. Tests

- `core/src/test/.../xfer/PlaybackPriorityTest.kt` (24 tests, rouges d'abord par assertion sur une API bouchon : 21/24 en échec) : tables
  lecture/repos/pause/buffering, plafond selon `writeBps`, copie qui nourrit vs autre copie, `underrun-risk`, avance devant la tête, fenêtre tête
  d'abord, cache du lecteur, préallocation par système de fichiers, rythmeur, file reportée vidée une seule fois à l'arrêt, `refresh` forcé et
  intervalle publié, priorité abaissée puis toujours rétablie (même sur exception), relecture cadencée seulement en lecture, intervalle de
  progression, plafond de connexions, assembleur sans préallocation hors ordre, fichier court ⇒ blocs à renvoyer, `GrowingStream` borné au
  préfixe et patient tant que la copie vit.
- `:core:test` complet : 2456 tests, 1 échec, 3 ignorés. L'échec, `DownloadTest.downloadResumesWithRangeAfterACut` (« TV injoignable » :
  60 essais **sans pause**, `sleep = { }`, épuisés sous la charge de la suite), **passe seul** à chaque nouvel essai avec cette branche. Il
  passe par `/stream/` sur un fichier **terminé**, où le seul changement (`stillComing`) n'agit qu'en attente d'octets absents. Je le tiens
  pour un test instable sous charge, sans lien avec ce changement ; à surveiller (piste : une vraie pause entre les essais dans le test).
- `:receiver:compileDebugKotlin` et `:sender:compileDebugKotlin` : OK.

## 8. Risques

- Copie plus lente pendant qu'on regarde (voulu) ; une relecture finale de 2 Go à 12 Mo/s dure ~3 min : le téléphone peut afficher « en attente »
  puis reprendre (le double `finish` est désormais sûr).
- `THREAD_PRIORITY_BACKGROUND` peut, selon le profil de tâches du constructeur, placer le thread dans le cpuset d'arrière-plan : voulu, mais si
  GaiaOS le restreint à un seul cœur la copie peut tomber sous le plafond — à mesurer.
- Sans préallocation, la place n'est plus réservée d'avance : un disque plein en cours de copie donne `DiskFail`/507 (déjà géré), la vérification
  d'espace à l'allocation reste.
- `file-caching` n'est choisi qu'à l'ouverture : une copie qui démarre après garde 400 ms (le reste de la politique s'applique quand même).

## 9. Ce que seule la vraie TV peut confirmer

Le zéro-remplissage exFAT de `setLength` sur ce noyau (temps du `begin` avant/après), l'effet réel de la priorité d'arrière-plan sur GaiaOS,
le débit de la clé en écriture et en lecture simultanées, la part du décodage logiciel selon les fichiers, et l'attribution finale A/B/D par le
protocole du § 4 (avec `playbackPriority` affiché).
