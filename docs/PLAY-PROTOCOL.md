# Protocole `play-v1` — Quiz en ligne

> Créé par w20-01 (section « Timing »), complété par w20-02 (messages, codec, reprise, équité). Conception : `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md`. Code : `android/core/src/main/kotlin/castbridge/core/quiz/online/` (`PlayProtocol`, `PlayCodec`, `ServerRoom`, `RttBook`, `RoomCode`, `EventRing`, `PlayTransport`, `ServerAuthority`). Tout est pur (aucune socket) : le service `castbridge-play` (w20-03) apporte la socket, l'horloge et le tick.

## Versions et capacités

| Élément | Valeur |
|---|---|
| Nom / numéro | `play-v1`, `PlayProtocol.PROTO = 1` |
| Capacités (`caps`, additives) | `play1`, `sse`, `longpoll`, `relay`, `spectate`, `play-ticket` (w20-04 : `create` porte l'activation `cbx1`), `chess` (games-G2 : salles d'échecs `game:chess`) ; le serveur répond avec l'intersection. La page `GET /play/.well-known/caps` annonce en plus `"chess":true|false` et `"stakes":true|false` (mises ouvertes : clé « portefeuille » et clé « résultat » configurées, interrupteur allumé) : c'est ce que lit la TV, plus aucun drapeau saisi. **games-G5** : la page annonce aussi `"quizStakes":true|false` (et la capacité `quizStakes` dans `caps` quand elle est vraie) : ce service arbitre un **Quiz misé** (mêmes conditions que `stakes`, indépendante de `chess` : couper les échecs ne coupe pas le Quiz misé) ; absente d'un service plus ancien, la TV ne propose alors que « Libre » au Quiz |
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

Décodage **strict** (`PlayCodec.decodeClient`) : type inconnu ⇒ `error UNSUPPORTED` ; champ manquant, mal typé, hors borne, caractère de contrôle ou message de plus de 2 048 octets ⇒ `error BAD_REQUEST` (seuls `create` et le `join` d'une TV, w20-04b, peuvent atteindre **8 192 caractères** : ils portent l'activation de la TV, w20-04 ; plafond du service `MessageSchema` et du corps du POST de repli, `PlayProtocolLimits.MAX_BODY` = 8 192 + 512 ; l'ancienne mention « 16 384 octets » était fausse en pratique, `MAX_CREATE_BYTES` vaut 8 192 depuis l'audit Opus M-1). Mesures réelles d'une activation `cbx1` : 737 à 1 281 caractères (essai, production, illimitée, super, abonnement « tout »), ≈ 1 700 pour 13 achats, ≈ 2 270 pour 20 achats : tout cela passe ; une location (`rentals`, ≈ 960 à 1 260 caractères chacune) reste **non envoyable en pratique** (le plafond de 8 192 laisse la place à une activation et au plus quelques locations, jamais à deux grosses) ; côté TV, un contrôle de taille avant envoi dit clairement « activation trop volumineuse ». Taille mesurée : champs au maximum permis (test `PlayCodecTest`).

| `t` | Champs | Bornes | Rôle | Octets max mesurés |
|---|---|---|---|---|
| `hello` | `proto`, `caps`, `deviceHash?`, `ticket?` | proto 1..1000 ; ≤ 12 capacités de ≤ 24 car. ; ticket ≤ 1 200 car. | ouverture, négociation | 1 650 |
| `create` | `name?`, `mode?`, `activation?`, `rentals?`, `proof?` | nom ≤ 16 ; `MILLIONAIRE` ou `DUEL` ; `activation` : un jeton `cbx1` ≤ 4 096 car. ASCII visibles ; `rentals` : ≤ 2 jetons `cbx1` (lignes de location signées) de ≤ 4 096 car. | l'hôte (TV) crée la salle ; sans nom la TV ne joue pas ; **sans `activation` valable pour le code d'appareil du ticket : `PLAY_SCOPE_FORBIDDEN`, aucune salle** (w20-04, additif : un ancien client qui ne les envoie pas est refusé de la même façon) | 61 (sans activation) |
| `join` | `code`, `name?`, `token?`, `deviceHash?`, `spectate?`, `activation?`, `proof?` | code normalisé (casse, tiret, I/L→1, O→0) ; `activation` (w20-04b, additif) : un jeton `cbx1` ≤ 4 096 car. ASCII visibles ; un `join` qui porte `activation` peut atteindre 8 192 caractères, sans elle 2 048 comme avant ; `proof` (audit Opus H-3, additif) : preuve de possession de la clé d'installation de la TV, `<clé publique base64>.<signature base64>` (≤ 200 car.), voir « Ticket et droits »; un `join` sans `name` d'une TV qui regarde (`spectate`) est admis et nommé « TV » | entrer ; une TV (hôte, ou TV invitée qui a le relais) enregistre ainsi un joueur local relayé. **Avec `CASTBRIDGE_PLAY_WEB=0` (défaut), une connexion non assise doit présenter ticket `cbp1` + `activation` `cbx1` : seule une CastBridge-TV activée entre** (voir « Ticket et droits ») | 227 sans `activation` |
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
| `error` | `reason`, `message`, `retryable`, `retryAfterMs?` | refus : `PLAY_*` (voir `PlayReason`) ou `UNSUPPORTED`, `BAD_REQUEST`, `FORBIDDEN`, `BAD_NAME` (pseudonyme refusé : le message dit le motif). `retryAfterMs` (additif, w20-07) : attente conseillée avant de réessayer, absent s'il n'est pas précisé ; porté par `PLAY_BUSY` (service ou salle saturés) et par `PLAY_BAD_CODE` quand l'adresse est bloquée | non mesuré |
| `roomGone` | `reason` | salle fermée : `EXPIRED`, `HOST_CLOSED_INTERNET`, `HOST_LOST`… | non mesuré |
| `replay` | `events` | évènements manqués (anneau de 50) avant le `state` d'une reprise | non mesuré |
| `ack` | `ref`, `result` | accusé d'un `act` / `relayAct` : `OK SAME CLOSED UNKNOWN_QUESTION TOO_EARLY FORBIDDEN BAD_REQUEST UNKNOWN_PLAYER IGNORED` | 42 |

Limites reprises de `QuizHttp` (constantes de `PlayProtocol`) : 12 flux, rafale 30, 10 requêtes/s, 30 codes faux de `join` par adresse et par 5 minutes (jamais de blocage ni de compte sur un `resume`). Le service les applique.

### Anti-triche et limites (w20-07)

Toutes les décisions sont **douces** : un message refusé, jamais de liste noire d'adresses, jamais de blocage par pays, jamais d'empreinte de navigateur. Table des valeurs effectives : `docs/agent-reports/sonnet-w20-07.md`.

- **Schéma** : un message invalide reçoit `BAD_REQUEST` ou `UNSUPPORTED` sans effet sur la salle ; imbrication > 6 niveaux refusée ; **3 `BAD_REQUEST` en une minute ⇒ fermeture 1008** (un type inconnu, `UNSUPPORTED`, répond sans compter : un client plus récent n'est pas coupé). Les clés inconnues restent acceptées (règle additive).
- **Pseudonymes** (`Pseudonym`) : NFKC, 2 à 16 caractères, lettres/chiffres/espace/`-`/`'`/`.` (et marques de ton des langues camerounaises, 2 au plus par lettre), une seule écriture (latin mêlé de cyrillique ou de grec refusé), au plus 6 chiffres, pas d'adresse web, pas d'usurpation (`CastBridge`, `Admin`, `Modérateur`, `Prof`…), liste fr/en/pidgin avec « leet » et homoglyphes ; refus = `BAD_NAME` (« Pseudonyme refusé : <motif>. »). Un nom de 17 à 64 caractères donne `BAD_NAME` (motif : 2 à 16 caractères) ; au-delà de 64, le codec refuse (`BAD_REQUEST`).
- **Robots** (`BotScore`) : à partir de 70/100, **ce siège** sort du classement (les autres restent classés). En FIN de partie seulement, la vue `room` du joueur concerné porte `ranked` (et `rankNote: "Classement non pris en compte."` s'il est sorti) : jamais en direct pendant la partie (ce serait un oracle pour régler un robot sous le seuil), jamais pour un autre siège. Aucune accusation, aucune expulsion ; les motifs restent internes (modération). Hors Internet : toujours classé.
- **Codes faux** : le BON code entre toujours (la salle est cherchée d'abord) ; les mauvais codes sont comptés par adresse (30 / 5 min, 5 000 / jour), par paire adresse + appareil (30 / 5 min, 300 / jour) et par /48 IPv6 (120 / 5 min, aucun quota de jour) ; une adresse bloquée reçoit `PLAY_BAD_CODE` avec `retryAfterMs`.
- **Code de salle** : une frappe à un seul symbole d'un code vivant compte pour cette salle ; à la 50e son code change (salle d'attente seulement, au plus une fois par minute) ; seul chemin : `join`.

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
| Siège relais (w20-04b) | la TV qui entre par `join` (ticket + activation) reçoit du **service** le droit de relayer (`grantRelay`, jamais un message client) : elle relaie **ses** joueurs locaux (`join` d'une connexion assise ⇒ `relayJoin`, `relayAct`) comme l'hôte relaie les siens. Au plus **8 sièges relayés par TV** (`CASTBRIDGE_PLAY_MAX_RELAYED_PER_TV`, 1..8 ; `PLAY_ROOM_FULL` au-delà) en plus du plafond de la table. Chaque siège relayé garde `relayedBy` : `relayAct` d'une TV pour le joueur d'une autre ⇒ ack `FORBIDDEN` ; la reprise d'un siège relayé ne se fait que par sa TV. La perte d'une TV ne rend absents que **ses** joueurs (la reprise par `resume` les ramène) ; un spectateur relais n'est pas purgé tant qu'il porte des sièges ; expulser une TV relais retire aussi ses sièges relayés |
| Hôte perdu | si tous les joueurs sont derrière la TV (relayés **par l'hôte**) : table **en pause** (l'horloge de la table est gelée, le temps restant ne bouge pas) ; au bout de 60 s : table `ABANDONED(HOST_LOST)`, partie « interrompue », salle purgée à 10 min ; avec des joueurs distants (dont ceux d'une **autre TV**) la partie continue jusqu'à 60 s |
| Hôte qui part | `end` : fin pour tout le monde ; `autohost` : le serveur enchaîne les questions (Duel) |
| Fermer Internet | refusé pendant une partie (`FORBIDDEN`, « seulement en salle d'attente ») ; en salle d'attente : les joueurs distants (dont les sièges relayés par une autre TV) deviennent spectateurs, puis `roomGone(HOST_CLOSED_INTERNET)` après 30 s ; rouvrir annule |
| Secret | aucun message avant la clôture ne contient `answer`, `explanation` ni la bonne réponse sous une autre clé (`NoAnswerLeakTest`, 1 000 parties) ; le 50:50 ne retire que de mauvaises réponses ; aucun message ne liste plus d'une question |

## Salle de jeu `game:chess` (chantier games-G2)

Une salle d'échecs en ligne : **deux TV** jouent, des TV regardent. Conception : `docs/coordination/DESIGN-JEUX-CARTES-ET-ECHECS-EN-LIGNE-2026-10-07.md` ; usage : `docs/CHESS.md` § 6. Code : `core/chess/online/` (`ChessServerRoom`, pure ; `ChessSettlement` ; `GameReason`), service `server-play` (`RoomRegistry`, `stake/`). **Additif : aucun message ni champ du Quiz ne change** (`PlayCodecGameTest` prouve que `create` et `join` d'un Quiz sont identiques octet pour octet) ; un client ancien ignore les clés neuves, un service ancien répond `UNSUPPORTED`/`BAD_REQUEST`.

| Sens | `t` | Champs (additifs) | Rôle |
|---|---|---|---|
| client → serveur | `create` | `game:"chess"`, `chess:{perMoveSeconds 1..3600, mode "COMPETITION"\|"PRACTICE", color "white"\|"black"\|"random"}`, `stake?:{cur "NDEM"\|"MBOKO", per}`, `escrow?:<cbe1 ≤ 1 200 car.>` | l'hôte (TV) ouvre une salle d'échecs ; le service ramène `perMoveSeconds` dans 10..60 ; avec `stake`, la partie est en `COMPETITION` et `escrow` est **obligatoire** (sans lui : `STAKE_ESCROW_REQUIRED` avec la mise) |
| client → serveur | `join` | `escrow?` | l'adversaire entre par le code ; une salle misée sans `escrow` répond `STAKE_ESCROW_REQUIRED` (**rien n'est consommé** : la TV bloque sa mise et renvoie `join` sur la **même** liaison) ; `spectate:true` regarde sans mise |
| client → serveur | `resume` | — | reprise d'un siège (joueur ou spectateur) par `roomId` + `token` : `welcome` (même jeton), vue complète, et `result` si la partie est finie |
| client → serveur | `game` | `op` ≤ 16 car., `arg?` ≤ 64, `ply?` 0..100 000, `seq` | `op` = `move` (`arg` = UCI, `ply` **obligatoire**), `resign`, `draw` (`arg` = `offer`\|`accept`\|`decline`), `cancel` (l'hôte renonce avant l'arrivée de l'adversaire) |
| serveur → client | `welcome` | `game:"chess"` | entrée acceptée dans une salle d'échecs |
| serveur → client | `state` | `view` = format d'état des échecs (`docs/CHESS.md` § 5 et § 6.6) | la vue **du siège** : `legal` seulement pour qui a le trait ; `room:{id, game, code, state, role, seq, serverNowMs, hostConnected, forfeitMs, away}` ; `stake:{cur, per, pot, settled}` |
| serveur → client | `ack` | `result` | accusé d'un `game` : `OK`, `ILLEGAL`, `NOT_YOUR_TURN`, `STALE` (doublon ou retard : rien n'est joué), `OVER`, `FORBIDDEN` (spectateur, hors rôle), `IGNORED`, `BAD_REQUEST`, `UNKNOWN_PLAYER` |
| serveur → client | `result` | `token` = `cbr1` (≤ 6 000 car.) | partie misée finie ou interrompue : le résultat **signé** par la clé dédiée du service ; envoyé aux deux joueurs (jamais aux spectateurs) ; un jeton n'est jamais journalisé (`PlayRedact` le remplace par `[retiré]`, comme `cbe1`, `cbw1`, `cbx1` et `cbp1`) |
| serveur → client | `error` | `data?` | refus structuré ; `STAKE_ESCROW_REQUIRED` : `data:{game:"chess", cur, per}` |

**Refus propres aux jeux** (`GameReason`, code stable, texte français côté TV) : `GAME_UNAVAILABLE` (échecs coupés : `CASTBRIDGE_PLAY_CHESS=off`), `GAME_UNKNOWN` (jeu inconnu), `STAKES_SUSPENDED` (mises coupées, les parties libres restent ouvertes), `STAKE_TRIAL_FREE_ONLY` (TV d'essai : jamais de mise), `STAKE_ESCROW_REQUIRED`, `STAKE_ESCROW_INVALID` (signature, audience, échéance, autre TV, autre monnaie ou montant, **déjà employé**), `STAKE_BAD` (monnaie ou montant hors de ce que le service admet), `SEATS_TAKEN`, `SAME_TV`. Les refus du Quiz (`PLAY_*`) s'appliquent aussi (code faux, salle pleine, ticket, droits).

**Règles** : le service est l'arbitre (moteur `core/chess`) ; pendule du service, 10 à 60 s ; coups numérotés ; forfait d'une TV absente **60 s** ; deux TV absentes = partie interrompue (mises rendues) ; salle d'attente 30 min ; partie 3 h au plus ; partie finie gardée 5 min ; 8 TV spectatrices au plus.

**Blocage `cbe1`** (signé par l'API, domaine `castbridge-wallet-escrow-v1`) vérifié par le service avec la **clé publique** « portefeuille » (`CASTBRIDGE_PLAY_WALLET_PUBKEY`) : audience `castbridge-play`, `id` = l'identité d'appareil de l'activation présentée, monnaie et montant = ceux de la salle, un siège, valable (30 min), **jamais déjà employé dans une salle vivante** (un refus d'entrée **libère** le blocage). **Résultat `cbr1`** (domaine `castbridge-play-result-v1`) : `{kid, rid, room, game:"chess", cur, per, kind "END"\|"ABORT", at, lines:[[eid, id, utilisé, versé]…]}` où `room` est l'identifiant de la salle et `rid` un identifiant de résultat DÉTERMINISTE de cette salle (SHA-256 de `castbridge-chess-rid-v1\n<room>`, 128 bits en hexadécimal : un seul résultat possible par salle, d'où l'idempotence du règlement) ; Σ versé = Σ utilisé ; `ABORT` : tout à zéro (chaque blocage est rendu en entier). Le service **ne parle jamais à l'API** et ne détient **aucun secret du grand livre** : il dépose aussi une copie de chaque `cbr1` dans son volume (`CASTBRIDGE_PLAY_RESULTS_DIR`) pour le collecteur de l'hôte (`tools/wallet/collect-results.sh`).

**Quotas** : les salles de jeu comptent dans les mêmes plafonds que le Quiz (`CASTBRIDGE_PLAY_MAX_ROOMS`, par appareil attesté et par identité, créations par adresse et par heure) ; le code est tiré de l'espace commun (jamais deux salles de même code, Quiz ou échecs) ; l'essai : 3 parties par jour, libres.

**Santé et arrêt** : `GET /play/health` ajoute `chess` (booléen), `chessRooms` (salles d'échecs ouvertes) et `stakes` (booléen : mises acceptées) ; `GET /play/.well-known/caps` ajoute `games`, `chess`, `stakes` (et la capacité `stakes`). Un arrêt du service (`drain`) **interrompt** les parties en cours (`cbr1` `ABORT` : les mises sont rendues) avant de fermer.

## Salle de Quiz misée (chantier games-G5)

Le Quiz en ligne se joue aussi **avec une mise en NDEM ou en MBOKO**, par le même mécanisme que les échecs (option B de W22 § 3.3) : un blocage `cbe1` signé par l'API et porté par chaque TV, le résultat `cbr1` signé par le service, le règlement par l'API. Usage : `docs/QUIZ.md` § 5 bis. Code : `core/quiz/online/` (`ServerRoom`, `QuizStake`, `QuizOnlineStake`), service `server-play` (`RoomRegistry`, `stake/EscrowGate`). **Aucun message ni champ d'une salle libre ne change** : une salle de Quiz sans `stake` ni `escrow` est servie octet pour octet comme avant (`ServerRoomStakeTest.aFreeRoomIsExactlyAsBefore`, `PlayCodecGameTest`). Ce n'est **pas** une salle `game:<id>` : le Quiz garde son `create` sans clé `game` (`PlayProtocol.GAMES` reste `{chess}` ; `create{game:"quiz"}` répond `GAME_UNKNOWN`) ; `quiz` est le nom du jeu dans le blocage, le résultat signé et le journal des parties misées.

| Sens | `t` | Champs (tous additifs, déjà portés par le codec) | Rôle |
|---|---|---|---|
| client → serveur | `create` | `stake:{cur "NDEM"\|"MBOKO", per}`, `escrow:<cbe1>` | l'hôte (TV) ouvre une salle **Duel** misée ; le blocage vaut `per × k` pour `k` sièges (1 à 8) ; sans `escrow` : `STAKE_ESCROW_REQUIRED` avec `data:{game:"quiz", cur, per}` ; un mode autre que `DUEL` : `STAKE_BAD` ; un `escrow` sans `stake` : `STAKE_BAD` |
| client → serveur | `join` | `escrow?` | **une TV** entre par le code avec **son** blocage (son `k`) ; sans lui, la salle répond `STAKE_ESCROW_REQUIRED` avec la mise (**rien n'est consommé** : la TV bloque et renvoie `join` sur la même liaison) ; une salle libre refuse un `escrow` (`STAKE_BAD`). Une TV entre en **spectatrice relais** : ses téléphones jouent par elle (`join` d'une connexion assise = `relayJoin`), au plus son `k` ; **jamais de simple regard** sur une salle misée |
| client → serveur | `game` | `op:"cancel"` | seule action de jeu d'une salle de Quiz misée : l'hôte renonce **avant le départ** (accusé `OK`) ; chaque blocage est rendu en entier (`cbr1` `ABORT`) ; toute autre TV, ou après le départ : `FORBIDDEN` |
| serveur → client | `state` | `view.stake:{game:"quiz", cur, per, tvs, seats, pot, started, settled, outcome, payouts}` | additif, **absent d'une salle libre** : `tvs` = TV qui jouent (avant le départ celles qui ont bloqué ET dont la liaison est là ; après, celles qui ont au moins un siège), `seats` = sièges qui misent (figés au départ), `pot` = `per × seats`, `started` / `settled`, `outcome` = `SPLIT` (cagnotte partagée), `REFUND` (personne n'a marqué : chacun reprend sa mise) ou `ABORT` (partie interrompue), `payouts` = part de chaque joueur par identifiant (avant frais éventuels de la plateforme ; seul le règlement de l'API fait foi) ; dans une salle misée `view.settings.playLabel` vaut « Partie avec mise » (une salle libre garde « Compétition entre amis ») |
| serveur → client | `result` | `token` = `cbr1` | envoyé aux **TV qui ont bloqué une mise** (jamais aux spectateurs sans mise) à la fin ou à l'interruption ; renvoyé à une TV qui reprend sa place après la fin |

**Règles** (`ServerRoom`) :

- **Une mise par siège**, payée par le compte de la TV : le blocage `cbe1` de la TV porte `k` (1 à 8) et `amt = per × k` ; la TV ne peut asseoir plus de joueurs que son `k` (le suivant : `PLAY_ROOM_FULL`) ; au départ le service retient `min(k, joueurs présents)` pour cette TV, le non-utilisé est rendu par l'API. Une TV par identité (code d'appareil de son activation : `SAME_TV`), **8 TV au plus** (`PlayProtocol.MAX_STAKE_TVS`), ce qui tient dans les 16 lignes d'un `cbr1`.
- **Départ** : `start` exige **au moins deux TV qui misent, chacune avec un joueur** (sinon `BAD_REQUEST` « Une partie avec mise a besoin d'au moins deux TV qui misent, chacune avec un joueur. ») ; au départ les **sièges qui misent sont figés** (`started`) : une TV qui n'était pas là n'entre plus (`STAKE_ROOM_STARTED`), un téléphone déjà assis qui revient avec son jeton reprend sa place.
- **Une salle misée ne joue qu'une partie** : `lobby` est refusé une fois la partie commencée, `start` après la fin répond « …terminée… » ; `mode` ne vaut que `DUEL`. `end` (« fin pour tout le monde ») est **refusé** pendant la partie (`FORBIDDEN` : un hôte qui perd n'arrête pas la partie pour se faire rembourser).
- **Règlement** (`QuizSettlement`, **pur**) : cagnotte = `per × sièges qui misent` ; partagée par `Pot.split` selon le classement de ces sièges (1 : 100 % ; 2 : 70/30 ; 3 et plus : 60/30/10 ; ex æquo à parts égales ; 0 point = rien ; reste d'arrondi au meilleur) ; la ligne d'une TV `[eid, id, utilisé, versé]` = ses sièges × `per` et la somme de leurs parts ; **personne n'a marqué** : `versé = utilisé` pour chaque TV ; un siège sorti du classement par `BotScore` compte 0 ; Σ versé = Σ utilisé toujours (l'API refuse sinon). `rid` = SHA-256 de `castbridge-quiz-rid-v1\n<room>` (128 bits, hexadécimal) : **un seul résultat possible par salle**, d'où l'idempotence du règlement.
- **Interruption** (`ABORT`, tout à zéro) : salle fermée ou expirée (en attente comme en jeu), hôte qui annule avant le départ, **arrêt du service** (`finishGames`). Le résultat est signé **avant** `roomGone` et déposé dans le dépôt du service (`CASTBRIDGE_PLAY_RESULTS_DIR`) pour le collecteur de l'hôte. **Une TV perdue en cours de partie n'interrompt rien** : une TV invitée perdue plus de 60 s, ses sièges marquent 0 aux questions manquées, la partie continue, sa mise reste en jeu ; **l'hôte aussi** (le service ne l'abandonne pas à 60 s dans une salle misée : même sans l'action `autohost`, que la TV envoie toujours, il enchaîne les questions) : débrancher sa TV ne rend pas sa mise. Une TV qui a bloqué une mise n'est **jamais purgée** de la salle (son blocage doit figurer dans le résultat).
- **TV partie avant le départ** : une TV invitée dont la liaison est coupée **au moment du `start`** ne joue pas (`takesPart`) : ses téléphones sont retirés de la table, ses sièges ne misent pas, son blocage figure au résultat avec `utilisé 0` (l'API le rend en entier) ; elle ne compte ni pour les deux TV du départ ni dans `stake.tvs` / `stake.pot` du salon.
- **Blocage** : même vérification que les échecs (`EscrowGate`, clé publique « portefeuille », audience `castbridge-play`, `id` = identité de **cette** TV, monnaie et mise de la salle, **`k` de 1 à 8 au Quiz** contre **1 aux échecs**, valable 30 min, **jamais déjà employé dans une salle vivante**). Un refus d'entrée **libère** le blocage, et **seulement** s'il a été réservé par cet essai (un `cbe1` rejoué ne libère jamais la réservation d'une autre salle ; correctif latent du hub, aussi pour les échecs).
- **Refus propres** (`GameReason`, codes stables) : ceux des échecs (`STAKES_SUSPENDED`, `STAKE_TRIAL_FREE_ONLY` : la TV d'essai ne mise jamais, `STAKE_ESCROW_REQUIRED`, `STAKE_ESCROW_INVALID`, `STAKE_BAD`, `SAME_TV`) plus **`STAKE_ROOM_STARTED`** (409, non réessayable, « Cette partie avec mise a déjà commencé : vous ne pouvez plus la rejoindre. »).
- **Santé** : `GET /play/health` ajoute `quizStakes` (booléen) et `stakedRooms` (parties misées vivantes, Quiz et échecs : à vider avant un déploiement, l'arrêt les interrompt mises rendues) ; `GET /play/.well-known/caps` ajoute `quizStakes` et la capacité du même nom.
- **Compatibilité** : une TV d'avant games-G5 ne sait pas bloquer pour un Quiz : en entrant dans une salle misée elle lit le texte de `STAKE_ESCROW_REQUIRED` (« …bloquez votre mise pour entrer. ») ; un service sans la capacité `quizStakes` fait proposer « Libre » seulement, avec « Mises NDEM/MBOKO : mettez à jour ».

## Exemples

```json
{"t":"create","name":"TV Salon","activation":"cbx1.…","proof":"…","game":"chess","chess":{"perMoveSeconds":30,"mode":"COMPETITION","color":"random"},"stake":{"cur":"NDEM","per":20},"escrow":"cbe1.…"}
{"t":"welcome","seq":2,"roomId":"0f…","code":"K7M2QX4T","token":"9f2c…","role":"HOST","playerId":"p1","proto":1,"caps":["play1","chess"],"game":"chess"}
{"t":"join","code":"K7M2-QX4T","name":"TV Chambre","activation":"cbx1.…","proof":"…"}
{"t":"error","seq":1,"reason":"STAKE_ESCROW_REQUIRED","message":"Cette partie se joue avec une mise : …","retryable":true,"data":{"game":"chess","cur":"NDEM","per":20}}
{"t":"game","seq":7,"op":"move","arg":"e2e4","ply":0}
{"t":"ack","seq":9,"ref":7,"result":"OK"}
{"t":"result","seq":31,"token":"cbr1.…"}
{"t":"create","name":"TV Salon","activation":"cbx1.…","proof":"…","mode":"DUEL","stake":{"cur":"NDEM","per":20},"escrow":"cbe1.…"}
{"t":"error","seq":1,"reason":"STAKE_ESCROW_REQUIRED","message":"Cette partie se joue avec une mise : …","retryable":true,"data":{"game":"quiz","cur":"NDEM","per":20}}
{"t":"join","code":"K7M2-QX4T","name":"TV Chambre","activation":"cbx1.…","proof":"…","escrow":"cbe1.…"}
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


## Ticket et droits (w20-04)

- **Ticket `cbp1`** (émis par l'API principale, `POST /api/v1/play/ticket`, clé dédiée `play-ticket.key`) : `cbp1.<charge utile base64url>.<signature base64url>` ; signature Ed25519 sur `castbridge-play-ticket-v1\ncbp1.<charge utile>` ; charge `{"aud":"castbridge-play","deviceId","blocked":false,"country","deviceCode","iat","exp","jti"}` (ms ; vie 600 s ; `jti` = 128 bits aléatoires, hexadécimal). **Aucune édition, aucun droit.**
- Le service ne garde que la clé **publique** (`CASTBRIDGE_PLAY_TICKET_PUBKEY*`). Il refuse (fermé) : signature fausse, mauvaise audience, appareil bloqué, expiré, pas encore valable, vie > 15 min, `jti` absent ou mal formé, ancien préfixe `v1`. Sans clé publique : aucune salle.
- **Audit w20-04** : le plafond de salles et un plafond **quotidien** (30 par défaut) comptent aussi l'**identité d'activation** (code d'appareil signé), car l'appareil API se recrée à volonté ; une salle d'essai accueille 8 joueurs au plus ; `create` est refusé (retentable) tant que les révocations ne sont pas connues ; lecture base64url stricte ; `jti` en mémoire (redémarrage = un rejeu possible d'un ticket < 10 min, mono-instance seulement).
- **Usage unique** : le `jti` est mémorisé jusqu'à `exp` (au plus `CASTBRIDGE_PLAY_MAX_USED_TICKETS`, plein = refus, jamais d'éviction d'un `jti` valable) ; un second `create` avec le même ticket ⇒ `PLAY_TICKET_REFUSED`. Un ticket refusé après ce pas (droits, plafonds) est brûlé : l'API en redonne 20 par heure et par appareil.
- **Plafonds** : salles ouvertes par appareil attesté (`CASTBRIDGE_PLAY_MAX_ROOMS_PER_SUBJECT`, 2 ; l'essai : 1), créations par adresse cliente (/64 en IPv6) et par heure (`CASTBRIDGE_PLAY_CREATES_PER_IP_HOUR`, 20) ⇒ `error PLAY_BUSY` (réessayable) avec le texte.
- **Droits** : `HostRights` = évaluation de l'activation `cbx1` et des lignes de location jointes à `create` par `TvGate`/`RentalEngine` du cœur avec l'horloge du **service**, clés publiques des émetteurs de confiance (`CASTBRIDGE_PLAY_TRUSTED_KEYS`), révocations signées relues toutes les 15 min ; le code d'appareil de l'activation doit être celui du ticket. Règles commerciales : `PlayRules` (cœur) ; refus par `PLAY_SCOPE_FORBIDDEN` (« Activez la TV pour créer une partie Internet », « Vérifiez l'heure de la TV », essai : privé, 3 parties par jour).
- **Entrée des TV (w20-04b, `CASTBRIDGE_PLAY_WEB=0`, défaut)** : seule une CastBridge-TV activée, connectée à Internet, est cliente du service. Pour **créer comme pour rejoindre**, elle présente son ticket `cbp1` ET son activation `cbx1` (`join.activation`). Ordre du `join` d'une connexion non assise, fermé à chaque pas et sans rien consommer avant la fin : ticket valide ⇒ maintenance ⇒ liste de révocations connue (`PLAY_MAINTENANCE`, réessayable) ⇒ `jti` pas encore servi (**la même table que `create`** : un ticket sert une seule fois, à `create` **ou** à `join`) ⇒ activation de **cette** TV (sinon `PLAY_SCOPE_FORBIDDEN`, ticket brûlé) ⇒ **alors seulement** la recherche du code. Un appelant sans ticket valide reçoit `PLAY_TICKET_REFUSED`, **le même pour un code vivant et un code faux** : aucun compteur de codes faux ni rotation de code ne lui est accessible. Code faux d'une TV authentifiée : compteurs d'adresse existants **plus** un compteur par identité de TV (30 / 5 min, `PLAY_BAD_CODE` avec `retryAfterMs`) ; un code valide n'est jamais refusé par un compteur. Essai : créations **et** entrées dans le même compteur (3 par jour), une seule connexion vivante par identité ; production : au plus `CASTBRIDGE_PLAY_MAX_ROOMS_PER_SUBJECT` salles créées + rejointes. Une connexion déjà assise qui envoie `join` relaie un joueur local (aucun ticket redemandé) ; `resume` : inchangé. Aucun navigateur : une requête `/play/ws`, `/play/act`, `/play/events` ou `/play/state` portant un en-tête `Origin` ⇒ 403 `{"error":"origine refusée"}` ; `/play` et `/play/j/<code>` servent une page d'information statique (sans script ni formulaire). `CASTBRIDGE_PLAY_WEB=1` (staging seulement, avec `CASTBRIDGE_PLAY_DIRECT=1`) rend le `join` libre par code d'avant. `CASTBRIDGE_PLAY_DIRECT=1` est refusé si `CASTBRIDGE_PLAY_TRUSTED_PROXIES` est posé. `CASTBRIDGE_PLAY_REVOCATIONS=on` (défaut) : fermé tant qu'aucune liste signée récente n'est acceptée ; `off` (POC, ≤ 20 salles) est explicite et visible (santé `"revocations":"disabled"`, avertissement au démarrage).
- **Preuve de possession de la TV (audit Opus H-3, additif)** : le ticket peut porter `ik` (SHA-256 en hexadécimal de la clé d'installation Ed25519 de la TV, épinglée par l'API à la première demande : `POST /api/v1/play/ticket` accepte `installKey`). `create` et `join` portent alors `proof` = `<clé publique brute base64>.<signature base64>` ; la signature couvre `castbridge-play-bind-v1\n<jti>\n<code d'appareil>\n<SHA-256 hex de l'activation>` (même schéma que la liaison du portefeuille, autre domaine). Le service vérifie l'empreinte de la clé puis la signature ; une preuve absente, fausse, faite pour un autre ticket ou une autre activation ⇒ `PLAY_SCOPE_FORBIDDEN`, ticket brûlé. Un ticket sans `ik` n'est accepté que si `CASTBRIDGE_PLAY_REQUIRE_PROOF=0` (migration). Une TV 0.14.32 (sans preuve) n'ouvre plus de partie Internet tant qu'elle n'est pas mise à jour.
- **Ticket jugé à la création de la session (M-6)** : un POST de repli d'une session existante n'exige plus de ticket (le secret `X-Play-Conn` de 128 bits fait foi) ; un ticket valide reste exigé pour créer une session. Un service plus ancien (ticket à chaque POST) reste compatible avec la TV, qui peut encore l'envoyer.
- **Sondage `GET /play/.well-known/caps`** : `maxCreateBytes` (8 192) et, quand `CASTBRIDGE_PLAY_REVOCATIONS=off`, `"revocations":"off"` (la TV le dit à l'écran). `GET /play/health` : `revocationsEnforced` (booléen) et `proof` (`required` | `optional`).
- **Long-polls (H-4)** : un seul `GET /play/state` en cours par session (le nouveau termine l'ancien par une réponse vide) ; sockets tenues (flux + long-polls) plafonnées par adresse (`CASTBRIDGE_PLAY_MAX_HELD_PER_ADDR`, /64 en IPv6) : 429 au-delà.
- **Coalescence « dernier état seulement » (w20-04b)** : dans la file de sortie de chaque connexion (WebSocket et repli), un `state` en file est **remplacé** par le suivant (le nouveau prend la place en fin de file) ; `question`, `reveal`, `ack`, `ping`, `error`, `roomGone`, `replay`, `welcome`, `safety` ne sont jamais retirés ni réordonnés. Sur une liaison EDGE (40 kbps ≈ 5 Ko/s) une rafale de 8 réponses ne met plus ≈ 40 Ko d'états périmés devant la révélation.
- **Questions réservées** : servies seulement dans la salle d'un hôte titulaire, pour les lots qu'il couvre (`ReservedBank`), une question à la fois (inchangé : `ServerRoom`) ; sans le gel `reserved-ids.json`, aucune réservée n'est servie.
