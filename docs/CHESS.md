# Échecs sur la TV (branche `feat/tv-chess`)

Jeu d'échecs sur CastBridge TV : **seul contre l'ordinateur**, **à deux sur la TV**, **télécommande contre téléphone**,
**deux téléphones** (la TV affiche la partie) et **en ligne : une TV contre une autre TV sur Internet, en partie libre ou
avec mise de jetons NDEM / MBOKO** (§ 6 ; un téléphone ne se connecte jamais au service, il regarde par sa TV).
Chaque coup a un **compte à rebours réglable de 10 à 60 s, jamais plus**.

- Ouvrir : tuile **Échecs** de l'accueil TV, ou onglet **Échecs** de l'app téléphone (« Ouvrir les échecs sur la TV »,
  avec le PIN déjà connu de l'app).
- Télécommande : **flèches** = déplacer le curseur doré sur l'échiquier, **OK** = prendre la pièce puis la poser
  (les cases possibles sont marquées d'un point, d'un anneau pour une prise), **RETOUR** = annuler la sélection, sinon
  menu Pause. Dans les menus : haut/bas = choisir la ligne, **‹ ›** (gauche/droite) = changer la valeur, OK = valider.
- Téléphone sans l'app : scanner le QR code affiché par la TV (page web `/chess`, rien à installer).

## 1. Réglages (écran « Échecs »)

| Réglage | Valeurs |
|---|---|
| Adversaire | Ordinateur · Deux joueurs sur la TV · Un joueur sur téléphone · Deux téléphones (la TV affiche) · En ligne (Internet) |
| Niveau (ordinateur) | 1-2 débutant, 3-4 facile, 5-6 moyen, 7-8 fort |
| Votre couleur | Blancs · Noirs · Au hasard |
| Compte à rebours | 10 · 20 · 30 · 45 · 60 s **par coup** |
| Temps dépassé | **partie perdue** (Compétition) · **coup joué d'office** (Entraînement) |
| Son du compte à rebours | un bip discret par seconde sous 10 s, seulement pour le joueur de la TV |

Les réglages sont gardés d'une partie à l'autre (préférences `castbridge_chess` de l'app TV).

## 2. Le compte à rebours (règle choisie)

- Chaque joueur a **au plus N secondes pour jouer chaque coup** (N = 10 à 60) ; le compteur repart à N après chaque coup.
  Toute valeur demandée est ramenée dans 10..60 s (`MoveTimer.clamp`), côté TV **et** côté serveur : **jamais plus de 60 s**,
  même si un client ou une configuration demande plus.
- **Compétition** (défaut) : dépassement = **partie perdue au temps** (règle standard). Exception FIDE 6.9 : si l'adversaire
  n'a plus de quoi mater (roi seul, roi + fou ou cavalier seul…), la partie est **nulle**.
- **Entraînement** : nul ne perd au temps ; à zéro, **un coup légal est joué d'office** (au hasard) pour le joueur en
  retard, affiché en orange dans la liste des coups. Aux échecs on ne peut pas « passer » : c'est la seule façon de
  garder un compte à rebours utile sans finir la partie.
- **Qui tient l'horloge ?** L'hôte seul (la TV pour une partie à la maison, le serveur central pour une partie en ligne).
  Les clients n'envoient jamais de temps, seulement des coups ; un coup arrivé après l'échéance est refusé.
- **Pause** : le menu Pause **arrête** la pendule quand personne ne joue depuis un téléphone (contre l'ordinateur, à deux
  sur la TV). Face à un joueur sur téléphone ou en ligne, la pendule **continue** (l'adversaire n'est jamais bloqué).
- Pendule à l'écran : anneau qui se vide + secondes ; **rouge et clignotant sous 10 s**.

## 3. Règles du jeu et fin de partie

Moteur complet (`core/chess`) : roque (droits, cases traversées non attaquées), prise en passant (seulement juste après
la double poussée, jamais en découvrant son roi), promotion avec **choix** (Dame, Tour, Fou, Cavalier), échec, mat, pat,
**règle des 50 coups** et **triple répétition** appliquées automatiquement (pas de « réclamation » sur une TV),
**matériel insuffisant**. Fins : mat, pat, temps, abandon, nulle d'un commun accord, 50 coups, répétition, matériel.

- **Proposer nulle** : à deux sur la TV, la nulle est conclue tout de suite (les deux joueurs sont devant l'écran) ;
  contre l'ordinateur, il n'accepte que s'il est moins bien (≥ 0,5 pion) ou après 20 coups sans prise ni pion ;
  contre un téléphone, l'autre accepte ou refuse (jouer un coup = refuser).
- **Annuler le dernier coup** : seulement contre l'ordinateur (reprend votre coup et sa réponse ; le compteur repart).
- Écran de fin : résultat, **Rejouer (mêmes réglages)**, Revoir l'échiquier, Nouvelle partie, Quitter. Sur téléphone :
  la partie en **PGN** à copier.
- Notation : SAN anglaise standard (K Q R B N, `O-O`, `exd6`, `e8=Q+`), import/export **FEN** et **PGN** dans `core`.

## 4. L'ordinateur (`ChessAi`)

Alpha-bêta (négamax, PVS) avec **approfondissement itératif**, recherche de calme (prises), coup nul, réductions des coups
tardifs, coups « tueurs » et historique ; évaluation matériel + **tables pièce-case** (avec table du roi de finale et
bonus pour pousser le roi seul au bord). **Table de transposition de taille fixe : 4 Mio** (2^18 entrées × 16 octets,
bornée à 16 Mio par construction). Petite **bibliothèque d'ouvertures** embarquée (42 lignes : espagnole, italienne,
siciliennes, française, Caro-Kann, gambit dame, indiennes, anglaise, Réti…), coup choisi au hasard parmi les lignes.

| Niveau | Profondeur max | Temps max | Aléa |
|---|---|---|---|
| 1 | 1 | 0,25 s | ± 2 pions |
| 2 | 2 | 0,4 s | ± 1,2 pion |
| 3 | 3 | 0,7 s | ± 0,6 pion |
| 4 | 4 | 1 s | ± 0,25 pion |
| 5 | 5 | 1,5 s | — |
| 6 | 7 | 2 s | — |
| 7 | 9 | 2,5 s | — |
| 8 | 40 | 3 s | — |

Le temps de réflexion est toujours ≤ **3 s** et ≤ **1/3 du compte à rebours** (`ChessAi.allowance`) : l'ordinateur ne perd
jamais au temps. L'aléa ne touche jamais un mat : un mat visible est toujours joué. Le calcul tourne sur un fil à part
(`chess-ai`, priorité basse), jamais sur le fil de l'écran. Repère : ~3,6 millions de positions/s sur un PC (JVM) ;
sur l'ARM 32 bits de la TV, compter 10 à 20 fois moins (profondeur ~6-7 au niveau 8) — **à mesurer sur la vraie TV**.

## 5. Parties avec les téléphones (maison)

La TV héberge la salle (`ChessRoom`, comme le quiz) : **code à 4 chiffres + QR code**, un **jeton aléatoire** par joueur
(le code ne sert qu'à entrer ; le jeton permet de **revenir à sa place** après une coupure), spectateurs illimités (10
personnes max dans la salle). Le premier téléphone arrivé prend la couleur libre (Blancs d'abord) ; avant la partie on peut
changer de place (« Jouer les Blancs / les Noirs / Regarder ») ou échanger les couleurs depuis la TV.

Depuis la plateforme de jeux (docs/GAMES.md § 3, chantier games-G1) la salle (code, jetons, places, spectateurs, présence,
notification des changements) est la classe générique `GameRoom` dont `ChessRoom` hérite, et les routes `/chess/*` sont servies
par le `RoomHttp` commun à tous les jeux (`ChessHttp` n'en dit que ce qui est propre aux échecs : préfixe, page, paramètre `ply`,
messages). **Rien ne change pour les échecs** : mêmes réponses octet pour octet (`ChessGoldenTest`), mêmes tests (`ChessRoomTest`,
`ChessHttpTest`). Le moteur, l'ordinateur, la pendule et les nulles restent ceux d'ici. La salle `game:chess` du service en ligne
(§ 6) n'est pas une `GameRules` : elle reprend directement le moteur pur `core/chess` (`ChessGame`), qui fait foi pour les règles,
et les téléphones du foyer regardent la partie en ligne de leur TV par une salle de la plateforme sans place
(`OnlineChessHost`, servie par le même `RoomHttp` sur `/chess`).

**Anti-triche de base** : la TV vérifie chaque coup avec le moteur (coup illégal → refusé, rien ne change), seul le joueur
dont c'est le tour peut jouer, un spectateur ne peut rien jouer, chaque coup porte le numéro du demi-coup auquel il répond
(un doublon ou un coup en retard est refusé, `STALE`), et l'horloge est celle de la TV.

Routes publiques (sans PIN, port 8765, à côté de `/quiz`) :

| Route | Rôle |
|---|---|
| `GET /chess` | page web mobile (vanilla JS, pièces vectorielles injectées) |
| `GET /chess/api/hello` | une salle est-elle ouverte ? (ni code ni noms) |
| `POST /chess/api/join?code=&name=[&token=]` | entrer (ou revenir avec son jeton) → `token`, `id`, `name`, `color` |
| `GET /chess/api/events?token=` | Server-Sent Events : l'état à chaque changement (+ ping 15 s) |
| `GET /chess/api/state?token=&since=&wait=` | repli long-poll (≤ 25 s) |
| `POST /chess/api/act?token=&action=&arg=&ply=` | `move` (arg = UCI, ply), `resign`, `draw` (offer/accept/decline), `sit` (w/b), `stand` |
| `POST /chess/api/leave?token=` | quitter |

Limites : requêtes par IP, 10 codes faux par IP / 5 min, 2 flux par joueur, 12 flux au total.
Routes avec PIN pour l'app : `GET /api/chess` (salle ouverte, son code ; pendant une partie en ligne de la TV : `onlineGame:true`
et le code à 4 chiffres de la vitrine, voir § 6.6 ; `online` reste `false`), `POST /api/chess/open` (ouvre l'écran sur la TV).
`POST /api/chess/config` n'a plus d'effet (l'ancien interrupteur `online=0|1` n'existe plus : c'est le service qui dit s'il
héberge les échecs) ; la route répond encore l'état pour les anciennes applications.

### Format d'état (commun TV, téléphone, web et relais)

```json
{ "v": 12, "protocol": 1, "stage": "LOBBY|PLAYING|FINISHED|CLOSED", "code": "4821",
  "settings": {"perMoveSeconds": 30, "mode": "COMPETITION", "level": 3, "white": "REMOTE", "black": "PHONE"},
  "white": {"kind": "REMOTE|PHONE|AI|ONLINE", "name": "…", "connected": true, "level": null},
  "black": {"…": "…"}, "me": {"id": "p1", "name": "Awa", "color": "w|b|null"}, "spectators": 1,
  "fen": "…", "startFen": "…", "ply": 3, "turn": "b", "check": false,
  "san": ["e4", "e5", "Nf3"], "uci": ["e2e4", "e7e5", "g1f3"], "lastMove": "g1f3", "auto": [],
  "legal": ["b8c6", "…"],
  "clock": {"perMoveMs": 30000, "remainingMs": 21400, "running": true, "paused": false, "turn": "b"},
  "drawOffer": null, "thinking": false, "notice": null,
  "result": {"winner": "w|b|null", "reason": "CHECKMATE|STALEMATE|TIMEOUT|TIMEOUT_DRAW|RESIGNATION|FIFTY_MOVES|THREEFOLD|INSUFFICIENT|AGREEMENT|ABANDONED", "text": "Échec et mat : les Blancs gagnent", "pgn": "1-0"},
  "pgn": "[Event …] … (une fois la partie finie)" }
```

`legal` n'est rempli que pour le joueur qui a le trait ; aucun jeton d'un autre joueur n'apparaît jamais.
`remainingMs` est relatif à l'instant de la réponse : le client décompte localement jusqu'au prochain état.

## 6. Jeu en ligne : une TV contre une autre TV, libre ou avec mise (chantier games-G2, réalisé)

Les échecs en ligne sont **réalisés dans `castbridge-play`** (le service de jeu, `/play/`), pas dans l'API : la salle `game:chess`
tient la partie, l'API ne fait que **bloquer** puis **régler** les jetons. L'ancien projet de routes REST `/api/chess/v1/*` et son
client (`ChessRelayClient` REST, `FakeRelay`) sont **supprimés** ; le drapeau `POST /api/chess/config?online=1` n'existe plus : la
capacité vient de `GET /play/.well-known/caps` (clés `chess` et `stakes`).

### 6.1 Qui joue, qui voit

- **Seule une TV activée, connectée à Internet (directement ou par le tuyau d'un téléphone synchronisé, relay-R1), joue** : ticket
  `cbp1` + activation `cbx1` + preuve de possession de la clé d'installation, comme le Quiz en ligne. Une TV d'essai joue en
  ligne **en partie libre seulement** (règle du propriétaire : essai = pas de mise).
- **Aucun téléphone ne parle au service.** Pendant une partie en ligne, les téléphones du foyer **regardent par leur TV** (code
  à 4 chiffres + page `/chess` de la TV, routes inchangées) : vitrine `OnlineChessHost` (`GameRoom` sans place), sans coups
  légaux, sans identifiant de salle, sans aucun jeton de siège, blocage `cbe1` ou résultat `cbr1`. Un téléphone ne commande rien
  (`FORBIDDEN`) ; la place en ligne est celle de la TV (télécommande).
- **Aucune page web de jeu publique** : `/play` reste une page d'information.

### 6.2 Écran « Échecs › Adversaire : En ligne (Internet) »

La porte est celle du cœur (`ChessOnlineGate`, testée) : interrupteur « jeu en ligne » des réglages, TV activée avec une activation
vérifiable par le service, heure fiable, profil adulte, Internet (ou un téléphone synchronisé capable de donner un tuyau),
service qui annonce `chess`. Sinon **une raison est toujours dite** (« Connexion Internet requise », « Les échecs en ligne ne sont
pas encore ouverts sur le service CastBridge… »), jamais un écran vide, et aucune connexion ne s'ouvre. Menu :

1. **Créer une partie** : « Mise » (*Libre* / *NDEM* / *MBOKO* ; seules les monnaies que la TV peut miser sont offertes), « Montant »
   (parmi l'échelle du serveur : NDEM 10, 20, 50, 100, 200 ; MBOKO 1, 2, 5, 10), solde affiché, couleur, compte à rebours, « Créer
   la partie ». Une mise demande une confirmation (« Créer une partie avec mise ? 20 NDEM par joueur · votre solde 150 NDEM ») et
   rappelle : *abandonner ou quitter la partie fait perdre la mise ; plus de 60 s hors ligne = abandon*. Une partie misée se joue
   en **compétition** (jamais de coup tiré au hasard avec des jetons en jeu).
2. **Rejoindre avec un code** (`XXXX-XXXX`, 8 symboles, saisis avec ‹ ›) ou **Regarder une partie avec un code** (sans mise).
   Si la salle est misée, la TV apprend la mise **avant** de bloquer quoi que ce soit : « Cette partie se joue avec une mise — 20
   NDEM par joueur · votre solde… » ; elle bloque alors sa mise et revient **sur la même liaison** (même ticket). Solde
   insuffisant ou mises indisponibles : le choix « Regarder seulement » reste offert.
3. **Reprendre la partie en cours** : l'application a été fermée en pleine partie ; le siège gardé (6 min au plus) permet de
   revenir (`resume`), y compris pour récupérer le résultat d'une partie déjà finie et la régler.
4. Un écran recréé par le système **ne quitte pas la partie** : elle vit dans l'application, pas dans l'écran.

Pendant la partie, le pied de l'écran dit, par ordre d'importance : le **règlement** de la mise, une liaison dégradée (reprise en cours,
tuyau d'un téléphone), le décompte « Adversaire déconnecté : forfait dans N s », puis la mise en jeu et, sans alarme, « Partie par
relais : liaison lente » quand Internet ne vient que du tuyau d'un téléphone. Une liaison perdue (60 s) ouvre « Connexion perdue » :
le service tranche (forfait). En fin de
partie : « Vous avez gagné ! / perdu », « Partie nulle », ou « **Partie interrompue** » (jamais « nulle » pour une interruption),
et pour une partie misée « Voir « Mes jetons » ».

### 6.3 Règles de la salle `game:chess` (service, `ChessServerRoom`)

Le **service est l'arbitre** (moteur pur `core/chess`, `ChessGame`, partagé avec la TV : c'est lui qui fait foi pour les règles) :

- **Deux joueurs** : l'hôte (couleur au choix ou au hasard) et celui qui entre par le code ; **spectateurs** : des TV (8 au plus,
  sans mise). Une TV ne joue pas contre elle-même.
- **Coups numérotés** : chaque coup porte le numéro du demi-coup auquel il répond (`ply`, obligatoire) ; un doublon ou un coup en
  retard est refusé `STALE` (liaison lente, renvois). Seul le trait joue ; un coup illégal est refusé `ILLEGAL`.
- **Pendule du serveur** : 10 à 60 s par coup, jamais plus, compteur relancé à chaque coup ; **temps dépassé = partie perdue**
  (nulle si l'adversaire ne peut plus mater) ; en `PRACTICE` (parties libres seulement) le coup est joué d'office. La TV n'envoie
  jamais un temps ni un état, seulement des coups ; elle décompte localement entre deux positions.
- **Abandon** (`resign`), **nulle** proposée / acceptée / refusée, mat, pat, 50 coups, triple répétition, matériel insuffisant.
- **Déconnexion** : la place est gardée (reprise par `resume{roomId, token, lastSeq}`), la pendule continue ; **une TV absente 60 s
  perd par forfait** si l'autre est là ; si les deux sont absentes la partie est **interrompue** (mises rendues).
- **Salle d'attente** : une salle sans adversaire vit 30 min au plus ; l'hôte peut **annuler** (`cancel`) : sa mise est rendue.
  Une partie dure 3 h au plus (puis : interrompue, mises rendues). Une partie finie reste consultable 5 min (résultat, reprise).
- **Quotas** : les salles de jeu partagent l'espace de codes, `CASTBRIDGE_PLAY_MAX_ROOMS` et les plafonds par adresse et par
  identité du Quiz ; un code faux n'est jamais distingué d'un code inconnu.

Protocole : `docs/PLAY-PROTOCOL.md`, section « Salle de jeu `game:chess` ». Exploitation : `docs/PLAY-OPS.md` § 4.2 ter.

### 6.4 Mises (option B de la conception W22)

Le service **ne détient aucun secret du grand livre** et ne parle jamais à l'API : il vérifie un blocage signé par l'API et signe un
résultat avec **sa propre clé dédiée**. Enchaînement :

1. **Blocage** : la TV demande à l'API `POST /api/v1/wallet/escrow` (`game:"chess"`, monnaie, montant, clé d'idempotence, activations).
   L'API applique l'échelle de mises du jeu, **refuse l'essai** (`TRIAL_FREE_ONLY`), refuse un montant hors échelle
   (`STAKE_NOT_OFFERED`), un solde insuffisant (`INSUFFICIENT`) et un plafond de parties gagnées atteint (`STAKE_WIN_CAP`, avec le
   moment où la prochaine partie avec mise s'ouvre), puis rend un blocage signé **`cbe1`** (valable 30 min). Rejouer la même clé
   rend le **même** blocage : une coupure n'en crée jamais deux. Un blocage non employé (échec d'ouverture) est réutilisé pour la
   partie suivante.
2. **Entrée** : la TV porte `cbe1` au service (`create` ou `join`). Le service le vérifie (signature de la clé « portefeuille »
   publique, audience `aud`, échéance, identité de la TV = celle de son activation, monnaie, montant, un seul siège, **pas déjà
   employé** dans une salle vivante). Une salle misée sans blocage répond `STAKE_ESCROW_REQUIRED` avec la mise.
3. **Partie** : arbitrée comme ci-dessus ; une mise ne se joue qu'en compétition.
4. **Résultat** : à la fin (ou à l'interruption) le service signe **`cbr1`** (domaine `castbridge-play-result-v1`, lignes
   `[eid, id, utilisé, versé]`, somme versée = somme utilisée : rien ne se crée) avec sa clé dédiée, l'envoie aux deux TV (message
   `result`) **et** en dépose une copie dans son volume pour le collecteur de l'hôte (`tools/wallet/collect-results.sh`).
5. **Règlement** : la TV poste `cbr1` à l'API (`POST /api/v1/wallet/settle`, aucune authentification : la signature fait foi) ; à
   défaut, le collecteur de l'hôte le fait. L'API est **idempotente par identifiant de salle** (rejouer rend la même réponse).

| Issue | Règlement |
|---|---|
| Victoire (mat, temps, abandon de l'adversaire, **forfait**) | le gagnant reçoit la cagnotte (2 mises) **moins les frais de plateforme** ; le perdant perd sa mise |
| Nulle (accord, pat, 50 coups, triple répétition, matériel) | chaque mise est **rendue** (aucuns frais) |
| Interruption (annulation de l'hôte, salle expirée, deux TV absentes, partie trop longue, arrêt du service) | chaque mise est **rendue** (`cbr1` de type `ABORT`) |

Frais de plateforme : **politique du serveur** (`wallet_policy`, points de base sur la cagnotte d'une partie décidée seulement),
**0 % par défaut** ; les frais s'écrivent sur le compte système `SYS:FEE`. Plafonds de parties **gagnées** avec mise, par identité
(jour / semaine du lundi au dimanche / mois civil, calendrier Africa/Douala, comme les plafonds du Quiz) : **3 / 10 / 15 par
défaut, à confirmer par le propriétaire** (politique modifiable sans redéploiement). Chaque partie misée s'inscrit au **journal des
parties misées** (W22 § 5) lu par `GET /api/v1/admin/wallet/games`. Voir `docs/LICENSE-ADMIN.md` § « Échecs misés ».

Si la TV n'a pas pu régler (hors ligne), elle **garde** le résultat signé (8 au plus), le **reposte** à l'ouverture de l'écran et au
retour de la connexion, et l'écran dit « Règlement en attente… » ; le service en garde une copie 7 jours pour le collecteur. Un
refus définitif de l'API (« rendu à l'échéance », déjà réglé…) est dit et n'est pas renvoyé. **La TV ne calcule jamais un solde** :
le texte de fin (« Vous gagnez 16 NDEM ») vient de la réponse de l'API, après frais.

### 6.5 Messages (extrait ; voir `docs/PLAY-PROTOCOL.md`)

| Sens | Message |
|---|---|
| TV → service | `create{game:"chess", chess:{perMoveSeconds, mode, color}, stake?:{cur, per}, escrow?:<cbe1>, activation, proof}` ; `join{code, escrow?, spectate?, activation, proof}` ; `resume{roomId, token, lastSeq}` ; `game{op:"move"\|"resign"\|"draw"\|"cancel", arg?, ply?, seq}` |
| service → TV | `welcome{…, game:"chess"}` ; `state` (vue d'échecs de la TV : `legal` seulement pour qui a le trait) ; `ack{ref, result}` (`OK`, `ILLEGAL`, `NOT_YOUR_TURN`, `STALE`, `OVER`, `FORBIDDEN`, `IGNORED`) ; `result{token:<cbr1>}` ; `error{reason, data?}` |

Refus propres aux jeux (`GameReason`, textes français côté TV) : `GAME_UNAVAILABLE`, `GAME_UNKNOWN`, `STAKES_SUSPENDED`,
`STAKE_TRIAL_FREE_ONLY`, `STAKE_ESCROW_REQUIRED` (avec `data:{game, cur, per}`), `STAKE_ESCROW_INVALID`, `STAKE_BAD`, `SEATS_TAKEN`,
`SAME_TV`.

### 6.6 Format d'état en ligne (ajouts au § 5, additifs)

La vue d'une TV reprend le format du § 5 avec `white.kind = black.kind = "ONLINE"` et, en plus : `room:{id, game:"chess", code,
state, role, seq, serverNowMs, hostConnected, forfeitMs, away:{w, b}}` (`away` = depuis combien de ms chaque couleur est absente, pour
le décompte du forfait) et `stake:{cur, per, pot, settled}` (partie misée). `result.reason = "ABANDONED"` dit une partie
**interrompue** (jamais une nulle). Aux téléphones du foyer la TV sert la même vue **sans `legal`, sans `room`, sans `me`**, avec
`"online":true`, la pendule corrigée du temps écoulé depuis la dernière position ; `GET /chess/api/hello` ajoute alors `"online":true`.

### 6.7 Limites connues et décisions à confirmer

- Si la création échoue **après** que le service a accepté le blocage sans que la TV reçoive la réponse, ce blocage reste employé
  jusqu'à la fin de la salle (30 min au plus) ; il n'existe pas de route « libérer » (elle ouvrirait une triche : débloquer pendant
  la partie) ; l'API rend les blocages non réglés à leur échéance.
- La pendule du service est **stricte** (aucune grâce de latence) ; un coup qui arrive après l'échéance est refusé même si la TV l'a
  joué à temps sur sa liaison lente.
- Décisions du propriétaire : plafonds de parties gagnées (3 / 10 / 15), frais de plateforme (0 %), échelle de mises.

## 7. Écrans et mise en page

- **TV** : tout l'écran de jeu est dessiné sur un seul Canvas (`ChessTvView`), dimensionné à partir des **pixels** de
  l'écran (`ChessTvLayout`, unité = 1 % de la hauteur), pas des dp : même mise en page à 1280×720 / 160 dpi et à
  1920×1080 / 320 dpi (où l'écran ne fait que 960×540 dp). Chaque texte a sa boîte, une taille qui tient dans sa hauteur
  et une ellipse « … » si la largeur manque : **aucun texte ne peut se superposer** (vérifié par `ChessLayoutTest` sur 6
  résolutions et par captures sur l'émulateur Android TV aux deux configurations). Pas de gestion du focus Android : le
  D-pad pilote directement le curseur et les menus.
- Pièces : formes vectorielles maison (`ChessPieces`, syntaxe SVG), les mêmes sur la TV (Canvas), le téléphone
  (Compose) et la page web (Path2D) ; aucune image.
- **Téléphone** : onglet Échecs natif (Compose, thème sombre de l'app) ; **web** : `/chess` (vanilla JS), tactile.

## 8. Fichiers

`core/chess/` : `Position.kt` (échiquier 0x88, coups légaux, FEN, hachage Zobrist), `Notation.kt` (SAN, PGN, règles de
fin), `ChessAi.kt` (IA + bibliothèque d'ouvertures), `ChessGame.kt` (partie + compte à rebours), `ChessRoom.kt` (salle TV,
héritière de `core/games/GameRoom`), `ChessHttp.kt` (routes `/chess`, sur `core/games/RoomHttp`), `ChessTransport.kt` (LAN),
`ChessRelayClient.kt` (client des échecs en ligne de la TV sur `/play/`, § 6), `ChessPieces.kt`, `ChessTvLayout.kt` ;
`core/chess/online/` : `ChessServerRoom.kt` (la salle `game:chess` du service, pure), `ChessSettlement.kt` (règlement pur,
échelle de mises), `ChessOnline.kt` (porte, mises permises, écran de création, textes), `ChessStakeFlow.kt` (blocage, règlement,
mémoire), `ChessOnlineGame.kt` (la partie de la TV), `OnlineChessHost.kt` (vitrine des téléphones du foyer) ;
`core/tv/CombinedRoutes.kt` ; `resources/castbridge/chess/play.html`. Service : `server-play/…/RoomRegistry.kt` (salles de jeu),
`stake/` (porte des blocages, clé « résultat », dépôt des `cbr1`). API : `wallet/ops/EscrowService`, `SettleService`,
`GameJournal`, migration `V68__chess_stakes.sql`. TV : `ChessActivity.kt`, `ChessOnlineHub.kt`, `ChessTvView.kt`, `ChessHub.kt`,
`ic_t_chess.xml`. Téléphone : `ChessScreen.kt`. Tests : `ChessEngineTest` (perft), `ChessAiTest`, `ChessRoomTest` (horloge, salle,
HTTP), `ChessLayoutTest`, `ChessServerRoomTest`, `ChessSettlementTest`, `ChessOnlineGameTest` (deux TV sur un service en mémoire),
`ChessOnlineScreensTest`, `OnlineChessHostTest`, `WalletClientChessTest` ; `server-play` : `ChessHubTest`, `StakeUnitTest` ;
API : `ChessStakeApiTest`, `SettleFeeTest`, `WinWindowsTest`.
