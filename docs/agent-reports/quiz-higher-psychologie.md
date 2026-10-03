# Quiz supérieur — Psychologie L1/L2/L3 : rapport de rédaction

Branche `claude/quiz-higher-psychologie`. 12 fichiers `content/quiz/batches/higher-<l1|l2|l3>-psychologie-0N.json` (4 par niveau). Tous passent `quizbank.py import --dry-run` (0 rejet), validés avec `python3.12` (voir limites).

## L1 — 499 questions

Fichiers : higher-l1-psychologie-01.json: 125 ; higher-l1-psychologie-02.json: 125 ; higher-l1-psychologie-03.json: 125 ; higher-l1-psychologie-04.json: 124

Difficultés 1..5 : {1: 152, 2: 147, 3: 100, 4: 60, 5: 40}. Bonnes réponses : {'A': 128, 'B': 124, 'C': 123, 'D': 124}.

Par sous-thème :

- Adolescence et vieillesse : 24
- Attachement (Bowlby, Ainsworth) : 21
- Attention et conscience : 21
- Conditionnement classique : 21
- Conditionnement opérant et apprentissage social : 21
- Développement psychosocial d'Erikson : 20
- Grands courants de la psychologie : 21
- Intelligence : mesure, Binet-Simon et QI : 25
- Motivation et besoins (Maslow) : 21
- Mémoire : 21
- Méthodes de la psychologie : 22
- Naissance de la psychologie scientifique : 22
- Neurone et synapse : 20
- Organisation du système nerveux : 20
- Oubli et courbe d'Ebbinghaus : 20
- Personnalité : traits et Big Five : 25
- Psychologie sociale : conformité et obéissance : 25
- Sensation et perception : 21
- Stades de Piaget : 21
- Statistiques descriptives de base : 25
- Vygotski et zone proximale de développement : 20
- Émotions (James-Lange, Cannon-Bard) : 22
- Éthique de la recherche : 20

## L2 — 497 questions

Fichiers : higher-l2-psychologie-01.json: 125 ; higher-l2-psychologie-02.json: 125 ; higher-l2-psychologie-03.json: 123 ; higher-l2-psychologie-04.json: 124

Difficultés 1..5 : {1: 151, 2: 146, 3: 100, 4: 60, 5: 40}. Bonnes réponses : {'A': 128, 'B': 122, 'C': 124, 'D': 123}.

Par sous-thème :

- Attitudes et dissonance cognitive : 21
- Attribution causale et erreur fondamentale : 21
- Classifications DSM et CIM : 20
- Cortex cérébral et localisations : 19
- Développement cognitif : théories et critiques : 21
- Influence sociale et groupes : 21
- Langage : acquisition et troubles : 21
- Méthodologie : plans expérimentaux et variables : 21
- Neuropsychologie et imagerie cérébrale : 21
- Psychanalyse freudienne : instances et mécanismes de défense : 20
- Psychologie cognitive : traitement de l'information : 21
- Psychologie de l'éducation : motivation scolaire : 21
- Psychologie de la santé et du stress : 21
- Psychologie interculturelle et ethnopsychologie : 19
- Psychométrie : 21
- Psychopathologie générale : normal et pathologique : 20
- Psychose et schizophrénie : 21
- Raisonnement, jugement et biais cognitifs : 21
- Relations interpersonnelles et agressivité : 21
- Résolution de problèmes et prise de décision : 21
- Statistiques inférentielles : 21
- Stéréotypes, préjugés et discrimination : 21
- Théorie de l'esprit : 20
- Troubles anxieux et troubles de l'humeur : 22

## L3 — 495 questions

Fichiers : higher-l3-psychologie-01.json: 125 ; higher-l3-psychologie-02.json: 123 ; higher-l3-psychologie-03.json: 124 ; higher-l3-psychologie-04.json: 123

Difficultés 1..5 : {1: 146, 2: 150, 3: 99, 4: 60, 5: 40}. Bonnes réponses : {'A': 125, 'B': 125, 'C': 125, 'D': 120}.

Par sous-thème :

- Addictions et conduites à risque : 22
- Analyse de variance et régression : 22
- Approche systémique et thérapies familiales : 21
- Bilan psychologique et tests projectifs : 21
- Deuil, résilience et soutien psychosocial : 20
- Démences et vieillissement : 20
- Déontologie et code du psychologue : 18
- Entretien clinique et observation : 22
- Méthodologie du mémoire : 20
- Petite enfance et parentalité : 20
- Psychologie communautaire et santé mentale en Afrique : 18
- Psychologie de la santé : observance et éducation thérapeutique : 18
- Psychologie du travail : motivation et leadership : 18
- Psychologie scolaire et orientation : 17
- Psychologie sociale appliquée : communication et attitudes : 17
- Psychopathologie de l'enfant et de l'adolescent : 19
- Psychopharmacologie : 21
- Psychothérapies : approche psychanalytique : 19
- Tests d'intelligence de Wechsler : 22
- Thérapie centrée sur la personne (Rogers) : 18
- Thérapies cognitivo-comportementales : 21
- Traumatisme psychique et état de stress post-traumatique : 20
- Troubles de la personnalité : 20
- Troubles du neurodéveloppement : autisme, TDAH : 21
- Épistémologie de la psychologie : 20

## Rejets corrigés
Premier passage de validation : rejets surtout pour « choix dépendant de l'ordre » (choix commençant par « Les deux… », « Tous les… », « Aucune des… »), questions ne finissant pas par « ? », choix > 100 caractères, quasi-doublons liés aux chiffres. Tous reformulés ; état final : 0 rejet, 0 avertissement.

## Limites
- 9 doublons exacts entre fichiers ou niveaux ont été supprimés après coup (pas remplacés) : les totaux sont donc L1 499, L2 497, L3 495 au lieu de 500. Il manque 1, 3 et 5 questions.
- Seuls les doublons exacts ont été contrôlés entre fichiers ; des quasi-doublons de contenu entre niveaux (ex. instances freudiennes L2/L3) peuvent subsister. Relecture humaine nécessaire (statut `review`).
- Les agents ont rédigé les questions sans vérification externe : faits classiques de cours, à relire.
- `python3` (3.11) plante sur `tools/quiz-bank/qb/gen/sup_compta_l1.py` (f-string 3.12, fichier d'un autre rédacteur, non modifié) ; validation faite avec `python3.12`.
- `content/quiz/dist`, `android/`, `backend/` non modifiés.
