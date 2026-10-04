# wios-tv-03 — TV : « Ajouter un iPhone » (QR), iPhones dans l'écran « Téléphones », TXT mDNS additif, groupe Wi-Fi Direct gardé pour les iPhones
<!-- routage architecte 2026-10-04 (vague iOS, ordre 5) -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (le QR porte un mot de passe Wi-Fi : affiché sur demande seulement) · statut : **ATTEND wios-tv-02, w18-07** (et la sortie du gel W15 R3/R5 pour `R/`, ou une exception du propriétaire)
> **Groupe : WIOS-TV** (ordre 5) · porte : `tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*IphoneQr*' --tests '*PhoneRoster*'` puis `:receiver:assembleDebug -PrequireActivation=true` (build **verrouillé** seulement)
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 3.1, § 3.3, D-IOS-4, D-IOS-5). Contexte : `docs/coordination/DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` (§ 2.1 f, § 3.1, D-W18-2, D-W18-3, D-W18-5, D-W18-7). Branche `claude/wios-tv-03-qr-iphone`. Rapport : `docs/agent-reports/sonnet-wios-tv-03.md`.

## Objectif (autonome)
Un iPhone ne peut ni appairer en Bluetooth classique ni demander à la TV de créer son groupe Wi-Fi Direct. La TV doit donc (1) **montrer sur demande** un QR qui contient tout ce qu'il faut pour la joindre, (2) **lister** les iPhone dans son écran de téléphones (8 au plus, même registre), (3) annoncer en mDNS de quoi les reconnaître (sans secret), (4) garder son groupe allumé quand un iPhone de confiance existe.

## Fichiers possédés
- **Nouveaux** : `android/core/src/main/kotlin/castbridge/core/link/IphoneQr.kt` (pur : contenu du QR `castbridge://tv?v=1&id=<8 hex>&ip=<IPv4>&port=8765&wd=<SSID>&wp=<mot de passe>` ; sans `wd`/`wp` si aucun groupe persistant) ; `android/core/src/test/kotlin/castbridge/core/link/IphoneQrTest.kt` (rejoue `tools/ios-vectors/qr-vectors.json`) ; `android/receiver/src/main/kotlin/castbridge/receiver/AddIphoneActivity.kt`.
- **Zones** : `R/PhonesActivity.kt` (ligne iPhone : nom, « iPhone · Wi-Fi », retrait ⇒ révocation de la clé `key:<kid>` et rotation du secret Wi-Fi Direct si w18-07 l'implémente, D-W18-7) ; `R/TvService.kt` (zone mDNS : TXT additif `id=<8 hex>`, `pair=pin-http-v1` ; zone bail du groupe : « un iPhone de confiance existe » compte comme raison de garder le groupe, à l'écran et sans LAN, D-IOS-5) ; menu « Connexion » : entrée « Ajouter un iPhone » ; `C/trust/PhoneRoster.kt` (type de pair `IPHONE`, ≤ 15 lignes).
- **Interdit** : `C/tv/ReceiverServer.kt`, `C/trust/PinPairing.kt` (wios-tv-02), `S/`, `ios/**`.

## Spécification
1. `AddIphoneActivity` : écran D-pad ; QR grand format (`C/quiz/QrCode.kt` existant) ; sous le QR : « Sur l'iPhone : CastBridge › Ajouter ma TV › Scanner le QR », puis « Code de la TV : 77•••• » (le PIN reste affiché comme aujourd'hui) ; affiché **10 min** au plus, fermé par RETOUR ; jamais sur l'accueil ; mot de passe jamais écrit en clair à l'écran (seulement dans le QR).
2. Sans groupe persistant (w18-07 absent ou puce sans GO) : QR sans `wd`/`wp` + phrase « Reliez l'iPhone et la TV à la même box » ; avec variante SoftAP (verdict W18 b) : identifiants courants de la session.
3. `IphoneQr` : IPv4 de l'interface vers laquelle l'iPhone viendra (groupe : 192.168.49.1 ; box : IP de `wlan0`/`eth0`) ; échappement URL des champs ; refuse une IP publique.
4. Journal : jamais le contenu du QR, jamais le mot de passe (`Redact`).
5. Build TV **verrouillé** uniquement (`-PrequireActivation=true`) ; l'APK est copié par le coordinateur dans le `Download` de la clé USB (règle du propriétaire), pas par l'agent.

## Critères d'acceptation (mutations au rapport)
- `IphoneQrTest` : vecteurs valides produits identiques, invalides jamais produits ; mutation : oublier l'échappement de `;` ⇒ rouge.
- `PhoneRoster` : 7 téléphones Bluetooth + 1 iPhone = plein ; retrait de l'iPhone ⇒ sa clé ne renouvelle plus (test avec `PinPairing`).
- Le rapport liste ce qui n'a pas été vu sur la vraie TV (rendu du QR à 720p, lecture par un iPhone) ⇒ P-IOS-2.

## À ne pas faire
- Afficher le QR sans action ; mettre un secret dans le TXT mDNS ; build déverrouillé ; modifier les routes HTTP.
