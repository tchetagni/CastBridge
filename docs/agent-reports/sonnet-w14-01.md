STATUT: TERMINÉ (voir PORTE et SUITE pour l'état des builds)
CAHIER: sonnet-w14-01 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w14-01 · COMMIT: voir `git log -n 1`
JETONS: inconnu
PORTE: `gradle --offline :core:test --tests 'castbridge.core.journey.HarnessSmokeTest'` → VERT, 8 tests (0,6 s)
SUITE COMPLÈTE: `gradle --offline :core:test` → VERT (2307 tests) ; écarts : aucun
FICHIERS: android/core/src/test/kotlin/castbridge/core/journey/{JourneyKit,TvSim,PhoneSim,Scenario,ActivationApiSim,HarnessSmokeTest}.kt ; docs/agent-reports/sonnet-w14-01.md. Aucun fichier de production touché.

SIGNATURES LIVRÉES vs CONTRAT (écarts motivés)
- `PhoneSim.phone: Phone` ABSENT : `LinkFixtures.Phone` construit son `FakeEnv` en interne (pas d'injection, `FakeEnv.probe` non ouvert, fichier hors zone). `PhoneSim` refait le même câblage avec `JourneyEnv` (délègue tout à `FakeEnv`, sauf `probe`/`check` en HTTP réel). `PhoneSim.env` (le `FakeEnv`) garde les interrupteurs `bt`, `bonds`, `network`, `fg`, `candidates` ; `saved`, `store`, `link`, `driver`, `lastStep`, `shownHistory` sont exposés.
- `TvSim.transfers: TransferHost` : getter par RÉFLEXION sur le champ privé de `ReceiverServer` (aucune horloge injectable dans `TransferHost` ici). `transferState(id)` s'en sert (sans authentification, donc sans toucher au compteur PIN).
- `TvSim.pinFailures()` : réflexion sur `PinGuard.entries[..].failures` (aucun accesseur de production). Échoue bruyamment si le champ change.
- `TvSim.dir` est un `var` à setter privé (change à `reinstall()`). `FakeTv.persistence` est typé `MemoryTrustPersistence` et ne peut pas recevoir un fichier : `TvSim` remplace `bt.reg` et `bt.pairing` par un `TrustRegistry(FileTrustPersistence)` à l'horloge du parcours (équivalent de `restartApp()` sur un vrai fichier) et reconstruit le `HelloHandler` avec le PORT RÉEL (FakeTv code 8765 en dur).
- `routeGuard` : `TrialPolicy::guard` n'existe pas ; même forme que `TvService.kt:258` (`TrialPolicy.routeBlocked` + `MESSAGE`), lue en direct de l'état d'essai.
- `ActivationApiSim` : n'accepte que `TEST_PAYLOAD` (aucune vérification de signature ; les vecteurs `test-vectors.json` ne sont pas rejoués). `setTrial` y est `switchTrial` (clash JVM avec le setter).
- `rotatePin()` relance la TV (le PIN est immuable dans `ReceiverServer`) : port neuf, même registre. `slow()` n'est qu'ANNONCÉ (`StorageVolume.writeBps`), aucun octet ralenti. `busy` : le premier HELLO simple passe, les suivants reçoivent ERR_BUSY pendant 60 s simulées.
- `check()` suit `S/LinkAndroid.kt:85-93` (401 « bad token » = refusé, toute autre réponse = bon, IOException = injoignable), PAS la version du cahier (200 = bon). `probe()` suit `LinkAndroid.kt:70`.
- `Notice` : la notification FINALE (« Terminé » / « Envoi interrompu : … ») est une ajoute du harnais ; `UploadService` ne publie pas de notification finale (à vérifier par w14-02 pour R-04). Textes en cours = `S/UploadService.kt:272`, marqués « À BRANCHER w14-05 ». Pas de limitation à 1 s des notifications (l'horloge simulée n'avance pas pendant un envoi).
- Extras hors contrat : `Journey`/`withJourney` (DSL `given`/`whenever`/`then` avec journal inclus dans le message d'échec), `UploadRun.moved`, `PhoneSim.lastPair`, `pinTvName`, `credentialNow()`.

CHOIX
- `sleep` des envois = `clock.advance(ms)` + 5 ms réelles (pas de `Thread.sleep`) : un envoi bloqué fait avancer le temps simulé (jusqu'à ~17 min simulées par seconde réelle) : un test qui laisse un envoi bloqué doit l'`await` court puis `cancel`. `PhoneSim.close()` annule les envois.
- La puce garde le vert 40 s (`lostGraceMs`), c'est le comportement du `LinkMachine` : avancer `clock` de plus de 40 s avant d'attendre le rouge.
- `transferState()` ne passe pas par HTTP car un PIN juste remettrait à zéro le compteur de refus de 127.0.0.1 (toutes les connexions viennent de la boucle locale).

CE QUE TvSim/PhoneSim N'ONT PAS PU SIMULER (pour w14-05, signatures manquantes dans C/**)
`ReceiverServer` : accès aux `transfers` et au `volumes` sans réflexion ; horloge injectable. `PinGuard` : accesseur du compteur de refus. `TransferHost` : horloge injectable (`now`) exposée par `ReceiverServer`. `FakeTv` : persistance et port du HELLO injectables. `LinkFixtures.Phone` : `LinkEnv` injectable. `UploadService` : notification finale et textes (`XferTexts`). Vitesse d'écriture réelle du disque (`slow`), saturation HTTP 429 (`busy`), reprise `.cbx` après `restart()` (non rejouée).

À FAIRE VÉRIFIER PAR L'AUDITEUR OPUS
1. Fidélité de `JourneyEnv.check()`/`probe()` vs `S/LinkAndroid.kt:70-93`.
2. Les trois réflexions de `TvSim` (transfers, volumes, PinGuard) : bien en lecture seule.
3. Aucun faux vert : contre-épreuve `theChipIsNeverGreenByDefaultNorWhileTheTvIsClosed`, `pinFailures` testé, octets comparés.
4. `Thread.sleep` et `import android` absents de `journey/` (grep = 0).
5. Courses : `JourneyClock` est synchronisée mais `FakeTv.connect` avance `fake` sans verrou (appelé depuis le fil du pilote seulement).

QUESTION: aucune
AUTOCONTRÔLE: [x] zone [x] porte [x] suite [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit

RÉSULTATS
Porte : `HarnessSmokeTest` 8 tests verts, 0,6 s au total (le plus long : 0,4 s, envoi de 5 Mio).
Suite : `:core:test` complet VERT (2307 tests, 1 ignoré préexistant, 0 échec, 4 min 47 s) ; aucun des flakes connus n'est apparu. `Thread.sleep` : 0 occurrence dans `journey/` ; `import android` : 0.
Note : un autre agent a tenu le verrou Gradle plus d'une heure (son `:core:test` bloqué sur `ByteRelayTest.noServerMeansRefused`, hors de ma zone) ; mes exécutions ont attendu leur tour.
