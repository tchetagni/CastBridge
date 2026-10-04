STATUT: TERMINÉ (POC) — règles pures testées (RED/GREEN, 14 mutations) ; câblage Android vérifié par COMPILATION SEULEMENT ; audit Opus obligatoire (TV exposée à Internet : ticket, TLS, relais)
CAHIER: w20-05 (amendé « POC », DESIGN-W20-AMENDEMENT § 2.6) · MODÈLE: sonnet · BRANCHE: worktree-agent-a27ff0918c45e043d (depuis integration/agents 1f8937e6) · COMMIT: voir `git log -1` · NON POUSSÉ · version.properties NON modifié
DÉCISION DU PROPRIÉTAIRE REÇUE EN COURS DE ROUTE (2026-10-04, « lève tous les verrous, staging pour les innovants idem production ») : le réglage `quiz.online` est ALLUMÉ PAR DÉFAUT dans tous les builds (`QuizOnlineFlag.COMPILED_DEFAULT = true`, testé), l'interrupteur de l'utilisateur reste (menu de la TV « Quiz en ligne : activé / désactivé ») ; aucun raccourci de test, aucun mode « off » côté client.
PORTE: `:core:test --tests 'castbridge.core.quiz.online.*' :receiver:compileDebugKotlin` → VERT (241 tests du paquet, 0 sauté, 0 échec) ; suite complète `:core:test :receiver:compileDebugKotlin` → VERT (voir « Suite complète »).
SYMBIOSE: cap=play1 (TV) · proto=play-v1 inchangé · reason=PLAY_* (+ motifs inconnus tolérés) · deux écrans=H-PLAY-POC (relevé humain)

## Écrans livrés (POC, tous derrière `quiz.online`)
Carte « Partie Internet » dans l'accueil du Quiz (et interrupteur dans le menu de la TV) ; `PlayOnlineActivity` : menu « Créer une partie / Rejoindre avec un code / Retour » ; saisie du code à la grille de 32 symboles (4 × 8, Effacer, Valider, Retour) ; ouverture ; salle (code `XXXX-XXXX` en grand, « Ici : N joueurs · k places libres », « Commencer » pour l'hôte, question / révélation / classement, « Quitter ») ; confirmation de sortie (« Annuler » présélectionné) ; « Partie Internet perdue » ; refus avec le texte du cœur ; carte grisée avec la RAISON (jamais vide). Bandeau 1-2 lignes : forme + mot + texte reçu, abaissé par `LinkCause` (passerelle ⇒ orange « Internet par le téléphone (Bluetooth) · lent »). Textes ≥ 28 sp, marge de sécurité 5 % (`PlayerIcons.safe`), 5 touches, aucun QR ni lien web.
Pendant une partie Internet, `QuizHub.http` sert `/quiz` par `RelayAuthority` (les téléphones de la maison ne parlent qu'à leur TV, 8 au plus) ; `GET /api/quiz` et `POST /api/quiz/open` du téléphone répondent « partie Internet en cours » (code Internet, nombre de téléphones) au lieu d'ouvrir une salle locale par-dessus.

## ROUGE (étape 1, collé) — par assertion, jamais par erreur de compilation
Ébauche à comportement neutre (signatures seules) + `PlayTvScreensTest` (32 tests) : `:core:test --tests '*.PlayTvScreensTest'` → `32 tests completed, 29 failed`, par exemple :
```
flagOffMeansNothingVisibleAndNoNetworkWhateverTheRest : AssertionError: drapeau éteint ⇒ invisible (PROD true true true) expected:<Hidden> but was:<Available>
entryStopsAtEightAndSubmitsOnlyWhenComplete          : AssertionError: Expected value to be true.
leavingTheRoomAsksFirstAndCancelIsTheDefault         : AssertionError: expected:<CONFIRM_LEAVE> but was:<ROOM>
linkScreenAgreesWithTheBannerLevelForEveryCombination: AssertionError: Online null true true expected:<GREEN> but was:<RED>
unknownOrMissingReasonsNeverCrashAndNeverEchoHostileText : AssertionError: Expected value to be true.
relaySeatsTextCountsAndNeverOverflows                : ComparisonFailure: expected:<[Ici : aucun joueur · 8 places libres]> but was:<[]>
bannerSignDependsOnTheScreen                         : AssertionError: avant toute ouverture, rien ne sort : « TV seule » expected:<TV_ONLY> but was:<…>
```
(les 3 tests verts de l'ébauche étaient vrais par construction : tuile disponible, URL sans « http »). VERT après `PlayTvScreens.kt` : 32/32 ; l'ordre de gravité, la perte à 60 s et le certificat sont aussi couverts par `LinkCauseTest`/`PlayTvSessionTest` (w20-05a).

## Règles pures (cœur, `C/quiz/online/PlayTvScreens.kt`, testées)
`QuizOnlineFlag` (réglage > ordre signé > défaut compilé allumé) · `PlayGate.tile` (drapeau éteint ⇒ `Hidden` ; sinon, dans l'ordre : activation (essai/grâce/production ok), heure, profil enfant, « Connexion Internet requise », « Quiz en ligne : service indisponible » ; `mayOpenNetwork` seulement si disponible, testé exhaustivement) · `RoomCodeEntry` (grille, normalisation I/L→1, O→0, plafond 8, `K7M2-Q___`) · `PlayFlow` (automate : MENU, ENTER_CODE, OPENING, ROOM, CONFIRM_LEAVE, LOST, FAILED, BLOCKED, CLOSED ; évènement hors sujet = sans effet ; `FlagOff` ferme tout) · `PlayLinkScreen` (continuer / orange / quitter ; cohérent avec le niveau du bandeau pour les 36 combinaisons) · `PlayBannerModel` + `PlayBanner.sign` (le bandeau ne montre QUE la vue reçue ; « TV seule » avant toute ouverture ; « Internet » ensuite) · `RelaySeatsText` (plafond 8, places libres) · `PlayErrors` (tous les motifs connus, motif inconnu ou hostile jamais recopié, `Nouvel essai possible dans N s` seulement si le motif se réessaie, refus de ticket 401/403/429/503) · `PlayTicketReply.parse` (tolérante aux champs inconnus, stricte sur le ticket).

## Mutations (14, appliquées puis retirées ; fichier restauré identique, `assert` du script) — toutes détectées
1 ordre signé avant réglage → `flagPriority…` · 2 drapeau éteint ignoré → `flagOffMeansNothingVisible…` · 3 profil enfant ignoré → `reasonsComeInAFixedOrder…`, `eachMissingCondition…` · 4 plafond de 8 retiré → `entryStopsAtEight…` · 5 I/L→1 retiré → `entryNormalises…` · 6 sortie sans confirmation → `leavingTheRoomAsksFirst…` · 7 perte à 60 s n'ôte plus l'écran → `linkScreenAgreesWithTheBannerLevel…` · 8 certificat non valide toléré → `linkScreenAgrees…`, `linkScreenMapsEveryCause…` · 9 bandeau d'avant ouverture montre la session → `bannerSignDependsOnTheScreen` · 10 niveau recalculé en local → `bannerNeverRecomputes…` · 11 motif inconnu recopié → `unknownOrMissingReasons…` · 12 plafond de sièges affiché dépassé → `relaySeatsText…` · 13 espace accepté dans le ticket → `ticketReplyParsing…` · 14 ordre activation/enfant inversé → `reasonsComeInAFixedOrder…`.

## Fichiers
Cœur : `android/core/src/main/kotlin/castbridge/core/quiz/online/PlayTvScreens.kt` (nouveau), test `android/core/src/test/kotlin/castbridge/core/quiz/online/PlayTvScreensTest.kt` (nouveau). Récepteur (compilation seule) : `android/receiver/src/main/kotlin/castbridge/receiver/quiz/{PlayHub,PlayOnlineActivity}.kt` (nouveaux) ; zones additives `QuizHub.kt` (`/quiz` et `/api/quiz` par le relais pendant une partie Internet), `QuizActivity.kt` (carte + sondage du service), `PlayerActivity.kt` (interrupteur au menu), `AndroidManifest.xml` (activité). Doc : `docs/test-plans/H-PLAY-POC.md`, cette note, ligne d'index. Rien dans `server-play/`, `backend/`, `C/tv/ReceiverServer.kt`, `version.properties` ; aucune dépendance ajoutée (le transport est `PlayHttpTransport` du cœur).

## Écarts au cahier (honnêtes)
- Un fichier `PlayHub.kt` + une activité remplacent les six fichiers listés au cahier initial (hors périmètre POC : `PlayScopeDialog`, `PlayFallback`, `PlayLocalRelay`, OkHttp, QR).
- Drapeau : l'ordre signé `flag.set quiz.online` n'est PAS branché : la liste close `PolicyActions.FLAGS` est partagée avec le serveur (`tools/orders/actions.json` + `PolicyCatalog`, `ActionsParityTest`) ; l'ajouter exige de toucher le backend (interdit ici). L'interrupteur utilisateur et le défaut allumé suffisent à la décision du propriétaire. À faire plus tard si on veut couper à distance.
- Le sondage du service est `GET /play/.well-known/caps` (route publique, sans secret), par `Routes` ; résultat gardé 60 s ; non sondé ⇒ on essaie.
- « Commencer » envoie `mode DUEL`, `autohost`, `start` : à confirmer sur le vrai service que l'hôte automatique enchaîne bien les questions (le test de bout en bout de 04b joue avec des ordres explicites).
- Hôte : la TV ne joue pas (`create` sans pseudonyme) ; invitée : spectatrice (`join` avec `spectate=true`) ; ses téléphones jouent par elle.

## Constats pour l'audit Opus et le coordinateur
1. **Taille du `join`/`create` avec activation** : `MessageSchema.decode` du service borne tout message à 2 048 caractères (04b, QUESTION ouverte) : une activation `cbx1` de taille réelle (facteurs d'appareil) doit y tenir ; non mesuré. Premier test réel = premier relevé de cette taille.
2. **Ticket** : demandé par `Routes` (réseau de la TV puis passerelle), jamais journalisé ; un ticket frais par session (création, entrée, reprise) et au renouvellement de 8 min (le service juge chaque POST) ; limite serveur 20 par appareil et par heure (`CASTBRIDGE_PLAY_TICKET_PER_DEVICE_PER_HOUR`) : une longue panne ne la dépasse pas (la partie est dite perdue à 60 s, ≈ 6 tentatives).
3. **TLS** : `HttpURLConnection` seul, aucune confiance ajoutée (testé par `OnlinePurityTest`) ; certificat non valide ⇒ « Internet : impossible · certificat non valide », salle non créée, aucun bouton « continuer quand même » (écran FAILED/LOST avec « Retour » seulement).
4. **Relais** : 8 téléphones au plus par TV (`RelayAuthority.MAX_PHONES`, constante locale : `TrustRegistry.MAX_PHONES` n'existe pas dans l'arbre) ; le jeton de siège ne quitte jamais la TV.
5. **Ack ≤ 1,5 s non tenu à 40 kbps** (mesuré par 05a : médiane ≈ 1,6 s, max ≈ 3 s) : l'écran ne promet aucune latence ; à relever sur le vrai matériel (H-PLAY-BT).
6. `PlayHub.start` demande le premier ticket avant d'ouvrir le transport : un refus (401/403/429/503) est dit tel quel à l'écran.

## Ce que seuls une vraie TV et le service public déployé peuvent confirmer
Rendu et focus D-pad sur GaiaOS 720p ; `HttpURLConnection` d'Android sur SSE long (lecture 75 s, connexion gardée) ; TLS réel ; passerelle Bluetooth réelle ; nginx `/play/` réel ; taille de l'activation (constat 1) ; hôte automatique ; débit EDGE réel ; `Routes` collant 10 min sur la passerelle.

## Pas à pas du propriétaire
Voir `docs/test-plans/H-PLAY-POC.md` (10 minutes, 2 TV, 2 téléphones + téléphone passerelle). Résumé : TV A ▸ Quiz ▸ Partie Internet ▸ Créer (code `XXXX-XXXX`) ; A1 rejoint par l'application (Quiz comme aujourd'hui) ; TV B ▸ Quiz ▸ Partie Internet ▸ Rejoindre ▸ code à la grille ▸ Valider (bandeau orange en passerelle) ; B1 rejoint ; TV A ▸ Commencer ; classement commun ; navigateur sur `https://bridge.sti-cm.com/play` ⇒ page d'information.

## Ce que la production doit avoir (pour préparer le service)
- API `castbridge-api` : route `POST /api/v1/play/ticket` livrée (w20-04) ; clé PRIVÉE de ticket `secrets/play-ticket.key` (Ed25519, chmod 0400, branchée par `CASTBRIDGE_PLAY_TICKET_KEY_FILE`) ; optionnel `CASTBRIDGE_PLAY_TICKET_PER_DEVICE_PER_HOUR` (20 par défaut).
- `castbridge-play` (`.env.play`) : `CASTBRIDGE_PLAY_TICKET_PUBKEY` (clé PUBLIQUE SPKI base64 correspondante) ; `CASTBRIDGE_PLAY_TRUSTED_KEYS` (clés publiques qui signent les activations `cbx1` des TV, sinon aucune TV n'est reconnue) ; `CASTBRIDGE_PLAY_WEB=0` (aucune page web de jeu, `Origin` ⇒ 403, `join` sans ticket refusé) ; `CASTBRIDGE_PLAY_REVOCATIONS=off` pour le POC (refusé si `CASTBRIDGE_PLAY_MAX_ROOMS` > 20 : le poser ≤ 20) sinon `on` avec `CASTBRIDGE_PLAY_REVOCATIONS_URL` ou `_FILE` et le module des licences actif ; `CASTBRIDGE_PLAY_MAX_RELAYED_PER_TV=8` (défaut) ; `CASTBRIDGE_PLAY_TRUSTED_PROXIES` = adresse de nginx en /32 (obligatoire derrière nginx ; `CASTBRIDGE_PLAY_DIRECT` ne doit PAS être posé en production).
- nginx : route publique `/play/` vers `127.0.0.1:7091` avec `proxy_http_version 1.1`, `proxy_buffering off`, `proxy_read_timeout 75s` (flux SSE et connexion gardée), TLS système ; `/play/.well-known/caps` doit répondre en public (sondage de la TV) ; l'API et `/play/` sur le MÊME domaine que `DEFAULT_SERVER` du build (la TV bâtit `…/play/events` et `…/play/act` à partir de l'adresse du serveur de l'API).
- Heure juste sur les TV et le serveur (fenêtres d'activation et de ticket).

## Suite complète
`:core:test` complet + `:receiver:compileDebugKotlin` → VERT (code de sortie 0, une seule passe, aucun échec ni test relancé).
