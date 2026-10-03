# Quiz « Quel niveau ? » : tous les niveaux gratuits remplis (2026-10-03)

Branche `claude/quiz-levels-available` (depuis `origin/integration/agents`). Cahier du propriétaire : « Je veux voir tous les niveaux remplis au lieu de bientôt ».

## Causes
1. `QuizActivity.count(f)` comptait `room.bank` = la petite banque de base (320 questions), jamais les fichiers par niveau chargés au lancement d'une partie : seul CM2 apparaissait rempli.
2. `QuizBank.count` / `poolOf` utilisent `playable`, qui exclut les questions « review » ; 100 % des questions embarquées et des lots sont « review » (0 approuvée dans `content/quiz/dist/coverage.json`) : même comptées, elles auraient été exclues du sélecteur et du tirage. De plus la TV jouait sur le canal `stable` du serveur (par défaut), qui bloque les « review ».

## Règle des questions en relecture (décision du coordinateur)
Les questions « en cours de relecture » SONT jouables. `QuizEdition.REVIEW_PLAYABLE = true` : la TV joue sur le canal bêta (`QuizEdition.playChannel`), donc les marques « bêta : non validé » restent visibles en jeu ; les questions rejetées ou à corriger ne sont jamais jouées. Le tirage prend les questions APPROUVÉES d'abord (`drawDetailed(approvedFirst = true)`), puis celles en relecture. Chaque fiche porte « contenu en cours de relecture » (ou « dont N en cours de relecture »), jamais cachée.

## Logique pure (`core/quiz/QuizLevelAvailability.kt`)
- États : AVAILABLE (n > 0), RESERVED (« Réservé · en location » : aucun contenu ET niveau dans l'index `reservedLevels`, à défaut `reservedNotEmbedded`), SOON (rien et non réservé). La liste réservée est LUE dans index.json, jamais codée.
- Comptage : index.json seul (aucun fichier de niveau lu pour compter), banque de base + lots installés (`countsOf`), total = max(banque, index) car un lot du même niveau remplace les ids embarqués ; l'index ne donne pas le détail approuvé/relecture : l'embarqué compte comme relecture.
- Drapeau d'essai `QuizEdition.TRIAL_OPEN = true` : un niveau réservé par la famille mais ouvert par ce drapeau affiche « essai ». À false (édition de production) le contenu embarqué d'un niveau réservé est ignoré. Testé avec les deux formes d'index (réservé non embarqué ; `reservedNotEmbedded` vide + `reservedLevels`) et un index sans liste.
- Table clé du sélecteur -> clé embarquée (`EMBEDDED_KEYS`) testée contre le vrai index : chaque niveau embarqué correspond à exactement un niveau du sélecteur, et inversement (hors alias et famille réservée) ; culture-generale n'a pas de niveau.
- Texte de fiche (`cardText`) : « N questions · ≈ P parties sans répétition (objectif G) » ou « G parties sans répétition garanties » (P = N / 15, sans historique : le sélecteur ne charge jamais un niveau).

## Alias (transparents)
- SIL : mêmes questions que le CP (fiche : « mêmes questions que le CP »). Questions du CP re-étiquetées niveau SIL, id suffixé `@SIL` (un lot CP installé ne les remplace pas).
- Form 4 (absent du catalogue, ni lot ni question) : 1000 questions de Form 3 + 1000 de Form 5, répartition régulière sur les ids, entrelacées (une de chaque tour à tour), déterministe ; id suffixé `@Form-4` ; fiche : « questions des Form 3 et Form 5 ». Les deux niveaux sources sont lus l'un après l'autre (un seul niveau complet en mémoire, R-11) puis relâchés. Aucun autre contenu inventé.
- Limite : un lot loué « cp » ne sert pas SIL (autre niveau) ; le jeu affiche « SIL » (pas « CP »).

## État final de chaque niveau du sélecteur (index actuel, édition d'essai)
| Niveau | État | Détail |
|---|---|---|
| SIL | AVAILABLE | alias CP, 2000 (relecture) |
| CP, CE1, CE2, CM1 | AVAILABLE | 2000 (relecture) |
| CM2 | AVAILABLE | 2000 (index) ; les 20 approuvées de la base restent jouées en premier |
| Class 1 à 6 | AVAILABLE | 2000 chacun |
| 6e, 5e, 4e, 3e, 2nde, 1re | AVAILABLE | 2000 chacun |
| Tle | RESERVED « Réservé · en location » | aucune question embarquée aujourd'hui ; AVAILABLE + « essai » dès que l'index en compte ; AVAILABLE sans « essai » avec un lot loué |
| Form 1, 2, 3, 5 | AVAILABLE | 2000 chacun |
| Form 4 | AVAILABLE | alias Form 3 + Form 5, 2000 |
| Lower Sixth, Upper Sixth | AVAILABLE | 2000 chacun |
| L1, L2, L3 | RESERVED | idem Tle ; l'étape filière compte par filière (le niveau choisi est alors chargé, un seul) |
Aucun niveau des parcours gratuits ne reste « bientôt » (test réel sur l'index embarqué, drapeau d'essai activé ou non).

## Écran (fin)
Étapes « level » et « field » : texte de fiche = `cardText` ; seules les fiches AVAILABLE se choisissent ; une fiche RESERVED reste focalisable (D-pad) et affiche son message au OK (« réservé : il s'ouvre avec un lot loué depuis votre téléphone CastBridge ») ; si toutes les fiches sont réservées, le focus tombe sur la première. `count` de l'écran utilise `QuizRoom.playableCount` (relecture comprise). Rien dans android/sender, accueil TV inchangé.

## Tests
`QuizLevelAvailabilityTest` (25) écrit d'abord : 24 rouges par assertion sur une ébauche, puis verts. Les tirages réels (CP, Class 1, 6e, Form 1, Lower Sixth, Culture générale, SIL, Form 4) donnent 15 questions distinctes via `QuizRoom` construit comme `QuizHub`. `:core:test` complet : 3049 tests, 0 échec ; `:sender` et `:receiver` compilent. Aucune attente existante changée (le test de `QuizBank` sur SIL = 0 concerne la banque seule, inchangé).

## Risques
- Les questions marquées « bêta : non validé » apparaissent en jeu (décision du propriétaire) ; à retirer en repassant `REVIEW_PLAYABLE` à false quand la relecture sera faite.
- Le total affiché est une approximation (index, et max banque/index) ; l'écran « Comment jouer ? » recharge le niveau et donne le compte exact.
- `drawDetailed` donne désormais la priorité aux approuvées : sur banque mixte, la difficulté cible passe après (tri final par difficulté inchangé).
- Index futur avec tous les niveaux embarqués : `EmbeddedLevels` doit faire correspondre `levelFor` pour `Tle`, `L1…L3` (clés `tle`, `l1`, `l2`, `l3` déjà dans la table) ; le sélecteur de filières charge un fichier de 2000+ questions.

## Seule la vraie TV confirme
Fluidité du sélecteur (24 cartes à 3 lignes sur GaiaOS 720p 32 bits), lisibilité de la note de relecture, temps de chargement d'un niveau (et de Form 4 : deux fichiers), mémoire après plusieurs changements de niveau.
