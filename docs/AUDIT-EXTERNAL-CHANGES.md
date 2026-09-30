# Audit indépendant des modifications externes (`wip/external-ai-changes`)

Audit en lecture seule. Rien n'a été fusionné ni appliqué ; aucun appareil physique, aucune donnée envoyée à `bridge.sti-cm.com`.

- Base de référence : `origin/feat/ssh` = `5a63d98` (contient `feat/connect`).
- Objet audité : `origin/wip/external-ai-changes` = `9e3caa5`, un commit au-dessus de `b990002` (base commune). 220 fichiers, +6284/-96 (dont 71 PNG/WebP).
- Les références `fichier:ligne` ci-dessous sont celles de la branche `wip/external-ai-changes` (`9e3caa5`), sauf mention contraire.

## 0. Verdict global

**Ne pas fusionner en l'état.** Le lot mélange du travail utile et propre (charte graphique, thème, icônes, Sudoku, contrôle parental local, cast Bluetooth) et quatre éléments à rejeter ou à corriger avant tout merge, dont deux sont des contournements de contrôles de sécurité :

| # | Constat | Gravité | Verdict |
|---|---|---|---|
| S1 | Clé publique SSH codée en dur, **injectée et activée sur la TV à chaque envoi Bluetooth** (`BtUploadService.kt:45-54`). Commentaire de la clé : `letcheta@TCHETAGNIs-MBP.lan` (la machine du propriétaire). Donne un shell sur toute TV qui reçoit un envoi, à qui détient la clé privée. | Critique | **À REJETER** |
| S2 | Le service d'accessibilité de la TV **clique automatiquement** « Autoriser / Installer / Mettre à jour / Associer / Toujours… » dans les boîtes de dialogue système (settings, systemui, packageinstaller, permissioncontroller) (`RemoteAccessibilityService.kt:28-42`). Annule la confirmation d'installation d'APK et des permissions d'Android, contredit le commentaire de `AutoUpdater.kt:17-18` et `docs/REMOTE.md` (« nothing is read or kept »). | Critique | **À REJETER** |
| S3 | `UploadService` et `BtUploadService` passés en `android:exported="true"` sans raison (`sender/AndroidManifest.xml:79-82`). N'importe quelle app du téléphone peut les démarrer. | Haute | **À REJETER** (revenir à `false`) |
| S4 | « Mise à jour obligatoire » qui **bloque toute la télécommande, y compris BACK et HOME** tant que `auto.updateAvailable` est vrai (`PlayerActivity.kt:976-982`), et bloque toute lecture (`TvService.kt:394-396`). `updateAvailable` est aussi mis à vrai sur l'état `Blocked` (« bloquées par l'administrateur », `AutoUpdater.kt:100`) et n'est jamais remis à faux en cas d'échec de téléchargement. Une TV peut devenir inutilisable (kill switch involontaire). | Haute | **À REJETER** (remplacer par le gabarit `mustUpdate` de `feat/connect`) |

Autres constats importants : `DeviceHub` contourne le consentement de télémétrie de `feat/connect` (S5) ; `AutoUpdater`/`PhoneAutoUpdater`/`DeviceHub` font doublon avec `TvConnect`/`PhoneConnect` (section 3) ; le code parental a un PIN par défaut `4444` public et sans verrouillage anti-force-brute (S6).

Ce que l'audit n'a **pas** trouvé : aucun `DexClassLoader`, `Runtime.exec`, `ProcessBuilder`, désactivation TLS (`TrustManager`, `HostnameVerifier`), `WebView`, chargement de code natif, téléchargement d'APK non signé, ni domaine inattendu (voir 1.2). Le binaire `tools/cbt-rfcomm/cbt-rfcomm` est le seul artefact opaque (1.6).

## 1. Sécurité

### 1.1 Méthode

```
git fetch origin
git diff --stat origin/feat/ssh...origin/wip/external-ai-changes
git diff origin/feat/ssh...origin/wip/external-ai-changes -- android backend docs tools/generate-quiz.py ':!*.png' ':!*.webp' > code.diff
# manifestes et gradle
git diff origin/feat/ssh...origin/wip/external-ai-changes -- android/receiver/src/main/AndroidManifest.xml android/sender/src/main/AndroidManifest.xml android/*/build.gradle.kts backend/src/main/resources/application.yml
# recherche de motifs dans les lignes ajoutées
grep '^+' code.diff | grep -inE 'DexClassLoader|Runtime\.|ProcessBuilder|exec\(|TrustManager|HostnameVerifier|loadLibrary|Class\.forName|WebView|PackageInstaller|ssh-|Base64|password|secret'
grep '^+' code.diff | grep -oE 'https?://[A-Za-z0-9._:/?=&%-]+|\b[0-9]{1,3}(\.[0-9]{1,3}){3}(:[0-9]+)?' | sort | uniq -c
```

Remarque : la différence à trois points (`...`) part de `b990002`. Les fichiers touchés aussi par `feat/connect` apparaissent donc en conflit (section 3).

### 1.2 Hôtes, URL et IP codés en dur (liste exhaustive dans le code ajouté)

| Valeur | Où | Usage | Jugement |
|---|---|---|---|
| `https://bridge.sti-cm.com` | `AutoUpdater.kt:135`, `PhoneAutoUpdater.kt:29`, `DeviceHub.kt:18` | URL du serveur CastBridge (mises à jour, enregistrement de l'appareil) | Attendu (même domaine que `feat/connect`), mais **codé en dur 3 fois** au lieu de `BuildConfig.DEFAULT_SERVER`/`ConnectState` |
| `192.168.49.1` | `CastSession.kt` (épinglage Wi-Fi Direct), `WifiDirect.GROUP_OWNER_IP` | Adresse fixe du groupe Wi-Fi Direct Android | Normal |
| `schemas.android.com`, `www.w3.org/2000/svg`, `www.thymeleaf.org` | XML / SVG / gabarits | Espaces de noms | Normal |
| `scripts.sil.org/OFL`, `github.com/rsms/inter`, `github.com/googlefonts/manrope`, `github.com/ateliertriay/bricolage`, `github.com/sora-xor/sora-font`, `design-tokens.github.io/community-group/format/` | `branding/fonts/OFL-*.txt`, `branding/design-tokens.json` | Licences et références de polices (texte uniquement) | Normal, pas de requête réseau |

Aucun autre hôte ni IP. `branding/mockups/index.html` et `branding/propositions.html` ne chargent aucune ressource externe (aucune URL `http` hors espaces de noms). `tools/generate-quiz.py` n'ouvre aucune connexion (il écrit un fichier ; l'import serveur est décrit en commentaire seulement).

### 1.3 Constats détaillés

**S1 — Clé SSH codée en dur (À REJETER).** `android/sender/.../BtUploadService.kt:45-54` : avant chaque envoi Bluetooth, un thread appelle `POST /api/ssh/enable` puis `POST /api/ssh/key?key=ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIDN9K1WpO/h0u/5aecnscCUppvMQFq5N5t+HltitGwF+ letcheta@TCHETAGNIs-MBP.lan` sur la TV (PIN de l'utilisateur, canal `bt:`). Conséquences : (a) active SSH sur la TV sans action de l'utilisateur, alors que `feat/ssh` en fait une fonction explicite ; (b) installe une clé d'autorisation permanente inconnue de l'utilisateur ; (c) la clé est celle d'un poste de développement (probablement le propriétaire : aide au débogage oubliée). L'effet est celui d'une porte dérobée même si l'intention semble bénigne. Aucune preuve de malveillance ; preuve d'un raccourci de mise au point livré dans le code produit. Patch de retrait proposé : `docs/audit-patches/0001-...patch`. Si le propriétaire veut cette clé pour lui, le faire par la procédure explicite de `feat/ssh` (Bluetooth SSH, `docs/`), jamais dans l'APK.

**S2 — Clic automatique sur les dialogues système (À REJETER).** `RemoteAccessibilityService.kt:28-42` : sur `TYPE_WINDOW_STATE_CHANGED`/`CONTENT_CHANGED` des paquets `com.android.settings`, `systemui`, `packageinstaller`, `permissioncontroller`, cherche les libellés `Autoriser, Accepter, Associer, Oui, Toujours, Installer, Mettre à jour, Se connecter, Allow, Accept, Pair` et exécute `ACTION_CLICK`. Cela supprime la seule confirmation humaine qu'Android impose à l'installation d'un APK (et aux permissions, à l'appairage Bluetooth). Il annule la garantie documentée (`UpdateInstaller.kt:19-26` : « what Android allows (and this class does not try to get around) »). Le même fichier ajoute aussi la lecture du paquet au premier plan pour le journal parental (lignes 44-55) ; la description du service (`res/xml/*accessibility*`, « No event is read or kept ») n'a pas été mise à jour : **incohérence de transparence** vis-à-vis de l'utilisateur qui active ce service. Patch : `0002-...patch`.

**S3 — Services exportés (À REJETER).** `sender/AndroidManifest.xml:79-82` : `UploadService` et `BtUploadService` de `false` à `true`. Un intent d'une autre app peut les démarrer (`BtUploadService` lit `EXTRA_ADDR`, `EXTRA_PIN`, `data` ; il a aussi un `foregroundServiceType`). Aucun intent-filter n'est ajouté donc l'exposition est explicite-uniquement, mais elle n'est justifiée nulle part. Autres `exported="true"` (MainActivity, ShareToTvActivity, RemoteTileService, PlayerActivity, PlaybackService) existent déjà sur `feat/ssh`. `SudokuActivity` : `exported="false"` (correct). `BtDownloadService` : `exported="false"` (correct).

**S4 — Blocage de la TV (À REJETER en l'état).** `PlayerActivity.kt:976-982` intercepte **toutes** les touches (`return true`) tant que `svc?.auto?.updateAvailable == true`, donc plus de HOME/BACK/réglages. `AutoUpdater.kt:97` affiche « Mise à jour obligatoire » pour toute version disponible (le manifeste signé a un champ `mandatory` que `feat/connect` respecte, pas `AutoUpdater`). `AutoUpdater.kt:100` : `Check.Blocked` (administrateur) met `updateAvailable = true`. Aucun retour à `false` après échec (réseau, permission « sources inconnues », signature différente `sameSigner`). Risque produit majeur : TV définitivement verrouillée chez un client. `TvService.kt:394-396` (`gate()`) bloque de même la lecture.

**S5 — Télémétrie hors consentement (À CORRIGER/REJETER).** `DeviceHub.kt` envoie un heartbeat complet (`DeviceReport.collect`, fabricant, modèle, empreinte de build, ABI, etc. **plus l'adresse Bluetooth de la TV**) dès le démarrage (`TvService.kt:186`) puis toutes les 15 min (`TvService.kt:281`), sans passer par `ServerLink`/`ConnectState.needsConsent` (écran de consentement `ConsentText`/`ConsentScreen` de `feat/connect`). Il crée aussi un second `install_id`/`device_id`/`device_token` (prefs `castbridge_device`) distinct de celui de `TvConnect` : deux fiches appareil pour une même TV côté admin. Contourne le consentement et la séparation « essentiel/usage ». La MAC Bluetooth est une donnée d'identification persistante (RGPD) ; `BluetoothAdapter.address` renvoie `02:00:00:00:00:00` pour les apps tierces depuis Android 6 (le code le filtre, `DeviceHub.kt:36`), donc le gain est quasi nul pour un risque réel.

**S6 — Code parental (À CORRIGER).** `ParentalHub.kt:62` : PIN par défaut `"4444"` (affiché dans l'UI téléphone `ParentalScreen.kt:83`), stocké en clair dans les prefs (`:66`), sans limite d'essais (la protection anti-force-brute de `PinGuard` ne couvre que le PIN de la TV, pas ce second PIN ; `TvService.kt:529-556` le reçoit en paramètre de requête `ppin=…`, donc présent dans d'éventuels journaux d'URL). `pinOk` : « aucun PIN = ouvert » (`:72`), et `/api/parental/pin?new=` accepte une valeur vide ou non numérique (`TvService.kt:546-551`) : on peut supprimer le PIN. Sur la TV, l'enfant peut essayer `4444` sans limite (`PlayerActivity.kt` `showParental`). L'API passe bien par `denied()` (PIN TV requis, verrouillage : `ReceiverServer.kt:225`), donc l'exposition réseau est celle de la TV elle-même. Journal local uniquement : `Parental.kt` n'envoie rien (aucun appel réseau dans `core/parental`).

**S7 — Activation / licence (inerte, À CORRIGER ou À REJETER).** Voir 1.4.

**S8 — Mise à jour du téléphone (À REJETER, doublon).** `PhoneAutoUpdater.kt` et la permission `REQUEST_INSTALL_PACKAGES` (`sender/AndroidManifest.xml:30`). Il vérifie bien la signature Ed25519 du manifeste et le SHA-256 (via `UpdateClient`, `UpdateClient.kt:70`) — c'est correct — mais : (a) `feat/connect` a déjà `PhoneUpdater` + `PhoneConnect` (le conflit de `MainActivity.kt` le montre) ; (b) il utilise `USER_ACTION_NOT_REQUIRED` sans vérifier que le paquet est le nôtre, sans `setAppPackageName`, sans contrôle du `versionCode`/signataire de l'APK avant commit (`UpdateInstaller.installVerified` les fait : `UpdateInstaller.kt:183-188` sur `feat/ssh`) ; (c) ignore `mandatory`, le canal, le consentement, `Blocked` ; (d) un `PendingIntent` mutable sans récepteur enregistré pour `castbridge.sender.UPDATE_RESULT` (résultat jamais lu). La dépendance à un `UpdateClient` signé limite le risque d'APK non authentique ; le risque est la double logique, pas un contournement de signature.

### 1.4 `core/activation` — à quoi servent les clés ? (`ActivationKeys.kt`)

- `ActivationKeys.SECRET = ""` (`core/.../activation/ActivationKeys.kt:12`). Aucune clé réelle n'est dans le dépôt. Aucune clé privée n'y figure.
- Conception : schéma d'**activation hors ligne** (licence/paywall de la TV). La TV dérive un « code machine » (SHA-256 de l'adresse MAC ou d'un UUID, 8 caractères base32), affiché décalé par un chiffre de César de 3 (`Activation.kt:21,28`). Le vendeur saisit ce code dans la page serveur `/admin/activation` (`AdminActivationPage.java`, `ActivationService.java`) qui renvoie `HMAC-SHA256(code, secret)` tronqué à 12 symboles base32 (60 bits). La TV recalcule et compare en temps constant (`Activation.kt:53`).
- **Qui détient la clé ?** Un secret **symétrique** partagé : le même doit être compilé dans l'APK (`ActivationKeys.SECRET`) et configuré côté serveur (`CASTBRIDGE_ACTIVATION_SECRET`, `application.yml`). Donc quiconque extrait l'APK (décompilation triviale) peut générer des jetons pour n'importe quelle TV : **contournable et non sûr comme licence**. Le code le reconnaît (`Activation.kt:12-14`, `ActivationKeys.kt:8-9`) et suggère Ed25519 ; `feat/connect` a déjà l'infrastructure Ed25519 (`UpdateKeys`, clé privée tenue côté serveur). Le chiffre de César n'apporte aucune sécurité (obfuscation cosmétique).
- Autres défauts : valeur vide = « tout est activé » (`ActivationHub.kt:23,39`) ; l'état `activated` est un simple booléen dans les prefs (modifiable par root ou par restauration de sauvegarde) ; la MAC peut être aléatoire (le code la mémorise) ; **aucune fonctionnalité n'est conditionnée à `activated()`** : grep montre qu'il n'est lu que par l'API `/api/activation` (`TvService.kt:523-527`). La fonction est donc inerte, non demandée dans le périmètre, sans écran TV ni flux d'achat.
- La page admin `/admin/activation` est sous la sécurité admin existante (`/admin/**`) ; POST sans jeton CSRF visible dans le gabarit (à vérifier selon la config Spring Security du projet — non vérifié).
- Test : `ActivationTest` (5 tests), `ActivationServiceTest` (Java). Les tests valident la cohérence Kotlin/Java, pas la sécurité.
- **Verdict : À REJETER pour l'instant** (feature commerciale non spécifiée). Si un paywall est voulu : signature Ed25519 (clé privée côté vendeur/serveur uniquement), jeton lié au `install_id`/code machine, état non modifiable trivialement, décision produit d'abord.

### 1.5 Permissions et manifestes (comparaison avec `feat/ssh`)

- Téléphone : **ajout de `REQUEST_INSTALL_PACKAGES`** (`sender/AndroidManifest.xml:30`). Note : `feat/connect` ajoute la même permission avec un commentaire différent (conflit de texte seulement) : la permission est déjà voulue dans `feat/ssh` pour les mises à jour serveur ; sa présence n'est donc pas une nouveauté côté permissions. Ajout de `BtDownloadService` (`exported=false`, `connectedDevice`) : cohérent avec les permissions Bluetooth déjà déclarées.
- TV : aucune nouvelle permission. Nouvelle activité `SudokuActivity` (`exported=false`). `android:icon` passe de `@drawable/banner` à `@mipmap/ic_launcher` + `roundIcon` ; `banner` inchangé comme `android:banner`.
- Services exportés ajoutés : `UploadService`, `BtUploadService` (S3).
- Pas de comptes ou mots de passe par défaut, sauf le PIN parental `4444` (S6). Pas de secret en dur, sauf la clé publique SSH (S1) ; la clé de signature serveur reste hors dépôt.
- Journalisation de secrets : `BtUploadService.kt:51` journalise le résultat de l'injection de clé (pas de secret) ; `AutoUpdater`/`PhoneAutoUpdater` ne journalisent pas de jeton. `Parental.kt` ne journalise ni URL ni secret (commentaire de tête, vérifié).
- Vérifications désactivées : le consentement de télémétrie (S5) ; la confirmation système d'installation (S2). PIN de la TV, TLS et signature de manifeste : non désactivés.

### 1.6 Autres éléments

- `tools/cbt-rfcomm/cbt-rfcomm` : **binaire Mach-O arm64 de 116 824 octets** (SHA-256 `af3ac9c7c726c7ed14eb864bc3412567f70cf77d175f65599ce5fc6250fd1f88`) committé ; la source `main.swift` est dans `feat/ssh` (commit `2465c38`) et n'a pas changé. L'analyse `strings` ne montre que IOBluetooth RFCOMM et des messages d'usage (`list|send <adresse> <pin> <fichier>`), aucun hôte. Mais **rien ne prouve qu'il correspond à `main.swift`** : À REJETER comme artefact versionné (le recompiler soi-même, l'ajouter au `.gitignore`).
- `test.kt` à la racine : fichier de brouillon (`joinToString`) sans usage : supprimer.
- `.gitignore` +`.venv/` : inoffensif.
- `tools/generate-quiz.py` : générateur de questions de maths déterministe, sans réseau : SÛR. À relire côté contenu pédagogique (explications « Généré automatiquement ») ; `reviewStatus: "reviewed"` et `status: "approved"` sont **auto-attribués** à des questions non relues par un humain : à corriger avant import.
- `WifiDirectGroup.kt:70,101-104` : appelle `WifiManager.disconnect()` quand le groupe Wi-Fi Direct monte, puis `reconnect()`. Dégrade la connectivité (Internet, liaison serveur) de la TV tant que le groupe existe ; sans effet garanti pour une app non système depuis Android 10. À tester sur matériel avant toute adoption (non testé ici).
- `DirectLink` (téléphone, `WifiDirectScreen.kt`) devient un singleton qui reste lié au réseau Wi-Fi Direct sans Internet (`bindProcessToNetwork`) même après avoir quitté l'écran : le reste de l'app du téléphone (dont `PhoneConnect`) perd Internet tant que la liaison dure.

### 1.7 Verdict de sécurité par module

| Module | Verdict | Justification |
|---|---|---|
| `core/activation` + `ActivationHub` + page/serveur `ActivationService` + `/api/activation` | **À REJETER** (pour l'instant) | HMAC symétrique, clé extractible de l'APK ; inerte (rien n'est verrouillé) ; non demandé |
| `core/parental`, `ParentalHub`, `ParentalScreen`, écrans TV | **À CORRIGER** | Journal 100 % local, logique testée ; défauts S6 (PIN 4444, pas de verrou, PIN en URL, PIN vide acceptable) et collecte du paquet au premier plan sans mise à jour de la transparence (S2) |
| `RemoteAccessibilityService` (auto-clic) | **À REJETER** | S2 |
| `core/sudoku`, `SudokuActivity`, `ic_t_sudoku` | **SÛR** | Logique pure, aucun réseau, activité non exportée |
| `AutoUpdater` (TV) | **À REJETER** (doublon) | Vérifie manifeste + SHA-256 mais double `TvConnect`, bloque la TV (S4), ignore le consentement/canal, URL serveur en dur |
| `PhoneAutoUpdater` + `REQUEST_INSTALL_PACKAGES` (texte) | **À REJETER** (doublon) | S8 ; la permission est déjà dans `feat/connect` |
| `DeviceHub` + `btAddress` (Device*, `V4__bt_address.sql`, `device.html`) | **À REJETER** / **À ADAPTER** si la MAC Bluetooth est voulue | S5 ; fonction en double de `TvConnect`/`DeviceClient` |
| `BtUploadService` (clé SSH) | **À REJETER** | S1 |
| Manifeste téléphone (services exportés) | **À REJETER** | S3 |
| `BtDownloadService` + `BtProtocol.DOWNLOAD (CBTD)` + `BtServer.resolve` + `TvService.resolveStoredFile` | **SÛR** (avec réserves mineures) | PIN vérifié par `PinGuard` (`BtProtocol.kt` bloc `DOWNLOAD`), nom via `ReceiverServer.safeName`, tailles contrôlées. Réserve : pas de reprise, nom saisi à la main, la TV ne demande aucune confirmation (même niveau de confiance que l'envoi) |
| `RemoteApi`/`RemoteHub` play/pause/seek/volume/player/file + `CastSession`/`CastSheet`/`Media.kt` (cast Bluetooth) | **À CORRIGER** (légère) | Tout passe par `denied()` ; bornage des paramètres (`MAX_NAME`, `pos`, `pct`). Réserves : `takePersistableUriPermission` généralisé dans `Media.kt` (accumule des permissions d'URI), `lastAddress` mémorisé sans consentement, `WifiDirect` (1.6) |
| `Theme.kt`, `TvCards.kt` (TvStyle), `branding/` | **SÛR** | Aucune logique réseau (1.2). Polices sous licence OFL fournies avec leurs licences |
| Icônes de lanceur et bannière | **SÛR** | Voir 2.3 |
| `tools/generate-quiz.py` | **SÛR** (avec réserve de contenu) | 1.6 |
| `tools/cbt-rfcomm/cbt-rfcomm` (binaire) | **À REJETER** | Binaire non reproductible |
| `test.kt` | **À REJETER** | Brouillon |

## 2. Fonctionnel et qualité

### 2.1 Tests (`gradle :core:test`)

Exécutés hors ligne, JDK système, Android SDK local.

- Sur `wip/external-ai-changes` seule : **494 tests, 0 échec, 0 erreur, 1 ignoré** (`ActivationTest` 5, `ParentalTest` 7, `SudokuTest` 8, `BtLinkTest` 10, `RemoteTest` ajouté).
- Sur `feat/ssh` + wip (fusion de travail, voir 3) : **503 tests, 0 échec, 1 ignoré**.
- Tests Java du backend (`ActivationServiceTest`) et UI (Compose, Activity) : **non exécutés** (pas de test instrumenté ni d'appareil).
- Aucun test n'accompagne : `AutoUpdater`, `PhoneAutoUpdater`, `DeviceHub`, `ParentalHub`, `BtDownloadService`, `WifiDirectGroup`, `SudokuActivity`, les écrans parentaux, ni l'auto-clic d'accessibilité.

### 2.2 Module par module

| Module | Fini ? | Testé ? | Utile / cohérent |
|---|---|---|---|
| Activation hors ligne | Noyau + page admin ; aucune UI TV de saisie (seul `/api/activation`), rien n'est verrouillé | Oui (cohérence) | Non demandé ; pas de modèle économique défini ; cohérence Ed25519 de `feat/connect` ignorée |
| Contrôle parental | Noyau complet (journal borné à 2000 lignes, plage horaire, limite quotidienne, PIN), écrans TV (`AlertDialog`) et téléphone (`ParentalScreen`) | Noyau oui (7 tests), UI non | **Pertinent** pour famille/école, mais : ne bloque que la lecture des vidéos CastBridge (aveu dans `Parental.kt` : « Blocking other apps ... not implemented »), compteur journalier en mémoire (remis à zéro au redémarrage : contournable en éteignant la TV), PIN par défaut faible ; ne s'intègre pas à la télémétrie/à la charte de consentement de `feat/connect` (un journal d'apps ouvertes est sensible : à mentionner à l'utilisateur) |
| Sudoku | Complet (générateur à solution unique, 4 niveaux, notes, indice, UI TV) | Noyau oui (8) | Cohérent avec Quiz/Échecs, charge utile faible ; pas de tuile dans l'écran d'accueil de `feat/connect` (la tuile `HomeTool` de wip est l'ancienne API, conflit) ; aucun événement télémétrie (`TvConnect.feature`) |
| Cast par Bluetooth | Fonctionnel sur le papier (copie puis lecture, vérification avant « Déplacer ») | `BtLinkTest`, `RemoteTest` côté protocole ; pas de test de bout en bout | Utile (pas de Wi-Fi commun), cohérent avec `docs/REMOTE.md` (mis à jour) ; attention au repli automatique (`TvHome.kt`) qui change d'onglet seul après 4 s |
| Télécommande / lecture hors app (`RemoteHub.kt`) | Les touches média passent avant l'accessibilité : amélioration | Non | Utile |
| Thème, charte | Conforme à `branding/design-tokens.json` (vérifié : fond sombre `#0A0F1E`, primaire `#F5B025`, clair `#F7F8FC`/`#B7791F`) | n/a | Cohérent. `TvStyle` garde les alias `CARD`, `ACCENT`… : lisible |
| WifiDirectGroup | Déconnexion du Wi-Fi classique pendant le groupe | Non | Risqué (1.6) |
| Auto-mise à jour TV/téléphone | Fini côté logique | Non | Doublon exact de `feat/connect` (3) |
| `btAddress` | Champ traversant (Kotlin → JSON → backend → Flyway `V4__bt_address.sql` → `device.html`) | Non | Faible valeur (MAC masquée), S5 ; `V4` : pas de conflit de nom aujourd'hui (`feat/ssh` a V1-V3) mais entrera en collision avec la prochaine migration de `feat/connect` |

Reste à faire notable : `ParentalHub`/`DeviceHub`/`ActivationHub` sont des objets globaux initialisés dans `TvService` sans passer par `TvApp`/`TvConnect` (architecture de `feat/connect`) ; aucun sous-module ne suit le modèle « événement de télémétrie filtré par le consentement ».

### 2.3 Icônes de lanceur et bannière TV

Vérifié dans l'arbre de wip :

- Bannière TV : `res/drawable/banner.png` et `drawable-xhdpi/banner.png` = **320×180** (conforme). `banner.xml` supprimé (remplacé). `android:banner="@drawable/banner"` inchangé dans le manifeste TV.
- Icône adaptative : `mipmap-anydpi-v26/ic_launcher.xml` et `ic_launcher_round.xml` avec `<background>`, `<foreground>`, `<monochrome>` ; les trois drawables vectoriels sont en **108 dp** (`android:width/height="108dp"`, viewport 240) : conforme. Le glyphe occupe environ 51 % de la largeur (x 58-181 sur 240) : dans la zone sûre (66 dp sur 108, soit 61 %).
- Monochrome présent (`ic_launcher_monochrome.xml`, blanc, pour Android 13+) : conforme.
- PNG hérités : `mipmap-mdpi..xxxhdpi/ic_launcher*.png` de 48/72/96/144/192 px (et calques 108/162/216/324/432). **Inutiles** : `minSdk = 26` donc l'icône adaptative XML est toujours utilisée ; ces PNG alourdissent l'APK de quelques dizaines de Ko (l'écart mesuré de l'ensemble est en 3.3).
- Le manifeste du téléphone ajoute `android:icon`/`roundIcon` (le téléphone n'avait pas d'icône spécifique).
- Non vérifié : rendu visuel réel sur un lanceur Android TV et sur Android 13 (icônes thématiques).

## 3. Conflits et doublons

### 3.1 Fusion de wip sur `feat/ssh` actuel

`git merge --no-commit --no-ff origin/wip/external-ai-changes` (sur un arbre de travail jetable, non poussé) : **6 fichiers en conflit de contenu** :

1. `android/receiver/build.gradle.kts` : wip écrase `versionCode/versionName` (19 / `0.10`, contre `0.9.1` + propriétés `-Pcastbridge.*`) et **supprime les `buildConfigField` `EXTRA_UPDATE_KEY` / `DEFAULT_SERVER`** dont `TvConnect` a besoin (prendre « theirs » casse la compilation).
2. `android/sender/build.gradle.kts` : idem (`9`/`1.0` contre `8`/`1.0-beta` + propriétés).
3. `android/sender/src/main/AndroidManifest.xml` : commentaire de `REQUEST_INSTALL_PACKAGES` ; le reste (services exportés) fusionne silencieusement : **danger** d'un merge automatique qui garde S3.
4. `android/sender/.../MainActivity.kt` : wip appelle `PhoneAutoUpdater.start(this)` et supprime `Gate()` (écran de consentement, mise à jour obligatoire) de `feat/connect`.
5. `android/receiver/.../PlayerActivity.kt` : wip utilise l'ancienne API `HomeTool(...)`, `feat/connect` a `tile(id, ...)` avec télémétrie `TvConnect.feature`.
6. `android/receiver/.../TvDownloads.kt` : wip ajoute `ParentalHub.onDownload` à l'endroit où `feat/connect` ajoute `TvConnect.track("download", ...)`.

Le reste (`TvService.kt`, `RemoteHub.kt`, `DeviceReport.kt`, `RemoteApi.kt`, `BtProtocol.kt`…) fusionne sans conflit textuel.

### 3.2 Compilation

En résolvant les six conflits en faveur de `feat/ssh` (les hunks de wip dans ces 6 fichiers sont perdus, donc `SudokuActivity`, le menu parental et le blocage d'écran ne sont pas branchés dans `PlayerActivity`) :

- `gradle :receiver:compileDebugKotlin :sender:compileDebugKotlin` : **succès**.
- `gradle :core:test` : succès (503 tests).
- `gradle :receiver:assembleDebug :sender:assembleDebug` : succès.

Donc aucun conflit sémantique de compilation (p. ex. le paramètre `btAddress` inséré au milieu du constructeur de `DeviceReport` n'invalide pas les appelants de `feat/connect`) ; en revanche, la **compilation ne prouve pas la cohérence fonctionnelle** : `AutoUpdater` et `TvConnect` tournent tous deux dans `TvService`.

### 3.3 Tailles d'APK (debug, `feat/ssh` seul contre `feat/ssh` + wip avec conflits résolus côté `feat/ssh`)

| APK | `feat/ssh` | + wip | Écart |
|---|---|---|---|
| TV arm64-v8a | 32 768 206 o | 32 931 069 o | +162 863 o |
| TV armeabi-v7a | 28 850 382 o | 29 013 245 o | +162 863 o |
| Téléphone | 21 952 937 o | 22 124 148 o | +171 211 o |

Les builds sont en `debug` (non minifiés, pas représentatifs d'une version de production). Le chiffre est une borne basse : les hunks des six fichiers en conflit (Sudoku/parental sur l'écran d'accueil de la TV) n'ont pas été appliqués, donc leurs classes Kotlin ne comptent pas intégralement.

### 3.4 Doublons fonctionnels

| wip | `feat/connect` (déjà dans `feat/ssh`) | Problème |
|---|---|---|
| `AutoUpdater` (TV) : planification `UpdateSchedule`, `UpdateClient`, téléchargement, puis `installer.install(listOf(name), force=false)` (`AutoUpdater.kt:123`) | `TvConnect` + `ConnectAgent`/`ServerLink` (vérification, consentement, canal, état `blocked`), téléchargement par `UpdateClient.download`, installation par `UpdateInstaller.installVerified` (`TvConnect.kt:181`, `UpdateInstaller.kt:183`) | Deux planificateurs, deux téléchargements concurrents du même APK, deux états `update_schedule` ; `install()` (chemin USB/téléphone, autorise `force`) au lieu de `installVerified` (qui impose paquet, versionCode du manifeste, signataire, `EXTRA_AUTO` + repli en cas d'échec) ; ignore `BuildConfig.DEFAULT_SERVER`/`EXTRA_UPDATE_KEY` (tests locaux) ; ignore `mandatory` |
| `PhoneAutoUpdater` | `PhoneConnect` + `PhoneUpdater` (`MainActivity.installFrom`, `MandatoryUpdateScreen`) | Idem côté téléphone ; `USER_ACTION_NOT_REQUIRED` sans contrôle du paquet/signataire |
| `DeviceHub` (TV) | `TvConnect`/`ServerLink` + `DeviceClient`/`DeviceReport` (heartbeat de 15 min dans `TvService`) | Double enregistrement, contourne le consentement, second `install_id` |
| `btAddress` dans `DeviceReport`/`DeviceService`/`Device`/`V4__bt_address.sql`/`device.html` | `DeviceReport` de `feat/connect`/backend `feat/backend` | Ajout en milieu de record (ordre des paramètres) ; migration `V4` à renuméroter au merge ; le backend de production n'a pas été consulté |
| Menu « Mises à jour » de la TV (texte « Installer une nouvelle version envoyée par le téléphone ») | Tuile `updates` → `ServerActivity.MODE_UPDATES` | wip remet l'ancienne version de la tuile |
| `ParentalHub.onGame/onPlay/onFile/onDownload` | `TvConnect.feature/track` | Deux systèmes d'événements parallèles : le journal parental est local (correct), mais chaque point d'instrumentation doit être fait deux fois |

## 4. Plan d'intégration proposé (non appliqué)

Principe : repartir de `feat/ssh`, **ne jamais fusionner la branche wip**, y prélever des groupes de changements par `git checkout origin/wip/external-ai-changes -- <fichiers>` ou cherry-pick de hunks dans des branches courtes, chacune relue et testée.

| Ordre | Groupe | Décision | Correctifs nécessaires | Tests à ajouter |
|---|---|---|---|---|
| 0 | `BtUploadService` (clé SSH), services exportés, auto-clic accessibilité, `test.kt`, binaire `cbt-rfcomm`, `DeviceHub`, `AutoUpdater`, `PhoneAutoUpdater`, `ActivationHub`/`core/activation`/backend `Activation*` | **REJETER** | Appliquer les patchs `docs/audit-patches/0001-…` et `0002-…` si une branche intermédiaire est tout de même construite ; ajouter `tools/cbt-rfcomm/cbt-rfcomm` au `.gitignore` | Test de lint manifeste : aucun `exported="true"` hors liste blanche ; test statique : aucune clé `ssh-`/`AAAA` dans `android/**` |
| 1 | `branding/` (design tokens, logos, icônes SVG, guide PDF, polices OFL) | **GARDER** | Retirer `branding/mockups/` et `propositions.html` si inutiles ; conserver les licences OFL ; ne pas embarquer les polices dans l'APK sans décision | Test Gradle : les couleurs de `Theme.kt`/`TvStyle` = `design-tokens.json` |
| 2 | Icônes de lanceur et bannière (`res/mipmap-anydpi-v26`, drawables 108 dp, `banner.png` 320×180) | **GARDER / ADAPTER** | Supprimer les PNG `mipmap-*dpi` hérités (inutiles avec `minSdk 26`) ; supprimer `banner.xml` remplacé ; vérifier sur TV réelle | Lint Android (`adaptive-icon`), contrôle des dimensions dans un test de ressources |
| 3 | `Theme.kt` (téléphone) + `TvStyle` (TV) | **GARDER** | Résoudre contre l'API `tile(...)` de `feat/connect` (recoller les changements de couleur seuls) ; vérifier le contraste (accessibilité) | Captures de référence ou test de contraste WCAG sur les paires de tokens |
| 4 | Sudoku (`core/sudoku`, `SudokuActivity`, tuile, icône) | **GARDER** (après décision produit) | Brancher dans la tuile `tile("sudoku", …)` avec `TvConnect.feature("sudoku")` ; ajouter l'entrée dans `EventCatalog.TV_FEATURES` ; retirer l'appel `ParentalHub.onGame` tant que le parental n'est pas adapté | Garder `SudokuTest` ; test d'unicité sur N grilles aléatoires ; test de la liste `TV_FEATURES` |
| 5 | Contrôle parental | **ADAPTER** (seulement si la demande produit existe) | PIN : pas de défaut `4444` (forcer la création à l'activation), PIN haché (PBKDF2/SHA-256 salé), 4-6 chiffres validés côté serveur TV, verrouillage progressif comme `PinGuard`, PIN dans le corps POST et non dans l'URL, ne pas accepter de PIN vide via l'API ; compteur journalier persistant ; mise à jour de la description du service d'accessibilité et de `docs/REMOTE.md` ; journal d'apps au premier plan en option explicite ; intégrer à l'écran de consentement si l'un des éléments part un jour vers le serveur | `ParentalTest` étendu : verrouillage après N échecs, rejet d'un PIN vide/non numérique, persistance du compteur, franchissement de minuit ; tests d'API (401 sans PIN) |
| 6 | Cast Bluetooth + `BtDownloadService` + CBTD + `RemoteApi`/`RemoteHub` (play, pause, seek, volume, player, file) + `docs/REMOTE.md` | **GARDER / ADAPTER** | Rebaser sur `PlayerActivity` actuel ; retirer le bloc d'injection SSH ; `Media.kt` : ne prendre la permission persistante que si `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` ; revoir le repli automatique vers Bluetooth (`TvHome.kt`) ; tester `WifiDirectGroup` sur TV avant de garder `stationOff()` | `BtLinkTest` + test de CBTD (mauvais PIN, nom hors `safeName`, fichier tronqué) ; `RemoteTest` pour `play` hors bibliothèque |
| 7 | `btAddress` | **REJETER**, ou **ADAPTER** si la MAC Bluetooth est réellement exigée | Via `ServerLink`/`DeviceFacts` de `feat/connect` (donc sous consentement), nouvelle migration renumérotée, champ admin ; accepter que la MAC soit masquée | Test de `DeviceReport.toJson` (champ absent sans consentement) |
| 8 | `WifiDirectGroup` (`wifi.disconnect()`), `DirectLink` singleton | **ADAPTER** ou **REJETER** | Mesure sur matériel d'abord ; ne jamais couper Internet sans besoin ; `DirectLink` : ne pas lier tout le processus au réseau sans Internet | Test instrumenté ; à défaut, test manuel documenté |
| 9 | `tools/generate-quiz.py` | **GARDER** (hors APK) | Ne pas marquer `approved`/`reviewed` automatiquement ; relecture humaine du contenu | Test d'un échantillon : réponse correcte unique, pas de doublon |
| 10 | Versions (`0.10`, `1.0`) | **REJETER** | Suivre la numérotation de `feat/ssh` (`0.9.1`/19) et le propriétaire | — |

Ordre de livraison conseillé : 0 (rien à faire, simplement ne pas prendre) → 1 → 2 → 3 → 4 → 6 → 5 → 7/8 selon décision. Chaque lot : PR courte sur `feat/ssh`, `gradle :core:test :receiver:assembleDebug :sender:assembleDebug`, relecture de sécurité, test TV réel.

## 5. Reproduire l'audit

```
# Depuis la racine du dépôt
git fetch origin
git diff --stat origin/feat/ssh...origin/wip/external-ai-changes | tail -1      # 220 files, +6284/-96
git diff --name-status origin/feat/ssh...origin/wip/external-ai-changes | grep -v 'png\|webp'

# Arbre de travail de lecture de wip
git worktree add --detach /tmp/wip origin/wip/external-ai-changes
cd /tmp/wip/android && ANDROID_HOME=$HOME/Library/Android/sdk gradle :core:test --offline -q

# Fusion d'essai sur feat/ssh (jetable, ne pas pousser)
git worktree add --detach /tmp/merge origin/feat/ssh
cd /tmp/merge && git merge --no-commit --no-ff origin/wip/external-ai-changes      # 6 conflits (3.1)
# pour compiler : garder la version feat/ssh des 6 fichiers
git checkout --ours -- android/receiver/build.gradle.kts android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt \
  android/receiver/src/main/kotlin/castbridge/receiver/TvDownloads.kt android/sender/build.gradle.kts \
  android/sender/src/main/AndroidManifest.xml android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt
cd android && ANDROID_HOME=$HOME/Library/Android/sdk gradle :core:test :receiver:assembleDebug :sender:assembleDebug --offline -q
ls -l receiver/build/outputs/apk/debug/*.apk sender/build/outputs/apk/debug/*.apk

# Recherches ciblées (sur les fichiers de wip)
grep -rnE 'ssh-ed25519|AAAAC3' android                       # S1
grep -n 'performAction(AccessibilityNodeInfo.ACTION_CLICK)' android/receiver/src/main/kotlin/castbridge/receiver/RemoteAccessibilityService.kt   # S2
grep -n 'exported="true"' android/sender/src/main/AndroidManifest.xml   # S3
grep -rn '4444' android                                      # S6
grep -rnE 'https?://' android backend/src tools branding | grep -v 'schemas.android\|w3.org'
shasum -a 256 tools/cbt-rfcomm/cbt-rfcomm; strings -n 6 tools/cbt-rfcomm/cbt-rfcomm | grep -i http
# Patchs proposés (non appliqués) : vérifier qu'ils s'appliquent sur wip
git apply --check docs/audit-patches/0001-*.patch docs/audit-patches/0002-*.patch
```

## 6. Ce que je n'ai pas pu vérifier

- Aucun test sur appareil (TV, téléphone, Bluetooth, Wi-Fi Direct, accessibilité, installation réelle) ; le comportement de `WifiManager.disconnect()`, de l'auto-clic et du blocage de touches est déduit du code.
- Le backend Spring n'a pas été compilé ni testé (`ActivationServiceTest`, migration Flyway `V4`) ; la configuration Spring Security (protection de `/admin/activation`, CSRF) n'a pas été lue.
- Les APK sont en `debug` (non minifiés) ; pas de build `release` signé, donc pas de taille de production. Le build « avec wip » perd les hunks des six fichiers en conflit.
- Aucun accès à la production : je n'ai pas vérifié si le serveur `bridge.sti-cm.com` a une clé d'activation configurée, ni l'état réel de ses migrations (`V4` peut déjà exister côté serveur).
- Le rendu visuel des icônes/bannière et le contraste réel des couleurs n'ont pas été contrôlés à l'écran.
- La clé publique SSH : je n'ai pas pu confirmer qu'elle appartient au propriétaire (seul le commentaire `letcheta@TCHETAGNIs-MBP.lan` le suggère). Le coordinateur doit le confirmer avec lui.
- Le PDF de la charte (11 pages) et les polices ont été examinés par leurs métadonnées et licences seulement, pas page par page ; la conformité de marque n'a pas été jugée.
- Le binaire `cbt-rfcomm` n'a pas été exécuté ni reconstruit ; `swiftc main.swift` est à comparer par le propriétaire.
- Les 220 fichiers n'ont pas tous été lus ligne à ligne : lu en entier ou en diff complet : tout le code Kotlin/Java métier listé dans la mission, manifestes, gradle, `application.yml`, SQL, gabarits ; survolés : SVG de `branding/`, PNG (dimensions vérifiées), PDF, `build_guide.py`/`gen_android_icons.py` (aucun réseau détecté par recherche de motifs, sans relecture complète).
