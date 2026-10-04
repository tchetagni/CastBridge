# w22-10 — Administration du portefeuille (niveau 2) : gel, reprise, réaffectation d'appareil, réconciliation nocturne, clôture mensuelle et archivage, pages `/admin/wallet`
<!-- routage architecte 2026-10-04 (W22, niveau 2) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (actions d'administration journalisées, jamais de négatif, clôture conservatrice) · statut : **ATTEND la fin du niveau 1**
> **Groupe : W22-N2** · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.admin.**'`
> **Jauge : ≈ 450 k jetons entrée / 25 k sortie** (effort M, ≈ 1,5-2 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 3.7 budget, § 5.4). Branche `claude/w22-10-administration`. Rapport : `docs/agent-reports/sonnet-w22-10.md`.

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/wallet/admin/{FreezeService,ClawbackService,RebindService,NightlyReconciliation,MonthlyClose,EntryArchiver,WalletAdminPages}.java` ; gabarits Thymeleaf `backend/src/main/resources/templates/admin/wallet*.html` (modèle des pages `/admin/kpi`) ; migration additive « plus haut + 1 » (partitionnement mensuel de `wallet_entry` si MySQL 8.4 le permet sans arrêt, sinon table `wallet_entry_archive_index`) ; tests.
- **Interdit** : Android, `server-play/**`, classes `ops/` (appel par interface).

## Spécification
1. Gel (`frozen`) : plus aucune sortie (blocage, transfert, conversion), entrées et affichage continuent ; motif, TOTP, ligne dans l'`AuditLog` chaîné existant ; `cbw1.flags.frozen=1` ⇒ la TV affiche `FROZEN`.
2. Reprise : `ADJUST` négatif motivé, **jamais au-delà du disponible** ; le reste est noté `wallet_admin_debt` (informatif, jamais prélevé automatiquement).
3. Réaffectation : change l'`api_device_id` lié (changement de TV légitime), motif, TOTP.
4. Réconciliation nocturne (03:30 Douala) : I-1, I-3, I-8, blocages échus (rendus), résultats orphelins, alertes ; écart ⇒ alerte journal + `switch.transfer=0` **automatique** (les parties continuent), rétabli à la main.
5. Clôture mensuelle : transaction de report par compte ; écritures de plus de 13 mois exportées en CSV compressé (`/var/lib/castbridge/wallet-archive/AAAA-MM.csv.gz`, empreinte SHA-256 dans la base) puis retirées ; la conservation reste vérifiable (Σ reports + Σ écritures vives).
6. Pages : recherche par identité (code d'appareil), soldes, 100 dernières transactions, alertes de w22-09, boutons gel/reprise/réaffectation ; agrégats de masse en circulation par monnaie.

## Critères d'acceptation
- Gel : toute sortie refusée, entrée acceptée (propriété) ; reprise > disponible ⇒ reprise du disponible + dette, aucun négatif.
- Réconciliation : écart injecté dans `wallet_balance` ⇒ détecté, `switch.transfer` coupé.
- Clôture + archivage sur 20 000 écritures synthétiques ⇒ soldes identiques avant/après, conservation vérifiée.
