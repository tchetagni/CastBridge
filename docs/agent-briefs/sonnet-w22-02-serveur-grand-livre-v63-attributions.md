# w22-02 — Serveur : migration V63 du grand livre, persistance atomique, lecture de l'édition dans l'activation `cbx1`, tranches paresseuses, `sync` et historique, instantané signé `cbw1`, politique et interrupteurs d'exploitation
<!-- routage architecte 2026-10-04 (W22, niveau 1) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (transactions, verrous, vérification d'activation, signature) · statut : **ATTEND w22-01**
> **Groupe : W22-N1** (ordre 2) · prérequis : w22-01 fusionné · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.**'`
> **Jauge : ≈ 600 k jetons entrée / 30 k sortie** (effort L, ≈ 2,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 1.2, § 3.1, § 3.2, § 3.4 `cbw1`, § 3.6, § 3.7). Branche `claude/w22-02-serveur-grand-livre`. Rapport : `docs/agent-reports/sonnet-w22-02.md`.

## Objectif (autonome)
Tenir le grand livre de w22-01 dans MySQL (`castbridge-db`) avec les mêmes invariants, sous un module **éteint par défaut dans le code** (`castbridge.wallet.enabled`, allumé à la livraison par `CASTBRIDGE_WALLET_ENABLED=1`), apprendre l'édition et l'identité de la TV **depuis ses activations `cbx1`** (l'API ne connaît pas le cœur des licences : `backend/src/main/java/castbridge/server/play/PlayTicketService.java:24-25` ; module des licences éteint : `backend/src/main/resources/application.yml:117`), matérialiser les tranches dues à chaque contact et rendre un instantané signé.

## Fichiers possédés
- **Nouveaux** : `backend/src/main/resources/db/migration/V63__wallet.sql` (**vérifier** le plus haut numéro au moment de la fusion : V62 est réservé par W21 ; prendre « plus haut + 1 » et le dire au rapport) ; `backend/src/main/java/castbridge/server/wallet/{WalletProperties,WalletModuleConfig,JdbcLedger,WalletRepository,EditionReader,GrantService,WalletKey,SnapshotSigner,WalletPolicyService,SyncContributor,WalletSyncController,WalletHistoryController,WalletAdminController,WalletErrors}.java` ; tests `backend/src/test/java/castbridge/server/wallet/**` ; `tools/wallet/rollback-V63.sql` (DROP, avec en-tête « seulement si aucune écriture réelle »).
- **Zone additive** : `backend/src/main/resources/application.yml` (bloc `castbridge.wallet.*`, défauts éteints pour le module, interrupteurs **actifs**).
- **Interdit** : `backend/src/main/java/castbridge/server/wallet/core/**` (w22-01 : consommé tel quel), `licenses/**` (lecture seule ; si `EnvelopeVerifier`/`WireActivation` ne s'instancient pas sans le module des licences, **copier** le strict nécessaire dans `wallet/` et le dire), `play/**`, tout fichier Android.

## Spécification
1. Tables du § 3.7 de la conception (dont `wallet_policy` avec bornes) ; `CHECK` de non-négativité pour les poches de joueur ; index `wallet_entry(account_id, id)` ; aucune table existante modifiée.
2. `JdbcLedger implements Ledger` : une transaction SQL par `post` ; verrous `SELECT … FOR UPDATE` sur `wallet_balance` **dans l'ordre croissant des `account_id`** ; insertion `wallet_txn` (clé `UNIQUE(idem_key)`, empreinte comparée sur doublon) puis `wallet_entry` puis mise à jour des soldes ; toute violation ⇒ annulation totale ; même sémantique que `MemoryLedger` (oracle).
3. `EditionReader` : vérifie ≤ 4 activations (≤ 8 192 caractères) avec les clés publiques de `castbridge.wallet.trusted-keys` (même format que `CASTBRIDGE_PLAY_TRUSTED_KEYS`) et une liste de révocations **facultative** (`castbridge.wallet.revocations-file`) ; refuse une activation d'une autre TV ; rend `identité` + `EditionSpan`s (ESSAI / PRODUCTION à durée / ILLIMITÉE / SUPER) avec l'horloge du serveur ; activation émise > 24 h dans le futur ⇒ `CLOCK`.
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
- Migration : Flyway applique `V63` sur une base vide et sur une base au niveau V61/V62 ; `rollback-V63.sql` la retire (test si l'outillage le permet, sinon procédure au rapport).
- **Réutiliser l'outillage de test existant** : H2 en mode MySQL avec les mêmes migrations Flyway (`backend/src/test/resources/application-test.yml`, `ApiTestBase.java`) pour tous les tests ; **et** le test de concurrence + l'oracle rejoués contre un vrai MySQL 8.4 par `backend/src/test/java/castbridge/server/MySqlContainerTest.java` (Testcontainers, sauté sans Docker : le rapport dit s'il a tourné). Les verrous `FOR UPDATE` de H2 ne prouvent pas ceux de MySQL : le résultat MySQL est exigé avant l'audit Opus. Aucune dépendance nouvelle.

## À ne pas faire
- Créditer quoi que ce soit sur la parole de la TV ; journaliser une activation ou un jeton d'appareil en clair ; toucher une table existante.
