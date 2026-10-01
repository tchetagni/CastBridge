# Brief : remettre les tests au vert après la fusion des agents de contenu

Agent cloud. Base : `origin/integration/agents` (déjà fusionnée : lots, contenus Apprendre et Quiz, validation bêta, plug and play, télécommande).
Créer `claude/content-integration`, petits commits, pousser, pas de PR. Ne pas toucher `main`. Textes en français ; apps « CastBridge » / « CastBridge-TV ».
Lancer `cd android && gradle :core:test` (harnais `:core` seul si le plugin Android ne se résout pas). Aujourd'hui : 1228 tests, 18 échecs, tous liés au contenu :

1. `LearnLotsTest` (≈14) : « pack(s) sans lot dans scopes.txt : 1re-chimie-cd, 1re-economie, 1re-english, … » — les agents de contenu ont écrit des packs
   (lycée, collège, technique/supérieur, anglophone) absents de `content/learn/scopes.txt`. Ajouter chaque pack au bon lot (règle de niveau de
   `docs/LEARN.md` § lots ; créer les lots manquants si une classe n'existe pas, plafond 3 Mo par lot pour la TV), puis
   `gradle :core:buildLearnLots -Pupdate` (registre `content/learn/lots.json`) et `gradle :core:reviewLearn` (`docs/LEARN-REVIEW.md` périmé).
   Corriger `scopeGuessAgreesWithTheTable` et `everyPackIsInExactlyOneLot` (chaque pack dans exactement un lot).
2. `LearnContentTest` (3) : `bepc-english` : leçons en double (« bepc-english-conditionals en double », chapitre inconnu…) — deux agents ont écrit
   les mêmes leçons (brouillon des lots vs version complète du collège). Garder la version la plus complète, supprimer le doublon, vérifier les
   chapitres de `pack.json`. Questions d'auto-contrôle en double (`learn-1re-maths-e-ti-sta-q3`, `learn-2nde-english-fut-q2`, …) : rendre les
   identifiants uniques SANS changer le sens des questions ; ne jamais réutiliser un identifiant déjà livré.
3. `QuizLotsTest.lotsKeepEveryQuestionIdOfTheOlderPacks` : les packs Quiz primaire/collège régénérés ont perdu des identifiants (`ec4-…`) que les
   anciens packs avaient. Conserver tous les identifiants existants (règle « jamais d'identifiant perdu » de `docs/QUIZ.md`) en corrigeant le
   générateur `tools/quiz-bank` ou en rajoutant les questions manquantes ; relancer `python3 tools/quiz-bank/quizbank.py build` puis `lots`.
4. `MultiVolumeServerTest.preflightWarnsAboutASlowDrive` (HTTP 404) : vérifier si ce test est instable ou si une route du serveur de la TV a
   disparu lors des fusions (`/api/storage…` / préflight). Corriger la cause, pas le test, sauf s'il est réellement instable (le dire).
5. Budgets : `gradle :core:checkLearnContent`, outil `content-budget` si présent : aucun lot TV > 3 Mo, aucun fichier > 50 Mo, total < 3 Go. Rapporter les tailles.
6. Ne PAS déplacer le contenu vers `castbridge-content` dans ce chantier (le propriétaire le décidera après le vert) ; ne rien produire sur le serveur.
Rapport final en français : tests avant/après, ce qui a été supprimé ou renommé (liste), budgets, ce qui reste à faire.

## Coordination
Suis `docs/COORDINATION.md` : rapport vivant `docs/agent-reports/content-integration.md` sur ta branche (STATUT en première ligne, une ligne par jalon, QUESTION si bloqué).

## Réponses du coordinateur
(aucune pour l'instant ; relis cette section à chaque jalon)
