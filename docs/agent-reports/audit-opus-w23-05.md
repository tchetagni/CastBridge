# Audit Opus w23-05 : enregistrement automatique de la licence et du poste par une activation de production notifiée

Base auditée : `integration/agents` à `8544d173` (fusion w23-05). Le HEAD est passé à `926d898b` pendant l'audit : seuls `play/*` et une ligne `play` de `application.yml` ont changé, rien dans le périmètre. Lecture seule, sauf ce fichier. Aucun accès serveur, aucun déploiement, aucun commit.

Preuves : copie de `backend/` hors dépôt (`<scratchpad>/audit-opus-w23-05/backend`), tests `AuditW2305ProofTest` (8 tests) et `AuditW2305RateRaceMySqlTest` (MySQL 8.4 réel, Testcontainers). **Les 9 tests échouent sur le code audité.** Chaque assertion décrit le comportement attendu, donc chaque échec est un défaut démontré. Les suites w23-05 (`Registrar*`, `EndAtTest`, `CatchUpTest`, `RegistrationMySqlTest`, `DeviceRequestInstallLineTest` : 70 tests) passent dans la copie, MySQL compris. La suite complète n'a pas été lancée : le coordinateur la faisait tourner en même temps.

---

## Constats classés

### HIGH-1 : un jeton copié dans sa fenêtre de 48 h donne licence, identité de portefeuille et 5 000 NDEM + 50 MBOKO à n'importe quel appareil. La vraie TV n'a plus rien.

**Où :** `ReportedActivationRegistrar.java:164-167`, `WalletSyncController.java:104-122`, `GrantService.java:129, 166-186`, `BindProof.java:38-49`.

**Mécanisme :**
* Le contrôle `CLONE` compare le code ANNONCÉ par le client (`p.deviceCode()`) au code calculé sur les facteurs DU JETON. Or l'attaquant lit ce code dans le jeton lui-même : le contrôle ne vérifie aucun matériel.
* La preuve `bind` ne prouve que la possession d'une clé Ed25519 que l'attaquant a fabriquée.
* La route `/api/v1/devices/register` est publique et laisse choisir `app=tv` (commentaire « audit H1 » de `ReportController`).
* L'identité de portefeuille est ouverte au premier appareil qui « prouve » (`GrantService.sync` → `openIdentity`).
* L'ouverture illimitée n'est pas retenue (écart assumé avec D-W23B-6), donc elle est versée tout de suite.

**Preuve :** `f1_aCopiedTokenWithTheAttackersOwnKeyStealsLicenceIdentityAndOpening`. Un attaquant `registerApp("tv")` avec sa propre clé synchronise le jeton de la victime : édition `UNLIMITED`, solde 5 000 NDEM sur l'identité liée à SON appareil. La vraie TV obtient ensuite `boundOther=true` (`BOUND_OTHER_TV`), et sa ligne finit en `BIND_MISMATCH`, non persistée.
```
expected: <0> but was: <5000>   (« vraie TV : boundOther=true »)
```

**Fenêtre d'exposition :**
* Le jeton est accepté de son émission à `expiresAt + 5 min`, mesuré à l'heure du serveur au moment de la synchronisation. Avec les outils conformes, cela fait au plus 48 h 05.
* Le serveur ne vérifie pas `expiresAt − issuedAt ≤ 48 h` (voir LOW-1). Il accepte aussi `issuedAt` jusqu'à 24 h dans le futur (`:173`).
* Au-delà de la fenêtre, l'attaquant peut encore lier l'identité (limite w22-14, déjà connue). La première acceptation du propriétaire paiera alors l'attaquant, et la liste de décision ne montre ni la clé d'installation ni l'appareil API (LOW-3).
* Réponse à la question posée : **non, la liaison du jeton aux facteurs matériels n'arrête rien**. Les facteurs ne viennent que du jeton, et aucun matériel n'est attesté. Avant w23-05, ce même vol ne rapportait rien tant que le propriétaire n'avait pas saisi la licence (`LICENSE_PENDING`). Aujourd'hui, il paie immédiatement.
* La conception § 10 dit « la vraie TV ⇒ `BIND_MISMATCH` ⇒ rien payé aux deux ». **C'est faux dans le code** : le premier est payé.

**Correctif proposé :** pour une licence `report:` dont l'heure d'installation n'est pas prouvée, retenir l'ouverture ET les tranches tant que l'émission n'est pas déclarée (D-W23B-6 tel que conçu, étendu aux périodes). Ou bien ne pas payer une identité ouverte dans la même synchronisation par le registrar. Le vrai remède reste D-W23B-4 (clé d'installation liée à l'activation, w22-14).

### HIGH-2 : une clé limitée à `REACTIVATE` change la durée de la licence : une clé de 90 jours devient ILLIMITÉE (+5 000 / +50, puis 1 000 / 10 par mois)

**Où :** `ReportedActivationRegistrar.java:245-255` et `:257-267`. Le rattachement appelle `alignEnd` pour toute clé qui passe `:166`, `REACTIVATE` comprise. Ensuite, `alignEnd:404-410` met `end_at = NULL` dès qu'un jeton sans droit `usage` est vu, et `:420-423` repousse la fin au plus grand `to`.

**Preuve :** `f3_aReactivateOnlyKeyTurnsANinetyDayLicenceIntoUnlimited`. Le portefeuille passe à `end_at=null ed=UNLIMITED NDEM 1000 -> 6000`.

Une clé de réactivation (support, point focal) ne devrait que rattacher le poste existant (format § 2, `LedgerService:202,362`). Ici, elle prolonge la licence ou la rend illimitée pour toute licence `report:` ou `import:`.

**Correctif proposé :** `alignEnd` seulement si la clé a `ISSUE_PRODUCTION`, et le comptage `unlimited` de `:404` limité aux lignes signées par une clé `ISSUE_PRODUCTION`.

### HIGH-3 (cas réel) : deux licences pour la même TV. Celle qui arrive tard mais commence plus tôt fait repayer les périodes déjà versées

**Où :** `GrantService.java:176` et `:206-209`. `coveredByEarlierLicence` ne regarde que les licences d'INDEX inférieur. L'ordre vient de `JdbcLicenseFacts.java:49` (`ORDER BY l.start_at`), et les clés d'idempotence sont par licence (`grant:lic:<id>:…`).

**Scénario :**
1. Licence manuelle L1, début T0, déjà payée.
2. La TV présente son activation, illimitée, émise à T0 − 2 h sous un autre identifiant. Elle est hors fenêtre, donc `INSTALL_TIME_UNKNOWN`.
3. Le propriétaire accepte : L2 est créée avec un début plus ancien et passe à l'index 0.
4. Ses périodes p1 et p2 sont versées une seconde fois. La limite de rattrapage ne les arrête pas, puisque l'acceptation compte comme déclaration.

**Preuve :** `f4_…PaysThePastPeriodsTwice`.
```
F4 sync 1 : PENDING_DECISION / INSTALL_TIME_UNKNOWN  NDEM=7000 MBOKO=70
F4 décision=REGISTERED … NDEM 7000 -> 9000, MBOKO 70 -> 90
```

**Ce qui déclenche le défaut :**
* L'acceptation d'une ligne en attente, c'est-à-dire exactement ce que le propriétaire sera tenté de faire pour BRX4-W1C5-4WKB-6DGQ.
* Une émission déclarée par le registre.
* L'import d'une licence ancienne après une plus récente : les licences `import:` n'ont aucune limite de rattrapage.

`RegistrarOrderTest` vérifie l'indépendance à l'ordre sur `lic_*` seulement, jamais les soldes.

**Correctif proposé :** une période déjà payée à cette identité par N'IMPORTE QUELLE autre licence couvrante ne doit pas être repayée. Il faut tester « payé » sur le grand livre, pas sur l'index.

### MEDIUM-1 : le refus du propriétaire n'est pas définitif

**Où :** `apply:207-218`. Seules les lignes `REGISTERED`/`ATTACHED` court-circuitent. Une ligne `REFUSED/OWNER_REFUSED` est rejugée à chaque présentation et réécrite par `persist:445`.

**Preuves :**
* `f2_…` : TV refusée pour `OVER_QUOTA`, puis le propriétaire porte la licence à 2 postes pour une autre raison. La présentation suivante donne `REGISTERED`.
* `f2b_…` : une ligne hors fenêtre refusée repasse `PENDING_DECISION` à la synchronisation suivante (15 min). Le refus disparaît, la ligne revient dans la liste, et une déclaration ultérieure l'enregistrera.

Effet sur le cas réel : refuser la ligne de BRX4 ne fait pas taire l'avis.

**Correctif proposé :** une ligne `OWNER_REFUSED` est définitive (retour immédiat, aucun `UPDATE`).

### MEDIUM-2 : un renouvellement sous le même identifiant après une interruption paie les périodes de l'interruption

**Où :** `alignEnd:420-423` ne lit que `to`. `LicenseSpanBook` borne les intervalles par la fin courante, et aucun événement ne coupe l'intervalle.

**Preuve :** `f8_…`. Clé de 30 jours, puis renouvellement à J+100 avec le droit [J+100, J+190] : `NDEM 1000 -> 4000`. Les tranches de J+30, J+60 et J+90 sont payées alors qu'aucune clé ne valait.

**Correctif proposé :** si `usageFrom` dépasse la fin courante plus la grâce, ouvrir un nouvel intervalle à `usageFrom`, ou traiter le jeton comme une nouvelle licence.

### MEDIUM-3 : le plafond de 10 créations par jour et par clé est dépassé sous concurrence (MySQL 8.4)

**Où :** `rateCapped:329-335`. Il compte en `READ COMMITTED`, sans verrou par clé.

**Preuve :** `AuditW2305RateRaceMySqlTest`. 16 licences différentes de la même clé, présentées en parallèle (pool de 6) :
```
licences créées par la clé en parallèle : 12  (plafond 10)
```
C'est justement le cas que le plafond doit borner (clé volée, D-W23B-7). Le dépassement croît avec le parallélisme.

**Correctif proposé :** un verrou par `kid` (ligne compteur `FOR UPDATE` ou `GET_LOCK`).

### MEDIUM-4 : une TV vue pendant la suspension d'une licence n'obtient jamais son poste après la reprise

**Où :** `evaluate:269-272` renvoie `ATTACHED` sans poste. La ligne devient `ATTACHED`, puis `apply:207-209` court-circuite pour toujours.

**Preuve :** `f5_…`. Après `resume`, on obtient `ATTACHED, LICENSE_SUSPENDED, postes=0`.

**Correctif proposé :** `PENDING_DECISION(LICENSE_SUSPENDED)`, rejugé à chaque présentation.

### LOW

| # | Constat | Où / preuve |
|---|---|---|
| LOW-1 | La fenêtre d'installation n'est pas bornée par le serveur : `expiresAt − issuedAt ≤ 48 h` n'est pas vérifié (une clé d'outil qui signe 30 jours ouvre 30 jours), et `issuedAt` peut être jusqu'à +24 h dans le futur. Règle du propriétaire : 48 h depuis l'émission. | `check:173`, `evaluate:231` (lecture) |
| LOW-2 | Le plafond mensuel ne se lève pas par la décision du propriétaire : `decide(accept)` répond de nouveau `PENDING_DECISION/KEY_RATE`. La ligne reste bloquée jusqu'au glissement des 30 jours, et le propriétaire ne peut rien faire. | `rateCapped:332`. Preuve `f6_…` |
| LOW-3 | La liste de décision ne montre ni la clé d'installation, ni l'appareil API, ni si l'identité est déjà liée à un autre appareil. Le propriétaire ne peut donc pas distinguer la vraie TV d'un voleur (HIGH-1) avant d'accepter. | `pending:477-481` |
| LOW-4 | L'avis `REGISTRATION_REVIEW` (« jetons de production à venir ») est envoyé à chaque synchronisation, y compris pour des refus définitifs (`UNKNOWN_KEY`, `CLONE`, `EXPIRED`, `LICENSE_REVOKED`) et pour une TV déjà payée par une autre licence (cas réel). Le texte est faux et permanent. | `WalletSyncController.registrationNotices:188-201` |
| LOW-5 | Décision sur une empreinte inconnue : `queryForMap` lève `EmptyResultDataAccessException`, ce qui donne une 500 et une pile complète dans le journal (attendu : 404). | `decide:491` (lecture) |
| LOW-6 | La décision par API se fait avec le jeton d'administration statique (`Actor.token()` = « admin-token », OWNER, `strong=true`) : ni compte nommé ni TOTP, contrairement à ce que dit le rapport sonnet-w23-05. Même garde que le reste de `/api/v1/admin/licenses/**`, donc pas d'escalade, mais le texte du rapport et du guide est inexact. | `RegistrationDecisionController:48`, `Actor:15` |
| LOW-7 | Retour arrière : pas de `U64` séparé (seul `U50-U52` supprime `lic_registration`). Revenir au code 1.2.0 ou 1.2.1 garde les licences `report:` et supprime toute limite de rattrapage : les tranches RETENUES sont alors versées par l'ancien code. | `db/rollback/U50-U52`, `GrantService` (ancien) |
| LOW-8 | `LATE_NOTICE` est à 400 j mais `CatchUpPolicy.MAXIMUM` à 366 j : une clé illimitée acceptée entre 366 et 400 j après l'émission n'aura jamais son ouverture de 5 000 + 50. | `CatchUpPolicy:28`, `evaluate:232` |
| LOW-9 | `alert()` déduplique par `details LIKE '%fp=…%'` (parcours de `lic_audit`) à chaque synchronisation d'une TV en `BIND_MISMATCH` : coût qui croît avec le journal. | `alert:458` |
| LOW-10 | Course possible avec l'import du registre (une seule transaction pour tout le fichier, tête d'audit tenue puis `lic_license … FOR UPDATE`) : interblocage détecté par InnoDB. Le registrar réessaie (`PessimisticLockingFailureException`) ; l'import, s'il est choisi comme victime, échoue en entier. | `LedgerService.importLedger:109`, `amendReported:128` (lecture) |
| INFO | `ActivationsMySqlTest.twoContradictoryJournalsAtTheSameTimeNeverBothApplyOnMySql` a échoué une fois sur 503 sous charge, puis a passé seul, avec et sans mutations : test probablement instable sous contention. | |

---

## Réponses aux questions de l'audit

**(1) Création de valeur.** Créations non voulues démontrées : HIGH-1 (jeton copié), HIGH-2 (`REACTIVATE`), HIGH-3 (deux licences), MEDIUM-2 (interruption payée), MEDIUM-3 (plafond dépassé en parallèle).
* Clés inconnues, révoquées ou mal signées : refusées sans trace.
* Droit `super` et essai : `IGNORED`.
* Identifiant réservé ou `trial` : `MALFORMED`.
* `usage` : format validé (`rightLineOk`), `from>0`, `to>from`, durée ≤ 3 660 j. Aucun dépassement de capacité ni valeur négative. Un `from` antidaté reste borné par le rattrapage pour une licence `report:`, mais pas pour une licence `import:` réalignée (`alignEnd:417-418` déplace `start_at`), ce qui exige un outil de confiance.
* Ouverture illimitée versée deux fois par deux licences : non, `openedUnlimited` est par identité. Par deux postes : non, C1 (une identité par licence).
* Plafond comme moyen de déni de service : il faut 10 jetons fuités et signés par jour, et chacun crée d'abord de la valeur (HIGH-1). Contournement : possible par la concurrence seulement (MEDIUM-3).

**(2) Cohérence avec les correctifs antérieurs.**
* La réclamation C1 par (licence, période) est intacte.
* Les intervalles figés (H1) sont contournés par `alignEnd`, qui change `end_at` et redessine donc les intervalles a posteriori (MEDIUM-2, HIGH-2).
* La licence reste non transférable (`TRANSFER_CAP`, `OVER_QUOTA`), mais le PORTEFEUILLE suit le premier qui prouve (HIGH-1).
* Une licence révoquée reste refusée. Une licence suspendue ne change jamais d'état ; le registrar n'écrit jamais `lic_license.state`.
* Un poste libéré donne `SEAT_RELEASED`, et l'acceptation du propriétaire n'y change rien : la ligne reste en attente, sans issue.
* Le refus du propriétaire est rouvert (MEDIUM-1).
* Licence manuelle et licence du registrar pour la même TV : voir (6) et HIGH-3.

**(3) Concurrence MySQL 8.4.**
* Ordre des verrous identique partout : `lic_registration(fp)` → `lic_license` → `lic_seat` → `lic_audit_head`.
* Le correctif de l'épuisement du pool est complet sur ce chemin. `ensureAnonymousClient` est appelé avant la transaction ; l'appel imbriqué de `create:302` joint la transaction (`REQUIRED`). `AuditLog.record` est `MANDATORY`.
* Le seul `REQUIRES_NEW` du code (`JdbcLedger.accountTx`) est appelé hors transaction (`ensureAccounts` avant `tx.execute`). `WalletSyncController` et `GrantService` ne sont pas transactionnels.
* Rejeux : clé `fp`, émission par (licence, nonce), réessais sur doublon ou interblocage : idempotent (tests 16 fils existants, rejoués et verts).
* Seul trou de concurrence trouvé : le plafond (MEDIUM-3).

**(4) Accès et journaux.**
* `/api/v1/admin/licenses/registrations/**` exige `ROLE_ADMIN` (jeton porteur) et le module allumé (sinon 404).
* Réponses : empreinte, kid et code masqué ; jamais le jeton.
* Journaux : nom de l'exception, empreinte sur 8 hexadécimaux.
* Audit : code masqué `ABCD-****`.
* Défauts : LOW-5, LOW-6.

**(5) Migration V64.**
* Table nouvelle uniquement, types cohérents avec `lic_seat` (5 facteurs au plus, environ 235 caractères dans `VARCHAR(400)`), `CHECK` actif en 8.4, `BOOLEAN` = `TINYINT(1)`, jeu de caractères par défaut de la base (comme V50-V63).
* Ordre : sur une base en V62, cette version applique V63 puis V64 ; après 1.2.1, seulement V64. Le retour arrière du code tolère V64 (migrations futures ignorées par défaut).
* Risque : un ancien `V63__wallet.sql` (avant renumérotation, commit `c3f434df`) s'il a été appliqué sur une base de préproduction. La validation Flyway empêcherait alors le démarrage. À vérifier (liste ci-dessous).
* Aucune perte de données. Retour arrière : LOW-7.

**(6) Cas réel (BRX4-W1C5-4WKB-6DGQ, licence manuelle lic-3tb6w-hujb5, ouverture déjà versée).** Ce que fait le déploiement, avec pour hypothèse que l'activation de la TV désigne un AUTRE identifiant et a été émise il y a plus de 48 h :
1. V63 (si absente) et V64 s'appliquent. Aucune ligne existante n'est modifiée.
2. À la synchronisation suivante, le registrar juge le jeton :
   * clé absente de `CASTBRIDGE_LICENSES_TRUSTED_KEYS` : `REFUSED/UNKNOWN_KEY`, rien d'écrit ;
   * sinon `PENDING_DECISION/INSTALL_TIME_UNKNOWN` : une ligne `lic_registration`, un audit `REGISTRATION_PENDING` (une fois).
3. Dans les deux cas, l'avis `REGISTRATION_REVIEW` s'affiche sur la TV à CHAQUE synchronisation (LOW-4), et un refus du propriétaire ne l'arrête pas (MEDIUM-1).
4. Identité, liaison et clé d'installation : inchangées. Édition : `UNLIMITED` (licence manuelle). Soldes : **inchangés**, pas de double crédit, pas de bascule d'état.
5. `alignEnd` ne touche pas la licence manuelle, À CONDITION que son `created_by` ne commence ni par `import:` ni par `report:`.
6. Si le propriétaire ACCEPTE la ligne : L2 est créée (1 poste, même matériel). Si son début (émission du jeton) précède celui de lic-3tb6w-hujb5, toutes les tranches mensuelles déjà payées par la licence manuelle sont repayées (HIGH-3). Aujourd'hui, 0 période est probablement échue ; ce sera 1 000 + 10 par mois écoulé avant l'acceptation. L'ouverture n'est pas repayée.
7. Si l'activation désigne le MÊME identifiant que la licence manuelle : `ATTACHED`, une émission `REPORT` est ajoutée, rien d'autre.
8. Si elle a été émise il y a moins de 48 h : L2 est créée immédiatement, avec les mêmes effets qu'au point 6, mais sans période échue.

**(7) Qualité des tests : 6 mutations qui SURVIVENT à `licenses.**`, `wallet.**` et `activations.**` (503 tests, appliquées ensemble ; un seul échec, l'instabilité INFO ci-dessus, vert quand on le relance).**

| # | Mutation | Test manquant |
|---|---|---|
| MB | supprimer `WRONG_SUBJECT` (`check:162`) | jeton de production `subject=phone` |
| MC | supprimer « `GRACE` ⇒ attente » (`evaluate:274`) : un nouveau poste est créé sur une licence en grâce | licence en grâce + nouveau matériel |
| MD | appliquer aussi le plafond du jour à une émission déclarée (`rateCapped:333`) | émission déclarée au-delà de 10 par jour |
| ME | `notification()` sans le filtre `created_by LIKE 'report:%'` (`JdbcLicenseFacts:67`) : limite de rattrapage appliquée aux licences du propriétaire et du registre | licence importée ancienne + jeton rattaché |
| MF | révocation d'un poste qui refuse tout jeton, même émis APRÈS la révocation (`evaluate:229`) | jeton réémis après la révocation |
| MG | `registerAll` sans isolation des erreurs (`:110`) : une erreur sur un jeton casse toute la synchronisation | jeton qui fait échouer la base (date hors `DATETIME`) |

Également non couvert : ordre de paiement entre licences (HIGH-3), `alignEnd` par `REACTIVATE` (HIGH-2), refus définitif (MEDIUM-1), plafond en parallèle sur MySQL (MEDIUM-3), suspension puis reprise (MEDIUM-4). La mutation M18 du rapport (« voulu ») est acceptable.

---

## Vérifié correct
* Décodage canonique et signature ; `UNKNOWN_KEY` / `REVOKED_KEY` / `BAD_SIGNATURE` sans écriture ; essai et `super` ignorés ; identifiants réservés refusés ; 4 jetons au plus avant le registrar (`EditionReader.MAX_TOKENS`) ; `app=phone` refusé (403) sur `wallet/sync` et `activations/report`.
* Création : `PAID`, 1 poste, `transfer_cap 0`, `start_at` = `from` ou `issuedAt`, `end_at` = `to` ou NULL ; `createAuto` et `alignToKey` dans la transaction d'émission (R-1 corrigé, `EndAtTest`).
* 16 fils sur une licence à un poste : 1 poste ; 16 fils sur le même jeton : 1 licence, 1 émission (relancé, vert, MySQL 8.4).
* Pool : aucune transaction imbriquée sur le chemin registrar ou portefeuille ; ordre des verrous cohérent.
* Ouverture illimitée une fois par identité ; C1 intact ; aucune écriture de `lic_license.state` ; licence révoquée refusée.
* Aucun jeton dans les journaux, réponses ou tables (empreinte seulement) ; code masqué dans l'audit et la liste.
* V64 : additive, compatible avec un retour arrière du code ; `lic_issuance.source` sans `CHECK` (`REPORT` accepté).

## Liste à suivre avant le déploiement
1. **Ne pas déployer en l'état si le portefeuille sert à de vraies valeurs** : corriger au minimum HIGH-1 (retenue tant que non déclarée), HIGH-2, HIGH-3 et MEDIUM-1 ; ajouter les 6 tests de mutation et les tests de preuve ci-dessus.
2. Sauvegarde (`backend/backup.sh`) ; puis `SELECT version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;` en production ET en préproduction : V62 = `wallet`, et pas de V63 `wallet` ancien.
3. `SELECT license_id, created_by, kind, state, start_at, end_at, seats_allowed FROM lic_license;` : vérifier que `lic-3tb6w-hujb5` n'a ni `import:` ni `report:`, et noter son `start_at`.
4. `SELECT license_pk, seat_id, device_code, state FROM lic_seat WHERE device_code='BRX4-W1C5-4WKB-6DGQ';` puis relever les soldes, les clés `grant:lic:%` et la ligne `wallet_identity` de la TV (instantané « avant »).
5. Lire sur la console (journal) l'identifiant de licence et la date d'émission de l'activation de la TV, SANS exporter le jeton, pour prévoir l'issue : en attente, attachée ou créée.
6. `CASTBRIDGE_LICENSES_TRUSTED_KEYS` : clé de la console avec `ISSUE_PRODUCTION`. Retirer toute clé `REACTIVATE` seule tant que HIGH-2 n'est pas corrigé. `castbridge.wallet.require-bind-proof` = `true`.
7. Préproduction d'abord, configuration identique ; une TV de test : rejeu ×3 sans double versement.
8. Après le déploiement : deux synchronisations de BRX4, puis comparer les soldes à l'instantané (doivent être identiques) ; `GET /api/v1/admin/licenses/registrations` ; `SELECT action, reason, COUNT(*) FROM lic_audit WHERE action LIKE 'REGISTRATION%' GROUP BY 1,2;`.
9. **N'accepter AUCUNE ligne en attente de BRX4** tant que HIGH-3 n'est pas corrigé.
10. Plan de retour arrière écrit : revenir au code est sûr pour le schéma, mais lister d'abord les licences `created_by LIKE 'report:%'` (l'ancien code les paierait sans limite de rattrapage, LOW-7).

## Ce que je n'ai pas pu faire
* Pas de suite complète (le coordinateur la faisait tourner), pas de serveur réel, pas de vraie TV, pas de lecture de la base de production. Le cas réel (point 6) repose donc sur des hypothèses : identifiant de licence du jeton, date d'émission, `created_by` de la licence manuelle, clé de confiance configurée.
* Les mutations MA, MH, MI et MJ ont été tuées (1 échec chacune). Je n'ai pas vérifié quel test les a tuées.
* Le côté Kotlin (TV, console) n'a pas été lu. LOW-10 (interblocage avec l'import) a été raisonné, pas reproduit.
* Incident de manipulation : ma première copie de travail a été faite par `rsync` dans `<scratchpad>/backend`, un dossier partagé qui existait déjà (il contenait un ancien `V63__wallet.sql`). Les fichiers courants du dépôt ont écrasé ceux de cette copie. Mon fichier de test en a été retiré, mais le reste de la copie partagée a été modifié. Les preuves ont ensuite tourné dans `<scratchpad>/audit-opus-w23-05/backend`, propre.
