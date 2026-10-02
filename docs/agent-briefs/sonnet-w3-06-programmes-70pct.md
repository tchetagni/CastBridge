# w3-06 — Programmes officiels par (classe, matière) et porte « 70 % de couverture »

**Vague 3 · Effort M (≈ 2 j) · Statut BLOQUÉ PARTIEL** — **D15** : le propriétaire doit fournir les textes officiels (PDF MINESEC « programmes d'études », MINEDUB 2018, GCE Board syllabi). Sans eux, l'agent livre le **format**, l'outil, et 3 fichiers d'exemple marqués « reconstitué, à confirmer » ; il ne « devine » pas 60 programmes. Branche `claude/sonnet-w3-06`. Rapport : `docs/agent-reports/sonnet-w3-06.md`.

## Objectif
1. Format `docs/curriculum/programmes/<cursus>-<classe>-<matiere>.md` : liste numérotée des chapitres/objectifs officiels, source (titre, année, page), statut (`officiel` / `reconstitué`).
2. `cbvalidate.py programme` : pour chaque pack, lit `chapters[].programRef`/`lessons[].programRef`, les apparie aux chapitres du fichier de programme (par identifiant `P<n>` ou par titre normalisé), calcule `couverts / officiels`, échoue sous 0,70 avec `--strict` (avertissement sinon), et produit un tableau par classe/matière.
3. `docs/CONTENT-ARCHITECTURE.md` § porte : définition écrite de la porte (celle que personne n'avait trouvée : audit contenu § 4).

## Pourquoi (preuves)
- `docs/curriculum/*.md` (10 domaines, 22-56 lignes) : descripteurs de niveaux N1-N4, **pas** des listes de chapitres ; chaque `programRef` est reconstitué (« à vérifier sur le texte officiel ») : 142/259 packs, 1 141/2 537 fiches.
- La « porte 70 % » n'existe nulle part (`docs/CONTENT-*.md`, scripts, cahiers) ; seuls des seuils de maîtrise à 70 % (`CONTENT-ARCHITECTURE.md:59,174,179`).
- Audit : CO-4, R7 de l'audit contenu.

## Fichiers possédés
Nouveau dossier `docs/curriculum/programmes/` (+ `README.md`), `tools/content-validation/cbvalidate.py` (commande `programme` **additive** ; coordonner avec w2-12 déjà fusionné), `tools/content-validation/test_cbvalidate.py`, `docs/CONTENT-ARCHITECTURE.md` (§ porte). **Hors zone** : `content/learn/**`, `LessonValidator`.

## Étapes
1. `programmes/README.md` : format, convention de nommage (`fr-cm2-maths.md`, `en-form5-chemistry.md`, `sup-droit-l1-obligations.md`), champ `source:` obligatoire, statut, règle « un chapitre = une ligne `P<n>. Titre` ».
2. Trois exemples : `fr-cm2-maths.md`, `fr-3e-maths.md`, `en-form5-mathematics.md` à partir des `programRef` existants des packs (statut `reconstitué`), pour exercer l'outil.
3. `cbvalidate.py programme [--lot learn/<pack>] [--strict]` : appariement par `P<n>` si le `programRef` le cite, sinon par similarité de titre (normalisation accents/casse, Jaccard ≥ 0,6) ; sortie : `pack | officiels | couverts | % | manquants` ; code 1 sous 70 % avec `--strict` ; packs sans fichier de programme → « programme absent » (jamais une erreur en v1).
4. Tests : appariement exact, par titre, pack sans programme, seuil.
5. `CONTENT-ARCHITECTURE.md` : « Porte de couverture : un pack est publiable en `validated` si ≥ 70 % des chapitres du programme officiel ont au moins une fiche `validated` ; calcul : `cbvalidate.py programme --strict` ».

## Critères d'acceptation
```sh
cd tools/content-validation && python3 -m unittest -q test_cbvalidate              # vert
python3 tools/content-validation/cbvalidate.py programme --lot learn/cm2-maths      # tableau, % calculé
python3 tools/content-validation/cbvalidate.py programme                            # toutes les classes : « programme absent » là où il manque, code 0 sans --strict
ls docs/curriculum/programmes/*.md | wc -l                                           # ≥ 4 (README + 3)
```

## Cas limites
- Programmes par **série** (A/C/D/E/TI) et par **sous-système** : le nom de fichier porte la série quand elle change le programme (`fr-tle-c-maths.md`).
- Ne pas copier le texte intégral d'un programme officiel sous droit d'auteur : titres de chapitres et références de page seulement.

## À ne pas faire
Pas de commit sur les branches partagées, pas d'invention de programmes « officiels », pas de modification de packs, pas de secret.

## Rapport
`STATUT: BLOQUÉ` (D15 : liste des PDF à fournir, par priorité : cm2, 3e, tle, class6, form5) + ce qui est livré.
