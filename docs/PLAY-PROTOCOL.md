# Protocole `play-v1` — Quiz en ligne

> Créé par w20-01 (section « Timing »), complété par w20-02 (messages, codec, reprise, équité). Conception : `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md`. Code : `android/core/src/main/kotlin/castbridge/core/quiz/online/` (`PlayProtocol`, `PlayCodec`, `ServerRoom`, `RttBook`, `RoomCode`, `EventRing`, `PlayTransport`, `ServerAuthority`). Tout est pur (aucune socket) : le service `castbridge-play` (w20-03) apporte la socket, l'horloge et le tick.

## Versions et capacités

| Élément | Valeur |
|---|---|
| Nom / numéro | `play-v1`, `PlayProtocol.PROTO = 1` |
| Capacités (`caps`, additives) | `play1`, `sse`, `longpoll`, `relay`, `spectate` ; le serveur répond avec l'intersection |
| Règle d'évolution | additive : une clé inconnue est ignorée ; une clé retirée ou un champ obligatoire neuf = nouveau `PROTO` |
| Forme | JSON UTF-8, un objet par message ; `t` = type ; `seq` = compteur du client (client → serveur) ou numéro d'évènement de la salle (serveur → client) |

## Timing

Exigence du propriétaire (2026-10-03) : « entre 2 questions laisse 1 ou 2 s de latence pour pouvoir synchroniser les parties en ligne ».

Code : `PlayTiming` (`android/core/src/main/kotlin/castbridge/core/quiz/online/PlayTiming.kt`), fonctions pures, horloge du **serveur** seulement.

| Élément | Valeur |
|---|---|
| `PlayTiming.INTER_QUESTION_GAP_MS` | 1 500 ms |
| Plage permise | 1 000 à 2 000 ms ; `PlayTiming.gap(demandé)` ramène toute valeur dans la plage |
| `PlayTiming.gapFor(scope)` | `TV_ONLY` : 0 · `LAN` : 0 (comportement actuel inchangé, prouvé par test) · `INTERNET` : délai borné |

Règles :

1. Après la **révélation** d'une question (à `revealAtServerMs`), la question suivante est **annoncée tout de suite**.
2. L'annonce porte l'instant absolu `opensAtServerMs = revealAtServerMs + gap`, sur l'horloge du serveur.
3. Les clients affichent « Question suivante dans 1,5 s » et peuvent pré-afficher la question ; **personne ne peut répondre avant `opensAtServerMs`** (le serveur refuse : `PlayTiming.gate`).
4. Le temps de réponse (donc les points) se compte **depuis `opensAtServerMs`**, jamais depuis l'arrivée d'un message (`PlayTiming.scoringElapsedMs`).
5. Annonce tardive : un client qui reçoit l'annonce après `opensAtServerMs` démarre aussitôt ; la fenêtre n'est raccourcie que du temps mesuré par le **serveur** (l'annonce porte l'heure serveur), jamais par l'horloge du client (`PlayTiming.start`).
6. Les parties `TV_ONLY` et `LAN` n'ajoutent aucun délai.
7. **Où se place le délai** (choix de w20-02) : « tout de suite après la révélation » = au moment où `QuizDuel` ouvre la question suivante (fin du classement) ; la salle annonce alors la question et la **décale** de `PlayTiming.gap`. `QuizDuel` n'est pas modifié : la table lit une horloge `serveur − décalage` qui avance du délai à chaque ouverture, donc la fenêtre de 20 s reste entière.
8. La **première** question n'a pas de délai (la partie vient de commencer) ; la **dernière** n'est suivie d'aucun délai ni d'aucune annonce. Le Millionnaire (rythme donné par le candidat) n'a pas de délai.
9. Une réponse arrivée avant `opensAtServerMs` est refusée `TOO_EARLY` (accusé `ack`), **jamais comptée** ; l'hôte ne peut pas non plus « passer » pendant le délai.
10. Un client qui rejoint ou reprend entre la révélation et l'ouverture reçoit le message `question` avec le **même** `opensAtServerMs` absolu.
11. Le client calcule son attente avec l'heure serveur de l'annonce moins le trajet déjà fait (`opensAt − serverNowMs − rtt/2`) : tous ouvrent ensemble à `opensAtServerMs` sur l'horloge du serveur.

## Messages client → serveur

Décodage **strict** (`PlayCodec.decodeClient`) : type inconnu ⇒ `error UNSUPPORTED` ; champ manquant, mal typé, hors borne, caractère de contrôle ou message de plus de 2 048 octets ⇒ `error BAD_REQUEST`. Taille mesurée : champs au maximum permis (test `PlayCodecTest`).

| `t` | Champs | Bornes | Rôle | Octets max mesurés |
|---|---|---|---|---|
| `hello` | `proto`, `caps`, `deviceHash?`, `ticket?` | proto 1..1000 ; ≤ 12 capacités de ≤ 24 car. ; ticket ≤ 1 200 car. | ouverture, négociation | 1 650 |
| `create` | `name?`, `mode?` | nom ≤ 16 ; `MILLIONAIRE` ou `DUEL` | l'hôte (TV) crée la salle ; sans nom la TV ne joue pas | 61 |
| `join` | `code`, `name?`, `token?`, `deviceHash?`, `spectate?` | code normalisé (casse, tiret, I/L→1, O→0) | entrer ; la TV (hôte) enregistre ainsi un joueur local relayé | 227 |
| `resume` | `roomId`, `token`, `lastSeq` | `lastSeq` 0..2⁵³ | reprise après coupure | 192 |
| `act` | `questionId?`, `action`, `choice?`, `arg?`, `seq` | action dans `answer vote suggest report select cancel confirm next walk fifty audience phone mode start skip lobby end autohost candidate` ; choix -1..3 ; `arg` ≤ 256 | jouer ; actions d'hôte : `mode start skip lobby end autohost candidate` | 410 |
| `relayAct` | `token`, `questionId`, `choice`, `localElapsedMono`, `seq` | temps 0..60 000 ms | la TV relaie un joueur local avec son temps sur SON horloge monotone | 230 |
| `scope` | `open` | hôte seulement ; fermer : seulement hors partie | « Ouvrir / Fermer Internet » | 26 |
| `kick` | `playerId` | hôte seulement | retirer un joueur (appareil banni de la salle) | 90 |
| `mute` | `playerId`, `muted` | hôte seulement | couper un joueur | 103 |
| `report` | `questionId?`, `reason` | ≤ 64 | signalement (≤ 100 par salle, w20-10 le traite) | 170 |
| `pong` | `id` | ≤ 64 | réponse au `ping` (mesure du RTT par le serveur) | 84 |

## Messages serveur → client

| `t` | Champs | Rôle | Octets max mesurés (tests, 2 joueurs) |
|---|---|---|---|
| `welcome` | `roomId`, `code`, `token`, `role` (`HOST`, `PLAYER`, `SPECTATOR`), `playerId?`, `proto`, `caps` | entrée acceptée ; le jeton (128 bits) n'est jamais dans une URL | non mesuré |
| `state` | `view`, `full` | vue du siège = `QuizRoom.view` (déjà sans la bonne réponse avant clôture) + clés additives `room` et `timing` ; `full` = resynchronisation complète | 1 964 |
| `question` | `questionId`, `index`, `count`, `text`, `choices`, `opensAtServerMs`, `serverNowMs`, `windowMs` | annonce d'**une** question, sans réponse | 373 |
| `reveal` | `questionId`, `index`, `answer`, `explanation?` | envoyé seulement **après** la clôture | non mesuré |
| `safety` | `scope`, `level`, `word`, `text`, `action?`, `detail` | signe « Partie sûre » (`SafetyView`) | non mesuré |
| `ping` | `id`, `serverNowMs` | toutes les 5 s (salle ouverte ou en jeu) | 50 |
| `error` | `reason`, `message`, `retryable` | refus : `PLAY_*` (voir `PlayReason`) ou `UNSUPPORTED`, `BAD_REQUEST`, `FORBIDDEN` | non mesuré |
| `roomGone` | `reason` | salle fermée : `EXPIRED`, `HOST_CLOSED_INTERNET`, `HOST_LOST`… | non mesuré |
| `replay` | `events` | évènements manqués (anneau de 50) avant le `state` d'une reprise | non mesuré |
| `ack` | `ref`, `result` | accusé d'un `act` / `relayAct` : `OK SAME CLOSED UNKNOWN_QUESTION TOO_EARLY FORBIDDEN BAD_REQUEST UNKNOWN_PLAYER IGNORED` | 42 |

Limites reprises de `QuizHttp` (constantes de `PlayProtocol`) : 12 flux, rafale 30, 10 requêtes/s, 30 codes faux de `join` par adresse et par 5 minutes (jamais de blocage ni de compte sur un `resume`). Le service les applique.

## Réponses d'un `act` (`answer`)

Ordre des contrôles : salle en jeu et table active (sinon `CLOSED`) → question connue (**future ou inconnue ⇒ `UNKNOWN_QUESTION`**, jamais acceptée ; passée ⇒ `CLOSED`) → phase (`CLOSED`) → **ouverture** (`TOO_EARLY`) → rejeu (**même choix ⇒ `SAME`, sans effet** ; autre choix ⇒ `FORBIDDEN`) → temps compté → `QuizRoom.act`.

## Équité et latence (DESIGN § 2.6, appliquée AVANT `QuizRoom.act`)

| Mesure | Règle | Exemple vérifié |
|---|---|---|
| RTT | mesuré par le serveur (ping/pong) ; RTT retenu = MINIMUM des 8 derniers échantillons (un client qui retarde ses pongs ne gagne rien), chaque échantillon borné [0, 2 000 ms] | `RttBookTest`, `AuditCoreTest` |
| Temps d'un joueur | `arrivée − opensAtServerMs − min(rtt, 200 ms)/2` (100 ms au plus), jamais négatif | RTT 600, arrivée à 10 000 ⇒ **9 900** ; RTT 2 000 ⇒ **9 900** |
| Joueur relayé par la TV | `max(localElapsedMono, temps serveur − min(rttTV, 400 ms))` : la TV ne peut qu'**allonger** | local 9 900, RTT TV 300 ⇒ `max(9 900, 9 700) = 9 900` ; RTT TV 1 900 ⇒ terme 9 600 |
| Grâce | la question n'est clôturée qu'après `min(rtt, 1 s)` du plus lent connecté ; une réponse en vol dont le temps compté tient dans la fenêtre est acceptée | — |
| Borne d'équité | personne n'est compté plus vite que la vérité ; un joueur à RTT > 200 ms perd au plus `rtt/2 − 100 ms` | simulation `ServerRoomTimingTest` (RTT 0, 150, 600, 1 200) |
| `LAN`, `TV_ONLY` | aucune compensation, aucune grâce, aucun délai (comportement actuel) | `ServerRoomTest` |

## Salle (`ServerRoom`)

| Sujet | Règle |
|---|---|
| États | `OPEN → PLAYING → FINISHED → GONE` ; retour à `OPEN` par l'action d'hôte `lobby` |
| Code | 8 caractères Crockford (32 symboles, 40 bits), affiché `XXXX-XXXX` ; vie 2 h ; **50 essais faux sur la salle ⇒ nouveau code** (évènement `codeRotated`, l'hôte reçoit le nouvel état) ; 10 / IP / 5 min |
| Sièges | ≤ 8 joueurs par table ; le 9e : `PLAY_ROOM_FULL` ; spectateur par `join{spectate:true}` (≤ 50, si permis) ; un `deviceHash` ne tient qu'un siège, le second entre en spectateur ; deux `join` simultanés pour le dernier siège : un seul gagne (verrou de salle) |
| Reprise | `resume{roomId, token, lastSeq}` : dans l'anneau ⇒ `replay` puis `state` ; trop ancien ⇒ `state` complet (`full:true`) ; jeton inconnu ou d'une autre salle ⇒ `PLAY_BAD_CODE` **identique** (n'apprend rien sur l'existence de la salle) |
| Joueur perdu | la place est gardée jusqu'à la fin de la salle |
| Hôte perdu | si tous les joueurs sont derrière la TV : table **en pause** (l'horloge de la table est gelée, le temps restant ne bouge pas) ; au bout de 60 s : table `ABANDONED(HOST_LOST)`, partie « interrompue », salle purgée à 10 min ; avec des joueurs distants la partie continue jusqu'à 60 s |
| Hôte qui part | `end` : fin pour tout le monde ; `autohost` : le serveur enchaîne les questions (Duel) |
| Fermer Internet | refusé pendant une partie (`FORBIDDEN`, « seulement en salle d'attente ») ; en salle d'attente : les joueurs distants deviennent spectateurs, puis `roomGone(HOST_CLOSED_INTERNET)` après 30 s ; rouvrir annule |
| Secret | aucun message avant la clôture ne contient `answer`, `explanation` ni la bonne réponse sous une autre clé (`NoAnswerLeakTest`, 1 000 parties) ; le 50:50 ne retire que de mauvaises réponses ; aucun message ne liste plus d'une question |

## Exemples

```json
{"t":"join","code":"K7M2-QX4T","name":"Awa","spectate":false}
{"t":"welcome","seq":4,"roomId":"r1","code":"K7M2QX4T","token":"9f2c…","role":"PLAYER","playerId":"p1","proto":1,"caps":["play1","sse","longpoll","relay","spectate"]}
{"t":"question","seq":9,"questionId":"q-0412","index":1,"count":10,"text":"…","choices":["…","…","…","…"],"opensAtServerMs":88500,"serverNowMs":87000,"windowMs":20000}
{"t":"act","seq":12,"questionId":"q-0412","action":"answer","choice":2}
{"t":"ack","seq":10,"ref":12,"result":"TOO_EARLY"}
{"t":"relayAct","seq":3,"token":"a1b2…","questionId":"q-0412","choice":1,"localElapsedMono":9900}
{"t":"resume","roomId":"r1","token":"9f2c…","lastSeq":42}
{"t":"error","seq":10,"reason":"PLAY_ROOM_FULL","message":"La salle est complète. …","retryable":true}
```

## Deux écrans et symbiose

`ServerAuthority` (client) implémente la même interface `GameAuthority` que `LocalAuthority` : `AuthorityContractTest` version mémoire (`ServerAuthorityTest`) joue le même scénario à graine par les deux et exige les mêmes `stage`, `phase` et scores à chaque étape (périmètre `LAN`, sans délai). Capacité : `play1`. Refus : `PLAY_*` (`PlayReason`). La reprise dans `C/sync/Reason.kt` et `Caps.kt` reste à faire à la fusion de W19 (ces fichiers n'existent pas sur cette branche).
