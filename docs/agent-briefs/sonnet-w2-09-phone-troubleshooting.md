# w2-09 — CastBridge (téléphone) : une raison en français quand la TV est injoignable, un seul écran « Dépannage »

**Vague 2 · Effort M (≈ 1,5 j) · Statut PRÊT.** Branche `claude/sonnet-w2-09`. Rapport : `docs/agent-reports/sonnet-w2-09.md`.

## Objectif
1. Un modèle pur `TvReachability` (cœur) transforme l'état réseau/Bluetooth/HTTP en **une** raison en français et **une** action (« TV éteinte ou CastBridge-TV fermé », « Pas sur le même Wi-Fi », « Bluetooth éteint », « Version de CastBridge-TV trop ancienne : mettez-la à jour »).
2. Les écrans qui affichent aujourd'hui des exceptions brutes utilisent ce modèle.
3. Un écran « Dépannage » (sous Ma TV) remplace les trois diagnostics dispersés : vérifications en liste (Wi-Fi commun, Bluetooth, TV joignable, versions), puis « Copier le rapport » (sans secret).
4. L'envoi par Bluetooth affiche une estimation de durée.

## Pourquoi (preuves)
- `S/TvHome.kt:449` « TV injoignable : ${e.message} » (texte d'exception Java affiché), `:178` « Recherche de la TV… (même Wi-Fi, app CastBridge TV installée) », `S/UploadService.kt:185,191` « TV introuvable » : trois formulations d'un même état.
- Trois « Diagnostic » : `S/TvPairScreen.kt:93,108,306`, `S/MyTvActivity.kt:117` (« Copier le diagnostic » existe déjà), `S/BtRoutesScreen.kt:138`.
- `S/TvHome.kt:159` « Envoi par Bluetooth (plus lent que le Wi-Fi) » sans estimation (≈ 200 ko/s ⇒ 700 Mo ≈ 1 h).
- Avertissements de version dispersés : `S/ParentalScreen.kt:103`, `S/AssistantModel.kt:315`, `S/ParentalInbox.kt:86`.
- Audit : UX-11, G4, J2-J3.

## Fichiers possédés
Nouveau `C/tv/TvReachability.kt`, nouveau `android/core/src/test/kotlin/castbridge/core/tv/TvReachabilityTest.kt`, `S/TvHome.kt`, `S/UploadService.kt`, `S/TvPairScreen.kt`, `S/MyTvActivity.kt`, `S/BtRoutesScreen.kt`, nouveau `S/TroubleshootScreen.kt`. **Hors zone** : `S/ActivateTvActivity.kt`, `S/MainActivity.kt` (w2-03), `S/LotsRuntime.kt`, `S/RentalDeliveryActivity.kt` (w2-06), `C/tv/TvClient.kt` (w2-14).

## Étapes
1. `TvReachability` (cœur) : `data class Inputs(wifiConnected, sameSubnet: Boolean?, btEnabled, btBonded, httpResult: HttpOutcome (OK|TIMEOUT|REFUSED|UNAUTHORIZED|OTHER(message)), tvVersionCode: Int?, minVersionCode: Int)` → `data class Verdict(reason: String, action: String, severity)` ; table de décision testée (≥ 10 cas).
2. `TvHome`, `UploadService`, `TvPairScreen` : remplacer les messages par `Verdict.reason` (+ `action` en sous-ligne) ; ne plus afficher `e.message`.
3. `TroubleshootScreen` (Compose) : liste de contrôles avec icônes OK/KO, bouton « Relancer », « Copier le rapport » (réutiliser le rapport de `MyTvActivity.kt:117`), lien « Mettre à jour CastBridge-TV » si version trop ancienne (vers l'installation d'APK existante `TvHub.kt:245`, sans la modifier) ; `MyTvActivity`, `TvPairScreen`, `BtRoutesScreen` pointent vers cet écran au lieu de leurs dialogues.
4. ETA Bluetooth : `TvReachability.eta(bytes, bytesPerSecond)` → « ≈ 1 h 05 (Bluetooth) » affiché dans `TvHome` et `UploadService` (notification) ; débit mesuré s'il existe (`LinkPool` diagnostic), sinon 200 ko/s par défaut.
5. Tests JVM pour la table et `eta`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.tv.TvReachabilityTest'   # vert
grep -n 'e.message\|\${e}' android/sender/src/main/kotlin/castbridge/sender/TvHome.kt android/sender/src/main/kotlin/castbridge/sender/UploadService.kt   # 0 hit affiché à l'utilisateur (journal OK)
cd android && gradle --offline :sender:compileDebugKotlin   # compile (si SDK)
```
Observable (S21+) : TV éteinte → « TV éteinte ou CastBridge-TV fermé. Allumez la TV et ouvrez CastBridge-TV. » ; téléphone sur les données mobiles → « Pas sur le même Wi-Fi… » ; écran Dépannage liste les 4 contrôles.

## Cas limites
- Passerelle Bluetooth active (pas de Wi-Fi) : `sameSubnet = null` ne doit pas déclencher « Pas sur le même Wi-Fi ».
- Dual App Samsung (Bluetooth refusé) : garder le message existant de `TvPairScreen.kt:181-192`.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret dans le rapport copié (pas de PIN, pas de jeton) ; textes en français ; « CastBridge » / « CastBridge-TV ».

## Rapport
`STATUT`, table de décision, écrans modifiés, sorties des commandes.
