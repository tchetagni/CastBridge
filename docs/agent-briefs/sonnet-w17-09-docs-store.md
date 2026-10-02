# w17-09 — Docs : `docs/STORE.md` (la Boutique sur le téléphone et vue par la TV, formats, routes, états, demande), renvois dans `LOTS.md`, `RENTAL-LOTS.md`, `HANDOFF.md` § 0

<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : aucune (tout est donné par DESIGN-W17) · statut : PRÊT (après w17-01…04 fusionnés ; pendant le gel : docs seules)
> **Groupe : W17b-1** (vague W17b, docs) · prérequis : w17-01, 02, 03, 04 · porte : `python3 -m unittest discover -s tools/tests -p 'test_docs*.py' 2>/dev/null || true ; grep -c "castbridge-rent-request-v1" docs/STORE.md`
> **Jauge : ≈ 120 k jetons entrée / 12 k sortie** (effort S, ≈ 0,3 j) · audit Opus : non

**Vague 17b · Effort S · Modèle : haiku · Statut PRÊT.** Source unique : `docs/coordination/DESIGN-W17-STORE-TELEPHONE-ET-TV-2026-10-03.md` § 0, § 2.3, § 2.5, § 3.2, § 4.1, § 4.3, § 5. Branche `claude/sonnet-w17-09`. Rapport : `docs/agent-reports/sonnet-w17-09.md`. Dire « CastBridge » (téléphone) / « CastBridge-TV ». Aucun secret, aucun montant, aucun texte juridique.

## Fichiers possédés
Nouveau `docs/STORE.md` ; modifiés `docs/LOTS.md` (une ligne de renvoi à la fin du § 1), `docs/RENTAL-LOTS.md` (une ligne de renvoi à la fin du § 15), `docs/HANDOFF.md` (§ 0 : une entrée datée « W17 conçue, cœur en cours » + une ligne dans la liste des docs). **Hors zone** : tout le reste.

## Contenu de `docs/STORE.md` (≤ 180 lignes, sections numérotées, tableaux repris du document de conception **sans les reformuler**)
0. En bref (10 lignes : une Boutique, deux rendus ; aucun format signé nouveau ; la TV demande, le téléphone commande ; drapeau `store.enabled`).
1. Sources de données : les deux catalogues signés (formats existants, où ils vivent sur le téléphone et sur la TV, plafonds 256 Ko / 64 Ko, anti-retour).
2. États d'un article (table § 2.3) et phrases bloquées (table § 2.5), **copiées** depuis `StoreTexts` (citer les constantes).
3. Routes TV `/api/store*` (table § 4.3), essai (liste blanche), PIN.
4. La demande `castbridge-rent-request-v1` (format § 4.1, code court, états `PENDING → ACCEPTED|REFUSED|EXPIRED → FULFILLED`, ce qu'elle n'est pas).
5. Fraîcheur et téléphone absent (§ 5).
6. Essai, profil enfant, modes (table § 3.4).
7. Drapeau `store.enabled` (W12 › ordre signé `flag.set` › défaut compilé).
8. Tests : classes `CT/store/*`, parcours J `StoreJourneyTest` (J-S0…S6), vecteurs `tools/activation/store-vectors.json`.
9. Limites honnêtes : la demande ne prouve rien ; LAN avec PIN = nuisance bornée ; TV jamais rejointe = vitrine ancienne ; identifiants de bouquets BLOQUÉS.

## Critères d'acceptation
`grep -c "castbridge-rent-request-v1" docs/STORE.md` ≥ 2 ; `grep -n "sender\|receiver" docs/STORE.md` ne cite ces mots que dans des chemins de fichiers ; `grep -n "XAF" docs/STORE.md` vide ; `git status --porcelain` = 4 fichiers.

## À ne pas faire
Ne pas réécrire la conception ; ne pas inventer de valeur (aucune taille non issue de § 3.2) ; ne pas toucher d'autre doc.

## Rapport
`STATUT`, fichiers, nombre de lignes de `STORE.md`.
