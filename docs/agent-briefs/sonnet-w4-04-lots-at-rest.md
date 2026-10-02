# w4-04 — Lots chiffrés au repos sur la TV (`LotCrypt`, lecture déchiffrée en mémoire, lots loués scellés sous la clé de contrat)

**Vague 4a · Effort L (≈ 3,5 j) · Statut PRÊT (après w4-01 et w4-03 ; dépend de w1-01 fusionné pour `allowBackup`).** Conception : `docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md` § 7, § 8. Branche `claude/sonnet-w4-04`. Rapport : `docs/agent-reports/sonnet-w4-04.md`.

## Objectif
Plus aucun lot en clair dans `files/lots/` : chaque lot installé est `lot.enc` (AES-256-GCM sous une clé de lot aléatoire) + `meta.json` qui porte la clé de lot **enveloppée** : sous la **clé de contrat** pour un lot loué (détruite à l'échéance ⇒ illisible), sous la **clé de magasin** de la TV (enveloppée par le Keystore) pour tout autre lot. Apprendre et Langues lisent via un ouvreur unique avec cache mémoire (2 lots). `RentalApi` ne réécrit plus de clair durable.

## Pourquoi (preuves)
- `C/lots/RentalApi.kt:34-39` : `.plain` réécrit sur `.part` puis `store.installReceived` ; `C/lots/TvLotStore.kt:184` `consumer.install(meta, file)` ; `C/learn/LearnLotConsumer.kt:60` `data.copyTo(File(stage, LOT_FILE))` ; `:145-148` `ZipFile(lot)` ; `C/langues/LangLotConsumer.kt:56,107-119` ; `C/learn/LearnLots.kt:114,123` (`LotReader.index/read(File)`).
- `C/lots/RentalVault.kt:67-78` `putLot/readLot` inutilisés hors tests (`CT/lots/RentalTest.kt:334,387,593`).
- `R/LotsHub.kt:25-33` construit `TvLotStore` et les consommateurs ; `R/LearnHub.kt:84` `LearnLotConsumer(File(filesDir,"lots/learn"))`, `:101` `LearnLotSource(lots())`.
- Audit : constat 5, A2-2, A2-3, A6-6.

## Fichiers possédés
Nouveaux : `C/lots/LotCrypt.kt` (`LotCrypt`, `LotSealing`, `StoreKey`, `LotOpener`, `SealedZipCache`), `CT/lots/LotCryptTest.kt`. Modifiés : `C/lots/LotApi.kt` (`LotConsumer.install(meta, data, sealing)` **avec valeur par défaut** pour ne casser aucun autre consommateur), `C/lots/TvLotStore.kt`, `C/lots/RentalApi.kt`, `C/lots/LotAdapters.kt`, `C/learn/LearnLotConsumer.kt`, `C/learn/LearnLots.kt`, `C/langues/LangLotConsumer.kt`, `C/quiz/QuizLots.kt` (**étape 7, optionnelle**), `R/LotsHub.kt`, `R/LearnHub.kt`, `R/LanguesHub.kt`, tests `CT/LearnLotsTest.kt`, `CT/LangLotConsumerTest.kt`, `CT/lots/LotsTvTest.kt`, `CT/lots/RentalApiTest.kt`, `CT/lots/LotsTestKit.kt`, `CT/QuizLotsTest.kt` (si étape 7). **Hors zone** : `RentalKeys.kt`, `RentalLedger.kt`, `RentalVault.kt` (**ne pas retirer `putLot/readLot`** : `RentalTest` de w4-01 les utilise), `R/RentalHub.kt`/`R/ActivationCenter.kt` (w4-03 : utiliser `RentalHub.vault(ctx)` et `RentalHub.installKey`), manifeste.

## Étapes
1. `LotCrypt` : `newLotKey(random)`, `seal(plainFile, outFile, kLot, id, version, sha256)` (en flux, `CipherOutputStream`, en-tête `castbridge-lot-at-rest-v1\n`), `open(file, kLot, id, version, sha256): ByteArray?` (null si étiquette fausse), `wrapKey(kek, kLot, aad)`/`unwrapKey` (AES-GCM, nonce aléatoire). `LotSealing` : `object Store`, `data class Rental(contractKey)`. `StoreKey(dir, wrapper)` : `files/lots/store.key` (32 octets enveloppés, `SafeFile`), `loadOrCreate`. `LotOpener(vault: RentalVault, storeKey: StoreKey)` : `kekFor(meta.json)` → `RentalKeys.lotKey(vault.getKey(contrat), id, version)` ou `storeKey.bytes` ; `open(dir)`. `SealedZipCache(opener, max = 2)` : `entries(dir): Map<String, ByteArray>?` (via `ZipInputStream`, gardes `MAX_ENTRIES`/taille), `SoftReference`, `invalidate(id)`.
2. `TvLotStore.installReceived(name, proofJson, sealing = LotSealing.Store)` → `install(meta, file, sealing)` → `consumer.install(meta, file, sealing)`.
3. `LearnLotConsumer(root, …, opener: LotOpener? = null, random)` : `install` écrit `lot.enc` + `meta.json` (`enc`, `kwrap`, `encBytes`) ; `readInstalled` contrôle `lot.enc.length() == encBytes` ; `Installed.file` devient `dir` + `fun bytes(): ByteArray?` via le cache ; `index(scope)` et `LearnLotSource` lisent par le cache ; `diskBytes()` somme `lot.enc`. **Migration** : un dossier `v<n>/lot.zip` en clair (installé avant cette version) est lu tel quel **une fois** et rechiffré sur place à la première ouverture (`migrateIfPlain`), journalisé ; test.
4. `LotReader.index(bytes)`, `read(bytes, …)` (ZipInputStream, mêmes refus que la version `File`) ; la version `File` délègue.
5. `LangLotConsumer` : même traitement (`readPack(bytes)`), `pack(scope)` par le cache.
6. `RentalApi.handleBody` : ouvre le lot scellé **en mémoire**, écrit le clair dans `.part` (inbox, éphémère), appelle `store.installReceived(name, body, LotSealing.Rental(contract))`, puis **efface** `.part` quoi qu'il arrive (`finally`). Si le consommateur ne supporte pas le scellement (étape 7 non faite pour Quiz) : 422 « ce type de lot ne peut pas encore être loué sur cette TV ».
7. **Optionnel** (si ≤ 1 j restant) : `QuizLotConsumer` au même format ; sinon : `QuizLots.kt` non modifié, `LotConsumer.install` de Quiz ignore `sealing` (valeur par défaut) et **refuse** `LotSealing.Rental` (retourne `false` avec `lastError`), et le rapport le dit.
8. `LotsHub.store(ctx)` : `StoreKey(File(filesDir,"lots"), KeystoreWrapper.orPlain(app))`, `LotOpener(RentalHub.vault(ctx), storeKey)`, injectés dans les consommateurs (`LearnHub.lots()` et `LanguesHub.lotsConsumer`) ; invalidation du cache dans `remove`/`install`.
9. Tests : scellé ⇒ fichier illisible sans clé ; lot loué ⇒ illisible après `vault.destroyKey` **avant** tout balayage ; AAD (autre id/version) refusée ; cache invalidé après réinstallation ; migration d'un `lot.zip` clair ; budget 10 Mo compté sur `bytes` clair ; `RentalApiTest` : plus aucun fichier `.plain`/`.part` après installation ; corruption de `lot.enc` ⇒ `installedOne` repli sur la version précédente (comportement existant conservé).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.*' --tests 'castbridge.core.LearnLotsTest' --tests 'castbridge.core.LangLotConsumerTest' --tests 'castbridge.core.LearnLogicTest'   # vert
grep -rn 'lot\.zip' android/core/src/main/kotlin/castbridge/core/learn android/core/src/main/kotlin/castbridge/core/langues   # seulement dans migrateIfPlain / constantes de migration
grep -n 'allowBackup="false"' android/receiver/src/main/AndroidManifest.xml   # 1 (w1-01) ; sinon STATUT: BLOQUÉ (dépendance), ne pas éditer
cd android && gradle --offline :receiver:compileDebugKotlin   # si SDK
```
Observable (émulateur, `tools/rental-test`) : après livraison d'un lot loué, `adb shell run-as` impossible (release) ; sur une build debug : `files/lots/learn/<scope>/v1/lot.enc` n'est pas un zip (`unzip -l` échoue), `meta.json` porte `"enc":"rental:…"` ; après expiration + balayage, rien ne reste ; Apprendre ouvre la leçon louée pendant la location.

## Cas limites
- TV en mode réduit ou verrouillée : lecture des lots achetés inchangée (clé de magasin, pas de contrat).
- Mémoire : lot de 3 Mo + zip inflé ; mesurer `Runtime.totalMemory` dans un test de charge JVM (indicatif) ; `SoftReference` pour le cache.
- `StoreKey` perdue (effacement des données) ⇒ tous les lots achetés deviennent illisibles : ils sont de toute façon dans `files/` qui est effacé avec. Keystore cassé après redémarrage ⇒ `loadOrCreate` régénère + les lots existants sont illisibles : `TvLotStore.startup` doit **retirer** les lots dont `kwrap` ne s'ouvre pas (journal « lot illisible retiré : à renvoyer depuis le téléphone »), jamais afficher un lot vide.

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas retirer `RentalVault.putLot/readLot` ; ne pas toucher au manifeste ; pas de clair durable hors de `.in/` ; pas d'`import android` dans `C/` ; textes en français.

## Rapport
`STATUT`, étape 7 faite ou non, mesure du temps d'ouverture d'un lot de 3 Mo en JVM, formats de fichiers finaux (`meta.json` exemple), ce qui reste en clair.
