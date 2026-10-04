# wios-01 — Squelette `ios/` : paquet SwiftPM `CastBridgeKit`, projet Xcode à trois cibles, chargeur de vecteurs, porte minimale
<!-- routage architecte 2026-10-04 (vague iOS, ordre 1) -->
> **Modèle : sonnet** · escalade : aucune · statut : **PRÊT** (répertoire neuf, hors gel Android)
> **Groupe : WIOS** (ordre 1, en parallèle de wios-tv-01) · porte : `tools/agents/gradle-lock.sh -- swift test --package-path ios/CastBridgeKit` puis `tools/agents/gradle-lock.sh -- xcodebuild -project ios/CastBridge.xcodeproj -scheme CastBridge -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max,OS=27.0' CODE_SIGNING_ALLOWED=NO build`
> **Jauge : ≈ 200 k jetons entrée / 15 k sortie** (effort S, ≈ 0,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 4.2, § 4.3, § 6.1, D-IOS-6, D-IOS-7, D-IOS-12). Branche `claude/wios-01-squelette`. Rapport : `docs/agent-reports/sonnet-wios-01.md`.

## Objectif (autonome)
Créer la charpente de l'app iPhone **CastBridge** (compagnon de CastBridge-TV) pour que les cahiers suivants travaillent en parallèle sur des fichiers disjoints. Mac : Xcode 27.0, Swift 6.4, SDK iOS 27.0, un seul environnement de simulation iOS 27.0. **Aucun build signé** : `CODE_SIGNING_ALLOWED=NO` partout ; aucune équipe, aucun profil, aucun identifiant Apple écrit dans le projet (le propriétaire choisira son équipe dans Xcode).

## Fichiers possédés
- **Nouveaux** : `ios/CastBridgeKit/Package.swift` (cibles `CBCore`, `CBTv`, `CBTransfer`, `CBLink`, `CBPair` ; plateformes iOS 16, macOS 13 ; mode de langage Swift 6 ; **aucune dépendance externe**) ; `ios/CastBridgeKit/Sources/CBCore/{Hex,Json,Sha256,Vectors}.swift` ; un fichier `Placeholder.swift` par autre cible (remplacé par les cahiers suivants) ; `ios/CastBridgeKit/Tests/CBCoreTests/{HexTests,VectorsLoaderTests}.swift` ; `ios/CastBridge.xcodeproj` (dossiers synchronisés Xcode 16+, cibles `CastBridge` (app), `CastBridgeShare` (extension de partage, vide), `CastBridgeActivity` (extension Widget, vide), `CastBridgeUITests`) ; `ios/CastBridge/CastBridgeApp.swift` (écran « CastBridge » vide) ; `ios/CastBridge/Info.plist` (clés minimales, déploiement iOS 16.0, `CFBundleDisplayName` = « CastBridge ») ; `ios/.gitignore` (`DerivedData/`, `*.xcuserstate`, `xcuserdata/`, `.build/`) ; `ios/docs/BUILD.md`.
- **Interdit** : tout fichier hors `ios/` ; `DEVELOPMENT_TEAM`, `PROVISIONING_PROFILE*`, `CODE_SIGN_IDENTITY` renseignés.

## Spécification
1. Identifiants : `com.sti-cm.castbridge`, `.share`, `.activity` ; groupe d'apps déclaré dans les fichiers `.entitlements` (`group.com.sti-cm.castbridge`), **sans** équipe.
2. `Vectors.swift` : `Vectors.load(_ name: String) throws -> Data` qui trouve `tools/ios-vectors/<name>` en remontant depuis `#filePath` jusqu'à la racine du dépôt (contient `tools/ios-vectors`) ; erreur claire si absent. Pour le simulateur : copie des vecteurs dans le paquet de tests (ressource) par une phase de build documentée.
3. `Sha256` par `CryptoKit` ; `Hex` minuscules ; `Json` = `JSONDecoder` avec clés inconnues ignorées (règle additive de la TV).
4. `ios/docs/BUILD.md` : commandes de la porte, verrou obligatoire, liste de ce que le simulateur ne sait pas faire (Bluetooth, `NEHotspotConfiguration`, invite réseau local fidèle, fond), et « aucun agent ne signe ni n'installe sur un appareil ».

## Critères d'acceptation
- `swift test --package-path ios/CastBridgeKit` vert (≥ 6 tests) ; `xcodebuild … build` vert pour les 3 cibles, simulateur iOS 27.0.
- `grep -R "DEVELOPMENT_TEAM = [A-Z0-9]" ios/` vide ; aucun fichier hors `ios/` modifié (`git diff --stat`).
- Le rapport donne les durées des deux builds (base du planning des cahiers suivants).

## À ne pas faire
- Ajouter une dépendance (CocoaPods, SwiftPM distant, Carthage) ; utiliser le réseau ; signer ; écrire du code d'écran au-delà du vide.
