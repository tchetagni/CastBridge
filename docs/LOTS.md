# Lots : données hors ligne d'Apprendre et de Quiz

> Code : `android/core/src/main/kotlin/castbridge/core/lots/` (logique pure, testée en JVM), serveur `backend/…/lots/`, téléphone `sender/…/Lots*.kt`, TV `receiver/…/LotsHub.kt`.
> Les noms utilisateur sont **CastBridge** (téléphone) et **CastBridge-TV** (TV).

## 1. Principe

```
serveur ──(signé, Internet du téléphone)──▶ téléphone (100 Mo max) ──(Bluetooth / Wi-Fi, différé)──▶ TV (10 Mo max)
```

- **La TV est supposée hors ligne** (cas courant au Cameroun). Elle n'a **jamais** besoin d'Internet pour les lots, ne contacte **jamais** le serveur pour eux et **ne télécharge jamais** un lot : tout lui est poussé par le téléphone (c'est explicite dans le code : `TvLotApi` n'expose aucune route de téléchargement, `TvLotStore` ne connaît aucune URL).
- **Le téléphone est la seule passerelle** vers le serveur. Il se synchronise quand **lui** a Internet (tâche périodique, réseau non facturé par défaut), que la TV soit là ou non.
- **Livraison différée** : une file persistante par TV (`DeliveryQueue`) compare ce que la TV *devrait* avoir (`LotPlanner`) à ce que la TV *a déclaré* avoir (son manifeste), et livre dès qu'un lien existe (Bluetooth connecté, TV vue sur le réseau, app ouverte), des heures ou des jours plus tard, en reprenant au dernier octet confirmé. Elle survit à la fermeture de l'app, au redémarrage du téléphone et aux coupures. Elle ne bloque jamais l'utilisateur et n'exige pas que la TV soit allumée au moment du téléchargement.
- **Manifeste de la TV** dans les deux sens : la TV déclare à chaque contact ses lots (version, taille, date), son budget restant et sa version de schéma ; le téléphone envoie son plan (lots prioritaires). Additif aux routes existantes.
- **Lots autonomes et versionnés** : une TV absente pendant des semaines reçoit directement la **dernière** version de chaque lot (les versions intermédiaires ne sont jamais rejouées : la file garde une entrée par lot et la remplace).
- **Aucune fonction n'est bloquée** parce que la TV est hors ligne : sans lot reçu, la TV utilise les données de démarrage livrées avec l'APK.
- L'utilisateur voit toujours l'état en français : « Téléchargé sur le téléphone / En attente d'envoi à la TV (la TV n'est pas à portée) / Envoi en cours / Envoyé / À jour sur la TV / Refusé par la TV », avec l'âge des données et ce qui est en attente (`LotStatusText`).
- **Boutique et demande de location** : le cadre des lots (ici) sert aussi au cœur de la Boutique, la vitrine des contenus en location. Voir `docs/STORE.md`.

## 2. Qu'est-ce qu'un lot

Un **lot** = un paquet **homogène, versionné, signé** des données d'**une** fonction (`feature` : `learn` ou `quiz`) pour **un** périmètre (`scope` : une classe ou un niveau : `cm2`, `3e`, `tle-c`, `droit-l1`). C'est un fichier **ZIP** (déjà compressé : le transfert n'ajoute pas de compression, ce qui garde les reprises `Range` exactes) dont le contenu est défini par la fonction (voir §9). Une mise à jour = un lot entier, jamais un diff (un lot est petit : quelques centaines de Ko).

Contrat partagé (`LotApi.kt`, ne pas modifier) : `LotId(feature, scope)`, `LotMeta(id, version, bytes, sha256, title, minAppVersion)`, `LotSource`, `LotConsumer`, `LotBudget` (TV 10 Mo = Apprendre + Quiz, données de démarrage comprises ; téléphone 100 Mo).

Noms : fichier `castbridge-lot-<feature>-<scope>-v<version>.lot` (`LotNames`) ; identifiants `[a-z0-9-]{1,32}`.

## 3. Catalogue signé et signature

`GET /api/v1/lots/catalog?feature=&channel=&deviceId=` renvoie **un catalogue signé par réponse** (choix documenté : tous les lots du canal, ou ceux d'une fonction ; une seule signature à vérifier, un seul appel, et la signature lie le filtre, donc un catalogue « learn » ne peut pas être rejoué comme « quiz »).

```json
{"channel":"stable","feature":"learn","generatedAt":"2026-10-01T13:05:24+01:00",
 "lots":[{"feature":"learn","scope":"cm2","version":3,"bytes":6247,"sha256":"…","title":"Apprendre CM2","minAppVersion":0}],
 "keyId":"21fe31dfa154a261","signature":"<Ed25519 base64>"}
```

Même clé Ed25519 et même style que les mises à jour (`UpdateManifest`, `UpdateKeys`) : la signature couvre un texte canonique reconstruit des champs analysés (pas les octets du JSON) :

```
castbridge-lot-catalog-v1
channel=<canal demandé>
feature=<fonction ou *>
generatedAt=<horodatage>
lot=<feature>|<scope>|<version>|<bytes>|<sha256>|<minAppVersion>|<sha256 hex du titre en UTF-8>   (une ligne par lot, triées feature, scope, version)
```

Serveur : `LotCatalog.java` ; client : `LotManifest.kt`. Un test croisé (`LotManifestTest.verifiesACatalogSignedByTheServer`) vérifie en Kotlin un catalogue produit et signé par le serveur. Changer le format = changer les deux et incrémenter la première ligne.

**Preuve** : quand le téléphone installe un lot, il garde le catalogue signé qui l'a annoncé (`LotStore.proof`). Il l'envoie à la TV avec le lot. La **TV vérifie elle-même** la signature, que le catalogue contient exactement ce lot (version, taille, empreinte, titre), la taille et le SHA-256 du fichier, `minAppVersion`, et refuse les retours en arrière. **Le téléphone n'est jamais cru sur parole.**

## 4. Serveur (`backend/`)

| Route | Rôle |
|---|---|
| `GET /api/v1/lots/catalog?feature=&channel=&deviceId=` | catalogue signé ; pour chaque (fonction, périmètre) la plus haute version **publiée, non retirée, visible par cet appareil** |
| `GET /api/v1/lots/{feature}/{scope}/{version}` | fichier : `ETag` = `"sha256"`, `Range`/`If-Range` (206/416), `If-None-Match` (304), `Cache-Control: public, max-age=31536000, immutable`, `Content-Encoding: identity` ; 404 si non publié, 410 si retiré, 403 appareil bloqué |
| `POST /api/v1/admin/lots` (jeton admin, multipart `file`, `feature`, `scope`, `version`, `title`, `channel`, `minAppVersion`, `rollout`, `publish`, `sha256`) | dépôt : vérifie SHA-256 (si fourni), taille (≤ 10 Mo : il doit tenir sur la TV), `minAppVersion`, validateur de contenu **par fonction** (`LotValidator`, défaut : ZIP lisible, sans chemin sortant ni gonflement) ; **non publié par défaut** |
| `POST /api/v1/admin/lots/{id}/publish` · `/revoke` · `/rollout?percent=` · `DELETE` · `GET` | publier, retirer, déploiement progressif, supprimer, lister |

Une version ne change jamais (409 si elle existe) ; canaux `stable`/`beta` (un appareil beta voit aussi le stable) ; déploiement par appareil comme les APK (hachage stable `deviceId:lot:version`, pas de `deviceId` = déploiements à 100 % seulement). Les routes quiz (`/api/v1/quiz/packs`) et mises à jour restent inchangées. Une fonction ajoute son validateur avec un `@Component implements LotValidator` (il remplace le défaut pour sa `feature`). Migration `V4__lots.sql`. Fichiers : `<storage-dir>/lots/`.

## 5. Téléphone (CastBridge)

### Stockage et éviction (`LotStore`, quota 100 Mo)
- Installation **atomique** : vérification taille + SHA-256, déplacement, index, puis suppression de l'ancienne version. Une mise à jour qui échoue (corrompue, disque) **laisse la version précédente intacte**. Jamais d'installation partielle.
- Une seule version par lot (la dernière). Pas de retour en arrière vers une version plus ancienne.
- Éviction : on **ne retire jamais** les lots des classes choisies (profils actifs) ; sinon le **moins récemment utilisé** d'abord. Si cela ne suffit pas, **rien n'est évincé** et on répond « Stockage plein : il manque X Mo » (`Install.Full`). `fit()` est appelé **avant** de télécharger (pas de bande passante gaspillée).

### Synchronisation (`LotSync`)
- **Première synchronisation** : catalogue + les lots choisis (par défaut la classe du profil) jusqu'au quota. **Ensuite** : seulement les lots dont version/empreinte ont changé, **un lot à la fois**.
- Téléchargement dans un `.part` repris par `Range` + `If-Range` (ETag), nouvelle tentative avec attente croissante (2 s … 60 s), vérification SHA-256 + taille, puis installation atomique.
- **Wi-Fi uniquement** (par défaut) : aucun téléchargement sur réseau facturé (coût des données au Cameroun). Catalogue non signé = ignoré. Lot retiré du serveur (410/404) = arrêt sans insister.
- Tâche de fond : `JobScheduler` (même mécanisme que `ParentalSyncJob`, aucune dépendance de plus ; équivalent d'un `WorkManager` périodique) toutes les 12 h, réseau non facturé (ou quelconque si l'utilisateur l'autorise), batterie non faible, jamais pendant un envoi vers la TV. Respecte le consentement (`ServerLink`/`PhoneConnect` : adresse, jeton d'appareil, canal, appareil bloqué). Aucune télémétrie de plus.

### Planification pour la TV (`LotPlanner`)
Le sous-ensemble qui tient dans `TV_MAX_BYTES` : par priorité (profil actif d'abord, puis les autres), puis le plus petit d'abord (plus de lots pour la même place), puis par nom ; **déterministe** (testé : même plan quel que soit l'ordre des entrées). Les données de démarrage de l'APK sont **comptées d'avance**. Un lot qui ne tient pas est **ignoré avec sa raison** et une **suggestion de ce qu'on pourrait retirer** ; les lots plus petits derrière peuvent encore passer.

### File de livraison différée (`DeliveryQueue`, `LotDelivery`)
Machine à états pure, persistante (`FileQueueStore`), une entrée par (TV, lot) :

```
PENDING ──▶ SENDING(offset) ──▶ SENT ──▶ CONFIRMED
   ▲ lien coupé / TV partie (attente 5 s … 15 min)│
REFUSED (la TV a dit non, raison conservée)  ·  CANCELLED (plus voulu)
```

- **Dédoublonnage** : une version plus récente remplace l'entrée en attente (rien d'intermédiaire n'est envoyé). Le **manifeste de la TV fait foi** : un lot que la TV ne montre plus (remise à zéro, éviction) est remis en file.
- **Reprise** : au redémarrage, `SENDING` redevient `PENDING` ; le point de reprise réel est la taille du `.part` **de la TV** (l'offset de la file n'est qu'un indice).
- **Priorité** : profil actif, puis petit d'abord. **Annulation / « Réessayer »** : explicites. Un refus n'est pas rejoué tant que l'utilisateur ne le demande pas ou qu'une version plus récente n'existe pas.
- `enqueue(tv)` met la file à jour **sans contact** (« En attente d'envoi à la TV (la TV n'est pas à portée) » s'affiche tout de suite) ; `deliver(tv, transport)` vide la file quand un lien existe. Travaille depuis le **stock du téléphone** : le serveur peut être injoignable. Inversement, la TV peut être à portée alors que le téléphone n'a pas Internet.
- **Déclencheurs sans ouvrir l'app** : `LotsTriggerReceiver` (diffusion système `ACL_CONNECTED` du Bluetooth de la TV enregistrée, et démarrage du téléphone ; **exporté** depuis R-31 : l'application Bluetooth d'Android, uid 1002, envoie « appareil connecté » et un récepteur non exporté ne la reçoit jamais ; sans danger, ces deux diffusions sont protégées et `onReceive` ne traite que ces actions), `TvLinkManager` quand une session s'ouvre, tâche périodique de 30 min (réseau local) et bouton « Envoyer à la TV ». Jamais pendant un envoi de fichier vers la TV.

### Transports (`LotTransport`)
- **HTTP** (Wi-Fi LAN, Wi-Fi Direct, ou tunnel API-sur-Bluetooth de `claude/bt-everything` via son URL locale) : `HttpLotTransport`.
- **Bluetooth CBT1** : `Cbt1LotTransport` envoie le lot puis sa preuve signée (`<lot>.json`) comme deux fichiers CBT1 reprenables ; la TV les adopte. CBT1 n'a pas de canal de réponse : la livraison reste `SENT` jusqu'au prochain contact capable de lire le manifeste.
- Une nouvelle liaison n'a qu'à implémenter `LotTransport`.

## 6. TV (CastBridge-TV)

`TvLotStore` : plafond **strict** `TV_MAX_BYTES` (données de démarrage du bundle comprises, `StarterBudget`), vérifié **à l'installation et au démarrage** (`startup()`) ; une installation qui ne tient pas est **refusée en français**, jamais acceptée au-delà. Éviction : seulement les lots **non prioritaires** (la liste vient du téléphone, `POST /api/lots/priority`), les plus anciens d'abord, et seulement si cela suffit. Si le consommateur échoue, la version précédente reste. Les refus sont gardés dans le manifeste (20 derniers) pour que le téléphone dise pourquoi.

Routes (additives, derrière l'authentification existante PIN / jeton de téléphone de confiance) :

| Route | Rôle |
|---|---|
| `GET /api/lots` | manifeste : `schema`, `maxBytes`, `usedBytes`, `starterBytes`, `lots` (version, taille, empreinte, date d'installation), `priority`, `rejected` |
| `POST /api/lots/priority?ids=learn:cm2,quiz:cm2` | le plan du téléphone |
| `GET /api/lots/part?name=` | `{"received":n}` : où reprendre |
| `POST /api/lots/upload?name=&offset=&total=` | un morceau (≤ 512 Ko) **exactement** à l'offset de la TV ; 409 + `{"received":n}` sinon |
| `POST /api/lots/install?name=` | corps = catalogue signé ; la TV vérifie et installe ; 422 + raison en français |
| `POST /api/lots/remove?id=learn:cm2` | retrait choisi par l'utilisateur |

Morceaux en POST plutôt qu'un flux PUT : les routes d'extension de la TV prennent des corps bornés ; chaque réponse confirme l'offset, ce qui est exactement ce qu'une reprise demande. Par Bluetooth, `TvLotStore.adoptFrom` installe les paires (lot + preuve) reçues en fichiers (appelé après chaque transfert Bluetooth et au démarrage).

Écran d'état de la TV (Mises à jour) : budget (« 4,2 Mo utilisés sur 10 Mo, livrés avec l'application : …, reçus du téléphone : … »), âge des données, et la phrase que rien ne demande Internet. Sans lot reçu, les données de démarrage fonctionnent comme avant.

## 7. Budgets

| | Plafond | Comptage |
|---|---|---|
| Téléphone | 100 Mo (`PHONE_MAX_BYTES`) | somme des lots installés |
| TV | 10 Mo (`TV_MAX_BYTES`) | données de démarrage de l'APK (Apprendre + Quiz) + lots reçus |
| Un lot | ≤ 10 Mo (serveur) | doit tenir sur la TV |

`gradle :core:checkStarterBudget` (inclus dans `check`) affiche la taille des données de démarrage de l'APK (paquets Apprendre embarqués + banques de questions du quiz) et **fait échouer le build au-dessus de 10 Mo**. Mesure actuelle : voir la sortie (≈ 0,26 Mo).

## 8. Défaillances

| Situation | Comportement |
|---|---|
| Coupure pendant le téléchargement | `.part` repris (`Range`/`If-Range`), jamais d'installation partielle |
| Lot corrompu / mauvaise taille | refusé, `.part` supprimé, nouveau départ propre au prochain passage |
| Mise à jour qui échoue | version précédente conservée (téléphone et TV) |
| Disque du téléphone plein | « Stockage plein : il manque X Mo », rien d'évincé inutilement |
| Budget de la TV insuffisant | lot ignoré avec sa raison et ce qu'on peut retirer ; décision de l'utilisateur |
| Coupure à n'importe quel octet pendant l'envoi | reprise exacte à la taille du `.part` de la TV, chaque octet envoyé une seule fois (testé octet par octet) |
| Redémarrage / fermeture du téléphone en cours d'envoi | la file est rechargée, reprise au contact suivant |
| TV remise à zéro | son manifeste est vide : la file se reconstruit |
| TV absente des jours, versions successives | seule la dernière version est envoyée |
| Deux TV | files indépendantes |
| Serveur injoignable, TV à portée | livraison depuis le stock du téléphone |
| TV à portée, téléphone sans Internet | idem, rien ne dépend du serveur |
| Catalogue non signé / autre clé | ignoré (téléphone) ; lot refusé (TV) |

## 9. Brancher une fonction (Apprendre, Quiz)

1. Implémenter `LotConsumer` (squelette : `FeatureLotConsumer` : `place` / `unplace` / `list`). `install` doit être **atomique** (déballer et valider à part, puis échanger ; en cas d'échec rendre `false` en laissant la version précédente utilisable) ; `installed()` donne ce qui est utilisable avec la taille sur disque (c'est ce que compte le plafond).
2. TV : `LotsHub.register(consumer)` avant le premier usage (sinon le **shim** `StarterOnlyConsumer` refuse poliment les lots et les données embarquées continuent de fonctionner : comportement actuel inchangé).
3. Serveur : `@Component` implémentant `LotValidator` pour la fonction ; publier avec `POST /api/v1/admin/lots`.
4. Téléphone : lire les données depuis `LotsRuntime.store` (`LotSource`) ; écran « Données » : placer `LotsEntry()` où c'est utile (déjà dans Réglages, onglet Apprendre et liste des Jeux).
5. Décider du `scope` (classe ou niveau) et de ce qui est « prioritaire » (`ProfileNeed` : aujourd'hui les classes cochées sur l'écran « Données » ; à relier aux vrais profils quand ils existent).

Données **embarquées** à migrer : paquets Apprendre `*.learn.zip` (jar du `core`, `embedLearnPacks`) et banques de questions du quiz (`QuizPacks`/`QuizPackRelay`, packs `content/quiz/dist`). Rien n'est réécrit ici : les fonctions migrent ensuite. À noter : la synchronisation directe des questions du quiz par la TV (`syncQuiz`, remplissage des packs) est un héritage antérieur aux lots ; la fonction Quiz doit la remplacer par des lots poussés par le téléphone.

## 10. Sécurité et intégrité

Chaque lot est vérifié (taille, SHA-256, signature Ed25519 du catalogue) avant installation, sur le téléphone **et** sur la TV ; la TV refuse un lot que la clé du serveur ne signe pas. Ni PIN ni jeton dans les journaux ni dans les URL (en-tête d'authentification seulement). Aucun composant exporté de plus (le récepteur de déclenchement et les tâches ne sont pas exportés ; la diffusion `ACL_CONNECTED` est envoyée par le système). Aucune donnée personnelle dans les lots.

## 11. Tests

`gradle :core:test --tests 'castbridge.core.lots.*'` (64 tests : manifeste, stockage, synchronisation, planificateur, TV, transports HTTP et CBT1, file de livraison, scénarios hors ligne) ; `cd backend && ./mvnw -q test` (`LotsApiTest`). Non vérifié hors JVM : voir `docs/HANDOFF.md`.

## 12. Lots Langues : du serveur à la TV (2026-10-02)

La fonction `langues` (lots texte ≤ 3 Mo, **libres** CC BY-SA, jamais scellés ni loués) suit les mêmes tuyaux que `learn` et `quiz`. Deux chemins :

1. **Serveur → téléphone → TV** (le téléphone a Internet) : le téléphone liste le catalogue signé (écran « Données hors ligne » > Langues : profil, ou liste « Disponibles sur le serveur » lot par lot), télécharge avec reprise (Range + If-Range), vérifie signature du catalogue, taille et SHA-256, **puis le contenu du lot avec les contrôles mêmes du consommateur de la TV** (`LangLotConsumer.verifyContent` : un lot que la TV refuserait n'est jamais gardé), le range dans `LotStore` (quota 100 Mo du téléphone : le quota média de 500 Mo n'est pas codé, les lots média ne sont pas livrés), puis le pousse à la TV par `/api/lots/upload` + `/api/lots/install` avec le catalogue signé comme preuve. Aucune route nouvelle.
2. **Serveur → TV** (la TV a elle-même Internet) : bouton **« Mettre à jour les lots Langues »** de l'écran Langues, affiché seulement quand le système dit que la TV a Internet, **jamais en tâche de fond** (pas de planification, pas d'écouteur : la TV reste hors ligne tant qu'on n'appuie pas). `TvLotFetcher` (core) : catalogue filtré `feature=langues` ; **signature vérifiée d'abord** avec `UpdateKeys` (clé de production) ; ne garde que les entrées `langues` éditions complètes, nom valide, ≤ 3 Mo, absentes ou plus anciennes sur la TV ; jamais `learn`, `quiz`, ni un lot réservé ; téléchargement HTTPS seulement (`SecureHttpLotRemote` : aucune redirection suivie, délais fermes, catalogue ≤ 1 Mo), reprise, 3 essais, délai de 3 min par lot ; vérifie taille et SHA-256 puis `TvLotStore.installReceived` avec le catalogue signé en preuve (mêmes contrôles qu'un envoi du téléphone) ; **n'évince jamais** les données du téléphone pour faire de la place (lot sauté avec message). Erreurs en français : pas d'Internet, serveur injoignable, signature invalide, place insuffisante. Essai : l'écran Langues reste fermé (`TrialPolicy`) et le bouton le refuse aussi.

Publication par le propriétaire : voir `docs/LANGUES.md` § 15 (`tools/langues/publish_lots.py`, simulation par défaut). Vérifier : `curl -s 'https://bridge.sti-cm.com/api/v1/lots/catalog?feature=langues'` (catalogue signé, entrées `langues`) puis `curl -sI https://bridge.sti-cm.com/api/v1/lots/langues/<scope>/<version>` (200, `ETag`).
