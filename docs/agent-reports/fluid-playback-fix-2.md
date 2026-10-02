# Second audit of « la lecture d'abord » : constats restants

> Branche `claude/fluid-playback-fix-2` (depuis `claude/fluid-playback-fix`). Sonnet 5.5, 2026-10-02. Rien poussé, aucun appareil touché.
> Tests : ajouts à `core/src/test/.../xfer/FluidPlaybackFixTest.kt` (7 tests).

| # | Constat | Correctif | Tests (rouge par assertion avant) |
|---|---|---|---|
| 1 MAJEUR | Après le délai de 60 s d'un `finish` long, le téléphone relance `begin`, qui prenait le verrou par nom tenu par la relecture : un fil HTTP bloqué toutes les ~62 s, le pool de 8 se remplit et affame `/stream/` et `/api/info` | `transferBegin` répond 503 `{"error":"verifying","retryMs":2000}` AVANT le verrou si une session en `finish` existe pour l'id OU pour le nom (`TransferHost.finishingName`, cas d'un autre `blockSize`). Téléphone : `TransferApi.Verifying(retryMs)` levée par `begin` et `finish`, la boucle attend `retryMs` (borné 0,2-30 s) au lieu de `retryDelayMs` | `aBeginWhileAFinishVerifiesAnswersVerifyingAtOnce...` (503 attendu, 200 obtenu après la relecture ; un `begin` d'un AUTRE fichier reste < 1,5 s), `thePhoneWaitsForTheRetryMsOf...` |
| 4 mineur | Le verrou « buffering > 30 s » coupait la politique quand le lecteur est affamé par une copie du même fichier | `PlaybackSignal.copyBytes` (cumul des octets écrits, `WriteStats.total`) ; verrou seulement si aucune copie n'a écrit pendant la fenêtre OU si le fichier lu n'est pas celui qu'on copie (`growing` faux). Passer par `paused`/`idle` remet à zéro `lastAdvanceAt` | `aBufferingPlayerStarvedByALiveCopy...` (rouge), `...DeadCopy...`, `...NotBeingCopied...`, `goingThroughPausedResetsTheBufferingCounters` (le compteur était déjà remis à zéro par `lastHead = -1` : test de non-régression, vert dès le départ) |
| 5 mineur | Le fsync reporté tournait encore en ligne via `current()`/`refresh()` (fils de bloc, 429, `/api/stop`) | Tout `drain` passe par `drainAsync` : un seul fil `cb-deferred-sync` à la fois (paramètre `drainer`, par défaut un fil daemon) | `everyDeferredSyncRunsOnTheDedicatedThreadWhateverTheCaller` (current, refresh, json ; rouge) |

Tests existants adaptés : `governorDrainsTheDeferredWorkWhenPlaybackStops` et `aBufferingPlayerThatNeverAdvances...` passent `drainer = { it() }` (graine de test : drain synchrone, même assertion).

## Suites
Rouge avant correctifs : 4 des 7 nouveaux tests en échec par assertion (les 3 autres sont des témoins verts). `:core:test` complet : 2485 tests, 0 échec, 2 ignorés, aucun arrêt par le chien de garde. `:receiver:compileDebugKotlin` et `:sender:compileDebugKotlin` : OK.

## Reportés (mineurs, non touchés)
Constats 2, 3 et 6 de l'audit : non traités, comme demandé.

## Risques
- Règle (4) à la lettre : un lecteur bloqué sur un fichier qui n'est PAS celui d'une copie (autre copie écrivant sur la même clé) est relâché après 30 s, alors qu'il pourrait être affamé par cette autre copie.
- Un ancien téléphone traite le 503 `verifying` de `begin` comme une coupure et attend son délai fixe (2 s) : comportement correct, sans `retryMs`.
- Un `begin` d'un fichier de même nom mais de taille différente pendant la relecture reçoit aussi 503 (il aurait attendu le même verrou).
