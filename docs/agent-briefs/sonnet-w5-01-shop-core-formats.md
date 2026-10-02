# w5-01 — Cœur : formats de la boutique (demande, code de commande, bon de recharge, lot de bons, catalogue de boutique, politique, commande, `ShopApi`), vecteurs

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W5a-1** (vague W5a) · prérequis : w4-11 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Shop*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non

**Vague 5a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3, § 5, § 9 (lire en entier). Branche `claude/sonnet-w5-01`. Rapport : `docs/agent-reports/sonnet-w5-01.md`. **Premier cahier de la vague** : w5-05, w5-11, w5-12, w5-15, w5-16 codent contre ses API. Dépend de w4-11 (`C/sales/PriceGrid.kt`, `C/sales/Receipt.kt`, `Base32C`).

## Objectif
Le cœur (JVM pur, sans `android.*`) sait : (1) construire et analyser la **demande de boutique** (`castbridge-shop-request-v1` : demande d'appareil v2 + preuve d'activation + contrats + état des jetons) ; (2) produire/valider un **code de commande** `CB-XXXX-XX` et un **code de bon** `CB-XXXX-XXXX-XXXX-XXXX` (Crockford, contrôle `Base32C.check`, O/0 I/1 tolérés) ; (3) vérifier un **lot de bons** signé par le propriétaire (`castbridge-voucher-batch-v1`) ; (4) lire une grille de prix **étendue** (`price=jetons|<n>|<xaf>`, réglages `tokens.*`) ; (5) assembler un **catalogue de boutique** (bouquets + grille) avec les règles d'affichage ; (6) appliquer la **politique** (essai, réduit, enfant) ; (7) tenir le modèle pur d'une **commande** côté client (états, JSON) ; (8) définir l'interface **`ShopApi`** (client HTTP abstrait) et sa **factice** ; (9) figer tout cela par des vecteurs.

## Pourquoi (preuves)
- `C/lots/SignedBundleCatalog.kt:15-40` (catalogue signé, modèle de vérification avec anti-retour) ; `C/lots/EditionPolicy.kt:8` (`Bundle(id, type, lots, title, rawBytes, rentalDays)`) ; `C/sales/PriceGrid.kt` (w4-11 : lignes `price=` triées) ; `C/owner/DeviceIdentity.kt:96` (`Base32C.encode/decode/check`) ; `C/owner/LicensedIssuer.kt` (`DeviceRequest.parse`, ligne `install=` depuis w4-01) ; `C/lots/RentalApi.kt:62-72` (forme de `GET /api/rental` : `contract`, `product`, `endsAt`).
- Aucun code de boutique n'existe (`grep -rn 'voucher\|bon de recharge\|ShopOrder' android backend` → 0).

## Fichiers possédés
Nouveaux : `C/shop/ShopRequest.kt`, `C/shop/OrderRef.kt`, `C/shop/VoucherCode.kt`, `C/shop/VoucherBatch.kt`, `C/shop/ShopCatalog.kt`, `C/shop/ShopPolicy.kt`, `C/shop/ShopOrder.kt`, `C/shop/ShopApi.kt`, `C/shop/FakeShopApi.kt`, `C/shop/ShopVectors.kt`, `CT/shop/**`, `tools/activation/shop-vectors.json`. Modifiés : `C/sales/PriceGrid.kt`, `C/sales/Receipt.kt`, `CT/sales/PriceGridTest.kt`, `CT/sales/ReceiptTest.kt`. **Hors zone** : `C/tokens/**` (w5-02), `C/quiz/**` (w5-03), `C/owner/**`, `C/lots/**` (utiliser), `R/**`, `S/**`, `backend/`, Python (w5-05), docs (w5-20).

## Étapes
1. `ShopRequest(device: DeviceRequest, deviceText: String, activationToken: String?, rentals: List<RentalRef(product, period, endsAt)>, tokensSeq: Long?, tokensSpent: Long?, tokensMac: String?)` : `text()` canonique (§ 3.4 a ; `device=` = base64url du texte de la demande ; lignes inconnues ignorées à la lecture), `parse(text): ShopRequest?`, bornes (jeton ≤ 8 Ko, ≤ 20 contrats).
2. `OrderRef.generate(random): String` = « CB- » + 6 Crockford + 1 contrôle ; `OrderRef.normalize(input): String?` (O/0, I/1, minuscules, espaces/tirets libres) ; `isValid`.
3. `VoucherCode` : `generate(random, batchPrefix: String /* 2 Crockford */)` → 16 caractères (2 préfixe + 13 aléatoires + 1 contrôle `Base32C.check(salt = 1)`), `format(code) = "CB-XXXX-XXXX-XXXX-XXXX"`, `normalize(input): String?` (null si contrôle faux : **le message dit quel groupe** est douteux comme `CompactActivation`), `hash(code) = sha256 hex` (c'est ce que le serveur stocke), `serial(batchId, index)` = `<2 préfixe>` + 8 Crockford.
4. `VoucherBatch` : `parse(text)`, `canonicalPayload()`, `verify(text, publicKeys /* UpdateKeys.PUBLIC_KEYS */): Result` (signature Ed25519 `castbridge.core.update.Ed25519.verify`, bornes : `count ≤ 5000`, `expiresAt ≤ issuedAt + 24 mois`, `item` ∈ syntaxe de la grille, lignes `voucher=` triées, serials uniques), `item` typé (`ShopItem.parse("jetons|60")` → `ShopItem.Tokens(60)`, `Rental(bundleId, days)`, `Key(kind, days)`).
5. `PriceGrid` (w4-11) : accepter `price=jetons|<n>|<xaf>` et des lignes `set=tokens.expiryDays|<n>`, `set=tokens.offlineGrantMax|<n>`, `set=tokens.kidDailyDefault|<n>`, `set=tokens.welcome|<n>`, `set=tokens.cost.secondChance|<n>`, `set=tokens.cost.extraJoker|<n>`, `set=tokens.cost.swapQuestion|<n>` (triées, après les `price=`) ; valeurs par défaut si absentes : 0, 60, 0, 10, 5, 2, 3 ; une grille sans ces lignes reste valide (compatibilité : `canonicalPayload` ne change pas pour une grille sans `set=`). `tokenPacks(): List<Pack(n, xaf)>`, `settings(): TokenSettings`.
6. `ShopCatalog.build(bundles: BundleCatalog, grid: PriceGrid?, trialLotsPresent: Set<String>, tvManifestLots: Set<String>, activeRentals): List<ShopEntry>` : par bouquet : prix (ou null ⇒ « prix non communiqué »), `rentalDays`, `orderable` (prix présent **et** `rentalDays ≤ 60` **et** aucun lot libre), `previewAvailable`, `state` (`NONE | ACTIVE(endsAt) | RENEWABLE`), taille, « il manque X Mo sur la TV » (budget `LotBudget.TV_MAX_BYTES`).
7. `ShopPolicy` : `data class Context(trial: Boolean, degraded: Boolean, kidProfile: Boolean, online: Boolean, tunnelTermsAccepted: Boolean)` ; `mayBrowse` (toujours), `mayOrderRental`, `mayOrderTokens`, `mayRedeemVoucher(item)` (en réduit : seulement `Key`), `mayTypeVoucherOffline` (vrai : mis en attente), `reason(fr)` ; constantes `TRIAL_MAY_ORDER = false`, `ORDER_TTL_HOURS = 72`, `ONLINE_RENTAL_MAX_DAYS = 60`. Tests exhaustifs des 5 × 2 cas.
8. `ShopOrder` (modèle client) : `Order(ref, item, qty, amountXaf, status: CREATED|AWAITING_PAYMENT|PAID|FULFILLED|DELIVERED|CANCELLED|REFUNDED|EXPIRED, channel, createdAt, expiresAt, fulfilment: Fulfilment?)` ; `Fulfilment.Rental(activationToken, contract, lots: List<LotRef(id, version, bytes, sha256, url)>)`, `Fulfilment.Tokens(grantEnvelope, balance)`, `Fulfilment.Key(activationToken)` ; transitions pures `advance(from, event)` testées ; JSON via `C/net/JsonLite.kt`.
9. `ShopApi` (interface bloquante, pas de coroutine) : `quote(req: ShopRequest, item: ShopItem): Quote`, `order(req, item, qty, idemKey, voucher: String?): Order`, `get(ref): Order`, `delivered(ref, tvAck: String)`, `cancel(ref)`, `redeem(req, code): Order`, `me(req): ShopState`, `reportTokens(req, report: String): TokensReply`, `receipt(code): String?`, `downloadLot(url, dest: File, onProgress)` ; erreurs typées `ShopError(code: String, messageFr)`. `FakeShopApi` en mémoire (commande avec bon valide ⇒ `FULFILLED` immédiat avec une activation signée par une clé de test fournie, scénarios : bon inconnu, déjà utilisé, lot révoqué, essai, réduit, quota) : utilisée par les tests de w5-11/12/15/16 et l'émulateur.
10. `Receipt` (w4-11) : `verifySigned(text, signature, publicKeys)` additif pour le reçu v2 serveur ; `text(...)` inchangé.
11. `shop-vectors.json` (`castbridge-shop-vectors-v1`, clés de test **copiées** de `test-vectors.json`) : `order-ref` (génération déterministe avec graine, normalisation, contrôle faux), `voucher-code` (≥ 6 : valide, groupe 2 faux, O/0, hash attendu, serial), `voucher-batch` (signé OK, modifié, expiré > 24 mois, count > 5000, serial en double, item inconnu), `shop-request` (texte → champs ; lignes inconnues ; jeton trop long), `price-grid-tokens` (grille avec `set=` ⇒ réglages ; grille sans ⇒ défauts ; `canonicalPayload` identique à w4-11 pour une grille sans jetons), `shop-catalog` (bouquet > 60 j non commandable ; sans prix ; lot libre), `shop-policy` (table). ≥ 25 cas ; générateur `CASTBRIDGE_WRITE_VECTORS=1`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.shop.*' --tests 'castbridge.core.sales.*'   # vert
git diff --quiet tools/activation/test-vectors.json tools/activation/rental-vectors.json tools/activation/rental-vectors-v2.json tools/activation/agent-vectors.json && echo "vecteurs existants intacts"
python3 -c "import json;print(len(json.load(open('tools/activation/shop-vectors.json'))['cases']))"   # ≥ 25
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/shop   # 0 hit
grep -rn 'XAF [0-9]\|237' android/core/src/main/kotlin/castbridge/core/shop   # 0 hit (aucun montant, aucun numéro)
```

## Cas limites
- Demande sans `activation=` (TV en essai sans clé ? impossible : l'essai a une activation `trial`) : `activationToken = null` accepté par le format, refusé par la politique (« preuve d'activation absente »).
- Bon tapé avec le préfixe « CB » absent ou en minuscules : normalisé.
- Deux lots de bons avec le même préfixe de 2 caractères : autorisé (le préfixe n'est pas unique ; le serial l'est).
- Grille signée **avant** cette vague (sans `set=`) : valide, réglages par défaut.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun montant réel ; pas d'`import android` ; ne pas modifier `SignedBundleCatalog`, `PriceGrid.canonicalPayload` pour les grilles existantes, ni les vecteurs existants ; ne pas définir le porte-jetons (w5-02) ; pas de HTTP ici (seulement l'interface) ; textes utilisateur en français.

## Rapport
`STATUT`, signatures publiques (pour w5-05, w5-11, w5-12, w5-15, w5-16), nombre de vecteurs, décisions de détail (longueurs, alphabet), ce que `FakeShopApi` simule.
