# wios-05 — Swift : appairage par PIN (`pin-http-v1`), clé d'appareil Ed25519 dans le trousseau, fiche par TV, renouvellement silencieux
<!-- routage architecte 2026-10-04 (vague iOS, ordre 3) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (PIN, clé privée, jeton, mot de passe Wi-Fi, 8 téléphones) · statut : **ATTEND wios-02, wios-tv-02** (vecteurs `pair-vectors.json`)
> **Groupe : WIOS** (ordre 3, en parallèle de wios-04) · porte : `tools/agents/gradle-lock.sh -- swift test --package-path ios/CastBridgeKit --filter CBPairTests` + intégration TV factice (branche de wios-tv-02)
> **Jauge : ≈ 400 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 3.2, § 3.4, D-IOS-3, D-IOS-13, D-IOS-15). Protocole TV : `docs/agent-briefs/sonnet-wios-tv-02-appairage-http-pin-cle-appareil.md`. Branche `claude/wios-05-appairage`. Rapport : `docs/agent-reports/sonnet-wios-05.md`.

## Objectif (autonome)
Lier l'iPhone à une TV avec le PIN affiché par la TV, **une fois**, puis rester lié sans PIN : clé Ed25519 propre à l'iPhone (`CryptoKit` `Curve25519.Signing`), jeton 12 h, renouvellement par signature d'un défi. Garder, par TV (`tvId`), la clé privée, le jeton, le SSID et le mot de passe Wi-Fi Direct, la dernière IP : équivalent iOS de `C/trust/PinBook.kt` (lire), dans le **trousseau**.

## Fichiers possédés
- **Nouveaux** : `ios/CastBridgeKit/Sources/CBPair/{PinPairing,PinInput,DeviceKey,TvBook,KeychainStore,PairReason,CredentialGate}.swift` ; tests `ios/CastBridgeKit/Tests/CBPairTests/{PairVectorTests,PinPairingTests,PinInputTests,TvBookTests,CredentialGateTests}.swift`.
- **Remplace** : `ios/CastBridgeKit/Sources/CBPair/Placeholder.swift`.
- **Interdit** : écrans (wios-06), `CBTv`/`CBLink`/`CBTransfer` (lecture seule ; utiliser leurs types publics).

## Spécification
1. `PinInput` : longueur = `pinLen` de `/api/hello` (défaut 6), saisie acceptée 4 à 8 chiffres, chiffres ASCII seulement ; jamais conservé après la réponse (effacé de la mémoire de l'écran : `String` remplacée, pas de copie dans un modèle persistant).
2. `DeviceKey` : une clé **par TV** (révocation indépendante) ; trousseau `kSecClassGenericPassword`, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, `kSecAttrSynchronizable = false`, service `com.sti-cm.castbridge.tv`, compte `<tvId>.key` ; `kid = hex(SHA-256(pub)[0:8])`.
3. `PinPairing.pair(tv, pin, name)` : `POST /api/pair/pin` (PIN par `TvCredential.pin`, corps `{name, pub, platform:"ios"}`) ; 409 `full` ⇒ `PairReason.full(noms)` (« Cette TV a déjà 8 téléphones. Retirez-en un sur la TV : MENU › Téléphones ») ; 401 ⇒ essais restants / `retryAfter` (jamais de nouvel essai automatique) ; 403 `locked` ⇒ « Activez d'abord la TV » ; 404 ⇒ TV ancienne : `PairReason.legacy` (mode v0 : PIN gardé dans le trousseau **seulement** si un réglage interne « Mode propriétaire (v0) » est actif, désactivé par défaut et absent des builds externes : D-IOS-15).
4. `renew` : `GET /api/pair/challenge` ⇒ signature de `castbridge-pair-renew-v1\n<tvId>\n<nonce>` ⇒ `POST /api/pair/renew` ; à mi-vie du jeton ou sur 401 `token` ; `unknown_phone` ⇒ fiche marquée « Retiré par la TV » (nouveau PIN nécessaire), jamais de boucle.
5. `TvBook` : `tvId → {name, lastIp, token+expiresAt, wdSsid, wdPass, keyRef}` ; « Oublier cette TV » efface tout (clé comprise) ; aucune donnée dans `UserDefaults` sauf l'ordre d'affichage et la TV par défaut ; aucune sauvegarde iCloud.
6. `CredentialGate` : un PIN ou jeton refusé n'est jamais renvoyé (règle de `BT-PLUG-AND-PLAY.md` § Identifiants).
7. `KeychainStore` derrière un protocole (tests avec un faux en mémoire ; un test réel sur simulateur).

## Critères d'acceptation (mutations au rapport)
- `PairVectorTests` : signatures `pair-vectors.json` vérifiées par `CryptoKit` ; les 6 cas refusés refusés ; mutation : oublier le domaine ⇒ rouge. **Note** : `CryptoKit` signe avec aléa ; les vecteurs servent à **vérifier** des signatures Kotlin et à vérifier que la TV factice accepte celles de l'iPhone.
- `PinPairingTests` (faux serveur + TV factice de wios-tv-02) : appairage, 9e téléphone refusé avec noms, renouvellement sans PIN, téléphone retiré ⇒ état « Retiré par la TV ».
- `TvBookTests` : « Oublier » efface les 5 éléments ; aucun secret dans `String(describing:)`.
- Test de source : `PinPairing` est le seul appelant de `/api/pair/*`.

## À ne pas faire
- Enclave sécurisée P-256 (la TV vérifie Ed25519) ; stocker le PIN (hors v0 interne) ; synchroniser le trousseau ; dépendance externe.
