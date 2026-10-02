# protect-02 — Porte de classe d'appareil à l'exécution (TV / PAS_TV / INCERTAIN)

**Modèle recommandé : sonnet** (logique pure testable + UX de dégradation sans faux positifs).
**Vague B.** Dépend de protect-01 (`BuildConfig.DEV_BUILD`). Parallélisable avec protect-03/04 (fichiers disjoints).

## But
Refuser *courtoisement* de fonctionner sur un appareil qui n'est manifestement pas un téléviseur, sans jamais bloquer un vrai client (politique INCERTAIN = laisser passer, PD4). Deuxième barrière après le manifeste ; empêche aussi un téléphone de se comporter comme une TV.

## Fichiers possédés
- `android/core/src/main/kotlin/castbridge/core/tv/DeviceClass.kt` (**neuf**, pur Kotlin, sans dépendance Android)
- `android/core/src/test/kotlin/castbridge/core/DeviceClassTest.kt` (**neuf**)
- `android/receiver/src/main/kotlin/castbridge/receiver/DeviceClassGate.kt` (**neuf**, collecte Android + écran de dégradation)

## Point chaud partagé (édition minimale)
- `android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt` : **un seul** appel `DeviceClassGate.enforce(this)` au tout début de `onCreate` (après `super.onCreate`). Ne rien changer d'autre.

## Étapes
**Prémisse (correction propriétaire)** : cibles = **tous** les OS de smart TV (Android TV, Google TV, Fire TV/Fire OS, GaiaOS, autres firmwares TV Android). **Signaux génériques uniquement ; indépendant de l'OS et de l'ABI.** Aucune empreinte GaiaOS, aucun test sur `armeabi-v7a`/`SUPPORTED_ABIS`, aucun motif de `Build.FINGERPRINT` comme critère de décision.

1. `DeviceClass.kt` (pur) : `enum class DeviceClass { TV, NOT_TV, UNSURE }` + `fun classify(signals): DeviceClass`. Entrées = data class de signaux génériques (réutiliser les champs de `DeviceFacts`) : `isTelevisionUi` (UiModeManager TELEVISION), `hasLeanback` (FEATURE_LEANBACK), `hasTouchscreen`, `hasTelephony`, `hasDpadOrRemote` (périphériques d'entrée : D-pad/télécommande sans tactile — `Configuration.navigation`/`InputDevice`), `smallestWidthDp` + `densityDpi` (classe d'affichage grand écran). Règles :
   - **TV** si `isTelevisionUi==true` **ou** `hasLeanback==true` **ou** (`hasTouchscreen==false` **et** `hasTelephony==false`).
   - **NOT_TV** si `hasTelephony==true` **et** `hasTouchscreen==true` **et** `isTelevisionUi!=true` **et** `hasLeanback!=true` (téléphone/tablette évident).
   - **UNSURE** sinon ; les indices secondaires (D-pad sans tactile, grand écran) peuvent faire pencher UNSURE → TV, **jamais** vers NOT_TV.
   - Réutiliser `Platform.detect` pour le libellé de plateforme (android-tv/google-tv/fire-os/android-box) **sans en faire un critère** : `Platform` sert au rapport, `DeviceClass` à la décision.
2. `DeviceClassTest.kt` : vecteurs **par famille d'OS** : Android TV (leanback), Google TV (leanback + google-experience), Fire TV (Amazon, leanback, pas de tactile), GaiaOS (TELEVISION ui, pas de leanback déclaré), box Android générique sans leanback ni tactile (TV), téléphone (NOT_TV), tablette sans téléphonie (UNSURE), émulateur TV. Un test affirme que `supportedAbis`/`fingerprint` **ne changent pas** la décision.
3. `DeviceClassGate.kt` (receiver) : lit les vrais signaux Android, appelle `classify`. Si `BuildConfig.DEV_BUILD || BuildConfig.DEBUG` → ne rien faire (émulateur/dev). Sinon : `NOT_TV` → afficher un écran plein courtois « CastBridge TV est conçue pour une télévision » + contact (placeholder D7) + aucune donnée touchée, et terminer l'activité ; `UNSURE`/`TV` → laisser passer (PD4 décidé). Exposer `fun current(): DeviceClass` et **persister** la classe calculée (fichier texte simple dans `filesDir`) pour que protect-05 (battement de cœur) et protect-09 (ordre signé « classe d'appareil », cible parc INCERTAIN) la lisent. Ce cahier **ne** gère **pas** le blocage par ordre serveur (protect-09).

## Commandes d'acceptation
- `./gradlew :core:test --tests "*DeviceClassTest*"` vert.
- `grep -n "DeviceClassGate.enforce" PlayerActivity.kt` → exactement 1.
- Revue : `NOT_TV` ne se déclenche jamais pour `isTelevisionUi==true`.

## Cas limites / à préserver
- Vraie TV sans leanback déclaré (certains firmwares TV) mais sans tactile ni téléphonie → **TV** (ou au pire UNSURE = laisser passer).
- Fire TV : `manufacturer=amazon`, leanback présent → **TV** par le signal leanback, pas par la marque.
- Émulateur TV + `DEV_BUILD` → passe.
- Ne jamais effacer de données ; l'écran de refus est informatif et réversible (une TV correctement détectée ensuite fonctionne).

## Ne PAS faire
- Pas de lib native. Ne pas dupliquer `Platform.detect`. Ne pas bloquer sur UNSURE. Ne pas committer.

## Format de rapport
Fichiers neufs, diff minimal de PlayerActivity, sortie des tests, table des vecteurs classés.
