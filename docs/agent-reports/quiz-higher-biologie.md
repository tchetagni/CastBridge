# Rapport : Quiz Supérieur, filière Biologie (L2 et L3)

Branche `claude/quiz-higher-biologie`. 8 fichiers dans `content/quiz/batches/` (`higher-l2-biologie-01..04`, `higher-l3-biologie-01..04`), 125 questions chacun : **500 en L2 (`l2-bio`), 500 en L3 (`l3-bio`)**. Statut d'import : `review` (relecture humaine requise). Aucun fichier de `content/quiz/dist`, `android/` ni `backend/` modifié.

Validation : `python3.12 tools/quiz-bank/quizbank.py import --dry-run <fichier>` donne « 125 questions valides importées », 0 rejet, 0 avertissement pour les 8 fichiers. Note : `python3` (3.11) plante sur `tools/quiz-bank/qb/gen/sup_compta_l1.py` (f-string imbriquée, syntaxe 3.12) ; fichier d'un autre rédacteur, non modifié.

## L2

- Difficultés 1 à 5 : 152 / 152 / 100 / 56 / 40 (cible 30/30/20/12/8 % : 152/152/100/56/40).
- Bonnes réponses : A 125, B 125, C 125, D 125 sur 500.
- Questions par sous-thème (28 sous-thèmes) :
  - adn : structure, réplication : 19
  - biologie végétale : organisation et croissance de la plante : 18
  - biostatistiques et méthode expérimentale : 18
  - biotechnologies : pcr, clonage, ogm : 17
  - classification du vivant : bactéries, archées, eucaryotes : 16
  - cycle cellulaire : mitose et contrôle : 18
  - digestion et nutrition : 17
  - enzymes : cinétique et régulation : 17
  - génétique mendélienne et croisements : 18
  - hérédité liée au sexe : 17
  - immunologie : immunité innée et adaptative : 17
  - la cellule : théorie cellulaire et organites : 19
  - membrane plasmique : structure et transports : 17
  - microbiologie : bactéries, culture et antibiotiques : 19
  - mutations et maladies génétiques : 17
  - méiose et brassage génétique : 18
  - métabolisme énergétique : respiration cellulaire et atp : 20
  - parasitologie : paludisme et autres parasitoses tropicales : 18
  - photosynthèse : phases et rendement : 18
  - physiologie animale : système nerveux et hormonal : 18
  - physiologie de la circulation et de la respiration : 17
  - physiologie de la reproduction : 16
  - régulation de l'expression génétique : 17
  - transcription et traduction : synthèse des protéines : 20
  - vaccination et sérologie : 17
  - virus : structure et cycle de réplication : 18
  - écologie : écosystèmes et chaînes alimentaires : 19
  - évolution : sélection naturelle et spéciation : 20

## L3

- Difficultés 1 à 5 : 152 / 152 / 100 / 56 / 40 (cible 30/30/20/12/8 % : 152/152/100/56/40).
- Bonnes réponses : A 124, B 125, C 125, D 126 sur 500.
- Questions par sous-thème (26 sous-thèmes) :
  - apoptose et différenciation : 21
  - bio-informatique : alignement de séquences : 19
  - biochimie métabolique : glycolyse, cycle de krebs, chaîne respiratoire : 19
  - biodiversité et conservation en afrique centrale : 16
  - biologie cellulaire avancée : trafic membranaire : 20
  - biologie du développement : embryogenèse et gènes hox : 18
  - biologie moléculaire : régulation de la transcription, opéron lactose : 20
  - biotechnologie agricole et sécurité alimentaire : 16
  - cancer : oncogènes et gènes suppresseurs : 19
  - cytosquelette et motilité cellulaire : 20
  - génie génétique : plasmides, enzymes de restriction : 21
  - génomique et séquençage : 19
  - génétique des populations : hardy-weinberg : 20
  - immunologie avancée : complexe majeur d'histocompatibilité, anticorps : 19
  - microbiologie médicale : résistance aux antibiotiques : 20
  - métabolisme des lipides et des acides aminés : 21
  - méthodologie de la recherche et mémoire de biologie : 17
  - parasitologie et maladies tropicales négligées : 19
  - physiologie comparée : 19
  - physiologie végétale : hormones et stress : 18
  - protéomique et techniques d'analyse (électrophorèse, chromatographie) : 20
  - régulation hormonale du métabolisme : 19
  - signalisation cellulaire et récepteurs : 21
  - virologie : vih, hépatites, virus émergents : 20
  - écologie des populations et des communautés : 18
  - épissage et maturation des arn : 21

## Rejets corrigés

Au premier passage, les rédacteurs ont corrigé des rejets du contrôle : choix « Les deux… / Tous les… / Aucune des deux… » (dépendants de l'ordre), questions finissant par « : » ou « … » au lieu de « ? », choix de plus de 100 caractères, quasi-doublons internes (ex. 1/Km vs -1/Km, AA vs aa), et bonnes réponses trop souvent les plus longues (ramenées à environ 30-45 %).

Après assemblage, 27 doublons exacts entre fichiers ou entre niveaux (ex. constante de Michaelis, tsé-tsé, exclusion compétitive de Gause) ont été retirés (19 en L2, 8 en L3) puis remplacés par 27 questions nouvelles sur des sujets absents (contrôle par script contre les lots, L3 et les questions L1).

## Limites

- Plusieurs sous-thèmes proches sont abordés sous des angles voisins entre fichiers (angles 01 définitions, 02 mécanismes, 03 applications, 04 raisonnement) : des quasi-doublons sémantiques non détectés par le contrôle automatique peuvent subsister ; relecture humaine nécessaire.
- Les intitulés de `category` L3 varient parfois en casse (ex. « Parasitologie et maladies… » / « parasitologie et maladies… ») ; le comptage ci-dessus les fusionne.
- Calculs de génétique des populations : vérifiés par script pour certains lots (L2-01, L2-03, L2-04, L3-03, L3-04), faits à la main pour d'autres (L3-01, L3-02 et lots de remplacement : 9/64, Meselson-Stahl, 5/0,5), tous simples ; recontrôle conseillé.
- La bonne réponse reste un peu plus souvent la plus longue que le hasard dans certains fichiers (30-46 % selon le fichier).
- Pas de comparaison automatique exhaustive avec le L1 existant au-delà d'un contrôle de similarité sur les énoncés ; sources = familles de manuels (Alberts, Stryer/Lehninger, Griffiths, Madigan/Prescott, Janeway/Abbas, Sherwood, Ricklefs/Begon, Raven, Golvan/Bussieras, Futuyma, Sokal/Rohlf), sans titre ni page cités.
- `quizbank.py lots`/`check` et la construction des paquets n'ont pas été lancés (rôle de l'intégrateur).
