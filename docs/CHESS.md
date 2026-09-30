# Échecs sur la TV (branche `feat/tv-chess`)

Jeu d'échecs sur CastBridge TV : **seul contre l'ordinateur**, **à deux sur la TV**, **télécommande contre téléphone**,
**deux téléphones** (la TV affiche la partie) et, dès que le serveur central l'ouvrira, **en ligne** (TV ou téléphone
ailleurs sur Internet). Chaque coup a un **compte à rebours réglable de 10 à 60 s, jamais plus**.

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
Routes avec PIN pour l'app : `GET /api/chess` (salle ouverte, code, `online`), `POST /api/chess/open` (ouvre l'écran sur
la TV), `POST /api/chess/config?online=0|1[&relay=https://…]` (jeu en ligne, voir § 6).

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

## 6. Jeu en ligne : protocole du relais (serveur central)

Le serveur du projet (Spring Boot, `https://bridge.sti-cm.com`, branche `feat/backend`, **non modifié ici**) servira de
relais. Côté appareils, tout est prêt dans `core` : interface `ChessTransport`, implémentée par `LanChessClient` (TV de la
maison, **fonctionnel**) et `ChessRelayClient` (Internet, **testé contre un faux serveur**, `ChessRelayTest`). Le relais
est **désactivé par défaut** : l'option « En ligne » affiche « bientôt » et aucune requête ne part. Pour l'activer quand
le serveur exposera les routes : `POST /api/chess/config?online=1` (avec le PIN) sur la TV ; l'app téléphone suit ce que
la TV annonce. `ChessRelayClient.available()` vérifie `GET …/hello` (protocole 1) avant de jouer.

### Routes à implémenter par le serveur central

Base : `https://bridge.sti-cm.com/api/chess/v1`. Corps et réponses en JSON UTF-8. Authentification par jeton de joueur :
en-tête `Authorization: Bearer <token>` (aussi accepté en `?token=` pour `events`, EventSource ne sachant pas envoyer
d'en-tête). Codes de partie : **6 caractères** de l'alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (sans 0/O, 1/I/L),
insensibles à la casse côté client.

| Méthode et route | Corps | Réponse |
|---|---|---|
| `GET /hello` | — | `200 {"ok":true,"protocol":1}` |
| `POST /games` | `{"name","perMoveSeconds","color":"white|black|random","mode":"COMPETITION|PRACTICE"}` | `201 {"code","token","id","name","color":"w|b"}` |
| `POST /games/{code}/join` | `{"name","token"?}` | `200 {"token","id","name","color":"w|b|null"}` — même jeton = même place (reconnexion) ; 3e personne = spectateur (`color` null) ; `404` code inconnu |
| `GET /games/{code}/state?since=&wait=` | — | `200` état (format § 5), long-poll : attend jusqu'à `wait` s (≤ 25) que `v` > `since` ; `401` jeton inconnu |
| `GET /games/{code}/events?token=` | — | flux SSE `event: state` / `data: <état>` à chaque changement, `: ping` toutes les 15 s |
| `POST /games/{code}/move` | `{"uci":"e2e4","ply":0}` | `{"result":"OK|ILLEGAL|NOT_YOUR_TURN|STALE|OVER|FORBIDDEN","state":{…}}` (`400` si ILLEGAL, `409` si NOT_YOUR_TURN) |
| `POST /games/{code}/resign` | `{}` | `{"result","state"}` |
| `POST /games/{code}/draw` | `{"action":"offer|accept|decline"}` | `{"result","state"}` |
| `POST /games/{code}/leave` | `{}` | `{"result":"OK"}` (la place reste réservée au jeton ; la pendule continue) |

Obligations du serveur (mêmes que la TV, cf. `ChessGame`, `ChessRoom` et le faux serveur `FakeRelay` des tests) :
1. **Valider chaque coup** avec un moteur d'échecs complet (le moteur `core/chess` est du Kotlin pur sans dépendance :
   il peut être repris tel quel côté Spring Boot) ; refuser un coup hors tour, un doublon (`ply` ≠ demi-coups joués →
   `STALE`), un coup d'un spectateur.
2. **Tenir l'horloge** : `perMoveSeconds` ramené dans 10..60, compteur relancé à chaque coup, démarré quand le 2e joueur
   arrive, dépassement = perte (ou nulle si l'adversaire ne peut plus mater), `PRACTICE` = coup joué d'office ;
   vérifier l'échéance à chaque requête et par une tâche périodique (≤ 1 s).
3. Détecter mat, pat, 50 coups, triple répétition, matériel insuffisant ; nulle proposée / acceptée ; abandon.
4. Limiter : requêtes par IP, créations de parties par IP, codes faux par IP ; purger les parties finies ou inactives
   (ex. 30 min) ; ne jamais renvoyer le jeton d'un autre joueur.

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
fin), `ChessAi.kt` (IA + bibliothèque d'ouvertures), `ChessGame.kt` (partie + compte à rebours), `ChessRoom.kt` (salle TV),
`ChessHttp.kt` (routes `/chess`), `ChessTransport.kt` (LAN + relais), `ChessPieces.kt`, `ChessTvLayout.kt` ;
`core/tv/CombinedRoutes.kt` ; `resources/castbridge/chess/play.html`. TV : `ChessActivity.kt`, `ChessTvView.kt`,
`ChessHub.kt`, `ic_t_chess.xml`. Téléphone : `ChessScreen.kt`. Tests : `ChessEngineTest` (perft), `ChessAiTest`,
`ChessRoomTest` (horloge, salle, HTTP), `ChessRelayTest` (faux relais), `ChessLayoutTest`.
