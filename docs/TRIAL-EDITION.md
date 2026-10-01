# Édition d'essai (100 Mo) et droits d'accès

> **Statut : conception + squelette testé, à valider par le propriétaire.** Aucun paiement n'est implémenté ; **aucun prix, aucun prestataire de paiement** n'est fixé ici (décisions du propriétaire). Rien n'a été touché sur le serveur de production.
> Code : `tools/trial-edition/` (sélection, Python, 17 tests), `android/core/src/main/kotlin/castbridge/core/lots/` (`Edition`, `LotEditions`, `Entitlement`, `EditionPolicy`, `TrialManifest`) et `castbridge/core/owner/` (identité d'appareil, activation, commandes du propriétaire). Tests : `EditionTest` (31), `OwnerTest` (47 dans 8 classes) en plus des 64 tests `lots` existants.
> Console du propriétaire (spécification de l'écran, de la version propriétaire et des risques) : [OWNER-CONSOLE.md](OWNER-CONSOLE.md).

## 0. En bref

- **Décision du propriétaire** : l'essai ne donne accès qu'à **100 Mo** de contenu Quiz + Apprendre (+ médias des Langues), **avec toutes les sous-catégories présentes** (chaque classe, chaque matière, chaque langue, chaque niveau N0–N4 et A0–natif, chaque lot Quiz), pour **illustrer l'offre**. La production du contenu complet continue en parallèle.
- **Outil** `tools/trial-edition` : calcule `TRIAL-MANIFEST.json` (la liste exacte des lots et fichiers, avec tailles), de façon **déterministe** et **recalculée automatiquement** à chaque croissance du contenu ; **il échoue** au-dessus de 100 Mo ou si une sous-catégorie est vide.
- **Modèle** : l'essai est fait de **lots séparés `<lot>-trial`** (champ additif `edition` de `LotMeta`) ; la version complète **remplace** l'essai sans doublon. Les identifiants de leçons/questions **ne changent jamais** entre essai et complet.
- **Droits** : trois droits qui se cumulent : **essai** (toujours), **achat à la carte** d'un bouquet (définitif), **abonnement** (période, grâce hors ligne, retour à l'essai à la fin sans rien supprimer). Jeton signé, lié à l'appareil.
- **Sécurité honnête** : un blocage dans l'application est contournable ; **la vraie protection est que le serveur ne livre les lots complets qu'aux appareils autorisés** (et, pour la TV, des lots chiffrés par appareil). Ce qu'on ne peut pas empêcher est écrit au § 9.
- **Bilan sur le contenu d'aujourd'hui (Apprendre + Quiz)** : 29,9 Mo d'essai sur 100 Mo (voir § 2.5). **La catégorie Langues n'existe pas encore sur cette base** : l'outil échoue donc volontairement sur les 56 sous-catégories de langue vides, jusqu'à ce que leurs lots arrivent (branche `claude/languages-architect`).

## 1. Choix de conception : lots séparés `<lot>-trial`, pas des tranches

| | Lots d'essai séparés (`cm2-trial`) — **retenu** | Tranches d'un même lot |
|---|---|---|
| Atomicité | un lot reste installé, signé et vérifié **en entier** | le téléphone/la TV porterait un lot **partiel** à fusionner |
| Serveur | peut refuser le lot complet et servir l'essai : **deux fichiers, deux autorisations** | un seul fichier : impossible de n'en livrer qu'une partie sans le reconstruire par appareil |
| Versions | l'essai a sa propre version et se recalcule seul | la version du lot complet changerait à chaque ajustement de l'essai |
| Budget TV (10 Mo) | le plan (`LotPlanner`) traite l'essai comme n'importe quel lot (≈ 0,1 à 1,1 Mo) | il faudrait compter des « parts » dans les lots |
| Défaut | un peu de redondance (octets de l'essai) tant que le complet n'est pas là | complexité du format et des mises à jour |

**Règles** (`LotEditions`) : un lot est `TRIAL` exactement quand son scope se termine par `-trial` (un scope complet ne peut jamais finir ainsi) ; `LotMeta.edition` vaut `FULL` par défaut (**rétrocompatible** : le JSON, les index du stockage et les catalogues existants sont inchangés à l'octet près ; seul un lot d'essai ajoute `"edition":"trial"`). **La signature du catalogue couvre l'édition** : la ligne d'un lot d'essai finit par `|trial` (les lignes `FULL` ne changent pas, les signatures existantes restent valides), donc personne ne peut faire passer un lot d'essai pour un lot complet (testé).

**Remplacement propre** (budget TV 10 Mo, pas de doublon) :
- `TvLotStore` : installer le lot complet **retire son jumeau d'essai** et compte sa place comme libérée (un complet de 900 Ko s'installe même si 300 + 900 > 1 000) ; installer un lot d'essai **est refusé** si le complet est déjà là (testé).
- `LotStore` (téléphone) : `dropSupersededTrials()`, appelé à la fin de chaque synchronisation.
- `LotPlanner` : `EditionPolicy.planForTv` choisit essai ou complet selon les droits ; tant que le complet n'est pas sur le téléphone, **l'essai reste** (la TV garde quelque chose) ; l'essai n'est supprimé de la TV qu'**après** que le complet y est (`dropTrials`).
- `LotSync` : option `allowed` ; un lot complet sans droit donne `NOT_ENTITLED` (« fait partie de la version complète : débloquez-le »), jamais un échec muet.

**Correction d'un défaut existant au passage** : le nom de fichier d'un lot (`castbridge-lot-<fonction>-<périmètre>-v<n>.lot`) était ambigu pour un périmètre à tiret (`droit-l1`, `cm2-trial` : l'expression régulière lisait `learn-droit` comme fonction). Une **fonction est désormais un seul mot** (`learn`, `quiz`, `langues`, `langmedia`) ; les périmètres peuvent contenir des tirets (testé pour `cm2-trial`, `droit-l1`, `culture-afrique-trial`).

**À brancher côté Quiz** (hors de ma zone, non modifié) : `QuizLotFormat.read(file, expectScope)` compare l'`index.json` et les questions à l'`expectScope` ; un lot `quiz:cm2-trial` doit être lu avec `expectScope = "cm2"` et son `manifest.json` porte `edition:"trial"` et `baseScope:"cm2"` (voir le builder de l'outil). Non vérifié sur le code Quiz.

## 2. La sélection automatique (`tools/trial-edition`)

```
python3 tools/trial-edition/trial_edition.py select [--only learn,quiz,langues] [--previous FICHIER] [--draft]   # écrit content/TRIAL-MANIFEST.json (jamais s'il est invalide)
python3 tools/trial-edition/trial_edition.py check                                                         # le manifeste est-il à jour ? (CI : échoue s'il est périmé)
python3 tools/trial-edition/trial_edition.py build --out DIR                                               # fabrique les lots d'essai (zip) pour la publication
python3 -m unittest discover -s tools/trial-edition                                                        # 17 tests, contenu synthétique
```
Python 3.8+, bibliothèque standard. Réglages dans `tools/trial-edition/config.json` (aucune valeur n'est un prix).

### 2.1 Entrées (le « registre » et la « couverture »)
- **Apprendre** : `content/learn/scopes.txt` (classe → packs), `pack.json` (matière), leçons et exercices (`lessons/*.json`).
- **Quiz** : `content/quiz/lots/catalog-lots.json` + chaque `quiz-*.quiz.zip` (questions, `index.json`, `manifest.json`).
- **Langues** : `content/langues/*/langue.json` + `media.json` + `budget.json` (branche `claude/languages-architect`). Les 7 langues × 8 niveaux attendus sont déclarés **même si le contenu manque**, pour que l'outil échoue tant qu'une cellule est vide.
- (`docs/COVERAGE.md` du dépôt privé `castbridge-content` n'est pas accessible d'ici : ses informations utiles sont déjà dans les fichiers ci-dessus.)

### 2.2 Sous-catégories
`learn/<classe>/<matière>` · `quiz/<lot>` · `langues/<langue>/<niveau>` (A0…C2, NATIF). Une sous-catégorie sans élément **fait échouer** l'outil ; de même une classe (scope) sans échantillon, un niveau de difficulté (**N0–N4** = difficultés Quiz 1 à 5) absent d'un lot Quiz, un lot Quiz sans échantillon.

> **Hypothèse à confirmer** : je lis « niveaux N0–N4 » comme les cinq niveaux de **difficulté** du Quiz (1 = N0 … 5 = N4), seule échelle à cinq niveaux du dépôt ; le tableau est dans `config.json` (`levels.difficultyLabels`).

### 2.3 Planchers puis remplissage
1. **Plancher par sous-catégorie** (toujours pris, dans cet ordre ; le plancher qui ne tient pas fait échouer l'outil) :
   - *Apprendre* : la **première leçon** (chapitre le plus bas = introduction), la **leçon la plus difficile** (exercices liés les plus difficiles, avec priorité au niveau « examen »), une **leçon avec figure** (de préférence animée, la plus petite) si les deux premières n'en ont pas, **3 exercices corrigés** de niveaux variés (application, approfondissement, examen).
   - *Quiz* : **3 questions par niveau de difficulté** (N0–N4), prises par ordre de hachage SHA-256 de l'identifiant (déterministe, sans biais de position).
   - *Langues* : l'unité d'introduction, l'unité la plus riche en exercices, une unité avec animation, **un audio court** (≤ 60 s et ≤ 256 Kio) ; puis **une vidéo courte** (≤ 30 s, ≤ 1,5 Mo) **par langue et par niveau** (couverture croisée, les plus petites d'abord). Média sans licence admise (`CASTBRIDGE-ORIGINAL`, `CC0`, `CC-BY`, `CC-BY-SA`, `PUBLIC-DOMAIN`) : **jamais choisi**.
2. **Remplissage par priorité** : **examens nationaux d'abord** (CEP = `cm2`, BEPC = `3e`, Probatoire = `1ere`, Bac = `tle-*`, FSLC = `class6`, `form1`), puis le reste ; au sein d'un niveau de priorité, **un élément par sous-catégorie à tour de rôle** (équilibre). Dans Apprendre : 1 leçon puis 3 exercices corrigés (examen d'abord) ; dans Quiz : une question de chaque difficulté à tour de rôle ; dans Langues : unités et audios.
3. **Garde-fous** : `maxShareOfFull` (35 %) : une sous-catégorie n'est jamais donnée à plus de 35 % de son contenu complet (l'essai reste un **échantillon**, pas une copie) ; `categoryFillShare` (Apprendre 40 %, Quiz 25 %, Langues 35 % du reste après planchers) ; `reserveBytes` (2 Mo de marge sous le plafond) ; un lot d'essai texte ≤ 3 Mio, un lot média ≤ 100 Mio.
4. **Plafond dur** : la somme des tailles **non compressées** des fichiers d'essai doit rester ≤ 100 Mo (104 857 600 octets), toutes catégories confondues, médias des Langues compris. **Mesure volontairement prudente** : les lots sont des zip, donc la taille sur disque est inférieure (le Quiz se comprime ≈ 6 fois). **Le plafond n'est pas une cible** : le propriétaire peut le remplir davantage en relevant `maxShareOfFull` (compromis : plus généreux = moins d'incitation à acheter).

### 2.4 Stabilité et recalcul
- **Mêmes identifiants** : une leçon, un exercice, une question de l'essai garde l'identifiant de la version complète (testé). La projection ne modifie que les références vers ce qui n'est pas dans l'essai : `prerequisites`, `exercises`, `selfCheck` d'une leçon et les blocs `exercise` pointant vers un exercice absent sont filtrés ; chaque `pack.json` d'essai porte `"edition":"trial"`.
- **Recalcul automatique** : `select` relit tout le contenu ; `check` (à mettre en CI) échoue si `TRIAL-MANIFEST.json` n'est plus à jour. **Le manifeste n'est jamais édité à la main.**
- **Peu de remous quand le contenu grandit** : le manifeste précédent est relu (`--previous`, par défaut le fichier de sortie) et ses éléments **toujours présents sont repris en premier** (testé : aucun élément de l'essai n'en sort sans raison). Sans cela, les planchers par hachage restent stables d'eux-mêmes.
- **Déterminisme** : aucune horloge, aucun hasard ; même contenu + même configuration + même manifeste précédent = même fichier, octet pour octet (testé). Le manifeste contient l'empreinte de l'inventaire (`inventory`) et celle de la configuration.
- **Versions** : le manifeste ne fixe pas la version d'un lot d'essai ; elle est incrémentée à la publication quand l'empreinte du contenu change (comme `lots.json`).

### 2.5 Tailles (contenu actuel, `--only learn,quiz`)

| Catégorie | Essai (non compressé) | Complet (non compressé) | Éléments d'essai |
|---|---|---|---|
| Apprendre | 5,55 Mo | 17,97 Mo | 2 877 (24 classes, 100+ matières par classe) |
| Quiz | 24,21 Mo | 79,77 Mo | 48 193 questions, 35 lots |
| **Total** | **29,92 Mo** sur 100 | 97,7 Mo | 59 lots d'essai |
| Langues | — | — | **non évaluable : pas de contenu** (cible : 56 cellules langue × niveau) |

Plus gros lot d'essai : `eco-l2-trial` (1,1 Mo). Tous les lots d'essai tiennent donc dans les 10 Mo de la TV avec le socle (le plan choisit selon le profil). **Budget restant pour les Langues : ≈ 70 Mo**, soit ≈ 1,2 Mo par cellule langue × niveau pour 56 cellules (texte + audio court + part des vidéos) : **tenable pour le texte et l'audio ; pour la vidéo, 8 vidéos de 1,5 Mo (couverture croisée) coûtent 12 Mo**. Le jour où les médias existent, l'outil dira exactement ce qui tient. Le rapport par sous-catégorie est imprimé par `select` (octets d'essai / octets complets / éléments).

## 3. Catalogue de bouquets

Un **bouquet** = un ensemble de lots **complets**. Il est **généré** par l'outil depuis le registre (`bundles` dans le manifeste), donc il suit le contenu. Types :

| Type | Identifiant | Contient | Remarque |
|---|---|---|---|
| Classe | `classe-<scope>` (24 aujourd'hui) | `learn:<scope>` + `quiz:<scope>` (+ le lot Quiz équivalent : `class6` ↔ `class-6`, `form1..3` ↔ `form-1..3`, `tle-cd` ↔ `tle`, table `bundleQuizAliases` **à valider**) | de 0,05 Mo (`form1`) à 4,2 Mo (`cm2`) |
| Pack Quiz | `quiz-<lot>` (35) | un lot Quiz | chevauche la classe : **sans double facturation** |
| Langue | `langue-<code>` | tous les niveaux d'une langue (texte + médias) | à la livraison des Langues |
| Tout | `tout` | tous les lots | pour l'abonnement et « tout ouvert » ; 102,5 Mo non compressés aujourd'hui |

Les **matières** coupent les classes : un lot est une **classe** (≈ 0,2 à 4 Mo), donc un achat « par matière » demanderait des lots plus fins (**limite du découpage actuel**, à décider avant de vendre par matière).

**Lots d'essai = sous-ensemble cohérent des bouquets** : chaque lot `<lot>-trial` est dans **tous les bouquets qui contiennent `<lot>`** (champ `bundles` du manifeste, vérifié par `TrialBudget.verify` et par les tests) ; acheter ne change aucun identifiant.

**Champs prévus (aucun prix, aucun prestataire)** : `Product(id, kind = PURCHASE | SUBSCRIPTION, bundleIds, periodDays)`. Le prestataire de paiement appliquera plus tard son propre identifiant de produit vers ces champs.

### Chevauchements et choix achat / abonnement (`EditionPolicy.quote`)
Pour un bouquet, chaque lot est **`OWNED`** (déjà acheté : jamais refacturé), **`IN_SUBSCRIPTION`** (couvert par l'abonnement actif) ou **`PURCHASABLE`** (nouveau). Facturable = tout sauf `OWNED`. Le choix « déjà couvert par mon abonnement » est donc **explicite** : acheter un lot couvert ne débloque rien de plus aujourd'hui, mais **le fait survivre à la fin de l'abonnement**. Restitution propre à la bascule abonnement → achat : `EditionPolicy.expiryImpact` dit ce qui serait perdu à la date de fin (lots, octets) et propose, de façon déterministe, **les bouquets qui le conservent** (celui qui couvre le plus de lots perdus d'abord, puis le plus petit). Le calcul d'un éventuel crédit au prorata est une **décision du propriétaire / du prestataire** (non fixée).

## 4. Le droit d'accès (`Entitlement`)

**Trois droits qui se cumulent.**
1. **Essai** : gratuit, toujours là, jamais dans un jeton (c'est le plancher : tous les lots `-trial`).
2. **Achat à la carte** (`Right.Purchase(produit, bouquets, date)`) : **définitif**.
3. **Abonnement** (`Right.Subscription(produit, bouquets, début, fin, grâce, renouvellement auto)`) : actif jusqu'à la fin, puis **période de grâce hors ligne** (7 jours par défaut, réglable par le serveur) pendant laquelle l'accès continue avec « reconnectez-vous pour renouveler » ; ensuite `EXPIRED`.

**Jeton** (`cbe1.<charge utile base64url>.<signature>`) : texte canonique signé **Ed25519** (même famille de clé que les catalogues), contenant l'appareil, la date d'émission, l'identifiant de clé et la liste triée des droits ; relu **à partir du texte même qui a été signé** (forme canonique obligatoire). **Lié à l'appareil** : un jeton d'un autre appareil donne l'essai (`OTHER_DEVICE`). Falsifié, signé par une autre clé, illisible : essai, avec la raison en français ; l'application **garde le dernier bon jeton**. Horloge : le temps utilisé est `max(horloge, dernier instant vu, date d'émission du jeton)` : **reculer l'horloge n'allonge rien**.

**À la fin d'un abonnement** (`EditionPolicy.reconcile`) : les lots achetés restent ; les lots seulement couverts par l'abonnement sont listés en **rétrogradations** avec un message en français (« repasse en version d'essai ; vos achats restent disponibles ») et **ne sont retirés qu'une fois l'utilisateur prévenu** (`acknowledged`) : **rien n'est supprimé de la TV sans prévenir**. Le contenu rétrogradé redevient l'essai (même identifiants), la progression de l'élève n'est pas perdue.

**Interface « Version d'essai »** (spécification ; l'écran Android sera simple et compilé par le coordinateur) : bandeau « Version d'essai : N Mo sur 100 », liste des classes/matières/langues avec ce qui est inclus et ce qui est complet (cadenas), bouton **« Débloquer »** qui ouvre le choix **achat à la carte ou abonnement** (texte seulement : **aucun paiement n'est implémenté**, le bouton affiche le bouquet, sa taille, ce qui est déjà acquis et ce que l'abonnement couvre déjà ; le prix et le prestataire viendront du propriétaire). Sur la TV : écran de départ (§ 7), puis les mêmes libellés (« Version d'essai », « Version complète », « Tout ouvert (temporaire) »).

## 5. Routes du serveur (spécification, **non implémentées, production non touchée**)

| Route | Rôle |
|---|---|
| `GET /api/v1/lots/catalog?edition=trial\|full&channel=&deviceId=` (alias demandé au cahier : `GET /api/lots?edition=`) | le catalogue **signé** ; `edition=trial` : seulement les lots d'essai (publics pour un appareil enregistré) ; `edition=full` (défaut) : les lots d'essai + **les seuls lots complets que le jeton de cet appareil autorise** |
| `GET /api/v1/lots/{fonction}/{scope}/{version}` | un lot complet exige un **jeton valide couvrant ce lot** : sinon **403** avec la raison (« non inclus dans vos droits ») ; un lot d'essai : sans condition de droit |
| `POST /api/v1/entitlements/redeem` | corps : preuve du prestataire de paiement (**format à décider**) → ajoute un droit (achat ou abonnement) au compte de l'appareil, idempotent par identifiant de preuve |
| `GET /api/v1/entitlements/token?deviceId=` | délivre ou renouvelle le **jeton signé** de l'appareil (authentifié par le jeton d'appareil existant) ; renouvellement automatique avant la fin d'un abonnement |
| `POST /api/v1/entitlements/revoke` (admin) | retire un droit (remboursement, fraude) : le jeton suivant ne l'a plus ; **le contenu déjà livré reste** (§ 9) |
| `GET/POST /api/v1/entitlements/devices` | appareils liés au compte (nombre maximal à fixer par le propriétaire), déliaison, **re-clé** après réinstallation |

Le serveur **n'a qu'à comparer** le jeton aux lots demandés ; les bouquets sont les mêmes que ceux du manifeste. Écrire le code du backend relèvera d'une branche de revue (aucun code serveur n'a été écrit ici).

## 6. Identité d'appareil, activation et lots chiffrés (la TV)

Décisions du propriétaire : identité d'appareil + **code d'activation signé** + **lots chiffrés** + transfert.

### 6.1 Identité sans point unique de défaillance (`DeviceIdentity`)
Certaines TV n'ont pas d'Ethernet ; la TV de référence a un module Wi-Fi USB remplaçable. **Facteurs**, par ordre de stabilité : (1) **mémoire flash** (série + `cid`), (2) **MAC Ethernet** si présente, (3) **MAC Wi-Fi seulement si l'interface est sur un bus soudé** (chemin sysfs sans `/usb`, avec `sdio`/`mmc`/`pci`/`platform` ; **sous `usb` : exclue**, c'est le cas de la TV de référence ; chemin inconnu : exclu), (4) `ro.serialno`, (5) adresse Bluetooth si lisible. Les valeurs « bidon » (`02:00:00:00:00:00`, séries à zéro, `unknown`) sont ignorées. **Chaque facteur est haché séparément** (SHA-256, sel `castbridge-device-v1`, 16 octets).

**Code d'appareil** `XXXX-XXXX-XXXX-XXXX` (alphabet Crockford : pas de I, L, O, U ; on saisit O comme 0 et I/L comme 1) : 1 caractère = **masque des facteurs utilisés**, 14 caractères = 70 bits du hachage de l'ensemble, 1 caractère de contrôle (aucune faute de frappe simple n'échappe). Il **nomme** la TV ; l'activation signe **l'ensemble des empreintes**.

**k parmi n** (`kFor`) : n ≥ 3 → **k = n − 1** (un module remplacé ou une série illisible après mise à jour ne bloque pas) ; n = 1 ou 2 → **k = n** (tolérer une différence ne laisserait qu'un facteur à deviner). **Règle de plus** : si l'activation nomme un facteur soudé (flash, Ethernet), **au moins un facteur soudé doit correspondre** (deux pièces faibles identiques ne font pas la même TV). Identité **faible** (que des facteurs faibles) : signalée à la console, l'activation reste possible. Testé : module Wi-Fi changé accepté ; deux changements refusés ; autre TV refusée.

### 6.2 Activation signée (`Activation`, `cba1.…`)
Texte canonique signé Ed25519 : type (`trial` | `production`), identifiant de clé, nonce, `issuedAt`, **fenêtre d'installation** `notBefore`–`notAfter` (**≤ 1 an**, phase hors ligne), `k`, l'**ensemble des empreintes**, les droits (mêmes types que le jeton du téléphone). La TV n'accepte (`ActivationVerifier`) que : clé **connue** et **non révoquée**, signature valide, pouvoir de la clé suffisant, fenêtre ≤ 1 an, appareil conforme (k parmi n), fenêtre ouverte. **Une fois installée, l'activation reste** (un achat est définitif ; un abonnement porte sa propre date de fin). **Appareil suspect** (signature bonne mais matériel différent) : **retour à l'essai avec déblocage manuel**, jamais un refus net.

**Tant qu'aucune clé n'est installée, l'application n'ouvre aucun contenu** (`TvGate` : `keyInstalled = false`, y compris sans essai). Une **clé d'essai** (`trial`) ouvre l'essai ; la **clé de production** ajoute les droits ; une commande du propriétaire (§ 8) ajoute un déblocage temporaire. À l'expiration d'un déblocage la TV **revient aux droits acquis** (essai + achats + abonnement valide), sans rien supprimer.

### 6.3 Premier lancement de la TV
Un **seul écran de départ, deux branches** :
- **(A) Canal Bluetooth propriétaire** : caché, voir § 8.
- **(B) Identifiant à fournir** : le code d'appareil en **grand format**, pour obtenir la **clé d'essai** ou la **clé de production**.
- **Chemin automatique** : si le téléphone est appairé et en ligne, il récupère la **clé d'essai signée** au serveur et la remet à la TV par Bluetooth, **sans saisie**.
La TV fournit aussi une **demande d'appareil** (`OwnerFrames.deviceInfo` : code + ensemble des empreintes) par Bluetooth ou dans un fichier `Download/CastBridge/device-request` : c'est ce dont la console ou le serveur a besoin pour construire l'activation et les clés de lots (le code seul ne suffit pas pour le chiffrement).

### 6.4 Trois canaux de livraison d'une activation
1. **Téléphone → TV par Bluetooth** (principal) : trame `ACTIVATION` du canal propriétaire, ou service existant.
2. **Fichier** `activation` dans `Download/CastBridge/` de la clé USB, lu au démarrage et à l'insertion (utile hors ligne ; **la clé USB survit à la désinstallation**).
3. **Saisie manuelle** en dernier recours (`CompactActivation`) : 18 octets d'en-tête + signature de 64 octets = 82 octets, en Base32 Crockford, **33 groupes de 5 caractères (4 de données + 1 de contrôle)**, soit **165 caractères**. La TV **désigne le groupe mal saisi** (« Groupe 12 mal saisi »), accepte minuscules, espaces, O/0 et I/1. **Honnêtement : c'est long à taper à la télécommande** (≈ 3 à 5 minutes) ; elle ne porte **pas** de liste de droits (un `setId` du catalogue : 0 = clé d'essai), est **strictement liée** au code d'appareil (pas de k parmi n) et **ne porte aucune clé de lot** : le contenu chiffré demande l'activation complète (canaux 1 ou 2).

### 6.5 Lots chiffrés par TV (`LotKeys`)
Les **lots d'essai ne sont pas chiffrés** (échantillon public). Chaque lot **payant** a sa clé aléatoire (fabriquée à la construction du contenu), **enveloppée** (AES-256-GCM, identifiant et version authentifiés : une clé enveloppée ne peut pas être déplacée vers un autre lot) par une **clé de données propre à la TV**, elle-même enveloppée sous des **clés d'enveloppe (KEK) dérivées des facteurs matériels** (HKDF-SHA256) **pour chaque sous-ensemble de k facteurs** : la tolérance k parmi n vaut donc aussi pour le déchiffrement (testé : un module changé n'empêche pas de lire ; deux changements, si). **Correction de ma proposition précédente** (coordinateur) : une clé du coffre Android **disparaît à la désinstallation**, alors que les contenus lourds (dossier `Download/CastBridge` de la clé USB) doivent survivre ; l'identité et le déchiffrement ne reposent donc **pas** sur une clé du coffre seule. Le coffre ne sert qu'à **signer un défi** (preuve que c'est bien cette installation) ; après réinstallation, une **procédure de re-clé** (même matériel = ré-activation gratuite mais comptée) régénère la clé.
**Effet de sécurité, dit honnêtement** : celui qui connaît les facteurs d'une TV peut dériver sa KEK (cas du clonage) ; le chiffrement empêche de **copier les fichiers** d'une TV vers une autre, pas un attaquant qui possède les identifiants de la TV ni une TV qui a déjà déchiffré (§ 9).

## 7. Horloge et hors ligne (jusqu'à 1 an)
Les TV n'ont pas d'horloge fiable hors ligne. `TvClock` : le temps utilisé est `max(horloge, dernier instant vu, plancher signé)` ; un **retour en arrière** ne raccourcit ni n'allonge rien (il est détecté) ; un **saut en avant de plus de 400 jours** n'est pas cru (il ferait tout expirer s'il s'agissait d'un défaut) **tant qu'un message signé ne le confirme pas** ; la première observation est acceptée telle quelle. Une activation fournit elle-même un plancher (`issuedAt`) : une TV bloquée en 1970 accepte quand même une activation récente. Les **commandes du propriétaire n'utilisent aucune heure murale** (§ 8 : défi émis par la TV). Durée maximale d'une activation hors ligne : **1 an** (vérifiée par la TV).

## 8. Commandes du propriétaire (résumé ; détail dans [OWNER-CONSOLE.md](OWNER-CONSOLE.md))
**Conception retenue** : aucune porte dérobée (l'APK distribué ne contient aucun secret : seulement des **clés publiques**), commande **signée**, **liée à la TV cible**, avec le **défi** que **cette TV** vient d'émettre (usage unique, courte durée sur horloge **monotone**), clé **non révoquée**, pouvoir de la clé suffisant. **Pouvoirs gradués** : `support` (diagnostic, remise à zéro de l'essai), `déblocage` (lots ou bouquets précis, ≤ 30 jours), `tout ouvert` (**≤ 30 jours, réglable à chaque commande, borné par la TV**, renouvelable par une nouvelle commande). **Plusieurs clés publiques acceptées + liste de révocation + rotation + clé de secours hors ligne** dès la première version. Canal Bluetooth **propriétaire** : service dédié (`…0004`), magique `CBTO`, **additif et rétrocompatible**. Le panneau est **caché** sur l'écran de départ (↑ ↑ ↓ ↓ ← → ← → OK en moins de 8 s, constante paramétrable) : **il masque, il ne protège rien**.

## 9. Sécurité honnête : ce qu'on ne peut pas empêcher

- **Un blocage côté application est contournable** (APK modifié, TV en `userdebug`, appareil rooté) : la vérification peut être **retirée** d'un APK modifié. **La vraie protection est le serveur** : il ne livre un lot complet qu'à un appareil dont le jeton couvre ce lot (§ 5), et il peut refuser un appareil, limiter le nombre d'appareils par compte et révoquer un droit.
- **Contenu déjà livré** : un lot complet **déjà téléchargé ou copié** ne peut pas être repris ; un jeton révoqué ne supprime rien chez l'utilisateur. On limite le dégât (lots chiffrés par TV, lots versionnés, jetons courts à renouveler pour l'abonnement) ; on ne l'annule pas.
- **Copie entre TV** : les lots payants chiffrés par TV ne se lisent pas sur une autre TV (sauf si ses identifiants sont connus, § 6.5). Une TV qui a déchiffré garde le texte clair en mémoire/sur disque d'exécution.
- **Téléphone** : un lot complet copié du téléphone vers un autre appareil n'est pas protégé de la même façon ; le jeton est lié à l'appareil mais le contenu du téléphone n'est pas chiffré par appareil dans cette conception (**à décider**).
- **Horloge** : on borne les dégâts (§ 7) sans les supprimer.
- **Phase hors ligne** : sans serveur, **pas de détection de doublon** (une même activation peut être produite pour deux TV de même identité), pas de révocation immédiate d'un droit déjà installé ; voir OWNER-CONSOLE.md.
- **Clé privée de la console** = point unique de défaillance (vol du téléphone + code) : mesures dans OWNER-CONSOLE.md.

## 10. Tests

| Banc | Quoi | Résultat |
|---|---|---|
| `python3 -m unittest discover -s tools/trial-edition` | budget (échec > plafond, sous-catégorie vide), planchers, déterminisme, stabilité des identifiants et des sélections quand le contenu grandit, projection (références filtrées), bouquets, `check` périmé | 17 verts |
| `gradle :core:test --tests 'castbridge.core.lots.*'` | modèle d'édition (JSON/signature inchangés pour `FULL`), nom de fichier, jeton (valide / expiré / grâce / falsifié / autre appareil / mauvaise clé / horloge reculée), politique (essai seul, achat remplace l'essai, fin d'abonnement, rétrogradation avec avertissement, chevauchements sans double facturation, bascule abonnement → achat), planificateur (déterministe, essai → complet), TV (le complet remplace l'essai), synchronisation (`NOT_ENTITLED`), garde de budget JVM | verts |
| `gradle :core:test --tests 'castbridge.core.owner.*'` | identité (USB exclu, soudé inclus, sans Ethernet, placeholders, k parmi n), code d'appareil, activation (acceptée, falsifiée, clé inconnue/révoquée/trop faible, fenêtre 1 an, horloge fausse), saisie manuelle (groupe fautif désigné), commandes (signature, nonce, mauvaise TV, clé révoquée, rejeu, défi expiré sur horloge monotone, 30 jours, pouvoirs, rotation), horloge, séquence cachée (délai, erreur, touches parasites, répétition, imbriquée, limite 5/min, fermeture 60 s, Retour), clés de lots, coffre, compteur d'essais, journal d'audit chaîné, trames Bluetooth | verts |

**Non compilé / non vérifié** : `:sender` et `:receiver` (plugin Android introuvable dans le cloud : banc `:core` seul, voir HANDOFF) ; le serveur (rien écrit) ; Argon2id (la bibliothèque n'est pas dans le JDK : interface `Kdf` + `Pbkdf2Kdf` de repli) ; l'écran, le service Bluetooth propriétaire, la lecture des facteurs matériels (Android) ; le builder de lots d'essai n'a pas été chargé par les vrais consommateurs Apprendre/Quiz.
