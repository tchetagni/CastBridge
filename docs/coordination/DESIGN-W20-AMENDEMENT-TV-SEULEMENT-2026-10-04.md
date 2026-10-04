# Amendement W20 — Seule une TV activée et connectée à Internet joue en ligne ; les téléphones sont des joueurs locaux relayés par leur TV ; aucune page web de jeu ; POC « TV à TV avec relais » d'abord

> Document de conception (architecte, 2026-10-04). **Aucun code n'est modifié par ce document.** Il amende `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` (ci-dessous « DESIGN-W20 ») ; là où ils divergent, **celui-ci l'emporte**. Branche de référence `integration/agents`, HEAD `774afb31`. Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `SP/` = `server-play/src/main/kotlin/castbridge/play/`, `B/` = `backend/src/main/java/castbridge/server/`. Les fichiers cités ont été lus le 2026-10-04 (liste en § 11) ; rien n'a été exécuté, aucun réseau, aucun appareil. Les chiffres non mesurés sont marqués « estimé ».

## Décisions du propriétaire (verbatim, 2026-10-04, dans l'ordre)

1. « Les parties libres sont exclusivement sur la TV, rien ne se fait en dehors de la TV ou d'une appli phone synchronisée à une TV activée ».
2. « Je cherche juste à avoir un POC partie en ligne pendant le lancement ».
3. « Après le lancement, seules les TV pourront toujours faire les quiz et les téléphones synchronisés, car un téléphone synchronisé, par internet = TV à laquelle il est connecté ».
4. « Pas de délégation en phase 2. Seule une TV connectée à internet joue. Le pire des modes de connexion est la passerelle bluetooth ».
5. « La liaison passerelle se fera au pire en EDGE, sinon 3G, 4G ou 5G ».
6. (rapportée par le coordinateur) Le débit de référence de la liaison TV ↔ service est **fixé à 40 kbps (≈ 5 Ko/s)** : plancher à garantir pour un jeu fluide en ligne, y compris par la passerelle Bluetooth.

(Le propriétaire a aussi supprimé, le même jour, une étude « bas débit » séparée : aucune référence n'y est faite ici ; aucun profil « bas débit » n'est prévu.)

## Lecture appliquée (stricte)

- **I-1 Un seul client du service** : le service `castbridge-play` n'a **qu'un** genre de client : une **CastBridge-TV activée, connectée à Internet**, qui ouvre **une** connexion sortante et se présente avec son ticket `cbp1` (déjà construit) et son activation `cbx1` (déjà vérifiée par `HostRightsEvaluator`). Le service ne connaît que des **identités de TV** (code d'appareil signé de l'activation).
- **I-2 Téléphones = joueurs locaux** : un téléphone « synchronisé » est un **joueur local de sa TV** (page `/quiz` de la TV, comme aujourd'hui), relayé par elle (`relayJoin`/`relayAct`, déjà dans le protocole). **Aucun téléphone ne se connecte jamais au service de jeu**, ni en phase 1 ni en phase 2. Pas de voucher, pas de délégation, pas d'identité de téléphone, pas de ticket de téléphone.
- **I-3 Joueurs distants = TV** : une partie Internet réunit des **TV** : l'une crée la salle, les autres la rejoignent par le code, chacune avec **son propre** ticket et sa propre activation. Le « join libre par code » d'aujourd'hui est **fermé** derrière un drapeau désactivé par défaut.
- **I-4 Aucune page web de jeu, jamais.** `/play` ne garde qu'un rôle d'information statique.
- **I-5 « Connectée à Internet » inclut la passerelle Bluetooth du téléphone** (Internet du téléphone partagé à la TV par Bluetooth, `R/BtGatewayHost.kt`) : c'est le **pire** mode de connexion, pris comme **liaison de référence la plus lente** de toute la conception (débit, latence, coupures, reconnexion). Son débit réel **n'est pas mesuré** : c'est une mesure à faire pour le POC (§ 2.7).
- **« Parties libres »** = parties **sans paiement** (banque libre, aucune location). Permises à toute TV activée, **essai compris**, dans les règles d'essai déjà construites (w20-04 : 3 parties par jour, questions libres, salles privées, 8 joueurs).

| # | Ambiguïté | Lecture retenue | Si le propriétaire lit autrement |
|---|---|---|---|
| A-1 | « parties libres » = gratuites, ou = publiques ? | **gratuites** ; les salons publics (w20-09) restent possibles entre TV de **production** ; l'essai reste en privé | si « libres » = publiques, l'essai obtiendrait le public : contraire à w20-04, à redemander |
| A-2 | « synchronisé » | le téléphone est **en ce moment** joueur local de sa TV (Wi-Fi commun, Wi-Fi Direct, Bluetooth) ; un téléphone loin de sa TV ne joue pas en ligne | — (la décision 4 ferme toute autre voie) |
| A-3 | « TV activée » | production, grâce, essai : **oui** ; suspendue, verrouillée, révoquée, horloge douteuse : **non** (règles `HostRightsEvaluator` existantes) | — |
| A-4 | La passerelle Bluetooth est-elle **admise** ou seulement « la pire » ? | **admise** (« une TV connectée à Internet ») et **dimensionnante** : tout seuil du jeu en ligne doit tenir sur elle | si elle était exclue, la majorité des foyers sans box ne jouerait pas (DESIGN-W20 risque 10) |
| A-5 | Une TV sans Internet propre mais dont le téléphone partage le Wi-Fi (point d'accès) | c'est une TV en Wi-Fi ordinaire **avec** Internet : meilleure que la passerelle Bluetooth | — |

## 0. En vingt lignes

1. **Aujourd'hui** (vérifié, `SP/RoomRegistry.kt:201-209`) : créer une salle exige ticket + activation ; **rejoindre est libre** (code + pseudonyme), depuis la page web `/play` (`SP/PlayPageController.kt`) ou tout client : contraire à I-1…I-4.
2. **Changement de fond** : drapeau de service `CASTBRIDGE_PLAY_WEB`, **0 par défaut**, interdit à 1 hors staging ; à 0 : `join` d'une connexion non assise exige **ticket + activation de TV** (même chaîne que `create`), toute requête de navigateur (en-tête `Origin`) est refusée, `/play` sert une page d'information sans script.
3. **Phase 1 = POC (priorité absolue)** : « **TV à TV avec relais** ». TV A crée, TV B rejoint avec le code ; les téléphones de chaque foyer restent sur la page `/quiz` de **leur** TV, qui les relaie. Aucun téléphone ne parle à Internet : la règle est tenue **par construction**.
4. **Ce que le relais exige encore** (vérifié) : (a) `join` authentifié (service) ; (b) **seul l'hôte** peut relayer aujourd'hui (`C/quiz/online/ServerRoom.kt:249, 393`) et un siège relayé n'est rattaché à aucune TV (`:40`) : il faut un **siège relais** pour la TV invitée et `relayedBy` ; (c) le serveur n'envoie la vue qu'aux connexions (`:524`) : la TV compose la vue de ses téléphones (`RelayAuthority`, cœur pur) ; `QuizHttp` est lié à `QuizRoom` (`C/quiz/QuizHttp.kt:23`) : surcharge additive vers une autorité ; (d) **pas d'OkHttp** sur la TV : transport **SSE + POST** sur `HttpURLConnection`, avec le proxy SOCKS de la passerelle Bluetooth quand elle sert (`C/connect/Routes.kt`, `BtGatewayHost.proxy()`), dans le cœur, testé contre le vrai service en boucle locale.
5. **Côté service, presque tout existe** : relais, équité relayée (`RttBook.relayed`), repli SSE/POST avec secret en en-tête `X-Play-Conn` pour les clients natifs, tickets à usage unique, plafonds par identité, plafonds d'adresse partagée (une TV sur passerelle Bluetooth sort par l'adresse CGNAT de l'opérateur du téléphone).
6. **POC en deux marches** : **P1a « TV contre TV »** (chaque TV joue à la télécommande : aucune modification de `ServerRoom`) ; **P1b « + téléphones relayés »** (siège relais, `RelayAuthority`). La démonstration (2 TV, 2 téléphones) demande P1b.
7. **Cahiers du POC** : **w20-04b** service (join réservé aux TV, drapeau web, siège relais, révocations explicites, coalescence « dernier état ») — sonnet M, audit Opus **obligatoire** ; **w20-05a** cœur client TV (transport, session, `RelayAuthority`, cause locale « liaison de cette TV », simulateur EDGE 40 kbps) — sonnet M, audit échantillon ; **w20-05 réduit « POC »** (écrans TV minimum derrière un drapeau éteint) — sonnet M, audit Opus **obligatoire**. ≈ **4,5 agent·jours, ≈ 6 $** (estimé). **w20-14 (délégation) : RETIRÉ** par la décision 4.
8. **Liaison de référence = EDGE par la passerelle Bluetooth, plancher 40 kbps (≈ 5 Ko/s), RTT 0,5-1 s (parfois 2 s et plus), coupures aux changements de cellule.** Ce qui compte : la **latence** et le **nombre d'allers-retours** (connexion persistante, ouverture ≈ 4-6 RTT une seule fois, reprise par `resume` de 192 o). **Trouvé en chiffrant le protocole actuel** : le service envoie un `state` complet (≈ 5 Ko estimé à 8 joueurs) à **chaque** connexion **à chaque** réponse : une rafale de 8 réponses ≈ 40 Ko ≈ **8 s** de file à 40 kbps, devant la révélation : le critère « révélation ≤ 2 s » échoue. Parade **petite et suffisante** (aucun profil bas débit) : **coalescence « dernier état seulement »** dans la file de sortie de chaque connexion (w20-04b). Le délai entre questions de 1,5 s **suffit** à EDGE typique (annonce reçue ≈ 0,3-0,6 s après l'envoi) ; délai adaptatif seulement en phase 2, sur mesure (§ 2.7). La TV sur passerelle est **invitée**, pas hôte ; le signe la montre **orange** (dégradée, pas en panne).
9. **Bloquants réels du premier vrai test** : exception de gel des écrans TV (drapeau `quiz.online` éteint par défaut, modèle `StoreFlag` W17) ; clé de ticket de production ; route `POST /api/v1/play/ticket` **en production ?** (inconnu) ; route nginx `/play/` ; révocations (`GET /api/v1/revocations` = 503 tant que le module des licences est éteint) ; clé publique de l'émetteur des activations des TV de démonstration ; `TrialPolicy` **ferme encore le Quiz en essai** (`C/owner/TrialPolicy.kt:19`).
10. **Trou trouvé** : `CASTBRIDGE_PLAY_DIRECT=1` **avec** des proxys de confiance dispense de la liste de révocations et rend `revocationsReady` toujours vrai (`SP/PlayConfig.kt:131`, `SP/PlayServer.kt:50`) : w20-04b le ferme.
11. **Phase 2 = produit, 100 % relais, sans délégation** : repli en partie locale à 60 s, « Fermer Internet », coupure à la révocation, salons publics entre TV de production, classements **par identité de TV + siège**, modération par TV, passerelle Bluetooth éprouvée.
12. **Ce qui ne change pas** : format et clé du ticket, `HostRights`, `PlayRules`, réservées, anti-triche par siège, plafonds CGNAT, pseudonymes modérés, `NoAnswerLeak`, journaux. **Ce qui change de sens** : compteurs de codes faux (seul un appelant authentifié atteint la recherche de code), « joueurs distants » (= sièges d'**autres TV**), classement « par appareil » (= par identité de TV + siège).
13. **Aucun mode de test ouvert** n'est nécessaire : une TV d'essai crée déjà des salles privées gratuites.

## 1. Le principe d'identité, noir sur blanc (phases 1 et 2)

| Acteur | Identité côté service | Preuve | Peut | Ne peut jamais |
|---|---|---|---|---|
| CastBridge-TV activée, connectée à Internet (box, point d'accès Wi-Fi, ou passerelle Bluetooth du téléphone) | **identité de TV** = code d'appareil de l'activation (`HostRights.identity`) + appareil API du ticket (`subject`) | ticket `cbp1` + activation `cbx1`, à **chaque** `create` **et** chaque `join` | créer (`PlayRules`), rejoindre, relayer **ses** téléphones | relayer les téléphones d'une autre TV, agir comme hôte d'une salle qu'elle n'a pas créée |
| Téléphone synchronisé (joueur local de sa TV) | **aucune** : siège relayé `viaTv`, `relayedBy` = siège de SA TV | aucune vers le service ; admis par sa TV localement (PIN/jeton, page `/quiz`) | répondre, voter, signaler **par sa TV** | se connecter au service ; créer ; rejoindre seul |
| Navigateur, page web, téléphone seul, client inconnu | **aucune** | — | **rien** : refusé avant toute recherche de salle | — |

Conséquences : (1) tout plafond « par appareil » du service se lit **par identité de TV** ; les sièges relayés sont comptés sur leur TV (≤ 8 par TV, et ≤ 8 par table en V1) ; (2) une TV révoquée ou dont l'activation échoue ne crée plus, ne rejoint plus, et ses sièges relayés partent avec elle ; (3) un téléphone qui n'est plus joueur local d'une TV activée n'a aucun accès ; (4) l'API principale **n'émet jamais** de ticket à un téléphone.

## 2. Phase 1 — le POC « TV à TV avec relais »

### 2.1 Ce qu'on démontre

```
  Foyer A (box Wi-Fi) — HÔTE                                Foyer B (sans box) — INVITÉ, liaison de référence
  ┌──────────────────────────────┐                          ┌──────────────────────────────────────┐
  │ CastBridge-TV A (activée)    │                          │ CastBridge-TV B (activée)            │
  │  « Créer » ⇒ K7M2-QX4T        │                          │  « Rejoindre » ⇒ K7M2-QX4T            │
  │  /quiz local ◄─── tél. A1    │  (Wi-Fi de la box)        │  /quiz local ◄─── tél. B1 (W18 Wi-Fi   │
  │  RelayAuthority              │                          │  RelayAuthority     Direct, ou Wi-Fi) │
  └──────────┬───────────────────┘                          │  Internet : passerelle Bluetooth du   │
             │                                              │  téléphone G (SOCKS 127.0.0.1:1080)   │
             │ HTTPS sortant (SSE + POST), cbp1 + cbx1       └──────────┬───────────────────────────┘
             ▼                                                         ▼ HTTPS sortant par la passerelle, cbp1 + cbx1
        ┌──────────────── nginx 443 /play/ ─────► castbridge-play (127.0.0.1:7091) ─────────────────┐
        │  ServerRoom autoritaire : TV A = hôte + relais (A1) ; TV B = siège relais (B1)              │
        │  CASTBRIDGE_PLAY_WEB=0 : aucun navigateur ; join sans ticket + activation de TV : refusé    │
        └─────────────────────────────────────────────────────────────────────────────────────────────┘
  Aucun téléphone ne parle au service. Le téléphone G ne fait que transporter des octets TLS de la TV B.
```

Variante de secours (si la mesure § 2.7 échoue) : TV B sur le **point d'accès Wi-Fi** d'un téléphone (TV en Wi-Fi ordinaire avec Internet ; B1 rejoint ce même Wi-Fi).

### 2.2 Ce qui existe (vérifié) et ce qui manque

| Besoin | État (fichier:ligne) | Manque |
|---|---|---|
| TV hôte crée avec ticket + activation | **fait** : `RoomRegistry.create` (`SP/RoomRegistry.kt:142-195`), `HostRightsEvaluator` (`SP/entitlement/HostRights.kt:43`) | — |
| TV invitée authentifiée | **absent** : `join` sans ticket (`RoomRegistry.kt:201-209`) ; `ClientMsg.Join` sans champ `activation` (`docs/PLAY-PROTOCOL.md`) | `join` exige ticket + activation quand `CASTBRIDGE_PLAY_WEB=0` ; champ additif `activation` sur `join` (≤ 16 Ko comme `create`) |
| Relais par l'hôte | **fait** : `relayJoin` (`ServerRoom.kt:280`), `relay` (`:391`), `RttBook.relayed` | — |
| Relais par une TV invitée | **absent** : `sender === host` exigé (`:249`, `:393`) ; déconnexion : seuls les relayés de l'hôte (`:194`) ; `hostWatch` et `safety()` traitent **tout** `viaTv` comme « derrière l'hôte » (`:460`, `:578`) ; `setScope(false)` ne touche pas les `viaTv` (`:434`) | siège relais accordé par le service ; `Seat.relayedBy` ; `relay` limité à **ses** sièges ; présence, pause, signe, fermeture d'Internet raisonnent par `relayedBy` |
| Vue des téléphones relayés | **absent** : `state` aux seules connexions (`:524`) | `RelayAuthority` (cœur) compose la vue de chaque téléphone à partir de `question`, `reveal`, `ack` et de la vue du siège de la TV |
| Page `/quiz` des téléphones pendant une partie Internet | `QuizHttp(room: () -> QuizRoom?)` (`C/quiz/QuizHttp.kt:23`) | surcharge additive sur `() -> GameAuthority?` |
| Transport sortant de la TV | **absent** (pas d'OkHttp, `android/receiver/build.gradle.kts`) ; `PlayTransport` = interface (`C/quiz/online/PlayTransport.kt:4`) ; chemins réseau `Routes` (direct, puis passerelle SOCKS `127.0.0.1:1080`) | `PlayHttpTransport` : SSE `GET /play/events` + `POST /play/act` (en-têtes `X-Play-Ticket`, `X-Play-Conn`), long-poll `/play/state` en secours, `Proxy` fourni par `Routes` |
| Rendu de la partie sur la TV | `QuizActivity` lit une **vue** : `state = r.view(null)` (`R/QuizActivity.kt:111`) | brancher `ServerAuthority` ; écrans Créer / Rejoindre / Code ; bandeau |
| Ticket côté TV | `DeviceClient` porte le `deviceToken` ; route API faite (`B/play/PlayTicketController.java:33`) | `PlayTicketClient` par `Routes` |
| Signe pour la passerelle | `SafetyFacts` n'a pas de fait « passerelle » (`C/quiz/online/SafetySign.kt:12-20`) ; seuil « réseau lent » 1 500 ms (`:43`) | cause **locale** de la TV, qui ne peut que **baisser** le niveau reçu (§ 2.7) |
| Quiz en essai | **fermé** : `TrialPolicy.GAMES = setOf("sudoku")` (`C/owner/TrialPolicy.kt:19`) | POC sur TV de production, ou D-W20-5 appliquée (hors chemin) |

### 2.3 Côté service — w20-04b (liste exacte pour l'exécutant)

| Fichier | Changement |
|---|---|
| `SP/PlayConfig.kt` | `webPlay: Boolean = false` (`CASTBRIDGE_PLAY_WEB`, « 1 » accepté **seulement** si `CASTBRIDGE_PLAY_DIRECT=1`, sinon démarrage refusé) ; `maxRelayedPerTv: Int = 8` (`CASTBRIDGE_PLAY_MAX_RELAYED_PER_TV`, 1..8) ; `revocationsMode` (`CASTBRIDGE_PLAY_REVOCATIONS` = `on` défaut / `off`) ; **`DIRECT=1` refusé si `TRUSTED_PROXIES` est posé** (ferme le trou du point 10) ; `off` refusé si `CASTBRIDGE_PLAY_MAX_ROOMS` > 20 ; les trois noms ajoutés à `ENV_NAMES` |
| `SP/PlayServer.kt` | `revocationsReady = { cfg.revocationsMode == OFF \|\| feed.usable() }` (plus de « URL absente ⇒ prêt ») ; routage : si `!webPlay`, tout `/play/ws`, `/play/act`, `/play/events`, `/play/state` portant un en-tête `Origin` ⇒ 403 avant tout ; pages via `PlayPageController(webPlay)` |
| `SP/PlayPageController.kt` | si `!webPlay` : `/play`, `/play/j/<code>` ⇒ `static/play/info.html` (200, sans script, sans formulaire, CSP `default-src 'none'; style-src 'self'`) ; `play.js`, `play.css` ⇒ 404 |
| `SP/RoomRegistry.kt` (`PlayHub`) | `join` d'une connexion **non assise** quand `!webPlay` : ticket (`verifier.check`) ⇒ maintenance ⇒ `revocationsReady` ⇒ `used.seen(jti)` ⇒ `evaluator.evaluate(ticket.deviceCode, [join.activation])` ⇒ édition ≠ `NONE` ⇒ **puis seulement** la recherche du code (un inconnu n'atteint jamais les compteurs de codes ni la rotation) ⇒ compteur de codes faux **par identité** (30 / 5 min) en plus des compteurs d'adresse ⇒ essai : `trialDays.count(identity) < 3` (créations **et** entrées dans le même compteur) ⇒ connexions vivantes par identité ≤ `maxRoomsPerSubject` (essai : 1) ⇒ consommation (`used.use`, compteur d'essai) ⇒ `attachAndForward` ⇒ si `welcome` : `room.grantRelay(c.id)` ; `create` : **inchangé** ; `RoomEntry` mémorise les identités présentes ; `webPlay=true` : comportement d'aujourd'hui (tests historiques) |
| `C/quiz/online/PlayProtocol.kt`, `PlayCodec.kt` | `ClientMsg.Join.activation: String?` (additif) ; un `join` qui porte `activation` peut atteindre `MAX_CREATE_BYTES` (16 384), sinon 2 048 comme aujourd'hui |
| `C/quiz/online/ServerRoom.kt` | `Seat.relayer: Boolean`, `Seat.relayedBy: Seat?` ; `grantRelay(conn)` (pure, appelée par le service seul) ; `relayJoin` accepté de l'hôte **ou** d'un siège relais, plafonné à `settings.maxRelayedPerTv` par TV ; `relay` : `FORBIDDEN` si `s.relayedBy !== sender` ; `disconnect`/`reattach` : présence des seuls sièges de **cette** TV ; spectateur relais non purgé tant qu'il porte des sièges ; `hostWatch` : « derrière l'hôte » = `relayedBy === host` ; `safety()` : `localPlayers` = relayés par l'hôte, `remotePlayers` = tous les autres joueurs ; `setScope(false)` : les sièges relayés par une autre TV sont traités comme distants ; `kick` d'une TV relais retire ses sièges relayés ; `Settings.maxRelayedPerTv = 8` |
| `SP/PlayFallbackController.kt` (`FbConn.offer`), `SP/PlayWebSocketHandler.kt` (`WsConn.offer`) | **coalescence « dernier état seulement »** : un message `state` mis en file **remplace** le `state` encore non envoyé de cette connexion (les `question`, `reveal`, `ack`, `ping`, `error`, `roomGone`, `replay` ne sont jamais retirés ni réordonnés) ; la vue est complète, aucun client ne perd d'information ; borne la file à ≤ 1 état en attente (§ 2.7) |
| `server-play/src/main/resources/static/play/info.html` | neuf (≤ 4 Ko) : « Les parties en ligne se jouent sur CastBridge-TV. Ouvrez le Quiz sur votre TV ; vos téléphones jouent par votre TV. » |
| tests | fixtures existantes (`HubFixture`, `Wires`, `FallbackWires`, `PlayLoopbackTest`…) passées à `webPlay = true` **explicitement** ; nouveaux tests § 6 |

### 2.4 Essai, enfants, révocation dans le POC

- **TV d'essai invitée** : rejoint une salle privée (toutes le sont en V1) ; chaque **entrée** compte comme une partie dans le compteur d'essai de son identité (3 / jour, créations et entrées confondues) ; ses téléphones relayés ne comptent pas en plus.
- **Enfants** : la TV applique `PlayRules.internetForProfile` (déjà écrit) **avant** de demander un ticket ; profil enfant actif ⇒ tuile Internet noire avec texte. Le service ne reçoit aucune déclaration venant d'un téléphone.
- **Révocation** : une TV révoquée ne crée plus ni ne rejoint plus (prochain ticket ou prochaine évaluation). Le POC ne coupe pas une salle en cours (comme w20-04) ; la phase 2 ajoute la coupure.

### 2.5 Côté cœur client TV — w20-05a

`C/quiz/online/PlayHttpTransport.kt` (SSE + POST, `Proxy` injecté, garde-vivant HTTP, reprise par `resume` sur coupure, long-poll en secours, aucune confiance TLS ajoutée) ; `C/quiz/online/PlayTvSession.kt` (automate : ticket fourni ⇒ `hello` ⇒ `create` ou `join` avec activation ⇒ `ServerAuthority` ; reconnexion 0, 2, 4, 8, 15, 30 s ; perte ≥ 60 s ⇒ « Internet perdu ») ; `C/quiz/online/RelayAuthority.kt` (`GameAuthority` des téléphones locaux : `join` ⇒ `relayJoin`, `answer` ⇒ `relayAct` avec `localElapsedMono`, `view(token)` composée au format de `QuizRoom.view`) ; `C/quiz/online/LinkCause.kt` (cause locale « liaison de cette TV », § 2.7) ; `QuizHttp` : constructeur additif `() -> GameAuthority?`.

### 2.6 Côté écrans TV — w20-05 réduit « POC » (derrière `quiz.online`, éteint par défaut en release)

(1) tuile « Partie Internet » dans le Quiz (visible si drapeau + TV activée + Internet + profil adulte ; sinon la raison) ; (2) « Créer » (la télécommande vaut la confirmation de D-W20-7) ⇒ code `XXXX-XXXX` en grand, **sans QR vers une page web** ; (3) « Rejoindre » ⇒ saisie du code au D-pad ; (4) bandeau `SafetyView` reçu, **abaissé** par la cause locale (§ 2.7), + « Ici : N joueurs » (compte local) ; (5) relais : `QuizHub` sert `/quiz` par `RelayAuthority` ; (6) perte ≥ 60 s ⇒ « Partie Internet perdue » et retour au menu Quiz (repli automatique en partie locale : phase 2) ; (7) `PlayTicketClient` par `Routes`. Hors POC : `/api/quiz/scope` depuis le téléphone, « Fermer Internet » en cours, reprise proposée.

### 2.7 La liaison de référence : EDGE par la passerelle Bluetooth, plancher 40 kbps

**Faits du dépôt** : la TV joint Internet par le téléphone via un proxy SOCKS5 local `127.0.0.1:1080` tant qu'un téléphone est attaché (`R/BtGatewayHost.kt:91-92`), second chemin de `Routes`, collant 10 min (`C/connect/Routes.kt`) ; mesure de débit **descendant** existante `GET /api/gateway/speed?bytes=` (`R/BtGatewayHost.kt:146`, route `R/TvService.kt:928`) ; latence par `TvNetDiag.probe(proxy)` (`generate_204`, `R/TvService.kt:501`) ; « ~100-300 ko/s » publié pour le lien Bluetooth lui-même (`docs/REMOTE-TUNNEL-BT.md:38`, non mesuré pour le jeu). **Faits du propriétaire** : au pire EDGE (sinon 3G/4G/5G) ; plancher garanti 40 kbps. **Ordres de grandeur EDGE** (fournis par le coordinateur, non mesurés ici) : descendant ≈ 60-100 kbps, montant ≈ 30-50 kbps, RTT 0,5-1 s (parfois 2 s et plus), gigue, coupures de quelques secondes aux changements de cellule. Le maillon lent est donc **la radio mobile du téléphone**, pas le Bluetooth.

**Budget de trafic d'une TV**, Duel de 10 questions, 8 joueurs relayés (2 TV × 4), protocole **actuel** (tailles : `state` 1 964 o mesurés à 2 joueurs, ≈ 5 Ko **estimé** à 8 ; `question` 373 o ; `relayAct` 230 o ; `ack` 42 o ; `ping` 50 o + `pong` 84 o toutes les 5 s ; `hello` 1 650 o ; en-têtes HTTP d'un POST ≈ 350 o, estimé) :

| Moment | Actuel (sans coalescence) | Avec coalescence « dernier état » (w20-04b) |
|---|---|---|
| Ouverture (une fois) : TLS API + ticket, TLS service, `hello`, `create`/`join` avec activation | ≈ 12-16 Ko (estimé, taille de l'activation non mesurée) et ≈ 4-6 RTT ⇒ **≈ 4-6 s à RTT 0,8 s** : acceptable une fois, **jamais** par évènement | idem |
| `state` par question (ouverture 1 + une par réponse 8 + révélation 1 + classement 1) | ≈ 11 × 5 Ko ≈ 55 Ko par question ≈ **18 kbps** en moyenne | ≈ 4-5 états délivrés ≈ 20-25 Ko ≈ **7-8 kbps** |
| Pointe : 8 réponses en ≈ 2 s | ≈ 40 Ko en file ⇒ **≈ 8 s** à 5 Ko/s, **devant** `reveal` et le classement | ≤ 1 état en file + celui en cours ⇒ **≤ 2 s** |
| Montant : 4 `relayAct` en rafale (corps + en-têtes) | ≈ 2,3 Ko ⇒ ≈ 0,5 s à 30-50 kbps | idem |
| Entretien (`ping`/`pong`) | ≈ 0,2 kbps | idem |

**Conclusion** : à 40 kbps, le protocole actuel tient en **moyenne** mais **pas en pointe** à 8 joueurs : sans parade, la révélation et le classement arrivent jusqu'à ≈ 8 s en retard sur une TV EDGE, et la file de sortie (64 Ko, `PlayConfig.outboxMaxBytes`) frôle la fermeture 1008 sur deux questions rapprochées. La **coalescence « dernier état seulement »** (une vue `state` est complète : la plus récente rend les précédentes inutiles) suffit ; elle est petite, générique et ne change pas le protocole. **Aucun profil bas débit n'est requis pour le POC.** Les vues différentielles (DESIGN-W20 § 2.3) restent une optimisation de phase 2, à décider sur mesure.

**Délai entre deux questions face à EDGE** : la question suivante est annoncée tout de suite, avec `opensAtServerMs` = annonce + 1,5 s (`PlayTiming.INTER_QUESTION_GAP_MS`, plage du propriétaire 1-2 s). Elle arrive sur la TV après un aller simple (≈ 0,25-0,5 s à RTT 0,5-1 s ; ≈ 1 s à RTT 2 s) plus la transmission de 373 o (≈ 75 ms à 5 Ko/s), **à condition qu'aucun état ne la précède en file** (d'où la coalescence) : ≈ 0,3-0,6 s typique, ≈ 1,1 s au pire courant : **avant** l'ouverture. Ce que fait le code pour une annonce tardive : `PlayTiming.start(opensAt, window, announcementServerNowMs)` démarre tout de suite si l'attente est négative et raccourcit la fenêtre du retard **mesuré par le serveur** ; mais `announcementServerNowMs` est l'heure serveur **à l'envoi**, donc `start` ne voit jamais le trajet ; la règle 11 de `docs/PLAY-PROTOCOL.md` demande au client de retrancher `rtt/2` : **w20-05a l'applique** (`start(opensAt, window, serverNow + rttTv/2)`). Effet sur les points d'un joueur relayé par une TV EDGE : son temps est celui de l'horloge de la TV (`localElapsedMono`) tant que `rttTV ≤ 400 ms` ; au-delà, `max(local, serveur − 400 ms)` lui retire ≈ `rttTV − 400 ms` (≈ 400 ms à RTT 0,8 s ≈ 10 points sur 1 000, estimé d'après DESIGN-W20 § 2.6) : borné, accepté ; relever `MAX_RELAY_RTT_MS` donnerait à une TV tricheuse autant de marge : **non**. La grâce de clôture (≤ 1 s) couvre le trajet retour. **Délai adaptatif : non nécessaire pour le POC.** Proposition de phase 2, **non implémentée**, seulement si la mesure montre des annonces tardives : `gap = clamp(max(1 500 ms, pire RTT mesuré de la salle + 500 ms), 1 000, 2 000)` (reste dans la plage 1-2 s du propriétaire) ; la forme `max(1,5 s, 2 × pire RTT)` dépasserait 2 s dès RTT > 1 s et demanderait l'accord du propriétaire.

| Sujet | Règle |
|---|---|
| Transport | **une** connexion persistante de flux (SSE) + POST sur une connexion HTTP **gardée vivante** ; jamais une poignée TLS par évènement ; reprise par `resume` (192 o) ; WebSocket (une seule connexion) en phase 2 si la mesure montre des POST lents |
| Signe « Partie sûre » | **dégradé ≠ panne** : passerelle active ⇒ **orange** « Internet par le téléphone (Bluetooth) · lent » (cause locale de la TV, qui ne peut que baisser le niveau reçu du serveur) ; RTT de la salle > 1 500 ms ⇒ orange « réseau lent » (serveur, existant) ; flux perdu < 60 s ⇒ orange « liaison en reprise (12 s) » ; **rouge** seulement à ≥ 60 s de perte (« Partie Internet perdue ») ou certificat invalide ; sans réseau hors partie ⇒ noir |
| Reprises (S-REPRISE) | changement de cellule (2-10 s) : la session SSE/POST du service survit (`fallbackIdleMs`) ; la TV rouvre le flux avec `X-Play-Conn` (TCP + TLS ≈ 3 RTT ≈ 2,5-3 s) ; session perdue ⇒ nouvelle session + `resume(roomId, token, lastSeq)` ; ses téléphones locaux ne voient que l'orange ; ≥ 60 s ⇒ retour au menu (POC) ; une TV **hôte** perdue ≥ 60 s abandonne la table : **dans le POC, l'hôte est la TV la mieux reliée** |
| Limites du service | **une TV = une connexion, quel que soit son lien** ; une TV EDGE sort par l'adresse CGNAT de l'opérateur, partagée : plafonds relevés d'adresse partagée (w20-07) inchangés ; délais d'inactivité compatibles (ping SSE du service, `proxy_read_timeout 75 s`) |

**Critère d'acceptation du jeu en ligne** (propriétaire, à tenir en phase 1 sur simulateur, en phase 2 sur liaison réelle) : **un Duel complet à 8 joueurs relayés est fluide à 40 kbps** : question affichée **≤ 1,5 s** après son envoi, révélation et classement **≤ 2 s**, `ack` d'une réponse **≤ 1,5 s**. Simulateur : `SlowSocksProxy` de w20-05a (bande passante bornée à 5 Ko/s dans chaque sens, RTT 800 ms ± 200 ms de gigue, une coupure de 5 s « changement de cellule ») ; mesure des octets par type de message à 8 joueurs (remplace l'estimation « ≈ 5 Ko »).

**Mesure à faire (relevé H-PLAY-BT, propriétaire)** : sur la TV B reliée par la passerelle d'un téléphone en EDGE (forcer « 2G seulement » dans les réglages du téléphone si possible), puis en 3G/4G : débit descendant `GET /api/gateway/speed?bytes=500000` (le montant n'a **pas** de mesure dans le dépôt : à relever par l'envoi d'une partie réelle), latence `generate_204` (médiane, 95e centile), une partie de 10 questions à 2 joueurs relayés par TV, une coupure volontaire de 15 s. **Seuils** : débit ≥ **40 kbps** ; RTT médian ≤ **1 000 ms**, 95e centile ≤ **2 000 ms** ; critère de fluidité ci-dessus tenu ; reprise après coupure ≤ **30 s**. Échec ⇒ démonstration en 3G/4G ou par point d'accès Wi-Fi, et la mesure EDGE part en phase 2.

### 2.8 Ce qui bloque le premier vrai test, et comment le lever au moins cher

| # | Bloquant | Nature | Le moins cher |
|---|---|---|---|
| B-1 | Gel des écrans TV (W15 R3/R5) | décision | **exception de gel derrière `quiz.online`**, défaut compilé faux en release, allumé sur les TV de démonstration par le réglage W12 ou un ordre signé `flag.set` ; précédent : `StoreFlag` (`C/store/StoreFlag.kt`, D-W17-10) ; builds **verrouillés** (`-PrequireActivation=true`) |
| B-2 | Révocations : 503 tant que le module des licences est éteint (PLAY-OPS § 4.0) ⇒ aucune salle | décision | POC : `CASTBRIDGE_PLAY_REVOCATIONS=off` (w20-04b) : avertissement au démarrage, `/play/health` dit `"revocations":"disabled"`, ≤ 20 salles ; **jamais** au lancement public ; lancement : module des licences actif **ou** liste signée statique ré-émise chaque jour |
| B-3 | `DIRECT=1` + proxys de confiance désactive la révocation fermée | fait | fermé par w20-04b |
| B-4 | Clé de ticket de production non générée, secret non monté | fait | `tools/play/gen-ticket-keypair.sh`, PLAY-OPS § 4.1 |
| B-5 | `POST /api/v1/play/ticket` est-il en production ? | **inconnu** | propriétaire : `curl -s -o /dev/null -w '%{http_code}\n' -X POST https://bridge.sti-cm.com/api/v1/play/ticket` : `404` = absente (déployer l'API d'une étiquette contenant w20-04, **jamais `main`**) ; `401`/`403`/`503` = présente (503 : clé absente) |
| B-6 | Route nginx `/play/` (O-1) | acte du propriétaire | PLAY-OPS § 4.4 (`proxy_buffering off`, `proxy_read_timeout 75s`) |
| B-7 | Clé de l'émetteur des activations des TV de démonstration dans `CASTBRIDGE_PLAY_TRUSTED_KEYS` | fait à confirmer | même contenu public que le fichier des builds TV (`~/.castbridge-signing/activation-trusted-keys.txt`, `android/receiver/build.gradle.kts:27`), ou `server:<publicKey>:…` si le serveur émet (PLAY-OPS § 4.0) |
| B-8 | Quiz fermé en essai (`TrialPolicy`) | décision (D-W20-5) | POC sur 2 TV de **production** ; D-W20-5 en parallèle |
| B-9 | Mesure de la passerelle Bluetooth | fait à relever | relevé H-PLAY-BT (§ 2.7), par le propriétaire, avec le relais téléphone → TV déjà en place |

Un POC sur la **vraie** API (route dédiée, clé dédiée au POC, remplaçable par `TICKET_PUBKEY_2`) est plus simple qu'une API de test publique (seconde API exposée, second certificat).

### 2.9 Cahiers du POC, ordonnés (minimum)

| Ordre | Cahier | Livre | Modèle | Jauge (k entrée / sortie) | Audit Opus | Gel | Dépend |
|---|---|---|---|---|---|---|---|
| 1 | **w20-04b** service : join réservé aux TV activées, `CASTBRIDGE_PLAY_WEB=0`, page d'information, siège relais multi-TV, révocations explicites, trou `DIRECT` fermé, coalescence « dernier état » | une salle où seules des TV activées entrent et relaient, prouvé sur JVM | sonnet | 400 / 20 | **obligatoire** | cœur + service (permis) | — |
| 1 bis (en parallèle) | **w20-05a** cœur client TV : `PlayHttpTransport`, `PlayTvSession`, `RelayAuthority`, `LinkCause`, `QuizHttp` sur autorité | deux « TV » JVM et leurs téléphones simulés jouent contre le vrai service en boucle locale, dont une derrière un simulateur de liaison EDGE (40 kbps, RTT 800 ± 200 ms, coupure de 5 s), critère de fluidité tenu | sonnet | 400 / 20 | échantillon | cœur (permis) | test final de bout en bout après w20-04b |
| 2 | **w20-05 réduit « POC »** : écrans TV minimum derrière `quiz.online` | ce que le propriétaire montre | sonnet | 400 / 20 | **obligatoire** | **exception de gel** (B-1) | 04b, 05a |
| 3 | exploitation (propriétaire) : B-4…B-7, image `castbridge-play`, 2 APK verrouillés avec drapeau, copie dans `Download` de la clé USB, relevé H-PLAY-BT | le service public, la mesure | — | — | — | — | 1, 2 |

Coût estimé (prix de l'index W20 : sonnet 2/10 $/M, opus 4/20 $/M ; **non vérifié**) : 3 × sonnet M ≈ 3,0 $ ; 2 audits Opus obligatoires ≈ 1,6 $ ; 1 échantillon ≈ 0,4 $ ; reprises 15 % ≈ 0,8 $ ⇒ **≈ 6 $**, ≈ **4,5 agent·jours** ; ≈ **5 jours ouvrés** si 04b et 05a partent ensemble et si B-1 est accordée. w20-06, 09, 10, 11, 13 ne sont **pas** sur ce chemin.

### 2.10 Risques de démonstration

| # | Risque | Parade |
|---|---|---|
| D-1 | TV sans Internet (85 % des foyers sans box) | c'est le cas de la TV B **par la passerelle Bluetooth** si H-PLAY-BT passe ; sinon point d'accès Wi-Fi ; le dire honnêtement |
| D-2 | La passerelle coupe en pleine partie (changement de cellule EDGE) | TV B **invitée** (jamais hôte) ; orange puis reprise ; ses joueurs ont 0 aux questions manquées, la partie continue pour les autres |
| D-3 | nginx tamponne le flux SSE ou coupe à 60 s | `proxy_buffering off`, `proxy_read_timeout 75s`, ping SSE ; long-poll en secours |
| D-4 | `REVOCATIONS=off` oublié après le POC | santé `disabled`, ≤ 20 salles, ligne dans HANDOFF |
| D-5 | Heure d'une TV fausse | vérifier avant ; message existant « Vérifiez l'heure de la TV » |
| D-6 | Activation non reconnue (B-7) | essai à blanc la veille : créer une salle depuis chaque TV |
| D-7 | Vue composée des téléphones différente de la page locale | test de contrat sur les clés lues par `play.html` local (w20-05a) ; P1a montre le jeu en ligne si P1b glisse |
| D-8 | Wi-Fi Direct (W18) indisponible sur la TV B | B1 joue en Wi-Fi par le point d'accès, ou la TV B joue à la télécommande (P1a) ; statut de W18 sur l'APK de démonstration **non vérifié** |

### 2.11 Démonstration en 10 minutes (2 TV, 2 téléphones, + le téléphone passerelle)

| t | Qui | Geste | Ce qu'on voit |
|---|---|---|---|
| veille | propriétaire | APK verrouillés, drapeau allumé, activations de production, heure juste ; TV A sur la box ; TV B reliée à Internet par la passerelle Bluetooth du téléphone G ; relevé H-PLAY-BT passé ; A1 apparié à TV A, B1 à TV B | — |
| 0:00 | TV A | Quiz ▸ « Partie Internet » ▸ « Créer » | « ◎ Internet · ● Partie sûre » ; `K7M2-QX4T` en grand ; « Ici : 0 » |
| 1:00 | A1 | app CastBridge ▸ Quiz (comme aujourd'hui) ▸ « Awa » | TV A : « Ici : 1 » ; A1 : page `/quiz` avec le même bandeau |
| 2:00 | TV B | Quiz ▸ « Partie Internet » ▸ « Rejoindre » ▸ `K7M2QX4T` (ouverture ≈ 4-6 s en EDGE, une seule fois) | TV B : bandeau **orange** « Internet par le téléphone (Bluetooth) · lent » (dégradé, pas en panne) ; TV A : « TV B a rejoint » |
| 3:00 | B1 | app ▸ Quiz ▸ « Koffi » | 2 joueurs sur les deux TV |
| 3:30 | TV A | « Commencer » (Duel, 10 questions) | « Question suivante dans 1,5 s » partout ; ouverture simultanée |
| 3:30-8:00 | A1, B1 | répondent | classement commun après chaque question ; aucune réponse avant clôture |
| 5:30 | (option) | éloigner G 15 s (coupure Bluetooth) puis revenir | TV B : orange « liaison en reprise » puis retour ; B1 : 0 à la question manquée ; la partie continue |
| 8:00 | — | fin | classement final identique sur les 4 écrans |
| 8:30 | preuve de la règle | navigateur ▸ `https://bridge.sti-cm.com/play` | page d'information ; aucune saisie de code |
| 9:00 | preuve de la règle | (exploitant) `/play/health` par 127.0.0.1:7091 | `rooms:1`, `revocations` dit honnêtement (`ok` ou `disabled`) |

## 3. Ce qui est désactivé (phases 1 et 2)

| Élément | Avant | Maintenant | Dormant ? |
|---|---|---|---|
| Page `/play`, `/play/j/<code>` jouable | servie | **page d'information** statique ; `play.js`/`play.css` : 404 | fichiers gardés (tests), non servis ; suppression = nettoyage ultérieur |
| `join` sans ticket de TV | permis | **refusé** avant toute recherche de salle | fermé par `CASTBRIDGE_PLAY_WEB=0` ; `1` seulement en staging (`DIRECT=1`) |
| Requêtes de navigateur (`Origin`) | permises si connues | **403** | `OriginCheck` gardé (tests `webPlay=true`) |
| SSE / POST / long-poll | navigateurs et natifs | **TV seulement** | — |
| Cookie par onglet `__Host-cbp-<nonce>` | navigateurs | inatteignable | **oui, dormant** |
| QR / lien `/play/j/<code>` sur la TV | prévu | **supprimé** : la TV montre le code seul | — |
| Lien d'application `castbridge://play`, App Links, `assetlinks.json`, WebView `/play` du téléphone | prévus (w20-06, PLAY-OPS § 8) | **annulés** | — |
| Case « 13 ans ou plus » | prévue | sans objet | — |
| Délégation / voucher de siège téléphone (`w20-14`) | proposé le 2026-10-04 | **non retenu** (décision 4) | fichier gardé pour mémoire |

## 4. Phase 2 — produit (après le POC) : 100 % relais, sans délégation

Le téléphone ne joue en ligne que **par sa TV** ; la TV porte la seule connexion au service, par son réseau ou par la passerelle Bluetooth du téléphone. Contenu : repli automatique en **nouvelle partie locale** à 60 s et « Reprendre » une fois (DESIGN-W20 § 1.5) ; « Ouvrir sur Internet » demandé par le téléphone avec confirmation sur la TV (D-W20-7, route locale `/api/quiz/scope`) ; « Fermer Internet » ; **coupure à la révocation** (relecture toutes les 15 min des identités présentes : la TV révoquée finit la question en cours puis sort, avec ses sièges) ; salons publics entre TV de **production** (w20-09) ; classements **par identité de TV + siège** ; modération **par TV** (w20-10) ; passerelle Bluetooth éprouvée (mesures de H-PLAY-BT, WebSocket si les POST sont lents) ; plusieurs tables (w20-13, conditionnel). Un téléphone apparié à plusieurs TV joue par la TV dont il affiche la page `/quiz` (S-12) : le service ne le voit jamais.

**Pourquoi pas de connexion directe téléphone → service** (pour mémoire) : décision 4 ; et techniquement : aucune surface nouvelle sur le service, une seule identité à contrôler, la passerelle du téléphone couvre déjà la TV sans box. Le seul cas perdu (téléphone loin de sa TV) est exclu par la règle elle-même.

## 5. Effet sur le code déjà construit et audité

| Élément | Effet |
|---|---|
| `RoomRegistry.join` (« join libre par code ») | **fermé derrière `CASTBRIDGE_PLAY_WEB`, défaut 0** ; à 0 : ticket + activation de TV exigés avant toute recherche ; à 1 (staging/tests seulement) : comportement d'aujourd'hui. Non supprimé : il porte des tests historiques et les compteurs de codes faux audités |
| `RoomRegistry.create`, `TicketVerifier`, `UsedTickets`, `HostRightsEvaluator`, `PlayRules`, `ReservedBank` | **inchangés**, réutilisés par `join` ; `jti` commun (un ticket sert une fois, pour créer **ou** rejoindre) |
| `PlayServer` | révocations : prêtes seulement si liste valide **ou** `off` explicite ; refus des `Origin` quand `webPlay=false` ; page d'information |
| `PlayConfig` | + `CASTBRIDGE_PLAY_WEB`, `CASTBRIDGE_PLAY_MAX_RELAYED_PER_TV`, `CASTBRIDGE_PLAY_REVOCATIONS` ; `DIRECT=1` interdit avec proxys de confiance ; `NoSecretsTest`, `ProductionDocsTest`, `.env.play.example`, PLAY-OPS § 4.2 alignés |
| Compteurs de codes faux (adresse, paire, /48, rotation à 50) | **changent de sens** : seul un appelant authentifié y arrive ; + compteur **par identité** ; l'énumération anonyme disparaît (T-1 marginal) |
| Plafonds d'adresse partagée / CGNAT | **valables** (TV derrière CGNAT d'opérateur, notamment par la passerelle) ; les sièges relayés n'ont pas d'adresse (`ServerRoom.kt:286`) |
| `BotScore` | valable **par siège** ; à prouver par test : les relayés d'une même TV (sans adresse) ne déclenchent pas le signal « même adresse, jeu identique » |
| Pseudonymes | **toujours nécessaires** (nom de TV, pseudonymes des téléphones relayés) |
| `ServerRoom` relais | **étendu** (siège relais, `relayedBy`, présence, pause, signe, fermeture, `kick`) ; l'hôte garde son comportement |
| `SafetySign` | inchangé côté serveur ; la TV ajoute une cause locale qui ne peut que baisser le niveau ; « à distance » = sièges d'autres TV |
| `PlayFallbackController` | gardé pour la TV ; cookie dormant ; **coalescence « dernier état »** dans `FbConn.offer` (et `WsConn.offer`) |
| `PlayPageController` | remplacé (si `webPlay=false`) par la page d'information |
| `OriginCheck` | secondaire (tout `Origin` refusé si `webPlay=false`) |

**Surface d'attaque nouvelle** : (1) `join` porte une activation (≤ 16 Ko), vérifiée en quelques dizaines de ms (w20-04 (i)) **après** un ticket valide et un `jti` jamais vu, sous le plafond API de 20 tickets/heure/appareil : même profil que `create`. (2) Activation copiée sur une autre TV (limite connue w20-04 (d)) : peut désormais rejoindre ; bornée par les connexions vivantes par identité, les compteurs quotidiens et la révocation. (3) TV relais malveillante : ne répond que pour **ses** sièges, ne peut qu'allonger leurs temps, aucune action d'hôte.

**Tests à ajouter** (w20-04b, JVM, avec mutations) : § « Critères » du cahier.

## 6. Le téléphone (CastBridge)

- **Rien de nouveau côté réseau** : il ouvre la page `/quiz` **de sa TV** (WebView, PIN, `S/QuizScreen.kt`), qui montre le bandeau reçu par la TV. Ni ticket, ni code Internet, ni connexion au service.
- **TV non activée, révoquée, essai épuisé, sans Internet, profil enfant** : la **TV** le sait et le dit ; la page `/quiz` affiche sa raison (« Partie Internet : activez la TV », « 3 parties Internet par jour en essai : à demain », « Internet : réservé aux adultes (code parental) »).
- **Téléphone hôte** : **jamais** (D-W20-6 durcie).
- Le téléphone qui sert de **passerelle Bluetooth** n'est qu'un tuyau d'octets TLS ; il peut aussi être joueur local de la même TV.
- Garanties W19 inchangées : refus portés par la TV, S-12, S-REPRISE.

## 7. Parties « test » gratuites

**Aucun mode de test ouvert n'est nécessaire.** Ce qui bloque la **première vraie partie** et ce qui peut se faire **maintenant** :

| Bloquant | Maintenant ? |
|---|---|
| w20-04b (service) | **oui** (cœur + service, permis pendant le gel) |
| w20-05a (cœur client TV) | **oui** (cœur pur) |
| w20-05 POC (écrans) | **oui si B-1** ; sinon après la sortie du gel |
| écrans téléphone (w20-06) | **inutiles** |
| révocations (B-2) | décision ; POC : `off` |
| clé de ticket, API, nginx, clé d'émetteur (B-4…B-7) | **oui**, actes du propriétaire (≈ 1 h avec PLAY-OPS § 3-4) |
| mesure de la passerelle (B-9) | **oui**, dès qu'un APK de POC existe ; la partie débit et latence (`/api/gateway/speed`, `generate_204`) **dès aujourd'hui** sur l'APK actuel |
| Quiz en essai (D-W20-5) | hors chemin (POC sur TV de production) |

Chemin le moins cher : **w20-04b ∥ w20-05a → w20-05 POC (exception de gel) → actes du propriétaire + H-PLAY-BT → démonstration § 2.11.**

## 8. Décisions du propriétaire (chacune avec recommandation)

| id | Question | Recommandation | Si non |
|---|---|---|---|
| D-AM-1 | Exception de gel pour les écrans TV du POC, derrière `quiz.online` éteint par défaut en release (modèle `StoreFlag`) ? | **Oui** | le POC attend la sortie du gel |
| D-AM-2 | POC sans révocations (`CASTBRIDGE_PLAY_REVOCATIONS=off`, ≤ 20 salles) ; au lancement : module des licences **ou** liste signée statique quotidienne ? | **Oui pour le POC ; au lancement : liste statique quotidienne** si le module n'est pas prêt | activer le module des licences avant le POC (plus lourd) |
| D-AM-3 | POC sur la **vraie** API, clé de ticket dédiée ? | **Oui** | seconde API exposée |
| D-AM-4 | POC sur 2 TV **de production** ; D-W20-5 (Quiz ouvert en essai dans `TrialPolicy`) traitée à part ? | **Oui**, et **oui à D-W20-5** (la décision 1 donne les parties libres à l'essai) | l'essai ne joue pas en ligne avant ce changement |
| D-AM-5 | La TV sur passerelle Bluetooth : **invitée** dans le POC, jamais hôte ; seuils H-PLAY-BT (≥ 40 kbps, RTT médian ≤ 1 000 ms, 95e ≤ 2 000 ms, reprise ≤ 30 s) et critère de fluidité « Duel à 8 joueurs relayés à 40 kbps : question ≤ 1,5 s, révélation et classement ≤ 2 s, `ack` ≤ 1,5 s » ? | **Oui** | hôte sur passerelle : une coupure ≥ 60 s abandonne la partie de tous |
| D-AM-9 | Coalescence « dernier état seulement » dans la file de sortie du service (w20-04b) plutôt qu'un profil bas débit ? | **Oui** (petite, générique, protocole inchangé) | sans elle, une TV EDGE reçoit la révélation jusqu'à ≈ 8 s en retard à 8 joueurs (estimé) |
| D-AM-10 | Délai entre questions : **1,5 s fixe** pour le POC ; délai adaptatif `clamp(max(1,5 s, pire RTT + 0,5 s), 1-2 s)` en phase 2 seulement sur mesure ; jamais au-delà de 2 s sans votre accord ? | **Oui** | `max(1,5 s, 2 × RTT)` dépasse 2 s dès RTT > 1 s |
| D-AM-6 | `/play` : page d'information statique plutôt que 404 ? | **Oui** | 404 : plus simple, moins clair |
| D-AM-7 | Sièges relayés par TV ≤ **8** ? | **Oui** | plus haut : une activation copiée pèse plus |
| D-AM-8 | Essai : entrées et créations dans **le même** compteur de 3 parties / jour ? | **Oui** | deux compteurs : 6 parties / jour |

**BLOQUÉ (faits)** : B-5 (route de ticket en production : à lire) ; B-7 (clé qui a signé les activations des TV de démonstration) ; B-9 (débit et latence réels de la passerelle Bluetooth : à mesurer) ; B-W20-1 (nginx et pare-feu de production hors dépôt) reste vrai.

## 9. Risques

| # | Risque | Prob. | Parade |
|---|---|---|---|
| R-1 | Exception de gel refusée : le POC glisse | moyenne | 04b et 05a avancent pendant le gel |
| R-2 | Vue composée ≠ page `/quiz` locale | moyenne | test de contrat (05a) ; repli P1a |
| R-3 | Passerelle en EDGE trop lente ou instable pour un flux SSE (RTT 2 s, coupures de cellule) | moyenne | coalescence (w20-04b), simulateur 40 kbps (w20-05a), mesure H-PLAY-BT avant ; TV sur passerelle invitée ; 3G/4G ou point d'accès Wi-Fi en secours ; WebSocket en phase 2 |
| R-7 | La taille réelle d'un `state` à 8 joueurs dépasse l'estimation (≈ 5 Ko) | moyenne | w20-05a mesure les octets par type de message ; vues différentielles en phase 2 si nécessaire |
| R-4 | `REVOCATIONS=off` laissé en production | faible | santé `disabled`, ≤ 20 salles, HANDOFF |
| R-5 | Activation copiée : une TV étrangère rejoint | faible | connexions par identité, compteurs, révocation dès le lancement |
| R-6 | Les tests existants du service (joins libres) cassent au défaut 0 | certaine | fixtures `webPlay = true` explicites, revue Opus |

## 10. Pour mémoire : options écartées

- **Voucher autonome signé par la TV** (clé d'installation, format proche de `TvProof`) : la clé d'installation n'est pas certifiée par l'activation, et `TvProof` remet l'activation brute aux téléphones appariés (`C/owner/TvProof.kt`, `Proof.activationToken`) : un téléphone désapparié pourrait fabriquer des vouchers. Écarté avant la décision 4, puis exclu par elle.
- **Pass demandé à l'API par le téléphone** : donnerait au téléphone une identité API (contraire à I-1).
- **Délégation vivante portée par la connexion de la TV** (cahier w20-14) : techniquement saine, **non retenue par la décision 4**.

## 11. Ce qui a été lu, et ce qui n'a pas pu être vérifié

**Lu** : `DESIGN-W20` (entier), `docs/PLAY-PROTOCOL.md`, `docs/PLAY-OPS.md` (plan, § 4.0), `docs/REMOTE-TUNNEL-BT.md`, `docs/agent-reports/sonnet-w20-04.md` (choix, non fait, correctifs d'audit), index W20, cahiers w20-04/05/06/09/10 ; code : `SP/RoomRegistry.kt`, `SP/PlayServer.kt`, `SP/PlayPageController.kt`, `SP/OriginCheck.kt`, `SP/PlayFallbackController.kt` (act/events/state), `SP/PlayConfig.kt` (environnement), `SP/entitlement/{TicketVerifier,HostRights,RevocationsFeed}.kt`, `C/quiz/online/ServerRoom.kt` (entier), `ServerAuthority.kt` (signatures), `PlayTransport.kt`, `Authority.kt` (interface), `SafetySign.kt` (faits, seuils), `PlayRules.kt` (constantes), `C/quiz/QuizHttp.kt` (constructeur), `C/connect/Routes.kt` (en-tête), `C/ux/BtGatewayView.kt` (en-tête), `R/BtGatewayHost.kt` (proxy, mesure), `R/TvService.kt` (route de mesure, sonde), `R/QuizHub.kt`, `R/QuizActivity.kt` (lecture de la vue), `C/owner/{TrialPolicy,TvProof,InstallSigner}.kt` (en-têtes), `C/store/StoreFlag.kt`, `B/play/PlayTicket{Controller,Service}.java`, `android/receiver/build.gradle.kts`, `DESIGN-W19` (S-12, RS).

**Non lu ou non vérifié** : les sections « Audit Opus » de `sonnet-w20-03.md` et `sonnet-w20-07.md` en entier (leurs effets ont été lus dans le code) ; `PlayGuard`, `Limits`, `BotScore` ligne à ligne ; `PinBook`, `TvGate`, `Entitlements`, `DeviceCode` ; le format exact attendu par `play.html` local face à une vue composée ; le comportement d'un flux SSE long et du garde-vivant HTTP à travers le SOCKS de la passerelle sur RFCOMM ; le délai de reconnexion de la passerelle ; le statut de W18 (Wi-Fi Direct) dans l'APK de démonstration ; l'état de l'API et du nginx de production ; la clé qui a signé les activations des TV de démonstration ; aucun chiffre de coût, de délai, de débit ou de latence n'a été mesuré.
