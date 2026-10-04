# w22-02 — Serveur : migration du grand livre (V62, ex-V63), persistance atomique, lecture de l'édition dans l'activation `cbx1`, tranches paresseuses, `sync` et historique, instantané signé `cbw1`, politique et interrupteurs d'exploitation
<!-- routage architecte 2026-10-04 (W22, niveau 1) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (transactions, verrous, vérification d'activation, signature) · statut : **ATTEND w22-01**
> **Groupe : W22-N1** (ordre 2) · prérequis : w22-01 fusionné · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.**'`
> **Jauge : ≈ 600 k jetons entrée / 30 k sortie** (effort L, ≈ 2,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 1.2, § 3.1, § 3.2, § 3.4 `cbw1`, § 3.6, § 3.7). Branche `claude/w22-02-serveur-grand-livre`. Rapport : `docs/agent-reports/sonnet-w22-02.md`.

## Précision du propriétaire (2026-10-04) : clé d'activation ≠ licence — impact sur ce cahier
1. « Les licences sont différentes des clés d'activation (Essai & Production). Les licences sont gérées exclusivement par le serveur pour les clés activées de production » (conception § 1.2).
2. `EditionReader` ne rend plus que l'**identité** et l'**édition ESSAI** (durée lue dans `cbx1`) ; une activation de production sert seulement à prouver l'identité.
3. Nouveau `LicenseFacts` (interface, lecture seule) : pour un code d'appareil, la licence du poste `lic_seat.state = ACTIVE` ⇒ `lic_license` (`state`, `start_at`, `end_at`, `grace_days`) ; implémentation JDBC sur les tables V51 existantes, **sans** modifier le module des licences ni ses tables.
4. PRODUCTION à durée / ILLIMITÉE / GRÂCE / suspendue / révoquée / échue = **état de la licence** (horloge du serveur) ; `end_at` NULL ⇒ ILLIMITÉE ; N = `ceil((end_at − start_at)/30 j)`.
5. Module des licences éteint ou licence absente ⇒ aucune tranche de production, motif `LICENSE_PENDING` (« Licence en attente d'enregistrement ») ; tranches rétroactives depuis `start_at` dès qu'elle apparaît.
6. Ancre = plus ancien de (début de la première clé d'essai, `start_at` de la première licence).
7. Ouverture illimitée : première licence `end_at` NULL et `ACTIVE` vue pour l'identité, une fois pour toujours.
8. Révocation/suspension = état de licence (production) ou révocation de la clé `cbx1` (essai) : les tranches futures cessent.
9. `cbw1.ed` reflète la licence (`PROD`, `UNLIMITED`, `GRACE`, `PENDING`) ; `StakeRules` reçoit l'édition issue de la licence (utilisée par w22-05 au blocage MBOKO).
10. Tests ajoutés : licence `ACTIVE` 90 j ⇒ 3 tranches ; licence révoquée au mois 2 ⇒ arrêt ; activation de production sans licence ⇒ `LICENSE_PENDING` puis rattrapage ; licence sans fin ⇒ ouverture une fois ; essai toujours lu dans `cbx1`.

## Objectif (autonome)
Tenir le grand livre de w22-01 dans MySQL (`castbridge-db`) avec les mêmes invariants, sous un module **éteint par défaut dans le code** (`castbridge.wallet.enabled`, allumé à la livraison par `CASTBRIDGE_WALLET_ENABLED=1`), apprendre l'édition et l'identité de la TV **depuis ses activations `cbx1`** (l'API ne connaît pas le cœur des licences : `backend/src/main/java/castbridge/server/play/PlayTicketService.java:24-25` ; module des licences éteint : `backend/src/main/resources/application.yml:117`), matérialiser les tranches dues à chaque contact et rendre un instantané signé.

## Fichiers possédés
- **Nouveaux** : `backend/src/main/resources/db/migration/V62__wallet.sql` (renumérotée depuis V63 avant tout déploiement ; aucun numéro n'est réservé : vérifier le plus haut numéro au moment de la fusion, prendre « plus haut + 1 » et le dire au rapport) ; `backend/src/main/java/castbridge/server/wallet/{WalletProperties,WalletModuleConfig,JdbcLedger,WalletRepository,EditionReader,LicenseFacts,JdbcLicenseFacts,GrantService,WalletKey,SnapshotSigner,WalletPolicyService,SyncContributor,WalletSyncController,WalletHistoryController,WalletAdminController,WalletErrors}.java` ; tests `backend/src/test/java/castbridge/server/wallet/**` ; `tools/wallet/rollback-V62.sql` (DROP, avec en-tête « seulement si aucune écriture réelle »).
- **Zone additive** : `backend/src/main/resources/application.yml` (bloc `castbridge.wallet.*`, défauts éteints pour le module, interrupteurs **actifs**).
- **Interdit** : `backend/src/main/java/castbridge/server/wallet/core/**` (w22-01 : consommé tel quel), `licenses/**` (lecture seule ; si `EnvelopeVerifier`/`WireActivation` ne s'instancient pas sans le module des licences, **copier** le strict nécessaire dans `wallet/` et le dire), `play/**`, tout fichier Android.

## Spécification
1. Tables du § 3.7 de la conception (dont `wallet_policy` avec bornes) ; `CHECK` de non-négativité pour les poches de joueur ; index `wallet_entry(account_id, id)` ; aucune table existante modifiée.
2. `JdbcLedger implements Ledger` : une transaction SQL par `post` ; verrous `SELECT … FOR UPDATE` sur `wallet_balance` **dans l'ordre croissant des `account_id`** ; insertion `wallet_txn` (clé `UNIQUE(idem_key)`, empreinte comparée sur doublon) puis `wallet_entry` puis mise à jour des soldes ; toute violation ⇒ annulation totale ; même sémantique que `MemoryLedger` (oracle).
3. `EditionReader` (identité + essai ; la production passe par `LicenseFacts`, voir plus haut) : vérifie ≤ 4 activations (≤ 8 192 caractères) avec les clés publiques de `castbridge.wallet.trusted-keys` (même format que `CASTBRIDGE_PLAY_TRUSTED_KEYS`) et une liste de révocations **facultative** (`castbridge.wallet.revocations-file`) ; refuse une activation d'une autre TV ; rend `identité` + `EditionSpan`s (ESSAI / PRODUCTION à durée / ILLIMITÉE / SUPER) avec l'horloge du serveur ; activation émise > 24 h dans le futur ⇒ `CLOCK`.
4. `POST /api/v1/wallet/sync` (jeton d'appareil existant, comme `PlayTicketController`) : ouvre le compte (`wallet_identity`, liaison à l'`deviceId` API, ancre) ; **autre `deviceId`** ⇒ lecture permise, sorties refusées plus tard (`BOUND_OTHER_TV`) ; calcule `GrantSchedule.due` et poste chaque tranche (idempotent) ; appelle les `SyncContributor` (interface publiée ici, implémentée par w22-05 et w22-06) ; rend `{snapshot: cbw1, history: 20 lignes, contributions}`.
5. `SnapshotSigner` : `cbw1` exactement au format du § 3.4 (domaine `castbridge-wallet-snapshot-v1`, `kid`, champs `id, ed, n, nb, m, mb, seq, at, flags`) ; clé « portefeuille » Ed25519 lue d'un fichier secret (`castbridge.wallet.key-file`, absent ⇒ module en 503 « Portefeuille indisponible ») ; deux `kid` acceptés pour la rotation.
6. `GET /api/v1/wallet/history?before=` (50 lignes, libellés français, contrepartie masquée « TV …4F2Q ») ; `GET /api/v1/wallet/policy`.
7. `POST /admin/wallet/grant` (TOTP comme les autres actions d'administration existantes, motif obligatoire) et `GET /admin/wallet/reconcile` (I-1, I-3, I-8 : soldes = Σ écritures ; rapport JSON).
8. Débits : 30 écritures / min / identité, 600 / min global (réglables).

## Critères d'acceptation (mutations au rapport)
- Oracle : 2 000 suites aléatoires rejouées sur `JdbcLedger` **et** `MemoryLedger` ⇒ mêmes soldes, mêmes refus (mutation : retirer le `FOR UPDATE` ⇒ le test de concurrence échoue).
- Concurrence : 16 fils × 500 opérations croisées (transferts A↔B, blocages) ⇒ aucun négatif, Σ = 0, aucun interblocage non rattrapé (réessai borné à 3).
- Édition : activations de test (clés des fixtures existantes) : essai 30 j, production 90 j, production sans durée, `super`, autre TV, révoquée ⇒ éditions et refus attendus ; `sync` répété ⇒ tranches une seule fois ; ouverture illimitée une seule fois même après une nouvelle clé illimitée.
- `cbw1` vérifié par le vérificateur de test Java ; `seq` croissant ; module éteint ⇒ routes 404 ; clé absente ⇒ 503.
- Migration : Flyway applique `V62` sur une base vide et sur une base au niveau V61 ; `rollback-V62.sql` la retire (test si l'outillage le permet, sinon procédure au rapport).
- **Réutiliser l'outillage de test existant** : H2 en mode MySQL avec les mêmes migrations Flyway (`backend/src/test/resources/application-test.yml`, `ApiTestBase.java`) pour tous les tests ; **et** le test de concurrence + l'oracle rejoués contre un vrai MySQL 8.4 par `backend/src/test/java/castbridge/server/MySqlContainerTest.java` (Testcontainers, sauté sans Docker : le rapport dit s'il a tourné). Les verrous `FOR UPDATE` de H2 ne prouvent pas ceux de MySQL : le résultat MySQL est exigé avant l'audit Opus. Aucune dépendance nouvelle.

## À ne pas faire
- Créditer quoi que ce soit sur la parole de la TV ; journaliser une activation ou un jeton d'appareil en clair ; toucher une table existante.
