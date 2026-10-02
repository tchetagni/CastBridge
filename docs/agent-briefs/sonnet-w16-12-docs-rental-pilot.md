# w16-12 — Docs : `RENTAL-LOTS.md` § 17 (deux unités, défaut 30 jours, mode « choisi par l'utilisateur », pilote), `ACTIVATION-TOOLS.md` § 9, `LOTS.md`, `HANDOFF.md` § 0

<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : aucune (tout est donné) · statut : PRÊT (après la fusion de w16-01…05)
> **Groupe : W16b-2** (vague W16b, docs, autorisé pendant le gel) · prérequis : w16-01…05 fusionnés (citer les lignes réelles) · porte : `grep -n "## 17" docs/RENTAL-LOTS.md && grep -n "location-choix" docs/ACTIVATION-TOOLS.md`
> **Jauge : ≈ 150 k jetons entrée / 10 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 16b · Effort S · Modèle : haiku · Statut PRÊT.** Conception : DESIGN-W16 § 1, § 2.5, § 3.2, § 4. Branche `claude/sonnet-w16-12`. Rapport : `docs/agent-reports/sonnet-w16-12.md`. Français ; « CastBridge » / « CastBridge-TV » ; aucun secret ; aucun texte juridique.

## Objectif
Les documents disent la vérité du code fusionné : la ligne `rental` porte l'unité par ses deux plafonds ; trois choix ; « exacte » = `rental.userChosen = 0` ; borne de sûreté ; compteur ; relevé ; outils ; limites honnêtes.

## Fichiers possédés
`docs/RENTAL-LOTS.md` (nouveau § 17 ; § 14 : une phrase « voir § 17 » ; § 11 : deux limites ajoutées), `docs/ACTIVATION-TOOLS.md` (nouveau § 9 : `--location-choix`, `--pilote`, `--registre`, `louer`, `rapport-usage`), `docs/LOTS.md` (une ligne de renvoi), `docs/HANDOFF.md` (§ 0 : état W16, tranche 1, D-W16-9). **Hors zone** : tout le reste.

## Étapes
1. § 17 de `RENTAL-LOTS.md` : tableau des trois choix (nature, `durationDays`, `maxUsageMinutes`, libellé), règle « l'unité est portée par la ligne », prolongation sans mélange, clamp 96 h, borne de sûreté, compteur (`UseMeter` : minute entière, report, pause 5 min, inactivité 30 min, perte ≤ 1 min), relevé `castbridge-rental-usage-v1` (exemple), réglages W12 (§ 4.1 de la conception, noms exacts), limites honnêtes (réinstallation ⇒ reste au dernier relevé ; relevé non signé au pilote ; root).
2. `ACTIVATION-TOOLS.md` § 9 : aide réelle des commandes (copiée de `DK/Cli.kt` fusionné), exemple de `pilot.json`, dossier de livraison, `louer.py`, `bilan.py`.
3. `HANDOFF.md` § 0 : trois lignes.
4. Relire : aucune phrase ne convertit heures en jours.

## Critères d'acceptation
Porte verte ; chaque affirmation cite un fichier:ligne du code fusionné ; `grep -n "96 h = \|= 4 jours" docs/RENTAL-LOTS.md` vide.

## À ne pas faire
Ne pas réécrire les § 1-16 ; pas de code ; pas de montant.

## Rapport
`STATUT`, liste des sections touchées, écarts trouvés entre la conception et le code fusionné (à remonter, ne pas corriger le code).
