# Brief : préparer le dépôt privé `castbridge-content` (instantané, sans rien supprimer ici)

Session cloud `castbridge-content`. Source : `origin/integration/agents` (contenu Apprendre/Quiz + outils) et, pour les outils de découpage,
`origin/claude/net-architect` (split-repo, Git LFS, content-budget, publish pipeline, MEDIA-MANIFEST, PEDAGOGY-RUBRIC) ; ne pas fusionner dans integration/agents.
Écrire UNIQUEMENT dans le dépôt `castbridge-content` (branche `claude/initial-import`, jamais `main`) ; dans CastBridge, seulement ce cahier est lu.
Français. Aucun secret. Ne rien produire ni copier sur le serveur de production (https://bridge.sti-cm.com).

## Contexte
Règle du propriétaire : la base de formation vit dans un dépôt privé dédié, puis est simplement copiée vers le serveur ; rien n'est produit sur le serveur.
Plafond 3 Go au total, 3 Mo par lot TV, 50 Mo par fichier. Tout contenu reste « bêta : non validé » ; des agents vérificateurs passeront plus tard.
Un autre agent (« Content integration ») est en train de corriger des tests de contenu sur `claude/content-integration` : **ne touche pas** aux fichiers
de contenu du dépôt CastBridge et ne supprime rien de `content/` là-bas. Ce chantier ne produit qu'un INSTANTANÉ.

## À faire
1. Dans `castbridge-content` : arborescence (`learn/`, `quiz/`, `media/`, `graph/`, `docs/`, `tools/`), `README` (rôle, plafonds, flux « dépôt → copie → serveur »),
   `.gitattributes` Git LFS pour les médias lourds (si LFS est refusé par la plateforme, le dire et documenter l'alternative), `.gitignore`, `CONTENT-RELEASE.json` (version, date, empreintes).
2. Importer un instantané de `content/learn`, `content/quiz` (packs et catalogues), `tools/quiz-bank`, `tools/learn-authoring` depuis `origin/integration/agents`,
   en conservant les identifiants tels quels (jamais d'identifiant renommé). Utiliser le script de découpage de `net-architect` s'il convient, sinon `git subtree`/copie contrôlée.
3. Vérifier les plafonds avec l'outil `content-budget` de `net-architect` (ou un script équivalent) : aucun lot TV > 3 Mo, aucun fichier > 50 Mo, total < 3 Go ; écrire le rapport dans `docs/BUDGET.md`.
4. Pousser sur `claude/initial-import` (petits commits). Ne PAS pousser sur `main` : le propriétaire fusionnera.
5. Rapport final en français, court, dans `docs/agent-reports/<date>-content-export.md` du dépôt CastBridge sur la branche `claude/content-export` (créée depuis integration/agents) :
   ce qui a été importé, tailles, ce qui manque, difficultés (LFS, droits), et la commande exacte pour copier vers le serveur (sans l'exécuter).
