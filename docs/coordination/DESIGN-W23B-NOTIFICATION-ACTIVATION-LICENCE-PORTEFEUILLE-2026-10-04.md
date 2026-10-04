# Conception W23-B — Notification des activations au serveur (directe ou par le téléphone relais), enregistrement de la licence et du poste, portefeuille créé à l'activation, voie descendante des ordres

> Document de conception (architecte, 2026-10-04). **Aucun code n'est modifié par ce document.** Branche de référence `integration/agents`. Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`, `B/` = `backend/src/main/java/castbridge/server/`, `A/` = `B/activations/` (**branche `worktree-agent-a31c5a85f56ccbeab`, non fusionnée, lue en lecture seule**). Rien n'a été exécuté : aucun réseau, aucun appareil, aucun serveur, aucun test. Les chiffres non mesurés sont marqués « estimé ». Fichiers lus : § 13.
> Suite de `DESIGN-W23-SUIVI-ACTIVATIONS-CONSOLE-2026-10-04.md` (suivi) et de `DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (portefeuille) ; réutilise le coursier scellé de `DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 3.4 et les ordres différés de `docs/ORDRES.md`.

## Règles du propriétaire (verbatim, 2026-10-04)

1. « les codes transiteront par le téléphone Bluetooth qui resynchronisera avec le serveur et la TV. Les clés d'activation de l'essai et la production restent offline comme c'est le cas, la notification future au serveur de l'activation étant asynchrone (via l'app phone en relais) ou synchrone si la TV est connectée ».
2. Rappels transmis par le coordinateur : préproduction des clients innovants **identique** à la production (aucun raccourci de test) ; code d'activation valable **48 h après l'émission** ; licences **non transférables** ; **licence ≠ clé d'activation** ; seuls NDEM et MBOKO se transfèrent.
3. « app phone est le meilleur relais de confiance entre la TV et le serveur sans que l'utilisateur puisse connaître et affecter le processus. Le serveur peut donner des ordres asynchrones et invisibles de l'utilisateur à la TV qui lui est synchronisée. L'historique des TV synchronisées dans l'app phone permet au serveur de manager offline les TV par des relais discrets (inconnu de l'utilisateur) » (traité au § 9, avec les garde-fous demandés par le coordinateur).
4. « toute activation donne lieu à un portefeuille, il est autonome et hors ligne mais sa mise à jour dépend du serveur » (traité au § 4).

## 0. En vingt lignes

1. **Le trou** : le portefeuille (w22-02) paie la production d'après la **licence** lue au serveur (`lic_license` + poste `lic_seat` ACTIF, par `LicenseFacts`) ; or une activation de production émise **hors ligne** (console du téléphone, bureau) n'arrive au serveur que par l'import manuel du registre ou par le rapport de TV de w23-01, et **rien ne crée la licence ni le poste** quand une TV rapporte une activation vérifiée.
2. **Pire, constaté en lisant le code** : (a) la console du téléphone (`OL/ConsoleActivity.kt:195`) émet la production avec `ActivationIssuer` **sans aucun événement de registre** (seul `OwnerStore.journal` local, qui écrit « `lic-…/48j` » : le 48 est la validité du code en heures, pas la durée de la clé) : **l'import du registre ne peut pas enregistrer les TV actuelles du propriétaire** ; (b) toute licence créée par le serveur aujourd'hui (auto à l'émission `LicenseService.createAuto`, ou import d'un événement `license`, `LedgerService.applyLicense`) a `end_at = NULL` quelle que soit la durée de la clé : le portefeuille la paierait comme **ILLIMITÉE** (5 000 + 50 puis 1 000 + 10 par mois) même pour une clé de 90 jours.
3. **Notification** : la TV produit, à chaque activation acceptée, un **avis d'activation** signé par sa clé d'installation (`cbx1` type `actnotice`, nouveau type du codec unique) qui porte le ou les jetons `cbx1`, l'heure d'installation (horloge `TvClock`) et l'uptime ; voie **synchrone** (`POST /api/v1/activations/report`, version 2) si la TV a Internet ; sinon **asynchrone** : l'avis est **scellé pour le serveur** (X25519 éphémère + HKDF + AES-256-GCM, clé publique « relais » du serveur, aucune dépendance au jeton d'appareil) et confié au **téléphone synchronisé**, qui le pousse au serveur dès qu'il a Internet, sans pouvoir le lire ni l'altérer.
4. **Accusé** : le serveur répond par un **reçu signé** (`cbx1` type `receipt`, clé des ordres, `nonce` = identifiant de l'avis) qui revient par le même chemin ; la TV passe de « en attente de notification » à « activation notifiée au serveur ».
5. **Enregistrement** : un nouveau service **du module des licences** (`ReportedActivationRegistrar`, le module W23 n'écrit toujours pas `lic_*`) crée ou rattache la licence et le poste depuis les **revendications signées** de l'activation, seulement si : clé d'outil de confiance avec `ISSUE_PRODUCTION`, jeton visant ce matériel, avis lié à la TV, installation prouvée dans la fenêtre de 48 h, aucune révocation, sous les plafonds par clé ; licence auto : `seats = 1`, `start_at` = début du droit `usage` (sinon `issuedAt`), **`end_at` = fin du droit `usage` (NULL seulement si la clé est vraiment illimitée)**, plafond de transferts 0, client technique anonyme.
6. **Sinon : jamais de refus silencieux** : `PENDING_DECISION` motivé (poste en trop, hors fenêtre, heure d'installation inconnue, licence suspendue, clé non déclarée…), alerte douce, décision du propriétaire (TOTP + motif) ; le portefeuille affiche le motif.
7. **Registre et journaux** : réconciliation sans double création (identifiant de licence et de poste identiques par construction ; `lic_issuance` unique par `(licence, nonce)`) ; le registre ou le journal signé d'un outil relève le nombre de postes et vaut **déclaration** (lève les retenues).
8. **Portefeuille créé à l'activation** : côté TV dès l'installation (« Portefeuille créé · en attente de la première synchronisation », aucun chiffre) ; côté serveur à la **première notification verte** (identité ouverte sans appareil API, liée à la clé d'installation de l'avis) ; montants **calculés uniquement par le serveur** ; mises à jour descendantes (instantané `cbw1`) par le même chemin.
9. **Rattrapage** : tranches dues depuis le début de la licence, une fois par `(licence, période)`, jamais pour un intervalle suspendu ; **limite** : automatique pour les périodes commencées au plus 90 jours avant la première notification, jusqu'à 366 jours si l'émission est déclarée (journal ou registre) ou acceptée par le propriétaire, jamais au-delà ; ouverture illimitée (5 000 + 50) retenue tant que la clé n'est pas déclarée (D-W23B-6).
10. **Voie descendante** : les ordres différés existent déjà (serveur V60, téléphone `OrdersRuntime` câblé ; **TV : `PolicyHub` écrit mais non câblé**) ; on les étend en **relais discret par l'historique** (le serveur dépose un objet signé destiné à une TV hors ligne dans la file d'un téléphone qui l'a synchronisée), liste blanche fermée, **clé des ordres distincte**, accusés **signés par la TV**, scellement de bout en bout, interrupteurs serveur et TV, transparence écrite.
11. **Cahiers** : w23-05 (serveur : enregistrement + rattrapage + identité de portefeuille, L), w23-06 (avis scellé + reçu, cœur et serveur, M), w23-04 amendé (TV, S → M), w21-07 amendé (téléphone, deux sens), w23-07 (voie descendante serveur + téléphone, M), w23-08 (TV : câblage des ordres, accusés signés, états du portefeuille, M), w23-09 (textes de transparence, haiku S) : **≈ 13-17 $**, ≈ 9 agent·jours, ≈ 6-7 jours ouvrés (estimé, prix non vérifiés).
12. **Tout de suite (aucun code)** : procédure provisoire du § 8 pour enregistrer les licences des TV actuelles par l'API d'administration ; **limite honnête** : aucune TV ne peut encore recevoir de jetons, car le client portefeuille de la TV (w22-07) n'est pas sur `integration/agents` et le portefeuille serveur n'est pas déployé.

## 1. Ce qui existe et ce qui manque (faits lus le 2026-10-04)

| Élément | Fait lu | Conséquence ici |
|---|---|---|
| Console du téléphone, production | `OL/ConsoleActivity.kt:186-207` : `LicenseIds.generate()`, `ActivationIssuer.issue(...)`, `store.journal(..., lic, ActivationPolicy.CODE_VALIDITY_HOURS)` ; **aucun** `LicensedIssuer`, aucun événement `license`/`issue` | le serveur ne peut apprendre ces licences **que** par la TV (avis) ou par une saisie du propriétaire |
| `C/owner/LicensedIssuer.kt` (chemin `PhoneConsole`, `OwnerCli`) | écrit des événements de registre | ces émissions-là sont importables |
| Journal local de la console | `OL/OwnerStore.kt:57-63` : `seq, at, what, device, kind, license/<48>j, h` (chaîne de 16 hex) | donne l'identifiant de licence et la date ; **pas** la durée de la clé |
| Licence serveur | `V51` : `lic_license.client_id NOT NULL`, `seats_allowed 1..1000`, `start_at NOT NULL`, `end_at NULL` = sans fin, `transfer_cap` | une licence auto exige un client technique |
| Licence auto du serveur | `B/licenses/LicenseService.java:286-315` `createAuto` : `NewLicense(id, client, "PAID", 1, null, null, …)` ⇒ `end_at` NULL | **bogue pour le portefeuille** : une clé de N jours paraît illimitée |
| Import d'un événement `license` | `B/licenses/LedgerService.java:276-286` : `end_at` NULL, client « Client importé (à renseigner) », premier gagne | idem ; et le registre ne porte **aucune durée** (§ 8.1 du format : `license`, `seats`, `maxTransfersPerYear`) |
| Import d'un `issue` | `LedgerService.applyIssue` : licence inconnue ⇒ conflit `UNKNOWN_LICENSE` ; poste trouvé ⇒ mise à jour ; même matériel ⇒ alias ; poste en trop ⇒ `OVER_QUOTA` ; `lic_issuance` unique `(license_pk, nonce)` | règles à reprendre telles quelles pour l'enregistrement par avis |
| Portefeuille | `B/wallet/JdbcLicenseFacts.java:48-49` : poste `ACTIVE` (ou libéré d'une licence révoquée), `subject='tv'`, `kind='PAID'` ; `GrantService` : clés `grant:lic:<licence>:<monnaie>:p<k>`, ouverture illimitée une fois par identité, `LicenseSpanBook` (intervalles actifs figés), réclamation de la licence par la première identité payée | une licence apparue tard est **rattrapée automatiquement** depuis `start_at`, **sans aucune limite** aujourd'hui |
| Identité de portefeuille | `V62__wallet.sql:58-69` : `wallet_identity.api_device_id NOT NULL`, `install_pub` (preuve de possession) | une identité ouverte par le téléphone relais (TV sans jeton d'appareil) exige `api_device_id` NULL (V62 jamais déployée : à modifier avant déploiement, ou colonne rendue NULL par la migration de w23-05) |
| Client portefeuille de la TV | aucune occurrence de `wallet/sync` ni de `castbridge-wallet-bind` dans `android/` (hors tests) | **aucune TV ne peut aujourd'hui recevoir de jetons**, quel que soit l'enregistrement |
| Rapport de TV (w23-01, non fusionné) | `A/ReportController.java` : JSON v1, jeton d'appareil, `deviceCode` non lié (audit H1) ; `A/ActivationObserver.observe(deviceId, tokens, state, via)` vérifie signature et matériel, **sans portée** (audit L4), ne crée rien dans `lic_*` | base de la voie synchrone ; H1 et C1 à corriger d'abord (autre agent, renommage en « plus haut + 1 ») |
| Heure d'installation sur la TV | `R/ActivationCenter.kt:133-142, 183-200` : `activations.txt` = `installedAt` (temps `TvClock`) `\t` `uptimeAtInstall` `\t` jeton ; `clock.observe(wall, issuedAt)` avant l'enregistrement | `installedAt ≥ issuedAt` sur une TV honnête ; l'avis peut porter ces deux valeurs |
| Clé d'installation Ed25519 | `C/owner/InstallSigner.kt`, `C/owner/TvProof.kt` (enveloppe `cbx1` type `proof` signée par cette clé, qui embarque déjà `activation=<cbx1>`) | **précédent exact** pour l'avis d'activation |
| X25519 pur Kotlin | `C/owner/X25519.kt`, boîte des locations (`C/lots/RentalKeys.kt`) | scellement vers une clé publique sans dépendance |
| Coursier W21 | conception § 3.4-3.4 quater ; cahiers w21-01b (`Sealer`, `SealedOutbox`, `CourierQueue`), w21-02b (`/api/v1/events/relay`), w21-07 (téléphone) : **non fusionnés** (non vérifié branche par branche) ; scellement dérivé du **jeton d'appareil** (`K = HKDF(SHA-256(jeton))`) | inutilisable tel quel pour une TV jamais enregistrée : d'où le scellement à clé publique (§ 2.4) |
| Ordres différés | `docs/ORDRES.md` ; serveur `B/orders/**` + `V60` ; téléphone `S/OrdersRuntime.kt` câblé dans `PhoneConnect.kt:44` et le manifeste (`OrdersSyncJob`, `OrdersDeliverJob`) ; TV `R/PolicyHub.kt` **sans aucun appel** hors de son fichier | voie descendante à 70 % écrite, **non câblée côté TV** ; accusés non signés (limite § 12 d'ORDRES) |
| Liaison téléphone ↔ TV | `C/trust/*` (`TrustRegistry.MAX_PHONES` = 8 téléphones par TV) ; serveur : `POST /api/v1/orders/pair` enregistre l'appairage | l'« historique » existe des deux côtés ; **plafond de 8 TV par téléphone non trouvé dans le code** |

## 2. Flux de bout en bout d'une activation de production

### 2.1 Vue d'ensemble

```
 Outil hors ligne (console du téléphone, bureau)            CastBridge-TV                          Serveur (castbridge-api)
 ──────────────────────────────────────────────            ──────────────                         ─────────────────────────
 E1 lit la demande d'appareil (Bluetooth DEVICE_INFO,
    fichier device-request.txt, saisie)
 E2 signe l'activation cbx1 (kid de l'outil, 48 h,
    usage|duree|from|to ou illimitée, license=lic-…, seat)
 E3 journal local / journal signé w23-03 (entrée issue)
 E4 remet : Bluetooth ACTIVATION, fichier USB, QR ───────► V1 vérifie hors ligne (signature, portée,
                                                              matériel k parmi n, 48 h, séquence)
                                                           V2 installe : activations.txt
                                                              (installedAt TvClock, uptimeAtInstall)
                                                           V3 portefeuille local « créé, en attente »
                                                           V4 fabrique l'AVIS d'activation signé par
                                                              sa clé d'installation (§ 2.3), état
                                                              « en attente de notification »
                         ┌───────────────── (a) TV avec Internet ────────────────────────────────────────►  N1 POST /api/v1/activations/report v2
                         │                                                                                  N2 vérifie avis + jetons, enregistre
                         │                                                                                     (§ 3), ouvre l'identité (§ 4),
                         │◄──────────────────────── reçu signé (receipt) dans la réponse ─────────────────     rattrape (§ 5), journalise (W23)
                         │
                         └──────── (b) sans Internet ──► téléphone synchronisé (LAN, Wi-Fi Direct, Bluetooth)
                                                         file opaque ──── dès qu'il a Internet ──────────────►  N1' POST /api/v1/activations/relay
                                                         ◄────────────── reçu scellé ◄──────────────────────     (mêmes étapes N2)
                                                         remet le reçu à la TV au contact suivant
 (c) Import manuel : registre (existant) et journal signé (w23-03) téléversés depuis le navigateur ───────────►  déclaration : lève les retenues, relève les postes
```

### 2.2 Voie synchrone (a) : séquence

```
TV                                    Serveur                                         Base
│ POST /api/v1/activations/report     │                                               │
│ Authorization: Bearer <jeton TV>    │                                               │
│ {"v":2,"notice":"cbx1.<actnotice>"} │                                               │
│────────────────────────────────────►│ 1 jeton d'appareil (app=tv, non bloqué)       │
│                                     │ 2 avis : forme, signature par installKey,     │
│                                     │   code = DeviceIdentity.code(facteurs)        │
│                                     │ 3 lien collant device.id ↔ code (≤ 2, H1)     │
│                                     │ 4 chaque jeton : anneau, révocations, PORTÉE, │
│                                     │   cible = ce matériel                         │
│                                     │ 5 observe() W23 (inventaire, alertes)         │
│                                     │ 6 Registrar.register(...)  ──────────────────►│ lic_license / lic_seat / lic_issuance
│                                     │ 7 Wallet.onNotified(...)   ──────────────────►│ wallet_identity, wallet_txn (tranches)
│                                     │ 8 reçu signé (clé des ordres)                 │
│◄────────────────────────────────────│ {"receipt":"cbx1.<receipt>","snapshot":"cbw1…","next":24}
│ vérifie le reçu (nonce = avis ouvert)│                                               │
│ état : « activation notifiée »      │                                               │
```

Les étapes 2 à 8 sont **une transaction** par jeton enregistré (étape 6 sous le verrou de ligne de la licence, comme `LicenseService`) ; les étapes 5 et 7 ne bloquent jamais la 6 (une erreur du portefeuille laisse la licence enregistrée et le reçu le dit : `wallet=pending`).

### 2.3 L'avis d'activation (`cbx1`, type `actnotice`) — additif, versionné

Même codec que tout message signé (`docs/ACTIVATION-FORMAT.md` § 3 : « ajouter un type ajoute un analyseur de corps et une portée, jamais un second format ») ; même principe que `proof` (`C/owner/TvProof.kt`) : signé par la **clé d'installation** de la TV, dont la clé publique est dans le corps.

```
castbridge-envelope-v1
type=actnotice
kid=<16 hex : identifiant de la clé d'installation>
seq=<compteur d'avis de cette installation, strictement croissant>
nonce=<32 hex aléatoires : identifiant de l'avis>
issuedAt=<ms, TvClock>   notBefore=<= issuedAt>   expiresAt=<issuedAt + 400 j>
target=device   k=<k>   factor=<TYPE>|<32 hex> … (les facteurs de CETTE TV)
--
v=1
code=<code d'appareil>
installKey=<clé publique Ed25519, base64>
app=<versionCode>|<versionName>
clock=<uptime ms>|<bootId 16 hex>|<doute 0/1>
act=<fp 64 hex>|<installedAt ms>|<uptimeAtInstall ms ou -1>       (une ligne par jeton, ≤ 4, le courant d'abord)
token=<jeton cbx1 de l'activation>                                (mêmes rang et nombre que act=)
edition=<TRIAL|PRODUCTION|SUPER|NONE|ENDED>
```

- **Taille** (estimé) : jeton d'activation de production ≈ 1,2-1,8 Ko ; avis à un jeton ≈ 2,8 Ko ; scellé (base64, + 60 o) ≈ 3,8 Ko ; avis maximal (4 jetons de 8 192 car.) ≤ 40 Ko : **refusé au-delà de 16 Ko** à la création (la TV n'envoie que le jeton courant et ceux qui n'ont jamais été notifiés).
- **Débit** : EDGE 40 kbps ≈ 3 Ko/s utiles (estimé) ⇒ avis typique **≈ 1,3 s**, reçu (≈ 0,9 Ko) ≈ 0,3 s ; Bluetooth local (hypothèse W21 : 20 Ko/s) ⇒ < 0,3 s.
- **Quand** : à chaque activation acceptée, puis réémis (nouveau `nonce`, nouveau `seq`) toutes les 24 h tant qu'aucun reçu `registered` ou `pending` n'est revenu, puis toutes les 7 jours ; jamais pendant une partie Internet ni 30 s après (règle W21).
- **Jamais** dans l'avis : le jeton d'appareil, un code PIN, une clé de lot, un contenu, une donnée personnelle.
- **Version 1 du rapport** (JSON de w23-01) : reste acceptée pour le **suivi** seulement ; elle n'enregistre **jamais** de licence (pas de preuve de liaison).

### 2.4 Voie asynchrone (b) : scellement, coursier, reçu

```
TV                         Téléphone synchronisé (CastBridge)            Serveur
│ scelle l'avis :          │                                             │
│  e = X25519 éphémère     │                                             │
│  K = HKDF(e·Srelay, sel=noticeId, "castbridge-actrelay-v1")             │
│  AES-256-GCM(K, avis), AAD = {v:1, kind:"act", id, createdAt, size}     │
│ file « act » (prioritaire sur la télémétrie)                           │
│── GET /api/tele/outbox (ou CBTO 22-26) ─►│ range l'objet OPAQUE           │
│   via LinkPlanner : LAN > Wi-Fi Direct   │ (id, taille, sha256)          │
│   existant > tunnel Bluetooth            │                               │
│                          │── dès Internet (règles § 6.3) ───────────────►│ POST /api/v1/activations/relay
│                          │   jeton du TÉLÉPHONE, ≤ 16 objets              │ ouvre (relay-box.key), vérifie l'avis,
│                          │                                               │ mêmes étapes N2 que (a)
│                          │◄── {id, status, receipt (scellé avec K)} ─────│ dédoublonne par id puis par fp
│◄── POST /api/tele/receipts (ou TELE_RECEIPT) ──│                         │
│ ouvre avec K (la TV a gardé e jusqu'au reçu)    │ purge l'objet          │
│ vérifie la signature du reçu, purge l'avis      │                        │
```

- **Pourquoi une clé publique et pas le jeton d'appareil (W21)** : beaucoup de TV du terrain n'ont **jamais** eu Internet, donc pas de jeton d'appareil ; un avis doit pouvoir partir **dès l'installation**. La clé publique « relais » du serveur (`relay-box.key`, X25519, dossier des secrets) est **compilée** dans l'APK TV (comme les clés de confiance), avec un `kid` et une seconde clé de rotation. Le téléphone ne voit que des octets.
- **Reçu** : `cbx1` type `receipt`, signé par la **clé des ordres** (portée `POLICY`), `nonce` = `noticeId`, `target=device` (facteurs de la TV), corps `status=registered|pending|refused`, `reason=<motif fermé>`, `license=<id ou ->`, `seat=<id ou ->`, `fp=<8 hex>` par jeton, `wallet=<created|pending|none>|<NDEM prévus>|<MBOKO prévus>`, `serverAt=<ms>`. **La TV n'élève pas son plancher d'horloge** sur un reçu (même règle que les ordres). Elle n'accepte un reçu que pour un avis **ouvert** (comme le défi d'une commande) : pas de séquence, donc pas de refus `STALE_SEQUENCE` quand deux téléphones livrent dans le désordre. Le reçu voyage scellé avec `K` sur la voie (b) ; en clair dans la réponse HTTPS sur la voie (a).
- **Reprises** : la TV garde l'avis scellé (et `e`) jusqu'à un reçu valide ; un objet remis à un téléphone n'est pas réoffert pendant 24 h, puis l'est à n'importe quel téléphone synchronisé ; 8 téléphones au plus par TV : **un seul suffit**, le serveur dédoublonne. File TV : 32 avis, 256 Ko, durée de vie 400 j (l'avis le plus récent par jeton remplace les anciens).
- **(c) Import** : le journal signé de w23-03 (entrée `issue` avec `usageTo`/`unlimited`, `registry=`) et le registre existant sont des **déclarations d'émission** par l'outil (§ 3.6).

### 2.5 Identité, idempotence, ordre

| Question | Règle |
|---|---|
| Identité de la TV | code d'appareil **dérivé des facteurs** de l'avis (jamais un champ libre) ; liaison = signature de l'avis par la clé d'installation **et**, sur la voie (a), jeton d'appareil collant (≤ 2 installations par code, correctif H1 de w23-01) |
| Clé d'installation de référence | la première vue pour ce code, figée (`lic_registration.install_pub`, `wallet_identity.install_pub`) ; une autre clé plus tard ⇒ alerte `BIND_MISMATCH`, rien n'est payé aux deux tant que le propriétaire n'a pas réaffecté (TOTP) ; voir D-W23B-4 (lier la clé d'installation à l'activation, w22-14) |
| Idempotence | avis : `noticeId` unique (rejeu ⇒ même reçu) ; jeton : empreinte `fp` ; licence : `license_id` ; poste : `(licence, seat_id)` ; émission : `(licence, nonce)` ; tranches : clés du grand livre |
| Ordre | indifférent pour les faits d'un jeton (premier avis vérifié gagne pour `installedAt`, une valeur différente plus tard ⇒ alerte) ; l'**état** de la TV (édition, version) suit le plus grand `seq` d'avis par clé d'installation |
| Ce que montre la TV | écran d'activation et insigne : « Activation notifiée au serveur le 04/10 » (reçu `registered`) ; « Activation en vérification au serveur » (reçu `pending`, motif en clair) ; « En attente de notification · par Internet ou par un téléphone synchronisé » (aucun reçu) |

## 3. Enregistrement serveur : créer ou rattacher la licence et le poste

### 3.1 Où

Nouveau service **dans le module des licences** (`B/licenses/ReportedActivationRegistrar.java`), seul à écrire `lic_*`, appelé par le module W23 (règle A6 de W23 conservée : `activations` n'écrit jamais `lic_*`) et par la synchronisation du portefeuille (chemin court de w23-05a, § 7). Nouvelle table (migration « plus haut + 1 » à la fusion) :

```
lic_registration (fp CHAR(64) PK, kid CHAR(16), nonce VARCHAR(64), license_id VARCHAR(64), seat_id CHAR(16), device_code VARCHAR(24),
                  kind VARCHAR(12), subject VARCHAR(8), issued_at, expires_at, usage_from NULL, usage_to NULL, unlimited BOOL, super BOOL,
                  installed_at_tv NULL, uptime_at_install NULL, install_pub VARCHAR(64), first_notice_id CHAR(32), first_server_at, via ENUM('direct','relay','wallet','admin'),
                  status ENUM('REGISTERED','ATTACHED','PENDING_DECISION','REFUSED'), reason VARCHAR(32) NULL, declared BOOL [journal ou registre],
                  decided_by NULL, decided_at NULL, decision_reason VARCHAR(500) NULL,
                  INDEX(device_code), INDEX(license_id), INDEX(kid, first_server_at), INDEX(status))
```

`lic_issuance.source` prend une troisième valeur **`REPORT`** (colonne `VARCHAR(8)` sans contrainte : rien à migrer), canal `report-direct` / `report-relay` / `report-wallet`, empreinte = SHA-256 du jeton. Audit chaîné `lic_audit` : `LICENSE_CREATE_FROM_REPORT`, `SEAT_ATTACH_FROM_REPORT`, `REGISTRATION_PENDING`, `REGISTRATION_DECIDED`. Évènements W23 (`act_event`) : `REGISTERED`, `REGISTRATION_PENDING`, `REGISTRATION_DECIDED`.

### 3.2 Conditions communes (dans cet ordre ; premier échec = motif)

| # | Contrôle | Échec |
|---|---|---|
| 1 | avis valide (forme canonique, signature par `installKey`, `kid` = identifiant de `installKey`, code = facteurs) ; voie (a) : jeton d'appareil `app=tv`, non bloqué, lien collant | rejet (aucune écriture `lic_*`, alerte `BAD_NOTICE`) |
| 2 | jeton `cbx1` d'activation valide (`EnvelopeVerifier`), `kid` dans `castbridge.licenses.trusted-keys` (ou clé du serveur), non révoqué | `UNKNOWN_KEY` / `REVOKED_KEY` / `BAD_SIGNATURE` : `REFUSED` |
| 3 | jeton visant **ce** matériel (k parmi n sur les facteurs de l'avis, et `DeviceIdentity.code(facteurs du jeton)` = code ou poste déjà connu sous ce code) | `CLONE` : `REFUSED`, alerte haute |
| 4 | `kind=production` ; **portée `ISSUE_PRODUCTION`** pour créer une licence ou un poste (`REACTIVATE` ne fait que rattacher un poste existant) ; `kind=trial` ⇒ pas de licence (§ 3.8) | `KEY_NOT_ALLOWED` : `REFUSED` |
| 5 | aucune révocation : clé (`lic_revocation.kid`), poste (`licence|poste` avec `revoked_at ≥ issuedAt`), licence `REVOKED` | `REVOKED_SEAT` / `LICENSE_REVOKED` : `REFUSED` |
| 6 | **fenêtre d'installation** : `issuedAt − 5 min ≤ installedAt ≤ expiresAt + 5 min` (heure TV de l'avis) | `installedAt` dans `]expiresAt + 5 min, expiresAt + 24 h]` ⇒ `PENDING_DECISION(OUT_OF_WINDOW)` ; au-delà ⇒ `PENDING_DECISION(OUT_OF_WINDOW)` + alerte haute ; `installedAt` absent (ancien format `-1`) ⇒ `PENDING_DECISION(INSTALL_TIME_UNKNOWN)` **sauf** si l'émission est **déclarée** (§ 3.6) |
| 7 | avis reçu au plus **400 jours** après `issuedAt` | `PENDING_DECISION(LATE_NOTICE)` |
| 8 | plafond d'**auto-créations par clé d'outil** : 10 par jour glissant et 50 par mois (table de politique) | `PENDING_DECISION(KEY_RATE)` + alerte haute (clé peut-être compromise, § 10) |
| 9 | identifiant de licence conforme (`[a-z0-9][a-z0-9-]{0,63}`, mots réservés refusés, pas `trial`) | `REFUSED(MALFORMED)` |

### 3.3 Licence inconnue : création depuis les revendications signées

| Champ `lic_license` | Valeur |
|---|---|
| `license_id` | `license=` du jeton |
| `client_id` | client technique **« Client anonyme (activation rapportée) »** (créé une fois, sans contact ; même modèle que `AUTO_CLIENT`) |
| `kind`, `state` | `PAID`, `ACTIVE` |
| `seats_allowed` | **1** (le jeton ne dit pas le nombre de postes ; un événement `license` du registre ou une entrée du journal le relève, § 3.6) |
| `start_at` | `from` du droit `usage` s'il existe, sinon `issuedAt` (signés) |
| `end_at` | `to` du droit `usage` ; **NULL seulement** si la production n'a aucun droit `usage` (illimitée) ou porte `super` |
| `grace_days` | défaut du module (14) |
| `transfer_cap` | **0** (licence non transférable) |
| `created_by` | `report:<nom de l'outil>:<kid 8 premiers>` |

Le poste (`lic_seat`) : `seat_id` = `seat=` du jeton, `subject` du jeton, `device_code` = code des facteurs, `factors` + `k` du jeton, `slot_no` 1, `first_seen` = `installedAt` (sinon `issuedAt`), `last_seen` = maintenant. Émission (`lic_issuance`) : `kid`, `nonce`, `issued_at`, `not_after` = `expiresAt`, `source=REPORT`. Statut `REGISTERED`.

**Correctif connexe (même cahier, D-W23B-3)** : `LicenseService.createAuto` (émission serveur avec licence « auto ») reçoit la durée de la clé et pose `end_at` = fin du droit `usage` ; `LedgerService.applyLicense` garde `end_at` NULL **mais** le registrar corrige `end_at` au premier jeton vérifié d'une licence importée dont `end_at` est NULL et dont **tous** les jetons connus portent un `usage` (sinon une illimitée importée resterait illimitée à tort). Toute correction est auditée (`LICENSE_END_FROM_TOKEN`, avant/après).

### 3.4 Licence connue : rattachement

| Situation | Effet | Statut / motif | Portefeuille |
|---|---|---|---|
| poste `seat_id` actif, même matériel (k parmi n) | `last_seen` mis à jour, émission enregistrée (doublon de `nonce` ignoré) | `ATTACHED` | payé |
| même matériel sous un **autre** `seat_id` de la licence | alias (`lic_seat_alias`), compté une fois (règle du format § 8.2) | `ATTACHED` | payé |
| poste absent, matériel nouveau, place libre (`active < seats_allowed`) | nouveau poste (plus petit numéro libre, sous verrou) | `ATTACHED` | payé |
| poste absent, **plus de place** | **aucun poste**, conflit `OVER_QUOTA` (même file que le registre) | `PENDING_DECISION(OVER_QUOTA)` | refusé : « Licence déjà utilisée sur une autre TV » |
| poste existant **libéré**, même matériel | `PENDING_DECISION(SEAT_RELEASED)` (le propriétaire réactive : il sait pourquoi il l'avait libéré) | idem | refusé jusqu'à décision |
| poste existant sur un **autre matériel** (même `seat_id`) | impossible sans transfert (le `seat_id` dérive du matériel) ; plafond 0 ⇒ | `PENDING_DECISION(TRANSFER_CAP)` + alerte | refusé |
| licence `SUSPENDED` | émission enregistrée, poste inchangé | `ATTACHED` (motif `LICENSE_SUSPENDED`) | rien de nouveau (`LicenseSpanBook`) |
| licence `kind=TRIAL` avec un jeton de production | incohérence | `PENDING_DECISION(KIND_MISMATCH)` | refusé |

### 3.5 Même TV sur plusieurs licences, licence « dupliquée »

- **Renouvellement normal** : la console génère un **nouvel** identifiant de licence à chaque production ⇒ une TV accumule des licences au fil des renouvellements. C'est **permis** (aucune alerte) ; le portefeuille ne paie **qu'une tranche par période** (« meilleure édition », `coveredByEarlierLicence`) et l'ouverture illimitée **une fois par identité**.
- **Licence non transférable** : un second matériel ne peut obtenir le poste d'une licence que si un outil a signé **pour lui** (`seat_id` différent) ; une licence auto a 1 poste ⇒ `OVER_QUOTA` ⇒ aucune tranche pour la seconde TV ; la première garde sa réclamation (`wallet_license_claim`). Un jeton **copié** rapporté par une autre TV échoue au contrôle 3 (`CLONE`). Un poste qui change de code d'appareil (module remplacé) reste le même poste (k parmi n) : la licence suit **le même matériel**, pas une autre TV.

### 3.6 Réconciliation avec le registre et les journaux (aucune double création)

| Ordre d'arrivée | Résultat |
|---|---|
| avis puis registre (`license` puis `issue`) | `applyLicense` : la licence existe (premier gagne) ⇒ **amendement** : si `created_by` commence par `report:` et que l'événement est signé par une clé d'outil de confiance, `seats_allowed = max(actuel, seats de l'événement)` (tracé) ; `applyIssue` : poste trouvé ⇒ mise à jour ; `lic_issuance` en double ⇒ ignoré |
| registre puis avis | licence et poste existent ⇒ `ATTACHED` ; `end_at` corrigé depuis le jeton si NULL (§ 3.3) |
| journal signé w23-03 (`issue` avec `usageTo`/`unlimited`) | vaut **déclaration** : `lic_registration.declared = TRUE` ; lève `INSTALL_TIME_UNKNOWN`, `KEY_RATE` (dans la limite du plafond mensuel) et la retenue d'ouverture illimitée (§ 5.3) ; divergence de durée journal ↔ jeton ⇒ alerte, le **jeton** fait foi |
| deux avis de deux téléphones | dédoublonnés par `noticeId`, puis par `fp` |

**Test exigé** : les quatre ordres (avis, registre, journal, émission serveur) en permutations donnent le **même** état `lic_*` (propriété, sur MySQL 8.4).

### 3.7 Heure d'installation : comment le serveur la croit, horloges

- **Ce que la TV garantit si elle est honnête** : elle refuse l'installation si `max(maintenant, issuedAt) > expiresAt` ; elle écrit `installedAt` avec la même horloge `TvClock` (maximum de l'horloge murale, du dernier instant vu et du plancher signé `issuedAt`) ⇒ `issuedAt ≤ installedAt ≤ expiresAt`.
- **Ce que le serveur vérifie** : (1) signature de l'avis par la clé d'installation (continuité de **la même installation** entre avis) ; (2) cohérence monotone entre deux avis du même `bootId` : `Δ(heure de l'avis) ≈ Δ(uptime)` à 5 min près, et `installedAt` jamais déplacé (premier avis vérifié gagne) ; (3) **ancre serveur** : `first_server_at` ; `installedAt ≤ first_server_at + 24 h` ; (4) bornes : toute heure de TV hors `[2026-01-01, maintenant + 400 j]` ⇒ avis rejeté (audit L3).
- **Limite dite franchement** : une TV trafiquée signe ce qu'elle veut avec **sa** clé d'installation. Le serveur ne peut pas prouver l'heure ; il borne le dommage : le jeton reste signé **par l'outil du propriétaire, pour ce matériel précis** ; au pire, un code émis mais installé hors délai compte (alerte `OUT_OF_WINDOW` si l'incohérence se voit, `EXPIRED_UNUSED` → constat tardif dans W23) ; aucune licence n'est créée pour un matériel que l'outil n'a pas visé.
- **Décalage d'horloge** : le serveur n'utilise que **sa** montre pour « maintenant » et pour les tranches ; l'heure de la TV ne sert qu'aux contrôles 6-7 ; une TV « à 1970 » donne `installedAt = issuedAt` (plancher) et passe.

### 3.8 Essais

Pas de licence (« licence ≠ clé d'activation », essai lu dans `cbx1`). L'avis d'un essai est vérifié de la même façon (portée `ISSUE_TRIAL`), inscrit au suivi W23 (`act_key`, `EXPIRED_UNUSED` sinon), ouvre l'identité de portefeuille (§ 4) et donne les tranches d'essai (100 NDEM par période) **depuis `cbx1`**, avec la même limite de rattrapage. Reçu `status=registered`, `license=-`.

## 4. Création du portefeuille à l'activation (règle du propriétaire)

| Moment | Côté TV | Côté serveur |
|---|---|---|
| activation installée (essai, production, illimitée, `super`) | identité locale créée = code d'appareil ; état **« créé, en attente de la première synchronisation »** ; **aucun chiffre** (la TV ne calcule ni ne crée jamais de valeur) | rien (le serveur ne sait pas encore) |
| première **notification verte** (reçu `registered`, voie (a) ou (b)) | « Activation notifiée · vos jetons arrivent » + aperçu du reçu (« 5 000 NDEM et 50 MBOKO prévus »), présenté comme **prévision** | `wallet_identity` ouverte : `holder` = code, `install_pub` = clé de l'avis, **`api_device_id` NULL** (liaison à l'appareil API à la première synchronisation directe avec preuve de possession par la **même** clé) ; tranches dues inscrites (§ 5) ; instantané `cbw1` signé |
| instantané `cbw1` reçu (réponse directe, ou objet descendant par le téléphone, § 9) | affiche « 5 000 NDEM · 50 MBOKO · au 04/10 18:42 » hors ligne (dernier instantané signé, `C/wallet/WalletCache.kt`) | — |
| notification `pending` | « Jetons en attente : activation en vérification au serveur » + motif | identité ouverte, **aucune tranche de production** ; tranches d'essai normales |
| aucune notification | « Jetons en attente de notification de votre activation » | alerte douce W23 `NEVER_NOTIFIED` (activation déclarée ou remise par Bluetooth, jamais notifiée après 14 j) ; **aucun crédit** |

**Autonomie hors ligne** : la TV garde et affiche son dernier `cbw1`, active les **bons hors ligne** (`cbv1`, w22-06 : crédit « en attente », confirmé par le serveur) et garde le budget hors ligne du **Défi des 10 000** (compteur scellé, w22-17). **Toute mise à jour** de solde (tranches, conversions, transferts, résultats de parties, gel) vient du serveur, par la réponse directe **ou** par le téléphone (objet descendant `cbw1`, § 9). **Toute dépense** (mise, conversion, transfert) exige toujours la TV en ligne et liée à son appareil API (inchangé, W22).

**Calculé uniquement par le serveur** (W22 § 1.2, rappel) :

| Édition | Attribution | Rythme |
|---|---|---|
| Essai (`cbx1`, 1-365 j) | 100 NDEM | par période de 30 j commencée pendant la validité |
| Production à durée (licence avec `end_at`) | 1 000 NDEM + 10 MBOKO | idem, depuis `start_at` |
| Illimitée (licence `end_at` NULL) | **5 000 NDEM + 50 MBOKO à l'ouverture seulement**, puis 1 000 + 10 **à partir de la période suivante** (jamais de `p0`) | ouverture une fois par identité |
| `super` | aucune attribution automatique | — |

**Page du téléphone** (`/quiz` locale de la TV, carte compacte) : mêmes textes que la TV, lus chez **sa** TV ; le téléphone ne demande rien au serveur du portefeuille. Aucun écran nouveau sur le téléphone.

## 5. Interaction avec le portefeuille : rattrapage et limites

### 5.1 Règle

À chaque matérialisation (notification verte, synchronisation directe) : toutes les tranches dues depuis `start_at` de la licence (essai : depuis l'ancre de l'identité) **jusqu'à maintenant**, chacune **une fois** par `(licence, période)` (clé `grant:lic:<licence>:<monnaie>:p<k>`), **aucune** pour une période dont le début tombe dans un intervalle suspendu (`LicenseSpanBook`), ouverture illimitée une fois par identité, première mensuelle à ancre + 1 période.

### 5.2 Limite de rattrapage (nouvelle, D-W23B-5)

Soit `R` = première notification verte de cette licence au serveur (`lic_registration.first_server_at`) :

| Début de la période | Licence **déclarée** (journal, registre) ou créée/acceptée par le propriétaire | Licence connue **seulement** par l'avis de la TV |
|---|---|---|
| `≥ R − 90 j` | automatique | automatique |
| `[R − 366 j, R − 90 j[` | automatique | **retenue** (`wallet_catchup_hold`), libérée par déclaration ou décision du propriétaire (TOTP + motif) |
| `< R − 366 j` | **jamais** (durée maximale de la phase hors ligne) | jamais |

Une tranche retenue n'est **ni perdue ni versée** ; la TV voit « Jetons de votre activation en vérification ». Les tranches **futures** ne sont jamais retenues.

### 5.3 Ouverture illimitée

Les 5 000 NDEM + 50 MBOKO d'une licence **illimitée connue seulement par l'avis** sont **retenus** jusqu'à déclaration ou décision (c'est la plus grosse attribution unique et la cible n° 1 d'une clé d'outil volée) ; les tranches mensuelles suivantes suivent le § 5.2. D-W23B-6.

### 5.4 Motifs affichés (ajouts à W22 § 7.6, mêmes textes TV et téléphone)

| Motif | Texte |
|---|---|
| `NOTIFY_PENDING` | « Jetons en attente de notification de votre activation » |
| `REGISTRATION_REVIEW` | « Activation en vérification au serveur : jetons de production à venir » |
| `SEAT_OVER_QUOTA` | « Licence déjà utilisée sur une autre TV : contactez votre point focal » |
| `CATCHUP_HELD` | « Jetons de votre activation en vérification » |
| `WALLET_CREATED` | « Portefeuille créé · en attente de la première synchronisation » |
| `LICENSE_PENDING` (existant) | « Licence en attente d'enregistrement : jetons de production à venir » |

## 6. Le téléphone relais des avis (montant)

### 6.1 Réutilisation de W21

Mêmes mécanismes que le coursier de télémétrie (`DESIGN-W21-…` § 3.4 ter / quater) : retrait par `LinkPlanner` (réseau commun > groupe Wi-Fi Direct **déjà existant** > tunnel Bluetooth ; trames `CBTO` 22-26 en repli), morceaux de 4 Ko reprenables, empreinte vérifiée, file par TV, coursier **opaque**, seul un téléphone **synchronisé** (`TrustRegistry`/`PinBook`) reçoit des objets, n'importe lequel des 8. **Ajouts** :

| Ajout | w21-07 (téléphone) | w23-04 (TV) |
|---|---|---|
| genre d'objet `act` (avis scellé à clé publique) à côté des lots de télémétrie | rangé dans `CourierQueue`, **priorité au-dessus** de la télémétrie, jamais évincé au profit d'un lot de télémétrie | file `act` dans `SealedOutbox` (ou file dédiée si w21-01b absent), exposée par `GET /api/tele/outbox` avec `kind=act` |
| envoi | `POST /api/v1/activations/relay` (route de w23-06 ; même forme que `/api/v1/events/relay`, fusionnable plus tard) | — |
| reçus | rapportés à la TV par `POST /api/tele/receipts` (`kind=act`) | la TV ouvre le reçu avec `K`, vérifie la signature, purge |
| voie descendante | même file, sens inverse (§ 9) | `GET /api/tele/inbox`, `POST /api/tele/acks` (§ 9.4) |
| consentement | les avis sont une fonction de **licence** (D-W23-5) : **hors** porte de consentement des statistiques | idem |

### 6.2 Formats (additifs, versionnés)

- Sur la TV : `GET /api/tele/outbox?kinds=tele,act` ⇒ `[{id, kind, size, sha256}]` ; une TV ancienne ignore `kinds` et ne rend que `tele` (le téléphone sait alors qu'elle n'a pas d'avis).
- Vers le serveur : corps `{"v":1,"items":[{"kind":"act","id":"<32 hex>","box":"<base64url>"}]}` (≤ 16 objets, ≤ 256 Ko) ; réponse `{"items":[{"id","status":"accepted|duplicate|rejected","reason?","receipt":"<base64url scellé>"}],"down":[…objets descendants pour les TV de ce téléphone…]}`.

### 6.3 Budget, batterie, débit

Jamais en **itinérance** ; Wi-Fi d'abord ; sur forfait mobile **compté**, seulement les objets de licence (avis, reçus, ordres P0) dans un budget de **32 Ko par jour** (D-W23B-8 : la règle littérale « jamais sur forfait compté sans Wi-Fi » laisserait sans notification la plupart des foyers, qui n'ont que des données mobiles) ; au plus un envoi par 15 min ; `JobScheduler`, batterie non faible ; aucune notification, aucun écran. Tailles : avis ≈ 3,8 Ko scellé, reçu ≈ 1,2 Ko ⇒ une activation ≈ **5 Ko** aller-retour.

### 6.4 Vie privée

Aucun code d'appareil complet dans les journaux du téléphone (étiquette `…4F2Q`), aucun jeton, aucun avis en clair ; le téléphone stocke des octets chiffrés dans ses fichiers privés, purgés à l'accusé du serveur ou à l'expiration (14 j).

### 6.5 Ce que la console du propriétaire ne doit PAS faire

L'application « CastBridge Propriétaire » (`castbridge.owner`) **reste sans permission réseau** pendant la phase hors ligne (D-W23-3 = B) : elle n'est **pas** relais, ne pousse rien au serveur, n'ouvre aucune session ; elle ne fait qu'émettre, remettre par Bluetooth et **exporter** son journal (w23-03). Le relais est l'application **grand public** CastBridge ; le code relais ne touche **jamais** `ownerlib` ni la clé de signature de l'outil (l'entrée super-administrateur embarquée reste éteinte et hors ligne).

## 7. Livraison par étapes : ce qui existe, ce qui manque, cahiers

### 7.1 État

| Brique | État |
|---|---|
| Vérification `cbx1` côté serveur, registre, postes, verrous | existe (`B/licenses/**`, V50-V52) |
| Portefeuille serveur (grand livre, tranches, licences, preuve de liaison) | existe sur `integration/agents` (V62), **non déployé** |
| Suivi W23 (`act_*`, rapport v1) | branche non fusionnée, C1/H1 en correction par un autre agent (migration renommée « plus haut + 1 ») |
| Client portefeuille TV (w22-07) | **absent** de `integration/agents` |
| Coursier W21 (w21-01b, w21-02b, w21-04, w21-07) | non fusionnés (non vérifié branche par branche) |
| Ordres différés | serveur V60 + téléphone câblés ; **TV non câblée** |

### 7.2 Cahiers (ordre, fichiers, tests, coût)

| Ordre | Cahier | Objet | Effort | Audit Opus | Dépend |
|---|---|---|---|---|---|
| 1 | **w23-05** `sonnet-w23-05-enregistrement-licence-par-notification-et-rattrapage.md` | `ReportedActivationRegistrar` + `lic_registration` ; correctif `createAuto`/`end_at` ; identité de portefeuille ouverte par notification (`api_device_id` NULL) ; limite de rattrapage, retenues, motifs ; décisions du propriétaire (page + API) ; **w23-05a** : appel du registrar depuis `POST /api/v1/wallet/sync` (chemin synchrone court) | L | **obligatoire** (écritures `lic_*`, création de valeur) | w23-01 corrigé (C1, H1) et fusionné ; tests MySQL 8.4 Testcontainers |
| 2 | **w23-06** `sonnet-w23-06-avis-activation-scelle-recu-signe.md` | type `actnotice` et `receipt` (Kotlin, Java, Python, vecteurs), scellement à clé publique, `POST /api/v1/activations/relay`, rapport v2 | M | **obligatoire** (cryptographie) | 05 (interface du registrar) |
| 3 | **w23-04** (amendé) | TV : avis signé, file `act`, envoi direct v2, reçu, états affichés | S → M | échantillon → **obligatoire** (clé d'installation) | 06 ; exception de gel |
| 3 | **w21-07** (amendé) | téléphone : objets `act` montants et descendants, budget | M+ → M+ (+0,3 j) | échantillon | 06, w21-01b |
| 4 | **w23-07** `sonnet-w23-07-voie-descendante-relais-par-historique.md` | serveur + téléphone : clé des ordres distincte, relais par historique, objets descendants (`order`, `revocation`, `receipt`, `cbw1`), accusés signés vérifiés, interrupteur, nouvelles actions | M | **obligatoire** | 06 |
| 4 | **w23-08** `sonnet-w23-08-tv-ordres-accuses-portefeuille-attente.md` | TV : câblage `PolicyHub`, accusés signés, `downlink.pause`, états du portefeuille (écran portefeuille, exception D-W22-13) | M | **obligatoire** | 07, w22-07 |
| 5 | **w23-09** `haiku-w23-09-textes-transparence-relais.md` | lignes de transparence versionnées (TV, téléphone) | S | — | 07 |

**Coût** (prix des index précédents, **non vérifiés** : sonnet 2/10 $/M, opus 4/20 $/M, haiku 1/5 $/M) : sonnet L 1,6 $ + M × 4 (06, 07, 08, 04 amendé) 4,0 $ + amendement w21-07 0,4 $ ; haiku S 0,2 $ ; audits Opus obligatoires × 5 (05, 06, 04, 07, 08) 4,0 $ + échantillon 0,4 $ ; reprises 20 % (cryptographie) ≈ 2,1 $ ; MySQL et vecteurs ≈ 0,5 $ ⇒ **≈ 13-17 $**, ≈ **9 agent·jours** ; chemin critique 05 (2,5 j) → 06 (1,5 j) → 04 ∥ 07 (1,5 j) → 08 (1 j) ⇒ **≈ 6-7 jours ouvrés** (estimé).

### 7.3 En un jour, et plus tard

| Faisable en ≈ 1 jour | Plus tard |
|---|---|
| **Procédure provisoire** (§ 8) : licences des TV actuelles saisies par l'API d'administration (aucun code) | voie asynchrone complète (06, 04, w21-07) |
| **w23-05a** seul : dans `POST /api/v1/wallet/sync` (qui vérifie déjà les `cbx1` et la preuve de liaison), appeler le registrar quand une production n'a pas de licence ⇒ une TV **en ligne** reçoit ses jetons à sa première synchronisation, sans avis ni téléphone | voie descendante (07, 08), transparence (09) |
| Limite honnête : utile seulement quand le client portefeuille TV (w22-07, avec `bind`) est livré **et** le portefeuille serveur déployé ; la préproduction doit être **identique** à la production : `CASTBRIDGE_WALLET_REQUIRE_BIND_PROOF` reste **vrai** (aucun raccourci) | |

## 8. Procédure provisoire pour les TV actuelles du propriétaire (sans secret)

But : que chaque TV de production déjà activée ait sa **licence** et son **poste** au serveur avant que la chaîne existe, pour que ses jetons soient versés (avec rattrapage) dès que le portefeuille sera livré.

1. **Serveur (une fois)** : sauvegarde (`backup.sh`), module des licences **allumé** (`CASTBRIDGE_LICENSES_ENABLED`), TOTP du propriétaire enrôlé, clé publique de la console du téléphone déclarée dans `CASTBRIDGE_LICENSES_TRUSTED_KEYS` avec ses portées (onglet « Clé publique » de la console : ligne `kid=… pub=… scopes=…`, **seule la clé publique** quitte le téléphone). Préproduction d'abord, configuration identique à la production.
2. **Inventaire, TV par TV** (console du téléphone, Bluetooth, sans réseau) : appairer la TV, onglet « Activer » > **Lire le code de la TV** : la **demande d'appareil** complète (`code=`, `k=`, `factor=` …) s'affiche ; la recopier (fichier ou copier-coller vers l'ordinateur ; ce texte ne contient que des empreintes).
3. **Identifiant de licence et date** : onglet « Journal » de la console : la ligne de l'émission porte la date et `lic-xxxxxxxxxx/48j` (**48 = validité du code en heures**, pas la durée de la clé).
4. **Durée de la clé** : lue sur l'**insigne** de la TV (« Clé valable jusqu'au JJ/MM/AAAA (N j) » ou « Clé illimitée »).
5. **Créer la licence** (API `POST /api/v1/admin/licenses/` avec le jeton d'administration, ou page *Licences > Nouvelle licence*) : identifiant = le `lic-…` du journal, type payante, **1 poste**, début = date d'émission du journal, fin = date de l'insigne (vide si « Clé illimitée »), plafond de transferts **0**, client « Client anonyme (activation rapportée) » (à créer une fois).
6. **Créer le poste sans rien installer** : *Licences > fiche > Émettre* (ou `POST /api/v1/admin/licenses/{licence}/activations {deviceRequest}`) avec la demande d'appareil : le serveur prend **un poste** avec l'identifiant de poste par défaut — **le même** que celui de la console (`SHA-256("castbridge-seat|licence|H")`, formule commune aux trois outils) — et affiche une activation serveur **qu'on n'installe pas** (elle n'est conservée nulle part ; l'émission reste tracée au registre).
7. **Vérifier** : `GET /api/v1/admin/licenses/devices/{code}` montre licence ACTIVE + poste ; noter la date dans un tableau hors dépôt.
8. **Quand le portefeuille sera livré** : la TV en ligne se synchronise (preuve de liaison) et reçoit les tranches dues depuis le début, une fois par période ; une illimitée reçoit 5 000 + 50 puis les mensuelles à partir de la période suivante. Ces licences, créées par le propriétaire, sont « déclarées » (§ 5.2).

**Non vérifié** : que la route d'émission de l'API accepte une durée (champ `usageDays` présent dans `ActivationService.IssueRequest`, mais la documentation de la route ne le liste pas : la durée n'importe pas ici puisque l'activation serveur n'est pas installée) ; que le module des licences soit déjà allumé en production (le guide dit « éteint par défaut »).

## 9. Voie descendante : ordres du serveur vers la TV par le téléphone

### 9.1 Principe

Le téléphone **synchronisé** est le relais de confiance **dans les deux sens**. Tout ce qui transite est **signé par le serveur** et **scellé de bout en bout** (serveur ↔ TV) : le téléphone ne voit que des octets ; l'utilisateur ne peut **ni lire** (chiffré), **ni modifier** (signature), **ni forger** (clé du serveur) ; il ne peut **supprimer** qu'en désinstallant l'application ou en effaçant ses données (ce qui met fin au relais, § 9.6 e), et **retarder** qu'en restant hors de portée ou hors ligne (le serveur reprend par un autre téléphone ou la voie directe, § 9.4). « Invisible » = **aucun écran, aucune interruption, aucune notification** dans l'usage normal ; ce n'est **pas** caché : une ligne de transparence existe (§ 9.6 a) et la TV garde son journal en lecture seule (« À propos > Politiques appliquées », existant dans ORDRES § 11).

### 9.2 Objets descendants (liste fermée de genres)

| Genre | Signé par | Vérifié par la TV |
|---|---|---|
| `order` (`cbx1`, ORDRES § 3-4) | clé des **ordres** (portée `POLICY`) | `PolicyEngine` (13 contrôles, séquence par clé) |
| `revocation` (`cbx1`) | clé avec `REVOKE` (serveur des licences ou secours hors ligne) | `RevocationState` (union) |
| `receipt` (`cbx1`, § 2.4) | clé des ordres | avis ouvert |
| `cbw1` (instantané de portefeuille) | clé « portefeuille » de l'API | `WalletCache` (`seq` croissant, identité) |

Un genre inconnu est **ignoré** (et accusé `UNKNOWN_KIND`).

### 9.3 Liste blanche des actions d'ordre

Existantes (ORDRES § 4, conservées) : `license.suspend`, `license.revoke`, `license.activate`, `rights.refresh`, `flag.set`, `app.min_version`, `update.channel`, `catalog.available`/`catalog.retire`, `budget.set`, `message.show`/`message.clear` ; `license.extend` et `revocation.add` exigent des portées que la clé des ordres **n'a pas** (elles passent par une nouvelle activation ou une liste `revocation` signée).

Ajouts (même règle : affirmations absolues, idempotentes, paramètres bornés, parité Kotlin/Java/JSON) :

| Action | Paramètres | Effet |
|---|---|---|
| `wallet.freeze` / `wallet.unfreeze` | `reason` (code fermé) | affiche « Compte en vérification » ; suspend localement l'activation des bons et le budget hors ligne du Défi (le serveur gèle déjà les sorties) |
| `wallet.policy` | `name` ∈ {`defi.dailyWins`, `defi.weeklyWins`, `defi.monthlyWins`, `convert.rate.display`}, `value` borné | plafonds et taux **affichés / appliqués hors ligne** ; le serveur reste l'autorité |
| `report.request` | `what` ∈ {`activations`, `telemetry`, `policy-journal`} | la TV produit un avis / un lot à la prochaine occasion (dans ses bornes) |
| `sync.force` | `what` ∈ {`wallet`, `revocations`, `orders`, `all`} | prochaine occasion : synchronisation directe si Internet, sinon demande au téléphone |
| `update.available` | `version`, `channel` | avis de mise à jour (la mise à jour reste signée et confirmée par l'utilisateur) |
| `downlink.pause` / `downlink.resume` | `until` (≤ 90 j) | **interrupteur côté TV** : jusqu'à `until`, la TV n'applique plus que `downlink.resume`, les `revocation` et les `receipt` |

**Exclusions fermes** (aucune action n'existe, la liste `NEVER` est testée côté TV **et** serveur) : exécution de code ou de commande arbitraire ; installation d'APK ; lecture, envoi, modification ou suppression de fichiers, de la bibliothèque, des contenus, des réglages personnels ; caméra, micro, capture d'écran, géolocalisation, contacts, liste d'applications ; suppression de données de l'utilisateur ; envoi de tout contenu non listé ; ajout de clé de confiance (une clé ne peut qu'être **révoquée**) ; désactivation de la vérification des mises à jour signées.

Contrôles TV (rappel ORDRES § 8) : liste blanche, signé, portée, **destiné à elle** (facteurs k parmi n), séquence strictement croissante par clé, fenêtre, idempotence ; refus = accusé motivé, jamais de blocage des suivants.

### 9.4 Relais par l'historique

```
Serveur                                   Téléphone P (historique : TV X, Y)          TV X (hors ligne)
│ ordre o pour X (signé, scellé pour X)   │                                           │
│ choisit ≤ 3 téléphones de l'historique  │                                           │
│ de X, par contact le plus récent        │                                           │
│── GET /api/v1/orders?since= (6 h) ─────►│ (réponse : o pour X, en octets)            │
│                                         │ file « down » de P, par TV                 │
│                                         │── à la prochaine liaison existante ───────►│ ouvre, vérifie, applique
│                                         │◄── accusé SIGNÉ par la clé d'installation ─│
│◄── POST /api/v1/orders/acks ────────────│                                           │
│ vérifie la signature (install_pub connue)│ purge o                                  │
│ état APPLIED / REFUSED (premier gagne)  │                                           │
│ pas d'accusé en 72 h ⇒ remet o au téléphone suivant de l'historique ; après expiresAt ⇒ EXPIRED, alerte W23 SILENT_TV
```

| Point | Règle |
|---|---|
| Historique | serveur : appairages `POST /api/v1/orders/pair` (existant) + table `relay_history(phone_device_id, tv_ref, first_at, last_contact_at, last_ack_at, removed_at)` alimentée par les appairages, les accusés et la **liste de confiance** que le téléphone rapporte à chaque synchronisation (≤ 8 TV par téléphone ; 8 téléphones par TV) |
| Files du téléphone | ≤ 16 objets et 64 Ko **par TV**, ≤ 8 TV, ≤ 256 Ko au total, objet ≤ 32 Ko ; durée de vie = `min(expiresAt, 30 j)` ; chiffré au repos (objets déjà scellés), purgé à l'accusé du serveur ou à l'expiration |
| Priorité, ordre | P0 `revocation`, `receipt`, `license.suspend/revoke/activate`, `downlink.*` ; P1 `cbw1`, `wallet.*` ; P2 `flag.set`, `budget.set`, `wallet.policy`, `catalog.*` ; P3 `report.request`, `update.available`, `message.*` ; livraison par priorité puis `seq` croissant par clé ; éviction : priorité la plus basse, la plus ancienne |
| Plusieurs téléphones | dédoublonnage par identifiant d'objet (empreinte) côté TV (même accusé rendu) et serveur (premier accusé gagne) ; au plus 3 téléphones simultanés par objet |
| TV injoignable | expiration ⇒ `EXPIRED` (W23), alerte douce ; le propriétaire décide (réémettre, autre chemin) |
| Budget | § 6.3 (pas d'itinérance ; Wi-Fi ; mobile compté : P0 seulement, 32 Ko/jour avec le montant) ; jamais de liaison créée pour le relais ; aucune notification |
| Scellement descendant | vers la clé X25519 d'installation de la TV (déjà dans la demande d'appareil `DEVICE_INFO`, `RentalHub.installPubOrNull`) ; si elle est inconnue du serveur : signé seulement (aucune donnée personnelle dans un ordre), noté au suivi |

### 9.5 Accusés signés par la TV

La TV signe chaque accusé avec sa **clé d'installation** Ed25519 (`InstallSigner`) : `castbridge-order-ack-v1 ⏎ code ⏎ id d'objet ⏎ résultat ⏎ motif ⏎ heure TV`. Le serveur le vérifie avec `install_pub` connue par l'avis (§ 2.5) ; tant qu'elle est inconnue, l'accusé reste **informatif** (limite actuelle d'ORDRES § 12, désormais refermée dès le premier avis).

### 9.6 Garde-fous (non négociables)

a) **Transparence documentaire** : écran de consentement **versionné** sur le téléphone et sur la TV ; textes proposés (juridique reporté au 2027-01-01) :
- Téléphone : « CastBridge peut transmettre en arrière-plan, sous forme chiffrée, des messages techniques entre nos serveurs et les TV CastBridge avec lesquelles ce téléphone a été synchronisé (état de licence et d'activation, jetons, réglages). Ils ne contiennent aucune donnée personnelle et ne donnent accès ni à vos fichiers ni à vos contenus. Pour y mettre fin, retirez la TV de vos TV synchronisées ou désinstallez l'application. »
- TV : « Cette TV peut recevoir de son éditeur, par Internet ou par un téléphone synchronisé, des messages chiffrés et signés (état de licence et d'activation, jetons, réglages, demandes de rapport technique). Ils ne peuvent ni lire, ni modifier, ni supprimer vos fichiers ou vos contenus, et ne donnent aucun accès à distance. Vous pouvez les consulter dans À propos > Politiques appliquées. »

b) **Proportionnalité** : liste blanche seulement ; aucune donnée personnelle ; stockage chiffré, purgé à l'accusé ou à l'expiration.
c) **Traçabilité** : chaque objet émis, relayé (`HANDED` + téléphone), remis, accusé, refusé, expiré entre dans l'historique immuable W23 (`act_event` : `ORDER_ISSUED`, `ORDER_HANDED`, `ORDER_APPLIED`, `ORDER_REFUSED`, `ORDER_EXPIRED`, `RECEIPT_SENT`) et dans `order_audit` ; lectures auditées (`adm_read_audit`).
d) **Interrupteurs** : serveur `CASTBRIDGE_ORDERS_ENABLED` (existant) + `CASTBRIDGE_RELAY_DOWN_ENABLED` (nouveau, voie par l'historique) ; TV : `downlink.pause` signé (sans mise à jour) et drapeau compilé `relay.down`.
e) **Fin du relais** : désinstallation de l'application téléphone, ou retrait de la TV de son historique (« TV synchronisées » du téléphone, ou « Téléphones synchronisés » de la TV) ⇒ le téléphone efface la file de cette TV et le rapporte ; le serveur cesse de router par lui (`removed_at`) ; un téléphone muet 30 jours est retiré du routage.

### 9.7 Sécurité de la voie descendante

| Menace | Rayon d'impact | Parade |
|---|---|---|
| Clé des **ordres** volée (serveur compromis) | ordres de la liste blanche à toutes les TV : verrouillage par suspension, gel d'affichage, indicateurs — **aucun** droit de contenu, aucune exécution, rien supprimé | clé **distincte** (`orders-signing.key`, `POLICY` seule) ; débit par TV (≤ 20 ordres / jour) ; ordre visant > 50 TV ⇒ **règle des deux personnes** ; `downlink.pause` ; révocation de la clé par liste `revocation` signée par la clé de secours hors ligne ; rotation (nouvelle clé dans l'anneau des APK avant usage, comme `LICENSE-ADMIN.md` § 3.6) |
| Téléphone malveillant | lit rien, modifie rien ; peut **retenir**, **rejouer**, **retarder**, **supprimer** | rejeu ⇒ séquence / identifiant ; retard ⇒ fenêtre de validité ; suppression ou rétention ⇒ pas d'accusé ⇒ reprise par un autre téléphone après 72 h ou par la voie directe ; faux accusé ⇒ signature TV |
| TV trafiquée | ignore les ordres | protection **comptable** au serveur (licence, portefeuille), comme aujourd'hui |
| Fuite de `relay-box.key` | lecture des avis en transit (jetons d'activation : copie possible, inutilisable sur un autre matériel) | rotation (deux clés publiques dans l'APK), dossier des secrets, sauvegarde chiffrée hors serveur |

## 10. Sécurité de la notification et de l'enregistrement

| Abus | Parade | Reste |
|---|---|---|
| Avis forgé | signature par la clé d'installation + jetons signés par un outil de confiance + cible = matériel | aucun enregistrement sans jeton d'outil valide |
| Rejeu | `noticeId` unique, `fp` immuable, `seq` d'avis croissant | rejouer ne change rien |
| Avis pour une autre TV | code dérivé des facteurs de l'avis ; jeton doit viser ces facteurs ; lien collant du jeton d'appareil (voie a) | `CLONE` |
| **Jeton volé dans les 48 h** (copié avant installation) et rapporté par une TV trafiquée qui se fait passer pour le matériel visé avec **sa** clé d'installation | première clé d'installation figée ; la vraie TV ⇒ `BIND_MISMATCH` ⇒ rien payé aux deux, alerte haute, réaffectation par le propriétaire (TOTP) ; preuve indépendante : remise Bluetooth `deliver tv=ok` du journal w23-03 | **limite** : tant que la clé d'installation n'est pas liée à l'activation (D-W23B-4, w22-14), le premier avis gagne |
| Moisson d'essais / productions pour les jetons | émission seulement par des clés d'outil ; plafond d'auto-créations par clé (10/j, 50/mois) ; ouverture illimitée retenue sans déclaration ; rattrapage borné ; vélocité W22 | un outil légitime peut toujours émettre : c'est voulu |
| **Clé d'outil compromise** | rayon : licences auto-créées pour des matériels arbitraires, tranches NDEM/MBOKO | plafonds par clé ; révocation du `kid` (`lic_revocation`) ⇒ plus aucune création ; **action de masse** (TOTP) : licences `created_by=report:<kid>` postérieures à la date de compromission ⇒ suspendues, identités gelées (`wallet.freeze`), reprise W22 niveau 2 ; liste `revocation` poussée par la voie descendante |
| Inondation de la route | rapport : 1 / 10 min / appareil (contrôlé **avant** le budget global, correctif M5) ; relais : 200 objets et 1 Mo / jour / téléphone, 16 avis / jour / TV ; corps ≤ 256 Ko ; bornes de taille avant analyse | — |
| Audit | toute décision d'enregistrement : `lic_audit` + `act_event` ; aucune donnée sensible (empreintes, étiquettes, code masqué `ABCD-****`) | — |

## 11. Risques

| # | Risque | Prob. | Impact | Parade |
|---|---|---|---|---|
| R-1 | Licences auto à `end_at` NULL payées comme illimitées | **certaine aujourd'hui** | élevé (création de valeur) | correctif § 3.3 dans w23-05 avant d'allumer le portefeuille |
| R-2 | TV actuelles du propriétaire introuvables par le registre (console sans événements) | certaine | moyen | procédure § 8 ; avis TV ensuite |
| R-3 | Client portefeuille TV absent : aucun jeton visible avant w22-07 | certaine | moyen | dire la vérité au propriétaire ; w23-05a branché sur w22-07 |
| R-4 | Première clé d'installation volée (jeton copié dans les 48 h) | faible | moyen | `BIND_MISMATCH`, D-W23B-4 |
| R-5 | Retenues trop nombreuses (avis sans déclaration) | moyenne | faible (attente) | w23-03 (journal signé) ; décision en un clic |
| R-6 | Forfait mobile : relais trop restreint | haute (terrain) | moyen | budget de 32 Ko/j pour la licence (D-W23B-8) |
| R-7 | `PolicyHub` non câblé sur la TV | certaine | moyen | w23-08 |
| R-8 | Collision de numéros de migration (W21, W22, W23) | moyenne | faible | « plus haut + 1 » à la fusion, dit au rapport |
| R-9 | Perception d'un canal « caché » | moyenne | élevé (confiance) | transparence écrite, journal visible, liste blanche publiée, interrupteurs |
| R-10 | Compromission du serveur | faible | élevé | clés séparées (licences, ordres, portefeuille, relais), deux personnes pour les ordres de masse, clé de secours hors ligne |
| R-11 | Dérive Kotlin/Java/Python des nouveaux types | moyenne | moyen | vecteurs partagés, trois implémentations (règle du format) |

## 12. Décisions du propriétaire (chacune avec recommandation)

| id | Question | Recommandation | Si non |
|---|---|---|---|
| D-W23B-1 | Un avis de TV vérifié **crée** la licence et le poste (conditions § 3.2) | **Oui** | les TV hors ligne attendent une saisie manuelle |
| D-W23B-2 | Avis scellé pour une **clé publique du serveur** (et non par le jeton d'appareil) | **Oui** | les TV jamais connectées ne peuvent rien notifier |
| D-W23B-3 | `end_at` d'une licence = fin du droit `usage` (auto, import, avis) | **Oui** | clés à durée payées comme illimitées |
| D-W23B-4 | Lier la clé d'installation de la TV à l'activation (ligne `bind|install|<kid>` additive, conservée par les anciennes TV qui ignorent un genre inconnu ; w22-14) | **Oui, au niveau 2** | « premier avis gagne » reste la règle |
| D-W23B-5 | Limite de rattrapage : 90 j automatiques, jusqu'à 366 j si déclarée, jamais au-delà | **Oui** | rattrapage sans limite (risque de clé volée) |
| D-W23B-6 | Ouverture illimitée retenue tant que la clé n'est pas déclarée | **Oui** | 5 000 + 50 versés sur la seule parole de la TV |
| D-W23B-7 | Plafond d'auto-créations par clé d'outil : 10 / jour, 50 / mois | **Oui** (réglable) | une clé volée crée sans limite |
| D-W23B-8 | Relais sur forfait mobile compté : objets de licence seulement, 32 Ko / jour, jamais en itinérance | **Oui** | notification seulement en Wi-Fi |
| D-W23B-9 | Clé des ordres **distincte** (`POLICY` seule) | **Oui** | la clé des licences signe aussi les ordres |
| D-W23B-10 | Nouvelles actions (`wallet.*`, `report.request`, `sync.force`, `update.available`, `downlink.*`) | **Oui** | voie descendante limitée à l'existant |
| D-W23B-11 | Règle des deux personnes pour tout ordre visant plus de 50 TV | **Oui** | un compte compromis verrouille le parc |
| D-W23B-12 | Textes de transparence du § 9.6 a | **Oui, à relire** | relais non annoncé : risque de confiance |
| D-W23B-13 | Le relais descendant est permis pour les TV de **tous** les clients (pas seulement celles du propriétaire) | **Oui** (avec transparence et interrupteurs) | relais limité aux TV de démonstration |

## 13. Ce qui a été lu, et ce qui n'a pas pu être vérifié

**Lu** : `docs/LICENSE-ADMIN.md` (entier), `docs/ACTIVATION-FORMAT.md` (entier), `docs/OWNER-CONSOLE.md` (entier), `docs/ORDRES.md` (entier), `DESIGN-W23-SUIVI-…` (entier), `DESIGN-W22-JETONS-…` § 0-3.2, 3.5-3.7, 7, 14, `DESIGN-W21-…` § 3.4-3.4 quater, 9, 10 ; rapports `sonnet-w22-02.md`, `audit-opus-w22-02.md`, `audit-opus-w23-01.md`, `sonnet-w23-01.md` (branche) ; cahiers w23-04, w21-07 (deux), index W23 ; code : `B/wallet/{JdbcLicenseFacts, GrantService}.java`, `V62__wallet.sql` (identité, réclamation), `V51__licenses_core.sql`, `B/licenses/LedgerService.java:260-410`, `LicenseService.java:284-316` et extraits, `ActivationService.java:55-110` ; branche W23 : `A/ReportController.java`, `A/ActivationObserver.java` ; `OL/ConsoleActivity.kt:180-210`, `OL/OwnerStore.kt:57-65`, `R/ActivationCenter.kt` (structure, persistance), `C/owner/{InstallSigner, TvProof}.kt` (en-têtes) ; recherches textuelles dans `android/` (`PolicyHub`, `OrdersRuntime`, `wallet/sync`, `TrustRegistry.MAX_PHONES`, `X25519`).
**Non lu ou non vérifié** : `LicensedIssuer`, `PhoneConsole`, `DK/Desk.kt` ligne à ligne (quelles émissions écrivent un registre) ; `TvConnect`, `HelloHandler`, `BtApiTunnel` ligne à ligne (point d'accroche exact de l'avis et des routes `/api/tele/*`) ; un plafond de **8 TV par téléphone** (non trouvé dans le code) ; si toutes les TV du terrain ont déjà une clé d'installation Ed25519 (`InstallSigner`) et depuis quelle version ; si la clé X25519 de `DEVICE_INFO` est présente sur toutes les TV ; l'état de fusion de w21-01b, w21-02b, w21-04, w21-07, w22-03, w22-07 ; l'état de la correction de w23-01 par l'autre agent ; le déploiement réel des modules licences et portefeuille ; la route d'émission de l'API avec durée ; MySQL 8.4 (aucun test lancé) ; tailles et débits (tous estimés) ; prix des modèles (repris des index, non vérifiés).
