# Conception W4-C : vente au comptant par les points focaux (agents de terrain)

> **Statut : conception (2026-10-02), cahiers `sonnet-w4-11` à `sonnet-w4-18`.** Remplace `sonnet-w2-06` (boutique téléphone) et `sonnet-w2-10` (commandes serveur), **abandonnés** : décisions D8/D9 du propriétaire = **paiement en espèces à un point focal**, pas de mobile money, pas de paiement ni de commande en ligne. Le point focal vend et livre ; les clés et locations sont émises avec les outils du propriétaire (console téléphone / bureau) **ou, par délégation, par l'application du point focal**. `docs/RENTAL-LOTS.md` § 15 (locations gérées par le serveur) est **remplacé** par cette conception : le serveur ne scelle rien et n'émet rien pour les ventes terrain ; il **réconcilie**.
> Chemins : `C/`, `R/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `CT/`, `B/`, `BT/` = `backend/src/test/java/castbridge/server/`. Aucun montant, aucun numéro ici.

## 1. Rôles

| Rôle | Outil | Détient |
|---|---|---|
| **Propriétaire** | console « CastBridge Propriétaire » (`android/ownerlib/.../ConsoleActivity.kt`), outil de bureau (`tools/activation-desktop`), console web du serveur (`/admin/licenses/**`) | clé d'émission par outil (toutes portées sauf `POLICY`), **maître des locations**, clé de signature des catalogues/grille de prix (clé des mises à jour), TOTP serveur |
| **Point focal** (agent) | CastBridge (téléphone) en **mode « Point focal »** (`S/focal/**`, nouveau ; `:sender` dépend déjà de `:ownerlib`, `android/sender/build.gradle.kts:30`) | sa **propre clé Ed25519** (coffre à code, même mécanique qu'`OwnerStore`), une **délégation** signée par le propriétaire (durée, plafonds, bouquets), le catalogue et la grille de prix signés, les lots (téléchargés quand il est en ligne, `S/LotsRuntime.kt`), le maître des locations **enveloppé pour lui** |
| **Client** | CastBridge-TV ; éventuellement CastBridge (téléphone) | sa TV (code d'appareil), son reçu |

Un point focal **vend** (clé de production d'une durée choisie dans la grille ; location d'un bouquet pour la durée fixée par le catalogue, ≤ 60 jours), **encaisse** en espèces, **livre** (Bluetooth ou Wi-Fi local vers la TV, ou fichier `activation` sur la clé USB du client), **remet un reçu** (code court), et **remet l'argent** au propriétaire à échéance convenue. Le serveur tient le **grand livre** et dit ce que chaque point focal doit.

## 2. Comment l'agent obtient la demande de la TV

Trois canaux, tous existants ou presque :
1. **Bluetooth** (le plus courant, chez le client) : `TvBluetooth.with(tv) { it.deviceInfo() }` (`android/ownerlib/.../TvBluetooth.kt`, trame `DEVICE_INFO_REQUEST`/`DEVICE_INFO`, `C/owner/OwnerFrames.kt:21-22`) : la demande v2 (avec `install=`, W4-A).
2. **Wi-Fi local** : `GET /api/activation/request` (PIN de la TV, `R/RentalHub.kt:72-77`).
3. **Partage par le client** (hors de sa présence) : le client touche « Demander la clé de production » dans CastBridge (`S/ActivateTvActivity.kt:158-175`) → texte partagé par WhatsApp/SMS → l'agent **colle** le texte ; ou **QR** : l'écran d'activation de la TV affiche déjà le code ; ajouter le **QR de la demande complète** (`R/ActivationActivity.kt`, w4-15 ; `tools/activation-desktop/.../Qr.kt` existe pour le bureau ; sur la TV, un générateur QR cœur minimal ou `zxing` déjà présent ? à vérifier par l'agent du cahier : `grep -rn zxing android`) que l'app du point focal **scanne**.

## 3. Délégation (`type=delegation`) : vendre sans pouvoir tout forger

Une **enveloppe `cbx1`** (un seul codec, ACTIVATION-FORMAT § 3), nouveau type de corps, signée par une clé du propriétaire portant la **nouvelle portée `DELEGATE`** (`KeyScope`, `C/owner/Keys.kt:21-34` ; recommandée pour la console téléphone et le bureau, **jamais le serveur**) :
```
castbridge-envelope-v1
type=delegation
kid=<clé du propriétaire>
seq=<n>                         (par clé, croissant : une délégation plus récente du même agent remplace l'ancienne sur la TV)
nonce=<hex>
issuedAt=<ms>  notBefore=<ms>  expiresAt=<ms>     (validité du mandat ; ≤ 180 jours, 90 par défaut)
target=any
--
agent=<kid de la clé de l'agent, 16 hex>
pub=<clé publique Ed25519 brute de l'agent, Base64>
name=<[a-z0-9-]{1,32}>          (nom affiché : « Point focal : douala-akwa-01 »)
scopes=ISSUE_PRODUCTION,ISSUE_TRIAL     (sous-ensemble fermé : jamais TRANSFER, REVOKE, REGISTRY, COMMAND_*, SUPER_UNLIMITED, POLICY, DELEGATE)
maxKeyDays=<1..3660>            (durée maximale d'une clé de production ; « illimitée » jamais)
maxRentalDays=<1..60>           (0 = pas de location)
maxSales=<n>                    (quota de ventes sur la validité ; informatif pour la TV, appliqué par l'app et réconcilié par le serveur)
bundles=<ids triés, virgules, ou « tout »>
agentx=<clé publique X25519 de l'agent, 64 hex>              (optionnel ; requis si master=)
master=v2:<eph b64url>:<blob b64url>                          (optionnel : le maître des locations enveloppé pour agentx, même boîte v2 que W4-A § 5, produit « delegation », period = issuedAt)
```
Forme canonique obligatoire (lignes dans cet ordre, champs optionnels omis). Vérification (TV, serveur, outils) : `MALFORMED`, `UNKNOWN_TYPE`, `UNKNOWN_KEY`, `REVOKED_KEY` (clé du propriétaire **ou** `agent` révoqué par une liste `cbr1`, `RevocationState.keys`), `BAD_SIGNATURE`, `KEY_NOT_ALLOWED` (portée `DELEGATE` absente, ou `scopes` hors sous-ensemble), `BAD_DELEGATION` (bornes), `STALE_SEQUENCE`, `NOT_YET_VALID`, `WINDOW_CLOSED` (temps TV `max(now, issuedAt)`).

**Activation « avec ticket ».** L'agent signe l'activation **avec sa clé** (`kid` = agent). La TV ne la connaît pas (`KeyRing.find`, `C/owner/Activation.kt:143`) : la livraison transporte **délégation + activation** sur **une ligne** : `<jeton delegation>|<jeton activation>` (`|` n'apparaît dans aucun Base64 ; une ligne = compatible avec `activations.txt`, le fichier USB et la trame `ACTIVATION`). Nouveau `C/owner/TicketedActivation.kt` (`encode`, `split`) et `C/owner/DelegatedVerifier.kt` : (1) vérifie la délégation contre l'anneau de la TV ; (2) construit `TrustedKey(agentKid, pub, scopes de la délégation)` et vérifie l'activation avec `ActivationVerifier(KeyRing(anneau + agent))` **sans modifier `Activation.kt`** ; (3) applique les **contraintes** sur l'activation acceptée : `kind` ∈ scopes ; production **avec** `usage` obligatoire et `to − from ≤ maxKeyDays` (un agent ne délivre jamais de clé illimitée) ; chaque `rental` : `durationDays ≤ maxRentalDays`, `bundleIds ⊆ bundles`, produit `loc-<bouquet>` ; aucun `super`, `openall`, `subscription`, `purchase` (les achats définitifs restent au propriétaire) ; sinon `KEY_NOT_ALLOWED` « Le point focal n'est pas autorisé à délivrer ceci ». Une TV **ancienne** refuse la ligne (`Envelope.decode` → `cbx1…|cbx1…` illisible → `MALFORMED`) : message clair, rien d'autre.

**Mémoire sur la TV** : `R/DelegationStore.kt` (nouveau) garde les délégations acceptées (`delegations.txt`, `SafeFile`) : au rechargement (`ActivationCenter.reload`, `R/ActivationCenter.kt:205-210`), la ligne stockée contient la délégation : revérifiée telle quelle « à la date d'installation ». Révocation d'un agent = liste `cbr1` avec `key=<kid agent>` (w2-01 la persiste, w3-09 la relaie par le téléphone) : les activations de cet agent restent installées (déjà acceptées, comme pour toute clé révoquée a posteriori : ACTIVATION-FORMAT § 7 « limite honnête ») mais **aucune nouvelle** n'est acceptée ; le propriétaire révoque les postes douteux un à un (`revokeSeat`).

**Enregistrement comptable** : les événements `license` et `issue` (`C/owner/License.kt:40-47`) signés par la clé de l'agent sont rejoués par `LicenseBook.replay(events, ring)` (`:100`) qui exige la portée dans l'anneau (`:111-119`) : les outils du propriétaire et le serveur construisent leur anneau **avec les délégations connues** (`KeyRing.withDelegated(list)`, portées limitées à celles de la délégation ; une délégation expirée ne retire pas les événements passés : l'anneau de rejeu garde les clés déléguées avec leur fenêtre, et un événement `at` hors fenêtre est rejeté `KEY_NOT_ALLOWED`).

## 4. Journal des ventes (sur le téléphone de l'agent), hors ligne, infalsifiable a posteriori

`C/sales/SalesLedger.kt` (pur) : fichier `files/focal/ledger.jsonl` **à ajout seul** (`SafeFile` d'ajout avec fsync ; jamais réécrit), une entrée par ligne, chaînée et signée :
```
castbridge-sale-v1
seq=<n>                 (1, 2, 3… sans trou)
at=<ms>
kind=SALE|REFUND|REMIT|NOTE
agent=<kid>
device=<code d'appareil>              (SALE/REFUND)
license=<id>  seat=<hex>              (SALE : ce que l'activation porte)
item=cle-production|<jours> | loc-<bouquet>|<jours> | cle-essai|<jours>
price=<XAF entier>                    (grille) 
cash=<XAF encaissé>                   (= price sauf geste commercial, qui exige un NOTE)
grid=<generatedAt de la grille>
receipt=<code de reçu>
fp=<sha256 hex du jeton d'activation>  (jamais le jeton)
ref=<seq d'une SALE>                   (REFUND)
prev=<hash de l'entrée précédente, 16 hex ; « 0 » pour la première>
hash=<sha256 des lignes ci-dessus, 16 hex>
sig=<Ed25519(agent) des lignes ci-dessus, Base64>
```
- **Pas d'effacement** : une correction est une nouvelle entrée (`REFUND`, `NOTE`). L'app ne propose aucune suppression ; un trou de `seq` ou un `prev` qui ne correspond pas est une **anomalie** côté serveur (« entrées manquantes » / « chaîne divergente »).
- `REMIT` = versement d'espèces au propriétaire (montant, date) saisi par l'agent **et** confirmé côté serveur par le propriétaire (`agent_remittance`) ; le solde dû = Σ cash(SALE) − Σ cash(REFUND) − Σ remises confirmées.
- **Reçu** (`C/sales/Receipt.kt`) : `code = "R-" + groupes(Base32C(SHA-256("castbridge-receipt-v1|" + agent + "|" + seq + "|" + device + "|" + at)[0:5]) + contrôle)` → `R-XXXX-XXXX` (7 caractères de données + 1 de contrôle `Base32C.check`, `C/owner/Base32C`), texte partagé (WhatsApp/SMS/impression) : « CastBridge — Reçu R-… — TV XXXX-… — Version complète 90 jours — 5 000 XAF — Point focal <name> — <date> — <contact du propriétaire> ». Vérifiable par le propriétaire sur le serveur (recherche par code) ; le client qui conteste montre le reçu.
- **Synchronisation** (`C/sales/LedgerSync.kt` + `S/focal/LedgerSyncJob.kt`) : quand le téléphone est en ligne, `POST /api/v1/agent/sync` (`Authorization: Bearer <jeton d'appareil>` comme les ordres, `C/policy/OrderTransport.kt:13-18`) avec `{agent, delegation, entries: [depuis le curseur serveur], registry: [événements signés]}` → `{cursor, revoked, expiresAt, anomalies[]}`. Idempotent (le serveur ignore un `seq` déjà stocké si `hash` identique, signale sinon). Le téléphone **garde tout** (le serveur n'est qu'une copie).

## 5. Le maître des locations (qui scelle les lots ?)

Aujourd'hui chaque outil dérive son maître de sa propre clé (`RentalKeys.masterFrom`, `C/lots/RentalKeys.kt:26-27` ; bureau `Desk.rentalMaster`, console `ConsoleActivity.kt:194`). Un point focal doit **sceller les lots lui-même** (hors ligne, chez le client : `RentalKeys.seal(rentalKey, lot, version, zip)`) avec la **même** clé que celle que la boîte transporte. Deux choix : (a) maître **par agent** (`masterFrom(agentSigner)`) : simple, mais un renouvellement vendu par un autre agent (ou par le propriétaire) donnerait une autre clé pour la même `period` ⇒ lots déjà livrés illisibles (le carnet garde la première clé, `RentalLedger.install`, `:105`) ; (b) **un maître unique du propriétaire**, enveloppé pour chaque agent dans sa délégation (`master=`, boîte v2 de W4-A vers `agentx`). **Retenu : (b).** Conséquences, dites honnêtement : un téléphone d'agent compromis (code du coffre cassé) livre le maître, donc toutes les clés de location passées et futures ; mais l'agent détient de toute façon les lots **en clair** (il les scelle) : le maître ne donne rien de plus que ce qu'il a déjà, et **n'ouvre aucune boîte** d'une autre TV (clé d'installation, W4-A). Révoquer l'agent ferme l'émission ; **la rotation du maître** (nouvel identifiant de maître dans la ligne `rental`) est un travail ultérieur, noté résiduel.
- Le maître unique = **32 octets aléatoires créés une fois par la console du propriétaire** (`OwnerStore`, scellé dans le coffre `owner-vault.txt` sous une nouvelle ligne `master=`), exporté vers le bureau par un fichier chiffré (`tools/activation-desktop/.../KeyFile.kt` + phrase), **jamais sur le serveur**. Les outils gardent `masterFrom(signer)` comme repli pour prolonger une `period` antérieure à la bascule (fenêtres d'essai seulement, en pratique).
- `RentalPolicy.refusal` (lot libre CC BY-SA jamais loué) : appliqué par l'app de l'agent avec le catalogue de lots signé (`families`), comme le bureau avec `--lots-libres`.

## 6. Grille de prix signée (tenue par le serveur, signée hors ligne)

Même mécanique que le catalogue de bouquets (`C/lots/SignedBundleCatalog.kt:34-40`, `B/lots/BundleCatalogController.java:28-38`, `GET /api/v1/catalog/bundles`) : fichier `content/prices.json` → `tools/prices/sign_prices.py` (clé des mises à jour, même `KeyFile` que `sign-catalog`) → déposé sur le serveur → `GET /api/v1/catalog/prices` (relais, cache, 404 si absent). Texte signé :
```
castbridge-price-grid-v1
generatedAt=<AAAA-MM-JJTHH:MM:SSZ>
currency=XAF
price=cle-essai|30|0
price=cle-production|30|<xaf>
price=cle-production|90|<xaf>
price=cle-production|365|<xaf>
price=loc-<bouquet>|<rentalDays du catalogue>|<xaf>      (une ligne par bouquet louable ; durée = celle du catalogue, jamais une autre)
```
Lignes `price=` triées ; un article absent n'est **pas vendable** par un agent. Les outils (console, bureau, app agent) refusent une grille non signée, modifiée ou plus ancienne que celle gardée. **Montants : décision du propriétaire (BLOQUÉ, D9-bis)** ; les cahiers livrent la mécanique avec un fichier d'exemple aux montants `0` marqué `TEST`.

## 7. Flux de vente (app du point focal), de bout en bout

1. **Mise en service** (une fois, en ligne ou non) : l'agent crée sa clé (code ≥ 12 caractères), montre sa ligne publique (`kid`, `pub`, `agentx`) au propriétaire (QR/texte) ; le propriétaire crée la délégation (console : onglet « Points focaux » ; bureau : `delegation`) et la lui remet (QR/texte/fichier) ; l'app la vérifie (anneau = clés de confiance compilées, `BuildConfig.TRUSTED_KEYS` du téléphone, à vérifier : le téléphone a-t-il les clés ? `grep -n TRUSTED_KEYS android/sender/build.gradle.kts` ; sinon les clés publiques du propriétaire sont dans la délégation importée **et** vérifiées contre `UpdateKeys.PUBLIC_KEYS`… non : une délégation doit être vérifiée contre une clé du propriétaire **connue de l'app** ; cahier w4-13 règle : `castbridge.core.update.UpdateKeys.PUBLIC_KEYS` ne sont pas des clés d'activation ; utiliser `TRUSTED_KEYS` injecté comme sur la TV, en l'ajoutant au `build.gradle.kts` du téléphone si absent).
2. **Préparer** (en ligne) : catalogue signé (déjà : `ServerBundleCatalog`), grille de prix, lots complets des bouquets autorisés (`LotsRuntime`), synchronisation du journal.
3. **Chez le client** (hors ligne) : lire la demande (§ 2) → choisir l'article (grille) → **encaisser** (saisir le montant reçu, = prix sauf NOTE) → l'app émet : clé de production (`LicensedIssuer` avec la clé de l'agent, licence `lic-…` générée, `usageDays` ≤ `maxKeyDays`) et/ou location (`RentalIssuing.right` avec le maître déballé, `period` nouvelle ou renouvelée d'après `GET /api/rental` de la TV) → journal `SALE` (avant la livraison : l'argent est déjà pris) → **livraison** : `TicketedActivation` par Bluetooth (`sendActivation`) ou Wi-Fi (`POST /api/activation/install`), puis les lots scellés (`RentalDelivery.deliver`, `C/lots/RentalDelivery.kt:96-119`, transport Bluetooth de `LotsRuntime`) → reçu partagé.
4. **Échec de livraison** (TV absente, Bluetooth capricieux) : la vente est enregistrée, l'activation gardée **48 h** (fenêtre d'installation) sous `files/focal/pending/` ; « Livrer plus tard » ; au-delà, l'app **réémet** (même licence/poste/period : idempotent) : nouvelle ligne `NOTE` « réémission », pas de nouvelle vente.
5. **Renouvellement** : même TV, même bouquet, location encore active → ligne `rental` avec la `period` d'origine (prolongation) ; clé terminée (mode dégradé, W4-B) → nouvelle clé de production (nouvelle licence auto, ou **même licence** si l'app retrouve `license|seat` dans son registre : `LicenseBook.plan` → `Reuse`, aucun poste consommé).

## 8. Serveur : réconciliation et tableau de bord

- Tables (migration « plus haut + 1 », `V62__agents.sql` ou suivante) : `agent_delegation(agent_kid PK, name, delegation_text, owner_kid, issued_at, expires_at, max_sales, max_key_days, max_rental_days, bundles, revoked_at, last_sync_at)`, `agent_ledger(agent_kid, seq, at, kind, device_code, license_id, seat_id, item, price, cash, grid_at, receipt, fp, ref_seq, prev_hash, hash, sig, received_at, PK(agent_kid, seq))`, `agent_remittance(id, agent_kid, amount, at, confirmed_by, note)`, `agent_anomaly(id, agent_kid, seq NULL, kind, detail, at, resolved_by, resolved_at)`.
- `POST /api/v1/agent/sync` : vérifie la délégation (clés du propriétaire configurées : `TrustedKeys`), la signature et la chaîne de chaque entrée, insère, fusionne les événements de registre (`RegistryStore`/`LedgerService`, anneau avec délégations), retourne curseur, `revoked`, anomalies. Limite de débit existante.
- **Anomalies** calculées à chaque synchronisation : trou/divergence de chaîne ; `price` ≠ grille (`grid_at`) ; `cash` < `price` sans `NOTE` ; ventes > `maxSales` ; vente après `expires_at` ou après `revoked_at` ; même `device_code` vendu deux fois en < 48 h ; `REFUND` sans `SALE` ; `fp` d'activation qui apparaît chez deux agents ; `item` location > `maxRentalDays` ; bouquet hors `bundles`.
- **Console** `/admin/agents` (lien dans `templates/admin/lic-nav.html`) : liste (nom, validité, ventes, encaissé, remis, **à remettre**, anomalies ouvertes), détail (ventes, reçus, recherche par code de reçu, versements : « Enregistrer un versement » avec TOTP, « Clore une anomalie »), **« Révoquer »** (TOTP) → `RevocationService` ajoute `key=<agent kid>` à la liste `cbr1` (`GET /api/v1/revocations`, `B/licenses/PublicLicenseController.java:31-34`) et marque `revoked_at` ; **« Nouvelle délégation »** n'existe **pas** sur le serveur (la clé serveur n'a pas `DELEGATE`) : le propriétaire la crée avec sa console et **l'importe** ici (« Importer une délégation ») pour que le serveur la connaisse avant la première synchronisation (ou l'apprend à la première synchronisation : les deux chemins vérifient la signature du propriétaire).
- Export CSV par agent et par mois (`LicenseCsv` existe pour les licences).

## 9. Remboursements, litiges

- **Avant livraison** (statut `pending`) : l'agent enregistre `REFUND` (ref = la vente), ne livre pas ; l'activation non livrée périme en 48 h ; le serveur n'a rien à faire.
- **Après livraison** : `REFUND` + le propriétaire décide (console) de révoquer le poste (`revokeSeat`) ou non ; la TV l'apprendra par la liste de révocation (relais w3-09) ; une location livrée se termine d'elle-même. Politique commerciale (délai, conditions) : brouillon CGV de w3-13.
- Litige client : le reçu (code) + `agent_ledger` + `lic_issuance`/registre donnent qui a émis quoi, quand, pour quel code d'appareil.

## 10. Interaction avec l'existant

- **Console du propriétaire** (`ConsoleActivity`) : onglet « Points focaux » (créer/renouveler une délégation à partir de la ligne publique de l'agent, choisir plafonds/bouquets/durée, envelopper le maître, partager par QR/texte/fichier ; lister/révoquer = produire une liste `cbr1`). Le maître unique est créé à la première ouverture de l'onglet.
- **Bureau** : commandes `delegation` (mêmes options), `grille-prix` (import/affichage), `registre` fusionne les événements d'agents (anneau avec délégations : `Desk.ring()` lit `delegations/*.txt`).
- **Serveur, licences et postes** : une licence créée par un agent (`lic-…`) entre dans le registre serveur à la synchronisation ; le décompte de postes (1 par licence auto) et les transferts restent au propriétaire ; `lic_issuance.source` = `AGENT:<kid>` pour les activations vues par synchronisation (empreinte `fp`, jamais le texte).
- **Révocation TV** (w2-01) et relais (w3-09) : prérequis pour que la révocation d'un agent atteigne les TV ; sans eux, la révocation n'agit que sur le serveur et l'app de l'agent (qui se verrouille à la prochaine synchronisation ou à `expiresAt`).
- **Mode dégradé** (W4-B) : le renouvellement vendu par l'agent est le chemin de sortie du mode dégradé.
- **Télémétrie** : rien de nouveau n'est envoyé par la TV ; le journal de l'agent contient des codes d'appareil (identifiants techniques, pas de nom de client) : à mentionner dans le registre des traitements (LE-3).

## 11. Décisions prises ici (renversables)

- Validité d'une délégation : **90 jours** par défaut, 180 maximum ; `maxSales` 200 par défaut.
- Un agent n'émet **jamais** de clé illimitée, d'achat définitif, d'abonnement, de `super`, de « tout ouvert », de transfert ; **jamais** de commande (`COMMAND_*`).
- Un agent peut émettre des **clés d'essai** (`ISSUE_TRIAL`) : utile pour la démonstration ; `cle-essai` à prix 0 dans la grille ; la fenêtre d'essai des lots exige le maître (il l'a).
- Maître unique du propriétaire enveloppé dans la délégation (§ 5) ; rotation du maître = travail ultérieur.
- Remboursement après livraison = décision du propriétaire sur le serveur ; l'agent n'émet que l'écriture.
- **BLOQUÉ** (deux questions seulement) : **D9-bis** les montants XAF de la grille (et pour quelles durées de clé : 30/90/365 ?) ; **D7** le nom commercial et le contact (WhatsApp) à imprimer sur le reçu et dans la fiche du point focal.
