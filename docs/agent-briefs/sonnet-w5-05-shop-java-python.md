# w5-05 — Miroirs Java (serveur) et Python des formats de la boutique et des jetons ; rejeu des vecteurs

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w5-01, w5-02)
> **Groupe : W5a-2** (vague W5a) · prérequis : w5-01, w5-02, w4-05, w4-17 · porte : `python3 tools/activation/verify_vectors.py && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest=ShopVectorsTest`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 5a (fin) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w5-01 et w5-02).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3.4, § 3.5. Branche `claude/sonnet-w5-05`. Rapport : `docs/agent-reports/sonnet-w5-05.md`. Dépend de w1-10 (vecteurs Java/Python v1), w4-05 (`RentalBoxV2.java`, XDH, Python `x25519`), w4-17 (`Delegation.java`, `verify_vectors.py` étendu).

## Objectif
Le serveur (Java 17) et le vérificateur indépendant Python savent lire, vérifier et **produire** : la demande de boutique, le code de commande, le code de bon (+ hash, serial), le lot de bons signé, la grille étendue (`jetons|…`, `set=tokens.*`), l'enveloppe `type=tokens` (émission **et** vérification), le reçu v2 ; ils rejouent `shop-vectors.json` et `tokens-vectors.json` **en entier**.

## Pourquoi (preuves)
- `B/licenses/Envelope.java` (codec `cbx1` : activation, command, revocation, order), `B/licenses/EnvelopeVerifier.java` (pur), `B/licenses/EnvelopeIssuer.java` (commandes, ordres, révocations) ; `B/licenses/Crockford.java`, `Hashing.java` ; `BT/licenses/{EnvelopeVectorsTest,RentalVectorsTest,AgentVectorsTest}.java` (modèle de rejeu) ; `tools/activation/verify_vectors.py` (référence indépendante, doit lire les JSON de vecteurs et **ne jamais** importer le code Kotlin).
- w5-01 (`shop-vectors.json`), w5-02 (`tokens-vectors.json`).

## Fichiers possédés
Nouveaux `B/shop/wire/ShopRequest.java`, `B/shop/wire/OrderRef.java`, `B/shop/wire/VoucherCode.java`, `B/shop/wire/VoucherBatch.java`, `B/shop/wire/TokenGrant.java`, `B/shop/wire/ShopWireVectors.java` (chargeur), `BT/shop/wire/ShopVectorsTest.java` ; modifiés `tools/activation/verify_vectors.py`, `tools/tests/test_verify_vectors.py`. **Hors zone** : `B/licenses/**` (utiliser ; si une méthode manque dans `Envelope`/`EnvelopeIssuer`, la demander dans le rapport et poser une classe locale), le reste de `B/shop/**` (w5-06…09), Kotlin, docs.

## Étapes
1. `OrderRef`, `VoucherCode` (génération déterministe à partir d'un `Random` injecté pour les vecteurs ; normalisation ; contrôle ; `hash`, `serial`).
2. `VoucherBatch.parse/verify(text, trustedUpdateKeys)` : les clés de mise à jour du propriétaire côté serveur = celles qui vérifient le catalogue de bouquets (`BundleCatalogController` : le serveur relaie sans vérifier aujourd'hui : utiliser `castbridge.signing` **public** ? Non : la clé de signature des manifestes est la clé **du serveur**. Le lot de bons est signé par la **clé du propriétaire** (clé des mises à jour / catalogues, `UpdateKeys.PUBLIC_KEYS` côté client). Côté serveur, ajouter la lecture d'une liste `castbridge.shop.owner-public-keys` (base64 Ed25519, plusieurs) **dans la classe wire** (constructeur), la propriété elle-même étant posée par w5-06 : ici, paramètre explicite.)
3. `ShopRequest.parse(text)` + accès aux champs ; la vérification de l'activation (preuve) est faite par w5-07 avec `EnvelopeVerifier` : ici seulement le format.
4. `TokenGrant` : corps, `issue(signer, …)` via `Envelope` (si `EnvelopeIssuer` n'accepte pas un type arbitraire, construire la charge comme `OrderEnvelope` le fait et signer avec `LicenseKeyring.sign`), `verify(...)` dans l'ordre de w5-02.
5. Grille : `B/licenses/PriceGrid.java` (w4-17) doit accepter `jetons|…` et `set=` : si w4-17 est fusionné, étendre **par une sous-classe/wrapper** `B/shop/wire/TokenSettings.java` qui lit les `set=` après coup (ne pas modifier `PriceGrid.java`, hors zone) ; si la vérification de signature de `PriceGrid.java` échoue sur une grille avec `set=`, c'est un défaut à signaler (le texte canonique inclut les `set=` : w5-01 le définit).
6. `ShopVectorsTest` : rejoue **tous** les cas des deux fichiers ; `verify_vectors.py` : sections `shop` et `tokens` (Python : `cryptography` Ed25519 déjà utilisée) ; `test_verify_vectors.py` : au moins un cas par type.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='ShopVectorsTest'   # vert
cd backend && ./mvnw -q -o test                            # suite complète verte
python3 tools/activation/verify_vectors.py                 # rejoue test, rental, rental-v2, agent, shop, tokens : tout vert
python3 -m unittest discover -s tools/tests -p 'test_verify_vectors.py'
git diff --quiet tools/activation/*.json && echo "vecteurs intacts (aucun vecteur modifié ici)"
```

## Cas limites
- Vecteur `build-tokens` : la graine du nonce et la clé serveur de test sont dans le fichier ; mêmes octets exigés.
- Grille sans `set=` : `TokenSettings` par défaut (0, 60, 0, 10, 5, 2, 3).

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas modifier les vecteurs ; ne pas toucher `B/licenses/**` ; pas de montant réel ; ne pas implémenter la logique métier (w5-06…08).

## Rapport
`STATUT`, classes et méthodes publiques (pour w5-06, w5-07, w5-08), écarts constatés entre Kotlin et Java (aucun attendu), méthodes manquantes demandées.
