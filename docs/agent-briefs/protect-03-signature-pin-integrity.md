# protect-03 — Épinglage de la signature APK + auto-intégrité (contrôles redondants)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (D12 : empreinte vide = ignorée)
> **Groupe : P-B** (vague PROTECT) · prérequis : protect-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*ApkIntegrity*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Modèle recommandé : sonnet** (redondance, PackageManager multi-API, dégradation sans faux positifs).
**Vague B.** Dépend de protect-01 (`BuildConfig.EXPECTED_SIG_SHA256`). Parallélisable avec protect-02/04 (fichiers disjoints ; touche ActivationCenter, pas PlayerActivity).

## But
Détecter un APK recompressé/re-signé (repackaging) via l'empreinte du certificat de signature, à **plusieurs points d'appel indépendants** (pas de booléen unique `isLicensed()`). Rappeler que la vraie résistance vient de la dépendance cryptographique (W4), pas de ce contrôle : celui-ci **augmente le coût et la visibilité**, il ne « protège » pas à lui seul.

## Fichiers possédés
- `android/core/src/main/kotlin/castbridge/core/owner/ApkIntegrity.kt` (**neuf**, pur : compare deux empreintes hex, calcule SHA-256 d'un cert donné en bytes)
- `android/core/src/test/kotlin/castbridge/core/ApkIntegrityTest.kt` (**neuf**)
- `android/receiver/src/main/kotlin/castbridge/receiver/SelfIntegrity.kt` (**neuf**, lit `apkContentsSigners` via PackageManager, calcule l'empreinte, compare à `BuildConfig.EXPECTED_SIG_SHA256`)

## Point chaud partagé (édition minimale)
- `android/receiver/src/main/kotlin/castbridge/receiver/ActivationCenter.kt` : ajouter **un** point d'appel redondant (p. ex. dans `accept(...)` avant d'installer une activation, et/ou à l'ouverture de lot via RentalHub) qui consulte `SelfIntegrity.status()` et, si **ALTERED**, journalise + marque un drapeau (pas de blocage dur ici : la dégradation visible est gérée côté UI/heartbeat). **Réutiliser** la logique de lecture de signature déjà présente dans `UpdateInstaller.kt` (ne pas la réécrire ; factoriser si simple).

## Étapes
1. `ApkIntegrity.kt` : `fun sha256Hex(cert: ByteArray): String` ; `fun matches(expectedHex: String, certHex: String): Boolean` (comparaison constante, insensible à la casse, `expectedHex` vide ⇒ `true` = ignoré).
2. `SelfIntegrity.kt` : `enum class IntegrityStatus { OK, ALTERED, UNKNOWN }` ; `fun status(ctx): IntegrityStatus` — si `EXPECTED_SIG_SHA256` vide → `UNKNOWN` (dev/avant D12) ; sinon lit le(s) `apkContentsSigners` (API>=28) / `signatures`, calcule l'empreinte, `OK`/`ALTERED`. Mettre en cache le résultat. Aucune exception ne doit planter l'app (try/catch → `UNKNOWN`).
3. Deux points d'appel indépendants minimum (ActivationCenter + un second, p. ex. SelfIntegrity appelé aussi depuis DeviceClassGate ou au heartbeat) pour que patcher un seul site ne suffise pas.
4. Dégradation : sur `ALTERED`, message FR clair (« copie non authentique ») + chemin d'assistance ; **ne pas** détruire de données.

## Commandes d'acceptation
- `./gradlew :core:test --tests "*ApkIntegrityTest*"` vert (dont : empreinte attendue vide ⇒ matches=true ; empreinte différente ⇒ false).
- `grep -rn "SelfIntegrity.status\|SelfIntegrity.check" android/receiver` → ≥2 points d'appel distincts.
- Revue : aucun `isLicensed()` booléen central introduit.

## Cas limites / à préserver
- `EXPECTED_SIG_SHA256` vide (dev, avant D12) : `UNKNOWN`, aucun effet.
- Émulateur/debug signé avec la clé debug : ne pas afficher d'alerte (status `UNKNOWN` tant que pas d'empreinte attendue, ou whitelister la clé debug).
- Ne pas bloquer le flux d'activation en dur ; la dégradation est douce et traçable.

## Ne PAS faire
- Pas de lib native. Ne pas réécrire la lecture de signature de UpdateInstaller. Ne pas committer.

## Format de rapport
Fichiers neufs, diff minimal ActivationCenter, liste des points d'appel, sortie des tests.
