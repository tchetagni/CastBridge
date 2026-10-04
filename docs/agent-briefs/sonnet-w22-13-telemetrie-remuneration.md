# w22-13 — Télémétrie technique du portefeuille et table des politiques de rémunération (niveau 2)
<!-- routage architecte 2026-10-04 (W22, niveau 2) -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (aucune identité dans les agrégats, règles inactives par défaut) · statut : **ATTEND w22-09 et la fusion de W21 (V62, `kpi_*`)**
> **Groupe : W22-N2** · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.kpi.**,castbridge.server.wallet.reward.**'`
> **Jauge : ≈ 300 k jetons entrée / 15 k sortie** (effort S, ≈ 1 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 1.5, § 8) ; `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` (plancher k = 5, histogrammes à bornes fixes, catalogue fermé). Branche `claude/w22-13-telemetrie-remuneration`. Rapport : `docs/agent-reports/sonnet-w22-13.md`.

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/wallet/kpi/WalletDailyKpi.java` (agrégation nocturne depuis le grand livre vers `kpi_wallet_daily` : tranches par édition et monnaie, masse en circulation, conversions par sens et histogramme 1 / 2-5 / 6-20, transferts par bornes 100 / 1 000 / 10 000 NDEM et 10 / 50 / 100 MBOKO, parties misées par monnaie, histogramme des mises, taux d'abandon, délai blocage → règlement p50/p95, part réglée par le collecteur, bons confirmés/doublons, alertes par type) ; `backend/src/main/java/castbridge/server/wallet/reward/{RewardRule,RewardEngine}.java` (table `wallet_reward_rule` du § 1.5, **toutes inactives par défaut**, plafonds par identité et globaux, écritures `REWARD` en poche `BONUS`) ; migration additive « plus haut + 1 » ; section « Jetons » de `/admin/kpi` ; tests.
- **Zone additive** : `backend/src/main/java/castbridge/server/telemetry/EventCatalog.java` (un seul évènement TV : `wallet_error{reason}`, niveau statistiques d'usage, motif dans la liste fermée de `WalletReason`).
- **Interdit** : Android (l'émission de `wallet_error` côté TV est une ligne dans w22-07 ou un suivi), `server-play/**`.

## Critères d'acceptation
- Aucune colonne d'agrégat ne contient d'identité ; une case < 5 identités n'est ni affichée ni exportée (test).
- Règle de rémunération inactive ⇒ aucun versement ; active ⇒ versement plafonné, idempotent par `reward:<règle>:<id>:<réf>` ; conservation tenue.
