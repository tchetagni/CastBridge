# w18-07 — CastBridge-TV : groupe Wi-Fi Direct persistant, allumé à l'écran ; identifiants chiffrés ; DNS-SD P2P ; QR étendu ; rotation ; variante SoftAP si le verdict terrain l'exige

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (radios TV, identifiants) · statut : **ATTEND** verdict terrain (VERT/ORANGE) **et** sortie du gel W15 (ou exception « correctif terrain R-14 »)
> **Groupe : W18b-1** (vague W18b, TV) · prérequis : w18-01, 02, 04 fusionnés ; `docs/agent-reports/w18-field-test.md` rempli · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin :core:test --tests 'castbridge.core.link.*' --tests 'castbridge.core.journey.WdJourneyTest'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 3 j) · audit Opus : obligatoire

**Vague 18b · Effort L · Modèle : sonnet · Statut ATTEND.** Conception : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 3.1, § 4, § 5.3, § 6, § 7.3, D-W18-2, D-W18-3, D-W18-5, D-W18-7. Branche `claude/sonnet-w18-07` (depuis `integration/agents` **après** fusion de `claude/auto-wifi-direct`). Rapport : `docs/agent-reports/sonnet-w18-07.md`. Règle W15 R2/R9 : seul cahier sur `R/TvService.kt`.

## Objectif
(1) `WifiDirectGroup` : identifiants **persistants** (`TvPrefs.wdCredentials()` dans le stockage chiffré ; créés une fois par `WdCredentials.fresh`), groupe créé avec `setNetworkName/setPassphrase` + `enablePersistentMode(true)` (repli : `false` si la puce refuse), **allumé** dès que `PlayerActivity` est au premier plan **si** la TV n'a pas de LAN ou si le verdict F9 dit « LAN gardé » ; sinon à la demande (CBTN, comportement R-14) ; retiré 2 min après le dernier client **sauf** réception, cast, télécommande ouverte (`RemoteSession` active), salle Quiz ouverte (corrige l'audit « groupe jamais retiré avec une télécommande ouverte » **dans l'autre sens** : la télécommande compte comme usage) ; (2) `WdDnsSd` : `addLocalService(WifiP2pDnsSdServiceInfo("castbridge", "_castbridge._tcp", {id, name, v, proto}))` quand le groupe est actif (**jamais** MAC ni mot de passe) ; (3) `WdQr` : contenu du QR W7 étendu de `wd`/`wp`, affiché **sur demande** (MENU › Connexion › « Montrer le QR ») et au premier démarrage sans téléphone de confiance (10 min), jamais sur l'accueil ; (4) rotation : « Retirer ce téléphone » et « Changer le mot de passe Wi-Fi Direct » ⇒ `WdCredentials.fresh` + re-création du groupe ; (5) relevé des `WdFacts` (w18-04) : `PackageManager`, `requestGroupInfo`, `/sys/class/net`, `UsbHardware`, dernière erreur ; (6) **variante SoftAP** (seulement si verdict `ORANGE_B_SOFTAP`) : `WifiManager.startLocalOnlyHotspot` dans un service, identifiants lus du `LocalOnlyHotspotReservation` et remis par CBTN/HELLO **à chaque session** (`wd.persist=0`), fermeture propre.

## Pourquoi (preuves)
- `R/WifiDirectGroup.kt` (branche R-14) : `start(forPhone)`, mot de passe frais, `removeGroup` avant `createGroup`, `clients()`, `lastError` ; `R/TvService.kt` `linkInfo(peer, flags)`, `wdLeaseCheck()` (bail 30 s / 45 s / 10 min), `wd.touch()` sur jeton HTTP ; `R/TvPrefs.kt` (stockage chiffré, PIN) ; `R/PlayerActivity.kt` (`NEARBY_WIFI_DEVICES` au premier lancement) ; `R/QuizHub.kt` (salle) ; `R/PairActivity.kt` (QR W7 : non écrit ; ce cahier écrit `WdQr` consommé par w7-14 plus tard).
- `C/link/{WdCredentials,WdDiag}.kt`, `C/tv/WdDiagRoutes.kt` (w18-01/04) ; `C/tv/BtProtocol.kt` `LinkInfo` (`wd.persist`).
- Faits Android : `WifiP2pConfig.Builder` API 29 ; `setGroupOperatingBand(GROUP_OWNER_BAND_5GHZ)` si F3 dit bi-bande ; `startLocalOnlyHotspot` API 26 (SSID/mot de passe tirés par Android, non choisissables).

## Fichiers possédés
`R/WifiDirectGroup.kt`, `R/TvPrefs.kt` (`wdCredentials`, `wdPolicy`), nouveaux `R/WdDnsSd.kt`, `R/WdQr.kt`, `R/WdFactsAndroid.kt`, `R/WdSoftAp.kt` (variante b), `R/TvService.kt` (zone : `linkInfo`, `wdLeaseCheck` ⇒ `wdPolicyTick`, démarrage au premier plan, rotation sur retrait), `R/PlayerActivity.kt` (MENU : « Montrer le QR », « Changer le mot de passe Wi-Fi Direct », « Diagnostic Wi-Fi Direct » ⇒ ≤ 30 lignes), `R/ConnectionActivity.kt` (page diagnostic : lignes de `WdDiag`). **Hors zone** : `R/BtServer.kt`, `R/QuizHub.kt` (w18-10), `C/**`, `S/**`.

## Étapes
1. **Rouge d'abord (JVM)** : les décisions de ce cahier sont dans `C/link/` (déjà testées) ; ce cahier ajoute `CT/link/WdGroupPolicyTest` ? **Non** : il ne possède pas `C/`. Il écrit ses règles de tenue du groupe **dans `WdSession`/`WdPolicy` existants** si elles manquent : si une règle manque, **s'arrêter** et le dire au rapport (w18-02 la fournit) ; l'exécutant Android ne contient aucun `if` de politique.
2. `WifiDirectGroup.ensure(mode: PERSISTENT|ON_DEMAND)` ; `rotate()` ; `facts(): WdFacts` ; journal sans secret.
3. `TvService` : `onResume` de `PlayerActivity` ⇒ `wd.ensure(policy)` ; `linkInfo` renvoie `WdCredentials.encodeInto` **seulement** pour `trusted(peer)` (existant `btTrusted`) ; `WD_RELEASE` n'arrête plus un groupe persistant (il note l'usage) ; retrait d'un téléphone ⇒ `rotate()`.
4. `WdDnsSd` : enregistré à la création du groupe, retiré à sa fin ; TXT ≤ 4 clés.
5. `WdQr` : `castbridge://tv?id=…&bt=…&ip=192.168.49.1&port=8765&pub=…&wd=…&wp=…` ; rendu par le générateur QR existant du MENU Wi-Fi Direct (`WifiDirect.wifiUri` sert déjà) ; **le texte en clair du mot de passe n'est plus affiché** sous le QR (il l'était pour le groupe du MENU : on garde une ligne « Mot de passe : afficher » derrière un bouton, pour le Mac).
6. **Sur appareil (propriétaire, liste humaine)** : H-1…H-11 de `docs/agent-reports/auto-wifi-direct.md` **plus** : H-12 groupe présent au retour sur l'accueil après veille ; H-13 `dumpsys wifip2p` montre le même nom après redémarrage ; H-14 téléphone retiré ⇒ nouveau nom/mot de passe ; H-15 `logcat` sans mot de passe ; H-16 DNS-SD vu depuis le téléphone (w18-08).
7. **Vert** : porte ; `:core:test` complet ; `compileDebugKotlin` des deux apps.

## Critères d'acceptation
Porte verte ; `grep -n "pass" android/receiver/src/main/kotlin/castbridge/receiver/WifiDirectGroup.kt | grep -i "Log\."` vide ; aucune politique dans `R/` (`grep -n "IDLE\|_MS =" R/WifiDirectGroup.kt` ne montre que des constantes importées de `C/link/`) ; rapport d'audit Opus : identifiants (lecture/écriture chiffrée, qui les reçoit), radios (quand le groupe est créé/retiré, effet sur le Wi-Fi STA), aucun geste ajouté à l'écran TV hors MENU.

## Cas limites
`createGroup` ⇒ `BUSY` après veille (groupe fantôme) ⇒ `removeGroup` puis nouvel essai une fois, puis `lastError = failed` (diag) ; `FEATURE_WIFI_DIRECT` absent ⇒ `wd.cap=0`, rien d'autre ; Wi-Fi de la TV éteint ⇒ `wifi_off` (une application ne peut pas l'allumer : phrase « Paramètres › Réseau ») ; TV sur un LAN avec F9 rouge ⇒ `ON_DEMAND` ; `enablePersistentMode(true)` refusé ⇒ identifiants fixes sans persistance système (même nom ⇒ même BSSID en pratique : à noter au rapport).

## À ne pas faire
Aucune décision de politique dans `R/` ; pas de mot de passe à l'écran par défaut ni dans `/api/connections` ; pas de groupe pendant la veille ; pas de `WifiNetworkSuggestion` ni de point d'accès du téléphone (écartés) ; ne pas toucher `R/BtServer.kt`.

## Rapport
`STATUT`, sorties, le relevé `WdFacts` réel de la TV de référence (sans secret), l'issue H-12…H-16, le point d'audit, question : bande 5 GHz forcée si bi-bande (recommandé : oui si F3 le dit).
