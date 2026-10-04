# Gestion des licences : administration du serveur en ligne

> Module « licences » de `backend/` (branche `claude/license-admin`). **Éteint par défaut.** Aucun prix, aucun prestataire de paiement, aucune clé dans le dépôt.
> Il parle le **format filaire publié** (`docs/ACTIVATION-FORMAT.md` : enveloppe signée `cbx1` pour les activations, les commandes, les ordres et les listes de révocation, registre d'événements signés) et le rejoue sur les **vecteurs** de `tools/activation/test-vectors.json`.
> Décisions du propriétaire respectées : une clé de signature par outil ; **la clé du serveur ne transfère pas et n'ouvre pas « tout »** ; règle commune **identifiant de licence + décompte des postes** ; essai **émis manuellement par défaut** ; contenus **à la carte ou par abonnement** ;
> phase hors ligne jusqu'à 1 an avec **import et réconciliation** des journaux émis par le bureau et le téléphone.

## 1. Ce que fait le module (en bref)

| Besoin | Où |
|---|---|
| Tableau de bord (licences actives, expirant sous 30 jours, postes utilisés, essais délivrés, doublons suspects, alertes) | `/admin/licenses` |
| Liste (recherche, filtres état/client/bouquet/expiration, tri, export CSV) | `/admin/licenses/list` |
| Fiche licence (droits, postes avec première/dernière vue, émissions, transferts, historique, actions) | `/admin/licenses/{licence}` |
| Formulaire d'émission (demande d'appareil + licence → activation à copier / télécharger / QR) | `/admin/licenses/issue` |
| Recherche par code d'appareil | `/admin/licenses/device` |
| Clients (minimum de données, export et effacement RGPD) | `/admin/licenses/clients` |
| Catalogue de produits et de bouquets | `/admin/licenses/products` |
| Registre : import/export du fichier d'événements signés, rapport de réconciliation, décision manuelle | `/admin/licenses/registry` |
| Journal d'audit chaîné, filtrable, avec vérification | `/admin/licenses/audit` |
| Comptes, rôles, double authentification | `/admin/licenses/security` |
| API pour scripts (jeton Bearer existant) | `/api/v1/admin/licenses/**` |
| Routes publiques minimales (éteintes par défaut) | `GET /api/v1/revocations`, `GET /api/v1/entitlements/me` |

Même sécurité que le reste de `/admin` : session + CSRF, CSP stricte (aucun script ni style en ligne), thème sombre, français, utilisable sur téléphone.

## 2. Guide du propriétaire

### 2.1 Avant de commencer (une seule fois)
1. Le module est **éteint** : rien ne change tant que `CASTBRIDGE_LICENSES_ENABLED` n'est pas `true`. Suivre d'abord la procédure du § 8 (sauvegarde, préproduction).
2. Activer **votre double authentification** : *Licences > Sécurité > Activer*, scanner le QR dans FreeOTP / Aegis / Google Authenticator, saisir le code. Tant que ce n'est pas fait, votre compte propriétaire **peut lire mais ne peut rien modifier**
   (réglable : `CASTBRIDGE_LICENSES_REQUIRE_TOTP`).
3. Créer, si besoin, un compte **Support** (lecture + réémission d'un poste existant) et un compte **Lecture seule**.
4. Déclarer les **clés publiques de vos outils hors ligne** (bureau, téléphone) pour pouvoir importer leur registre : `CASTBRIDGE_LICENSES_TRUSTED_KEYS="desktop:<clé publique base64>:ISSUE_TRIAL+ISSUE_PRODUCTION+TRANSFER+REVOKE+REGISTRY,phone:<base64>:ISSUE_TRIAL+ISSUE_PRODUCTION+TRANSFER+REVOKE"`
   (clé publique brute de 32 octets en base64 ; `kid` = 16 premiers chiffres hexadécimaux de SHA-256 de cette clé ; **les portées sont obligatoires** : une entrée sans portée est ignorée, jamais « toutes les portées par défaut »).
5. Créer les **produits** (catalogue), puis un **client**.

### 2.2 Créer une licence
*Licences > Nouvelle licence* : client, type (payante ou **essai**), nombre de postes, début, fin (vide = sans fin, définitif), période de grâce (14 jours par défaut ; au plus 30 jours sont portés dans l'abonnement), plafond de transferts par an (2 par défaut), produits inclus.
L'identifiant `lic-xxxxx-xxxxx` (minuscules, chiffres, tirets : le format des activations) est généré ou saisi ; « trial » et les mots des pages sont réservés. Une licence d'**essai** (`essai-xxxxx-xxxxx`, 1 poste) délivre une **clé d'essai** (`license=trial`, aucun droit) **sous votre contrôle**, jamais automatiquement
(`CASTBRIDGE_LICENSES_TRIAL_ISSUANCE=manual`, seule valeur livrée active ; aucune route d'essai automatique n'existe).
**Produits et droits** : un produit est **à la carte** (droit `purchase`, définitif) ou **abonnement** (droit `subscription`, qui finit à la fin du produit sinon à la fin de la licence ; sans date de fin, l'émission est refusée). Un produit porte des **bouquets de contenu** (identifiants du manifeste, ex. `classe-cm2`, `tout` ; à défaut, l'identifiant du produit) et, à titre d'information, les lots couverts. Aucun prix.

### 2.3 Activer une TV (ou un téléphone)
1. L'appareil fournit sa **demande d'appareil** : le texte `code=XXXX-XXXX-XXXX-XXXX` / `k=…` / `factor=TYPE|empreinte` (une ligne par facteur) affiché ou transmis par Bluetooth (trame `DEVICE_INFO`). **Le code seul ne suffit pas** : l'activation signe l'ensemble des empreintes de facteurs.
   Le serveur ne voit jamais de valeur brute (numéro de série, adresse MAC) : seulement des empreintes. La demande est **vérifiée en entier** (le code doit dériver des facteurs, `k` aussi) ; une demande altérée est refusée avec un message clair.
2. *Licences > Émettre* : licence (**facultative** : laissée vide ou « auto », le serveur crée dans la même transaction une licence `lic-` + 10 chiffres hexadécimaux aléatoires, 1 poste, plafond de transferts par défaut, et l'affiche sur la page de résultat ; un identifiant existant réactive ou prend un nouveau poste), durée de la clé (production = **version complète** : aucun droit, seulement la durée, illimitée ou 1 à 3660 jours), type d'appareil (TV ou téléphone), demande d'appareil (et, si besoin, fenêtre d'installation : 30 jours par défaut, 1 à 366) → **Émettre**. Le serveur prend **un poste** sous verrou, signe l'activation `cba1.…` avec sa clé et vous la montre **une seule fois** : texte à copier, **fichier `activation`** à déposer dans `Download/CastBridge/` de la clé USB, ou **QR**.
   Elle n'est **enregistrée nulle part** (seule son empreinte l'est) ; l'émission est inscrite au registre comme événement signé.
3. **Le même matériel garde son poste** : au moins *k* facteurs sur *n* identiques (donc aussi après le remplacement d'un module, par exemple le Wi-Fi), même poste, **aucun poste consommé**, quel que soit l'outil qui répond. Redemander l'activation (TV réinstallée, fichier perdu) : cocher **Réémission**, ou « Télécharger l'activation » sur la fiche.
   Tant que l'activation précédente peut encore s'installer (fenêtre d'au moins un jour restante) et que rien n'a changé (droits, matériel, durée), le serveur redonne **exactement la même** (même `nonce`, même signature) ; sinon il en émet une nouvelle.
4. Un **téléphone** est un poste distinct d'une TV (même licence, autre type d'appareil).
5. Plus de poste libre ? « Plus de poste disponible » : libérez un poste, augmentez le quota, ou faites un **transfert avec le bureau ou le téléphone propriétaire** (le serveur ne signe jamais un transfert) puis importez le registre (§ 2.5).
6. Fenêtre d'installation : l'activation ne peut s'**installer** que pendant sa fenêtre ; une fois installée, elle reste (un achat est définitif, un abonnement porte sa propre fin).

### 2.4 Libérer un poste, prolonger, suspendre, révoquer
Sur la fiche. Toute action **destructrice** (suspendre, révoquer, libérer un poste, réduire le quota ou raccourcir la fin, désactiver un produit, effacer un client, retirer un TOTP, décider un conflit, révoquer une clé) passe par une page de **confirmation** avec **motif obligatoire** (3 caractères au moins) et une case à cocher ;
le serveur refuse un motif vide même si la page est contournée.
- **Suspendre / réactiver** : réversible ; plus aucune activation n'est émise pendant la suspension.
- **Libérer un poste** et **révoquer une licence** : le poste (ou tous les postes de la licence) entre dans la **liste de révocation signée** `cbx1` (type `revocation`) (`licence|poste|date`) que les appareils récupèrent en ligne : une activation émise **avant** cette date est révoquée, une activation émise **après** (même poste, après libération) est valide.
  La révocation d'une licence est **définitive**. Chaque révocation est aussi inscrite au registre (événement `revoke` signé) pour les outils hors ligne.
- **Prolonger** : nouvelle date de fin ; une licence expirée prolongée au-delà de maintenant redevient active.
- **Expiration et grâce** : après la fin, la licence est « en grâce » (14 jours par défaut) : seule la **réémission d'un poste existant** est possible ; ensuite elle est « expirée ».
- **Révoquer une clé de signature** (`POST /api/v1/admin/licenses/keys/revoke {kid, reason}`) : la clé entre dans la liste de révocation `cbx1` et le registre ; ses événements ne sont plus acceptés à l'import.

### 2.5 Importer le registre hors ligne (bureau et téléphone propriétaire)
Pendant la phase hors ligne, le bureau et le téléphone émettent sans serveur. Chacun exporte son **registre** : un fichier JSON d'**événements signés** (`license`, `issue`, `transfer`, `revoke`). Pour synchroniser :
1. *Licences > Registre > Importer*, cocher d'abord **Simulation** : le **rapport** montre ce qui serait appliqué, ce qui est déjà connu, ce qui est **refusé** et les **conflits**, sans rien modifier. Relancer sans la case pour appliquer.
2. L'import est une **union idempotente** : un événement déjà connu (même identifiant) ne change rien ; réimporter le même fichier, ou un fichier qui en recoupe un autre, est sans effet et sans erreur.
3. **Chaque événement est vérifié** (dans l'ordre du format) : clé connue de `TRUSTED_KEYS` (`UNKNOWN_KEY`), non révoquée (`REVOKED_KEY`), signature (`BAD_SIGNATURE`), **portée** exigée (`KEY_NOT_ALLOWED`), champs valides (`MALFORMED`). Un événement refusé n'est **jamais appliqué ni gardé**.
   Une entrée dont l'identifiant ne correspond pas à son texte est ignorée (jamais crue).
4. **Conflits** (le format les applique silencieusement ; le serveur, par défaut, les met en attente de **votre décision** avec motif) :
   - `UNKNOWN_LICENSE` : licence ou poste inconnu (format : événement rejeté). Créez-la d'abord, puis « Accepter » ; ou « Rejeter ».
   - `OVER_QUOTA` : plus d'appareils que de postes (format : avertissement, jamais caché). « Accepter » **relève le quota** (tracé).
   - `TWO_TOOLS` : **le même matériel** a reçu deux identifiants de poste de deux outils (format : le plus ancien est gardé, l'autre devient son alias, compté une fois). « Accepter » fait cette fusion.
   - `TRANSFER_CAP` : plafond de transferts par an dépassé (format : rejeté). « Rejeter » garde la trace d'un transfert refusé ; « Accepter » est une exception délibérée.
   La case **« résolution automatique »** (`policy=auto` de l'API) applique le format tel quel (fusion des doublons, quota relevé avec avertissement, plafond et licence inconnue rejetés) : à réserver aux scripts.
5. **Le serveur ne signe jamais un transfert** : il **enregistre** ceux du registre (portée `TRANSFER` : bureau et téléphone), les compte contre le plafond de la licence (**0 par an par défaut** depuis le 2026-10-04 : une licence ne se transfère pas, décision du propriétaire ; réglable licence par licence, les licences existantes gardent leur plafond), fait suivre le poste au **nouveau matériel** (même identifiant de poste) et révoque l'ancienne activation à la date du transfert.
   Un événement `transfer` signé par la clé du serveur (qui n'a pas la portée) est refusé (`KEY_NOT_ALLOWED`).
6. **Exporter** (*Registre > Télécharger*) donne l'ensemble des événements du serveur (les siens, signés par sa clé, et ceux importés, signés par leurs auteurs), que les outils peuvent importer à leur tour : fusion = union, sans doublon, indépendante de l'ordre.
   **Quota et plafond** : le format ne connaît que le *premier* événement `license` d'une licence ; modifier le nombre de postes ou le plafond sur le serveur **ne se propage pas** aux outils hors ligne (à répercuter à la main).

### 2.6 Droits d'un client (RGPD)
- **Export** : fiche client > « Exporter ses données (JSON) » (client, licences, postes, émissions ; jamais de texte d'activation, il n'est pas conservé).
- **Effacement** : nom, contact et notes effacés définitivement ; les **postes sont anonymisés, pas supprimés** (le code d'appareil devient `ANON-…`, les empreintes de facteurs et les observations sont supprimées) : le **décompte des postes reste exact**.
  Les événements du registre qui portent les empreintes du matériel (`issue`, `transfer`) ne sont plus gardés ni exportés par le serveur (une pierre tombale empêche leur retour à un import) ; **les outils hors ligne gardent leurs propres copies signées** (à traiter de leur côté).
  Limites assumées : un appareil anonymisé ne peut plus être réémis depuis le serveur (il refait une demande, et compte comme un nouvel appareil) ; l'identifiant de poste (16 chiffres hexadécimaux dérivés d'un hachage) reste, car il est déjà dans l'activation que l'appareil détient.
  Le journal d'audit ne contient jamais de nom ni de contact : l'effacement ne le touche pas.

## 3. Sécurité

### 3.1 Rôles (appliqués côté serveur, testés par matrice)
| | Propriétaire (avec TOTP) | Propriétaire sans TOTP | Support | Lecture seule |
|---|:-:|:-:|:-:|:-:|
| Tableau de bord, listes, fiches, CSV, recherche appareil, registre (vue) | oui | oui | oui | oui |
| Journal d'audit, vérification | oui | oui | oui | non |
| Réémettre l'activation d'un poste **existant**, télécharger le fichier | oui | oui | oui | non |
| Émettre pour un **nouvel appareil** (consomme un poste) | oui | non | non | non |
| Créer/modifier licences, clients, produits, libérer, suspendre, révoquer, prolonger | oui | non | non | non |
| Import/export du registre, décisions de conflits | oui | non | non | non |
| Export et effacement RGPD, comptes et rôles | oui | non | non | non |

Le **jeton d'administration** des scripts (`/api/v1/admin/…`) vaut « propriétaire » (c'est un secret long, comparé en temps constant). Un compte inconnu de la base n'a **aucun** rôle. Le dernier propriétaire ne peut pas être rétrogradé.

### 3.2 Double authentification (TOTP, sans service externe)
RFC 6238 (HMAC-SHA1, 6 chiffres, 30 s, tolérance ±1 pas). Le secret est **chiffré au repos** (AES-256-GCM, clé `license-totp.key` du dossier des secrets) ; chaque code **ne sert qu'une fois** (rejeu refusé) ;
un mauvais code compte comme un mauvais mot de passe pour le **verrouillage** existant (5 échecs = 15 minutes) ; la limite de débit par adresse couvre maintenant aussi les `POST /admin/licenses/**`.
Téléphone perdu : un autre propriétaire retire le TOTP du compte (motif, audité). Si le **seul** propriétaire perd son téléphone : voir « Dépannage » (§ 10).

### 3.3 Journal d'audit chaîné
Chaque ligne porte `SHA-256(empreinte précédente | ses champs)` (ou un HMAC si `license-audit.key` existe : alors, sans cette clé, on ne peut pas refabriquer une chaîne valide). La ligne de tête enregistre le dernier numéro et la dernière empreinte :
**modification, suppression au milieu, suppression de la fin, réordonnancement** sont détectés, avec le numéro de la ligne fautive. *Audit > Vérifier l'intégrité*, ou `GET /api/v1/admin/licenses/audit/verify`. **Notez l'empreinte de tête ailleurs**
(la sauvegarde la journalise) : c'est l'ancrage extérieur de la chaîne. Choisir (ou non) la clé HMAC **avant** la première écriture et ne plus la changer.
Les dates du journal sont à la seconde (le résultat ne dépend pas de la précision de la base).
Le journal ne contient **ni secret, ni activation complète (seulement 12 chiffres de son empreinte), ni nom, ni contact, ni code d'appareil entier** (`ABCD-****`).

### 3.4 Portée de la clé du serveur (code, pas configuration)
La clé du serveur a les portées `ISSUE_TRIAL`, `ISSUE_PRODUCTION`, `REACTIVATE`, `REVOKE`, `REGISTRY` et `POLICY` du format (celles de la clé « server » des vecteurs ; `POLICY` = ordres différés). Elle **ne peut ni transférer (`TRANSFER`), ni « tout ouvrir » (`COMMAND_OPEN_ALL`), ni donner une commande propriétaire** :
`ScopedActivationSigner.SERVER_SCOPES` est une constante du code, immuable, vérifiée **avant** toute écriture (aucun poste n'est pris pour une demande interdite) et avant la signature, quel que soit l'appelant (page, API, import) ; testée
(`LicenseScopeAndAuditTest`, `WireFormatVectorsTest` dont le vecteur `build-refuse-scope`). Côté import, un événement signé par la clé du serveur d'un type qu'elle n'a pas le droit de signer est refusé (`KEY_NOT_ALLOWED`).
La clé est lue **dans le dossier des secrets** (`license-signing.key`), jamais dans l'image ni les journaux.

### 3.5 Détection d'abus (alertes, **jamais de blocage automatique**)
Tableau de bord : même code d'appareil sur plusieurs licences actives ; même code vu depuis ≥ 2 téléphones ou ≥ 3 adresses IP en 24 h (seule une empreinte tronquée de la source est gardée, 90 jours) ; postes au-dessus du quota ou dépassements importés en attente ;
rafale d'émissions (≥ 10 en 10 minutes par licence) ; transferts répétés (plafond atteint, ou 2 en 30 jours). La décision (suspendre, libérer, révoquer) reste la vôtre.

### 3.6 Rotation de la clé serveur
À faire une fois par an, ou **tout de suite** si la clé privée (`license-signing.key`) a pu fuiter. Règle d'or : **la nouvelle clé est connue des appareils avant d'émettre quoi que ce soit avec elle**. Aucune de ces étapes n'est automatique.
1. **Préparer** : sauvegarde récente de la base et du dossier des secrets (§ 8). Noter la clé actuelle : `GET /api/v1/admin/licenses/signing` (champs `publicKey`, `kid`). Garder le serveur en émission normale avec l'ancienne clé.
2. **Générer la nouvelle clé hors ligne** (sur le bureau, pas sur le serveur de production) : même procédure qu'au § 8.1 (fichier de 32 octets aléatoires, `chmod 600`). Relever sa clé publique et son nouveau `kid` (16 premiers chiffres hexadécimaux de SHA-256 de la clé publique). Ne jamais mettre la clé privée dans un dépôt, un courriel ou un journal.
3. **Distribuer la clé publique d'abord** : l'ajouter à l'anneau de clés de confiance des TV et des téléphones (`KeyRing`/`TrustedKey`, **mêmes portées que l'ancienne** : `ISSUE_TRIAL`, `ISSUE_PRODUCTION`, `REACTIVATE`, `REVOKE`, `REGISTRY`, `POLICY`, ni `TRANSFER` ni `COMMAND_OPEN_ALL`), sans retirer l'ancienne : nouvelle APK TV (CastbridgeTV) et téléphone (CastBridge). Ajouter aussi la clé aux outils du bureau et du téléphone propriétaire.
4. **Période de chevauchement** : publier les APK, attendre que le parc ait majoritairement mis à jour (tableau de bord `/admin` : versions installées ; compter au moins 30 jours, plus si des TV restent hors ligne). Les TV pas encore à jour ne reconnaîtront pas les activations de la nouvelle clé : ne pas basculer avant.
5. **Basculer l'émission** : arrêter l'écriture (fenêtre calme), déposer la nouvelle clé comme `license-signing.key` (ancienne copiée hors ligne sous un autre nom), redémarrer le serveur, vérifier `GET /api/v1/admin/licenses/signing` : le `kid` est le nouveau. Émettre une licence d'essai de test et l'installer sur une TV déjà mise à jour.
6. **Révoquer l'ancienne clé** : produire une révocation du `kid` **ancien** signée par une clé **autre que celle qui est révoquée** (la clé de secours hors ligne, portée `REVOKE`, jamais la clé compromise : elle ne prouverait rien), l'importer dans le registre (`POST /api/v1/admin/licenses/keys/revoke {kid, reason}` côté serveur avec le nouveau kid ayant la portée `REVOKE`, ou import du registre signé par la clé de secours, § 2.5). La liste est au format `cbx1` (l'ancien `cbr1` n'existe plus, § 6).
7. **Vérifier** : `GET /api/v1/revocations` doit rendre une ligne `cbx1.…` plus récente qui contient l'ancien `kid` ; une TV à jour l'applique à sa prochaine connexion ; un événement signé par l'ancienne clé est refusé à l'import (`REVOKED_KEY`).
8. **Archiver** : ancienne clé privée chiffrée et hors ligne (deux supports, coffre), jamais supprimée tant que des activations qu'elle a signées existent ; noter dans le journal : dates, anciens et nouveaux `kid`, qui a fait quoi. Retirer l'ancienne clé de l'anneau des appareils dans une APK ultérieure, pas avant la fin de validité des activations qu'elle a émises.
**Si la clé a fuité** : sauter la période de chevauchement pour l'émission (étapes 5 puis 6 dès que possible), prévenir les clients concernés et réémettre leurs activations avec la nouvelle clé.

## 4. Format filaire et modèle de données

### 4.1 Ce que le serveur écrit et lit (docs/ACTIVATION-FORMAT.md)
- **Enveloppe `cbx1`** (un seul codec pour tout message signé, `Envelope`) : `castbridge-envelope-v1`, `type`, `kid`, `seq`, `nonce`, `issuedAt`, `notBefore`, `expiresAt`, `target` (`any`, `device` + `k` + facteurs, `license:<id>`, `group:<id>`), `--`, corps du type ; Ed25519 sur tout le texte ; jeton `cbx1.<base64url>.<base64>`. Décodage **strict** : le texte est reconstruit à partir des champs lus et doit être identique octet pour octet, sinon le jeton est refusé. Un type inconnu est refusé (`UNKNOWN_TYPE`).
  **Numéro de séquence par clé** : celui de l'activation est la **date d'émission** (`issuedAt`, défaut du format) ; le serveur ne l'émet jamais en arrière (la date d'émission d'une nouvelle activation n'est jamais antérieure à la précédente de la même clé), car un appareil refuse une activation **plus ancienne** que la dernière vue pour la clé (la **même** est acceptée : fichier réinstallé). Un ordre exige, lui, un `seq` **strictement croissant**.
- **Activation** (type `activation`, `WireActivation`, `Ed25519ActivationSigner`) : corps `kind`, `subject`, `license`, `seat`, droits triés. Port **octet pour octet** de `ActivationIssuer` (cœur Kotlin), vérifié par `WireFormatVectorsTest` sur les **9 vecteurs `build-activation`** (essai, production achat + abonnement, téléphone, refus : fenêtre de 400 jours, fenêtre nulle, « tout ouvert » avec la clé serveur, « tout ouvert » de 31 jours, production sans droit, code mal formé), les codes d'appareil, le k parmi n et la base32.
  **Lecture** : `EnvelopeVerifier` (port de `ActivationVerifier`, `OrderVerifier`, `RevocationNotice.verify`) rejoue les **29 vecteurs `activation`**, les **20 `order`** et les **5 `revocation`** avec les mêmes raisons (`EnvelopeVectorsTest`). Le serveur n'installe pas d'activation : ce vérificateur sert aux outils de registre et aux tests de bout en bout.
- **Autres types** (`EnvelopeIssuer`, portées contrôlées dans le code) : `command` (vecteur `build-command`, jamais avec la clé serveur : aucune portée `COMMAND_*`), `order` (portée `POLICY`, vecteurs `build-order` dont le refus), `revocation` (portée `REVOKE`, `target=any`, fenêtre de 366 jours, vecteur `build-revocation`). **Même octets** que le cœur et la référence Python (`EnvelopeVectorsTest`).
- **Registre** : événements `castbridge-licence-event-v1` (`license`, `issue` avec `seq`, `transfer`, `revoke`), identifiant `hex(SHA-256(kid|texte)[0:8])`, fichier `castbridge-licence-registry-v1`. Les **10 vecteurs `licence`** (réutilisation de poste, plus de poste, doublon matériel, sur-émission, transfert, plafond, transfert l'an suivant, clé serveur sans portée de transfert, falsifié et inconnu, révocation de poste)
  sont rejoués par `LicenceVectorsTest` **à travers l'import du serveur** (politique `auto`) : postes, postes utilisés, transferts, doublons fusionnés, rejets, avertissements, postes révoqués et plans de ré-activation sont ceux attendus. Une clé qui n'a que `REACTIVATE` peut réémettre le poste d'un matériel qui en a déjà un, **jamais en créer un** (`KEY_NOT_ALLOWED`) ; un transfert n'est **jamais signé** par le serveur, seulement enregistré depuis le registre hors ligne.
- **Bout en bout avec le cœur** : `tools/activation/server-issued.json` contient des activations, une liste de révocation et un ordre **produits par le code du serveur** (clé de TEST « server » des vecteurs). `ServerIssuedVectorsTest` (backend) vérifie que le serveur produit exactement ces octets et que le port Java du vérificateur les accepte ; `ServerIssuedActivationTest` (module `core`, Kotlin) les fait vérifier par le **vrai** `ActivationVerifier`, `RevocationNotice` et `OrderVerifier`. Régénérer : `CASTBRIDGE_WRITE_SERVER_ISSUED=1 mvn test -Dtest=ServerIssuedVectorsTest`.
- **Ancien format `cba1` / `cbr1`** : **supprimé** (aucune activation `cba1` n'a été émise en production : le module est éteint par défaut et n'a jamais tourné). Le serveur ne lit ni n'écrit plus que `cbx1` ; un jeton `cba1` est refusé comme illisible (testé). Si une activation `cba1` de test circule encore, la réémettre.
- **Sans vecteur dans le format** : la clé compacte (§ 4.1 du format) n'est **pas** émise par le serveur.
- Les **valeurs brutes** du matériel (n° de série, MAC) ne parviennent jamais au serveur : la normalisation et le calcul des empreintes (§ 1.1 du format) sont faits par les applications ; le serveur reçoit les empreintes et vérifie la cohérence code ↔ facteurs ↔ k.

### 4.2 Tables (migrations Flyway V50 à V52, rétrocompatibles)
`lic_client`, `lic_product` + `lic_product_bundle` (bouquets de contenu portés par les droits) + `lic_product_lot` (lots couverts, information), `lic_license` (`license_id` unique, client, type, état, **postes**, début, fin, grâce, plafond de transferts),
`lic_license_product` (droits), `lic_seat` (poste : `seat_id` du format, type d'appareil, **code d'appareil**, empreintes de facteurs hachées, k, première/dernière vue, état) + `lic_seat_alias` (doublons fusionnés), `lic_issuance` (`kid`, `nonce`, fenêtre d'installation, émetteur, canal, **empreinte** du jeton : jamais le jeton, jamais une clé),
`lic_transfer`, `lic_revocation` (postes et clés), `lic_event` (**le registre**), `lic_sighting`, `lic_audit` + `lic_audit_head`, `lic_ledger_import`, `lic_conflict`, et 4 colonnes sur `admin_user` (`role`, `totp_enabled`, `totp_secret_enc`, `totp_last_step` ; les comptes existants deviennent propriétaires).
Les migrations n'ont jamais été déployées : leur contenu a été aligné sur le format avant toute mise en production.

**Décompte des postes sûr en concurrence** (jamais plus de postes que permis, même sous requêtes simultanées), à trois niveaux :
1. chaque opération sur les postes commence par le **verrou de ligne de la licence** (`SELECT … FOR UPDATE`) dans une transaction **READ COMMITTED** (avec le REPEATABLE READ par défaut de MySQL, un instantané figé pourrait masquer un poste validé par la transaction attendue) ;
2. le poste actif prend le **plus petit numéro libre de 1 à quota** ; contrainte `UNIQUE (licence, numéro)` ;
3. contrainte `CHECK` : `ACTIVE` ⇔ numéro renseigné (1..1000) ; `UNIQUE (licence, seat_id)` : une seule ligne par poste, réactivée à la place d'être dupliquée.
Tests : 16 fils simultanés pour 3 postes = exactement 3 ; 8 fils pour le même appareil = 1 poste, 1 émission, la même activation ; contraintes testées en contournant le service.

## 5. API d'administration (`/api/v1/admin/licenses/**`, `Authorization: Bearer <jeton>`)
JSON ; listes paginées `{"items":[…],"page":0,"size":50,"total":n}` (taille ≤ 100) ; erreurs `{"status","erreur","message","details","chemin","date"}` en français.

| Méthode et chemin | Fonction |
|---|---|
| `GET /` (`q,state,client,product,expiringDays,sort,dir,page,size,format=csv`) · `POST /` | liste · création |
| `GET /{licence}` | fiche complète (postes, émissions, transferts, historique) |
| `POST /{licence}/suspend` `resume` `revoke` `{reason}` | états (motif obligatoire) |
| `POST /{licence}/extend {endAt,reason}` · `/seats {seats,reason}` · `/settings {graceDays,transferCap}` · `/products {productId,endsAt}` | prolonger, postes, réglages, produit |
| `POST /{licence}/seats/release {seatId,reason}` | libérer un poste (révoque) |
| `POST /{licence}/activations {deviceRequest,subject?,kind?,productIds?,windowDays?}` | émettre ; `kind` autre que `PRODUCTION`/`TRIAL`, ou `productIds:["*"]` → **403** |
| `POST /{licence}/reissue {seatId}` ou `{deviceRequest,subject?}` | réémettre (même activation tant qu'elle est installable) |
| `POST /keys/revoke {kid,reason}` | révoquer une clé de signature |
| `GET /devices/{code}` | licences et postes d'un code d'appareil |
| `GET/POST /clients` · `GET/PUT /clients/{id}` · `GET /clients/{id}/export` · `POST /clients/{id}/erase {reason}` | clients, RGPD |
| `GET/POST /products` · `PUT /products/{id}` | catalogue |
| `GET /dashboard` · `/alerts` · `/audit` (`actor,action,targetType,targetId,from,to`) · `/audit/verify` · `/signing` | pilotage |
| `GET /registrations` · `POST /registrations/{empreinte}/decision {accept,reason}` | activations de production notifiées par une TV (portefeuille ou rapport) qui attendent le propriétaire (W23-05) : jamais un jeton, empreinte SHA-256 et code masqué ; accepter DÉCLARE l'émission et ouvre la licence et le poste, refuser clôt la ligne ; motif obligatoire, journal d'audit |
| `GET /ledger/export` · `POST /ledger/import?dryRun=&policy=review\|auto` · `GET /ledger/conflicts` · `POST /ledger/conflicts/{id}/decision {accept,reason}` · `GET /ledger/imports` | registre |

**Routes publiques** (désactivées par `CASTBRIDGE_LICENSES_PUBLIC_ROUTES=false` ; 404 quand éteintes ; aucune route n'existe pour la phase hors ligne) :
- `GET /api/v1/revocations` : la liste `cbx1.…` (enveloppe de type `revocation`) en **texte brut** (une ligne), publique (elle est signée). L'appareil la fusionne à ce qu'il sait déjà (union des clés, maximum des dates par poste) et l'applique à sa prochaine connexion.
- `GET /api/v1/entitlements/me?deviceCode=…` avec `Authorization: Bearer <jeton d'appareil>` (authentification d'appareil existante) : `{entitled, licenseId, state, endAt, graceDays, seats, products, trialIssuance}` ; le poste est retrouvé par son code **courant ou celui de sa dernière activation** (un module remplacé change le code) ;
  un code inconnu répond seulement `entitled:false` (rien ne fuit sur les autres codes). Enregistre une observation (empreinte) pour la détection d'abus.

### 5.1 Licences ouvertes par une activation notifiée (W23-05)
Une activation de **production** émise hors ligne et présentée par la TV (`POST /api/v1/wallet/sync` avec la preuve de possession, ou `POST /api/v1/activations/report` avec `bind`) **ouvre ou rattache la licence et le poste** sans saisie du propriétaire, si la clé d'outil est de confiance avec `ISSUE_PRODUCTION`, si le jeton vise cette TV, si rien n'est révoqué et sous le plafond de 10 licences par jour et 50 par mois et par clé.
- Licence créée : `created_by = report:<outil>:<kid>`, client technique « Client anonyme (activation rapportée) », 1 poste, plafond de transferts 0, `start_at` / `end_at` = droit signé `usage` de la clé (**`end_at` NULL seulement pour une clé vraiment illimitée**). Même règle pour la licence générée par le serveur (`auto`) et pour la licence importée du registre (corrigée à la première clé vérifiée, `LICENSE_END_FROM_TOKEN`).
- Jamais de refus silencieux : poste en trop, heure d'installation inconnue après 48 h, plafond par clé, même poste revendiqué par un autre matériel, poste libéré… donnent une ligne **en attente** (`GET /registrations`) et un motif affiché par le portefeuille ; alerte douce `REGISTRATION_ALERT` dans le journal d'audit, **jamais de révocation automatique**.
- Limite connue : la TV 0.14.32 n'envoie pas l'heure d'installation ; le jeton est accepté tant que sa fenêtre de 48 h court (marqué `install_time_unproven`), au-delà il attend votre décision sauf émission déclarée par le registre ou le serveur.
- Rattrapage du portefeuille : périodes commencées au plus 90 jours avant la première notification versées ; entre 90 et 366 jours seulement si l'émission est déclarée (registre, serveur) ou acceptée par vous ; jamais au-delà ; une fois par (licence, période).

## 6. Écarts assumés avec le format (à connaître)
- **Conflits en attente** : là où le format applique ou rejette automatiquement (licence inconnue, dépassement de postes, doublon de matériel, plafond de transferts), le serveur met l'événement en attente de décision du propriétaire (le brief l'exige). L'événement est gardé et exporté (c'est un fait signé) mais **sans effet** sur les postes tant qu'il n'est pas accepté. La politique `auto` redonne le comportement exact du format (vérifié par les vecteurs).
- **Une licence d'essai** est un objet du serveur (1 poste, sous contrôle du propriétaire) mais s'écrit `license=trial` dans l'activation, et ses événements `issue` ne sont jamais comptés comme poste dans les outils (format § 8.2).
- **Quota et plafond** modifiés sur le serveur ne se propagent pas par le registre (premier événement `license` gagnant).
- **Fenêtre d'installation** de **48 h** à partir de l'émission, pour tout code d'activation d'essai ou de production (décision du propriétaire, 2026-10-04) ; passé ce délai le code expire. Une activation déjà installée reste valable pour sa propre durée.
- **Le serveur n'émet ni clé compacte, ni commande `cbo1`, ni « tout ouvert », ni transfert.**
- Le droit « abonnement » est émis avec renouvellement automatique `0` ; la tolérance est `min(grâce de la licence, 30 jours)`.

## 7. Mémoire (JVM limitée à 512 Mo) et performances
Aucune requête lourde : tout est **paginé** (≤ 100 lignes), les listes sont agrégées en SQL avec `LIMIT`, la vérification du journal lit **par lots de 500** (mémoire constante), l'export CSV est borné à 10 000 lignes, l'export du registre à 50 000 événements et l'import à 5 000 événements / 5 Mo.
Les alertes sont des agrégats avec `LIMIT 50`. Une purge quotidienne efface les observations de plus de 90 jours.

## 8. Déploiement sûr (à faire dans cet ordre)
1. **Sauvegarde** : `./backup.sh` (dump complet **et** dump séparé des tables `lic_*` + `admin_user`, tête du journal d'audit journalisée). Vérifier la ligne « base : … ».
2. **Préproduction** (port 7091, base de test) : déployer l'image avec la nouvelle version **sans** la surcouche : les migrations V50–V52 s'appliquent (tables nouvelles seulement ; 4 colonnes ajoutées à `admin_user` avec valeur par défaut), **le module reste éteint**, l'administration actuelle est inchangée (testé). Vérifier `/admin` et `/api/v1/updates/…`.
   Vérifier qu'**aucune autre branche n'a pris les numéros de migration V50 à V52** avant de fusionner.
3. Créer les **secrets** (jamais dans le dépôt) : `secrets/license-signing.key` (clé Ed25519 du serveur : `openssl genpkey -algorithm ed25519 -out secrets/license-signing.key`), `secrets/license-totp.key` (`openssl rand -base64 32`), `secrets/license-audit.key` (`openssl rand -base64 32`, optionnel mais recommandé, **à ne plus changer**).
   `chmod 600`. Relever la clé publique (`GET /api/v1/admin/licenses/signing`, champs `publicKey` et `kid`) : **c'est elle que les TV, les téléphones et les outils doivent connaître comme clé du serveur, avec les portées `ISSUE_TRIAL`, `ISSUE_PRODUCTION`, `REACTIVATE`, `REVOKE`, `REGISTRY`, `POLICY` et aucune autre (**ni `TRANSFER`, ni `COMMAND_OPEN_ALL`**)** (anneau de clés de l'appareil, `docs/ACTIVATION-FORMAT.md` § 2).
4. Allumer : `docker compose -f docker-compose.yml -f docker-compose.licenses.yml up -d` (ou `CASTBRIDGE_LICENSES_ENABLED=true` + `CASTBRIDGE_LICENSES_SECRETS_DIR`). Activer le TOTP du propriétaire, créer un produit, un client, une licence d'essai, émettre, réémettre, importer un registre de test **en simulation**, vérifier l'audit.
   **Essai de bout en bout** avant toute activation de `REQUIRE_ACTIVATION` : faire installer sur une TV de test l'activation émise ici (la clé publique du serveur doit être dans son anneau de clés).
5. Seulement après : bascule en production, **dans cet ordre** : sauvegarde, migration, module éteint, vérification, puis interrupteur.
6. **Retour arrière** (du plus léger au plus lourd) :
   - **Interrupteur** : `CASTBRIDGE_LICENSES_ENABLED=false` et redémarrer : les pages et l'API répondent 404, tout le reste est inchangé ; les données restent.
   - **Version précédente de l'application** : les migrations sont **additives** (ancien code compatible : il ignore les tables et colonnes nouvelles).
   - **Retour du schéma** : arrêter le serveur, sauvegarder, lancer `src/main/resources/db/rollback/U50-U52__licenses_rollback.sql` à la main (perd toutes les données de licences), puis `DELETE FROM flyway_schema_history WHERE version IN ('50','51','52');`. Testé (réversible, et les migrations se rejouent proprement).
   - **Restauration** : voir ci-dessous.

### Sauvegarde et restauration
- **Dans la base** (déjà couvert par `backup.sh`) : toutes les tables `lic_*` (dont le registre `lic_event`), les comptes (rôles, secrets TOTP chiffrés), le journal d'audit. **Hors base, à sauvegarder vous-même, chiffré, ailleurs que sur ce serveur** : `license-signing.key` (sans elle, plus d'émission ni de signature ; les activations déjà émises restent valables tant que la clé publique est dans l'anneau des appareils),
  `license-totp.key` (sans elle, les TOTP déjà inscrits sont illisibles : les réinscrire), `license-audit.key` (sans elle, l'audit ne se vérifie plus).
- **Ce qu'il faut ajouter à `backup.sh`** : c'est fait (dump séparé `castbridge-licenses-AAAAMMJJ-HHMMSS.sql.gz`, jamais bloquant). À ajouter côté propriétaire, hors dépôt : la copie chiffrée des trois fichiers de secrets et la note de l'empreinte de tête de l'audit.
- **Restaurer** : (1) arrêter `castbridge-api` ; (2) `gunzip -c castbridge-AAAA….sql.gz | docker exec -i castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'` (voir README, « Sauvegardes ») ; ou, pour le seul module, le fichier `castbridge-licenses-….sql.gz` ;
  (3) redéposer les secrets ; (4) démarrer ; (5) **vérifier** : *Audit > Vérifier l'intégrité* doit dire « intact » et l'empreinte de tête doit être celle notée à la sauvegarde ; compter les licences et les postes.
  **Testé automatiquement** (`LicenseMigrationTest`) : un dump complet restauré dans une autre base redonne les mêmes nombres de licences, de postes et d'émissions et une chaîne d'audit qui se vérifie (même numéro de lignes, même empreinte de tête) ; une restauration qui a perdu des lignes d'audit est détectée.
  **Testé sur un vrai serveur** (MariaDB 10.11, `mysqldump` puis restauration dans une autre base, `LicenseRestoreCheckTest`) : mêmes nombres, chaîne vérifiable.

## 9. Brancher les outils et les appareils
- **TV et téléphones** : ajouter la clé publique du serveur à l'anneau de clés (`KeyRing`/`TrustedKey`) avec **exactement** les portées `ISSUE_TRIAL`, `ISSUE_PRODUCTION`, `REACTIVATE`, `REVOKE`, `REGISTRY`, `POLICY` ; la TV applique la liste `cbx1` reçue (Bluetooth, fichier, ou `GET /api/v1/revocations`).
- **Bureau et téléphone propriétaire** : importer le registre exporté par le serveur (fusion par union) et exporter le leur ; déclarer leurs clés publiques dans `CASTBRIDGE_LICENSES_TRUSTED_KEYS` avec leurs portées réelles.
- **Si le format évolue** : régénérer les vecteurs (`CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*ActivationVectorsTest*'`) puis rejouer `mvn test -Dtest='WireFormatVectorsTest,EnvelopeVectorsTest,LicenceVectorsTest,ServerIssuedVectorsTest'` : toute divergence apparaît tout de suite (les trois outils doivent rester identiques). Le serveur ne contient **aucun** quatrième format : seulement un port de celui-ci.

## 10. Dépannage
- *« Activez d'abord la double authentification »* : votre compte propriétaire n'a pas de TOTP : *Sécurité > Activer*. Si la clé `license-totp.key` manque : message « Clé de chiffrement TOTP absente ».
- *Propriétaire unique, téléphone TOTP perdu* : dans la base, `UPDATE admin_user SET totp_enabled = FALSE, totp_secret_enc = NULL, totp_last_step = NULL WHERE username = '…';` (accès à la base = accès physique : c'est la seule porte de secours, à protéger comme les secrets) ; ou créer d'abord un second propriétaire.
- *« Aucune clé de signature serveur »* : `license-signing.key` absent ou illisible dans le dossier des secrets.
- *« Plus de poste disponible »* : libérer un poste (motif), augmenter le quota, faire un transfert avec les outils hors serveur, ou regarder l'onglet Registre pour des dépassements importés.
- *« Demande d'appareil : … »* : coller le texte complet fourni par l'appareil (code, k et toutes les lignes `factor=`) ; un code seul ou une demande modifiée est refusé.
- *Le journal d'audit est « altéré »* : ne rien modifier ; comparer avec l'empreinte de tête notée ; restaurer la dernière sauvegarde saine ; analyser la ligne indiquée.
- *Un événement du registre est « refusé »* : `UNKNOWN_KEY` (déclarer la clé publique avec ses portées), `KEY_NOT_ALLOWED` (la clé n'a pas la portée de ce type d'événement), `BAD_SIGNATURE` (fichier ou événement altéré), `REVOKED_KEY`, `MALFORMED`.

## 11. Ce que le module ne fait pas (périmètre)
Aucun paiement, aucun prix, aucun prestataire ; ne signe jamais un transfert ni un « tout ouvert » ; ne bloque rien automatiquement ; n'embarque aucune clé privée ; aucune donnée réelle de client dans les tests ; ne touche pas à l'accès au serveur ni à sa configuration partagée.
La clé privée du serveur est un **point unique de défaillance** pour l'émission serveur (mais pas pour le « tout ouvert » ni le transfert, que le serveur ne peut pas faire) : sauvegardez-la, et prévoyez sa **rotation** (procédure pas à pas : § 3.6 ; nouvelle clé = nouveau `kid` ; les appareils acceptent plusieurs clés, ajouter la nouvelle avant de retirer l'ancienne).

## 12. Ce qui a été vérifié, et ce qui ne l'a pas pu être
**Vérifié** (`cd backend && mvn test`) : conformité au format (9 vecteurs de construction d'activation octet pour octet, 10 vecteurs de registre via l'import, codes d'appareil, k parmi n, base32) ; migrations sur base vide et sur une copie des schémas V1–V30 avec données, retour arrière puis rejeu, contraintes (unicité, clés étrangères, `CHECK`) ;
concurrence (16 fils / 3 postes ; 8 fils / même appareil ; 24 écritures simultanées de l'audit) ; idempotence ; grâce et expiration ; portée de la clé du serveur (code, API, page, import) ; chaîne d'audit (modification, suppression au milieu, à la fin, concurrence) ;
registre (signature, clés inconnues ou révoquées, portée, import à blanc, union idempotente, conflits et décisions, plafond de transferts, export réimporté) ; matrice des rôles (propriétaire avec/sans TOTP, support, lecture seule, anonyme, compte inconnu, absence de CSRF) ; TOTP (inscription, secret chiffré, rejeu, verrouillage) ;
RGPD (export, effacement, décompte intact, événements du registre effacés) ; alertes d'abus ; API (pagination, filtres, erreurs) ; routes publiques (liste `cbx1` signée et canonique, droits d'un appareil) ; module éteint (404 partout, administration inchangée) ; CSP/XSS/CSV ; dump restauré dans une autre base (chaîne d'audit vérifiable).
**Aussi vérifié sur un vrai serveur** : toute la suite du module a été rejouée sur **MariaDB 10.11 (InnoDB, REPEATABLE READ)** avec le pilote MySQL, ce qui a révélé et fait corriger des défauts invisibles sur H2 (lecture de dates en `LocalDateTime`, précision des fractions de seconde) ;
un vrai `mysqldump` restauré dans une autre base redonne les mêmes nombres et une chaîne d'audit qui se vérifie (`LicenseRestoreCheckTest`, optionnel : `-Dcb.restore.url=…`) ; le script de retour arrière s'y exécute proprement ; `backup.sh` a été exécuté avec un faux `docker` (dump séparé, aucune table, échec non bloquant).
**NON vérifié** (à faire en préproduction par le coordinateur) : MySQL **8.4** lui-même (le test `MySqlContainerTest` existant est ignoré ici : pas de Docker) ; `backup.sh` contre le vrai conteneur ; l'image Docker et la surcouche `docker-compose.licenses.yml` ; la limite de 512 Mo sous charge réelle ; l'interface sur un vrai téléphone ;
**l'installation réelle d'une activation émise ici sur une TV** (le vérificateur Kotlin de `core` n'a pas été exécuté contre ce serveur ; seule la conformité aux vecteurs est prouvée) ; l'écran de saisie et la clé compacte.
