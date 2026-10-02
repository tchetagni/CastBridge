# w4-03 — CastBridge-TV : clé d'installation (Keystore), demande v2, ouverture des boîtes v2

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après w4-01 ; déployer après w4-02, w4-05)
> **Groupe : W4a-2** (vague W4a) · prérequis : w4-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui
> **Amendement (architecte, 2026-10-02)** : lire `docs/coordination/ADDENDUM-W6-PREUVE-TV-LIEN-CLE-2026-10-02.md` (§ 2 D-W6-L2, § 3 « w4-03 »). Ce cahier **ne change pas** : il écrit la seule ligne `install=x25519|…` ; la ligne **`sign=ed25519|…`** (clé de signature `InstallSigner`) est ajoutée par **w6-12**, pas ici. La règle « `install.key` jamais réécrit sur panne du wrapper » (correctif w4-01) vaut aussi pour la graine de signature de w6-12 ; `KeystoreWrapper` sert aux deux graines sous deux alias distincts.

**Vague 4a · Effort M (≈ 1,5 j) · Statut PRÊT (après w4-01 ; déployer après w4-02 et w4-05).** Conception : `docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md` § 3, § 4, § 6. Branche `claude/sonnet-w4-03`. Rapport : `docs/agent-reports/sonnet-w4-03.md`.

## Objectif
La TV génère sa clé d'installation X25519 au premier démarrage, la garde enveloppée par une clé AES-GCM de l'`AndroidKeyStore` (repli en clair signalé si le Keystore échoue), publie sa clé publique dans la demande d'appareil (fichier `device-request.txt`, trame `DEVICE_INFO`, `GET /api/activation/request`), ouvre les boîtes v2 à l'installation d'une activation, et expose l'état de protection dans `GET /api/activation` et la page d'administration.

## Pourquoi (preuves)
- `R/ActivationCenter.kt:77` `requestText()` = `OwnerFrames.deviceInfo(deviceCode, fp)` ; `:150-157` écrit `device-request.txt` ; `:118-131` `accept` → `RentalHub.onActivation` ; `R/RentalHub.kt:39-42` `ledger.install(a, all, fingerprints, vault)` ; `R/OwnerBtHost.kt:30` `deviceInfo = { ActivationCenter.requestText() }` ; `R/RentalHub.kt:72-77` route `/api/activation/request`.
- Aucun usage d'`AndroidKeyStore` dans `android/receiver` (`grep -rln AndroidKeyStore android/receiver/src/main` vide). `minSdk = 26` : `KeyGenParameterSpec` AES-GCM disponible.
- `R/TvService.kt:808` : JSON de `/api/activation`.

## Fichiers possédés
Nouveau `R/KeystoreWrapper.kt` ; modifiés `R/ActivationCenter.kt`, `R/RentalHub.kt`, `R/TvService.kt` (**ligne `/api/activation` seulement**, `:808`), `android/core/src/main/resources/castbridge/admin.html` (une ligne d'état « Clé d'installation : protégée par le coffre Android / non protégée »). **Hors zone** : `C/**` (w4-01, w4-04), `R/LotsHub.kt`, `R/LearnHub.kt`, `R/LanguesHub.kt` (w4-04), manifeste (w1-01), outils.

## Étapes
1. `KeystoreWrapper : SecretWrapper` : alias `castbridge-install-v1`, `KeyGenerator("AES","AndroidKeyStore")`, `KeyGenParameterSpec.Builder(alias, ENCRYPT|DECRYPT).setBlockModes(GCM).setEncryptionPaddings(NONE).setKeySize(256).setRandomizedEncryptionRequired(true)` ; `wrap` = iv(12) ‖ ct ; `unwrap` null sur `AEADBadTagException`. Fabrique `KeystoreWrapper.orPlain(ctx, log)` : tente une génération + un aller-retour de 32 octets ; sur toute exception (`KeyStoreException`, `ProviderException`, `UnrecoverableKeyException`…) → `PlainWrapper` + `Log.w` « coffre Android indisponible : clé d'installation stockée sans enveloppe ». Jamais sur le fil principal au premier appel : `ActivationCenter.init` tourne déjà hors fil principal ? **Vérifier** (`PlayerActivity.onCreate` l'appelle sur le fil principal, `R/PlayerActivity.kt:96`) : faire l'aller-retour Keystore dans `RentalHub.ensure` (appelé depuis `bg`/`Thread`), pas dans `init`.
2. `RentalHub` : `ensure` crée `InstallKeyStore(File(filesDir,"rental"), KeystoreWrapper.orPlain(app))` ; expose `fun installKey(ctx): InstallKey`, `fun installProtection(ctx): String`, **`fun vault(ctx): RentalVault`** (pour w4-04 : lecture des lots scellés) ; `onActivation` passe `installKey` à `ledger.install`.
3. `ActivationCenter.requestText()` = `OwnerFrames.deviceInfo(deviceCode, fp, RentalHub.installKey(app).pub)` ; `statusFields()` ou `/api/activation` ajoute `"installKeyProtection":"keystore|plain"` et `"installId":"<16 hex>"`.
4. `admin.html` : afficher la protection (lecture de `/api/activation`, `textContent` seulement).
5. Message utilisateur à l'activation : les retours de `ledger.install` (« enveloppée pour une autre installation… ») remontent dans `notice(...)` de `TvService` via `RentalHub.onActivation` qui retourne la carte (aujourd'hui ignorée : `R/ActivationCenter.kt:128` `runCatching { RentalHub.onActivation(...) }`) → journaliser et retourner `List<String>` pour l'écran d'activation (`ActivationActivity` n'est pas possédé : exposer `ActivationCenter.lastRentalNotes`).
6. Émulateur : `files/test-factors.txt` (debug) continue de marcher ; vérifier `tools/rental-test/rental_test.py` (lecture seule ; si son flux d'émission casse à cause de la demande v2, l'indiquer : w4-02 a mis l'outil de bureau à jour, le script passe par lui).

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (SDK requis ; sinon le dire)
grep -n 'AndroidKeyStore' android/receiver/src/main/kotlin/castbridge/receiver/KeystoreWrapper.kt   # ≥ 1
grep -n 'installKeyProtection' android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt android/core/src/main/resources/castbridge/admin.html   # ≥ 2
grep -n 'fun vault(' android/receiver/src/main/kotlin/castbridge/receiver/RentalHub.kt   # 1
```
Observable (émulateur, `tools/rental-test`) : `GET /api/activation/request` contient une ligne `install=x25519|…` ; une activation avec location émise par l'outil de bureau à jour installe sa clé (« clé installée ») ; la même activation sur une **autre** installation (effacer les données, réinstaller) donne « enveloppée pour une autre installation ».

## Cas limites
- « Effacer les données » : `install.key` disparaît, l'alias Keystore aussi (ou pas : si l'alias survit, `KeystoreWrapper` doit **recréer** une clé si `unwrap` échoue, jamais rester bloqué).
- Keystore qui marche à la génération mais lève à l'`unwrap` après redémarrage (bogue connu de certains OEM) : `InstallKeyStore.loadOrCreate` régénère + journal ; les locations sont perdues (comme une réinstallation) : le dire dans le journal et dans `notice`.
- Mode `trial()`/`locked()` : `requestText()` est appelé sur l'écran verrouillé (`ActivationActivity`) : `RentalHub.installKey` doit fonctionner **verrouillé** (pas de dépendance au cœur démarré).

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas toucher le manifeste ; aucune clé en dur ; pas de `Thread.sleep` ; ne pas modifier `C/**` ; textes en français ; « CastBridge-TV ».

## Rapport
`STATUT`, comportement observé sur émulateur (Keystore OK ?), signatures exposées pour w4-04 (`vault`, `installKey`).
