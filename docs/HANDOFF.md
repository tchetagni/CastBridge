# CastBridge : passation (handoff)

> À tenir à jour à chaque étape. **Aucun secret ici** (mots de passe, PIN, jetons, clés) : voir « Où sont les secrets ».
> Dernière mise à jour : 2026-10-01, branche `feat/ssh`. **Règle du propriétaire : ce document se met à jour en temps réel** (à chaque fusion, publication, lancement/fin d'agent, décision ou problème), commité et poussé aussitôt.

## 0. Journal en direct (le plus récent en haut)
- 2026-10-01 : branche `feat/agent-a-tv` **fusionnée** (conflits `TvService` et HANDOFF résolus en additif ; la TV 0.12.1 ne l'a PAS : nouvelle version TV à publier) : (1) `ContentGuard` branché au contrôle parental, évalué par la TV et annoncé dans `/api/library` (`guard`, `childActive`, `protected`) : un fichier protégé (au-dessus de l'âge, non classé = adulte, règle sur son nom) n'est jamais listé, renommé, déplacé, mis à la corbeille, lu ni envoyé à l'IA ; TV ancienne = tout protégé ; (2) dossiers sur la TV en **virtuel** (`FolderIndex`, `/api/folders`, `folder` dans `/api/library`, fichiers toujours à plat, anciens clients inchangés) ; vrais sous-dossiers rejetés (analyse dans `docs/LIBRARY-AGENT.md` § 13) ; (3) renommer / déplacer / corbeille durcis (aucun écrasement, rien sous un lecteur ou un envoi, règle 1 Go sur les déplacements côté TV, déplacement vérifié avec reprise après coupure). `:core:test` 707 verts, TV et téléphone compilent ; **la TV 0.12.1 installée n'a rien de tout ça : publier une nouvelle version TV** (et vérifier à la main sur la TV réelle : jamais testé hors JVM et émulateur Android TV). Doc : `docs/LIBRARY-AGENT.md` §§ 12-14.
- 2026-10-01 : `feat/quiz-no-repeat` **fusionnée** (2 conflits additifs : `core/build.gradle.kts`, `TvService`) : historique 300 parties par profil/parcours, packs de questions signés (plafond 11 Mo, cache clé USB, reprise, relais par le téléphone), pipeline `tools/quiz-bank/`, `content/quiz/dist` (25 packs, 1,74 Mo). `:core:test` 692 verts, backend vert, apps compilées. **Banque réelle** : 0 question `approved`, tout est `review`. Parties sans répétition garanties : CM2 434, 3e 357, Tle 380, L1 éco 325, L1 maths 349 (≈ 99 % variantes numériques de 33-52 modèles) ; **culture générale 156/300** (manque ≈ 1 500 Cameroun + 300 Afrique) ; **droit 7/300** (106 questions, manque ≈ 4 400). **Décision en attente d'Esaie** : les questions `review` *calculées* sont jouables avant relecture (`computedPlayable = true` dans `QuizPackFormat.read`) ; les factuelles restent bloquées jusqu'à relecture humaine. Serveur : définir `CASTBRIDGE_QUIZ_PACKS_DIR` et y copier `content/quiz/dist` pour servir les packs (non déployé).
- 2026-10-01 : accélération de l'assistant de bibliothèque (demande d'Esaie) : 3 agents en parallèle, zones disjointes : `feat/agent-a-tv` (ContentGuard ↔ contrôle parental, sous-dossiers TV, durcissement corbeille/déplacement), `feat/agent-b-phone` (analyse rapide en tâche de fond, dossier téléphone SAF, assistant guidé en 3 étapes), `feat/agent-c-ai` (corpus + jeu de test gelé, intelligence circonstancielle, IA serveur configurable sans clé dans le dépôt). Fusion une par une, avec tests, à leur fin.
- 2026-10-01 : `feat/library-agent` **fusionnée** (1 conflit `TvService`, gardé parental + corbeille) : assistant « Ranger ma bibliothèque » (règles locales, corbeille 30 j, annulation, aide IA serveur optionnelle OFF et sans clé). `:core:test` 654 verts, apps compilées. **La TV (0.12.1) n'a PAS les routes `/api/trash/*`** : publier une nouvelle version TV avant d'utiliser l'assistant. À faire : brancher `ContentGuard` au contrôle parental ; tester sur la vraie bibliothèque (taux 84 % au 1er passage sur corpus de test) ; décider de l'activation de l'IA serveur (clé, coût, texte de consentement à faire relire).
- 2026-10-01 : demande d'Esaie : **l'adresse du serveur officiel n'apparaît plus dans les réglages** (TV et téléphone) ; une adresse personnalisée reste saisissable (champ vide = serveur par défaut) et n'est affichée que si elle est modifiée. CastBridge-TV **0.12.1 (code 23)** publiée sur la clé (`Download/CastBridge-TV-0.12.1.apk`, sha256 `9f996c4b…00434` vérifié) ; CastBridge **1.1.1-beta** installée sur le S21+. L'adresse reste dans le code (nécessaire au fonctionnement) et dans l'API `/api/connect/...`.
- 2026-10-01 : CastBridge **1.1-beta (code 9)** installée en USB sur le S21+ (utilisateur principal) et lancée. L'ancienne version était une « 1.0 » de même code 9 (build des autres IA) : remplacée. La copie « Dual App » (utilisateur 95) a aussi CastBridge, non mise à jour (le Bluetooth y est refusé : ne pas l'utiliser). Prochain test : « Ajouter ma TV » (appairage Bluetooth réel).
- 2026-10-01 : CastBridge-TV **0.12.0 (code 22)** publiée sur la clé (`Download/CastBridge-TV-0.12.0.apk`, sha256 `2d034318…f26f2f` vérifié) ; l'API de la TV annonce ensuite versionCode 22 / 0.12.0 (installée). Non encore testée en usage réel. **À traiter : 3 clés SSH autorisées sur la TV** — `claude-code-session` (la nôtre) et deux « letcheta@TCHETAGNIs-MBP.lan » (dont une avec le préfixe `HltitGwF`) : à confirmer avec Esaie, puis retirer les inconnues (`/api/ssh/key`).
- 2026-10-01 : `feat/charte` **fusionnée** (5 conflits résolus à la main : manifestes TV/téléphone, accueil TV `PlayerActivity`, onglets `MainActivity`, `TvHome`) : logo/icônes/thèmes/polices/contrastes WCAG/guide PDF 16 p. ; icônes `ic_t_quiz/chess` supprimées → `ic_cb_*`. `:core:test` 574 verts, APK TV 29,6 Mo / téléphone 23,0 Mo compilés. **Aucune vérification visuelle après fusion** (accueil TV, onglets téléphone) : à faire sur émulateur/matériel. Écart connu : écran Échecs TV sans logo ; tuiles Jeux/Contrôle parental gardent leurs icônes `ic_t_*` (pas d'équivalent charte) ; l'onglet Jeux du téléphone utilise l'icône Quiz.
- 2026-10-01 : `feat/parental` fusionnée (`17e9838`). Agents en cours : `feat/charte` (charte graphique), `feat/quiz-no-repeat` (300 parties sans répétition, packs < 11 Mo), `feat/library-agent` (assistant de rangement). Les agents « cloud » tournent en fait sur le Mac d'Esaie (dossiers `.claude/worktrees/`) : le Mac doit rester allumé.
- 2026-10-01 : `feat/games-hub` et `feat/bt-plug-and-play` fusionnées ; TV 0.12.0 / téléphone 1.1-beta compilés, **pas encore installés** (la TV a la 0.11 ; la 0.11.1 est sur la clé USB).
- 2026-10-01 : audit des modifications d'autres IA terminé : ne pas fusionner `wip/external-ai-changes` (voir §3).
- Nom des apps : on dit **CastBridge** (téléphone) et **CastBridge-TV** ; les modules Gradle `:sender`/`:receiver` et les applicationId ne sont pas renommés (décision à prendre quand aucune branche d'agent n'est ouverte).

## 1. Ce qu'est le projet
Écosystème pour relier le téléphone à la télévision, pour le Cameroun et l'Afrique francophone (propriétaire : Esaie Tchetagni Ngassa) :
- **App TV** `castbridge.receiver` (module `:receiver`) : service d'arrière-plan `TvService` + écrans (accueil à tuiles, bibliothèque, lecteur libVLC, quiz, échecs, téléchargements aria2, Apprendre, télécommande, tests Internet).
- **App téléphone** `castbridge.sender` (module `:sender`, Compose) : envoi/copie/déplacement vers la TV, cast DLNA, lecteur multimédia (« Ouvrir avec »), télécommande, quiz, échecs, Apprendre, passerelle Internet Bluetooth.
- **Cœur** `:core` (Kotlin JVM, testé) : protocoles, moteurs (quiz, échecs, apprentissage), clients serveur.
- **SSH** `:sshd` (Apache MINA SSHD) : administration à distance de la TV.
- **Serveur** `backend/` (Java 21, Spring Boot 3, MySQL 8.4, Docker) : mises à jour signées, questions du quiz, suivi des appareils, télémétrie, interface `/admin`.
- **Charte graphique** `branding/` (101 fichiers sur `feat/ssh`) ; application aux apps en cours sur `feat/charte`.

## 2. État des versions
| Élément | Version | Où |
|---|---|---|
| App TV | 0.11.1 (versionCode 21) | clé USB de la TV : `Download/CastBridge-TV-0.11.1.apk` (armeabi-v7a, sha256 `50d6c815…37e95`) ; la TV tournait en 0.11 (corrige la bibliothèque illisible : crochet `]` en trop dans `/api/library`) |
| App téléphone | 1.0-beta (versionCode 8) | installée sur le Samsung S21+ d'Esaie (sans `feat/connect`) |
| Serveur | commit `992db18` | https://bridge.sti-cm.com (en ligne, sain) |

Branche d'intégration : **`feat/ssh`** (poussée sur `origin` et sur le dépôt du serveur, branche `main`). Toutes les fonctionnalités y sont fusionnées.

## 3. Branches (une par fonctionnalité, fusionnées dans `feat/ssh`)
`feat/tv-admin` (PIN, page web, USB, lecture pendant l'envoi) · `feat/tv-usb-storage` (volumes multiples) · `feat/tv-library-player` (bibliothèque, lecteur, service, UX) · `feat/tv-quiz` · `feat/tv-chess` · `feat/tv-downloads` (aria2) · `feat/phone-player` · `feat/phone-remote` · `feat/backend` · `feat/learn` (Apprendre).
`feat/parental` (contrôle parental réécrit proprement, non fusionné : voir `docs/PARENTAL.md` ; limites : un enfant peut changer d'app/lecteur, à valider sur la vraie TV) · `feat/connect` (apps branchées sur le serveur : consentement, identification, heartbeat, mises à jour signées, télémétrie, questions du quiz en ligne) est **fusionnée** dans `feat/ssh` (2026-10-01) ; jamais testée contre le serveur de production : premier enregistrement réel à vérifier.

**ATTENTION — `wip/external-ai-changes`** (non fusionnée, NON revue) : instantané de 220 fichiers trouvés non commités dans le dossier de travail, écrits par d'autres sessions d'IA (activation/licence, contrôle parental, sudoku, deux mises à jour automatiques concurrentes `AutoUpdater`/`PhoneAutoUpdater`, suivi de l'adresse Bluetooth, assets de la charte, icônes, thème). À auditer (sécurité, doublons avec `feat/connect`) avant toute intégration ; rien de cela n'a été demandé dans les sessions de référence.
`feat/games-hub` (non fusionnée) : catégorie **Jeux** (tuile TV unique + écran « Jeux », onglet téléphone « Jeux »), Sudoku repris par fichiers de `wip/external-ai-changes` (seuls ces 4 fichiers) et amélioré : voir `docs/GAMES.md`. À valider sur la vraie TV : D-pad et lisibilité à 3 m.
`main` (GitHub) est resté à l'état initial : les fusions vers `main` sont à décider.

**Fusionnées le 2026-10-01** : `feat/parental` (contrôle parental : voir `docs/PARENTAL.md`, limites : ne contrôle que CastBridge-TV ; Quiz/Échecs non filtrés par âge) ; `feat/games-hub` (tuile/onglet Jeux, Sudoku) et `feat/bt-plug-and-play` (appairage Bluetooth, téléphones de confiance, jeton par téléphone ; voir `docs/BT-PLUG-AND-PLAY.md`) → CastBridge-TV 0.12.0 (code 22), CastBridge 1.1-beta (code 9), compilées, `:core:test` vert ; **non installées** ni testées sur matériel réel. Point à nettoyer : deux tuiles d'accueil portent l'id `bluetooth` (« Ajouter un téléphone » et « Bluetooth ») → statistiques d'usage confondues. Test connu instable : `TvSshServerTest.unknownKeyIsRefusedAndAddressGetsLocked` (MissingAttachedSessionException, course de timing MINA, passe en relance).

**Travaux en cours (agents, branches non fusionnées)** : `feat/charte` (logo, icônes, thèmes, polices, contrastes WCAG, guide PDF) · `feat/quiz-no-repeat` (300 parties sans répétition, packs < 11 Mo) (contrôle parental réécrit : PIN haché, sans défaut, verrouillage progressif, BACK/HOME jamais bloqués).

**Assistant « Ranger ma bibliothèque »** (branche `feat/library-agent`, non fusionnée, voir `docs/LIBRARY-AGENT.md`) : agent du téléphone qui lit la bibliothèque de la TV (ou un dossier du téléphone), comprend les noms par règles locales, propose renommages / doublons / place à libérer ; l'utilisateur coche et confirme, corbeille récupérable 30 jours (`/api/trash/*` côté TV, derrière le PIN : **la TV doit être mise à jour**), journal et annulation. Aide IA du serveur (`POST /api/v1/library/suggest`) facultative, désactivée sans clé `CASTBRIDGE_LIBRARY_LLM_API_KEY` ; jamais testée contre un vrai LLM. À brancher : `AgentStore.guard` au contrôle parental de `feat/parental`. Jamais essayé sur la vraie bibliothèque d'Esaie ni sur un vrai téléphone (SAF).

**Audit de `wip/external-ai-changes`** (branche `audit/external-ai`, `docs/AUDIT-EXTERNAL-CHANGES.md`) : NE PAS FUSIONNER en l'état. À rejeter : clé SSH en dur injectée par `BtUploadService`, auto-clic d'accessibilité sur les dialogues système, services du téléphone `exported=true`, blocage de toutes les touches pendant une mise à jour, activation/licence (HMAC symétrique, inerte), `AutoUpdater`/`PhoneAutoUpdater`/`DeviceHub` (doublons de `feat/connect`, sans consentement), binaire `cbt-rfcomm` non vérifié. Sûr : charte, icônes, thème, Sudoku, téléchargement Bluetooth. À confirmer avec Esaie : la clé SSH (commentaire `letcheta@…`) est-elle la sienne ?

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
- Mot de passe admin : 12 caractères minimum imposés ; Esaie a choisi un mot de passe conforme (voir « Où sont les secrets »).

## 8. À faire / en attente
1. Publier les APK via `/admin` et vérifier le premier enregistrement réel d'une TV (`feat/connect` est fusionnée).
2. **Appliquer la charte graphique** (en cours : `feat/charte`) (`branding/`) aux apps : couleurs, typographies, icônes des tuiles, icône d'app et bannière TV, anneau de focus. Avant : corriger les paires de couleurs qui échouent en WCAG AA pour le texte courant (or clair sur blanc 3,6 ; accent clair 3,3 ; blanc sur vert 3,4 ; gris `#6E7A93` sur fond sombre 4,4), refaire la mise en page du guide PDF (logos déformés aux p. 4-7 et 11), ajouter les icônes manquantes (Apprendre, Télécommande, Sur le téléphone, Internet/passerelle), envisager de distinguer le symbole d'un casque audio.
3. Clé de signature de release + secrets GitHub (`release.yml`) : à décider.
4. Contenu d'Apprendre : **124 points « à vérifier »**, tout est en brouillon, relecture par des enseignants (deux sous-systèmes) et par un agent de santé ; matières non couvertes : HG-ECM, SVT, anglais BEPC, philosophie, GCE A Level, licence ; vidéos (droits).
5. Quiz : 3 questions à relire (docs/QUIZ.md) ; serveur en ligne pour la banque. **Règle des 300 parties** (branche `feat/quiz-no-repeat`, non fusionnée) : historique anti-répétition par profil et parcours, `QuizBank.remainingFresh`, indicateur dans l'écran de choix et dans `/admin/quiz`, packs de questions signés (plafond 11 Mo, reprise, relais par le téléphone) et pipeline `tools/quiz-bank/` : ≈ 26 000 questions `review` produites (CM2, 3e, Tle, L1 éco, L1 maths > 4 500 mais calculées à ≈ 99 % ; culture générale 3 205 ≈ 156 parties ; **droit 106**) ; **0 question `approved`** : relecture humaine et rédaction du contenu camerounais/africain/droit à organiser (docs/QUIZ.md § 6 bis à 6 quater). Copier `content/quiz/dist` dans `CASTBRIDGE_QUIZ_PACKS_DIR` du serveur pour publier les packs.
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
