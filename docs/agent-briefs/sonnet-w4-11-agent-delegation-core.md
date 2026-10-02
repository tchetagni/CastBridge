# w4-11 — Cœur : délégation (`type=delegation`, portée `DELEGATE`), activation « avec ticket », grille de prix signée, journal des ventes chaîné, reçus, vecteurs

**Vague 4c · Effort L (≈ 3 j) · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W4-VENTE-TERRAIN.md` § 3, § 4, § 6 (lire en entier). Branche `claude/sonnet-w4-11`. Rapport : `docs/agent-reports/sonnet-w4-11.md`. **Premier cahier de la sous-vague 4c** : tous les autres en dépendent. Dépend de w4-01 (boîte v2 : `RentalKeys.makeBoxV2/openBox`, `X25519`) pour la ligne `master=`.

## Objectif
Le cœur sait : (1) émettre et vérifier une **délégation** signée par le propriétaire (nouvelle portée `DELEGATE`) qui autorise une clé d'agent à émettre des clés d'essai/production bornées et des locations ≤ 60 j sur des bouquets donnés, avec le maître des locations enveloppé pour l'agent ; (2) vérifier une **activation avec ticket** (`<délégation>|<activation>`) sans modifier `ActivationVerifier` ; (3) rejouer un registre dont des événements sont signés par des clés déléguées ; (4) lire une **grille de prix** signée ; (5) tenir un **journal des ventes** à ajout seul, chaîné et signé, avec des **reçus** à code court ; (6) figer tout cela par des vecteurs.

## Pourquoi (preuves)
- `C/owner/Keys.kt:21-34` (`KeyScope`), `:49-56` (`TrustedKey`), `:59-69` (`KeyRing` : `find`, `isRevoked`) ; `C/owner/Activation.kt:143` (`keys.find(a.keyId)` ⇒ `UNKNOWN_KEY` pour une clé d'agent) ; `C/owner/Envelope.kt` (un seul codec, `TYPE` libre) ; `C/owner/License.kt:100-165` (`replay` exige la portée dans l'anneau), `:204-227` (`RevocationNotice`).
- `C/lots/SignedBundleCatalog.kt:34-65` (modèle de fichier signé hors ligne) ; `android/ownerlib/.../OwnerStore.kt:57-63` (journal chaîné minimal, modèle à généraliser) ; `C/owner/Base32C` (contrôle Crockford, `C/owner/Activation.kt:270`).
- Décisions D8/D9 : espèces au point focal ; w2-06/w2-10 abandonnés.

## Fichiers possédés
Modifiés : `C/owner/Keys.kt` (`KeyScope.DELEGATE`, `TrustedKey.validity: LongRange?` additif, `KeyRing.withDelegated(list)`), `C/owner/License.kt` (`replay` : refuse un événement `at` hors `validity` de la clé ⇒ `KEY_NOT_ALLOWED` ; tout le reste inchangé). Nouveaux : `C/owner/Delegation.kt`, `C/owner/DelegatedVerifier.kt`, `C/owner/TicketedActivation.kt`, `C/sales/PriceGrid.kt`, `C/sales/SalesLedger.kt`, `C/sales/Receipt.kt`, `C/owner/AgentVectors.kt`, `tools/activation/agent-vectors.json`, tests `CT/owner/DelegationTest.kt`, `CT/owner/TicketedActivationTest.kt`, `CT/owner/AgentVectorsTest.kt`, `CT/sales/PriceGridTest.kt`, `CT/sales/SalesLedgerTest.kt`, `CT/sales/ReceiptTest.kt`, `CT/owner/LicenseAndGateTest.kt` (**rejeu avec clé déléguée seulement**). **Hors zone** : `Activation.kt`, `FeatureGate.kt`, `OwnerFrames.kt`, `LicensedIssuer.kt`, `RentalKeys.kt` (utiliser), outils, `R/**`, `S/**`, `backend/`, Python (w4-17), docs (w4-18).

## Étapes
1. `KeyScope.DELEGATE` (KDoc : console téléphone et bureau, jamais le serveur) ; `TrustedKey(…, validity: LongRange? = null)` ; `KeyRing.withDelegated(keys: List<TrustedKey>)` (une clé déléguée ne peut pas écraser une clé compilée de même `kid`).
2. `Delegation` : `data class` (champs du § 3 de la conception), `line()`/`parse(body)` canoniques (ordre fixe, champs optionnels omis), `ALLOWED_SCOPES = {ISSUE_TRIAL, ISSUE_PRODUCTION}`, bornes (`maxKeyDays 1..3660`, `maxRentalDays 0..60`, `maxSales 1..10000`, `name [a-z0-9-]{1,32}`, validité ≤ 180 j), `issue(signer, at, seq, nonce, …)` (envelope `type=delegation`, `target=any`), `verify(token, ring, revocations, nowMs): Result` (ordre des refus : conception § 3), `wrapMaster(master, agentX, ephSeed)` / `openMaster(delegation, agentXPriv)` via `RentalKeys.makeBoxV2/openBox` (produit `delegation`, period = `issuedAt`).
3. `TicketedActivation` : `encode(delegationToken, activationToken) = "$d|$a"`, `split(line): Pair?` (exactement un `|`, deux `cbx1.`), `isTicketed(text)`.
4. `DelegatedVerifier(ring, revocations, expect)` : `verify(line, device, nowMs): ActivationResult` = délégation → `TrustedKey(agent, pub, scopes, validity = notBefore..expiresAt)` → `ActivationVerifier(KeyRing(ring).withDelegated(…), revocations, expect).verify(activation)` → contraintes (production **avec** `usage` et durée ≤ `maxKeyDays` ; `rental` : jours ≤ `maxRentalDays`, bouquets ⊆ `bundles`, produit `loc-<b>` ou `essai` ; aucun `super`/`openall`/`subscription`/`purchase`) ⇒ `KEY_NOT_ALLOWED` « Le point focal n'est pas autorisé à délivrer ceci : … ». Retourne aussi la `Delegation` acceptée (pour l'affichage « Point focal : <name> » et la persistance).
5. `PriceGrid` : format du § 6 (lignes `price=` triées), `canonicalPayload`, `verify(json, publicKeys, notOlderThan)` (même discipline que `SignedBundleCatalog`), `priceOf(item, days): Int?`, `items()`. Les clés publiques = `UpdateKeys.PUBLIC_KEYS` (comme le catalogue).
6. `SalesLedger` : `Entry` (champs § 4), `canonical()`, `hash` (16 hex), `sign(signer)`, `verify(pub)`, `chain(entries): ChainResult` (ok / trou à `seq` / divergence à `seq` / signature fausse) ; `LedgerStore` interface (`append(entry)`, `all()`), `MemoryLedgerStore` ; `append` refuse un `seq` ≠ dernier + 1 ou un `prev` faux ; **aucune** méthode de suppression ou de réécriture ; `balance(entries, remittancesConfirmed)`.
7. `Receipt.code(agentKid, seq, deviceCode, at)` = `R-XXXX-XXXX` (7 caractères Crockford de données + 1 contrôle `Base32C.check(…, salt = 0)`), `parse` tolérant (O/0, I/1), `text(entry, name, contact, grid)` (texte français du reçu, contact en paramètre : **jamais en dur**).
8. `agent-vectors.json` (`castbridge-agent-vectors-v1`, clés de test de `test-vectors.json` **copiées**, pas référencées) : `build-delegation` (octets), `delegation` (accepté / expiré / portée hors sous-ensemble / clé propriétaire sans `DELEGATE` / agent révoqué / seq ancien), `ticketed` (accepté ; clé illimitée refusée ; location 90 j refusée ; bouquet hors liste refusé ; `purchase` refusé ; vieille TV = MALFORMED), `master-wrap` (ouvre avec la clé X25519 de test de l'agent, pas avec une autre), `price-grid` (signée OK / modifiée / plus ancienne), `ledger` (chaîne OK / trou / divergence / signature), `receipt` (code attendu), `registry-delegated` (événements `license`+`issue` signés par l'agent rejoués avec l'anneau délégué ; hors fenêtre ⇒ rejeté). ≥ 20 cas ; générateur `CASTBRIDGE_WRITE_VECTORS=1`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*' --tests 'castbridge.core.sales.*'   # vert
git diff --quiet tools/activation/test-vectors.json tools/activation/rental-vectors.json tools/activation/rental-vectors-v2.json && echo "vecteurs existants intacts"
python3 -c "import json;print(len(json.load(open('tools/activation/agent-vectors.json'))['cases']))"   # ≥ 20
grep -n 'DELEGATE' android/core/src/main/kotlin/castbridge/core/owner/Keys.kt   # ≥ 1
grep -rn 'fun delete\|fun remove\|fun rewrite' android/core/src/main/kotlin/castbridge/core/sales/SalesLedger.kt   # 0 hit
```

## Cas limites
- Délégation renouvelée (même agent, `seq` supérieur) : remplace l'ancienne dans l'état de la TV (`DelegationStore` de w4-15 ; ici, `Delegation.newer(a, b)`).
- Deux délégations de deux clés propriétaires différentes pour le même agent : la plus récente par `issuedAt` gagne ; le dire dans le KDoc.
- `master=` absent : `openMaster` ⇒ null, l'app agent ne vend pas de location (w4-13).
- Clé d'agent qui serait aussi une clé compilée de la TV : `withDelegated` refuse (une clé compilée garde ses portées).

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas modifier `ActivationVerifier` ; pas de montant ni de nom en dur ; pas d'`import android` ; ne pas réutiliser `Entitlement` (`cbe1`) ; textes en français.

## Rapport
`STATUT`, signatures des nouvelles API (pour w4-12…17), nombre de vecteurs, décisions sur les cas limites.
