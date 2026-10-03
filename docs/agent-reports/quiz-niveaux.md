# Quiz « Quel niveau ? » : tous les niveaux remplis, grille compacte (2026-10-03)

Branche `claude/quiz-levels-available`. Cahier du propriétaire : « Je veux voir tous les niveaux remplis au lieu de bientôt », puis « les quiz vont de 6e en 4e au lieu de tous les niveaux » (TV 0.14.23), puis « ce sont les questions qui sont réservables, pas les niveaux ».

## Causes
1. `QuizActivity.count(f)` comptait `room.bank` = la petite banque de base (320 questions), jamais les fichiers par niveau : seul CM2 apparaissait rempli.
2. `QuizBank.count` / `poolOf` excluent les questions « review » (100 % des questions embarquées et des lots) ; la TV jouait en plus sur le canal `stable` par défaut.
3. (0.14.23) Une fiche portait une note longue et s'élargissait au focus : la ligne de 14 niveaux du Secondaire débordait de l'écran 1280x720 @160 dpi, les niveaux suivants n'étaient ni visibles ni atteignables.

## Règle des questions en relecture (décision du coordinateur)
Les questions « en cours de relecture » SONT jouables. `QuizEdition.REVIEW_PLAYABLE = true` : la TV joue sur le canal bêta (`QuizEdition.playChannel`), les marques « bêta : non validé » restent visibles en jeu ; les questions rejetées ou à corriger ne sont jamais jouées. Le tirage prend les approuvées d'abord (`drawDetailed(approvedFirst = true)`). L'information « contenu en cours de relecture » est dans la ligne de détail, jamais cachée.

## Questions réservables (décision du propriétaire, dernière version)
Aucun niveau n'est réservé en entier : chaque niveau a des questions LIBRES (≈ 70 %) et RÉSERVABLES (≈ 30 %, marquées une à une). Index `embedded/index.json` : par niveau `count` (total), `freeCount`, `reservedCount` ; sans le découpage (ancienne forme) tout est libre. Plus aucune lecture de `reservedNotEmbedded` / `reservedLevels`. `QuizEdition.TRIAL_OPEN = true` : les questions réservables sont jouables pendant l'essai ; nombre de la fiche = freeCount + reservedCount (si TRIAL_OPEN) et le détail ajoute « dont N questions en essai » ; à false = freeCount seul. États : AVAILABLE dès qu'une question est jouable ; RESERVED (« Réservé · en location ») seulement si TOUTES les questions du niveau sont réservables et l'essai fermé ; SOON si rien du tout.

## Logique pure (core/quiz)
- `QuizLevelAvailability` : états, comptes (index.json seul, base, lots installés ; total = max(banque, index) car un lot du même niveau remplace les ids embarqués), `compactText` (ligne unique de la fiche : « 2 000 questions », « Réservé », « bientôt »), `detailText` (« CP : 2000 questions · ≈ P parties sans répétition (objectif G) · alias · dont N en essai · contenu en cours de relecture »), table `EMBEDDED_KEYS` testée contre le vrai index (chaque niveau embarqué = exactement un niveau du sélecteur et inversement hors alias et niveaux à importer Tle, L1-L3).
- `LevelGridLayout` : `columns(widthDp, cardWidthDp)` (1280 dp → 5 colonnes, 190 dp de carte, 16 de marge, 80 de bords) et `move(index, dir, count, cols)` : gauche/droite suivent l'ordre de lecture avec retour à la ligne, haut/bas d'une rangée, bas depuis une colonne sans carte en dernière rangée = dernière carte. Testé : tous les niveaux atteignables pour 1, 4, 12, 13, 14, 24 cartes et 1 à 6 colonnes.
- Alias : SIL = questions du CP (id `@SIL`) ; Form 4 = 1000 questions de Form 3 + 1000 de Form 5 entrelacées, déterministe (id `@Form-4`), sources lues l'une après l'autre (un seul niveau en mémoire, R-11). Un lot loué « cp » ne sert pas SIL.

## Écran (QuizActivity, étapes level et field)
Fiches compactes de largeur fixe 190 dp (ne s'élargissent plus au focus), grille qui passe à la ligne (colonnes calculées de la largeur d'écran et de la densité), ScrollView vertical (la vue suit le focus), D-pad par `LevelGridLayout.move`, ligne de détail unique sous la grille qui suit la fiche focalisée. Fiches « bientôt » et « Réservé » focalisables, non choisissables (message en pied). Filières (L1-L3) et mêmes rendus. `bankNote` est gardé pour « Comment jouer ? » et Culture générale. Rien dans android/sender ni sur l'accueil TV.

## Vérification visuelle (émulateur emulator-5580, 1280x720 @160, build non verrouillée d'essai)
`docs/agent-reports/quiz-niveaux/selecteur-primaire.png` (12 niveaux, 3 rangées), `selecteur-secondaire.png` (14 niveaux, 3 rangées, Upper Sixth atteint à la télécommande, détail « Upper Sixth : 2000 questions · ≈ 133 parties… »), `selecteur-superieur.png` (3 niveaux). Taille et densité remises à l'origine, application arrêtée.

## État final de chaque niveau (index actuel : ancienne forme, 24 niveaux de 2000 ; essai ouvert)
| Niveau | État | Fiche |
|---|---|---|
| SIL | AVAILABLE (alias CP) | 2 000 questions |
| CP, CE1, CE2, CM1, CM2 | AVAILABLE | 2 000 questions |
| Class 1 à 6 | AVAILABLE | 2 000 questions |
| 6e, 5e, 4e, 3e, 2nde, 1re | AVAILABLE | 2 000 questions |
| Tle | AVAILABLE (base seule) | 20 questions ; passera à N dès l'import de `tle` dans l'index |
| Form 1, 2, 3, 5 ; Lower, Upper Sixth | AVAILABLE | 2 000 questions |
| Form 4 | AVAILABLE (alias Form 3 + 5) | 2 000 questions |
| L1 | AVAILABLE (base seule) | 60 questions |
| L2, L3 | SOON (« bientôt ») | rien dans la base ni l'index tant que l'autre agent n'a pas importé l'index complet ; aucun niveau n'est réservé en entier |
Avec l'index complet (freeCount / reservedCount) : tout niveau non vide est AVAILABLE (test), et RESERVED n'apparaît que pour un niveau 100 % réservable avec l'essai fermé.

## Tests
`QuizLevelAvailabilityTest` (nouveau modèle, deux formes d'index) et `LevelGridLayoutTest` écrits d'abord : 4 rouges par assertion sur l'ébauche de la grille, puis verts. Tirages réels en salle (CP, Class 1, 6e, Form 1, Lower Sixth, Culture générale, SIL, Form 4) : 15 questions distinctes. Aucune attente existante modifiée.

## Risques et à faire par ailleurs
- Le jeu ne distingue pas encore les questions réservables dans le tirage : tant que `TRIAL_OPEN = true` tout est jouable ; pour la production il faudra exclure du tirage les questions marquées réservables (marque à définir par l'import de l'index).
- Total affiché approximatif (index et max banque/index) ; « Comment jouer ? » donne le compte exact après chargement du niveau.
- Questions « bêta : non validé » visibles en jeu (décision du propriétaire) ; retour en arrière par `REVIEW_PLAYABLE = false`.
- Sur la vraie TV (GaiaOS 720p 32 bits) seuls restent à confirmer : fluidité du défilement et des animations d'entrée des cartes, lisibilité à 3 m des fiches de 16 sp, temps de chargement d'un niveau (Form 4 : deux fichiers), mémoire après plusieurs changements de niveau.
