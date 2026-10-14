# CastBridge

**CastBridge MBOKO** : relier le téléphone à la télévision, pour le Cameroun et l'Afrique francophone.

## Les deux applications

**CastBridge** (téléphone, module `:sender`) :

- envoie des vidéos à la TV (copier, copier et lire, « Ouvrir avec ») et sert de télécommande ;
- donne accès au Quiz, à Apprendre, aux Langues et aux échecs ;
- sert de passerelle Internet Bluetooth (relais Internet de la TV).

**CastBridge-TV** (module `:receiver`) :

- lit les vidéos (lecteur libVLC) et range les fichiers reçus dans une bibliothèque ;
- propose le Quiz, Apprendre, les Langues et les échecs ;
- télécharge elle-même (aria2, voir plus bas) ;
- s'administre à distance par SSH (`:sshd`).

## Modules

| Module ou dossier | Rôle |
|---|---|
| `:core` | Cœur Kotlin JVM sans Android : protocoles, moteurs (Quiz, échecs, apprentissage), clients serveur. Environ 5 800 tests (`@Test`). |
| `:sshd` | Serveur SSH embarqué (Apache MINA SSHD). |
| `:receiver` | CastBridge-TV (libVLC). Produit des APK `armeabi-v7a` et `arm64-v8a` ; `armeabi-v7a` est obligatoire pour la TV de référence (GaiaOS 32 bits). |
| `:sender` | CastBridge, l'application téléphone (Compose). |
| `:ownerlib`, `:owner` | Console propriétaire des activations. Jamais publiée. |
| `:devbridge` | CastBridge Dev, outil de développement (SSH, installation). Jamais distribué. |
| `:activation-desktop` (`tools/activation-desktop`) | Outil de bureau des activations. |
| `:server-play` (`server-play/`) | Service de jeu en ligne castbridge-play (JDK 21). |
| `backend/` | Serveur Spring Boot 3.5 (Java 21, MySQL 8.4, Docker), déployé sur bridge.sti-cm.com. |
| `server/` | Ancien serveur Python de transcodage (2026-09-29). Non maintenu. |

## Activation

CastBridge-TV n'est distribuée **que verrouillée** : la compilation se fait avec `-PrequireActivation=true` (variante `-verrouillee`) et exige les clés de confiance du fichier `~/.castbridge-signing/activation-trusted-keys.txt`, hors dépôt. Sur la TV, un code de connexion à 6 chiffres s'affiche ; sur le téléphone, « Activer la TV » le reçoit. Voir `docs/TRIAL-EDITION.md` et `docs/TV-ACTIVATION-CLE-USB.md`.

## Téléchargements sur la TV (aria2)

La TV télécharge elle-même (HTTP, HTTPS, FTP, SFTP, torrents, Metalink), pilotée depuis le téléphone, la télécommande ou la page web. Elle garde toujours 1 Go libre (`docs/DOWNLOADS.md`).

Le moteur est **aria2 1.37.0** (GPL-2.0-or-later), compilé depuis ses sources officielles par `tools/build-aria2-android.sh` (versions et SHA-256 épinglés). Ce script et les archives qu'il désigne sont la source correspondante exigée par la GPL : ils accompagnent tout APK distribué.

## Compiler et tester

- Gradle 8.14.3 installé localement (pas de wrapper dans le dépôt), SDK Android 35, JDK 17 (JDK 21 pour `backend/` et `server-play`). `ANDROID_HOME` doit pointer vers le SDK.
- Android, depuis `android/` :

```sh
cd android
gradle :core:test :sshd:test :receiver:assembleDebug :sender:assembleDebug
```

- Serveur, depuis `backend/` : `./mvnw -q test`.
- Outils Python, depuis la racine : `python3 -m unittest discover -s tools/tests`.

## Versions et documentation

- `version.properties` est la source unique des numéros. Règles : `docs/RELEASES.md`. Journal : `docs/CHANGELOG.md`.
- `docs/HANDOFF.md` donne l'état courant et la passation : à lire en premier.
- Guide utilisateur : `docs/guide-utilisateur/`.

| Document | Sujet |
|---|---|
| `docs/ADMIN.md` | API HTTP de la TV et administration à distance |
| `docs/API-SERVER.md` | Routes et formats du serveur (`backend/`) |
| `docs/QUIZ.md` | Quiz sur la TV |
| `docs/LEARN.md` | Apprendre |
| `docs/LANGUES.md` | Catégorie Langues d'Apprendre |
| `docs/STORAGE.md` | Stockage sur clé USB |
| `docs/DOWNLOADS.md` | Téléchargements sur la TV (aria2) |
| `docs/REMOTE.md` | Télécommande du téléphone |
| `docs/OWNER-CONSOLE.md` | Console propriétaire |
| `docs/ACTIVATION-FORMAT.md` | Format filaire des activations |
| `docs/RELEASES.md` | Procédure de publication des versions |
| `docs/HANDOFF.md` | État courant et passation |
