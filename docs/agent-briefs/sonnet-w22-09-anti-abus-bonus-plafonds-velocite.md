# w22-09 — Anti-abus (niveau 2) : poche BONUS dont l'étiquette suit la conversion, plafonds de conversion (deux sens) et de transfert, perte nette quotidienne, âge minimal du compte, alertes de vélocité
<!-- routage architecte 2026-10-04 (W22, niveau 2 : après le test de faisabilité) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (aucun contournement par aller-retour, conservation avec poches) · statut : **ATTEND la fin du niveau 1 et D-W22-6**
> **Groupe : W22-N2** · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.**'`
> **Jauge : ≈ 450 k jetons entrée / 25 k sortie** (effort M, ≈ 1,5-2 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 1.3 étiquette d'origine, § 5.2 S-1/S-4/S-6/S-13/S-14, § 5.3 R-E4…R-E9). Branche `claude/w22-09-anti-abus`. Rapport : `docs/agent-reports/sonnet-w22-09.md`.

## Objectif (autonome)
Borner la ferme d'essais, le blanchiment par cascade et l'aller-retour de conversion **sans gêner le joueur honnête**, uniquement par des règles d'intégrité économique paramétrées dans `wallet_policy` (jamais en dur).

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/wallet/abuse/{BonusPockets,DailyCaps,VelocityMonitor,WalletAlert,AbuseRules}.java` ; migration additive `V6x__wallet_abuse.sql` (« plus haut + 1 » : table `wallet_alert`, compteurs journaliers `wallet_daily_counter(holder, day, kind, cur, amount)`, lignes de politique nouvelles) ; tests `backend/src/test/java/castbridge/server/wallet/abuse/**`.
- **Zone additive** : `wallet/core/{Conversion,Ledger}` (poche `BONUS` : consommée d'abord par mises et conversions, crédit dans la poche de même étiquette de la monnaie cible ; gains de partie en `DISPO`) ; `wallet/ops/{ConvertService,TransferService,EscrowService}` (appel des plafonds) ; `GrantService` (tranches d'essai et rémunérations en `BONUS`).
- **Interdit** : Android, `server-play/**`.

## Spécification (valeurs = défauts de `wallet_policy`)
R-E4 BONUS non transférable en sortie ; étiquette qui **suit** la conversion (NDEM BONUS ↔ MBOKO BONUS). R-E5 conversion : 20 MBOKO / jour / identité, essai 2, **deux sens additionnés**. R-E6 transferts sortants : NDEM 10 000 / jour, 5 000 / destinataire / jour ; MBOKO 100 / jour, 50 / destinataire ; essai : 1 000 NDEM / jour, MBOKO 0 ; émetteur âgé d'au moins 72 h. R-E8 perte nette MBOKO ≤ 500 / jour / identité (refus de blocage au-delà, « Plafond du jour atteint »). R-E9 alertes **sans blocage automatique** : > 3 donateurs vers un même compte en 24 h ; > 80 % des gains de partie d'une identité contre une même identité sur 7 j (≥ 10 parties) ; > 20 transferts / jour ; conversion au plafond 5 jours de suite ; aller-retour N→M→N > 3 fois / jour.

## Critères d'acceptation (mutations au rapport)
- Propriété (5 000 suites) : avec poches, I-1…I-9 tiennent ; **aucune suite** d'opérations ne fait passer un jeton d'origine BONUS dans une poche transférable autrement que par un **gain de partie** (mutation : créditer la conversion en `DISPO` ⇒ échec).
- Aller-retour N→M→N d'un NDEM BONUS ⇒ revient en BONUS ; transfert refusé.
- Ferme simulée : 10 identités d'essai, 3 mois, transferts et parties arrangées vers un collecteur ⇒ gain borné (rapporté au rapport) et alertes levées.
- Joueur honnête simulé (production, 60 parties/mois, 2 conversions/mois, 3 transferts/mois) ⇒ **aucun** refus, **aucune** alerte.
