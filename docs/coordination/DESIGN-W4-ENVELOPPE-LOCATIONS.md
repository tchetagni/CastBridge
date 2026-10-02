# Conception W4-A : enveloppe des clés de location v2 (clé d'installation X25519) et lots chiffrés au repos sur la TV

> **Statut : conception (2026-10-02), à exécuter par les cahiers `sonnet-w4-01` à `sonnet-w4-06`.** Remplace `sonnet-w1-13` (qui acceptait la limite : **décision D4 = NON**, la faille se corrige). Lecture préalable : `docs/coordination/AUDIT-PROJET-2026-10-02.md` (constats 1 et 5, A2-1, A2-2, A2-3, A2-7), `docs/RENTAL-LOTS.md` § 3 et § 11, `docs/ACTIVATION-FORMAT.md` § 1 et § 5.2.
> Chemins : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `B/` = `backend/src/main/java/castbridge/server/`. Aucun secret ici.

## 1. Le défaut, exactement

- `C/lots/RentalKeys.kt:70-79` (`makeBox`) enveloppe la clé de location sous une KEK `LotKeys.kek(empreintes)` (`C/owner/LotKeys.kt:53-56`) : HKDF des **empreintes** des facteurs. Ces empreintes sont **publiques** : lignes `factor=TYPE|empreinte` de la cible de toute activation (`C/owner/Envelope.kt:57`), de `device-request.txt` (`R/ActivationCenter.kt:150-157`), de la trame `DEVICE_INFO` (`C/owner/OwnerFrames.kt:49-50`). Le fichier `activation` + le lot scellé suffisent donc, sans la TV, à ouvrir la boîte (`openBox`, `:82-91`) puis le lot (`open`, `:48-54`), pour toujours. La destruction de clé (`RentalVault.destroyKey`) ne protège rien contre qui a gardé le jeton.
- `C/lots/RentalApi.kt:34-39` : le lot reçu scellé est **déchiffré puis réécrit en clair** (`.plain` renommé en `.part`) et installé tel quel par `TvLotStore.installReceived` (`C/lots/TvLotStore.kt:150-158`) → `LearnLotConsumer.install` copie `lot.zip` en clair (`C/learn/LearnLotConsumer.kt:60`), `LangLotConsumer` idem (`:56`), Quiz idem (`C/quiz/QuizLots.kt:283,290`). `RentalVault.putLot/readLot` (`C/lots/RentalVault.kt:67-78`) ne sont appelés que par les tests (`CT/lots/RentalTest.kt:334,387-391,593`).
- Le manifeste de la TV n'a pas `allowBackup="false"` (`android/receiver/src/main/AndroidManifest.xml:56-57` : seulement `fullBackupContent`/`dataExtractionRules`, et `res/xml/backup_rules.xml` n'exclut que `trusted_phones*` et les rapports parentaux). Cahier **w1-01** (vague 1) le corrige ; W4-A en dépend et le vérifie.
- `LotKeys.DeviceKeyBox` (`C/owner/LotKeys.kt:59-78`) a le même défaut ; il n'est utilisé par aucun chemin de l'application (vérifier par `grep -rn DeviceKeyBox android --include='*.kt'` hors tests) : il est **déprécié**, pas migré.

## 2. Objectif et modèle de menace honnête

**Objectif.** Un lot loué ne doit s'ouvrir que sur la TV **installation** qui a demandé la location, pendant la location. Ni le jeton d'activation, ni la demande d'appareil, ni une copie des fichiers de la TV (sauvegarde, SFTP, clé USB) ne doivent suffire à le lire ; après l'échéance, les fichiers restants sont du bruit même pour qui a tout copié **pendant** la location, sauf s'il a aussi copié la clé privée d'installation en clair (voir ci-dessous).

**Ce que la v2 garantit.**
1. La boîte (`box`) ne s'ouvre qu'avec une **clé privée X25519 propre à l'installation**, générée sur la TV, jamais transmise, jamais écrite dans un jeton.
2. Les empreintes ne servent plus qu'à **lier** l'activation au matériel (k parmi n, inchangé) ; elles n'entrent plus dans aucune dérivation de clé.
3. Au repos, chaque lot (loué ou non) est chiffré sous une clé de lot aléatoire, elle-même enveloppée : pour un lot **loué**, sous la clé de contrat (dérivée de la clé de location, détruite à l'échéance) ; pour un lot **acheté/libre/essai**, sous la clé de magasin de la TV. Le coffre Android (`AndroidKeyStore`, AES-GCM, clé non exportable, API ≥ 23 ; `minSdk = 26`) enveloppe la clé privée d'installation et la clé de magasin.
4. Une copie de `files/` (adb backup, SFTP) sans le coffre Android ne donne que des chiffrés : la clé privée d'installation et la clé de magasin y sont enveloppées par une clé qui ne quitte jamais le Keystore et n'est pas sauvegardée.

**Ce que la v2 ne garantit pas (à écrire dans RENTAL-LOTS § 11).**
- Une TV **rootée** ou en `userdebug` : qui lit la mémoire du processus pendant la location lit la clé de location et le lot en clair. Inchangé (TRIAL-EDITION § 9).
- Un Keystore **défaillant** (certaines boîtes GaiaOS) : repli `PlainWrapper` (clé enveloppée… par rien, fichier privé 0600) ; l'état est visible dans `GET /api/activation` (`installKeyProtection: "keystore" | "plain"`) et sur la page d'administration. Le repli garde la propriété 1 (le jeton seul n'ouvre rien) mais perd la propriété 4 pour cette TV.
- Le **shell SSH** livré en production (décision D5 = NON : il reste) : un client avec son PIN peut lire `files/` ; il obtient des chiffrés + une clé privée enveloppée par le Keystore ; il ne peut pas appeler le Keystore sans exécuter du code dans le processus de l'app. Avec `run-as` impossible (app non débogable) c'est fermé ; avec le shell **de l'app** (`sh -i` sous l'uid de l'app, `sshd/.../TvSshServer.kt:122,125`) un script Kotlin n'est pas exécutable, mais `keystore` CLI non plus sur un appareil non rooté : acceptable, à documenter comme résiduel.
- La **clé d'émission** reste le bien le plus précieux (w2-01, révocation) : une clé d'outil volée émet des activations, pas des lots lisibles sans la TV.

## 3. Clé d'installation (`InstallKey`)

- **Génération** : au premier `ActivationCenter.init` (ou `RentalHub.ensure`), `InstallKey.generate(random)` : graine 32 octets → clé privée X25519 (clampée RFC 7748 § 5) et clé publique 32 octets.
- **Stockage** : `files/rental/install.key`, texte :
  ```
  castbridge-install-key-v1
  pub=<64 hex>
  wrap=<keystore|plain>
  priv=<hex du blob enveloppé>
  createdAt=<ms>
  ```
  écrit par `SafeFile` ; `priv` = `SecretWrapper.wrap(privé)`. `SecretWrapper` (interface cœur, `C/crypto/SecretWrapper.kt`) : `wrap(plain: ByteArray): ByteArray`, `unwrap(blob: ByteArray): ByteArray?`, `val label: String`. Implémentations : `MemoryWrapper` (tests), `PlainWrapper` (repli), `R/KeystoreWrapper.kt` (AndroidKeyStore, alias `castbridge-install-v1`, `KeyGenParameterSpec` AES-256 GCM, `setRandomizedEncryptionRequired(true)`, blob = iv(12) ‖ ct+tag).
- **Identifiant public** : `installId = hex(SHA-256(pub)[0:8])` (16 hex), affiché nulle part au client ; dans l'AAD des boîtes et dans les journaux (pas de donnée personnelle).
- **Ce qui survit** : la clé survit aux redémarrages et aux mises à jour. **« Effacer les données » ou désinstaller la perd** (fichier supprimé ; l'alias Keystore est effacé avec les données de l'app). Conséquence : toutes les locations (clés du coffre, carnet) sont perdues ; relire le fichier `activation` de la clé USB redonne l'activation (droits d'achat, durée de clé) mais ses boîtes de location sont illisibles (« enveloppée pour une autre installation »). **Flux de réémission** : le client renvoie une **nouvelle demande d'appareil** (même code, nouvelle clé publique) ; l'émetteur réémet **la même location** (`--periode` = la `period` d'origine) : même `rentalKey` (dérivée du maître, de la licence, du poste, du produit et de la `period`), donc **les lots déjà scellés restent valables** (le téléphone n'a pas à les resceller) ; seule la boîte change. La fenêtre d'essai (« une seule fois ») est perdue : limite déjà documentée (A2-5).
- **Pas dans le code d'appareil** : le code reste calculé sur les empreintes (stable à travers une réinstallation, ce qui est voulu : un poste = un matériel).

## 4. Demande d'appareil v2

Texte `OwnerFrames.deviceInfo` (`C/owner/OwnerFrames.kt:49-50`) : on **ajoute une ligne après les facteurs** :
```
code=XXXX-XXXX-XXXX-XXXX
k=<n>
factor=TYPE|<32 hex>        (ordre canonique, inchangé)
install=x25519|<64 hex>     (nouvelle ligne, clé publique d'installation)
```
- `OwnerFrames.parseDeviceInfo` (`:52-57`) retourne aujourd'hui `Triple` et échoue (`runCatching` → null) sur toute ligne non `factor=`. Nouvelle API : `OwnerFrames.parseDeviceInfo(text): DeviceInfo?` avec `DeviceInfo(code, k, fp, installPub: ByteArray?, unknown: List<String>)` ; les lignes `clé=valeur` inconnues sont **ignorées** (compatibilité ascendante) ; `Triple` conservé en surcharge dépréciée pour ne pas casser `S/OrdersRuntime.kt:43` et `C/owner/OwnerCli.kt:189`.
- `DeviceRequest` (`C/owner/LicensedIssuer.kt:6-16`) gagne `installPub: ByteArray?` ; `DeviceRequest.parse` tolère l'absence (demande v1 d'une vieille TV).
- Côté serveur : `B/licenses/DeviceIdentity.parseRequest` (`:127-161`) doit **ignorer** la ligne `install=` (vérifier le comportement actuel sur une ligne inconnue ; le serveur n'émet pas de location : il n'a pas besoin de la clé). Côté Python : `verify_vectors.py` reçoit un analyseur `parse_device_request` pour les vecteurs.
- **Ordre de déploiement** (important) : les outils (cœur, console, bureau, serveur, Python) apprennent la v2 **avant** que la TV ne l'émette (w4-01, w4-02, w4-05 avant w4-03). Une TV v2 face à un vieil outil : la demande est illisible (« Demande d'appareil illisible ») : message clair, pas de corruption silencieuse.

## 5. Boîte v2 (`box`)

Ligne filaire inchangée (`RentalLines.line`, 10 champs ; regex `BOX = [A-Za-z0-9_;:+-]{0,4096}`, `C/lots/RentalLines.kt:26`). Le champ `box` prend une **nouvelle syntaxe**, reconnaissable à son préfixe :
```
v2:<base64url(clé publique éphémère, 32 o)>:<base64url(nonce 12 o ‖ AES-256-GCM(rentalKey))>
```
(≈ 44 + 1 + 80 caractères ; pas de `;`). Une boîte v1 reste `NOM+NOM:<b64url>;…` (sans préfixe `v2:` : `v2` n'est pas un `FactorKind`, donc pas d'ambiguïté).

**Dérivations (identiques Kotlin/Java/Python, figées par les vecteurs).**
- `eph` = paire X25519 éphémère (graine 32 octets aléatoire ; **dans les vecteurs, la graine est donnée** pour que « mêmes entrées → mêmes octets »).
- `shared = X25519(ephPriv, installPub)` (32 o ; refuser un résultat tout à zéro, RFC 7748 § 6.1).
- `kek = HKDF-SHA256( extract(sel = "castbridge-rentalbox-v2", ikm = shared), info = "kek|" + hex(installPub) + "|" + hex(ephPub) + "|" + produit + "|" + period, 32 )`.
- `nonce = HKDF( extract("castbridge-rentalbox-nonce-v2", kek), "nonce", 12 )` (unique parce que `eph` l'est).
- `aad = "castbridge-rentalbox-v2|" + produit + "|" + period + "|" + hex(installPub)`.
- Blob = `nonce ‖ AES-256-GCM(kek, nonce, aad, rentalKey)`.
- `Hkdf.extract/expand` existent (`C/owner/LotKeys.kt:11-19`) ; le serveur (`B/…`) et Python ont déjà leurs HKDF (w1-10, RENTAL-LOTS § 10.4).

**Ouverture sur la TV** : `RentalKeys.openBox(box, current: Fingerprints, productId, period, install: InstallKey?)` : si `box` commence par `v2:` → nécessite `install` ; recalcule `shared`, `kek`, vérifie l'AAD (qui lie la boîte à **cette** clé publique : une boîte d'une autre installation échoue avec un message précis « enveloppée pour une autre installation de cette TV : demandez une réémission ») ; sinon chemin v1 (voir § 6).

**X25519 pur Kotlin** : le cœur n'a pas de `XDH` (Android ne l'expose qu'à partir de l'API 33 ; `minSdk = 26`), mais a déjà `castbridge.core.update.Ed25519` en `BigInteger` (110 lignes). Nouveau `C/owner/X25519.kt` : échelle de Montgomery RFC 7748 § 5 (≈ 70 lignes, `BigInteger`), `scalarMult(k, u)`, `publicKey(priv)`, clampage, vecteurs RFC 7748 § 5.2 (deux vecteurs) et § 6.1 (Alice/Bob). Pas de temps constant : la clé privée ne vit que sur la TV hors ligne et sur l'outil d'émission ; à noter dans le KDoc. Bureau et serveur (Java 17) : `KeyAgreement.getInstance("XDH")` avec `XECPublicKeySpec(NamedParameterSpec.X25519, u)` (u = petit-boutiste, bit 255 masqué) et `XECPrivateKeySpec` ; Python : `cryptography.hazmat.primitives.asymmetric.x25519`.

## 6. Migration et versionnage

- **Lecture rétrocompatible** : la TV ouvre encore une boîte **v1** si `issuedAt` de l'activation `< RentalKeys.V1_BOX_SUNSET_MS` (constante cœur = **2027-01-01T00:00Z**, `1798761600000`), afin que les activations déjà émises s'installent ; au-delà : « enveloppe v1 périmée : refaire la clé avec un outil à jour ». Les clés **déjà dans le coffre** ne sont jamais rouvertes (`RentalLedger.install` : « clé déjà en place », `C/lots/RentalLedger.kt:105`) : les locations en cours ne bougent pas.
- **Émission** : les outils émettent **v2 dès que la demande porte `install=`** ; sans `install=` (vieille TV) : refus par défaut (« cette TV n'a pas fourni sa clé d'installation : mettez CastBridge-TV à jour »), sauf option explicite `--enveloppe-v1` (bureau) / bascule « Enveloppe v1 (TV ancienne) » (console), acceptée seulement avant le coucher v1.
- **Vecteurs** : `tools/activation/rental-vectors.json` (v1, 41 cas, rejoué par Kotlin, Java et Python depuis w1-10) **n'est pas modifié**. Nouveau fichier `tools/activation/rental-vectors-v2.json` (`format = castbridge-rental-vectors-v2`) : cas `x25519` (RFC 7748), `install-key` (graine → pub), `box-v2` (graine éphémère donnée → octets de la boîte ; s'ouvre avec la clé privée d'installation de test ; ne s'ouvre ni avec une autre clé privée, ni avec les empreintes, ni après altération de l'AAD), `box-v2-reissue` (même `period`, deux installations → deux boîtes, même `rentalKey`), `box-v1-sunset` (v1 acceptée avant la date, refusée après), `request-v2` (texte de demande → champs), `build-activation-v2` (jeton complet, octets). Générateur : `CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*RentalVectorsV2Test*'`.
- **Serveur et Python** : le serveur n'émet pas de location (vente par point focal, W4-C) mais doit **analyser** une ligne `rental` v2 (bornes inchangées, `BOX` inchangée) et **ignorer** `install=` dans une demande ; il rejoue `x25519`, `box-v2`, `request-v2` pour prouver l'alignement (w4-05). Python idem.

## 7. Lots chiffrés au repos (`LotCrypt`)

**Format de fichier** `lot.enc` (remplace `lot.zip` dans `<root>/<scope>/v<n>/`) : `castbridge-lot-at-rest-v1\n` + `nonce(12)` + `AES-256-GCM(kLot, nonce, aad, zip)` ; `aad = "castbridge-lot-at-rest-v1|" + LotNames.key(id) + "|" + version + "|" + sha256 du zip`. `kLot` = 32 octets aléatoires **par installation de lot**, enveloppé dans `meta.json` :
```json
{ ..., "enc": "rental:<contrat>" | "store", "kwrap": "<hex nonce ‖ GCM(kek, kLot)>", "encBytes": <taille de lot.enc> }
```
- `enc = rental:<produit@period>` : `kek = RentalKeys.lotKey(vault.getKey(contrat), id, version)` (déjà défini, `C/lots/RentalKeys.kt:35`). Clé de contrat détruite ⇒ `kwrap` inouvrable ⇒ lot illisible même si le balayage n'a pas encore effacé les fichiers (et même après restauration d'une copie : la pierre tombale empêche de rouvrir la boîte).
- `enc = store` : `kek = StoreKey` (32 octets aléatoires par TV, `files/lots/store.key`, enveloppée par le même `SecretWrapper` Keystore que la clé d'installation).
- `bytes` de `meta.json` reste la taille **claire** (budget 10 Mo, `TvLotStore.usedBytes`) ; `encBytes` sert au contrôle d'intégrité du fichier (`readInstalled` compare `lot.enc.length() == encBytes` au lieu de `lot.length() == bytes`).

**Chemin d'installation.** `RentalApi.handleBody` continue d'ouvrir le lot scellé **en mémoire** pour le vérifier (le catalogue signé atteste du **clair** : taille, SHA-256) et l'écrit en clair dans `.part` **le temps de l'installation seulement** (fichier dans `files/lots/.in`, jamais sauvegardé ; supprimé par `TvLotStore.install` à la fin, `:196`). `TvLotStore.installReceived(name, proofJson, sealing: LotSealing = LotSealing.Store)` transmet au consommateur `install(meta, data, sealing)` (`LotConsumer`, `C/lots/LotApi.kt:23-28`, paramètre additif avec valeur par défaut `Store`) ; `RentalApi` passe `LotSealing.Rental(contrat)`. Le consommateur : vérifie comme aujourd'hui, génère `kLot`, écrit `lot.enc` + `meta.json` dans le dossier de mise en scène, renomme (atomique, inchangé).

**Chemin de lecture.** Un seul point d'entrée cœur : `LotOpener.open(dir: File, keys: LotKeys2): ByteArray?` (résout `enc` → `kek` via `RentalVault.getKey` ou `StoreKey` ; `null` si clé absente/détruite ou AAD faux) + `SealedZipCache` (LRU de **2 lots** décompressés en mémoire, clé `(scope, version, sha256)`, invalidé par `install/remove`) exposant `entry(name): ByteArray?` et `entries(): List<String>`. `LotReader` (`C/learn/LearnLots.kt:114,123`) gagne les surcharges `index(bytes)` et `read(bytes, …)` sur `ZipInputStream` (mêmes gardes : `MAX_ENTRIES`, tailles bornées, pas d'entrée hors index) ; `LearnLotSource.read` (`C/learn/LearnLotConsumer.kt:145-148`) et `LangLotConsumer.readPack(zip: File)` (`:107-119`) lisent via le cache. **Quiz** (`QuizLotFormat.read(file: File)`, `C/quiz/QuizLots.kt:209`) : même traitement **si le budget le permet** (étape optionnelle du cahier w4-04 ; sinon Quiz reste en clair et c'est écrit dans RENTAL-LOTS § 11 comme résiduel, les lots Quiz loués étant refusés par `RentalApi` tant que le consommateur Quiz ne scelle pas : `families`/`LotSealing` non supporté → 422 « ce type de lot ne peut pas encore être loué sur cette TV »).

**Coût sur une TV 32 bits (armeabi-v7a, Cortex-A53, Android 9-11)** : AES-GCM via Conscrypt (BoringSSL, assembleur ARM) ≈ 30-80 Mo/s sans extension crypto ; un lot Apprendre ≤ 3 Mo se déchiffre en < 100 ms, une fois par ouverture d'écran (cache). Mémoire : 2 lots × 3 Mo clairs + le zip déjà inflé par `ZipInputStream` ≈ 10-12 Mo de tas, acceptable (le constat A6-6 note déjà ≈ 30 Mo au pic pendant une réception ; le cache est vidé sous pression mémoire : `SoftReference`). Quiz (lots jusqu'à 10 Mo) : raison de l'option.

**Progression, profils, journaux** : jamais touchés (ailleurs, `C/learn/Progress.kt`).

## 8. Sauvegarde (`allowBackup`)

Dépend de **w1-01** (`android:allowBackup="false"` sur `:receiver` et `:sender`, exclusions `rental/`, `lots/`, `activations*`, `clock.txt`, `shared_prefs`). W4-A **vérifie** (critère d'acceptation de w4-04 : `grep -n 'allowBackup="false"' android/receiver/src/main/AndroidManifest.xml` = 1) et ne modifie pas le manifeste. Si w1-01 n'est pas fusionné au moment de w4-04 : l'agent le signale `STATUT: BLOQUÉ` (dépendance), n'édite pas le manifeste.

## 9. Interfaces touchées (résumé pour la matrice)

| Symbole | Fichier | Changement |
|---|---|---|
| `X25519` (nouveau) | `C/owner/X25519.kt` | RFC 7748 pur Kotlin |
| `SecretWrapper`, `MemoryWrapper`, `PlainWrapper` (nouveaux) | `C/crypto/SecretWrapper.kt` | enveloppe de secrets |
| `InstallKey`, `InstallKeyStore` (nouveaux) | `C/lots/InstallKey.kt` | génération, fichier, chargement |
| `RentalKeys.makeBoxV2/openBox(…, install)`, `V1_BOX_SUNSET_MS` | `C/lots/RentalKeys.kt` | v2 + lecture v1 bornée |
| `OwnerFrames.deviceInfo/parseDeviceInfo` → `DeviceInfo` | `C/owner/OwnerFrames.kt` | ligne `install=` |
| `DeviceRequest.installPub`, `RentalIssuing.right(…, install: ByteArray?, ephSeed: ByteArray?)` | `C/owner/LicensedIssuer.kt` | émission v2 |
| `RentalVectors` (v2) | `C/lots/RentalVectorsV2.kt` (nouveau) | rejeu |
| `KeystoreWrapper` (nouveau) | `R/KeystoreWrapper.kt` | AndroidKeyStore |
| `ActivationCenter.requestText/init`, `RentalHub.ensure/onActivation` | `R/ActivationCenter.kt`, `R/RentalHub.kt` | clé d'installation, statut |
| `RentalLedger.install(…, install: InstallKey?)` | `C/lots/RentalLedger.kt` | ouverture v2 |
| `LotCrypt`, `LotOpener`, `SealedZipCache`, `StoreKey`, `LotSealing` (nouveaux) | `C/lots/LotCrypt.kt` | au repos |
| `LotConsumer.install(meta, data, sealing)` | `C/lots/LotApi.kt` | paramètre additif |
| `TvLotStore.installReceived(…, sealing)` | `C/lots/TvLotStore.kt` | transmission |
| `RentalApi` | `C/lots/RentalApi.kt` | `LotSealing.Rental` |
| `LearnLotConsumer`, `LangLotConsumer`, `LotReader`, (`QuizLotConsumer`) | `C/learn/*.kt`, `C/langues/*.kt`, (`C/quiz/QuizLots.kt`) | écriture/lecture chiffrées |
| Console, bureau, `OwnerCli` | `android/ownerlib/…/ConsoleActivity.kt`, `tools/activation-desktop/…`, `C/owner/OwnerCli.kt` | v2, refus sans `install=` |
| `DeviceIdentity.parseRequest`, `WireActivation` | `B/licenses/*.java` | tolérance + vecteurs |
| `verify_vectors.py` | `tools/activation/` | vecteurs v2 |

## 10. Décisions prises ici (le propriétaire peut les renverser)

- **Coucher de la v1** : 2027-01-01. Avant : v1 lue ; après : refusée. Les contrats en cours ne sont pas touchés.
- **Pas de clé d'installation dans le code d'appareil** (le code reste matériel).
- **Repli `PlainWrapper`** si le Keystore échoue (plutôt que de refuser toute location sur ces TV), visible dans l'état.
- **Quiz au repos** : optionnel dans w4-04 ; sinon résiduel documenté et lots Quiz non louables sur la TV.
- **Secret maître** : inchangé ici (`masterFrom(signer)` par outil) ; la vente par point focal (W4-C, § 5 de `DESIGN-W4-VENTE-TERRAIN.md`) introduit un maître explicite du propriétaire ; la v2 de la boîte lui est indifférente (elle enveloppe la clé de location quelle que soit sa dérivation).
