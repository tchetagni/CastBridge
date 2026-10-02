# w13-07 — CastBridge (téléphone) : services d'envoi branchés sur le chien de garde et les `Blocker` (notification « Envoi bloqué » + « Réparer », lien profond, file d'attente)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (repli jeton après PIN refusé) · statut : PRÊT
> **Groupe : W13-c** (vague W13, tranche S1) · prérequis : w13-03 et w13-04 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin && cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M) · audit Opus : échantillon

**Vague 13c (téléphone) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W13` § 1.3 (F1), § 3.2, § 3.4, § 3.6 (notification), § 2 (`QUEUE_NO_LINK`, `PHONE_BG_KILLED`). Branche `claude/sonnet-w13-07`. Rapport : `docs/agent-reports/sonnet-w13-07.md`.

## Objectif
(1) `UploadService` et `BtUploadService` font tourner `TransferWatchdog` ; sur `PREFLIGHT` ils lancent `Preflight.run` (env Android) et publient le `Blocker` dans `BlockerState` ; (2) `UploadService.State.Failed/Waiting` portent `blocker` (additif) ; **F1 corrigée** : un `Failed` avec `Blocker` produit une **notification persistante silencieuse** « Envoi bloqué : <titre> » avec actions « Réparer » (lien profond `castbridge://repair?b=<wire>&tv=<nom>`) et « Annuler » ; `ESCALATE` fait de même sans arrêter l'envoi ; (3) **repli de secret** : `fallbackCredential = { TvLinkManager.credentialForBase(base) }` ; (4) `TransferQueue` : `QUEUE_NO_LINK` au lieu de « TV non connectée » ; si `PinStore` a un PIN utilisable pour cette TV, repli sur `UploadService` avec PIN ; `Idle` après démarrage ⇒ `PHONE_BG_KILLED` ; (5) `BlockerLog` du téléphone alimenté (`files/link/blockers.txt`).

## Pourquoi (preuves)
- F1 : `S/UploadService.kt:255-265` (`notifyProgress` ignore `Failed`), `:295-298` (`finish` ⇒ `stopSelf`) ; `:84-87` (`startForeground` refusé) ; `:124` (`credential` lambda) ; `:155-157` (fast / classique) ; `:102-104` (`resolve`) ; `:247-253` (`watchNetwork`).
- Bluetooth : `S/BtUploadService.kt:59-117` (`run`), `:94` (`Failed` autre que « liaison perdue » ⇒ fin), `:127-137` (`httpUpload`, `giveUpAfter = 6`).
- File : `S/TransferQueue.kt:82-83` (60 s puis « TV non connectée »), `:88-89` (doublon), `:92` (secret = `session.credential`), `:121-142` (`watch` : « Envoi interrompu », « L'envoi n'a pas démarré »).
- Secret par base : `S/TvLink.kt:200-206` (`credentialForBase`) ; sondes : `S/LinkAndroid.kt:75-93`.
- Contrats : `C/link/{TransferWatchdog,Preflight,LinkHealth,BlockerLog,SupportCode}.kt` (w13-03), `ResumableUpload(onBlocker, ctx, fallbackCredential)` (w13-04).

## Fichiers possédés
`S/UploadService.kt`, `S/BtUploadService.kt`, `S/TransferQueue.kt`, `S/TransferQueueService.kt` ; nouveaux : `S/link/BlockerState.kt` (`StateFlow<Blocker?>` par service + `last`), `S/link/PreflightAndroid.kt` (`PreflightEnv` Android : réutilise `AndroidLinkEnv.probe/check`), `S/link/BlockerNotifications.kt`, `S/link/BlockerLogPhone.kt`. **Hors zone** : `S/TvHome.kt`, `S/TvScreen.kt`, `S/TvPairScreen.kt`, `S/TvLink.kt`, `S/LinkAndroid.kt`, `S/PinStore.kt`, manifeste (w13-08/09), `C/**`.

## Signatures (contrat pour w13-08, w13-09)
```kotlin
// S/link/BlockerState.kt
object BlockerState { val upload: StateFlow<Blocker?>; val bt: StateFlow<Blocker?>; val last: StateFlow<Blocker?>; fun clear(kind: Kind); fun supportCode(b: Blocker): String }
// S/UploadService.kt (additif)
data class Waiting(val job: Job, val sent: Long, val total: Long, val reason: String, val blocker: Blocker? = null) ; data class Failed(val job: Job?, val reason: String, val blocker: Blocker? = null)
companion { fun retryWithCredential(ctx: Context, credential: String) }   // relance le dernier job avec un nouveau secret (utilisé par la saisie PIN de w13-08)
// S/link/BlockerNotifications.kt
object BlockerNotifications { fun blocked(ctx: Context, b: Blocker, tvName: String, cancelIntent: PendingIntent); fun clear(ctx: Context); fun repairIntent(ctx: Context, b: Blocker, tvName: String): PendingIntent /* castbridge://repair?b=&tv= */ }
```

## Étapes
1. `UploadService.runJob` : `watchdog.start(route)` ; `onState` ⇒ `watchdog.onBytes` ; fil `tick` 2 s ⇒ `PREFLIGHT` ⇒ `Preflight.run(PreflightAndroid(this), probe)` ⇒ `BlockerState.upload = b` + `BlockerLogPhone.add(b)` ; `ESCALATE` ⇒ `BlockerNotifications.blocked` ; `GIVE_UP` ⇒ `cancelled = true` + `Failed(texte, b)`.
2. `ResumableUpload(..., onBlocker = { BlockerState.upload.value = it }, ctx = BlockerCtx(tv = job.tvName, credential = kind(cred), route = LAN), fallbackCredential = { TvLinkManager.credentialForBase(base) })` ; même chose pour `runFast` (`TransferClient(onBlocker, ctx)`).
3. `finish(Failed(_, blocker != null))` ⇒ notification persistante (canal `upload`, `IMPORTANCE_LOW`, `setOngoing(false)`, `setAutoCancel(false)`) : titre « Envoi bloqué : <BlockerTexts.title> », texte « <cause> », actions « Réparer » / « Annuler » ; `Done` ou nouvel envoi ⇒ `clear`.
4. `BtUploadService` : chien de garde Bluetooth (premier octet 45 s) ; `ResumableBtUpload` : refus `ERR_*` ⇒ `BlockerMap.ofBt` ⇒ `BlockerState.bt`.
5. `TransferQueue.runOne` : `waitForTv()` null ⇒ si `PinStore.get(clé)` utilisable ⇒ `UploadService.start(..., pin)` (repli), sinon `finish(item, false, BlockerTexts.of(QUEUE_NO_LINK).sentence)` + `BlockerState.last` ; `watch` : `Idle` après `started` et non annulé ⇒ `PHONE_BG_KILLED`.
6. `TransferQueueService.notification()` : si `BlockerState.upload != null`, texte = titre du blocage.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -n 'is UploadService.State.Failed' android/sender/src/main/kotlin/castbridge/sender/UploadService.kt` montre la notification ; `grep -c 'BlockerMap\|BlockerState' android/sender/src/main/kotlin/castbridge/sender/UploadService.kt` ≥ 4 ; aucun `Thread.sleep` nouveau sur le fil principal ; test manuel documenté (émulateur TV `ReceiverServer` + faux PIN) dans le rapport.

## Cas limites
Deux envois enchaînés (file) : l'état et la notification sont remis à zéro au `onStartCommand` ; annulation pendant un pré-vol ; `startForeground` refusé ⇒ `Failed` + `PHONE_BG_KILLED` ; Android 13+ sans `POST_NOTIFICATIONS` ⇒ bandeau in-app seulement (w13-09).

## À ne pas faire
Aucune chaîne française nouvelle (`BlockerTexts`) ; ne pas renvoyer un PIN refusé ; ne pas toucher aux écrans ; ne pas démarrer un FGS depuis le fond hors règles existantes.

## Rapport
`STATUT`, lignes modifiées, intent exact du lien profond, ce que w13-08/09 doivent brancher, questions pour l'audit (repli jeton).
