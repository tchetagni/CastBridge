# Gestion des versions de CastBridge : politique de bout en bout (DevOps, 2026-10-02)

> Demande du propriétaire : « en tant qu'expert DevOps, assure efficacement le versionning du projet ».
> Document de politique et de procédure. **Aucun secret.** Rien n'a été commité, poussé, étiqueté ni déployé en le produisant : les outils livrés (`tools/release/check_versions.py`, `tag-plan.sh`, `deploy-server.sh`) sont en lecture seule ou en simulation par défaut, et les décisions qui engagent le dépôt de référence ou la production sont listées au § 15 pour le propriétaire.
> Compléments : `docs/RELEASES.md` (procédure de publication des APK, § 12 à 14 ajoutés), `docs/CHANGELOG.md` (journal reconstruit).

## 1. Constat de départ (vérifié le 2026-10-02)

| Sujet | Constat |
|---|---|
| Tags git | **Aucun** (`git tag -l` vide) : impossible de retrouver le code d'une APK ou du serveur par son numéro. |
| Source des versions | `version.properties` (créé le 2026-10-01, commit `0ff64ed`) : phone 1.2.29-beta (59), TV 0.14.17-beta (60), propriétaire 0.2.3 (5), dev 0.1.0 (1). **Pas de ligne pour le serveur** (`backend/pom.xml` : 1.0.0, jamais incrémenté). |
| Branches | `main` (local) = `05a303d` du 2026-09-29, **637 commits** derrière `integration/agents` ; `origin/main` = `1d81b61` du 2026-10-01 (446 commits derrière) ; `bridge/main` = `98525a9` (449 derrière). `main` est ancêtre de `integration/agents` dans les trois cas : une avance rapide est possible, sans conflit. 49 branches locales (dont 21 `worktree-agent-*`), 68 branches distantes. |
| Arbre de travail | 82 fichiers modifiés, environ 100 non suivis (travail du 2026-10-02), dont `version.properties` (clé `lock.graceDays`) : un build actuel porte « 0.14.17-beta » avec un contenu qui n'est pas celui du commit `faf8636`. |
| Serveur | Le VPS (`ubuntu@bridge.sti-cm.com`, partagé avec d'autres projets derrière un nginx commun) exécute `/home/ubuntu/castbridge/services/castbridge/backend` : **pas un clone git** (copie de fichiers du 2026-09-30), image `castbridge-api:current` étiquetée « revision unknown ». `backend/deploy.sh` (git fetch + checkout) n'y est pas utilisable ; la révision en production est **intraçable**. Le remote `bridge` est un dépôt bare (`/home/ubuntu/castbridge/castbridge.git`) qui ne contient que `main` (`98525a9`). |
| APK | Ni l'identifiant de commit ni la date ne sont dans les APK : seuls `versionName` et `versionCode` (écran « Version » du téléphone, écran d'activation de la TV, `/api/apk` de la TV d'après `docs/HANDOFF.md`). |
| Contenu lourd | `content/langues-media/` (66 fichiers audio du propriétaire) est non suivi **et non ignoré** par `.gitignore` : un `git add -A` l'ajouterait au dépôt de code. |

## 2. Sources de version : `version.properties` reste la source unique

Gradle lit déjà `version.properties` (surcharge possible par `-Pcastbridge.versionName` / `-Pcastbridge.versionCode`, **jamais en production**). Proposition pour y ajouter le serveur (à faire par le propriétaire ou dans un commit dédié ; `version.properties` n'a pas été modifié ici) : ajouter à la fin du fichier

```properties
# CastBridge Serveur (backend/, déployé par tools/release/deploy-server.sh ; doit égaler <version> de backend/pom.xml)
server.versionName=1.0.0
server.versionCode=1
```

Règles associées :

- `server.versionName` **égale** `<version>` de `backend/pom.xml` (premier enfant `<version>` du projet, pas celui de `<parent>`). `check_versions.py` signale tout écart. Pour changer : `./mvnw versions:set -DnewVersion=1.0.1` puis la même valeur dans `version.properties`, dans un commit dédié.
- `server.versionCode` est un entier strictement croissant (il sert à l'ordre et à l'audit ; le serveur n'en a pas besoin pour fonctionner).
- Les autres sources de vérité sont **dérivées** : tag git, `versionName` d'une APK (via Gradle), étiquettes de l'image Docker (`org.opencontainers.image.version` et `.revision`, posées par `deploy-server.sh`), fichiers `REVISION` et `RELEASE` de la release serveur. Aucune ne se saisit à la main.
- Aucune version codée en dur dans `android/*/build.gradle.kts`, `pom.xml` (hors la valeur synchronisée ci-dessus) ou dans la documentation autre que `docs/CHANGELOG.md` et `docs/RELEASES.md` § 9.

## 3. Numérotation

- **SemVer** `MAJOR.MINOR.PATCH`, suffixe **`-beta`** tant que le produit n'est pas offert à la vente (règle actuelle, conservée). Passage en `1.0.0` stable (serveur et applications) : décision du propriétaire, après la boutique et la bêta réelle.
- **versionCode strictement croissant, jamais réutilisé**, même après un retour arrière ou un build d'essai. Android refuse une mise à jour dont le code n'est pas supérieur.
- **TV, variante verrouillée** : code du `version.properties` **+1** et suffixe `-verrouillee` au nom. Conséquence à connaître : le code `N+1` est pris par la variante verrouillée, donc **le prochain code de la TV est au moins `N+2`** (c'est ce que montre l'historique : 39, 41, 43, 45, 47, 49, 54, 56, 58, 60). `check_versions.py` impose cette règle (erreur si la TV monte de moins de 2 codes quand le nom change, et aussi par rapport aux tags `tv-*`).
- Un **build d'essai** porte `-test` (ex. `0.14.12-test`), n'a pas de tag, n'est jamais publié ni envoyé au serveur `/admin/releases` ; on ne le désigne jamais comme « version ».
- Incrément : PATCH pour une correction ou un lot de fonctions compatible tant que le produit est en bêta (pratique actuelle : `0.14.x`, `1.2.x`) ; MINOR pour un jalon fonctionnel annoncé ; MAJOR réservé à la sortie commerciale.
- Un numéro est posé **dans un commit dédié** (`chore(release): …`, § 6) qui ne touche que `version.properties`, `docs/CHANGELOG.md` et `docs/RELEASES.md` § 9 : c'est ce commit que `tag-plan.sh` retrouve pour étiqueter (la version introduite par un commit mélangé à du code reste prouvable, mais l'étiquette désigne alors du code non revu seul).
- Écarts d'historique à ne pas reproduire : TV 0.14.12 et 0.14.13 construites sans commit de numéro ; code téléphone passé de 35 à 56 (1.2.25 vers 1.2.26) ; apps construites avant le commit qui pose leur numéro.

## 4. Étiquettes (tags)

| Tag | Quand | Sur quel commit |
|---|---|---|
| `tv-<versionName>` | à chaque TV publiée (une étiquette pour les deux variantes) | commit de numéro |
| `phone-<versionName>` | à chaque téléphone publié | idem |
| `owner-<versionName>` | à chaque console Propriétaire livrée au propriétaire | idem |
| `server-<versionName>` | à chaque version du serveur déployable (après création des lignes `server.*`) | idem |
| `release-AAAA.MM.JJ-N` | publication coordonnée TV + téléphone + serveur ; N = rang du jour | le commit qui contient les trois numéros ; son message liste les trois tags |

Règles : tags **annotés** (`git tag -a`, message = application, version, code, variante verrouillée) ; **jamais déplacés, jamais supprimés** une fois poussés (une erreur se corrige par un nouveau numéro) ; poussés un par un par leur nom (`git push origin refs/tags/<tag>`), jamais `--tags` ; pas de tag `dev-*` (outil jamais distribué).

Mise en place initiale des tags manquants : `bash tools/release/tag-plan.sh` (plan, sans effet). Résultat au 2026-10-02 : **26 versions prouvables** (TV : 10, de 0.14.6 à 0.14.17 sauf 0.14.12 et 0.14.13 ; téléphone : 11, de 1.2.19 à 1.2.29 ; propriétaire : 5, 0.1.0 et 0.2.0 à 0.2.3), **aucune ambiguë** ; les numéros antérieurs (0.13.x, 1.2.1 à 1.2.5, 0.4 à 0.12) ne sont cités que dans des sujets de commits et ne sont pas étiquetables de façon sûre. Le propriétaire décide si les 26 sont créées (`--apply`) et publiées (`--push`) : voir § 15.

## 5. Dépôts, branches et mise à jour de `main`

### Deux remotes

| Remote | Cible | Rôle | Règle |
|---|---|---|---|
| `origin` | GitHub `tchetagni/CastBridge` | dépôt de référence, CI, relecture | tout le développement ; branches, PR, tags de version |
| `bridge` | `ubuntu@bridge.sti-cm.com:castbridge/castbridge.git` (dépôt **bare** de production, `/home/ubuntu/castbridge/castbridge.git`) | source des déploiements du serveur | **ne reçoit que les tags `server-<v>` (ou une branche de publication) poussés délibérément par l'outillage du propriétaire** (`deploy-server.sh` ; jamais `main`, jamais `--force`, jamais le déplacement d'une référence existante) ; aucun développement ne s'y fait |

Le dépôt bare ne contient aujourd'hui que `main` (`98525a9`) et n'a pas de hook actif. Les références utiles y sont consultables en lecture seule par `bash tools/release/deploy-server.sh --status --apply` (`git for-each-ref`).

### Modèle de branches

- **`main`** = ce qui a été publié et validé. Elle doit toujours désigner un commit **étiqueté** (`release-…` ou au moins `tv-…`/`phone-…`).
- **`integration/agents`** = branche d'intégration actuelle (les agents y fusionnent leurs branches `claude/*` après tests). Décision du propriétaire : déploiements faits depuis elle ; **on ne fusionne rien dans `main` sans son accord**.
- **Branches de fonction courtes** `feat/<sujet>`, `fix/<sujet>`, `claude/<sujet>` (agents) : créées depuis `integration/agents`, fusionnées puis **supprimées** sous deux semaines. Hotfix : `fix/<sujet>` depuis le tag de la version en cause (`docs/RELEASES.md` § 11), fusionné dans `integration/agents` (et `main` après validation).
- Jamais de travail direct sur `main`. Jamais de `push --force` sur une branche partagée. `wip/external-ai-changes` n'est **jamais** fusionnée (audit du bilan du 2026-10-02).
- Nettoyage proposé (aucune suppression faite) : 21 branches locales `worktree-agent-*` (worktrees d'agents) et les branches `claude/*` déjà fusionnées ; à supprimer après vérification par `git branch --merged integration/agents`.

### Mettre `main` à jour : décision du propriétaire, procédure la plus sûre

`main` est 637 commits en retard (locale) et le propriétaire n'a pas décidé de la fusion. Procédure recommandée, **chaque étape est réversible jusqu'à la dernière** et ne change rien en production :

1. **Figer le travail du jour** : committer le travail non commité en commits thématiques (§ 6), poser un commit de numéro de version, relancer les tests (`docs/RELEASES.md` § 4). Rien de ce qui suit n'a de sens sur un arbre sale.
2. **Étiqueter la tête d'intégration** : `git tag -a release-2026.10.02-1 -m "…" <commit>` (et les tags de § 4 via `tag-plan.sh --apply`), puis `git push origin refs/tags/release-2026.10.02-1`. Un tag est une photo : on peut toujours y revenir, même si la fusion tourne mal.
3. **Ouvrir une Pull Request** `integration/agents` → `main` sur GitHub. `main` étant ancêtre de `integration/agents`, la fusion est une **avance rapide** : aucun conflit possible. Relire la liste des fichiers (pas de secret, pas de contenu lourd : § 8) et laisser tourner la CI (§ 9).
4. **Fusion par le propriétaire** lui-même, via le bouton GitHub (« Create a merge commit » conserve l'historique ; l'avance rapide stricte se fait seulement par un `git push origin <commit>:main` qui doit rester une décision explicite du propriétaire). Aucun outil de ce dépôt ne pousse `main`.
5. **Après la fusion** : `git fetch`, vérifier `git rev-parse origin/main`, mettre à jour la `main` locale par `git merge --ff-only origin/main` (jamais `reset --hard`), et seulement ensuite supprimer les branches obsolètes. Faire de `main` la branche par défaut des déploiements (`deploy-server.sh` n'accepte que des tags ou des branches de publication, jamais `main` directement).

## 6. Commits

- **Conventional Commits** : `type(scope): sujet à l'impératif, sans point`, corps libre en français ou en anglais (l'historique est en anglais depuis le 2026-09-29 ; **choisir l'anglais pour les sujets** et rester cohérent), une idée par commit.
  - types : `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `security` ; marqueur `!` ou `BREAKING CHANGE:` pour une rupture (format de clé, route, base).
  - scopes usuels : `tv`, `phone`, `owner`, `core`, `server`, `content`, `tools`, `docs`, `ci`.
  - commit de numéro : `chore(release): tv 0.14.18-beta, phone 1.2.30-beta, owner 0.2.4` (ne touche que `version.properties`, `docs/CHANGELOG.md`, `docs/RELEASES.md` § 9).
- **Pieds de message** (ceux déjà utilisés, à garder sur chaque commit produit avec l'assistant) :
  ```
  Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_<identifiant>
  ```
  Aucun secret, jeton, chemin de clé ou adresse privée dans un message de commit.
- Un commit ne mélange pas : code et contenu lourd ; numéro de version et fonctionnalité ; formatage global et correction.
- Le sujet suit la forme actuelle de l'historique quand il le faut (« Phone: … », « Docs: … ») tant que la convention n'est pas adoptée ; l'important est qu'il dise **ce qui change pour l'utilisateur**, car `docs/CHANGELOG.md` est construit à partir des sujets.

## 7. Liste de contrôle de publication

À cocher dans l'ordre (détails de construction et de signature : `docs/RELEASES.md` § 4 à 8) :

1. [ ] Arbre propre (`git status`), branche `integration/agents` à jour de `origin`, rien en retard.
2. [ ] Tests : `:core:test`, `:sshd:test`, `:core:checkStarterBudget`, `mvn test` si le serveur change, `python3 tools/activation/verify_vectors.py`, `python3 -m unittest discover -s tools/tests`.
3. [ ] Commit de numéro (§ 3, § 6) : versions, `lock.graceDays` / `lock.graceStartMs` jamais reculées, `server.*` si le serveur change, `docs/CHANGELOG.md` complété (une entrée par numéro), `docs/RELEASES.md` § 9.
4. [ ] `python3 tools/release/check_versions.py` : **0 erreur** (les avertissements sont lus et compris).
5. [ ] Construction (variante sans verrou pour l'essai, verrouillée pour la production), signature **locale** (jamais en CI, `docs/RELEASES.md` § 6).
6. [ ] `python3 tools/release/check_versions.py --apk <APK…>` : chaque APK conforme à `version.properties` (nom, code, variante verrouillée).
7. [ ] `bash tools/release/sha256sums.sh ~/CastBridge-release/` puis `--check` (§ 10).
8. [ ] Essai sur la TV de référence (GaiaOS, armeabi-v7a) et le téléphone : badge d'activation, démarrage sans plantage, parcours de base (`docs/RELEASES.md` § 7).
9. [ ] Publication : copie dans le `Download/` de la clé USB par SSH, `SHA256SUMS` joint, `/admin/releases` du serveur pour les APK publiées.
10. [ ] `bash tools/release/tag-plan.sh` relu puis `--apply` (tags locaux) ; `release-AAAA.MM.JJ-N` si publication coordonnée ; **`--push` sur décision du propriétaire**.
11. [ ] Serveur, si concerné : tag `server-<v>`, `bash tools/release/deploy-server.sh server-<v>` (plan), puis `--apply` (§ 11).
12. [ ] `docs/HANDOFF.md` mis à jour (versions en service, tags posés, état du serveur ; **sans secret**).

## 8. Ce qui va dans le dépôt de code et ce qui va dans `castbridge-content`

| Dépôt de code `CastBridge` | Dépôt privé `castbridge-content` |
|---|---|
| Code (`android/`, `backend/`, `tools/`), documentation, tests, vecteurs de test, `version.properties`, workflows CI | Médias lourds (audio, vidéo, images volumineuses), sources de contenu non nécessaires à la compilation, lots chiffrés et archives générées |
| Contenu **embarqué** et petit : leçons JSON, figures sous le budget de 8 Ko, `content/langues/*` (JSON), registres (`lots.json`, `LOT-VERSIONS.json`, `MEDIA-MANIFEST.json`) | Instantané et phases de production de contenu (briefs `castbridge-content`) |

Règles :

- **`content/langues-media/` (66 fichiers audio du propriétaire) ne doit jamais être commité dans le dépôt de code.** Il est aujourd'hui non suivi et non ignoré : un `git add -A` ou `git add .` le publierait. Mesure proposée (à appliquer par le propriétaire, `.gitignore` n'a pas été modifié ici) : ajouter à `.gitignore` la ligne `content/langues-media/` ; ne jamais utiliser `git add -A` : ajouter par chemins.
- Le manifeste (`MEDIA-MANIFEST.json`, sommes SHA-256 et tailles) est, lui, versionné dans le dépôt de code : il permet de vérifier le contenu sans le contenir.
- Garde-fou : la CI (§ 9) échoue si un fichier suivi dépasse 1 Mo hors des chemins autorisés (proposition) ; 7 roues Python de 75 Mo ont déjà été commitées par erreur (corrigé le 2026-10-02, mais elles restent dans l'historique de `.git` : 162 Mo).
- Les clés (`*.jks`, `*.keystore`, `*.pem`, `*.key`, `.env`, `secrets/`) sont déjà ignorées ; la clé de signature des applications, `~/.castbridge-signing` et les clés de confiance d'activation vivent **hors dépôt**.

## 9. Garde-fous d'intégration continue (propositions, rien n'est appliqué)

`.github/workflows/tools.yml` est un fichier **nouveau non suivi** (et `android.yml`, `release.yml` sont modifiés non commités) : ajouter ce qui suit à `tools.yml` dans le commit qui l'introduit.

```yaml
  versions:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v5
        with: { fetch-depth: 0 }          # l'historique et les tags sont nécessaires
      - uses: actions/setup-python@v5
        with: { python-version: "3.12" }
      - run: python3 tools/release/check_versions.py        # code 1 = erreur : la CI échoue ; les avertissements sont affichés
      - run: python3 -m unittest discover -s tools/tests -p 'test_release_tools.py'
      - name: Aucun fichier lourd suivi
        run: |
          git ls-files -z | xargs -0 ls -l | awk '$5 > 1048576 {print $5, $9; bad=1} END {exit bad}'
```

Notes : sur une CI, l'arbre est propre et `git fetch` complet, donc l'état git est fiable ; le contrôle d'avance/retard n'a de sens qu'en local. Le seuil de 1 Mo doit être ajusté à l'état réel du dépôt (des figures et des fichiers JSON de contenu sont légitimes) avant d'être activé, sous peine d'échec immédiat. Dans `android.yml` : rien à changer pour les versions (Gradle lit `version.properties`) ; ne **jamais** déclencher une publication sur un tag `tv-*` / `phone-*` (la signature reste locale, `release.yml` n'a que `workflow_dispatch`).

## 10. Sommes de contrôle (`SHA256SUMS`)

`bash tools/release/sha256sums.sh ~/CastBridge-release/` écrit `SHA256SUMS` (format `shasum -a 256 --check`) pour les APK du dossier, et `--check` le vérifie. Usage : produit avant la copie sur la clé USB et le serveur ; la TV et le téléphone comparent la somme avant installation ; le fichier est conservé avec chaque version (dossier d'archives) et le tag annoté mentionne la somme de l'APK verrouillée. Une APK dont la somme ne correspond pas n'est ni installée ni publiée. (Le fichier `SHA256SUMS` n'est pas dans le dépôt de code.)

## 11. Serveur : déploiement traçable

### Principe

`backend/deploy.sh` suppose un clone git dans le répertoire déployé. Ce n'est pas le cas du VPS (copie de fichiers), donc **`tools/release/deploy-server.sh`** : (1) refuse si l'arbre local suivi est modifié, si la révision n'est atteignable d'aucune branche `origin/*`, si la référence est `main` ou un commit nu, ou si le remote `bridge` ne pointe pas sur l'hôte cible ; (2) pousse le **tag** (ou la branche de publication) vers `bridge`, sans `--force`, en refusant de déplacer une référence existante ; (3) sur le serveur, `git --git-dir=/home/ubuntu/castbridge/castbridge.git archive <sha> backend | tar -x` dans une **nouvelle** release `/home/ubuntu/castbridge/releases/<tag>-<UTC>` (jamais dans le répertoire vivant), y copie `.env` (0600), `secrets/`, `docker-compose.override.yml` (copies) et `geoip/` (lien symbolique : répertoire possédé par root), écrit `REVISION` (sha) et `RELEASE` (référence, version, date, release précédente) ; (4) construit `castbridge-api:candidate` avec `--build-arg VCS_REF=<sha>` et les étiquettes `org.opencontainers.image.revision=<sha>` et `.version=<tag>` (le `Dockerfile` pose déjà `revision` à partir de `ARG VCS_REF`, le script ajoute `version` et `created` à la construction, sans modifier le `Dockerfile`) ; (5) sauvegarde la base par `backup.sh --db-only` et **abandonne** si elle échoue ; (6) étiquette l'image en service `castbridge-api:previous`, bascule `:current`, relance **uniquement** `castbridge-api` (`sudo docker compose --project-name castbridge up -d --no-build --no-deps castbridge-api`) ; (7) attend le healthcheck, puis seulement bascule le lien `/home/ubuntu/castbridge/current` (et `previous`) ; en cas d'échec, **retour automatique** (image précédente et fichiers de la release précédente) et code de sortie 1, le lien `current` n'ayant pas bougé.

Garanties : les conteneurs des autres projets du VPS (`sti-frontend`, `sti-backend`, `sti-db`, `infra-nginx`, `infra-certbot`) ne sont ni listés, ni arrêtés, ni redémarrés ; nginx n'est pas rechargé ; la base (`castbridge-db`) n'est pas recréée (`--no-deps`) ; la release d'origine reste intacte (copie, jamais déplacement). Les migrations Flyway ne reviennent pas en arrière : elles doivent rester compatibles avec le code précédent (README du backend) ; le `--rollback` ne restaure pas la base (utiliser la sauvegarde faite avant le déploiement).

### Commandes

```bash
bash tools/release/deploy-server.sh server-1.0.0                   # plan : aucune connexion, imprime les commandes locales et le script distant
bash tools/release/deploy-server.sh server-1.0.0 --push-only --apply   # publie seulement le tag dans le dépôt bare
bash tools/release/deploy-server.sh server-1.0.0 --apply           # push + déploiement
bash tools/release/deploy-server.sh --status --apply               # révision en service (REVISION, étiquettes du conteneur), références du dépôt bare, historique
bash tools/release/deploy-server.sh --rollback --apply             # échange current/previous (images et fichiers), attend la santé
```

Variables (valeurs par défaut sûres) : `CB_DEPLOY_HOST` (`ubuntu@bridge.sti-cm.com`), `CB_DEPLOY_ROOT` (`/home/ubuntu/castbridge`), `CB_LIVE_DIR`, `CB_BARE_REPO`, `CB_BRIDGE_REMOTE` (`bridge`), `CB_DOCKER` (`sudo docker`), `CB_HEALTH_TIMEOUT` (240 s), `CB_SSH_OPTS`.

### Migration depuis la disposition actuelle (première exécution)

1. Prérequis, par le propriétaire : lignes `server.*` (§ 2), tag `server-1.0.0` sur le commit à déployer, branche poussée sur `origin` (GitHub), remote `bridge` présent localement (`git remote -v`).
2. `bash tools/release/deploy-server.sh server-1.0.0` (plan) : relire la release cible, la source des fichiers d'exploitation et le script distant.
3. `--apply` : comme il n'existe pas encore de lien `current`, la source de `.env`, `secrets/`, `geoip/` et `docker-compose.override.yml` est `/home/ubuntu/castbridge/services/castbridge/backend`, **copiée et jamais déplacée**. Sa révision n'étant pas connue (image « unknown »), le script écrit `/home/ubuntu/castbridge/releases/INITIAL-REVISION-INCONNUE.txt` (« révision initiale inconnue », chemin copié, date, identifiant d'image) et fait pointer `previous` vers cette ancienne disposition : le retour arrière de la première exécution la remet en service.
4. Contrôle : `--status --apply` : `REVISION` et étiquettes du conteneur désignent le sha du tag ; `bash ops/first-run/check-server.sh` (lecture seule) et `backend/smoke-test.sh`.
5. Ancien répertoire : le **garder** au moins deux semaines (c'est la cible du retour arrière), puis seulement l'archiver ; ne rien y modifier.

Rien n'a été exécuté sur le serveur en rédigeant ce document : les scripts ont été testés avec de faux `docker`/`ssh`/`flock` dans des dépôts temporaires (`tools/tests/test_release_tools.py`).

### Coexistence avec `backend/deploy.sh`

`backend/deploy.sh` (verrou, `git fetch`, `checkout` détaché, image `previous`, sauvegarde, `up -d`, santé, retour arrière) garde exactement ses règles dans un **vrai clone** (nouvelle installation décrite par `backend/README.md`, « Déploiement pas à pas »). `deploy-server.sh` en reprend la sémantique (verrou, image `previous`, sauvegarde avant migration, santé, retour automatique) mais n'a pas besoin d'un clone côté serveur. Ne pas utiliser les deux sur le même répertoire.

## 12. Retrouver ce qui tourne : d'une installation au commit

| Objet | Où lire le numéro | Chemin vers le commit |
|---|---|---|
| APK téléphone | écran « Version installée » (nom + code) ; `aapt2 dump badging fichier.apk` | `versionName` → tag `phone-<versionName>` → `git rev-list -n 1 phone-<versionName>` ; sinon `bash tools/release/tag-plan.sh` donne le commit qui a introduit le numéro |
| APK TV | écran d'activation (version affichée), `/api/apk` (d'après le HANDOFF) | nom sans `-verrouillee` → tag `tv-<versionName>` ; le code −1 donne la variante sans verrou |
| Console Propriétaire | À propos / écran de la console | tag `owner-<versionName>` |
| Serveur | `deploy-server.sh --status --apply` : `REVISION`, étiquettes `org.opencontainers.image.revision` et `.version` du conteneur ; `docker inspect castbridge-api` | `REVISION` = sha ; tag `server-<v>` ; fichier `/home/ubuntu/castbridge/current/RELEASE` |
| Serveur avant le premier déploiement tracé | image « revision unknown » | **intraçable** : noté « révision initiale inconnue » dans `INITIAL-REVISION-INCONNUE.txt` |

Limite connue : les APK n'embarquent pas le sha du commit. Amélioration proposée (hors de ce périmètre, à faire dans Gradle) : ajouter `GIT_SHA` à `BuildConfig` (`git rev-parse --short HEAD`) et l'afficher sous la version ; l'APK d'un arbre sale serait alors repérable (`-dirty`). Aucun contrôle ne remplace pour l'instant la discipline « on ne construit que depuis un commit propre » (§ 7).

## 13. Retour arrière

- **APK** : jamais de numéro réutilisé ; republier l'ancienne source sous un **nouveau** `versionCode` supérieur (`docs/RELEASES.md` § 10). Pour reconstruire une ancienne version sans bouger l'arbre de travail, préférer un worktree séparé (`git worktree add ../cb-old tv-0.14.15-beta`, ou `git archive`) à un `git checkout` dans le dépôt de travail. Le tag `tv-0.14.15-beta` n'existe qu'après `tag-plan.sh --apply`.
- **Serveur** : `deploy-server.sh --rollback --apply` ; automatique en cas d'échec du déploiement ; pour la base, restaurer la sauvegarde `backup.sh` faite avant le déploiement (README du backend, « Sauvegardes »).
- **Tag erroné** : ne pas le déplacer ; poser un nouveau tag sur le bon commit et noter l'erreur dans `docs/CHANGELOG.md`.
- **`main`** : jamais de `reset --hard` ni de force-push ; annuler par un commit `revert` ou revenir au tag `release-…` précédent par une branche.

## 14. Ce qui est versionné, et où

| Objet | Où est le numéro | Format | Outil de contrôle |
|---|---|---|---|
| CastBridge-TV | `version.properties` `tv.versionName` / `tv.versionCode` ; APK (`versionName`, `versionCode`) ; tag `tv-<v>` ; `docs/CHANGELOG.md` | SemVer `-beta`, code entier ; verrouillée : code +1, suffixe `-verrouillee` | `check_versions.py` (+ `--apk`), `tag-plan.sh` |
| CastBridge téléphone | `phone.*` ; APK ; tag `phone-<v>` | idem | idem |
| CastBridge Propriétaire | `owner.*` ; APK ; tag `owner-<v>` (jamais distribué) | idem | idem |
| CastBridge Dev | `dev.*` ; pas de tag (outil jamais distribué) | idem | `check_versions.py` |
| Serveur (`backend/`) | `server.*` (**à créer**) = `pom.xml` `<version>` ; tag `server-<v>` ; étiquettes de l'image ; `REVISION`/`RELEASE` de la release ; `/home/ubuntu/castbridge/releases/<tag>-<UTC>` | SemVer ; code entier | `check_versions.py`, `deploy-server.sh --status` |
| Grâce de la TV verrouillée | `lock.graceStartMs` (jamais reculée), `lock.graceDays` | ms UTC, jours | `check_versions.py` |
| Format de clé / activation | `docs/ACTIVATION-FORMAT.md`, vecteurs `tools/activation` (miroirs Java/Python) | version de format, vecteurs de test | `tools/activation/verify_vectors.py` |
| Migrations de base | `backend/src/main/resources/db/migration` (Flyway `V<n>__…`) | entier croissant, jamais modifié après déploiement | tests du backend |
| Contenu embarqué et lots | `content/**/lots.json`, `LOT-VERSIONS.json`, `MEDIA-MANIFEST.json` ; version propre à chaque lot | versions de lot, sommes SHA-256 | `tools/content-validation`, `:core:checkStarterBudget` |
| Contenu lourd | dépôt privé `castbridge-content` (pas ce dépôt) | instantané daté | `tools/publish-content.sh` |
| Livrables | `~/CastBridge-release/` + `SHA256SUMS` ; clé USB `Download/` ; serveur `/admin/releases` | `<App>-<version>-<abi>.apk` | `tools/release/sha256sums.sh` |
| Documents | `docs/CHANGELOG.md` (journal), `docs/RELEASES.md` (procédure et § 9), `docs/HANDOFF.md` (état courant) | Markdown, sans secret | relecture humaine |
| Clés et secrets | **hors dépôt** (`~/.castbridge-signing`, `backend/.env`, `backend/secrets/`) | — | `.gitignore` |

## 15. Décisions attendues du propriétaire, et ce qui n'a pas pu être vérifié

Décisions :

1. **Créer les 26 tags prouvés ?** `bash tools/release/tag-plan.sh --apply` (locaux), puis publication : `--apply --push` (un tag à la fois sur `origin`). Rien n'est fait.
2. **Ajouter `server.versionName=1.0.0` et `server.versionCode=1`** à `version.properties` (§ 2) et créer `server-1.0.0` : prérequis du premier déploiement tracé.
3. **Commiter le travail du 2026-10-02** en commits thématiques (§ 6) et poser un commit de numéro (TV 0.14.18-beta, téléphone 1.2.30-beta ?) : tant que ce n'est pas fait, aucun build ne doit être distribué sous « 0.14.17 ».
4. **`.gitignore`** : ajouter `content/langues-media/` (§ 8).
5. **Mettre `main` à jour** par la procédure du § 5 (tag, PR, fusion par le propriétaire).
6. **Premier déploiement tracé du serveur** (§ 11) et décision de conserver ou d'archiver l'ancien répertoire.
7. **Sortie de la bêta** : critères du passage à `1.0.0` stable.
8. **Écarts relevés dans `docs/RELEASES.md` à trancher** : le § 3 donne la clé de debug pour la TV sans verrou alors que `docs/HANDOFF.md` mentionne un build d'essai « signé release » ; le § 10 propose un `git checkout` d'un tag dans l'arbre de travail (voir § 13) ; le § 11 parle de fusion dans « `main` / `feat/ssh` » alors que la branche d'intégration est `integration/agents`.

Non vérifié :

- **Aucune connexion au serveur** : le déroulement réel de `deploy-server.sh` (sudo sans mot de passe, version de Docker Compose, comportement de `--no-deps` avec le fichier `docker-compose.override.yml` du serveur, propriété et permissions de `geoip/` et `secrets/`, droits d'écriture de `backup.sh` dans `/var/backups/castbridge`, présence de `flock`) n'est testé qu'avec de faux outils. À valider par un premier `--status --apply` (lecture seule), puis un déploiement de la même révision que l'actuelle.
- Le script distant n'a pas été analysé par `shellcheck` (non installé) ; `bash -n` est vert.
- `aapt2` n'a pas été exécuté sur une vraie APK (comparaison testée avec un faux `aapt2`).
- L'état d'avance/retard sur `origin` vient des références locales (aucun `git fetch` fait).
- Le `/api/apk` de la TV et l'écran d'activation comme lieux d'affichage de la version sont tirés de `docs/HANDOFF.md` et des commits, pas contrôlés sur le matériel.
- La correspondance version / commit antérieure au 2026-10-01 (TV 0.13.x et avant, téléphone 1.2.1 à 1.2.18) est tirée de sujets de commits et n'est pas prouvée.
