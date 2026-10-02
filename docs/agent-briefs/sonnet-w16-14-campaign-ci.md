# w16-14 — Campagne de tests et CI de la vague 16 : porte unique, tests Python du pilote dans `tools.yml`, plan de test `docs/test-plans/RENTAL-PILOT.md`, liste humaine sur la TV de référence

<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : aucune (tout est donné) · statut : PRÊT (après w16-01…07, 12, 13 fusionnés)
> **Groupe : W16b-4** (vague W16b, campagne, autorisé pendant le gel) · prérequis : tranche 1 fusionnée · porte : `tools/agents/gate-w16.sh` (créé ici) vert
> **Jauge : ≈ 100 k jetons entrée / 10 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 16b · Effort S · Modèle : haiku · Statut PRÊT.** Conception : DESIGN-W16 § 6. Branche `claude/sonnet-w16-14`. Rapport : `docs/agent-reports/sonnet-w16-14.md`.

## Objectif
Une commande verte = toute la tranche 1 est bonne : tests cœur `*Rental*`, `*UseMeter*`, `*PilotRules*`, `*RentalPilotVectors*`, `*Telemetry*` ; bureau ; Python (`test_pilot*`) ; vecteurs (`verify_vectors.py --pilot`) ; serveur (si w16-08 fusionné). Le plan de test écrit ce qu'un humain vérifie sur la TV de référence avant le S0 du pilote.

## Fichiers possédés
Nouveaux `tools/agents/gate-w16.sh`, `docs/test-plans/RENTAL-PILOT.md` ; `.github/workflows/tools.yml` (**un** job additif `pilot-tools`) ; `docs/test-plans/README.md` (une ligne). **Hors zone** : le reste de la CI (`android.yml`, `gate.sh` W14 : proposer l'inclusion au rapport).

## Étapes
1. `gate-w16.sh` : enchaîne les portes des cahiers w16-01…07 (commandes exactes de leurs en-têtes), bureau, Python, vecteurs ; sortie « W16 tranche 1 : VERT/ROUGE » ; option `--serveur`.
2. `tools.yml` : job `pilot-tools` (`python3 -m unittest discover -s tools/tests -p 'test_pilot*.py'`, `verify_vectors.py --pilot`).
3. `RENTAL-PILOT.md` : liste humaine (12 points) : louer 1 heure d'utilisation ⇒ lire ⇒ bandeau à 10 min ⇒ fin à 60 min ⇒ lots effacés ; 7 jours ⇒ horloge +8 j ⇒ fin ; sans durée ⇒ 30 jours affichés ; prolongation 6 h + 6 h ; refus d'un mélange ; relevé lu par `louer.py releve` ; réinstallation ⇒ réémission du reste ; profil enfant : aucune question ; Langues « Gratuit » ; TV en essai refusée ; Keystore indisponible : compteur vivant ; horloge reculée : heures intactes.
4. Vert : porte.

## Critères d'acceptation
`tools/agents/gate-w16.sh` vert sur `integration/agents` après la tranche 1 ; `tools.yml` valide (`yamllint` ou `act` à défaut : syntaxe vérifiée) ; plan de test relu par le propriétaire.

## À ne pas faire
Pas de modification des jobs existants ; pas de script qui vise la TV du propriétaire (R6).

## Rapport
`STATUT`, durée de la porte, points de la liste humaine non vérifiables sans TV.
