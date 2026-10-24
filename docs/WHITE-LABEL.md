# Marque blanche (white label)

Conception du 2026-10-24. Premier client : Hassan, produit **EduPlay** de **TechnoHub Enterprise** (branche `client/hassan-eduplay`).

## Décisions du propriétaire (2026-10-24)

| # | Décision |
|---|---|
| WL-1 | Le mécanisme de marque est fait **une seule fois sur le tronc** (branche `claude/white-label`) ; chaque client a **sa branche exclusive** `client/<client>-<marque>` qui ne contient que ses fichiers de marque (`branding/clients/<marque>/`) et suit le tronc par simple fusion, sans conflit. |
| WL-2 | Les apps d'un client sont **séparées** : identifiants d'app propres, clé de signature propre, installables à côté de CastBridge sur le même appareil. |
| WL-3 | Serveur : **notre serveur** (bridge.sti-cm.com) avec un **espace séparé** par client (codes d'activation, APK, guide). Phase 2. |
| WL-4 | Les éléments de marque sont **fournis par le client** ; nous n'en redessinons aucun (charte EduPlay § 3). |

## Principe

Une propriété Gradle `-Pbrand=<marque>` choisit la marque. Sans elle (ou `-Pbrand=castbridge`), la construction est **identique à aujourd'hui** : mêmes tâches, mêmes noms d'APK, mêmes identifiants, même rendu.

Avec `-Pbrand=eduplay`, Gradle lit `branding/clients/eduplay/brand.properties` et :

1. remplace `applicationId` (téléphone, TV) ;
2. expose la marque au code : `BuildConfig.BRAND_ID`, `BRAND_NAME`, `BRAND_TV_NAME`, `BRAND_OWNER`, `BRAND_TAGLINE`, `SUPPORT_PHONE`, et `DEFAULT_SERVER` si `server.url` est rempli ;
3. superpose les ressources `branding/clients/eduplay/android/<module>/res` **par le jeu de sources du type de build** (`debug`, `release`), qui a priorité sur `main` sans renommer aucune tâche : couleurs, icônes, logos, polices. Les noms (`app_name`, libellés du manifeste, raccourci « Ouvrir … TV », service d'accessibilité) sont **générés** depuis `brand.name` / `brand.tvName` : le client ne les écrit pas (s'il déclare lui-même une chaîne du même nom, la sienne l'emporte) ;
4. nomme les APK `<marque>-sender-…` / `<marque>-receiver-…` (aujourd'hui `sender-…` / `receiver-…` ; rien ne change pour CastBridge) ;
5. lit les clés d'activation de confiance dans `~/.castbridge-signing/<marque>/activation-trusted-keys.txt` (jamais celles de CastBridge : une TV EduPlay ne s'active pas avec un code CastBridge, et inversement).

## Ce qui change de nom, ce qui n'en change pas

- **Change** : tout texte vu par l'utilisateur (« CastBridge », « CastBridge-TV », libellés du manifeste, notifications, guide intégré, écran « À propos »), icônes, logos, couleurs, polices.
- **Ne change pas** : paquets Kotlin (`castbridge.*`), noms internes d'actions d'intent, clés de préférences, format des fichiers. Ce sont des identifiants, pas de la marque.
- **À trancher (phase 2)** : identifiants de découverte (nom de service Bluetooth, Wi-Fi Direct, mDNS). Proposition : les préfixer par `BRAND_ID` pour qu'un téléphone EduPlay ne voie que des TV EduPlay. Le recensement est fait en phase 1, le changement attend la décision.

## Fichiers d'une marque

```
branding/clients/<marque>/
  brand.properties        identité, identifiants, serveur, support
  CHARTE.md               charte reçue du client (référence)
  source/                 fichiers maîtres reçus du client, intouchés
  android/sender/res/     superposition téléphone
  android/receiver/res/   superposition TV
```

## Phases

1. **Mécanisme** (tronc) : propriété `brand`, superposition des ressources, textes visibles tirés de `BuildConfig`/ressources, CastBridge inchangé (vérifié par comparaison des APK avant/après).
2. **EduPlay** (branche client) : `brand.properties`, couleurs et polices (Poppins, Inter), icônes et logos à partir des fichiers fournis, construction signée avec la clé EduPlay.
3. **Serveur** : espace `/eduplay/` (activation, mises à jour, guide), outil d'activation avec clé propre.
4. **Découverte isolée** par marque (après décision).

## Bloquants côté client (EduPlay)

- Logo **vectoriel** (SVG ou PDF) : seuls des JPG ont été reçus ; l'icône d'app et la bannière TV ne peuvent être produites fidèlement qu'à partir du fichier maître.
- Version **compacte** / icône d'application validée par le client (la charte la demande, elle n'existe pas encore).
- Numéro et canal de support EduPlay.
- Identifiants d'app définitifs (proposés : `com.technohub.eduplay` et `com.technohub.eduplay.tv`).

## Construire une marque (phase 1, en place)

Le mécanisme est dans `android/brand.gradle.kts`, appliqué par `:sender` et `:receiver`. Sans `-Pbrand` : construction d'aujourd'hui, identifiants, libellés et noms d'APK inchangés (vérifié, voir plus bas).

```bash
cd android
# CastBridge, comme avant
gradle :sender:assembleDebug :receiver:assembleDebug -PrequireActivation=true

# une marque : branding/clients/<marque>/brand.properties doit exister (sinon erreur « Marque … introuvable »)
git checkout client/hassan-eduplay -- ../branding/clients/eduplay        # ou : git merge claude/white-label sur la branche client
gradle :sender:assembleDebug :receiver:assembleDebug -Pbrand=eduplay -PrequireActivation=true
# essai sans toucher à l'arbre : -PbrandDir=/chemin/vers/dossier-contenant-brand.properties
```

Clés lues dans `brand.properties` : `brand.id` (doit égaler `-Pbrand`), `brand.name`, `brand.tvName` (obligatoires), `brand.owner`, `brand.tagline`, `phone.applicationId`, `tv.applicationId` (obligatoires), `server.url` (vide = serveur par défaut ; `-Pcastbridge.serverUrl` l'emporte), `support.phone`. Les autres (couleurs, polices) servent aux ressources de la phase 2.

Côté code :
- `BuildConfig.BRAND_ID`, `BRAND_NAME`, `BRAND_TV_NAME`, `BRAND_OWNER`, `BRAND_TAGLINE`, `SUPPORT_PHONE` existent dans les deux apps (valeurs CastBridge par défaut) ; `CastBridgeApp` et `TvApp` les passent à `castbridge.core.brand.Brand` au démarrage.
- `Brand.t(texte)` remplace « CastBridge-TV » / « CastBridge TV » par `tvName` et « CastBridge » par `name` (jamais dans un chemin : `Download/CastBridge` reste). Pour CastBridge il rend le texte tel quel.
- Téléphone : `Text(String)` du paquet `castbridge.sender` (et `.player`, `.agent`), fichiers `BrandText.kt`, passe par `Brand.t` ; ne pas importer `androidx.compose.material3.Text` explicitement dans ces paquets. Notifications, toasts, boîtes de dialogue (`setTitle`, `setMessage`, boutons, `setContentTitle/Text`) passent par `Brand.t`. Nouveau texte : écrivez-le comme avant, la marque est appliquée à l'affichage.
- TV : `BrandViews` (enregistré par `TvApp` seulement si la marque n'est pas CastBridge) réécrit les `TextView` de chaque fenêtre d'activité. Limite connue : le texte stylé (spans) perd son style ; les `TextView` placés dans un `AlertDialog` via `setView` ne sont pas atteints (titre, message et boutons le sont).
- Raccourci « Ouvrir … TV » et `targetPackage` : `xml/shortcuts.xml` et `open_tv_strings.xml` sont régénérés pour la marque (`generateBrandResources`, `build/brand/res`).
- Clés d'activation de confiance (TV) : `~/.castbridge-signing/<marque>/activation-trusted-keys.txt` pour une marque autre que CastBridge ; `-PtrustedKeysFile` l'emporte toujours.

Vérifications faites (2026-10-24) : `assembleDebug` des deux apps sans `-Pbrand` : `castbridge.sender` « CastBridge », `castbridge.receiver` « CastBridge TV » (aapt2), comme avant ; avec `-Pbrand=eduplay` : `com.technohub.eduplay` « EduPlay » et `com.technohub.eduplay.tv` « EduPlay TV », APK `eduplay-sender-debug.apk` / `eduplay-receiver-<abi>-debug.apk`, raccourci `targetPackage` à jour ; `-Pbrand=inconnue` : erreur française claire. Tests du cœur : `BrandTest`, `OpenTvShortcutTest`, `BrandContrastTest`, `OpenTv*` verts ; la suite complète du cœur est très longue sur ce poste (non terminée), et `PlayHttpTransportStubTest` se bloque sur le JDK 21 local (verrou dans `HttpURLConnection.disconnect`, sans rapport avec ce changement). Pas encore faits : APK signé de marque (clé propre, phase 2), contrôle visuel sur appareil, textes à l'intérieur d'images ou de ressources du client.

## Couleurs et thème (phase 2, en place)

Les couleurs construites dans le code passent par `castbridge.core.brand.BrandPalette` (palette effective). Sans clé `color.*`, chaque valeur est exactement l'ancienne constante de `BrandTokens` (test `BrandPaletteTest.defaultPaletteEqualsOldConstants`) : CastBridge est visuellement identique. Les clés de `brand.properties` arrivent par `BuildConfig.BRAND_PALETTE` / `BRAND_PHONE_THEME` puis `Brand.init(...)`.

| Clé | Rôle |
|---|---|
| `theme.phone.default` | `dark` (défaut), `light` ou `system` : thème du téléphone tant que l'utilisateur n'a rien choisi dans Réglages (son choix prime) |
| `color.background` | téléphone clair : fond (et surfaces) |
| `color.text` | téléphone clair : texte ; les niveaux moyen/faible et les contours en sont dérivés ; texte posé sur les remplissages vert/cyan |
| `color.primary`, `color.onPrimary` | téléphone clair : couleur principale (boutons) et texte dessus |
| `color.success`, `color.info` | remplissages vert/cyan ; en TEXTE, assombris (clair) ou éclaircis (sombre) automatiquement jusqu'à 4,5:1 |
| `color.tvBackground`, `color.tvSurface`, `color.tvSurface2` | fond de la TV et thème sombre du téléphone ; cartes ; carte focalisée |
| `color.focus` | anneau de focus (TV et téléphone sombre) |
| `color.tvAccent` | optionnel : accent lisible sur fond sombre (texte, sélection, progression) ; défaut = `color.focus` |

Encore aux couleurs CastBridge, volontairement : palettes de sous-marque Quiz des Millions (`QuizViews.kt`), Échecs (`ChessTvView.kt`), Apprendre (`LearnViews.kt`) ; pastilles d'état (`StatusLevel.kt`, sémantique) ; lecteur et télécommande du téléphone (`player/`) ; avertissement et erreur (sémantique universelle, jamais recolorés).

## Inventaire des identifiants de découverte (phase 4)

Rien ci-dessous n'a été changé en phase 1. Ce sont des identifiants de protocole ou de reconnaissance entre un téléphone et une TV, pas de la marque. Avant tout changement (proposition WL : préfixer par `BRAND_ID`), les deux apps de la même marque doivent changer ensemble, et une TV ou un téléphone CastBridge déjà installé ne parlera plus à la nouvelle marque (voulu : isolation).

| Fichier:ligne | Valeur | Rôle |
|---|---|---|
| `core/.../tv/BtProtocol.kt:103` | UUID RFCOMM `7c5e3b9a-4d2f-4c61-9b0e-cb0000000001` | service principal TV (fichiers, commande CBT1/CBTN/CBTH/CBTP/CBTR) |
| `core/.../tv/BtProtocol.kt:105` | `…0002` | tunnel SSH par Bluetooth |
| `core/.../tv/BtProtocol.kt:107` | `…0003` | tunnel de l'API HTTP, une liaison par connexion |
| `core/.../tv/BtProtocol.kt:109` | `…0004` | tunnel de l'API HTTP, liaison partagée (mux) |
| `core/.../owner/OwnerFrames.kt:14` | `…0005` | canal propriétaire CBTO |
| `core/.../tv/BtProtocol.kt:114` | `…0007` | passerelle Internet CBG1 |
| `core/.../tv/BtProtocol.kt:120` | `…0008` | activation sans appairage (CBTA), aussi UUID de service BLE annoncé (`core/.../btact/BtActAd.kt:25`) |
| `core/.../tv/BtProtocol.kt:129-135` | noms SDP `CastBridge TV`, `CastBridge SSH`, `CastBridge API`, `CastBridge API v2`, `CastBridge Owner`, `CastBridge Internet`, `CastBridge Activation` | noms de service enregistrés par la TV avec chaque UUID (informatif, visibles dans les outils Bluetooth) |
| `core/.../trust/LinkRecovery.kt:11` | préfixe `7c5e3b9a-4d2f-4c61-9b0e-cb00000000` | le téléphone reconnaît un appareil Bluetooth appairé comme « peut être une TV CastBridge » |
| `sender/.../BtGatewayCard.kt:66` | même préfixe (`CB_UUID_PREFIX`) | idem, pour l'écran de la passerelle |
| `sender/.../TvLink.kt:386` | `ParcelUuid(BtProtocol.SERVICE_UUID)` | le téléphone teste la présence du service principal sur l'appareil |
| `core/.../gateway/BtGateway.kt:56` | `LEGACY_SERVICE_UUID` = `…0002` | ancienne TV : passerelle sur le service SSH |
| `core/.../btact/BtActAd.kt:27` | identifiant de fabricant BLE `0xFFFF` | enregistrement BLE d'annonce de l'activation |
| `core/.../btact/BtActAd.kt:31` | clé `castbridge-bt-activation-v1/advert` | dérivation de l'étiquette annoncée (ne pas changer : étiquette calculée des deux côtés) |
| `core/.../tv/ReceiverServer.kt:2221` | mDNS/NSD `_castbridge._tcp.` | type de service annoncé par la TV (`receiver/.../TvService.kt:1332`) et cherché par le téléphone (`sender/.../TvDiscovery.kt:118`) |
| `receiver/.../TvService.kt:1331` | nom de service mDNS `"CastBridge TV " + Build.MODEL` | nom annoncé ; attribut `role=receiver` (ligne 1334) |
| `core/.../tv/TvReach.kt:164` | regex `^castbridge[ -]tv\s+` | le téléphone retire ce préfixe pour comparer un nom mDNS au nom Bluetooth d'une TV (`TvNameMatch.base`) |
| `core/.../tv/WifiDirect.kt:33,47,49` | SSID `DIRECT-CB-xxxxxx`, suffixe par défaut `CastBridge` | nom de groupe Wi-Fi Direct |
| `core/.../tv/WdCode.kt:19,33` | `SSID_PREFIX = "DIRECT-CB-"` | SSID du groupe d'activation, calculé du code à 6 chiffres ; `looksLikeActivationGroup` le reconnaît |
| `core/.../link/WdDiagnostic.kt:57` | préfixe `DIRECT-` | lecture du nom de groupe |
| `core/.../cast/dial/DialRules.kt:52` | `friendlyName` = `CastBridge-TV <suffixe>` | nom DIAL (SSDP/UPnP) vu par les applis de diffusion |
| `core/.../cast/dial/DialRules.kt:54` | UDN `uuid:` + hash de `castbridge-tv-dial:<id>` | identité DIAL stable |
| `core/.../cast/dial/Ssdp.kt:20` | `Linux/4.9 UPnP/1.1 CastBridge-TV/1` | en-tête SERVER de la réponse SSDP |
| `receiver/.../DialHost.kt:77` | fabricant (repli `CastBridge`) et modèle `CastBridge-TV` | description DIAL |
| `receiver/.../TvService.kt:1328` | verrou multidiffusion `castbridge-tv` | nom interne, sans effet réseau |
| `core/.../net/HttpLite.kt:17`, `core/.../free/FreeDownload.kt:29`, `core/.../lots/LotSync.kt:31`, `core/.../lots/TvLotFetcher.kt:39`, `core/.../update/UpdateClient.kt:27`, `receiver/.../quiz/PlayHub.kt:184,209`, `receiver/.../TunnelHub.kt:163,186`, `receiver/.../TvNetDiag.kt:47`, `receiver/.../wallet/WalletHub.kt:275` | User-Agent `CastBridge`, `CastBridge-phone`, `CastBridge-TV`, `CastBridge-lots`, `CastBridge-$app-updater`, `CastBridge-TV-wallet`… | identification auprès de NOTRE serveur (journaux) ; à garder tant que le serveur ne les trie pas par marque (phase 3) |
| `core/.../tv/activation/ActivationLookup.kt:55`, `ActivationAccess.kt:51`, `receiver/.../LearnActivity.kt:574` | chemin `Android/data/castbridge.receiver/files` | dossier de la TV montré au propriétaire et cherché sur la clé USB : **dépend de l'applicationId TV** (à rendre dynamique avec la marque, phase 2) |
| `core/.../parental/ParentalApi.kt:41` | `AppEnv("castbridge.receiver")` | valeur par défaut ; la TV passe son vrai `packageName` (`AppCatalog.kt:63`) |
| `receiver/.../ServiceKeepAlive.kt:28-29`, `UpdateInstaller.kt:244`, `sender/.../PhoneUpdater.kt:161`, `BtGatewayService.kt:290`, `RemoteService.kt:115-116` | actions d'intent `castbridge.receiver.*` / `castbridge.sender.*` | internes à l'app, sans effet entre apps |
| `core/.../tv/BtProtocol.kt` (en-têtes de trame) | `CBT1`, `CBTN`, `CBTH`, `CBTP`, `CBTR`, `CBTO`, `CBTA`, `CBG1` | marqueurs de protocole sur le lien |
| formats signés | `cbx1`, `cbe1`, `cbw1`, `cbk1`, `cbm1`, `cbr1`, `cbg1`, `castbridge-*-v1` | formats et domaines de signature (jamais de la marque) |
| `sshd` | aucune bannière « CastBridge » trouvée dans les sources (bannière SSH par défaut de la bibliothèque) | à revérifier à l'exécution |

Découverte aujourd'hui sans nom de marque : la TV est trouvée par le type mDNS `_castbridge._tcp.`, par l'UUID RFCOMM (appairage Bluetooth) et par le SSID `DIRECT-CB-…` ; aucun de ces trois ne dépend d'un texte affiché. Les deux seuls tests sur un **nom** sont `TvReach.kt:164` (préfixe `castbridge tv` du nom mDNS) et `DialRules.kt:52` (nom DIAL).
