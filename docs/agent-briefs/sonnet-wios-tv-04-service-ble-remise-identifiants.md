# wios-tv-04 — TV (v2, conditionnel) : sonde BLE puis service GATT « CastBridge-TV » : identité, remise chiffrée des identifiants Wi-Fi Direct, demande d'allumage du groupe
<!-- routage architecte 2026-10-04 (vague iOS, v2) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (poignée de main, PIN, mot de passe Wi-Fi) · statut : **CONDITIONNEL** : étape 0 (sonde) d'abord ; étapes 1-3 seulement si la sonde dit « BLE périphérique possible » sur la TV de référence
> **Groupe : WIOS-TV v2** · porte : `tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.ble.*'` ; TV : build **verrouillé**
> **Jauge : ≈ 550 k jetons entrée / 30 k sortie** (effort L, ≈ 2,5 j ; étape 0 seule : S) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 3.1 « Pourquoi le QR d'abord », D-IOS-4, R18, § 6.4). Contexte : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 2.1 e (BLE, PAKE), § 9 (« BLE absent sur la TV », non mesuré). Branche `claude/wios-tv-04-ble`. Rapport : `docs/agent-reports/sonnet-wios-tv-04.md`.

## Objectif (autonome)
Le QR (v1) oblige à viser l'écran. Un iPhone sait parler BLE (`CoreBluetooth`, rôle central) mais **pas** Bluetooth classique. Si le contrôleur Bluetooth de la TV sait **annoncer** en BLE et ouvrir un serveur GATT, l'iPhone peut : trouver la TV sans QR, recevoir SSID + mot de passe du groupe de façon sûre après le PIN, et demander à la TV d'allumer son groupe.

## Étape 0 — sonde (toujours)
- `R/BleProbe.kt` + ligne dans le diagnostic existant (`GET /api/diag/...` de w18-04 si fusionné, sinon `GET /api/bluetooth` additif) : `FEATURE_BLUETOOTH_LE`, `adapter.isMultipleAdvertisementSupported`, `bluetoothLeAdvertiser != null`, `openGattServer` réussi, permissions (`BLUETOOTH_ADVERTISE`/`CONNECT` API 31+), version Android. **Aucun secret.**
- Rapport : faits sur la TV de référence (relevés par le propriétaire ou le coordinateur, commande `curl` fournie). **Si non ⇒ arrêter ici** ; le cahier est clos « BLE impossible sur cette TV », le QR reste la voie.

## Étapes 1-3 (si oui)
1. **Cœur pur** `android/core/src/main/kotlin/castbridge/core/ble/{BleHandoff,BleFrames}.kt` + tests + `tools/ios-vectors/ble-vectors.json` : poignée de main X25519 éphémère (réutiliser `C/owner/X25519.kt`) ; clé de session = HKDF-SHA256(secret partagé, sel = `castbridge-ble-v1` ‖ id TV) ; **confirmation par PIN** : HMAC-SHA256(clé dérivée du PIN et de la transcription) dans les deux sens (un essai par connexion ; `PinGuard` global 5 / 60 s, l'adresse BLE de l'iPhone changeant) ; remise de `{ssid, pass}` en AES-256-GCM (nonce compteur) ; commande `WANT_GROUP` (allumer le groupe, même effet que `CBTN WANT_WIFI_DIRECT`). Analyse de menace au rapport (passif : sûr ; actif : un essai de PIN par connexion, borné par le verrou ; hors ligne : impossible sans casser X25519).
2. **TV** `R/BleHandoff.kt` : annonce d'un UUID de service propre à CastBridge-TV + 4 hex de l'`id` (pas de nom de foyer), seulement CastBridge-TV à l'écran ou fenêtre « Ajouter un iPhone » ouverte ; GATT : `info` (lecture : id, proto, `pair`), `hs` (écriture/notification : poignée de main), `cmd` ; ≤ 2 connexions ; arrêt après 10 min sans usage.
3. **Compatibilité** : additive ; une TV sans BLE n'annonce rien ; `pin-http-v1` reste l'appairage de référence (le BLE ne fait que remettre les identifiants Wi-Fi et allumer le groupe).

## Fichiers possédés
`R/BleProbe.kt`, `R/BleHandoff.kt`, `C/ble/**`, `CT/ble/**`, `tools/ios-vectors/ble-vectors.json` ; zone `R/TvService.kt` (démarrage/arrêt du service BLE, ≤ 20 lignes) ; zone manifeste TV (permissions BLE). **Interdit** : `S/`, `ios/**`, routes HTTP existantes, protocole RFCOMM.

## Critères d'acceptation (mutations au rapport)
- Vecteurs : transcription complète reproductible (clés éphémères de test fixées) ; PIN faux ⇒ aucune remise ; mutation : HMAC sans la transcription ⇒ test « réflexion » rouge.
- Aucun mot de passe en clair dans une trame, un journal, un `toString`.
- Réexamen du chiffrement à l'export signalé au propriétaire (D-IOS-11) avant tout build iOS qui consomme ce service.

## À ne pas faire
- Inventer une cryptographie hors X25519/HKDF/HMAC/AES-GCM ; ajouter une dépendance ; annoncer en permanence ; mettre l'identité complète ou le nom du foyer dans l'annonce.
