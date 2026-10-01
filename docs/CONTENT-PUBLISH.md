# Publication du contenu : du dépôt au serveur (règle A)

> **Règle A : rien n'est produit sur le serveur.** Les agents travaillent dans le dépôt ; le résultat fini est **construit** par `tools/publish-content.sh` puis **copié** sur le serveur de production **par le propriétaire**. Aucun agent ne se connecte au serveur, n'y écrit ni n'y déploie. Rien de ce qui suit n'a été exécuté contre la production.

## 1. Vue d'ensemble

```
dépôt (agents)  ──▶  tools/publish-content.sh  ──▶  build/content-release/tree/  ──(propriétaire : rsync)──▶  serveur
  content/             1 valider tout                CONTENT-RELEASE.json
  content/graph/       2 construire les paquets      SHA256SUMS
                       3 construire TOUS les lots    lots/castbridge-lot-*.lot + catalog.json (signé)
                       4 version + arbre rsync       graph/  MEDIA-MANIFEST.json
```

## 2. Le script

```sh
tools/publish-content.sh [--out DIR] [--version V] [--bump] [--sign-key PEM] [--skip-gradle-checks]
tools/publish-content.sh --rsync utilisateur@hôte:/chemin/                # SIMULATION (rsync --dry-run)
tools/publish-content.sh --rsync utilisateur@hôte:/chemin/ --go           # copie réelle : exige CASTBRIDGE_CONFIRM_PRODUCTION=yes ET un catalogue signé
```

| Étape | Ce qui est vérifié ou produit | Outil |
|---|---|---|
| 1 Valider | graine du graphe à jour (`gen_graph.py --check`) · graphe de compétences, parcours, portées (`:core:checkContentGraph`) · leçons Apprendre (`:core:checkLearnContent`) · banques de quiz (`quizbank.py check`, Python ≥ 3.12 ; sinon ignoré **avec avertissement**) · manifeste de médias · budgets · tests Python des outils · format du rapport pédagogique d'exemple et des rapports `content/qa/*.json` | scripts + `tools/core-harness/run.sh` |
| 2 Paquets | `:core:buildLearnPacks` → `<id>-v<n>.learn.zip` (les paquets de quiz sont déjà dans `content/quiz/dist`) | gradle |
| 3 Lots | un **lot** par (fonction, portée) : `castbridge-lot-<feature>-<scope>-v<n>.lot`, ZIP **déterministe** (`lot.json` + `packs/` + `media/`), puis le **catalogue** `catalog.json` | `tools/content-lots/build_lots.py` |
| 4 Release | `CONTENT-RELEASE.json`, `SHA256SUMS`, arbre prêt pour rsync | `tools/content-lots/make_release.py` |

Gradle sans le plugin Android : `tools/core-harness/run.sh` (le module `:core` est du JVM pur ; dans un conteneur sans accès au plugin Android, c'est le moyen de lancer `:core:test`). Autre lanceur : `GRADLE_RUNNER=...`.

## 3. Lots, versions et catalogue signé

- **Nom** : `castbridge-lot-<feature>-<scope>-v<version>.lot` (comme `castbridge.core.lots.LotNames`). Contenu : `lot.json` (feature, scope, version, titre, compétences, empreintes), `packs/…` (les paquets Apprendre `*.learn.zip` et Quiz `*.quiz.zip` de la portée), `media/…` (médias déclarés pour ce lot). Les deux fonctions peuvent changer l'organisation interne de leurs lots (branches `claude/learn-lots`, `claude/quiz-lots`) sans toucher à la version, au catalogue ni à la signature, qui ne dépendent que du fichier lot.
- **Version** : `content/LOT-VERSIONS.json` garde l'empreinte du contenu de chaque lot. Un lot **ne change de version que si son contenu change** ; un contenu modifié sans `--bump` fait **échouer** la construction (la CI ne change jamais une version par surprise) ; `--bump` incrémente de 1 les seuls lots modifiés. Les lots sont reproductibles à l'octet près (testé).
- **Catalogue** : même format et même texte canonique de signature que `LotManifest` (`castbridge-lot-catalog-v1 / channel / feature / generatedAt / lot=…`), signé **Ed25519** avec `--sign-key` (clé privée PEM, ou `CASTBRIDGE_LOT_SIGN_KEY`) ; sans clé, il est écrit **NON SIGNÉ** (les téléphones le refusent ; la copie réelle est refusée). La clé de signature est celle des mises à jour : **décision du propriétaire**, jamais dans le dépôt.
- **TV** : un lot compatible TV ne dépasse pas 3 Mo ; la TV reçoit les lots du téléphone (jamais du serveur) ; elle rattrape directement la dernière version.

## 4. `CONTENT-RELEASE.json`

```json
{"format":1,"version":"2026.10.01","generatedAt":"…","sourceCommit":"…","channel":"stable","signed":true,"keyId":"…",
 "features":{"learn":{"lots":8,"bytes":…},"quiz":{"lots":6,"bytes":…}},
 "lots":[{"id":"learn:cm2","version":1,"bytes":…,"sha256":"…"}],
 "graph":{"domains":7,"skills":378},"files":27,"totalBytes":…,"treeSha256":"…",
 "limits":{"tvLotMaxBytes":3145728,"phonePathMaxBytes":104857600,"totalMaxBytes":3221225472}}
```
`treeSha256` = empreinte des lignes `chemin empreinte` triées de `SHA256SUMS` : deux constructions du même contenu donnent la même empreinte. `sha256sum -c SHA256SUMS` vérifie l'arbre après la copie.

## 5. Copie vers le serveur (décision du propriétaire)

Modèle (jamais exécuté ici) :

```sh
# 1. construire et signer
CASTBRIDGE_LOT_SIGN_KEY=/chemin/privé/lots.pem tools/publish-content.sh --bump --version 2026.11.01
# 2. simulation : liste ce que rsync changerait
tools/publish-content.sh --skip-gradle-checks --rsync deploy@bridge.sti-cm.com:/srv/castbridge/content/
# 3. copie réelle, volontairement lourde à lancer
CASTBRIDGE_CONFIRM_PRODUCTION=yes tools/publish-content.sh --rsync deploy@bridge.sti-cm.com:/srv/castbridge/content/ --go
```
La copie réelle envoie **d'abord les lots**, **puis** le catalogue et `CONTENT-RELEASE.json` : un téléphone ne voit jamais un catalogue qui annonce un lot pas encore arrivé. Le serveur (`backend/…/lots/`) reçoit ensuite les lots par son point d'entrée d'administration (`POST /api/v1/admin/lots`, non publié par défaut) : cette étape reste **manuelle et hors du dépôt**.

## 6. Dépôt de contenu dédié et privé (décision du propriétaire)

Le propriétaire a choisi un **dépôt privé dédié** (nom suggéré : `castbridge-content`, à créer par lui) : texte et ressources vectorielles dans git, médias lourds via **Git LFS** ou assets de release ; le dépôt de code ne contient **aucun** média lourd.

| Élément | Où |
|---|---|
| `.gitattributes` (LFS : `*.webm *.mp4 *.opus *.ogg *.webp`, fins de ligne), README, workflow de CI | `tools/content-split-repo/templates/` |
| Export | `tools/content-split-repo/split_repo.sh <dossier>` : copie `content/` (learn, quiz/dist, graph, médias, registre des versions), les outils `tools/content-*`, `tools/tests`, `core-harness`, `pedagogy-report`, `publish-content.sh`, les quatre documents, `CODE-REF` (commit du code) |
| Contrôle de tailles | `tools/content-split-repo/check_sizes.py --mode code` (dépôt de code : aucun fichier > 50 Mo, aucun binaire > 5 Mo, aucun média lourd sous `content/`) · `--mode content` (dépôt de contenu : idem, les binaires > 5 Mo n'étant admis que s'ils sont suivis par LFS) |

Le script **ne pousse rien** et ne crée aucun dépôt ; il affiche les commandes (`git init`, `git lfs install`, `git remote add`, `git push`) que le propriétaire exécute. Les validateurs Kotlin restent dans le dépôt de code (épinglés par `CODE-REF`) : `CASTBRIDGE_CODE_DIR` pointe vers une copie de ce commit. **Aucun binaire de plus de 5 Mo n'est commité** dans le dépôt de code par ce travail.

## 7. Plafonds (rappel)

| Plafond | Valeur | Contrôle |
|---|---|---|
| lot compatible TV | 3 Mo (figures et animations comprises) | `content_budget.py` |
| lots d'un parcours sur le téléphone | 100 Mo | `content_budget.py` (`paths.json`) |
| un fichier | 50 Mo | `content_budget.py`, `check_sizes.py` |
| base entière | 3 Go (alerte à 2,5 Go) | `content_budget.py` |
| binaire commité | 5 Mo (hors LFS) | `check_sizes.py` |
