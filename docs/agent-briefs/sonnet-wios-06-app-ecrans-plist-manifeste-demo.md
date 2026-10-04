# wios-06 — App iPhone : écrans SwiftUI (« Mes TV », « Ajouter ma TV », accueil), Info.plist, manifeste de confidentialité, textes français, mode démonstration
<!-- routage architecte 2026-10-04 (vague iOS, ordre 4) -->
> **Modèle : sonnet** · escalade : aucune (aucune logique de confiance : tout vient de `CBPair`/`CBLink`) · statut : **ATTEND wios-04, wios-05**
> **Groupe : WIOS** (ordre 4) · porte : `tools/agents/gradle-lock.sh -- xcodebuild -project ios/CastBridge.xcodeproj -scheme CastBridge -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max,OS=27.0' CODE_SIGNING_ALLOWED=NO test`
> **Jauge : ≈ 600 k jetons entrée / 35 k sortie** (effort L, ≈ 2,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 3.2, § 5.1, § 6.3, § 6.5, D-IOS-2, D-IOS-9, D-IOS-10). Vocabulaire : `android/core/src/main/kotlin/castbridge/core/ux/UiTexts.kt` (*Copier sur la TV et lire*, *Copier sur la TV*), `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md`. Branche `claude/wios-06-app`. Rapport : `docs/agent-reports/sonnet-wios-06.md`.

## Objectif (autonome)
Donner à l'app **CastBridge** (iPhone) sa navigation et ses écrans de liaison, en français, avec les clés Info.plist et le manifeste de confidentialité exigés par Apple, et un **mode démonstration** (TV simulée dans l'app) pour que le relecteur d'Apple, qui n'a pas de CastBridge-TV, puisse tout essayer (règles 2.1/4.2). L'app iOS n'a **aucune activation propre**, aucun prix, aucun achat, aucun appel Internet (D-IOS-2, D-IOS-10).

## Fichiers possédés
- **Nouveaux** : `ios/CastBridge/App/{RootView,AppModel,Tabs}.swift` ; `ios/CastBridge/Tv/{MyTvsView,AddTvView,PinEntryView,TvCardView,TvSettingsView}.swift` ; `ios/CastBridge/Demo/{DemoTv,DemoBanner}.swift` ; `ios/CastBridge/Help/{HelpView,LocalNetworkHelpView}.swift` ; `ios/CastBridge/Resources/Localizable.xcstrings` (fr seulement) ; `ios/CastBridge/PrivacyInfo.xcprivacy` ; `ios/CastBridgeUITests/{AddTvFlowTests,DemoModeTests}.swift`.
- **Zone** : `ios/CastBridge/Info.plist` (wios-01) : clés ci-dessous ; `ios/CastBridge/CastBridgeApp.swift` (point d'entrée vers `RootView`).
- **Interdit** : `ios/CastBridgeKit/**` (lecture seule), `ios/CastBridge/{Link,Send,Remote}/**` (autres cahiers), extensions.

## Spécification
1. Onglets : **« CastBridge TV »** (départ : TV par défaut, carte d'état vert/orange/rouge/noir de `CBLink.TvSignalText`), **« Envoyer »** (vide, rempli par wios-07), **« Télécommande »** (wios-09), **« Quiz »** (wios-09). Sans TV : bouton unique « Ajouter ma TV ».
2. « Ajouter ma TV » : (1) recherche Bonjour 3 s (`LinkCoordinator` de wios-04) ; (2) « Scanner le QR affiché par la TV » (TV : MENU › Connexion › Ajouter un iPhone) ; (3) « Saisir l'adresse » (IPv4) ; puis `PinEntryView` (« Tapez le code affiché sur la TV », longueur `pinLen`) ; erreurs de `PairReason` mot pour mot ; succès : « Salon est maintenant liée ».
3. Info.plist : `NSLocalNetworkUsageDescription`, `NSBonjourServices = ["_castbridge._tcp"]`, `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription` (textes exacts de la conception § 6.5), `NSAppTransportSecurity › NSAllowsLocalNetworking = YES` (et rien de plus large), `ITSAppUsesNonExemptEncryption = NO`, `UIRequiredDeviceCapabilities` minimal, orientation portrait (paysage pour la télécommande facultatif), `CFBundleDevelopmentRegion = fr`.
4. `PrivacyInfo.xcprivacy` : `NSPrivacyTracking = false`, `NSPrivacyTrackingDomains = []`, `NSPrivacyCollectedDataTypes = []`, `NSPrivacyAccessedAPITypes` pour les API réellement appelées (UserDefaults CA92.1 ; autres ajoutées par wios-11 après inventaire).
5. **Mode démonstration** : bouton discret « Essayer sans TV » sur l'écran sans TV ; `DemoTv` implémente les mêmes protocoles que le client réel (bibliothèque de 3 éléments, copie simulée à 4 Mo/s, télécommande qui répond) ; bandeau permanent « Démonstration : aucune TV réelle » ; jamais mélangé avec une vraie TV.
6. Accessibilité : Dynamic Type, libellés VoiceOver des boutons principaux, contraste ; textes courts ; aucune phrase anglaise visible.
7. Aucune mention de prix, d'achat, de clé ou de code d'activation dans l'app ; TV verrouillée ⇒ « Activez d'abord votre CastBridge-TV » sans lien externe.

## Critères d'acceptation
- `AddTvFlowTests` (XCUITest, TV factice + branche wios-tv-02, ou `DemoTv`) : sans TV ⇒ « Ajouter ma TV » ⇒ adresse ⇒ PIN faux (message) ⇒ PIN juste ⇒ carte verte.
- `DemoModeTests` : démonstration complète sans réseau ; bandeau visible sur tous les onglets.
- `plutil -lint` vert sur Info.plist et manifeste ; `grep -R "NSAllowsArbitraryLoads" ios/` vide ; `grep -Ri "prix\|acheter\|€\|FCFA" ios/CastBridge` vide.

## À ne pas faire
- Écrire de la logique d'appairage ou de réseau (paquet) ; ajouter une langue autre que le français ; afficher une adresse complète de TV hors réglages avancés.
