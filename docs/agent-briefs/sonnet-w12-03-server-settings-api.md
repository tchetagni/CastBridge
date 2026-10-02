# w12-03 — Serveur : module `settings` (miroir Java du document, signature par la clé `POLICY`, séquence sous verrou, migration additive, API publique et d'administration, audit chaîné)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (signature, séquence, argent) · statut : PRÊT
> **Groupe : W12-b** (vague W12) · prérequis : w12-01 fusionné (vecteurs `settings-vectors.json`, `schema.json`) · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Settings*Test'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : **oui**

**Vague 12b (serveur) · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 2.2-2.5, § 4.2-4.4, § 9 (D-W12-1). Branche `claude/sonnet-w12-03`. Rapport : `docs/agent-reports/sonnet-w12-03.md`. **Jamais le serveur de production** (`bridge.sti-cm.com`) ni un secret : tests hermétiques avec une clé de test.

## Objectif
Module additif `B/settings/**` (`castbridge.settings.enabled=false` par défaut, env `CASTBRIDGE_SETTINGS_ENABLED`) : (1) `SettingsSchema` Java **égal octet pour octet** à `tools/settings/schema.json` (test de parité) ; (2) `SettingsEnvelope` : construction et vérification du type `settings` avec `EnvelopeIssuer`/`EnvelopeVerifier` existants, **mêmes octets** que Kotlin (vecteurs) ; (3) `SettingsService` : brouillon, validation des bornes (erreur par clé), diff, **publication** (TOTP) = `seq` suivant sous verrou `settings_key_seq … for update`, signature par `OrderSigner` (clé `POLICY`), insertion immuable `settings_doc`, audit chaîné ; retour arrière (`basedOn`) ; interrupteur (`reset=all`) ; (4) routes § 4.2 ; (5) migration `V<plus haut + 1>__settings.sql` ; (6) `settings_device` alimentée par l'événement essentiel `settings_applied` (crochet additif dans `TelemetryService`, une ligne).

## Pourquoi (preuves)
- La clé serveur porte `POLICY` et jamais `TRANSFER`/`OPEN_ALL`/`SUPER` : `B/licenses/ScopedActivationSigner.java:14-18`, vérifié `:44-52`. `OrderSigner` (`B/orders/OrderSigner.java`, `ConfiguredOrderSigner.java:19-35`, `CASTBRIDGE_ORDERS_KEY_FILE`) est le signataire à réutiliser (même `kid` que les ordres : **compteur distinct**, `settings_key_seq`).
- Séquence sous verrou et audit chaîné : `OrderService.java:29,46,94,123,148`, `V60__deferred_orders.sql:6` (`order_key_seq`), `:72` (`order_audit` avec `prev_hash`) ; `GET /api/v1/admin/orders/audit/verify` (`AdminOrderController.java:48`).
- Enveloppes côté serveur : `B/licenses/EnvelopeIssuer.java:17-19,94-96`, `EnvelopeVerifier.java` ; parité par vecteurs : `BT/licenses/{EnvelopeVectorsTest,WireFormatVectorsTest}` (`ACTIVATION-FORMAT.md` § 11.1).
- **Numéro de migration** : le plus haut est **V61** (`backend/src/main/resources/db/migration/`, README `:3-4` : « plus haut + 1 ») ; V62 est convoité par w1-11 (`sonnet-w1-11-backend-hygiene.md:29`), w2-10/W5 (`DESIGN-W5:79`), W4-C (`DESIGN-W4-VENTE-TERRAIN.md:112`), W10. **Au lancement** : `git log --all -- backend/src/main/resources/db/migration | head` et prendre le premier numéro libre ; noter le numéro au rapport.
- TOTP existant : `B/licenses/Totp.java`, `TotpVault.java`, `admin_user.totp_*` (`V52__…:90-93`).
- Jeton d'appareil pour les routes publiques : `B/devices/DeviceController.java:24-64` ; patron de relais à ETag : `B/lots/BundleCatalogController.java:29-44`.

## Fichiers possédés
- Nouveaux : `B/settings/{SettingsProperties,SettingsSchema,SettingsEnvelope,SettingsService,SettingsController,AdminSettingsController,SettingsAudit,SettingsDeviceSink}.java`, `BT/settings/{SettingsSchemaParityTest,SettingsVectorsTest,SettingsServiceTest,SettingsApiTest,SettingsAuditTest}.java`, `backend/src/main/resources/db/migration/V<n>__settings.sql`.
- Existants (zones précises) : `backend/src/main/resources/application.yml` (bloc `castbridge.settings`), `backend/README.md` (§ « Réglages signés » : variables, procédure préproduction), `B/telemetry/TelemetryService.java` (**une ligne** : appel de `SettingsDeviceSink` sur `settings_applied` ; l'acceptation de l'événement lui-même est **w12-10**), `backend/.env.example` (2 lignes commentées).
- Hors zone : pages HTML (**w12-04**), `EventCatalog.java` (**w12-10**), `B/orders/**` (lecture seule : réutiliser `OrderSigner`), toute grille de prix (W4-C).

## Contrat d'API (à respecter par w12-04, w12-06, w12-08)
- `GET /api/v1/settings/current` (Bearer jeton d'appareil, 404 si module éteint ou rien publié, `ETag: "<seq>"`, `If-None-Match` → 304) → `{"seq":n,"kid":"…","token":"cbx1…","issuedAt":ms,"expiresAt":ms}`.
- `GET /api/v1/settings/{seq}` → même forme, immuable.
- Admin (jeton d'administration, rôle `OWNER` ; TOTP sur `publish` et `kill`) : `GET …/schema`, `GET/PUT …/draft` (400 `{"errors":[{"key","message"}]}`), `GET …/draft/diff` (`{"added":[],"changed":[{"key","from","to"}],"removed":[],"money":n,"security":n}`), `POST …/publish {"summary","validDays":90,"totp"}` → `{"seq"}`, `GET …/history`, `POST …/history/{seq}/restore`, `POST …/kill {"totp"}`, `GET …/audit/verify` → `{"ok":true}` ou `{"ok":false,"firstBad":id}`, `GET/POST …/experiments` (registre complet § 3.1, états `DRAFT/RUNNING/CLOSED`, `POST …/experiments/{name}/close {"decision","chosenArm"}` → brouillon pré-rempli).
- **Refus** : clé hors schéma (400), hors bornes (400 par clé), préfixe `NEVER` (400), `validDays` hors 1 h–366 j (400), publication sans TOTP (403), module éteint (404), deux publications simultanées ⇒ seq distincts (test de concurrence comme `OrderService`).

## Étapes
1. Migration additive (tables § 4.3 sauf `kpi_experiment_day`) ; `README.md` des migrations : une ligne.
2. `SettingsSchema` Java chargé **depuis** `tools/settings/schema.json` copié en ressource (`src/main/resources/settings/schema.json`) ; test de parité : la ressource est identique au fichier du dépôt.
3. `SettingsEnvelope` : `issue`/`verify` ; rejouer `settings-vectors.json` (`build-settings` octets identiques ; `settings` acceptations/refus).
4. `SettingsService` : brouillon (1 ligne), validation, diff, publication sous verrou, `basedOn`, `reset=all`, historique, audit chaîné (copier `SettingsAudit` sur `order_audit`), registre d'expériences.
5. Contrôleurs ; interrupteur ; ETag ; tests d'API (MockMvc) : 404 éteint, 304, TOTP, concurrence, retour arrière crée seq + 1, kill.
6. `SettingsDeviceSink` + crochet dans `TelemetryService` (ignore tout si le module est éteint).
7. `application.yml`, `README.md`, `.env.example`.

## Critères d'acceptation (hors ligne)
- Porte verte ; vecteurs rejoués ; parité schéma ; audit : une ligne altérée est détectée ; **aucun secret** dans le dépôt ni les journaux ; `git log --all` consulté pour le numéro (noté au rapport).
- Rapport : numéro de migration pris, routes, ce que l'audit Opus doit relire (verrou de séquence, signature, TOTP).
