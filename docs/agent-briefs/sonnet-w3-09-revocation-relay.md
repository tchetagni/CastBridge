# w3-09 — Le téléphone relaie la liste de révocation du serveur vers la TV ; compteur d'essais par appareil

**Vague 3 · Effort M (≈ 1,5 j) · Statut PRÊT** (après w2-01 : la TV sait appliquer un `cbr1`). Branche `claude/sonnet-w3-09`. Rapport : `docs/agent-reports/sonnet-w3-09.md`.

## Objectif
1. CastBridge (téléphone), quand il est en ligne, récupère `GET /api/v1/revocations` (liste `cbr1` signée, module licences) au plus une fois par jour et la **pousse** à chaque TV appairée à la prochaine connexion (Bluetooth ou Wi-Fi), via `POST /api/activation/install` (corps `cbr1.…`, PIN/jeton selon w2-01).
2. Le serveur compte les émissions d'**essai** par code d'appareil (alerte `TRIAL_REPEAT` dans `AbuseService`, jamais de blocage) pour repérer « effacer les données puis redemander ».
3. Tests cœur (relais) et serveur (compteur).

## Pourquoi (preuves)
- `docs/ACTIVATION-FORMAT.md:184` et `docs/TRIAL-EDITION.md:220` : « liste de révocation côté TV : pas encore » ; `B/licenses/PublicLicenseController.java:31-41` sert déjà `cbr1` (module éteint par défaut) ; la TV est hors ligne : seul le téléphone peut la lui apporter.
- `B/licenses/AbuseService.java:39-101` : alertes DUPLICATE_DEVICE, MULTI_SOURCE, OVER_QUOTA, ISSUANCE_BURST… mais pas de compteur d'essais répétés par code (audit T4, MO-5).
- Audit : A5 (backend), MO-5 ; dépend de w2-01 (SE-1).

## Fichiers possédés
Nouveau `S/RevocationRelay.kt`, `C/connect/ServerLink.kt` (**ajout** d'une méthode `fetchRevocations()`), nouveau `C/connect/RevocationCache.kt` (pur : fraîcheur, idempotence, persistance via `SafeFile`), `B/licenses/PublicLicenseController.java` (**aucun** changement de contrat ; seulement `Cache-Control`/`ETag` si absents), `B/licenses/AbuseService.java`, `backend/src/test/java/castbridge/server/licenses/**`, `android/core/src/test/kotlin/castbridge/core/connect/**`. **Hors zone** : `ActivationCenter` (w2-01), `TvLinkManager`, `LotsRuntime` (utiliser leurs API publiques pour « à la prochaine connexion »).

## Étapes
1. `RevocationCache` (cœur) : garde le dernier `cbr1` (texte + `generatedAt`), `shouldFetch(now)` (24 h), `shouldPush(tvId, now)` (une fois par liste et par TV, mémorisé), persistance JSON.
2. `ServerLink.fetchRevocations()` : `GET /api/v1/revocations` avec le jeton d'appareil existant ; 404 (module éteint) ⇒ rien, sans erreur visible.
3. `RevocationRelay` (sender) : déclenché par la tâche périodique existante de `LotsRuntime` (JobScheduler) et à chaque connexion TV réussie ; pousse via `TvClient`/transport Bluetooth déjà utilisés pour l'activation (`ActivationSend`) ; journal sans secret.
4. Serveur : `AbuseService` : à chaque émission `kind=TRIAL`, compter par `device_code` sur 90 j ; ≥ 2 ⇒ alerte `TRIAL_REPEAT` (visible dans la page d'alertes) ; test.
5. Tests cœur : fraîcheur, idempotence par TV, 404 silencieux ; test serveur : 2 essais ⇒ alerte.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.connect.*'   # vert
cd backend && ./mvnw -q -o test -Dtest='Abuse*,PublicLicense*'                  # vert
cd android && gradle --offline :sender:compileDebugKotlin                       # compile (si SDK)
```
Observable (préprod, plus tard) : révoquer un poste dans `/admin/licenses` → `GET /api/v1/revocations` le contient → le téléphone (en ligne) puis la TV (`GET /api/activation` → `keyInstalled:false`) dans les 24 h + une connexion.

## Cas limites
- Téléphone jamais en ligne : rien ne se passe (limite documentée, RENTAL-LOTS § 11).
- Liste plus ancienne que celle que la TV a : la TV l'ignore (w2-01) ; le relais ne doit pas boucler.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas activer le module licences ; textes en français.

## Rapport
`STATUT`, fréquence et déclencheurs, tests, limites.
