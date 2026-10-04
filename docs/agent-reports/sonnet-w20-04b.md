STATUT: TERMINÉ (audit Opus obligatoire : chemin d'entrée du service, droits, révocations)
CAHIER: w20-04b · MODÈLE: sonnet · BRANCHE: claude/w20-04b-poc-join-tv (depuis integration/agents, HEAD 5696385e) · COMMIT: celui qui contient ce rapport (voir `git log -1 claude/w20-04b-poc-join-tv`) · NON POUSSÉ
JETONS: inconnu
PORTE: `:server-play:test --tests 'castbridge.play.poc.*'` + `:core:test --tests 'castbridge.core.quiz.online.*'` → VERT
SUITE COMPLÈTE: `:core:test :server-play:test :sender:compileDebugKotlin :receiver:compileDebugKotlin` → VERT (une seule passe, par le verrou, code de sortie 0, aucun échec ni test flaky relancé).
SYMBIOSE: cap=play1 · proto=play-v1 additif (join.activation) · reason=PLAY_* inchangés · deux écrans=MultiTvRelayTest (deux TV sur le vrai service, chacune relaie ses deux joueurs locaux jusqu'au classement commun ; aucun écran touché)

## Rouge d'abord (règle R1)
`TvOnlyJoinTest.joinWithoutTicketIsRefusedBeforeLookup` contre `integration/agents` (aucun code modifié), avant tout code : un `join` sans ticket avec le bon code reçoit `welcome`.
```
> Task :server-play:test FAILED
TvOnlyJoinTest > joinWithoutTicketIsRefusedBeforeLookup FAILED
    java.lang.AssertionError at TvOnlyJoinTest.kt:23
1 test completed, 1 failed
```
(ligne 23 : `assertFalse(caller.welcomed(), "un join sans ticket ne doit jamais entrer")`.) Vert après implémentation.

## Ce qui est nouveau
Cœur (`C/quiz/online/`) : `ClientMsg.Join.activation` (additif, absent du fil quand il est nul ; un `join` qui en porte un peut atteindre `MAX_CREATE_BYTES`) ; `ServerRoom` : `Seat.relayer`, `Seat.relayedBy`, `grantRelay(conn)` (appelée par le service seul), `relayJoin` accepté de l'hôte ou d'un siège relais et plafonné à `Settings.maxRelayedPerTv` (8) par TV, `relay` ⇒ `FORBIDDEN` si `relayedBy` n'est pas l'émetteur, présence/pause/signe/fermeture d'Internet/expulsion par `relayedBy`, `safetyFacts()` (interne, pour le test).
Service (`server-play/`) : `PlayConfig` (`webPlay`, `maxRelayedPerTv`, `revocationsMode`, trois noms dans `ENV_NAMES`, `DIRECT=1` refusé avec `TRUSTED_PROXIES`, `WEB=1` refusé sans `DIRECT=1`, `off` refusé si `MAX_ROOMS` > 20) ; `PlayServer` (`revocationsReady = mode OFF || feed.usable()`, 403 `{"error":"origine refusée"}` avant tout pour un `Origin` non vide sur `/play/ws|act|events|state`, santé `disabled`, avertissement au démarrage) ; `PlayPageController(webPlay)` + `info.html` ; `PlayHub.join` (chemin `tvJoin`) ; coalescence dans `FbConn.offer` et `WsConn.offer`.
Docs : `docs/PLAY-PROTOCOL.md` (`join.activation`, « Siège relais », « Entrée des TV », « Coalescence »), `docs/PLAY-OPS.md` § 4.2 (paragraphe POC), `backend/.env.play.example`, `server-play/README.md` (3 lignes de variables : fichier hors liste du cahier, ajouté parce que `ProductionDocsTest` en fait la référence des variables).

## Ordre du `join` d'une connexion non assise (`webPlay=false`), fermé à chaque pas
ticket `cbp1` ⇒ maintenance ⇒ `revocationsReady()` ⇒ `jti` pas encore servi (table `UsedTickets` COMMUNE à `create`) ⇒ `evaluator.evaluate(deviceCode, [activation])` et édition ≠ `NONE` (sinon `used.use`, ticket brûlé, `PLAY_SCOPE_FORBIDDEN` + note de `HostRights`) ⇒ **recherche du code** ⇒ code faux : compteurs d'adresse existants + `badCodesIdentity` (30 / 5 min) ⇒ `PlayRules.canCreate` (essai : `trialDays` commun aux créations et entrées ; refus ⇒ ticket brûlé comme à `create`) ⇒ sous `roomLock` : salle toujours là, connexions vivantes de l'identité (salles créées + connexions entrées vivantes) < `maxRoomsPerSubject` (essai : 1 ; rejoindre sa propre salle ne compte pas deux fois), recontrôle du compteur d'essai, `used.use`, `trialDays.record`, note de l'identité dans `RoomEntry.joined` ⇒ `attachAndForward(grantRelay = true)` : `grantRelay` est appelée sous le verrou de la salle avant toute livraison. Refus du `join` par la salle (pleine, bannie…) après consommation : le ticket est brûlé (comme à `create`).

## Mutations (appliquées puis retirées ; chaque fichier restauré identique à l'original : `cmp`)
| # | Mutation | Test(s) en échec |
|---|---|---|
| 1 | rechercher la salle avant le ticket | `TvOnlyJoinTest.joinWithoutTicketIsRefusedBeforeLookup`, `aWrongCodeByAnAuthenticatedTvIsCountedPerIdentity` |
| 2 | ignorer l'activation (contrôle `NONE` ET verdict `PlayRules` retirés) | `aValidTicketWithoutActivationIsScopeForbidden`, `anActivationOfAnotherTvIsRefused`, `aTrialTvCountsCreationsAndEntriesInTheSameDailyCounter`. Avec le seul contrôle `NONE` retiré, `anActivationOfAnotherTvIsRefused` échoue seul (le verdict `PlayRules` double ce contrôle) |
| 3 | deux tables de `jti` (create / join) | `TvOnlyJoinTest.aTicketUsedForCreateCannotJoinAndTheReverse` |
| 4 | compteur d'essai séparé pour les entrées | `TvOnlyJoinTest.aTrialTvCountsCreationsAndEntriesInTheSameDailyCounter` |
| 5 | pas de compteur de codes faux par identité | `TvOnlyJoinTest.aWrongCodeByAnAuthenticatedTvIsCountedPerIdentity` |
| 6 | retirer le contrôle `relayedBy` de `relay` | `ServerRoomRelayerTest.aTvRelaysOnlyItsOwnPlayers`, `MultiTvRelayTest.twoTvsPlayOneDuel…` |
| 7 | plafond de 8 sièges relayés par TV ignoré | `ServerRoomRelayerTest.aTvRelaysAtMostMaxRelayedSeats…`, `MultiTvRelayTest.aTvCannotRelayMoreThanEightSeats…` |
| 8 | `hostWatch` sur tout `viaTv` | `ServerRoomRelayerTest.hostLostWithAnotherTvsPlayersPresent…`, `MultiTvRelayTest.aLostHostWithAnotherTvsPlayersPresentDoesNotPauseTheTable` |
| 9 | `disconnect` : présence retirée à tous les `viaTv` | `ServerRoomRelayerTest.onlyThePlayersOfALostTvAreAbsent…` |
| 10 | `kick` sans retrait des sièges relayés | `ServerRoomRelayerTest.kickingARelayTvRemovesItsRelayedSeatsToo` |
| 11 | `setScope(false)` ne traite pas les sièges d'une autre TV comme distants | `ServerRoomRelayerTest.closingInternetTurnsAnotherTvsPlayersIntoSpectators` |
| 12 | `safety()` : local = tout `viaTv` | `ServerRoomRelayerTest.safetyCountsAnotherTvsPlayersAsRemote` |
| 13 | `OriginCheck` seul (branche « navigateur ⇒ 403 » retirée) | `WebClosedTest.aBrowserNeverReachesTheGame` |
| 14 | `revocationsReady = url == null || usable` (ancien comportement) | `RevocationsModeTest.withRevocationsOnAndNoAcceptedListCreateAndJoinAreRefusedRetryably` |
| 15 | `DIRECT=1` accepté avec `TRUSTED_PROXIES` | `RevocationsModeTest.directTogetherWithTrustedProxiesIsRefusedAtStartup` (+ `AuditW2004Test`) |
| 16 | coalescence retirée (`FbConn` et `WsConn`) | `StateCoalescingTest` : `aSlowFallbackConnection…` (taille/compte), `aWebSocketQueueKeepsOneStateToo`, `aSlowTvInARealRoom…` |
| 17 | coalescer aussi `reveal` | `StateCoalescingTest` : les quatre tests (perte de la `reveal`) |

## Tests existants modifiés (et pourquoi)
- Fixtures : `webPlay = true` explicite (comme demandé) dans tous les `PlayConfig(...)` des tests (`HubFixture` : paramètre `webPlay` pour les nouveaux tests) ; `NoSecretsTest` : l'environnement du service de test ajoute `CASTBRIDGE_PLAY_WEB=1` (avec `DIRECT=1`).
- **Hors consigne, nécessaire** : ces mêmes configurations des tests qui ouvrent un `PlayServer` reçoivent aussi `revocationsMode = RevocationsMode.OFF`, parce que « adresse de révocations absente ⇒ prêt » est supprimé (cahier, étape 3) : sans liste, un `PlayServer` de test n'ouvrait plus de salle. Aucune assertion modifiée. Exception : `FinalFixesTest` (santé `"revocations":"none"`) garde le mode `on`.
- `AuditW2004Test.startupWithoutARevocationsUrlIsRefusedUnlessDirect` : sa dernière ligne attendait que `DIRECT=1` + `TRUSTED_PROXIES` démarre ; c'est exactement le trou que le cahier ferme, l'assertion est inversée (refus) et `DIRECT=1` seul reste permis.
- `LimitFilterTest.codeRotationIsReachableOnlyFromTheJoinPath` (analyse du source) : inchangé ; `tvJoin` est placé entre `join` et `resume` pour que l'appel de `nearMiss` reste dans la zone « join » du test.

## Points à signaler (QUESTION / dette)
- **QUESTION** : `guard/MessageSchema.decode` refuse tout message de plus de 2 048 caractères avant le codec (`PlayProtocol.MAX_MESSAGE_BYTES`), tous types confondus : le chemin `MAX_CREATE_BYTES` (16 Ko) de `create` ET de `join.activation` n'est donc pas atteignable par le hub ; un `cbx1` + ticket de taille réelle doit rester sous 2 048 caractères (non mesuré sur une vraie activation). Fichier hors liste, non modifié ; à trancher par l'audit (relever la borne pour `create` et `join` portant `activation`).
- `BotScore` : le cas « 4 joueurs relayés par la même TV, réponses identiques » passe avec des `deviceHash` distincts (le test le prouve) ; si une TV relayait tous ses téléphones sous le même `deviceHash`, la règle des jumeaux (`BOT_TWINS`) les sortirait du classement : à régler côté TV (`deviceHash` par téléphone), pas dans `BotScore`.
- La TV invitée entre ici en **spectatrice** dans les tests (`spectate=true`) : elle ne prend pas de siège de table ; en joueuse (`spectate=false`) elle en prend un (cas du `kick`). Le choix est celui de la TV (w20-05a).
- Un `join` refusé par la salle après la consommation (salle pleine, appareil banni) brûle le ticket et compte une partie d'essai (comme `create` après consommation) : la TV demande un nouveau ticket.

## Points d'audit Opus
1. **Ordre du `join`** : aucun accès à `rooms`, aux compteurs de codes ni à `nearMiss` avant l'évaluation de l'activation ; réponse identique code vivant / faux sans ticket (`joinWithoutTicketIsRefusedBeforeLookup` : 120 essais, adresse commune, code proche : aucune rotation).
2. **`jti` commun** : `used` unique ; recontrôle `used.use` sous `roomLock` (REPLAY ⇒ refus) ; ticket brûlé par une preuve fausse.
3. **`relayedBy`** : `grantRelay` n'est atteignable que par `attachAndForward(grantRelay = true)` ; reprise d'un siège relayé réservée à sa TV (`join` à jeton d'un client non assis ignore les sièges relayés) ; `relayAct` d'une TV pour un joueur d'une autre ⇒ `FORBIDDEN`.
4. **Révocations** : `off` jamais implicite (ni `DIRECT`, ni URL absente) ; borné à 20 salles ; visible (santé, journal) ; `DIRECT` + `TRUSTED_PROXIES` refusé.
5. **Coalescence** : seul le préfixe `{"t":"state"` est remplaçable ; ordre relatif des autres types prouvé ; compte d'octets corrigé (`FbConn.bytes`, `WsConn.queued`) ; long-poll `since` : aucun message non-`state` perdu ; fenêtre de course WsConn (l'écrivain prend l'état au moment du remplacement) : `queue.remove` rend faux, l'écrivain décompte lui-même.
6. Hors périmètre (non fait, par le cahier) : TV côté client (w20-05a), délégation (retirée), pages web de jeu (`play.html/js/css` gardés, non servis).
