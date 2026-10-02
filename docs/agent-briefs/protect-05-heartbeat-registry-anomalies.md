# protect-05 — Empreinte de signature + drapeaux au battement de cœur, registre & anomalies serveur

**Modèle recommandé : sonnet** (Kotlin + Java backend + tests ; coordination w3-09).
**Vague C.** Dépend de protect-01 (watermark), protect-03 (`SelfIntegrity`), protect-04 (`EnvTrust`) pour les valeurs à rapporter — peut démarrer en parallèle en stubant ces lectures si besoin. **Coordination w3-09** (relais de révocation) : champs **distincts**, ne pas empiéter.

## But
La TV rapporte l'empreinte SHA-256 de son certificat de signature, sa classe d'appareil et le niveau de confiance d'environnement. Le serveur tient un **registre des empreintes connues** (hash inconnu = signalé) et des **anomalies** (même poste de licence sur deux appareils, activations impossibles). Transforme une fuite/partage en événement **traçable**.

## Fichiers possédés
- `backend/src/main/java/castbridge/server/devices/DeviceReport.java` (ajout de champs)
- `backend/src/main/java/castbridge/server/devices/DeviceService.java` (persistance + détection hash inconnu)
- `backend/src/main/java/castbridge/server/licenses/AbuseService.java` (étendre `alerts()`/`sighting(...)` pour hash inconnu et poste dupliqué)

## Point chaud partagé (édition minimale, seul ce cahier le touche)
- `android/core/src/main/kotlin/castbridge/core/device/DeviceReport.kt` : ajouter les champs `sigSha256: String?`, `deviceClass: String?`, `envTrust: String?`, `envReasons: List<String>?`, `watermark: String?` à la data class, au `toJson()` et à `collect(...)` (lire depuis `DeviceFacts` étendu ou via paramètres). Ne pas retirer de champ existant (compat serveur).

## Étapes
1. Côté app (`DeviceReport.kt`) : nouveaux champs optionnels (null si inconnu), sérialisés dans `toJson()`. La collecte réelle des valeurs (SelfIntegrity/EnvTrust/BuildConfig.BUILD_WATERMARK) est câblée dans le receiver par le code appelant existant de `TvConnect`/heartbeat — **édition minimale** : exposer les champs, laisser le receiver les remplir.
2. Côté serveur : `DeviceReport.java` reçoit les mêmes champs. `DeviceService` : stocker `sigSha256` par appareil ; comparer à une **liste d'empreintes attendues** (configurable, `LicenseProperties` ou table) ; si inconnue → `AbuseService.sighting("unknown-sig", ...)`.
3. `AbuseService` : nouvelles alertes `UNKNOWN_SIGNATURE` et `SEAT_ON_TWO_DEVICES` (un poste `licence|seat` vu avec deux `installId`/empreintes matérielles différentes), `ENV_HOOKED` (drapeau remonté). Réutiliser le mécanisme `Alert`/`dashboard()` existant.
4. Tests backend : étendre `DevicesApiTest`/un test AbuseService pour les nouveaux cas (hash inconnu → alerte ; poste sur 2 appareils → alerte).

## Commandes d'acceptation
- `cd backend && ./mvnw -q test` (ou `mvn test`) vert sur les tests touchés.
- `grep -n "sigSha256" backend/src/main/java/castbridge/server/devices/DeviceReport.java android/core/src/main/kotlin/castbridge/core/device/DeviceReport.kt` → présent des deux côtés.
- Revue : aucun champ existant retiré ; JSON rétro-compatible (champs null tolérés).

## Cas limites / à préserver
- Anciennes TV qui n'envoient pas les nouveaux champs : serveur tolère null (pas d'alerte à tort).
- Ne pas confondre avec w3-09 : ici ce sont des champs d'anti-abus, pas la liste de révocation.
- Consentement/vie privée : ne rapporter que des empreintes/drapeaux techniques, pas de contenu personnel (cf. LE-3, SE-13 ; pas de `deviceName` en clair supplémentaire).

## Ne PAS faire
- Ne pas implémenter la distribution de révocation (w2-01/w3-09). Ne pas committer.

## Format de rapport
Diffs par fichier, sortie des tests, liste des nouvelles alertes, confirmation de compat JSON.
