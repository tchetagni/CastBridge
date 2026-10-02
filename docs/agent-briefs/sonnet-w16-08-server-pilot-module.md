# w16-08 — Serveur : module `castbridge.pilot` (éteint par défaut) : commandes à prix 0, contrats à clé aléatoire sous KEK, activation signée par la clé serveur, lots scellés, relevés d'usage, réémission, quotas

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (signature, KEK, scellement) · statut : PRÊT (après w16-04, w16-06 ; w4-05 fusionné pour X25519 Java)
> **Groupe : W16c-1** (vague W16c, serveur, **autorisé pendant le gel**) · prérequis : w16-04 (règles), w16-06 (vecteurs), w4-05 (XDH Java, vérifié dans `B/licenses`) · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Pilot*Test'`
> **Jauge : ≈ 700 k jetons entrée / 35 k sortie** (effort L, ≈ 3 j) · audit Opus : **oui**

**Vague 16c · Effort L · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W16 § 3.3 (lire en entier), § 1.4, DESIGN-W5 § 4.2-4.4 (clé aléatoire sous KEK, cache de lots). Branche `claude/sonnet-w16-08`. Rapport : `docs/agent-reports/sonnet-w16-08.md`. **Rien n'est déployé** ; le module licences reste éteint en production (`application.yml:112-114`) ; ce module sert au pilote **payant** et à la préproduction.

## Objectif
Un téléphone authentifié (jeton d'appareil) demande « louer `classe-cm2` pour 12 heures d'utilisation » : le serveur vérifie la demande d'appareil et la preuve d'activation, applique `PilotRules` (miroir Java, mêmes vecteurs), crée une commande à **0 XAF**, un contrat à **clé aléatoire** chiffrée sous la **KEK fichier**, signe une **activation de location** (clé serveur, `ISSUE_PRODUCTION`, boîte v2 vers `installPub`), scelle les lots du bouquet, et rend tout au téléphone ; il reçoit les **relevés d'usage** (maximum monotone) et réémet le **reste** après réinstallation (≤ 3).

## Pourquoi (preuves)
- `B/licenses/{EnvelopeIssuer,EnvelopeVerifier,ScopedActivationSigner,WireActivation,DeviceIdentity}.java` : émission et vérification `cbx1` ; `WireActivation.rentalBounds:44`.
- `B/lots/{LotService,LotRepository}.java` : lots publiés (octets du zip) à sceller ; `B/lots/BundleCatalogController.java` : catalogue des bouquets.
- `DESIGN-W5:173-179` (clé aléatoire sous KEK, réémission ≤ 3), `:181-186` (cache scellé, déterminisme, `Range`/`ETag`).
- Migrations : `backend/src/main/resources/db/migration/` (**vérifier `git log --all -- …`** : numéro libre, V62 convoité).
- **Non vérifié** : clé publique serveur dans `TRUSTED_KEYS` des TV (sans elle la TV refuse `UNKNOWN_KEY`, `C/owner/Activation.kt:143`) : critère de préproduction, pas de ce cahier.

## Fichiers possédés
Nouveaux `B/pilot/**` (`PilotProperties`, `PilotRules` (miroir), `PilotRentalService`, `PilotSealer`, `PilotController`, entités/repositories), `backend/src/main/resources/db/migration/V<n>__pilot_rentals.sql`, `backend/src/test/java/castbridge/server/pilot/**`, `application.yml` (bloc `castbridge.pilot`, éteint). **Hors zone** : `B/licenses/**`, `B/lots/**`, `B/orders/**`, `B/telemetry/**`, `B/admin/**` (w16-09), Android.

## Étapes
1. **Rouge** : `PilotRentalServiceTest` : `orderTwelveHoursIsFreeAndFulfilled` ; `activationLineMatchesPilotVector` (`line-12h-bound-25oct` de w16-06) ; `contractKeyIsRandomAndStoredEncrypted` ; `renewalKeepsTheKeyAndAddsHours` ; `mixedUnitsRefused` ; `afterPilotEndRefused` ; `fourthActiveRefused` ; `usageReportIsMonotone` ; `reissueGivesRemainderThreeTimesThenAbuse` ; `freeBundleNeverSealed` ; `moduleOffAnswers404`.
2. Propriétés `castbridge.pilot.{enabled=false, kek-file, cache-dir, cache-max-bytes=4G, params-file=pilot.json}`.
3. Tables § 3.3 ; routes § 3.3 (`quote`, `orders` avec `Idempotency-Key`, `orders/{ref}`, `{contract}/lots/{lot}/{v}`, `{contract}/usage`, `delivered`, `me`, `config`) ; corps ≤ 256 Ko sauf lots ; limite de débit existante.
4. Scellement : `RentalKeys.seal` miroir Java (déjà requis par w5-06 : si absent, l'écrire ici d'après les vecteurs `seal-lot-file` v1 et `build-activation-12h`) ; cache `${cache-dir}/<contract>/<lot>-v<n>.sealed`, ETag = SHA-256.
5. `AuditLog` chaîné existant pour chaque émission, réémission, relevé anormal.
6. Vert : porte ; `./mvnw -q -o test` complet.

## Critères d'acceptation
Porte verte ; 11 tests rouges puis verts ; module éteint ⇒ toutes les routes 404 ; migration additive ; aucun secret ; les vecteurs `line-*` et `build-activation-12h` rejoués par le service ; aucun lot libre scellé (test).

## Cas limites
Demande sans `install=` (vieille TV) ⇒ refus « mettez CastBridge-TV à jour » ; TV en essai ⇒ refus (clé de production requise) ; relevé plus ancien que le précédent ⇒ ignoré ; `Idempotency-Key` rejouée ⇒ même réponse.

## À ne pas faire
Pas de montant ; pas de mobile money ; ne pas activer le module ; ne pas toucher `B/licenses/**` (si `RentalKeys` Java manque, l'ajouter dans `B/pilot/` et le dire) ; ne pas déployer.

## Rapport
`STATUT`, numéro de migration retenu, sorties rouge/vert, dépendances manquantes (XDH, `seal` Java), question : fusion de `pilot_rental_contract` dans `rental_contract` (w5-06) quand W5 sera là ?
