# w15-04 — Téléphone : services d'envoi sans plantage ni attente infinie (SecurityException, bornes, `onTimeout`, génération de travail, `abort`)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (cycle de vie des services, états partagés, annulation) · statut : PRÊT
> **Groupe : W15-S0-a** (vague W15, tranche S0, risqué n° 3 : à lancer après la fusion de w15-01 ou w15-03 pour respecter « 2 risqués max ») · prérequis : aucun ; W13 w13-04 (`TvClient`/`TransferClient`) et w13-07 (services) **se rebasent** sur ce cahier · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*InFlight*' --tests '*Transfer*' --tests '*Multipath*' --tests 'castbridge.core.tv.UploadJobsTest'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 2 j) · audit Opus : oui

**Vague 15 S0 (cœur + téléphone) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-04`. Rapport : `docs/agent-reports/sonnet-w15-04.md`. Règle : test rouge d'abord.

## Objectif
Aucun chemin d'envoi ne **plante** ni n'**attend sans fin** : exceptions de source attrapées, bornes de reprise, `onTimeout` des services `dataSync`, `startForeground` protégé partout, un seul travail actif par service avec un identifiant de génération (plus d'écriture tardive sur le suivant), état publié dès le début d'un envoi Bluetooth, `abort` envoyé à l'annulation.

## Pourquoi (preuves)
- X-15 : `S/UploadService.kt:235` `openFileDescriptor` lève `SecurityException` ; `C/tv/TvClient.kt:280-294` n'attrape que `IOException`/`HttpError` ⇒ sortie du `thread("upload")` ⇒ plantage. `S/TransferQueueService.kt:38` `stopSelf()` sans `startId`.
- X-04 : `TvClient.kt:213` `giveUpAfter = Int.MAX_VALUE` ; `:287-289` 403/429/5xx ⇒ `Waiting` sans fin ; `UploadService.kt:185` `resolve()` sans fin ; `TransferClient.kt:128` `begin` rejoué sans fin ; manifeste téléphone `:144-156` trois services `dataSync`, **aucun** `onTimeout` (grep vide) ; `TransferQueueService.kt:49` `startForeground` non protégé (`UploadService.kt:84` l'est).
- X-01/X-02 : `UploadService.kt:74,358` (2ᵉ `start()` perdu, `_state = Idle` écrase) ; `:71,145,174,300` (annulation ⇒ écritures tardives ⇒ `TransferQueue.watch` `S/TransferQueue.kt:135-137` marque le suivant « annulé »).
- X-05 : `S/TransferQueue.kt:140` 20 s ; `S/BtUploadService.kt:44,153-154,216` aucun état avant le 1ᵉʳ octet.
- X-16 : `C/xfer/TransferClient.kt:70` `abort` jamais appelé.
- Q-01 : `CT/ReceiverTest.kt:81-88` boucle serrée (même trou).

## Fichiers possédés
`S/UploadService.kt`, `S/TransferQueueService.kt`, `S/TransferQueue.kt`, `S/BtUploadService.kt`, `C/tv/TvClient.kt` (classe `ResumableUpload`/`ResumableDownload` : `giveUpAfter`, `catch`, borne « sans progrès »), `C/xfer/TransferClient.kt` (`abort` à l'annulation, borne `begin`), nouveau `C/tv/UploadJobs.kt` (modèle pur de génération/arbitrage), nouveaux `CT/tv/UploadJobsTest.kt`, `CT/InFlightTest.kt` (ajouts), `CT/ReceiverTest.kt` (borne). **Hors zone** : `C/xfer/Lanes.kt`, `Scheduler.kt` (w15-10), `ReceiverServer.kt`, écrans, `S/MoveToTv.kt` (w15-05).

## Signatures (additives)
```kotlin
// C/tv/UploadJobs.kt (pur)
class UploadJobs { fun start(job: JobKey): Admission /* Accepted(gen) | Busy(current) */ ; fun isCurrent(gen: Int): Boolean ; fun cancel(gen: Int) ; fun finish(gen: Int) }
// ResumableUpload : giveUpAfter borné par défaut (ex. 120 essais) + stallLimitMs (30 min sans octet) ⇒ Failed("…reprendra") ; catch (e: SecurityException) ⇒ Failed(sourceLost = true)
// State.Failed(reason, sourceLost: Boolean = false, resumable: Boolean = true)   // champs additifs
```

## Étapes
1. **Rouge** : `InFlightTest.sourceDeniedFailsCleanly` (`openAt` lève `SecurityException` ⇒ `Failed(sourceLost)`, aucun thread mort) ; `InFlightTest.repeated403EndsAfterBound` (horloge/`sleep` injectés ⇒ `Failed` après la borne, pas de boucle) ; `InFlightTest.noProgressForThirtyMinutesGivesUp` ; `UploadJobsTest.secondStartIsRefusedWhileBusy`, `staleGenerationCannotWriteState` ; `TransferQueueTest.watchIgnoresStaleFailure` (travail fantôme) ; `ReceiverTest.insufficientStorageIsFatal` reçoit un délai global (10 s) et une réponse 503 ⇒ doit finir en `Failed`/`Waiting` borné, pas boucler.
2. `TvClient` : bornes, `SecurityException`, `Failed` additif ; `TransferClient.run` : `begin` borné, `abort()` appelé par `cancel()` quand une session existe.
3. `UploadService` : `UploadJobs` (refus du 2ᵉ `start` avec `Toast` français « Un envoi est déjà en cours : il sera repris par la file ») ; toute écriture de `_state`/notification gardée par `isCurrent(gen)` ; `finish` ⇒ `nm.cancel(NOTIF)` ; `onTimeout(startId, type)` ⇒ `Failed(« L'envoi reprendra quand l'app sera rouverte »)` + `stopSelf` ; `startForeground` protégé partout ; `stopSelf(startId)`.
4. `TransferQueueService`, `BtUploadService` : même traitement ; `BtUploadService.run` publie `Waiting(0, total, « négociation Bluetooth… »)` avant toute négociation ; `ACTION_CANCEL` ⇒ `stopSelf`. `TransferQueue.watch` : délai de démarrage porté à 90 s **ou** « service vivant » ; filtre par génération.
5. Vert : porte ; `:sender:compileDebugKotlin` ; 20 passes de `*ReceiverTest*`.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -n "Int.MAX_VALUE" C/tv/TvClient.kt` vide ; `grep -n "override fun onTimeout" S/UploadService.kt S/TransferQueueService.kt S/BtUploadService.kt` = 3 ; `grep -n "startForeground(" S/TransferQueueService.kt` entouré d'un `runCatching`/`try` ; tests rouges puis verts cités ; `:sender:compileDebugKotlin` OK.

## Cas limites
Annulation pendant un `sleep` injecté ; `onTimeout` pendant la relecture `finish` (laisser la TV finir : `Failed(resumable=true)`) ; `abort` sur TV ancienne (404 ignoré).

## À ne pas faire
Pas de texte « Blocker » (W13) ; pas de modification de `Lanes`/`Scheduler` ; ne pas persister la file (W7 w7-21) ; ne pas changer le protocole ; ne pas toucher `MoveToTv`.

## Rapport
`STATUT`, sorties rouge/vert, liste des `catch` ajoutés (fichier:ligne), valeurs des bornes choisies, question d'audit : le refus du 2ᵉ `start()` peut-il casser `TvTransferScreen`/`TvHub` (séries) ? (attendu : elles passent par la file, w15-10 les y déplace).
