STATUT: TERMINÉ (cœur pur) — deux écarts à connaître : le critère « ack ≤ 1,5 s » n'est PAS tenu à 40 kbps ; les tests à deux TV attendent w20-04b (2 tests sautés proprement)
CAHIER: w20-05a · MODÈLE: sonnet · BRANCHE: claude/w20-05a-poc-client-core (depuis integration/agents 5696385e) · COMMIT: voir `git log -1`
PORTE / SUITE COMPLÈTE: `cd android && ANDROID_HOME=$HOME/Library/Android/sdk bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline -Pkotlin.daemon.jvmargs=-Xmx3g -Dorg.gradle.jvmargs=-Xmx4g :core:test :server-play:test :sender:compileDebugKotlin :receiver:compileDebugKotlin -q` → VERT (EXIT 0, 2 tests sautés : assumeTrue « w20-04b absent »)
SYMBIOSE: cap=play1 · proto=play-v1 (inchangé) · reason=PLAY_* · deux écrans=TvClientLoopbackTest (TV + 2 téléphones simulés par `/quiz`, vrai service, vraie socket)

## ROUGE (étape 1, collé)
`:core:test --tests 'castbridge.core.quiz.online.RelayAuthorityTest'` avant toute implémentation :
```
e: .../RelayAuthorityTest.kt:11:27 Unresolved reference 'PlayTvSession'.
e: .../RelayAuthorityTest.kt:21:59 Unresolved reference 'PlayTvSession'.
e: .../RelayAuthorityTest.kt:28:21 Unresolved reference 'RelayAuthority'.
> Task :core:compileTestKotlin FAILED
```
VERT ensuite : `phoneAnswerBecomesRelayActWithLocalElapsed` (relayAct avec `localElapsedMono` = 800 ms, jeton de SIÈGE, jamais le jeton local).

## Fichiers
Nouveaux (cœur, paquet `quiz.online`) : `PlayHttpTransport.kt`, `PlayTvSession.kt`, `RelayAuthority.kt`, `LinkCause.kt`. Additifs : `ServerAuthority.kt` (`rebind`, `relayJoin`, `relay` rend la référence, `onRelayAck`, `onServerMessage`, `joinRoom`, `changeCount`/`poke`/`awaitChanges`), `Authority.kt` (méthodes par défaut `knows/touch/leave/closed`, implémentées par `LocalAuthority`), `PlayTransport.kt` (`RttSource`, `TransportHealth`, `TransportFactory`), `QuizHttp.kt` (même routes sur une autorité), `OnlinePurityTest.kt` (exception déclarée : le seul fichier qui touche le réseau est `PlayHttpTransport.kt`, toujours sans horloge réelle ni confiance TLS ajoutée).
Tests cœur : `RelayAuthorityTest`, `LinkCauseTest`, `PlayTvSessionTest`, `PlayTvSecretsSourceTest`, `QuizHttpAuthorityTest`, `ScriptedTransport` (aide). Tests service : `server-play/src/test/kotlin/castbridge/play/poc/client/{TvClientLoopbackTest,EdgeFluidityTest,SlowSocksProxy,TvSim}.kt`. Doc : cet index + ligne de `SONNET-WAVE20-INDEX.md`. Rien dans `server-play/src/main`, `backend/`, `R/`, `S/`.

## Mesures (simulateur : 5 Ko/s par sens, RTT 800 ± 200 ms, coupure de 5 s à la question 5, derrière une façade keep-alive qui joue nginx)
`state` réel à 8 joueurs : **≈ 2 780 octets** (médiane ; min 1 438, max 2 940), non ≈ 5 Ko estimés. Autres messages (réels) : `question` ≈ 310 o, `reveal` ≈ 190 o, `ack` ≈ 70 o, `ping` ≈ 90 o, `welcome` ≈ 270 o ; montant : POST moyen 764 o avec le ticket dans chaque en-tête, 381 o sans (mesuré sur 89 POST de la partie).
Configuration mesurée : TV A hôte derrière le simulateur avec 4 téléphones relayés + 4 joueurs distants sur liaison directe (ce que serait la TV B vue de la TV A ; la vraie configuration 2 × 4 exige w20-04b). Partie de 10 questions, 106 s.

| mesure (hors question de la coupure et suivante) | sans coalescence | avec coalescence | critère |
|---|---|---|---|
| question → TV (envoi serveur → lecture TV) | 0,50-0,62 s | 0,48-0,60 s | ≤ 1,5 s : TENU |
| `reveal` → TV | 2,07-2,38 s | 0,50-1,08 s | ≤ 2 s : échoue sans, TENU avec |
| état de révélation → TV | 2,54-2,96 s | 1,15-1,63 s | ≤ 2 s : échoue sans, TENU avec |
| classement → TV | 0,99-1,14 s | 0,94-1,10 s | ≤ 2 s : TENU |
| `ack` d'un `relayAct` | médiane 2,26 s, max 3,63 s (30/36 au-delà de 1,5 s) | médiane 1,55 s, max 3,16 s (20/36 au-delà de 1,5 s) | ≤ 1,5 s : **NON TENU** |

Débit de la TV : moyen descendant ≈ 2,0 Ko/s avec coalescence (2,65 sans), pointe ≈ 6,6 Ko/s ; montant pointe ≈ 3,1 Ko/s (1,9 Ko/s si le ticket n'est joint qu'une fois). 11 connexions SOCKS pour toute la partie : la connexion HTTP gardée vivante fonctionne (mais seulement derrière un nginx : voir F-2).
Cas le plus chargé (1 TV, 8 téléphones relayés, mesuré une fois, non gardé en test : 4 minutes) : question ≤ 0,64 s, révélation ≤ 0,75 s, état de révélation ≤ 1,36 s, classement ≤ 1,09 s, `ack` médiane 1,8-2,3 s, max 4 s.
Boucle locale (`build/loopback-latencies*.txt`) : sans simulateur RTT estimé 1 ms, ack médiane 3 ms (max 21 ms) ; derrière +300 ms par sens : RTT estimé 609 ms, ack médiane 1 214 ms (max 2 435 ms, coupure de 15 s comprise).
Pourquoi l'ack ne passe pas : plancher physique ≈ 0,9 s (RTT 0,8 + envoi du POST) ; s'y ajoute l'attente derrière le `state` en cours d'envoi (2,8 Ko = 0,56 s à 5 Ko/s, non préemptible) et la rafale de réponses sur 5 Ko/s. Les tests bornent donc l'ack à 4,5 s (hors coupure) et n'affirment pas 1,5 s.
Coalescence : posée AU POINT DE CONGESTION du simulateur (`coalesceStates` : un `state` encore en file est remplacé par le suivant, `question`/`reveal`/`ack` jamais retirés) en attendant celle du service (w20-04b). Le test « sans coalescence » montre l'échec (mutation demandée) : `withoutCoalescenceTheRevealAndRankingCriterionFails`.

## Mutations (appliquées puis retirées)
- `RelayAuthority` : temps compté depuis la réception au lieu de l'ouverture locale → `phoneAnswerBecomesRelayActWithLocalElapsed` et `relayAckIsRecordedAndOutcomeIsComposedAtReveal` ÉCHOUENT (800 attendu, 2 300 obtenu).
- `RelayAuthority` : bonne réponse de la question précédente copiée sur la nouvelle → `noViewContainsTheRightAnswerBeforeReveal` ÉCHOUE.
- `RelayAuthority` : plafond `>` au lieu de `>=` → `ninthPhoneIsRefusedRoomFull` ÉCHOUE.
- `LinkCause` : comparaison de gravité inversée (le local relève ou ignore) → 5 tests de `LinkCauseTest` ÉCHOUENT.
- `PlayTvSession` : perte à 59 s → `lostIsDeclaredAtSixtySecondsExactlyNotBefore` et la courbe ÉCHOUENT.
- Coalescence retirée du simulateur → critère de révélation/classement échoue (tableau ci-dessus).

## Constats pour l'architecte (à traiter avant le POC réel)
F-1 **Ticket sur chaque POST.** `PlayFallbackController.act` appelle `origin.allows(Origin, verifier.verify(X-Play-Ticket))` à CHAQUE POST : un client natif sans `Origin` doit joindre un ticket valide (10 min de vie) à chaque envoi, ≈ 380 o de plus par POST (764 o → 381 o en moyenne) ; w20-04b refuse tout `Origin` quand `webPlay=false`, donc pas d'échappatoire. Le client joint le ticket à chaque POST, le renouvelle à 8 min (fonction injectée) et en redemande un à chaque NOUVELLE session de service, reprise comprise (le brief disait « une fois par création ou entrée »). Correctif de service proposé (04b) : ne juger le ticket qu'à la création de la session (`c == null`) ; gain mesuré : montant ÷ 2.
F-2 `MiniHttp` répond `Connection: close` : la connexion « gardée vivante » du brief n'existe que derrière nginx (`proxy_http_version 1.1`, keep-alive côté client). Les tests la modélisent par `KeepAliveFront`. `http.keepAlive=false` du build de `server-play` : `TvSim` le remet à `true` avant tout `HttpURLConnection` (aucun autre test du module n'en utilise) et les tests vérifient ≤ 40 connexions SOCKS.
F-3 `relayJoin` et `relay` ne sont acceptés que de l'hôte : une TV INVITÉE ne peut pas relayer avant w20-04b ; les deux tests à deux TV (`TvClientLoopbackTest`, `EdgeFluidityTest`) sont écrits et se sautent par `assumeTrue` ; à relancer après la fusion.
F-4 Pistes pour l'ack (hors de ce cahier) : messages `relayActs` groupés (un POST = N réponses, additif) ; `state` plus petit (vues différentielles) ; priorité des messages de commande sur le `state` en file : mesurée (`controlFirst` du simulateur, ticket une fois) : médiane 1,65 s, max 2,9 s, soit un gain faible.
F-5 `ClientMsg.Join.activation` n'existe pas encore : `ServerAuthority.joinRoom(code, name, deviceHash, activation)` ne transmet pas l'activation (TODO w20-04b).

## Écarts au cahier
- `QuizHttp(authority: () -> GameAuthority?)` clashait avec `QuizHttp(room: () -> QuizRoom?)` (même signature JVM) : le constructeur prend `QuizHttp.AuthoritySource { … }` (interface fonctionnelle). Le constructeur existant et son comportement sont inchangés (`QuizHttpTest` existant vert, `QuizHttpAuthorityTest` : mêmes codes et JSON, 21 échanges).
- 8 téléphones : `TrustRegistry.MAX_PHONES` n'existe pas dans l'arbre (la branche `claude/tv-phones-max8` est fusionnée mais ne porte pas cette constante) : constante locale `RelayAuthority.MAX_PHONES = 8` avec TODO.
- Courbe de reprise datée de la tentative (0, 2, 6, 14, 29, 59 s cumulés) ; perte à 60 s exactement ; certificat invalide = fatal (rouge, jamais de contournement).
- Pas de suppression ni de dépendance ajoutée ; aucun appel hors boucle locale ; aucun secret.

## NON FAIT / À VALIDER SUR MATÉRIEL
Vraie passerelle Bluetooth (SOCKS réel du téléphone), TLS réel et certificat invalide (test de bout en bout non faisable en boucle locale ; seul le chemin d'échec est codé et testé par un transport factice), `HttpURLConnection` d'Android (basé sur OkHttp : la réutilisation des connexions et le délai de lecture de 75 s du flux sont à confirmer sur la TV GaiaOS), nginx réel (`proxy_buffering off`, `proxy_read_timeout 75s`), l'ack ≤ 1,5 s (voir F-4), le relais par une TV invitée (04b), l'écran et le câblage Android (w20-05).
