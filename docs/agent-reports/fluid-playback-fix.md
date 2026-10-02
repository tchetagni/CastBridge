# Correctifs de l'audit « la lecture d'abord » (R-06)

> Branche `claude/fluid-playback-fix` (depuis `claude/fluid-playback-during-copy` a4c44ea, puis fusion de `integration/agents` : w15-07). Sonnet 5.5, 2026-10-02. Rien poussé, aucun appareil touché.
> Tests : `core/src/test/.../xfer/FluidPlaybackFixTest.kt` (19 tests au niveau du vrai `ReceiverServer` sur boucle locale, toutes attentes bornées).

## Constats corrigés (rouge par assertion, puis vert)

| # | Constat | Correctif | Test (rouge avant) |
|---|---|---|---|
| 1 | La voie lente prend la fin du fichier : sans préallocation (FAT/exFAT) le noyau 5.15 zéro-remplit tout le trou sous le verrou d'inode | `TransferHost.admitAhead` (câblé, `PlaybackPriority.admitAhead` + `headWindow` = max(32 Mio, (maxStreams+2) blocs)) : 429 pour un bloc au-delà de la fenêtre quand `!preallocated` ; la TV annonce `"ordered":true`, le téléphone passe `slowFromHead` au `Scheduler` (voie lente en tête) | `aBlockFarBeyondTheContiguousPrefixIsRefusedAndNeverZeroFillsTheGap` (« 429 attendu, 200 obtenu » ; compteur de trous = octets au-delà de la fin du fichier), `theTvAnnouncesAnOrderedFile...` |
| 2 | 429 envoyé puis `Connection: close` alors que le téléphone envoie tout le corps : RST, `Failed` au lieu de `Busy`, pénalités | `busy()` lit puis jette le corps (≤ 17 Mio) avant le 429, sans fermer la connexion ; `caps` et l'état annoncent `allowedStreams()` (2 pendant la lecture) | `aPhoneLikeClientSendingAFullBodyToATvAtItsStreamLimitGetsBusyNotAReset` (« Connection reset by peer » avant), `theTvAdvertisesTheStreamsItReallyAccepts...` |
| 3 | Lecteurs `/stream/` zombies (attente 2 min, pool de 8) | `StreamUse.track(maxOpen=2)` ferme les plus anciens lecteurs d'un fichier qui grandit ; `GrowingStream.clientGone` (sonde d'un octet avec marque/retour, toutes les secondes, sur le socket de la connexion capturé par `createClientHandler`) | `aNewStreamRequestClosesTheOldestReader...`, `aReaderWhoseClientHungUpIsReleasedWhileItWaits`, `growingStreamStopsWaitingWhenTheClientIsGone`, `streamUseClosesTheOldestReadersBeyondTheCap` |
| 4 | Second `finish` bloqué pendant toute la relecture ; 404 « inconnu » = refus définitif | `Session.tryBeginFinish` (atomique) : second appel = 503 `verifying` immédiat ; session absente = 503 `unknown transfer` (le téléphone reprend par `begin`, qui répond « fait » si le fichier est là) ; côté téléphone 503/404 de `finish` = `IOException` (reprise) | `aSecondFinishWhileTheFirstVerifiesAnswersAtOnce...` (200 attendu 503), `aFinishForATransferTheTvLost...`, `aFinishAfterTheFirstConcluded...`. `MultipathServerTest.badRequestsAreRefusedCleanly` mis à jour (404 -> 503, contrat voulu) |
| 5 | « buffering » éternel garde la politique allumée ; lecteur d'une copie morte boucle | `PlaybackGovernor.settle` : `buffering` compte au plus 30 s sans avance de la tête, verrou « éteint » jusqu'à une vraie avance (crash, flapping) ; `/stream/` : 503 `copy stopped` si l'octet demandé n'est pas là et la copie est morte, `deadWaitMs` 5 s en cours de lecture | `aBufferingPlayerThatNeverAdvances...`, `aBufferingPlayerWhoseHeadMoves...` (témoin), `aFlappingPlayer...`, `aStreamForADeadCopy...`, `growingStreamGivesUpQuicklyOnceTheCopyIsDead` |
| 6 | `onChange` hors verrou ; `json()` pouvait lancer le fsync reporté sur le fil `/api/info` | `onChange` sérialisé, toujours avec la dernière décision ; `json()` ne vide plus en ligne (fil `cb-deferred-sync`) | `jsonNeverRunsTheDeferredWorkOnTheCallersThread` |
| 7 | `syncPart` ouvrait en « rw » : un .part vide ressuscité | fsync d'un fichier ouvert en lecture seule, `isFile` d'abord | `syncingAPartThatWasRenamedDoesNotCreateAnEmptyPart` |
| 8 | `writeBps` mesure le cache de pages | KDoc de `WriteStats` ; le plafond reste moitié de la mesure bornée 1-6 Mo/s. Débit soutenu sur fenêtre : non fait (voir risques) | — |
| 9 | Tests / doc | ligne R-06 dans `docs/REGRESSIONS.md` ; `leadMs` supprimé (code mort) et son test ; `admitAhead` câblé ; `DownloadTest.downloadResumesWithRangeAfterACut` : `ReceiverServer(port=0)` + `listeningPort` et vraie pause de 20 ms entre essais | — |

Rouge avant correctifs : 17 des 19 tests en échec par assertion (les 2 verts sont des témoins : voie lente par la fin quand la TV préalloue, tête qui avance). Vert après : 19/19.

## Garanties conservées
Rien n'est déclaré complet avant relecture complète + `force(true)` + renommage (`PartAssembler.finish` inchangé ; le test de double `finish` compare le fichier final octet par octet). Le 429 et le report de bloc ne touchent jamais l'état des blocs.

## Suites
`:core:test` complet : 2478 tests, 0 échec, 2 ignorés. `:receiver:compileDebugKotlin` et `:sender:compileDebugKotlin` : OK.

## Risques restants
- `maxStreams` annoncé au `begin` : une copie commencée pendant une lecture reste à 2 voies après la fin de la lecture jusqu'à son prochain `begin` (relance) ; la contre-pression 429 reste le garde-fou.
- Le 429 « jette » un bloc entier de réseau (≤ 17 Mio) : un peu de Wi-Fi perdu, mais plus de pénalités.
- Fenêtre de tête (≥ 32 Mio) : un trou de cette taille peut encore être zéro-rempli sur exFAT (quelques secondes), sous le délai de 20 s.
- Latch « buffering » : suppose que `posMs` avance pendant une lecture saine ; un lecteur qui ne le publierait jamais serait traité comme bloqué au premier `buffering` de plus de 30 s.
- Sonde du socket : suppose que le client ne pipeline pas de requête pendant une réponse (libVLC ne le fait pas) ; l'octet lu est de toute façon remis (`reset`).
- Débit d'écriture soutenu sur fenêtre (8) non fait ; fsync reporté vidé par le fil de `playbackChanged` ou par `json()`.

## Seule la vraie TV confirmera
Le zéro-remplissage exFAT avant/après (temps du `begin` et des premiers blocs), le comportement de libVLC (un seul lecteur ou deux par saut, reconnexion après 503 `copy stopped`), l'effet de la priorité d'arrière-plan sur GaiaOS, le débit réel de la clé et la durée d'une relecture finale de 2 Go à 12 Mo/s côté téléphone (boucle de reprises 503 `verifying`).
