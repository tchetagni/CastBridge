# Conception W23 — Suivi des codes d'activation (essai et production) : inventaire serveur, émission hors ligne déclarée, synchronisation avec la console du propriétaire, historique immuable

> Document de conception (architecte, 2026-10-04). **Aucun code n'est modifié par ce document.** Branche de référence `integration/agents` (HEAD `3484fbb1`). Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `R/` = `android/receiver/…/receiver/`, `B/` = `backend/src/main/java/castbridge/server/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`, `OW/` = `android/owner/src/main/kotlin/castbridge/owner/`, `DK/` = `tools/activation-desktop/src/main/kotlin/castbridge/desktop/`. Rien n'a été exécuté : aucun réseau, aucun appareil, aucun serveur. Les chiffres non mesurés sont marqués « estimé ». Fichiers lus : § 13.

## Demande du propriétaire (verbatim) et lecture retenue

1. Demande : « traque et synchronise tous les jetons activés avec la console propriétaire et garde l'historique de toutes les informations utiles dans le serveur ».
2. **Correction du propriétaire (verbatim, transmise par le coordinateur)** : « je parle des codes d'activation d'essai et de production ».
3. **Lecture retenue** : « jetons activés » = les **codes et clés d'activation `cbx1`** (essai et production, y compris la **clé compacte** de 165 caractères à saisir) et ce qui en découle : **licences** du serveur (`lic_license`, production seulement), **postes** (`lic_seat`), **commandes du propriétaire** exécutées sur une TV (`support`/`reset-trial`, `unlock`, `open_all`), **révocations**, **transferts**. « Console propriétaire » = l'application **« CastBridge Propriétaire »** (`android/owner` + `android/ownerlib`, `docs/OWNER-CONSOLE.md`) **et** l'interface web d'administration du serveur (`/admin/**`, gabarits `backend/src/main/resources/templates/admin/`, ressources `static/admin/assets/`). « Synchronise » = la console montre l'**état du serveur, qui fait foi**, presque en temps réel, **et** le serveur apprend tout ce que la console (et l'outil de bureau) émet hors ligne. « Historique » = un journal **immuable**, interrogeable, conservé au serveur.
4. **Lecture abandonnée** : le suivi des **soldes** NDEM/MBOKO (première lecture du coordinateur) n'est **pas** conçu ici ; seul subsiste le lien du § 11 (les attributions de jetons suivent l'activation et la licence).
5. **Ambiguïtés signalées** : (a) « synchronise avec la console » pourrait vouloir dire « la console **pousse** ses émissions au serveur » **ou** « la console **affiche** l'état du serveur » : les **deux** sont conçus (§ 3 et § 4) ; (b) « tous » inclut-il les clés émises par les **points focaux** (agents délégués, mandats W4-C, `C/owner/DelegatedVerifier.kt`) ? lu **oui**, au niveau 2 (D-W23-13) ; (c) la demande parle de « fenêtre d'installation ≤ 1 an » (lecture du coordinateur, issue de `OWNER-CONSOLE.md` § 4) : **le format en vigueur dit 48 h exactement** (`docs/ACTIVATION-FORMAT.md` § 3.2-3.3, « Durées ») ; « jusqu'à 1 an » est la durée de la **phase hors ligne** (§ 8 de `OWNER-CONSOLE.md`), pas la fenêtre : la conception retient **48 h**.

## 0. En vingt lignes

1. **Le serveur sait déjà beaucoup** : registre signé des outils (`lic_event`, import `LedgerService`, union idempotente), émissions serveur **et** importées (`lic_issuance.source = SERVER | IMPORT`, empreinte du jeton seulement), postes, transferts, révocations, conflits (`TWO_TOOLS`, `OVER_QUOTA`…), journal d'audit chaîné HMAC (`lic_audit`), appareils (`device` : version, dernier contact). **Il ne sait pas** : quelle TV porte **réellement** quelle activation (aucune route utilisée par la TV ne la rapporte), les **clés compactes** (hors registre), les **commandes** du propriétaire (journal local de la console seulement), la **remise** par Bluetooth, et il n'apprend le registre que par **import manuel** d'un fichier.
2. **Trois voies d'information, croisées** : (A) **journal d'émission signé** de chaque outil (bureau, téléphone propriétaire ; serveur en interne), enveloppe `cbx1` de type **`journal`**, numéroté et chaîné, remonté à la prochaine occasion ; (B) **rapport de la TV** (`POST /api/v1/activations/report`, jeton d'appareil existant) : activations installées, commandes actives, version ; direct, ou par le **téléphone coursier** W21 ; (C) **remise Bluetooth** vue par la console (trame `RESULT` de la TV).
3. **Réconciliation** « émis ↔ constaté » au serveur : activation **non déclarée** (vue sur une TV, absente de tout journal), **trou** dans le journal d'un outil, **doublon** (même activation sur deux appareils), **hors fenêtre** (installée après les 48 h : TV trafiquée ou horloge), activation **terminée/révoquée encore utilisée**, **production sans licence**, **postes dépassés**, **commande non déclarée**, **TV muette**. Alertes **douces** : jamais de révocation automatique.
4. **Synchronisation** : le serveur fait foi ; API d'administration **en lecture** (`/api/v1/admin/activations/**`, pagination par curseur, filtres, export CSV/JSONL) ; **flux de changements** par interrogation longue (25 s) ; indicateurs « à jour il y a X » par TV, par outil, par activation ; une activation jamais vue d'une TV s'affiche « émise le …, jamais constatée sur une TV », avec la date de fermeture de sa fenêtre.
5. **Console** : pages web `/admin/activations/**` (adaptées au téléphone) ; application propriétaire : **export du journal** (fichier, partage, QR) et bouton « Ouvrir le suivi » (navigateur) **sans permission réseau** pendant la phase hors ligne (recommandé, D-W23-3) ; variante en ligne (hôte unique épinglé, lecture seule) **sur décision**.
6. **Historique** : table d'évènements **immuable chaînée** (`act_event`), journal d'audit **des lectures** (`adm_read_audit`), lots de journal **conservés signés** (`act_journal_batch`), instantanés quotidiens (comptes) et mensuels (par TV), **point de contrôle quotidien signé** (tête de chaîne) ; **jamais purgé**, archive froide au-delà de 24 mois ; rapports bruts des TV bornés à 90 j. ≈ **0,5 Go / an pour 10 000 TV** (estimé).
7. **Sécurité** : permissions `ACT_READ` / `ACT_EXPORT` / `ACT_ALERT_DECIDE` ; **toute lecture auditée** (jeton d'API compris) ; actions destructrices (révoquer, libérer, réémettre) **restent** dans le module des licences, sous TOTP et motif ; jamais un jeton ni une clé compacte en clair (empreinte SHA-256 + étiquette de 8 hex) ; dans l'historique, la TV est désignée par une **référence HMAC** (`tv_ref`), effaçable par le droit à l'effacement sans casser la chaîne.
8. **Cahiers** (vague **W23**, indépendante du grand livre W22) : w23-01 API serveur + **V65** (L), w23-02 pages web (M), w23-03 outils du propriétaire (journal signé, téléphone et bureau) (M), w23-04 rapport de la TV (S) ; ordre 01 → (02 ∥ 03 ∥ 04) ; **≈ 8 $**, ≈ 6,5 agent·jours, ≈ 4 jours ouvrés (estimé).
9. **Prérequis** : module des licences **allumé** (`CASTBRIDGE_LICENSES_ENABLED`, éteint par défaut) et clés publiques des outils dans `castbridge.licenses.trusted-keys` ; sans cela, le suivi ne voit que les rapports des TV.
10. Décisions : 14, chacune avec recommandation (§ 10) ; aucune ne bloque w23-01.

## 1. Ce qui existe (faits lus le 2026-10-04) et ce qui manque

### 1.1 Faits

| Élément | Où | Ce qu'il donne au suivi |
|---|---|---|
| Licences, postes, alias, émissions, transferts, révocations, observations | `V51__licenses_core.sql` : `lic_license` (état `ACTIVE/SUSPENDED/REVOKED/EXPIRED`, `start_at`, `end_at` NULL = sans fin, `grace_days`), `lic_seat` (`device_code`, empreintes hachées, `k`, `first_seen`, `last_seen`), `lic_seat_alias`, `lic_issuance` (`kind`, `kid`, `nonce`, `issued_at`, `not_after` = fin de fenêtre, `channel`, **`token_fingerprint`**, **`source SERVER|IMPORT`**), `lic_transfer`, `lic_revocation` (clé ou poste à une date), `lic_sighting` (purgé à 90 j, `AbuseService.purgeSightings`) | l'inventaire **émis** connu du serveur (production, et essais émis par le serveur) |
| Registre signé des trois outils | `V52` : `lic_event` (événements `license|issue|transfer|revoke`, `kid`, `at_ms`, signature, `source SERVER|IMPORT`), `lic_ledger_import`, `lic_conflict` (`UNKNOWN_LICENSE`, `OVER_QUOTA`, `TWO_TOOLS`, `TRANSFER_CAP`) ; `B/licenses/LedgerService.java` (union idempotente, politique REVIEW/AUTO) ; route web `POST /admin/licenses/registry/import` | ce que le **bureau** et le **téléphone** ont émis, **quand le propriétaire importe le fichier** ; `LicensedIssuer` (`C/owner/LicensedIssuer.kt:173`) écrit un événement `issue` **pour toute émission**, essai compris |
| Journal d'audit chaîné | `B/licenses/AuditLog.java` + `lic_audit`, `lic_audit_head` (verrou de tête, HMAC si `license-audit.key` existe, `verify()`) | modèle à réutiliser ; il ne journalise que les **écritures** du module des licences, pas les lectures |
| Rôles et TOTP | `B/licenses/Role.java` (`OWNER`, `SUPPORT`, `READONLY` ; permissions « sensibles » ⇒ TOTP) ; `admin_user.role`, `totp_*` (V52) ; `SecurityConfig` : `/api/v1/admin/**` = jeton porteur `CASTBRIDGE_ADMIN_TOKEN` (≥ 32 car., vaut OWNER), `/admin/**` = formulaire + session + CSRF | le socle du contrôle d'accès |
| Appareils | `V2__devices_admin.sql` : `device` (`version_code`, `version_name`, `last_seen`, `android_id_hash`), `device_install`, `device_version`, `device_heartbeat` (30 j), `device_daily` ; `POST /api/v1/devices/heartbeat` | version de l'application et dernier contact **par installation** ; **aucun lien** avec le code d'appareil d'activation |
| Route d'entitlement | `GET /api/v1/entitlements/me?deviceCode=` (`PublicLicenseController`, éteinte par défaut, enregistre une observation) | **aucun appelant** trouvé dans `android/` ni `tools/` (recherche textuelle) |
| Console du téléphone | `OL/ConsoleActivity.kt` (onglets « Activer », « Clé publique », « Journal » ; application `castbridge.owner`, **sans permission**, jamais publiée) ; `C/owner/PhoneConsole.kt` ; `AuditChain` (`C/owner/OwnerVault.kt:81`, chaîne SHA-256 locale des actions : `unlock`, `issue`, `license`) | ce que fait la console, **resté sur le téléphone** |
| Outil de bureau | `DK/Desk.kt` : `journal.jsonl` (« jamais la clé ni le code »), registre, `compact(...)` (clé compacte) ; `DK/Cli.kt` : `registre exporter/importer` | idem, sur le Mac |
| Points focaux (agents) | `C/owner/DelegatedVerifier.kt` (mandat + activation, jamais d'illimitée) | émetteurs supplémentaires, non suivis |
| Téléphone coursier (W21) | `DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 3.4 : lots **scellés** TV → téléphone de confiance → `POST /api/v1/events/relay` | voie pour une TV sans Internet |

### 1.2 Trous à combler

| # | Trou | Conséquence | Comblé par |
|---|---|---|---|
| T-1 | Aucune TV ne rapporte l'activation qu'elle porte | « activé sur quelle TV » est inconnu ; une activation copiée ou non déclarée est invisible | rapport TV (§ 3.3, w23-04) |
| T-2 | Clés compactes absentes du registre | émission invisible | journal d'émission (§ 3.2, w23-03) |
| T-3 | Commandes (`reset-trial`, `unlock`, `open_all`) absentes du registre | « tout ouvert » non suivi | journal d'émission + rapport TV |
| T-4 | Registre importé **à la main** | retard illimité | téléversement du journal + rappel « journal non remonté depuis X » |
| T-5 | Aucune vue « par TV » ni « par activation » ; aucune réconciliation | rien à montrer à la console | API et pages (§ 4-7) |
| T-6 | Lectures d'administration non journalisées | pas de traçabilité de « qui a regardé quoi » | `adm_read_audit` |
| T-7 | Appareil API (`device`) non relié au code d'appareil d'activation | version et dernier contact non rattachés | rapport authentifié par le jeton d'appareil |

## 2. Ce qui est suivi (inventaire)

### 2.1 Par activation (une ligne par jeton connu, clé = empreinte)

| Champ | Source | Remarque |
|---|---|---|
| `fp` = SHA-256 du jeton `cbx1` (ou des 82 octets de la clé compacte) ; `tag` = 8 premiers hex (affichage) | journal, registre, `lic_issuance.token_fingerprint`, rapport TV | **jamais le jeton ni la clé compacte** |
| forme : `ENVELOPE` / `COMPACT` | idem | |
| outil : `kid`, type d'outil `DESK` / `PHONE` / `SERVER` / `AGENT` / `UNKNOWN`, portées de la clé | `castbridge.licenses.trusted-keys` (« `desktop:<pub>:portées,phone:…` »), clé serveur | `UNKNOWN` = clé hors anneau : alerte |
| `kind` (`TRIAL`/`PRODUCTION`), `subject` (`tv`/`phone`), `license_id` (`trial` pour l'essai), `seat_id` | en-tête et corps `cbx1` | clé compacte : `kind` + ensemble de produits |
| `seq` par clé, `nonce` (empreinte), `issued_at`, `not_before`, `expires_at` (**fenêtre d'installation, 48 h**) | en-tête | |
| durée : `usage_to` (essai 1..365 j ; production 1..3 660 j) ou **illimitée** (aucune ligne `usage`) ; `months = ceil(jours/30)` ; `super` (SUPER_UNLIMITED) ; droits résumés (`purchase`, `subscription`, `openall`, `rental` : types et nombres, sans détail commercial) | corps | |
| cible : `device_code`, `k`, empreintes hachées des facteurs (`TYPE|32 hex`) | en-tête | empreintes déjà hachées par le format |
| état (§ 2.3), drapeaux (`declared_journal`, `declared_registry`, `server_issued`, `seen_on_tv`, `delivered_bt`), dates `first_seen_tv_at`, `last_seen_tv_at`, `installed_at` (dite par la TV si elle la connaît) | réconciliation | |

### 2.2 Par TV (une ligne par code d'appareil)

Code d'appareil et `tv_ref` ; activation **courante** (et précédentes) ; édition effective dite par la TV (`TRIAL`, `PRODUCTION`, `SUPER`, `NONE`, `ENDED`) ; fin d'usage ; licence et poste (état, `start_at`, `end_at`, grâce) lus dans `lic_*` ; **commandes actives** (`open_all` jusqu'au …, `unlock` lots/bouquets jusqu'au …) ; **remises à zéro d'essai** (nombre, dernière date) ; installations API liées (`device.id`, nombre, `android_id_hash` distincts) ; **version** de CastBridge-TV (`version_name`, `version_code`) ; dernier contact **par voie** (`direct`, `courier`, `console_bt`, `heartbeat`) ; alertes ouvertes ; état de réconciliation (`OK`, `ÉCART`, `JAMAIS_VUE`).

### 2.3 États d'une activation (liste fermée)

```
                 journal / registre / émission serveur
                               │
                               ▼
   ┌──────────── ÉMISE ───────────────┐  remise Bluetooth (RESULT ok) ou rapport TV
   │  (jamais vue d'une TV)            ├──────────────────────────────► ACTIVÉE
   │  fenêtre de 48 h close            │                                 │  │  │
   ▼  sans constat                     │                                 │  │  └─ activation plus récente acceptée
NON_CONSTATÉE (*)                      │                                 │  │     (même poste ou même TV) ──► REMPLACÉE
   │ rapport TV tardif ────────────────┘                                 │  └─ fin du plafond d'usage ──► TERMINÉE
                                                                         └─ clé ou poste révoqué ──► RÉVOQUÉE
   vue sur une TV sans aucune déclaration ──► ACTIVÉE + drapeau NON_DÉCLARÉE (alerte)
(*) « non constatée », pas « non installée » : une TV hors ligne qui ne rapporte rien peut l'avoir installée.
```

L'état de la **licence** (`ACTIVE/SUSPENDED/REVOKED/EXPIRED`, grâce) s'affiche **à côté**, jamais confondu avec l'état de l'activation (clé d'activation ≠ licence : `DESIGN-W22-…` § 1.2).

### 2.4 Vue globale

Compteurs par **édition** × **outil** × **état** (émises, activées, non constatées, remplacées, terminées, révoquées) ; production : licences par état, postes utilisés / permis, dépassements ; commandes `open_all`/`unlock` actives ; remises à zéro d'essai sur 30 j ; TV par fraîcheur (§ 4.4) ; outils : dernier journal remonté, chaîne intacte ou non ; alertes ouvertes par type ; versions de CastBridge-TV parmi les TV activées.

## 3. Émission hors ligne : comment le serveur apprend tout de même

### 3.1 Vue d'ensemble

```
 Bureau (Mac, hors ligne)        Téléphone propriétaire (hors ligne)            CastBridge-TV
 émet activation / compacte      émet, envoie par Bluetooth, commande ───BT───► accepte, RESULT ok
 journal.jsonl + registre        AuditChain + registre                           mémorise l'état
   │ journal signé (cbx1          │ journal signé (cbx1 type journal)              │ rapport (jeton d'appareil)
   │ type journal) : fichier      │ : fichier / partage / QR                       │ direct (12-24 h, à chaque
   │ « journal-exporter »         │ (en ligne : téléversement, D-W23-3)            │ changement) ou lot scellé
   ▼                              ▼                                                ▼ par le téléphone coursier (W21)
 ┌───────────────────────────── castbridge-api (module « activations », B/activations/**) ─────────────────────────────┐
 │ POST /api/v1/admin/activations/journal   POST /admin/licenses/registry/import (existant)   POST /api/v1/activations/report │
 │        │ vérifie signature, kid de confiance, chaîne, seq dense   │ union lic_event          │ vérifie chaque cbx1, empreinte  │
 │        ▼                                                          ▼                          ▼                                 │
 │   act_journal_batch ──► act_event (immuable, chaîné) ◄── projection ──► act_key / act_tv ◄── act_report (90 j)              │
 │                                   │ réconciliation (à chaque arrivée + nocturne 03:50 Douala) ──► act_alert                 │
 │                                   ▼                                                                                        │
 │                     API admin en lecture + flux de changements ──► pages /admin/activations (navigateur, téléphone)         │
 └──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Journal d'émission signé (voie A)

**Pourquoi un journal en plus du registre** : le registre (`castbridge-licence-registry-v1`) est un ensemble d'événements **comptables** (licence, émission, transfert, révocation) dont la liste de types est **fermée et rejouée à l'identique par les trois outils** (`ACTIVATION-FORMAT.md` § 8.2) : y ajouter des types casserait le rejeu des anciens outils (type inconnu ⇒ `MALFORMED`). Le journal porte ce que le registre ne porte pas (clés compactes, commandes, remises, refus) et apporte une **numérotation dense** qui rend un trou visible.

**Format** : une **enveloppe `cbx1`** (codec unique, `ACTIVATION-FORMAT.md` § 3 : « ajouter un type ajoute un analyseur de corps et une portée, jamais un second format ») de type **`journal`**, `target=any`, `seq` = numéro du **lot** pour cette clé (strictement croissant), signée par la clé **de l'outil** qui a fait les actions. **Aucune portée nouvelle** : la règle est « une clé ne déclare que ses propres actions » (toute entrée porte implicitement le `kid` signataire) ; le serveur accepte un journal signé par une clé de son anneau d'outils, non révoquée.

```
castbridge-envelope-v1
type=journal
kid=<16 hex>   seq=<n° de lot>   nonce=<16 hex>   issuedAt=<ms>   notBefore=<ms>   expiresAt=<ms + 365 j>   target=any
--
tool=<desk|phone|agent>
app=<version de l'outil>
from=<n° de la première entrée>      to=<n° de la dernière entrée>      prev=<64 hex : empreinte de la dernière entrée du lot précédent, ou 0×64>
e=<n>|<at ms>|<type>|<champs séparés par « | »>       (une ligne par entrée, n dense et croissant)
```

| Type d'entrée | Champs (jamais de secret) |
|---|---|
| `issue` | `fp`, `form=envelope`, `kind`, `subject`, `license`, `seat`, `device` (code), `k`, `nonce` (empreinte 16 hex), `aseq` (seq de l'activation), `issuedAt`, `expiresAt`, `usageTo` ou `unlimited`, `super` 0/1, `rights` (types:nombre), `registry` (identifiant de l'événement `issue` correspondant) |
| `compact` | `fp`, `form=compact`, `kind`, `device`, `windowStartHour`, `set` |
| `deliver` | `fp`, `way` (`bt`/`usb`/`qr`/`text`), `tv` (`ok`/`refused:<raison fermée>` si Bluetooth, sinon `-`) |
| `command` | `power` (`support`/`unlock`/`open_all`), `action` (`diagnostic`/`reset-trial`/`-`), `bundles` (nombre), `lots` (nombre), `days`, `clamped` 0/1, `device`, `challenge` (8 premiers hex de SHA-256 du défi), `result` (`ok`/`refused:<raison>`) |
| `license` / `revoke` / `transfer` | identifiant de l'événement du registre (le contenu reste dans le registre) |
| `refused` | émission refusée par l'outil : `reason` (message fermé), `device` |

**Chaîne** : empreinte d'entrée `h_n = SHA-256(h_{n−1} | ligne e=…)` ; le lot dit `prev` ; le serveur vérifie la continuité **d'un lot à l'autre** (`prev` = dernière empreinte connue pour ce `kid`) et la densité de `n`. Un lot manquant ⇒ trou visible (« entrées 141-163 de l'outil `phone …a3f2` jamais reçues »). Taille : ≈ 200 o par entrée ; lot ≤ 500 entrées, ≤ 128 Ko.

**Remontée** :

| Outil | Comment | Fréquence attendue |
|---|---|---|
| Bureau | `journal-exporter [--depuis n] --sortie journal-<kid>-<from>-<to>.cbj` (enveloppe), puis **téléversement** par la page web « Outils » (ou `curl` avec le jeton d'API) | à chaque séance d'émission |
| Téléphone propriétaire, **hors ligne** (défaut, D-W23-3 : B) | onglet « Journal » : **Exporter** (fichier dans `Download/`, partage Android, ou QR en série pour ≤ 2 Ko), puis téléversement par la page web ouverte dans le navigateur du même téléphone | à chaque séance ; rappel dans l'application après 7 j |
| Téléphone propriétaire, **en ligne** (option A, D-W23-3) | téléversement direct `POST /api/v1/admin/activations/journal` sur session signée (§ 6.3) | à chaque émission, file locale sinon |
| Serveur | ses émissions écrivent **directement** `act_event` (même transaction que `lic_issuance`), sans journal signé | immédiat |
| Point focal (niveau 2) | même format, clé d'agent (mandat) | D-W23-13 |

Le **registre** continue d'être importé par la route existante ; la page « Outils » propose les deux fichiers côte à côte, et le serveur rapproche `issue` du journal et événement `issue` du registre par `registry=`.

### 3.3 Rapport de la TV (voie B)

`POST /api/v1/activations/report` (jeton d'appareil existant de `DeviceClient`, comme `PlayTicketController`) :

```json
{"v":1,"deviceCode":"XXXX-XXXX-XXXX-XXXX","app":{"code":1412,"name":"0.14.12-beta"},
 "activations":["cbx1.…"],                 // ≤ 4 jetons installés (≤ 8 192 car. chacun), le courant en premier
 "compact":["<82 octets en base64>"],      // ≤ 2, seulement si la TV les garde (à vérifier, § 13)
 "state":{"edition":"PRODUCTION","usageTo":null,"super":false,"openAllUntil":0,"unlockUntil":0,
          "trialResets":1,"installedAt":{"<fp8>":1759600000000},"commands":[["open_all","<challenge8>",1759600000000,30]]},
 "at":1759600000000}
```

Le serveur **vérifie chaque jeton** (`B/licenses/EnvelopeVerifier.java`, clés de confiance), calcule l'empreinte, **jette le jeton**, garde les champs analysés ; refuse un jeton dont les facteurs ne correspondent pas au `deviceCode` (k parmi n) ; relie `device.id` (jeton d'appareil) ↔ code d'appareil. **Cadence** : à chaque **changement** (activation acceptée, commande exécutée, remise à zéro, mise à jour de l'application), sinon **toutes les 24 h** (± 1 h) ; au plus 1 rapport / 10 min / appareil ; ≈ 2-4 Ko (≈ 0,5-0,8 s à 40 kbps, estimé). **Jamais pendant une partie Internet ni dans les 30 s qui suivent** (règle W21). **TV sans Internet** : le rapport entre dans la file **scellée** W21 (`SealedOutbox`, type `act.report`) et part par le **téléphone coursier** (`POST /api/v1/events/relay`), qui le remet à ce module ; **par la passerelle Bluetooth** : permis (≤ 4 Ko par jour, hors partie), D-W23-6. La réponse porte `{"next": <h>, "revocations": <liste signée si changée>}` : le serveur peut **ralentir** (`next`) ou **couper** (`next = 0`) les rapports sans livrer d'APK.

**Consentement** : un rapport d'activation est une fonction de **licence** (essentielle), pas une statistique d'usage : il ne passe **pas** par la porte de consentement W21 (D-W23-5). Contenu : identifiants techniques seulement.

### 3.4 Remise Bluetooth (voie C)

La console reçoit déjà la trame `RESULT` (`ok` + message) après `ACTIVATION` ou `COMMAND` (`OWNER-CONSOLE.md` § 5) : elle l'écrit en entrée `deliver`/`command` du journal. Au serveur, `deliver … tv=ok` vaut **constat** (état `ACTIVÉE`, voie `console_bt`), même si la TV n'a jamais Internet.

### 3.5 Réconciliation « émis ↔ constaté » et alertes

Exécutée **à chaque arrivée** (journal, registre, rapport) pour les objets touchés, et **chaque nuit à 03:50 Douala** pour tout (après les travaux existants de 03:20 et 03:40).

| Code | Condition | Gravité | Délai de grâce | Effet (toujours doux) |
|---|---|---|---|---|
| `UNDECLARED` | activation vue (rapport TV) dont l'empreinte n'est ni dans un journal, ni dans le registre, ni émise par le serveur | haute | **72 h** après le premier constat (les journaux arrivent en retard) | alerte ; ligne marquée « non déclarée » |
| `JOURNAL_GAP` | `n` non dense ou `prev` discordant pour un `kid` | haute | 7 j | alerte « entrées a-b manquantes » |
| `JOURNAL_BROKEN` | signature fausse, clé révoquée ou inconnue, entrée réécrite (même `n`, autre empreinte) | critique | aucun | lot refusé, gardé en quarantaine |
| `CLONE` | même empreinte rapportée par **deux installations** de `android_id_hash` différents en 30 j, ou deux codes d'appareil | haute | aucun | alerte ; rejoint la logique du lien collant (`PlayTicketService.MAX_DEVICES_PER_CODE = 2`) |
| `OUT_OF_WINDOW` | `installedAt` > `expiresAt` + 24 h, ou fenêtre > 48 h | haute | aucun | alerte (TV trafiquée ou horloge) |
| `ENDED_IN_USE` | TV qui se dit `PRODUCTION`/`TRIAL` après `usage_to`, ou porte une activation d'une clé/d'un poste révoqué après la diffusion de la révocation + 7 j | moyenne | 7 j | alerte |
| `UNKNOWN_KEY` | jeton signé par un `kid` hors anneau du serveur | haute | aucun | alerte |
| `LICENSE_PENDING` | activation de production sans licence connue (`lic_seat` actif) | basse | 7 j | file « à enregistrer » |
| `OVER_SEATS` | plus de TV constatées que de postes permis | moyenne | aucun | alerte (rejoint `lic_conflict.OVER_QUOTA`) |
| `UNDECLARED_COMMAND` | TV qui rapporte `open_all`/`unlock`/`reset-trial` sans entrée `command` correspondante (défi tronqué) | haute | 72 h | alerte |
| `SILENT_TV` | TV activée sans aucun contact depuis 30 j (production) ou 14 j (essai en cours) | info | — | colonne « muette », pas d'alerte bloquante |
| `TOOL_STALE` | outil qui a émis (activations vues) mais n'a remonté aucun journal depuis 14 j | moyenne | 14 j | rappel au propriétaire |

**Aucune action automatique** (ni révocation, ni libération de poste) : l'administrateur **décide** (accuser réception, classer avec motif, ou aller révoquer dans le module des licences). Les décisions sont des évènements.

## 4. Synchronisation avec la console

### 4.1 Principe

Le **serveur fait foi**. La console **lit** ; elle ne garde qu'un cache d'affichage étiqueté de sa date. Les outils hors ligne **poussent** leurs journaux. Les TV **rapportent**. Rien n'est « fusionné » côté console.

### 4.2 API d'administration en lecture (`/api/v1/admin/activations/**`)

| Route | Paramètres | Sortie |
|---|---|---|
| `GET /activations` | `state`, `kind`, `tool` (type ou `kid`), `license`, `device` (code complet ou 4 derniers car.), `from`, `to` (date d'émission), `flag` (`undeclared`, `clone`…), `q` (étiquette `tag`), `cursor`, `limit` (50, ≤ 500), `sort` (`issued`/`seen`) | page + `nextCursor` (curseur = `(clé de tri, id)`, aucun `OFFSET`) |
| `GET /activations/{fp}` | — | détail + évènements de cette activation |
| `GET /tvs` | `edition`, `freshness` (`fresh`/`stale`/`old`/`never`), `alerts`, `version`, `license`, `cursor`, `limit` | page |
| `GET /tvs/{deviceCode}` | — | fiche TV + chronologie complète (§ 7.2) |
| `GET /dashboard` | `day` | compteurs § 2.4 (depuis l'instantané du jour + delta) |
| `GET /alerts` | `state` (`OPEN`/`ACK`/`CLOSED`), `type`, `cursor` | page |
| `POST /alerts/{id}/ack`, `/close` | `reason` (obligatoire pour `close`) | permission `ACT_ALERT_DECIDE` (TOTP) |
| `GET /tools` | — | outils, dernier lot, chaîne, trous |
| `GET /changes` | `after` (id d'évènement), `wait` (0-25 s) | évènements postérieurs (≤ 200), ou liste vide au bout de `wait` |
| `GET /export` | `what` (`activations`/`tvs`/`events`/`alerts`), `format` (`csv`/`jsonl`), filtres ci-dessus | flux, ≤ 200 000 lignes, permission `ACT_EXPORT` |
| `GET /checkpoints` | `from`, `to` | points de contrôle signés (§ 5.5) |
| `POST /journal` | corps = enveloppe `journal` | lot accepté, entrées nouvelles, trous |

Pages web : mêmes données par `/admin/activations/**` (session, CSRF), qui appellent les mêmes services.

### 4.3 Temps réel

**Interrogation longue** (`/changes?after=&wait=25`) plutôt que SSE : traverse nginx et les réseaux mobiles sans réglage de mise en mémoire tampon, se reprend sans état (le client garde `after`), une connexion par onglet. La page web relance aussitôt ; l'application propriétaire en ligne (option A) seulement **écran ouvert**, sinon actualisation manuelle. Délai de propagation visé : **≤ 30 s** entre l'arrivée d'un rapport ou d'un journal et l'affichage (estimé). Bornes : 1 interrogation longue simultanée par session, 20 sessions au total.

### 4.4 Fraîcheur (« à jour il y a X »)

| Objet | Horodatage | Vert | Orange | Rouge | Gris |
|---|---|---|---|---|---|
| TV | dernier rapport (toutes voies) | < 26 h | 26 h - 7 j | > 7 j | « jamais vue » |
| Outil | dernier lot de journal | < 7 j | 7-14 j | > 14 j **et** activations non déclarées | « aucun journal » |
| Activation | dernier constat sur une TV | idem TV | | | « émise le 04/10 18:42, jamais constatée · fenêtre close le 06/10 18:42 » |
| Page | dernière réponse du serveur | < 60 s | 1-5 min | > 5 min (« connexion perdue ») | — |

Une TV jamais vue montre son **dernier état connu** (celui du journal : émise pour elle le …, remise par Bluetooth le …) et l'**écart** (« aucun rapport depuis l'émission, 12 j »). Pour une TV muette, la fiche montre l'état au dernier rapport, barré de « au 21/09 ».

### 4.5 Divergences

Une **divergence** = deux sources qui ne disent pas la même chose (journal ↔ registre ↔ `lic_issuance` ↔ rapport TV ↔ licence). Elle ouvre une alerte du § 3.5 et un indicateur « ÉCART » sur la fiche TV ; la réconciliation nocturne publie le compte des écarts dans le tableau de bord.

## 5. Historique au serveur

### 5.1 Tables (migration **`V65__activation_tracking.sql`**)

**Numéro** : AUCUN numéro n'est réservé (règle unique, 2026-10-04) : w23-01 prend « plus haut numéro existant + 1 » AU MOMENT DE SA FUSION et le dit au rapport (V65 n'est qu'une indication de rédaction ; V62 = grand livre W22, W21 prendra aussi le suivant à sa fusion). Les cahiers W22 « plus haut + 1 » (w22-10, w22-16) font de même. Toutes les tables sont **nouvelles** ; **aucune table existante n'est modifiée** (le module des licences est lu, jamais écrit).

```
act_tool        (kid CHAR(16) PK, tool ENUM('DESK','PHONE','SERVER','AGENT','UNKNOWN'), label VARCHAR(64), scopes VARCHAR(200),
                 last_entry_n BIGINT, last_entry_hash CHAR(64), last_batch_seq BIGINT, last_upload_at DATETIME(6), chain_ok BOOL)
act_journal_batch (id PK, kid, seq BIGINT, sha256 CHAR(64) UNIQUE, text MEDIUMTEXT [enveloppe signée, aucun secret], from_n, to_n,
                 received_at, via ENUM('web','api','phone'), accepted INT, duplicate INT, rejected INT, status ENUM('OK','QUARANTINE'))
act_key         (fp CHAR(64) PK, tag CHAR(8), form ENUM('ENVELOPE','COMPACT'), kid, kind, subject, license_id, seat_id, tv_ref CHAR(16),
                 k INT, aseq BIGINT, issued_at, expires_at, usage_to NULL, unlimited BOOL, super BOOL, rights VARCHAR(200),
                 state VARCHAR(16), state_at, flags SET('declared_journal','declared_registry','server_issued','seen_on_tv','delivered_bt',
                 'undeclared','clone','out_of_window'), first_seen_tv_at NULL, last_seen_tv_at NULL, installed_at NULL,
                 INDEX(state, issued_at), INDEX(tv_ref), INDEX(kid, aseq), INDEX(license_id), INDEX(tag))
act_tv          (tv_ref CHAR(16) PK, device_code VARCHAR(24) UNIQUE [effaçable], current_fp CHAR(64) NULL, edition VARCHAR(10),
                 usage_to NULL, open_all_until NULL, unlock_until NULL, trial_resets INT, last_report_at NULL, last_report_via VARCHAR(12),
                 last_bt_at NULL, api_devices INT, android_ids INT, app_code INT, app_name VARCHAR(64), reco ENUM('OK','GAP','NEVER'),
                 alerts_open INT, INDEX(last_report_at), INDEX(edition, last_report_at), INDEX(app_code))
act_tv_device   (tv_ref, device_id BIGINT [device.id], first_at, last_at, PRIMARY KEY(tv_ref, device_id))
act_event       (id BIGINT PK AUTO_INCREMENT, at DATETIME(6) [moment de l'action], recorded_at DATETIME(6), type VARCHAR(24),
                 fp CHAR(64) NULL, tv_ref CHAR(16) NULL, license_id VARCHAR(64) NULL, kid CHAR(16) NULL,
                 actor_type ENUM('TOOL','SERVER','TV','ADMIN','JOB'), actor VARCHAR(64) [kid, compte admin, « api-token », job],
                 source ENUM('JOURNAL','REGISTRY','ISSUANCE','REPORT','COURIER','CONSOLE_BT','ADMIN','RECONCILE','LICENSE'),
                 before_json VARCHAR(1000) NULL, after_json VARCHAR(1000) NULL, idem_key VARCHAR(96) UNIQUE,
                 prev_hash CHAR(64), hash CHAR(64) UNIQUE,
                 INDEX(fp, id), INDEX(tv_ref, id), INDEX(type, at), INDEX(license_id, id))
act_event_head  (id INT PK CHECK (id = 1), last_id BIGINT, last_hash CHAR(64))         -- même modèle que lic_audit_head
act_alert       (id PK, type VARCHAR(24), severity, fp NULL, tv_ref NULL, kid NULL, opened_at, last_seen_at, hits INT,
                 state ENUM('OPEN','ACK','CLOSED'), decided_by NULL, decided_at NULL, reason VARCHAR(500) NULL,
                 open_key CHAR(64) NULL UNIQUE [SHA-256(type|fp|tv_ref|kid) tant que l'alerte n'est pas CLOSED, NULL ensuite :
                 une seule alerte ouverte par objet, les répétitions incrémentent hits], INDEX(state, severity, opened_at))
act_report      (id PK, device_id, tv_ref, received_at, via ENUM('direct','courier','gateway'), sha CHAR(64), app_code, n_activations,
                 INDEX(tv_ref, received_at), INDEX(received_at))                          -- borné à 90 j
adm_read_audit  (id PK, at, actor VARCHAR(64), role VARCHAR(16), channel ENUM('web','api','phone'), route VARCHAR(80),
                 params VARCHAR(300) [filtres normalisés, sans secret], target VARCHAR(64) NULL [tv_ref ou fp8], rows INT, export BOOL,
                 prev_hash CHAR(64), hash CHAR(64), INDEX(actor, at), INDEX(at))
act_daily       (day DATE, kind, tool, state, n INT, PRIMARY KEY(day, kind, tool, state))
act_tv_monthly  (month CHAR(7), tv_ref, edition, current_fp8, app_code, last_report_at, alerts_open, PRIMARY KEY(month, tv_ref))
act_checkpoint  (day DATE PK, event_last_id, event_head CHAR(64), read_last_id, read_head CHAR(64), counts_json VARCHAR(2000),
                 sig_kid CHAR(16) NULL, signature VARCHAR(100) NULL, created_at)
act_archive     (id PK, table_name, from_id, to_id, from_at, to_at, file VARCHAR(200), sha256 CHAR(64), rows BIGINT, created_at)
```

**Types d'évènements** (liste fermée) : `ISSUED`, `ISSUED_COMPACT`, `DELIVERED`, `ACTIVATED` (premier constat), `SEEN` (seulement si quelque chose change : version, installation, voie), `REPLACED`, `ENDED`, `REVOKED_KEY`, `REVOKED_SEAT`, `TRANSFERRED`, `TRIAL_RESET`, `COMMAND_UNLOCK`, `COMMAND_OPEN_ALL`, `COMMAND_SUPPORT`, `LICENSE_CHANGED` (état, fin, postes, grâce, avec avant/après lus dans `lic_audit`), `SEAT_RELEASED`, `APP_VERSION`, `DEVICE_LINKED`, `JOURNAL_BATCH`, `REGISTRY_IMPORT`, `ALERT_OPENED`, `ALERT_DECIDED`, `RECONCILED`, `POLICY_CHANGED` (seuils et cadences du module, avant/après), `ERASED` (effacement d'une correspondance `tv_ref` ↔ code). Le rapport quotidien d'une TV **sans changement** n'écrit **pas** d'évènement (seulement `act_report` et `act_tv.last_report_at`).

**Changements de licence** : le module des licences reste propriétaire de ses écritures ; w23-01 **lit** `lic_audit` (par `id` croissant, curseur gardé) et en recopie les actions utiles en `LICENSE_CHANGED` — aucune modification du module.

### 5.2 Désignation de la TV dans l'historique

`tv_ref = hex(HMAC-SHA-256(clé act-ref, code d'appareil normalisé)[0:8])` (clé `act-ref.key` dans le dossier des secrets ; absente ⇒ module en 503). L'historique immuable ne porte **que** `tv_ref` ; la correspondance avec le code lisible est dans `act_tv.device_code`, **effaçable** : quand `ClientService.erase` anonymise un client (droit à l'effacement existant), w23-01 efface `device_code` des TV de ses postes (évènement `ERASED`) ; la chaîne d'évènements reste intacte et vérifiable. Recherche par code : le serveur calcule `tv_ref` à partir du code tapé. Cohérent avec `lic_audit` (« aucun code complet dedans »).

### 5.3 Conservation

| Données | Conservation | Pourquoi |
|---|---|---|
| `act_event`, `act_key`, `act_journal_batch`, `act_checkpoint`, `act_alert`, `act_daily`, `act_tv_monthly`, `act_archive` | **jamais purgé** ; lignes de plus de **24 mois** exportées en archive froide (`/var/lib/castbridge/act-archive/<table>-AAAA-MM.jsonl.gz`, empreinte SHA-256 dans `act_archive`, point de contrôle qui couvre la plage) puis retirées de la base **sur ordre du propriétaire seulement** (`POST /admin/activations/archive`, TOTP) | historique complet demandé ; la base reste petite |
| `adm_read_audit` | 24 mois en ligne, puis archive froide (jamais détruit) | traçabilité des lectures |
| `act_report` | **90 j** (comme `lic_sighting`) | le changement utile est déjà un évènement |
| `act_tv` | état courant (tenu à jour) | projection |

### 5.4 Volumes pour 10 000 TV (estimé, aucun chiffre mesuré)

| Table | Hypothèse | Lignes / an | Taille / an (données + index) |
|---|---|---|---|
| `act_key` | ≈ 3 activations / TV / an (essai, production, réémission) | 30 000 | ≈ 15 Mo |
| `act_event` | ≈ 40 évènements / TV / an (émission, remise, constat, version × 12, commandes, alertes, licence) | 400 000 | ≈ 250 Mo |
| `act_journal_batch` | ≈ 1 lot / outil / jour, 3 outils | ≈ 1 100 | ≈ 30 Mo (texte signé) |
| `act_report` | 1 rapport / TV / jour, 90 j glissants | 900 000 en permanence | ≈ 180 Mo stables |
| `adm_read_audit` | ≈ 2 000 lectures / jour | 730 000 | ≈ 180 Mo |
| `act_daily` + `act_tv_monthly` | 160 lignes / jour ; 10 000 / mois | 58 000 + 120 000 | ≈ 20 Mo |
| **Total** | | | **≈ 0,5 Go / an** + 180 Mo stables |

Disque seulement : la mémoire de MySQL (pool 128 Mo, `backend/docker-compose.yml`) n'est pas sollicitée tant que les requêtes passent par les index (curseurs, aucun `OFFSET`, aucun `COUNT(*)` sur `act_event` à l'affichage : le tableau de bord lit `act_daily`).

### 5.5 Preuve d'intégrité

- **Chaîne par ligne** sur `act_event` et `adm_read_audit` (modèle `AuditLog` : tête verrouillée, HMAC si la clé `act-audit.key` existe, `verify()` par lots de 500). Débit attendu < 1 écriture / s : le verrou de tête n'est pas un goulot (estimé).
- **Point de contrôle quotidien** (00:10 Douala) : tête des deux chaînes + compteurs du jour, **signé Ed25519** par une clé dédiée `act-checkpoint.key` (facultative ; absente ⇒ HMAC seulement, D-W23-9). Le propriétaire télécharge `checkpoints.json` (page « Intégrité ») et le garde **hors du serveur** : une réécriture de la base par quelqu'un qui n'a ni la clé ni ce fichier se voit au prochain `verify`.
- **Outil de vérification hors ligne** : `tools/activations/verify_export.py` (export JSONL + points de contrôle + clé publique ⇒ « chaîne intacte jusqu'au … »).

### 5.6 Sauvegarde, restauration, export

- **Export propriétaire** : `GET /api/v1/admin/activations/export` (CSV RFC 4180 UTF-8, ou JSONL), et « Dossier complet » (ZIP : exports + points de contrôle + `README`), permission `ACT_EXPORT`, journalisé.
- **Sauvegarde** : les tables `act_*` et `lic_*` doivent figurer dans la sauvegarde de `castbridge-db` ; **je n'ai pas lu** la procédure de sauvegarde du serveur (ni `deploy-server.sh` à ce sujet) : w23-01 la vérifie et l'écrit dans `docs/ACTIVATION-TRACKING.md` ; à défaut, il propose une tâche `mysqldump` nocturne vers un emplacement hors hôte (acte du propriétaire).
- **Restauration** : après restauration, `verify()` puis comparaison au dernier point de contrôle conservé par le propriétaire ; écart ⇒ les rapports et journaux postérieurs sont **rejoués** (idempotents par `idem_key`, `sha256` de lot, empreinte de rapport).

## 6. Sécurité et vie privée

### 6.1 Données

Identités = **codes d'appareil** et empreintes de facteurs déjà hachées ; aucune donnée personnelle (le module des licences garde le client à part, `lic_client`, jamais lu par W23 hormis le lien d'effacement). Jamais : jeton `cbx1`, clé compacte, défi complet, jeton d'appareil, jeton d'API, TOTP, code de déverrouillage de la console, adresse IP brute (étiquette tronquée comme `Hashing.tag`). Test exigé : recherche de motifs (`cbx1.`, 165 caractères Crockford groupés, `Bearer`) dans les journaux applicatifs et les tables après une suite de tests.

### 6.2 Permissions

Nouvelles permissions, tenues dans le module W23 (`B/activations/ActPermissions.java`, correspondance par `Role` **sans modifier** `B/licenses/Role.java`) :

| Permission | Sensible (TOTP) | OWNER | SUPPORT | READONLY |
|---|---|---|---|---|
| `ACT_READ` (listes, fiches, tableau de bord, alertes) | non | oui | oui | oui |
| `ACT_ALERT_ACK` (accuser réception) | non | oui | oui | non |
| `ACT_ALERT_DECIDE` (classer avec motif) | **oui** | oui | non | non |
| `ACT_EXPORT` (export, dossier complet, points de contrôle) | **oui** | oui | non | non |
| `ACT_JOURNAL_UPLOAD` (téléverser un journal ou un registre) | non (le lot est signé) | oui | oui | non |
| `ACT_ARCHIVE` (archive froide, retrait de la base) | **oui** | oui | non | non |

Le jeton porteur `CASTBRIDGE_ADMIN_TOKEN` vaut OWNER (convention existante) : ses lectures sont **auditées** sous l'acteur `api-token`. Les actions **destructrices** (révoquer une clé ou un poste, suspendre, libérer, réémettre) **ne sont pas dupliquées** : la fiche TV renvoie vers les pages du module des licences, qui exigent déjà TOTP et motif et écrivent `lic_audit`.

### 6.3 Console du téléphone

- **Défaut (D-W23-3 = B)** : l'application propriétaire **reste sans permission réseau** (principe actuel, `OL/ConsoleActivity.kt`, et phase hors ligne de `OWNER-CONSOLE.md` § 8) ; le suivi s'affiche dans le **navigateur** du téléphone (pages web adaptées, compte web + TOTP) ; le journal sort par fichier/partage/QR.
- **Option A (en ligne, sur décision)** : permission `INTERNET` **dans `android/owner` seulement** (jamais dans `ownerlib`, embarqué dans l'APK grand public de CastBridge), configuration de sécurité réseau limitée à **un hôte** (`bridge.sti-cm.com`) avec épinglage de clé ; **session signée** : `GET /api/v1/admin/activations/console/challenge` ⇒ défi 32 hex (120 s, usage unique) ; la console le signe avec **sa** clé d'outil (déjà de confiance pour le registre) ⇒ `POST …/console/session` ⇒ jeton porteur de **15 min**, portée `ACT_READ` + `ACT_JOURNAL_UPLOAD` seulement ; ouverture exigeant la console **déverrouillée** (`UnlockGuard`) ; révoquer la clé du téléphone coupe ses sessions.
- **Actions sensibles depuis le téléphone** : jamais dans l'application ; dans le navigateur, TOTP + motif comme sur ordinateur.

### 6.4 Règle des deux personnes

Non recommandée aujourd'hui (un seul propriétaire) : **TOTP + motif + console déverrouillée** suffisent ; à rouvrir quand des comptes SUPPORT existent (D-W23-10).

### 6.5 Débits

| Route | Limite |
|---|---|
| `POST /api/v1/activations/report` | 1 / 10 min / appareil ; 4 jetons ; 16 Ko ; 3 000 / min global (alerte au-delà) |
| `POST …/journal` | 30 / h / `kid` ; 128 Ko ; 500 entrées |
| lectures admin | 120 / min / acteur ; interrogation longue : 1 par session |
| export | 10 / h / acteur |
| défi de session console | 10 / h / `kid` |

## 7. Interface

### 7.1 Pages web (`/admin/activations/**`, Thymeleaf, gabarit `layout.html`, adaptées à 360 px)

```
┌ Activations ─────────────────────────────────────────── à jour il y a 12 s ┐
│ Émises 1 284 · Activées 1 102 · Non constatées 96 · Terminées 61 · Révoq. 25│
│ Alertes : 3 hautes · 7 moyennes          Outils : bureau ✓ 2 j · tél. ⚠ 16 j │
├─────────────────────────────────────────────────────────────────────────────┤
│ Filtres : [État ▾] [Édition ▾] [Outil ▾] [Période ▾] [ code / étiquette  ]   │
│ Étiq.    Édition  Durée     TV (…4F2Q)  État        Outil   Vue il y a  ⚑   │
│ 9c1e…    PROD     illimitée …4F2Q      ACTIVÉE     tél.    3 h            │
│ 77ab…    ESSAI    30 j      …K9MX      NON CONST.  bureau  —              │
│ 0f3d…    PROD     90 j (3m) …A1B2      ACTIVÉE     ?       5 j        ⚑ non déclarée │
│ [Exporter CSV] [Exporter JSONL]                          [Suivant ▸]         │
└─────────────────────────────────────────────────────────────────────────────┘
```

- **Fiche TV** (`/admin/activations/tv/{code}`) : en-tête (code, version de CastBridge-TV, édition, fin, licence et poste, fraîcheur, voie) ; **chronologie** : émise (outil, `kid`) → remise (Bluetooth, résultat) → activée → commandes (`open_all` 30 j, …) → remise à zéro d'essai → version 0.14.12 → licence suspendue (avant/après) → alertes et décisions ; liens vers les actions du module des licences.
- **Tableau de bord** : compteurs par édition × outil × état, courbe des activations par jour (`act_daily`, `chart.umd.min.js` déjà présent), versions des TV activées, fraîcheur.
- **Alertes** : file triée par gravité, accuser / classer (motif, TOTP).
- **Outils** : par `kid` : type, dernier lot, entrées, trous, chaîne ; téléversement d'un journal ou d'un registre.
- **Intégrité** : `verify()` des deux chaînes, points de contrôle, téléchargement.
- **Lectures** (OWNER) : journal d'audit des lectures, filtrable par acteur.

### 7.2 Application propriétaire

- **Défaut (B)** : onglet « Journal » existant enrichi : liste des entrées (déjà `AuditChain`), **« Exporter le journal signé »** (fichier `Download/castbridge-journal-<kid>-<from>-<to>.cbj`, partage, QR), état « dernier export il y a X · N entrées non exportées » ; bouton **« Ouvrir le suivi »** (intention vers le navigateur, `https://…/admin/activations`), aucune permission ajoutée.
- **Option A** : onglet « Suivi » (dans `android/owner` seulement) : tableau, alertes, recherche par code (saisi, ou lu de la TV par la trame `DEVICE_INFO`), fiche TV en lecture ; bandeau « à jour il y a X » ; cache d'affichage marqué de sa date ; aucune action sensible.

### 7.3 Gel des écrans

Le gel (W15, R3/R5) protège ce qui est **livré aux consommateurs** ; exceptions déjà accordées : écran portefeuille de la TV (D-W22-13), saisie du PIN. **Les pages web d'administration** du serveur et **l'application « CastBridge Propriétaire »** (`castbridge.owner`, jamais publiée) **ne sont pas distribuées aux consommateurs** : **recommandation : le gel ne s'y applique pas** (D-W23-7, à confirmer). Deux réserves : (1) `ownerlib` est **embarqué** dans l'APK grand public de CastBridge (entrée super-administrateur cachée, éteinte sans `-Pcastbridge.superAdmin=true`) : w23-03 ne touche `ownerlib` que par un **crochet minimal** (onglets supplémentaires fournis par l'application hôte) et met tout écran nouveau dans `android/owner` ; (2) **w23-04 touche CastBridge-TV** (`R/`, distribuée) : aucun écran, mais du code TV en zone gelée ⇒ **exception de gel** derrière un drapeau (D-W23-7), APK **verrouillés** (`-PrequireActivation=true`) copiés dans le `Download` de la clé USB.

## 8. Cahiers (vague W23)

**Préfixe** : **W23**, et non « w21b » : (1) le sujet n'est ni la télémétrie technique (W21) ni le grand livre (W22) ; (2) des cahiers `w21-01b`, `w21-02b`, `w21-03b` existent déjà : « w21b » prêterait à confusion ; (3) ce chantier **n'attend pas** le grand livre : il dépend seulement des tables du module des licences (V50-V52, fusionnées), des tables d'appareils (V2) et de la sécurité d'administration existante. Les trois cahiers prévus par le coordinateur (API, pages, téléphone) sont gardés et réorientés ; un **quatrième** (rapport de la TV, petit) est nécessaire, faute de quoi « activé sur quelle TV » ne serait connu que par la remise Bluetooth.

| Ordre | Cahier | Objet | Effort | Modèle | Audit Opus | Dépend |
|---|---|---|---|---|---|---|
| 1 | `sonnet-w23-01-suivi-activations-api-serveur-v65.md` | module `B/activations/**`, V65, journal `cbx1 type=journal` (vérification Java + vecteurs), route de rapport TV, réconciliation et alertes, API en lecture, flux de changements, audit des lectures, instantanés, points de contrôle, export, effacement | L | sonnet (4.6) | **obligatoire** (contrôle d'accès, audit, vérification de signatures) | V50-V52, V2 fusionnées |
| 2 | `sonnet-w23-02-suivi-activations-pages-admin.md` | pages `/admin/activations/**` (liste, fiche TV, tableau de bord, alertes, outils, intégrité, lectures), interrogation longue, adaptées au téléphone | M | sonnet (4.6) | **obligatoire** (contrôle d'accès des routes web, CSRF, TOTP, rien sans audit) | 01 |
| 2 bis | `sonnet-w23-03-suivi-activations-telephone-proprietaire.md` | journal d'émission signé côté outils (`C/owner/ToolJournal.kt`), console du téléphone (export, QR, « Ouvrir le suivi » ; option A en annexe), bureau (`journal-exporter`) | M | sonnet (4.6) | **obligatoire** (signature, chaîne, aucune permission ajoutée en B) | 01 (vecteurs) |
| 2 ter | `sonnet-w23-04-suivi-activations-rapport-tv.md` | rapport d'activation de la TV (direct, puis coursier W21 si fusionné), cadence, coupure par le serveur | S | sonnet (4.6) | échantillon | 01 ; exception de gel (D-W23-7) ; coursier : w21-01b/w21-07 si fusionnés |

**Coût** (prix des index précédents, **non vérifiés** : sonnet 2/10 $/M, opus 4/20 $/M) : sonnet 1 × L (1,6 $) + 2 × M (2,0 $) + 1 × S (0,5 $) = 4,1 $ ; audits Opus 3 × 0,8 $ + 1 échantillon 0,4 $ = 2,8 $ ; reprises 15 % ≈ 1,0 $ ⇒ **≈ 8 $**, ≈ **6,5 agent·jours** ; chemin critique 01 (≈ 2,5 j) → 02 ∥ 03 ∥ 04 (≈ 1,5 j) ⇒ **≈ 4 jours ouvrés** + audits (estimé). Option A du téléphone : ≈ +0,5 j, +0,5 $.

**Actes du propriétaire** : allumer le module des licences ; inscrire les clés publiques du bureau et du téléphone dans `CASTBRIDGE_LICENSES_TRUSTED_KEYS` ; créer `act-ref.key` (et, au choix, `act-audit.key`, `act-checkpoint.key`) dans le dossier des secrets ; livrer `server-1.x` (`deploy-server.sh`) avec `CASTBRIDGE_ACTIVATIONS_ENABLED=1` ; importer une première fois le registre et les journaux des outils ; livrer CastBridge-TV verrouillée avec le rapport.

## 9. Risques

| # | Risque | Prob. | Impact | Parade |
|---|---|---|---|---|
| R-1 | Journaux des outils jamais remontés (oubli) | haute | moyen : fausses alertes « non déclarée » | grâce 72 h, alerte `TOOL_STALE` à 14 j, rappel dans l'application, option A |
| R-2 | TV qui ne rapporte jamais (hors ligne, sans téléphone coursier) | haute (terrain) | état « non constatée » durable | remise Bluetooth comptée comme constat ; coursier W21 ; étiquette honnête « non constatée » |
| R-3 | Rapport falsifié par une TV trafiquée | moyenne | faux état | chaque jeton revérifié (signature, facteurs) ; un rapport ne crée ni ne retire aucun droit ; il ne fait qu'alerter |
| R-4 | Clé d'outil volée : faux journal pour « blanchir » des activations | faible | moyen | journal signé + chaîne : une réécriture casse la chaîne ; révoquer la clé ⇒ lots refusés, activations marquées |
| R-5 | Fuite de données par l'export | moyenne | moyen | `ACT_EXPORT` sous TOTP, export journalisé, aucun secret ni donnée personnelle dans l'export |
| R-6 | Croissance de `adm_read_audit` (interrogation longue) | moyenne | faible | l'interrogation longue n'est **pas** auditée à chaque tour : une ligne par ouverture de session de suivi + une par lecture de fiche ou d'export |
| R-7 | Verrou de tête de chaîne sous charge | faible | faible | < 1 écriture / s ; rapports sans changement n'écrivent pas d'évènement |
| R-8 | `seq` des activations non dense (outil qui utilise `issuedAt`) | moyenne | faible | la détection de trous repose sur le `n` **du journal**, pas sur le `seq` des activations |
| R-9 | Collision de numéro de migration (V65) | moyenne | faible | « plus haut + 1 » à la fusion, dit au rapport |
| R-10 | Exception de gel refusée pour la TV | moyenne | moyen | w23-01/02/03 livrent seuls de la valeur (journal + registre + remise Bluetooth) ; w23-04 attend |
| R-11 | Module des licences éteint en production | certaine aujourd'hui | élevé | prérequis écrit ; le tableau de bord affiche « module des licences éteint : suivi partiel » |
| R-12 | Ajout du réseau à l'application qui détient la clé de signature (option A) | — | élevé si mal fait | défaut B (aucun réseau) ; A seulement sur décision, hôte unique épinglé, lecture seule |

## 10. Décisions du propriétaire (chacune avec recommandation)

| id | Question | Recommandation | Si non |
|---|---|---|---|
| D-W23-1 **(DÉCIDÉ par la correction du 2026-10-04)** | « Jetons activés » = codes d'activation d'essai et de production (et licences, commandes), pas les soldes NDEM/MBOKO | — | — |
| D-W23-2 | Chaque outil hors ligne (bureau, téléphone) tient un **journal d'émission signé** (`cbx1 type=journal`, numérotation dense, chaîne) remonté au serveur, **en plus** du registre existant | **Oui** | le serveur ne voit ni les clés compactes ni les commandes, et aucun trou |
| D-W23-3 | Console du téléphone : **(B) sans réseau** (journal par fichier/partage/QR, suivi dans le navigateur) pendant la phase hors ligne, **(A) en ligne** (hôte unique épinglé, session signée de 15 min, lecture seule) plus tard | **B maintenant**, A quand la phase hors ligne se termine | A tout de suite : surface d'attaque sur l'application qui détient la clé |
| D-W23-4 | Les TV **rapportent** leurs activations (`POST /api/v1/activations/report`) à chaque changement et toutes les 24 h, coupables par le serveur | **Oui** | « activé sur quelle TV » connu seulement par la remise Bluetooth |
| D-W23-5 | Le rapport d'activation est une fonction de **licence** (essentielle), hors de la porte de consentement des statistiques W21 | **Oui** (contenu purement technique ; juridique reporté au 2027-01-01) | sans consentement, aucun suivi des TV concernées |
| D-W23-6 | Rapport permis par la **passerelle Bluetooth** (≤ 4 Ko / jour, jamais pendant une partie Internet ni 30 s après), sinon par le **téléphone coursier** | **Oui** | TV à passerelle seule : suivi par coursier uniquement |
| D-W23-7 | Le gel ne s'applique pas aux pages web d'administration ni à l'application « CastBridge Propriétaire » ; **exception de gel** pour le rapport de la TV (sans écran), derrière un drapeau | **Oui et oui** | w23-04 attend la sortie du gel |
| D-W23-8 | Conservation : évènements, activations, journaux, points de contrôle **jamais purgés**, archive froide après 24 mois sur ordre du propriétaire ; rapports bruts 90 j ; lectures 24 mois puis archive | **Oui** | purge : perte d'historique |
| D-W23-9 | Point de contrôle quotidien **signé** par une clé dédiée (`act-checkpoint.key`), conservé par le propriétaire hors serveur | **Oui** (facultatif à l'installation, HMAC sinon) | une réécriture complète de la base reste indétectable |
| D-W23-10 | Pas de règle des deux personnes : TOTP + motif (+ console déverrouillée) pour toute action sensible | **Oui** tant qu'il n'y a qu'un administrateur | règle des deux personnes : un second compte OWNER à créer |
| D-W23-11 | Alertes **douces** seulement : jamais de révocation ni de libération automatique | **Oui** | une fausse alerte verrouillerait une TV honnête |
| D-W23-12 | Toute lecture d'administration journalisée (y compris le jeton d'API), lignes conservées 24 mois puis archivées | **Oui** | aucune trace de qui a consulté quoi |
| D-W23-13 | Les **points focaux** (agents délégués) remontent le même journal (niveau 2) | **Oui, au niveau 2** | leurs émissions ne sont vues que par les rapports des TV |
| D-W23-14 | Délais : « non déclarée » après 72 h, trou de journal après 7 j, outil muet après 14 j, TV muette après 30 j (production) / 14 j (essai) ; seuils dans une table de politique du module | **Oui** | autres valeurs : seuils modifiables sans livraison |

## 11. Lien avec W22 (jetons NDEM/MBOKO), sans suivi de soldes

Les attributions de jetons **suivent** l'activation et la licence (`DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` § 1.2 : essai lu dans `cbx1`, production lue dans la licence). Deux points de contact seulement : (1) quand w22-02 sera fusionné, sa route `POST /api/v1/wallet/sync` (qui reçoit les `cbx1` de la TV) **pourra** appeler l'interface `ActivationObserver` publiée par w23-01 : une synchronisation de portefeuille vaut alors rapport d'activation (une voie de plus, aucune dépendance dans l'autre sens) ; (2) la fiche TV W23 peut afficher l'état « Licence en attente d'enregistrement » (`LICENSE_PENDING`), le même que celui qui prive la TV de tranches de production. **Aucun solde n'est suivi ici.**

## 12. Ce que ce document ne fait pas

Ne conçoit pas : l'émission en ligne de nouvelles activations, la révocation (existantes dans le module des licences), le suivi des soldes de jetons, les locations, la livraison des listes de révocation aux TV (déjà décrite au § 7 du format), la vente par les points focaux.

## 13. Ce qui a été lu, et ce qui n'a pas pu être vérifié

**Lu** : `DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (entier) ; `SONNET-WAVE22-INDEX.md`, cahiers w22-02, w22-05, w22-06, w22-10, w22-16 ; `docs/OWNER-CONSOLE.md` (entier) ; `docs/ACTIVATION-TOOLS.md` (entier) ; `docs/ACTIVATION-FORMAT.md` § 2-9 ; `DESIGN-W21-…` (passages coursier, cadences, files) ; `SONNET-WAVE15-INDEX.md` (règles R3-R6) ; `DESIGN-W20-AMENDEMENT-…` (passages gel) ; migrations `V2`, `V50`, `V51`, `V52` (début) ; `B/licenses/{AuditLog (début), Role, PublicLicenseController, RevocationService (en-tête), LedgerService (en-tête), LicenseFeature (début), LicenseApiController (signatures)}.java` ; `B/config/SecurityConfig.java`, `AdminToken.java`, `RateLimitFilter.java` (passages) ; `B/play/PlayTicketService.java` (en-tête) ; `B/devices/{DeviceController, DeviceReport}` (signatures) ; liste des routes `/admin/**` ; `application.yml` (bloc licences) ; `C/owner/{PhoneConsole (passages), LicensedIssuer (passages), OwnerVault (AuditChain), DelegatedVerifier (en-tête)}.kt` ; `OL/{ConsoleActivity (passages), SuperAdmin}.kt`, `OW/OwnerActivity.kt` ; `DK/{Desk, Cli}.kt` (passages) ; `docs/HANDOFF.md` (passages admin).

**Non lu ou non vérifié** : si la TV **garde** le texte de la clé compacte et la date d'installation de chaque activation (le rapport en dépend : sinon `installedAt` = premier constat serveur) ; si `seq` des activations est dense ou vaut `issuedAt` selon l'outil ; le journal des points focaux (`sonnet-w4-12`) ; la procédure de **sauvegarde** du serveur ; l'état de fusion de w21-01b / w21-07 (coursier) ; `TvService`/`TvConnect` ligne à ligne (où brancher le rapport) ; `SuperAdminActivity` (si elle hérite de `ConsoleActivity`, le crochet d'onglets doit rester neutre pour elle) ; le contenu exact de `lic_audit` pour les changements de licence ; les prix des modèles (repris des index, non vérifiés) ; aucune taille ni aucun délai n'a été mesuré.
