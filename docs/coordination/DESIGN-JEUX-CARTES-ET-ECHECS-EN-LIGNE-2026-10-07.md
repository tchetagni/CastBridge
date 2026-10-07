# Conception — Échecs en ligne avec jetons ; deux nouveaux jeux de cartes (Fap-Fap, Agraham Tia), solo et multijoueurs, avec ou sans jetons

Date : 2026-10-07. Décision du propriétaire : « permets que les échecs soient jouables en ligne avec le jeton (NDEM et MBOKO) et introduis un nouveau jeu fap-fap et Agraham Tia, solo et multijoueurs avec ou sans jetons » ; « les 2 jeux nouveaux sont des jeux de cartes ». Cadre : W20 (jeu en ligne : TV activées seulement, téléphones relayés par leur TV, aucun téléphone ne parle au service), W22 (jetons NDEM/MBOKO, grand livre serveur, **option B** : blocage `cbe1` signé par l'API porté par la TV avec son ticket `cbp1`, résultat `cbr1` signé par le service, réglé par l'API), relais (DESIGN-RELAIS : une TV reliée à un téléphone synchronisé est **en ligne par relais**).

## 0. En dix lignes
- **Une seule plateforme de parties** pour tous les jeux à tour de rôle : échecs, Fap-Fap, Agraham Tia (et plus tard dames, Songo…). Trois cadres : **solo** (contre l'ordinateur de la TV), **maison** (la TV héberge la salle, les téléphones du foyer jouent par elle : code + QR, jeton de place, comme `ChessRoom`), **en ligne** (TV à TV via `castbridge-play`, téléphones relayés par leur TV, ticket `cbp1` + activation + preuve de clé d'installation).
- **Jetons facultatifs** : toute partie en ligne peut être **libre** (sans enjeu) ou **avec mise** en NDEM (sans valeur) ou MBOKO (enjeu). La mise suit **exactement** le mécanisme W22 option B déjà conçu pour le Quiz : la TV bloque la mise (`cbe1` signé par l'API), le service produit un **résultat signé** (`cbr1`), l'API règle. Solo et maison : **jamais de jetons** entre places d'une même TV (un seul compte par TV, W22 § 1.6) ; le solo peut **gagner** des NDEM d'entraînement plafonnés (politique serveur), jamais de MBOKO.
- **Règles des jeux** : les échecs sont déjà implémentés (moteur, IA, salle maison, client de relais prêt sans serveur). **Fap-Fap et Agraham Tia** : jeux de cartes dont les règles **doivent être fournies par le propriétaire** (fiches § 4) ; rien n'est inventé. Le moteur de cartes générique est construit d'abord ; chaque jeu est une **fiche de règles exécutable** (pure, testée) branchée dessus.
- Anti-triche : l'autorité de l'état est **le serveur** en ligne et **la TV** à la maison ; les téléphones ne voient que leur main ; coups vérifiés par le moteur ; journal signé des parties avec mise (W22 § 5).

## 1. Vue opérationnelle
| Cadre | Qui héberge | Qui joue | Jetons | Internet |
|---|---|---|---|---|
| **Solo** | TV (`GameAi`) | 1 joueur à la télécommande ou au téléphone | NDEM d'entraînement plafonnés (décision § 6), jamais de mise | non |
| **Maison** | TV (`GameRoom`, code + QR, 10 personnes) | 2 à N téléphones du foyer + télécommande ; spectateurs | aucun | non |
| **En ligne libre** | `castbridge-play` (salle par code, TV à TV) | 2 à N TV, chaque TV avec ses téléphones relayés | aucun | TV en ligne (direct ou **par relais**) |
| **En ligne avec mise** | idem | TV **activées en production** | NDEM ou MBOKO, blocage `cbe1`, résultat `cbr1`, règlement API | idem ; TV d'essai : parties libres seulement |

Parcours : « Jeux » › choisir le jeu › choisir le cadre › (en ligne) choisir « libre » ou « mise » (montant parmi une échelle, solde affiché, « vous perdez la mise si vous abandonnez ») › salle : code à partager (TV à TV) › partie › résultat identique sur toutes les TV › règlement visible dans « Mes jetons ».

## 2. Vue fonctionnelle (plateforme)
| Élément (core, pur) | Rôle |
|---|---|
| `GameRules<S,M>` | interface d'un jeu à tour de rôle : `initial(seed, players)`, `legal(S, player)`, `apply(S, M)`, `view(S, player)` (ce que **ce** joueur voit : sa main, pas celles des autres), `outcome(S)` (en cours / gagnant(s) / nul / scores), `serialize` (format d'état commun TV, téléphone, web, relais, comme `docs/CHESS.md` § 5.1) |
| `CardDeck` | jeux de 32 ou 52 cartes, mélange **déterministe par graine** (la graine vient de l'autorité : TV ou serveur ; en ligne avec mise, graine = engagement serveur révélé en fin de partie pour vérification), distribution, pioche, défausse, levées, combinaisons |
| `TurnEngine` | tours, horloge par joueur (celle de l'autorité), coups numérotés (`STALE` sur doublon ou retard, comme les échecs), abandon, déconnexion (pause puis forfait à 60 s en ligne, comme le quiz) |
| `GameRoom` (TV) | salle maison générique (code 4 chiffres + QR, jeton de place, spectateurs), vues par joueur, page web `/jeux/<id>` pour les téléphones (vanilla JS), remplace l'écriture d'une salle par jeu |
| `Stake` (W22) | `cbe1` (blocage), `cbr1` (résultat), échelle de mises, plafonds jour/semaine/mois par identité, abandon = perte de la mise, nul = remboursement, frais de plateforme = politique serveur |
| `GameAi` | adaptateur par jeu : échecs = `ChessAi` existant ; cartes = IA simple (gloutonne) puis réglable |
| `GameJournal` | journal signé par l'autorité des parties avec mise (coups, graine, résultat), envoyé au mieux (direct ou coursier du téléphone), anti-triche W22 § 5 |

**Serveur de jeu (`castbridge-play`)** : type de salle `game:<id>` à côté de `quiz`, règles exécutées **côté serveur** (même code pur, module JVM partagé), état envoyé par vue joueur (SSE + POST existants, WebSocket), 40 kbps suffisent (coups de quelques octets). **API** : routes W22 de blocage/règlement (déjà conçues pour le quiz), échelle et plafonds par jeu.

**Échecs en ligne** : `ChessRelayClient` existe et est testé contre un faux serveur ; `docs/CHESS.md` § 6 décrit les routes `/api/chess/v1/*` : elles sont **réalisées dans `castbridge-play`** (pas dans l'API) via la salle `game:chess`, et le drapeau TV `POST /api/chess/config?online=1` devient inutile (capacité annoncée par `/play/.well-known/caps`).

## 3. Discrétion, relais, compatibilité
- En ligne par relais (téléphone synchronisé) : même règles que le quiz (DESIGN-RELAIS § 2.5) ; les cartes se jouent bien à 40 kbps.
- Aucun secret dans les journaux ; la main d'un joueur n'est jamais envoyée à un autre ; l'état complet n'existe que chez l'autorité.
- TV ancienne : la tuile « En ligne » dit « Mettez la TV à jour » (capacités) ; aucun changement des protocoles existants du quiz.

## 4. Fiches de règles à fournir par le propriétaire (une par jeu ; rien n'est codé sans elles)
Pour **Fap-Fap** et **Agraham Tia**, remplir :
1. Jeu de cartes : 32 ou 52 cartes ? jokers ? ordre des valeurs (as haut/bas) ? couleurs importantes ?
2. Joueurs : minimum, maximum ; équipes ou chacun pour soi ; sens du tour.
3. Distribution : combien de cartes par joueur ; pioche ; talon ; retourne.
4. Tour de jeu : ce qu'un joueur **doit** et **peut** faire ; obligation de suivre ; atouts ; combinaisons ; « passer ».
5. Fin de manche et de partie : condition d'arrêt ; comptage des points ; égalités.
6. Mise (si le jeu se joue traditionnellement avec enjeu) : qui mise quoi, quand ; pot ; répartition ; abandon.
7. Variantes locales à retenir et à exclure ; vocabulaire exact des coups (pour les textes TV/téléphone).
8. Une partie exemple pas à pas (3 à 5 tours) : elle devient le **test de référence** du moteur.
Format accepté : texte, photo d'un carnet, vidéo ; l'orchestrateur transcrit en fiche exécutable et vous la fait valider avant tout code.

## 5. Chantiers
- **games-G1 Plateforme (Sonnet)** : `GameRules`, `CardDeck`, `TurnEngine`, `GameRoom` générique (migration de `ChessRoom` dessus sans changer son comportement), `GameJournal`, page web `/jeux/<id>`, hub `Games.all` + téléphone `PHONE_GAMES` ; tests JVM (graine, vues par joueur, `STALE`, reprise).
- **games-G2 Échecs en ligne + mises (Sonnet)** : salle `game:chess` dans `castbridge-play` (même module de règles), caps, `ChessRelayClient` branché sur `/play/`, `Stake` option B (`cbe1`/`cbr1`) côté API et service, échelle et plafonds, écrans « libre / mise », règlement et journal ; TV d'essai = libre seulement.
- **games-G3 Fap-Fap** et **games-G4 Agraham Tia** : après réception des fiches § 4 : règles pures + tests de la partie exemple + IA simple + textes ; branchées sur G1/G2.
- **games-G5 Quiz en ligne misé (Sonnet)** : réalisé (2026-10-07) : le Quiz de `castbridge-play` mise en NDEM ou MBOKO comme les échecs (option B de W22 : `cbe1` par TV, une mise par siège, `cbr1` signé par le service, règlement par l'API ; `game.quiz` dans la politique, migration `V69`) ; la mise virtuelle de la maison devient la « Compétition à points » ; voir `docs/QUIZ.md` § 5 bis et `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` § 15.
- Guide utilisateur : section Jeux à compléter à la livraison.

## 6. Décisions du propriétaire
1. **Solo et NDEM** : le solo rapporte-t-il des NDEM d'entraînement (plafond serveur, ex. 50/jour) ? Recommandation : oui, NDEM seulement, jamais de MBOKO.
2. **Échelle de mises** par jeu (NDEM : 10, 20, 50, 100, 200 ; MBOKO : 1, 2, 5, 10) et **frais de plateforme** (W22 § 1.5 « politiques futures ») : à fixer ; recommandation : 0 % au lancement, réglable.
3. **Abandon et déconnexion** avec mise : perte de la mise après 60 s de silence (comme le quiz) ; nul = remboursement. Recommandation : oui.
4. **Règles de Fap-Fap et d'Agraham Tia** : fiches § 4 à fournir.
5. **Ordre** : échecs en ligne d'abord (tout existe sauf le serveur), puis le premier jeu de cartes dont la fiche arrive.
