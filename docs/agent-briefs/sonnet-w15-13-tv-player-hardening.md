# w15-13 — Lecteur TV : position sauvée périodiquement, `stop()` hors fil principal, repli logiciel seulement sur erreur de décodage, lecture progressive bornée, miniatures et libVLC jamais en double
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus par échantillon · statut : PRÊT
> **Groupe : W15-S2-c** (vague W15, tranche S2, non risqué pour la liaison ; fichiers disjoints de w15-14/15/16/17) · prérequis : S0 fusionnée · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Progressive*' --tests '*PlayerFeatures*' --tests '*LibraryStore*' --tests 'castbridge.core.tv.PlayerPolicyTest'`
> **Jauge : ≈ 350 k jetons entrée / 18 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 15 S2 (TV + cœur) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-13`. Rapport : `docs/agent-reports/sonnet-w15-13.md`. Règle : test rouge d'abord ; la logique de décision va dans le cœur (`PlayerPolicy`), l'activité ne fait que l'appliquer (règle de pureté W14 § 3.4).

## Défauts traités (preuves `PLAN-STABILISATION` § 2.5)
P-04 (`R/PlayerActivity.kt:191,638-643,931,949` : position sauvée seulement à la pause/arrêt) · P-05 (`:345-355` `mp.stop()` synchrone sur le fil principal, ≤ 30 s sur `/stream`) · P-06 (`C/tv/Progressive.kt:38-72`, `PlayerActivity.kt:305-319` : envoi avorté ⇒ tampon sans fin ; 404 ⇒ repli **logiciel**) · P-07 (`ReceiverServer.kt:1334-1348` `/api/play` d'un MP4 moov-en-fin incomplet non refusé) · P-11 (`:491-499`, `PlayerActivity.kt:906-916` clé de reprise « 0:Depuis le téléphone ») · P-12 (`Progressive.kt:60-82` : chaque connexion `/stream` retient un thread ≤ 30 s) · P-14 (`R/Thumbnailer.kt:60-88`, `TvService.kt:209` : miniature libVLC pendant une lecture) · P-16 (`R/LanguesActivity.kt:192-196` `MediaPlayer` non libéré, `prepare()` synchrone) · P-17 (`R/LearnHub.kt:163-166`, `R/LanguesHub.kt:81-82` chemin non validé).

## Fichiers possédés
Nouveau `C/tv/PlayerPolicy.kt` (pur : « sauver maintenant ? », « repli logiciel autorisé ? », « clé de reprise », « lecteurs `/stream` par fichier ») + `CT/tv/PlayerPolicyTest.kt` ; `C/tv/Progressive.kt`, `C/tv/ReceiverServer.kt` (**zones** `/stream` `:1285-1318` et `playIncomplete`/`playurl` `:491-499,1334-1348`), `R/PlayerActivity.kt`, `R/PlayerExtras.kt` (hors zone sous-titres : w15-14), `R/Thumbnailer.kt`, `R/LanguesActivity.kt`, `R/LearnHub.kt` (zone `playVideo`), `R/LanguesHub.kt` (zone `mediaFile`), `CT/ProgressiveTest.kt`. **Hors zone** : `C/tv/PlayerFeatures.kt` (sous-titres, w15-14), `R/TvService.kt`, `R/HomeScreen.kt`, `S/**`.

## Étapes (test rouge, correctif, vert)
1. `PlayerPolicy` : `shouldSave(now, lastSaveMs, playing)` (≥ 20 s) ; `softwareFallbackAllowed(error, streaming, complete)` (jamais sur erreur réseau d'un `.part`) ; `resumeKey(name, size, url)` (hash d'URL si `size == 0`) ; `maxStreamReaders = 2` ; tests de table.
2. `PlayerActivity` : tick 20 s dans `onPlaying` ⇒ `bgRun { db.onStopped(...) }` ; `releasePlayer` sur un exécuteur dédié avec verrou, fil principal libre (bouton BACK immédiat, écran « Arrêt… » ≤ 200 ms) ; repli selon la politique ; attente de données bornée à 2 min avec message « L'envoi s'est interrompu : relancez-le depuis le téléphone ».
3. `Progressive`/`/stream` : ≤ 2 lecteurs par fichier, 503 `{"code":"STREAM_BUSY"}` au-delà ; `GrowingStream` garde le fichier ouvert entre blocs (fermeture à la fin ou après 2 s d'inactivité) ; `playIncomplete` ⇒ `Mp4Atoms.layout` ⇒ 409 si moov en fin (test rouge d'abord).
4. `Thumbnailer` : `play()` annule le worker en cours (`ThumbWorker.cancel()`) avant `ensurePlayer` ; une seule instance libVLC de miniatures réutilisée.
5. `LanguesActivity` : `try/finally release()`, `prepareAsync`, `setAudioAttributes` ; `LearnHub`/`LanguesHub` : `safeName` + rejet `..`, `/`, `\`.
6. Vert : porte ; `:receiver:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; `PlayerPolicyTest` ≥ 8 cas ; `ProgressiveTest` : 3 nouveaux tests (lecteurs concurrents, envoi interrompu, moov en fin refusé) rouges puis verts ; `grep -n "mp.stop()" R/PlayerActivity.kt` : appel hors fil principal (relecture) ; aucune ligne de `PlayerFeatures.kt` modifiée.

## À ne pas faire
Pas de changement de dépendance libVLC ; pas de sous-titres (w15-14) ; pas de texte hors des deux messages cités ; ne pas toucher `TvService`.

## Rapport
`STATUT`, table défaut ⇒ test, mesure (durée de `releasePlayer` simulée), à valider sur la vraie TV (liste H point 4 : lecture pendant l'envoi puis coupure).
