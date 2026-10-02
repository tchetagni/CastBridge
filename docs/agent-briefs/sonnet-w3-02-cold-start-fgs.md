# w3-02 — Démarrage à froid hors du fil principal ; type de service d'avant-plan Android 14 ; StrictMode en debug

**Vague 3 · Effort M (≈ 2 j) · Statut PRÊT** (après w2-01 et w2-07, qui touchent `ActivationCenter` et `TvService`). Branche `claude/sonnet-w3-02`. Rapport : `docs/agent-reports/sonnet-w3-02.md`.

## Objectif
1. `ActivationCenter.init` (exec `getprop`, lectures `/sys`, vérification Ed25519 de chaque activation, `fsync`) ne s'exécute plus sur le fil principal ; les écrans attendent un état `ready` (écran de démarrage court) et `locked()` répond depuis un cache dès qu'il est connu.
2. `TvService.startCore` : balayage des volumes, `logResources`, `LotsHub.startup` sur `bg` ; `activationWatch` (`scanFiles` toutes les 5 s) sur `bg`.
3. `foregroundServiceType` ne dépend plus d'une permission runtime : `specialUse` (+ `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) ou `mediaPlayback` selon ce que GaiaOS accepte ; `startForeground` journalise `e.message`.
4. `StrictMode` (`penaltyLog`) en build debug pour détecter les régressions.

## Pourquoi (preuves)
- `R/PlayerActivity.kt:95` et `R/TvService.kt:194` → `ActivationCenter.init` sur le main (`Runtime.exec`, `/sys`, `reload()` = vérification de chaque activation, `SafeFile.write` + fsync) : plusieurs centaines de ms sur l'Amlogic 32 bits ; risque d'ANR.
- `R/TvService.kt:191-215` : `VolumeRegistry.refresh()` (commentaire : « no speed test » seulement), `logResources()` → `Storage.used(dir)` (`:762`) ; `:174` `ActivationCenter.scanFiles()` (volumes USB) sur le main toutes les 5 s en état verrouillé.
- `android/receiver/src/main/AndroidManifest.xml:75` `foregroundServiceType="connectedDevice"`, `R/TvService.kt:154-158` : sur API 34 ce type exige une permission (Bluetooth/USB…) **accordée** ; sur une installation neuve `startForeground` peut lever une exception, avalée (`:158` journalise seulement la classe) → service non promu, tué sous pression mémoire ; `BootReceiver` (`:987`) → `startForegroundService` sans `startForeground` réussi dans les 5 s → `ForegroundServiceDidNotStartInTimeException`.
- Aucun `StrictMode` (`grep -rn StrictMode android/receiver` vide).
- Audit : C2, C3, C6, C8, OP-5, OP-6.

## Fichiers possédés
`R/ActivationCenter.kt`, `R/TvService.kt`, `R/TvApp.kt`, `R/PlayerActivity.kt` (**`onCreate` et l'attente `ready` seulement**), `android/receiver/src/main/AndroidManifest.xml`. **Hors zone** : `ReceiverServer` (w3-01), `KeyBadge*`, `ActivationActivity`, `LearnHub`.

## Étapes
1. `ActivationCenter` : `init` découpé en `prepare()` (lourd, sur `TvService.bg` ou `TvExecutors.io`) et `ready: CountDownLatch` + `@Volatile cachedState` ; `state()/locked()/trial()` : si `ready` n'est pas atteint, attendre **au plus 2 s** puis renvoyer l'état « verrouillé » par défaut (fermé, jamais ouvert) ; `PlayerActivity.onCreate` affiche le logo et un « Démarrage… » tant que `ready` n'est pas atteint, puis continue comme aujourd'hui.
2. `TvService.startCore` : les trois travaux lourds sur `bg`, puis `main.post { started = true; … }` ; `activationWatch` planifié sur `bg`.
3. Manifeste : `android:foregroundServiceType="specialUse"` + `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Réception et diffusion vers la TV (CastBridge-TV)"/>` + permission `FOREGROUND_SERVICE_SPECIAL_USE` ; `startForeground(NOTIF, n, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` sur API ≥ 34, `CONNECTED_DEVICE` entre 29 et 33 (comportement actuel) ; `Log.w(TAG, "startForeground: ${e.javaClass.simpleName}: ${e.message}")` ; `DownloadService` (`dataSync`) : laisser, mais documenter le plafond 6 h/24 h d'API 34 dans un commentaire.
4. `TvApp.onCreate` : `if (BuildConfig.DEBUG) StrictMode.setThreadPolicy(ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())`.
5. Mesure : sur émulateur Android TV (ou TV), `adb logcat -s StrictMode` ne montre plus `ActivationCenter`/`VolumeRegistry` sur le main au démarrage ; `adb shell am start -W castbridge.receiver/.PlayerActivity` : `TotalTime` avant/après dans le rapport.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:assembleDebug        # compile (si SDK)
grep -n 'specialUse\|SPECIAL_USE' android/receiver/src/main/AndroidManifest.xml android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt   # présent
grep -n 'StrictMode' android/receiver/src/main/kotlin/castbridge/receiver/TvApp.kt   # présent sous BuildConfig.DEBUG
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*'     # inchangé, vert
```
Observable (campagne) : installation neuve, sans permission Bluetooth accordée, `adb shell dumpsys activity services castbridge.receiver` montre `isForeground=true` ; démarrage au boot sans crash ; écran d'accueil en < 2 s après le logo.

## Cas limites
- Si GaiaOS (userdebug, CVTE) refuse `specialUse`, repli `mediaPlayback` (la TV lit des médias : défendable) ; le code choisit au runtime en attrapant l'exception et en réessayant avec l'autre type **une fois**.
- `locked()` appelé depuis un fil HTTP avant `ready` : attendre le latch (2 s max), jamais ouvrir.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne jamais renvoyer « déverrouillé » par défaut en cas de doute ; textes en français.

## Rapport
`STATUT`, temps de démarrage avant/après (émulateur), type FGS retenu et pourquoi, journal StrictMode résiduel.
