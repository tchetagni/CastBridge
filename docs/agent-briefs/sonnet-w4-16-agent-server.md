# w4-16 — Serveur : synchronisation des journaux d'agents, réconciliation, tableau de bord « Points focaux », grille de prix, révocation d'un agent

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT **amendé W5** (colonnes may_*, exposer verifyDelegation/verifyEntry)
> **Groupe : W4c-3** (vague W4c) · prérequis : w4-11, w4-17 · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Agent*Test,LicenseKeyringTest'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui

**Vague 4c · Effort L (≈ 4 j) · Statut PRÊT (après w4-11 et w4-17 : le serveur vérifie les délégations avec `Delegation.java`).** Conception : `docs/coordination/DESIGN-W4-VENTE-TERRAIN.md` § 4, § 6, § 8, § 9. Branche `claude/sonnet-w4-16`. Rapport : `docs/agent-reports/sonnet-w4-16.md`. **Rien n'est déployé.**

## Objectif
Le serveur reçoit les journaux signés des agents (`POST /api/v1/agent/sync`), vérifie délégation, signatures et chaîne, stocke, fusionne les événements de registre signés par les agents (anneau avec délégations), calcule les **anomalies** et le **solde à remettre**, et donne au propriétaire une console `/admin/agents` (totaux, versements avec TOTP, anomalies, révocation d'un agent via la liste `cbr1`, import d'une délégation). Il relaie la grille de prix signée (`GET /api/v1/catalog/prices`).

## Pourquoi (preuves)
- `B/licenses/PublicLicenseController.java:31-41` (routes publiques, `DeviceService.authenticate`), `B/devices/DeviceService.java:152` ; `B/lots/BundleCatalogController.java:28-38` (relais d'un fichier signé déposé) ; `B/licenses/RevocationService.java` (`signedList`, révocation de clés : `LicenseApiController.java:155` `/keys/revoke`) ; `B/licenses/RegistryStore.java`/`LedgerService.java` (import du registre) ; `B/licenses/LicenseKeyring.java`/`TrustedKeys.java` (anneau) ; `Role.java` (`Permission`) ; `templates/admin/lic-nav.html:5-13` ; migrations : dernière `V61__tunnel.sql` (règle « plus haut + 1 » ; w1-11 a peut-être pris V62).
- Audit MO-1, MO-5 (anti-partage comptable), A4-1.

## Fichiers possédés
Nouveau paquet `B/agents/**` (`AgentProperties` (`castbridge.agents.enabled=false` par défaut, `prices-file`), `AgentService`, `AgentLedgerVerifier`, `AgentAnomalies`, `AgentSyncController` (`/api/v1/agent/**`), `PriceGridController` (`/api/v1/catalog/prices`), `AdminAgentController` (`/admin/agents/**`), entités/`JdbcTemplate`), migration `backend/src/main/resources/db/migration/V6x__agents.sql` (numéro = plus haut + 1), `backend/src/main/resources/templates/admin/agents*.html`, `templates/admin/lic-nav.html` (un lien), `application.yml` (bloc `castbridge.agents`), `B/licenses/LicenseKeyring.java` (**méthode additive** `withDelegations(...)`), `B/licenses/RegistryStore.java` (point d'entrée additif `importSigned(events, ring)` si l'existant exige l'anneau par défaut), `BT/agents/**`, `BT/licenses/LicenseKeyringTest.java`. **Hors zone** : `ActivationService.java`, `LicenseService.java`, `RevocationService.java` (appeler l'existant ; si une méthode manque, la demander dans le rapport), `B/licenses/Delegation.java` (w4-17), `B/tunnel/**`, `B/orders/**`, Android, docs (w4-18).

## Étapes
1. Migration : `agent_delegation`, `agent_ledger`, `agent_remittance`, `agent_anomaly` (colonnes de la conception § 8), index `(agent_kid, seq)`, `(receipt)`, `(device_code)`.
2. `POST /api/v1/agent/sync` (auth : jeton d'appareil ; limite de débit existante ; corps ≤ 1 Mo) : `Delegation.verify` (w4-17) contre `TrustedKeys` (clés du propriétaire) ⇒ upsert `agent_delegation` (plus récente par `seq`) ; chaque entrée : signature (pub de la délégation), `hash`, chaîne par rapport à la dernière stockée (trou ⇒ anomalie `CHAIN_GAP`, stockée quand même ; divergence sur un `seq` déjà stocké ⇒ anomalie `CHAIN_FORK`, entrée **refusée**) ; événements de registre : `LicenseKeyring.withDelegations` + import existant ; réponse `{cursor: dernier seq stocké, revoked, expiresAt, anomalies: [ouvertes]}`. Idempotent.
3. `AgentAnomalies.recompute(agent)` après chaque synchronisation : liste du § 8 ; `agent_anomaly` avec `resolved_*`.
4. `GET /api/v1/catalog/prices` : relais du fichier `prices-file` (cache mémoire 60 s, `ETag`, 404 si absent), comme `BundleCatalogController`.
5. `/admin/agents` : liste (nom, kid court, validité, ventes, encaissé, remis, **à remettre**, anomalies ouvertes, révoqué ?) ; `/admin/agents/{kid}` : ventes (filtre mois, recherche par code de reçu ou code d'appareil), export CSV, « Enregistrer un versement » (TOTP, montant, date, note), « Clore l'anomalie », « Révoquer » (TOTP ⇒ `RevocationService` ajoute `key=<kid>` à la liste signée ; `revoked_at`), « Importer une délégation » (colle le jeton ; vérifié). Rôle : `OWNER` écrit ; `SUPPORT`/`READONLY` lisent.
6. Tests MockMvc/H2 : synchronisation valide ; signature fausse ⇒ 422 ; trou ⇒ anomalie ; fork ⇒ refus ; prix hors grille ⇒ anomalie ; ventes > quota ⇒ anomalie ; vente après expiration ⇒ anomalie ; révocation ⇒ `revoked: true` à la synchronisation suivante et `key=` dans `GET /api/v1/revocations` ; versement ⇒ solde ; import de registre d'agent ⇒ licence `lic-…` visible dans `/admin/licenses/list`.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='AgentSyncTest,AgentAdminTest,AgentAnomaliesTest,PriceGridControllerTest,LicenseKeyringTest'   # vert
cd backend && ./mvnw -q -o test   # suite complète verte
grep -n 'enabled: false' backend/src/main/resources/application.yml | grep -i -B2 agents   # module éteint par défaut
ls backend/src/main/resources/db/migration/ | tail -2   # V6x__agents.sql = plus haut + 1
```

## Cas limites
- Délégation inconnue à la première synchronisation : apprise si signée par une clé du propriétaire connue ; sinon 403 « délégation inconnue ou invalide ».
- Deux téléphones, même agent : `CHAIN_FORK` ⇒ anomalie + agent marqué « à vérifier » (pas de révocation automatique).
- Fichier de grille absent : `/api/v1/catalog/prices` 404 ; les anomalies de prix ne sont pas calculées (note).

## À ne pas faire
Pas de déploiement ; pas de commit sur les branches partagées ; aucun secret, montant ou numéro réel dans le code et les tests (`TEST-…`) ; ne jamais donner `DELEGATE` à la clé serveur ; ne pas émettre d'activation ici ; ne pas modifier les vecteurs.

## Rapport
`STATUT`, numéro de migration pris, endpoints et JSON exacts (pour w4-14 et w4-18), méthodes manquantes constatées dans `RevocationService`/`RegistryStore`.
