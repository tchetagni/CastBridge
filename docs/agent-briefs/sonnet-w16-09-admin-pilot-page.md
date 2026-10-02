# w16-09 — Serveur : page `/admin/pilot/rentals` (TOTP), KPI du pilote HP1-HP10, export CSV, bouton « réémettre (reste) », `GET /api/v1/admin/kpi/pilot`

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (TOTP sur la réémission) · statut : PRÊT (après w16-08)
> **Groupe : W16c-2** (vague W16c, serveur, autorisé pendant le gel) · prérequis : w16-08 · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='AdminPilot*Test,PilotKpi*Test'`
> **Jauge : ≈ 350 k jetons entrée / 18 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 16c · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W16 § 5.1, § 5.3. Branche `claude/sonnet-w16-09`. Rapport : `docs/agent-reports/sonnet-w16-09.md`.

## Objectif
Le propriétaire lit, sur la console web, chaque contrat du pilote (bouquet, unité, quantité, minutes utilisées, état, dernier relevé, réémissions) et les indicateurs HP1-HP10 avec leurs seuils (vert / orange / rouge), exporte en CSV, et peut **réémettre le reste** (TOTP) pour une TV réinstallée.

## Pourquoi (preuves)
- Patrons : `B/admin/AdminKpiPages.java`, `B/orders/AdminOrdersPage.java`, `TPL/admin/*.html`, `B/licenses/Totp.java` ; `DESIGN-W12:194-200` (pages TOTP, CSRF, CSP).
- Données : tables de w16-08 ; télémétrie `rental_*` (w16-07) via `kpi_event_day` si disponible, sinon relevés seuls (le dire).

## Fichiers possédés
Nouveaux `B/pilot/AdminPilotPage.java`, `B/pilot/PilotKpiService.java`, `TPL/admin/pilot-rentals.html`, tests ; `TPL/admin/lic-nav.html` (**un lien**, après w5-09/w10-06 s'ils sont lancés). **Hors zone** : le reste de `B/admin/**`, `B/pilot/PilotRentalService.java` (w16-08, appelé seulement).

## Étapes
1. **Rouge** : `AdminPilotPageTest` (page 200 avec TOTP, 403 sans ; CSV ; réémission exige TOTP) ; `PilotKpiServiceTest` (HP1-HP10 sur un jeu de données : parts heures/jours/défaut, médiane `used/max` des contrats horaires, intensité jours, abandon à 7 j, prolongations, % à 96 h, écart relevés, seuils).
2. Page : tableau, filtres (unité, état, bouquet, point focal si connu), KPI avec seuils de DESIGN-W16 § 5.1, export CSV (une ligne par contrat, aucune personne), bouton « réémettre (reste) » (TOTP ⇒ `PilotRentalService.reissue`).
3. `GET /api/v1/admin/kpi/pilot` (jeton admin) : même contenu JSON.
4. Vert : porte.

## Critères d'acceptation
Porte verte ; 6 tests rouges puis verts ; aucune donnée personnelle dans la page ni le CSV ; module éteint ⇒ 404.

## À ne pas faire
Pas de montant ; pas de modification des pages existantes hors un lien ; pas de décision automatique (les seuils s'affichent, le propriétaire décide).

## Rapport
`STATUT`, sorties rouge/vert, capture textuelle de la page (tableau), question : faut-il un graphique « heures utilisées par jour écoulé » (recommandation : CSV + `bilan.py`, pas de JS).
