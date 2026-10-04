# Audit Opus de w23-01 : suivi des activations (module serveur `activations`, migration V65)

> Audit en lecture seule, 2026-10-04. Code audité : branche `worktree-agent-a31c5a85f56ccbeab`, commit `6972c035` (non fusionné), lu dans `.claude/worktrees/agent-a31c5a85f56ccbeab`. Aucune modification du code, aucun commit, aucun accès serveur, aucun réseau. Préfixe `A/` = `backend/src/main/java/castbridge/server/activations/`.
> Décisions du propriétaire prises comme référence : code valable 48 h après l'émission puis `EXPIRED_UNUSED` s'il n'a jamais été vu ; activation installée = sa propre durée ; téléphone propriétaire hors ligne ; licences non transférables ; licence ≠ clé d'activation ; jamais de code complet stocké ni journalisé ; alertes douces seulement ; numéros de migration non réservés.

## Résumé en 12 lignes

1. **CRITIQUE** : sur MySQL (pilote Connector/J 9.7.0, URL de production sans `treatMysqlDatetimeAsTimestamp`), une colonne `DATETIME` lue par `queryForMap`/`queryForList` arrive en `java.time.LocalDateTime`. Le module la convertit de force en `java.sql.Timestamp` à 22 endroits. Résultat : chaque rapport de TV portant un jeton tombe en 500, les lectures par curseur restent bloquées, les listes répondent 500. H2 renvoie `Timestamp`, c'est pourquoi les 89 tests sont verts.
2. **ÉLEVÉ** : n'importe quelle installation enregistrée (inscription publique, même `app=phone`) peut envoyer un rapport pour **n'importe quel** code d'appareil, sans aucun jeton vérifié. Elle réécrit alors l'état de la TV visée, fabrique des commandes et des alertes, et marque comme clone une activation authentique. Prouvé par un test.
3. **ÉLEVÉ** : l'audit des lectures laisse passer en cas d'échec. Il est écrit après la réponse. Si son écriture échoue, la lecture est quand même servie (prouvé). Un export interrompu par le client n'est pas audité du tout. Les services publiés pour w23-02 n'auditent rien eux-mêmes.
4. **ÉLEVÉ** : la preuve d'intégrité ne voit pas une troncature. Retirer la queue de l'historique et reculer la tête, ou insérer une fausse ancre d'archive, laisse `verify()` vert (prouvé). L'outil hors ligne `verify_export.py` classe un export raccourci en « NON VÉRIFIÉ », code 2, au lieu de signaler une ANOMALIE (prouvé).
5. **MOYEN** (8 constats) : numéro V65 avec V62 déjà présent sur `integration/agents` ; réconciliation nocturne en une seule transaction qui tient le verrou de tête (pool Hikari de 6) ; l'archive froide avec retrait fait revenir un code effacé (prouvé) ; une clé `act-ref.key` changée provoque un 500 pour toute TV connue (prouvé) ; le débit global est consommé avant le débit par appareil (prouvé) ; deux journaux concurrents peuvent fourcher sans quarantaine ; les lectures par curseur sur les tables de licences peuvent sauter des lignes ; les émissions connues seulement par le registre n'ont pas d'inventaire, donc pas d'`EXPIRED_UNUSED`.
6. **FAIBLE** (13 constats), dont : le code d'appareil complet figure dans le journal d'accès de l'application (constaté dans la sortie de la suite) ; une clé compacte tapée dans un filtre est recopiée en clair dans l'audit des lectures.
7. **Vérifié correct** : contrôle d'accès de l'API d'administration (jeton porteur, `ROLE_ADMIN`, matrice des permissions) ; aucune écriture dans `lic_*` ; aucun jeton `cbx1` stocké ni journalisé ; séparation de domaine des signatures ; logique de chaîne du journal en séquentiel ; règles des 48 h, `EXPIRED_UNUSED` et durée propre ; date de remise Bluetooth quel que soit l'ordre d'arrivée ; alertes douces.
8. **Tests** : 89 exécutés, 0 échec, 2 ignorés (MySQL) dans ce worktree. Les 5 mutations supplémentaires essayées **survivent toutes**.
9. **Non exécuté** : MySQL 8.4 et Docker (absents) ; vrai démarrage avec les tâches planifiées ; consommation des vecteurs par Kotlin. Le constat CRITIQUE vient de la lecture du pilote et de Spring, pas d'une exécution sur MySQL.
10. **Avant toute mise en route** : corriger C1, H1, H2 et H3 ; faire tourner `ActivationsMySqlTest` sur MySQL 8.4 ; renuméroter la migration en « plus haut + 1 » ; écrire un script de retour arrière.
11. Le module reste **éteint par défaut**. Tant que `CASTBRIDGE_ACTIVATIONS_ENABLED` n'est pas positionné, la fusion seule ne change rien en production, à part la création des 19 tables par Flyway.
12. Recommandation : **ne pas allumer** avant que C1 et H1 soient corrigés et que le test MySQL passe. La fusion du code éteint est acceptable une fois la migration renumérotée (M1).

## Ce qui a tourné, ce qui n'a pas tourné

| Action | Résultat |
|---|---|
| `cd <worktree>/backend && mvn -o -q test -Dtest='castbridge.server.activations.**'` | 89 tests, 0 échec, 0 erreur, 2 ignorés (`ActivationsMySqlTest` sans Docker) |
| Tests de preuve `AuditProofTest` (7) et `NightlyProofTest` (1), dans une **copie hors dépôt** (`<scratchpad>/proof/backend`) | 7/7 passent : **chaque test passe parce que le défaut est présent** |
| 5 mutations supplémentaires, dans la copie hors dépôt | 5/5 survivent (suite toujours verte) ; base : 89 tests verts |
| Preuve Python de `verify_export.py` (export tronqué) | code 2 « NON VÉRIFIÉ » au lieu de 1 « ANOMALIE » |
| Lecture du pilote `mysql-connector-j-9.7.0.jar` (`javap`, `PropertyDefinitions`) | `treatMysqlDatetimeAsTimestamp` vaut `false` par défaut ; `MysqlType.DATETIME` correspond à `java.time.LocalDateTime` ; `ResultSetImpl.getObject` renvoie `getLocalDateTime` pour `DATETIME` |
| MySQL 8.4, Docker, Flyway réel, démarrage du serveur avec ses tâches planifiées | **non exécuté** (aucun démon Docker, aucun MySQL) |
| Les 5 mutations du rapport de l'auteur | non rejouées (seule la base verte a été confirmée) |

## Constats classés

### CRITIQUE

**C1. `DATETIME` lu comme `LocalDateTime` sur MySQL : conversions forcées en `Timestamp` qui font tomber le module en production**

- **Preuve (pilote)** : la propriété `treatMysqlDatetimeAsTimestamp` vaut `false` par défaut et `MysqlType.DATETIME` correspond à la classe `java.time.LocalDateTime`. Dans le bytecode de `ResultSetImpl.getObject(int)`, la branche `DATETIME` appelle `getLocalDateTime` sauf si ce drapeau est vrai. `JdbcUtils.getResultSetValue` de Spring, appelé par `queryForMap`/`queryForList` sans type, ne convertit pas `LocalDateTime`. L'URL de production (`application.yml:42`, `docker-compose.yml:60`) ne pose pas ce drapeau.
- **Endroits qui plantent** (`ClassCastException` dès que la valeur n'est pas nulle) :
  - `A/Inventory.java:161, 164, 186, 204`. La ligne 186 lit `issued_at`, **jamais nul**. Or `refreshState` est appelée à chaque rapport, chaque journal, chaque tour des lectures par curseur et chaque réconciliation.
  - `A/ActivationObserver.java:273, 288-289`.
  - `A/IssuanceTap.java:81` : la lecture par curseur reste bloquée au premier enregistrement.
  - `A/LicenseAuditTap.java:74, 90, 103, 134` : imports, transferts, **révocations** et audit ne remontent jamais.
  - `A/ActivationsAdminService.java:86` (`inst`, donc **toutes** les listes et fiches), `:112, 230, 343, 417, 560`.
  - `A/Archiver.java:107`, `A/Jsonl.java:15, 16, 30` (export `events` et archive froide), `A/JournalService.java:198`.
- **Scénario** : la TV envoie `POST /api/v1/activations/report` avec un jeton d'essai. `upsertKey` passe, puis `reconcile` appelle `refreshState`, qui exécute `(Timestamp) k.get("issued_at")` et lève `ClassCastException`. La transaction est annulée et la TV reçoit un 500, à chaque rapport. Côté propriétaire, `GET /activations` répond 500 dès qu'une ligne existe.
- **Pourquoi la suite est verte** : H2 renvoie `java.sql.Timestamp`. `ActivationsMySqlTest`, qui l'aurait vu, a été ignoré faute de Docker.
- **Correction** : remplacer chaque conversion par un utilitaire `Times.instant(Object)` qui accepte `Timestamp`, `LocalDateTime` (lu en UTC, puisque `connectionTimeZone=UTC`) et `OffsetDateTime`. Autre voie : lire avec `rs.getTimestamp` via des `RowMapper` typés. Ajouter `treatMysqlDatetimeAsTimestamp=true` à l'URL est possible mais touche toute l'application. À noter : le même motif existe hors du périmètre, dans `B/orders/OrderService.java:116, 272, 278, 283, 312`. Ajouter ensuite un test qui tourne sur MySQL 8.4 et le rendre **bloquant** pour la mise en route.

### ÉLEVÉ

**H1. Rapport de TV : aucune liaison entre jeton d'appareil et code d'appareil. Inventaire et alertes empoisonnables par n'importe qui**

- **Où** :
  - `A/ReportController.java:49-61` : le jeton d'appareil est authentifié, mais `deviceCode` n'est contrôlé que sur sa forme et son caractère de contrôle. `d.app` n'est jamais vérifié, donc une installation `phone` est acceptée.
  - `A/ActivationObserver.java:81-110` : `ensureTv`, `linkDevice` et `updateTv` sont appliqués **même sans aucun jeton vérifié**.
  - `:273` : `maxTs` rend `open_all_until` et `unlock_until` croissants pour toujours.
  - `:283-285` : jusqu'à 20 évènements `TRIAL_RESET` par rapport.
  - `:304-323` : des commandes inventées entrent dans `act_command`.
  - `:130-137` : un appareil étranger lié au code fait marquer l'activation **authentique** comme `clone`.
- **Scénario** :
  1. L'attaquant appelle `POST /api/v1/devices/register` (route publique) et obtient un jeton.
  2. Il envoie un rapport avec le code de la TV visée : `edition=PRODUCTION`, `openAllUntil=2099`, `trialResets=1000`, deux commandes inventées.
  3. `act_tv` affiche maintenant PRODUCTION, `open_all_until` reste figé à 2099 de façon permanente, et 20 évènements `TRIAL_RESET` sont écrits.
  4. 73 h plus tard, l'alerte `UNDECLARED_COMMAND` s'ouvre.
  5. Quand la vraie TV fait son rapport, sa vraie activation reçoit le drapeau `clone`.
  6. Avec 3 000 rapports par minute au total, l'attaquant peut aussi créer en masse des TV fictives : lignes `act_tv`, évènements `DEVICE_LINKED` et `APP_VERSION`, qui grossissent la chaîne.
- **Preuve** : `AuditProofTest.aStrangerInstallationPoisonsAVictimTvWithoutAnyToken` passe. Extrait :

  ```java
  var attacker = install();                       // inscription publique
  String json = "{\"v\":1,\"deviceCode\":\"" + victim.code() + "\",...\"state\":{\"edition\":\"PRODUCTION\",...\"openAllUntil\":" + year2099
          + ",...\"trialResets\":1000,...\"commands\":[[\"open_all\",\"deadbeef\"," + now + ",30],...]},...}";
  assertEquals(200, report(attacker, json).getResponse().getStatus());
  assertEquals(year2099, ((Timestamp) tv.get("open_all_until")).getTime());
  assertEquals(20, /* TRIAL_RESET */ ...);
  // 73 h plus tard : UNDECLARED_COMMAND = 1 ; puis la vraie TV fait son rapport : flags de sa vraie activation contient ",clone,"
  ```

- **Correction** :
  - (a) Refuser si `d.app` n'est pas `tv`.
  - (b) **Ne rien projeter** (`act_tv`, `act_command`, `act_tv_device`, évènements) tant qu'aucun jeton du rapport n'est vérifié **et** ne vise `deviceCode` par ses facteurs. Sans cela, n'écrire qu'une ligne `act_report` marquée « non vérifié ».
  - (c) Lien collant `device.id` → premier code prouvé, au plus 2 installations par code comme `PlayTicketService.MAX_DEVICES_PER_CODE`. Un écart est ignoré et ouvre une seule alerte par appareil.
  - (d) Borner chaque horodatage envoyé par la TV entre 2026-01-01 et maintenant + 400 j.
  - (e) Ne plus rendre `open_all_until` et `unlock_until` monotones : prendre la valeur du dernier rapport vérifié.
  - (f) Ajouter le test de non-régression correspondant.

**H2. Audit des lectures qui laisse passer en cas d'échec, export interrompu non audité, services non audités pour w23-02**

- **Où** :
  - `A/ReadAuditInterceptor.java:48-61` : `afterCompletion` écrit la ligne après que le corps est parti. Un échec d'écriture ne laisse qu'un `log.error`.
  - `:49` : `ex != null` fait sortir sans écrire de ligne.
  - `A/Exporter.java:89-90` relance l'`IOException`. Un client qui coupe la connexion pendant l'export donne donc `ex != null`, et **aucune ligne** n'est écrite, alors que jusqu'à 199 999 lignes ont pu partir.
  - `A/ActivationsModuleConfig.java:44` : l'audit n'est branché que sur le préfixe de l'API. Les services publiés pour les pages w23-02 (`ActivationsAdminService`, `Exporter`) vérifient la permission mais **n'auditent pas**.
- **Preuve** : `AuditProofTest.aReadIsServedEvenWhenItsAuditLineCannotBeWritten` passe. La table `adm_read_audit` est renommée, `GET /tvs` répond quand même 200 avec l'inventaire. Le cas de l'export interrompu est établi par lecture du code, la coupure de connexion n'a pas été simulée.
- **Correction (audit qui bloque en cas d'échec)** :
  - Déplacer l'écriture **dans le service**, avant de renvoyer les données. `ActivationsAdminService.*` reçoit un `ReadContext` (acteur, canal, route, filtres) et appelle `readAudit.record(...)`. Si l'écriture échoue, l'exception remonte et la réponse est 503, sans données.
  - Pour l'export : écrire **avant le premier octet** une ligne `export=true` avec la borne `exportBound`. `LazyOut` garantit que rien n'est encore engagé. Une ligne « fin » facultative peut suivre.
  - Garder l'intercepteur seulement comme filet : il vérifie qu'une ligne a bien été écrite pour la requête (attribut posé par le service) et journalise une anomalie sinon.
  - Auditer aussi les 403 : une tentative refusée doit laisser une trace.

**H3. Preuve d'intégrité aveugle à une troncature (côté serveur et hors ligne)**

- **Où** :
  - `A/EventLog.java:122-126` et `A/ReadAudit.java:102-105` comparent la dernière ligne à la tête. Mais la tête `act_event_head` est dans la **même base** : un attaquant qui y écrit recule la tête en même temps qu'il supprime la queue.
  - `A/Chains.java:56-60` : l'ancre d'archive est lue dans `act_archive`, que n'importe qui peut écrire, sans clé ni signature. Aucun contrôle croisé avec `act_checkpoint` n'est fait côté serveur.
  - `tools/activations/verify_export.py:114-119` : un point de contrôle dont `eventLastId` dépasse la fin de l'export donne « NON VÉRIFIÉ » et le code 2, alors que c'est exactement le cas d'une **troncature**.
- **Preuves** (toutes passent) :
  - `AuditProofTest.aTailRollbackWithTheHeadRewoundStillVerifies` : 3 évènements supprimés, tête reculée, `verify().ok()` vrai.
  - `aForgedArchiveAnchorHidesTheDeletionOfTheWholeHistory` : un seul `INSERT` dans `act_archive`, puis `DELETE FROM act_event`, et `verify()` reste vrai.
  - Le script Python : la ligne 3 est retirée de l'export alors que le point de contrôle annonce `eventLastId=3`. La sortie est « points de contrôle vérifiés / NON VÉRIFIÉ … export plus court », code 2.
- **Correction** :
  - `verify()` doit aussi vérifier le **dernier point de contrôle signé**. Sa signature Ed25519 doit être bonne, et la ligne `event_last_id` doit exister avec `hash = event_head`, sinon il faut une archive signée qui la couvre. Il faut aussi `last_id ≥ event_last_id`.
  - Signer `act_archive` (Ed25519, `act-checkpoint.key`) et refuser une ancre non signée.
  - Dans `verify_export.py` : « export plus court qu'un point de contrôle » doit devenir une **ANOMALIE** (code 1) pour un export complet (sans `after`). Ajouter `--reads-anchor-*` et comparer aussi `readHead`.
  - Exposer la clé publique des points de contrôle (`GET /checkpoints` → `publicKey`) pour que le propriétaire la garde hors du serveur.

### MOYEN

**M1. Numéro de migration V65 : risque d'exécution hors ordre, et pas de script de retour arrière**

- `integration/agents` porte déjà `V62__wallet.sql`. La production est à V61. V63 et V64 sont absents, et la conception (§ 5.1) annonce encore « V64 réservé à W21 ».
- Scénario : V65 est déployé, puis une V63 ou V64 arrive plus tard. Flyway `validate` refuse alors de démarrer (« resolved migration not applied »), parce que `out-of-order` n'est pas positionné (`application.yml:57-59`). Le `README.md` des migrations impose « plus haut + 1, jamais de trou ».
- Correction : à la fusion, renommer en **V63** (plus haut + 1 sur `integration/agents`) et mettre à jour les références dans `SourceRulesTest`, `ActivationsOffTest` et `docs/ACTIVATION-TRACKING.md`. Retirer toute « réservation » de la conception et des cahiers W21. Ne jamais remplir un numéro inférieur au plus haut déjà appliqué en production.
- Retour arrière : il n'existe **pas** de `U65` (il existe `db/rollback/U50-U52__licenses_rollback.sql`). Les tables sont nouvelles, donc revenir au code précédent sans rien supprimer est sans risque. Supprimer les tables fait perdre l'historique, qui par décision n'est jamais purgé. Écrire `db/rollback/U6x__activation_tracking_rollback.sql` : `mysqldump` des tables `act_*` et `adm_read_audit` d'abord, puis `DROP` dans l'ordre et `DELETE FROM flyway_schema_history WHERE version = '6x'`.

**M2. Réconciliation nocturne en une seule transaction qui tient le verrou de tête**

- `A/Reconciler.java:104-120` : `reconcileAll()` parcourt toutes les activations dans les états `EMISE`, `EXPIRED_UNUSED` et `ACTIVATED`. Ces états ne font que croître : une production illimitée reste `ACTIVATED` à vie, et une activation `EXPIRED_UNUSED` le reste. Le tout s'exécute **dans une seule transaction**, qui prend le verrou `act_event_head` (`FOR UPDATE`) dès le premier évènement écrit.
- Le pool Hikari ne compte que 6 connexions (`application.yml:46`). Pendant ce temps, les rapports et les journaux attendent ce verrou avec une connexion ouverte : risque d'épuiser le pool **pour toute l'application** et de dépasser l'attente de verrou de 50 s.
- Mesure indicative (`NightlyProofTest`, H2 en mémoire) : 30 000 activations et 10 000 TV, soit environ un an à 10 000 TV. Le résultat est **une transaction de 3,5 s et 9 301 évènements**. MySQL n'a pas été mesuré ; estimé 5 à 20 fois plus lent.
- Correction : une transaction par lot de 500 activations, comme dans les lectures par curseur. Requêtes ciblées sur index : `state = 'EMISE' AND expires_at < now`, `state = 'ACTIVATED' AND usage_to < now`. Sortir `EXPIRED_UNUSED` de la boucle : un constat tardif est déjà traité à l'arrivée. Mesurer sur MySQL avant la mise en route.

**M3. L'archive froide avec retrait casse l'effacement et l'idempotence**

- `A/Inventory.java:70` : l'effacement tient tant que l'évènement `E:<tv_ref>` existe. Or `A/Archiver.java:134-140` supprime les évènements de plus de 24 mois. Même dépendance pour `A/JournalService.java:64-88` (`DbState` relit les empreintes d'entrées dans `act_event.after_json`).
- Scénario : 25 mois après un effacement, le propriétaire archive avec retrait. Au rapport suivant de cette TV, `act_tv.device_code` réapparaît : le **droit à l'effacement est violé**. Un vieux lot de journal téléversé de nouveau est alors traité comme neuf : évènements en double, et une réécriture n'est plus détectée.
- Preuve : `AuditProofTest.anErasedCodeComesBackAfterTheColdArchive` passe.
- Correction : une table de tombes `act_erased(tv_ref PK, at)` et une table `act_journal_entry(kid, n, hash, prev, PK(kid, n))`, jamais archivées. Elles remplacent l'analyse du JSON des évènements. Ne retirer de `act_event` que des faits qui ne servent plus à l'idempotence.

**M4. Clés `act-*.key` sans valeur de contrôle : une clé changée ou restaurée casse tout en silence**

- `A/TvRef.java:24-29` et `A/Chains.java:23-28` : la clé est le SHA-256 des **octets bruts** du fichier, donc un simple retour à la ligne la change.
- Avec une autre `act-ref.key`, toute TV déjà connue reçoit un nouveau `tv_ref`. Dans `A/Inventory.java:63-67`, la contrainte unique sur `device_code` fait échouer l'`INSERT`, et l'erreur est avalée en silence. Ensuite `A/ActivationObserver.java:269` (`queryForMap`) lève `EmptyResultDataAccessException`, et **chaque rapport répond 500**.
- Avec une autre `act-audit.key`, `verify()` déclare toutes les lignes « modifiées » : faux soupçon de falsification.
- Preuve : `AuditProofTest.aTvKnownUnderAnotherReferenceCanNoLongerReport` passe. Le 500 est constaté et l'exception apparaît dans la sortie.
- Correction : à la première utilisation, enregistrer `HMAC(clé, "castbridge-act-ref-check-v1")` (et l'équivalent pour la clé d'audit) dans une table. Au démarrage, en cas d'écart, répondre 503 « clé changée » au lieu de corrompre. Dans `ensureTv`, ne plus avaler la violation de `uq_act_tv_code`.

**M5. Débits : le budget global est consommé avant le budget par appareil, et les compteurs ne sont jamais purgés**

- `A/ReportController.java:79-80` : `report-all` (3 000 par minute) est compté **avant** `report:<id>` (1 par 10 min). Une seule installation dont toutes les tentatives sont refusées consomme donc tout le budget global, et toutes les TV reçoivent 429. Il suffit de répartir les requêtes sur environ 25 adresses IP, la limite par IP de production étant de 120 par minute.
- Preuve : `AuditProofTest.oneDeviceTokenExhaustsTheGlobalReportBudget` passe. Le spammeur n'obtient qu'un seul 200, puis la TV honnête reçoit 429.
- Les compteurs en mémoire (`A/ActivationsPolicy.java:29`) ne sont jamais purgés : il en reste un par appareil, par acteur et par clé. Le compteur `journal:<kid>` (`A/JournalService.java:106`) prend un `kid` **non authentifié**, puisqu'il est lu avant la vérification de signature.
- Correction : contrôler d'abord la limite par appareil, et ne compter dans le budget global que les rapports acceptés. Purger les compteurs inactifs. Prendre le `kid` du compteur de journaux après la vérification de signature, ou compter par acteur.

**M6. Journal : deux téléversements concurrents peuvent fourcher sans quarantaine**

- `A/JournalService.java:64-88, 101-149` : la vérification lit l'état sans verrou. Deux lots signés différents qui portent le même `n` (téléphone cloné, ou clé copiée) et arrivent en même temps sont tous deux jugés « nouveaux ».
- Le second voit `ev()` renvoyer `false` (`:239-241`, valeur de retour ignorée), mais ses projections sont **appliquées** : `upsertKey` d'une autre empreinte, drapeau `declared_journal`. Il n'y a ni quarantaine ni `JOURNAL_BROKEN`. L'`INSERT` dans `act_journal_gap` (`:142`) peut aussi échouer en 500.
- Constat établi par lecture du code, sans test de concurrence.
- Correction : au début de `upload`, poser `INSERT … act_tool` si la ligne est absente, puis `SELECT … FOR UPDATE` sur cette ligne. Si `ev()` renvoie `false` pour une entrée réputée nouvelle, mettre le lot en quarantaine avec la raison `REWRITTEN`.

**M7. Lectures par curseur sur `lic_*` : des lignes validées dans le désordre peuvent être sautées**

- `A/IssuanceTap.java:72-73` et `A/LicenseAuditTap.java:71, 84, 99` lisent `WHERE id > curseur`. Avec InnoDB, une ligne d'identifiant plus petit peut être validée **après** une ligne plus grande déjà lue.
- Effets : une émission du serveur manquée entraîne un faux `UNDECLARED` ; une **révocation manquée** laisse l'activation jamais marquée `REVOKED`.
- Le cas `lic_audit` est sûr : la tête de chaîne sérialise les insertions.
- Correction : relire une fenêtre glissante, par exemple `id > curseur - 200`. Les ajouts sont idempotents (`idem_key`), donc relire ne coûte presque rien. On peut aussi ne lire que les lignes de plus de 5 s.

**M8. Émissions connues seulement par le registre : pas d'inventaire, donc la décision « expiré non utilisé » n'est pas tenue**

- `A/IssuanceTap.java:90-99` : une émission importée devient un évènement et une ligne `act_reg_issue`, mais **aucune ligne `act_key`**.
- Jusqu'à w23-03 (journaux signés), toutes les émissions du bureau et du téléphone arrivent par cette voie. Aucune n'apparaît donc dans les listes, le tableau de bord ou `act_daily`. Elles ne deviennent jamais `EXPIRED_UNUSED`, ce qui contredit la décision du propriétaire.
- Le guide documente cette limite, mais pas cette conséquence.
- Correction : afficher et compter les émissions non vues à partir de `act_reg_issue` (`not_after < maintenant` et aucune `act_key` avec le même `(kid, nonce)`). On peut aussi créer une ligne d'inventaire avec une empreinte de substitution, à fusionner au premier constat.

### FAIBLE

- **L1** : `B/config/AccessLogFilter.java:36` journalise le chemin complet. `GET /api/v1/admin/activations/tvs/{code}` écrit donc le **code d'appareil complet** dans le journal applicatif, comme le montre la sortie de la suite (`… /tvs/ZAEX-6TB5-7KTC-0N7M`). Masquer ce chemin dans le filtre, ou passer le code dans le corps ou dans un paramètre masqué.
- **L2** : `A/ReadAudit.java:43-58` recopie tous les paramètres. Seuls `cbx1.…` et la forme exacte d'un code d'appareil sont masqués. Une **clé compacte** de 165 caractères tapée dans `license=`, `sort=` ou un paramètre inconnu est recopiée en clair. Les tentatives refusées (403) ne sont pas auditées (`ReadAuditInterceptor.java:51`). Une sonde qui obtient 404 sur `/tvs/{code}` laisse une ligne, mais **sans la cible**. Correction : liste blanche de paramètres, masquage de toute suite de plus de 20 caractères base32, audit des 403.
- **L3** : les horodatages venus de la TV (`installedAt`, `commands[2]`, `openAllUntil`, `usageTo`) et le `at` d'une entrée de journal (`JournalVerifier.java:173`, aucune borne haute) ne sont pas bornés. Sur MySQL en mode strict, une valeur hors de la plage `DATETIME` provoque une erreur, une annulation et un 500. Un lot signé qui en contient ne pourra **jamais** être ingéré. Les autres champs du journal sont déjà bornés par `num()`.
- **L4** : les jetons des rapports sont vérifiés sans les portées (`KEY_NOT_ALLOWED`, `ActivationObserver.java:150-164`). Les champs d'une entrée `issue` du journal ne sont jamais comparés au jeton vérifié : la première source gagne (`COALESCE`) et aucune divergence ne déclenche d'alerte (conception § 4.5).
- **L5** : `changes` (`ActivationsAdminController.java:202-225`) plafonne à 20 places pour l'API et les futures sessions web réunies. La clé de session inclut un identifiant choisi par le client, donc un seul acteur peut prendre les 20 places. La séquence « tester puis poser » (`:215`) n'est pas atomique. `integrity` parcourt toute la chaîne et ne demande que `ACT_READ`, jusqu'à 120 fois par minute : à réserver à `ACT_EXPORT` ou à mettre en cache.
- **L6** : `ConsoleSession` n'est pas branché, ce qui est correct puisque l'option A est désactivée. Mais `ActAccess.actorOf` (`:28`) traduit **tout** `ROLE_ADMIN` en propriétaire (`api-token`). Celui qui branchera la session console ne doit surtout pas accorder `ROLE_ADMIN`, sinon une session téléphone devient OWNER. Le dire dans le cahier.
- **L7** : effacement (`ErasureHook.java:68-89`). Seule la forme canonique du code est effacée dans les lots. Le lien `tv_ref` ↔ `device.id` reste (pseudonymisation, pas anonymisation). Les TV vues seulement en essai ne sont pas effaçables, ce qui est documenté.
- **L8** : la détection de clone (`ActivationObserver.java:131`) ignore un `androidIdHash` nul, alors que ce champ est facultatif à l'inscription. Alerter aussi sur au moins 2 `installId` distincts en 30 jours.
- **L9** : `verify_export.py` n'a pas d'ancre pour la chaîne des lectures (fausse alarme après une archive), et `readHead` n'est jamais comparé. La clé publique des points de contrôle n'est exposée nulle part.
- **L10** : le dépôt de journal (`@RequestBody String`) lit le corps sans limite avant le contrôle des 180 000 caractères, alors que la conception dit 128 Ko. Le risque est réservé aux administrateurs.
- **L11** : `delivered_bt_at`, `installed_at` et `first_seen_tv_at` sont remplis avec `COALESCE` (premier arrivé), ce qui dépend de l'ordre d'arrivée. Prendre `LEAST` des dates portées par les sources.
- **L12** : interblocage possible entre un journal (TV X, puis la tête, puis TV Y) et un rapport (Y, puis la tête). InnoDB annule une transaction, ce qui donne un 500 et une nouvelle tentative.
- **L13** : avec le module des licences éteint, chaque production vue ouvre `LICENSE_PENDING` après 7 jours. C'est une alerte faible, mais elle fait du bruit.

## Réponses aux questions de l'audit

1. **Contrôle d'accès**
   - Toutes les routes `/api/v1/admin/activations/**` passent par `apiChain` (`SecurityConfig`, `hasRole("ADMIN")` : jeton porteur seulement). Les comptes web reçoivent `ROLE_WEBADMIN` et `ROLE_LIC_*`, jamais `ROLE_ADMIN`.
   - Aucune lecture ni écriture n'est possible sans le jeton. Il n'y a pas de cloisonnement entre clients (un seul propriétaire), donc pas d'IDOR au sens propre.
   - La matrice `ActPermissions` est conforme au § 6.2. Le second facteur suit la règle du module des licences : TOTP **enrôlé**, et le jeton compte comme second facteur fort.
   - Constats liés : la route du rapport de TV (**H1**, **M5**) ; le flux `changes` (L5) ; la reprise asynchrone en 401, qui relève de `SecurityConfig` : en Spring Security 6, le contexte posé par `BearerFilter` n'est pas enregistré pour la reprise asynchrone, et la correction est hors du périmètre du cahier ; `ConsoleSession` (L6).
2. **Audit des lectures** : H2 (laisse passer en cas d'échec, correction détaillée) et L2 (secrets dans les paramètres).
   - Le verrou de tête est correct pour 16 écrivains : `FOR UPDATE` sur une ligne de clé primaire, et les deux têtes sont sur des lignes distinctes.
   - `prev` est vérifié ligne à ligne.
   - La troncature et le retour arrière ne sont **pas** détectés (H3).
   - Les points de contrôle quotidiens ont une signature correcte, mais ne sont pas recoupés côté serveur.
   - Clés : voir M4. `act-checkpoint.key` est sur le même hôte que la base, donc ne protège que si le propriétaire exporte les points de contrôle hors du serveur.
3. **Signatures et journaux**
   - Séparation de domaine correcte : `type=journal` est signé dans l'enveloppe, et les domaines `castbridge-console-session-v1` et `castbridge-activation-compact-v1` sont distincts.
   - Rejeu : un lot identique donne `DUPLICATE`.
   - Trous : bien détectés, et refermés par un lot plus ancien avec contrôle `prev` avant et après. Le contrôle « après » n'est **pas testé** (mutation M10 ci-dessous).
   - Réécriture : mise en quarantaine en séquentiel, contournable en concurrence (M6).
   - Registre importé sans le jeton : reconnu par `(kid, nonce)`, mais sans inventaire (M8).
   - Clone : jeton d'un autre appareil, ou deux identifiants matériels (L8).
   - Fenêtre : `> 48 h` alerte, `= 48 h` est accepté ; installation tardive tolérée 24 h (marge d'horloge de la TV, documentée).
   - `EXPIRED_UNUSED` : dérivé de `expires_at` selon l'horloge du serveur, en instants UTC, sans problème de fuseau. Correct, mais rafraîchi seulement à l'arrivée d'un fait et chaque nuit.
   - Date de remise Bluetooth : correcte dans tous les ordres d'arrivée (`syncContact` dans `upsertKey` et `deliver`).
   - Parité Java avec `journal-vectors.json` : verte. Kotlin non vérifié. Le document devrait préciser que `h` est l'empreinte en **texte hexadécimal minuscule**.
4. **Protection des données**
   - Aucun jeton `cbx1` dans les tables ni dans les journaux : 0 occurrence de `cbx1.` dans la sortie de la suite. Les exceptions ne journalisent que le nom de leur classe.
   - Écarts : le code complet dans le journal d'accès (L1), la clé compacte dans l'audit des lectures (L2), le texte d'un lot en quarantaine non authentifié (`JournalService.java:116`), qui pourrait contenir n'importe quelle valeur de champ.
   - `tv_ref` : HMAC tronqué à 64 bits. Collisions négligeables à 10 000 TV (environ 3 × 10⁻¹²).
   - Archive et purge : 90 jours de rapports correctement purgés ; archive froide, voir M3.
   - Tâches planifiées : toutes vérifient `enabled`. Un échec est journalisé sans nouvelle tentative avant le tour suivant. `Snapshots` et `Checkpoints` sont idempotentes. Les lectures par curseur sont idempotentes, mais restent bloquées sur un enregistrement qui provoque une exception (voir C1).
5. **Migration**
   - Types, longueurs d'index en utf8mb4 (au plus 384 octets) et contraintes `CHECK` (8.0.16+) : corrects. Pas de clé étrangère. Les mots réservés de H2 ont été renommés.
   - Collation `utf8mb4_0900_ai_ci` : insensible à la casse, sans effet nuisible, puisque l'hexadécimal est toujours en minuscules et le code d'appareil en forme canonique.
   - Trou V61 → V65 : accepté seul. Le risque d'exécution hors ordre est décrit en **M1**. Pas de script de retour arrière.
   - Tailles : l'estimation d'environ 0,5 Go par an à 10 000 TV reste plausible. `act_journal_batch` peut monter jusqu'à 180 Ko par lot.
6. **Cohérence avec le module des licences**
   - Lectures seulement. Aucune écriture dans `lic_*` : vérifié par lecture et par `grep`, aucune requête `insert`, `update` ni `delete` sur `lic_`.
   - Module des licences éteint : les composants existent quand même ; le TOTP web ne peut pas être enrôlé, donc les permissions sensibles sont fermées pour le web, alors que le jeton porteur passe ; voir aussi L13.
   - Licence ≠ activation : affichées séparément. Aucun transfert de licence n'est inventé (`TRANSFERRED` est recopié depuis `lic_transfer`).
7. **Qualité des tests** : voir ci-dessous.

## Mutations

Les 5 mutations du rapport de l'auteur ne sont pas rejouées ici. La base de la copie hors dépôt est verte : 89 tests, 0 échec.

5 mutations supplémentaires, exécutées une par une sur `castbridge.server.activations.**` dans la copie hors dépôt. **Toutes survivent** :

| # | Mutation | Ligne | Résultat |
|---|---|---|---|
| M6 | supprimer le refus d'un appareil bloqué | `ReportController.java:50` | suite verte (aucun test « bloqué ») |
| M7 | ne plus écarter les paramètres secrets (`token`, `password`, `signature`…) de l'audit | `ReadAudit.java:46` | suite verte |
| M8 | supprimer la garde de l'effacement (`E:<tv_ref>`), donc toujours remettre le code | `Inventory.java:70` | suite verte (`ErasureTest` ne fait aucun rapport après l'effacement) |
| M9 | supprimer la restriction du canal `phone` dans `require` | `ActPermissions.java:57` | suite verte (`ConsoleSessionTest` ne teste que `allows`, une logique **dupliquée**) |
| M10 | ne plus vérifier le `prev` du lot **suivant** quand un trou est comblé | `JournalVerifier.java:147` | suite verte (aucun vecteur « comblement discordant ») |

Tests à ajouter :
- la suite sur MySQL 8.4, à rendre obligatoire avant d'allumer le module ;
- un rapport sans jeton qui ne modifie rien (H1) ;
- l'audit qui bloque en cas d'échec et l'export interrompu (H2) ;
- une troncature avec la tête reculée, et une fausse ancre (H3) ;
- l'archive suivie d'un rapport sur une TV effacée (M3) ;
- un changement de `act-ref.key` (M4) ;
- l'ordre des débits (M5) ;
- deux téléversements concurrents (M6) ;
- un vecteur `gap-fill-next-mismatch` dans `journal-vectors.json`.

## Vérifié correct

- Les routes de l'API d'administration exigent le jeton porteur. Le module répond 404 quand il est éteint et 503 sans `act-ref.key`. Les tâches planifiées vérifient l'interrupteur.
- Chaque méthode de service vérifie sa permission **avant** toute lecture. La matrice est conforme au § 6.2. `READONLY` ne peut ni exporter ni classer. `SUPPORT` accuse réception sans pouvoir classer.
- Aucune révocation, suspension ni libération automatique. Les alertes sont douces : une seule ouverte par objet, et `hits` n'augmente que sur un nouvel indice.
- Aucun jeton stocké : seuls l'empreinte et l'étiquette de 8 hex sont gardées. `EventLog.append` refuse un champ qui a la forme d'un code d'appareil.
- Chaîne `act_event` : tête verrouillée, `idem_key` unique. Un doublon ne compromet pas la transaction sur MySQL. Les horodatages en millisecondes avec `connectionTimeZone=UTC` et `preserveInstants` font un aller-retour stable (Douala n'a pas d'heure d'été).
- Fenêtre de 48 h exactes, `EXPIRED_UNUSED`, durée propre d'une activation installée, production illimitée qui reste `ACTIVATED`.
- Pagination par curseur sans `OFFSET`, aucun `COUNT(*)` sur `act_event` dans le chemin d'affichage. Le tri des `NULL` est cohérent avec MySQL.
- Les exports protègent contre l'injection de formules (apostrophe devant `= + - @`), ne contiennent jamais un code complet, et respectent la limite de lignes en refusant au lieu de tronquer.
- `ConsoleSession` : défi à usage unique, consommé même par un échec, 120 s, 10 par heure et par clé, jeton de session stocké haché, éteint par défaut.

## Liste de contrôle avant la mise en route

1. Corriger **C1**, **H1**, **H2**, **H3** et **M1** à **M5**, puis refaire passer la suite et les tests de preuve ci-dessus, qui doivent alors **échouer**.
2. Sur une machine avec Docker, exécuter `cd backend && ./mvnw -q test -Dtest='ActivationsMySqlTest,MySqlContainerTest'` sur **MySQL 8.4**. Puis lancer toute la suite `castbridge.server.activations.**` contre MySQL : Flyway réel, 16 écrivains, rapport de bout en bout, export `events`, archive.
3. Sur une copie de la base de production (V61), vérifier que Flyway applique V62 (portefeuille) puis la migration de ce module renumérotée, que `validate` est vert, et que `SHOW CREATE TABLE` montre les contraintes `CHECK` et les index attendus.
4. Mesurer sur MySQL `reconcileAll()` avec 30 000 et 100 000 activations, ainsi que la durée du verrou de tête et la saturation du pool Hikari (M2).
5. Configuration :
   - `CASTBRIDGE_ACTIVATIONS_ENABLED=1` seulement après les points 1 à 4 ;
   - `CASTBRIDGE_LICENSES_ENABLED=1` ;
   - `CASTBRIDGE_LICENSES_TRUSTED_KEYS` avec les clés du bureau et du téléphone, et leurs portées ;
   - `CASTBRIDGE_ACTIVATIONS_ARCHIVE_DIR` sur un volume persistant hors de l'image ;
   - `CASTBRIDGE_ACTIVATIONS_CONSOLE_SESSIONS` laissé à `false` ;
   - vérifier `CASTBRIDGE_DB_URL` en production (`connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`, plus le drapeau `DATETIME` si c'est la voie retenue pour C1).
6. Secrets, dans le dossier des secrets, créés **une seule fois**, sans retour à la ligne parasite, et **sauvegardés chiffrés hors du serveur** avant la première écriture :
   - `act-ref.key` (32 octets aléatoires, obligatoire) ;
   - `act-audit.key` ;
   - `act-checkpoint.key` (graine Ed25519 en base64).

   Noter aussi la clé publique des points de contrôle. Ne **jamais** les changer : voir M4.
7. Sauvegarde : ajouter à `backup.sh` le relevé de `act_event_head`. Télécharger chaque semaine `GET /checkpoints` et le conserver hors du serveur.
8. Préparer et tester le script de retour arrière `U6x` (M1).
9. Après la mise en route : `GET /api/v1/admin/activations/integrity` doit être vert. Vérifier que la sortie des tâches planifiées de 60 s, 00:05, 00:10, 03:50 et 03:55 ne contient pas d'`ERROR`, et qu'aucun code d'appareil complet n'apparaît dans les journaux (après L1).
10. Pour la TV (w23-04) : l'exception de gel est à obtenir. Ne publier le rapport de la TV qu'après la correction de H1.

## Fichiers

- Ce rapport : `docs/agent-reports/audit-opus-w23-01.md`.
- Tests de preuve et journaux (**hors dépôt**, session) : `<scratchpad>/proof/backend/src/test/java/castbridge/server/activations/AuditProofTest.java`, `NightlyProofTest.java`, `<scratchpad>/py_proof.py`, `<scratchpad>/suite.log`, `<scratchpad>/mut-*.log`.
