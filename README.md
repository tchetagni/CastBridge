# CastBridge

Diffusion vidéo téléphone / PC vers TV. Modules Android : `:core` (Kotlin JVM, testable), `:receiver` (app TV, libVLC),
`:sender` (app téléphone, Compose). Conception du préchargement : `docs/NEXT-preload.md`.

**Quiz culture générale sur la TV** (solo à la télécommande ou multijoueur avec les téléphones, QR code, sans app à installer) : voir **`docs/QUIZ.md`**.

## Nouveautés 0.5 (branche `feat/tv-library-player`)

Détails, API et limites : **`docs/ADMIN.md`**.

- **Bibliothèque** sur la TV (grille au D-pad, miniatures, reprise « Reprendre à 12:34 », vu/non vu, déplacer, renommer, supprimer), sur le téléphone et sur la page web.
- **Accueil TV façon lanceur média** (fond flouté animé, horloge, rangées Reprendre / Récemment ajoutés / Sur la clé USB), plus de jargon à l'écran : tout le technique
  est dans MENU > « Connexion & réglages ». **Accueil du téléphone par tâches** (Envoyer une vidéo, Regarder sur la TV, Bibliothèque, Échanger des fichiers) avec
  assistant de première connexion ; l'ancien écran est sous « Avancé ».
- **Lecteur libVLC complet** : pistes audio, sous-titres (pistes et fichiers `.srt/.ass` à côté de la vidéo), décalages ±50 ms, taille des sous-titres, vitesse
  0,5-2x, format d'image, chapitres, décodage matériel/logiciel, égaliseur, listes de lecture, mémorisation par fichier ; réglages par MENU sur la TV et depuis le téléphone.
- **Échange de fichiers à pleine vitesse** dans les deux sens, avec la règle « il doit rester 1 Go libre sur la TV à la fin du transfert » (refus précis avant tout
  octet) ; téléchargement TV -> téléphone reprenable.
- **SSH sans réseau** par un tunnel Bluetooth (passerelle du téléphone, ou `tools/bt-ssh-bridge.py` sous Linux), verrouillage par appareil.
- **Bluetooth plus rapide** : il sert de canal de contrôle et bascule les données sur le Wi-Fi (réseau commun ou Wi-Fi Direct de la TV) quand c'est possible.
- **Service d'arrière-plan qui démarre avec la TV** : réception, SSH, API marchent écran fermé ; la lecture à distance ouvre l'écran si Android le permet.

## Administrer la TV (branche `feat/tv-admin`)

Voir **`docs/ADMIN.md`** (API HTTP, exemples `curl`, guide pour agent). En résumé, l'app CastBridge TV reçoit et gère
des fichiers dans son stockage privé (`getExternalFilesDir("videos")`), sans aucune permission de stockage :

| Canal | Utilisation | Notes |
|---|---|---|
| Wi-Fi (HTTP) | app téléphone, page web `http://TV:8765/`, `curl` | reprise après coupure |
| Bluetooth (RFCOMM) | app téléphone, appareil appairé | reprise, plus lent ; permissions `BLUETOOTH_CONNECT/ADVERTISE` demandées à l'exécution (Android 12+) |
| Wi-Fi Direct | MENU de la TV > activer ; le téléphone rejoint `DIRECT-CB-…` | sans routeur, `192.168.49.1:8765` |
| Clé USB / OTG | MENU > USB (scan des volumes ou sélecteur de dossier système) | copie en arrière-plan avec progression |
| Clé USB comme **stockage** | branchée : les envois vont dans son dossier d'app (cible `auto`), lecture pendant l'envoi, retrait à chaud géré | voir **`docs/STORAGE.md`** (comportement de GaiaOS non validé) |

**Service d'arrière-plan (0.5)** : CastBridge TV démarre avec la TV (option « Démarrer avec la TV ») et continue de recevoir fichiers, commandes et SSH écran
fermé ; l'écran (accueil, bibliothèque, lecteur) n'est plus qu'une vue du service. Voir `docs/ADMIN.md`, « Démarrage avec la TV ».

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

### Accès SSH / shell distant

Serveur SSH embarqué (module `:sshd`, MINA SSHD : shell, SFTP), désactivé par défaut, clés publiques uniquement : voir `docs/ADMIN.md` §9.
**Sans réseau commun**, SSH passe aussi par un tunnel Bluetooth (TV <-> passerelle du téléphone, ou `tools/bt-ssh-bridge.py` sous Linux) : `docs/ADMIN.md` §10.
Aucun binaire natif (busybox...) n'est embarqué.

## Téléchargements sur la TV (aria2)

La TV télécharge elle-même liens web (HTTP/HTTPS/FTP/SFTP multi-connexions), torrents (`magnet:`, `.torrent`) et Metalink,
pilotée depuis le téléphone (« Téléchargements sur la TV », ou Partager > Télécharger sur la TV), la télécommande (MENU >
Téléchargements) ou la page web ; les fichiers terminés vont dans la bibliothèque. Garde toujours 1 Go libre, met en pause
si la clé USB est retirée et reprend à son retour. Voir **`docs/DOWNLOADS.md`**.

Le moteur est le vrai **aria2 1.37.0** (GPL-2.0-or-later), compilé depuis ses sources officielles par
`tools/build-aria2-android.sh` (versions et SHA-256 épinglés ; détail de la compilation livrée : `tools/aria2-android-build.txt`).
Ce script et les archives qu'il désigne sont la source correspondante exigée par la GPL : ils accompagnent tout APK distribué.
## Serveur CastBridge (branche `feat/backend`)

Serveur Java Spring Boot + MySQL en Docker (`backend/`, projet Maven séparé) : mises à jour automatiques des deux apps
(manifestes signés Ed25519, APK choisi selon l'ABI, déploiement progressif), banque de questions du quiz
(synchronisation incrémentale, tirages), suivi des appareils (enregistrement, heartbeats, plantages) et interface
d'administration web `/admin`. Déploiement : `backend/README.md` ; routes et formats : `docs/API-SERVER.md`.
Clients testés côté `:core` : `castbridge.core.update` et `castbridge.core.device`.

## Compilation

Gradle 8.13 / 8.14 installé localement (pas de wrapper dans le dépôt), SDK 35 :

```sh
cd android
gradle :core:test :sshd:test :receiver:assembleDebug :sender:assembleDebug
```

La TV d'Esaie n'accepte que l'APK `armeabi-v7a` (32 bits) : le module `:receiver` produit des APK séparés `armeabi-v7a` et
`arm64-v8a` ; installer le premier (par clé USB, via l'explorateur multimédia de la TV).
