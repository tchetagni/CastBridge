# w22-01 — Cœur pur Java du grand livre NDEM/MBOKO : comptes, transactions à double entrée, attributions par édition, conversion dans les deux sens, transfert, blocage et règlement, invariants prouvés par tests de propriétés
<!-- routage architecte 2026-10-04 (W22, niveau 1 : POC de faisabilité) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (conservation, arrondis, idempotence) · statut : **PRÊT** (serveur, pur : aucun Spring, aucune base)
> **Groupe : W22-N1** (ordre 1, en parallèle de w22-03) · prérequis : `integration/agents` ≥ `5696385e` · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.core.**'` + `:core:test --tests 'castbridge.core.quiz.PotVectorsTest'` (par `tools/agents/gradle-lock.sh`)
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 1.2, § 1.3, § 1.4, § 3.1, § 3.3 règles, § 5.3 R-E1/R-E2/R-E7). Branche `claude/w22-01-grand-livre-coeur`. Rapport : `docs/agent-reports/sonnet-w22-01.md`.

## Objectif (autonome)
Le serveur (`backend/`, Maven, Java 21) **ne dépend pas** du cœur Kotlin (`backend/pom.xml`) : le grand livre, tenu par l'API, a besoin d'un **cœur pur Java** qui décide de tout ce qui touche à la valeur, testable sans base. Livrer ce cœur et prouver ses invariants par **tests de propriétés** (générateur aléatoire à graine fixée, **sans dépendance nouvelle**).

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/wallet/core/{Currency,Pocket,AccountRef,Entry,Txn,TxnKind,Ledger,MemoryLedger,LedgerException,WalletReason,Edition,EditionSpan,GrantSchedule,Conversion,WalletPolicy,PotSplit,Settlement,StakeRules}.java` ; tests `backend/src/test/java/castbridge/server/wallet/core/{LedgerPropertyTest,GrantScheduleTest,ConversionTest,SettlementTest,PotSplitVectorsTest,StakeRulesTest}.java` ; `tools/wallet/ledger-vectors.json` (`castbridge-ledger-vectors-v1`) ; `android/core/src/test/kotlin/castbridge/core/quiz/PotVectorsTest.kt` (lit les mêmes vecteurs et les vérifie contre `Pot.split` de `android/core/src/main/kotlin/castbridge/core/quiz/Wallet.kt`).
- **Interdit** : tout autre fichier ; aucune modification de `Wallet.kt`.

## Spécification
1. `Currency { NDEM, MBOKO }` ; `Pocket { DISPO, BLOQUE, BONUS, SYS }` ; `AccountRef(holder, currency, pocket)` ; titulaires système `SYS:GRANT`, `SYS:VOUCHER`, `SYS:REWARD`, `SYS:CONVERT`, `SYS:FEE`, `SYS:ADJUST`, `SYS:POT` (poche `SYS`, solde signé libre) ; identité = `XXXX-XXXX-XXXX-XXXX` (validée par une expression régulière).
2. `Txn(kind, idemKey, entries, contentSha)` : **pour chaque monnaie, Σ montants = 0** (sinon `LedgerException(UNBALANCED)`) ; `contentSha` = SHA-256 d'une forme canonique (genre, écritures triées, références). `TxnKind` : `GRANT, CONVERT, TRANSFER, ESCROW_LOCK, SETTLE, ESCROW_REFUND, VOUCHER, ADJUST`.
3. `Ledger` (interface) : `post(Txn)` ⇒ `Posted(replayed: boolean)` ; même `idemKey` + même `contentSha` ⇒ `replayed=true`, rien ne change ; même clé, autre contenu ⇒ `IDEM_CONFLICT` ; tout compte de poche ≠ `SYS` qui deviendrait négatif ⇒ `INSUFFICIENT`, rien n'est écrit (atomique). `MemoryLedger` = implémentation de référence (tests, et oracle des tests de w22-02).
4. Constructeurs de transactions (statiques, purs) : `grant(id, cur, amount, key)`, `convert(id, dir N2M|M2N, q, policy, key)` (formules du § 3.1 : `rate·q`, `f = ceil(rate·q·fee)`, `fee = reverseFeeBp/10 000`), `transfer(src, dst, cur, amount, key)` (src ≠ dst), `lock(id, cur, per, k, eid)`, `settle(rid, lines, cur)`, `refund(eid, …)`, `voucher(id, cur, amount, nonce)`, `adjust(…)`.
5. `WalletPolicy` : `rate` (1..1 000 000, défaut 1 000), `reverseFeeBp` (0..2 000, défaut 0 ; **jamais négatif** : un sens inverse plus favorable est refusé), bornes de mise (NDEM 1..1 000, MBOKO 1..100), plafond simple de transfert (10 000 NDEM, 100 MBOKO / jour / identité) ; valeurs hors bornes ⇒ exception française.
6. `Edition { NONE, TRIAL, PRODUCTION, UNLIMITED, SUPER }` et `EditionSpan(edition, start, endExclusive|null)` ; `GrantSchedule.due(anchor, spans, alreadyGranted, now)` ⇒ liste des tranches dues (`grant:<id>:<cur>:p<k>`, `grant:<id>:open-unlimited`) selon le § 1.2 : périodes de 30 j depuis l'ancre, une tranche par période, meilleure édition à l'instant de début (UNLIMITED > PRODUCTION > TRIAL), montants ESSAI 100 NDEM, PRODUCTION 1 000 NDEM + 10 MBOKO, UNLIMITED 1 000 NDEM + 10 MBOKO + ouverture 5 000 NDEM + 50 MBOKO une fois, SUPER et NONE : rien ; les montants viennent de `WalletPolicy` (défauts ci-dessus), jamais en dur ailleurs.
7. `PotSplit.split(pot, scores)` : **port exact** de `Pot.split` (`Wallet.kt:54-85`) ; `Settlement.compute(cur, per, escrows, seatScores, kind END|ABORT)` ⇒ lignes `(eid, id, used, pay)` : `used = per × min(k, sièges présents)`, `pay` = somme des parts des sièges de ce blocage ; ABORT ⇒ `pay = used` ; `Settlement.check(lines, escrows)` ⇒ Σ pay = Σ used, `used ≤ amount`, chaque `eid` une fois, aucun `eid` inconnu.
8. `StakeRules.mayStake(edition, cur)` : MBOKO ⇒ PRODUCTION, UNLIMITED (et GRÂCE, passée en paramètre) ; NDEM ⇒ toute édition sauf NONE.
9. `WalletReason` : motifs fermés du § 7.6 de la conception avec leur texte français.

## Étapes
1. **Rouge** (sortie collée) : `LedgerPropertyTest.conservationHoldsForRandomSequences` (classes absentes).
2. Implémenter ; écrire `ledger-vectors.json` (≥ 12 cas `Pot.split`, ≥ 10 cas d'échéancier, ≥ 6 cas de conversion dont frais 0, 150, 2 000 bp) ; **vert**.

## Critères d'acceptation (mutations à appliquer puis retirer, résultat au rapport)
- `LedgerPropertyTest` : 10 000 suites aléatoires (graines 1..10 000) de 1..200 opérations mêlant les 8 genres, rejeux et clés en conflit : après chaque opération, **I-1** Σ par monnaie = 0, **I-2** aucun compte de joueur négatif, **I-3** `SYS:CONVERT(NDEM) = −rate × SYS:CONVERT(MBOKO)` à `fee` = 0 et masse en circulation = −Σ comptes système, **I-4** rejouer une opération ne change rien, **I-6** un blocage n'est réglé ou rendu qu'une fois. Mutations : retirer la vérification de négatif ; accepter `IDEM_CONFLICT` comme rejeu ; arrondir `f` vers le bas ⇒ un test échoue.
- `ConversionTest` : N→M puis M→N de la même quantité à `fee` = 0 ⇒ tous les comptes identiques au départ ; à `fee` > 0 ⇒ le joueur perd exactement `f`, `SYS:FEE` gagne `f` ; jamais de gain (propriété sur 1 000 tirages) ; `reverseFeeBp = −1` refusé ; N→M avec moins de `rate` NDEM ⇒ `INSUFFICIENT`.
- `GrantScheduleTest` : essai 7 j ⇒ 1 tranche ; production 90 j ⇒ 3 ; 365 j ⇒ 13 ; illimitée ⇒ ouverture + 1 tranche par période ; clés superposées ⇒ jamais deux tranches par période ; essai puis production ⇒ montants de production dès la première période couverte ; TV synchronisée après 3 mois hors ligne ⇒ toutes les tranches dues, une seule fois ; SUPER ⇒ rien ; révocation à `t` ⇒ aucune tranche dont la période commence après `t`.
- `SettlementTest` : Σ pay = Σ used pour 5 000 tirages (2..8 sièges, 1..4 blocages, ex æquo, zéros) ; ABORT rend tout ; `check` refuse un `eid` inconnu, un doublon, Σ ≠.
- `PotSplitVectorsTest` (Java) **et** `PotVectorsTest` (Kotlin) passent sur les mêmes vecteurs.

## À ne pas faire
- Ajouter une dépendance (jqwik, etc.) ; toucher Spring, la base, `Wallet.kt` ; coder un montant d'attribution hors de `WalletPolicy`.
