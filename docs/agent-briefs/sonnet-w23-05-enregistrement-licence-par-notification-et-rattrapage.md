# w23-05 — Serveur : une activation de production notifiée et vérifiée CRÉE ou RATTACHE la licence et le poste ; identité de portefeuille ouverte à la notification ; rattrapage borné ; correctif `end_at`
<!-- routage architecte 2026-10-04 (W23-B) -->
> **Modèle : sonnet (4.6)** · escalade : audit Opus **obligatoire** (écritures `lic_*`, création de valeur, verrous) · statut : **ATTEND w23-01 corrigé (C1, H1, migration « plus haut + 1 ») et fusionné**
> **Groupe : W23-B** (ordre 1) · porte : `cd backend && mvn -o test` **et** les tests MySQL 8.4 Testcontainers de ce cahier sur une machine avec Docker (**bloquant** avant l'audit)
> **Jauge : ≈ 650 k jetons entrée / 32 k sortie** (effort L, ≈ 2,5 j)

**Conception** : `docs/coordination/DESIGN-W23B-NOTIFICATION-ACTIVATION-LICENCE-PORTEFEUILLE-2026-10-04.md` § 3, § 4, § 5, § 7.3 (w23-05a), § 10. Branche `claude/w23-05-enregistrement`. Rapport : `docs/agent-reports/sonnet-w23-05.md`.

## Objectif
Qu'une activation de production émise hors ligne, vérifiée au serveur (avis de TV, synchronisation du portefeuille, ou décision du propriétaire), apparaisse comme **licence + poste** lisibles par `LicenseFacts`, sans double création avec le registre, et que le portefeuille rattrape les tranches dues **une fois par (licence, période)**, dans les limites du § 5.

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/licenses/ReportedActivationRegistrar.java` (seul écrivain `lic_*` de ce cahier ; interface publique `register(VerifiedActivation, NoticeFacts, Via) → Registration`), `.../licenses/RegistrationDecisionController.java` (API `GET/POST /api/v1/admin/licenses/registrations[/{fp}/decision]`, compte nommé + TOTP + motif), `.../wallet/CatchUpPolicy.java` (limite de rattrapage, retenues), `.../wallet/NotifiedIdentity.java` (ouverture d'identité sans appareil API, `install_pub`), migration `V<plus haut + 1>__activation_registration.sql` (`lic_registration`, `wallet_catchup_hold`, `wallet_identity.api_device_id` rendu NULL), tests `backend/src/test/java/castbridge/server/licenses/Registrar*Test.java`, `.../wallet/CatchUp*Test.java`, `RegistrationMySqlTest.java`.
- **Zone additive** : `LicenseService.createAuto` (reçoit la durée de la clé ⇒ `end_at`), `LedgerService.applyLicense` (amendement des postes d'une licence `created_by=report:`), `JdbcLicenseFacts` (statut d'enregistrement par code), `GrantService` (appel de `CatchUpPolicy`, retenues), `WalletSyncController` (**w23-05a** : appel du registrar quand une production vérifiée n'a pas de licence), `A/ActivationObserver` (appel du registrar après vérification, jamais d'écriture `lic_*` dans `activations`), `WalletReason` côté serveur (motifs § 5.4).
- **Interdit** : Android, formats `cbx1` existants, `application.yml` hors blocs additifs, toute route publique nouvelle (la route d'avis est w23-06).

## Spécification
Conditions et ordre exacts du § 3.2 ; création du § 3.3 (client technique « Client anonyme (activation rapportée) », `seats=1`, `start_at`/`end_at` du droit `usage`, `transfer_cap=0`) ; rattachement du § 3.4 ; réconciliation du § 3.6 ; essais § 3.8 ; identité § 4 ; rattrapage § 5.2-5.3 ; plafond par clé D-W23B-7 dans la table de politique ; audit `lic_audit` + évènements W23.

## Critères d'acceptation (mutations au rapport)
- `RegistrarRulesTest` : chaque ligne des tableaux § 3.2 et § 3.4 a son test (statut ET motif) ; mutation « portée ignorée » ⇒ un `REACTIVATE` crée une licence ⇒ échec.
- `RegistrarOrderTest` (propriété, 200 permutations) : avis, registre, journal, émission serveur dans tous les ordres ⇒ même état `lic_*` ; aucune double licence ni double poste.
- `EndAtTest` : clé de 90 jours ⇒ `end_at = from + 90 j` (auto, avis, import corrigé) ; illimitée ⇒ NULL ; le portefeuille paie 3 tranches de 1 000 + 10, jamais l'ouverture illimitée (mutation : `end_at` NULL ⇒ 5 000 versés ⇒ échec).
- `CatchUpTest` : avis 200 jours après l'émission, non déclaré ⇒ 3 périodes versées, les autres retenues ; déclaration ⇒ libérées une fois ; 400 jours ⇒ jamais au-delà de 366 ; suspension ⇒ aucune tranche de l'intervalle ; deux notifications concurrentes ⇒ une seule pose par période.
- `NotifiedIdentityTest` : identité ouverte par avis (`api_device_id` NULL) ; première synchronisation directe avec preuve par la MÊME clé ⇒ liée ; autre clé ⇒ `BIND_MISMATCH`, aucun versement.
- `RegistrationMySqlTest` (MySQL 8.4) : 16 fils, même licence, 1 poste ⇒ 1 seul poste, les autres `OVER_QUOTA` ; dates `DATETIME` lues sans `ClassCastException` (leçon C1 de w23-01).
- Aucun jeton ni code complet dans les journaux et les tables (recherche de motifs après la suite).

## Préproduction = production
Aucun drapeau de test (pas de `require-bind-proof=false`, pas de clé de test hors des tests).
