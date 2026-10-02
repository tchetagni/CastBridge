# w12-06 — CastBridge (téléphone) : `SettingsRuntime` (tirage serveur toutes les 6 h, cache, application, relais vers la TV par Wi-Fi local, adoption du document de la TV, import fichier / collage / QR), ligne « Réglages » dans À propos
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (le téléphone ne décide rien : il vérifie et transporte) · statut : PRÊT
> **Groupe : W12-c** (vague W12) · prérequis : w12-01 fusionné ; w12-03 pour le test de bout en bout (sinon serveur simulé) ; `S/PhoneConnect.kt` et `S/ConnectScreens.kt` **après** w11-07/w11-09 s'ils sont lancés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.settings.*' && cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : échantillon

**Vague 12c (téléphone) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 2.5, § 2.7, § 5.4, § 7. Branche `claude/sonnet-w12-06`. Rapport : `docs/agent-reports/sonnet-w12-06.md`.

## Objectif
(1) `SettingsRuntime` (objet, même mécanique qu'`OrdersRuntime`) : au démarrage charge `files/settings/current.txt` dans un `SettingsEngine` ; **tirage** `GET /api/v1/settings/current` (`If-None-Match`) dans la tâche de fond existante toutes les 6 h et à la demande ; `Settings.bind` + `Settings.bindSubject` (licence de la TV liée si connue, sinon sel local) ; (2) **relais** : à chaque liaison avec une TV (Wi-Fi local, après `/api/hello`), `GET /api/settings` ; si `seq` TV < téléphone ⇒ `POST /api/settings/install` (corps = jeton, PIN / jeton de confiance comme `/api/activation/install`) ; si TV > téléphone ⇒ **adopter** le jeton de la TV (vérifié par le moteur) ; (3) **import** : fichier (sélecteur, `settings` ou `.cbx1`), collage de texte, QR **si** un lecteur existe déjà dans l'app (sinon « BLOQUÉ : collage et fichier seulement », noté au rapport) ; export vers la clé USB du téléphone (`Download/CastBridge/settings`) ; (4) consommateurs tranche 1 : `langues.phoneQuotaMb` (là où `LangBudget` serait lu : aujourd'hui **aucun** code de production ne le lit, `C/langues/LangBudget.kt:75` : brancher le point de lecture unique sans changer de comportement), `telemetry.flushMin` (cadence du `TelemetryUploader` du téléphone), `settings.message` (affiché une fois, carte fermable) ; (5) ligne « Réglages : … » (`Settings.version()`) dans `AboutSection`.

## Pourquoi (preuves)
- `OrdersRuntime` (`S/OrdersRuntime.kt:25,38` : file `orders/queue.json`, tâches `OrdersSyncJob`/`OrdersDeliverJob`, init `S/PhoneConnect.kt:44`) ; transport serveur avec jeton d'appareil `C/policy/OrderTransport.kt:13-33`.
- Livraison d'une activation à la TV : `C/owner/ActivationSend.kt:36`, routes `POST /api/activation/install` (`R/RentalHub.kt:71-79`), en-têtes `trust/Credentials.kt`.
- Écran « À propos » : `S/ConnectScreens.kt:142-148` ; diagnostic exportable `C/remote/RemoteRoutes.kt:61-75` (ajouter `Experiments.summaryLine` **si** w12-02 est fusionné, sinon la seule ligne de version).
- Le téléphone **n'a pas** `TRUSTED_KEYS` injecté (`android/sender/build.gradle.kts:21-22` : seulement `EXTRA_UPDATE_KEY`, `DEFAULT_SERVER`) : l'anneau du téléphone pour vérifier les réglages est **`BuildConfig.TRUSTED_KEYS` à ajouter au `sender`** comme sur la TV (`receiver/build.gradle.kts:27-39`, même fichier `activation-trusted-keys.txt`, **absence tolérée** : sans clés, le téléphone ne vérifie rien et **n'applique rien** — jamais de clé en dur dans le code).

## Fichiers possédés
- Nouveaux : `S/settings/SettingsRuntime.kt`, `S/settings/SettingsSyncJob.kt`, `S/settings/SettingsRelay.kt`, `S/settings/SettingsImport.kt`, `S/settings/SettingsMessageCard.kt`, `C/settings/SettingsTransport.kt` (client HTTP pur : `fetchCurrent(base, token, etag)`), `CT/settings/SettingsTransportTest.kt`.
- Existants (zones précises) : `S/PhoneConnect.kt` (une ligne d'init), `S/ConnectScreens.kt` (`AboutSection` : une ligne), `S/TvLink.kt` ou l'endroit où `/api/hello` est appelé (**un** crochet `SettingsRelay.onLinked(tv)` ; nommer la ligne exacte au rapport), `android/sender/build.gradle.kts` (`TRUSTED_KEYS`, copie du bloc TV, tolérant à l'absence du fichier), `S/…TelemetryUploader` côté téléphone (cadence lue via `Settings.int`).
- Hors zone : `S/MainActivity.kt`, `S/shop/**`, `S/focal/**` (W5/W4-C), la TV, `OrdersRuntime.kt` (lecture seule : si une tâche commune est préférable, **réutiliser** `OrdersSyncJob` par un appel additif plutôt que la modifier : sinon tâche propre).

## Étapes
1. `SettingsTransport` (cœur, testé avec un serveur HTTP local) : 200 / 304 / 404 / réseau absent ⇒ `null` sans exception.
2. `SettingsRuntime.init(ctx, keyRing)` ; `keyRing` depuis `BuildConfig.TRUSTED_KEYS` (`TrustedKeyParser`) ; sans clé : journal « réglages non vérifiables : clés absentes » et **rien appliqué**.
3. Tâche de fond 6 h (`JobScheduler`, batterie non faible) ; tirage à l'ouverture de l'app si > 6 h.
4. Relais : crochet à la liaison ; `GET /api/settings` → `{seq, kid, issuedAt, expiresAt, schema, cohorts, stale}` (contrat w12-07) ; `POST /api/settings/install` ; adoption ; journal.
5. Import / export ; message ; ligne À propos ; consommateurs tranche 1.
6. Test manuel documenté (émulateur + serveur de test) : tirage, relais vers une TV émulée, adoption.

## Critères d'acceptation (hors ligne)
- Porte verte ; `SettingsTransportTest` ≥ 6 cas ; compilation `sender` ; sans fichier de clés, l'app démarre et la ligne À propos dit « Réglages : défauts (clés absentes) ».
- Rapport : ligne exacte du crochet de liaison, existence ou non d'un lecteur QR, et ce que w12-07 doit exposer.
