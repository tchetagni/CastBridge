# Quiz Supérieur : sociologie L2 et L3

Branche `claude/quiz-higher-sociologie`. Livrables : 8 fichiers `content/quiz/batches/higher-{l2,l3}-sociologie-0{1..4}.json` (125 questions chacun, statut `review` à l'import). Aucune modification de `content/quiz/dist`, `android/`, `backend/`. Aucune PR.

## Comptes

| Niveau | Clé | Questions | Fichiers |
|---|---|---|---|
| L2 | `l2-socio` | 500 | 4 × 125 |
| L3 | `l3-socio` | 500 | 4 × 125 |


## L2 : sous-thèmes

- Démographie sociale et structures familiales : 19
- Histoire de la sociologie : Comte, Marx, Durkheim, Weber : 21
- Interactionnisme et Goffman : 19
- Inégalités sociales et pauvreté : 16
- L'action sociale chez Weber et les types d'autorité : 21
- La socialisation primaire et secondaire : 19
- La sociologie de Pierre Bourdieu : habitus, capital, champ : 20
- Le fait social et les règles de la méthode sociologique : 21
- Mobilité sociale : 16
- Méthodes qualitatives : entretien, observation, monographie : 21
- Méthodes quantitatives : enquête par questionnaire, échantillonnage : 20
- Normes, déviance et contrôle social : 20
- Parenté et systèmes de filiation en Afrique : 19
- Sociologie africaine : ethnicité, chefferie, urbanisation : 18
- Sociologie de l'éducation et reproduction sociale : 19
- Sociologie de la famille et du mariage : 20
- Sociologie de la santé : 19
- Sociologie des médias et de la communication : 18
- Sociologie des religions : Durkheim et Weber : 19
- Sociologie du développement : 20
- Sociologie du genre : 19
- Sociologie du travail et des organisations : 19
- Sociologie politique : pouvoir, État, participation : 21
- Sociologie urbaine et rurale : 17
- Statistiques descriptives en sciences sociales : 20
- Stratification sociale : classes, castes, statuts : 19

Difficultés (1 à 5) : 1=150, 2=149, 3=100, 4=60, 5=41 (cible 30/30/20/12/8 %).

Bonnes réponses : A=128, B=124, C=124, D=124 (25 % chacune).

## L3 : sous-thèmes

- Analyse qualitative : codage et analyse thématique : 20
- Analyse statistique : tableaux croisés, khi-deux, régression : 20
- Décentralisation et pouvoirs locaux : 20
- Globalisation et inégalités mondiales : 20
- Migrations et diasporas : 20
- Méthodes d'enquête : conception d'un protocole : 20
- Méthodologie du mémoire de sociologie : 20
- Sociologie de l'environnement et des risques : 20
- Sociologie de l'économie informelle : 20
- Sociologie de l'éducation : accès, échec, orientation : 20
- Sociologie de la famille contemporaine : 20
- Sociologie de la jeunesse : 20
- Sociologie de la santé : systèmes de santé et médecine traditionnelle : 20
- Sociologie de la ville africaine : quartiers, mobilités : 20
- Sociologie des conflits et de la paix : 20
- Sociologie des organisations : bureaucratie, pouvoir : 20
- Sociologie des religions et pluralisme religieux en Afrique : 20
- Sociologie du changement social et de la modernité : 20
- Sociologie du développement et critiques de la modernisation : 20
- Sociologie du genre et des violences : 20
- Sociologie du numérique et des réseaux sociaux : 20
- Sociologie du travail et du chômage des jeunes : 20
- Sociologie politique de l'Afrique : État, clientélisme, ethnicité : 20
- Théories sociologiques contemporaines : Giddens, Habermas, Beck : 20
- Éthique de la recherche sociale : 20

Difficultés (1 à 5) : 1=148, 2=136, 3=92, 4=76, 5=48 (cible 30/30/20/12/8 %).

Bonnes réponses : A=128, B=124, C=124, D=124 (25 % chacune).

## Validation et rejets corrigés

Les 8 fichiers passent `quizbank.py import --dry-run` avec 0 rejet (Python 3.12 requis : `qb/gen/sup_compta_l1.py` ne se compile pas en 3.11). Rejets rencontrés puis corrigés : « choix trop long » (plusieurs), « choix identiques » (formule de khi-deux, symboles normalisés), « finit par ? » (1), « choix qui dépend de l'ordre » (3, après réécriture). Deux questions identiques à des questions du L1 existant, et quatre autres au niveau L3, ont été remplacées ; aucun doublon exact entre L2, L3 et L1 (vérifié sur le texte normalisé).

Un biais de longueur (bonne réponse nettement plus longue que les distracteurs) a été détecté sur 726 questions ; 16 sous-agents ont réécrit les distracteurs (et condensé les bonnes réponses). Rapport longueur bonne réponse / moyenne des distracteurs : L2 2,28 → 1,14 ; L3 3,29 → 1,20.

## Limites

- Difficulté L3 légèrement plus dure que la cible (148/136/92/76/48 contre 150/150/100/60/40) ; les niveaux de difficulté ont été attribués par motif au sein de chaque sous-thème (ordre croissant), donc approximatifs.
- Après correction, la bonne réponse reste la plus longue dans environ 55 % (L2) et 67 % (L3) des questions : biais résiduel, à relire.
- Les distracteurs réécrits par sous-agents n'ont été relus que par sondage : relecture humaine indispensable (statut `review`) avant approbation, notamment pour la certitude des attributions (auteurs, dates de conventions et conférences).
- Les sources sont des familles de manuels génériques (9 libellés), non des références précises.
- L'équilibre A-D est exact au niveau de chaque cellule (128/124/124/124), mais la position de la bonne réponse est tirée par un mélange pseudo-aléatoire.
