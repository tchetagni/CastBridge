# Gestion des licences : administration du serveur en ligne

> Module « licences » de `backend/` (branche `claude/license-admin`). **Éteint par défaut.** Aucun prix, aucun prestataire de paiement, aucune clé dans le dépôt.
> Décisions du propriétaire respectées : une clé de signature par outil ; **la clé du serveur ne transfère pas et n'ouvre pas « tout »** ; règle commune
> **identifiant de licence + décompte des postes** ; essai **émis manuellement par défaut** ; contenus **à la carte ou par abonnement** ; phase hors ligne
> jusqu'à 1 an avec **import et réconciliation** des journaux émis par le bureau et le téléphone.

## 1. Ce que fait le module (en bref)

| Besoin | Où |
|---|---|
| Tableau de bord (licences actives, expirant sous 30 jours, postes utilisés, essais délivrés, doublons suspects, alertes) | `/admin/licenses` |
| Liste (recherche, filtres état/client/bouquet/expiration, tri, export CSV) | `/admin/licenses/list` |
| Fiche licence (droits, postes avec première/dernière vue, émissions, transferts, historique, actions) | `/admin/licenses/{licence}` |
| Formulaire d'émission (code d'appareil + licence → activation à copier / télécharger / QR) | `/admin/licenses/issue` |
| Recherche par code d'appareil | `/admin/licenses/device` |
| Clients (minimum de données, export et effacement RGPD) | `/admin/licenses/clients` |
| Catalogue de bouquets | `/admin/licenses/products` |
| Registre : import/export du fichier signé, rapport de réconciliation, décision manuelle | `/admin/licenses/registry` |
| Journal d'audit chaîné, filtrable, avec vérification | `/admin/licenses/audit` |
| Comptes, rôles, double authentification | `/admin/licenses/security` |
| API pour scripts (jeton Bearer existant) | `/api/v1/admin/licenses/**` |
| Routes publiques minimales (éteintes par défaut) | `GET /api/v1/revocations`, `GET /api/v1/entitlements/me` |

Même sécurité que le reste de `/admin` : session + CSRF, CSP stricte (aucun script ni style en ligne), thème sombre, français, utilisable sur téléphone.

## 2. Guide du propriétaire

### 2.1 Avant de commencer (une seule fois)
1. Le module est **éteint** : rien ne change tant que `CASTBRIDGE_LICENSES_ENABLED` n'est pas `true`. Suivre d'abord la procédure du § 8 (sauvegarde, préproduction).
2. Activer **votre double authentification** : *Licences > Sécurité > Activer*, scanner le QR dans FreeOTP / Aegis / Google Authenticator, saisir le code. Tant que ce n'est
   pas fait, votre compte propriétaire **peut lire mais ne peut rien modifier** (réglable : `CASTBRIDGE_LICENSES_REQUIRE_TOTP`).
3. Créer, si besoin, un compte **Support** (lecture + réémission d'une activation existante) et un compte **Lecture seule**.
4. Créer les **bouquets** (catalogue) puis un **client**.

### 2.2 Créer une licence
*Licences > Nouvelle licence* : client, type (payante ou **essai**), nombre de postes, début, fin (vide = sans fin, définitif), période de grâce (14 jours par défaut),
plafond de transferts par an (2 par défaut), bouquets inclus. L'identifiant `LIC-XXXXX-XXXXX` est généré (ou saisi). Un **essai** est une licence à 1 poste : il est
délivré **sous votre contrôle**, jamais automatiquement (`CASTBRIDGE_LICENSES_TRIAL_ISSUANCE=manual`, seule valeur livrée active ; aucune route d'essai automatique n'existe).

### 2.3 Activer une TV
1. La TV affiche son **code d'appareil** (`XXXX-XXXX-XXXX-XXXX`) ; le client vous le transmet.
2. *Licences > Émettre* : licence + code d'appareil → **Émettre**. Le serveur prend **un poste** (sous verrou), signe l'activation avec sa clé et vous la montre **une seule fois** : texte à
   copier, **fichier `activation`** à déposer dans `Download/CastBridge/` de la clé USB, ou **QR**. Elle n'est **enregistrée nulle part** (seule son empreinte l'est).
3. Redemander la même activation (TV réinstallée, fichier perdu) : cocher **Réémission** (ou « Télécharger l'activation » sur la fiche). Même matériel = **même activation, aucun poste de plus**.
   L'activation réémise reste identique tant que les droits de la licence (bouquets, fin, nombre de postes) ne changent pas ; si vous les changez, la réémission donne une **nouvelle** activation (nouveau `nonce`, nouvelle ligne d'émission).
4. Plus de poste libre ? Message clair « Quota atteint » : libérez un poste ou augmentez le quota (§ 2.4).

> **Format provisoire.** `docs/ACTIVATION-FORMAT.md` (branche `claude/trial-edition`) n'est pas encore publié. En attendant, le serveur signe un format **provisoire `CBP0`** qu'**aucun appareil
> n'accepte** : il sert à éprouver tout le flux sur la préproduction. La page d'émission l'affiche en toutes lettres. Voir § 9 pour le remplacer.

### 2.4 Libérer un poste, prolonger, suspendre, révoquer
Sur la fiche. Toute action **destructrice** (suspendre, révoquer, libérer un poste, réduire le quota ou raccourcir la fin, désactiver un bouquet, effacer un client, retirer un TOTP, décider un conflit)
passe par une page de **confirmation** avec **motif obligatoire** (3 caractères au moins) et une case à cocher ; le serveur refuse un motif vide même si la page est contournée.
- **Suspendre / réactiver** : réversible ; plus aucune activation n'est émise pendant la suspension.
- **Révoquer** : **définitif** ; les postes sont libérés et la licence entre dans la liste de révocation signée que les appareils récupèrent en ligne.
- **Prolonger** : nouvelle date de fin ; une licence expirée prolongée au-delà de maintenant redevient active.
- **Expiration et grâce** : après la fin, la licence est « en grâce » (14 jours par défaut) : seule la **réémission d'un poste existant** est possible ; ensuite elle est « expirée ».

### 2.5 Importer le registre hors ligne (bureau et téléphone propriétaire)
Pendant la phase hors ligne, le bureau et le téléphone émettent sans serveur. Chacun exporte son **journal** dans un fichier **signé** (clé de l'outil). Pour synchroniser :
1. Déclarer **une fois** la clé publique de chaque outil : `CASTBRIDGE_LICENSES_LEDGER_KEYS="<kid>:desktop:<base64>,<kid>:phone:<base64>"` (clé publique brute de 32 octets en base64 ; `kid` = 16 premiers chiffres hexadécimaux de SHA-256 de la clé publique).
2. *Licences > Registre > Importer*, cocher d'abord **Simulation** : le **rapport** montre ce qui serait appliqué, ce qui est déjà connu et les **conflits**, sans rien modifier. Relancer sans la case pour appliquer.
3. L'import est **idempotent** : un même fichier ne s'importe qu'une fois ; une même émission (même `nonce`) n'est jamais comptée deux fois.
4. **Conflits** (ils attendent **votre décision**, avec motif ; rien n'est appliqué à votre insu) :
   - `UNKNOWN_LICENSE` : licence inconnue du serveur. Créez-la d'abord, puis « Accepter » ; ou « Rejeter ».
   - `OVER_QUOTA` : plus d'appareils que de postes. « Accepter » **relève le quota** (tracé) ; « Rejeter » laisse tel quel.
   - `TWO_TOOLS` : le même poste et la même sorte ont reçu deux activations **différentes** de deux outils. Une réémission **identique** n'est pas un conflit.
   - `TRANSFER_CAP` : plafond de transferts par an dépassé. « Rejeter » garde la trace d'un transfert refusé.
5. **Le serveur ne signe jamais un transfert** : il **enregistre** ceux du registre (signés par le bureau ou le téléphone), les compte contre le plafond de la licence et libère l'ancien poste. Un fichier qui prétend qu'un transfert a été signé par le serveur est **refusé en entier**.
6. **Exporter** le registre (*Registre > Télécharger*) donne un fichier signé par la clé du serveur, que les outils peuvent importer à leur tour.

### 2.6 Droits d'un client (RGPD)
- **Export** : fiche client > « Exporter ses données (JSON) » (client, licences, postes, émissions ; jamais de texte d'activation, il n'est pas conservé).
- **Effacement** : nom, contact et notes effacés définitivement ; les **postes sont anonymisés, pas supprimés** (le code d'appareil devient `ANON-…`, les empreintes de facteurs et les observations sont supprimées) :
  le **décompte des postes reste exact**. Limite assumée : un appareil anonymisé qui reviendrait compte comme un nouvel appareil de la licence. Le journal d'audit ne contient jamais de nom ni de contact : l'effacement ne le touche pas.

## 3. Sécurité

### 3.1 Rôles (appliqués côté serveur, testés par matrice)
| | Propriétaire (avec TOTP) | Propriétaire sans TOTP | Support | Lecture seule |
|---|:-:|:-:|:-:|:-:|
| Tableau de bord, listes, fiches, CSV, recherche appareil, registre (vue) | oui | oui | oui | oui |
| Journal d'audit, vérification | oui | oui | oui | non |
| Réémettre une activation d'un poste **existant**, télécharger le fichier | oui | oui | oui | non |
| Émettre pour un **nouvel appareil** (consomme un poste) | oui | non | non | non |
| Créer/modifier licences, clients, bouquets, libérer, suspendre, révoquer, prolonger | oui | non | non | non |
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
Le journal ne contient **ni secret, ni activation complète (seulement 12 chiffres de son empreinte), ni nom, ni contact, ni code d'appareil entier** (`ABCD-****`).

### 3.4 Portée de la clé du serveur (code, pas configuration)
La clé du serveur émet : **essai, achat à la carte, abonnement, ré-activation**. Elle **ne peut ni transférer, ni « tout ouvrir »** : `ScopedActivationSigner.SERVER_SCOPES` est une constante du code, immuable, vérifiée **avant** toute écriture
(aucun poste n'est pris pour une demande interdite) et avant la signature, quel que soit l'appelant (page, API, import) ; testée (`LicenseScopeAndAuditTest`). La clé est lue **dans le dossier des secrets** (`license-signing.key`), jamais dans l'image ni les journaux.

### 3.5 Détection d'abus (alertes, **jamais de blocage automatique**)
Tableau de bord : même appareil sur plusieurs licences actives ; même code d'appareil vu depuis ≥ 2 téléphones ou ≥ 3 adresses IP en 24 h (seule une empreinte tronquée de la source est gardée, 90 jours) ; postes au-dessus du quota ou dépassements importés en attente ;
rafale d'émissions (≥ 10 en 10 minutes par licence) ; transferts répétés (plafond atteint, ou 2 en 30 jours). La décision (suspendre, libérer, révoquer) reste la vôtre.

## 4. Modèle de données (migrations Flyway V50 à V52, rétrocompatibles)
`lic_client`, `lic_product` + `lic_product_lot` (bouquets : à la carte ou abonnement, lots couverts, durée, champs de tarif **vides**), `lic_license` (`license_id` unique, client, type, état, **postes**, début, fin, grâce, plafond de transferts),
`lic_license_product` (droits), `lic_seat` (poste : **code d'appareil**, empreintes de facteurs hachées, première/dernière vue, état), `lic_issuance` (`kid`, `nonce`, expiration, émetteur, canal, **empreinte** du jeton : jamais le jeton, jamais une clé),
`lic_transfer`, `lic_revocation`, `lic_sighting`, `lic_audit` + `lic_audit_head`, `lic_ledger_import`, `lic_conflict`, et 4 colonnes sur `admin_user` (`role`, `totp_enabled`, `totp_secret_enc`, `totp_last_step` ; les comptes existants deviennent propriétaires).

**Décompte des postes sûr en concurrence** (jamais plus de postes que permis, même sous requêtes simultanées), à trois niveaux :
1. chaque opération sur les postes commence par le **verrou de ligne de la licence** (`SELECT … FOR UPDATE`) dans une transaction **READ COMMITTED** (avec le REPEATABLE READ par défaut de MySQL, un instantané figé pourrait masquer un poste validé par la transaction attendue) ;
2. le poste actif prend le **plus petit numéro libre de 1 à quota** ; contrainte `UNIQUE (licence, numéro)` ;
3. contrainte `CHECK` : `ACTIVE` ⇔ numéro renseigné (1..1000) ; `UNIQUE (licence, code d'appareil)` : une seule ligne par appareil, réactivée à la place d'être dupliquée.
Tests : 16 fils simultanés pour 3 postes = exactement 3 ; 8 fils pour le même appareil = 1 poste, 1 émission, la même activation ; contraintes testées en contournant le service.

## 5. API d'administration (`/api/v1/admin/licenses/**`, `Authorization: Bearer <jeton>`)
JSON ; listes paginées `{"items":[…],"page":0,"size":50,"total":n}` (taille ≤ 100) ; erreurs `{"status","erreur","message","details","chemin","date"}` en français.

| Méthode et chemin | Fonction |
|---|---|
| `GET /` (`q,state,client,product,expiringDays,sort,dir,page,size,format=csv`) · `POST /` | liste · création |
| `GET /{licence}` | fiche complète (postes, émissions, transferts, historique) |
| `POST /{licence}/suspend` `resume` `revoke` `{reason}` | états (motif obligatoire) |
| `POST /{licence}/extend {endAt,reason}` · `/seats {seats,reason}` · `/settings {graceDays,transferCap}` · `/products {productId,endsAt}` | prolonger, postes, réglages, bouquet |
| `POST /{licence}/seats/release {deviceCode,reason}` | libérer un poste |
| `POST /{licence}/activations {deviceCode,kind?,productIds?,factorsHash?}` · `/reissue {deviceCode}` | émettre · réémettre (`kind` ≠ `TRIAL/PURCHASE/SUBSCRIPTION/REACTIVATION` ou `productIds:["*"]` → **403**) |
| `GET /devices/{code}` | licences d'un code d'appareil |
| `GET/POST /clients` · `GET/PUT /clients/{id}` · `GET /clients/{id}/export` · `POST /clients/{id}/erase {reason}` | clients, RGPD |
| `GET/POST /products` · `PUT /products/{id}` | catalogue |
| `GET /dashboard` · `/alerts` · `/audit` (`actor,action,targetType,targetId,from,to`) · `/audit/verify` · `/signing` | pilotage |
| `GET /ledger/export` · `POST /ledger/import?dryRun=` · `GET /ledger/conflicts` · `POST /ledger/conflicts/{id}/decision {accept,reason}` · `GET /ledger/imports` | registre |

**Routes publiques** (désactivées par `CASTBRIDGE_LICENSES_PUBLIC_ROUTES=false` ; 404 quand éteintes ; aucune route n'existe pour la phase hors ligne) :
- `GET /api/v1/revocations` : `{"v":1,"kid","payload":base64({generatedAt,seq,revoked:[{licenseId|deviceCode|kid+nonce,at}]}),"sig":base64(Ed25519 du payload)}`. Publique (elle est signée). L'appareil garde le plus grand `seq` vu et applique les entrées à sa prochaine connexion.
- `GET /api/v1/entitlements/me?deviceCode=…` avec `Authorization: Bearer <jeton d'appareil>` (authentification d'appareil existante) : `{entitled, licenseId, state, endAt, graceDays, seats, products, trialIssuance}` ; un code inconnu répond seulement `entitled:false` (rien ne fuit sur les autres codes). Enregistre une observation (empreinte) pour la détection d'abus.

## 6. Format du registre (fichier signé)
`{"v":1,"kid":"<16 hex>","tool":"desktop|phone|server","payload":"<base64 du JSON>","sig":"<base64 Ed25519 des octets du payload>"}`.
Le `payload` : `{"format":"castbridge-ledger/1","tool":"…","exportedAt":"…Z","entries":[…]}` avec des entrées :
- `{"t":"license","licenseId","kind","seats","startAt"?,"endAt"|null}`
- `{"t":"issuance","licenseId","deviceCode","kind","kid":"<8-16 hex>","nonce":"<32 hex>","issuedAt","expiresAt"|null,"fingerprint":"<SHA-256 hex>"}`
- `{"t":"transfer","licenseId","from","to","signedBy":"desktop|phone","at"}`
- `{"t":"revocation",…}` : reconnue et ignorée à l'import (la révocation se décide sur le serveur).
Toute entrée invalide **refuse le fichier entier** (aucune demi-importation) avec la liste des erreurs. Limites : 5 Mo, 5 000 entrées.

## 7. Mémoire (JVM limitée à 512 Mo) et performances
Aucune requête lourde : tout est **paginé** (≤ 100 lignes), les listes sont agrégées en SQL avec `LIMIT`, la vérification du journal lit **par lots de 500** (mémoire constante), l'export CSV est borné à 10 000 lignes et l'export/import du registre à 50 000/5 000 entrées.
Les alertes sont des agrégats avec `LIMIT 50`. Une purge quotidienne efface les observations de plus de 90 jours.

## 8. Déploiement sûr (à faire dans cet ordre)
1. **Sauvegarde** : `./backup.sh` (dump complet **et** dump séparé des tables `lic_*` + `admin_user`, tête du journal d'audit journalisée). Vérifier la ligne « base : … ».
2. **Préproduction** (port 7091, base de test) : déployer l'image avec la nouvelle version **sans** la surcouche : les migrations V50–V52 s'appliquent (tables nouvelles seulement ; 4 colonnes ajoutées à `admin_user` avec valeur par défaut), **le module reste éteint**, l'administration actuelle est inchangée (testé). Vérifier `/admin` et `/api/v1/updates/…`.
3. Créer les **secrets** (jamais dans le dépôt) : `secrets/license-signing.key` (clé Ed25519 du serveur : `openssl genpkey -algorithm ed25519 -out secrets/license-signing.key`), `secrets/license-totp.key` (`openssl rand -base64 32`), `secrets/license-audit.key` (`openssl rand -base64 32`, optionnel mais recommandé, **à ne plus changer**).
   `chmod 600`. Relever la clé publique (`GET /api/v1/admin/licenses/signing`, champs `publicKey` et `kid`) pour les outils.
4. Allumer : `docker compose -f docker-compose.yml -f docker-compose.licenses.yml up -d` (ou `CASTBRIDGE_LICENSES_ENABLED=true` + `CASTBRIDGE_LICENSES_SECRETS_DIR`). Activer le TOTP du propriétaire, créer un bouquet, un client, une licence d'essai, émettre, réémettre, importer un registre de test **en simulation**, vérifier l'audit.
5. Seulement après : bascule en production, **dans cet ordre** : sauvegarde, migration, module éteint, vérification, puis interrupteur.
6. **Retour arrière** (du plus léger au plus lourd) :
   - **Interrupteur** : `CASTBRIDGE_LICENSES_ENABLED=false` et redémarrer : les pages et l'API répondent 404, tout le reste est inchangé ; les données restent.
   - **Version précédente de l'application** : les migrations sont **additives** (ancien code compatible : il ignore les tables et colonnes nouvelles).
   - **Retour du schéma** : arrêter le serveur, sauvegarder, lancer `src/main/resources/db/rollback/U50-U52__licenses_rollback.sql` à la main (perd toutes les données de licences), puis `DELETE FROM flyway_schema_history WHERE version IN ('50','51','52');`. Testé (réversible, et les migrations se rejouent proprement).
   - **Restauration** : voir ci-dessous.

### Sauvegarde et restauration
- **Dans la base** (déjà couvert par `backup.sh`) : toutes les tables `lic_*`, les comptes (rôles, secrets TOTP chiffrés), le journal d'audit. **Hors base, à sauvegarder vous-même, chiffré, ailleurs que sur ce serveur** : `license-signing.key` (sans elle, plus d'émission ni de signature), `license-totp.key` (sans elle, les TOTP déjà inscrits sont illisibles : les réinscrire), `license-audit.key` (sans elle, l'audit ne se vérifie plus).
- **Ce qu'il faut ajouter à `backup.sh`** : c'est fait (dump séparé `castbridge-licenses-AAAAMMJJ-HHMMSS.sql.gz`, jamais bloquant). À ajouter côté propriétaire, hors dépôt : la copie chiffrée des trois fichiers de secrets et la note de l'empreinte de tête de l'audit.
- **Restaurer** : (1) arrêter `castbridge-api` ; (2) `gunzip -c castbridge-AAAA….sql.gz | docker exec -i castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'` (voir README, « Sauvegardes ») ; ou, pour le seul module, le fichier `castbridge-licenses-….sql.gz` ;
  (3) redéposer les secrets ; (4) démarrer ; (5) **vérifier** : *Audit > Vérifier l'intégrité* doit dire « intact » et l'empreinte de tête doit être celle notée à la sauvegarde ; compter les licences et les postes.
  **Testé automatiquement** (`LicenseMigrationTest`) : un dump complet restauré dans une autre base redonne les mêmes nombres de licences, de postes et d'émissions et une chaîne d'audit qui se vérifie (même numéro de lignes, même empreinte de tête) ; une restauration qui a perdu des lignes d'audit est détectée.
  **Non testé ici** : la restauration sur un vrai MySQL 8.4 avec `mysqldump` (voir « Ce qui n'a pas pu être vérifié »).

## 9. Remplacer le format provisoire par le vrai (quand `docs/ACTIVATION-FORMAT.md` est publié)
Tout est isolé derrière deux interfaces : `ActivationEncoder` (octets signés + texte rendu ; aujourd'hui `ProvisionalActivationEncoder`) et `DeviceCode` (alphabet et champ des facteurs du code d'appareil, provisoire : 4 × 4 caractères `0-9A-Z`).
1. Implémenter `ActivationEncoder` avec l'`ActivationIssuer` de `core` (même vecteurs de test `tools/activation/test-vectors.json`) ; le déclarer comme bean `activationEncoder` à la place du provisoire.
2. Ajuster `DeviceCode.normalize` au vrai format (champ des facteurs, somme de contrôle) et `Validate`/`LedgerService.Entry` si les champs de l'entrée d'émission changent (`kid`, `nonce`, empreinte).
3. La **portée** (`ScopedActivationSigner.SERVER_SCOPES`), le décompte des postes, l'idempotence et l'audit **ne changent pas** : leurs tests restent valables. Seul `ProvisionalActivationEncoder` et ses tests de texte (`CBP0.`) sont à remplacer.
Rappel : l'idempotence « même appareil = même activation » suppose que la signature soit déterministe (Ed25519 l'est) et que le `nonce` vienne de la ligne d'émission (c'est le cas).

## 10. Dépannage
- *« Activez d'abord la double authentification »* : votre compte propriétaire n'a pas de TOTP : *Sécurité > Activer*. Si la clé `license-totp.key` manque : message « Clé de chiffrement TOTP absente ».
- *Propriétaire unique, téléphone TOTP perdu* : dans la base, `UPDATE admin_user SET totp_enabled = FALSE, totp_secret_enc = NULL, totp_last_step = NULL WHERE username = '…';` (accès à la base = accès physique : c'est la seule porte de secours, à protéger comme les secrets) ; ou créer d'abord un second propriétaire.
- *« Aucune clé de signature serveur »* : `license-signing.key` absent ou illisible dans le dossier des secrets.
- *« Quota de postes atteint »* : libérer un poste (motif), augmenter le quota, ou vérifier l'onglet Registre pour des dépassements importés.
- *Le journal d'audit est « altéré »* : ne rien modifier ; comparer avec l'empreinte de tête notée ; restaurer la dernière sauvegarde saine ; analyser la ligne indiquée.

## 11. Ce que le module ne fait pas (périmètre)
Aucun paiement, aucun prix, aucun prestataire ; ne signe jamais un transfert ni un « tout ouvert » ; ne bloque rien automatiquement ; n'embarque aucune clé privée ; aucune donnée réelle de client dans les tests ; ne touche pas à l'accès au serveur ni à sa configuration partagée.
La clé privée du serveur est un **point unique de défaillance** pour l'émission serveur (mais pas pour le « tout ouvert » ni le transfert, que le serveur ne peut pas faire) : sauvegardez-la, et prévoyez sa **rotation** (nouvelle clé = nouveau `kid` ; les appareils acceptent plusieurs clés).

## 12. Ce qui a été vérifié, et ce qui ne l'a pas pu être
**Vérifié** (`cd backend && mvn test`, 116 tests dont 42 pour ce module, 0 échec) : migrations sur base vide et sur une copie des schémas V1–V30 avec données, retour arrière puis rejeu, contraintes (unicité, clés étrangères, `CHECK`),
concurrence (16 fils / 3 postes ; 8 fils / même appareil ; 24 écritures simultanées de l'audit), idempotence, grâce et expiration, portée de la clé du serveur (code, API, page), chaîne d'audit (modification, suppression au milieu, à la fin, concurrence),
registre (signature, outil, import à blanc, idempotence, conflits et décisions, plafond de transferts, export réimporté), matrice des rôles (propriétaire avec/sans TOTP, support, lecture seule, anonyme, compte inconnu, absence de CSRF), TOTP (inscription, secret chiffré, rejeu, verrouillage),
RGPD (export, effacement, décompte intact), alertes d'abus, API (pagination, filtres, erreurs), routes publiques (liste signée, droits d'un appareil), module éteint (404 partout, administration inchangée), CSP/XSS/CSV, dump restauré dans une autre base (chaîne d'audit vérifiable).
**Aussi vérifié sur un vrai serveur** : toute la suite du module a été rejouée sur **MariaDB 10.11 (InnoDB, REPEATABLE READ)** avec le pilote MySQL, ce qui a révélé et fait corriger deux défauts invisibles sur H2 (lecture de dates en `LocalDateTime`, précision des fractions de seconde du journal d'audit) ;
un vrai `mysqldump` restauré dans une autre base redonne les mêmes nombres et une chaîne d'audit qui se vérifie (`LicenseRestoreCheckTest`, optionnel : `-Dcb.restore.url=…`) ; le script de retour arrière s'y exécute proprement ; `backup.sh` a été exécuté avec un faux `docker` (dump séparé, aucune table, échec non bloquant).
**NON vérifié** (à faire en préproduction par le coordinateur) : MySQL **8.4** lui-même (le test `MySqlContainerTest` existant est ignoré ici : pas de Docker) ; `backup.sh` contre le vrai conteneur ; l'image Docker et la surcouche `docker-compose.licenses.yml` ; la limite de 512 Mo sous charge réelle ; l'interface sur un vrai téléphone ;
l'envoi réel d'une activation à une TV (le format filaire de `claude/trial-edition` n'étant pas publié, le serveur signe un format **provisoire** qu'aucun appareil n'accepte).
