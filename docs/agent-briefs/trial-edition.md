# Brief : édition d'essai de 100 Mo (monétisation) couvrant tout le catalogue

Agent cloud. Base `origin/integration/agents`. Branche `claude/trial-edition`. Protocole `docs/COORDINATION.md` (rapport vivant `docs/agent-reports/trial-edition.md`). Pas de PR, pas de main, rien sur le serveur de production. Français, aucun secret.
Lire : `docs/LOTS.md`, `docs/LEARN.md`, `docs/QUIZ.md`, `content/learn/lots.json`, `content/learn/scopes.txt`, `content/quiz/lots`, `core/.../lots/*` (LotMeta, LotPlanner, LotSync, LotBudget), `tools/content-budget` (branche `claude/net-architect`), `docs/COVERAGE.md` de `castbridge-content`, et `docs/agent-briefs/languages-architect.md` (la catégorie Langues arrive).

## Décision du propriétaire
Pour la monétisation, la **version d'essai ne donne accès qu'à 100 Mo** de contenu Quiz + Apprendre, **avec toutes les sous-catégories fournies** : toutes les classes, toutes les matières, toutes les langues et tous les niveaux, ainsi que le Quiz, de façon à **illustrer parfaitement l'offre**. La production du **contenu complet continue** en parallèle, comme aujourd'hui.

## À livrer
1. **Sélection automatique** `tools/trial-edition` (Python ou JVM, déterministe, reproductible, testé) : à partir du registre des lots et du tableau de couverture, calcule `TRIAL-MANIFEST.json` = la liste exacte des lots/fichiers de l'essai, avec tailles. Règles à concevoir et justifier :
   - **Plafond dur 100 Mo** au total (toutes catégories confondues, médias des langues compris) et **aucune sous-catégorie vide** (chaque classe, chaque matière, chaque langue, chaque niveau N0–N4 et A0–natif, chaque lot Quiz doit avoir au moins un échantillon) ; l'outil **échoue** sinon.
   - **Échantillon représentatif** par sous-catégorie : une première leçon d'introduction, une leçon difficile, quelques exercices corrigés, un lot de questions Quiz équilibré en difficulté, une figure ou animation, quelques médias de langues (audio court, vidéo courte) ; plancher par sous-catégorie puis remplissage du reste du budget par priorité (examens nationaux d'abord).
   - **Stabilité** : une même leçon/question garde le même identifiant dans l'essai et dans la version complète ; la sélection se **recalcule toute seule** quand le contenu complet grandit (jamais éditée à la main) ; rapport des tailles par sous-catégorie.
2. **Modèle d'édition (core, testé)** : `Edition {TRIAL, FULL}`, champ **additif** `edition` dans `LotMeta` (défaut `full`, rétrocompatible), et choix de conception justifié : lots d'essai séparés (`<lot>-trial`) ou tranches d'un même lot. La version complète remplace proprement l'essai (pas de doublons sur la TV : budget 10 Mo respecté).
3. **Droit d'accès (spécification + squelette testé)** : `Entitlement` (jeton signé, lié à l'appareil, durée, tolérance hors ligne), `LotPlanner`/`LotSync` : sans droit, seuls les lots d'essai ; avec droit, les lots complets ; interface « Version d'essai » avec invitation à débloquer (aucun paiement n'est implémenté : le choix du prestataire de paiement est au propriétaire). Spécifie les routes du serveur (`GET /api/lots?edition=`, délivrance et vérification du jeton) **sans toucher au serveur de production** ; le code du backend, s'il est écrit, reste sur ta branche pour revue.
4. **Honnêteté sur la sécurité** : un blocage côté application est contournable ; **la vraie protection est que le serveur ne livre les lots complets qu'aux appareils autorisés**. Documente ce qu'on ne peut pas empêcher (contenu déjà livré) et ce que le serveur doit faire.
5. **Tests JVM** : budget (échec si > 100 Mo ou sous-catégorie vide), déterminisme, stabilité des identifiants, passage essai → complet, jeton expiré / altéré / lié à un autre appareil. `cd android && gradle :core:test` (banc « core seul » si le plugin Android est introuvable ; dis ce qui n'a pas été compilé).
6. Docs : `docs/TRIAL-EDITION.md` (règles de sélection, budget par catégorie, flux d'accès, limites), `docs/HANDOFF.md`.

## À ne pas faire
Ne réduis jamais le contenu complet ; ne supprime rien ; ne choisis pas le prix ni le prestataire de paiement ; ne touche pas à la production.

## Coordination
Rapport vivant sur ta branche ; relis la section ci-dessous à chaque jalon.

## Réponses du coordinateur
(aucune pour l'instant)
