# w2-08 — Un exécuteur partagé pour CastBridge-TV ; plus de `Thread {}` bruts

**Vague 2 · Effort S (≈ 4 h) · Statut PRÊT.** Branche `claude/sonnet-w2-08`. Rapport : `docs/agent-reports/sonnet-w2-08.md`.

## Objectif
Les threads bruts et les exécuteurs mono-thread créés par écran (jamais arrêtés) de l'app TV sont remplacés par un exécuteur partagé nommé, daemon, borné ; les handlers sont nettoyés en `onDestroy`.

## Pourquoi (preuves)
- 14 sites `Thread {` non poolés en receiver : `R/ChessActivity.kt:222,237,545-553,559` (+ poller `:525` avec `Thread.sleep`), `R/PlayerActivity.kt:449,558,697,705,709`, `R/ParentalUi.kt:91`, `R/SudokuActivity.kt:256` (et `ActivationActivity`, `LearnActivity`, `TvService` : hors zone, traités ailleurs).
- 9 exécuteurs mono-thread : `R/HomeScreen.kt:58`, `R/LibraryScreen.kt:54`, `R/TvCards.kt:134`, `R/DownloadsActivity.kt:38` créés **par instance d'écran** sans `shutdown()` ; sur une TV à 1 Go, chaque thread inactif coûte ≈ 1 Mo de pile.
- `R/DownloadsActivity.kt:81` ne retire que `refresher`.
- Audit : OP-6 (T4, T5, T6).

## Fichiers possédés
Nouveau `R/TvExecutors.kt`, `R/ChessActivity.kt`, `R/SudokuActivity.kt`, `R/ParentalUi.kt`, `R/LibraryScreen.kt`, `R/TvCards.kt`, `R/DownloadsActivity.kt`, `R/HomeScreen.kt` (**l'exécuteur seulement** ; w2-04 modifie les tuiles : si le coordinateur lance les deux en parallèle, **ne pas toucher HomeScreen** et le signaler). **Hors zone** : `PlayerActivity.kt` (w2-04), `LearnActivity.kt`/`LearnHub.kt` (w2-05), `ActivationActivity.kt` (w2-03), `TvService.kt` (w2-07), tout le sender.

## Étapes
1. `TvExecutors` : `val io: ExecutorService` = `ThreadPoolExecutor(2, 4, 30 s, LinkedBlockingQueue(256), ThreadFactory nommé "cb-io-N", daemon)` avec politique `CallerRunsPolicy` ; `val single: ExecutorService` (1 thread, « cb-serial ») pour ce qui exigeait un ordre strict (`TvCards`, `LibraryScreen` : lire le code pour décider).
2. Remplacer chaque `Thread { … }.start()` par `TvExecutors.io.execute { … }` ; le poller d'échecs (`ChessActivity.kt:525`) devient une tâche planifiée (`ScheduledExecutorService` unique `TvExecutors.timer`) annulée en `onDestroy`.
3. Supprimer les `Executors.newSingleThreadExecutor()` par écran ; si un écran a besoin d'ordre, utiliser `TvExecutors.single`.
4. `onDestroy` de chaque activité possédée : `main.removeCallbacksAndMessages(null)` (ou équivalent) ; annuler les `Future`.
5. Vérifier qu'aucune tâche longue (téléchargement, VLC) ne passe par `io` avec un `CallerRunsPolicy` sur le fil principal : si une tâche peut durer > 1 s et être soumise depuis le main, utiliser `single` ou un `Future` avec timeout ; le documenter en commentaire.

## Critères d'acceptation
```sh
grep -n 'Thread {' android/receiver/src/main/kotlin/castbridge/receiver/{ChessActivity,SudokuActivity,ParentalUi,LibraryScreen,TvCards,DownloadsActivity}.kt   # 0 hit
grep -n 'newSingleThreadExecutor' android/receiver/src/main/kotlin/castbridge/receiver/{HomeScreen,LibraryScreen,TvCards,DownloadsActivity}.kt   # 0 hit (HomeScreen seulement si touché)
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (si SDK)
```
Observable (campagne) : aucun changement fonctionnel ; `GET /api/sysinfo` (si le nombre de threads y figure) ou `adb shell ps -T` montre moins de threads `cb-*` après avoir ouvert/fermé 5 écrans.

## Cas limites
- Les `Handler(Looper.getMainLooper())` restent ; seuls les threads de travail changent.
- `ChessActivity` : le relais réseau a un délai de 5 s ; la file bornée (256) est largement suffisante ; en cas de saturation, `CallerRunsPolicy` exécute sur l'appelant : acceptable pour des appels courts.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; aucun changement de comportement ; ne pas toucher aux fichiers hors zone.

## Rapport
`STATUT`, tableau site → remplacement, résultat de compilation.
