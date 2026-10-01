# Assistant « Ranger ma bibliothèque »

> Branche `feat/library-agent`. Demande d'Esaie : « un agent sur l'app smartphone intelligent qui lira la bibliothèque,
> renommera et organisera efficacement les données. Ce doit être une IA circonstancielle. »
> Code : `core/library/agent/` (logique pure, testée sur la JVM), `sender/agent/` (écrans Compose), routes de la corbeille
> dans `core/library/agent/TvTrash.kt` (côté TV), `backend/…/library/` (aide facultative du serveur).

## 1. Ce que l'assistant fait, en une phrase

Il **lit** les noms de la bibliothèque de la TV (mémoire + clé USB) ou d'un dossier du téléphone, **comprend** ce que sont
les fichiers, **propose** un rangement (noms lisibles, dossiers, doublons, place à libérer) et **n'agit que sur ce que
l'utilisateur a coché et confirmé**. Tout est annulable.

Où : bibliothèque de la TV (onglet CastBridge TV) → icône ✨ « Ranger ma bibliothèque (Assistant) » en haut à droite, ou la
ligne discrète « 12 fichiers mal nommés » → bouton « Ranger ».

## 2. Le flux

| Étape | Ce qui se passe | Réseau |
|---|---|---|
| Lire | `GET /api/library` + `GET /api/storage` de la TV (tous les volumes), ou parcours d'un dossier choisi avec le sélecteur Android (SAF : aucune permission de stockage) | la TV seulement |
| Comprendre | moteur de règles `NameParser` (§ 3), habitudes locales, espace libre, langue | aucun |
| Doublons | taille égale → lecture de 64 ko au début et de 64 ko à la fin **sur le réseau local** (`TvFingerprinter`, via `/stream/` avec `Range`) ; l'empreinte reste sur le téléphone | la TV seulement |
| (facultatif) IA | noms nettoyés des cas ambigus → serveur CastBridge (§ 5) | **seulement après consentement séparé** |
| Proposer | écran « Plan de rangement » : renommer / déplacer / doublons à mettre à la corbeille / libérer de l'espace, avant → après, cases à cocher | aucun |
| Valider | l'utilisateur coche (ou « Tout accepter » : jamais les suppressions), peut **modifier un nom**, ou « Ne plus toucher à ce fichier » | aucun |
| Agir | `Executor` : étape par étape, journal avant/après chaque étape | la TV (routes existantes) |
| Annuler | « Annuler tout ce rangement », ou « Annuler un rangement » (historique) | la TV |

## 3. Le moteur de règles (le cœur, 100 % local)

`NameParser.parse(nom, dossier, durée)` → type, titre, année, saison, épisode, langue, qualité. Types reconnus :

- **Séries** : `S01E04`, `s1e4`, `S01.E04`, `S01E01E02`, `1x04`, `Saison 2 Episode 5`, `Season 3 Episode 10`, `Ep 4`, `E12`, `Episode 1000`,
  `[Groupe] Titre - 1045` (anime), dossier parent « Prison Break/Saison 1 » + fichier « Episode 04 », titre d'épisode conservé
  (« Game of Thrones – S08E06 – The Iron Throne »), année de série (« The Flash (2014) »).
- **Films** : année valide (1900 → année en cours + 1), « 2012.2009 » = titre « 2012 », année 2009 ; sans année mais avec des
  étiquettes (BluRay, 1080p…) ; « Star Wars Episode 1 … 1999 » n'est pas une série.
- **Musique / clips** : « Artiste - Titre », numéro de piste, « ft. », « (Official Video) », « (Clip Officiel) », « [Lyrics] », `| …`.
- **Cours / Apprendre** : mots forts (cours, tuto, leçon, formation, Udemy, Khan Academy…) ou faibles (chapitre, TD, bac…)
  + matière (Mathématiques, Physique-Chimie, SVT, Informatique…).
- **Vidéos personnelles** : `WhatsApp Video 2024-03-15 at 14.22.11`, `VID-20240315-WA0012`, `PXL_…`, `VID_…`, `20240315_142211`,
  `Screenrecorder-…`, Telegram, « Mariage … 2022 », « Anniversaire … ».
- **Documents, applications, archives, sous-titres** (le sous-titre suit sa vidéo : `Titre (2010).fr.srt`).

**Nettoyage** : sites de téléchargement (`[www.torrent9.ph]`, `NetNaija.com`, `o2tvseries`, `YTS.MX`, `wawacity`…), qualités et codecs
(`720p`, `x264`, `WEB-DL`, `DD5.1`, `H.264`, groupes `-RARBG`), points / tirets bas / `%20`, accents (NFC), « Copie de », « (1) »,
casse (Title Case français ou anglais, seulement si le nom est tout en minuscules ou tout en majuscules ; `WALL-E`, « The Last of Us »,
« The Office US », `DJ`), caractères interdits sur FAT / exFAT / NTFS (`: * ? " < > |` → remplacés, noms réservés CON/PRN…, longueur ≤ 150).

**Noms produits** : « `Prison Break – S01E04` », « `Inception (2010)` », « `Artiste – Titre` », « `Vidéo WhatsApp – 2024-03-15 14h22` ».
Les noms produits sont **stables** (un second passage ne change rien : testé sur tout le corpus).
**Dossiers** (téléphone seulement) : `Séries/<Série>/Saison 01`, `Films/<Titre> (année)`, `Musique`, `Clips`, `Famille`, `Cours/<Matière>`,
`Documents`, `Applications`, `Archives`, `Captures`, `À trier` (labels anglais si le téléphone est en anglais).

### Taux de bonnes propositions sur le corpus de test

Le corpus (`NamingCorpus.kt`, `NamingCorpusB.kt`) contient 186 noms réalistes (séries, films, WhatsApp, noms africains / francophones /
anglophones, accents, sites). Chaque cas attend le nom **et** le dossier **et** le type.

| Jeu | Noms | Résultat |
|---|---|---|
| A (écrit puis utilisé pour régler le moteur) | 109 | 109 / 109, mais il a servi à régler les règles : **pas une mesure honnête** |
| B (écrit après A, exécuté **une seule fois avant tout réglage**) | 77 | **65 / 77 = 84,4 %** au premier passage |

Les 12 échecs du premier passage de B : « FINAL » pris pour un titre d'épisode (2), `Episode 1000` (4 chiffres), un préfixe `[ToonsHub]`,
« The Last of **US** » (suffixe de pays), `E.T.` (initiales à points), deux vidéos de famille classées en films (« Mariage … 2022 »),
`FB_VID_…` renommé à tort, `DJ`, un `.exe`. Tous corrigés ensuite par des règles générales (pas des cas particuliers), B est repassé à
77 / 77. **Sur une vraie bibliothèque il faut s'attendre à moins** : ce corpus est écrit par nous. Voir § 9.

## 4. Circonstanciel : ce qui change les propositions

| Contexte | Effet (tous testés) |
|---|---|
| Langue du nom | fr / en : Title Case différent (« de », « of »), mots « Saison / Season », étiquette de langue |
| Langue de l'interface | noms de dossiers (`Séries` / `Series`, `Saison 01` / `Season 01`) |
| Habitudes | la version qu'on regarde d'habitude (VF ou VOSTFR, d'après les fichiers déjà lus) n'est **pas** écrite dans le nom ; l'autre l'est (`[VOSTFR]`, `[VF]`) |
| Habitudes | doublons « même épisode en 720p et 1080p » : on garde la meilleure qualité **dans la même version linguistique** ; deux langues différentes ne sont jamais des doublons |
| Espace | mémoire interne < 2 Go libres : propose de déplacer les plus gros fichiers vers la clé USB, **en laissant ≥ 1 Go libre à la fin** (règle de la TV), jamais > 4 Go vers du FAT32, jamais sans clé |
| Espace | volume plein à ≥ 90 % ou < 2 Go : propose de mettre à la corbeille ce qui est **déjà vu depuis plus de 90 jours** (jamais ce qui n'est pas vu) ; sinon, on n'y touche pas |
| Moment d'usage | si on propose un déplacement long à une heure où la TV sert d'habitude (d'après les heures de lecture), un avertissement le dit |
| Profil enfant / contrôle parental | contenu protégé : jamais renommé, déplacé, mis à la corbeille ni envoyé ; profil enfant actif : conseils seulement. Branchement : `AgentStore.guard` (interface `ContentGuard`, voir § 8) |
| TV ou téléphone | la bibliothèque de la TV est plate (pas de dossiers) ; le téléphone a de vrais dossiers |
| Corrections de l'utilisateur | modifier un nom enseigne le titre aux fichiers suivants (« prison break » → « PB ») ; « Ne plus toucher » ; stocké sur le téléphone, **effaçable** (Réglages) |

## 5. « IA » : ce qui est réel et ce qui ne l'est pas

- **Réel et actif** : un moteur de règles et d'heuristiques, déterministe, avec du contexte. Ce n'est **pas** un modèle d'apprentissage :
  l'« apprentissage » est une mémoire de corrections (alias de titres, dossiers préférés, fichiers ignorés), pas un entraînement.
- **Interface `NamingModel`** : (a) `LocalRulesModel` = les règles derrière la même interface (aucun réseau, aucune dépendance : pas de
  TFLite / ML Kit, l'APK ne grossit pas) ; (b) `ServerNamingModel` = `POST /api/v1/library/suggest` du serveur CastBridge.
- **Couche IA du serveur** : désactivée par défaut. Pour les noms que les règles ne comprennent pas, des noms **nettoyés** partent au
  serveur, qui appelle un LLM **configuré par variable d'environnement** (`CASTBRIDGE_LIBRARY_LLM_API_KEY`, `…_LLM_MODEL`, `…_LLM_URL`).
  **Sans clé (état actuel du dépôt et du serveur) le serveur répond `available:false` et n'appelle personne.** Aucune clé dans le dépôt
  (un test le vérifie). Le client LLM a été testé contre un faux service local, **jamais contre un vrai LLM**.
- Une réponse de l'IA est une **proposition de confiance modeste** (≤ 0,6), jamais cochée d'avance, étiquetée « Proposé par l'IA : à vérifier »,
  et validée localement (type autorisé, titre sans `/ : * ? " < > |`, année / saison / épisode bornés).

### Ce qui quitte le téléphone (uniquement si l'utilisateur a accepté l'aide de l'IA, Réglages de l'assistant)

Envoyé : pour les cas ambigus seulement (vidéo / audio non reconnus), au plus 100 par analyse, 40 par requête :
le **nom nettoyé** (« prison break s01e04 », sans lien, adresse e-mail ni suite de 6 chiffres ou plus), l'**extension**, un **type supposé**,
la **durée arrondie à 5 minutes**, la **langue** de l'application, et la mention de consentement `library-ai-v1`.
Jamais : contenu des fichiers, dossiers, chemins, tailles, dates, vidéos personnelles (WhatsApp, appareil photo), photos, documents,
contenu protégé, profil enfant, identifiant de l'appareil dans le corps (l'appareil est reconnu par son **jeton** en en-tête, comme les autres
routes, pour refuser les appareils bloqués et limiter le débit). L'écran « Voir ce qui a été envoyé » montre la liste exacte.
Le consentement est **séparé** de celui des statistiques, **versionné** (`AiConsent.VERSION`) et révocable à tout moment.
Côté serveur : limite 30 requêtes par appareil et par heure (+ limite par IP existante), 40 noms par requête, rien n'est stocké ni journalisé
(le journal d'accès ne garde que le chemin).

## 6. Agir en sécurité (garanties vérifiées par les tests)

1. **L'agent propose, l'utilisateur valide.** « Tout accepter » ne coche **jamais** les suppressions ; les mises à la corbeille se cochent une à une
   et demandent une confirmation qui dit ce qui va se passer.
2. **Jamais de suppression définitive par l'agent.** « Supprimer » = **Corbeille CastBridge** (récupérable 30 jours). Seuls « Supprimer
   définitivement » / « Vider la corbeille » (boutons séparés, avec confirmation) effacent pour de bon.
3. **Jamais le dernier exemplaire** : une mise à la corbeille de doublon vérifie que l'exemplaire à garder existe toujours et n'est pas lui-même
   mis à la corbeille dans la même opération.
4. **Jamais d'écrasement** : un nom pris (n'importe quel volume de la TV, un seul espace de noms) reçoit un suffixe (`[VOSTFR]`, `[1080p]`, puis `(2)`).
   À l'annulation, un nom d'origine entre-temps pris n'est pas écrasé : l'étape est signalée comme impossible.
5. **Chemins** : `..`, `/`, `\`, chemin absolu, nom commençant par un point, caractères interdits, noms réservés, noms trop longs : refusés avant toute opération.
6. **Pas pendant la lecture** (état d'analyse **et** vérification au moment d'agir), pas de fichier modifié depuis l'analyse (taille), pas de fichier protégé.
7. **Espace** : déplacement entre volumes seulement si ≥ 1 Go reste libre à destination et que le fichier tient (FAT32).
8. **Journal** (`FileJournal`, lignes JSON ajoutées et synchronisées) : l'intention est écrite **avant** (`PENDING`), le résultat **après** (`DONE` / `FAILED`).
   Après une coupure, `recover()` regarde l'état réel des fichiers et dit la vérité (fait / rien changé / état incertain) ; une reprise ne refait pas ce qui est fait.
9. **Annuler** : dernier rangement ou n'importe lequel de l'historique, du plus récent au plus ancien.

### Routes ajoutées côté TV (`TrashApi`, derrière le PIN comme toute `/api/`)

La TV avait déjà `POST /api/rename?name=&to=` et `POST /api/storage/move?name=&to=` (+ `GET /api/storage` pour suivre le déplacement) : réutilisées telles quelles.
Ajoutées (dossier caché `.castbridge-trash` du volume du fichier, invisible dans la bibliothèque) :

| Route | Effet |
|---|---|
| `GET /api/trash` | contenu de la corbeille (id, nom, volume, taille, date, expiration) ; purge au passage ce qui a plus de 30 jours |
| `POST /api/trash/put?name=[&volume=]` | déplace un fichier terminé dans la corbeille (renommage atomique) ; 409 en lecture ; 404 si absent |
| `POST /api/trash/restore?id=` | remet le fichier ; autre nom (`… (restauré).mkv`) si le sien est pris |
| `POST /api/trash/purge?id=` / `POST /api/trash/empty` | suppression **définitive**, appelées seulement après confirmation de l'utilisateur |

Une TV plus ancienne (sans ces routes) fait **refuser** la mise à la corbeille (« mettez à jour CastBridge TV ») : l'assistant ne retombe **jamais** sur `/api/delete`.
Il n'y a pas de route « créer un dossier » : la bibliothèque de la TV est plate (voir § 8).

## 7. UX (téléphone)

Compose, composants Material 3 qui reprennent le thème en vigueur (la charte `branding/design-tokens.json` s'applique avec `feat/charte` sans toucher à ces écrans) ;
zones tactiles ≥ 48 dp, descriptions pour TalkBack sur les cases et icônes, français simple. Écrans : accueil de l'assistant (source TV / dossier du téléphone),
analyse avec barre de progression et bouton Annuler, plan de rangement, confirmation de mise à la corbeille, rangement en cours (arrêt possible),
résultat avec « Annuler tout ce rangement », Corbeille (restaurer / supprimer définitivement), Historique, Réglages (rangement automatique, aide de l'IA, effacer ce qui a été appris).
Suggestions proactives : **une ligne** dans la bibliothèque (« 12 fichiers mal nommés », « 3 doublons = 4,2 Go », « La clé « Clé USB » est pleine à 92 % »),
jamais une notification ni une fenêtre, masquable 7 jours, calculée localement.
**Rangement automatique des nouveaux envois** : désactivé par défaut ; activé, un fichier envoyé par Wi-Fi est renommé **avant** l'envoi seulement si la règle est sûre
(séries avec saison et épisode, films avec année, vidéos WhatsApp / appareil photo), jamais de dossier ni de suppression, inscrit au journal (annulable : la TV renomme le fichier comme avant).

## 8. Limites connues (à lire)

- **La bibliothèque de la TV est plate** : le plan de la TV ne contient que des **noms** (et des déplacements entre volumes, et la corbeille) ; les dossiers
  `Séries/…` n'existent que pour un dossier du téléphone. Ajouter de vrais sous-dossiers à la TV toucherait au stockage, à l'index, au lecteur : non fait.
- **Dossier du téléphone** : sélecteur Android (SAF). Sur Android 11+, le système refuse de choisir le dossier Téléchargements lui-même : choisir un sous-dossier.
  Déplacer d'un dossier à l'autre dépend du fournisseur de fichiers (`DocumentsContract.moveDocument`) ; si le système refuse, l'étape est signalée et rien n'est supprimé.
  La corbeille du téléphone est un dossier visible « Corbeille CastBridge » dans le dossier choisi, **jamais vidé automatiquement** (la TV, elle, purge à 30 jours).
  Exécuté sur émulateur et sur un fournisseur simulé (§ 12) ; **pas encore sur un vrai téléphone** (§ 9).
- **Un seul volume** pour le téléphone (pas de déplacement entre volumes là-bas).
- **Contrôle parental** : la branche `feat/parental` n'est pas fusionnée ; l'interface `ContentGuard` est prête (`AgentStore.guard`, par défaut « rien de protégé »).
  Quand elle sera fusionnée, il faut y brancher son test « ce fichier est marqué » et « profil enfant actif ».
- **Ambiguïtés assumées** : « Mariage/Anniversaire/Voyage… + année » = famille (un film de ce titre serait mal classé) ; une vidéo « Artiste - Titre » sans durée = clip (confiance basse, non cochée) ;
  `Mr. Bean` perd son point ; `The Final Cut 2004` en majuscules peut perdre « FINAL » ; épisodes d'anime sans saison (« E1045 ») ; les titres tout en minuscules sans accents restent sans accents.
- **Durée des vidéos du téléphone** : lue dans l'en-tête (300 fichiers de ≥ 5 Mo au plus) ; sinon inconnue.
- **Empreintes** : jusqu'à 60 fichiers de même taille par analyse ; un nom présent sur deux volumes n'est pas empreint (le flux de la TV ne distingue pas) : jugé par taille + durée + nom.
- Les barres de progression des déplacements entre volumes suivent l'état de la TV (un seul déplacement à la fois, géré par la TV).

## 9. Ce qui reste à valider avec la vraie bibliothèque d'Esaie

1. Passer l'assistant sur la bibliothèque réelle (séries **Prison Break**, etc.) **en regardant le plan sans rien cocher** : relever les noms mal compris, en faire des cas de `NamingCorpus`.
2. Essayer sur la TV de référence (GaiaOS, clé exFAT) : renommage, déplacement interne → clé (lent : 2 à 15 Mo/s), mise à la corbeille, restauration, annulation, coupure du Wi-Fi en cours de route.
   Il faut d'abord **installer la TV 0.12+** (routes `/api/trash/*`) ; sinon l'assistant refuse la corbeille.
3. Le dossier du téléphone (SAF) sur le Samsung S21+ : déjà fait sur émulateur (§ 12) ; reste le vrai téléphone (fournisseur Samsung, vraie carte SD, fichiers de plusieurs Go).
4. Décider si l'aide de l'IA doit être activée sur le serveur (clé, modèle, coût) : le texte de consentement (`AiConsent`) est à **faire relire** (juriste), comme celui de `ConsentText`.
5. Brancher `ContentGuard` au contrôle parental une fois `feat/parental` fusionnée.

## 10. Tests

`gradle :core:test` (JVM) — package `castbridge.core.library.agent` : corpus (3 tests dont stabilité des noms), planificateur (24), exécuteur / sécurité / annulation / reprise (27), couche IA (13),
TV réelle (serveur de test, volumes, corbeille, PIN, empreintes : 12). `./mvnw -q test` dans `backend/` : `LibrarySuggestApiTest` (9 tests : authentification, consentement,
validation, débit, appareil bloqué, aucun secret dans la configuration, client LLM contre un faux service).
Démo pour captures d'émulateur (non livrée dans l'APK) : `DemoTv` (faux serveur de TV, tests du module `:core`) et, dans les variantes **debug** seulement,
`AssistantDemoActivity` (`adb shell am start -n castbridge.sender/.agent.AssistantDemoActivity --es base http://10.0.2.2:<port> --es pin <pin>`).

## 11. Captures (émulateur Android 35, faux serveur de TV `DemoTv`, bibliothèque de démonstration)

Émulateur avec le téléphone en anglais : les « raisons » sous chaque proposition sont alors en anglais ; sur un téléphone en français elles sont en français.
Le rangement des captures a été **réellement appliqué** à la TV de démonstration (renommages, doublon mis dans la corbeille de sa clé), puis **annulé** (noms et fichier restaurés).

| | |
|---|---|
| `docs/library-agent/01-accueil.png` | accueil : source (TV / dossier du téléphone), « tout reste sur votre téléphone », Analyser, Corbeille, Annuler un rangement |
| `docs/library-agent/02-plan.png` | plan : résumé, conseils, « Tout accepter » (jamais les suppressions), avant → après avec cases |
| `docs/library-agent/03-plan-doublons.png` | déplacement vers la clé (≥ 1 Go libre à la fin) et doublons à mettre dans la corbeille, non cochés |
| `docs/library-agent/04-confirmation-corbeille.png` | confirmation explicite avant toute mise à la corbeille |
| `docs/library-agent/05-resultat-annulation.png` | résultat et « Annuler tout ce rangement » |
| `docs/library-agent/06-reglages.png` | réglages : rangement automatique et IA désactivés par défaut, effacer ce qui a été appris |

## 12. Assistant du téléphone : rapidité, dossier choisi, parcours guidé (branche `feat/agent-b-phone`)

### 12.1 Parcours en 3 étapes
**Analyser** (choix de la source, lecture) → **Vérifier** (le plan, rien n'est modifié) → **Appliquer** (récapitulatif exact, puis exécution). Un fil d'étapes en haut,
« Retour » = un pas en arrière. Étape 3 : un écran de récapitulatif dit exactement ce qui va se passer ; les mises à la corbeille se confirment **là**, avec un interrupteur
(plus de fenêtre surprise). Écrans : `AssistantScreen.kt` (parcours, accueil, récapitulatif, réglages), `PlanScreen.kt` (vérifier), `AssistantModel.kt` (état).

**Vérifier** : filtres (Tout / Séries / Films / Doublons / Gros fichiers ≥ 1 Go, avec le nombre), **groupes repliables** (une série = un groupe, « Films », « Doublons »…) avec case
tri-état (« tout cocher » du groupe : jamais une suppression), lignes **Avant → Après** étiquetées, indice de confiance en mots **et** icône (Sûr / Assez sûr / Peu sûr : pas seulement une couleur),
« **Pourquoi cette proposition ?** » par ligne (ce qui a été retiré du nom, pourquoi ce dossier, source des règles, confiance, ce qu'on peut faire), pagination par groupe (30 lignes puis « Afficher … de plus »).
Logique pure dans `core` (`PlanView.kt` : filtres, groupes, `Explain`) donc testée sans écran.

**Correction manuelle → règle apprise** : « Modifier le nom » valide le nom, l'enregistre comme règle (`LearnedRules`), affiche « Retenu : … » et **re-propose tout de suite** les autres fichiers du même titre avec ce nom.
Effaçable (Réglages).

Accessibilité : zones ≥ 48 dp, titres de groupes marqués comme titres, états « coché / décoché » et descriptions parlées complètes par ligne, filtres annoncés avec leur nombre, avancement annoncé poliment,
couleurs de la charte (`Cb.success/warning`) toujours doublées d'une icône ou d'un mot, police système à 200 % vérifiée sur émulateur (fil d'étapes réduit à l'étape en cours, boutons qui passent à la ligne). **Pas testé avec un vrai lecteur d'écran (TalkBack).**

### 12.2 Vitesse perçue : ce qui a été mesuré, ce qui a été fait
Mesure (JVM, 5 000 fichiers, `PhoneAgentTest`) : les règles comprennent et proposent pour **5 000 fichiers en ≈ 0,45 s** et le groupement du plan prend ≈ 5 ms. **Le moteur n'est donc pas le goulot** ;
la lenteur vient de la lecture du dossier (une requête système par dossier), de la lecture de la durée de chaque vidéo (un en-tête par fichier) et, pour la TV, des empreintes (2 × 64 ko par fichier sur le Wi-Fi). C'est là qu'on a agi :

| Mesure | Effet |
|---|---|
| **Analyse en 2 phases** | phase 1 (règles) → le plan s'ouvre ; phase 2 (empreintes TV, IA facultative) tourne **pendant** que l'utilisateur lit, et **ajoute** ses trouvailles (cases cochées et noms saisis conservés). « Passer » l'arrête ; « Continuer » aussi |
| **Résultats au fil de l'eau** | pendant la lecture d'un dossier : nombre de fichiers / dossiers, noms déjà à améliorer et 4 exemples avant → après (recalculés à 100, 200, 400… fichiers) |
| **Cache de l'analyse** (`FileAnalysisCache`, fichier privé, effaçable) | durées des vidéos du téléphone (clé : chemin + taille + date) et empreintes des fichiers de la TV (clé : volume + nom + taille + date). Une 2ᵉ analyse ne relit que le nouveau ; au plus 300 nouvelles durées / 60 nouvelles empreintes par analyse, **mais** ce qui est déjà en cache ne compte plus dans ce budget : chaque analyse va plus loin que la précédente (testé) |
| **Opérations sans relister** | `DocTreeLibrary` tient les listes de dossiers à jour au fil des renommages / déplacements (les identifiants changent à chaque opération sur la plupart des fournisseurs) : 500 renommages = moins de 400 requêtes de listage (testé) ; la taille d'un fichier avant d'agir est relue à neuf (une seule ligne) |
| **Tâche de fond** | le travail vit dans `AssistantHost` (portée du processus), pas dans l'écran : fermer l'assistant ou tourner le téléphone n'arrête rien, et rouvrir montre où on en est. **Limite honnête** : pas de service au premier plan ni de WorkManager (≈ 1 Mo pour une tâche par jour) : si Android tue l'application, l'analyse recommence (le cache la rend peu coûteuse). Une vérification quotidienne facultative existe (`JobScheduler`, § 12.5) |
| **Milliers de fichiers** | `LazyColumn` + groupes repliés + pages de 30 ; le calcul des groupes est mémorisé |

Ce qui n'a **pas** été mesuré : une vraie bibliothèque de milliers de fichiers sur le S21+ ou sur la TV (l'émulateur a servi pour 20 à 30 fichiers). Le chiffre de 5 000 fichiers vient d'un test JVM, pas d'un téléphone.

### 12.3 Dossier du téléphone (SAF) : testé
Toute la décision (parcours, collisions, corbeille, restauration, annulation, mémoire des identifiants) est dans `core` (`DocTree.kt`, `DocTreeLibrary` au-dessus de l'interface `DocProvider`) ; `SafLibrary.kt` n'est
plus qu'un adaptateur `DocumentsContract`. Tests JVM contre un fournisseur simulé (`FakeDocProvider`) qui reproduit les cas pénibles : identifiants = chemins qui changent à chaque opération, noms insensibles à la casse (carte SD),
fournisseur **sans** `moveDocument`, fournisseur qui **renomme en silence** (« (2) » ajouté), volume retiré en cours de route, volume en lecture seule, fichier modifié depuis l'analyse, deux fichiers de même nom mis à la corbeille depuis deux dossiers.
Corrections trouvées par ces tests : le journal enregistre maintenant le nom **réel** donné par le système (sinon l'annulation échouait) ; une mise à la corbeille refusée par le fournisseur remet le nom d'origine (rien n'est laissé à moitié fait) ;
« Restaurer » depuis la Corbeille remet le fichier **dans son dossier d'origine** (d'après le journal) et non à la racine ; après une annulation, les dossiers vides créés par le rangement sont retirés (jamais un dossier non vide) ; si le dossier choisi
s'appelle déjà « Séries » le plan ne crée pas « Séries/Séries ».
Fournisseur sans déplacement : repli « copier, vérifier la taille, puis supprimer l'original » (≤ 1 Go) ; sinon refus, rien ne change. Ce repli **n'a été testé que sur le fournisseur simulé** (côté logique) et jamais sur un vrai fournisseur.

**Sur émulateur** (Android 35, vrai `ExternalStorageProvider`, sélecteur Android piloté par `uiautomator`) : `Movies` (renommage, création de dossiers `Films/<titre (année)>/`, déplacement, doublon à la corbeille avec confirmation,
restauration, annulation de 6 étapes : tout est revenu à sa place), `Download/Séries` (série + sous-titre rangés ensemble, accents), et un **volume amovible** (disque virtuel `sm partition … public`, « Virtual SD card », FAT) : lecture, déplacement dans
`Séries/Narcos/Saison 01`, accents et tiret long « – » dans les noms, annulation. `WhatsApp/Media/WhatsApp Video` : jeu de fichiers créé mais **non parcouru** sur émulateur (couvert par le test JVM, pas par l'émulateur). L'espace libre et le nom du volume
sont lus par le fournisseur (`Root.AVAILABLE_BYTES`) : « Téléphone » ou le nom de la carte. Les dossiers vides laissés par une annulation (vus lors de ces essais) sont maintenant retirés : correction faite **après** ces essais, vérifiée par test JVM, pas revue sur émulateur.
**Pas testé** : vraie carte SD / clé USB OTG, déplacement d'un volume à l'autre (impossible : un dossier choisi = un volume, le plan le dit), fournisseurs Samsung / Google Drive, plus de quelques dizaines de fichiers sur appareil.

### 12.4 Rangement automatique des nouveaux envois (OFF par défaut) : fini
Trois étapes (`AgentAuto`) : un **nom candidat** au début de l'envoi (rien n'est écrit) ; dans le service d'envoi, **avant le premier octet**, le nom n'est gardé que si la TV répond **et** n'a pas déjà un fichier de ce nom
(l'envoi reprend dans un fichier existant du même nom : un nom propre qui tombe sur un autre fichier ne doit jamais être utilisé ; sinon le nom d'origine est envoyé) ; le renommage n'est inscrit au journal (annulable) **qu'une fois le fichier arrivé**.
Défaut corrigé : l'envoi de plusieurs fichiers d'affilée (`TvTransferScreen`) attendait le nom d'origine et se serait arrêté après un fichier renommé. Réglages : interrupteur, **champ « Essayer un nom »** (montre le nom qui serait envoyé, sans rien envoyer),
nombre d'envois renommés. Seul le chemin Wi-Fi (`UploadService`) est concerné ; Bluetooth et DLNA envoient le nom d'origine. **Non testé par un vrai envoi** vers une TV : seulement compilé et relu.

### 12.5 Suggestions proactives
Toujours là : **une ligne** discrète dans la bibliothèque de la TV (masquable 7 jours). Facultatif, **OFF par défaut** : une **notification silencieuse** (canal à faible importance, sans son ni vibration) **au plus une par semaine**, seulement s'il y a de quoi
(≥ 10 fichiers à renommer, ≥ 1 Go de doublons, ou un volume plein), jamais deux fois pour la même trouvaille, jamais avec un profil enfant, jamais pour un conseil masqué. Elle porte sur le **dossier du téléphone** choisi (calcul local, aucun réseau) et ouvre l'assistant :
elle ne fait rien d'elle-même. Mécanisme : `JobScheduler` (une tâche par jour, rechargée après redémarrage : permission `RECEIVE_BOOT_COMPLETED`). L'autorisation de notifier est demandée **au moment où on active l'option** ; refusée, l'option reste éteinte. La décision (`ProactivePolicy`) est testée.
**Pas de notification pour la TV** (il faudrait la retrouver sur le réseau en tâche de fond : non fait). Vu sur émulateur : la notification est arrivée (« 15 fichiers mal nommés · 7 doublons = 43 Mo »), tâche lancée à la main (`cmd jobscheduler run`), pas attendue 24 h.

### 12.6 Captures (émulateur Android 35, `docs/library-agent-phone/`)
`01-accueil` (étapes, source) · `02-lecture` (progression et résultats déjà trouvés) · `03-verifier` (groupe de série, avant → après, confiance) · `04-pourquoi` (explication) · `05-correction-apprise` (« Retenu : … ») ·
`06-recap-corbeille` (étape 3, confirmation) · `07-carte-sd` (volume amovible) · `08-reglages` (essai de nom, notification, IA : tout éteint) · `09-tv` (TV de démonstration) · `10-police-200` (police 200 %).
Les captures de l'ancienne version (`docs/library-agent/`, § 11) montrent l'ancien écran du plan.

### 12.7 Limites et à valider (en plus du § 9)
- Rien de tout cela n'a tourné sur le vrai S21+ (interdit pendant ce travail) ni sur la vraie TV. Les doublons **sur le téléphone** se jugent par taille + durée + nom (pas d'empreinte) : deux fichiers différents de même taille et de même durée seraient signalés (rare avec de vrais fichiers ; vu sur l'émulateur avec des fichiers de test identiques).
- Phase 2 (doublons TV) : si l'application est tuée pendant la recherche, elle recommence au prochain lancement (cache conservé).
- Tâche de fond limitée à la vie du processus (§ 12.2). Pas de reprise d'un rangement interrompu après un redémarrage de l'application (le journal permet `recover()`, non branché à un écran).
- Écrans en français uniquement (le moteur de noms gère l'anglais ; les écrans ne sont pas traduits).
- Le parcours du sélecteur Android (« Utiliser ce dossier », autorisation) est celui d'Android 15 ; sur le Samsung il peut différer.
