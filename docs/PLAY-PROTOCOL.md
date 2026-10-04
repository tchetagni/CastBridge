# Protocole `play-v1` — Quiz en ligne

> Créé par w20-01 (section « Timing »), complété par w20-02 (messages, codec, reprise, équité). Conception : `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md`. Code : `android/core/src/main/kotlin/castbridge/core/quiz/online/` (`PlayProtocol`, `PlayCodec`, `ServerRoom`, `RttBook`, `RoomCode`, `EventRing`, `PlayTransport`, `ServerAuthority`). Tout est pur (aucune socket) : le service `castbridge-play` (w20-03) apporte la socket, l'horloge et le tick.

## Versions et capacités

| Élément | Valeur |
|---|---|
| Nom / numéro | `play-v1`, `PlayProtocol.PROTO = 1` |
| Capacités (`caps`, additives) | `play1`, `sse`, `longpoll`, `relay`, `spectate`, `play-ticket` (w20-04 : `create` porte l'activation `cbx1`) ; le serveur répond avec l'intersection |
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

Décodage **strict** (`PlayCodec.decodeClient`) : type inconnu ⇒ `error UNSUPPORTED` ; champ manquant, mal typé, hors borne, caractère de contrôle ou message de plus de 2 048 octets ⇒ `error BAD_REQUEST` (seuls `create` et le `join` d'une TV, w20-04b, peuvent atteindre 16 384 octets : ils portent l'activation de la TV, w20-04). Taille mesurée : champs au maximum permis (test `PlayCodecTest`).

| `t` | Champs | Bornes | Rôle | Octets max mesurés |
|---|---|---|---|---|
| `hello` | `proto`, `caps`, `deviceHash?`, `ticket?` | proto 1..1000 ; ≤ 12 capacités de ≤ 24 car. ; ticket ≤ 1 200 car. | ouverture, négociation | 1 650 |
| `create` | `name?`, `mode?`, `activation?`, `rentals?` | nom ≤ 16 ; `MILLIONAIRE` ou `DUEL` ; `activation` : un jeton `cbx1` ≤ 4 096 car. ASCII visibles ; `rentals` : ≤ 2 jetons `cbx1` (lignes de location signées) de ≤ 4 096 car. | l'hôte (TV) crée la salle ; sans nom la TV ne joue pas ; **sans `activation` valable pour le code d'appareil du ticket : `PLAY_SCOPE_FORBIDDEN`, aucune salle** (w20-04, additif : un ancien client qui ne les envoie pas est refusé de la même façon) | 61 (sans activation) |
| `join` | `code`, `name?`, `token?`, `deviceHash?`, `spectate?`, `activation?` | code normalisé (casse, tiret, I/L→1, O→0) ; `activation` (w20-04b, additif) : un jeton `cbx1` ≤ 4 096 car. ASCII visibles ; un `join` qui porte `activation` peut atteindre 16 384 octets, sans elle 2 048 comme avant | entrer ; une TV (hôte, ou TV invitée qui a le relais) enregistre ainsi un joueur local relayé. **Avec `CASTBRIDGE_PLAY_WEB=0` (défaut), une connexion non assise doit présenter ticket `cbp1` + `activation` `cbx1` : seule une CastBridge-TV activée entre** (voir « Ticket et droits ») | 227 sans `activation` |
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


## Ticket et droits (w20-04)

- **Ticket `cbp1`** (émis par l'API principale, `POST /api/v1/play/ticket`, clé dédiée `play-ticket.key`) : `cbp1.<charge utile base64url>.<signature base64url>` ; signature Ed25519 sur `castbridge-play-ticket-v1\ncbp1.<charge utile>` ; charge `{"aud":"castbridge-play","deviceId","blocked":false,"country","deviceCode","iat","exp","jti"}` (ms ; vie 600 s ; `jti` = 128 bits aléatoires, hexadécimal). **Aucune édition, aucun droit.**
- Le service ne garde que la clé **publique** (`CASTBRIDGE_PLAY_TICKET_PUBKEY*`). Il refuse (fermé) : signature fausse, mauvaise audience, appareil bloqué, expiré, pas encore valable, vie > 15 min, `jti` absent ou mal formé, ancien préfixe `v1`. Sans clé publique : aucune salle.
- **Audit w20-04** : le plafond de salles et un plafond **quotidien** (30 par défaut) comptent aussi l'**identité d'activation** (code d'appareil signé), car l'appareil API se recrée à volonté ; une salle d'essai accueille 8 joueurs au plus ; `create` est refusé (retentable) tant que les révocations ne sont pas connues ; lecture base64url stricte ; `jti` en mémoire (redémarrage = un rejeu possible d'un ticket < 10 min, mono-instance seulement).
- **Usage unique** : le `jti` est mémorisé jusqu'à `exp` (au plus `CASTBRIDGE_PLAY_MAX_USED_TICKETS`, plein = refus, jamais d'éviction d'un `jti` valable) ; un second `create` avec le même ticket ⇒ `PLAY_TICKET_REFUSED`. Un ticket refusé après ce pas (droits, plafonds) est brûlé : l'API en redonne 20 par heure et par appareil.
- **Plafonds** : salles ouvertes par appareil attesté (`CASTBRIDGE_PLAY_MAX_ROOMS_PER_SUBJECT`, 2 ; l'essai : 1), créations par adresse cliente (/64 en IPv6) et par heure (`CASTBRIDGE_PLAY_CREATES_PER_IP_HOUR`, 20) ⇒ `error PLAY_BUSY` (réessayable) avec le texte.
- **Droits** : `HostRights` = évaluation de l'activation `cbx1` et des lignes de location jointes à `create` par `TvGate`/`RentalEngine` du cœur avec l'horloge du **service**, clés publiques des émetteurs de confiance (`CASTBRIDGE_PLAY_TRUSTED_KEYS`), révocations signées relues toutes les 15 min ; le code d'appareil de l'activation doit être celui du ticket. Règles commerciales : `PlayRules` (cœur) ; refus par `PLAY_SCOPE_FORBIDDEN` (« Activez la TV pour créer une partie Internet », « Vérifiez l'heure de la TV », essai : privé, 3 parties par jour).
- **Entrée des TV (w20-04b, `CASTBRIDGE_PLAY_WEB=0`, défaut)** : seule une CastBridge-TV activée, connectée à Internet, est cliente du service. Pour **créer comme pour rejoindre**, elle présente son ticket `cbp1` ET son activation `cbx1` (`join.activation`). Ordre du `join` d'une connexion non assise, fermé à chaque pas et sans rien consommer avant la fin : ticket valide ⇒ maintenance ⇒ liste de révocations connue (`PLAY_MAINTENANCE`, réessayable) ⇒ `jti` pas encore servi (**la même table que `create`** : un ticket sert une seule fois, à `create` **ou** à `join`) ⇒ activation de **cette** TV (sinon `PLAY_SCOPE_FORBIDDEN`, ticket brûlé) ⇒ **alors seulement** la recherche du code. Un appelant sans ticket valide reçoit `PLAY_TICKET_REFUSED`, **le même pour un code vivant et un code faux** : aucun compteur de codes faux ni rotation de code ne lui est accessible. Code faux d'une TV authentifiée : compteurs d'adresse existants **plus** un compteur par identité de TV (30 / 5 min, `PLAY_BAD_CODE` avec `retryAfterMs`) ; un code valide n'est jamais refusé par un compteur. Essai : créations **et** entrées dans le même compteur (3 par jour), une seule connexion vivante par identité ; production : au plus `CASTBRIDGE_PLAY_MAX_ROOMS_PER_SUBJECT` salles créées + rejointes. Une connexion déjà assise qui envoie `join` relaie un joueur local (aucun ticket redemandé) ; `resume` : inchangé. Aucun navigateur : une requête `/play/ws`, `/play/act`, `/play/events` ou `/play/state` portant un en-tête `Origin` ⇒ 403 `{"error":"origine refusée"}` ; `/play` et `/play/j/<code>` servent une page d'information statique (sans script ni formulaire). `CASTBRIDGE_PLAY_WEB=1` (staging seulement, avec `CASTBRIDGE_PLAY_DIRECT=1`) rend le `join` libre par code d'avant. `CASTBRIDGE_PLAY_DIRECT=1` est refusé si `CASTBRIDGE_PLAY_TRUSTED_PROXIES` est posé. `CASTBRIDGE_PLAY_REVOCATIONS=on` (défaut) : fermé tant qu'aucune liste signée récente n'est acceptée ; `off` (POC, ≤ 20 salles) est explicite et visible (santé `"revocations":"disabled"`, avertissement au démarrage).
- **Coalescence « dernier état seulement » (w20-04b)** : dans la file de sortie de chaque connexion (WebSocket et repli), un `state` en file est **remplacé** par le suivant (le nouveau prend la place en fin de file) ; `question`, `reveal`, `ack`, `ping`, `error`, `roomGone`, `replay`, `welcome`, `safety` ne sont jamais retirés ni réordonnés. Sur une liaison EDGE (40 kbps ≈ 5 Ko/s) une rafale de 8 réponses ne met plus ≈ 40 Ko d'états périmés devant la révélation.
- **Questions réservées** : servies seulement dans la salle d'un hôte titulaire, pour les lots qu'il couvre (`ReservedBank`), une question à la fois (inchangé : `ServerRoom`) ; sans le gel `reserved-ids.json`, aucune réservée n'est servie.
