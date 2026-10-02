# Conception W5 : boutique (téléphone + TV), locations gérées en ligne par le serveur, jetons virtuels du Quiz, paiement en espèces (bons de recharge et commandes confirmées par le point focal)

> **Statut : conception (2026-10-02), à exécuter par les cahiers `sonnet-w5-01` à `sonnet-w5-24` (`docs/agent-briefs/SONNET-WAVE5-INDEX.md`).** Elle **remplace en partie** `DESIGN-W4-ENVELOPPE-LOCATIONS.md` (W4-A), `DESIGN-W4-VENTE-TERRAIN.md` (W4-C) et complète `DESIGN-W4-MODE-DEGRADE.md` (W4-B) ; la liste exacte de ce qui change est au § 14 et dans l'index de la vague 5 (« Changements aux cahiers w4 »). Rien n'est implémenté par ce document ; aucun montant, aucun numéro, aucun secret.
> Chemins : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = tests cœur, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`, `DK/` = `tools/activation-desktop/src/main/kotlin/castbridge/desktop/`, `B/` = `backend/src/main/java/castbridge/server/`, `BT/` = tests serveur.

## 0. Décisions du propriétaire qui fondent la vague (2026-10-02)

| # | Décision | Conséquence directe |
|---|---|---|
| **P1** | « Pour l'instant le commercial vendra les clés d'activation d'appli TV » | Le point focal vend **des clés** (essai / production, durée de la grille) et, en complément, **des bons de recharge** et **l'encaissement de commandes** de la boutique. Il **n'émet plus de location** ; le **maître des locations n'est plus délégué** à son téléphone ; la ligne `master=` et `maxRentalDays` de la délégation disparaissent (toujours 0). |
| **P2** | « Les locations seront gérées en ligne » | Le **serveur** émet et renouvelle les locations des TV déjà activées : durée fixée par le catalogue (30 j par défaut, **≤ 60 j**), **clé de contrat aléatoire par contrat, enveloppée pour la clé d'installation X25519 de la TV** (boîte v2 de W4-A), lots scellés **par le serveur**, livrés par le téléphone-passerelle ou par le Wi-Fi direct de la TV. **Plus aucun secret maître partagé** (décision D8 close : § 4.2). La consommation d'une location livrée reste **entièrement hors ligne** (moteur, échéance, destruction de clé : inchangés). |
| **P3** | « Les 2 applications auront une boutique pour visualiser le contenu louable et pour s'acheter des jetons virtuels pour les jeux » | CastBridge (téléphone) **et** CastBridge-TV ont un écran **Boutique** : catalogue louable (bouquets, durées, prix XAF, contenu, aperçu gratuit), commande de location, **jetons** du Quiz, reçus, historique. |
| **P4** | Jetons : **le Quiz des Millions est le seul jeu qui consomme des jetons** ; Sudoku et Échecs restent gratuits (sans jetons) | § 6 : économie des jetons construite autour du Quiz seulement ; la base du Quiz reste jouable gratuitement. |
| **P5** | **Moyens de paiement : espèces et, éventuellement, bons de recharge. Aucun mobile money, aucun agrégateur** (ni code, ni cahier, ni estimation) | § 5 : deux chemins en espèces, (a) **bon de recharge** prépayé vendu par le point focal, (b) **commande en espèces** confirmée par le point focal ; abstraction `Order` + `Payment` neutre pour un moyen ultérieur, **sans** intégration. |
| Rappel | Principe hors ligne de la TV : elle ne va en ligne que par Internet direct ou par la passerelle du téléphone ; **un achat se termine toujours sur le téléphone ou par un code tapé sur la TV** | § 7 et § 8. |
| Rappel | D4 = NON (clé d'installation X25519, lots chiffrés au repos : W4-A, sous-vague 4a **conservée telle quelle**) ; D6 = mode réduit (W4-B conservé) ; D5 = NON (shell SSH gardé) | La vague 5 **dépend** de 4a (clé d'installation) et de 4b (mode réduit). |

## 1. En bref (ce que livre la vague 5)

1. **Boutique** dans les deux applications, pilotée par **deux fichiers signés hors ligne par le propriétaire** (catalogue de bouquets, déjà en place, et grille de prix de W4-C, étendue aux jetons) et par **un serveur qui tient les commandes, les paiements, les contrats de location, le grand livre des jetons et les reçus**.
2. **Location en ligne** : le téléphone (ou la TV en ligne) envoie au serveur la **demande de location** (demande d'appareil v2 avec `install=` + **preuve d'activation** = le jeton d'activation de production installé sur la TV) ; le serveur vérifie, émet une **activation de location** signée par sa clé (`rental|loc-<bouquet>|…|v2:<boîte>`), scelle les lots du bouquet sous la clé du contrat, et sert le tout ; le téléphone livre à la TV par les routes existantes (`POST /api/activation/install`, `/api/rental/install`). Renouvellement = même `period`, même clé, lots déjà livrés toujours lisibles.
3. **Jetons du Quiz** : un **grand livre serveur par licence**, des **bons de jetons signés** (`cbx1`, nouveau type `tokens`, ciblant la TV) qui créditent un **porte-jetons local** de la TV pour dépenser **hors ligne**, un journal de dépenses chaîné et réconcilié à chaque contact. Les jetons achètent des **commodités de la partie Millionnaire** (jokers supplémentaires, seconde chance), jamais la progression, jamais une mise redistribuée, jamais un gain réel.
4. **Paiement en espèces** : (a) **bons de recharge** `CB-XXXX-XXXX-XXXX-XXXX` (lots signés par le propriétaire, le serveur ne garde que des **empreintes**), vendus par les points focaux et inscrits dans leur journal des ventes ; (b) **commande en espèces** : code de commande court montré au point focal, qui la confirme depuis son app (quota et grille de la délégation) ; le serveur livre. **Chemin principal recommandé : (a) le bon de recharge** (§ 5.1).
5. **Point focal recentré** (W4-C réduit) : clés + bons + confirmation de commandes ; même journal chaîné, même réconciliation, mêmes anomalies ; plus de scellement ni de maître.
6. **Protection** : rien de ce qui a de la valeur n'est produit par la TV : locations et bons de jetons sont **signés par le serveur**, ouverts avec la **clé d'installation** ; le porte-jetons local est **lié à l'installation** (HMAC dérivé de la clé privée X25519) et sa séquence est **rapportée au battement de cœur** (protect-05) ; un APK rapiécé ne fabrique ni clé de lot, ni bon, ni crédit.
7. **Enfants** : les profils enfant **ne commandent jamais** ; dépenser des jetons sur la TV demande le **code parental** au-delà d'une allocation réglée par le parent (0 par défaut) ; le téléphone de l'enfant ne voit pas la boutique en achat.
8. **Ce qui reste hors ligne** : jouer au Quiz (gratuit et avec jetons déjà crédités), lire les locations livrées, consulter la boutique en cache, taper un bon (mis en attente), voir ses reçus.

## 2. Vue d'ensemble : acteurs, canaux, autorité

```
                 fichiers signés hors ligne (clé des mises à jour du propriétaire)
                 ┌── catalogue de bouquets (rentalDays)  ── grille de prix (clés, locations, jetons) ──┐
                 ▼                                                                                   ▼
┌──────────────┐   HTTPS (jeton d'appareil)   ┌──────────────────────┐   HTTPS (jeton d'appareil, direct)  ┌──────────────┐
│  CastBridge  │ ───────────────────────────► │   Serveur (VPS)      │ ◄──────────────────────────────── │ CastBridge-TV│
│  téléphone   │ ◄─────────────────────────── │ commandes, paiements │ ──────────────────────────────── ►│  (boutique)  │
│  (boutique,  │   activation de location,    │ contrats, lots       │   seulement si la TV a Internet   │              │
│  passerelle) │   lots scellés, bons jetons, │ scellés, grand livre │                                   │  porte-jetons│
└──────┬───────┘   reçus                      │ jetons, bons, reçus  │                                   │  coffre      │
       │ Bluetooth / Wi-Fi local (PIN, téléphone de confiance)        │                                   │  catalogue   │
       │ /api/activation/install, /api/rental/install, /api/tokens/*,  │                                   │  en cache    │
       │ /api/shop/catalog (relais des fichiers signés)                │                                   └──────────────┘
       ▼                                                               │
┌──────────────┐  Bluetooth / Wi-Fi / papier  ┌──────────────────────┐ │  POST /api/v1/agent/sync (journal chaîné)
│ CastBridge-TV│                              │ CastBridge           │◄┘  POST /api/v1/agent/orders/confirm
│ (client)     │                              │ mode « Point focal » │    GET  /api/v1/agent/vouchers/stock
└──────────────┘                              │ clés, bons, encaisse │
                                              └──────────────────────┘
```

**Autorité.** Le serveur est **l'autorité comptable** (commandes, paiements, soldes de jetons, contrats, reçus). La TV est **l'autorité d'exécution hors ligne** (elle applique ce qu'elle a reçu signé : location, bon de jetons ; elle ne crée rien). Le téléphone est **transporteur et vitrine** ; il n'est jamais cru sur parole (la TV revérifie chaque signature, chaque empreinte de lot, chaque boîte). Le propriétaire signe hors ligne ce qui définit l'offre (catalogue, grille, lots de bons) : **une compromission du serveur ne change ni les prix ni le catalogue et ne fabrique pas de bons vendables** (il faut la clé du propriétaire ; elle peut en revanche créditer des jetons et émettre des locations : limite assumée, § 10).

## 3. Modèle de données

### 3.1 Produits et articles (identifiants figés, utilisés par la grille, les commandes, les reçus, les bons)

| Article (`item`) | Forme | Vendu par | Délivré comme |
|---|---|---|---|
| `cle-essai\|<jours>` | clé d'essai | point focal (app agent, hors ligne) | activation `trial` (inchangé, W4-C) |
| `cle-production\|<jours>` | clé de production (durée de la grille : 30 / 90 / 365, à confirmer D9-bis) | point focal ; **ou** bon de recharge `cle` redimé en boutique (optionnel, § 5.2) | activation `production` (W4-C) ; en boutique : émise par le serveur (`ActivationService.issue`, existant) |
| `loc-<bouquet>\|<rentalDays>` | location d'un bouquet pour la durée **exacte** du catalogue (≤ 60 j ; un bouquet à `rentalDays` > 60 n'est **pas louable en ligne**, le serveur le refuse et la boutique le grise « durée non disponible ») | boutique (téléphone ou TV) | activation de location signée par le serveur + lots scellés |
| `jetons\|<n>` | un paquet de `n` jetons du Quiz (ex. 20 / 60 / 150, montants et tailles : **D-W5-1, BLOQUÉ**) | boutique | crédit du grand livre + bon de jetons signé pour la TV |
| `essai-lots` | fenêtre d'essai des lots (produit `essai`, 3 j / 720 min, une fois par installation) | gratuit, automatique | contrat de location **gratuit émis par le serveur** (§ 4.6) |

**Grille de prix** (`castbridge-price-grid-v1`, W4-C § 6, format **inchangé**, lignes supplémentaires) :
```
price=jetons|20|<xaf>
price=jetons|60|<xaf>
price=jetons|150|<xaf>
price=loc-<bouquet>|<rentalDays>|<xaf>
```
Un article absent de la grille n'est **ni vendable, ni affiché avec un prix** ; la boutique l'affiche « prix non communiqué » et désactive la commande. La grille porte aussi, nouvelles lignes optionnelles : `tokens.expiryDays=0` (0 = les jetons n'expirent pas, **recommandé**), `tokens.offlineGrantMax=60` (plafond d'un bon de jetons hors ligne, § 6.5), `tokens.kidDailyDefault=0`.

### 3.2 Catalogue de bouquets

`castbridge-bundle-catalog-v1` (existant, `C/lots/SignedBundleCatalog.kt`, `GET /api/v1/catalog/bundles`) : **inchangé**. La boutique en lit : `id`, `title`, `lots`, `rentalDays`, taille, et déduit l'aperçu gratuit (les lots `<lot>-trial` du même bouquet, déjà sur la TV ou le téléphone).

### 3.3 Tables serveur (migration `V62__shop.sql` ou « plus haut + 1 » ; le cahier vérifie)

```
shop_order        (id PK, order_ref CHAR(9) UNIQUE 'CB-XXXX-XX', license_id, seat_id, device_code, install_pub CHAR(64) NULL,
                   kind ENUM(RENTAL, TOKENS, KEY, TRIAL_LOTS), item VARCHAR(64), qty INT, amount_xaf INT, grid_at,
                   status ENUM(CREATED, AWAITING_PAYMENT, PAID, FULFILLED, DELIVERED, CANCELLED, REFUNDED, EXPIRED),
                   channel ENUM(PHONE, TV, AGENT), created_at, paid_at, fulfilled_at, delivered_at, expires_at, idem_key UNIQUE,
                   fulfilment_ref NULL  -- rental_contract.id | token_ledger.id | lic_issuance.id
                   )
shop_payment      (id PK, order_id FK, method ENUM(VOUCHER, CASH_AGENT, OWNER_GRANT), ref VARCHAR(64),   -- serial du bon | agent_kid:seq | motif
                   amount_xaf INT, at, confirmed_by VARCHAR(64), UNIQUE(method, ref))
voucher_batch     (id PK, batch_id CHAR(8) UNIQUE, item VARCHAR(64), qty_per_code INT, count INT, issued_at, expires_at,
                   signed_text TEXT, owner_kid, imported_at, revoked_at NULL, revoked_reason NULL, assigned_agent_kid NULL)
voucher           (id PK, batch_id FK, serial CHAR(10) UNIQUE, code_hash CHAR(64) UNIQUE,     -- SHA-256(code), jamais le code
                   status ENUM(ISSUED, SOLD, REDEEMED, REVOKED, EXPIRED), sold_by_agent NULL, sold_at NULL,
                   redeemed_at NULL, redeemed_order_id NULL, redeemed_device_code NULL, attempts INT)
rental_contract   (id PK, license_id, seat_id, device_code, install_pub CHAR(64), product_id, bundle_id, period_ms BIGINT,
                   starts_at, ends_at, days, max_concurrent, key_enc VARBINARY(60),      -- clé de contrat AES-GCM sous la KEK serveur
                   state ENUM(ACTIVE, RENEWED, ENDED, REVOKED), last_activation_fp CHAR(64), created_at, updated_at,
                   UNIQUE(license_id, seat_id, product_id, period_ms))
rental_lot_cache  (contract_id FK, lot_id, version, sha256_sealed, bytes, path, built_at, PK(contract_id, lot_id, version))
token_account     (license_id PK, balance BIGINT, reserved BIGINT, updated_at)
token_ledger      (id PK, license_id, seq INT, kind ENUM(CREDIT, GRANT, SPEND, REVERSAL, ADJUST), amount BIGINT,
                   ref VARCHAR(64), device_code NULL, at, note, UNIQUE(license_id, seq))
token_grant       (id PK, license_id, device_code, install_pub, grant_seq INT, amount BIGINT, envelope_text TEXT,
                   issued_at, expires_at, spent_reported BIGINT, last_report_at, state ENUM(ISSUED, DELIVERED, SETTLED, VOID),
                   UNIQUE(license_id, device_code, grant_seq))
shop_receipt      (id PK, receipt_code CHAR(11) UNIQUE 'R-XXXX-XXXX', order_id FK, text TEXT, signed TEXT, issued_at)
agent_order_confirm (id PK, agent_kid, ledger_seq, order_ref, amount_xaf, at, accepted BOOL, reason)   -- lien journal d'agent ↔ commande
```
`agent_*` (W4-C § 8) : **conservées** ; `agent_delegation` perd l'usage de `max_rental_days` (colonne gardée, toujours 0) et gagne `may_confirm_orders BOOL`, `may_sell_vouchers BOOL`, `max_confirm_xaf_per_day INT`.

### 3.4 Formats signés nouveaux (vecteurs : `tools/activation/shop-vectors.json`, `castbridge-shop-vectors-v1`)

**(a) Demande de location / de jetons** (texte envoyé au serveur par le téléphone ou la TV ; signé par **personne** : la preuve est dans son contenu) :
```
castbridge-shop-request-v1
device=<demande d'appareil v2 complète, encodée base64url>     (code, k, factor=…, install=x25519|…)
activation=<jeton cbx1 de l'activation de production installée, le plus récent qui compte>
rentals=<lignes « produit@period:fin » des contrats actifs de la TV, séparées par « , » ; vide si aucun>
tokensSeq=<dernier grant_seq reçu>|<somme dépensée rapportée>|<hmac hex 32 du journal local>     (facultatif, § 6.5)
```
Le serveur : analyse la demande (tolère les lignes inconnues), **vérifie l'activation** avec `EnvelopeVerifier` et son anneau (`TrustedKeys` : clés du propriétaire, serveur, **délégations connues** pour une activation avec ticket), vérifie que sa cible correspond aux empreintes de la demande (k parmi n, `DeviceIdentity.matches`), qu'elle est de **production**, en cours (plafond `usage` non passé, sinon « clé à renouveler »), et en déduit `(licenseId, seatId)`. Une TV en **mode réduit** ou en **essai** est reconnue (§ 9).

**(b) Activation de location** : enveloppe `cbx1` `type=activation`, `kind=production`, `license`/`seat` de la preuve, **un seul droit** : `right=rental|loc-<b>|<b>|<startsAt>|<period>|<jours>|0|0|<maxConcurrent>|v2:<eph>:<blob>` ; `target` = empreintes de la demande ; fenêtre d'installation 48 h ; signée par la **clé serveur** (`ISSUE_PRODUCTION`, déjà dans ses portées). **Aucun autre droit** (la clé de production du client reste la seule à compter pour la durée ; la ligne de location a sa propre horloge : `RentalEngine`). Vérifiée sur la TV exactement comme aujourd'hui (`ActivationVerifier`, `RentalLedger.install(…, install)`) ; **prérequis** : la clé publique du serveur est dans `TRUSTED_KEYS` compilées de la TV (à vérifier au premier déploiement : `activation-trusted-keys.txt` ; sinon la TV refuse `UNKNOWN_KEY` : cahier w5-17, critère).

**(c) Bon de jetons** (`type=tokens`, nouveau corps d'enveloppe, portée **`ISSUE_PRODUCTION`** de la clé serveur ; W4-C avait prévu que seule la clé du propriétaire porte `DELEGATE` : rien ne change) :
```
castbridge-envelope-v1
type=tokens
kid=<serveur>  seq=<n>  nonce=<hex>  issuedAt=<ms>  notBefore=<ms>  expiresAt=<ms>   (fenêtre d'installation 30 j)
target=<k, empreintes de la TV>
--
license=<id>
grant=<grant_seq, croissant par (licence, appareil)>
amount=<1..tokens.offlineGrantMax>
install=<hex de la clé publique d'installation>      (lie le bon à l'installation : une réinstallation ne peut pas le rejouer)
expiry=<ms ou 0>
```
Vérification (TV, serveur, Python) : `MALFORMED`, `UNKNOWN_TYPE`, `UNKNOWN_KEY`, `REVOKED_KEY`, `BAD_SIGNATURE`, `KEY_NOT_ALLOWED`, `BAD_GRANT` (bornes, `install` ≠ clé de cette installation), `WRONG_TARGET`, `STALE_SEQUENCE` (`grant` ≤ dernier crédité : **un bon ne se crédite qu'une fois**), `NOT_YET_VALID`, `WINDOW_CLOSED`.

**(d) Bon de recharge** (code papier) : `CB-XXXX-XXXX-XXXX-XXXX` : 16 caractères Crockford = 2 (préfixe de lot) + 13 (secret aléatoire, 65 bits) + 1 (contrôle `Base32C.check`, saisie O/0, I/1 tolérée ; une faute de frappe est détectée **localement** avant tout appel). `serial` = `<lot 2>` + 8 caractères = identifiant public imprimé **à côté** du code (sous la zone à gratter : le serial sert au stock, le code à la recharge). **Lot signé par le propriétaire** (fichier produit hors ligne par `tools/vouchers/make_vouchers.py`, § 5.3) :
```
castbridge-voucher-batch-v1
batch=<2 Crockford + 6 hex>
item=jetons|60                     (ou loc-<bouquet>|30, cle-production|90)
count=<n ≤ 5000>
issuedAt=<ISO>  expiresAt=<ISO>    (≤ 24 mois)
agent=<kid ou « - »>               (lot affecté à un point focal, ou libre)
voucher=<serial>|<sha256 hex du code>        (une ligne par bon, triées)
signature=<Ed25519 base64 de la clé des mises à jour du propriétaire>  keyId=<kid>
```
Le serveur importe **ce fichier** (empreintes seulement) ; les codes clairs sont dans un second fichier (`*-CODES-SECRET.csv`) que seul le propriétaire imprime, **jamais importé**.

**(e) Reçu signé** (`castbridge-receipt-v2`, serveur) : texte français + signature serveur ; code `R-XXXX-XXXX` (`Receipt.code` de W4-C, même alphabet) ; vérifiable en ligne (`GET /api/v1/shop/receipts/{code}`) ; conservé dans les deux applications.

**(f) Journal local du porte-jetons de la TV** (`files/tokens/wallet.txt`, `SafeFile`) :
```
castbridge-token-wallet-v1
license=<id>  install=<installId>
grant=<grant_seq>|<amount>|<fp enveloppe>                    (une ligne par bon crédité)
spend=<seq>|<at>|<game>|<item>|<cost>|<prev 16 hex>|<mac 16 hex>   (chaîné ; mac = HMAC-SHA256(kWallet, ligne) ; kWallet = HKDF(installPriv, "castbridge-wallet-v1"))
```
Solde = Σ grants − Σ spends. Un fichier dont une `mac` est fausse ou dont la chaîne est rompue est **ignoré** : solde 0 et message « Porte-jetons illisible : reconnectez le téléphone pour le resynchroniser » (le serveur réémet les bons non épuisés, § 6.5).

### 3.5 Code de commande

`CB-XXXX-XX` (6 caractères Crockford de données + 1 de contrôle, préfixe « CB- » ; 30 bits : unique par serveur pendant 90 jours, vérifié à la création) : affiché en grand sur la TV ou le téléphone, dit à voix haute au point focal, tapé par lui dans son app. Ne contient aucun secret (confirmer exige la clé de l'agent **et** le montant).

## 4. Locations gérées en ligne (P2) : cryptographie, serveur, flux

### 4.1 Ce qui ne change pas
`Right.Rental` (10 champs, bornes), `RentalLines`, `RentalEngine` (états, horloge, usage), `RentalLedger` (fusion par `period`, pierre tombale), `RentalVault`, `RentalSweeper`, `RentalPolicy` (lot libre jamais loué), `RentalDurations` (durée exacte du catalogue), `RentalApi` (routes TV `/api/rental/*`), `LotCrypt` au repos (W4-A § 7), **boîte v2** et **clé d'installation** (W4-A § 3-6 : prérequis), format des vecteurs v1/v2.

### 4.2 Décision D8 close : **aucun secret maître partagé**
- `rentalKey` d'un contrat = **32 octets aléatoires tirés par le serveur** à la création du contrat (`SecureRandom`), **conservés chiffrés** en base (`rental_contract.key_enc` = nonce ‖ AES-256-GCM(KEK serveur, clé), AAD = `licence|poste|produit|period`). La **KEK serveur** = fichier `rental-kek.key` (32 octets) dans `secrets-dir`, **distinct** de la clé de signature et de la clé des ordres ; absent ⇒ module boutique refusé au démarrage avec message clair. Rotation de la KEK = re-chiffrement des lignes (commande d'administration, hors vague).
- La boîte **v2** (W4-A § 5) enveloppe cette clé pour `installPub` : `kek = HKDF(X25519(ephPriv, installPub), …, produit, period)`. Le serveur utilise `KeyAgreement("XDH")` (Java 17, prévu par w4-05).
- **Renouvellement** (même `period`) : le serveur **relit** `key_enc` et réémet une ligne avec la **même** clé ⇒ la TV fusionne (`RentalLedger.install` : « clé déjà en place »), les lots livrés restent lisibles, **rien à retélécharger**. La `period` d'un renouvellement vient des `rentals=` de la demande **et** de `rental_contract` (le serveur fait foi ; un écart ⇒ nouvelle location, dit à l'utilisateur).
- **Réinstallation de la TV** (nouvelle clé d'installation, W4-A § 3) : la boutique envoie une nouvelle demande ; le serveur retrouve le contrat actif `(licence, poste, produit, period)` et réémet **la même clé dans une nouvelle boîte**, gratuitement (`shop_order.kind=RENTAL`, `amount=0`, `payment=OWNER_GRANT:reissue`), au plus **3 fois par contrat** (anomalie au-delà).
- **Pourquoi c'est mieux que le maître** : la compromission d'un téléphone (agent ou client) ne donne **aucune** clé ; la compromission du serveur donne les clés des contrats **en cours** (chiffrées sous une KEK fichier), pas une formule qui ouvre le passé et l'avenir ; la rotation est triviale (contrat suivant = clé suivante) ; aucun secret à synchroniser entre console, bureau et serveur.
- **Fenêtre d'essai des lots** (`essai|tout`, 3 j / 720 min, une fois) : elle **quitte** la clé d'essai hors ligne (les outils émettent `--sans-lots-essai` **par défaut**) et devient un **contrat gratuit émis par le serveur** à la première demande (§ 4.6). Les outils du propriétaire gardent `masterFrom(signer)` **uniquement** pour `tools/rental-test` et les vecteurs v1 (rien ne l'utilise en production). **Décision renversable** : si le propriétaire veut garder la fenêtre d'essai hors ligne, l'option `--lots-essai` reste et le téléphone scelle avec le maître de l'outil (comportement actuel), au prix d'un maître dans la console.

### 4.3 Scellement des lots par le serveur : coût, cache, bande passante (VPS sans CDN, Cameroun)
- Format de transport **inchangé** : `RentalKeys.seal(rentalKey, lot, version, zip)` = `nonce(12) ‖ AES-256-GCM(lotKey, zip)` avec `lotKey = HKDF(rentalKey, "lot|<id>|<version>")`, **déterministe** (nonce dérivé) ⇒ le même contrat produit toujours les mêmes octets : `Range`/`If-Range`/`ETag` fonctionnent, et le téléphone **reprend** un téléchargement coupé (`LotSync` sait déjà).
- **Coût** : un bouquet classe = 0,05 à 4,2 Mo (TRIAL-EDITION § 3) ; AES-GCM sur un VPS ≈ 0,5 à 1 Go/s ⇒ **< 10 ms par lot**, négligeable ; on scelle **à la première demande** dans `${storage-dir}/rentals/<contract_id>/<lot>-v<n>.sealed` (`rental_lot_cache`), on sert ensuite le fichier (ETag = SHA-256 du scellé). Purge : contrats `ENDED` + 7 j, et plafond global `castbridge.shop.rental-cache-max-bytes` (défaut 4 Go, LRU). 1 000 contrats × 4 Mo = 4 Go : tenable.
- **Bande passante** : chaque TV télécharge **une fois** ses lots scellés (Wi-Fi seulement sur le téléphone, règle existante de `LotSync` : « jamais sur réseau facturé » sauf accord de l'utilisateur) ; renouvellement = 0 octet ; réinstallation de la TV = re-livraison depuis le **stock du téléphone** si les fichiers scellés y sont encore (`files/shop/sealed/<contrat>/`, gardés jusqu'à la fin du contrat + 7 j, dans le quota de 100 Mo), sinon re-téléchargement.
- **Pas de scellement à deux niveaux** (clé de contenu par lot + enveloppe par contrat) : il économiserait des octets seulement pour un téléphone qui sert **plusieurs** TV pour le **même** bouquet (rare) et ajouterait un format ; refusé pour cette vague, noté comme évolution possible.
- Les lots **libres** (CC BY-SA) ne sont jamais scellés ni loués (`RentalPolicy.refusal`, catalogue de lots `families`) ; les lots **d'essai** non plus (gratuits, en clair).

### 4.4 Routes serveur (module `B/shop/**`, `castbridge.shop.enabled=false` par défaut)

Auth : jeton d'appareil (`Authorization: Bearer`, `DeviceService.authenticate`) du **téléphone ou de la TV** ; limite de débit existante ; corps ≤ 256 Ko sauf lots.

| Route | Rôle |
|---|---|
| `GET /api/v1/catalog/bundles`, `GET /api/v1/catalog/prices` | existants (bouquets, grille) : la boutique les relit, les garde, les relaie à la TV |
| `POST /api/v1/shop/quote` `{request, item}` | vérifie la demande (§ 3.4 a), répond `{licenseId, seatId, item, days, amountXaf, renewalOf: period?, allowed: bool, reason?, orderable: bool}` ; ne crée rien |
| `POST /api/v1/shop/orders` (`Idempotency-Key`) `{request, item, qty, channel, payment?: {method: VOUCHER, code}}` | crée la commande ; avec un **bon valide** : paiement + exécution dans la **même transaction** (`PAID` → `FULFILLED`) ; sans paiement : `AWAITING_PAYMENT`, retourne `orderRef`, `expiresAt` (72 h), instructions « Payez en espèces chez votre point focal avec ce code » |
| `GET /api/v1/shop/orders/{ref}` | statut ; si `FULFILLED` : `fulfilment` = `{activation, contract: {product, period, endsAt}, lots: [{id, version, bytes, sha256, url}]}` **ou** `{tokensGrant: <enveloppe>, balance}` **ou** `{activation}` (clé) |
| `POST /api/v1/shop/orders/{ref}/delivered` `{tvAck}` | accusé de livraison (`DELIVERED`) ; purge des fichiers après 7 j |
| `POST /api/v1/shop/orders/{ref}/cancel` | seulement `AWAITING_PAYMENT` |
| `GET /api/v1/shop/rentals/{contract}/lots/{lot}/{version}` | lot scellé (Range/ETag), **seulement** pour le jeton d'appareil qui a commandé **ou** un appareil de la même licence |
| `POST /api/v1/shop/vouchers/redeem` `{request, code}` | sans commande préalable : crée la commande de l'article du bon et l'exécute ; 5 essais par appareil et par heure, verrou 1 h après 10 échecs (par appareil **et** par licence) |
| `GET /api/v1/shop/me` `{request}` | état : commandes récentes, contrats actifs, solde de jetons, bons de jetons non livrés (réémis à la demande), reçus |
| `POST /api/v1/shop/tokens/report` `{request, spends: [...]}` | journal de dépenses de la TV (§ 6.5) ; réponse : bons à installer, anomalies |
| `GET /api/v1/shop/receipts/{code}` | reçu signé (public, par code) |
| `POST /api/v1/agent/orders/confirm` `{agentDelegation, ledgerEntry}` | le point focal confirme une commande en espèces (§ 5.2) |
| `GET /api/v1/agent/vouchers/stock` | ses lots de bons et leur état (vendu / redimé / révoqué) |
| `/admin/shop/**` | console : commandes, paiements, contrats, bons (import d'un lot signé, révocation d'un lot ou d'un bon avec TOTP), jetons (soldes, grand livre, ajustement motivé avec TOTP), reçus, remboursements (TOTP), CSV |

### 4.5 Flux « louer un bouquet » depuis le téléphone (séquence)
1. **Vitrine** (hors ligne possible) : `ShopScreen` lit le catalogue et la grille gardés (`ServerBundleCatalog`, `PriceGrid`), le profil de l'élève (classe) et le manifeste de la TV liée (`GET /api/lots`, `GET /api/rental`) : « CM2 : 2 lots, 4,2 Mo, 30 jours, X XAF, aperçu gratuit déjà sur la TV ».
2. **Lire la TV** (à portée, PIN / téléphone de confiance) : `GET /api/activation/request` (demande v2) + **nouvelle route** `GET /api/activation/proof` (le jeton d'activation de production qui compte, PIN) + `GET /api/rental`. Hors de portée : la demande **gardée** lors du dernier contact sert (le téléphone la met en cache à chaque liaison : `TvShopCache`), avec l'avertissement « d'après le dernier contact avec la TV ».
3. **Devis** (en ligne) : `POST /shop/quote` → prix, « renouvellement de votre location en cours (fin le …) » ou « nouvelle location ».
4. **Payer** : écran « Comment payer » : **(a) « Saisir un code de recharge »** (16 caractères, contrôle local, puis `POST /shop/orders` avec le bon) ; **(b) « Payer en espèces chez votre point focal »** → `POST /shop/orders` sans paiement → **code de commande** en grand + partage (WhatsApp/SMS) + « valable 72 h ».
5. **Attente** (b) : le téléphone interroge `GET /shop/orders/{ref}` à l'ouverture de l'écran et par la tâche périodique existante (`LotsRuntime` 12 h / réseau non facturé ; **plus** un rafraîchissement toutes les 15 min pendant 72 h si l'app est ouverte) ; notification locale « Commande CB-… confirmée ».
6. **Récupérer** : activation + lots scellés (Wi-Fi, reprise, SHA-256) dans `files/shop/sealed/<contrat>/` + catalogue signé de preuve.
7. **Livrer à la TV** : file de livraison existante (`DeliveryQueue` / `RentalDelivery.deliver`) : d'abord `POST /api/activation/install` (activation de location), puis `/api/rental/install` par lot ; Bluetooth CBT1 ou Wi-Fi local. État visible : « Payée · Clé reçue · À livrer à la TV (TV hors de portée) · Livrée ».
8. **Accusé** : `POST /shop/orders/{ref}/delivered` ; reçu rangé dans « Mes reçus ».

### 4.6 Fenêtre d'essai des lots, servie par le serveur
À la première `GET /shop/me` d'une TV **en essai** (preuve = activation `trial` qui compte) ou **en production sans contrat**, si `(licence, installPub)` n'a jamais eu de contrat `essai`, le serveur crée une commande `TRIAL_LOTS` à 0 XAF et un contrat `essai|tout`, 3 j / 720 min, **une fois par (licence, installation)** et **une fois par (code d'appareil)** sur 12 mois (anti-réinstallation ; `AbuseService` alerte). Les lots d'essai étant **en clair**, ce contrat n'a rien à sceller : il sert à ouvrir la **consultation** des lots loués d'essai comme aujourd'hui (`TvGate` lit `rentals`). Le téléphone le livre comme une location.

### 4.7 TV en ligne (Wi-Fi direct) : même flux, sans téléphone
`R/shop/ShopClient.kt` (TV) appelle les **mêmes routes** avec le jeton d'appareil de la TV (`TvConnect`, déjà enregistré pour le battement de cœur) **seulement** quand l'utilisateur ouvre la Boutique ou tape un bon : jamais au démarrage, jamais en tâche de fond (principe hors ligne). Les lots scellés sont téléchargés par la TV elle-même **dans son budget de 10 Mo** (`TvLotStore` refuse sinon : la boutique le dit avant de commander : « il manque X Mo, retirez … »). Exception explicite au principe « la TV ne télécharge jamais un lot » de LOTS.md § 1, documentée : *la TV ne télécharge que ses propres locations commandées, à la demande de l'utilisateur*.

### 4.8 Expiration, suppression, hors ligne : inchangés
`RentalEngine`/`RentalSweeper` font foi sur la TV ; le serveur ne révoque pas un contrat livré (il peut refuser le renouvellement et marquer `REVOKED` pour le comptable ; une révocation de poste `cbr1` reste le levier existant). Consommer une location livrée **n'exige jamais** le serveur.

## 5. Paiement en espèces (P5) : bons de recharge et commandes confirmées

### 5.1 Comparaison et recommandation

| | (a) Bon de recharge (code prépayé) | (b) Commande en espèces confirmée par le point focal |
|---|---|---|
| Moment où l'agent intervient | **avant** (il a vendu le bon, n'importe quand) | **après** la commande (le client vient le voir avec un code) |
| Agent en ligne nécessaire | non (le bon se redime en ligne par le **client**) | non pour encaisser ; **oui pour que la livraison parte** (la confirmation se synchronise avec son journal ; hors ligne, le client attend la prochaine synchronisation de l'agent) |
| Logistique | impression / distribution de cartes, stock, perte, vol (lot révocable) | aucune impression ; quotas et grille de la délégation |
| Fraude principale | code deviné (65 bits + verrou : négligeable), **vol de stock** (révocation de lot, serial vs journal des ventes) | agent qui confirme sans encaisser (**son compte est débité** : solde dû = Σ confirmations ; anomalie si écart) |
| Expérience client | familier (recharges téléphoniques), immédiat, **marche sur la TV seule** si elle a Internet | deux allers (commander, payer) ; mais aucune carte à acheter d'avance |
| Comptabilité | vente du bon = ligne `SALE item=bon|…|serial` dans le journal d'agent ; redemption indépendante | ligne `SALE item=commande|<ref>|<montant>` ; le serveur rapproche avec la commande |

**Recommandation : (a) le bon de recharge est le chemin principal** (cohérent avec « espèces à un point focal », aucun temps réel entre agent et serveur, utilisable sur une TV en ligne sans téléphone, limite naturelle des montants) ; **(b) est construit aussi**, comme complément quand le point focal est présent ou que les bons manquent ; les deux passent par la **même** abstraction `Payment`. Les écrans disent : **« Saisir un code de recharge »** (a) et **« Payer en espèces chez votre point focal »** (b).

### 5.2 Les deux flux, précisément
**(a) Bon** : saisie (téléphone ou TV) → contrôle local (format, caractère de contrôle) → en ligne `POST /shop/vouchers/redeem` ou `POST /shop/orders` avec `payment.method=VOUCHER` → le serveur : `code_hash` connu, `ISSUED`/`SOLD`, lot non révoqué ni expiré, article du bon = article demandé (un bon `jetons|60` crédite 60 jetons ; un bon `loc-cm2|30` loue CM2 30 j **pour la TV de la demande** ; un bon `cle-production|90` émet une clé de 90 j pour la TV de la demande, via `ActivationService.issue` existant, licence auto) → `REDEEMED` + `shop_payment(VOUCHER, serial)` + exécution. **Hors ligne sur la TV** : le code est **mis en attente** (`files/shop/pending-voucher.txt`, un seul à la fois, non affiché ensuite) et envoyé par la **passerelle du téléphone** à la prochaine liaison (le téléphone relaie `POST /shop/vouchers/redeem` avec la demande de la TV ; la TV reçoit le résultat par les routes TV). Un bon **n'est jamais vérifiable hors ligne** (c'est voulu : la valeur est au serveur).
**(b) Commande** : `AWAITING_PAYMENT` + code `CB-XXXX-XX` → le client paie le point focal → l'app agent (w4-13 réduit) : « Confirmer une commande » : saisit le code (ou scanne / colle), **le serveur lui renvoie le détail** (article, montant de la grille, code d'appareil masqué `XXXX-…-XXXX`) s'il est en ligne ; il encaisse, l'app écrit `SALE item=commande|<ref>|<montant>` dans le **journal chaîné** (W4-C § 4) et envoie `POST /api/v1/agent/orders/confirm` (délégation + entrée signée) ; hors ligne, l'entrée part à la prochaine synchronisation (`/agent/sync` traite aussi ces lignes). Le serveur vérifie : délégation valide, `may_confirm_orders`, montant = `amount_xaf` de la commande, quotas (`max_confirm_xaf_per_day`, `maxSales`), commande encore `AWAITING_PAYMENT` → `PAID` → exécution → `FULFILLED`. L'argent est **dû par l'agent** (W4-C § 4 `REMIT`, inchangé). Le client reçoit la livraison à sa prochaine connexion (téléphone) ; la TV en ligne peut aussi la tirer (`GET /shop/orders/{ref}` depuis la Boutique TV « Vérifier ma commande »).

### 5.3 Outil du propriétaire : fabrication des bons (`tools/vouchers/`)
`make_vouchers.py --item jetons|60 --count 500 --expire 2028-10 --agent <kid|-> --key <KeyFile des mises à jour>` → trois fichiers : `batch-<id>.signed.txt` (empreintes, **à importer sur le serveur** : `/admin/shop/vouchers/import`), `batch-<id>-CODES-SECRET.csv` (serial, code : **à imprimer puis détruire**, jamais commité ni envoyé), `batch-<id>-stock.csv` (serials seuls, à remettre à l'agent avec les cartes). Génération : `secrets.token_bytes`, codes **non dérivés** d'un secret (pas de formule à voler) ; `--check` revérifie la signature ; tests Python. Impression : hors périmètre (mise en page CSV → imprimeur ; recommandation : zone à gratter ou enveloppe scellée ; serial visible).

### 5.4 Stock et comptabilité des agents (réutilise W4-C § 4 et § 8)
- Nouvelles valeurs d'`item` du journal : `bon|<article>|<serial>` (vente d'un bon : `price` = grille de l'article, `cash` encaissé) ; `commande|<ref>|<montant>`.
- Le serveur rapproche : **bon redimé jamais vendu** (serial sans `SALE` à la synchronisation suivante + 7 j) ⇒ anomalie `VOUCHER_UNSOLD_REDEEMED` (vol de stock ou vente non journalisée) ; **bon vendu par un agent auquel le lot n'est pas affecté** ⇒ `VOUCHER_WRONG_AGENT` ; **bon vendu deux fois** ⇒ `VOUCHER_DOUBLE_SALE` ; **confirmation d'une commande déjà payée** ⇒ refus ; **confirmations > quota** ⇒ refus + anomalie.
- **Révocation** : d'un lot entier (`voucher_batch.revoked_at`, TOTP, motif : vol) ou d'un bon ; les bons `REDEEMED` restent redimés (le client n'est pas puni) ; `/admin/shop/vouchers` montre par lot : émis / vendus / redimés / révoqués / expirés et le **taux de redemption** par agent.
- Solde dû d'un agent = Σ cash (clés + bons + commandes) − Σ remboursements − Σ versements confirmés (formule W4-C inchangée, items en plus).

### 5.5 Abstraction neutre (pour un moyen de paiement ultérieur, **sans aucun travail ici**)
`Payment(method, ref, amountXaf, at, confirmedBy)` et une interface serveur `PaymentMethod { boolean settles(Order, PaymentProof) }` avec **deux** implémentations : `VoucherPayment`, `AgentCashPayment` (+ `OwnerGrant` interne : gratuités, réémissions, gestes commerciaux avec TOTP et motif). Aucune autre ; aucune dépendance HTTP tierce ; aucun champ « numéro marchand ».

## 6. Jetons virtuels du Quiz (P4)

### 6.1 Principes (le modèle le moins prédateur, tranché ici)
1. **La base du Quiz reste gratuite et complète** : Millionnaire solo et avec le public, Duel, Entraînement, les **trois jokers standard** (50:50, Avis du public, Appel à un ami : une fois chacun), meilleurs scores, 300 parties sans répétition, lots de questions. Aucun jeton n'est requis pour **entrer** dans une partie, un mode, un parcours ou une salle.
2. **Les jetons achètent des commodités de la partie Millionnaire uniquement** (trois articles, prix en jetons fixés dans la grille signée, valeurs par défaut) : **« Seconde chance »** (après une mauvaise réponse, continuer la partie depuis la question ratée, **une fois par partie**, 5 jetons) ; **« Joker en plus »** (rejouer **un** 50:50 ou un Avis du public déjà utilisé, au plus **deux** par partie, 2 jetons) ; **« Changer de question »** (une fois par partie, 3 jetons). Les gains affichés restent **fictifs** (échelle FCFA du jeu, inchangée) ; les records obtenus avec une seconde chance sont marqués « avec jetons » et n'écrasent pas un record sans jetons (comme l'indice du Sudoku).
3. **Interdits (anti-prédation, anti-jeu de hasard)** : pas de mise redistribuée en jetons achetés (**le mode « Compétition avec mise » garde ses « points de défi » gratuits, sans valeur, non achetables, et le `VirtualWallet` est renommé/séparé** : `C/quiz/Wallet.kt` ne touche jamais au porte-jetons payant) ; pas de gain réel ni de conversion en argent ni de cadeau ; pas de hasard sur ce que donne un jeton (prix fixes, effet certain) ; pas d'énergie, de minuterie ou de pénalité qu'un jeton lèverait ; pas de rareté artificielle ni d'offre « expire dans 10 min » ; pas de dépense en un clic : **toujours une confirmation** (« Utiliser 5 jetons ? Solde : 23 ») ; pas de jetons offerts par le jeu pour créer une boucle (**un seul** geste d'accueil : 10 jetons à la première activation de production, inscrits au grand livre comme `CREDIT welcome`, désactivable par la grille) ; pas de sollicitation d'enfant.
4. **Compte = licence** (le téléphone et la TV sont deux postes de la même licence) ; le solde serveur est **unique** par licence ; les jetons sur la TV sont des **bons livrés** (§ 6.5), le reste est « au serveur ».
5. **Expiration : aucune** (recommandé : plus simple à expliquer, plus respectueux ; la grille permet `tokens.expiryDays` si le propriétaire tranche autrement, affiché partout). **Non remboursables** une fois dépensés ; non dépensés : remboursables **par le propriétaire** en cas de litige (console, TOTP), jamais automatiquement ; un paquet acheté par erreur est remboursable **tant qu'aucun jeton n'en a été dépensé** et dans les 7 jours (geste CGV, § 11).

### 6.2 Ce que voit l'édition d'essai
Le Quiz est **fermé** en essai (`TrialPolicy` : streaming + Sudoku + lots d'essai). Décision : l'essai **voit la Boutique en lecture** (catalogue, prix, « disponible avec une clé de production ») et peut **jouer une « partie découverte » du Quiz** : Millionnaire solo, parcours Culture générale, **sans jokers payants ni jetons**, limitée à **3 parties par jour** (`TrialPolicy.quizTasterPerDay = 3`, compteur local ; `/quiz` reste fermé aux téléphones : pas de salle en essai). Le Quiz passe donc dans `TrialPolicy` de « fermé » à « découverte » : nouveau drapeau `Feature.QUIZ_TASTER` ouvert en essai, `QUIZ` complet fermé ; `routes.txt` : `/api/quiz` ouvert en lecture, `/quiz/*` fermé. **Aucun achat de jetons en essai** (bouton grisé « Passez en version complète pour acheter des jetons »). Renversable d'une ligne.

### 6.3 Mode réduit (W4-B) et jetons
En mode réduit le Quiz est fermé (W4-B § 2) : les jetons **restent** au solde, visibles dans la Boutique (« vos N jetons vous attendent : renouvelez la clé »), aucune dépense, aucun achat de jetons (la commande de clé passe par le point focal) ; les locations en cours continuent ; la Boutique reste **consultable** et permet de **taper un bon `cle-production`** (sortie du mode réduit en ligne : le serveur émet la clé de la durée du bon pour cette TV : `ActivationService.issue`, licence retrouvée par la preuve de l'activation terminée).

### 6.4 Flux d'achat de jetons
Téléphone ou TV : Boutique > Jetons > paquet (grille) > paiement (§ 5) → le serveur crédite le grand livre (`CREDIT`) **et** prépare un **bon de jetons** pour la TV de la demande : `GRANT` = `min(solde disponible, tokens.offlineGrantMax)` (défaut 60), enveloppe `tokens` (§ 3.4 c) ; le reste attend au serveur et est **réapprovisionné** à chaque contact (`/shop/me` ou `/shop/tokens/report` : si la TV a dépensé, un nouveau bon part pour revenir au plafond). Le téléphone pousse le bon : `POST /api/tokens/install` (TV, PIN / téléphone de confiance), la TV vérifie (clé serveur, cible, `install`, `grant` > dernier) et crédite son porte-jetons. La TV en ligne tire ses bons elle-même (§ 4.7).

### 6.5 Dépense hors ligne, réconciliation, anti-fraude (dit honnêtement)
- **Dépense** (`C/tokens/TokenWallet.kt`, pur, testé) : `spend(game="quiz", item, cost, now)` → ligne `spend` chaînée + HMAC ; `balance()` ; `report()` = lignes depuis le dernier accusé. Appelée par `QuizGame` via une interface `QuizBoosts { fun canAfford(item): Boolean; fun buy(item): Boolean }` ; l'écran confirme toujours.
- **Réconciliation** : à chaque contact (téléphone-passerelle : `GET /api/tokens/report` TV → `POST /shop/tokens/report` serveur → réponse (bons, accusé) → `POST /api/tokens/install` TV), le serveur **inscrit** chaque `spend` (`SPEND`, idempotent par `(licence, device, seq)`), compare à ses `GRANT` : dépenses rapportées > bons livrés ⇒ **anomalie `TOKEN_OVERSPEND`** (impossible sans altération) ; **séquence qui recule** (une TV rapporte `seq` 12 puis 7 : restauration d'une copie) ⇒ `TOKEN_REPLAY` ; la réponse du serveur porte le `seq` accusé ; la TV n'affiche jamais un solde supérieur à Σ grants − Σ spends locaux.
- **Clonage / restauration** : une copie de `wallet.txt` sur une autre installation ne vaut rien (HMAC lié à `installPriv`, protégée par le Keystore, W4-A) ; sur la **même** installation, restaurer un fichier antérieur rejoue au plus les jetons des bons déjà livrés (**≤ `offlineGrantMax` = 60 jetons**, soit le prix de quelques commodités) : c'est la perte maximale assumée, détectée (`TOKEN_REPLAY`) et sanctionnée par le serveur en **cessant d'envoyer des bons hors ligne** à cette installation (dépense en ligne seulement, « porte-jetons à resynchroniser »). TV rootée : même limite ; aucune mesure hostile (protect-* : dégrader, tracer).
- **Battement de cœur** (protect-05) : la TV ajoute `tokensSeq`, `tokensMac` (16 hex) et `walletState` (`ok|unreadable|absent`) ; le serveur recoupe avec `token_grant.spent_reported`.
- **Double dépense en ligne** : le serveur sérialise par licence (verrou de ligne `token_account`) ; `reserved` couvre les bons livrés non encore rapportés : solde affiché au téléphone = `balance − reserved` + « dont N sur la TV ».

### 6.6 Enfants, parents, consommateurs (indicateurs, pas un avis juridique)
- **ParentalHub** : nouvelle catégorie bloquable **« Achats et jetons »** (bloquée **par défaut** pour tout profil enfant) ; **profil enfant actif ⇒ aucune commande, aucune saisie de bon** (écran « Demandez à un parent » avec « Saisir le code parental ») ; **dépenser des jetons** avec un profil enfant : autorisé jusqu'à une **allocation quotidienne** réglée par le parent (`kidDailyTokens`, **0 par défaut** = code parental à chaque dépense), compteur par profil et par jour, visible dans le rapport parental (« jetons dépensés : 7 »). Le profil adulte / sans profil : dépense libre avec confirmation ; **achat** (commande, bon) : jamais sous profil enfant, et **« PIN pour acheter »** activable par le parent pour tous les profils (recommandé actif par défaut dès qu'un code parental existe).
- **Indicateurs à faire valider par un juriste** (Cameroun / OHADA / magasins d'applications ; cahier w5-22) : (1) **jeu de hasard et loterie** : la mise redistribuée en jetons achetés tomberait sous la réglementation des jeux d'argent (Cameroun : loi sur les jeux de divertissement, d'argent et de hasard ; agrément) : **exclue par conception** (§ 6.1.3) ; les commodités à effet certain ne sont pas un jeu de hasard ; (2) **monnaie virtuelle / prépayé** : les jetons sont un **droit d'usage prépayé non convertible** ; absence d'expiration et remboursement du non-dépensé réduisent le risque « clause abusive » (droit de la consommation OHADA/Cameroun : information précontractuelle, prix TTC en XAF, confirmation avant dépense) ; (3) **mineurs** : incapacité contractuelle des mineurs : le **titulaire de la licence** (adulte) est l'acheteur ; blocage par défaut des profils enfant, code parental, allocation, journal des dépenses visible du parent, pas de sollicitation ; (4) **magasins d'applications** (si un jour) : règles sur les achats intégrés (Google Play exige sa facturation pour les biens numériques consommés dans l'app : **à étudier avant toute publication Play Store** ; aujourd'hui distribution directe par le propriétaire : non applicable) et sur les loot boxes (aucune) ; (5) **TVA et facturation** : reçu avec montant, article, date, vendeur (D7) ; (6) **données** : registre des traitements : code d'appareil, licence, historique d'achats, dépenses de jetons (pas de nom d'enfant côté serveur : les profils restent locaux, PARENTAL.md).
- **Accessibilité** (TV) : jamais la couleur seule, montant en chiffres **et** en toutes lettres pour une confirmation (« cinq jetons »), focus visible, textes ≥ 28 px à 720p, lecture par le lecteur d'écran (contentDescription), 3 m de recul ; téléphone : TalkBack, tailles dynamiques.

## 7. Boutique : UX téléphone (CastBridge)

Onglet / entrée **« Boutique »** (remplace l'entrée « Locations », `S/MainActivity.kt` ; `RentalDeliveryActivity` devient « Livraison avancée » dans ⋯, comme prévu par w4-13). Sections (Compose, `S/shop/**`) :
1. **Leçons à louer** : filtre par classe (profil), cartes bouquet : titre, « 2 lots · 4,2 Mo », « 30 jours », prix, « Aperçu gratuit : inclus » (badge si les lots `-trial` sont là), état : « En location jusqu'au … (renouveler) » / « Louer ». Détail : contenu (matières), ce que l'essai montre déjà, durée exacte, « ne peut pas être prolongé au-delà de 60 jours ».
2. **Jetons du Quiz** : solde (« 23 jetons · dont 20 sur la TV »), paquets de la grille, « À quoi servent les jetons ? » (texte des trois commodités, « le Quiz reste gratuit sans jetons »).
3. **Payer** (écran commun) : « Saisir un code de recharge » (16 cases, O/0 I/1 tolérés, contrôle local) ; « Payer en espèces chez votre point focal » (code `CB-XXXX-XX`, partage, « valable 72 h », « Point focal le plus proche : contact du propriétaire » D7).
4. **Mes commandes / Mes reçus** : liste, statut, « Livrer à la TV » (file), reçu partageable (texte + code `R-…`), « Vérifier » (en ligne).
5. **Ma TV** : la TV liée, dernier contact, demande en cache, locations en cours (compte à rebours), jetons livrés.
Hors ligne : tout est lisible depuis le cache ; les boutons de commande disent « Connexion nécessaire pour commander » ; un bon saisi est **gardé** et envoyé à la prochaine connexion.

## 8. Boutique : UX TV (D-pad, bas de gamme, 720p)

Tuile **« Boutique »** sur l'accueil (`homeTools`, après « Jeux » ; `ic_t_shop`), `R/shop/ShopActivity.kt` (vues classiques, grille `Dx` 1920×1080 comme `GamesUi`, focus explicite, aucune WebView). Bandeau haut : état réseau « En ligne » / « Hors ligne : commandez depuis CastBridge sur votre téléphone (code TV XXXX-XXXX-XXXX-XXXX) ». Colonne gauche (5 entrées, ↑↓) : **Leçons à louer**, **Jetons du Quiz**, **Code de recharge**, **Mes locations**, **Mes reçus**. Panneau droit : cartes (← → OK). Détail d'un bouquet : contenu, durée, prix, « Aperçu gratuit » (ouvre Apprendre sur le lot d'essai), bouton **« Louer (prix) »** → si en ligne : écran « Payer » (**Code de recharge** → clavier à l'écran 5×7 Crockford + chiffres, saisie par groupes de 4, contrôle local, « Valider » ; **Espèces chez votre point focal** → code `CB-XXXX-XX` en très grand + « Montrez ce code à votre point focal ; la location arrivera dès qu'il aura confirmé ; vous pouvez éteindre la TV » + « Vérifier ma commande ») ; si hors ligne : « À commander depuis le téléphone » + le bon peut quand même être tapé et **mis en attente**. **Jetons** : solde en grand, « sur cette TV : 20 · au serveur : 3 », paquets, même écran « Payer ». **Mes locations** : compte à rebours existant (`RentalEngine` phrases), « Renouveler ». **Mes reçus** : code, date, article, montant. Le **badge** de clé (`KeyBadge`) reste visible. Télécommande : Retour ferme ; touches couleur : rouge Annuler, verte Valider, jaune « Code de recharge », bleue « Aide ». Essai : lecture seule (§ 6.2) ; mode réduit : lecture + bon `cle-production` (§ 6.3) ; profil enfant : « Demandez à un parent » (§ 6.6) ; `LOCKED` : la tuile n'existe pas (liste blanche inchangée).

## 9. Interactions : licence, essai, mode réduit, protection, tunnel, télémétrie

- **Licence / postes** : une location est émise pour `(licence, poste)` de la preuve ; `maxConcurrent` par licence appliqué par le serveur (défaut 3 contrats actifs) et par appareil (TV). Un téléphone et une TV de la même licence partagent le compte de jetons. Transfert de poste (propriétaire) : les contrats suivent la licence ; la boîte doit être réémise pour la nouvelle installation (§ 4.2 : réémission gratuite).
- **Essai** : Boutique en lecture, « partie découverte » du Quiz (§ 6.2), fenêtre d'essai des lots servie par le serveur (§ 4.6) ; **aucune commande** (ni location, ni jetons) : « Passez en version complète » (le point focal vend la clé). Renversable : autoriser la location en essai = une ligne (`ShopPolicy.trialMayOrder`).
- **Mode réduit** : § 6.3 ; `/api/rental/install` reste fermé (W4-B) ; le serveur refuse `loc-*` et `jetons` à une preuve terminée (« clé à renouveler ») et accepte un bon `cle-production`.
- **Protection** (PROTECTION-TV-FABLE § 3) : couche 1 renforcée : locations, bons, reçus signés serveur ; boîte v2 ; porte-jetons HMAC-installation ; contrôle d'intégrité de signature APK (protect-03) consulté avant d'installer un bon (refus doux + message) ; heartbeat (protect-05) : `tokensSeq`, `walletState`, `shopCatalogAt` ; anomalies serveur : `TOKEN_OVERSPEND`, `TOKEN_REPLAY`, `RENTAL_REISSUE_ABUSE`, `VOUCHER_BRUTE_FORCE`, `TRIAL_LOTS_REPEAT`. **Rien de destructeur**.
- **Tunnel / conditions** : la Boutique n'ouvre pas le tunnel ; l'écran « Payer » renvoie aux CGV (lien « Conditions de vente ») ; acceptation des CGV **à la première commande** sur chaque appareil (date gardée localement et dans `shop_order.terms_version`).
- **Télémétrie / vie privée** : événements agrégés seulement avec consentement « statistiques » : `shop_view`, `shop_order{kind}`, `tokens_spend{item}` (jamais le code d'appareil dans l'événement : déjà la règle) ; le serveur boutique, lui, tient des données nominatives techniques (code d'appareil, licence, montants, serial de bon) : registre des traitements (w5-22), rétention 5 ans comptable pour commandes/paiements, 12 mois pour les journaux de dépenses, effacement du code d'appareil à la purge d'un appareil (`devices` existant).
- **Ordres différés** (`type=order`, `POLICY`) : utilisés en complément pour `rights.refresh` après une confirmation (déjà dans `PolicyCatalog`) : le téléphone qui tire ses ordres apprend qu'une commande est prête sans sondage de la boutique (optimisation, cahier w5-13 l'utilise si simple, sinon sondage).

## 10. Sécurité et fraude : analyse (résumé)

| Menace | Contre-mesure | Résiduel |
|---|---|---|
| Forger une location / un bon de jetons en rapiéçant la TV | signés par la clé serveur (clé publique compilée), boîte v2 vers la clé d'installation : le clair d'un lot loué n'existe qu'ouvert par cette installation | TV rootée pendant la location (inchangé, RENTAL-LOTS § 11) |
| Lire un lot loué ailleurs (copie, sauvegarde) | boîte v2 + `LotCrypt` au repos + `allowBackup=false` (W4-A) | idem |
| Deviner un bon | 65 bits + contrôle local + 5 essais/h/appareil, verrou, alerte | négligeable |
| Vol de cartes | serial public vs journal d'agent, révocation de lot, taux de redemption par agent | un lot volé non signalé s'épuise au prix d'un lot |
| Agent qui confirme sans encaisser | il doit le montant au propriétaire (grand livre), quotas journaliers, anomalies | risque commercial, pas technique |
| Serveur compromis | ne peut ni changer les prix/catalogue (signés hors ligne) ni créer de bons vendables (lot signé hors ligne) ; **peut** créditer des jetons et émettre des locations : journal d'audit chaîné (`AuditLog` existant) + rapprochement commandes/paiements ; KEK en fichier séparé | assumé ; sauvegardes et alertes (w1-12) |
| Rejeu de jetons hors ligne (restauration) | plafond 60 jetons par bon, séquence + HMAC-installation, `TOKEN_REPLAY` ⇒ plus de bons hors ligne | perte bornée |
| Double livraison / idempotence | `Idempotency-Key`, `UNIQUE(licence, poste, produit, period)`, `grant_seq`, `UNIQUE(method, ref)` | — |
| Commande payée, livraison jamais faite | commande `FULFILLED` réclamable 90 j (`/shop/me`), réémission gratuite de la boîte, reçu | — |
| Enfant qui achète | profil enfant = jamais ; PIN pour acheter ; allocation | enfant qui connaît le code parental (limite PARENTAL.md) |
| Fuite de la base des bons | empreintes seulement : inutilisable | les codes clairs ne vivent que sur le papier |

## 11. Textes contractuels nécessaires (brouillons, w5-22 ; validation juriste)
`docs/legal/CGV-boutique.md` : objet (location de contenus pédagogiques à durée fixe, jetons du Quiz = commodités sans valeur monétaire, clés), prix TTC XAF de la grille, moyens de paiement (espèces chez un point focal, bons de recharge), durée exacte et non prolongeable au-delà de 60 j, pas de remboursement d'une location commencée sauf défaut, remboursement d'un paquet de jetons non entamé sous 7 j, jetons sans expiration et non convertibles, mineurs, réclamations (code de reçu, contact D7), données. Clause point focal (mandataire d'encaissement). `docs/legal/BON-DE-RECHARGE.md` : conditions imprimées au dos (validité, un seul usage, pas de remboursement en espèces, perte = perte). Mentions dans « À propos » > « Boutique et jetons ».

## 12. Effort, coût, risques

| Bloc | Effort (agent·jours) | Coût d'exploitation |
|---|---|---|
| Cœur (formats, porte-jetons, commandes, bons, vecteurs, Java/Python) | ≈ 9 | — |
| Serveur (locations en ligne, boutique, bons, jetons, console, essai) | ≈ 13 | disque ≤ 4 Go de cache, CPU négligeable, +1 secret (`rental-kek.key`) |
| Téléphone (boutique, passerelle, jetons, agent réduit) | ≈ 11 | — |
| TV (boutique D-pad, routes, porte-jetons, Quiz, parental, heartbeat) | ≈ 12 | APK +≈ 150 Ko (vues, textes) |
| Docs, CGV, campagne, outils propriétaire | ≈ 7 | impression des bons (hors périmètre) |
| **Total** | **≈ 52 j** (24 cahiers, 5 sous-vagues) | |

**Risques** : (1) clé publique du serveur absente des `TRUSTED_KEYS` de la TV ⇒ aucune location en ligne n'est acceptée : vérification au premier déploiement et procédure dans RELEASES ; (2) `TRUSTED_KEYS` du serveur (`castbridge.licenses.trusted-keys`) sans les clés des agents ⇒ une TV activée par un point focal ne peut pas prouver sa clé : le serveur apprend les délégations à la synchronisation (W4-C) : **ordre de déploiement** 4c avant 5b ; (3) UX de saisie à la télécommande (16 caractères) : testée sur la TV de référence, repli « depuis le téléphone » toujours affiché ; (4) budget TV 10 Mo : la Boutique dit avant de commander ce qui ne tiendra pas ; (5) horloge de la TV pour les bons (`notBefore`/`expiresAt` 30 j) : règle `TvClock` existante ; (6) juridique (jetons, mineurs) : brouillons à valider avant mise en vente ; (7) réconciliation des jetons tolérante aux coupures : tests de rejeu obligatoires (idempotence) ; (8) dépendances : 4a (clé d'installation) **indispensable** ; 4b, 4c (délégation, journal) nécessaires pour le chemin (b) et les clés d'agents.

## 13. Décisions prises ici (renversables) et questions au propriétaire

**Prises par l'architecte** : chemin principal = bon de recharge ; aucun maître partagé, clé aléatoire par contrat sous KEK serveur ; fenêtre d'essai des lots servie par le serveur (plus dans la clé d'essai) ; location en ligne ≤ 60 j, 3 contrats actifs par licence ; jetons : trois commodités du Millionnaire, prix par défaut 5/2/3, sans expiration, 10 jetons d'accueil, plafond hors ligne 60, mise redistribuée **jamais** en jetons achetés ; essai = boutique en lecture + partie découverte 3/jour, aucune commande ; mode réduit = lecture + bon de clé ; profils enfant : pas d'achat, allocation 0 par défaut ; commande en espèces valable 72 h ; réémission gratuite de boîte ≤ 3 par contrat ; code de commande `CB-XXXX-XX`, bon `CB-` 16 caractères ; module serveur éteint par défaut ; réception TV directe à la demande seulement.

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **D-W5-1** | Tailles des paquets de jetons et prix XAF ; prix en jetons des trois commodités si différents de 5/2/3 ; jetons d'accueil (10 ?) | grille réelle (w5-24), CGV | 20 / 60 / 150 jetons ; garder 5/2/3 ; 10 d'accueil |
| **D-W5-2** | Dénominations des bons de recharge à imprimer (jetons 60 ? location 30 j d'un bouquet ? clé 90 j ?) et premier tirage | fabrication (w5-24), fiche agent | commencer par `jetons|60` et `loc-<bouquet>|30` pour 3 bouquets phares, 200 bons chacun |
| **D9-bis** (W4) | montants des clés et des locations par bouquet | grille | inchangé |
| **D7** (W4) | nom commercial, contact (reçus, « point focal le plus proche ») | textes | inchangé |

## 14. Ce qui change par rapport aux conceptions W4

| Conception / cahier W4 | Statut | Changement |
|---|---|---|
| **W4-A** (w4-01…06) | **conservée**, prérequis | aucune modification de fond ; w4-06 (docs) doit écrire RENTAL-LOTS § 15 « serveur » comme ici (ou le laisser à w5-21) ; `RentalKeys.masterFrom` reste pour tests/vecteurs seulement ; le texte « le serveur n'émet pas de location » (W4-A § 4, § 6 et w4-05) est **faux désormais** : w5-06 l'implémente |
| **W4-B** (w4-07…10) | **conservée** | ajouts : `Feature.SHOP` (ouvert en réduit, lecture), `QUIZ_TASTER` ; `DegradedPolicy.routeAllowed` ouvre `/api/shop/*` en lecture et `/api/tokens/report` ; `/api/rental/install` reste fermé |
| **W4-C** § 1, § 5, § 7.3, § 11 | **remplacés** | l'agent vend des **clés** (+ bons, + confirmation de commandes) ; **pas de location, pas de maître, pas de scellement** ; `maxRentalDays` toujours 0 ; `master=`/`agentx=` **retirés** de la délégation ; `FocalSealer` et `FocalDelivery` (lots) **supprimés** du cahier w4-13 ; `OwnerStore.master=`/`exportMaster` et le repli `MASTER_SWITCH_MS` **supprimés** de w4-12 ; `maitre importer/exporter` du bureau supprimés |
| W4-C § 3 (délégation) | amendé | champs `master=`, `agentx=` disparaissent ; nouveaux champs optionnels `confirmOrders=1`, `sellVouchers=1`, `maxConfirmXafPerDay=<n>` ; `maxRentalDays` fixé à 0 (vérifié) |
| W4-C § 4 (journal) | amendé | items `bon\|…`, `commande\|…` ; `REFUND` d'une commande = acte du propriétaire |
| W4-C § 6 (grille) | amendé | lignes `jetons\|<n>\|<xaf>` et réglages `tokens.*` |
| W4-C § 8 (serveur) | amendé | colonnes de délégation, `/agent/orders/confirm`, `/agent/vouchers/stock`, anomalies bons/commandes |
| `sonnet-w2-06`, `sonnet-w2-10` | restent **abandonnés** | remplacés par w5-07/w5-12/w5-13 (la boutique revient, mais en espèces/bons, sans « preuve de paiement manuelle ») |
| `docs/RENTAL-LOTS.md` § 15 | réécrit par w5-21 | questions 1-4 tranchées : § 4 ici |

Les amendements cahier par cahier sont listés dans `docs/agent-briefs/SONNET-WAVE5-INDEX.md` (« Changements aux cahiers w4 »).

## 15. Ancrages dans le code existant (vérifiés le 2026-10-02, pour les exécutants)

| Besoin | Où c'est aujourd'hui | Ce que la vague 5 y fait |
|---|---|---|
| Routes d'extension de la TV | `R/TvService.kt:244-253` : chaîne `ApiExtension(::extraApi).then(RemoteHub…).then(QuizHub.packApi).then(LotsHub.api).then(RentalHub.api)…` ; `ApiExtension` = `C/tv/Device.kt:54` ; garde essai/réduit `R/TvService.kt:258` | `.then(ShopHub.api(this))` ; routes `/api/shop/*`, `/api/tokens/*` ; `GET /api/activation/proof` dans `RentalHub` (`R/RentalHub.kt:71-79`) |
| Liste blanche de l'essai | `C/owner/TrialPolicy.kt:19` (`GAMES = {"sudoku"}`), `:29-43` (routes par défaut fermées) ; `tools/routes/routes.txt` (w1-06) ; mode réduit `C/owner/DegradedPolicy.kt` (w4-07) | `QUIZ_TASTER`, `/api/shop/catalog|state` en lecture, `/api/tokens/report` ; `Games.visible()` (`R/Games.kt:43`) lit la politique |
| Routes TV des locations | `C/lots/RentalApi.kt:10-75` (`GET /api/rental`, `POST /api/rental/install?name&contract`, `/api/rental/sweep`), `R/RentalHub.kt` (`POST /api/activation/install`, **409 si les conditions du tunnel ne sont pas acceptées**) | inchangées ; l'installation d'une activation de location passe par `/api/activation/install` : les conditions d'assistance doivent être acceptées sur la TV (déjà exigé pour toute clé) |
| Livraison téléphone → TV | `C/lots/RentalDelivery.kt:77-139` (`deliver(contract, catalogJson, lots, …)` : `GET /api/rental` d'abord, « envoyez d'abord la clé d'activation ») ; `S/RentalDeliveryActivity.kt` (manuel) ; `S/LotsRuntime.kt` (stock 100 Mo, transports) | `ShopDelivery` : activation **puis** `RentalDelivery.deliver` ; `RentalDeliveryActivity` → « Livraison avancée » |
| Demande d'appareil v2, clé d'installation, boîte v2 | W4-A (w4-01, w4-03) : `OwnerFrames.deviceInfo` + ligne `install=`, `InstallKey`, `RentalKeys.makeBoxV2/openBox`, `R/KeystoreWrapper.kt` | prérequis ; `TokenWallet` dérive sa clé HMAC de la clé privée d'installation via `SecretWrapper` |
| Émission serveur d'une activation | `B/licenses/ActivationService.java:111` (`issue(Actor, IssueRequest, channel)`), `doIssue` `:158` (verrou licence, k parmi n, idempotence, `signer.sign`), droits construits par `rightsOf` `:254` (`purchase`, `subscription`, `usage` seulement) ; `ScopedActivationSigner.SERVER_SCOPES` (`ISSUE_TRIAL, ISSUE_PRODUCTION, REACTIVATE, REVOKE, REGISTRY, POLICY`) | méthode **additive** `issueWithRights(seat, kind, rightsLines, window)` pour la ligne `rental` ; `EnvelopeIssuer` (commandes/ordres/révocations) gagne `tokensGrant(...)` |
| Vérification d'une activation côté serveur | `B/licenses/EnvelopeVerifier.java` (pur), `B/tunnel/TunnelService.java:167-181` (enrôlement : vérifie l'activation comme la TV) ; anneau `B/licenses/TrustedKeys.java` (`castbridge.licenses.trusted-keys`), délégations : `LicenseKeyring.withDelegations` (w4-16) | `ShopRequestVerifier` réutilise ce chemin (preuve d'activation, activation avec ticket d'un agent) |
| Jeton d'appareil (téléphone **et** TV) | `B/devices/DeviceService.java:152` (`authenticate(Authorization)`), `register` `:88` ; côté client `C/device/DeviceClient.kt`, `C/connect/ConnectState.kt:114` (`deviceToken`) | toutes les routes `/api/v1/shop/**` |
| Catalogue de bouquets signé | `C/lots/SignedBundleCatalog.kt:15-40`, `C/lots/EditionPolicy.kt:8` (`Bundle(id, type, lots, title, rawBytes, rentalDays)`), `B/lots/BundleCatalogController.java:44` ; `ServerBundleCatalog.fetch` n'est utilisé **que** par les outils du propriétaire (pas par `S/`) | la boutique du téléphone l'adopte ; la TV le reçoit relayé (`POST /api/shop/catalog`) et le vérifie avec `UpdateKeys.PUBLIC_KEYS` |
| Grille de prix | W4-C § 6, `C/sales/PriceGrid.kt` (w4-11), `GET /api/v1/catalog/prices` (w4-16), `tools/prices/sign_prices.py` (w4-12) | lignes `jetons\|<n>` et réglages `tokens.*` |
| Quiz | `C/quiz/QuizGame.kt:25` (`Joker{FIFTY,AUDIENCE,PHONE}`, `canUse`, `beginJoker`), phases `:46`, `Ladder.DEFAULT` paliers 5 et 10 ; `C/quiz/QuizRoom.kt:44-45` (`Play{FRIENDS,STAKE,PRACTICE}`), mises 50/100/200 « jetons » (`R/QuizActivity.kt:303-343`), `C/quiz/Wallet.kt` (`VirtualWallet(1000)`, `Pot`) ; état JSON `C/quiz/QuizHttp.kt` | `QuizBoosts` (seconde chance, joker en plus, changer de question) ; `Play.STAKE` renommé à l'écran « Défi en points » (unité **« points de défi »**, jamais « jetons ») et **jamais** relié au porte-jetons |
| Jeux et parental | `R/Games.kt:27-58` (`GameDef`, `Games.all`, `visible()`), `R/GamesUi.kt` (`Dx`, `GamesColors`), `R/ParentalHub.kt:208` (`allow(activity, Category.GAMES, …)`), `:268` (`kidHomeActive()`), `:273` (`filterHome`) ; `C/parental/ParentalModel.kt:36` (`Category`), `ParentalEngine.check(c)` `:149` ; **lacune** : `categoryOf`/`categoryOfFeature` ne couvrent ni `games` ni `sudoku` | nouvelle `Category.PURCHASES` (« Achats et jetons »), allocation `kidDailyTokens`, « PIN pour acheter » ; la lacune est corrigée au passage (w5-18) |
| Accueil TV | `R/PlayerActivity.kt:530-608` (`homeTools()`, `tile(feature,…)` passe par `TvConnect.feature` et `ParentalHub.guardTile`), `R/HomeScreen.kt:183-213`, `R/TvCards.kt:258` (`HomeTool`), menu « À propos : Assistance à distance » `R/PlayerActivity.kt:737` | tuile `shop` « Boutique » après `games` ; entrée « À propos : Boutique et jetons » |
| Téléphone | `S/MainActivity.kt:106-131` (actions « Activer la TV », « Locations » ; onglets « TV DLNA », « CastBridge TV », « Jeux », « Sur le téléphone », « Apprendre », « Parental ») ; `S/TvLink.kt:97+` (`TvLinkManager`, jeton de téléphone de confiance) ; `S/LotsRuntime.kt:154` (catalogue de lots) ; `S/LearnScreen.kt:58-62` (sous-onglets) ; `S/GamesScreen.kt:38-42` (`PHONE_GAMES`) ; thème `S/Theme.kt` (`Cb`, `CastTheme`), `branding/design-tokens.json` | onglet **« Boutique »** entre « Apprendre » et « Parental » ; l'action « Locations » disparaît ; lien « Louer » dans Apprendre ; solde de jetons dans Jeux |
| Télémétrie | `C/telemetry/Telemetry.kt:16` (`TV_FEATURES`), `:25-51` (événements, `quiz_game{jokers, score}`), `B/telemetry/EventCatalog.java` | `shop` dans `TV_FEATURES` ; `shop_order{kind}`, `tokens_spend{item}` (consentement « statistiques ») |
| Ordres différés | `B/orders/**` (`PolicyCatalog.ACTIONS` dont `rights.refresh`), `C/policy/OrderTransport.kt` (`GET /api/v1/orders?since`), `S/OrdersRuntime.kt` | optionnel : `rights.refresh` émis à la confirmation d'une commande pour réveiller le téléphone |
| Migrations | `backend/src/main/resources/db/migration/` : la plus haute = `V61__tunnel.sql` (w4-16 prendra la suivante ; w1-11 peut-être `V62`) | la boutique prend « plus haut + 1 » au moment du cahier |
| Secrets serveur | `castbridge.licenses.secrets-dir` (+ `license-signing.key`, `license-totp.key`, `license-audit.key`), clé des ordres `castbridge.orders.key-file`, clé des manifestes `castbridge.signing.*` | `rental-kek.key` (nouveau, 32 octets), **jamais** la clé de signature |
| Échecs | `C/chess/ChessTransport.kt:119` (`ChessRelayClient`, `enabled=false`), `R/ChessActivity.kt:42` (modes dont « En ligne ») | **rien** (P4 : pas de jetons aux Échecs) |
| Sudoku | `C/sudoku/SudokuGame.kt:101` (`MAX_HINTS = 3`) | **rien** (P4) |
