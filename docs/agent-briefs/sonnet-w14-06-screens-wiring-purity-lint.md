# w14-06 — CastBridge (téléphone) et CastBridge-TV : les écrans et services appellent les fonctions pures ; test lint `UiStatePurityTest` qui interdit toute décision d'état dans un écran
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus en échantillon · statut : PRÊT
> **Groupe : W14-c** (vague W14, tranche S1) · prérequis : w14-04 et w14-05 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.lint.UiStatePurityTest' && cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin :receiver:compileDebugKotlin`
> **Jauge : ≈ 500 k jetons entrée / 24 k sortie** (effort M) · audit Opus : échantillon

**Vague 14c (Android) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 3.3, § 3.4. Branche `claude/sonnet-w14-06`. Rapport : `docs/agent-reports/sonnet-w14-06.md`.

## Objectif
Câbler chaque écran/service cité en § 3.3 sur sa fonction pure (w14-05, w14-04) de sorte que **l'écran ne décide plus rien** (il collecte des faits, appelle, dessine), et écrire le test lint qui lit les sources de `:sender`/`:receiver` et **échoue** sur toute décision résiduelle hors liste blanche datée. Comportement visible **inchangé** sauf les deux corrections par construction : le message de 401 de `TvHome` est rendu (`wizardReason`) et une notification d'envoi ne disparaît plus sans texte final.

## Pourquoi (preuves)
Sites : `S/TvHome.kt:106-137,174-178`, `S/TvScreen.kt:61,77,113-115,236`, `S/TvPairScreen.kt:65-66,93,294`, `S/ActivateTvActivity.kt:63,95`, `S/UploadService.kt:255-265,272`, `S/TransferQueueService.kt:59`, `S/BtUploadService.kt:179`, `S/DownloadService.kt:154`, `S/PinStore.kt:22`, `R/UpdateInstaller.kt` (`a.code < installedCode()`), `R/BtServer.kt:113-127`, `R/HomeScreen.kt:124-126` (« Prêt à recevoir »). Modèle de lint : `CT/link/RouteStatusLintTest` (W13) lit des sources ; `tools/routes/list_routes.py` lit les sources du cœur et du récepteur.

## Fichiers possédés
`S/{TvHome,TvScreen,TvPairScreen,ActivateTvActivity,UploadService,TransferQueueService,BtUploadService,DownloadService,PinStore}.kt`, `R/{UpdateInstaller,BtServer}.kt`, `R/TvService.kt` (**zone** : la ligne de statut des transferts passée à `ReceiveCards`), `R/HomeScreen.kt` (**zone** : `headline`). Nouveau : `CT/lint/UiStatePurityTest.kt`, `CT/lint/purity-allowlist.txt`. **Hors zone** : `S/TvLink.kt`, `S/LinkAndroid.kt` (façade : liste blanche), `S/OpenWithActivity.kt` (déjà pur), `S/link/**` (W13), tout `C/**`.

## Étapes
1. `TvHome` : remplacer `reachable`/textes par `HomeLinkView.decide(HomeFacts(...))` ; rendre `wizardReason` au-dessus de l'assistant (un `Text`, même style que `msg`).
2. `TvScreen` : `ManualTvViews.decide` ; clé de PIN par `PinKeys.normalize` (IP sans port ⇒ `:8765`).
3. `TvPairScreen`, `ActivateTvActivity` : `PairScreenView.decide` / `ActivateTargetView.decide` ; supprimer les littéraux « Aucune TV … ».
4. Services : `XferTexts.notification(state)` pour les 4 ; sur `Failed`/`Cancelled` : **afficher** la `Notice` finale (`ongoing=false`) puis `stopForeground(STOP_FOREGROUND_DETACH)` ; ne jamais `cancel` sans `final`.
5. `PinStore.put` ⇒ `putAll(PinKeys.keysOf(...))` ; `get` essaie `normalize`.
6. `UpdateInstaller` : `when (UpdateRules.mayInstall(installedCode(), a.code, force, BuildConfig.DEBUG)) { Refuse -> err(http, reason) }` ; `BtServer` : alimente `ReceiveCards` (ligne `1-bt` conservée mais lue par `of`) ; `HomeScreen` : `ReceiveCards.headline`.
7. `UiStatePurityTest` : lit récursivement `android/sender/src/main/kotlin` et `android/receiver/src/main/kotlin` (chemin relatif au projet `:core` : `../sender/...`, comme `tools/routes`), applique les motifs de la conception § 3.4 (liste **dans le test**, chaque motif avec le nom de la fonction pure à appeler), ignore les fichiers de `purity-allowlist.txt` (format : `chemin | motif | échéance : cahier`). Échec = liste `fichier:ligne — motif — remplacer par …`.
8. Liste blanche initiale **minimale** : `S/TvLink.kt` (`LinkUi`), `S/LinkAndroid.kt`, et toute ligne que ce cahier n'a pas pu migrer (avec échéance W13 ou W7 nommée).

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -rn '"Aucune TV' android/sender/src/main/kotlin android/receiver/src/main/kotlin | grep -v 'TvLink.kt' | wc -l` ⇒ 0 ; `grep -rn '· \$pct %' android/sender/src/main/kotlin | wc -l` ⇒ 0 ; `wc -l < CT/lint/purity-allowlist.txt` ≤ 8 ; `:core:test` complet vert ; les deux modules compilent.

## Cas limites
Compose : `remember`/`LaunchedEffect` restent dans l'écran (collecte de faits), seule la **décision** part ; `DownloadService` : état dédié si `XferState` ne convient pas (le dire, pas de texte en dur) ; `HomeScreen` TV : le `headline` ne doit pas casser le D-pad (aucune vue ajoutée, seul le texte change).

## À ne pas faire
Aucun nouveau comportement au-delà des deux corrections par construction ; aucune modification de `C/**` (si une signature manque : `STATUT: BLOQUÉ`, question) ; ne pas toucher `S/OpenWithActivity.kt` ; ne pas construire d'APK ; aucune liste blanche sans échéance.

## Rapport
`STATUT`, table site → fonction (fait / liste blanche + échéance), sortie du lint avant/après, points **à vérifier sur appareil** par la fumée (notification finale sur échec, `wizardReason` visible), contrats pour W13 (w13-07/08/09 lisent `XferTexts`, `PinKeys`, `HomeLinkView`).
