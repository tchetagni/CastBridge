# wios-04 — Swift : trouver et joindre la TV (`CBLink` + couche app) : Bonjour, réseau local, QR, groupe Wi-Fi Direct de la TV comme client Wi-Fi
<!-- routage architecte 2026-10-04 (vague iOS, ordre 3) -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (le mot de passe Wi-Fi ne fuit nulle part) · statut : **ATTEND wios-02** (et wios-tv-01 pour `qr-vectors.json`)
> **Groupe : WIOS** (ordre 3, en parallèle de wios-05) · porte : `tools/agents/gradle-lock.sh -- swift test --package-path ios/CastBridgeKit --filter CBLinkTests` ; partie appareil : parcours P-IOS-2/P-IOS-3 (wios-10), **non exécutable par l'agent**
> **Jauge : ≈ 400 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 2 F2/F3/F22, § 3.1, § 3.2, D-IOS-4). Contexte TV : `docs/coordination/DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 2.1 f, § 3.1, D-W18-2, D-W18-5. Branche `claude/wios-04-decouverte`. Rapport : `docs/agent-reports/sonnet-wios-04.md`.

## Objectif (autonome)
Un iPhone n'a **ni Wi-Fi Direct ni Bluetooth classique**. Il joint la TV de deux façons : (A) même box : Bonjour `_castbridge._tcp` (la TV l'annonce déjà, TXT `role=receiver`) ; (B) sans box : il rejoint le **groupe Wi-Fi Direct de la TV comme un client Wi-Fi WPA2 ordinaire** (`NEHotspotConfiguration`), avec le SSID `DIRECT-CB-…` et le mot de passe lus dans le **QR** affiché par la TV (`castbridge://tv?v=1&id=&ip=&port=&wd=&wp=`) ou dans un QR `WIFI:`. Puis il vérifie l'identité par `/api/hello`.

## Fichiers possédés
- **Nouveaux (paquet, pur)** : `ios/CastBridgeKit/Sources/CBLink/{QrPayload,WifiUri,TvEndpoint,EndpointResolver,LinkState,TvSignalText}.swift` ; tests `ios/CastBridgeKit/Tests/CBLinkTests/{QrPayloadVectorTests,EndpointResolverTests,LinkStateTests,TvSignalTextTests}.swift`.
- **Nouveaux (app, minces)** : `ios/CastBridge/Link/{BonjourBrowser,LocalNetworkProbe,HotspotJoiner,QrScannerView,LinkCoordinator}.swift` ; `ios/CastBridge/CastBridge.entitlements` (**zone** : `com.apple.developer.networking.HotspotConfiguration` ; wios-01 possède le fichier, ajout d'une clé seulement).
- **Remplace** : `ios/CastBridgeKit/Sources/CBLink/Placeholder.swift`.
- **Interdit** : écrans autres que `QrScannerView` ; `CBPair`, `CBTransfer`, `CBTv` (lecture seule).

## Spécification
1. `QrPayload.parse` : vecteurs `qr-vectors.json` (valides/invalides avec motif) ; `id` 8 hex, IPv4 **privée** seulement, port 1..65535, `wd` commençant par `DIRECT-`, champs en double refusés ; `WifiUri.parse` (échappement ZXing).
2. `BonjourBrowser` : `NWBrowser(for: .bonjourWithTXTRecord(type: "_castbridge._tcp", domain: nil))` ; ignore TXT `role=server` ; résout en **IPv4** (connexion `NWConnection` puis `currentPath.remoteEndpoint`, ou équivalent) ; jamais d'adresse `.local` envoyée à la TV.
3. `LocalNetworkProbe` : détecte l'invite refusée (`NWPath`/erreur `-65555` ou équivalent documenté au rapport) ⇒ état « Réseau local refusé » + bouton vers `UIApplication.openSettingsURLString`.
4. `HotspotJoiner` : `NEHotspotConfiguration(ssid:passphrase:isWEP:false)`, `joinOnce = false` ; erreurs `alreadyAssociated` = succès, `userDenied` ⇒ phrase, `invalid*` ⇒ « QR illisible » ; après jonction : sonde `http://192.168.49.1:8765/api/hello` (ou `ip` du QR) jusqu'à 10 s ; **ne joint jamais** le groupe si une TV répond déjà par la box (règle W18 : jamais à la place d'un LAN qui répond).
5. `EndpointResolver` : une seule adresse utilisable par TV, péremption 30 s (comme `C/tv/TvEndpointResolver.kt`) ; identité par `id` (192.168.49.1 n'est jamais une identité) ; `id` du QR ≠ `id` de hello ⇒ « Ce n'est pas la TV scannée ».
6. `QrScannerView` : `DataScannerViewController` (VisionKit, iOS 16, appareils A12+) sinon `AVCaptureMetadataOutput` ; `NSCameraUsageDescription` (texte de la conception § 6.5, ajouté par wios-06).
7. `TvSignalText` : vert/orange/rouge/noir, phrases de `signal-texts-vectors.json` ; cas iOS en plus : « Votre iPhone ne peut joindre cette TV que par Wi-Fi… » (situation C, texte exact en conception § 3.1).
8. Secrets : le mot de passe ne passe jamais par `print`, `Logger`, `description`, une URL d'erreur ; il est remis à `CBPair.TvBook` (wios-05) par une fonction unique.

## Critères d'acceptation (mutations au rapport)
- `QrPayloadVectorTests` 100 % ; mutation : accepter une IP publique ⇒ rouge.
- `EndpointResolverTests` : deux TV, mauvaise `id` refusée, péremption 30 s.
- Build simulateur vert ; le rapport liste **ce qui n'a pas pu être exercé** (NEHotspot, invite réseau local, Bonjour réel) et renvoie aux parcours P-IOS-2/3.
- Test de source : aucune occurrence de `wp`/`passphrase` dans un appel de journal.

## À ne pas faire
- BLE (v2, wios-tv-04 côté TV) ; balayage en fond ; `NSAllowsArbitraryLoads` ; demander la localisation.
