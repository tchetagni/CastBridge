# w13-10 — Tests : catalogue table-driven sur faux transport (≥ 39 cas), lint des statuts de `ReceiverServer`/`routes.txt`, test de source « aucun catch muet »
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT
> **Groupe : W13-b** (vague W13, tranche S1) · prérequis : w13-01, w13-04, w13-05 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.BlockerCatalogueTest' --tests 'castbridge.core.link.RouteStatusLintTest' --tests 'castbridge.core.link.NoSilentFailureSourceTest'`
> **Jauge : ≈ 400 k jetons entrée / 25 k sortie** (effort M) · audit Opus : non

**Vague 13b (tests) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W13` § 4.1, § 2. Branche `claude/sonnet-w13-10`. Rapport : `docs/agent-reports/sonnet-w13-10.md`.

## Objectif
Trois tests JVM hors ligne qui **échouent** si un chemin finit sans `Blocker`, si la TV émet un statut non mappé, ou si un `catch` de la couche transfert redevient muet.

## Pourquoi (preuves)
- Modèle de faux serveur : `CT/TransferTest.kt:28-100` (`OneGigRuleServerTest`, `ReceiverServer` sur port 0, `ResumableUpload` avec `sleep = {}`) ; `CT/MultipathTransferTest.kt:210` (`SchedulerTest`, `FakeLane`) ; `CT/ResilientCallTest.kt:17-23` (faux `LinkGate`, horloge).
- Statuts émis : `C/tv/ReceiverServer.kt` (`Response.Status.*`, `status(NNN)`, classes `:1393-1410`), `C/parental/ParentalApi.kt` (`ApiReply(NNN`), `C/xfer/TransferHost.kt:42,50,59` ; liste des routes : `tools/routes/routes.txt`.
- Contrats : `BlockerMap.KNOWN_HTTP`, `BlockerCode.wire` (w13-01) ; `ResumableUpload(onBlocker, ctx)`, `TransferClient(onBlocker, ctx)` (w13-04) ; corps `"code"` (w13-05).

## Fichiers possédés
Nouveaux : `CT/link/BlockerCatalogueTest.kt`, `CT/link/ScriptedTv.kt` (faux serveur NanoHTTPD scripté : liste de réponses `(statut, corps, délai, coupure)` par route, compteur de requêtes), `CT/link/RouteStatusLintTest.kt`, `CT/link/NoSilentFailureSourceTest.kt`. **Hors zone** : tout fichier `main`.

## Étapes
1. `ScriptedTv` : `script[path] = ArrayDeque<Reply>` ; `Reply(status, body, delayMs = 0, dropConnection = false, stall = false)` ; `/api/hello` répond 200 par défaut ; `requests: List<Pair<method, path>>`.
2. `BlockerCatalogueTest` : **table** `Case(code: BlockerCode, script, runner, expectRequestsMax, expectSeverity)` avec les trois `runner` : `classic` (`ResumableUpload`), `fast` (`TransferClient` + `WifiLane` sur `HttpConn.tcp` vers le faux serveur), `download` (`ResumableDownload`) ; ≥ 39 cas distincts (un par situation du § 2 reproductible par HTTP ; les cas Bluetooth passent par `BlockerMap.ofBt` dans une sous-table ; `PHONE_BG_KILLED`, `BATTERY_SAVER`, `WIFI_SLEEPING`, `COLD_START_FALSE_STATE`, `VPN_OR_CAPTIVE`, `OTHER_WIFI`, `CLIENT_ISOLATION` passent par `Preflight` avec un `PreflightEnv` fictif) ; assertions : le résultat porte un `Blocker` non nul au code attendu ; un `ACTION`/`FATAL` fait ≤ `expectRequestsMax` requêtes (PIN : ≤ 1 envoi avec le PIN refusé) ; un `WAIT` continue (`Waiting` + `blocker`) ; **aucun** `Failed(reason)` sans `blocker` ; une TV **ancienne** (corps sans `"code"`) donne le même `Blocker` (table rejouée deux fois : avec et sans champ `code`).
3. `RouteStatusLintTest` : lit les sources listées ci-dessus (`File("src/main/kotlin/...")`, chemin relatif au module `core`), extrait les statuts par regex, exige `⊆ BlockerMap.KNOWN_HTTP` ; lit `tools/routes/routes.txt` (chemin `../../tools/routes/routes.txt`), exige que chaque route de transfert (`/upload/`, `/api/transfer/*`, `/api/part`, `/api/storage/check`, `/api/info`, `/api/hello`, `/api/link/blockers`) apparaisse dans au moins un `Case` ; échoue avec la liste des statuts/routes manquants.
4. `NoSilentFailureSourceTest` : pour `C/tv/TvClient.kt`, `C/xfer/TransferClient.kt`, `C/xfer/Lanes.kt`, `C/trust/ResilientCall.kt` : chaque `catch (e: TvClient.HttpError)`, `catch (e: IOException)`, `catch (e: TransferApi.Refused)` est suivi de `BlockerMap.` ou `blocker` dans les 12 lignes suivantes ; aucune chaîne française (`Regex("[àéèêçù]")`) dans ces fichiers hors commentaires (les textes viennent de `BlockerTexts`/`LinkText`).

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c 'Case(' android/core/src/test/kotlin/castbridge/core/link/BlockerCatalogueTest.kt` ≥ 39 ; durée de la suite ≤ 60 s (`sleep` injectés, délais scriptés ≤ 200 ms) ; un `Case` retiré de la table fait échouer `RouteStatusLintTest` (couverture des routes).

## Cas limites
Ports occupés (port 0) ; `HttpConn` et son chien de garde 20 s (ne pas scripter de `stall` > 1 s : injecter `stallMs` court dans `WifiLane`/`HttpConn` si possible, sinon marquer le cas `@Ignore` avec raison) ; locale (`Locale.ROOT` pour les nombres).

## À ne pas faire
Aucun fichier `main` ; pas de réseau réel ; pas de `Thread.sleep` > 200 ms.

## Rapport
`STATUT`, nombre de cas par runner, statuts trouvés par le lint, cas ignorés et pourquoi.
