# w3-07 — Contenu « Apprendre » : étoffer la 2nde et les matières minces de 1re/Tle

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w2-12)
> **Groupe : W3-A** (vague W3) · prérequis : w2-12 · porte : `python3 tools/content-validation/cbvalidate.py content/learn --lot <lot>`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non

**Vague 3 · Effort L (≈ 3-4 j) · Statut PRÊT** (après w2-12 : la porte qualité v1 guide la rédaction). Branche `claude/sonnet-w3-07`. Rapport : `docs/agent-reports/sonnet-w3-07.md`.

## Objectif
1. 2nde : de 54 à ≈ 110 fiches (chaque matière à 10-14 fiches : maths, physique, chimie, SVT, français, english, histoire-géo-ECM, économie).
2. 1re : chimie (4 → 10), english (5 → 10), histoire-géo (6 → 10) ; Tle (`tle-cd`) : english (5 → 10), français (5 → 10), chimie (5 → 10).
3. Tout en `draft` + `reviewNotes`, `programRef` explicite (« reconstitué, à vérifier »), méta de pack `license`/`credits` (porte v1), **aucune** modification des fiches existantes sauf ajout d'un `disclaimer` si la porte l'exige.
4. Lots reconstruits (`LearnTool lots --update`), chaque lot ≤ 3 Mo, `BaseContent` inchangé (les 2 premières fiches existantes restent les premières : **ne pas réordonner les chapitres**).

## Pourquoi (preuves)
- `docs/coordination/AUDIT-CONTENU-APPRENDRE-2026-10-02.md` § 2-3 : 2nde 54 fiches (4-6 par matière, moitié du collège), chimie 2nde/1re à 4, english 1re/Tle/2nde à 5, français 2nde/Tle à 5, hist-géo 1re/Tle à 6.
- Mesure : une fiche ≈ 13 Ko JSON, ≈ 3 Ko zippée ; +55 fiches ≈ +0,17 Mo : aucun lot n'approche 3 Mo.
- Outils : `tools/learn-authoring/lessonlib.py` + `check.sh` ; validation `gradle :core:checkLearnContent` ; relecture `docs/LEARN-REVIEW.md` régénérée par `LearnTool review`.
- Audit : CO-1, CO-2 (porte v1), trous n° 2-3 de l'audit contenu.

## Fichiers possédés
`content/learn/2nde-*/**`, `content/learn/1ere-chimie*/**`, `content/learn/1ere-english*/**` (ou `1ere-anglais*` : vérifier le nom réel dans `scopes.txt`), `content/learn/1ere-histoire-geo*/**`, `content/learn/tle-english*/**`, `content/learn/tle-francais*/**`, `content/learn/tle-chimie*/**`, `content/learn/lots.json` (**entrées de ces lots seulement**), `docs/coverage/learn-lycee.md`. **Hors zone** : tout autre pack (w3-08 : primaire anglophone, droit), `scopes.txt` (sauf si un pack neuf doit y entrer : alors **une** ligne, signalée), code Kotlin, outils.

## Étapes
1. Lire `docs/LEARN.md` § 3 (format `format: 1`, blocs, exercices, `selfCheck`), `docs/PEDAGOGY-RUBRIC.md`, `docs/curriculum/{mathematiques,physique,chimie,svt,francais,english,histoire-geo-ecm,droit-eco-gestion}.md` (bandes N1-N4), les fiches existantes du pack pour le style et les ids (`<pack>-<chapitre>-<n>`).
2. Par matière : plan des chapitres manquants (programme MINESEC reconstitué, à marquer), puis rédaction : objectifs, 2 exemples travaillés, bloc clé, 3 exercices d'application + 2 d'approfondissement + 1 niveau examen, 5 QCM d'auto-évaluation, figure avec `alt` quand utile (≤ 8 Ko), textes ≤ 650 caractères par bloc, phrases courtes.
3. Après chaque pack : `bash tools/learn-authoring/check.sh <pack>` puis `cd android && gradle --offline :core:checkLearnContent` ; corriger jusqu'à 0 erreur ; les avertissements de la porte v1 (w2-12) sont à réduire, pas obligatoirement à zéro (le dire).
4. `cd android && gradle --offline :core:test --tests 'castbridge.core.*Learn*'` (y compris `LearnLotsTest` : relancer `LearnTool lots --update` pour les lots modifiés : commande dans `docs/LEARN.md` § 4b).
5. `docs/coverage/learn-lycee.md` mis à jour (fiches par matière, manques restants).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:checkLearnContent                                  # 0 erreur
cd android && gradle --offline :core:test --tests 'castbridge.core.*Learn*' --tests 'castbridge.core.*Starter*' --tests 'castbridge.core.*Embedded*'   # vert
python3 - <<'EOF'
import json,glob
n=sum(len(glob.glob(f'{p}/lessons/*.json')) for p in glob.glob('content/learn/2nde-*'))
print('2nde fiches', n); assert n>=100
EOF
python3 tools/content-budget/content_budget.py --quiet                                   # budgets OK
git diff --stat content/learn/ | grep -v '2nde-\|1ere-\|tle-' | grep -v lots.json        # vide (rien d'autre touché)
```

## Cas limites
- Séries A/C/D : si un pack est par série (`2nde-maths-a`, `2nde-maths-c`), compléter chacun ; ne pas fusionner.
- Ids immuables : jamais renommer une fiche existante.
- Contenu original ; pas de copie de manuels ; faits datés en histoire : prudence et `reviewNotes`.

## À ne pas faire
Pas de commit sur les branches partagées, pas de modification de fiches existantes (hors méta), pas de code, pas de secret ; français correct (orthographe vérifiée).

## Rapport
`STATUT`, tableau avant/après par classe/matière, erreurs/avertissements de la porte, lots reconstruits (versions).
