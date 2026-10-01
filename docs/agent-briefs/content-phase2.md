# Brief : phase 2 du réseau de contenu (vague 2, dans `castbridge-content`)

Session cloud `castbridge-content` : tu es désormais **la session permanente des contenus d'apprentissage** (Apprendre et Quiz). Même cadre que `content-phase1.md` (dépôt `castbridge-content`, branche de travail autorisée, jamais `main`, rien sur le serveur de production, rien dans le code des applications, tout « bêta : non validé », aucun identifiant renommé, plafonds : lot TV ≤ 3 Mo, fichier ≤ 50 Mo).
Protocole `docs/COORDINATION.md` : rapport vivant `docs/agent-reports/content-phase2.md` dans `castbridge-content` (`STATUT`, une ligne par jalon, `QUESTION:` si bloqué, `ORDRES-TRAITES: n`).

## Réponse à ta question
Ni (c) tout de suite : la relecture indépendante (QA) sera faite plus tard par des agents vérificateurs dédiés, ne la fais pas toi-même. Fais **(a) puis (b)**, dans cet ordre :
1. **(a) Quiz N2/N4** pour les 8 parcours « excellence » déjà écrits (cep-maths, cep-sciences, bepc-maths, bepc-pct, bac-maths-cd, bac-physique-c…) : explore `tools/quiz-bank` (qb/, `quizbank.py build` puis `lots`), produis des questions de niveau excellence (N2 = excellence Cameroun, N4 = excellence mondiale), à réponse vérifiable (modèles/faits sourcés, pas de données inventées), sans perdre aucun identifiant existant.
2. **(b) Apprendre : autres classes et matières**, par vagues, en commençant par les trous signalés dans ton rapport : N4 absent en anglais, français, histoire-géo-ECM, informatique, SVT ; puis les autres classes du tableau `docs/COVERAGE.md` (primaire → terminale, GCE O/A Level, supérieur), en priorité les examens nationaux. Un commit par lot, jalon dans le rapport.
3. **Animations** : 0 pour l'instant. Ajoute de **courtes animations vectorielles** (≤ 40 Ko, moteur `AnimatedFigure`, voir la politique de médias) pour les notions où le mouvement aide vraiment (géométrie, physique, SVT, conjugaison visuelle), sans en faire pour en faire.

## Règles de qualité (plus importantes que le volume)
- Le volume n'est pas un objectif : la vague 1 pèse 0,8 Mo, c'est acceptable si les leçons sont denses et justes. Ne gonfle jamais pour atteindre une taille.
- Chaque paquet garde sa liste des **points douteux** (niveau réel, hors programme, données inventées, LaTeX converti) pour les futurs vérificateurs.
- Contrôles à chaque lot : `check_pack.py`, `check_graph.py`, `content-budget`. Les validateurs Kotlin (`LearnTool`, `ContentGraphValidator`) ne tournent pas dans ta session (gradle refusé) : **ne le contourne pas** ; je les lance moi-même sur CastBridge avant toute fusion, dis seulement ce que tu n'as pas pu exécuter.

## À venir (ne pas commencer)
La catégorie **Langues** (7 langues, enveloppe de 6 Go) est en conception par un autre agent (`languages-architect`). Quand sa conception sera validée, la production des contenus de langues te sera confiée par un ordre séparé.

## Coordination
Relis la section ci-dessous à chaque jalon.

## Réponses du coordinateur
(aucune pour l'instant)
