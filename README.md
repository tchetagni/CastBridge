# CastBridge

Diffusion vidéo téléphone / PC vers TV. Modules Android : `:core` (Kotlin JVM, testable), `:receiver` (app TV, libVLC),
`:sender` (app téléphone, Compose). Conception du préchargement : `docs/NEXT-preload.md`.

## Administrer la TV (branche `feat/tv-admin`)

Voir **`docs/ADMIN.md`** (API HTTP, exemples `curl`, guide pour agent). En résumé, l'app CastBridge TV reçoit et gère
des fichiers dans son stockage privé (`getExternalFilesDir("videos")`), sans aucune permission de stockage :

| Canal | Utilisation | Notes |
|---|---|---|
| Wi-Fi (HTTP) | app téléphone, page web `http://TV:8765/`, `curl` | reprise après coupure |
| Bluetooth (RFCOMM) | app téléphone, appareil appairé | reprise, plus lent ; permissions `BLUETOOTH_CONNECT/ADVERTISE` demandées à l'exécution (Android 12+) |
| Wi-Fi Direct | MENU de la TV > activer ; le téléphone rejoint `DIRECT-CB-…` | sans routeur, `192.168.49.1:8765` |
| Clé USB / OTG | MENU > USB (scan des volumes ou sélecteur de dossier système) | copie en arrière-plan avec progression |

Sécurité : un **PIN à 6 chiffres** est généré au premier lancement de la TV et affiché sur son écran d'attente. Toutes les
routes sauf `GET /` et `GET /api/hello` l'exigent (`X-CB-Pin`), comparaison en temps constant, verrouillage 60 s par IP après
5 échecs, PIN jamais journalisé. Le téléphone mémorise le PIN par TV.

### Limites (Android, non contournées)

Une app Android normale **ne peut pas** : redémarrer la TV, changer ses réglages système, écrire hors de son stockage privé,
accéder au stockage d'autres apps, ni obtenir root. « Redémarrer » ne relance que l'app CastBridge TV. Le volume peut être
ignoré par les TV à volume fixe. Bluetooth, Wi-Fi Direct et USB dépendent du matériel et du firmware de la TV.

### DLNA : limite structurelle

Une TV DLNA pure lit la vidéo **en direct depuis le téléphone** : si le téléphone quitte le réseau, seul le tampon interne du lecteur continue à jouer.
Seul le mode CastBridge TV (données stockées sur la TV) y échappe. L'écran DLNA propose donc « Continuer sur la TV sans réseau » : copie du même fichier
vers une TV CastBridge découverte, reprise à la position DLNA courante dès que la TV a assez d'avance, puis arrêt du flux DLNA.

### Non livré : accès SSH / shell distant

Le serveur SSH embarqué (MINA SSHD, shell, SFTP) n'a **pas** été implémenté dans cette branche : sa construction a été
refusée par le système de permissions de l'agent (surface d'exécution distante) et doit être décidée explicitement par le
propriétaire. Aucun code SSH n'est présent. Idem pour un binaire busybox : aucun binaire natif n'est embarqué.
L'administration à distance passe par l'API HTTP authentifiée par PIN.

## Compilation

Gradle 8.13 / 8.14 installé localement (pas de wrapper dans le dépôt), SDK 35 :

```sh
cd android
gradle :core:test :receiver:assembleDebug :sender:assembleDebug
```

La TV d'Esaie n'accepte que l'APK `armeabi-v7a` (32 bits) : le module `:receiver` produit des APK séparés `armeabi-v7a` et
`arm64-v8a` ; installer le premier (par clé USB, via l'explorateur multimédia de la TV).
