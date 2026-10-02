# Coordination des agents (le dépôt sert de messagerie)

Une session cloud ne peut pas écrire à la session locale (`castbridge-f4`), et celle-ci ne lit pas leurs réponses : tout passe donc par Git.
Pas de conversation, des **fichiers courts, à ajout seul** (pas de conflits). Tout en français, sans secret, sans journal recopié.

## Les rôles
- **Coordinateur** (session locale `castbridge-f4`) : écrit les cahiers, tient `docs/coordination/BOARD.md`, fusionne dans `integration/agents`, compile Android, teste sur la TV et le téléphone.
- **Agent** (routine ou session cloud) : exécute UN cahier, sur UNE branche, et rend compte dans UN fichier de rapport.

## Le protocole d'un agent
1. **Départ** : `git fetch`, lis `docs/agent-briefs/<id>.md` et la section « Réponses du coordinateur » en bas du cahier. Crée `claude/<id>` depuis `origin/integration/agents`.
2. **Rapport vivant** : crée `docs/agent-reports/<id>.md` sur ta branche, première ligne `STATUT: EN COURS`. À chaque jalon, ajoute UNE ligne : `- AAAA-MM-JJ HH:MM | jalon | commit abc1234`. Commit + push à chaque jalon.
3. **Blocage ou question** : mets `STATUT: BLOQUÉ` en première ligne et ajoute `QUESTION: …` (une phrase, avec les options). Pousse, puis continue le reste qui n'en dépend pas. Ne devine pas une décision du propriétaire.
4. **Réponse** : le coordinateur répond dans la section « Réponses du coordinateur » du cahier, sur `integration/agents`. À chaque jalon, relis-la (`git fetch && git show origin/integration/agents:docs/agent-briefs/<id>.md`).
5. **Fin** : première ligne `STATUT: TERMINÉ`, puis un résumé de 10 lignes max : livré, tests avant/après, à valider sur matériel, branche et dernier commit.
6. **Périmètre** : touche seulement les fichiers de ta zone (voir BOARD.md). Ne modifie jamais `integration/agents`, `main` ni le cahier d'un autre agent.

## Le suivi côté coordinateur
`tools/coord-status.sh` : récupère le dépôt et affiche, par branche `claude/*`, le statut, l'âge, le dernier jalon et la question éventuelle.
Les routines sont suivies par leurs exécutions (`list_runs`). Une session ouverte à la main n'est suivie que par son rapport : ouvre-la toi-même pour le reste.

## Nouveaux ordres sans relancer la session
Une session qui a fini relit `docs/coordination/ORDRES.md` (sur `origin/integration/agents`) toutes les minutes ; règles et format dans ce fichier.
Le coordinateur ajoute une ligne `ORDRE n …` qui pointe vers un cahier. Rien n'est exécuté en dehors de ce fichier et des cahiers.

## Flux CI

Trois workflows exécutent les tests et les vérifications de l'intégration :

- **tools.yml** : (`on: push, pull_request, workflow_dispatch` ; `concurrency` par ref ; `timeout: 20 min`) Python 3.12 + FFmpeg ; jobs :
  - `python-tools` : `python3 -m unittest discover` sur `tools/tests`, `tools/trial-edition`, `tools/anim` ; tests de contenu (`content-validation`, `media-pipeline/tests`)
  - `quiz-bank` : tests du quiz-bank et `quizbank.py check` (filtre de chemins `tools/quiz-bank/**, content/quiz/**`)
  - `vectors` : vérification des vecteurs d'activation (dépend de `cryptography`)
  - `content-checks` : budgets de contenu et vérifications d'essai (filtre de chemins `content/**, tools/content-budget/**, tools/trial-edition/**`)

- **android.yml** : (`on: push, workflow_dispatch` ; `timeout: 30 min`) Gradle + tests : `:core:test :sshd:test :core:checkStarterBudget assembleDebug` ; artefacts : APK + rapports de tests

- **release.yml** : (`on: workflow_dispatch` seulement) Build unsigned release APKs (`-PrequireActivation=true`) ; la signature et la publication se font sur le Mac du propriétaire (docs/RELEASES.md) ; artefacts nommés `NON-VERROUILLEE-NE-PAS-DISTRIBUER`.

Pour relancer un workflow : Actions > workflows > Run workflow > Branch/Ref.

## Ce qui reste manuel (propriétaire)
Approbations (accès aux dépôts, branches), installation sur la TV, tout ce qui touche au serveur de production.

## Barrière anti-régression (W14)

| Type de cahier | Exécutant doit faire tourner | Coordinateur avant fusion | Opus audite |
|---|---|---|---|
| cœur pur hors liaison/transfert/confiance (`C/learn`, `C/quiz`, `C/lots`…) | porte étroite du cahier + `:core:test --tests 'castbridge.core.journey.*'` | `:core:test` complet + lint | si C = 2 (routage existant) |
| **liaison / confiance / transfert / TV serveur** (`C/trust`, `C/xfer`, `C/tv/ReceiverServer`, `C/link`) | porte + **J complet** + le parcours J **nommé** dans le cahier (nouveau ou étendu) | J complet + **F `--tv fake`** (< 10 min) | **oui** (diff + rapport F) |
| **écran ou service Android** (`S/**`, `R/**`) | `compileDebugKotlin` + **lint de pureté** + J de la zone (via la fonction pure créée/modifiée) | **F `--tv fake`** obligatoire ; `--tv emu` si l'écran est TV ; **aucune fusion sans `REPORT.md` PASS** | échantillon (1 sur 3) |
| docs, CI, scripts | porte du cahier | lint YAML/Python | non |
| **correctif terrain** | test rouge d'abord (§ 4.4), puis vert ; ligne REGRESSIONS | J + F ; H si l'écran TV change | oui si liaison/confiance/transfert |

Règles :
1. **Aucune fusion d'un cahier touchant `S/**` ou `R/**` sans `tools/smoke/out/<date>/REPORT.md` PASS** cité dans le rapport.
2. **Au plus 2 cahiers risqués** (liaison/confiance/transfert) en parallèle, un seul sur `C/tv/ReceiverServer.kt`, `C/trust/LinkDriver.kt`, `S/UploadService.kt`, `S/TvLink.kt`.
3. **Tout correctif terrain** = ligne `docs/REGRESSIONS.md` + test rouge d'abord.
