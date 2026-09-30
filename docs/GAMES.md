# Jeux : le hub, comment ajouter un jeu, le Sudoku

Demande d'Esaie : « regrouper quiz, échecs et sudoku dans la catégorie Jeux ». Branche `feat/games-hub`.

## 1. Le hub « Jeux »

**TV.** L'accueil n'a plus qu'une tuile **Jeux** (icône manette `ic_t_games`, statut « 3 jeux ») à la place des tuiles Quiz et Échecs. OK ouvre `GamesActivity` : une carte par jeu (icône, nom, modes, une phrase, état : « Meilleur score », « Partie en cours »…). OK lance le jeu, Retour revient à l'accueil. La liste est **déclarative** : `Games.all` dans `receiver/.../Games.kt`.

Les URL publiques `/quiz` et `/chess` et les routes `/api/quiz`, `/api/chess` ne changent pas (elles restent servies par `QuizHub` / `ChessHub`). Nouveau : `GET /api/games` (liste et états), `POST /api/games/open[?game=quiz|chess|sudoku]` (ouvre le hub ou un jeu ; il faut que l'écran de CastBridge TV, l'accueil ou « Jeux », soit visible : Android 14 interdit de lancer une activité depuis l'arrière-plan).

**Téléphone.** Les onglets Quiz et Échecs sont remplacés par un seul onglet **Jeux** (`sender/.../GamesScreen.kt`) : trois cartes ; Quiz et Échecs ouvrent les écrans existants sans changement (`QuizScreen`, `ChessScreen`), avec un bandeau « ‹ Jeux » pour revenir ; le Sudoku propose « Jouer sur la TV » puis une manette.

### Ajouter un jeu
1. TV : créer l'activité, la déclarer dans le manifeste (`exported=false`).
2. Ajouter un `GameDef(id, icône, nom, modes, phrase, couleur, état, lancement)` dans `Games.all` : la carte, le compteur « N jeux » de la tuile et `/api/games` suivent.
3. Télémétrie : ajouter l'id à `EventCatalog.TV_FEATURES` (client `core/telemetry` et serveur `EventCatalog.java`) ; `Games.open` émet `feature_used{feature=id}`.
4. Téléphone : ajouter une ligne à `PHONE_GAMES` et une branche dans `GamesScreen`.
La mise en page des cartes est en « pixels de conception 1920x1080 » (`Dx`, `GamesUi.kt`) : identique en 1280x720 @160 dpi et en 1920x1080 @320 dpi. Couleurs : `branding/design-tokens.json`.

## 2. Le Sudoku

Repris en **copie de fichiers** depuis `origin/wip/external-ai-changes` (audit : sûr) : `Sudoku.kt`, `SudokuTest.kt`, `SudokuActivity.kt`, `ic_t_sudoku.xml`, relus (aucun réseau, aucune permission) et rien d'autre de cette branche (ni activation, ni mise à jour automatique, ni clé SSH, ni clic d'accessibilité). Puis améliorés.

- **Règles** : grille 9x9, chaque ligne, colonne et bloc 3x3 contient 1 à 9 une fois.
- **Niveaux** : Facile (≈46 indices), Moyen (≈36), Difficile (≈30), Expert (≈26). Le niveau se mesure au **nombre d'indices de départ**, pas à la technique de résolution requise. Chaque grille a **une seule solution** (le générateur retire des cases tant que la solution reste unique ; testé).
- **Jeu** : curseur à la flèche, OK ouvre un pavé de chiffres (les touches 1 à 9 marchent si la télécommande en a), 0 efface. **Brouillon** (notes) : mode Notes, « Remplir le brouillon » ; un chiffre posé disparaît des notes de ses voisines. **Vérifier** : cases fausses en rouge (comparées à la solution). **Indice** : 3 par grille, révèle une case. **Chrono** (en pause dans les menus). Retour ouvre le menu ; touches de couleur : rouge vérifier, vert indice, jaune notes, bleu menu.
- **Stockage** (préférences privées `castbridge_sudoku`, rien ne quitte la TV) : `save` (une ligne `S1|niveau|ms|indices|grille|plateau|notes`, écrite à chaque coup, refusée si altérée ou terminée), `records` (meilleur temps par niveau ; une partie avec indice n'est pas un record), `level`.
- **Téléphone** : `GET /api/sudoku`, `POST /api/sudoku/open[?level=]`, `POST /api/sudoku/cmd?do=left|right|up|down|cell&v=|digit&v=|clear|notes|hint|check|autonotes|menu|new&level=|quit` (protégées par le PIN comme le quiz) ; l'onglet Jeux affiche un miroir de la grille et les boutons. Différence avec le quiz : le quiz sert une page web publique ; ici c'est l'app téléphone qui pilote (pas de page web publique pour le Sudoku).
- **Télémétrie** (avec le consentement « statistiques » seulement) : `feature_used{games|quiz|chess|sudoku}`, `sudoku_game{level, ms, result=win|abandon, hints}` (docs/TELEMETRY.md).
- **Tests JVM** : `SudokuTest`, `SudokuGameTest` (génération, unicité, résolution, validation, niveaux, brouillon, indices, sauvegarde, records).
