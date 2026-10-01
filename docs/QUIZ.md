# Quiz sur la TV — « Le Quiz des Millions » (POC, version 0.5-quiz)

Jeu de questions façon « Qui veut gagner des millions ? » sur CastBridge TV. On y joue **seul à la télécommande** (sans
téléphone ni réseau) ou **à plusieurs** : chacun répond sur son téléphone, sans rien installer (QR code affiché sur la TV).

- Ouvrir : touche **MENU** de la télécommande > « Quiz culture générale », ou onglet **Quiz** de l'app téléphone CastBridge.
- Télécommande : flèches = se déplacer, **OK** = valider, **RETOUR** = pause (reprendre / abandonner / quitter, toujours avec
  confirmation).

## 1. Choisir sa partie (écran de la TV)

**Mode → Parcours → Niveau → Filière → Façon de jouer**

| Mode | Principe |
|---|---|
| Compétition entre amis | gratuit, pour le plaisir |
| Compétition avec mise | mise en **jetons virtuels sans valeur** (voir § 5), en Duel seulement |
| Entraînement | sans enjeu ni chrono, l'explication de chaque réponse s'affiche, une erreur n'arrête pas la partie |

| Parcours | Contenu |
|---|---|
| Culture générale | **70 % Cameroun, 20 % Afrique, 10 % Monde** (± 1 question) |
| Primaire | SIL, CP, CE1, CE2, CM1, CM2 ; Class 1 à 6 |
| Secondaire | 6e à Tle ; Form 1 à 5, Lower / Upper Sixth |
| Supérieur | L1, L2, L3, par filière : droit, économie, mathématiques, physique, psychologie, géographie, littérature, histoire, informatique, chimie, biologie, philosophie, sociologie |

Chaque parcours indique sous son nom « *N questions · M parties sans répétition (objectif 300)* » (§ 6 bis). Un niveau ou une filière sans questions s'affiche « bientôt » : il suffit d'ajouter des questions avec ce `level` / `field`
pour l'ouvrir (§ 6). Dans le POC : culture générale, **CM2, 3e, Tle, L1 droit, L1 économie, L1 mathématiques**.

| Façon de jouer | Qui joue | Réseau |
|---|---|---|
| **Seul, à la télécommande** | la TV | aucun |
| Millionnaire avec le public | un candidat (TV ou téléphone) + public et amis sur téléphones | Wi-Fi |
| Duel | 1 à 8 joueurs, tous sur téléphone | Wi-Fi |

## 2. Règles

### Millionnaire
- 15 questions de difficulté croissante (3 par niveau 1 → 5). Échelle de gains **fictifs** en FCFA, de 10 000 à
  100 000 000 ; **paliers de sécurité** à la 5e (100 000) et à la 10e question (3 000 000).
- On choisit une réponse, puis « **C'est votre dernier mot ?** » (Oui / Non). Réponse verrouillée en orange, suspense ~1,8 s,
  puis révélation : vert ✓ / rouge ✗ (jamais la couleur seule) et une courte explication.
- Erreur = on repart avec le dernier palier atteint. **S'arrêter** = on garde le gain de la dernière bonne réponse.
- Chrono : 30 s (questions 1-5), 45 s (6-10), aucun ensuite ; temps écoulé = comme une erreur. Pas de chrono en Entraînement.
- Jokers, **une fois chacun** par partie :
  - **50:50** retire deux mauvaises réponses (jamais la bonne) ;
  - **Avis du public** : vote réel des autres joueurs connectés (15 s) ; sans joueur, vote simulé plausible (d'autant plus
    juste que la question est facile) — la TV indique « simulé » ;
  - **Appel à un ami** : le candidat choisit un joueur connecté, qui voit la question 30 s sur son téléphone et suggère une
    réponse ; sans joueur (ou sans réponse à temps) un « ami virtuel » répond avec un degré de certitude.
  Le chrono est en pause pendant le public et l'appel.
- Le candidat peut jouer sur son téléphone ; la télécommande garde toujours la main (utile si ce téléphone se déconnecte).

### Duel
- 10 questions ; tout le monde répond en même temps sur son téléphone (20 s, 40 s en Entraînement).
- Bonne réponse : **1000 points si instantanée, jusqu'à 500 au gong** ; mauvaise ou pas de réponse : 0.
  La rapidité est mesurée avec l'horloge **de la TV** (moment où la réponse arrive), jamais celle du téléphone.
- La question se ferme quand tout le monde a répondu ou à la fin du temps ; révélation (répartition des réponses, qui a
  juste), puis **classement animé** ; OK sur la télécommande passe l'attente.
- Première réponse définitive (renvoyer la même est sans effet, en changer est refusé).

### Meilleurs scores (solo)
Les parties Millionnaire (gain) et Entraînement (bonnes réponses) inscrivent leur score dans un tableau gardé sur la TV, par
façon de jouer et parcours (10 par tableau). « Nouveau record » est célébré ; menu > « Meilleurs scores ».

## 3. Rejoindre depuis un téléphone

- **Sans app** : scanner le QR code de la salle d'attente (appareil photo), ou ouvrir `http://<ip de la TV>:8765/quiz`,
  puis saisir le **code de salle à 4 chiffres** affiché sur la TV et un pseudo. Même Wi-Fi que la TV.
- **App CastBridge** (onglet Quiz) : trouve la TV, peut ouvrir le quiz sur la TV (PIN déjà connu de l'app), récupère le code
  toute seule et rejoint.
- Reconnexion : la page garde un jeton ; en rouvrant la page, on retrouve sa place (et son score).

## 4. Réseau et sécurité (pour les curieux)

- Routes `/quiz/*` sur le serveur existant de la TV (port 8765), **séparées de l'admin** : elles n'utilisent pas le PIN et
  ne donnent accès à rien d'autre. Le code de salle sert seulement à rejoindre ; ensuite un **jeton aléatoire par joueur**
  (128 bits). Le PIN n'est jamais donné aux joueurs.
- État en direct par **Server-Sent Events** (`/quiz/api/events`), repli automatique en long-poll 25 s (`/quiz/api/state`).
  Commandes POST idempotentes (`/quiz/api/act`, avec l'id de la question).
- **La bonne réponse n'est jamais envoyée avant la clôture de la question** (testé).
- Limites : 8 joueurs, 30 requêtes en rafale puis 10/s par adresse IP, 10 codes faux par IP / 5 min, 12 flux d'événements,
  2 par joueur. La salle se ferme en quittant le quiz ou après 10 min sans activité.
- Côté PIN (app téléphone) : `GET /api/quiz` (salle ouverte ? code) et `POST /api/quiz/open` (ouvre le quiz sur la TV).

## 5. « Mise payante » : jetons virtuels seulement

**Dans ce POC il n'y a aucun paiement ni argent réel** : pas d'intégration de paiement, aucune donnée bancaire ni Mobile
Money. La mise est en **jetons virtuels sans valeur** (1 000 offerts à chaque téléphone, remis à zéro au redémarrage de
l'app), affichés « Jetons virtuels — démo ». Mises égales ; la cagnotte est partagée selon le classement (1 joueur 100 % ;
2 : 70/30 ; 3 et plus : 60/30/10 ; ex æquo à parts égales ; 0 point = rien) ; partie abandonnée = mises rendues.

Tout passe par l'interface `WalletProvider` (`core/.../quiz/Wallet.kt`) ; l'implémentation actuelle est `VirtualWallet`.
**Avant de brancher un vrai prestataire**, vérifier le cadre légal camerounais sur les jeux d'argent et concours (jeu
d'adresse ou de hasard, licence éventuelle, âge minimum, identification KYC, fiscalité), puis implémenter `WalletProvider`
côté serveur (jamais de secret de paiement sur la TV).

## 6. Banque de questions — ajouter vos questions

Fichiers : `android/core/src/main/resources/castbridge/quiz/questions.json` (culture générale) et `questions-school.json`
(parcours scolaires). Tout fichier ajouté à `EmbeddedQuestionSource.DEFAULT_RESOURCES` est fusionné. Une question par ligne :

```json
{"id":"cm-geo-001","track":"general","level":null,"field":null,"region":"CM","category":"Géographie","difficulty":1,
 "question":"Quelle est la capitale politique du Cameroun ?","choices":["Douala","Yaoundé","Garoua","Bamenda"],"answer":1,
 "explanation":"Yaoundé est la capitale politique ; Douala est la capitale économique.","source":"Constitution",
 "review":false,"lang":"fr"}
```

| Champ | Règle |
|---|---|
| `id` | unique et stable (slug ou UUID) ; ne jamais le réutiliser pour une autre question |
| `track` | `general` (défaut), `primary`, `secondary`, `higher` |
| `level` / `field` | niveau (`CM2`, `3e`, `Tle`, `L1`, `Form 5`…) et filière (`droit`, `economie`…) — voir `QuizCatalog` |
| `region` | `CM`, `AF`, `WORLD` (la règle 70/20/10 ne s'applique qu'à `general`) |
| `difficulty` | 1 (facile) à 5 (expert), relatif au niveau pour les parcours scolaires |
| `choices` / `answer` | exactement 4 choix distincts ; `answer` = index 0-3. L'ordre est **mélangé à chaque partie** : pas de « toutes ces réponses » |
| `explanation`, `source` | obligatoires (courtes) |
| `review` | `true` = pas sûr : **exclue du jeu** |

Contrôles : `gradle :core:test` vérifie la banque (4 choix distincts, index valide, pas de doublon d'id ni de question,
champs requis, régions, niveaux connus, assez de questions par région et difficulté). Conseils : faits stables et vérifiables,
pas de chiffres qui changent (population…) ni de dirigeants en poste sans date (« en 2024 »), pas de sujets clivants,
mauvaises réponses plausibles mais clairement fausses.

### Questions marquées `review` (exclues du jeu)
- `cm-sym-007` : établissement et année de composition de l'hymne (École normale de Foulassi, 1928) — à confirmer.
- `cm-sym-008` : premier vers exact de la version anglaise de l'hymne.
- `sup-l1-droit-010` : mode de scrutin présidentiel (« à un tour » vient du Code électoral, pas du texte constitutionnel).

À relire en priorité (non marquées, mais à confirmer par un humain) : `cm-geo-022` (chutes d'Ekom-Nkam près de Melong),
`cm-nat-011` (Debundscha), `cm-sym-009` (balance des armoiries), `cm-lang-008` (« a-ka-u-ku »), `sup-l1-droit-002`
(formulée sur la loi de révision du 18 janvier 1996).

Contenu embarqué dans l'APK (inchangé) : 200 questions de culture générale (140 CM, 40 AF, 20 Monde ; 28-29 par difficulté pour le Cameroun, 8
pour l'Afrique, 4 pour le Monde), 120 questions scolaires (CM2, 3e, Tle, L1 droit / économie / mathématiques, 20 chacune).

## 6 bis. Règle des 300 parties : une question ne revient pas avant 300 parties

Demande d'Esaie : « des quiz où la répétition d'une question demande au moins 300 parties ». Règle retenue : **une même
question n'est jamais reposée au même joueur (ou au même appareil) avant que 300 parties du même parcours se soient
écoulées depuis**, tant que la banque le permet.

**Mécanisme** (`core/.../quiz/QuizHistory.kt`, `QuizBank.kt`, `QuizRoom.kt`) :

- Un **historique par profil et par parcours** : le numéro de la partie à laquelle chaque `id` a été posé. Profils : `host` =
  le joueur de la TV (partie solo à la télécommande, Millionnaire sans téléphone) ; `dev:<identifiant du téléphone>` = chaque
  téléphone (l'identifiant que la page de jeu envoie déjà à l'inscription). Parcours = `general`, `primary/CM2`,
  `secondary/3e`, `higher/L1/droit`… (`QuestionFilter.courseKey`).
- `QuizBank.draw(..., history, minGapGames = 300)` n'écarte jamais au hasard : il exclut toute question posée dans les
  300 dernières parties du parcours (`age < 300`), en gardant les quotas 70/20/10 et la difficulté croissante. Les questions
  de la banque non posées depuis le plus longtemps sont préférées ; en Duel / avec plusieurs téléphones, c'est l'**union**
  des historiques des joueurs connectés (une question vue par l'un d'eux est exclue), à défaut celui de l'hôte.
- **Seules les questions réellement affichées sont comptées** : une partie perdue à la question 3 n'« use » pas les douze
  suivantes.
- **Banque trop petite** : on reprend d'abord la question de la région **posée le plus longtemps auparavant**, à ±1 de la
  difficulté voulue si possible (jamais une question récente tirée au hasard). Les quotas 70/20/10 ne sont cassés que si une
  région n'a plus aucune question. `QuizBank.drawDetailed` renvoie un `DrawReport` (nombre de reprises, plus petit écart en
  parties, quotas touchés) ; la salle l'expose (`settings.repeats` dans l'état) : le signalement n'est jamais silencieux.
- **Stockage** : un fichier texte par profil dans `files/quiz/history/` (`CBQH1`, une ligne par question : `id numéro-de-partie`),
  uniquement les 300 dernières parties de chaque parcours (≈ 4 500 lignes au maximum par parcours, 80 à 100 Ko), écriture
  atomique (fichier temporaire + renommage, ancienne version gardée en `.bak`), en tâche de fond : une partie n'attend jamais
  le disque. Fichier endommagé : les lignes lisibles sont gardées, sinon la sauvegarde `.bak`, sinon historique vide. 24 profils
  au plus (les moins récents sont supprimés), 1 Mo par fichier au plus.
- `QuizBank.remainingFresh(filter, history)` → `Freshness(pool, fresh, freshByRegion, gamesLeft, capacityGames)`. L'écran de
  choix de partie de la TV affiche par parcours « *N questions · M parties sans répétition (objectif 300)* » ; `/admin` du
  serveur affiche le même tableau (« Parties sans répétition ») avec ce qui manque.

**Taille de banque requise** : 300 parties × 15 questions = **4 500 questions par parcours** (≈ 3 150 Cameroun, 900 Afrique,
450 Monde pour la culture générale), avec au moins 900 par niveau de difficulté. Le test JVM `QuizNoRepeatTest` le vérifie
avec une banque simulée de 4 500 questions (aucune répétition sur 300 parties, écart minimal ≥ 300 sur 900 parties).

## 6 ter. Volume de contenu : le pipeline `tools/quiz-bank/`

Le mécanisme est inutile sans ~4 500 questions par parcours. Elles ne sont **pas** écrites à la main : `tools/quiz-bank/`
(Python, bibliothèque standard seulement) les produit de façon **reproductible** (mêmes `id`, mêmes archives à chaque
exécution) et les contrôle. Les questions sortent en statut **`review`** (« à vérifier ») : rien n'est présenté comme sûr.

```sh
python3 tools/quiz-bank/quizbank.py check     # génère + contrôle, rien d'écrit (code 1 s'il y a une erreur)
python3 tools/quiz-bank/quizbank.py build     # écrit content/quiz/dist (packs, catalog.json, coverage.json, sources.json)
python3 tools/quiz-bank/quizbank.py report    # tableau de couverture par parcours
python3 -m unittest discover -s tools/quiz-bank   # tests du pipeline
```

| Source | Où | Ce que c'est | Statut / `verif` |
|---|---|---|---|
| Générateurs programmatiques | `qb/gen/*.py` | calcul exact (fractions, dérivées vérifiées numériquement, intégrales par Simpson, dénombrements par force brute) : arithmétique, fractions, FCFA, mesures (CM2) ; algèbre, Pythagore, Thalès, trigonométrie, physique-chimie (3e) ; analyse, complexes, suites, probabilités, physique (Tle) ; matrices, séries, arithmétique modulaire (L1 maths) ; équilibre, élasticités, intérêts, comptabilité, TVA 19,25 % (L1 éco) ; calendrier, fuseaux, siècles (culture générale) ; conjugaison, pluriels (CM2 français) | `review` + `verif: computed` |
| Faits sourcés | `qb/facts/*.py` | tables de faits (régions, départements, villes, fleuves, pays d'Afrique, capitales, histoire, personnalités, concepts de droit, d'économie…) avec une **fiche source par fait** (`sources.json` : où comparer ; la fiche ne prétend jamais que la vérification a été faite) | `review` + `verif: fact` |
| Lots importés | `content/quiz/batches/*.json` | questions écrites par des experts ou par une IA, importées par `quizbank.py import lot.json` (validation, jamais `approved` à l'import) | `review` + `verif: import` |
| Relecture humaine | `content/quiz/approvals.json` | `quizbank.py approve ids.txt --by "Nom" --date 2026-10-15` : les `id` relus passent `approved` | `approved` |

**Contrôle qualité automatique** (`qb/qc.py`, appliqué à chaque `build` ; une question en erreur est exclue et listée dans
`rejected.json`) : champs obligatoires ; 4 choix distincts (casse, accents et ponctuation ignorés) ; index de réponse valide ;
la question finit par « ? » ; longueurs ; pas de « toutes ces réponses » ; espaces mal placés ; parenthèses et guillemets
fermés ; **doublons exacts** et **quasi-doublons** (même réponse et mots proches, formulation identique de deux modèles) ;
orthographe de base (noms propres fréquents) ; **équilibre des bonnes réponses A/B/C/D** (±5 points) ; difficulté 1..5 ;
répartition 70/20/10 et nombre de parties garanties (`coverage.json`) ; part d'un modèle dans un parcours (≤ 8 %) et part du
calcul (≤ 80 % en culture générale et en droit). Les générateurs se vérifient eux-mêmes (substitution, valeur numérique,
force brute) ; une vérification qui échoue est un bogue du générateur, jamais une question.

**Règle d'honnêteté sur le statut `review`** : une question **calculée** a sa réponse prouvée par le calcul mais sa
formulation n'a pas été lue par un enseignant ; une question **factuelle** repose sur des connaissances de l'assistant, à
confronter à sa fiche source. L'appli applique donc : `approved` → jouable ; `review` + `verif: computed` → jouable
(`QuizBank.parse(..., computedPlayable = true)`, pour les packs et lots seulement ; décision centralisée dans `QuizPlay.isPlayable`, § 6 quinquies) ; `review` + `fact` / `import` → **exclue des parties**
tant qu'un humain ne l'a pas approuvée. Pour tout exclure jusqu'à relecture complète (ou pour le canal stable), changer `QuizPlay.isPlayable`.

**Ajouter du contenu** :
1. un modèle calculé : une fonction `@gen("tle", "tle-mon-modele", cap=200, cat="Analyse")` dans `qb/gen/…` qui renvoie un
   `Draft(texte, bonne_réponse, mauvaises_réponses, explication)` ; les mauvaises réponses sont des erreurs typiques ;
2. des faits : `fq(...)` ou `pairs(...)` dans `qb/facts/…` avec une fiche `source(...)` ;
3. un lot rédigé : un fichier JSON (voir l'en-tête de `qb/importer.py`) puis `quizbank.py import` ;
4. `quizbank.py build`, relire `coverage.json` et `sources.json`, committer `content/quiz/`, copier `content/quiz/dist` dans le dossier
   des lots du serveur (`CASTBRIDGE_QUIZ_PACKS_DIR`).

**Chiffres réels (build `v1`, 2026-10-01)** — questions par parcours, toutes `review`, **0 `approved`** :

| Parcours | Questions | dont calculées | dont factuelles | Parties sans répétition (par effectif / par difficulté) | Manque pour 300 |
|---|---:|---:|---:|---|---|
| Culture générale | 3 205 (CM 1 639 · AF 591 · Monde 975) | 1 260 | 1 945 | 156 / 84 | CM ≈ 1 510, AF ≈ 310 |
| Primaire · CM2 | 6 523 | 6 455 | 68 | 434 / 390 | rien |
| Secondaire · 3e | 5 361 | 5 305 | 56 | 357 / 322 | rien |
| Secondaire · Tle | 5 701 | 5 682 | 19 | 380 / 373 | rien |
| Supérieur · L1 Droit | 106 | 0 | 106 | 7 / 0 | ≈ 4 400 |
| Supérieur · L1 Économie | 4 889 | 4 845 | 44 | 325 / 318 | rien |
| Supérieur · L1 Mathématiques | 5 236 | 5 210 | 26 | 349 / 335 | rien |

Lecture honnête : **CM2, 3e, Tle, L1 éco et L1 maths dépassent 4 500 questions, mais ≈ 99 % sont des variantes numériques de 33
à 52 modèles** (une question « 347 + 286 » n'est pas la même que « 512 + 98 », mais ce n'est pas du contenu éditorial) ; ce
volume tient les 300 parties sans répétition, pas la variété des sujets. **La culture générale n'atteint pas 300 parties** (≈ 150,
limitée par la part « Cameroun » : 70 % de 4 500 = 3 150 questions camerounaises, nous en avons 1 639 dont 490 de calendrier
calculé) et **le droit est loin du compte** (106) : ces contenus demandent de vrais rédacteurs et relecteurs — c'est le rôle
de `quizbank.py import` + `approve`. Les « parties par difficulté » comptent 3 questions par niveau de difficulté : la culture
générale n'a que 252 questions de niveau 5, ce qui donne 84 parties avec une montée régulière 1→5 (au-delà, le tirage
prend la difficulté voisine).

## 6 quater. Packs de questions : chargement dynamique sur la TV

La TV ne reçoit pas les ~27 000 questions dans l'APK (la banque embarquée reste ≈ 320 questions, 0 octet de plus) : elles sont
découpées en **packs** (`.quiz.zip`) téléchargés **seulement quand l'historique montre qu'il reste moins de 60 parties
fraîches** (`QUIZ_PACK_THRESHOLD_GAMES`) sur un parcours joué.

- **Format** : une archive par parcours et par lot de ≈ 1 500 questions (`quiz-cm2-p1-v1.quiz.zip` = `manifest.json` +
  `questions.json`, ≈ 75 Ko). Les lots d'un même parcours ont la même répartition (région, difficulté) : n'importe quel lot est
  une mini-banque jouable. **Densité réelle : ≈ 17 800 questions par Mo** compressé ; les 25 packs du dépôt pèsent 1,74 Mo au total.
- **Signés** : le catalogue `GET /api/v1/quiz/packs` donne pour chaque lot `sha256`, taille, version et une **signature Ed25519**
  (même clé que les mises à jour, `UpdateKeys`) du texte `castbridge-quiz-pack-v1\nid=…\ncourse=…\npart=…\nparts=…\nversion=…\nfile=…\nsize=…\nsha256=…\nquestions=…`.
  La TV refuse tout lot dont la signature, la taille, l'empreinte, le contenu ou le parcours ne correspondent pas
  (`QuizPackStore.install`). Une version plus ancienne n'écrase jamais une plus récente.
- **Plafond global : 11 Mo** (`QUIZ_PACK_MAX_BYTES = 11 000 000` octets) pour tous les packs téléchargés. Au-delà, on retire d'abord
  les lots **épuisés** (toutes leurs questions posées dans les 300 dernières parties), puis ceux des parcours **jamais joués**
  ici, les plus anciens d'abord ; un lot du parcours en cours qui a encore des questions fraîches n'est jamais retiré, et si
  rien ne peut partir sans perdre des questions utiles l'installation est refusée.
- **Où** : la clé USB si elle est présente (`CastBridge/QuizPacks/cache`), sinon le stockage externe de l'appli, jamais la
  mémoire interne de la TV si on peut l'éviter (et alors 200 Mo doivent rester libres). Les packs **posés tels quels** dans
  `CastBridge/QuizPacks/` d'une clé USB sont aussi lus (sans plafond, sans téléchargement ; contenu vérifié).
- **Sources, dans l'ordre** : (1) le serveur, en direct ou **par la passerelle Internet du téléphone** (Bluetooth/Wi-Fi, c'est la
  route de repli de `Routes`) ; (2) le **relais par le téléphone** : l'appli téléphone (onglet Quiz) demande à la TV de quoi elle a
  besoin (`GET /api/quiz/packs/status`, PIN), télécharge les lots et les lui envoie (`POST /api/quiz/packs/push?info=…`) ; la TV
  les contrôle comme les siens ; (3) la banque embarquée : la TV reste **jouable hors ligne**.
- **Reprise** : le téléchargement continue après une coupure (requêtes `Range` + `If-Range` sur l'empreinte, fichier `.part`),
  3 tentatives par lot avec pause croissante, puis source suivante.
- **Serveur** (`backend/`) : `CASTBRIDGE_QUIZ_PACKS_DIR` (défaut `{CASTBRIDGE_STORAGE_DIR}/quiz-packs`) contient `catalog.json` + les
  `.quiz.zip` copiés de `content/quiz/dist` ; le serveur n'annonce que les lots dont le fichier correspond à la taille et à
  l'empreinte, et signe le catalogue à la demande. `GET /api/v1/quiz/packs/{fichier}` accepte les plages (206/416) ;
  `GET /api/v1/admin/quiz/coverage` et la page `/admin/quiz` (« Parties sans répétition ») donnent la couverture.

## 6 quinquies. Lots : les banques rangées par thème (mode hors ligne différé)

**Règles du propriétaire.** La TV n'embarque qu'environ **10 Mo au maximum** de données Apprendre + Quiz. Le téléphone gère ces données (plus de place,
Internet plus fiable, synchronisation avec notre serveur) et en charge jusqu'à **100 Mo** après la première synchronisation. Les mises à jour se font par
**lots** homogènes (tout le contenu d'une classe ou d'un niveau). **C'est le mode hors ligne différé, et il est prioritaire** : la plupart du temps la TV n'a
**pas d'Internet**. Conséquence pour le Quiz : **tous les modes de jeu (solo, famille, duel, salle par téléphone / Wi-Fi local / Bluetooth) marchent
entièrement avec les lots que la TV détient, sans aucun appel Internet et sans roue d'attente réseau** ; le téléphone télécharge dès qu'il a Internet, le
cadre commun des lots (`LotStore`, `LotSync`, `LotPlanner`, `TvLotStore`, `LotPush`, `DeliveryQueue`, écrit par une autre branche) livre ensuite à la TV.
Le Quiz ne fournit que son adaptateur, l'organisation de ses banques, ses écrans et ses tests (contrat : `core/.../lots/LotApi.kt`).

### Table des lots (build `v1`)

Un lot = un thème = **un seul pack signé** `quiz-<thème>-p1-v<version>.quiz.zip` (le format des packs de § 6 quater : `manifest.json` + `questions.json`, plus
`index.json`). Le parcours « Culture générale » est coupé par région pour livrer d'abord le Cameroun (70 % du tirage). Les noms de thèmes suivent une règle
(`QuizLotScopes.scopeFor`) : niveau scolaire en minuscules (`3e`, `class-1`, `lower-sixth`), supérieur « `<filière>-<niveau>` » (`eco-l1`, `maths-l1`).

| Thème (`scope`) | Titre | Parcours (région) | Questions | Octets | Version |
|---|---|---|---:|---:|---|
| `culture-cm` | Culture générale · Cameroun | general (CM) | 1639 | 111101 | v1 |
| `culture-afrique` | Culture générale · Afrique | general (AF) | 591 | 36967 | v1 |
| `culture-monde` | Culture générale · Monde | general (WORLD) | 975 | 60014 | v1 |
| `cm2` | Primaire · CM2 | cm2 | 6523 | 432226 | v1 |
| `3e` | Secondaire · 3e | 3e | 5361 | 356782 | v1 |
| `tle` | Secondaire · Terminale | tle | 5701 | 365406 | v1 |
| `droit-l1` | Supérieur · L1 Droit | l1-droit | 106 | 12685 | v1 |
| `eco-l1` | Supérieur · L1 Économie | l1-eco | 4889 | 328665 | v1 |
| `maths-l1` | Supérieur · L1 Mathématiques | l1-maths | 5236 | 313365 | v1 |
| **Total** | | | **31021** | **2017211** (2.02 Mo) | |

Thèmes prévus, même règle, pas encore de questions : `6e`, `5e`, `4e`, `2nde`, `1re`, `bepc`, `probatoire`, `bac`, `gce-ol`, `gce-al`, `culture-*` anglophone,
`phys-l1`, etc. Ajouter un thème = ajouter ses questions avec le bon `track` / `level` / `field` dans le pipeline et l'inscrire dans `qb/lots.py` (`SCOPES`) et
`QuizLotScopes.specs` (un test vérifie que les deux tables sont d'accord). Aucun lot ne dépasse 0,45 Mo : **les 9 lots tiennent ensemble à 2.02 Mo, sous les 10 Mo de la TV**
(la TV n'en reçoit qu'une partie, dans l'ordre de priorité ci-dessous).

### Index et versions
`index.json` : `{"v":1,"scope","version","count","contentHash","q":{<id de question>:<empreinte de 8 caractères>}}`. L'empreinte d'une question = SHA-256 de ses champs
(id, parcours, niveau, filière, région, catégorie, difficulté, texte, choix, bonne réponse, explication, source, statut, vérification, langue ; `qb/lots.py` et
`QuizLotIndex.questionHash` calculent la même chose, vérifié par un test sur les vrais lots). **La version d'un lot ne change que si son contenu change**
(`content/quiz/lots/lots-state.json` garde la dernière empreinte du contenu ; un `build` identique donne les mêmes octets). Une question modifiée **garde son `id`** : sa place
dans l'historique « 300 parties » est conservée, et `QuizLotIndex.diff` dit ce qui a été ajouté / modifié / retiré.

### Fabriquer et publier
```sh
python3 tools/quiz-bank/quizbank.py lots      # (Python ≥ 3.12) écrit content/quiz/lots : les lots, catalog-lots.json, lots-state.json ; code 2 si un lot dépasse 3 Mo
```
Il affiche la taille de chaque lot et le total. `catalog-lots.json` = une entrée par lot au format `LotMeta` (`feature = "quiz"`, `scope`, `version`, `bytes`, `sha256`,
`title`, `minAppVersion`) plus `file`, `questions`, `contentHash` et les champs de pack (`id` = thème, `course`, `part = 1`, `parts = 1`) : prêt pour le point de
publication des lots du serveur. **Signature** : le serveur signe (Ed25519, même clé que les mises à jour) la charge `QuizPackInfo.canonicalPayload()` de l'entrée
(`id` = thème), exactement comme pour les packs ; le fichier du lot ne contient pas sa signature.

### Côté appareil : `QuizLotConsumer` (`core/.../quiz/QuizLots.kt`)
Implémente `LotConsumer` (`feature = "quiz"`). `install` refuse (rien n'est modifié) : mauvais type ou nom de thème, app trop ancienne (`minAppVersion`), taille ou SHA-256
différents, **signature absente ou invalide**, structure du pack, index ou questions incohérents, question hors du thème, version plus ancienne que celle installée, même
version avec un autre contenu, budget (`maxBytes`) dépassé : jamais de lot retiré dans le dos de l'utilisateur. **Installation atomique** : fichier mis en place par
renommages, l'ancienne version est gardée (`prev`) et **revient seule** si une étape échoue ; au redémarrage, `recover()` termine ou annule une installation
interrompue (coupure de courant). `rollback(id)` revient à la version précédente. `bank()` = toutes les questions des lots installés (sans doublon d'`id`) ;
`PackedQuestionSource(..., lots = consumer)` les ajoute à la banque de jeu (un lot remplace une question de même `id`), sans redémarrage. Les packs d'avant (relais
par le téléphone, § 6 quater) **continuent de fonctionner** tels quels jusqu'à ce que le cadre commun livre les lots (couche de compatibilité du côté de l'autre branche).

### Priorité de remplissage : `QuizLotScopes.quizPriority(track, level, field, played)`
Fonction pure pour le `LotPlanner` : 1) les lots du parcours du profil, 2) la culture générale (Cameroun, Afrique, Monde), 3) les parcours déjà joués ici (`played`, plus
récents d'abord), 4) les niveaux voisins du même parcours (les plus proches d'abord), 5) tout le reste. Sans profil : culture générale puis le reste. `QuizHub.lotPriority`
(CastBridge-TV) la nourrit avec l'historique de la TV.

### Budget de la TV
Le Quiz embarque dans l'APK un **jeu de départ** minuscule : les banques d'origine (`questions.json` + `questions-school.json`, **≈ 138 Ko**, ~320 questions : culture générale
et CM2 / 3e / Tle / L1 droit / économie / mathématiques), contre un maximum de **2 Mo**. Avec le jeu de départ d'Apprendre (au plus 3 Mo, autre branche), le départ combiné reste
**sous 5 Mo** et il reste **au moins 5 Mo** pour les lots poussés. Un test (`QuizLotsTest.theStarterIsWithinBudgetAndAFullGameWorksOnItAlone`) vérifie la taille et joue **des parties
complètes (Millionnaire sur les 7 parcours, Duel avec un téléphone) sur le seul jeu de départ, sans aucun accès réseau** (un `ProxySelector` espion le prouve).

### « Mes thèmes » (onglet Quiz de CastBridge, téléphone)
Une carte par thème : version, taille, **fraîcheur** (« mis à jour il y a 3 jours »), mise à jour disponible, **présence sur la TV** (« Sur la TV », « mise à jour à envoyer »,
« sera envoyé quand la TV est à portée »), et pour un thème non téléchargé la taille à télécharger avec un bouton (« Télécharger », fourni par le cadre commun via
`QuizLotHooks.download`) et le lien vers l'écran « Données » (`QuizLotHooks.openData`). Tout se lit en local (`QuizThemes.build` + dernier catalogue gardé par
`QuizThemes.CatalogCache`) : l'écran est complet sans Internet. La TV signale ses lots dans `GET /api/quiz/packs/status` (clé `lots`).

### Fusion des données quand les appareils se rencontrent (`QuizMerge`)
Historique, scores et jetons restent **locaux à chaque appareil** et se fusionnent à la rencontre (TV ↔ téléphone) avec des règles commutatives et idempotentes (testées) :
- **meilleurs scores** : union des deux tableaux (une entrée identique compte une fois), puis le meilleur (`HighScores` garde 10 par tableau, un tableau = une façon de jouer et un parcours) : **meilleur score par profil et par jeu** ;
- **historique des questions** (300 parties) : par parcours, une question compte comme posée au plus récent de ses deux moments (mesurés en « parties écoulées » sur chaque appareil), compteur de parties = le plus grand ; dans le doute elle reste « récente » ;
- **jetons virtuels** : dernière écriture par joueur (horodatée) ; égalité = le plus grand solde (déterministe).

### Politique de jeu : un seul endroit, `QuizPlay.isPlayable(question, channel)`
Le propriétaire validera la qualité **3 mois après la distribution aux bêta-testeurs** : les questions marquées `review` doivent être **jouables dans le canal bêta**, avec la
mention visible « bêta : non validé » et une action « Signaler une erreur », et rester **bloquées dans un futur canal stable** tant qu'elles ne sont pas validées. L'outil de
validation (autre agent) ne change que `QuizPlay.isPlayable` ; le comportement actuel est le défaut, inchangé : jouable si pas `review`, **ou** si la réponse est calculée et testée
(`Question.computedOk`, posé pour les packs et lots seulement : ancien `computedPlayable`). `Question.review` garde l'indicateur brut. **Lacune connue** : les questions factuelles
(`verif: fact`) sont `review` : le thème `droit-l1` (106 questions) n'a **aucune** question jouable et la culture générale n'en a que 1 260 sur 3 205 (Cameroun 490 / 1 639, Afrique 200 / 591, Monde 570 / 975) : tant
qu'un humain ne les a pas approuvées ou que la politique bêta n'est pas en place, ces thèmes ne s'ajoutent presque pas au jeu de départ (qui, lui, est entièrement jouable).

## 7. Format d'échange avec le futur serveur de questions

La TV ne dépend d'aucun serveur : la banque embarquée suffit et reste le **repli hors ligne**. L'abstraction
`QuestionSource` (`core/.../quiz/QuestionSource.kt`) prévoit la suite :

- `EmbeddedQuestionSource` : fichiers de l'APK (actuel) ;
- `CachedQuestionSource` : banque embarquée **+** un seul fichier cache (`files/quiz/questions-cache.json`, **2 Mo max**)
  rempli par le futur client serveur via `update(json)` ; le cache est validé (taille, version, chaque question), écrit
  atomiquement ; une question du serveur remplace celle de même `id` ; cache absent, trop gros ou corrompu = banque
  embarquée seule ;
- `RemoteQuestionApi` : interface du client à écrire (`fetch(since, page)`).

Document échangé (version **2**, un lecteur accepte sa version et les précédentes) :

```json
{"version":2,"generatedAt":"2026-09-30T10:00:00Z","page":1,"pages":3,
 "questions":[{"id":"6f1c…-uuid","lang":"fr","track":"secondary","level":"3e","field":null,"region":"CM",
   "category":"Histoire","difficulty":3,"question":"…?","choices":["…","…","…","…"],"answer":2,
   "explanation":"…","source":"…","status":"approved","updatedAt":"2026-09-30T09:12:00Z"}]}
```

- `status` : `approved` (jouable) ; toute autre valeur (`draft`, `review`, `rejected`) = exclue, comme `review:true`.
- `updatedAt` (ISO 8601) permet la synchronisation incrémentale ; `lang` prépare d'autres langues (anglais pour les
  parcours anglophones).
- API suggérée : `GET /v1/questions?since=<ISO>&track=&level=&field=&page=` (pagination), éventuellement
  `POST /v1/draws` pour un tirage côté serveur ; la TV garde le tirage local (70/20/10, difficulté croissante, pas de
  répétition dans la session) pour fonctionner hors ligne.

## 8. Technique et ressources

- `:core` (JVM, testé) : `QuizBank` (tirage), `QuizGame` (machine à états Millionnaire, sérialisable en JSON),
  `QuizDuel`, `QuizRoom` (salle), `QuizHttp` (routes), `QrCode` (encodeur QR en Kotlin pur, ~250 lignes, vérifié contre
  la bibliothèque Python `qrcode` ; pas de ZXing), `Wallet`, `HighScores`, `Json` (mini-lecteur, pas de dépendance).
- `:receiver` : `QuizActivity` (vues Android classiques, Canvas et ValueAnimator, aucun moteur de jeu, aucune image),
  `QuizViews`, `QuizSound` (sons **synthétisés** au premier lancement en petits WAV dans le cache, ~200 ko, joués par
  SoundPool ; aucun fichier audio dans l'APK), `QuizHub`.
- `:sender` : `QuizScreen` (Compose) : découverte, ouverture, code automatique, puis la page web `/quiz` dans une WebView.
- APK TV armeabi-v7a : debug 42 773 056 → 43 131 070 octets (+350 ko), release non signé 40 757 700 → 40 864 074
  (+104 ko). Mémoire mesurée sur émulateur TV (arm64, debug) pendant une partie : PSS ≈ 62 Mo.

## 9. Limites du POC et reste à valider sur la vraie TV

Testé : tests JVM (tirage, banque, règles, jokers, duel, salle, HTTP de bout en bout, QR, jetons, scores), parcours complet
sur **émulateur Android TV 1080p** (et 720p) à la télécommande : réglages, salle d'attente, Millionnaire solo, Duel avec 3
joueurs simulés, fin de partie, record ; le QR des captures d'écran est décodé par OpenCV.

À valider sur la TV GaiaOS / Amlogic 32 bits : rendu et fluidité (fond animé, animations), focus D-pad avec la vraie
télécommande, **lisibilité du QR à 2-3 m** avec plusieurs téléphones, latence du multijoueur sur le Wi-Fi réel (SSE à
travers le routeur), sons (SoundPool / sortie HDMI), mémoire réelle (~300 Mo disponibles), et la page web sur des
téléphones variés (iPhone/Safari, Android ancien). L'app téléphone n'a pas été essayée sur un vrai téléphone.

Pistes : questions en anglais (parcours anglophones), plus de niveaux et de filières, serveur de questions, relecture
humaine des questions, sons plus riches, écran « podium » avec confettis, mode équipes, historique des scores par joueur.
