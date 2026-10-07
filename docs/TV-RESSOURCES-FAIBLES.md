# CastBridge-TV sur boîtier à faibles ressources (512 Mo, 32 bits)

Cibles : toutes sortes de TV, dont des boîtiers de 512 Mo de RAM, Android 32 bits (armeabi-v7a), processeur lent, heap d'application de 64 à 128 Mo, `isLowRamDevice` parfois vrai, 720p. La TV de référence du propriétaire (GaiaOS 32 bits, 720p) est dans cette famille. **Rien n'a été mesuré sur une vraie TV de 512 Mo** (voir « Non mesuré »).

## Le profil (`core/.../device/ResourceProfile.kt`, pur, testé en JVM)

Entrées : RAM totale, `memoryClass`, `isLowRamDevice`, nombre de coeurs, 32/64 bits (+ `Runtime.maxMemory()`). Lues une fois par `ResourceProfiles` (receiver) ; une lecture qui échoue = inconnu = comportement d'avant.

**ÉCONOME** si l'un de ces critères est vrai : RAM totale connue <= 768 Mo (un boîtier « 512 Mo » annonce ~450-500 Mo) ; `memoryClass` connu <= 96 Mo ; 32 bits avec 1 ou 2 coeurs et <= 1 Go. Sinon **NORMAL**. Le drapeau `isLowRamDevice` (`ro.config.low_ram`) SEUL ne rend plus économe (audit 2026-10-07, R-27) : avec RAM > 768 Mo et heap > 96 Mo il donne NORMAL, avec pour seule différence un cache de vignettes réduit (heap/8, entre 1 et 3 Mo au lieu de 4 Mo). **Mesuré sur la TV de référence (SMART_TV, GaiaOS)** : `ro.config.low_ram=true`, `dalvik.vm.heapgrowthlimit=160m`, `heapsize=224m`, RAM 981 Mo, 4 coeurs, 32 bits ⇒ NORMAL (test `ResourceProfileTest.referenceTvMeasuredValuesAreNormalWithASmallerThumbCache`) ; un boîtier de 512 Mo ⇒ ÉCONOME. Une TV 32 bits de 1 Go à 4 coeurs et 192 Mo de heap reste NORMALE (aucune régression).

| Borne | Économe | Normal (= avant) | Pourquoi |
|---|---|---|---|
| Flux de réception simultanés (`maxStreams`) | 2 | 6 | chaque flux garde un tampon et un fil ; 2 suffisent à saturer un Wi-Fi/disque lent |
| Fils HTTP (`BoundedRunner`, file de 32) | 5 | 8 | 2 flux + lecture + interrogations ; jamais un fil par connexion |
| Tampon de copie (`ioBufferBytes`) | 32 Ko | 64 Ko | par flux ; évite les pics du ramasse-miettes |
| Tampon d'écriture d'un envoi | 128 Ko | 256 Ko | idem (écritures encore groupées) |
| Cache de vignettes décodées (LRU, en octets) | 1/8 du heap, 1 à 3 Mo | 4 Mo | bitmaps RGB_565 de 320x180 (115 Ko) ; aucune gardée hors écran (vidées à l'arrêt de l'écran et à `onTrimMemory`) |
| Fiches de la bibliothèque en mémoire (`LibraryDb`) | 500 | 2000 | les plus anciennement lues sont élaguées |
| Empreintes de l'index de contenu (`ContentIndex`) | 20 000 | 100 000 | calcul paresseux, un fil, par blocs : jamais de liste complète des fichiers |
| Lecteur par défaut | léger | actuel | pas de désentrelacement « au cas où » (une piste entrelacée reçoit `blend`), pas de mise à l'échelle logicielle (swscale lanczos), cache libVLC plafonné à 2 s |
| Animations de l'accueil | coupées | actives | pas de zoom au focus (le cadre bleu reste), pas de fondu d'arrière-plan ni de vignette |
| Lecteur en arrière-plan | libéré en entier | déjà libéré à l'arrêt de l'écran | décodeur et surface rendus ; vignettes vidées |
| Packs de leçons ouverts (`LearnLibrary`) | 1 | 3 | les 244 packs embarqués ne sont jamais décompressés d'avance : la liste ne lit que les manifestes (flux), un pack est décompressé à la demande |

## Où c'est branché

- `TvPrefs.profile()` remplit `TvProfile` (`maxHttpThreads`, `maxTransferStreams`, `ioBufferBytes`, `uploadBufferBytes`, `indexEntries`, `resourceProfile`) ; `ReceiverServer` s'en sert (pool, `TransferHost(maxStreams)`, tampons, `ContentIndex`). `TransferHost.kt` n'est pas modifié : `streamLimit` (politique « la lecture d'abord ») reste bornée par `maxStreams`.
- `/api/transfer/caps` : champ OPTIONNEL `profile` (« low » / « normal »), `maxStreams` reflète la borne. Une TV ancienne n'a pas le champ : le téléphone la traite comme normale.
- `PictureQuality.Facts.lightPlayer`, `PlayerTuning.Facts.lightPlayer` / `cachingCapMs`.
- `onTrimMemory` : `TvService` (packs de leçons vidés ; dès MODERATE en économe), `PlayerActivity` (vignettes vidées, lecteur rendu s'il ne joue pas).
- Pas de `largeHeap` (non honoré partout), aucune permission, aucun nouveau cache.
- Journal au démarrage (une ligne) : `profil ressources : économe (RAM 512 Mo, heap 96 Mo)`. Ligne « Profil ressources » dans « Infos techniques » du lecteur de CastBridge-TV.

## Autres sources de mémoire vérifiées

Fils : `BoundedRunner` (pool fixe, file de 32, refus propre au-delà), un fil de vignettes, un fil d'index, un fil de politique de lecture (daemon). Copies : tampons alloués par flux, jamais de fichier entier en mémoire (corps POST d'extension plafonné par `MAX_EXT_BODY`). Journaux et files en mémoire : `LibraryDb` bornée, `ThumbWorker` file dédupliquée, vignettes sur disque (20 Mo).

## Mesurer sur une TV

```
adb shell dumpsys meminfo castbridge.receiver      # PSS total, Java heap, Native heap, Graphics
adb shell dumpsys meminfo castbridge.receiver | grep -E "Java Heap|Native Heap|TOTAL"
adb logcat -d | grep "profil ressources"
adb shell dumpsys activity processes | grep -A3 castbridge.receiver   # oom_adj, kills récents
adb shell getprop ro.config.low_ram
```
Repères à relever : PSS stable pendant 2 h de lecture, aucun `Killing ... castbridge.receiver` dans `logcat`, copie de 348 Mo pendant une lecture sans image figée (parcours P-74).

## Non mesuré

Aucune mesure sur TV réelle de 512 Mo : PSS en lecture, effet du plafond de 2 flux sur le débit de copie, effet de la qualité légère sur la fluidité de libVLC, seuils (768 Mo, 96 Mo) à confirmer sur plusieurs boîtiers. Les valeurs sont des prudences déclarées, à corriger après P-74.
