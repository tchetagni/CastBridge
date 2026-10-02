# w4-01 — Cœur : X25519, clé d'installation, boîte de location v2, vecteurs v2

**Vague 4a · Effort M (≈ 2,5 j) · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md` § 3, § 5, § 6 (à lire en entier avant de coder). Branche `claude/sonnet-w4-01`. Rapport : `docs/agent-reports/sonnet-w4-01.md`. **Premier cahier de la vague 4a** : w4-02, w4-03, w4-04, w4-05 en dépendent.

## Objectif
La clé de location n'est plus enveloppée sous les empreintes (publiques) mais sous une **clé publique X25519 propre à l'installation** de la TV (ECDH + HKDF + AES-GCM), transportée dans la demande d'appareil (`install=x25519|<64 hex>`). Le cœur sait : générer et stocker la clé d'installation (enveloppée par un `SecretWrapper`), émettre une boîte `v2:…`, l'ouvrir avec la clé privée, lire encore une boîte v1 jusqu'au coucher (`V1_BOX_SUNSET_MS`), et tout cela est figé par un **nouveau** fichier de vecteurs rejouable.

## Pourquoi (preuves)
- `C/lots/RentalKeys.kt:70-79` : `makeBox` = AES-GCM sous `LotKeys.kek(empreintes)` (`C/owner/LotKeys.kt:53-56`) ; les empreintes sont dans la cible de l'activation (`C/owner/Envelope.kt:57`) et `device-request.txt`. Commentaire `:70` faux (« stay secret »).
- `C/owner/OwnerFrames.kt:49-57` : `deviceInfo`/`parseDeviceInfo` (échoue sur toute ligne autre que `factor=`). `C/owner/LicensedIssuer.kt:6-16` (`DeviceRequest`), `:41-47` (`RentalIssuing.right` → `makeBox`). `C/lots/RentalLedger.kt:107` (`openBox` avec `device`).
- Aucun X25519 dans le dépôt (`grep -rln 'X25519\|XDH' android backend tools` vide) ; `C/update/Ed25519.kt` est pur Kotlin `BigInteger` (110 lignes) : même style. `minSdk = 26` (`android/receiver/build.gradle.kts:14`) : pas de `XDH` JCE sur la TV.
- Audit : constat 1, A2-1, A2-7 ; décision **D4 = NON** (corriger).

## Fichiers possédés
Nouveaux : `C/owner/X25519.kt`, `C/crypto/SecretWrapper.kt`, `C/lots/InstallKey.kt`, `C/lots/RentalVectorsV2.kt`, `tools/activation/rental-vectors-v2.json`, `CT/owner/X25519Test.kt`, `CT/lots/InstallKeyTest.kt`, `CT/lots/RentalVectorsV2Test.kt`. Modifiés : `C/lots/RentalKeys.kt`, `C/lots/RentalLedger.kt` (signature de `install`), `C/owner/OwnerFrames.kt`, `C/owner/LicensedIssuer.kt`, `C/owner/LotKeys.kt` (KDoc de dépréciation de `DeviceKeyBox` seulement), `CT/lots/RentalTest.kt` (section enveloppe), `CT/lots/TrialWindowTest.kt` (si la signature d'`install` l'impose). **Hors zone** : `RentalApi.kt`, `RentalVault.kt`, `TvLotStore.kt`, consommateurs (w4-04), `R/**` (w4-03), outils (w4-02), `backend/`, `verify_vectors.py` (w4-05), `rental-vectors.json` v1 (**intouchable**), `Activation.kt`, `FeatureGate.kt`.

## Étapes
1. `X25519` (`object`, RFC 7748) : `clamp(k)`, `scalarMult(k: ByteArray, u: ByteArray): ByteArray` (échelle de Montgomery en `BigInteger`, `p = 2^255 − 19`, `a24 = 121665`), `publicKey(priv) = scalarMult(priv, 9)`, `sharedSecret(priv, pub)` qui **refuse** un résultat tout à zéro (retourne `null`). Tests : les deux vecteurs § 5.2, Alice/Bob § 6.1, 1 000 itérations § 5.2 (valeur publiée) **si** < 10 s, sinon 1 itération commentée.
2. `SecretWrapper` : interface `wrap/unwrap/label` ; `MemoryWrapper` (AES-GCM sur une clé en mémoire, tests), `PlainWrapper` (identité, `label = "plain"`). Pas de dépendance Android dans `C/` (`grep -rn 'import android' android/core/src/main` doit rester vide).
3. `InstallKey(priv, pub)` + `InstallKeyStore(dir, wrapper, random)` : `loadOrCreate(): InstallKey`, fichier `install.key` (format § 3 de la conception, `SafeFile`), `installId`, `protection: String` (`wrapper.label`), rechargement après redémarrage, fichier corrompu ⇒ nouvelle clé **et** journal (`loadNote`), jamais d'exception vers l'appelant.
4. `RentalKeys` : `makeBoxV2(installPub, rentalKey, productId, period, ephSeed: ByteArray)` (dérivations **exactement** § 5 de la conception ; `ephSeed` obligatoire : l'appelant fournit `SecureRandom` en production, une graine fixe dans les vecteurs) ; `openBox(box, current, productId, period, install: InstallKey? = null): BoxResult` (`sealed` : `Key(bytes)`, `OtherInstall`, `NeedsInstallKey`, `V1Expired`, `Unreadable`) ; l'ancien `openBox(...): ByteArray?` reste en surcharge dépréciée (v1 seulement) pour ne pas casser `CT/lots/RentalTest.kt` d'un coup ; `V1_BOX_SUNSET_MS = 1798761600000L` ; `isV1Accepted(issuedAt)`. Corriger le commentaire `:70`.
5. `OwnerFrames` : `deviceInfo(code, fp, installPub: ByteArray? = null)` ajoute `install=x25519|<hex>` **après** les facteurs ; `parseDeviceInfo(text): DeviceInfo?` (`code, k, fp, installPub?, unknown: List<String>`), lignes inconnues `clé=valeur` ignorées, ligne sans `=` ⇒ null ; garder `parseDeviceInfoLegacy(text): Triple<…>?` (déprécié) = ancien comportement **mais** tolérant à `install=`.
6. `LicensedIssuer` : `DeviceRequest(code, k, factors, installPub: ByteArray?)` ; `RentalIssuing.right(r, issuedAt, license, seat, device, master, installPub: ByteArray?, ephSeed: ByteArray?, allowV1: Boolean = false)` : v2 si `installPub != null`, sinon v1 **seulement si** `allowV1 && issuedAt < V1_BOX_SUNSET_MS`, sinon `IssueException("Cette TV n'a pas fourni sa clé d'installation : mettez CastBridge-TV à jour, ou forcez l'enveloppe v1 (TV ancienne)")`. `IssueSpec` : `boxV1: Boolean = false`. `LicensedIssuer.issue` passe `device.installPub` et une graine `SecureRandom`.
7. `RentalLedger.install(activation, all, device, vault, install: InstallKey? = null)` : utilise `openBox(..., install)` ; messages : « clé installée », « enveloppée pour une autre installation de cette TV : demandez une réémission », « enveloppe v1 périmée : refaire la clé avec un outil à jour », « clé d'installation absente ».
8. `RentalVectorsV2` + `rental-vectors-v2.json` (`format = castbridge-rental-vectors-v2`, mêmes clés/appareils/maître de test que v1 **copiés**, plus `installs: [{name, seed}]`) : cas `x25519`, `install-key`, `box-v2`, `box-v2-other-install`, `box-v2-reissue`, `box-v1-sunset`, `request-v2`, `build-activation-v2` (jeton complet avec `ephSeed`). Générateur `CASTBRIDGE_WRITE_VECTORS=1` dans `RentalVectorsV2Test` (même mécanique que `RentalVectorsTest`). ≥ 12 cas.
9. `LotKeys.DeviceKeyBox` : `@Deprecated("empreintes publiques : voir DESIGN-W4-ENVELOPPE-LOCATIONS § 1 ; non utilisé par l'application")` après `grep -rn DeviceKeyBox android --include='*.kt' | grep -v test` (si un usage existe hors tests : le signaler, ne pas déprécier).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.X25519Test' --tests 'castbridge.core.lots.InstallKeyTest' --tests 'castbridge.core.lots.RentalVectorsV2Test' --tests 'castbridge.core.lots.RentalTest' --tests 'castbridge.core.lots.RentalVectorsTest'   # vert
git diff --quiet tools/activation/rental-vectors.json && echo "v1 intact"      # v1 non modifié
python3 -c "import json;d=json.load(open('tools/activation/rental-vectors-v2.json'));print(len(d['cases']))"   # ≥ 12
grep -n 'stay secret' android/core/src/main/kotlin/castbridge/core/lots/RentalKeys.kt   # 0 hit
grep -rn 'import android' android/core/src/main   # 0 hit
cd android && gradle --offline :core:test   # suite complète verte
```

## Cas limites
- Boîte v2 dans une activation dont `issuedAt` est postérieur au coucher v1 **et** une partie v1 à côté (`v2:…;FLASH+…:…`) : refuser (une boîte est v1 **ou** v2, jamais mixte : `MALFORMED` à l'analyse `RentalLines.bounds` reste inchangé, le refus se fait dans `openBox` → `Unreadable`).
- `installPub` de 32 octets mais point de petit ordre (secret partagé nul) : `sharedSecret == null` ⇒ l'émetteur refuse (« clé d'installation invalide »), la TV `Unreadable`.
- Renouvellement (`period` identique) sur une TV réinstallée : nouvelle boîte, même `rentalKey` : test `box-v2-reissue` vérifie `fingerprintOf(key)` identique.
- `Hkdf.extract` avec clé vide (`LotKeys.kt:12`) : ne pas réutiliser ce chemin pour `shared` (32 octets non vides toujours).

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas toucher `rental-vectors.json` v1 ni `test-vectors.json` ; pas d'`import android` dans `core` ; pas de temps constant prétendu (dire dans le KDoc que l'implémentation n'est pas en temps constant et pourquoi c'est acceptable) ; textes utilisateur en français ; ne pas modifier `Activation.kt`/`FeatureGate.kt` (w4-07, autre sous-vague).

## Rapport
`STATUT`, signatures des nouvelles API (pour w4-02/03/04/05), nombre de vecteurs v2, temps du test 1 000 itérations, décisions prises sur les cas limites.
