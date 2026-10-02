STATUT: TERMINÉ
CAHIER: sonnet-w4-11 (amendé W5) · MODÈLE: sonnet · BRANCHE: claude/sonnet-w4-11 · COMMIT: voir `git log -n 1`
PORTE: `gradle :core:test --tests '*AgentVectorsTest*' '*Delegation*' '*Ticketed*' '*Sales*' '*PriceGrid*' '*ReceiptTest*' '*DelegatedReplay*'` → VERT (48 tests)
SUITE COMPLÈTE: `:core:test` → 2024 tests, 1 rouge hors zone et antérieur : `TrialRoutesTest.everyServedRouteIsClassified` (`/api/library/organize`, `/api/library/organize/apply` dans routes.txt, non classées dans `TrialPolicy`)
FICHIERS: `C/owner/{Keys,License}.kt` (modifiés) ; neufs `C/owner/{Delegation,DelegatedVerifier,TicketedActivation,AgentVectors}.kt`, `C/sales/{PriceGrid,SalesLedger,Receipt}.kt`, `tools/activation/agent-vectors.json` (37 cas) ; tests `CT/owner/{DelegationTest,TicketedActivationTest,AgentVectorsTest,AgentFixtures,LicenseAndGateTest(+DelegatedReplayTest)}.kt`, `CT/sales/{PriceGridTest,SalesLedgerTest,ReceiptTest}.kt`
API (pour w4-12…17) :
- `Delegation.issue(signer, at, seq, nonce, agentPubB64, name, maxKeyDays, maxSales, bundles, scopes, validityDays, notBefore, confirmOrders, sellVouchers, maxConfirmXafPerDay): String` ; `Delegation.verify(token, ring, revocations, nowMs, seqState?): DelegationResult` ; `decode`, `newer(a,b)`, `replayKeys(tokens, ring): List<TrustedKey>` ; `Delegation.agentKey()`.
- `TicketedActivation.encode/split/isTicketed` ; `DelegatedVerifier(ring, revocations, expect, seqState, delegationSeq).verify(line, device, now): Outcome(result: ActivationResult, delegation: Delegation?)`.
- `KeyScope.DELEGATE` ; `TrustedKey.validity: LongRange?` ; `KeyRing.withDelegated(list)`.
- `PriceGrid.verify(json, publicKeys, notOlderThan): Verified{priceOf(item,days), items()}` ; `PriceGrid.signedJson(...)`.
- `SalesLedger.Entry{sign, verify, text, toLine, parse, fromLine}`, `chain(entries, pubB64): ChainResult{Ok,Gap,Diverged,BadSignature}`, `next`, `balance`, `LedgerStore`/`MemoryLedgerStore` ; `Receipt.code/parse/text`.
CHOIX:
- Amendements W5 appliqués : ni `agentx=` ni `master=` (pas de dépendance à w4-01), `maxRentalDays` toujours 0 (`BAD_DELEGATION` sinon), champs `confirmOrders`/`sellVouchers`/`maxConfirmXafPerDay`, toute ligne `rental` d'un agent refusée (y compris `essai`), items `bon|…|serial` et `commande|…|montant` admis.
- `KeyScope.ALL` n'inclut PAS `DELEGATE` (explicite seulement : vecteurs existants et `OwnerCli` inchangés) ; la clé propriétaire doit recevoir `ALL + DELEGATE`.
- `maxKeyDays` borne les clés de production ; un essai d'agent est borné à 365 j ; production sans `usage` refusée.
- Une activation refusée par les limites du mandat ne consomme pas son `seq` (copie de `SeqState`).
- Délégation : `agent` déjà clé compilée de la TV => `BAD_DELEGATION` ; activation non signée par l'agent du mandat => `KEY_NOT_ALLOWED`.
- Rejeu : fenêtre `notBefore..expiresAt` de la clé déléguée ; `replayKeys` vérifie chaque mandat à son propre `issuedAt` (un mandat périmé garde ses événements passés).
- `Delegation.newer` : même clé propriétaire, `seq` le plus haut ; deux clés, `issuedAt` le plus récent.
- Fichier de support de test ajouté : `AgentFixtures.kt` (clés/appareils partagés).
NON FAIT / À VALIDER : `cash != price` sans `NOTE` n'est pas imposé par le journal (contrôle côté serveur/app) ; grille sans `jetons|…` (w5-01) ; `rental-vectors-v2.json` absent de la base (w4-01 non fusionné), non touché.
QUESTION: aucune
AUTOCONTRÔLE: [x] zone [x] porte [x] suite (1 rouge antérieur) [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
