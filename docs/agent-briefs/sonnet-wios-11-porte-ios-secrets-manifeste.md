# wios-11 — Porte de livraison iOS (`tools/ios/check.sh`) : tests, simulateur sous verrou, secrets, aucune URL Internet, manifeste ajusté
<!-- routage architecte 2026-10-04 (vague iOS, ordre 6) -->
> **Modèle : haiku** · escalade : sonnet si un contrôle exige du Swift · statut : **ATTEND wios-06…09**
> **Groupe : WIOS** (ordre 6) · porte : `tools/ios/check.sh` vert sur la branche intégrée
> **Jauge : ≈ 150 k jetons entrée / 12 k sortie** (effort S, ≈ 0,5 j) · exécutant le moins cher compétent : haiku

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 6.5, § 7, D-IOS-10, D-IOS-12). Branche `claude/wios-11-porte`. Rapport : `docs/agent-reports/sonnet-wios-11.md`.

## Objectif (autonome)
Une seule commande qui dit si `ios/` est livrable en TestFlight interne, sur ce Mac (aucune intégration continue distante n'existe pour iOS).

## Fichiers possédés
- **Nouveaux** : `tools/ios/check.sh`, `tools/ios/README.md`.
- **Zone** : `ios/CastBridge/PrivacyInfo.xcprivacy` (compléter `NSPrivacyAccessedAPITypes` selon l'inventaire réel des API appelées dans `ios/`).
- **Interdit** : tout autre fichier.

## Spécification (`tools/ios/check.sh`, sortie française, code 0/1)
1. `tools/agents/gradle-lock.sh -- swift test --package-path ios/CastBridgeKit`.
2. `tools/agents/gradle-lock.sh -- xcodebuild -project ios/CastBridge.xcodeproj -scheme CastBridge -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max,OS=27.0' CODE_SIGNING_ALLOWED=NO test` (TV factice démarrée par `tools/ios/fake-tv.sh` en arrière-plan, arrêtée à la fin).
3. Secrets : aucune clé privée PEM/base64 de 32/64 o hors vecteurs de **test** identifiés, aucun `DEVELOPMENT_TEAM` renseigné, aucun PIN/jeton littéral, aucune chaîne `superadmin`/`OwnerVault`/`SuperAdmin`.
4. Réseau : aucune URL `http(s)://` vers un nom d'hôte Internet dans `ios/CastBridge*/**` et `ios/CastBridgeKit/Sources/**` (v1 : D-IOS-10) ; aucune référence à `castbridge-play`, `/api/v1/`, `bridge.`.
5. Info.plist : présence de `NSLocalNetworkUsageDescription`, `NSBonjourServices`, `NSCameraUsageDescription`, `NSAllowsLocalNetworking`, `ITSAppUsesNonExemptEncryption`, absence de `NSAllowsArbitraryLoads` ; `plutil -lint` du manifeste.
6. Manifeste : inventaire par `grep` des API à raison déclarée (`UserDefaults`, `systemUptime`/`mach_absolute_time`, `volumeAvailableCapacity`, attributs de date de fichier) ⇒ chaque API trouvée a sa raison dans `PrivacyInfo.xcprivacy`, sinon échec avec le nom du fichier.
7. Résumé final : tests (nombre), durée, contrôles OK/KO.

## Critères d'acceptation
- Vert sur la branche intégrée ; rouge (avec fichier:ligne) si l'on ajoute `NSAllowsArbitraryLoads`, une URL `https://exemple.org`, ou un `DEVELOPMENT_TEAM = ABCDE12345` (mutations au rapport, annulées ensuite).

## À ne pas faire
- Signer, archiver, installer ; appeler le réseau ; lancer deux builds en parallèle hors verrou.
