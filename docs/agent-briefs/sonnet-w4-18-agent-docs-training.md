# w4-18 — Documentation de la vente terrain et fiche de formation du point focal

**Vague 4c · Effort M (≈ 1 j) · Statut PRÊT (après w4-11…w4-17 ; BLOQUÉ partiel D7/D9-bis pour les montants et le contact dans la fiche).** Conception : `docs/coordination/DESIGN-W4-VENTE-TERRAIN.md` (source unique). Branche `claude/sonnet-w4-18`. Rapport : `docs/agent-reports/sonnet-w4-18.md`.

## Objectif
Un document de référence (`docs/VENTE-TERRAIN.md`) et une **fiche d'une page** pour former un point focal (`docs/FICHE-POINT-FOCAL.md`), plus la mise à jour des documents existants (format, console, serveur, locations).

## Fichiers possédés
Nouveaux `docs/VENTE-TERRAIN.md`, `docs/FICHE-POINT-FOCAL.md`. Modifiés `docs/ACTIVATION-FORMAT.md` (§ 3.5 « Type `delegation` », § 4 ligne ticket, § 2 portée `DELEGATE`, § 11 `agent-vectors.json`), `docs/RENTAL-LOTS.md` (§ 15 **remplacé** : « Locations vendues par les points focaux » ; § 3 maître explicite), `docs/OWNER-CONSOLE.md` (onglet « Points focaux », maître, grille), `docs/ACTIVATION-TOOLS.md` (commandes `delegation`, `maitre`, `grille-prix`), `docs/LICENSE-ADMIN.md` (§ « Points focaux » : console, anomalies, versements, révocation), `docs/API-SERVER.md` (§ 6 `/api/v1/agent/sync`, `/api/v1/catalog/prices`), `docs/TELEMETRY.md` (registre des traitements : codes d'appareil dans les journaux d'agents), `docs/HANDOFF.md` (§ 0), `docs/agent-briefs/SONNET-WAVES-INDEX.md` **non** (ne pas modifier l'existant ; l'index de la vague 4 est `SONNET-WAVE4-INDEX.md`). **Hors zone** : code.

## Étapes
1. `VENTE-TERRAIN.md` : rôles, flux (§ 7 de la conception) avec captures textuelles, délégation (format exact, copié du rapport w4-11), ticket, journal (format d'une ligne, copié du rapport w4-14), reçu, synchronisation, anomalies (liste et ce que le propriétaire fait pour chacune), révocation (chemin complet : console → `cbr1` → serveur → TV via relais), remboursements, limites honnêtes (maître enveloppé pour l'agent, agent révoqué hors ligne, deux téléphones), décisions prises (§ 11).
2. `FICHE-POINT-FOCAL.md` (une page, français simple, listes numérotées) : « Avant de partir » (téléphone chargé, synchronisé, lots prêts, code du coffre), « Chez le client » (6 étapes : lire la TV, choisir, encaisser, émettre, livrer, reçu), « Si ça ne marche pas » (TV introuvable, refus « mettez CastBridge-TV à jour », mandat expiré), « Interdits » (jamais de clé illimitée, jamais de vente sans reçu, jamais de suppression dans le journal, ne pas prêter son téléphone), « Remise d'espèces » (quand, comment, le solde affiché fait foi), contact du propriétaire (`[à compléter : D7]`), grille des prix (`[à compléter : D9-bis]`).
3. Mises à jour des documents existants (sections listées), chaque § citant les symboles/routes réellement livrés (lire les rapports).
4. HANDOFF § 0 : entrée datée.

## Critères d'acceptation
```sh
test -f docs/VENTE-TERRAIN.md && test -f docs/FICHE-POINT-FOCAL.md
grep -n 'type=delegation' docs/ACTIVATION-FORMAT.md   # ≥ 1
grep -n 'NON IMPLÉMENTÉE' docs/RENTAL-LOTS.md   # 0 hit (§ 15 remplacé)
grep -n 'à compléter' docs/FICHE-POINT-FOCAL.md   # exactement les 2 champs bloqués
wc -w docs/FICHE-POINT-FOCAL.md   # ≤ 600 mots
```

## À ne pas faire
Pas de code ; aucun montant, numéro ou nom inventé ; pas d'avis juridique ; ne pas éditer les cahiers existants ni `SONNET-WAVES-INDEX.md` ; français.

## Rapport
`STATUT: BLOQUÉ partiel` (D7, D9-bis) + sections livrées ; écarts conception/code remontés.
