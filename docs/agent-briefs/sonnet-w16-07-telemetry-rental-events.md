# w16-07 — Télémétrie : événements `rental_start`, `rental_use`, `rental_end`, `rental_extend`, `rental_survey` (catégorie usage, consentement), cœur + serveur + `docs/TELEMETRY.md`

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune (liste blanche, deux catalogues à aligner) · statut : PRÊT
> **Groupe : W16a-6** (vague W16a, autorisé pendant le gel : cœur, serveur, doc) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Telemetry*' && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='EventCatalog*Test,Telemetry*Test'`
> **Jauge : ≈ 200 k jetons entrée / 10 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 16a · Effort S · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W16 § 5.2. Branche `claude/sonnet-w16-07`. Rapport : `docs/agent-reports/sonnet-w16-07.md`.

## Objectif
Les deux catalogues (cœur Kotlin, serveur Java) acceptent cinq événements de location avec une **liste blanche** de propriétés à faible cardinalité, en catégorie **usage** (refusés sans consentement « statistiques », sur l'appareil et au serveur), et la documentation les décrit. Aucun identifiant de contrat, de licence, d'appareil dans les propriétés.

## Pourquoi (preuves)
- `C/telemetry/Telemetry.kt:23` (`ESSENTIAL`), `:25` (`EVENTS` liste blanche), `:58` (`FORBIDDEN`), `:164-165` (refus sans consentement, clés interdites).
- `B/telemetry/EventCatalog.java` (miroir ; **lignes non vérifiées** : lire le fichier), `docs/TELEMETRY.md:66-89` (catalogue), `:116-117` (« déployer le serveur avant les apps »).
- Fichier partagé avec W11/W12/W10 (`DESIGN-W12:314`) : `Telemetry.kt` ⇒ lancer **après** w11-04/w11-14/w10-07 s'ils sont lancés ; sinon ce cahier passe d'abord et les autres se rebasent (une ligne chacun).

## Fichiers possédés
`C/telemetry/Telemetry.kt` (zone `EVENTS` : 5 lignes ajoutées), `CT/TelemetryTest.kt` (tests ajoutés), `B/telemetry/EventCatalog.java` (zone catalogue), test serveur correspondant, `docs/TELEMETRY.md` § 4 (5 lignes de tableau). **Hors zone** : `TelemetryUploader`, pages KPI (w16-09), `R/`, `S/`.

## Étapes
1. **Rouge** : `TelemetryTest.rentalEventsNeedUsageConsent` ; `rentalEventsRejectForbiddenAndUnknownProps` ; serveur : même paire.
2. Propriétés : `rental_start{bundle, unit (hours|days|default), amount, maxMinutes}` ; `rental_use{bundle, unit, minutes}` ; `rental_end{bundle, unit, reason (usage|date|over_limit), usedMinutes, maxMinutes}` ; `rental_extend{bundle, unit, amount}` ; `rental_survey{unit, q, answer}` ; `exp` (W12) acceptée si déjà prévue par le catalogue, sinon rien. `bundle` : code ≤ 64 car. `[A-Za-z0-9_.:+/-]` (règle existante `docs/TELEMETRY.md:53`).
3. `docs/TELEMETRY.md` § 4 : cinq lignes, niveau **usage**, note « `rental_use` est agrégé par jour et par contrat sur l'appareil, jamais par minute ; jamais d'identifiant de contrat ».
4. Vert : porte.

## Critères d'acceptation
Porte verte ; 4 tests rouges puis verts ; les deux catalogues listent exactement les mêmes clés (test de parité s'il existe, sinon `grep` cité au rapport) ; `docs/TELEMETRY.md` à jour.

## À ne pas faire
Pas de nouvel identifiant de fonctionnalité (`feature_used`) ; pas d'événement essentiel ; ne pas toucher `TelemetryUploader` ni les écrans.

## Rapport
`STATUT`, sorties rouge/vert, ordre de déploiement rappelé (serveur avant apps), conflit éventuel avec w11-04/w11-14/w10-07 sur `Telemetry.kt`.
