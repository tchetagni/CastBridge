# « Copier sur la TV et lire » : la TV démarre pendant la copie (R-08, 2026-10-03)

Demande du propriétaire : « copier et lire doit lancer la lecture à la TV ». Branche `claude/copy-and-play-handoff` (depuis `integration/agents` 2e20cf4), un seul commit, non poussé. Rien n'a été installé ni modifié sur les appareils. Le journal du téléphone (`adb -s RFCR313ABNF logcat -d`) n'a pas pu être lu : le téléphone n'était pas branché (seul un émulateur était visible), donc aucune preuve de terrain n'en vient.

## 1. Diagnostic, classé

| # | Hypothèse | Verdict | Preuve |
|---|---|---|---|
| C | `UploadService.start(progressive=false)` choisit le chemin rapide | **CONFIRMÉE** | `S/UploadService.kt:155` (avant correctif) : `val fast = if (!progressiveNow && FastTransfer.enabled(this)) runFast(...)`. `CastSession.copy` passait `progressive = false`, et `FastTransfer.enabled` vaut `true` par défaut (`S/FastTransfer.kt:8`). Donc « Copier sur la TV et lire » partait toujours en multivoie. |
| A | Sur ce chemin, `/api/info` ne montre aucun `received`, donc `f == null` | **CONFIRMÉE** | Les blocs vont dans `.cbx/<id>.data` (`C/xfer/PartAssembler.kt:43,242-255`). `listing()` (`C/tv/ReceiverServer.kt:1121-1146`) ne liste que les fichiers finaux et les `.part` qui ont un `.meta`. Test réel : `CopyAndPlayHandoffServerTest.duringAFastBlockCopyTheTvSeesNothingAndCannotPlay` (2 blocs de 4 Mio reçus, `file(name) == null`). |
| B | Même exposé, `/stream/` et `/api/play` ne liraient pas un fichier qui n'est que dans `.cbx` | **CONFIRMÉE** | `stream()` et `playIncomplete()` ne cherchent que `findFinal`/`findPart` (`ReceiverServer.kt:1506-1580`). Même test : `/api/play` répond 404, `/stream/` aussi. |
| D | Les seuils de `Handoff.copyReady` sont trop prudents | **CONFIRMÉE, mais seulement quand la durée est inconnue** | Durée connue (lecteur du téléphone) : le seuil est de 30 s d'avance plus l'amorçage, soit environ 2 % d'un film de 1 Go à 0 s (`CopyRouteTest.aKnownDurationHandsOff…`). Ce n'est pas trop prudent. Depuis « Ouvrir avec » en revanche, `CastSession.start(…)` reçoit `fallbackDurMs = 0` et le lecteur local n'a pas ce fichier, donc `dur = 0`. Or `Progressive.bytesForPosition(total, 0, …) = total` : le relais n'arrivait qu'à 100 %, **même sur le chemin classique**. |
| E | (trouvé en lisant) le nom suivi peut différer du nom envoyé | probable, secondaire | Avec « Rangement automatique » (désactivé par défaut), `AgentAuto.nameFor/settle` peut renommer l'envoi, alors que `CastSession` cherchait `item.name`. Une fois le fichier complet et classé, `client.play(item.name)` visait un nom que la TV n'a plus (`/api/play` est strict), ce qui donnait 404 et une boucle sans fin. |

Conclusion : avec les réglages par défaut, A, B et C font que la TV ne pouvait jamais démarrer avant la fin de la copie. Depuis « Ouvrir avec », D le garantissait de toute façon, même avec le transfert rapide désactivé.

## 2. Ce qui change

Conception retenue, la plus sûre : **pour une vidéo ou un audio lu sur la TV, la copie passe par le chemin classique ordonné**. C'est une seule connexion qui part de l'octet 0, en `PUT /upload`, et qui remplit le `.part` + `.meta` visibles. Ainsi `/api/info` montre `received`, `/api/play` lance le fichier en cours (`playIncomplete`) et `/stream/` sert son préfixe (`GrowingStream`). Le relais existant fonctionne alors sans autre modification. Je n'ai **pas** exposé le préfixe contigu de `.cbx` : il aurait fallu un second lecteur pour un fichier écrit par blocs puis renommé au `finish`, ce qui n'est ni bon marché ni sûr.

- `C/phone/CopyRoute.kt` (nouveau, pur) contient deux éléments.
  - `CopyRoute.decide(Facts)` renvoie `ORDERED`, `FAST` ou `NONE`, ainsi que `handoffDuringCopy`. La table :
    - LIVE : `NONE`.
    - Bluetooth seul : `NONE`.
    - TV d'essai : `NONE`, car la copie est fermée et `CopyAndPlay` dégrade en LIVE.
    - Photo (rien n'est lu) : `FAST`.
    - **MP4 à index en fin** : `FAST`. La TV doit attendre tout le fichier de toute façon, donc la copie la plus rapide donne aussi la lecture la plus tôt.
    - Toute autre vidéo ou tout audio lu sur la TV (COPY comme MOVE) : `ORDERED`, avec relais pendant la copie.
    - « Transfert rapide » désactivé : `ORDERED` partout, comme avant.
  - `CopyHandoff` porte les phrases. « Copie en cours · la TV démarrera la lecture dès qu'elle aura assez d'avance ». Pour un MP4 à index en fin, la phrase dit que la TV ne démarre qu'à copie complète, avec l'attente estimée. « La lecture continue ici en attendant » ne s'affiche que si le téléphone lit vraiment.
- `C/phone/PhonePlayer.kt` contient les changements suivants.
  - `Handoff.bytesNeeded` : pour une durée inconnue avec un départ à 0, il faut l'amorçage + 32 Mio (environ 30 s à 8,5 Mbit/s). Un départ au milieu d'un fichier de durée inconnue exige le fichier entier : on ne devine pas l'octet. Un MP4 à index en fin exige toujours le fichier entier.
  - `CopyProgress.moovAtEnd` fait que le détail affiche « la TV démarrera la lecture à la fin de la copie, dans X ».
- `S/UploadService.kt` reçoit un nouveau drapeau `ordered` (`Job`, `EXTRA_ORDERED`, `start(…)`) : quand il est actif, `runFast` n'est jamais appelé. À la différence de `progressive`, le service ne lance rien lui-même : c'est `CastSession` qui décide du relais, à la position du téléphone.
- `S/player/CastSession.kt` : `copy()` fait les choses suivantes.
  - Il mesure la taille et la disposition MP4 avant le premier octet, puis appelle `CopyRoute.decide` (la décision est journalisée dans `CastSession`).
  - Il lit la durée du fichier (`MediaMetadataRetriever`) quand le téléphone ne la connaît pas, ce qui est le cas depuis « Ouvrir avec ».
  - Il suit le nom réellement envoyé (`job.fileName`) et lance `play(f.name)`, c'est-à-dire le nom que la TV lui donne (cas E).
  - Il affiche les phrases de `CopyHandoff` et le drapeau `Remote.phonePlays`.
- `S/player/RemoteControls.kt` : le bandeau de copie affiche la phrase principale. La ligne « la lecture continue ici » n'apparaît que si c'est vrai. Quand la TV démarre, la phase passe à PLAYING et l'écran devient la télécommande (comportement existant).
- `S/OpenWithActivity.kt` : sous le bouton « Copier sur la TV et lire », on lit « La TV démarre la lecture dès qu'elle a assez d'avance ; ce téléphone devient sa télécommande. », ou la raison de la limitation (essai, Bluetooth). La phrase « sans lire le fichier » est maintenant rattachée explicitement à « Copier vers la TV ». Le bouton ouvre toujours la télécommande (`PlayerActivity.ACTION_REMOTE`), qui affiche la progression puis bascule.

## 3. Compromis de vitesse

C'est le même compromis que « Lire en direct » face à la copie rapide. Le chemin ordonné utilise **une seule connexion**, alors que le transfert rapide en utilise jusqu'à 6 et compresse. Sur un bon Wi-Fi, la copie complète d'une vidéo lue sur la TV peut donc être plus lente (ordre de grandeur : de 1,5 à 3 fois sur un lien où le multivoie gagnait). En échange, la lecture démarre après quelques secondes d'avance au lieu d'attendre 100 %. Les photos, les MP4 à index en fin et les simples « Copier vers la TV » gardent le transfert rapide.

## 4. Garanties conservées

- **Intégrité** : on reste sur le chemin classique éprouvé. Chaque PUT doit partir de l'offset exact détenu par la TV, sinon on obtient 409. La TV ne valide (`commit`) qu'à `partSize == total`, et le téléphone ne déclare « terminé » que sur `done:true`. `NAME_TAKEN` et `PART_OTHER` sont traités comme avant (`ResumableUpload.run`, `uploadCounted`). La reprise après coupure se fait à partir de ce qui est arrivé. « Déplacer » ne supprime l'original qu'après la confirmation `complete` à la taille exacte (`checkMoved`, inchangé).
  - **Différence assumée** : le chemin ordonné n'a pas le SHA-256 par bloc du multivoie. La vérification y repose sur TCP, l'ordre strict et la taille exacte, comme pour « lire pendant l'envoi » depuis toujours.
- **fsync différé** : le chemin `uploadCounted` est inchangé, y compris la règle « fsync espacé pendant la lecture, dû à la coupure, jamais retiré avant le commit ».
- **« La lecture d'abord »** : la copie qui **nourrit** la lecture en cours en est exemptée. `playIncomplete` pose `growingName` ; `playbackSignal` voit alors `growing` et `PlaybackPriority.decide` renvoie `feeds-playback`, sans plafond ni fils en arrière-plan. Le test réel le mesure : 34 Mio passent en moins de 4 s après le démarrage de la lecture, alors qu'une copie bridée (6 Mo/s au plus) en mettrait au moins 5,8.

## 5. Tests

- **Rouge par assertion** (avant implémentation, `CopyRoute.decide` remplacé par un bouchon, `bytesNeeded` sur l'ancienne règle) : `CopyRouteTest` (9 tests) et `CopyAndPlayHandoffServerTest` (2) ont donné 11 tests exécutés et 4 échecs, tous dans `CopyRouteTest` : `whichTransportCopyAndPlayUses` (l.40), `aVideoThatPlaysOnTheTvNeverTakesTheInvisibleFastPath…` (l.51), `anUnknownDurationFromOpenWithStillHandsOffEarlyFromTheStart` (l.72), `theProgressDetailOfAMoovAtEndFileGivesTheWaitForTheWholeCopy` (l.111). Les deux tests `CopyAndPlayHandoffServerTest` étaient déjà verts, ce qui prouve que le côté TV du chemin ordonné fonctionne déjà et que A/B est bien la cause.
- **Vert** après le correctif. La suite complète `:core:test` a tourné une fois après la dernière modification du cœur : 2509 tests, 0 échec, 2 ignorés (déjà ignorés avant), en 3 min 49 s, sans « TEST TROP LONG ». `:sender:compileDebugKotlin` et `:receiver:compileDebugKotlin` réussissent. Le seul correctif fait après la suite était une erreur de compilation côté téléphone (appel suspendu sorti d'une lambda) ; le cœur n'a pas changé depuis.
- Docs : parcours **P-38** (`docs/test-plans/PARCOURS-CRITIQUES.md`), régression **R-08** (`docs/REGRESSIONS.md`).

## 6. Risques

- La copie complète d'une vidéo copiée-et-lue est plus lente sur un Wi-Fi où le multivoie gagnait (§ 3).
- Seuil pour une durée inconnue : on suppose 32 Mio ≈ 30 s. Une vidéo à très haut débit (plus de 8,5 Mbit/s) peut se mettre en mémoire tampon au début. La TV attend alors poliment (`GrowingStream`, jusqu'à 2 min tant que la copie vit) au lieu de couper. Dans le cas normal, la durée est maintenant lue sur le fichier, et ce seuil ne sert que si la lecture des métadonnées échoue.
- Un fichier MP4 non probé (taille illisible avant l'envoi) part en ordonné, puis est reconnu « index en fin » dans la boucle : il est correct mais copié plus lentement que nécessaire.
- La TV contrôle toujours le démarrage : `/api/play` répond 409 `buffering` sous `bootstrapBytes` (amorçage de 3 s du débit mesuré). Le téléphone réessaie chaque seconde.

## 7. Ce que seule la vraie TV peut confirmer

- Le délai réel entre l'appui sur le bouton et l'image sur la TV GaiaOS (S21+, Wi-Fi maison), pour un MKV et un MP4 « faststart » de quelques centaines de Mo.
- La fluidité de la lecture pendant la suite de la copie, quand la copie et la lecture partagent le même eMMC ou la même clé exFAT. C'est la mesure du § 4 de `fluid-playback-fix.md`.
- Le débit d'une seule connexion comparé au multivoie sur ce Wi-Fi, pour chiffrer le compromis.
- Le message et l'attente affichés pour un MP4 à index en fin, ainsi que le démarrage à la fin de la copie.
