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
| Profil enfant / contrôle parental | contenu protégé : jamais listé, renommé, déplacé, mis à la corbeille, lu (empreinte) ni envoyé ; profil enfant actif : conseils seulement. **Branché** : la TV évalue et le dit dans `/api/library` (voir § 12) |
| TV ou téléphone | le téléphone a de vrais dossiers ; la TV a des dossiers **virtuels** (étiquette gardée par la TV, fichiers toujours à plat : voir § 13) ; une TV ancienne reste plate |
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
6. **Pas pendant la lecture ni l'envoi** (état d'analyse **et** vérification au moment d'agir, **et** refus côté TV : voir § 14), pas de fichier modifié depuis l'analyse (taille), pas de fichier protégé.
7. **Espace** : déplacement entre volumes seulement si ≥ 1 Go reste libre à destination et que le fichier tient (FAT32) : vérifié par l'assistant **et** par la TV elle-même (§ 14).
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
Les dossiers de la TV sont décrits au § 13 (routes `/api/folders`).

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

- **Les dossiers de la TV sont virtuels** (§ 13) : sur la clé, les fichiers restent à plat ; un PC qui lit la clé ne voit pas `Séries/…`. Un nom reste unique sur toute la TV
  (deux « Episode 01.mkv » de séries différentes ne peuvent pas coexister : l'assistant écrit « Série – S01E01 », donc unique). Une TV plus ancienne que cette version reste plate.
- **Dossier du téléphone** : sélecteur Android (SAF). Sur Android 11+, le système refuse de choisir le dossier Téléchargements lui-même : choisir un sous-dossier.
  Déplacer d'un dossier à l'autre dépend du fournisseur de fichiers (`DocumentsContract.moveDocument`) ; si le système refuse, l'étape est signalée et rien n'est supprimé.
  La corbeille du téléphone est un dossier visible « Corbeille CastBridge » dans le dossier choisi, **jamais vidé automatiquement** (la TV, elle, purge à 30 jours).
  Ce code n'a pas été exécuté sur un vrai téléphone ni sur un vrai dossier (voir § 9).
- **Un seul volume** pour le téléphone (pas de déplacement entre volumes là-bas).
- **Contrôle parental** : branché (§ 12). Limites : un fichier avec une règle « cette vidéo » est protégé contre le renommage (la classification suit le nom) ;
  tant que le parent n'a pas classé ses vidéos (« non classée = adulte »), l'assistant n'y touche pas et le dit (« N fichiers protégés ») ; une TV plus ancienne ne dit rien
  sur le contrôle, donc l'assistant refuse de toucher à quoi que ce soit.
- **Ambiguïtés assumées** : « Mariage/Anniversaire/Voyage… + année » = famille (un film de ce titre serait mal classé) ; une vidéo « Artiste - Titre » sans durée = clip (confiance basse, non cochée) ;
  `Mr. Bean` perd son point ; `The Final Cut 2004` en majuscules peut perdre « FINAL » ; épisodes d'anime sans saison (« E1045 ») ; les titres tout en minuscules sans accents restent sans accents.
- **Durée des vidéos du téléphone** : lue dans l'en-tête (300 fichiers de ≥ 5 Mo au plus) ; sinon inconnue.
- **Empreintes** : jusqu'à 60 fichiers de même taille par analyse ; un nom présent sur deux volumes n'est pas empreint (le flux de la TV ne distingue pas) : jugé par taille + durée + nom.
- Les barres de progression des déplacements entre volumes suivent l'état de la TV (un seul déplacement à la fois, géré par la TV).

## 9. Ce qui reste à valider avec la vraie bibliothèque d'Esaie

1. Passer l'assistant sur la bibliothèque réelle (séries **Prison Break**, etc.) **en regardant le plan sans rien cocher** : relever les noms mal compris, en faire des cas de `NamingCorpus`.
2. Essayer sur la TV de référence (GaiaOS, clé exFAT) : renommage, déplacement interne → clé (lent : 2 à 15 Mo/s), mise à la corbeille, restauration, annulation, coupure du Wi-Fi en cours de route.
   Il faut d'abord **installer la TV 0.12+** (routes `/api/trash/*`) ; sinon l'assistant refuse la corbeille.
3. Le dossier du téléphone (SAF) sur le Samsung S21+ : choisir `Movies`, `Download/Séries`, `WhatsApp/Media/WhatsApp Video` ; vérifier le déplacement et la corbeille.
4. Décider si l'aide de l'IA doit être activée sur le serveur (clé, modèle, coût) : le texte de consentement (`AiConsent`) est à **faire relire** (juriste), comme celui de `ConsentText`.
5. ~~Brancher `ContentGuard` au contrôle parental~~ : fait (§ 12) ; à essayer sur la vraie TV avec un profil enfant (non testé hors JVM / émulateur).

## 10. Tests

`gradle :core:test` (JVM) — package `castbridge.core.library.agent` : corpus (3 tests dont stabilité des noms), planificateur (24), exécuteur / sécurité / annulation / reprise (27), couche IA (13),
TV réelle (serveur de test, volumes, corbeille, PIN, empreintes : 12), contrôle parental (19), dossiers de la TV (17), durcissement (17 : `MoverFaultTest`, `TvHardeningRouteTest`).
Total `:core:test` après cette étape : 707 tests (1 ignoré, 0 échec). `./mvnw -q test` dans `backend/` : `LibrarySuggestApiTest` (9 tests : authentification, consentement,
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
| `docs/library-agent/07-tv-dossiers.png` | **écran de la TV** (émulateur Android TV 34, 1920x1080, vraie appli `receiver`) : rangées « Films » et « Séries › Prison Break › Saison 01 » créées par `/api/folders/set`, fichiers toujours à plat dans `videos/` |

## 12. Contrôle parental (branché)

Principe : **la TV évalue**, parce qu'elle seule possède la configuration (profils, règles de classement, « non classée = adulte »). Le téléphone n'a donc **pas besoin du code
parental** et aucune donnée parentale ne le traverse : `GET /api/library` ajoute `"guard":true`, `"childActive":bool` et, par fichier, `"protected":bool`.

Un fichier est **protégé** quand le contrôle est actif (un code parental existe, le contrôle est activé) et :
- c'est une vidéo dont la classification dépasse l'âge du profil de référence : le profil **actif**, sinon le **plus jeune** profil (un parent qui a ouvert une séance de 30 min
  ne rend pas « organisable » ce qui est interdit aux enfants qui reviennent) ;
- elle n'est pas classée : « non classée » vaut `ParentalConfig.unrated` (adulte par défaut) ;
- une règle porte sur ce nom de fichier (renommer perdrait silencieusement le classement, qui suit le nom) ;
- c'est un sous-titre dont la vidéo est protégée.
Photos, musique, documents, applications ne sont pas classés : jamais protégés. Un profil « adulte » seul ne protège rien.

Effets (`LibraryAgent.analyze`, `Executor`, `AiApply.select`, tous testés) :
1. un fichier protégé est **retiré de l'instantané avant tout** : son nom n'est ni analysé, ni **affiché** (pas même dans « laissés de côté » : seul le **nombre** apparaît, « 2 fichiers
   protégés par le contrôle parental »), ni inclus dans les conseils, les doublons, les habitudes ;
2. il n'est **jamais lu** (pas d'empreinte, pas de flux), **jamais envoyé** au serveur d'aide de l'IA (le filtre est aussi dans `AiApply.select`) ;
3. à l'exécution, un plan qui le contiendrait quand même est refusé étape par étape (`FileRef.guarded`, `ContentGuard`) ;
4. **profil enfant actif** (`childActive`, hors séance parent) : l'assistant ne donne que des conseils, le plan porte `childActive` et l'exécuteur ne change **rien** ;
5. **échec sûr** : une TV qui ne répond pas `guard:true` (version ancienne) est traitée comme « tout est protégé » (« mettez CastBridge-TV à jour ») ; une corbeille absente l'était déjà.

Code : `core/library/agent/ParentalGuard.kt` (`ParentalContentGuard`, pure ; `EngineContentFlags` côté TV), `ContentFlags` (`core/tv/Library.kt`), `ReceiverServer.libraryJson`,
`TvSnapshot.read`. Tests : `ParentalGuardTest` (19).

## 13. Dossiers sur la TV : étude d'impact et choix

**Question** : mettre de vrais sous-dossiers (`Séries/<Série>/Saison 01`) dans le stockage de la TV ?

**Réponse : non pour des dossiers physiques, oui pour des dossiers virtuels** (implémentés, testés).

Pourquoi pas de vrais sous-dossiers :
| Élément | Impact |
|---|---|
| Identité d'un fichier | partout un **nom simple** (`/stream/<n>`, `/api/play`, `/api/rename`, `/api/delete`, `/api/thumb`, `/api/library/watched`, playlists, téléchargements aria2, import USB, Bluetooth) ; `safeName` refuse `/`. Un chemin obligerait à changer **tous** les clients (téléphones 1.0/1.1 déjà installés, page web `/admin`) |
| `FileStore` / `VolumeRegistry` | `listFiles()` plat, `finalSize`, `partSize`, `Meta`, fichiers `.part`, marques « vu », `cleanOrphans`, `Storage.used`/quota/éviction : tout suppose **un dossier par volume** |
| Volumes SAF (dossier choisi au sélecteur) | `AndroidVolumes` : enfants d'un seul dossier ; sous-dossiers = `DocumentsContract` récursif, lent et fragile |
| Index de la bibliothèque, miniatures | clés `(nom, taille)` / `(nom, taille, date)` : deux fichiers de même nom dans deux dossiers se confondraient |
| Corbeille | `.castbridge-trash` à plat par volume : un nom + un dossier à restituer |
| exFAT / FAT32 | les dossiers y existent, mais chaque création / déplacement est une écriture de plus sur une clé lente (2-15 Mo/s) sans journal : un arrêt au mauvais moment peut laisser un dossier incohérent ; FAT32 : 255 car. par nom **et** chemin limité |
| Anciens clients | une liste plate de noms **doit** rester complète : avec de vrais dossiers, un ancien client ne verrait que la racine |

**Ce qui est fait : une couche de dossiers virtuelle** (`core/tv/Folders.kt`, `FolderIndex` + `FoldersApi`).
- Un dossier est un **attribut du fichier** gardé par la TV (fichier `folders.db` des données privées de l'app, réécrit de façon atomique : fichier temporaire, `sync`, renommage). Les fichiers **ne bougent pas** : à plat, un seul espace de noms. Rien d'autre ne change : flux, lecture, miniatures, positions de reprise, marques « vu », volumes SAF, limites exFAT / FAT32, déplacement entre volumes (le nom, donc le dossier, est conservé).
- **Rétro-compatibilité** : `/api/library` garde la liste plate et complète ; il gagne `"folders":true` (au sommet) et `"folder":"Séries/…"` par fichier. Un ancien client ignore ces champs et voit **tous** les fichiers, par leur nom. Une TV sans cette version ne les envoie pas : l'assistant le sait (`foldersSupported`) et ne propose alors **aucun** dossier (testé).
- Chemin accepté : 1 à 4 segments, caractères exFAT / FAT32 / NTFS interdits refusés (`SafeName.checkFolder`), `..` et chemins absolus refusés, Unicode normalisé (NFC), la casse d'un dossier existant l'emporte (`séries` = `Séries`). Un dossier existe tant qu'un fichier y est (pas de dossier vide).
- Le dossier suit le fichier : renommage (`/api/rename`), suppression (`/api/delete`, suppression après lecture), corbeille (il est gardé et **remis à la restauration**) ; un nouveau fichier du même nom n'en hérite pas.
- Routes (derrière le code de la TV) : `GET /api/folders` (dossiers utilisés + nombre de fichiers), `POST /api/folders/set?name=&folder=` (`folder=` vide = racine), `POST /api/folders/rename?from=&to=` (renomme aussi ce qu'il y a dessous ; fusion permise : ce n'est qu'une étiquette). Changer de dossier **ne touche aucun octet** : c'est sûr même pour un fichier en lecture ou en envoi.
- Assistant : `Planner` propose `Séries/<Série>/Saison 01`, `Films/…`, etc. pour la TV comme pour le téléphone si la TV dit `folders:true` ; `TvLibraryOps.moveToFolder` appelle `/api/folders/set` ; annuler remet le dossier d'avant ; une seconde analyse ne propose plus rien (pas d'aller-retour).
- Écran de la TV : `LibrarySections` ajoute, après « Toutes », **une rangée par dossier** (`Séries › Prison Break › Saison 01`, épisodes dans l'ordre du nom, donc « Lire la suite de cette rangée » enchaîne la saison). Chaque fichier reste aussi dans « Toutes » : un dossier ne cache jamais un fichier.
- Limites : la clé lue sur un PC ne montre pas les dossiers ; un nom est unique sur toute la TV ; pas de dossier vide ; les volumes n'ont pas de dossiers par volume (le dossier suit le fichier d'un volume à l'autre).

Tests : `TvFoldersTest` / `FolderIndexTest` (17) : chemins, casse / Unicode, redémarrage et fichier tronqué, routes, anciens clients, TV sans dossiers, rangement bout en bout sur serveur réel puis annulation, rangées de l'écran.

## 14. Durcissement de l'écriture sur la TV (renommer, déplacer, corbeille)

Contexte : clé USB exFAT lente (2-15 Mo/s), coupures de courant, clé retirée, plusieurs téléphones.

| Risque | Mesure (testée) |
|---|---|
| Un renommage **écrase** silencieusement (un `rename` POSIX remplace) ; deux renommages vers le même nom | `FileStore.rename` refuse si la cible existe ; verrou d'espace de noms `NameSpace` partagé par `/api/rename` et la restauration de la corbeille (test : 25 courses, un seul gagnant, aucun fichier perdu) |
| Changement de casse seul sur exFAT (« film » → « Film ») refusé comme « existe déjà » | accepté (même entrée) |
| Renommer / déplacer / mettre à la corbeille **sous un lecteur** | `ReceiverServer.busyReason` : 409 `moving` / `uploading` (une copie `.part` existe) / `streaming` (un téléphone lit `/stream/`, suivi par `StreamUse`, ignoré après 60 s d'inactivité) / `playing`. `/api/rename?safe=1` (l'assistant) refuse un fichier en lecture au lieu d'**arrêter** la vidéo ; sans `safe`, comportement historique (écran de la TV, page web) |
| Déplacement : espace | la TV applique elle-même la règle de 1 Go libre **après** (comme un envoi), plus seulement la réserve de 100 Mo |
| Déplacement coupé (courant, appli tuée, clé retirée) | la copie va dans `.part` avec sa taille ; reprise au même octet ; marqueur `.castbridge-move` sur la clé : « copying » puis « verified » |
| Source modifiée pendant la copie | taille **et** date comparées avant validation ; sinon la copie est abandonnée, rien n'est validé |
| Copie corrompue (clé qui ment) | les 1 Mo du début et de la fin sont comparés (SHA-256) avant de supprimer la source ; sinon la copie est supprimée, la source gardée |
| Coupure **entre** la copie vérifiée et la suppression de la source | marqueur « verified » écrit (synchronisé) avant ; au démarrage ou au retour de la clé, `Mover.recover` termine la suppression **seulement** si la copie est vérifiée et identique ; ne supprime **jamais** un fichier non vérifié (testé : copie de l'utilisateur sans marqueur, marqueur « copying », source différente) |
| Renommage / corbeille pas encore écrits sur la clé quand on la retire | `sync` du fichier renommé sur les volumes amovibles |
| Liste de la TV périmée après une mise à la corbeille | `TrashApi` prévient le serveur (`changed`) |
| Panne simulée | `MoverFaultTest` (11) : disque plein puis reprise, coupure franche (`PowerCut`, une `Error` qu'aucun code ne peut attraper) pendant la copie / après la vérification, clé absente, source modifiée, copie corrompue, `.part` d'un autre fichier ; `TvHardeningRouteTest` (6) |

Ce qui n'est **pas** fait : un `fsck` de la clé après coupure (impossible depuis une appli) ; une vérification complète octet par octet (doublerait le temps sur une clé lente : début et fin seulement) ; le renommage lui-même est atomique côté système de fichiers mais exFAT n'a pas de journal : une coupure exactement pendant l'écriture de l'entrée de répertoire peut corrompre la clé (rare, hors de portée de l'appli).
