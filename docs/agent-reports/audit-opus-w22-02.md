STATUT: TERMINÉ (audit, lecture seule ; aucun code modifié dans le dépôt, aucun commit)
AUDIT: w22-02 (grand livre serveur V63, attributions, cbw1) · MODÈLE: Opus · BRANCHE: integration/agents @ 8e8b652d · DATE: 2026-10-04

## Ce qui a tourné, ce qui n'a pas pu tourner

- `cd backend && mvn -o -q test -Dtest='castbridge.server.wallet.**'` : **100 tests, 0 échec, 2 ignorés** (`WalletMySqlContainerTest`, Docker absent). Les chiffres du rapport w22-02 sont exacts.
- **Preuves** : 6 tests écrits dans une COPIE de `backend/` (bloc-notes de la session, hors dépôt), qui affirment le comportement attendu par la conception : **les 6 échouent** (sorties citées plus bas).
- **Mutations** : 6 mutations appliquées ensemble dans la même copie : la suite du portefeuille reste **verte (100/100)**. Elles ont toutes survécu.
- **Pas lancé** : MySQL 8.4 (ni Docker ni serveur MySQL ici), le Kotlin (`WalletCache.kt` a seulement été lu), le serveur de production. Tout ce qui touche à la sémantique MySQL reste **une analyse**, pas une mesure.

## Constats classés

### CRITIQUE

**C1. Un transfert de poste rejoue tout l'historique de la licence sur la nouvelle TV (valeur créée deux fois).**
- Preuve : `JdbcLicenseFacts.java:32-33` cherche la licence par `lic_seat.device_code` ; `GrantService.java:56,63,69` prend l'intervalle et l'ancre depuis `lic_license.start_at` ; les clés sont `grant:<identité>:…` (`GrantSchedule.java:34-36`), donc **propres à chaque identité**. Or `LedgerService.java:427` (`applyTransfer`) réécrit `device_code` sur la MÊME ligne `lic_seat`, sans toucher `start_at`. Même effet quand un poste est libéré puis réattribué (`LicenseService.java:521`) ou qu'un nouveau poste est ouvert sur une licence à plusieurs postes.
- Scénario : licence illimitée démarrée au jour 0 ; la TV A se synchronise au jour 95 (ouverture 5 000 NDEM + 50 MBOKO, puis p0 à p3) ; le poste passe sur la TV B ; B se synchronise au jour 96 et reçoit **encore 9 000 NDEM + 90 MBOKO**. A garde tout. Avec le plafond par défaut (2 transferts par an), une licence de 2 ans recrée ≈ 29 000 NDEM + 290 MBOKO à chaque transfert. Les MBOKO sont misables et transférables.
- Test (échoue) : `p1_seatTransferMustNotReplayTheLicenceHistoryOnTheNewTv` → `reçu NDEM=9000 MBOKO=90`.
  ```java
  long pk = license(a, "lic-t-…", "ACTIVE", T0, null, 14, T0);
  service().sync(a.code(), 1, prod(a), day(95));                       // 9 000 NDEM
  jdbc.update("UPDATE lic_seat SET device_code = ? WHERE license_pk = ?", b.code(), pk);
  service().sync(b.code(), 2, prod(b), day(96));
  assertEquals(0, bal(b, NDEM));                                         // ÉCHOUE : 9000
  ```
- Correctif : (a) les tranches de production deviennent des clés de **licence**, pas d'identité : `grant:lic:<license_id>:<seat_id>:<cur>:p<k>` sur une grille ancrée à `start_at`, avec une ouverture `grant:lic:<license_id>:open` par licence ; (b) au minimum, l'intervalle d'une TV commence à `max(start_at, dernier lic_transfer.at accepté vers ce code, first_seen du poste)`. Test de non-régression : le scénario ci-dessus, avec en plus libération puis réattribution de poste.

### ÉLEVÉ

**H1. Une licence suspendue paie ses périodes de suspension dès que `updated_at` bouge. Les tests figent aussi le rattrapage après reprise, contre la conception.**
- Preuve : `GrantService.java:59` prend `end = updated_at` pour SUSPENDED/REVOKED. Or `LicenseService.update()` (`:426`, délai de grâce, plafond de transferts), `extend()` (`:399`) et `setSeats()` (`:415`) changent `updated_at` **sans exiger l'état ACTIVE**. La conception (§ 1.2) dit : « une tranche est due si, au début de sa période, une licence **ACTIVE** couvrait cet instant ». `GrantServiceTest.suspendedLicenseGivesNothingMoreAndResumesRetroactivelyWithoutLoss` (`:111-122`) affirme l'inverse (p1 à p3 payées après la reprise).
- Scénario : licence suspendue au jour 10 ; l'administrateur change le délai de grâce au jour 95, licence toujours suspendue ; la synchronisation du jour 100 verse p1, p2 et p3 (+3 000 NDEM, +30 MBOKO).
- Test (échoue) : `p2_suspendedLicenceEditedLaterMustNotPayTheSuspendedPeriods` → `expected: <6000> but was: <9000>`.
- Correctif : ne plus jamais dériver une date d'état de `updated_at`. Deux options : (1) lire `lic_audit` (actions `LICENSE_SUSPEND/REVOKE/REACTIVATE`, `at`, cible = licence), qui reconstitue les intervalles ACTIVE ; (2) plus simple et autonome : une table `wallet_license_state(license_id, state, first_seen_at)` où le portefeuille note, **la première fois qu'il la voit**, la fin d'un état ACTIVE (`min(updated_at, maintenant)`) et ne la déplace plus jamais. Une suspension coupe l'intervalle ; une reprise ouvre un NOUVEL intervalle `[reprise, …)`, pas un rattrapage. Le test de w22-02 doit être réécrit selon la règle de la conception.

**H2. Création de valeur par l'administration : le jeton porteur est le seul facteur.**
- Preuve : `SecurityConfig.java:68` (`/api/v1/admin/**` ⇒ ROLE_ADMIN par jeton porteur statique) ; `WalletAdminController.java:50-72` : jusqu'à 10⁹ par appel, nombre d'appels illimité, aucun plafond journalier, aucun débit, acteur fixe `admin:admin-token` (aucune personne nommée). La conception (§ 3.1 et § 3.6) exige le **TOTP** pour `ADJUST` et `/admin/wallet/grant`. Le module des licences l'impose déjà (`actor.require(…, props.requireTotp())`).
- Scénario : le jeton fuit (journal de proxy, poste d'exploitation, script) ⇒ émission de MBOKO sans limite, indiscernable d'un don légitime.
- Correctif proposé : (1) TOTP obligatoire sur `grant` (en-tête `X-Totp`, même vérificateur que `LicenseAccounts`) et acteur nominatif ; (2) plafond par don (ex. 10 000 NDEM / 100 MBOKO) et plafond quotidien global dans `wallet_policy` ; (3) **règle des deux personnes** au-delà du plafond : `POST /grant` crée une demande `PENDING` ; un second administrateur, distinct, l'approuve avec son propre TOTP ; seule l'approbation pose la transaction (clé = identifiant de la demande) ; (4) débit de 10 dons par heure ; (5) alerte dans le journal d'audit chaîné.

**H3. N'importe quel appareil « tv » lit, SANS AUCUNE activation, le solde signé, l'historique et les contributions d'une identité dont il connaît le code.**
- Preuve : `WalletSyncController.java:95-102` (branche « aucune activation acceptée ») : identité connue ⇒ `readOnly`, puis signature d'un `cbw1` (`:110-112`), historique (`:127`) et **appel des contributeurs** (`:116-123`). Cela viole le contrat de `SyncContributor.java:15` (« identité DÉJÀ ouverte et prouvée par une activation »). L'enregistrement d'un appareil est ouvert (`POST /api/v1/devices/register`). Le code d'appareil s'affiche sur la TV et circule au support et dans les demandes d'activation.
- Test (échoue) : `p5_aStrangerWithoutActivationMustNotReadSomeoneElsesWallet` → `reçu 200 avec {"snapshot":"cbw1…","history":[{"amount":100,…}],"contributions":{"test":{"identity":"3Y8X-…"}},…}`.
- Correctif : sans activation acceptée À CE CONTACT, ne servir l'identité qu'à l'appareil API qui la porte (`known.apiDeviceId() == d.id`), sinon 409 `ACTIVATE` sans corps de portefeuille ; ne jamais appeler les contributeurs dans la branche en lecture seule ; calculer `stakesN/stakesM` à faux quand aucune activation n'est acceptée (aujourd'hui, une licence ACTIVE donne `stakesM=true` même si la `cbx1` présentée est révoquée).

**H4. V63 déployée avant V62 bloquera le démarrage du serveur à la livraison qui apportera V62.**
- Preuve : production à V61 ; `V63__wallet.sql` ; README des migrations : « plus haut + 1, jamais de trou volontaire ». Avec Spring Boot 3.5.16 et Flyway 11.7.2 (`validate-on-migrate=true`, `out-of-order=false` par défaut), une V62 arrivée après une V63 appliquée est « résolue non appliquée » (*ignored*). La validation échoue alors avec « Detected resolved migration not applied to database: 62 » et l'API ne démarre pas.
- Voir la séquence de déploiement (§ 6) : renuméroter en **V62** maintenant est la seule option sans réglage de Flyway.

### MOYEN

**M1. Changer `grant.periodDays` crée des tranches rétroactives pour toutes les identités.** `GrantSchedule.java:49-59` : la clé `p<k>` est un indice sur la grille `ancre + periodDays × k` ; la valeur est réglable de 1 à 365 (`V63__wallet.sql`, ligne de semis `grant.periodDays`) par `WalletPolicyService.set`. Passer de 30 à 15 jours réinterprète p4 à p6 comme « non versées ». Test (échoue) : `p4_…` → `expected: <9000> but was: <12000>`. Correctif : figer `min_value = max_value = 30` dans la migration ; si l'on veut un jour régler la période, mettre l'instant de début dans la clé (`p@<epochSeconds>`) ou figer la période par identité (`wallet_identity.period_days`). Même remarque, en moins grave : les montants sont lus au moment de la matérialisation, donc changer `grant.*` modifie aussi les tranches passées pas encore versées.

**M2. Une révocation réelle n'est jamais vue comme REVOKED, et les tranches passées non versées sont perdues.** `LicenseService.revoke()` (`:383-384`) libère tous les postes ; `JdbcLicenseFacts` filtre `s.state='ACTIVE'`, donc la licence disparaît. La branche REVOKED de `GrantService.java:59` est morte dans la réalité : les tests (`GrantServiceTest:93`, `:105`) révoquent sans libérer les postes, ce que le serveur ne fait jamais. La TV affiche alors « Licence en attente d'enregistrement » (`licensePending`) pour une licence révoquée, et p1 (avant la révocation, pas encore versée) est perdue alors que la conception dit « les tranches passées restent acquises ». Test (échoue) : `p3_…` → `expected: <2000> but was: <1000>`. Correctif : lire aussi les postes RELEASED, avec `released_at` comme fin de l'intervalle du poste et `released_reason` / l'état de la licence pour le libellé.

**M3. Une clé d'essai révoquée continue de rapporter.** `EditionReader` ne consulte que le fichier de révocations FACULTATIF (`revocations-file`, vide par défaut). `lic_revocation` (même base, alimentée par `revoke`, `releaseSeat`, `revokeKey` et les transferts) n'est jamais lue. Correctif : `LicenseFacts.revocations()` lit `lic_revocation` (kid, ou licence + poste avec `revoked_at ≥ issuedAt`) ; l'intervalle d'essai se ferme à `revoked_at` ; garder le fichier comme source supplémentaire.

**M4. `cbw1.seq` ne reflète pas les changements des champs signés, et l'instantané peut être incohérent.** Le `seq` est `MAX(wallet_entry.id)` de l'identité (`JdbcLedger.java:210-213`) ; la TV ignore tout instantané dont `seq ≤ seq courant` (`WalletCache.kt:61`). Fin d'essai (ed TRIAL ⇒ NONE), gel (`frozen`), interrupteurs de mise, licence suspendue ou révoquée : **aucune nouvelle écriture, donc même seq**, et la TV garde l'ancien état. Test (échoue) : `p6_…` → `s1={ed=TRIAL, seq=4, stakesN=true}` puis `s2={ed=NONE, seq=4, stakesN=false}`. De plus, les 4 soldes et le seq sont lus par 5 requêtes séparées en validation automatique (`WalletSyncController.java:110-111`) : un règlement concurrent donne un instantané incohérent qui « verrouille » ce seq. Sous MySQL, les identifiants AUTO_INCREMENT de deux transactions concurrentes sur deux comptes de la même identité peuvent en plus être validés dans le désordre. Correctif : un compteur `wallet_identity.snap_seq` incrémenté (sous verrou) à chaque écriture de l'identité ET à chaque changement d'un champ signé (comparer avec un condensé du dernier instantané émis) ; lire soldes et seq dans UNE transaction en lecture seule (REPEATABLE READ = une seule vue). Côté TV (w22-03), accepter aussi `seq == courant && at > courant.at`.

**M5. Liaison au premier venu : une `cbx1` copiée suffit pour « squatter » une identité.** `WalletRepository.openIdentity` (`:45-53`) lie l'identité au PREMIER appareil API qui présente une activation valide. Le jeton d'appareil n'a aucun lien avec les facteurs matériels (`Device.java`). Une `cbx1` copiée (clé USB, envoi par le téléphone, « Ouvrir avec CastBridge ») présentée avant la vraie TV lie le compte à l'attaquant ; quand w22-05 refusera les sorties depuis un autre `deviceId`, ce sera la vraie TV qui sera refusée. La conception renvoie au niveau 2 (w22-14) ; **à fermer avant d'allumer les sorties** (transferts, mises, conversions) : réaffectation par l'administrateur avec TOTP, et preuve de possession par la clé d'installation.

### FAIBLE

- **L1** Forme de SETTLE trop permissive (cœur ET JDBC, `JdbcLedger.java:267-273`, `MemoryLedger` idem) : un participant listé peut avoir un DISPO **négatif** (fonds libres prélevés et versés à un autre participant), donc « aucun gain hors des blocages listés » n'est pas tenu au niveau du grand livre. Seul `Txn.settle` protège. Ajouter `net(DISPO de h) ≥ 0` pour SETTLE et REFUND.
- **L2** `JdbcLedger.post` rejoint une transaction extérieure (`TransactionTemplate` en REQUIRED). Si w22-05 l'appelle sous `@Transactional`, le réessai borné devient faux et `ensureAccounts` (REQUIRES_NEW) prend une deuxième connexion pendant que la première tient des verrous (réserve Hikari de 6). Imposer REQUIRES_NEW, ou lever une erreur si `TransactionSynchronizationManager.isActualTransactionActive()`.
- **L3** Ouverture illimitée = **6 000 NDEM / 60 MBOKO** le premier jour (ouverture + p0, `GrantServiceTest:146-147`). La phrase du propriétaire (« 5000 à l'ouverture et 1000/mois ») peut se lire 5 000 + 1 000 dès le premier mois, ou 5 000 puis 1 000 à M+1 : à confirmer par le propriétaire.
- **L4** `wallet_account.cur/pocket`, `wallet_escrow.cur`, `state`… gardent la collation par défaut (`utf8mb4_0900_ai_ci`) : le CHECK accepte `'ndem'`. Les mettre en `ascii_bin`, comme les clés.
- **L5** `WalletKey.parse` (`:57`) accepte n'importe quel DER PKCS#8 de 48 octets commençant par `30 2e`, sans vérifier l'OID Ed25519 (une clé X25519 passerait). Les droits du fichier de clé ne sont pas vérifiés. La clé n'est jamais journalisée : **vérifié**.
- **L6** Le Kotlin borne les montants à 10¹² (`WalletFormats.kt:37`), le serveur ne borne pas les soldes : au-delà, la TV refuse l'instantané. Théorique.
- **L7** `rollback-V63.sql` n'est pas rejouable (aucun `IF EXISTS`). Le DDL MySQL n'étant pas transactionnel, un échec à mi-chemin laisse un état partiel, et rien n'interdit l'exécution s'il existe des écritures. Ajouter `IF EXISTS` et une garde (procédure qui signale une erreur si `wallet_txn` contient une ligne autre qu'un essai).
- **L8** Chaque attribution verrouille la ligne `SYS:GRANT` de sa monnaie : toutes les attributions du serveur sont sérialisées. Sans risque au niveau 1 ; à mesurer si des milliers de TV se synchronisent au même moment.
- **L9** Les débits ne couvrent que `sync` avec activation acceptée ; la lecture seule et `/history` ne sont bornées que par le limiteur global de l'API.
- **L10** L'ancre figée à la première lecture fait perdre les périodes d'une licence ou d'un essai plus anciens présentés plus tard (choix documenté ; il perd, il ne crée rien).

## Vérifié correct

- Conservation : `Txn` refuse toute transaction non nulle par monnaie (`Math.addExact`) ; chaque `post` = une transaction SQL ; écritures immuables ; `CHECK` sur la base en seconde barrière ; réconciliation I-1, I-3 et I-8 juste (`WalletAdminController.java:79-119`).
- Arrondis : entiers seulement ; N→M exact (multiples de `rate`) ; M→N `fee = ceil(gross × bp / 10 000)` contre le joueur, `gross ≤ 10¹⁵`, `gross × 2 000 ≤ 2·10¹⁸ < 2⁶³` ; aucune poussière.
- Idempotence : même clé + même empreinte = rejeu ; autre empreinte = `IDEM_CONFLICT` ; clés d'administration globales (`adj:admin:<idem>`) : réutilisées sur une autre identité, elles donnent un conflit, pas un doublon. Pas de TOCTOU exploitable : la lecture d'idempotence suit les verrous (la première lecture cohérente sous REPEATABLE READ crée la vue après les `FOR UPDATE`), et `UNIQUE(idem_key)` rattrape toute course (réessai ⇒ rejeu ou conflit).
- Verrous : ordre croissant des `account_id` ; blocages verrouillés après les soldes ; aucune lecture verrouillante de plage hors clé primaire. Sous MySQL, une lecture `FOR UPDATE` sur un `eid` absent pose un verrou d'intervalle (*gap lock*) relâché aussitôt par l'erreur : pas de cycle identifié.
- Attributions : une tranche par période et par monnaie (meilleure édition) ; essai + licence superposés ne doublent pas ; ouverture une fois par identité ; deux synchronisations parallèles = une seule pose ; grille de 30 jours en UTC (`WalletClock`), indépendante d'Africa/Douala (UTC+1 sans heure d'été), des mois courts et des années bissextiles, conforme au § 1.2 (« période de 30 j »).
- Fuseaux : URL JDBC `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` ; les `DATETIME(6)` sont en UTC, cohérents avec le module des licences.
- SQL : toutes les requêtes sont paramétrées (l'historique ne concatène que des fragments constants) ; `GROUP BY … HAVING … ORDER BY MAX()` compatible avec ONLY_FULL_GROUP_BY ; aucun mot réservé MySQL ; index ≤ 3 072 octets ; aucun CHECK sur une colonne à action référentielle.
- `cbw1` : domaine `castbridge-wallet-snapshot-v1` séparé ; charge signée telle qu'envoyée ; lecture stricte Java et Kotlin ; parité octet pour octet avec `tools/wallet/wallet-vectors.json` (`SnapshotSignerTest` vert) ; la TV exige `id` = son identité ; un instantané plus ancien est refusé ; deux `kid` acceptés pendant une rotation.
- Écart GRÂCE ⇒ PROD / SUPER ⇒ UNLIMITED : la décision économique est SIGNÉE (`flags.stakesN/stakesM/frozen`). Les champs hors signature (`grace`, `license`, `boundOther`, `notices`) ne servent qu'à l'affichage, et la TV n'est pas une autorité : elle ne peut pas être trompée sur ce qu'elle peut faire. À terme, signer GRACE/PENDING/SUPER (w22-03).
- Journaux : ni clé, ni jeton, ni activation (seulement des noms de classe et des montants d'administration).
- Flyway : un trou est toléré (V52 ⇒ V60 existe déjà) ; un retour de l'API à une version sans V63 démarre (`ignore-migration-patterns` par défaut `*:future`) ; l'ordre des DROP de `rollback-V63.sql` respecte les clés étrangères, et l'effacement de la ligne `version='63'` permet de rejouer.

## Qualité des tests : 6 mutations qui survivent (suite verte avec les 6 appliquées)

| # | Mutation (copie hors dépôt) | Test à ajouter |
|---|---|---|
| 1 | retirer `if (!"tv".equals(d.app))` (`WalletSyncController.java:76`) | jeton d'un appareil `phone` ⇒ 403 sur `/sync` |
| 2 | retirer `if (d.blocked)` (`:77`) | appareil bloqué par l'administrateur ⇒ 403 |
| 3 | retirer `!row.frozen()` des drapeaux de mise (`:108-109`) | identité gelée (`UPDATE wallet_identity SET frozen=TRUE`) ⇒ `stakesN=stakesM=false` et `frozen=true` signés |
| 4 | `lastEntryId` sans filtre d'identité (`… WHERE a.holder = ? OR 1=1`) | le seq de A ne bouge pas quand B reçoit une écriture |
| 5 | règlement sans `AND state = 'OPEN'` (`JdbcLedger.java:141`) | test direct de la garde `n != 1` (blocage clos entre la lecture et la mise à jour) |
| 6 | lecture seule : `boundOther = false` (`:99`) | appareil B sans activation sur l'identité liée à A ⇒ notice `BOUND_OTHER_TV` (voire 409 après le correctif de H3) |

Autres trous de réalisme : révocation simulée sans libérer les postes (M2) ; transfert de poste absent (C1) ; aucun test de gel ; tout tourne sur H2 (READ COMMITTED et non REPEATABLE READ).

## Liste avant déploiement

**À corriger d'abord (ordre)** : C1 ; H1 (et réécrire le test de suspension selon le § 1.2) ; H2 (TOTP au minimum) ; H3 ; H4 (numérotation) ; M1 (bornes 30..30) ; M2 et M3 ; M4 (compteur de seq, lecture dans une transaction) ; L2 avant w22-05 ; M5 avant toute route de sortie.

**À lancer sur un vrai MySQL 8.4 (image et options de `backend/docker-compose.yml`)**, machine avec Docker :
1. `cd backend && mvn -o test -Dtest=WalletMySqlContainerTest` : oracle sur 2 000 suites et concurrence 16 × 500. Attendu : 0 écart, Σ = 0, **0 réessai** (sinon, mesurer les interblocages avec `SHOW ENGINE INNODB STATUS`).
2. Rejouer TOUTE la suite du portefeuille sur MySQL (ajouter au conteneur `JdbcLedgerTest`, `GrantServiceTest`, `WalletApiTest`, `WalletMigrationTest`), notamment :
   - `sixteenThreadsPostingTheSameOperationCreditItOnce` (une seule pose, sous REPEATABLE READ) ;
   - `parallelSyncsCreditEachTrancheOnce` ;
   - `aRefusalFromTheDatabaseRollsBackEverything` : MySQL renvoie l'erreur 3819 (SQLSTATE HY000) pour un CHECK violé ; Spring la traduit peut-être en `UncategorizedSQLException` et non en `DataIntegrityViolationException`. Le test peut échouer alors que l'annulation, elle, reste correcte ;
   - `historyPagesOf50…` (GROUP BY/HAVING, `LIMIT ?`).
3. Migration : base de production restaurée (copie de V61) ⇒ démarrage ⇒ V63 appliquée ; `SHOW CREATE TABLE` de chaque table (`ascii_bin`, CHECK présents et appliqués : `INSERT … balance=-1, floor_zero=1` refusé) ; `INSERT` d'une clé en casse différente = 2 lignes distinctes ; retour à l'image précédente : démarrage OK (`*:future`) ; `rollback-V63.sql` puis redémarrage : V63 rejouée.
4. Fuseaux : vérifier que `anchor_at` lu égale `anchor_at` écrit, avec une JVM en `TZ=Africa/Douala` puis en `TZ=UTC`.
5. Les 6 tests de preuve de ce rapport, ajoutés au dépôt : ils doivent passer après les correctifs.

**Séquence de déploiement la plus sûre (H4)** :
- **Recommandé** : renommer `V63__wallet.sql` en **`V62__wallet.sql`** avant tout déploiement (elle n'a jamais été appliquée hors des bases de test) ; adapter `WalletMigrationTest` et `rollback-V63.sql` (⇒ `rollback-V62.sql`, `version='62'`) ; W21 et W23 prennent « plus haut + 1 » **au moment de leur fusion**, sans numéro réservé (V65 de W23 laisserait le même trou). Réglages Flyway inchangés, ceux par défaut : `spring.flyway.validate-on-migrate=true`, `out-of-order=false`, `baseline-on-migrate=false`, `clean-disabled=true`, `ignore-migration-patterns=*:future`. Bases de développement qui ont déjà V63 : les recréer (ou `flyway repair` puis suppression de la ligne 63).
- Sinon, livrer W21 (V62) **dans la même livraison ou avant** V63.
- En dernier recours seulement (V63 déjà en production, V62 encore à venir) : sur la SEULE livraison qui apporte V62, `SPRING_FLYWAY_OUT_OF_ORDER=true`, retiré à la livraison suivante ; ne jamais mettre `*:ignored` ni `*:missing` dans `ignore-migration-patterns`.
- Dans tous les cas : sauvegarde `mysqldump` avant la livraison, module éteint (`CASTBRIDGE_WALLET_ENABLED=0`) à la première livraison, migration vérifiée, puis module allumé à la livraison suivante, après les correctifs C1, H1, H2 et H3.

## Résumé (12 lignes)

1. Suite du portefeuille : 100 tests verts, 2 ignorés (MySQL). Rien n'a tourné sur MySQL ni sur le Kotlin.
2. Le grand livre lui-même (verrous, idempotence, conservation, arrondis) est sain : aucune création trouvée par concurrence ou rejeu.
3. Les défauts sont dans la règle des licences, et 6 tests de preuve échouent.
4. CRITIQUE : un transfert de poste rejoue tout l'historique de la licence sur la nouvelle TV (prouvé : +9 000 NDEM et +90 MBOKO en double).
5. ÉLEVÉ : `updated_at` comme date de suspension paie des périodes suspendues (prouvé : +3 000) ; la reprise rattrape, contre le § 1.2.
6. ÉLEVÉ : les dons de l'administrateur ne demandent que le jeton porteur, sans TOTP, plafond ni double validation.
7. ÉLEVÉ : un appareil sans activation lit le solde signé, l'historique et les contributions d'une autre identité (prouvé).
8. ÉLEVÉ : V63 avant V62 bloquera le démarrage de la livraison suivante ; renommer en V62 maintenant.
9. MOYEN : changer `periodDays` crée des tranches rétroactives (prouvé) ; une révocation réelle disparaît et s'affiche « en attente » (prouvé).
10. MOYEN : les essais révoqués continuent (`lic_revocation` non lue) ; seq inchangé ⇒ la TV ignore fin d'essai, gel et coupures (prouvé).
11. Qualité des tests : 6 mutations survivent (type d'appareil, appareil bloqué, gel, seq global, garde de règlement, liaison en lecture seule).
12. Avant déploiement : corriger C1, H1 à H4 et M1, puis passer la liste MySQL 8.4 ci-dessus.
