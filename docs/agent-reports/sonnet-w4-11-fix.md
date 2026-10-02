# sonnet-w4-11-fix : corrections de l'audit Opus (délégation point focal)

Branche `claude/sonnet-w4-11-fix` (depuis `claude/sonnet-w4-11`), un commit, non poussée.

## Constats corrigés
1. BLOQUANT `DelegatedVerifier.refusal` : plus aucune addition avant comparaison. Exige `startsAt >= notBefore - 1 j`, `startsAt <= expiresAt`, `endsAt > startsAt`, `Math.subtractExact`, puis `endsAt - startsAt <= maxKeyDays * jour` (multiplyExact) ; même règle pour l'essai (365 j). Vecteurs `ticket-usage-overflow-refused`, `ticket-usage-overflow-negative-start-refused`, `ticket-usage-end-before-start-refused`. Tests Kotlin (débordement 0..Long.MAX, début négatif, Long.MIN, fin avant début, 366 j essai, début après mandat, bornes incluses).
2. Séquence des mandats par `"$ownerKid/$agent"` (`Delegation.seqKey()`). Test A seq 10 puis B seq 11 : A non périmé. Les vecteurs `lastSeq` utilisent désormais `"desk/agent"`.
3. Rejeu : `KeyRing.withDelegated` fusionne les entrées d'un même `kid` (portées en union, fenêtres dans `validity` + `moreValidity`, `TrustedKey.validAt`). `LicenseBook.replay` utilise `validAt`. Test à deux mandats + vecteur `registry-delegated-two-mandates`.
4. `Delegation.replayKeys(tokens, ring, revocations)` : un agent révoqué par liste cbr1 ne donne plus de clé. Test.
5. `DelegatedVerifier` exige `activation.issuedAt` dans `notBefore..expiresAt`. Test + vecteur `ticket-issued-before-mandate-refused`.
6. `PriceGrid` : jours et prix doivent être des entiers JSON exacts (Long) ; décimaux, chaînes refusés (« illisible ») ; hors bornes (jours 1..3660, prix 0..100 000 000) refusés sans rétrécissement (saturation puis contrôle de bornes). KDoc : tri ASCII strict de la ligne entière, parité Python w4-17 obligatoire. Tests.
7. KDoc de `LicenseEvent.license` : nombre de postes déclaré par l'agent, borné seulement côté serveur (`maxSales`).
8. `SalesLedger.chain` vérifie `agent == idOf(pub)` (sinon `BadSignature`). Hash de 16 hex inchangé : à passer à 32+ hex en v2 (suivi). Test.
9. `Delegation.problem` : `notBefore > 0`. Test.

## Notes et risques
- Union des portées sur plusieurs mandats d'un même agent : un mandat plus large peut élargir les portées d'un mandat plus étroit pour les événements de sa propre fenêtre (acceptable, la fenêtre temporelle reste respectée).
- Hors périmètre, non touché : `ActivationVerifier` ~l.214 fait `endsAt - startsAt` sans protection ; le contrôle du mandat refuse avant, mais à corriger pour les clés non déléguées (propriétaire).
- `AgentFixtures.forgedActivation` signe sans les contrôles de l'émetteur (agent hostile).
- Python `verify_vectors.py` ne lit pas agent-vectors.json : le format `lastSeq` ("owner/agent") est à répercuter dans les portages Java/Python.
- test-vectors.json et rental-vectors.json inchangés.

## Tests
Ciblés (AgentVectors, Delegation, TicketedActivation, PriceGrid, SalesLedger) : verts. `:core:test` complet : 2032 tests, 1 échec sans rapport (`TrialRoutesTest.everyServedRouteIsClassified` : routes `/api/library/organize*`, déjà corrigé par `routes.txt` sur main/w5-03, absent de cette branche).
