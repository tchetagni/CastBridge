# w2-10 — Serveur : commandes et locations, phase 0 (confirmation manuelle), secret maître serveur

**Vague 2 · Effort L (≈ 4-5 j) · Statut BLOQUÉ** — questions : **D8** (secret maître dédié au serveur, recommandé), **D9** (phase 0 manuelle validée ; grille de prix XAF ; numéro marchand). Sans réponse, l'agent construit tout derrière `castbridge.shop.enabled=false` avec une table de prix vide et pose `QUESTION:`. **Rien n'est déployé.** Branche `claude/sonnet-w2-10`. Rapport : `docs/agent-reports/sonnet-w2-10.md`.

## Objectif
Le serveur sait : (1) donner un devis pour (code d'appareil, bouquet) à partir du catalogue signé (`rentalDays`, plafond 60 j) et de la table de prix ; (2) enregistrer une commande idempotente ; (3) recevoir une preuve de paiement manuelle ; (4) laisser le propriétaire **confirmer** (TOTP, motif) dans `/admin/shop` ; (5) à la confirmation, **émettre** l'activation de location (`kind=production` + ligne `rental`, `period` = nouvelle ou renouvelée) avec la **boîte** scellée pour la TV et **sceller les lots** avec un secret maître **serveur** ; (6) servir activation + lots au téléphone ; (7) enregistrer l'accusé de livraison.

## Pourquoi (preuves)
- `docs/RENTAL-LOTS.md:133-141` : « NON IMPLÉMENTÉE » ; `grep -rn 'rental' backend/src/main` : seulement l'analyse/bornes (`B/licenses/WireActivation.java:34-44,126`), aucun HKDF/AES, aucun secret maître, aucune table.
- `C/lots/RentalKeys.kt:26-33` : `master = SHA-256(sign("castbridge-rental-master-v1"))` dérivé de la clé de **l'outil émetteur** ; `rentalKey = HKDF(master, "key|licence|poste|produit|period")` ; `box` par sous-ensembles k parmi n (`:70-80`) ; vecteurs `box`/`seal` dans `tools/activation/rental-vectors.json` (§ 10.4 de RENTAL-LOTS donne les dérivations exactes).
- `B/licenses/ActivationService.java:145-219` : émission avec verrou de licence, réutilisation de poste k parmi n, idempotence dérivée ; `:221` le serveur ne construit jamais de ligne `rental`.
- `B/licenses/ProductService.java:17`, `V50:25-27` : champs tarifaires volontairement vides.
- Audit : MO-1, MO-2, MO-5.

## Fichiers possédés
Nouveau paquet `B/shop/**` (`ShopProperties`, `ShopService`, `ShopController` `/api/v1/shop/**`, `AdminShopController` `/admin/shop/**`, `RentalKeysJava` (port de HKDF/AES-GCM/box/seal), `ShopOrder` entité), `B/licenses/ActivationService.java` (**un** point d'entrée `issueRental(...)` additif), nouvelle migration `backend/src/main/resources/db/migration/V62__shop.sql` (**vérifier d'abord** `ls db/migration` : si w1-11 a pris V62, prendre V63 ; règle « plus haut + 1 »), `backend/src/main/resources/templates/admin/shop*.html`, `application.yml` (bloc `castbridge.shop`), `B/config/CastbridgeProperties.java`, `backend/src/test/java/castbridge/server/shop/**`, `docs/API-SERVER.md` (§ boutique), `docs/RENTAL-LOTS.md` (§ 15 : « implémenté » + détails). **Hors zone** : `WireActivation.java`/`EnvelopeVerifier.java` (w1-10), `devices/telemetry/lots` (w1-11), `tunnel` (w2-15), tout le code Android (w2-06).

## Étapes
1. Migration : `shop_price(bundle_id, days, amount, currency, valid_from, valid_to, active)`, `shop_order(id, order_ref UNIQUE, device_code, license_id NULL, seat_id NULL, bundle_id, days, amount, currency, provider, provider_ref NULL, status, idem_key UNIQUE, created_at, paid_at, confirmed_by, issued_activation_id NULL → lic_issuance.id, period_ms NULL, UNIQUE(provider, provider_ref))`, `shop_rental(license_id, seat_id, product_id, period_ms, ends_at, state, PK composite)`.
2. Secret maître : fichier `secrets-dir/rental-master.key` (32 octets aléatoires ; **jamais** la clé de signature), chargé comme `license-signing.key` (`application.yml:114-115`) ; absent ⇒ module refusé au démarrage avec message clair.
3. `RentalKeysJava` : port de `RentalKeys` (HKDF-SHA256, `rentalKey`, `lotKey`, `seal`, `makeBox` k parmi n) **validé sur les vecteurs `box` et `seal`** de `rental-vectors.json` (clé de test des vecteurs) — c'est le test d'acceptation principal.
4. Endpoints (auth par jeton d'appareil comme `entitlements/me`, `B/licenses/PublicLicenseController.java:37-39` ; limite de débit globale existante) : `POST /quote` {deviceCode, bundleId, activation?} → {licenseId, seatId, days=min(rentalDays,60), amount?, quoteId, expiresAt} ; `POST /orders` (`Idempotency-Key`) → {orderId, orderRef, status PENDING_PAYMENT, payInstructions (numéro depuis la config, jamais en dur)} ; `POST /orders/{id}/proof` {providerRef, paidAt} → AWAITING_CONFIRMATION ; `GET /orders/{id}` → statut + (si ISSUED) `activation` + `lots[{lotId, version, url, sha256, bytes}]` ; `POST /orders/{id}/ack` {tvAck} → DELIVERED. Admin : liste, détail, **Confirmer** (TOTP + motif) / **Refuser** / **Rembourser** (marque seulement ; la révocation de poste reste manuelle via le module licences).
5. Émission : à la confirmation, `ActivationService.issueRental(seat, bundle, days, period)` : scope `ISSUE_PRODUCTION` de la clé serveur, ligne `rental|loc-<bundle>|<bundle>|…|box`, `period = issuedAt` ou période existante de `shop_rental` (renouvellement idempotent) ; refuser tout lot de famille `free` (catalogue de lots signé : lire la famille) ; `maxConcurrent` par licence (config, défaut 3). Lots scellés écrits dans `${storage-dir}/shop/<orderRef>/…` servis par `GET` authentifié ; supprimés après `DELIVERED` + 7 j.
6. Seat inconnu (licence émise hors ligne, registre non importé) : accepter `activation` dans `/quote`, le vérifier avec `EnvelopeVerifier` (comme le tunnel, `TunnelService.java:181`), en déduire licence/poste et créer le poste **sans** consommer un nouveau poste (alias matériel).
7. Tests MockMvc/H2 : devis, commande idempotente (même `Idempotency-Key` ⇒ même `orderId`), preuve, confirmation TOTP, émission (activation vérifiable par `EnvelopeVerifier`, box ouvrable par le port Java avec les empreintes du vecteur), renouvellement même période, refus lot libre, refus > 60 j, `provider_ref` réutilisé ⇒ 409.
8. `docs/API-SERVER.md` + `docs/RENTAL-LOTS.md` § 15.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='RentalKeysJavaTest,ShopApiTest,ShopAdminTest'   # vert ; RentalKeysJavaTest rejoue box/seal de tools/activation/rental-vectors.json
cd backend && ./mvnw -q -o test                                                          # suite complète verte
grep -n 'shop' backend/src/main/resources/application.yml                                 # enabled: false par défaut
ls backend/src/main/resources/db/migration/ | tail -3                                     # V62 ou V63 __shop
```
Observable (préprod, par le propriétaire plus tard) : commande créée depuis le téléphone (w2-06) → page admin → Confirmer → `GET /orders/{id}` renvoie `activation` + lots ; `tools/rental-test/rental_test.py` adapté peut installer cette activation sur l'émulateur et lire les lots.

## Cas limites
- Deux confirmations simultanées : verrou de ligne sur `shop_order` + idempotence sur `issued_activation_id`.
- Catalogue de bouquets absent (404 aujourd'hui tant que le fichier n'est pas déposé) : `/quote` répond 503 « catalogue indisponible ».
- Fenêtre d'installation 48 h : si le téléphone ne livre pas à temps, `GET /orders/{id}` réémet (même période, nouvelle fenêtre) : idempotent par `orderId`.

## À ne pas faire
Pas de déploiement, pas de commit sur les branches partagées, aucun secret ni numéro marchand dans le code ou les tests (valeurs factices nommées `TEST-…`), ne pas modifier les vecteurs, ne pas réutiliser la clé de signature comme maître, ne jamais signer `TRANSFER`/`COMMAND_OPEN_ALL`.

## Rapport
`STATUT: BLOQUÉ` + `QUESTION:` (D8, D9) ; endpoints livrés ; preuve des vecteurs `box`/`seal` ; numéro de migration pris.
