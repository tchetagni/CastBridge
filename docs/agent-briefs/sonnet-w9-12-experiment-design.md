# w9-12 — Conception de l'expérience de teasing : matrice, affectation A/B intra-lot, carnet du point focal, mesures

**Vague 9a · Effort S (≈ 1 j) · Modèle : sonnet · Statut PRÊT.** Conception : guide § 5 (en entier), § 9 (D3, D4, D6). Branche `claude/sonnet-w9-12`. Rapport : `docs/agent-reports/sonnet-w9-12.md`.

## Objectif
(1) `experiment.py matrix` : écrit `work/experiment.json` : cellules Langues (zh, ja, en × A0, A1 × audio/cartes/short) et Apprendre (cm2, 3e, tle-cd × 5 shorts), lots cibles, taille prévue ; `experiment.py assign --pack <id>` : **affectation A/B intra-lot** des fiches/unités (SHA-256 de l'id, bit de poids faible ⇒ groupe `A` avec short, `B` sans), déterministe, listée dans `work/experiment-assign.json` et **lue par `scripts.py`** (option `--assign FICHIER`, à documenter pour w9-02 : seules les fiches `A` reçoivent un storyboard) ; `experiment.py analyze --events events.jsonl` : lit des événements au format du catalogue (`learn` : `action`, `pack`, `lesson`, `ms` ; `content_stat` : `kind`, `item`, `shown`, `correct`, `ms`) exportés localement (`GET /api/learn/events?since=` d'une TV bêta ou export admin CSV), joint l'affectation, et imprime : taux `lesson_complete/lesson_view` et `ms` médian par groupe, intervalle de confiance bootstrap simple (1 000 tirages, graine fixe), effectifs ; (2) `docs/content-production/EXPERIENCE-TEASING-W9.md` : protocole complet (hypothèses, cellules, prix en **variables** P-bas/P-moyen/P-haut à fixer par le propriétaire (D4), durées 30 j et 7 j, fenêtre d'essai 3 j / 720 min, seuils de réussite du guide § 5.5, calendrier, consentement, ce qui n'est **pas** mesurable sans D3-a) ; (3) `docs/content-production/CARNET-POINT-FOCAL-W9.md` : fiche papier/CSV par TV (code d'appareil, cellule de prix, cellule de durée, version de lot livrée, date d'ouverture de la fenêtre d'essai, commande oui/non, date, questionnaire de 5 questions, remarques) **sans nom ni téléphone de client**.

## Pourquoi (preuves)
- Télémétrie existante et consentement : `docs/TELEMETRY.md:23-24` (deux niveaux), `:70-89` (catalogue : `learn`, `content_stat`, `screen_time`, `library_stats`), `:57-59` (clés interdites) ; `android/core/.../telemetry/Telemetry.kt:48-56` (propriétés `learn`, actions `lesson_view`, `lesson_complete`, `exercise_result`) ; **aucun événement de lecture de média de leçon** ⇒ D3.
- Location : `docs/RENTAL-LOTS.md:11` (lots libres jamais loués ⇒ expérience sur Apprendre), `:123,129` (fenêtre d'essai, 30 j par défaut), `content/bundles-rental.json` (`bundles` pour une durée propre, ex. 7 j) ; `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md:11,66` (≤ 60 j, grille de prix signée).
- Essai : `docs/TRIAL-EDITION.md:65` (planchers média de l'essai) ; `docs/LEARN.md:512` (500 derniers événements lisibles sur la TV).
- Classes retenues : `docs/coordination/AUDIT-CONTENU-APPRENDRE-2026-10-02.md:12` (socle : cm2, 3e, tle-cd).

## Fichiers possédés
`tools/content-gen/experiment.py`, `tools/content-gen/tests/test_experiment.py`, `docs/content-production/EXPERIENCE-TEASING-W9.md`, `docs/content-production/CARNET-POINT-FOCAL-W9.md`.

## Étapes
1. `matrix` (constantes dans le script, surchargeables par `--langs`, `--levels`, `--classes`) ; sortie JSON déterministe.
2. `assign` : lit les ids de fiches (`content/learn/<pack>/lessons/*.json`) ou d'unités (`langue.json`) ; équilibre vérifié (|A−B| ≤ 1 par pack, sinon bascule la dernière) ; `--seed-salt` pour une seconde expérience.
3. `analyze` : parseur tolérant JSON-lines/CSV ; aucune propriété interdite n'est lue ni réécrite ; sortie Markdown.
4. Documents : hypothèses H1 (shorts ⇒ +15 % de fiches terminées), H2 (voix de synthèse jugée compréhensible ≥ 8/10), H3 (conversion essai → location ≥ 10 %), H4 (chargement < 2 s) ; **plan de mesure manuelle** sur la TV de référence (chronomètre, `GET /api/lots` pour `usedBytes`, `GET /api/learn/events`) ; questionnaire ; critères d'arrêt ; ce que la version « point focal » permet sans serveur (clés et locations émises par `tools/activation-desktop`, `tools/rental-test`).
5. Tests : `assign` déterministe et équilibré sur un pack de test de 7 fiches ; `analyze` sur 40 événements synthétiques ⇒ taux attendus ; aucune clé interdite dans les sorties (`FORBIDDEN` recopié du catalogue dans le test).

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_experiment.py'
python3 tools/content-gen/experiment.py assign --pack content/learn/cep-maths --out /tmp/a.json && python3 -c "import json;d=json.load(open('/tmp/a.json'));a=sum(1 for v in d['assign'].values() if v=='A');b=len(d['assign'])-a;assert abs(a-b)<=1"
python3 tools/content-gen/experiment.py analyze --events tools/content-gen/tests/fixtures/events-40.jsonl --assign /tmp/a.json
grep -c "XAF" docs/content-production/EXPERIENCE-TEASING-W9.md   # ≥ 1, mais aucun montant chiffré : grep -E "[0-9]{3,} ?XAF" → 0
```

## Cas limites
Pack avec 1 fiche ⇒ groupe `A` et avertissement « pas d'A/B possible » ; événements sans `lesson` ⇒ ignorés et comptés ; export contenant un nom de fichier ⇒ ligne rejetée (clé interdite) ; TV sans consentement « statistiques d'usage » ⇒ aucun événement `learn` : le protocole le dit et prévoit la lecture locale.

## À ne pas faire
Aucun prix chiffré ; aucun nouvel événement de télémétrie ; aucune donnée personnelle dans le carnet ; ne pas modifier `Telemetry.kt` ni `EventCatalog.java`.

## Rapport
`STATUT`, matrice finale (nombre de lots, Mo prévus), hypothèses et seuils, ce qui dépend de D3/D4/D6.
