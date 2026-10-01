# Brief : fusionner dans une branche d'intégration les travaux terminés et non fusionnés (lot 2)

Session cloud `Castbridge-cloud`. Base : `origin/integration/agents` au commit 3115b2e (tous les tests du cœur verts : 1267). Branche `claude/integration-2`. Pas de PR ; ne touche ni `main` ni `integration/agents`.
Protocole : `docs/COORDINATION.md` (rapport vivant `docs/agent-reports/integration-2.md`). Français, aucun secret.

## À fusionner (dans cet ordre), en résolvant les conflits sans rien perdre ; `docs/HANDOFF.md` : garder les deux versions
1. `origin/claude/usb-data` (1 commit : données lourdes de CastBridge-TV dans Download/CastBridge de la clé USB, réadoption après réinstallation) : **évalue d'abord s'il est complet** par rapport à `docs/agent-briefs/usb-data.md` ; liste ce qui manque dans le rapport sans le refaire.
2. `origin/claude/net-architect` (outils : politique de médias, content-budget, publish pipeline, split-repo, PEDAGOGY-RUBRIC, graphes de compétences).
3. `origin/claude/content-quiz-culture`, `origin/claude/content-quiz-lycee` (et `content-quiz-superieur` s'il reste des commits non fusionnés) : ce sont des générateurs de questions ; **lance-les** (`python3 tools/quiz-bank/quizbank.py build` puis `lots`) pour produire les packs, **sans perdre aucun identifiant de question existant**.
4. Branches déjà fusionnées une première fois mais qui ont reçu de nouveaux commits depuis : \`origin/claude/smart-remote\` (4), \`origin/claude/content-learn-anglophone\` (10), \`origin/claude/content-quiz-superieur\` (8). Fusionne seulement ces nouveaux commits.
Après chaque fusion : `cd android && gradle :core:test` ; si le plugin Android est introuvable, banc « :core seul » et dis ce qui n'a pas été compilé (:sender, :receiver : je les compile moi-même).
Contrôle des budgets à la fin (content-budget de net-architect) : lot TV ≤ 3 Mo, fichier ≤ 50 Mo, total ≤ 3 Go ; rapporte les tailles.
Si une fusion casse des tests que tu ne sais pas corriger sans décider à ma place : `STATUT: BLOQUÉ` + `QUESTION:` et continue avec la suivante.

## Coordination
Rapport vivant sur ta branche ; relis cette section à chaque jalon.

## Réponses du coordinateur
(aucune pour l'instant)
