# Quiz Supérieur — Géographie L1/L2/L3

Branche `claude/quiz-higher-geographie`. 12 fichiers `content/quiz/batches/higher-<l1|l2|l3>-geographie-0[1-4].json`, 125 questions chacun, 1 500 au total (statut `review` à l'import, relecture humaine requise).

## L1 — 500 questions (4 fichiers)

- Difficultés 1..5 : 150 / 150 / 100 / 60 / 40
- Bonnes réponses A/B/C/D : 125 / 125 / 125 / 125
- Régions : AF 56, CM 30, WORLD 414
- Sous-thèmes (25) :
  - Biomes et grandes formations végétales : 20
  - Cameroun : relief, climats, régions naturelles : 20
  - Circulation atmosphérique et saisons : 20
  - Climats du monde : facteurs et éléments : 20
  - Coordonnées géographiques et fuseaux horaires : 20
  - Démographie : natalité, mortalité, croissance naturelle : 20
  - Grands fleuves et lacs d'Afrique : 20
  - Hydrologie : cycle de l'eau, bassins versants : 20
  - La Terre : forme, dimensions, mouvements : 20
  - Le relief : montagnes, plateaux, plaines : 20
  - Lecture de cartes : échelle, légende, courbes de niveau : 20
  - Les milieux naturels d'Afrique centrale : 20
  - Migrations et réfugiés : 20
  - Objet et méthodes de la géographie : 20
  - Océans, courants marins et littoraux : 20
  - Peuplement et densités : 20
  - Photographies aériennes et télédétection : 20
  - Projections cartographiques : 20
  - Sols et ressources : 20
  - Systèmes d'information géographique : 20
  - Tectonique des plaques, séismes et volcans : 20
  - Transition démographique : 20
  - Urbanisation et villes du monde : 20
  - Zones climatiques et classification de Köppen : 20
  - Érosion et modelé du relief : 20

## L2 — 500 questions (4 fichiers)

- Difficultés 1..5 : 148 / 152 / 100 / 60 / 40
- Bonnes réponses A/B/C/D : 128 / 124 / 124 / 124
- Régions : AF 118, CM 61, WORLD 321
- Sous-thèmes (24) :
  - Agriculture tropicale : cultures vivrières et de rente : 21
  - Cartographie thématique et statistiques de base : 22
  - Commerce international et mondialisation : 21
  - Corridors et intégration régionale : 20
  - Croissance urbaine et mégapoles africaines : 21
  - Décentralisation et collectivités territoriales : 20
  - Enquête de terrain et traitement des données : 22
  - Géographie de la population : structures par âge et par sexe : 22
  - Géographie de la santé : grandes endémies : 22
  - Géographie humaine : cadre conceptuel : 21
  - Géographie industrielle : localisation et facteurs : 21
  - Géographie rurale : systèmes agraires et paysages agraires : 20
  - Géographie régionale de l'Afrique : 20
  - Géographie urbaine : morphologie, fonctions et hiérarchie urbaine : 21
  - Habitat spontané et politiques de la ville : 21
  - Organisation de l'espace et aménagement du territoire : 20
  - Organisations régionales : CEMAC, CEEAC, CEDEAO, Union africaine : 20
  - Pyramides des âges et politiques de population : 20
  - Pêche et aquaculture : 20
  - Ressources minières et pétrolières : 22
  - Réseaux de transport : routes, rail, ports, aéroports : 22
  - Tourisme et géographie des loisirs : 20
  - Élevage et pastoralisme : 20
  - Énergie : sources, production et consommation : 21

## L3 — 500 questions (4 fichiers)

- Difficultés 1..5 : 152 / 148 / 100 / 60 / 40
- Bonnes réponses A/B/C/D : 125 / 125 / 125 / 125
- Régions : AF 66, CM 57, WORLD 377
- Sous-thèmes (24) :
  - Biodiversité, aires protégées et parcs nationaux : 21
  - Cartographie numérique et cartes participatives : 22
  - Changement climatique : causes, effets, accords internationaux : 20
  - Déforestation et gestion des forêts du bassin du Congo : 21
  - Environnement et développement durable : 21
  - Espaces transfrontaliers et intégration régionale : 20
  - Foncier et gestion des terres : 20
  - Frontières africaines héritées de la colonisation : 21
  - Gestion de l'eau : bassins transfrontaliers : 21
  - Géographie de l'énergie et transition énergétique : 21
  - Géographie des mobilités et migrations internationales : 21
  - Géographie du Cameroun : régions, économie et aménagement : 21
  - Géographie du développement : indicateurs : 21
  - Géographie physique appliquée : géomorphologie tropicale : 21
  - Géopolitique : États, frontières et conflits : 22
  - Méthodologie du mémoire de géographie : 20
  - Métropolisation et réseaux de villes : 21
  - Risques naturels : inondations, glissements de terrain, sécheresses : 21
  - Statistiques spatiales et analyse des données : 21
  - Systèmes d'information géographique : 20
  - Télédétection : images satellitaires et indices : 20
  - Urbanisme et planification urbaine : 21
  - Économie spatiale et modèles de localisation : 21
  - Épistémologie de la géographie : grandes écoles : 21

## Rejets corrigés
Premiers passages de `import --dry-run` : choix de plus de 100 caractères, formulations « Les deux… / Tous les… » (dépendantes de l'ordre), choix jugés identiques une fois les signes +/- ignorés (UTC+1/UTC-1…), un quasi-doublon et un mot répété. Tous reformulés ; état final : 0 rejet, 0 avertissement sur les 12 fichiers. Biais « bonne réponse la plus longue » corrigé par allongement des distracteurs.

## Limites
- Faits issus de la connaissance du rédacteur, sans vérification externe ; sources = familles de cours génériques (aucun titre ni auteur inventé).
- À revérifier par un humain : valeurs numériques (mont Cameroun ≈ 4 040 m, Nil ≈ 6 650-6 700 km, superficie du Cameroun ≈ 475 000 km²), dates et institutions camerounaises (COMIFAC, SIC 1952, Crédit foncier 1977, Sénat 2013, Code des collectivités 2019, pipeline Doba-Kribi), part de l'hydroélectricité, date 2007 du Programme frontière de l'UA, titre français de l'ouvrage de Jane Jacobs.
- Part Afrique/Cameroun balisée : L1 ≈ 17 %, L2 ≈ 36 %, L3 ≈ 25 %.
- L2 : dans les sous-thèmes 19 à 24 la bonne réponse reste souvent la plus longue.
- Outillage : `python3` 3.11 plante sur `tools/quiz-bank/qb/gen/sup_compta_l1.py` (f-string 3.12, fichier d'un autre rédacteur, non touché) ; validation faite avec `python3.12`. `cells_report.py --batches-only` plante (KeyError 'track') sur les fichiers bruts.
