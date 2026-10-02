# w10-14 — Kit du pilote papier : journal des ventes, calcul des relevés, fiches terrain, questionnaires, affiche-catalogue

<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet au 2e échec ou fichier sensible · statut : PRÊT
> **Groupe : W10d-4** (vague W10d) · prérequis : aucun · porte : `python3 -m unittest discover -s tools/tests -p 'test_releve.py'`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 10d · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT.** Conception : DESIGN-W10 § 2.4, § 10.2-10.7. Branche `claude/sonnet-w10-14`. Rapport : `docs/agent-reports/sonnet-w10-14.md`. Indépendant : peut partir le jour 1.

## Objectif
Tout ce qu'il faut pour **mener le pilote sans serveur** : un gabarit de journal des ventes (CSV), un script `releve.py` qui calcule les parts et imprime un relevé producteur à partir du CSV, la fiche du point focal pilote, le questionnaire foyer (S3 et S6), le guide d'entretien producteur (S2 et S6), la fiche d'incident, et une affiche-catalogue (texte à mettre en page).

## Pourquoi (preuves)
- DESIGN-W10 § 10.4 A : « journal des ventes papier (modèle `content/oeuvres/pilote/ventes.csv`) », « `releve.py` calcule les parts depuis le CSV » ; § 10.5 (collecte) ; § 10.2 (métriques H1-H11).
- `docs/FICHE-POINT-FOCAL.md` (w4-18 / w5-23, **si présents** : même ton, renvoi ; sinon autonome).
- `tools/tests/` : patron de tests Python existants.

## Fichiers possédés
Nouveaux `content/oeuvres/pilote/ventes.csv`, `content/oeuvres/pilote/releve.py`, `content/oeuvres/pilote/FICHE-POINT-FOCAL-PILOTE.md`, `content/oeuvres/pilote/QUESTIONNAIRE-FOYER.md`, `content/oeuvres/pilote/ENTRETIEN-PRODUCTEUR.md`, `content/oeuvres/pilote/FICHE-INCIDENT.md`, `content/oeuvres/pilote/AFFICHE-CATALOGUE.md`, `content/oeuvres/pilote/README.md`, `tools/tests/test_releve.py`. **Hors zone** : tout le reste.

## Étapes
1. `ventes.csv` (en-tête + 3 lignes d'exemple fictives) : `date,agent,foyer,article,montant_xaf,mode,reference,remarque` ; `foyer` = numéro de fiche (jamais un nom), `article` = `loc-oeuvre-<id>|30`, `loc-chaine-<p>|30`, `loc-classe-<scope>|30`, `achat-pack-langues-<code>`, `cle-production|<j>`, `jetons|<n>` ; `mode` = `especes|bon` ; `reference` = serial du bon ou code de commande ; une ligne `REMBOURSEMENT` = montant négatif avec la référence d'origine.
2. `releve.py --ventes ventes.csv --registre content/oeuvres/registry.json --mois 2026-11 --parts 60,15 --seuil 5000 [--producteur slug]` : par producteur : lignes par œuvre/chaîne (locations, remboursements, brut, part producteur, part agent, part plateforme), total, report si < seuil, texte du relevé (même rubriques que `castbridge-producer-statement-v1` de w10-06, **non signé**, mention « relevé provisoire du pilote ») ; `--csv` ; chaîne ⇒ 100 % au producteur ; erreurs : article inconnu, montant non numérique, mois vide ; bibliothèque standard.
3. `FICHE-POINT-FOCAL-PILOTE.md` : rôle, ce qu'il vend (trois familles), comment faire écouter un aperçu (TV ou téléphone), prendre une commande (fiche papier), remplir `ventes.csv` le soir, remettre les espèces, suppression à l'échéance (pilote papier A : œuvres en clair), que dire sur la vie privée (pas de nom), à qui remonter un incident ; **aucun montant** (emplacements `[prix]`).
4. `QUESTIONNAIRE-FOYER.md` : 10 questions (aperçu écouté ?, loué ?, pourquoi / pourquoi pas, prix juste ?, durée, genres/langues préférés, Apprendre ?, Langues ?, réclamation ?, consentement oral noté) ; grille de codage pour H1-H4, H9-H10.
5. `ENTRETIEN-PRODUCTEUR.md` : S2 (effort, outil, attentes) et S6 (relevé compris ?, part acceptable ?, continuer ?, 2ᵉ lot ?) ; codage H5, H8.
6. `FICHE-INCIDENT.md` : date, foyer (numéro), problème, durée, résolution ; codage H7.
7. `AFFICHE-CATALOGUE.md` : structure (trois colonnes : Leçons / Langues / Œuvres d'ici), par œuvre : titre, artiste, genre, durée, classification, `[prix]` 30 j, « Aperçu gratuit sur la TV » ; mention « Les artistes d'ici reçoivent la plus grande part » (si D-W10-3).
8. `README.md` : calendrier S0-S7 résumé, où ranger les papiers, règle « jamais de nom de foyer dans un fichier ».

## Critères d'acceptation
```sh
python3 -m unittest discover -s tools/tests -p 'test_releve.py'                       # vert
python3 content/oeuvres/pilote/releve.py --ventes content/oeuvres/pilote/ventes.csv --registre content/oeuvres/registry.json --mois 2026-11   # relevé des exemples
grep -L 'pilote' content/oeuvres/pilote/*.md                                          # vide (chaque fiche dit « pilote »)
grep -rn '[0-9]\{3,\} XAF' content/oeuvres/pilote/*.md                                # 0 (emplacements [prix] seulement)
```

## Cas limites
- Vente d'une chaîne d'un producteur absent du registre : erreur nommant le slug.
- Remboursement sans référence : avertissement, compté quand même.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun montant réel, aucun nom réel, aucun numéro de téléphone ; pas de dépendance Python ; français.

## Rapport
`STATUT`, exemple de relevé produit, questions.
