# w5-03 — Cœur Quiz : commodités à jetons (`QuizBoosts`), « points de défi » séparés des jetons, état JSON

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W5a-1** (vague W5a) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Quiz*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 5a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 6.1, § 6.2, § 15 (ligne Quiz). Branche `claude/sonnet-w5-03`. Rapport : `docs/agent-reports/sonnet-w5-03.md`. Indépendant de w5-02 : ce cahier définit l'**interface** `QuizBoosts` ; le branchement au porte-jetons réel est fait sur la TV par w5-17. **Décision du propriétaire (P4) : seul le Quiz consomme des jetons** ; **décision d'architecte : jamais de mise redistribuée en jetons achetés.**

## Objectif
`QuizGame` (Millionnaire) offre trois commodités payables en jetons, toujours **optionnelles** et **confirmées** : **Seconde chance** (après une mauvaise réponse ou un temps écoulé, reprendre la partie à la même question, une fois par partie), **Joker en plus** (réutiliser un 50:50 ou un Avis du public déjà consommé, au plus deux fois par partie), **Changer de question** (une fois par partie, question suivante de même difficulté). Le mode « Compétition avec mise » reste tel quel **mais** son unité devient **« points de défi »** (jamais « jetons »), sans aucun lien avec les jetons achetés. La base du jeu reste gratuite et inchangée.

## Pourquoi (preuves)
- `C/quiz/QuizGame.kt:25` (`Joker{FIFTY,AUDIENCE,PHONE}`, `jokersUsed`, `canUse` `:136`, `beginJoker` `:147`), phases `:46`, `End` `:52`, `finish` `:197`, état JSON `:222` ; `Ladder.DEFAULT` (paliers 5 et 10).
- `C/quiz/QuizRoom.kt:44-45` (`Play.STAKE("Compétition avec mise")`), `:65` (`stake = 100L`), `:219-243` (mises, `Pot.split`) ; `C/quiz/Wallet.kt` (`WalletProvider.unit = "jetons"`, `VirtualWallet(1000)`).
- `R/QuizActivity.kt:303-343` : menu « avec mise » 50/100/200 « jetons » (texte TV : w5-17).

## Fichiers possédés
`C/quiz/QuizGame.kt`, `C/quiz/QuizRoom.kt`, `C/quiz/Wallet.kt`, `C/quiz/QuizHttp.kt`, nouveau `C/quiz/QuizBoosts.kt`, tests `CT/quiz/**` (créer le paquet si les tests Quiz sont à la racine `CT/` : alors modifier **ceux-là** et le dire). **Hors zone** : `C/tokens/**` (w5-02), `C/shop/**` (w5-01), `R/**`, `S/**`, `C/quiz/{QuizBank,QuizHistory,QuizLots,QuizPacks,QuizDuel}.kt` (lecture seule).

## Étapes
1. `QuizBoosts` (interface) : `enum Boost { SECOND_CHANCE, EXTRA_JOKER, SWAP_QUESTION }` ; `fun available(b: Boost): Boolean` (le fournisseur sait si des jetons existent : TV en essai ⇒ faux) ; `fun cost(b: Boost): Long` ; `fun balance(): Long` ; `fun charge(b: Boost, gameId: String): Boolean` (débite, faux si insuffisant) ; `object NoBoosts : QuizBoosts` (tout faux : comportement actuel). **Aucune implémentation payante ici.**
2. `QuizGame` : constructeur gagne `boosts: QuizBoosts = NoBoosts` et un `gameId` ; compteurs `boostsUsed: Map<Boost, Int>` ; `canBoost(b)` = `boosts.available(b) && boostsUsed[b] < max(b)` (max : 1 / 2 / 1) **et** phase compatible : `SECOND_CHANCE` seulement en `REVEALED` après `End.WRONG|TIMEOUT` **avant** `FINISHED` (introduire une phase `LOST_OFFER` de 10 s où le jeu propose la seconde chance ; sans réponse ⇒ `finish`) ; `EXTRA_JOKER` en `QUESTION` quand `FIFTY` ou `AUDIENCE` est dans `jokersUsed` (jamais `PHONE` : il dépend d'un autre joueur) ; `SWAP_QUESTION` en `QUESTION` avant tout joker sur cette question. `applyBoost(b, now): Boolean` = `boosts.charge(...)` puis effet (reprise à la question courante avec un nouveau chrono ; retrait du joker de `jokersUsed` ; question suivante tirée par le fournisseur de questions existant à la même difficulté, l'ancienne comptée comme posée). Les gains et paliers sont inchangés ; `End` inchangé ; un `WON` après une seconde chance porte `boosted = true` dans l'état JSON (pour le tableau des scores : marqué « avec jetons », n'écrase pas un record sans jetons : `HighScores` gagne un drapeau additif).
3. `QuizRoom` : transmet `boosts` à `QuizGame` (constructeur additif, défaut `NoBoosts`) ; les commandes des téléphones (`/quiz/api/act`) **ne peuvent pas** déclencher une commodité (seule la télécommande / l'hôte le peut : `act=boost` refusé avec « réservé à la TV »).
4. « Points de défi » : `WalletProvider.unit` devient `"points de défi"` ; `Play.STAKE.label = "Défi en points"` ; `VirtualWallet` renommé `ChallengePointsWallet` (alias déprécié `VirtualWallet` conservé pour ne rien casser) ; KDoc : « sans valeur, non achetables, jamais reliés aux jetons de la boutique ». Aucun changement de règle de partage de la cagnotte (ce sont des points).
5. `QuizHttp` : l'état exposé aux téléphones porte `boosts: {available, used, costs}` et `unit` (pour l'affichage « points de défi ») ; rien d'autre ne change.
6. Tests : seconde chance acceptée une fois / refusée la seconde ; refusée sans jetons (`NoBoosts`) ; joker en plus deux fois puis refus ; jamais pour `PHONE` ; changer de question une fois ; chrono réarmé ; `boosted` dans l'état ; téléphone qui tente `act=boost` ⇒ refus ; `unit` = « points de défi » ; tous les tests Quiz existants verts sans modification de leurs attentes (sauf le libellé de `STAKE`).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.quiz.*' --tests '*Quiz*'   # vert
grep -n '"jetons"' android/core/src/main/kotlin/castbridge/core/quiz/Wallet.kt   # 0 hit
grep -n 'NoBoosts' android/core/src/main/kotlin/castbridge/core/quiz/QuizGame.kt   # ≥ 1 (défaut gratuit)
grep -rn 'castbridge.core.tokens' android/core/src/main/kotlin/castbridge/core/quiz   # 0 hit (pas de dépendance au porte-jetons)
```

## Cas limites
- Partie en Entraînement : aucune commodité (déjà sans enjeu) : `canBoost` faux.
- Duel : aucune commodité (équité entre téléphones).
- `charge` renvoie faux après `canBoost` vrai (course) : message « jetons insuffisants », partie inchangée.
- Sauvegarde/reprise d'une partie (`QuizGame` JSON) : `boostsUsed` et la phase `LOST_OFFER` sont sérialisés.

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas relier `WalletProvider` aux jetons ; aucun gain réel, aucun mode à jetons en Duel ; ne pas modifier le tirage des questions (`QuizBank`) ; pas de texte « jetons » dans la cagnotte ; français.

## Rapport
`STATUT`, interface `QuizBoosts` finale (pour w5-17), phases ajoutées, liste des textes à afficher sur la TV.
