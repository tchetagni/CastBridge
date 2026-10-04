STATUT: TERMINÉ (audit Opus obligatoire à lancer : transactions, verrous, vérification d'activation, signature)
CAHIER: sonnet-w22-02 · MODÈLE: sonnet · BRANCHE: worktree-agent-afab6c875088f3962 (depuis integration/agents HEAD 3484fbb1 ; le worktree d'origine pointait sur un vieux commit, je l'ai remis sur ce HEAD avant tout) · COMMIT: voir `git log -1`
JETONS: inconnu

## Rouge d'abord (R1)
1. Tous les tests écrits avant la moindre classe : `cd backend && mvn -o -q test -Dtest='castbridge.server.wallet.*Test'` → 84 × `ERROR … cannot find symbol` / `package EditionReader does not exist` (ex. `EditionReaderTest.java:[30,12] cannot find symbol`, `WalletTestBase.java:[33,44] package WalletModuleConfig does not exist`).
2. Rouge PAR ASSERTION sur les deux classes testables sans Spring (pièces provisoires qui compilent mais ne font rien) : `mvn -o -q test -Dtest=EditionReaderTest,SnapshotSignerTest` →
```
EditionReaderTest.trialOf30DaysGivesATrialSpanAndTheIdentity:44 expected: <3QGG-JP78-MJ6Y-9WPW> but was: <null>
EditionReaderTest.anotherTvsActivationIsRefused:97 expected: <[OTHER_TV]> but was: <[]>
EditionReaderTest.revokedKeyAndRevokedSeatAreRefused:121 expected: <[REVOKED]> but was: <[]>
EditionReaderTest.activationIssuedMoreThan24HoursInTheFutureIsAClockDoubt:142 expected: <true> but was: <false>
SnapshotSignerTest.javaProducesTheKotlinGoldenTokensByteForByte:46 snapshot-prod ==> expected: <cbw1.eyJraWQi…> but was: <>
SnapshotSignerTest.twoKidsAreAcceptedForRotation:84 expected: not equal but was: <>
Tests run: 14, Failures: 12, Errors: 1 (EditionReaderTest) ; Tests run: 6, Failures: 3, Errors: 3 (SnapshotSignerTest)
```
Les tests Spring (grand livre JDBC, `sync`, administration) n'ont de rouge que par compilation (1) : leurs classes n'avaient pas de pièce provisoire.
3. VERT ensuite. Un défaut réel attrapé par un test avant le vert : le vérificateur Java de `cbw1` acceptait `extra-field`, `decimal-number` (vecteurs « refusés » de `tools/wallet/wallet-vectors.json`) ; lecture stricte ajoutée (clés exactes, base64url canonique, réécriture compacte identique, entiers seulement).

## Ce qui a tourné (hors ligne, H2 en mode MySQL, mêmes migrations Flyway)
- `cd backend && mvn -o test` complet : **327 tests, 0 échec, 4 ignorés** (dont mes 2 tests MySQL) ; ≈ 70 s. Non instable.
- Mes tests : EditionReaderTest 15, SnapshotSignerTest 6, GrantServiceTest 14, JdbcLedgerTest 5, WalletApiTest 13, WalletLimitsTest 1, WalletNoKeyTest 1, WalletDisabledTest 1, WalletMigrationTest 3, WalletMySqlContainerTest 2 (ignorés) = **59 exécutés + 2 ignorés**.
- Oracle : 2 000 suites aléatoires (≈ 15 000 opérations dont rejeux, conflits d'empreinte, découverts, blocages clos, règlements truqués) rejouées sur `JdbcLedger` ET `MemoryLedger` : même verdict à chaque opération, mêmes soldes, mêmes blocages, comptes système en variation.
- Concurrence : 16 fils × 500 opérations croisées (transferts, blocage+rendu, deux blocages+règlement, conversions, rejeux) : aucun négatif, Σ = 0, tous blocages clos, I-8 (solde = Σ écritures), **0 réessai** (donc aucun interblocage) ; 16 fils postant la même opération : une seule pose réelle.
- Parité Kotlin ↔ Java : `SnapshotSigner` reproduit OCTET POUR OCTET les 3 `cbw1` dorés de `tools/wallet/wallet-vectors.json` (clé de test, Ed25519 déterministe) et refuse les vecteurs refusés (signature, clé inconnue, illisible).
- Migration : V63 sur base vide ET sur base arrêtée à V61 (puis migrée), aucune table existante modifiée (colonnes de `lic_*`/`device` inchangées), `rollback-V63.sql` retire tout, laisse `lic_license`, et la migration se rejoue ensuite.

## Mutations tentées (appliquées puis retirées, fichiers restaurés et comparés)
| Mutation | Résultat |
|---|---|
| retirer `FOR UPDATE` du verrou de solde | **TUÉE** : `I-1 Σ = 0 NDEM expected 0 but was 16845` (concurrence) |
| rejeu accepté même si l'empreinte diffère | **TUÉE** : l'oracle diverge (IDEM_CONFLICT attendu) |
| ordre des verrous incohérent | **TUÉE** (après l'ajout du compteur `retries()` et de l'assertion « 0 réessai ») ; ÉCHAPPAIT d'abord : les interblocages H2 étaient absorbés par le réessai borné |
| ignorer l'état suspendu/révoqué de la licence | **TUÉE** par 3 tests (4 000 au lieu de 2 000, etc.) |
| ne plus vérifier que l'activation est celle de cette TV | **TUÉE** par 2 tests (`EditionReaderTest`, `WalletApiTest`) |
| signer `cbw1` sans le domaine | **TUÉE** par 3 tests (parité dorée, rotation, falsification) |
| retirer le `CHECK (solde >= 0)` de V63 | **TUÉE** : `aRefusalFromTheDatabaseRollsBackEverything` ne voit plus de refus de la base |
Non tentées : arrondi des frais et formules de conversion (cœur de w22-01, interdit ici).

## Fichiers
Nouveaux : `backend/src/main/resources/db/migration/V63__wallet.sql` ; `backend/src/main/java/castbridge/server/wallet/{EditionReader,GrantService,JdbcLedger,JdbcLicenseFacts,LicenseFacts,SnapshotSigner,SyncContributor,WalletAdminController,WalletErrors,WalletHistoryController,WalletKey,WalletModuleConfig,WalletPolicyService,WalletProperties,WalletRepository,WalletSyncController}.java` ; `backend/src/test/java/castbridge/server/wallet/{Acts,EditionReaderTest,GrantServiceTest,JdbcLedgerTest,LedgerSuites,SnapshotSignerTest,WalletApiTest,WalletDisabledTest,WalletLimitsTest,WalletMigrationTest,WalletMySqlContainerTest,WalletNoKeyTest,WalletTestBase}.java` ; `tools/wallet/rollback-V63.sql` ; ce rapport. Modifiés : `backend/src/main/resources/application.yml` (bloc `castbridge.wallet.*`, zone additive) ; ligne w22-02 de l'index. Rien dans `wallet/core`, `licenses/**`, `play/**`, Android.
Écarts de liste du cahier : aucune classe hors liste. `WalletProperties` et `WalletModuleConfig` (liste) portent aussi l'interrupteur `@WalletModuleConfig.Enabled` posé sur chaque bean du module (éteint ⇒ 404).

## Choix et écarts à relire à l'audit
1. **Numéro de migration** : le plus haut existant est V61 ; j'ai créé `V63__wallet.sql` (nom du cahier, pas `V63__wallet_ledger.sql`). V62 n'existe pas (réservée à W21). Flyway tolère le trou (V52 → V60 existe déjà). **Risque** : `backend/src/main/resources/db/migration/README.md` dit « jamais de trou volontaire », et sans `spring.flyway.out-of-order=true`, si V63 est déployée AVANT V62, la validation de Flyway refusera ensuite V62 (« migration résolue non appliquée »). Je n'ai renuméroté aucune autre migration ni touché au README. À trancher par l'architecte : fusionner W21 d'abord, ou renommer V63 en « plus haut + 1 » au moment de la fusion.
2. **`EditionReader` / `LicenseFacts`** comme le cahier révisé : `cbx1` = identité + essai (durée du droit `usage`, sinon 30 j implicites ; 1 ms à 365 j, sinon refus) ; droit `super` ⇒ SUPER (aucune tranche) ; production = **licence** lue sur les tables V51 (`lic_seat.state='ACTIVE'`, `subject='tv'`, `lic_license.kind='PAID'`). Module des licences éteint (`castbridge.licenses.enabled`) ou aucune licence ⇒ `LICENSE_PENDING` puis rattrapage rétroactif depuis `start_at`. Licence `kind='TRIAL'` ignorée (l'essai se lit dans `cbx1`).
3. **Date de suspension/révocation** : le module des licences n'a pas d'historique d'état lisible simplement ; je prends `lic_license.updated_at` comme date de la mesure (intervalle `[start_at, updated_at)`). Si la ligne est modifiée après la révocation, la date glisse (surestimation possible d'une tranche). Une licence suspendue puis réactivée récupère les périodes manquées. À confirmer.
4. **Ancre figée** à la première lecture (`UPDATE … WHERE anchor_at IS NULL`) : les clés `p<k>` en dépendent. Une clé d'essai plus ancienne présentée plus tard ne la déplace pas.
5. **`cbw1.ed`** : le lecteur Kotlin de w22-03 n'accepte que TRIAL, PROD, UNLIMITED, NONE ; le cahier parle de GRACE et PENDING. J'émets GRÂCE ⇒ `PROD`, SUPER ⇒ `UNLIMITED`, production sans licence ⇒ `NONE`, et je rends la nuance hors signature (`edition.license`, `edition.grace`, `notices`). Pour signer GRACE/PENDING il faudrait étendre `Snapshot.EDITIONS` côté Kotlin (w22-03).
6. **`sync`** : `deviceCode` obligatoire dans le corps (comme le ticket de jeu) ; aucun compte n'est ouvert sans au moins une activation acceptée (409 `ACTIVATE`, ou `CLOCK` si une activation est datée de plus de 24 h dans le futur) ; identité déjà ouverte + activation absente/refusée ⇒ lecture seule, aucune tranche ; autre `deviceId` ⇒ lecture permise, pas de re-liaison, notice `BOUND_OTHER_TV`. Historique : lignes DISPO cumulées par transaction ; `GET /history` = identité liée à l'appareil.
7. **Administration** : routes sous `/api/v1/admin/wallet/{grant,reconcile}` (le jeton d'administration est le facteur fort des actions sensibles, comme `Actor.token()` ; `/admin/**` est l'interface web à session). Motif obligatoire, consigné (`wallet_txn.reason`, colonne ajoutée à la liste du § 3.7), acteur `admin:admin-token`. Réconciliation : I-1 (soldes ET écritures), I-3 (masse = −Σ système, `SYS:FEE ≥ 0`, `SYS:POT = 0`, BLOQUE = Σ blocages ouverts), I-8 ; la relation de conversion « NDEM = −taux × MBOKO » est RAPPORTÉE sans faire échouer `ok` (elle ne vaut exactement que si le taux n'a jamais changé).
8. **Schéma** : `ascii_bin` sur les clés textuelles (`idem_key`, `eid`, nonces… sensibles à la casse sous MySQL, `utf8mb4_0900_ai_ci` les confondrait) ; H2 accepte la clause. `wallet_policy.value` nommée `val` (mot réservé de H2). `wallet_escrow.eid` = la clé d'idempotence du blocage (jusqu'à 200 caractères, pas CHAR(22)) ; `per`/`k` NULL (le grand livre ne connaît que le montant ; w22-05 les renseignera).
9. **Isolation** : celle par défaut de la base (InnoDB REPEATABLE READ ; H2 READ COMMITTED). Les verrous `FOR UPDATE` lisent la dernière valeur validée ; la lecture d'idempotence se fait APRÈS la prise des verrous. `JdbcLedger.post` ne se compose pas dans une transaction extérieure (le réessai borné à 3 serait faux).
10. **Horloge** : `WalletClock` (bean) est le seul point d'entrée du temps ; `freezeAt` n'est appelé que par les tests (aucune route).
11. `postWithoutBalanceCheck` (visibilité paquet) existe pour prouver que le CHECK de la base refuse aussi : tests seulement.

## NON VÉRIFIÉ / À FAIRE
- **Rien n'a tourné contre MySQL** : le démon Docker n'est pas lancé ici, `WalletMySqlContainerTest` (oracle + concurrence sur MySQL 8.4) est IGNORÉ. Le cahier exige ce résultat AVANT l'audit Opus : à lancer sur une machine avec Docker (`mvn -o test -Dtest=WalletMySqlContainerTest`). Les verrous `FOR UPDATE`, `ascii_bin`, `CURRENT_TIMESTAMP(6)` dans le semis de `wallet_policy` et la prise en charge de `GROUP BY … HAVING` de l'historique n'ont été prouvés que sur H2.
- Aucun déploiement, aucun accès serveur, aucune vraie clé : clés de test générées par les tests (TEST SEULEMENT) ; `NoSecretsTest`-style : aucune clé dans les fichiers.
- Module non exercé avec le module des licences ALLUMÉ de bout en bout (les licences des tests sont de vraies lignes V51 lues par `JdbcLicenseFacts(jdbc, true)` ; le drapeau `castbridge.licenses.enabled=false` est testé en unité).
- Contributeurs w22-05 / w22-06 : seule l'interface `SyncContributor` est publiée ; un contributeur de test prouve l'appel.
- Pas de réessai ni de débit pour les routes d'administration ; les débits (30/min/identité, 600/min global, réglables par `castbridge.wallet.writes-per-minute-*`) ne couvrent que `sync`.
- Routes `convert`, `escrow`, `settle`, `transfer`, bons : hors cahier (w22-05, w22-06).

FICHIERS: voir ci-dessus
SYMBIOSE: cap=aucune (serveur) · proto=nouvelle route `POST /api/v1/wallet/sync` (corps `{deviceCode, activations[]}`, réponse `{snapshot, history, contributions, notices, edition}`), `GET /history`, `GET /policy`, administration `/api/v1/admin/wallet/*` · reason=motifs de `WalletReason` dans `details[0]` + `message` français ; `LICENSE_PENDING` ajouté côté serveur (texte « Licence en attente d'enregistrement », pas encore dans `WalletReason` ni dans le Kotlin) · deux écrans=sans objet
AUTOCONTRÔLE: [x] zone [x] porte (suite complète verte) [x] secrets [x] dépendances (aucune) [x] FR [x] un commit
