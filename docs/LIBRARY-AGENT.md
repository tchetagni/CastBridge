# Assistant « Ranger ma bibliothèque »

> Branches `feat/library-agent` (assistant) puis `feat/agent-c-ai` (qualité des propositions, état de la bibliothèque, couche IA du serveur). Demande d'Esaie : « un agent sur l'app smartphone intelligent qui lira la bibliothèque,
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

### Catalogue exhaustif des formes (branche `claude/naming-patterns`) : voir `docs/NAMING-PATTERNS.md`

Le catalogue (famille, exemple, règle, source, reconnu avant / après), la méthode, les mesures, les tests ReDoS, le regroupement des titres et les limites sont dans **`docs/NAMING-PATTERNS.md`**. Ajouts au moteur : suffixes de sous-titres (`.eng.forced`, `.fr.sdh`, `.pt-BR`, 3 lettres), mots de saison / épisode en 12 langues + chiffres romains + dossiers parents, séries quotidiennes par date (`Parsed.date`, jour/mois ambigus jamais devinés), spéciaux (`S00E01`, `OVA`, `SP01`, dossier `Specials`), numérotation à 3-4 chiffres **avec preuve** (étiquette de source télé, sans année), parties `CD1` / `Part 2` (`Parsed.part`, nom `Titre (année) - part1`) et éditions (`Parsed.edition`, jamais des doublons), `@canal` / `t.me/` / `Forwarded`, CamelCase, `Titre - nn - Épisode` en dossier de la même série. Regroupement : `Text.groupKey` + `SeriesClassifier.plan` (un dossier par série quelle que soit l'écriture, `Titre (année)` si deux années coexistent) + table d'alias **facultative** `SeriesAliases` (`series-aliases.tsv`, jamais appliquée par défaut). Nouveaux jeux : **DEV-3** (réglage) et **GELÉ-3** (`naming/frozen-3.tsv`, SHA-256 vérifié, jamais utilisé pour régler) : GELÉ-3 **52,6 % → 99,3 %** (écrits à la main : 68,2 % → 98,7 %), GELÉ **2 073 → 2 074 / 2 087**, GELÉ-DUR **799 / 812 inchangé**. Garde-fous : `FalsePositiveTest`, `PathologicalNamesTest` (noms de 250 caractères, délai 2 s).

### Taux de bonnes propositions : corpus, jeux gelés, chiffres honnêtes

Chaque cas attend le **nom**, le **dossier** et le **type**. Les attendus sont calculés **à partir des vraies métadonnées** (titre, saison, épisode…) par le générateur, jamais à partir du moteur.

| Jeu | Cas | Rôle |
|---|---|---|
| **DEV** : générés (`CorpusGen`, `CorpusGenHard`, graine fixe, titres « DEV ») + écrits à la main (`DevHand`) + anciens jeux A et B | 5 252 | **sert à régler les règles** ; rapport des échecs dans `build/reports/naming-dev-failures.txt` |
| **GELÉ** `naming/frozen.tsv` : 187 écrits à la main (`FrozenHand`) + 1 900 générés (titres disjoints, graine 20261001) | 2 087 | formes courantes. **Jamais utilisé pour régler une règle** |
| **GELÉ-DUR** `naming/frozen-hard.tsv` : 112 écrits à la main (`FrozenHardHand`) + 700 générés (formes inhabituelles : numérotation exotique, espaces, accents décomposés, extensions en majuscules, étiquettes rares, titres faits de chiffres, noms d'appareils photo) | 812 | formes difficiles. **Jamais utilisé pour régler une règle** |

Les deux jeux gelés sont des fichiers TSV dont le **SHA-256 est vérifié par un test** (`FrozenCorpusTest`, `FrozenHardCorpusTest`) : les modifier fait échouer `gradle :core:test`. Ils ont été écrits **avant** toute modification des règles pour ce travail, et mesurés **une fois** avant (« avant » ci-dessous). Un test (`DevCorpusTest`) interdit qu'un cas écrit à la main pour le réglage soit aussi un cas gelé. Les tests de jeux gelés n'affichent que les totaux par famille, pas les cas en échec (pour ne pas être tenté de les corriger un par un).

| Mesure | Avant (règles de `feat/library-agent`) | Après (`feat/agent-c-ai`) |
|---|---|---|
| **GELÉ** (formes courantes) | 1 971 / 2 087 = **94,4 %** | 2 073 / 2 087 = **99,3 %** |
| dont écrits à la main (187) | 183 / 187 = 97,9 % | 183 / 187 = 97,9 % (inchangé) |
| dont générés (1 900) | 1 788 / 1 900 = 94,1 % | 1 890 / 1 900 = 99,5 % |
| **GELÉ-DUR** (formes inhabituelles) | 564 / 812 = **69,5 %** | 799 / 812 = **98,4 %** |
| dont écrits à la main (112) | 92 / 112 = 82,1 % | 105 / 112 = 93,8 % |
| dont générés (700) | 472 / 700 = 67,4 % | 694 / 700 = 99,1 % |
| DEV (réglé dessus : **pas une mesure honnête**) | ≈ 85 % au premier passage | 5 251 / 5 252 = 99,98 % |

**Comment lire ces chiffres (limites, à ne pas oublier).**
- Les parties **générées** des jeux gelés utilisent les **mêmes gabarits** que le jeu DEV (autres titres, autres graines). Elles mesurent « une forme déjà vue sur d'autres titres », pas « une forme jamais vue » : leurs 99 % sont **optimistes**.
- Les parties **écrites à la main** sont l'estimation la plus indépendante : 97,9 % (formes courantes, inchangé) et 93,8 % (formes difficiles). Même là, l'auteur est le même que celui des règles, et quelques formes du jeu DEV écrit à la main ont été inspirées par celles du jeu gelé dur (autres titres, autres dates, jamais les mêmes noms : le test l'interdit) : le gain 82 → 94 % est en partie de la fuite de **formes**, pas de cas.
- Les 14 + 13 échecs restants des jeux gelés **n'ont pas été examinés** (volontairement) ; leurs familles seulement : jeu gelé = animés (7), séries générées (3), séries écrites à la main (2), sous-titre (1), musique (1) ; jeu gelé dur = titres faits de chiffres / initiales (6), écrits à la main (7).
- **Un corpus écrit par nous n'est pas une bibliothèque réelle.** Sur la vraie bibliothèque d'Esaie il faut s'attendre à moins (§ 9, point 1 : passer l'assistant en lecture seule et relever les erreurs).

**Règles générales ajoutées** (aucune par cas particulier) : `S01xE04` / `S01 - E04` ; épisodes multiples (`S01E04E05E06`, `1x04-05`) ; saison écrite après l'épisode (« Episode 4 - Season 1 », « Ep 7 Saison 8 ») ; « Saison 2 - 05 » ; fichier `04.mkv` dans `Série/Saison 2` ; marqueur entre crochets / parenthèses (`[1x04]`, `(1x04)`) sans laisser les crochets dans le titre ; groupe de diffusion avec espaces (`[Anime Time] Bleach - 138`) ; soulignés dans les parenthèses de bruit (`(Official_Video)`, `| Clip_officiel`) ; crédits (`| Prod. by …`) ; `Track 05 -`, face de disque `A1` ; `H 264`, `DD5.1` entre soulignés ; nom de site collé en fin de nom (`… E07 NetNaija`) ; « FRENCH iNTERNAL / PROPER » (mot de langue suivi d'un mot de publication) ; « MULTi SUBS » n'est pas une piste MULTI ; **langue du titre calculée sur le titre seul** (« house of cards » reste anglais malgré « Saison 2 Épisode 4 ») ; initiales (`S.W.A.T.`, `S.H.I.E.L.D.`) ; « LA CASA DE PAPEL » (l'article, pas Los Angeles) ; noms séparés seulement par des tirets (`Prison-Break-S01E04`) ; `Artiste_Titre` avec un seul tiret ; suffixes d'appareil photo (`_HDR`, `_BURST001`, `.MP`) ; captures « Screenshot 2024-03-15 at … », « Capture d’écran … à … » ; heure `2.22.11 PM`.

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

## 4 bis. État de la bibliothèque : ce que l'assistant constate (`Health.kt`, `Analysis.health`)

Calculé **localement** (noms, tailles, dates, durée et marques de lecture que la TV garde déjà) ; ne change rien tout seul. Chaque constat a un texte, des octets concernés, les fichiers, une priorité, et, quand c'est une action, une mise à la corbeille toute prête (jamais cochée, passe par le plan, la confirmation et l'`Executor` comme le reste).

| Constat | Règle | Action proposée |
|---|---|---|
| Épisodes manquants | ≥ 3 épisodes d'une saison, trous **à l'intérieur** de la plage (et le début si on commence à l'épisode 1 ou 2) ; pas de constat s'il y a plus de trous que d'épisodes (choix, pas accident) ; les doubles épisodes comptent toute leur plage ; animés sans saison inclus | conseil (« Prison Break saison 1 : il manque l'épisode 3 ») |
| Saisons mélangées | la même saison sur plusieurs volumes ; épisodes numérotés avec et sans saison (« E12 » / « S01E12 ») | conseil |
| Doublons de qualité | même épisode / film en plusieurs qualités **dans la même version linguistique** : on garde la meilleure résolution (puis celui qu'on a commencé à regarder, puis le plus gros) | corbeille des autres, avec « conservé : 1080p » |
| Doublons exacts | empreinte identique, ou même taille + même durée + même nom nettoyé | corbeille des copies |
| Fichiers vides, téléchargements interrompus | taille 0 ; `.part`, `.crdownload`, `.tmp`, `.aria2`, paire `fichier` + `fichier.aria2` ; **rien** s'il a bougé il y a moins de 3 jours (téléchargement probablement en cours) | corbeille (vides et interrompus) ; les octets ne comptent comme « récupérables » que si le fichier est vide ou inactif depuis 3 jours |
| Fichier tronqué | vidéo de durée connue dont le débit est < 80 kbit/s ; épisode < 20 % de la médiane de ses voisins (≥ 4 épisodes, médiane ≥ 50 Mo) | **conseil seulement** (peut être un épisode court) |
| Déjà vu depuis longtemps | décidé par le plan (volume sous pression seulement) | corbeille |
| Pression d'espace | volume < 2 Go libres ou plein à ≥ 90 % | « Il reste 800 Mo sur « Mémoire interne » : 1,5 Go récupérables ici : de quoi repasser au-dessus de 2 Go » ou « Rien de sûr à récupérer ici : déplacez vers une clé USB » |

**Chiffres.** `HealthReport.recoverableBytes` additionne ce qui est récupérable **sans compter deux fois** un même fichier ; `headline()` donne « 4,2 Go récupérables (3 doublons, 1 fichier incomplet…) » ; `recoverableByVolume` et `pressure` disent ce que changerait la récupération sur chaque volume.
**Priorités** (`ranked`) : pression d'espace d'abord ; puis, sur un volume sous pression, ce qui libère le plus ; fichiers cassés ; doublons exacts, doublons de qualité ; épisodes manquants (**en tête de ces conseils pour une série regardée ces 30 derniers jours**, un peu plus si on regarde surtout des séries) ; déjà vus ; saisons mélangées.
**Bandeau** : `LibraryAgent.insights` ajoute, seulement quand cela apporte quelque chose, « 4,2 Go récupérables (…) », « 2 fichiers vides ou incomplets » et la ligne du premier épisode manquant. Les **fichiers protégés** (contrôle parental) et le **profil enfant** sont exclus comme partout.
**Interface** : non touchée (zone de l'agent « UI ») ; elle peut lire `analysis.health.ranked` et `analysis.health.findings[].changes` pour afficher / proposer. Les nouvelles raisons de mise à la corbeille sont `TrashWhy.PARTIAL` et `TrashWhy.EMPTY` (l'écran du plan range tout ce qui n'est pas `WATCHED_OLD` sous « Doublons » : à adapter si ces changements y sont versés).

## 5. « IA » : ce qui est réel et ce qui ne l'est pas

- **Réel et actif** : un moteur de règles et d'heuristiques, déterministe, avec du contexte. Ce n'est **pas** un modèle d'apprentissage :
  l'« apprentissage » est une mémoire de corrections (alias de titres, dossiers préférés, fichiers ignorés), pas un entraînement.
- **Interface `NamingModel`** : (a) `LocalRulesModel` = les règles derrière la même interface (aucun réseau, aucune dépendance : pas de
  TFLite / ML Kit, l'APK ne grossit pas) ; (b) `ServerNamingModel` = `POST /api/v1/library/suggest` du serveur CastBridge.
- **Couche IA du serveur** : désactivée par défaut. Pour les noms que les règles ne comprennent pas, des noms **nettoyés** partent au
  serveur, qui appelle un modèle de langage **configuré par variables d'environnement** (fournisseur, clé, URL, modèle : § 15).
  **Sans clé (état actuel du dépôt et du serveur) le serveur répond `available:false` et n'appelle personne.** Aucune clé dans le dépôt
  (un test le vérifie). Deux familles de services sont prises en charge : l'**API Messages d'Anthropic** (par défaut, modèle `claude-haiku-4-5`) et tout
  service **compatible « chat completions » d'OpenAI**. **Tout a été testé avec un faux fournisseur et un faux service HTTP local, jamais contre un vrai modèle** :
  la qualité réelle des propositions de l'IA est **inconnue** tant que ce n'est pas essayé (§ 15, étape « essai »).
- **Prompts versionnés** (`backend/src/main/resources/library/prompts/suggest-<version>.txt`) : `v1` (défaut, minimal) et `v2` (règles + trois exemples). On **ne modifie jamais** une version déjà utilisée : on ajoute un fichier et on change `CASTBRIDGE_LIBRARY_PROMPT_VERSION`. La réponse porte `promptVersion` pour comparer.
- **Sortie JSON stricte** : le modèle doit répondre **un seul objet** `{"v":1,"results":[…]}` ; du texte autour, une seconde valeur, une mauvaise version, une clé inconnue à la racine **refusent toute la réponse** (le téléphone reçoit un 502 poli, le texte du modèle n'est jamais répété). Chaque résultat est validé seul : clé inconnue, nombre écrit en texte, type inconnu, position hors liste ou en double, titre inutilisable → écarté et compté dans `rejected`. Titres nettoyés de `/ \ : * ? " < > |`, bornes sur année / saison / épisode / confiance.
- **Coût et limites** : chaque réponse contient `usage` (`inputTokens`, `outputTokens`, `estimatedCostUsd`) ; les nombres de jetons sont ceux du service, ou une estimation (≈ 3 caractères par jeton) si le service n'en donne pas. Le prix par million de jetons vient de `CASTBRIDGE_LIBRARY_PRICE_IN/OUT_PER_MTOK` (défauts : Haiku 4.5, 1 $ / 5 $). **Avant** chaque appel le serveur réserve le coût estimé : limite **par appareil et par heure** (30), **par appareil et par jour** (300 noms), **budget quotidien global** (2 $ estimés par jour UTC) ; au-delà : réponse 429, **le modèle n'est plus appelé** et l'assistant continue avec ses règles locales. Ordre de grandeur **calculé, non mesuré sur un vrai service** : une analyse de 40 noms ≈ 0,003 à 0,013 $ avec Haiku 4.5 (tableau du § 15).
- **Mode hors ligne inchangé** : sans consentement, sans clé, serveur injoignable, limite atteinte ou réponse invalide, l'assistant fonctionne avec les règles locales (aucune erreur bloquante).
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
Côté serveur : limite 30 requêtes par appareil et par heure (+ limite par IP existante), 300 noms par appareil et par jour, 40 noms par requête, budget quotidien global (voir ci-dessus),
rien n'est stocké ni journalisé (le journal d'accès ne garde que le chemin). **Le texte exact de ce que voit l'utilisateur est au § 15 : à faire relire.**

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
  Exécuté sur émulateur et sur un fournisseur simulé (§ 16) ; **pas encore sur un vrai téléphone** (§ 9).
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
3. Le dossier du téléphone (SAF) sur le Samsung S21+ : déjà fait sur émulateur (§ 16) ; reste le vrai téléphone (fournisseur Samsung, vraie carte SD, fichiers de plusieurs Go).
4. Décider si l'aide de l'IA doit être activée sur le serveur (clé, modèle, coût) : le texte de consentement (`AiConsent`) est à **faire relire** (juriste), comme celui de `ConsentText`.
5. ~~Brancher `ContentGuard` au contrôle parental~~ : fait (§ 12) ; à essayer sur la vraie TV avec un profil enfant (non testé hors JVM / émulateur).

## 10. Tests

`gradle :core:test` (JVM) — package `castbridge.core.library.agent` : corpus (3 tests dont stabilité des noms), planificateur (24), exécuteur / sécurité / annulation / reprise (27), couche IA (13),
TV réelle (serveur de test, volumes, corbeille, PIN, empreintes : 12), contrôle parental (19), dossiers de la TV (17), durcissement (17 : `MoverFaultTest`, `TvHardeningRouteTest`).
(branche TV) Total `:core:test` après cette étape : 707 tests (1 ignoré, 0 échec). `./mvnw -q test` dans `backend/` : `LibrarySuggestApiTest` (9 tests : authentification, consentement,
validation, débit, appareil bloqué, aucun secret dans la configuration, client LLM contre un faux service).

`gradle :core:test` (JVM) — package `castbridge.core.library.agent` : corpus (jeux A / B, DEV de 5 252 cas, GELÉ de 2 087, GELÉ-DUR de 812, stabilité des noms : un second passage ne change rien, SHA-256 des jeux gelés, anti-fuite DEV / gelé), santé de la bibliothèque (`HealthTest`, 21 tests : épisodes manquants, doubles épisodes, saisons mélangées, doublons de qualité, fichiers vides / interrompus / tronqués, priorités, pression d'espace, fichiers protégés, profil enfant, exécution par l'`Executor`), planificateur, exécuteur / sécurité / annulation / reprise, couche IA côté téléphone (consentement : le texte liste **exactement** les champs de la requête), TV réelle (serveur de test, volumes, corbeille, PIN, empreintes).
`./mvnw -q test` dans `backend/` : `LibrarySuggestApiTest` (authentification, consentement, validation, débit, appareil bloqué, aucun secret dans la configuration), `LibraryAiLayerTest` (sortie stricte, prompts, coût, quotas, deux transports HTTP contre un faux service local, configuration), `LibrarySuggestBudgetApiTest` et `LibrarySuggestBudgetExhaustedApiTest` (vrai suggesteur sur un **faux fournisseur** : usage et coût dans la réponse, quota par appareil, budget quotidien, 502 sur réponse non conforme, modèle non appelé au-delà des limites). **Aucun test n'appelle un service externe.**
Note : `TrustTest.onlyTrustedPhonesGetTokensAndRevocationKillsThem` (module `:core`, étranger à ce travail) est parfois instable (jeton « altéré » tiré au hasard).
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

## 15. Mise en service de l'IA du serveur (procédure pour Esaie) et texte de consentement à relire

**État actuel : rien n'est activé.** Le serveur de production n'a pas de clé : `POST /api/v1/library/suggest` répond `available:false`. Cette branche n'a **rien déployé** et n'a **appelé aucun service externe**.

### Procédure (sur le serveur, jamais dans le dépôt)

1. **Choisir le fournisseur et ouvrir un compte** (par exemple console.anthropic.com pour Claude). Créer une **clé d'API dédiée** à CastBridge, avec une **limite de dépense mensuelle** côté fournisseur (la première protection ; celle du serveur n'est qu'une estimation).
2. **Mettre la clé dans l'environnement du serveur** : fichier `.env` du dossier `backend/` sur le serveur (jamais commité, `chmod 600`) :
   ```
   CASTBRIDGE_LIBRARY_LLM_API_KEY=<la clé>
   CASTBRIDGE_LIBRARY_LLM_PROVIDER=anthropic          # ou openai (service compatible OpenAI) ; avec openai, renseigner aussi _LLM_URL et _LLM_MODEL
   CASTBRIDGE_LIBRARY_LLM_MODEL=claude-haiku-4-5      # vide = le modèle par défaut du fournisseur
   CASTBRIDGE_LIBRARY_PROMPT_VERSION=v1               # ou v2 après essai comparatif
   CASTBRIDGE_LIBRARY_PRICE_IN_PER_MTOK=1.0           # dollars par million de jetons en entrée DU MODÈLE CHOISI
   CASTBRIDGE_LIBRARY_PRICE_OUT_PER_MTOK=5.0          # … en sortie
   CASTBRIDGE_LIBRARY_DAILY_BUDGET_USD=2.0            # plafond estimé par jour pour tout le serveur
   CASTBRIDGE_LIBRARY_DEVICE_DAILY_ITEMS=300          # noms par appareil et par jour
   ```
   Puis `docker compose up -d` (le fichier `docker-compose.yml` transmet ces variables). Rien d'autre à changer. Retirer la clé (ou la laisser vide) remet le serveur en mode « sans IA ».
3. **Vérifier avant d'ouvrir à d'autres** : avec un téléphone enregistré, envoyer une demande de quelques noms (l'application le fait après consentement) ; la réponse doit contenir `"available":true`, `"promptVersion"`, `usage.estimatedCostUsd`. Regarder la dépense réelle sur la console du fournisseur après quelques analyses et **ajuster les prix** ci-dessus si l'estimation est trop basse.
4. **Essai de qualité (à faire, jamais fait)** : prendre 100 noms réels que les règles ne comprennent pas, comparer `v1` et `v2` à la main, garder la version qui se trompe le moins. Les propositions de l'IA ne sont de toute façon **jamais cochées d'avance** (confiance ≤ 0,6, étiquette « Proposé par l'IA : à vérifier »).
5. **Faire relire le texte de consentement ci-dessous** (et le faire relire par un juriste avant ouverture au public), **puis** seulement activer. Si un mot change, augmenter `AiConsent.VERSION` : tout le monde est réinterrogé.
6. **Surveiller** : la dépense sur la console du fournisseur ; un 429 « suspendue pour aujourd'hui » signifie que le budget quotidien du serveur est atteint (normal, les téléphones continuent avec les règles locales).

### Coût (estimations ; à confirmer avec la console du fournisseur)

| Cas | Jetons (entrée / sortie) | Haiku 4.5 (1 $ / 5 $ par million) |
|---|---|---|
| prompt `v1` + 1 nom | ≈ 400 / ≈ 80 | ≈ 0,0008 $ |
| prompt `v2` + 40 noms | ≈ 1 300 / de ≈ 400 à ≈ 2 400 (≈ 60 jetons par nom reconnu) | de ≈ 0,003 $ à ≈ 0,013 $ (pire cas : tous les noms reconnus) |
| 100 noms (maximum d'une analyse) | ≈ 2 000 / jusqu'à 3 000 (plafond) | jusqu'à ≈ 0,017 $ |
| Budget par défaut (2 $ par jour) | | ≈ 150 analyses de 40 noms par jour au pire cas, plusieurs centaines en moyenne |

Les prompts sont courts et les sorties plafonnées (`CASTBRIDGE_LIBRARY_MAX_OUTPUT_TOKENS`, 3 000). Un modèle plus capable coûte plus cher par jeton (consulter la grille du fournisseur) : renseigner ses prix, sinon l'estimation et le budget seront faux.

### Texte de consentement (écran « Aide de l'intelligence artificielle », `AiConsent` dans `NamingModel.kt`) : À RELIRE

> **Aide de l'intelligence artificielle (facultatif)**
>
> Pour les seuls fichiers que les règles de l'application ne comprennent pas, l'assistant peut envoyer des informations au serveur CastBridge, qui les transmet à un service d'intelligence artificielle pour proposer un titre, une saison, un épisode. Voici la liste complète de ce qui part, pour chaque fichier concerné :
> - le nom du fichier, déjà nettoyé : sans lien Internet, sans adresse e-mail et sans suite de 6 chiffres ou plus (par exemple « prison break s01e04 »), 120 caractères au plus ;
> - son extension (mkv, mp4, mp3…) ;
> - un type supposé par l'application (série, film, musique, clip, cours) quand elle en a un ;
> - sa durée, arrondie à 5 minutes, quand elle est connue ;
> - son numéro dans la liste envoyée (0, 1, 2…), sans autre sens.
>
> Une fois par envoi : la langue de l'application (français ou anglais) et la mention de votre accord (« library-ai-v1 »). Comme toute connexion Internet, l'envoi laisse aussi au serveur l'adresse IP du téléphone et l'heure, et le serveur reconnaît l'appareil par son jeton (déjà utilisé pour les mises à jour) afin de refuser les appareils bloqués et de limiter le nombre de demandes. Au plus 100 noms par analyse.
>
> Le service d'intelligence artificielle est un fournisseur tiers choisi par l'administrateur de CastBridge. CastBridge n'enregistre ni ne journalise les noms envoyés ; le fournisseur peut appliquer ses propres règles de conservation, indiquées dans la documentation de mise en service.
>
> Ne quitte jamais le téléphone :
> - le contenu de vos fichiers ;
> - leurs dossiers, chemins, tailles et dates ;
> - vos vidéos et photos personnelles (WhatsApp, appareil photo, captures) et vos documents ;
> - tout ce que le contrôle parental protège, et rien du tout quand un profil enfant est actif ;
> - vos corrections et vos habitudes de visionnage ;
> - votre nom, vos contacts, votre numéro, un identifiant publicitaire.
>
> Vous pouvez l'activer et le désactiver quand vous voulez, et voir la liste exacte de ce qui serait envoyé avant chaque analyse. Désactivé, l'assistant fonctionne entièrement sur le téléphone, sans réseau.

Points à trancher à la relecture : (a) **nommer le fournisseur** dans le texte ou la politique de confidentialité (le texte dit « un fournisseur tiers » ; à préciser avant l'activation) ; (b) la **durée de conservation** du fournisseur et son éventuel usage pour l'entraînement (à vérifier dans ses conditions : certains services le désactivent pour l'API, à confirmer) ; (c) la **base légale** et le pays de traitement (les données envoyées sont des noms de fichiers nettoyés, mais un nom peut contenir un prénom : « Anniversaire de Junior » est classé « famille » et **n'est pas envoyé**, les vidéos personnelles ne partent jamais) ; (d) la mention de l'adresse IP et du jeton d'appareil.

Un test (`consentListsExactlyTheFieldsOfTheRequest`) échoue si un champ est ajouté à la requête sans que la liste du consentement change.

### Ce que cette mise en service ne fait pas

Pas de cache des noms côté serveur (aurait réduit le coût mais contredit « rien n'est stocké »), pas de statistiques de dépense persistantes (le compteur du jour est en mémoire : un redémarrage le remet à zéro), pas d'essai contre un vrai modèle, pas d'écran « coût estimé » dans l'application (la valeur `costUsd` est lue par `ServerNamingModel` ; à afficher par l'agent « UI »).



## 16. Assistant du téléphone : rapidité, dossier choisi, parcours guidé (branche `feat/agent-b-phone`)

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
- Tâche de fond limitée à la vie du processus (§ 16.2). Pas de reprise d'un rangement interrompu après un redémarrage de l'application (le journal permet `recover()`, non branché à un écran).
- Écrans en français uniquement (le moteur de noms gère l'anglais ; les écrans ne sont pas traduits).
- Le parcours du sélecteur Android (« Utiliser ce dossier », autorisation) est celui d'Android 15 ; sur le Samsung il peut différer.

## 17. Rangement réel à la réception (« Ranger ma bibliothèque » côté TV)

Ce chapitre complète le § 13 (dossiers VIRTUELS sur la TV). Les deux coexistent : l'assistant du téléphone range toujours par étiquettes (aucun octet déplacé, réversible) ; la TV range en plus VRAIMENT les fichiers, à la réception et à la demande (détail, garanties et tableau des règles : `docs/STORAGE.md` § 10).

- **Mêmes règles des deux côtés** : `Filing` (`core/tv/Filing.kt`) s'appuie sur `NameParser` et `Namer` (mêmes noms, mêmes dossiers `Séries/<Titre>/Saison NN`), avec un garde-fou plus strict que l'assistant : en dessous de 0,7 de confiance (0,8 pour un film) le nom n'est pas fait confiance, le fichier va dans `À trier` et GARDE son nom. Les dossiers virtuels du téléphone sont alignés sur les vrais dossiers dès le rangement (`FolderIndex.set`), donc l'écran du téléphone n'a rien de plus à comprendre : `folder` de `/api/library` est le dossier réel.
- **Contrôle parental** : à la réception un nouveau fichier n'est pas encore classé (donc protégé tant qu'un parent ne l'a pas classé). À la demande, un fichier protégé n'est jamais planifié ni déplacé ni nommé (seul le nombre apparaît dans le plan) ; tout est refusé quand un profil enfant est actif. Comme pour l'assistant, un fichier non protégé renommé perd les règles attachées à son ancien NOM (celles d'un fichier précis le rendent « protégé », donc intouchable).
- **Plan puis application** : `GET /api/library/organize` (essai à blanc), `POST /api/library/organize/apply` (recalcule, ne renomme que sur le même volume, jamais par-dessus, rien n'est supprimé : la « Corbeille CastBridge » n'a donc pas à intervenir). Téléphone : Bibliothèque de la TV > icône « Ranger les fichiers de la TV dans des dossiers » (`OrganizeOnTvDialog`) ; l'icône étoile reste l'assistant.
- **Anciens téléphones et anciennes données** : l'API reste compatible (champs ajoutés seulement) ; un fichier ancien resté à plat est valide et rangé à la demande ; l'ancien nom d'un fichier rangé reste reconnu pour la reprise et le « déjà sur la TV ».
- **Reste à faire** : bouton sur l'écran de la TV et réglage visible ; annulation du rangement ; `Mover` qui range à l'arrivée ; ranger aussi les fichiers diffusés pendant l'envoi une fois la lecture finie ; langue de la TV au lieu de « fr » fixe ; validation sur la vraie TV (GaiaOS) avec la vraie clé.

