# Validation du contenu : bêta d'abord, validation 3 mois plus tard

> Décision du propriétaire (2026-10-01). Le contenu d'« Apprendre » et du Quiz est volumineux, écrit surtout par des agents IA et
> marqué `review`. Il est **distribué aux bêta-testeurs tel quel, avec une mention visible**, et la **validation de qualité se fait
> 3 mois après**, à partir des retours et de l'usage des testeurs. Ce document décrit l'outillage qui rend cela possible et sûr.
> Code : `android/core/.../content/`, `backend/.../content/`, `tools/content-validation/`.

## 1. Cycle de vie d'un élément

Tout élément (question du quiz, leçon, exercice) a un **état** :

| État | Sens | Passe à |
|---|---|---|
| `review` | écrit, personne ne l'a validé (état de départ) | `validated`, `rejected`, `needs-fix` |
| `validated` | relu et validé par un relecteur, sur **cette version** du texte | `review` (le texte a changé), `needs-fix`, `rejected` |
| `needs-fix` | un défaut a été constaté, à corriger | `review` (corrigé, à relire), `rejected` |
| `rejected` | à ne jamais jouer | `review` (réécrit), `needs-fix` |

Toute autre transition est refusée (l'outil, le serveur et `ContentState.canMoveTo` appliquent la même table). `rejected` et
`needs-fix` exigent une **note** ; `validated` exige l'**empreinte** du texte vu. Un changement du texte d'un élément validé ou
rejeté le **remet en `review`** automatiquement (la décision est « périmée »).

### Politique de jeu par canal (`PlayPolicy`, un seul endroit)

Le **canal** (`stable` ou `beta`) vient du serveur, par appareil : `ServerLink.state.channel` (même mécanisme que les mises à jour,
`UpdatePolicy`, changement dans `/admin/devices`).

| État \\ canal | `stable` | `beta` |
|---|---|---|
| `validated` | jouable | jouable |
| `review` | **non jouable** (voir ci-dessous) | **jouable, marqué « bêta : non validé »** |
| `needs-fix` | non jouable | non jouable |
| `rejected` | jamais | jamais |

- **Comportement actuel conservé par défaut** : `QuizBank` est en canal `stable` tant que l'app ne dit pas autre chose
  (`QuizBank.forChannel`), donc les questions `review` restent bloquées comme avant. Les questions dont la réponse est **calculée et
  vérifiée** par le pipeline (`verif: computed`, chargées avec `computedPlayable`) restent jouables en `stable` (elles arrivent sans
  drapeau `review`) ; en bêta elles portent aussi la mention tant qu'un humain ne les a pas validées.
- **Apprendre** : une leçon est « validée » si sa source le dit (`state: validated`, sinon `status: validated`) ; un exercice est sous
  relecture s'il porte `review: true`. Aujourd'hui l'app **affiche encore** le contenu sous relecture en `stable` (avec son « ⚑ à
  vérifier » existant) : `PlayPolicy.isVisible(..., legacyStable = true)`. Quand le canal `stable` sera réellement ouvert (§ 6), passer
  `legacyStable` à `false` : seul le contenu `validated` sera alors visible. `rejected` et `needs-fix` sont déjà cachés partout.
- La mention (« bêta : non validé ») s'affiche : sur la question du quiz (TV et page de jeu du téléphone), sur les fiches et exercices
  d'Apprendre (TV et téléphone).

## 2. Identifiants stables et empreintes

- **Identifiant** : celui de la source (`cm-geo-001`, `s3-000ac48f`, `cep-maths-nombres`, `cep-maths-n-12`…), motif
  `[A-Za-z0-9][A-Za-z0-9_.:+-]{0,63}`. Il **ne change jamais** : un retour ou une décision l'utilise pour survivre aux mises à jour
  des lots. Corriger un texte garde l'id ; une question réécrite sur un autre sujet prend un nouvel id.
- **Empreinte** (`ContentHash`, 16 hex) : SHA-256 de l'énoncé, des choix (**triés** : le mélange ne la change pas), de la bonne réponse
  et de l'explication (exercices : type, énoncé, formule, choix, réponse, explication ; leçons : JSON canonique sans `status`, `state`,
  `review`). Même recette en Kotlin et en Python (`tools/content-validation/cbvalidate.py`), mêmes vecteurs de test des deux côtés.
- Un signalement ou une décision porte l'empreinte vue ; si le texte a changé depuis, le retour est affiché « ancienne version » et la
  décision est périmée.
- **Lot** : quiz = `quiz/<parcours>` (ex. `quiz/secondary/3e`, `quiz/general`), Apprendre = `learn/<id du pack>`.

## 3. Fichier des décisions (jamais dans un lot signé)

`content/validation/*.jsonl`, un objet JSON par ligne, **historique en ajout seul** :

```json
{"id":"cm-geo-001","kind":"question","state":"validated","reviewer":"Mme Ngo","date":"2026-12-05","note":"relu avec l'atlas","hash":"4aba34d897c8b24d","lot":"quiz/general"}
```

`kind` : `question` | `lesson` | `exercise`. La **dernière** décision de chaque id fait foi, si la transition est permise et si
l'empreinte correspond. Les décisions peuvent aussi vivre sur le serveur (§ 5) ; `GET /api/v1/admin/content/records` les exporte dans ce
format pour les ranger dans le dépôt. Elles ne sont **jamais** mélangées au contenu signé des lots : seul le *résultat* (statut du
contenu) y entre, au moment de la reconstruction.

### Outil : `tools/content-validation/cbvalidate.py` (Python standard, aucun paquet)

| Commande | Effet |
|---|---|
| `check` | lit les décisions, vérifie format et transitions, signale les décisions **périmées** et les ids **inconnus** |
| `apply [--dry-run]` | écrit les décisions effectives dans les sources : quiz → `content/quiz/approvals.json` (`approved` / `rejected` / `needs-fix`, lu par `quizbank.py`) ; Apprendre → `status`, `review`, `state` de la leçon ou de l'exercice, **par modification ciblée du texte** (le reste du fichier JSON reste identique) |
| `rebuild` | `apply`, puis `quizbank.py build` et `tools/build-learn-packs` : les lots sont reconstruits |
| `index [--out F]` | une ligne JSON par élément (id, lot, classe, matière, empreinte, extrait) : à importer dans la file de relecture du serveur |
| `import-csv F` | ajoute aux décisions celles d'un CSV de relecteur (`id;state;reviewer;date;note`), avec l'empreinte courante |
| `budget [--limit-gb 3]` | taille totale des lots, par fonctionnalité et par lot ; **code de sortie 1 au-dessus de la limite**, avertissement au-dessus de 80 % |

Tests : `cd tools/content-validation && python3 -m unittest -q test_cbvalidate`.

## 4. Signalements des testeurs (« Signaler une erreur »)

- **Où** : sur chaque question du quiz une fois la réponse révélée (écran TV Millionnaire ; page de jeu du téléphone, Millionnaire
  et Duel), sur chaque exercice corrigé et chaque fiche d'Apprendre (TV et téléphone).
- **Motifs** : réponse fausse, question ambiguë, faute de langue, hors programme, trop facile ou trop difficile, autre ; texte libre
  facultatif de **200 caractères** au plus (saisi sur le téléphone ; la télécommande de la TV ne propose que le motif).
- **Hors ligne d'abord** : le signalement va dans une file locale (`ReportQueue`, fichier `content/content-reports.jsonl`) :
  **dédoublonnée** (même élément + même motif + même version du texte), **bornée** (100 signalements / 64 Ko, les plus anciens partent
  les premiers) et **limitée en débit** (20 par heure, 2 s entre deux). La TV remet sa file au téléphone (`GET /api/content/reports`
  puis `POST /api/content/reports/ack`, protégés par le PIN comme tout `/api`, tirés par le téléphone quand l'écran « TV » est
  ouvert) ; la TV les envoie aussi elle-même quand elle a Internet. Le téléphone les envoie au serveur (`POST /api/v1/content/reports`,
  jeton de l'appareil, 50 par envoi, 60 par heure et 200 par jour par appareil, déjà reçu = ignoré) au prochain contact.
- **Données** : l'identifiant de l'élément, l'empreinte, le lot et sa version, le motif, le texte facultatif nettoyé (caractères de
  contrôle retirés, espaces réduits) et le canal. Pas d'autre donnée que l'identifiant d'appareil déjà connu du serveur (jeton
  d'authentification, jamais écrit dans les journaux). Le texte est affiché échappé dans l'admin.
- **Catégorie de consentement : ESSENTIEL.** Un signalement est un geste volontaire de l'utilisateur, avec un court texte ; il est envoyé
  quel que soit le choix sur les statistiques d'usage. En contrepartie, l'écran d'information le dit (`ConsentText.ESSENTIAL`) et invite
  à ne mettre aucune donnée personnelle dans le texte. **Les chiffres d'usage par élément (§ 5) sont de l'USAGE** : sans accord, rien
  n'est envoyé ni conservé. Le texte de consentement a été modifié et sa **version passe à `2026-11`** : l'écran d'information est
  montré de nouveau à tous. **À faire relire par un juriste avec le reste du texte** (loi n° 2024/017).

## 5. Signaux de qualité par l'usage

Événement `content_stat` (catalogue d'événements, usage, **aucun texte, seulement des identifiants et des nombres**) : par élément et
par envoi, `kind` (`question`/`lesson`/`exercise`), `item` (id), `shown`, `correct`, `ms` (temps total), `reports`. L'appareil agrège
(`ItemStatsCollector`) puis envoie avec les statistiques ; le serveur additionne dans `content_stat`. Mesurés : **vu, réussite, temps
moyen, nombre d'appareils ayant signalé**.

**Score de suspicion** (`QualitySignals.suspicion` / `ContentRules.suspicion`, mêmes vecteurs de test), de 0 à 1 :
- réussite **attendue** par difficulté 1..5 : 90 / 78 / 65 / 50 / 35 % ;
- la réussite ne compte qu'à partir de **30 affichages**, et seulement si la réussite attendue est **hors** de l'intervalle de
  Wilson à 95 % de la réussite observée (un petit échantillon ne déclenche rien) ; trop bas pèse deux fois plus que trop haut ;
- **sous le hasard** (25 %) avec assez d'affichages : clé de réponse probablement fausse (score maximal pour la part « réussite ») ;
- chaque signalement (par appareil) compte, 5 saturent la part « signalements » ; **3 appareils** qui signalent suffisent à marquer ;
- `score = 0,6 × réussite + 0,4 × signalements`, **suspect à partir de 0,5**.

## 6. Serveur : file de relecture, avancement, budget

Migration `V30__content_validation.sql` (tables `content_item`, `content_report`, `content_stat`, `content_decision`).

**Pages d'administration** (derrière la connexion habituelle `/admin`, CSRF, CSP stricte) :
- `/admin/content` — **file de relecture** : filtres lot / classe / matière / état / type / texte, tri par **suspicion**, par
  **nombre de signalements** ou par lot ; chaque élément montre l'extrait, l'état, les signalements, les chiffres d'usage ;
  `/admin/content/item` : détail avec chaque signalement (motif, texte, canal, « ancienne version »), les chiffres et le formulaire
  de décision (état suivant + note). **Valider un lot entier** : valide tous les éléments `review` du lot (ou d'une classe) **sauf**
  ceux qui ont des signalements ou sont suspects (case pour les inclure). **CSV** : export de la sélection (UTF-8 avec BOM, `;`,
  cellules protégées contre l'injection de formules) pour les enseignants qui travaillent hors ligne, et **ré-import** des décisions
  (colonnes `id`, `etat`, `relecteur`, `date`, `note`, `empreinte` ; une ligne dont le texte a changé depuis l'export est refusée).
  **Index des contenus** : import du fichier produit par `cbvalidate.py index`.
- `/admin/content/progress` — **avancement** par lot et par classe (% validé, à relire, à corriger, rejetés, signalés) et **budget de
  contenu**.

**API** : `POST /api/v1/content/reports` (appareil) ; en jeton d'administration : `POST /api/v1/admin/content/index`,
`GET /api/v1/admin/content/records` (décisions au format du § 3), `GET /api/v1/admin/content/progress`,
`GET /api/v1/admin/content/budget[?strict=true]` (507 au-dessus de la limite, pour qu'un script échoue).

**Budget de contenu** : limite du propriétaire **3 Go** pour tous les lots publiés (`castbridge.content.budget-limit-bytes`). Le
serveur additionne les `*.zip` du dossier des packs de quiz, de `{storage}/learn-packs`, de `{storage}/lots` et des dossiers de
`castbridge.content.lots-dirs` ; détail par fonctionnalité et par lot ; **OK** jusqu'à 80 %, **ATTENTION** au-delà, **ÉCHEC** au-dessus
de la limite. Même contrôle en local : `cbvalidate.py budget`.

## 7. Les 3 mois de bêta : plan

1. **Semaine 0** : publier les lots (tous `review`), mettre les appareils des testeurs en canal **bêta** (`/admin/devices`), importer
   l'index (`cbvalidate.py index` → `/admin/content`), vérifier le budget. Les autres appareils restent en `stable` (contenu validé
   seulement).
2. **Chaque semaine** : regarder `/admin/content` trié par suspicion. Corriger ce qui est faux : l'élément passe `needs-fix` (il
   disparaît des parties bêta), l'auteur corrige la source (même id), `cbvalidate.py index` puis l'import le remettent en `review`
   (nouvelle empreinte), le relecteur le valide.
3. **À mesurer** (par lot et par classe) : nombre de testeurs actifs ; affichages par élément ; réussite par difficulté (la courbe
   doit être décroissante, 1 → 5) ; signalements par 1 000 affichages ; part des éléments signalés ; temps moyen anormalement court
   (réponse au hasard) ou long (énoncé confus).
4. **Seuils pour passer de `review` à `validated`** (proposition, à ajuster) — un élément est **candidat** quand : au moins **30
   affichages** ; **aucun** signalement `wrong_answer` ni `ambiguous` ; **moins de 3 signalements** au total ; score de suspicion
   **< 0,5** ; et **un humain l'a relu** (échantillon ci-dessous). Un élément **sous le hasard** ou avec **3 signalements ou plus** est
   relu en priorité, jamais validé en bloc.
5. **Relecture par lot** : un enseignant par matière et par classe reçoit le CSV de son lot (`/admin/content` filtré → Exporter). Il
   relit **tous** les éléments suspects ou signalés, et un **échantillon de 10 % (au moins 30)** des autres ; si l'échantillon contient
   plus de 2 % d'erreurs, tout le lot est relu. Il renvoie le CSV (`etat` = `validé` / `rejeté` / `à corriger`, `relecteur`, `date`,
   `note`) : import dans `/admin/content`, ou `cbvalidate.py import-csv`. Quand un lot est prêt, **« Valider le lot »** valide le reste.
6. **Semaine 12 — passer au canal `stable`** : exporter les décisions (`/api/v1/admin/content/records` → `content/validation/`),
   `cbvalidate.py check`, `rebuild`, publier les lots reconstruits. Mettre `legacyStable = false` (§ 1) pour Apprendre. Les appareils
   `stable` ne jouent plus que du `validated` ; les lots non validés restent en bêta seulement. Le canal est choisi **par appareil**
   (`/admin/devices`) : on peut donc ouvrir `stable` lot par lot en décidant quels appareils y passent.
7. **Après** : la bêta continue sur les nouveaux lots ; les décisions restent l'historique de qualité.

## 8. Rôles

| Rôle | Fait |
|---|---|
| Propriétaire | fixe la limite de 3 Go, les seuils, décide quand ouvrir `stable` |
| Administrateur (compte `/admin`) | importe l'index, décide, valide en bloc, exporte, surveille budget et avancement |
| Relecteur (enseignant) | relit un lot (CSV ou page), renvoie ses décisions avec une note |
| Auteur (agent ou humain) | corrige les sources des éléments `needs-fix`, **sans changer l'id** |
| Testeur bêta | joue, signale ; accepte (ou non) les statistiques d'usage |

## 9. Ce qui n'a pas été vérifié

Les modules Android `:receiver` et `:sender` n'ont pas été compilés (pas de SDK Android dans l'environnement cloud) : boutons de
signalement, marque bêta, remise TV → téléphone, `play.html`. Le cœur (`:core`), le serveur et l'outil sont testés (voir `HANDOFF.md`).
