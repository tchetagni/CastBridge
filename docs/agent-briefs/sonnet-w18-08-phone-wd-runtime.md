# w18-08 — CastBridge (téléphone) : `WdRuntime` (jonction directe par identifiants mémorisés, découverte DNS-SD P2P, sockets par réseau sur Android 10-12, bascule entre deux TV), route de contrôle `Direct` quand la session est montée, fiche `wd.*`

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (radios téléphone, identifiants) · statut : **ATTEND** verdict terrain (VERT/ORANGE) **et** sortie du gel W15
> **Groupe : W18b-2** (vague W18b, téléphone) · prérequis : w18-01, 02, 07 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin :core:test --tests 'castbridge.core.link.*' --tests 'castbridge.core.journey.WdJourneyTest'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 3 j) · audit Opus : obligatoire

**Vague 18b · Effort L · Modèle : sonnet · Statut ATTEND.** Conception : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 3, § 5.1, § 5.3, § 6, § 10 (R4, R5), D-W18-9. Branche `claude/sonnet-w18-08`. Rapport : `docs/agent-reports/sonnet-w18-08.md`. Règle W15 R2/R9 : seul cahier sur `S/TvLink.kt`.

## Objectif
L'exécutant Android de `WdSession` (w18-02) : (1) `S/link/WdRuntime.kt` (renommage et extension de `S/AutoWifiDirect.kt`) : un `WdSession` par TV, **un seul** `Up` ; effets : `Join` par `WifiP2pManager.connect(WifiP2pConfig.Builder().setNetworkName().setPassphrase())` (33+ ; 29-32 : `WifiNetworkSpecifier` + `requestNetwork`, approbation mémorisée par Android pour un SSID/BSSID stable), `RequestGroup` par CBTN (R-14, gardé), `Probe` `/api/hello` (vérifie `id` = TV attendue), `Leave`, `Switch`, `ForgetCreds` (`PinBook.forgetWd`) ; déclencheurs : premier plan, action, `ACL_CONNECTED`, DNS-SD ; **jamais** en arrière-plan ; (2) `S/link/WdNetworkSockets.kt` : sur 29-32, **plus de `bindProcessToNetwork`** (défaut d'audit) : `Network.socketFactory`/`network.openConnection` par requête, exposés à `UploadService`/`TvClient` par un `SocketFactory` injecté ; (3) `S/link/WdDnsSdClient.kt` : `discoverServices` sur fenêtres (`WdTriggers`), candidats `(id, name)` ⇒ « Salon est à portée », choix de la TV cible ; (4) `S/TvLink.kt` : `LinkPlanner2` (w18-02) : route de contrôle `Direct` **si** `WdRuntime.isUp(tv)` ; repli Bluetooth à la perte ; (5) `S/PinStore.kt` : `wd.*` via `PinBook` (w18-01) ; (6) `S/TransferQueue.kt` zone `bulkBase` : `WdPolicy.route(BULK)` au lieu de `AutoWifiDirect.bulkBase`.

## Pourquoi (preuves)
- `S/AutoWifiDirect.kt` (branche R-14, 389 lignes : CBTN par `AndroidBtTransport`, `connect`, spécificateur avec `bindProcessToNetwork`, sonde, `WD_RELEASE`) ; `S/TvLink.kt` `canJoinWifiDirect = { false }` ; `S/TransferQueue.kt` `runOneInner` ⇒ `AutoWifiDirect.bulkBase(session, taille)` ; `S/WifiDirectScreen.kt:46` (`bindProcessToNetwork` : même défaut, onglet manuel) ; `S/PinStore.kt` (R-10, `PinBook`).
- `C/link/{WdSession,WdPolicy,WdTriggers,WdCredentials}.kt` (w18-01/02) ; `CT/journey/WdJourneyTest` (w18-06) : l'ordre des effets à reproduire.
- Faits Android : `WifiP2pManager.connect` par identifiants = API 29+, permission `NEARBY_WIFI_DEVICES` 33+ (manifeste R-14) ; `requestNetwork` + `WifiNetworkSpecifier` : approbation mémorisée pour un même SSID/BSSID (API 29+) ; une seule connexion P2P client par appareil.

## Brique existante
Le bouton « Wi-Fi Direct » de la fiche de la TV (branche `claude/wd-manual-button`) appelle aujourd'hui `AutoWifiDirect` (CBTN ⇒ jonction ⇒ état + débit 20 Mo). Ce cahier **se rebase** sur cette branche et fait que le bouton : (a) joint **directement** quand `PinBook.wdOf` connaît les identifiants (plus de Bluetooth nécessaire au second appui), (b) lit son état dans `WdSession` (une seule source), (c) envoie sa mesure à `POST /api/diag/wifi-direct` (w18-04). Le bouton reste visible aux mêmes conditions (lien Bluetooth + PIN/jeton) et reste le contournement manuel (D-W18-11).

## Fichiers possédés
`S/AutoWifiDirect.kt` → `S/link/WdRuntime.kt` (git mv + extension), nouveaux `S/link/WdNetworkSockets.kt`, `S/link/WdDnsSdClient.kt`, `S/TvLink.kt` (zone `canJoinWifiDirect`/`LinkPlanner2`, ≤ 40 lignes), `S/PinStore.kt` (`wd.*`), `S/TransferQueue.kt` (zone `bulkBase`, ≤ 20 lignes), `S/WifiDirectScreen.kt` (retrait de `bindProcessToNetwork` : même `WdNetworkSockets`). **Hors zone** : `S/UploadService.kt` (w18-13), `S/TvHome.kt`, `S/TvPairScreen.kt`, `S/ConnectScreens.kt` (w18-09), `S/player/**`, `S/RemoteController.kt` (w18-10), `C/**`, `R/**`.

## Étapes
1. Rejouer `WdJourneyTest` (w18-06) : l'ordre des effets est le contrat ; aucune politique dans `S/` (si une règle manque, s'arrêter et le dire : w18-02).
2. `WdRuntime` : `WifiP2pManager.Channel` unique ; `requestConnectionInfo` toutes les 2 s en `Up` (perte) + `WIFI_P2P_CONNECTION_CHANGED_ACTION` ; `goIp` ⇒ `WdAddress.base` (existant) ; sonde avec `id` ; `Switch` = `removeGroup`/`cancelConnect` puis `Join`.
3. `WdNetworkSockets` : `Network?` courant (null sur 33+ : la route système suffit, H-3 ; sinon le réseau du spécificateur) ⇒ `socketFactory()`, `open(url)` ; `UploadService` reçoit la fabrique par un paramètre **sans** modifier son fichier (w18-13) : passer par `TransferClient`/`ResumableUpload` existants qui acceptent déjà un `SocketFactory` ? **vérifier** ; sinon un `Intent` extra lu par le code existant (dire au rapport).
4. `WdDnsSdClient` : `setDnsSdResponseListeners` ; fenêtres de `WdTriggers` ; aucune localisation demandée (29-32 : découverte **désactivée**, la jonction directe suffit).
5. `TvLink` : `LinkPlanner2.plan(info, session, wdUp = WdRuntime.isUp(tv))` ; à `Lost` ⇒ `poke()` (repli existant).
6. **Sur appareil (propriétaire)** : H-2 (permission sans boîte), H-3 (routage sans liaison), H-16 (DNS-SD), H-17 : fermer/rouvrir l'app ⇒ `Up` en ≤ 8 s sans Bluetooth (Bluetooth **éteint** pour la preuve), H-18 : S21+ sur un autre Wi-Fi ⇒ Internet gardé pendant la copie, H-19 : deux TV (si disponibles) ⇒ bascule ≤ 10 s, H-20 : `logcat` sans mot de passe.
7. **Vert** : porte ; `:core:test` complet ; `compileDebugKotlin`.

## Critères d'acceptation
Porte verte ; `grep -rn "bindProcessToNetwork" android/sender/src/main/kotlin` **vide** ; `grep -rn "ACCESS_FINE_LOCATION" android/sender/src/main/AndroidManifest.xml` vide ; aucune constante de politique dans `S/link/WdRuntime.kt` ; rapport d'audit Opus : où les identifiants sont lus/écrits (`PinBook` seulement), quand le téléphone émet en radio (jamais en fond), ce que voit une autre application.

## Cas limites
Wi-Fi du téléphone éteint ⇒ `AskOnce(PHONE_WIFI)` une fois (panneau), puis BT ; `connect` ⇒ `ERROR`/`BUSY` (groupe fantôme côté téléphone) ⇒ `removeGroup` puis un nouvel essai ; `onLost` pendant une copie ⇒ `rerouteAfterLoss` (existant) ; TV qui répond `/api/hello` avec un autre `id` (voisin avec le même nom de réseau : impossible par construction, mais testé) ⇒ `Leave` + ligne rouge ; OEM qui refuse `connect` par identifiants (R4) ⇒ `WdBackoff` puis BT, cause dite.

## À ne pas faire
Aucune jonction ni découverte en arrière-plan ; aucun `bindProcessToNetwork` ; aucune localisation ; pas d'écran (w18-09) ; ne pas toucher `UploadService.kt` ; pas de SSDP sur le groupe.

## Rapport
`STATUT`, sorties, temps de jonction mesurés (H-17), l'issue H-2/H-3 (faits attendus de longue date), le point d'audit, question : si H-3 est rouge sur 33+ (routage absent), passer **tout** par `WdNetworkSockets` (recommandé : oui).
