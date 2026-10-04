STATUT: TERMINÉ (audit Opus obligatoire à suivre : conservation, arrondis, idempotence)
CAHIER: sonnet-w22-01 · MODÈLE: sonnet · BRANCHE: claude/w22-01-grand-livre-coeur (depuis integration/agents, HEAD contenait 66be622a) · COMMIT: voir `git log -1`
JETONS: inconnu

## Rouge d'abord (R1)
`LedgerPropertyTest.conservationHoldsForRandomSequences` écrit avant toute classe, `cd backend && mvn -o -q test -Dtest='castbridge.server.wallet.core.LedgerPropertyTest'` :
```
[ERROR] COMPILATION ERROR :
[ERROR] .../LedgerPropertyTest.java:[10,9] cannot find symbol
  symbol:   class MemoryLedger
[ERROR] .../LedgerPropertyTest.java:[11,54] cannot find symbol
  symbol:   variable Currency
[ERROR] .../LedgerPropertyTest.java:[11,21] cannot find symbol
  symbol:   variable Txn
```
Puis vert après implémentation (voir ci-dessous). Un défaut réel a été attrapé par le test de propriétés au premier passage : comparaison de `Long` par référence dans `MemoryLedger` (rendu d'un blocage refusé à tort), corrigée.

## Ce qui a tourné (hors ligne, rien d'autre)
- `cd backend && mvn -o test -Dtest='castbridge.server.wallet.core.**'` : 39 tests, 0 échec (PotSplitVectorsTest 3, GrantScheduleTest 10, ConversionTest 9, StakeRulesTest 6, LedgerPropertyTest 5 dont 10 000 suites en ≈ 6 s, SettlementTest 6).
- `bash tools/agents/gradle-lock.sh --timeout 600 ./mvnw -o test -Dtest='castbridge.server.wallet.core.**'` : même résultat, `./mvnw -o` fonctionne hors ligne (distribution du wrapper en cache). Le script n'est pas exécutable : l'appeler avec `bash`.
- Porte Gradle demandée (une fois) : `:core:test :server-play:test :sender:compileDebugKotlin :receiver:compileDebugKotlin` → premier passage ROUGE en 3 min 58 s : le chien de garde a tué `MultiVolumeServerTest.ejectAndRemountAreDetectedByRescan` (> 60 s, code 137), sans rapport avec ce cahier ; relancé seul : VERT. `PotVectorsTest` seul : VERT (le port Java et `Pot.split` Kotlin s'accordent sur les 17 vecteurs). Puis `:server-play:test :sender:compileDebugKotlin :receiver:compileDebugKotlin` : VERT. Non constaté en un seul passage : le reste de `:core:test` n'a pas été rejoué après la mort du worker (seuls PotVectorsTest et MultiVolumeServerTest l'ont été).

## Choix et écarts à la lettre du cahier (à relire à l'audit)
1. `Txn` porte un 5e champ `refs` (références de blocage `eid`) : sans lui le grand livre ne peut pas rendre un blocage « réglé ou rendu une seule fois » (I-6). L'empreinte `contentSha` inclut genre, références triées, écritures triées (pas la clé).
2. `Settlement.Line` a un champ de plus, `amount` (montant bloqué) : `settle` doit débiter BLOQUE du montant bloqué, pas du seul utilisé.
3. `GrantSchedule.due(id, policy, anchor, spans, alreadyGranted, now)` : `id` (clé d'idempotence) et `policy` (montants) ajoutés aux paramètres.
4. Les constructeurs de transactions sont des méthodes statiques de `Txn` (`grant`, `grantMulti`, `convert`, `transfer`, `lock`, `settle`, `refund`, `voucher`, `adjust`). L'ouverture illimitée = une transaction à deux monnaies sous `grant:<id>:open-unlimited` (`grantMulti`) ; les tranches de période = une transaction par monnaie (`grant:<id>:<cur>:p<k>`).
5. Cagnotte sans marqueur (END où personne n'a de point) : la conservation prime, chaque mise utilisée est rendue (comme ABORT). `Pot.split` seul ne distribue rien dans ce cas ; la conception ne le dit pas.
6. Édition SUPER couvrant l'instant de début d'une période : aucune tranche, même si une autre édition la couvre aussi. À confirmer.
7. `StakeRules` : MBOKO = PRODUCTION, UNLIMITED ou GRÂCE (paramètre) ; SUPER ne peut donc pas miser du MBOKO à la lettre du cahier ; NDEM = tout sauf NONE (ou grâce).
8. Défense en profondeur dans `MemoryLedger` (pas dans le cahier) : seuls ESCROW_LOCK/SETTLE/ESCROW_REFUND touchent la poche BLOQUE et `SYS:POT` ; un règlement ne peut créditer que les titulaires de ses blocages, BLOQUE baisse exactement du montant bloqué, `SYS:POT` revient à 0 ; blocage inconnu ⇒ `ESCROW_UNKNOWN`, déjà fermé ⇒ `ESCROW_CLOSED`.
9. Ajouts : `WalletPolicy.checkStake` (bornes de mise) et `checkTransfer` (plafond du jour, l'appelant fournit le cumul du jour : le cœur n'a pas d'horloge), `WalletReason` (motifs § 7.6 + motifs internes UNBALANCED, IDEM_CONFLICT, ESCROW_UNKNOWN, ESCROW_CLOSED, BAD_TXN). Politique : toute valeur hors bornes lève `IllegalArgumentException` (message français) ; frais inverses négatifs refusés.
10. Arrondi : `f = ceil(rate·q·reverseFeeBp / 10 000)` en entiers (jamais de flottant) ; vérifié contre un oracle BigDecimal ; le joueur ne gagne jamais (1 000 tirages dans les deux ordres).

## Invariants prouvés (LedgerPropertyTest, graines 1..10 000, 1..200 opérations, après chaque opération)
I-1 Σ par monnaie = 0 ; I-2 aucun compte de joueur négatif ; I-3 masse = −Σ comptes système, `SYS:CONVERT(NDEM) = −rate × SYS:CONVERT(MBOKO)` (taux et frais variables d'une graine à l'autre), `SYS:FEE ≥ 0`, `SYS:POT = 0`, BLOQUE = Σ blocages ouverts ; I-4 rejeu = aucun changement, même clé et autre contenu = IDEM_CONFLICT sans changement, découvert = INSUFFICIENT sans changement ; I-6 un blocage réglé ne se rend pas, un blocage rendu ne se règle pas (ESCROW_CLOSED, aucun changement). Compteurs de couverture asserts (conversions, règlements, rendus, rejeux, conflits, refus, fermetures doubles, frais).

## Mutations demandées par le cahier : NON FAITES
Appliquer puis retirer les trois mutations (retirer le test de négatif ; accepter IDEM_CONFLICT comme rejeu ; arrondir `f` vers le bas) n'a pas pu être exécuté : après l'édition de la première mutation, l'exécution du test muté a été refusée par le classificateur de permissions de l'environnement (« Security Test Removal »). La mutation a été retirée aussitôt (diff vérifié : `MemoryLedger` contient bien `if (!a.isSystem() && after < 0)`), et je n'ai pas cherché de contournement. Les tests sont écrits pour échouer sous ces mutations (`hostile` : découvert attendu INSUFFICIENT, conflit attendu IDEM_CONFLICT ; `ConversionTest.feeIsCeilingNotFloor` et `neverAGainOver1000Draws` contre l'oracle) mais **cela n'a pas été constaté**. À faire par l'auditeur ou à autoriser par le propriétaire.

## Vecteurs
`tools/wallet/ledger-vectors.json` (`castbridge-ledger-vectors-v1`) : 17 cas `Pot.split`, 13 cas d'échéancier, 8 cas de conversion (frais 0, 1, 150, 2 000 pb). Les cas `Pot.split` sont lus par `PotSplitVectorsTest` (Java) et `PotVectorsTest` (Kotlin, `android/core/src/test/kotlin/castbridge/core/quiz/PotVectorsTest.kt`, sans bibliothèque JSON : une ligne par cas).

FICHIERS: backend/src/main/java/castbridge/server/wallet/core/{AccountRef,Conversion,Currency,Edition,EditionSpan,Entry,GrantSchedule,Ledger,LedgerException,MemoryLedger,Pocket,PotSplit,Settlement,StakeRules,Txn,TxnKind,WalletPolicy,WalletReason}.java ; backend/src/test/java/castbridge/server/wallet/core/{ConversionTest,GrantScheduleTest,LedgerPropertyTest,PotSplitVectorsTest,SettlementTest,StakeRulesTest}.java ; tools/wallet/ledger-vectors.json ; android/core/src/test/kotlin/castbridge/core/quiz/PotVectorsTest.kt ; docs/agent-reports/sonnet-w22-01.md ; docs/agent-briefs/SONNET-WAVE22-INDEX.md (ligne w22-01). Aucune modification de Wallet.kt, de pom.xml, ni de dépendance.
SYMBIOSE: cap=aucune (cœur serveur pur, aucun message téléphone↔TV) · proto=inchangé · reason=motifs § 7.6 portés par WalletReason (affichage TV et téléphone : w22-05, w22-07) · deux écrans=sans objet ici
NON FAIT / À VALIDER: mutations (ci-dessus) ; branchement à la base (w22-02) ; aucune vérification sur appareil ni sur le serveur (non demandée).
