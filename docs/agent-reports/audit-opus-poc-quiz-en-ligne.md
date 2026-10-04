# Audit Opus — POC « Quiz en ligne » (service `server-play`, cœur client TV, écrans TV)

> Audit en lecture seule, 2026-10-04, branche `integration/agents` HEAD `3d8fbb24`. Aucun code du dépôt modifié, aucun commit, aucun serveur, aucun réseau hors boucle locale.
> Preuves écrites et exécutées dans une **copie hors dépôt** (`git archive HEAD` dans le scratchpad de session) ; elles ne sont pas dans le dépôt. Les extraits ci-dessous suffisent à les recréer.
> Préfixes : `SP/` = `server-play/src/main/kotlin/castbridge/play/`, `C/` = `android/core/src/main/kotlin/castbridge/core/quiz/online/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `B/` = `backend/src/main/java/castbridge/server/`.

## Ce qui a été exécuté (et ce qui ne l'a pas été)

- Suites existantes, dépôt intact : `:server-play:test --tests 'castbridge.play.*' :core:test --tests 'castbridge.core.quiz.online.*'` → **VERT** (EXIT 0 ; server-play 235 tests, core 241 tests, 0 sauté, 0 échec).
- **7 tests de preuve** (copie hors dépôt) → **7 ROUGES** sur `3d8fbb24`, chacun = un défaut ci-dessous (C-1, H-1, H-2 ×2, H-4, M-1, M-2).
- **Mutations** (copie hors dépôt) : 6 mutations appliquées ensemble, suites existantes relancées : voir § 6.
- **Non exécuté** : aucune TV, aucun Android (le câblage `R/quiz/*` est jugé à la lecture), aucun nginx réel, aucune passerelle Bluetooth, aucun TLS réel, aucun MySQL (inutile ici), aucune mesure EDGE (les chiffres d'ack sont ceux de w20-05a, non remesurés). La taille des activations est **mesurée** avec le vrai `ActivationIssuer` (format `cbx1` exact, signature Ed25519 réelle, 5 facteurs comme `tools/activation/test-vectors.json`) ; seuls les jeux de droits sont des profils plausibles, pas des licences réelles de production.

## Résumé en 12 lignes

1. **CRITIQUE** : « Rejoindre » ne peut pas marcher sur une vraie TV : `PlayHub` envoie `join` sans nom ; le service exige un nom même pour un spectateur, refuse (`BAD_REQUEST`) **après** avoir brûlé le ticket et compté une partie d'essai (prouvé).
2. **HAUT** : la TV invitée (spectatrice relais, la TV EDGE par construction) n'entre pas dans la grâce de clôture : ses réponses que la formule accepte sont refusées `CLOSED` (prouvé).
3. **HAUT** : un `welcome` de siège relayé arrivé en retard écrase le jeton de la TV ; un `welcome` de reprise est donné au téléphone (prouvé, 2 tests).
4. **HAUT** : l'entrée n'est liée qu'à la possession d'une activation `cbx1` (remise aux téléphones appariés, fichiers USB) + un appareil API « tv » auto-déclaré : un script ou un téléphone modifié entre comme une TV.
5. **HAUT** : une seule session de repli (un ticket) épuise le service par long-polls concurrents (prouvé) ; nginx `limit_conn` par adresse ne protège pas en IPv6.
6. **HAUT (config)** : les clés de confiance documentées (`server:…:REVOKE+ISSUE_TRIAL+ISSUE_PRODUCTION`) refusent les TV activées par l'outil de bureau, les TV « super » et « tout ouvert ».
7. **Question 2 048** : mesuré 737 à 1 281 caractères pour essai / production / illimitée / super / abonnement « tout » : `create` et `join` **passent** ; ils échouent au-delà d'≈ 1 930 caractères d'activation (≈ 19 achats, ou abonnement + 13 achats) et le POST de repli plafonne à 4 096 octets : correctif exact § M-1.
8. **MOYEN** : TV d'essai bloquée après « Quitter » tant que sa salle vit (prouvé) ; « Nouvelle partie » contourne les 3 parties/jour ; clés courtes refusées en ligne ; course « Retour pendant l'ouverture » ⇒ salle fantôme.
9. **ack ≤ 1,5 s** : l'ack n'est pas visible ; l'effet réel est une pénalité `max(0, montée − 400 ms)` ≈ 0,7 s médiane (≈ 2 % des points), ≈ 2,3 s au pire (≈ 6 %) et une **falaise** à 0 point en fin de fenêtre (H-1) : corriger la montée, puis relâcher le critère à « ack p95 ≤ 3 s ».
10. « Commencer » (`mode DUEL`, `autohost`, `start`) : le service enchaîne bien seul (révélation 6 s, classement 6 s, ouverture à +1,5 s horloge serveur) ; **sauf la question 1**, ouverte sans délai.
11. Révocations `off` : une TV révoquée/transférée joue, une clé d'émetteur fuitée crée des identités sans fin, 10 activations copiées remplissent les 20 salles ; bascule exacte vers la liste signée au § 5.
12. Avant la production : C-1, H-1 à H-6, M-1, M-6, M-7 à corriger ; liste précise au § « Liste avant production ».

## Constats classés

### CRITIQUE

**C-1 — La TV invitée réelle ne peut jamais entrer ; chaque essai brûle le ticket et une partie d'essai.**
- `R/quiz/PlayHub.kt:150` : `PlayIntent.Join -> PlayTvSession.Intent.Join(RoomCode.normalize(intent.code) ?: intent.code, null)` ⇒ `ServerAuthority.joinRoom(code, name = null, …, spectate = true, activation)` (`C/ServerAuthority.kt:117`).
- `C/ServerRoom.kt:274` : `val name = QuizRoom.cleanName(m.name) ?: run { … "Choisissez un pseudonyme." }` est évalué **avant** la branche spectateur (`:277`).
- `SP/RoomRegistry.kt:263-268` : `used.use(jti)` et `trialDays.record(identity)` ont lieu **avant** `attachAndForward` (`:274`) ; le refus de la salle arrive après.
- Les tests verts passent tous un nom (`"TV B"`, `"TV Chambre"` : `MultiTvRelayTest.kt:69`, `TvClientLoopbackTest.kt:139`, `EdgeFluidityTest.kt:173`) : le câblage réel n'est couvert par rien.
- Scénario : TV B ▸ Rejoindre ▸ code juste ▸ écran « échec » générique (`PlayErrors.text("BAD_REQUEST")`) ; ticket brûlé ; une TV d'essai perd une de ses 3 parties du jour **à chaque appui**.
- Preuve (ROUGE) : `BAD_REQUEST « Choisissez un pseudonyme. » ; tickets brûlés = 2`
  ```kotlin
  hub.onText(b, PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, "0123456789abcdef", TestKeys.ticket(deviceCode = TestRights.otherTv.code))))
  hub.onText(b, PlayCodec.encode(ClientMsg.Join(code, null, null, "0123456789abcdef", true, TestRights.activation(device = TestRights.otherTv))))
  assertTrue(b.welcomed())   // échoue
  ```
- Correctif : (1) service : un spectateur sans nom s'appelle « TV » (`val name = QuizRoom.cleanName(m.name) ?: if (m.spectate) "TV" else run { … }`) ; (2) TV : envoyer le nom de la TV (passé par `Pseudonym`) ; (3) `tvJoin` : faire juger par la salle, sous le verrou, une admission **pure** (nom, appareil, banni, plein, code échu) **avant** de consommer le `jti` et le compteur d'essai, et ne consommer qu'après un `welcome` (sinon tout refus de salle continue de brûler ticket et essai).

### HAUT

**H-1 — Pas de grâce de clôture pour la TV invitée : réponses justes refusées `CLOSED`.**
- `C/ServerRoom.kt:491` : `maxGrace()` = max des RTT des connexions `role != SPECTATOR` ; la TV invitée est SPECTATRICE (`ServerAuthority.joinRoom(spectate = true)`), ses sièges relayés n'ont pas de connexion. La grâce est donc celle de l'hôte (box : ≈ 50 ms), alors que la conception (amendement § 2.7) compte sur « la grâce de clôture (≤ 1 s) couvre le trajet retour » de la TV EDGE.
- Scénario : question de 10 s ; téléphone de la TV B (RTT 900 ms) répond à 9 600 ms locaux ; le POST arrive à 10 350 ms serveur ; formule `max(9 600, 10 350 − 400)` = 9 950 ms ≤ 10 000 : doit compter ; la salle a déjà clos à 10 050 ms ⇒ `CLOSED`, 0 point.
- Preuve (ROUGE) : `expected:<[OK]> but was:<[CLOSED]>` (ServerRoom pur : hôte + TV B spectatrice relais, pongs retardés de 900 ms, `RelayAct(seat, q, 1, 9_600)` à `opensAt + 10_350`).
- Correctif : `byConn.entries.filter { it.value.role != PlayRole.SPECTATOR || it.value.relayer }`.

**H-2 — `ServerAuthority` attribue mal les `welcome` (et `error`) pendant un relais.**
- `C/ServerAuthority.kt:56-57` : sans corrélation, le prochain `welcome` va au relais si `pendingRelayJoin`, sinon **écrase** `welcome` (le siège de la TV).
- Scénario A (coupure EDGE pendant l'entrée d'un téléphone) : `relayJoin` expire à 15 s, le `welcome` du siège du téléphone arrive ensuite ⇒ `authority.token` devient le jeton du téléphone ; la reprise suivante (`resume(roomId, token)`) attache la connexion de la TV au siège du téléphone : la TV perd le relais, tous ses `relayAct` ⇒ `FORBIDDEN`.
- Scénario B : la TV reprend (`resume`) pendant qu'un téléphone entre ⇒ le téléphone reçoit le jeton de siège de la TV ⇒ ses réponses `UNKNOWN_PLAYER`.
- Même défaut pour `error` : toute erreur sans rapport (débit `PLAY_BUSY`, annonce de maintenance) fait échouer l'entrée du téléphone.
- Preuves (ROUGE) : `expected:<[tv-own-token-00000]…> but was:<[relayed-seat-token-]…>` et `le téléphone a reçu le jeton de siège de la TV`.
- Correctif : `welcome.token == m.token` ⇒ reprise de la TV ; `welcome != null && m.role == PLAYER && role != PLAYER` ⇒ siège relayé (jamais `welcome = m`) ; à terme, écho additif d'une référence client (`ref`) dans `welcome`/`error` d'un `join` relayé.

**H-3 — L'entrée ne prouve pas « une TV » : elle prouve la possession d'une activation.**
- `B/devices/DeviceService.java:92-104` : `app` (« tv »/« phone ») est **auto-déclaré** à l'enregistrement public ; `B/play/PlayTicketService.java:79-84` : `deviceCode` fourni par le client, lien collant **en mémoire** (2 appareils par code et 24 h) ; `SP/entitlement/HostRights.kt:58` : seule vérification de matériel = `DeviceCode.of(facteurs) == ticket.deviceCode` (déclaré).
- Les activations circulent : `C/../owner/TvProof.kt:18-19` (l'activation brute est remise à chaque téléphone apparié, envoyée en en-tête `X-CB-TV-Proof` sur le Wi-Fi local), fichier `activation` sur clé USB, QR, Bluetooth.
- Scénario : un téléphone apparié une fois (ou quiconque a eu la clé USB) enregistre un faux appareil `app=tv`, demande un ticket pour le code de la TV, joint l'activation : il crée et rejoint **comme cette TV** — exactement ce que la règle « jamais de téléphone sur le service » interdit. Borné par : 2 salles vivantes / 30 créations par jour (production), 1 / 3 (essai), 2 appareils API par code et 24 h (oublié au redémarrage de l'API), et la révocation (désactivée dans le POC).
- Correctif avant production : lier le ticket à un secret que seule la TV détient (clé d'installation `InstallSigner`, jamais remise aux téléphones) : l'API épingle à la première demande `deviceCode ↔ clé d'installation` et exige une signature d'un défi serveur ; tant que ce n'est pas fait : `MAX_DEVICES_PER_CODE = 1`, lien persistant en base, alerte d'exploitation sur refus.

**H-4 — Épuisement du service par long-polls d'une seule session.**
- `SP/PlayFallbackController.kt:157-166` : aucune limite de long-polls simultanés par session (seul le flux SSE remplace l'ancien, `attachStream`) ; `SP/PlayServer.kt:55,87` : sémaphore global `2 × maxConnections`, aucune comptabilité par adresse avant la requête ; `ConnectionLimits.admit` ne compte que la **création** de session.
- Preuve (ROUGE) : `maxConnections = 5, maxPerIp = 2` ; une session ; 12 `GET /play/state` de la même adresse ; `GET /play/.well-known/caps` d'un autre client ⇒ connexion fermée sans réponse.
- En production : nginx `limit_conn cbplay_addr 20` (`docs/PLAY-OPS.md:372`) borne une adresse IPv4 (≈ 150 adresses pour 3 000 sockets) mais **chaque adresse IPv6 /128 est une clé distincte** : un seul abonné IPv6 suffit.
- Correctif : un seul long-poll en cours par session (le nouveau termine l'ancien, comme `attachStream`) ; compte de sockets par adresse (/64) dans `acceptLoop` ; côté nginx, clé `limit_conn` sur le /64 IPv6 (`map`).

**H-5 — Clés de confiance documentées incompatibles avec la flotte réelle.**
- `docs/PLAY-OPS.md:236` et `backend/.env.play.example` : `CASTBRIDGE_PLAY_TRUSTED_KEYS=server:$SERVER_PUB:REVOKE+ISSUE_TRIAL+ISSUE_PRODUCTION` seulement.
- `android/core/.../owner/Activation.kt:144-149` : clé inconnue ⇒ `UNKNOWN_KEY` ; droit `super` ⇒ exige `SUPER_UNLIMITED` ; `openall` ⇒ exige `COMMAND_OPEN_ALL`.
- Scénario : les TV activées par l'outil de bureau (fichier USB : le canal principal), les TV « super » du propriétaire, toute TV « tout ouvert » ⇒ `PLAY_SCOPE_FORBIDDEN`, ticket brûlé à chaque essai, alors que la tuile dit « disponible ».
- Correctif exact : `CASTBRIDGE_PLAY_TRUSTED_KEYS` = **la même liste que les builds TV** (`android/receiver/build.gradle.kts:27`, `~/.castbridge-signing/activation-trusted-keys.txt`) avec les **mêmes portées** (dont `SUPER_UNLIMITED`, `COMMAND_OPEN_ALL`), plus `server:<publicKey>:REVOKE+ISSUE_TRIAL+ISSUE_PRODUCTION` ; essai à blanc par TV de démonstration la veille (B-7).

### MOYEN

**M-1 — La borne de 2 048 caractères (question 2).** Mesures réelles (`ActivationIssuer`, 5 facteurs, signature réelle ; `create` = message de `PlayHub` : `name=null, mode=DUEL, rentals=[]` ; `join` avec `deviceHash` de 32 caractères, le vrai en fait 16 : retrancher 16) :

| profil | `cbx1` (car.) | `create` | `join` | `MessageSchema` |
|---|---|---|---|---|
| essai (usage 30 j) | 776 | 845 | 911 | OK |
| production illimitée (aucun droit) | 737 | 806 | 872 | OK |
| production 365 j + 1 achat | 869 | 938 | 1 004 | OK |
| super (outil du propriétaire) | 780 | 849 | 915 | OK |
| abonnement « tout » (24 bouquets) + usage | 1 281 | 1 350 | 1 416 | OK |
| école : 13 achats + usage | 1 709 | 1 778 | 1 844 | OK |
| école : 20 achats + usage | 2 273 | 2 342 | 2 408 | **REFUSÉ** |
| abonnement « tout » + 13 achats + usage | 2 192 | 2 261 | 2 327 | **REFUSÉ** |

- Réponse : **oui, `create` et `join` réussissent en pratique** pour l'essai, la production, l'illimitée et le super ; ils **échouent** (`BAD_REQUEST « message trop long »`, avant le codec) dès qu'une activation dépasse ≈ 1 979 caractères (`create`) / ≈ 1 930 (`join`) : ≈ 19 achats, ou un abonnement + 13 achats (une école qui achète par classe). Une location (`rentals`) n'est jamais envoyable (≈ 960-1 260 caractères chacune). `docs/PLAY-PROTOCOL.md:48` (« peut atteindre 16 384 octets ») est faux en pratique ; le chemin `MAX_CREATE_BYTES` de `C/PlayCodec.kt:38-43` est mort.
- Trois bornes à aligner : `SP/guard/MessageSchema.kt:18` (2 048), `SP/PlayFallbackController.kt:180` (`MAX_BODY = 4 096` : le seul transport de la TV), `PlayConfig.maxFrameBytes` (20 Ko, WebSocket).
- Correctif exact (plafond explicite par type, pas de relèvement général) :
  ```kotlin
  // MessageSchema.decode
  val cap = if (text.startsWith("{\"t\":\"create\"") || text.startsWith("{\"t\":\"join\"")) MAX_ACTIVATION_MESSAGE /* 8 192 */ else PlayProtocol.MAX_MESSAGE_BYTES
  if (text.length > cap) return bad("message trop long")
  ```
  (le codec garde déjà le contrôle fin par type : `join` sans `activation` > 2 048 ⇒ refusé, `C/PlayCodec.kt:43`) ; `PlayProtocolLimits.MAX_BODY = 8 192 + 512` ; `PlayProtocol.MAX_CREATE_BYTES = 8 192` (doc alignée) ; côté TV, contrôle de taille avant envoi avec un texte clair. Test rouge : le profil « école : 20 achats » ci-dessus doit donner `welcome` par `PlayHub` **et** par `POST /play/act`.

**M-2 — TV d'essai bloquée après « Quitter ».** `SP/RoomRegistry.kt:171-172` et `:255` comptent les salles **créées** par l'identité, connectée ou non ; « Quitter » (`PlayHub.stop`) ferme seulement le transport ; `autohost` empêche l'abandon (`C/ServerRoom.kt:480`) ; l'invitée connectée garde la salle vivante jusqu'à 2 h. Preuve (ROUGE) : `PLAY_BUSY « Vous avez déjà 1 partie ouverte… »`. Correctif : la TV hôte envoie `act end` (ou un `leave` additif) avant de fermer ; ne compter que les salles dont le siège hôte est connecté ou non terminées.

**M-3 — L'essai contourne « 3 parties par jour ».** « Nouvelle partie » (`R/quiz/PlayOnlineActivity.kt`, `act lobby`) relance autant de duels que voulu dans la même salle (2 h) ; seuls `create`/`join` sont comptés. Décision du propriétaire ; correctif : compter chaque `start` d'une salle d'essai.

**M-4 — TV activée par clé courte : tuile « disponible », service refuse.** `C/../owner/Activation.kt:358` synthétise une `Activation` sans signature ni facteurs ; `R/quiz/PlayHub.kt:135` prend la plus récente (`TunnelEnroll.pickActivation`) ⇒ `cbx1` invérifiable ⇒ `NONE`, ticket brûlé. Correctif : filtrer `signature.isNotEmpty() && factors.isNotEmpty()` ; sinon tuile « Partie Internet : activez la TV avec le fichier d'activation ».

**M-5 — Salle fantôme.** `R/quiz/PlayHub.kt:129-161` : « Retour » pendant l'ouverture appelle `stop()` pendant que le fil `start` attend le ticket (≤ 25 s en EDGE) ; ensuite `session = s; relay = …` : une salle invisible vit, le ticket et la partie d'essai sont consommés, `/quiz` des téléphones est servi par ce relais. Correctif : numéro de génération vérifié avant d'affecter `session`/`relay`.

**M-6 — Économie des tickets.** Ticket exigé sur **chaque** POST (`SP/PlayFallbackController.kt:109`) ; nouveau ticket à chaque réouverture (`C/PlayTvSession.kt:142`, `R/quiz/PlayHub.kt:141`) et renouvellement à 8 min ; plafonds API 20 / heure / appareil et **120 / heure / adresse** (`PlayTicketService`, valeur non exposée dans `application.yml`) : derrière un CGNAT d'opérateur, quelques TV EDGE se partagent 120 tickets ; une heure agitée (3 coupures ≈ 18 tickets + 7 renouvellements) dépasse 20 ⇒ 429 ⇒ ticket échu ⇒ POST 403 ⇒ partie perdue. Correctif : juger le ticket à la création de session seulement (F-1 de w20-05a), réutiliser le ticket courant (< 9 min) aux réouvertures, exposer `CASTBRIDGE_PLAY_TICKET_PER_ADDRESS_PER_HOUR`.

**M-7 — nginx `limit_conn 20` contre 9 connexions par TV.** `docs/PLAY-OPS.md:314,334,372` ; `C/PlayHttpTransport.kt:262` (`MAX_PARALLEL = 8`) + 1 flux SSE : deux TV EDGE derrière la même adresse CGNAT saturent la limite en rafale ⇒ 429 ⇒ attente ⇒ réponses en retard. Correctif : `MAX_PARALLEL = 2` avec envoi groupé ; `limit_conn` de `/play/act` aligné sur `CASTBRIDGE_PLAY_MAX_PER_IP_SHARED`. Au passage : l'implémentation Android garde 5 connexions vivantes par hôte (`http.maxConnections`) : au-delà, chaque POST parallèle paie une poignée TLS (≈ 3 RTT ≈ 2,4 s en EDGE), cause probable des maxima d'ack de 3-4 s.

**M-8 — La montée de la TV est sérialisée derrière les `pong`.** `C/PlayHttpTransport.kt:86-98,113-129` : tout message non-`relayAct` (dont le `pong` toutes les 5 s) part sur le fil écrivain en bloquant, avec jusqu'à 3 essais et des attentes ≤ 5 s ; les `relayAct` arrivés entre-temps attendent. Correctif : `pong` (et `act`) par le pool, ou priorité aux `relayAct`.

**M-9 — Question 1 sans délai.** `C/ServerRoom.kt:509` : `if (d.index == 0) Announcement(id, now)` ⇒ la première question s'ouvre à l'annonce : la TV EDGE la reçoit 0,3-1 s après l'ouverture, ses joueurs perdent ce temps (et le temps de lecture). Correctif : délai de 1,5 s aussi pour l'index 0.

### BAS

- **B-1** `C/RelayAuthority.kt:54-58` : un pseudonyme refusé par le service (`BAD_NAME`) est rendu au téléphone comme `CLOSED` (« salle fermée ») au lieu de « choisissez un autre nom ».
- **B-2** `C/ServerAuthority.kt:138` : `++clientSeq` hors verrou dans `act`, sous verrou dans `relay` (`:92`) : deux `seq` identiques possibles (accusés mélangés).
- **B-3** `C/PlayTvSession.kt:146-148` : une reprise refusée (`PLAY_BAD_CODE`, siège purgé) attend 60 s avant « perdue ».
- **B-4** `R/quiz/PlayOnlineActivity.kt` : « Commencer » sans joueur ⇒ `BAD_REQUEST` ignoré en silence (l'écran ROOM ne regarde que `gone` et la liaison).
- **B-5** `C/ServerRoom.kt:466` : expulser une TV bannit son adresse pour la salle (CGNAT : tout l'opérateur).
- **B-6** `C/ServerRoom.kt:311-316` : `resume` rattache aussi un siège relayé à n'importe quelle connexion (le `join` l'interdit) ; jetons jamais sortis de la TV : faible.
- **B-7** Docs : vérifications de `docs/PLAY-OPS.md` § 4.4 périmées avec `WEB=0` (page de jeu attendue, `101` sans ticket) ; `PLAY-OPS-REQUIREMENTS.md` dit « 8 par adresse », le défaut est 24 ; le profil STAGING de `.env.play.example` (`DIRECT=1`, clé de test) contredit « staging pour les innovants = production » (seule exception listée : `REVOCATIONS=off` du POC).

## Questions de l'audit : réponses

**(1) Chemin d'entrée.** Rien d'autre qu'un porteur de ticket `cbp1` valide **et** d'une activation `cbx1` acceptée n'entre ni ne crée (ordre `SP/RoomRegistry.kt:225-245` vérifié ; navigateur ⇒ 403 avant tout, `SP/PlayServer.kt:108`). Ticket volé seul : inutile sans activation (et brûlé au premier essai faux). Activation forgée / clé non fiable / autre TV / sujet téléphone / essai portant des droits / émise > 24 h dans le futur : refusées (`HostRights.kt`, `Activation.kt:139-165`), ticket brûlé. Rejeu de `cbp1` : `jti` à usage unique, table commune `create`/`join`, recontrôle sous verrou ; les POST acceptent un ticket déjà consommé (sans état, voulu) pendant ≤ 10 min. Ticket brûlé par un refus de la salle : **oui**, et C-1 rend ce cas systématique. Fenêtre de 48 h : jamais jugée en ligne (vérification à la date d'émission, choix documenté) : une activation jamais installée mais signée pour ce matériel vaut en ligne. Essai/production : essai 3 parties/jour (créations + entrées), 1 connexion vivante, 8 joueurs ; production 2 salles vivantes, 30 créations/jour. **Mais** H-3 : un téléphone ou un script **avec une activation copiée** entre. Tickets par appareil : 20/h par appareil API, 120/h par adresse, lien code ↔ appareil en mémoire (2 par 24 h).

**(2) 2 048.** Voir M-1 : mesures, seuil, correctif exact.

**(3) Abus.** Fuzz : profondeur 6, 40×4 éléments, aucune exception (OK) ; surtaille OK sauf M-1. Long-poll : H-4. SSE : un flux par session (OK). Connexions par adresse : 24 (service) / 20 (nginx) ; IPv6 contourne nginx (H-4). 8 sièges relayés : imposés **des deux côtés** (`RelayAuthority.MAX_PHONES` à la TV, `relayedOf(tv).size >= maxRelayedPerTv` au service, `ServerRoom.kt:300`) — vérifié. Force brute de code : seuls les appelants authentifiés, 30 / 5 min par identité, 32⁸ codes : infaisable (OK). Cookie `__Host-cbp-<nonce>` : dormant (tout `Origin` refusé), la TV lit `Set-Cookie` puis renvoie `X-Play-Conn` (OK). TV qui usurpe des sièges : `relayedBy` ⇒ `FORBIDDEN` (OK) ; une TV tricheuse ne gagne au plus que 400 ms (`RttBook.relayed`, voulu). Nom hostile d'un téléphone : `QuizRoom.cleanName` sur la TV puis `Pseudonym.check` au service (`PlayGuard.refuse`) (OK ; B-1 pour le message). `BotScore` : la règle des jumeaux exige le même `deviceHash` **non nul** et ≤ 60 ms d'écart sur ≥ 80 % : pas de faux positif pour des téléphones à empreintes distinctes ou nulles ; risque réel seulement si la TV relayait tous ses téléphones sous un même `deviceHash` (à interdire côté TV). Coalescence : seul `state` est remplacé, le nouveau en fin de file, octets recomptés, long-poll `since` correct : **aucune `reveal` ni annonce perdue** ; un état « classement » peut être sauté si la liaison retarde > 6 s (le suivant contient le classement) : acceptable.

**(4) Client / écrans.** Automates : H-2, M-5, B-3. Session perdue (410) ⇒ nouveau transport, `hello` + `resume` ; perte ≥ 60 s ⇒ « perdue » (OK). Flux rouvert sur la même session ⇒ `resume` sur la même connexion ⇒ `replay` + état complet + annonce (OK ; le `replay` est ignoré mais l'annonce renvoyée suffit). `LinkCause` : ne peut que baisser, passerelle ⇒ orange (OK). **ack** : l'accusé ne s'affiche nulle part ; ce qui compte est le temps compté `max(local, arrivée − min(rttTV, 400))` : pénalité ≈ montée − 400 ms (≈ 0,7 s médiane ⇒ ≈ 18 points sur 1 000 ; ≈ 2,3 s au pire ⇒ ≈ 58 points) et falaise en fin de fenêtre (H-1). Recommandation : **corriger** (H-1, M-6 ticket hors POST, M-7 groupage + 2 connexions chaudes, M-8, M-9) puis **relâcher** le critère à « ack p95 ≤ 3 s à 40 kbps » ; ne pas relever `MAX_RELAY_RTT_MS`. « Commencer » : enchaînement automatique vérifié (`QuizDuel.tick`, `ServerRoom.sync` : 6 s + 6 s puis ouverture à +1,5 s serveur ; `autohost` empêche seulement pause/abandon à la perte de l'hôte) ; aucun test de bout en bout ne joue sans `autoSkip`. Risques Android non couverts : `HttpURLConnection` + proxy SOCKS (même mécanisme que `HttpLite`, à confirmer sur la passerelle réelle, notamment la résolution DNS), lecture SSE 75 s, 5 connexions gardées par hôte (M-7), fils `play-writer`/`play-stream`/pool fermés par `close()` (OK), recréation d'activité pendant l'ouverture (M-5), focus : « Annuler » présélectionné à la sortie (OK), pas de piège D-pad trouvé à la lecture.

**(5) Parité production** : § 5 ci-dessous.

**(6) Mutations** : § 6 ci-dessous.

## 5. Parité production (« Ce que la production doit avoir », w20-05)

| Élément | État vérifié | Écart |
|---|---|---|
| `POST /api/v1/play/ticket` | code présent (`B/play/PlayTicketController.java`) ; refuse `app ≠ tv`, appareil bloqué ; 503 sans clé | présence en production **inconnue** (B-5) ; `app` auto-déclaré (H-3) |
| Clé privée `secrets/play-ticket.key` | `PlayTicketKey` : jamais journalisée, fichier secret, 503 si absente ; compose : déclarée, non montée | à monter : `chown 10001:10001`, `chmod 400` (REQUIREMENTS) — w20-05 dit `0400` sans le propriétaire |
| `CASTBRIDGE_PLAY_TICKET_PER_DEVICE_PER_HOUR` | dans `application.yml` | le plafond **par adresse** (120) n'est pas réglable (M-6) |
| `CASTBRIDGE_PLAY_TICKET_PUBKEY` | lu, SPKI ou brut | OK |
| `CASTBRIDGE_PLAY_TRUSTED_KEYS` | lu | **H-5** |
| `CASTBRIDGE_PLAY_WEB=0` | défaut ; `1` refusé sans `DIRECT` | OK |
| `CASTBRIDGE_PLAY_REVOCATIONS` | `off` ⇒ santé `disabled`, ≤ 20 salles ; la liste est quand même appliquée si une URL est posée et répond | voir bascule ci-dessous |
| `CASTBRIDGE_PLAY_MAX_RELAYED_PER_TV=8` | défaut, borné 1..8 | OK |
| `CASTBRIDGE_PLAY_TRUSTED_PROXIES` | obligatoire, refusé avec `DIRECT` | docs proposent le sous-réseau, REQUIREMENTS le /32 : choisir le /32 |
| nginx `/play/` | `docs/PLAY-OPS.md` § 4.4 : `proxy_http_version 1.1`, `proxy_buffering off`, `proxy_read_timeout 75s`, `^~ /play/` couvre `/.well-known/caps`, santé réservée à 127.0.0.1, `X-Forwarded-For $remote_addr` | M-7 (`limit_conn`), H-4 (IPv6), B-7 (vérifications) ; le SSE envoie aussi `X-Accel-Buffering: no` (OK) |
| même domaine que `DEFAULT_SERVER` | `PlayHub` bâtit `…/play/*` depuis `link.state.baseUrl` | OK |
| Horloges | tickets : `iat`/`exp` API et service sur le même hôte (dérive tolérée 60 s) ; temps de jeu : horloge serveur seule ; TV : `clockSuspended` ferme la tuile ; activations « futures » > 24 h refusées | NTP sur l'hôte obligatoire ; rien ne dépend de l'heure murale de la TV pour les points |

**Révocations `off` : rayon d'action.** Une TV révoquée, remboursée ou transférée (poste révoqué) continue de créer et rejoindre ; une clé d'émetteur révoquée (fuite) reste acceptée : son détenteur fabrique autant d'« identités de TV » qu'il veut (facteurs inventés, code assorti), chacune avec ses quotas ; avec la limite POC de 20 salles, 10 activations copiées (2 salles chacune) remplissent le service. **Bascule exacte vers la production** (dans cet ordre, hors partie) :
1. Choisir la source : module des licences actif (`CASTBRIDGE_LICENSES_ENABLED=true`, `license-signing.key` monté) **ou** liste signée statique ré-émise par l'outil de bureau (clé de portée `REVOKE`) toutes les **12 h** (le service refuse à 24 h) et servie en https par nginx.
2. `.env.play` : retirer `CASTBRIDGE_PLAY_REVOCATIONS=off` ; `CASTBRIDGE_PLAY_REVOCATIONS_URL=http://castbridge-api:8080/api/v1/revocations` (réseau interne, liste signée) ou l'adresse https de la liste statique ; `CASTBRIDGE_PLAY_REVOCATIONS_FILE=/var/lib/castbridge-play/revocations.txt` (volume `play-state`) ; la clé `REVOKE` présente dans `CASTBRIDGE_PLAY_TRUSTED_KEYS` ; retirer `CASTBRIDGE_PLAY_MAX_ROOMS=20`.
3. Vérifier `/play/health` (127.0.0.1:7091) : `"revocations":"ok"` (jamais `none`, `stale`, `disabled`) ; alerte si `stale`.

## 6. Mutations que les suites existantes laissent passer

Méthode : mutations appliquées **ensemble** dans la copie hors dépôt, suites `castbridge.play.*` (235) et `castbridge.core.quiz.online.*` (241) relancées. Passe 1 (A-F) : core 241/241 verts, server-play 1 échec (`PingPongTest`, tue F seulement). Passe 2 (A-E + G) : 3 échecs dans `FallbackTransportTest` (tue G). Passe 3 (A-E + H) : **235/235 verts**. Les six mutations ci-dessous survivent donc aux suites existantes (deux mutations tuées, F et G, montrent que la méthode détecte bien).

| # | Mutation (survit) | Fichier | Conséquence réelle non testée | Test à ajouter |
|---|---|---|---|---|
| A | le ticket n'est **jamais** renouvelé (`if (false && refreshTicket …)`) | `C/PlayHttpTransport.kt:153` | à 10 min de partie, tous les POST de la TV ⇒ 403 : partie perdue | horloge injectée : POST à t = 9 min porte un ticket neuf |
| B | recontrôle d'essai **sous verrou** supprimé | `SP/RoomRegistry.kt:262` | deux entrées simultanées d'une TV d'essai à 2/3 donnent 4 parties | deux `tvJoin` concurrents sur une identité à 2 parties |
| C | la reprise ne transmet plus l'identité (`joinedTokens` ⇒ `joined`) | `SP/RoomRegistry.kt:299` | après une reprise, le plafond de connexions vivantes par identité ne compte plus la TV : une activation copiée entre ailleurs | entrée, coupure, `resume`, puis 2e entrée de la même identité ⇒ refus |
| D | spectateur relais purgé **même s'il porte des sièges** | `C/ServerRoom.kt:322` | TV invitée coupée > 5 min : ses téléphones perdent leur TV, la reprise échoue | horloge de salle + 5 min : `resume` de la TV relais ⇒ `welcome` |
| E | un envoi d'**établissement** est rejoué même s'il a atteint le service | `C/PlayHttpTransport.kt:124` | en EDGE (réponse perdue), `hello`/`create` rejoués : salles en double, quota d'essai consommé deux fois | transport factice : 1er POST livré puis `SocketTimeoutException` ⇒ un seul envoi |
| H | `ownRoom` toujours faux | `SP/RoomRegistry.kt:254` | une TV d'essai ne peut plus regarder sa propre salle ; jamais testé | `tvJoin` d'une identité dans sa propre salle avec `cap = 1` ⇒ `welcome` |

## Vérifié OK (lecture et tests existants verts)

- Ordre fermé de `tvJoin` ; même réponse code vivant / code faux sans ticket ; aucun compteur ni rotation atteignable sans ticket.
- `Origin` ⇒ 403 sur les 4 routes de jeu avant tout ; `/play` = page d'information sans script ; `play.js`/`play.css` : 404.
- `WEB=1` sans `DIRECT` refusé ; `DIRECT` + `TRUSTED_PROXIES` refusé ; `off` + `MAX_ROOMS > 20` refusé ; `revocationsReady` jamais implicite.
- Vérification d'activation (clé, signature, portée, sujet, droits d'essai, futur, usage échu) ; `jti` unique commun ; consommation sous verrou.
- `TicketVerifier` : séparation de domaine, `aud`, vie ≤ 15 min, base64url strict, `blocked` exigé faux.
- Clé de ticket jamais journalisée ; privée seulement dans l'API.
- `relayedBy`, ≤ 8 sièges relayés par TV des deux côtés, table ≤ 8.
- Coalescence « dernier état » correcte (WebSocket non audité ligne à ligne : la TV ne l'utilise pas).
- Enchaînement automatique des questions avec 1,5 s (sauf la 1re, M-9).
- TLS : aucune confiance ajoutée, certificat invalide ⇒ fatal sans contournement.
- `create`/`join` sous 2 048 pour tous les profils courants mesurés.

## Liste avant production (précise)

1. C-1 corrigé (nom de spectateur, admission avant consommation) + test du câblage réel `Intent.Join(code, null)` contre le vrai service.
2. H-1 (`maxGrace` inclut les relais), H-2 (attribution des `welcome`/`error`), M-9 (délai à la question 1) + leurs tests.
3. H-4 : un long-poll par session, compte de sockets par adresse, `limit_conn` IPv6 par /64.
4. H-5 : `CASTBRIDGE_PLAY_TRUSTED_KEYS` = liste des builds TV avec leurs portées + clé serveur `REVOKE` ; essai à blanc la veille sur chaque TV de démonstration (création **et** entrée).
5. M-1 : plafond par type (8 192) dans `MessageSchema`, `MAX_BODY` relevé, doc `PLAY-PROTOCOL` alignée, contrôle de taille côté TV.
6. M-6/M-7/M-8 : ticket jugé à la création de session seulement, ticket réutilisé aux réouvertures, `MAX_PARALLEL = 2` + envoi groupé, `pong` hors du fil bloquant, plafond par adresse de l'API réglable.
7. M-2/M-3/M-4/M-5 : `end` à la sortie de l'hôte, compte des salles vivantes, décision sur « Nouvelle partie » en essai, filtre des clés courtes, génération dans `PlayHub.start`.
8. H-3 : décision du propriétaire et plan (épinglage de la clé d'installation) ; au minimum 1 appareil API par code, lien persistant.
9. Révocations : bascule du § 5 avant tout client hors démonstration.
10. Exploitation : B-5 (route de ticket en production), clé de ticket montée (`10001:10001`, `400`), nginx § 4.4 avec M-7 et H-4, vérifications § 4.4 réécrites pour `WEB=0`, `TRUSTED_PROXIES` en /32, NTP de l'hôte, `REVOCATIONS=off` jamais au-delà du POC.
11. Relevés matériels : H-PLAY-BT (débit, RTT, coupure), SSE 75 s sur GaiaOS, passerelle SOCKS (DNS), relance de la mesure d'ack après 6.
