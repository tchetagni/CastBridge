# w4-05 — Miroirs Java (serveur) et Python : demande v2 tolérée, boîte v2 rejouée

**Vague 4a · Effort M (≈ 1,5 j) · Statut PRÊT (après w4-01).** Conception : `docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md` § 4, § 5, § 6. Branche `claude/sonnet-w4-05`. Rapport : `docs/agent-reports/sonnet-w4-05.md`.

## Objectif
Le serveur accepte une demande d'appareil v2 (ligne `install=` ignorée pour l'émission de clés, mais conservée dans la réponse `GET /admin/licenses/device`), analyse une ligne `rental` dont la boîte est `v2:…` (bornes inchangées), et **prouve** l'alignement cryptographique en rejouant `rental-vectors-v2.json` (X25519 via `XDH` du JDK 17, HKDF, AES-GCM : il ouvre les boîtes de test avec la clé privée d'installation de test). Le vérificateur Python fait de même avec `cryptography`.

## Pourquoi (preuves)
- `B/licenses/DeviceIdentity.java:123-161` `parseRequest` : lignes `code=`, `k=`, `factor=` ; **vérifier** le sort d'une ligne inconnue (probablement refus) ; `B/licenses/WireActivation.java` (bornes `rental`, w1-10) ; `B/licenses/Envelope.java:107`.
- `tools/activation/verify_vectors.py:240-270` (`rental_ok`, `rental_bounds_ok`) ; w1-10 a ajouté le rejeu de `rental-vectors.json` v1 en Java (`BT/licenses/RentalVectorsTest.java`) et Python.
- `tools/activation/rental-vectors-v2.json` (w4-01) : cas `x25519`, `install-key`, `box-v2`, `box-v2-other-install`, `box-v2-reissue`, `box-v1-sunset`, `request-v2`, `build-activation-v2`.

## Fichiers possédés
`B/licenses/DeviceIdentity.java`, `B/licenses/WireActivation.java` (seulement si une règle sur `box` y existe), nouveau `B/licenses/RentalBoxV2.java` (X25519 `XDH` + HKDF + AES-GCM : `open(box, installPriv, product, period)`, `make(...)` avec graine éphémère pour les vecteurs), nouveau `BT/licenses/RentalVectorsV2Test.java`, `BT/licenses/DeviceIdentityTest.java` (ou le test existant de `parseRequest`), `tools/activation/verify_vectors.py`, `tools/requirements-dev.txt` (`cryptography` déjà listé par w1-07 : vérifier), `tools/tests/test_verify_vectors.py` (s'il existe ; sinon ne pas créer). **Hors zone** : `C/**`, `R/**`, outils Kotlin, les vecteurs (lecture seule), `ActivationService.java`, `LicenseService.java`.

## Étapes
1. `DeviceIdentity.parseRequest` : ligne `install=x25519|<64 hex>` → champ `installPub` (`Optional<byte[]>`) ; toute autre ligne `clé=valeur` inconnue : **ignorée** (compatibilité ascendante, comme le cœur) ; ligne sans `=` : refus comme aujourd'hui. `GET /admin/licenses/device` affiche « clé d'installation : présente/absente » (si le contrôleur est hors zone, l'exposer seulement dans `Request` et le dire).
2. `RentalBoxV2` : `KeyPairGenerator("XDH")`/`KeyAgreement("XDH")`, conversion clé brute ↔ `XECPublicKeySpec(NamedParameterSpec.X25519, u)` (u petit-boutiste, bit 255 masqué) et `XECPrivateKeySpec` ; HKDF réutilisé de w1-10 (`WireActivation` ou classe utilitaire existante) ; dérivations **exactement** celles de la conception § 5.
3. `RentalVectorsV2Test` : charge `../tools/activation/rental-vectors-v2.json` (chemin relatif comme `RentalVectorsTest` de w1-10), rejoue `x25519` (RFC 7748), `install-key`, `box-v2` (octets identiques avec la graine éphémère ; ouverture OK ; échec sur autre installation), `box-v2-reissue`, `request-v2` (parse), `build-activation-v2` (le jeton se vérifie avec `EnvelopeVerifier` et la ligne `rental` passe les bornes). `box-v1-sunset` : seulement « la ligne reste analysable » (le serveur n'ouvre pas de v1).
4. Python : `x25519` via `cryptography.hazmat.primitives.asymmetric.x25519`, `parse_device_request`, `box_v2_make/open`, rejeu des mêmes cas ; `python3 tools/activation/verify_vectors.py` imprime le nombre de cas v1 **et** v2.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='RentalVectorsV2Test,RentalVectorsTest,DeviceIdentityTest'   # vert
cd backend && ./mvnw -q -o test    # suite complète verte
python3 tools/activation/verify_vectors.py   # imprime « rental v2 : N cas OK » avec N ≥ 12
git diff --quiet tools/activation/rental-vectors.json tools/activation/rental-vectors-v2.json tools/activation/test-vectors.json && echo "vecteurs intacts"
```

## Cas limites
- JDK sans `XDH` (improbable en 17) : le test échoue avec un message clair, pas de repli silencieux.
- Clé publique avec bit 255 à 1 : masquer avant `XECPublicKeySpec` (RFC 7748 § 5).
- Demande v2 collée dans `/admin/licenses/issue` par le propriétaire : doit s'émettre comme avant (clé de production sans location : la boîte n'est pas nécessaire).

## À ne pas faire
Pas de déploiement, pas de commit sur les branches partagées ; ne pas modifier les vecteurs ; ne pas implémenter l'**émission** de locations côté serveur (hors périmètre : W4-C) ; aucune clé réelle dans les tests (clés de test des vecteurs).

## Rapport
`STATUT`, nombre de cas rejoués Java/Python, comportement constaté de `parseRequest` sur une ligne inconnue avant/après.
