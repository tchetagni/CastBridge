# w3-08 — Contenu « Apprendre » : French et Citizenship en Class 1-5, droit L1-L2, avertissements droit/santé

**Vague 3 · Effort L (≈ 3 j) · Statut PRÊT** (après w2-12). Branche `claude/sonnet-w3-08`. Rapport : `docs/agent-reports/sonnet-w3-08.md`.

## Objectif
1. Class 1 à 5 (sous-système anglophone) : nouveaux packs `class<n>-french` et `class<n>-citizenship` (8-10 fiches chacun, en anglais avec le français comme objet pour `french`).
2. Droit : `droit-l1-*` (8 fiches de droit pur → ≈ 20 : obligations, contrats spéciaux, institutions, introduction OHADA) et `droit-l2-*` (29 → ≈ 45).
3. **Avertissement** par pack pour tous les packs de droit (L1-L3, 24 packs) et les fiches santé (`gs-decouverte` hygiène, `vie-*` santé) : champ `disclaimer` (porte v1 de w2-12) : « Ces fiches sont un support pédagogique, pas un conseil juridique / médical. Vérifiez les textes en vigueur / consultez un professionnel de santé. »
4. Lots reconstruits ; `scopes.txt` complété pour les nouveaux packs.

## Pourquoi (preuves)
- Audit contenu § 2-3 : Class 1-5 sans French ni Citizenship/ICT ; droit-l1 50 fiches dont 8 de droit ; droit-l2 29 (MINCE) ; 0 avertissement sur les 24 packs de droit et les fiches santé (audit CO-2, LE-5).
- `docs/coverage/learn-anglophone.md` : programmes MINEDUB anglophones non consultés, œuvres non incluses ; `docs/curriculum/droit-eco-gestion.md` bandes N1-N4.
- Outils : `tools/learn-authoring/` (générateurs `anglophone/`), `LearnTool`, `:core:checkLearnContent`.

## Fichiers possédés
`content/learn/class1-french/**` … `class5-french/**`, `class1-citizenship/**` … `class5-citizenship/**` (nouveaux), `content/learn/droit-l1-*/**`, `content/learn/droit-l2-*/**`, `content/learn/droit-l3-*/pack.json` (**`disclaimer` seulement**), `content/learn/gs-decouverte/pack.json` et `content/learn/vie-*/pack.json` (**`disclaimer` seulement**), `content/learn/scopes.txt` (**lignes class1-5 seulement**), `content/learn/lots.json` (ces lots), `docs/coverage/learn-anglophone.md`, `docs/coverage/learn-technique-sup.md`. **Hors zone** : lycée francophone (w3-07), code, outils.

## Étapes
1. Lire `docs/LEARN.md` § 3, `docs/PEDAGOGY-RUBRIC.md`, les packs `class<n>-english` existants (style, ids, `lang: en`), `docs/curriculum/droit-eco-gestion.md`, les packs droit existants.
2. Class 1-5 French : alphabet et sons, salutations, nombres, couleurs, famille, école, jours/mois, phrases simples, chansons/comptines libres (texte original) ; exercices QCM/appariement/dictée (modèle) ; `programRef` « MINEDUB anglophone, French — reconstitué, à vérifier ». Citizenship : famille, école, village/quartier, règles de vie, symboles nationaux (faits vérifiables : drapeau, hymne — **ne pas reproduire** l'hymne intégral, citer), hygiène et sécurité, environnement.
3. Droit : fiches L1 (introduction au droit, sources, personnes, biens, obligations : formation/effets/inexécution, responsabilité, contrats spéciaux : vente/bail/mandat, institutions judiciaires camerounaises, introduction OHADA) ; L2 (sûretés, sociétés OHADA, droit pénal général, procédure civile de base, droit administratif) ; **aucun numéro d'article ni chiffre légal affirmé** sans `reviewNotes` ; `disclaimer` sur chaque pack.
4. Validation après chaque pack (`check.sh`, `:core:checkLearnContent`) ; lots (`LearnTool lots --update`) ; `scopes.txt` : ajouter les packs aux scopes `class1`…`class5` existants.
5. Couvertures mises à jour.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:checkLearnContent                                   # 0 erreur
cd android && gradle --offline :core:test --tests 'castbridge.core.*Learn*' --tests 'castbridge.core.*Starter*' --tests 'castbridge.core.*Embedded*'   # vert
ls -d content/learn/class{1,2,3,4,5}-french content/learn/class{1,2,3,4,5}-citizenship | wc -l    # 10
python3 - <<'EOF'
import json,glob
d=[p for p in glob.glob('content/learn/droit-l*/pack.json')+glob.glob('content/learn/vie-*/pack.json')+['content/learn/gs-decouverte/pack.json'] if 'disclaimer' not in json.load(open(p))]
print('sans disclaimer:',d); assert not d
EOF
python3 tools/content-budget/content_budget.py --quiet
```

## Cas limites
- `BaseContent` : les 2 premières fiches des **nouveaux** packs entrent dans l'APK (+ ≈ 60 Ko zip pour 10 packs) : vérifier `LearnContentTest.embeddedBudget` (5 Mo, actuel 2,3 Mo).
- Si le validateur refuse `disclaimer` (champ inconnu) : w2-12 l'a ajouté ; sinon mettre le texte en `reviewNotes` de pack et le signaler.

## À ne pas faire
Pas de commit sur les branches partagées, pas de code, pas de modification des fiches existantes (hors méta `disclaimer`), pas de recopie de textes officiels, pas de secret.

## Rapport
`STATUT`, tableau avant/après, lots, erreurs/avertissements, taille embarquée.
