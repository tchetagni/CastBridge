# protect-01 — Déploiement TV uniquement au niveau du paquet + champs BuildConfig

**Modèle recommandé : sonnet** (logique Gradle + manifestPlaceholders + exceptions dev à ne pas casser).
**Vague A (fondation).** Les cahiers protect-02/03/04/06/08 consomment les champs BuildConfig/placeholders définis ici.

## But
Faire que l'APK `release` verrouillé s'installe/affiche son icône **uniquement sur un téléviseur**, tout en gardant verts l'émulateur, les builds debug et `adb install` sur la TV de référence. Ajouter les champs BuildConfig dont dépendent les contrôles d'exécution et le filigrane.

## Fichiers possédés (ne rien éditer d'autre)
- `android/receiver/build.gradle.kts`
- `android/receiver/src/main/AndroidManifest.xml`

## Étapes
1. **build.gradle.kts** :
   - Définir un drapeau dev : `val devBuild = (findProperty("castbridge.devBuild") as String?) == "true"`.
   - `manifestPlaceholders["leanbackRequired"] = (!devBuild).toString()` (donc `true` en release normal, `false` en dev/émulateur).
   - Ajouter `buildConfigField("boolean", "DEV_BUILD", devBuild.toString())`.
   - Ajouter `buildConfigField("String", "EXPECTED_SIG_SHA256", "\"${(findProperty("castbridge.expectedSig") as String?) ?: ""}\"")` (vide = épinglage ignoré ; sera rempli après D12 keystore).
   - Ajouter `buildConfigField("String", "BUILD_WATERMARK", "\"${(findProperty("castbridge.watermark") as String?) ?: "dev"}\"")` (identifiant de build/parc ; filigrane protect-06).
   - Ne PAS toucher aux champs existants (`REQUIRE_ACTIVATION`, `TRUSTED_KEYS`, grâce, etc.).
2. **AndroidManifest.xml** :
   - `<uses-feature android:name="android.software.leanback" android:required="${leanbackRequired}" />` (remplace l'actuel `required="false"`).
   - Laisser `touchscreen required="false"` ; ajouter `<uses-feature android:name="android.hardware.telephony" android:required="false" />` s'il manque.
   - Activité principale (`PlayerActivity`) : conserver **`LEANBACK_LAUNCHER`** et **retirer la catégorie `LAUNCHER`** du filtre, pour qu'elle n'apparaisse plus sur un lanceur de téléphone. Pour garder l'install/debug commode, ajouter un `<activity-alias>` **dev seulement** portant `LAUNCHER`, activé par un placeholder (`android:enabled="${devBuild}"` via `manifestPlaceholders["devBuild"]`), OU documenter que le lancement dev se fait par `adb shell am start`. Choisir l'option activity-alias si elle reste simple.
   - Ajouter une `<meta-data android:name="castbridge.license-notice" android:value="@string/license_notice_short" />` dans `<application>` (la chaîne sera fournie par protect-06 ; si absente au build, mettre une valeur littérale temporaire « Logiciel propriétaire — voir LICENSE-NOTICE »).

## Commandes d'acceptation
- `./gradlew :receiver:assembleRelease -Pcastbridge.versionName=test` compile (ou, à défaut d'environnement Android, `./gradlew :receiver:tasks` et vérif. que le fichier parse).
- Vérifier le manifeste fusionné : `leanback required=true` en build normal, `false` avec `-Pcastbridge.devBuild=true`.
- `grep -n "LEANBACK_LAUNCHER" AndroidManifest.xml` et confirmer que `LAUNCHER` n'est plus dans le filtre principal (hors alias dev).

## Cas limites / à préserver
- Émulateur et `adb install` : avec `-Pcastbridge.devBuild=true`, l'app reste lançable (alias LAUNCHER ou `am start`).
- Ne pas casser les splits ABI ni `REQUIRE_ACTIVATION`.
- `EXPECTED_SIG_SHA256` vide par défaut : aucun effet tant que D12 n'a pas fourni l'empreinte.

## Ne PAS faire
- Ne pas introduire de dépendance native. Ne pas modifier d'autres modules. Ne pas committer.

## Format de rapport
Diffs par fichier, sortie des commandes d'acceptation, confirmation que dev/émulateur restent verts, valeurs des nouveaux champs BuildConfig.
