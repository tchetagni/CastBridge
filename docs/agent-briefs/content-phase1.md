# Brief : phase 1 du réseau de contenu (dans le dépôt privé `castbridge-content`)

Session cloud `castbridge-content`. Tu travailles **dans `castbridge-content`** (branche de travail déjà autorisée par le propriétaire ; jamais `main`). Lecture seule de CastBridge pour les règles et outils.
Protocole `docs/COORDINATION.md` : rapport vivant dans `castbridge-content/docs/agent-reports/content-phase1.md` (STATUT en première ligne, une ligne par jalon, QUESTION si bloqué).
Français, aucun secret, rien sur le serveur de production, rien dans le dépôt CastBridge.

## Règles de référence (CastBridge, branche `origin/claude/net-architect`)
`docs/PEDAGOGY-RUBRIC.md` (niveaux N0–N4), `docs/CONTENT-PUBLISH.md`, `content/graph/*.json` (graphes de compétences), politique de médias (figure ≤ 8 Ko, animation vectorielle ≤ 40 Ko, image WebP ≤ 120 Ko, clips seulement dans les lots média du téléphone), outil `tools/content-budget`.
Règles du propriétaire : le niveau **intermédiaire (N2) d'une classe = l'excellence au Cameroun** ; le niveau **expert (N4) = l'excellence mondiale**. Logique **par lot** (classe/niveau/thème) pour Apprendre et Quiz. Plafonds : lot de la TV ≤ 3 Mo, fichier ≤ 50 Mo, total ≤ 3 Go. Tout contenu reste « bêta : non validé » (des agents vérificateurs passeront plus tard) ; ne jamais renommer ni réutiliser un identifiant déjà livré.

## Étape A : corrections de ton propre rapport (petit, d'abord)
1. Alléger la figure de `learn/ce2-maths/lessons/3-tables.json` (8,6 Ko → ≤ 8 Ko) sans changer le sens.
2. Réaligner `MEDIA-MANIFEST.json` et `LOT-VERSIONS.json` sur les lots réels (24 lots Apprendre, lots Quiz actuels), avec l'outil `content-budget` de `net-architect` ; rapport dans `docs/BUDGET.md`.
3. Écrire dans `docs/COVERAGE.md` le tableau de couverture réel : par parcours (classe, matière), niveau N0–N4 présent ou absent, nombre de leçons, d'exercices, de questions Quiz, de figures, d'animations.

## Étape B : combler les trous les plus utiles (première vague, cible 150 Mo au plus)
Priorité aux examens nationaux (CEP, BEPC, Probatoire, Baccalauréat, GCE O/A Level) et au socle maths / français / anglais / sciences :
1. Compléter les graphes de compétences manquants (aujourd'hui 24 parcours couverts sur 70) : un fichier `graph/<domaine>.json` par domaine, prérequis explicites, niveaux N0–N4.
2. Pour chaque parcours de la première vague (propose la liste dans ton rapport d'après le tableau de couverture, au moins CM2/CEP, 3e/BEPC, Tle C/D et GCE O Level maths et sciences), produire les niveaux N2 (excellence Cameroun) puis N4 (excellence mondiale) : leçons, exercices corrigés, questions Quiz, et pour chaque notion difficile **une figure ≤ 8 Ko** ou **une courte animation vectorielle ≤ 40 Ko** (moteur `AnimatedFigure` déjà dans CastBridge ; décris les figures dans le format attendu, sans vidéo ni GIF).
3. Auto-contrôle de chaque lot : `content-budget` + les validateurs de `tools/quiz-bank` et `tools/learn-authoring` ; aucun lot TV > 3 Mo ; rapport des tailles.
4. Un commit par lot, marqué « bêta : non validé », jalon dans ton rapport.

## Ce que tu ne fais pas
Pas de fusion dans `main`, pas de copie vers le serveur, pas de modification du code des applications.

## Coordination
Rapport vivant ; relis la section ci-dessous à chaque jalon.

## Réponses du coordinateur
(aucune pour l'instant)
