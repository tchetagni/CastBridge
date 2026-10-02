# w3-12 — Alléger l'APK de CastBridge-TV (29 Mo) sans retirer de fonction

**Vague 3 · Effort M (≈ 1,5 j, mesures incluses) · Statut PRÊT.** Branche `claude/sonnet-w3-12`. Rapport : `docs/agent-reports/sonnet-w3-12.md`. Nécessite le SDK Android local (mesures) ; sans SDK, livrer les changements et le dire.

## Objectif
Réduire l'APK `armeabi-v7a` release d'au moins 15 % **sans** perte de fonction, en mesurant chaque levier : (1) règles `-keep` de ProGuard/R8 affinées (MINA SSHD, libVLC) ; (2) `libvlc` au lieu de `libvlc-all` si les modules utilisés le permettent ; (3) `jniLibs.useLegacyPackaging` réévalué ; (4) aria2 : extraction paresseuse ou saveur « téléchargements » (préparer, ne pas retirer : décision D3).

## Pourquoi (preuves)
- `android/receiver/proguard-rules.pro:2,8` : `-keep` sur **tout** `org.videolan.**` et `org.apache.sshd.**` → MINA (gros) n'est pas réduit.
- `android/receiver/build.gradle.kts:73` `libvlc-all 3.6.5` (tous modules, dont chromecast) ; `:58` `jniLibs.useLegacyPackaging = true` (extraction sur disque : double l'empreinte flash sur une TV à 2,2 Go presque pleins, `docs/HANDOFF.md` § 4) ; `:41-48` ABI `armeabi-v7a, arm64-v8a`, release `isMinifyEnabled=true`, `isShrinkResources=true`.
- `jniLibs/armeabi-v7a/libaria2c.so` 6,2 Mo (+ 8,5 Mo arm64) ; TV de référence : 1 Go de RAM, mémoire interne presque pleine.
- Audit : AR-8, §7 du rapport d'architecture.

## Fichiers possédés
`android/receiver/build.gradle.kts`, `android/receiver/proguard-rules.pro`, `android/sshd/build.gradle.kts` (règles consommateur `consumerProguardFiles` si utile), `docs/RELEASES.md` (§ « Taille et mesures »). **Hors zone** : code Kotlin, jniLibs (ne pas supprimer aria2 : D3), manifeste.

## Étapes
1. Mesure de départ : `gradle --offline :receiver:assembleRelease` (variante non verrouillée suffit pour mesurer) ; `ls -l android/receiver/build/outputs/apk/release/*armeabi-v7a*.apk` ; `unzip -l` par dossier (`lib/`, `classes*.dex`, `res/`, `assets/`) ; `apkanalyzer` si disponible. Noter dans le rapport.
2. ProGuard : remplacer `-keep class org.apache.sshd.** { *; }` par les règles ciblées (classes chargées par réflexion : `ServiceLoader`, `org.apache.sshd.common.util.security.*`, factories nommées dans `TvSshServer`) ; ajouter `-dontwarn` nécessaires ; **tester** : `:sshd:test` ne couvre pas R8 → installer l'APK release sur l'émulateur Android TV et vérifier `ssh`/`sftp` (w2-02 : SFTP média) ; même démarche pour `org.videolan.**` (garder `org.videolan.libvlc.**` JNI ; essayer de retirer le reste).
3. `libvlc` vs `libvlc-all` : vérifier les modules utilisés (`grep -rn 'org.videolan' android/receiver/src/main` : `LibVLC`, `MediaPlayer`, `Media`, `VLCVideoLayout` ?) ; si `libvlc` suffit, remplacer et tester lecture locale + streaming + sous-titres sur l'émulateur.
4. `useLegacyPackaging=false` : mesurer la mémoire au démarrage et le temps d'ouverture du lecteur sur l'émulateur (et, si disponible, sur la TV via `GET /api/sysinfo`) ; garder `true` si la lecture devient instable ; documenter.
5. aria2 : préparer un `productFlavors { full { } ; lite { } }` **non activé par défaut** (commenté ou derrière `-Pcastbridge.flavors=true`) excluant `jniLibs/*/libaria2c.so` en `lite` ; aucune build par défaut ne change.
6. `docs/RELEASES.md` § « Taille » : tableau avant/après par levier.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:assembleRelease && ls -l android/receiver/build/outputs/apk/release/ | grep v7a   # taille ≤ 85 % de la mesure de départ
cd android && gradle --offline :core:test :sshd:test        # inchangé, vert
# sur émulateur Android TV (ou TV) : installation de l'APK release, lecture d'un mp4 local, /stream depuis le téléphone, SSH/SFTP (si w2-02 fusionné) → OK
```

## Cas limites
- Une règle `-keep` manquante ne casse qu'à l'exécution : chaque retrait doit être suivi d'un test sur émulateur (lecture, SSH, Bluetooth).
- 32 bits : `libvlc` sans certains modules peut perdre un codec logiciel utilisé par des fichiers du client ; tester AVI/MKV/HEVC logiciel.

## À ne pas faire
Pas de commit sur les branches partagées, pas de publication, pas de suppression d'aria2, pas de changement de code ; ne pas activer les saveurs par défaut.

## Rapport
`STATUT`, tableau des mesures (Mo par levier), règles retirées/gardées avec justification, tests d'émulateur faits.
