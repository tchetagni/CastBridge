# w1-01 — Sauvegardes Android fermées et super-admin désactivé par défaut

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT
> **Groupe : W1-A** (vague W1) · prérequis : aucun · porte : `python3 -m unittest discover -s tools/tests -p 'test_backup_rules.py'`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : oui

**Vague 1 · Effort S (≈ 3 h) · Statut PRÊT.** Branche `claude/sonnet-w1-01` depuis `origin/integration/agents`. Rapport : `docs/agent-reports/sonnet-w1-01.md`.

## Objectif
1. CastBridge-TV (`:receiver`) et CastBridge (`:sender`) déclarent `android:allowBackup="false"` et des règles d'exclusion explicites (ceinture et bretelles) pour tout état lié aux licences, locations, essai, coffre propriétaire et PIN.
2. Le hachage bcrypt du super-administrateur n'est **plus compilé par défaut** dans l'APK téléphone : opt-in explicite.
3. Un test Python vérifie que les règles XML excluent bien les chemins sensibles.

## Pourquoi (preuves)
- `android/receiver/src/main/AndroidManifest.xml:56-57` : `fullBackupContent`/`dataExtractionRules` déclarés mais **aucun `allowBackup`** (donc `true`). `res/xml/backup_rules.xml` n'exclut que `trusted_phones.txt*` et les rapports parentaux : `activations.txt`, `clock.txt`, `rental/`, `lots/`, `shared_prefs` sont sauvegardés. Sur GaiaOS (Android ≤ 11 pour `adb backup`, 14 ici mais le transfert d'appareil reste), `adb backup` puis `restore` rembobine une location ou la fenêtre d'essai « une fois ».
- `android/sender/src/main/AndroidManifest.xml` : pas d'`allowBackup` ; `android/ownerlib/src/main/kotlin/castbridge/owner/OwnerStore.kt:14` affirme « excluded from every backup » alors que `owner-vault.txt` (`:18`) et `owner_guard` (`:21`) ne sont exclus nulle part : le coffre de signature part dans la sauvegarde Google.
- `android/ownerlib/build.gradle.kts:9-13` : le hachage `~/.castbridge-signing/superadmin.bcrypt` est lu **sauf** `-Pcastbridge.noSuperAdmin=true` → toute APK téléphone construite sur le Mac du propriétaire embarque un hachage attaquable hors ligne.
- Audit : `docs/coordination/RECOMMANDATIONS-FABLE-2026-10-02.md` SE-2, SE-10.

## Fichiers possédés
`android/receiver/src/main/AndroidManifest.xml`, `android/receiver/src/main/res/xml/backup_rules.xml`, `android/receiver/src/main/res/xml/data_extraction_rules.xml`, `android/sender/src/main/AndroidManifest.xml`, `android/sender/src/main/res/xml/*.xml`, `android/ownerlib/build.gradle.kts`, `android/ownerlib/src/main/kotlin/castbridge/owner/OwnerStore.kt` (**commentaire seulement**), nouveau `tools/tests/test_backup_rules.py`, `docs/OWNER-CONSOLE.md` (ajouter un § « Sauvegarde et super-admin »).
**Hors zone** : tout le reste (en particulier `ActivationCenter.kt`, `RentalVault.kt`, `TvService.kt`, les workflows CI).

## Étapes
1. Receiver : ajouter `android:allowBackup="false"` sur `<application>`. Dans `backup_rules.xml` et `data_extraction_rules.xml` (les deux balises `cloud-backup` et `device-transfer`), exclure : `activations.txt`, `activations.txt.bak`, `clock.txt`, `grace_prompt.txt`, domaine `file` chemin `rental/`, `lots/`, `learn/`, `ssh/`, `policy/`, et domaine `sharedpref` `castbridge_tv.xml` + prefs parentales déjà listées. Garder les exclusions existantes.
2. Sender : même attribut ; exclure `owner-vault.txt`, `owner_guard.xml` (sharedpref), `castbridge_trust.xml` (déjà), `orders/`, `lots/`.
3. `ownerlib/build.gradle.kts` : inverser le défaut — le hachage n'est inclus **que si** `-Pcastbridge.superAdmin=true` (nouvelle propriété) ; `-Pcastbridge.noSuperAdmin=true` reste accepté (sans effet). Mettre à jour le commentaire du fichier et `OwnerStore.kt:14`.
4. `tools/tests/test_backup_rules.py` (unittest, bibliothèque standard) : parse les 4 XML, vérifie `allowBackup="false"` dans les 2 manifestes, et que chaque chemin de la liste ci-dessus apparaît en `<exclude>` dans `backup_rules.xml` et dans les deux sections de `data_extraction_rules.xml`.
5. Documenter dans `docs/OWNER-CONSOLE.md` : « la build propriétaire se fait avec `-Pcastbridge.superAdmin=true` ; toute autre build n'a pas d'entrée super-admin ».

## Critères d'acceptation
```sh
python3 -m unittest discover -s tools/tests -p 'test_backup_rules.py'     # vert
grep -c 'allowBackup="false"' android/receiver/src/main/AndroidManifest.xml android/sender/src/main/AndroidManifest.xml   # 1 et 1
grep -n 'superAdmin' android/ownerlib/build.gradle.kts                      # défaut = absent
cd android && gradle --offline :receiver:assembleDebug :sender:assembleDebug   # compile (si le SDK est présent ; sinon le dire)
```
Comportement observable : `adb backup -f x.ab castbridge.receiver` sur un appareil produit une archive sans données d'app (ou refusée) ; la build téléphone par défaut ne contient pas la chaîne `$2a$` (bcrypt) : `unzip -p sender-debug.apk classes*.dex | grep -c '\$2a\$'` → 0 (vérifier sur le dex, ou sur `BuildConfig` généré).

## Cas limites
- Android 12+ ignore `fullBackupContent` au profit de `dataExtractionRules` : les deux fichiers doivent porter les mêmes exclusions.
- `allowBackup="false"` désactive aussi le transfert d'appareil (voulu : les activations sont liées au matériel).
- Ne pas exclure `trusted_phones*` deux fois avec des syntaxes différentes (doublon toléré mais inutile).

## À ne pas faire
Ne pas commiter/pousser sur `integration/agents` ni `main` ; ne pas déployer ; ne jamais lire ni citer `~/.castbridge-signing/*` ; ne pas renommer les modules ; textes utilisateur en français ; dire « CastBridge » / « CastBridge-TV ».

## Rapport (format)
`STATUT: TERMINÉ` ; fichiers touchés ; sortie des 3 commandes ; ce qui n'a pas été compilé ; question éventuelle.
