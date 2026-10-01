# Grille pédagogique et rapport de QA, par lot

> Utilisée par les agents de contrôle qualité (et les enseignants) pour juger **chaque lot** avant publication. Format machine : un rapport JSON par lot, vérifié par `python3 tools/pedagogy-report/validate_report.py` (`tools/pedagogy-report/sample-report.json` en donne un exemple). Rapports réels : `content/qa/<feature>-<scope>-v<n>.json`. Ce contrôle automatique ne remplace pas la **validation humaine 3 mois après la bêta** : chaque rapport l'indique (`humanValidation`).

## 1. Les dix critères (note de 0 à 4, avec preuve citée)

| Critère | Clé JSON | Ce qu'on vérifie | 4 = | 2 = | 0 = |
|---|---|---|---|---|---|
| Clarté | `clarity` | énoncés, consignes et explications compris du premier coup par le public visé | aucune ambiguïté repérée | quelques énoncés flous | incompréhensible |
| Progression et prérequis | `progression` | ordre logique, un seul nouvel obstacle à la fois ; les prérequis du graphe sont enseignés avant | chaque compétence prépare la suivante | sauts fréquents | ordre arbitraire |
| Exemples résolus | `workedExamples` | ≥ 2 exemples par fiche, étapes visibles, dont une fausse piste | exemples variés et commentés | exemples sans démarche | aucun |
| Idées fausses | `misconceptions` | erreurs fréquentes nommées et corrigées (blocs « pièges », distracteurs plausibles) | les erreurs réelles de ce public sont traitées | erreurs génériques | non traitées |
| Pertinence culturelle | `culturalRelevance` | contextes camerounais (marché, FCFA, taxi-moto, calendrier, langues), pas de stéréotype, pas de personne réelle | contextes justes et variés | contextes importés d'ailleurs | choquant ou faux |
| Niveau de langue | `languageLevel` | vocabulaire et syntaxe adaptés à l'âge ; termes techniques définis ; fr/en corrects ; lisible par un lecteur peu à l'aise | phrases ≤ 20 mots, termes définis | trop dense par endroits | inaccessible |
| Charge cognitive | `cognitiveLoad` | une idée par écran, pas de double tâche, textes ≤ limites du lecteur (650 caractères/bloc) | écrans aérés, étapes séparées | écrans chargés | surcharge systématique |
| Accessibilité | `accessibility` | daltonisme (jamais la couleur seule), dyslexie, alt, légendes et transcriptions, mouvement réduit, pas de clignotement, lisible à 3 m sur TV | tout vérifié | lacunes | inutilisable pour certains |
| Alignement de l'évaluation | `assessmentAlignment` | exercices et questions évaluent **exactement** la compétence (`skill`) au niveau (`level`) annoncé ; paliers et difficultés dans la fenêtre du niveau ; corrigés justes | 100 % alignés | 1 item sur 4 hors cible | sans rapport |
| **Fidélité au niveau** | `levelFidelity` | le contenu **atteint-il vraiment** l'excellence camerounaise (N2) ou mondiale (N4) annoncée ? un très bon élève le trouverait-il difficile ? | standard atteint, items originaux et exigeants | « plus du même » | niveau programme étiqueté excellence |

## 2. Fidélité au niveau (le critère le plus strict pour N2-N4)

Le relecteur (agent ou humain) : (1) lit le descripteur du niveau et de la bande dans [curriculum/](curriculum/README.md) ; (2) **résout lui-même** au moins **5 items** du lot (`itemsChecked ≥ 5` pour un lot N2-N4) sans consulter le corrigé ; (3) vérifie que ces items demandent des **étapes multiples, de la justification, du transfert** (N2 : combiner plusieurs méthodes ; N3 : démontrer, généraliser, modéliser ; N4 : problème ouvert, preuve complète) et pas seulement des calculs plus longs ; (4) estime si un élève du **top 5 % national** (N2) ou **d'un programme d'élite international** (N3-N4) le trouverait stimulant. Il renseigne `levelFidelity` : `claimed` (N0-N4), `standard` (`programme` pour N0-N1, `cm-excellence` pour N2, `bridge` pour N3, `world-excellence` pour N4), `verdict` (`meets` | `partial` | `below`), `challengeForTopLearner` (0-4), `itemsChecked`. L'**originalité** compte : un item recopié d'un concours ou d'un examen est un défaut **bloquant**.

## 3. Seuils et décision

| Règle | Seuil |
|---|---|
| Aucun critère sous | **2,0** (sinon `revise`) |
| Moyenne des dix critères | **≥ 3,0** |
| Accessibilité (porte stricte) | **≥ 3,0** et toutes les vérifications `accessibilityChecks` vraies |
| Lots N2-N4 : `levelFidelity` | **≥ 3,0**, `verdict = meets`, `challengeForTopLearner ≥ 3,0` |
| Point **bloquant** (`severity: blocker`) ou critère < 1 | `reject` (fausse réponse, contenu dangereux, item copié, contenu inadapté à l'âge…) |
| Sinon, seuils atteints | `pass` ; seuils manqués : `revise` |

Une note ≤ 2 exige au moins un `issue` détaillé (`severity` : `blocker` | `major` | `minor`, `ref` = id de leçon/exercice, `note`). **Le validateur recalcule la décision** et refuse un rapport qui affirme `pass` sous les seuils.

## 4. Format du rapport (JSON)

```json
{"format":1,"lot":"learn:cm2-exc","lotVersion":1,"reviewedAt":"2026-10-01","reviewer":{"kind":"agent","name":"qa-…"},
 "language":"fr","targetLevels":["N2"],
 "scores":{"<critère>":{"score":3.5,"evidence":"…","issues":[{"severity":"minor","ref":"cm2-exc-rates-02","note":"…"}]}, "…dix critères…":{}},
 "accessibilityChecks":{"colourBlindSafe":true,"dyslexiaFriendly":true,"altTextEverywhere":true,"captionsAndTranscripts":true,"reducedMotionFallback":true,"noFlashing":true,"tvReadable3m":true},
 "levelFidelity":{"claimed":"N2","standard":"cm-excellence","verdict":"meets","challengeForTopLearner":3.5,"itemsChecked":12,"notes":"…"},
 "humanValidation":{"required":true,"dueAfterBetaMonths":3,"status":"pending"},
 "decision":"pass"}
```
Champs obligatoires : tous ceux ci-dessus (sauf `note` libre). `lot` = `feature:scope` ; `targetLevels` ⊂ N0..N4 ; `humanValidation` toujours `required: true`, `dueAfterBetaMonths: 3`.

## 5. Liste de contrôle du relecteur (à cocher avant de noter)

- [ ] Chaque compétence du lot est dans `content/graph/` ; `skill`, `level`, `lot` renseignés ; prérequis enseignés plus tôt.
- [ ] Chaque exemple résolu est **juste** (recalculé) ; chaque corrigé est juste ; aucune réponse ambiguë ; pas de choix « toutes les réponses ».
- [ ] Les contextes sont camerounais, non stéréotypés ; aucun nom de personne réelle ; pas de contenu politique partisan ni de conseil médical/juridique non relu.
- [ ] Fr et en cohérents ; vocabulaire adapté ; textes courts.
- [ ] Médias déclarés, alt/légendes/transcriptions, mouvement réduit, 0 clignotement.
- [ ] N2-N4 : au moins 5 items résolus par le relecteur ; items **originaux** ; transfert et justification présents.
- [ ] Taille du lot ≤ 3 Mo (TV) ; `content_budget.py` vert.

## 6. Qui note quoi

| Qui | Quand | Rapport |
|---|---|---|
| Agent auteur | avant de livrer un lot | auto-contrôle, pas de rapport officiel |
| Agent QA pédagogique (autre que l'auteur) | à chaque lot et à chaque nouvelle version | `content/qa/…json`, `reviewer.kind = agent` |
| Enseignants / lauréats de concours / juristes / professionnels de santé | **3 mois après la distribution bêta** | `reviewer.kind = human`, `humanValidation.status = done` |
