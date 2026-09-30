# CastBridge : passation (handoff)

> À tenir à jour à chaque étape. **Aucun secret ici** (mots de passe, PIN, jetons, clés) : voir « Où sont les secrets ».
> Dernière mise à jour : 2026-10-01, branche `feat/ssh`.

## 1. Ce qu'est le projet
Écosystème pour relier le téléphone à la télévision, pour le Cameroun et l'Afrique francophone (propriétaire : Esaie Tchetagni Ngassa) :
- **App TV** `castbridge.receiver` (module `:receiver`) : service d'arrière-plan `TvService` + écrans (accueil à tuiles, bibliothèque, lecteur libVLC, quiz, échecs, téléchargements aria2, Apprendre, télécommande, tests Internet).
- **App téléphone** `castbridge.sender` (module `:sender`, Compose) : envoi/copie/déplacement vers la TV, cast DLNA, lecteur multimédia (« Ouvrir avec »), télécommande, quiz, échecs, Apprendre, passerelle Internet Bluetooth.
- **Cœur** `:core` (Kotlin JVM, testé) : protocoles, moteurs (quiz, échecs, apprentissage), clients serveur.
- **SSH** `:sshd` (Apache MINA SSHD) : administration à distance de la TV.
- **Serveur** `backend/` (Java 21, Spring Boot 3, MySQL 8.4, Docker) : mises à jour signées, questions du quiz, suivi des appareils, télémétrie, interface `/admin`.
- **Charte graphique** `branding/` (non encore appliquée aux apps).

## 2. État des versions
| Élément | Version | Où |
|---|---|---|
| App TV | 0.9.1 (versionCode 19) | clé USB de la TV : `Download/CastBridge-TV-0.9.1.apk` (armeabi-v7a, sha256 `9b936bde…f2945`) ; la TV tourne encore en 0.7 tant que l'APK n'est pas installé |
| App téléphone | 1.0-beta (versionCode 8) | installée sur le Samsung S21+ d'Esaie (sans `feat/connect`) |
| Serveur | commit `992db18` | https://bridge.sti-cm.com (en ligne, sain) |

Branche d'intégration : **`feat/ssh`** (poussée sur `origin` et sur le dépôt du serveur, branche `main`). Toutes les fonctionnalités y sont fusionnées.

## 3. Branches (une par fonctionnalité, fusionnées dans `feat/ssh`)
`feat/tv-admin` (PIN, page web, USB, lecture pendant l'envoi) · `feat/tv-usb-storage` (volumes multiples) · `feat/tv-library-player` (bibliothèque, lecteur, service, UX) · `feat/tv-quiz` · `feat/tv-chess` · `feat/tv-downloads` (aria2) · `feat/phone-player` · `feat/phone-remote` · `feat/backend` · `feat/learn` (Apprendre).
`feat/parental` (contrôle parental réécrit proprement, non fusionné : voir `docs/PARENTAL.md` ; limites : un enfant peut changer d'app/lecteur, à valider sur la vraie TV) · `feat/connect` (apps branchées sur le serveur : consentement, identification, heartbeat, mises à jour signées, télémétrie, questions du quiz en ligne) est **fusionnée** dans `feat/ssh` (2026-10-01) ; jamais testée contre le serveur de production : premier enregistrement réel à vérifier.

**ATTENTION — `wip/external-ai-changes`** (non fusionnée, NON revue) : instantané de 220 fichiers trouvés non commités dans le dossier de travail, écrits par d'autres sessions d'IA (activation/licence, contrôle parental, sudoku, deux mises à jour automatiques concurrentes `AutoUpdater`/`PhoneAutoUpdater`, suivi de l'adresse Bluetooth, assets de la charte, icônes, thème). À auditer (sécurité, doublons avec `feat/connect`) avant toute intégration ; rien de cela n'a été demandé dans les sessions de référence.
`main` (GitHub) est resté à l'état initial : les fusions vers `main` sont à décider.

## 4. Où tourne quoi
- **TV de référence** : « SMART_TV » (Amlogic, GaiaOS = Android 14, **32 bits armeabi-v7a**, ~1 Go de RAM, écran logique 1280×720 à 160 dpi, mémoire interne ~2,2 Go presque pleine). Clé USB exFAT de 58 Go (écriture ~2-15 Mo/s). Les TV cibles sont de **tout type** (Android TV, Fire TV, box), pas seulement GaiaOS.
- **Téléphone de test** : Samsung S21+ (SM-G996U, Android 14). Attention : Samsung a une copie « Dual App » (utilisateur 95) où l'autorisation Bluetooth est refusée.
- **Serveur** `ubuntu@bridge.sti-cm.com` (Ubuntu 24.04, 8 Go, **partagé avec d'autres projets de production : ne pas y toucher**). Périmètre autorisé pour CastBridge (jusqu'au 2026-11-30) : `~/castbridge/` (dépôt Git nu, application, sauvegardes) et le fichier nginx `/opt/infra/nginx/conf.d/castbridge.conf`. Conteneurs `castbridge-api` (127.0.0.1:7090) et `castbridge-db` (réseau interne, jamais exposé). HTTPS par le nginx partagé `infra-nginx` (certificat Let's Encrypt de `bridge.sti-cm.com`, renouvelé par le certbot commun).
- **Sauvegardes** : cron quotidien 3 h 15 (`backup.sh`), 14 jours de rétention, dans `~/castbridge/backups`.

## 5. Commandes utiles
```sh
# Compiler / tester (Gradle 8.14.3 du cache, pas de wrapper dans le dépôt)
export ANDROID_HOME=~/Library/Android/sdk
G=$(ls -d ~/.gradle/wrapper/dists/gradle-8.14.3-bin/*/gradle-8.14.3/bin/gradle)
$G :core:test :sshd:test :receiver:assembleDebug :sender:assembleDebug
# APK : android/receiver/build/outputs/apk/debug/receiver-armeabi-v7a-debug.apk (TV), android/sender/build/outputs/apk/debug/sender-debug.apk (téléphone)

# Déployer le serveur
git push bridge feat/ssh:main
ssh ubuntu@bridge.sti-cm.com 'cd ~/castbridge/services/castbridge/backend && git -C .. fetch -q origin && git -C .. checkout -q --detach origin/main && DOCKER_CMD="sudo docker" ./deploy.sh origin/main'
# (deploy.sh : sauvegarde, build, healthcheck, retour automatique en cas d'échec ; docker-compose.override.yml local = réseau infra-net)

# Installer sur la TV sans clé USB : envoyer l'APK par l'API (PIN), puis l'installer avec la télécommande
#   PUT /upload/<nom>.apk?offset=0&total=<taille>   puis  POST /api/apk/install?names=<nom>.apk   (en-tête X-CB-Pin)
# Voir aussi : GET /api/screenshot (capture de l'écran CastBridge de la TV), SSH sur le port 2222 (clé publique enregistrée via /api/ssh/key)
```

## 6. Où sont les secrets (jamais dans Git)
- **Serveur** : `~/castbridge/services/castbridge/backend/.env` (mot de passe MySQL, jeton d'API admin, compte `admin` de l'interface web) et `secrets/castbridge-signing.pem` (clé privée de signature des mises à jour). La clé **publique** est dans `core/.../update/UpdateKeys.kt`.
- **TV** : le PIN à 6 chiffres est affiché sur l'écran de la TV (Connexion & réglages).
- **Signature des APK** : les APK actuels sont signés avec la **clé de debug du Mac d'Esaie** (`~/.android/debug.keystore`). Une mise à jour ne s'installe que si elle est signée avec la même clé. Migration vers une clé de release = une seule réinstallation manuelle (les vidéos de la clé USB sont conservées, pas celles de la mémoire interne de la TV).

## 7. Décisions prises
- Aucun paiement réel dans le quiz : **jetons virtuels** seulement (le cadre légal camerounais des jeux est à vérifier avant tout vrai paiement).
- Compte à rebours du quiz : **20 s maximum** partout ; Duel : formats Classique / Le plus rapide / Course.
- Échecs : perte au temps en compétition, coup d'office en entraînement ; compte à rebours 10-60 s.
- Stockage : règle « **au moins 1 Go libre à la fin** » pour les transferts ; clé USB prioritaire ; contenu d'Apprendre : moins de 50 Mo dans l'app (packs en ligne ou sur stockage).
- Téléchargements (aria2) : fusionnés tels quels (≈ +29 Mo installés sur la TV).
- Télémétrie : deux niveaux de consentement (essentiel / statistiques d'usage), conformité à la loi camerounaise 2024/017 (**texte à faire relire par un juriste**).
- Mot de passe admin : 12 caractères minimum imposés ; Esaie a demandé `Centos` (trop faible) : **décision en attente**.

## 8. À faire / en attente
1. Fusionner `feat/connect` puis publier les APK via `/admin` et vérifier le premier enregistrement réel d'une TV.
2. **Appliquer la charte graphique** (`branding/`) aux apps : couleurs, typographies, icônes des tuiles, icône d'app et bannière TV, anneau de focus. Avant : corriger les paires de couleurs qui échouent en WCAG AA pour le texte courant (or clair sur blanc 3,6 ; accent clair 3,3 ; blanc sur vert 3,4 ; gris `#6E7A93` sur fond sombre 4,4), refaire la mise en page du guide PDF (logos déformés aux p. 4-7 et 11), ajouter les icônes manquantes (Apprendre, Télécommande, Sur le téléphone, Internet/passerelle), envisager de distinguer le symbole d'un casque audio.
3. Clé de signature de release + secrets GitHub (`release.yml`) : à décider.
4. Contenu d'Apprendre : **124 points « à vérifier »**, tout est en brouillon, relecture par des enseignants (deux sous-systèmes) et par un agent de santé ; matières non couvertes : HG-ECM, SVT, anglais BEPC, philosophie, GCE A Level, licence ; vidéos (droits).
5. Quiz : 3 questions à relire (docs/QUIZ.md) ; serveur en ligne pour la banque.
6. Échecs en ligne : nécessite les routes du serveur listées dans `docs/CHESS.md` § 6 (non implémentées).
7. Défauts connus : « Bravo » affiché même sur une mauvaise réponse dans certaines explications d'Apprendre ; sous-titres de tuiles tronqués ; indices `C_f` des figures non rendus ; l'installation d'APK par Wi-Fi n'a pas de confirmation silencieuse garantie sur GaiaOS.

## 9. Non vérifié sur la vraie TV (à faire avec Esaie)
Démarrage automatique après redémarrage de la TV (signal de démarrage sur GaiaOS, économie d'énergie) ; « Afficher par-dessus les autres apps » et lancement depuis l'arrière-plan ; lecture pendant l'envoi (libVLC 32 bits) ; écran de réglages « Installer des apps inconnues » ; focus à la télécommande dans tous les écrans (quiz, échecs, Apprendre) ; aria2 (exécution du binaire natif, DHT, débit sur exFAT) ; SSH par Bluetooth ; télécommande (latence, service d'accessibilité) ; miniatures ; passerelle Internet Bluetooth (la liaison tombait après quelques secondes : cause exacte non trouvée, la version téléphone affiche désormais l'erreur).

## 10. Pièges rencontrés
- Un `git checkout <fichier>` annule des modifications non commitées : commiter avant.
- Les redirections de ports `adb forward` et les émulateurs sont **partagés entre sessions** : utiliser un port et un émulateur à soi.
- Le shell SSH de la TV tourne sous l'identité de l'app (pas de root) ; `/storage/<clé>/` n'accepte l'écriture que dans `Android/data/castbridge.receiver/...`, `Download/`, etc.
- Sur GaiaOS, l'ouverture d'écrans de réglages depuis un thread HTTP (arrière-plan) est ignorée : les lancer depuis l'activité visible.
- Sur Android 14, `BLUETOOTH_SCAN` absent → `cancelDiscovery()` lève une exception (déjà entouré d'un `runCatching`).

## 11. Documentation détaillée
`docs/` : ADMIN, API-SERVER, TELEMETRY, STORAGE, DOWNLOADS, QUIZ, CHESS, LEARN, PHONE-PLAYER, REMOTE ; `backend/README.md` ; `branding/guide/CastBridge-charte-graphique.pdf`.
