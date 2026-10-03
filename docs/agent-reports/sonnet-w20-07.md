STATUT: TERMINÉ (audit Opus obligatoire : surface Internet, limites, équité, journaux)
CAHIER: w20-07 · MODÈLE: sonnet · BRANCHE: claude/w20-07-anti-triche-2 (rebasée sur integration/agents actuel, avec w20-04 ; la première livraison, e379304a, est remplacée) · COMMIT: celui qui contient ce rapport (voir `git log -1`)
JETONS: inconnu
PORTE: `:server-play:test --tests 'castbridge.play.guard.*'` + `:core:test --tests 'castbridge.core.quiz.online.*'` → VERT
SUITE COMPLÈTE: `:core:test :server-play:test :sender:compileDebugKotlin :receiver:compileDebugKotlin` → VERT : `:core` 3 547 tests, 0 échec ; `:server-play` 183 tests, 0 échec ; `:sender` et `:receiver` compilent. Au passage complet, 3 tests de `:server-play` (`LimitsTest`, `Audit2Test` ×2) encodaient l'ancien comportement « le bon code est refusé depuis une adresse bloquée » : réécrits, puis `:server-play:test` relancé seul : vert. Aucun test instable rencontré.
SYMBIOSE: cap=play1 · proto=play-v1 (champs additifs : `error.retryAfterMs`, `room.ranked`, `room.rankNote`) · reason=PLAY_BUSY,BAD_NAME · deux écrans=— (aucun écran touché : les deux applications ignorent les clés inconnues ; l'affichage de `BAD_NAME` / `PLAY_BUSY` et de `rankNote` est à câbler par w20-05 / w20-06, non fait ici ; `C/sync/Reason.kt` n'existe pas sur cette branche)

## Rouge d'abord (règle R1)
`GuardWiringTest` (API existante seule), avant tout code :
```
2 tests completed, 2 failed
GuardWiringTest threeInvalidMessagesCloseWith1008 : AssertionError: le troisième invalide ferme la session
GuardWiringTest refusedPseudonymsAnswerBadNameWithAReason : AssertionError: « Admin CastBridge » ne doit pas entrer
```
Puis vert après implémentation. Les autres tests ont été écrits avec le code (BotScore, Pseudonym, Limits, PlayRedact : cœur pur ; fuzz, limites, pings, relais, journaux : service).

## Déjà fait par w20-03 (non refait, utilisé tel quel)
Seau ping/pong, plafonds /64 et /48, `BadCodeCounter` borné, reprise jamais bloquée par adresse, join d'un siège refusé, cookie par onglet, RTT = minimum des 8 derniers plafonné, proxy de confiance obligatoire, `OriginCheck` (`null` accepté seulement avec ticket), grâce `min(rtt, 1 s)` et attente de la table avant `reveal`, `RttBook.relayed`. w20-07 en ajoute les **preuves** (`PingPongTest`, `RelayFairnessTest`).

## Ce qui est nouveau
Cœur (`C/quiz/online/`) : `BotScore`, `Pseudonym` + `blocklist.txt`, `Limits`, `PlayRedact` ; `BadCodeCounter` : quota de jour (mémoire O(1)) et `retryAfterMs` ; `RoomCode.nearMiss` ; `ServerRoom` : `botResults()`, `isRanked()`, `unrankedSeats()`, `noteNearMiss()` (rotation), `AnswerRecord` additif (`choice`, `correct`), `room.ranked` / `room.rankNote` dans la vue du siège, en fin de partie ; `PlayReason.PLAY_BUSY` et `BAD_NAME`, `retryAfterMs` structuré (`PlayReason`, `ServerMsg.Error`, codec : champ écrit seulement s'il est > 0) ; `toString` des messages porteurs de secret (T-18).
Service (`server-play/…/guard/`) : `PlayGuard` (point d'entrée unique des gardes), `MessageSchema` + `InvalidTally`, `LimitFilter`, `LogRedactor` (seule voie de journal : une ligne JSON ECS). Points d'appel dans le code existant (volontairement petits) : `RoomRegistry.kt` (décodage, refus avant aiguillage, débit de salle, journal, rotation depuis `join`, `PLAY_BUSY` structuré), `ConnectionLimits` (porte de débit + plafond relevé), `PlayServer` et `PlayFallbackController` (verdict `RATE` → 429 + `Retry-After`), `PlayConfig` (2 variables).
Non créés, faute d'objet : `PingPong.kt` et `RelayFairness.kt` (le comportement vit dans `ServerRoom` / `RttBook`, déjà testé ; les tests de garde sont dans `guard/`).

## Table des limites EFFECTIVES
| Limite | Valeur | Réglage | Au dépassement |
|---|---|---|---|
| Nouvelles connexions / adresse | 60 / min, 600 / h (CGNAT : 40 élèves tiennent dans la minute) | `CASTBRIDGE_PLAY_CONN_PER_MIN` (minute seule ; hors tests de charge, ne pas toucher) | HTTP 429 avant l'upgrade, `Retry-After` en secondes, journal `play.limit.exceeded` |
| Nouvelles connexions globales | 60 / s | `CASTBRIDGE_PLAY_CONN_PER_SEC` | idem |
| Connexions ouvertes / adresse (IPv4, /64) | 8 ; `8 + appareils assis` (au plus 64) si ≥ 8 JOUEURS distincts connectés depuis ≥ 30 s sont assis dans une même salle (spectateurs jamais comptés) | `CASTBRIDGE_PLAY_MAX_PER_IP`, `CASTBRIDGE_PLAY_MAX_PER_IP_SHARED` | 429, `Retry-After: 10` |
| Nouvelles connexions / /48 IPv6 | 300 / min (avant le seau global) | constante | 429, `Retry-After` |
| Connexions ouvertes / /48 IPv6 | 64 (inchangé) | constante | 429 |
| Entrées en salle / appareil | 20 / min | constante | `PLAY_BUSY` + `retryAfterMs`, message ignoré |
| Messages / salle | 300 / s, rafale 600 (8 joueurs à 10/s = 80/s) | constante | `PLAY_BUSY` + `retryAfterMs`, message ignoré |
| Codes faux | adresse : 30 / 5 min et **5 000 / jour** ; paire adresse + appareil : 30 / 5 min et **300 / jour** ; /48 IPv6 : 120 / 5 min, aucun quota de jour. Le BON code entre toujours (salle cherchée d'abord) | constantes | `PLAY_BAD_CODE` + `retryAfterMs` (reste de la fenêtre la plus longue), rien n'est compté tant que c'est bloqué |
| Codes proches d'un code vivant / salle | 50 ⇒ nouveau code, salle d'attente seulement, au plus 1 fois / min | constante | évènement `codeRotated`, journal |
| Messages invalides / connexion | 3 `BAD_REQUEST` / min ⇒ fermeture 1008 ; `UNSUPPORTED` (type inconnu) ne compte pas | constante | fermeture, journal |
| Journal | au plus 1 ligne / s par (action, adresse) ; compteur `suppressed` sur la suivante | constante | — |
| Imbrication JSON | 6 niveaux ; message ≤ 2 Ko | constante | `BAD_REQUEST` |
| Seaux en mémoire | ≤ 100 000 clés, éviction du moins récent (une clé évincée repart pleine : jamais de blocage par éviction) | constante | — |
| Ping d'application | 5 s en salle d'attente et en jeu, aucun après la fin (le brief disait 25 s hors question : gardé à 5 s car un RTT doit exister avant la 1re question, coût ≈ 12 octets/s/connexion) ; ping de transport 25 s | constantes | — |

Pourquoi plus large que la conception (20/min, 8 ouvertes, 100 codes/jour) : un CGNAT mobile, une école ou une box familiale partagent UNE adresse ; punir l'adresse punit des dizaines de joueurs honnêtes. Aucune liste noire permanente, aucun blocage par pays, aucune empreinte de navigateur.

## Choix à relire
- `BotScore` (après l'audit) : FAST 40 (≥ 90 % sous 300 ms, ≥ 8 réponses) + 20 (≥ 90 % sous 150 ms) ; UNIFORM 25 (CV < 0,08, moyenne ≥ 150 ms) ; ACCURATE 10 (> 95 % sur ≥ 10) ; JUMEAUX 40 (MÊME appareil, mêmes choix à ≤ 60 ms sur ≥ 80 % de ≥ 5 questions). Seuil 70 : **ce siège** sort du classement. Robot parfait à 100 ms : 70 ; humain 2 s ± 1 s à 70 % : < 30 (200 graines) ; élève rapide à 200-290 ms, tout juste : 50. Un robot qui s'aligne juste au-dessus de 150 ms avec de la gigue (40 + 10 = 50) n'est PAS attrapé : choix assumé (mieux vaut laisser passer un robot tiède que signaler un élève). Seul le Duel journalise des réponses.
- Pseudonyme : le point est permis (« Amina N. ») ; « Prof » aussi (décision de l'audit, contraire au § 3.3 de la conception : « Prof » n'est plus une usurpation, « Admin », « Modo », « Support », « CastBridge »… le restent). Mots courts en mot entier seulement. Fragments cherchés mot par mot ; lettres isolées recollées (« c.o.n ») ; Niger, Nigeria, Nigérian, Nigérien, « Sasha Wolf », « Fagot », « Dick », « Pedo », « Anal » passent ; « nigger », « faggot » (formes non réduites, entrées `~`) et « ashawo » sont refusés. Marques de ton (ɔ̀ ɛ́ ŋ̀) acceptées (2 au plus par lettre) ; écritures mélangées refusées.
- Les chiffres/symboles « leet » `@ $ ! €` ne sont pas dans l'alphabet : « S@lope » est refusé comme `CHARS`.

## Points pour l'audit Opus
1. Rotation du code : un énumérateur proche d'un code vivant peut la déclencher (50 frappes à un symbole, depuis n'importe quelles adresses) ; borne : salle d'attente, 1 / min. Une famille qui tape le code à ce moment-là doit le relire.
2. `Limits` : état synchronisé par un seul verrou ; vérifier qu'aucun chemin ne l'appelle sous le verrou d'une salle d'une façon qui bloque (appels courts, O(1)).
3. Journal : `PlayRedact.scrub` est un filtre à motifs (jeton = 32 hexadécimaux, ticket `v1.`, `cbx1`, cookies) : un secret d'une autre forme y échapperait ; la garde réelle est que le service n'écrit que des champs nommés (`RedactionTest` sur 50+ lignes réelles et test de source : aucune autre sortie).
4. `ConnectionLimits.admit` consomme un jeton de débit avant le contrôle des places : un refus de place coûte un jeton à l'adresse (voulu, ça limite aussi le martèlement).
5. Surface de conflit avec w20-04 : `RoomRegistry.kt` (ligne `PLAY_BUSY` de `create`, `attachAndForward`), `PlayReasonTest` (liste des codes : ajouter `PLAY_BUSY`, `BAD_NAME` en fin de liste lors de la fusion).
6. `retryAfterMs` n'est pas encore lu par les clients (TV, page `/play`).

## Non vérifié
Aucune exécution sur le serveur de production ni sur matériel ; pas de test de charge des seaux sous 500 salles (w20-11) ; collusion « même pièce » (T-15) non traitable par construction.


## Audit Opus (2e livraison) : corrigés
Rouge d'abord, par assertion (`AuditOpusTest`, `AuditOpusGuardTest`), puis vert :
- **B1** classement PAR SIÈGE (`BotScore.rankedSeats`, `ServerRoom.isRanked(playerId)`) ; `BotScore.ranked(Collection)` supprimée ; test : un intrus parmi 8 sort seul, les 7 autres restent classés. **I6** `ranked` n'est ni calculé ni diffusé hors fin de partie, et seulement dans la vue du siège concerné.
- **B2** BOT_IMPOSSIBLE et le code « réservées » supprimés (mort ou faux positif) ; BOT_UNIFORM ignoré sous 150 ms de moyenne ; BOT_TWINS sur le seul `deviceHash` ; tests : élève rapide non signalé, deux élèves d'une même adresse non jumeaux.
- **B3** quota de jour par paire adresse + appareil (300), adresse seule 5 000, /48 sans quota de jour ; le BON code n'est jamais refusé par un compteur de mauvais codes (test : 400 faux d'un élève, puis un autre appareil de la même adresse entre). Le blocage ne fait plus que répondre `PLAY_BAD_CODE` + `retryAfterMs` aux codes faux sans les compter ; le débit d'un énumérateur reste borné par le seau de messages (10 / s) et les limites de connexion.
- **I1** blocklist en forme réduite, fragments mot par mot, entrées `~` non réduites, tests « doivent passer ». **I2** marques de ton, anti-Zalgo. **I3** `UNSUPPORTED` ne compte pas ; nom de 17+ caractères ⇒ `BAD_NAME` motif LENGTH (le fil accepte 64). **I4** plafond relevé : joueurs seulement, connectés depuis ≥ 30 s, `8 + assis` ≤ 64, siège retiré à la fermeture. **I5** seau `/48` 300 / min avant le global. **I7** journal échantillonné.
- Mineurs : écritures mélangées refusées, ı sans point, petites capitales, cyrillique к м н т в, mots à points ; `pseudo` / `device` du journal = HMAC-SHA-256 à clé tirée au démarrage et renouvelée chaque jour (le jour est passé par `LogRedactor`, le cœur ne lit aucune horloge) ; `Json.write` échappe U+2028/2029 (test) ; aucun `log.event` sous `synchronized(salle)` ; `reset()` vide le journal des réponses et la mémoire de `botResults` (une revanche n'hérite de rien, test).
- Fusion avec w20-04 : conflits `PlayProtocol`, `PlayServer`, `RoomRegistry`, `Audit2Test`, `AuditFixesTest`, index résolus en gardant les deux comportements ; `Create` (avec l'activation `cbx1` et les locations de w20-04) a aussi un `toString` sans secret. `PLAY_MAINTENANCE` n'est pas une entrée de `PlayReason` sur cette branche (constante du registre) : non ajoutée à `PlayReasonTest`.
- Ce qui reste : `retryAfterMs`, `BAD_NAME` et `rankNote` ne sont lus par aucun client (w20-05 / w20-06) ; pas de test de charge des seaux (w20-11).
