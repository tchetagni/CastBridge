# Rapport w23-01 : suivi des activations, API serveur, migration V65

Cahier : `docs/agent-briefs/sonnet-w23-01-suivi-activations-api-serveur-v65.md` · conception : `docs/coordination/DESIGN-W23-SUIVI-ACTIVATIONS-CONSOLE-2026-10-04.md` · guide : `docs/ACTIVATION-TRACKING.md`.
Branche de travail : `worktree-agent-a31c5a85f56ccbeab` (avancée sans conflit sur `integration/agents` HEAD `89547c9b`, le worktree partait d'un ancêtre). Rien poussé, rien déployé, aucun accès serveur, `~/.castbridge-signing` non touché.

## Décisions du propriétaire (2026-10-04) appliquées
Code valable **48 h exactement** après l'émission (`expires_at`, alerte `OUT_OF_WINDOW` si fenêtre > 48 h ou installation > 24 h après sa fermeture) ; une activation installée **garde sa durée propre** (plafond d'usage, production illimitée : jamais bornée par les 48 h) ; code jamais vu sur une TV 48 h après l'émission = **« expiré non utilisé »** (`EXPIRED_UNUSED`, et non « non constatée » de la conception) ; téléphone propriétaire **hors ligne** (journaux par fichier/partage/QR, téléversés depuis un navigateur ; `ConsoleSession` désactivée) ; rapport TV : **exception de gel** à demander pour w23-04 (route serveur livrée, aucune ligne TV touchée). Test dédié : `OwnerRulesTest`.

## RED puis GREEN
* **RED (tests écrits d'abord, W15 R1)** : 82 tests écrits, V65 + squelettes neutres (méthodes sans effet) puis `mvn -o test -Dtest='castbridge.server.activations.*Test,!VolumeTest'` : `Tests run: 82, Failures: 61, Errors: 12`. Extraits collés (assertions, pas des erreurs de compilation) :
  `EventLogTest.theChainStaysVerifiableAfterTenThousandEvents:48 RED ==> expected: <true> but was: <false>` ·
  `EventLogTest.replayingTheSameSourceAddsNothing:106 expected: <20> but was: <0>` ·
  `JournalIngestTest.aValidBatchIsStoredProjectedAndChained:60->send:43 {"status":404,…} ==> expected: <200> but was: <404>` ·
  `ReportTest.aReportLinksTheDeviceAndRecordsTheActivationItCarries:53->ok:44 … expected: <200> but was: <404>` ·
  `AccessMatrixTest.everyFunctionAgainstEveryRoleAndSecondFactor:101 ACT_ALERT_ACK / reader must be refused ==> expected: <true> but was: <false>` ·
  `ReadAuditTest.everyReadRouteOfTheModuleIsAuditedExactlyOnce:67 … expected: <[/api/v1/admin/activations/activations, …]> but was: <[]>` ·
  `AlertsApiTest.severitiesFollowTheDesignTable:54 expected: <critical> but was: <?>`. (Seuls `ActivationsOffTest`, 3 tests, passait déjà : un module éteint répond 404 sans code.)
* **GREEN** : suite du module `castbridge.server.activations.*` : 89 tests, 0 échec, 2 ignorés (MySQL), dont `VolumeTest` ; **suite complète du backend `mvn -o test` : 355 tests, 0 échec, 4 ignorés** (Docker absent : `ActivationsMySqlTest` ×2, `MySqlContainerTest`, `LicenseRestoreCheckTest`). Python : `tools/activations/test_verify_export.py` 7 tests OK.

## Fichiers (tous dans ceux du cahier, sauf mention)
* **Migration** `backend/src/main/resources/db/migration/V65__activation_tracking.sql` : 19 tables (celles du § 5.1 + `act_policy` demandée + 5 ajouts que le code exige : `act_journal_gap`, `act_command`, `act_reg_issue`, `act_cursor`, et la tête n° 2 dans `act_event_head` pour la chaîne des lectures). Colonnes renommées pour H2 2.x qui réserve `DAY`, `MONTH`, `VALUE`, `ROWS` (`snap_day`, `snap_month`, `val`, `row_count`, `rows_rendered`) ; `flags` en texte « ,a,b, » (pas de `SET`, absent de H2).
  **Numéro** : V62, V63, V64 non créés ; V65 pris. Flyway accepte le trou V61→V65 ; la règle « jamais de trou volontaire » du `README.md` des migrations est volontairement non suivie (numéros réservés par le cahier).
* **Module** `backend/src/main/java/castbridge/server/activations/` (33 fichiers) : toutes celles du cahier (`ActivationsProperties`, `ActivationsModuleConfig`, `ActPermissions`, `TvRef`, `JournalVerifier`, `JournalService`, `ReportController`, `ActivationObserver`, `IssuanceTap`, `LicenseAuditTap`, `Reconciler`, `AlertService`, `EventLog`, `ReadAudit`, `ReadAuditInterceptor`, `Snapshots`, `Checkpoints`, `Exporter`, `Archiver`, `ErasureHook`, `ConsoleSession`, `ActivationsAdminController`, `ActivationsPolicy`) plus des classes d'appui du même dossier : `ActAccess`, `ActClock`, `AuditNote`, `Chains`, `ConsoleController`, `Cursors`, `Inventory`, `Jsonl`, `ActivationsAdminService`, `ToolDirectory`. `application.yml` : seulement le bloc `castbridge.activations.*` (éteint). `RateLimitFilter` non touché (les débits vivent dans le module).
* **Tests** `backend/src/test/java/castbridge/server/activations/` (20 classes de test et 2 outils : `ActTestBase`, `JournalBuilder`) ; **vecteurs** `tools/activation/journal-vectors.json` (14 cas, 23 étapes : clés de test dérivées de textes publics, résultat attendu écrit à la main, régénérable par `-Dcastbridge.vectors.write=true`) ; **hors ligne** `tools/activations/verify_export.py` + test ; **guide** `docs/ACTIVATION-TRACKING.md` ; ce rapport ; ma ligne de `docs/agent-briefs/SONNET-WAVE23-INDEX.md`.

## Mesures (H2, cette machine, indicatives)
100 000 activations, 1 000 000 d'évènements, 10 000 TV : page de liste 4-6 ms, page profonde 2-3 ms, fiche TV 3-4 ms (ses 100 évènements par son index), liste des TV 3 ms, tableau de bord 24 ms (médiane de 5 appels, avec écriture de la ligne d'audit). Aucun `OFFSET`, aucun `COUNT(*)` sur `act_event` (`SourceRulesTest` lit le code). **MySQL non mesuré.**

## Mutations essayées (chacune rouge, puis rétablie et reverte au vert)
1. retirer `UNIQUE (idem_key)` de V65 → `EventLogTest.replayingTheSameSourceAddsNothing` (20 attendus, 40) **et** `InventoryScenarioTest` (30 évènements attendus, 31 : les taps relisent les tables des licences après perte du curseur) ;
2. ne pas verrouiller la tête de chaîne (`FOR UPDATE` retiré) → `sixteenWritersAtOnceCannotForkTheChain` (400 attendus, 208) ;
3. ne pas vérifier `prev` dans `JournalVerifier` → `JournalVectorsTest` (`prev-mismatch`) et `JournalIngestTest.aBatchThatDoesNotFollow…` ;
4. exclure `/tools` de l'intercepteur d'audit → `ReadAuditTest.everyReadRouteOfTheModuleIsAuditedExactlyOnce` (9 lignes attendues, 8) ;
5. `closeAlert` vérifie `ACT_ALERT_ACK` au lieu de `ACT_ALERT_DECIDE` → `AccessMatrixTest` (OWNER sans TOTP aurait classé).
Un défaut réel trouvé par les tests de rejeu : la date de remise Bluetooth n'atteignait pas la TV quand le journal du bureau arrivait après celui du téléphone (`Inventory.syncContact`).

## Écarts à la conception, à connaître
* `changes` (flux de changements) **bloque un fil de requête** au lieu d'un `DeferredResult` : la reprise asynchrone repasse par la chaîne de sécurité, dont le filtre du jeton (`OncePerRequestFilter` de `SecurityConfig`, hors fichiers du cahier) n'authentifie pas un renvoi asynchrone : 401 constaté. Bornes : 1 par session, 20 en tout.
* `ConsoleSession` (option A) écrite et testée (défi 32 hex, 120 s, usage unique, 10/h/clé, jeton 15 min, portée lecture + dépôt de journal, **jamais** export ni décision) mais **non câblée** : il faut qu'un filtre de `SecurityConfig` appelle `ConsoleSession.authenticate`. Désactivée par défaut, comme demandé.
* Réponse du rapport TV : pas de liste de révocations signée (`revocations` omis) ; pas de « dossier complet » ZIP.
* Types d'évènement ajoutés à la liste fermée : `REFUSED` (refus du journal), `ARCHIVED` (ordre d'archive) ; alerte ajoutée : `BAD_TOKEN` (jeton faux ou mal formé, ignoré). `act_archive` garde `last_hash` et `removed` : la vérification de la chaîne repart de la dernière ligne archivée.
* `IssuanceTap` : les émissions **importées** du registre portent l'empreinte de l'évènement du registre, pas celle d'un jeton ; elles deviennent un évènement et un couple `(kid, nonce)` (`act_reg_issue`) qui fait reconnaître l'activation comme déclarée quand la TV la rapporte ; pas de ligne d'inventaire avant. Le module des licences ne publie aucun évènement Spring : tous les crochets sont des lectures par curseur (émissions, imports, transferts, révocations, audit), dit au guide.
* `ErasureHook` : le module des licences ne publie pas d'évènement d'effacement : travail de comparaison (`lic_seat.anonymized`, 5 min). Il efface aussi le code lu dans les lots de journal signés (le lot est marqué effacé, sa signature n'est plus vérifiable). Une TV vue seulement par un essai n'est reliée à aucun client.
* Audit des lectures écrit **après** la réponse (`afterCompletion`) : si l'écriture échoue, la lecture a eu lieu (ERROR journalisé sans donnée). Un audit fail-closed demanderait un `ResponseBodyAdvice` : à arbitrer par l'audit Opus.

## Non vérifié (honnêteté)
* **Rien n'a tourné contre le MySQL de production** ni contre MySQL 8.4 : Docker n'a pas de démon dans cette session ; `ActivationsMySqlTest` (migration V65, 16 écrivains sur les deux chaînes, journal + rapport de bout en bout) est écrit, compile, et a été **ignoré**. Les requêtes évitent les écarts connus (colonnes réservées, `UPDATE` à plusieurs affectations : la colonne `kind` est affectée en dernier car MySQL évalue de gauche à droite) mais n'ont pas été exécutées sur MySQL.
* Les clés d'outil Kotlin (w23-03) n'ont pas encore consommé `journal-vectors.json` ; le format du résumé de droits (`genre:nombre` trié, `usage` compris, `-` si aucun) doit être identique côté outil et côté TV (le serveur en calcule un pour les jetons rapportés, le journal le fournit pour les jetons émis : une différence ne casse rien, la valeur du journal est gardée).
* `backup.sh` non modifié (hors fichiers du cahier) : il sauvegarde déjà toutes les tables `act_*` dans le dump complet, mais ne journalise pas les têtes de chaîne et les trois fichiers de secrets du module restent à sauvegarder à la main (guide § 2).
* Le serveur n'a pas été démarré en vrai (scheduler 60 s des taps, 03:50 / 00:05 / 00:10 / 03:55) : les travaux sont appelés directement par les tests.

## Pour l'audit Opus (obligatoire)
Contrôle d'accès (matrice, `ActPermissions`, session console bornée), audit des lectures (fail-open après réponse, `changes` audité par session), vérification des signatures (`JournalVerifier`, rapport TV : clé de l'anneau, signature, appareil visé), aucun secret journalisé ni stocké (`SourceRulesTest`, `ReportTest`), absence d'écriture dans `lic_*`. Points d'attention : dépôt de journal par `SUPPORT` (non sensible : le lot est signé), garde-fou « jamais de code d'appareil entier » dans `EventLog.append`, limiteur en mémoire (une instance).
