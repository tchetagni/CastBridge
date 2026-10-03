# w20-02 — Cœur : protocole `play-v1` (messages, codec, `seq`, capacités) et machine à états pure de la salle Internet (`ServerRoom`, `RoomCode`, `Tables`)
<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (secret des questions dans les messages) · statut : PRÊT (après w20-01)
> **Groupe : W20-S0** (cœur pur) · prérequis : w20-01 fusionné · porte : `tools/core-harness/run.sh :core:test --tests 'castbridge.core.quiz.online.*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 2,5 j) · audit Opus : échantillon

**Vague 20 · Effort L · Modèle : sonnet · Statut PRÊT (après w20-01).** Conception : `DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` § 1.4-1.6, § 2.4, § 2.6-2.7, § 5.3. Branche `claude/sonnet-w20-02`. Rapport : `docs/agent-reports/sonnet-w20-02.md`. Règle W15 R5 : aucun `S/`, `R/`, `backend/`, aucun Spring : **tout est pur** et tourne dans `:core:test` sans socket.

## Objectif
(1) Le **protocole** `play-v1` : types de messages client→serveur (`hello{proto, caps, deviceHash, ticket?}`, `create`, `join{code, name, token?}`, `resume{roomId, token, lastSeq}`, `act{questionId, action, choice, arg, seq}`, `relayAct{…, localElapsedMono}` (TV), `scope{open|close}`, `kick`, `mute`, `report`, `pong{id}`) et serveur→client (`welcome{roomId, code, token, seq}`, `state{seq, view}` (vue différentielle ou complète), `question`, `reveal`, `safety{SafetyView}`, `ping{id}`, `error{Reason}`, `roomGone{reason}`), codec JSON avec le `Json` du cœur, **limites** (≤ 2 Ko, champs bornés), numéro de protocole et `caps` additifs (règle W19). (2) `ServerRoom` : salle Internet = `roomId`, `RoomCode` (8 car. Crockford, 40 bits, TTL, rotation après 50 essais faux), hôte, réglages, 1..8 `Table` (chacune un `QuizRoom` avec `clock` et `random` injectés), spectateurs, états `OPEN → PLAYING → FINISHED → GONE`, pause/abandon de la table de l'hôte (§ 1.5), auto-hôte (§ 2.7), `RttBook` (EWMA par connexion, compensation § 2.6 **appliquée au temps avant** `QuizRoom.act`), `seq` et ring de 50 évènements pour `resume`. (3) `ServerAuthority(transport: PlayTransport)` : l'adaptateur **client** de `GameAuthority` (w20-01) sur un transport abstrait (interface `PlayTransport { send(msg); onMessage; state }`), sans socket.

## Pourquoi (preuves)
- `C/quiz/QuizRoom.kt:18-48` : `clock`, `random`, `autoTick`, `wallet`, `histories` sont injectables : le serveur peut instancier **le même** `QuizRoom` ; `:82` `version` et `:473` `awaitChange` fournissent déjà la base du `seq`.
- `C/quiz/QuizDuel.kt:58` `answer(id, questionId, choice, now)` : le temps est un paramètre ⇒ la compensation RTT se fait **avant** l'appel, sans toucher à `QuizDuel`.
- `C/quiz/QuizGame.kt:278` `toMap(now)` et `QuizRoom.view` : déjà sans `answer` avant `reveal` (`docs/QUIZ.md` § 4 « testé ») ; le codec ne doit **rien** ajouter.
- `C/quiz/QuizHttp.kt:195-199` : limites existantes (12 flux, 30 rafale, 10/s, 10 codes faux / 5 min) : reprises comme constantes du protocole.
- `docs/QUIZ.md` § 2 Duel : « rapidité mesurée avec l'horloge de la TV » ⇒ en ligne : horloge **serveur** (§ 2.6).

## Fichiers possédés
- Nouveaux : `C/quiz/online/PlayProtocol.kt` (messages, `PROTO = 1`, `CAPS`), `C/quiz/online/PlayCodec.kt`, `C/quiz/online/RoomCode.kt`, `C/quiz/online/RttBook.kt`, `C/quiz/online/ServerRoom.kt` (+ `Table`, `Seat`, `Spectator`), `C/quiz/online/ServerAuthority.kt`, `C/quiz/online/PlayTransport.kt`, `C/quiz/online/EventRing.kt`, tests `CT/quiz/online/{PlayCodecTest, RoomCodeTest, RttBookTest, ServerRoomTest, ServerRoomResumeTest, ServerAuthorityTest, NoAnswerLeakTest}.kt`, `docs/PLAY-PROTOCOL.md` (le protocole, tableau par message, exemples JSON).
- Interdit : `QuizRoom.kt`, `QuizDuel.kt`, `QuizGame.kt` (si une extension est indispensable : `QUESTION:` dans le rapport, pas de modification).

## Étapes
1. **Rouge** : `ServerRoomTest` (créer, rejoindre par code, 8e joueur ⇒ `PLAY_ROOM_FULL`, 9e ⇒ spectateur si autorisé, Duel 10 questions à graine, classement), `RoomCodeTest` (alphabet, 40 bits, 50 essais ⇒ rotation + évènement, TTL), `RttBookTest` (EWMA, plafond 400 ms, grâce `min(rtt, 1 s)`), `NoAnswerLeakTest` (1 000 parties à graine : aucun message avant `reveal` ne contient `answer`/`explanation` ni la bonne réponse sous une autre clé), `ServerRoomResumeTest` (perte d'un joueur 30 s ⇒ rattrapage par `lastSeq` ; hôte perdu 60 s ⇒ `ABANDONED(HOST_LOST)` ; `resume` avec un `lastSeq` trop vieux ⇒ `state` complet).
2. Protocole + codec : chaque message a `t` (type), `seq` ; décodage **strict** (type inconnu ⇒ `error UNSUPPORTED`, champ hors borne ⇒ `BAD_REQUEST`, taille > 2 048 o ⇒ rejet) ; `caps` : `["play1","sse","longpoll","relay","spectate"]`.
3. `RoomCode.generate(random)` (Crockford 32, 8 car., affiché `XXXX-XXXX`), `RoomCode.normalize` (casse, tirets, I/L→1, O→0), `BadCodeCounter(perIp, perRoom)`.
4. `ServerRoom` : table = `QuizRoom(bank, clock, random, autoTick = false, histories = serverHistories)` ; `tick(now)` appelé par l'hôte du service ; `RttBook` ⇒ `effectiveNow(seat)` ; relais TV : `max(localElapsedMono, serverElapsed − rttTv)` ; `EventRing(50)` ; `scopeClose()` ⇒ spectateurs 30 s puis `roomGone(HOST_CLOSED_INTERNET)`.
5. `ServerAuthority` : `view()` = dernière `state` reçue + `safety` reçue ; `act()` ⇒ `send(act)` avec `seq` ; file locale bornée ; `awaitChange` sur `seq`.
6. `docs/PLAY-PROTOCOL.md` + **Vert**.

## Critères d'acceptation
- `NoAnswerLeakTest` vert sur 1 000 parties (Millionnaire et Duel, jokers inclus : le 50:50 ne révèle que des **mauvaises** réponses).
- Un `act` rejoué ×3 ⇒ un seul effet (`SAME`) ; un `act` sur une question fermée ⇒ `CLOSED` ; un `act` pour une question **future** ⇒ `UNKNOWN_QUESTION` (jamais acceptée).
- Compensation : RTT 600 ms, réponse arrivée à 10 000 ms ⇒ `elapsed = 9 700 ms` ; RTT 2 000 (plafonné) ⇒ 9 600 ms ; TV relais : `localElapsedMono = 9 900` avec RTT TV 300 ⇒ `max(9 900, 9 700) = 9 900`.
- Même scénario à graine joué par `LocalAuthority` (w20-01) et par `ServerAuthority` sur un `PlayTransport` **en mémoire** branché sur `ServerRoom` ⇒ **mêmes** `stage/phase/scores` à chaque étape (`AuthorityContractTest` version mémoire ; w20-03 la rejouera sur socket).

## Cas limites
- Deux `join` simultanés pour le 8e siège : un seul gagne (verrou de salle). Code expiré (TTL 2 h) ⇒ `PLAY_ROOM_GONE`. `resume` avec un jeton d'une autre salle ⇒ `PLAY_BAD_CODE` sans révéler l'existence de la salle. Hôte qui envoie `scope{close}` pendant une question ⇒ refusé (`FORBIDDEN`, « seulement en salle d'attente »).

## À ne pas faire
- Aucune socket, aucun thread (le service fournit l'horloge et le tick). Aucun champ client « temps de réponse » utilisé pour **réduire** un temps. Aucun message qui liste plus d'une question. Ne pas modifier le format de `QuizRoom.view` (clés additives seulement).

## Rapport
Format RAPPORT + `SYMBIOSE: cap=play1 · proto=play-v1 · reason=PLAY_* · deux écrans=AuthorityContractTest(mémoire)` + tableau des messages avec taille maximale mesurée.
