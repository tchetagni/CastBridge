# w5-20 — Documentation : `SHOP.md`, `TOKENS.md`, RENTAL-LOTS § 15 réécrit, LOTS, TRIAL-EDITION, QUIZ, GAMES, PARENTAL, API-SERVER, ACTIVATION-FORMAT, OWNER-CONSOLE, ACTIVATION-TOOLS, LICENSE-ADMIN, ADMIN, TELEMETRY, HANDOFF

**Vague 5e · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après la fusion de 5a…5d ; lire les rapports `docs/agent-reports/sonnet-w5-*.md`).** Conception : `docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` (source de vérité ; les rapports donnent les formats **réellement** livrés : en cas d'écart, documenter le livré et noter l'écart). Branche `claude/sonnet-w5-20`. Rapport : `docs/agent-reports/sonnet-w5-20.md`.

## Objectif
Les documents disent **ce qui est implémenté**, en français, sans secret ni montant : parcours de la boutique (téléphone et TV), locations en ligne (qui fait quoi, formats, routes, cache, réémission, fenêtre d'essai), jetons (règles, commodités, hors ligne, réconciliation, limites honnêtes), paiement (bons, espèces chez le point focal, abstraction neutre : « aucun autre moyen n'est prévu »), contrôle parental des achats, essai/mode réduit, protection, télémétrie et vie privée, administration serveur.

## Fichiers possédés
Nouveaux `docs/SHOP.md`, `docs/TOKENS.md` ; modifiés `docs/RENTAL-LOTS.md` (§ 15 **réécrit** « Locations gérées par le serveur : implémenté », § 3 et § 11 : mention « aucun maître en production ; `masterFrom` = tests »), `docs/LOTS.md` (§ 1 : exception « la TV ne télécharge que ses propres locations commandées, à la demande »), `docs/TRIAL-EDITION.md` (§ 15 : boutique en lecture, partie découverte, fenêtre d'essai servie par le serveur ; § 16 ligne « locations… relèvent du serveur » ⇒ « implémenté : SHOP.md »), `docs/QUIZ.md` (§ 5 réécrit : « points de défi » sans valeur ; nouveau § « Commodités à jetons » ; essai : partie découverte), `docs/GAMES.md` (jetons = Quiz seulement, Sudoku/Échecs gratuits), `docs/PARENTAL.md` (catégorie « Achats et jetons », allocation, PIN pour acheter, rapport), `docs/API-SERVER.md` (§ boutique : routes, JSON, erreurs, limites), `docs/ACTIVATION-FORMAT.md` (§ 3.5 type `tokens` ; annexe : activation de location serveur), `docs/OWNER-CONSOLE.md` et `docs/ACTIVATION-TOOLS.md` (délégation sans maître, nouveaux champs, `tools/vouchers`, grille jetons), `docs/LICENSE-ADMIN.md` (console boutique, secrets `rental-kek.key`, rôle de la clé serveur), `docs/ADMIN.md` (routes TV `/api/shop`, `/api/tokens`, `/api/activation/proof` ; `GET /api/activation` champs), `docs/TELEMETRY.md` (`shop`, `shop_order`, `tokens_spend` ; données serveur de la boutique et rétention), `docs/HANDOFF.md` (état, à valider sur matériel, ordre de déploiement, risque `TRUSTED_KEYS`). **Hors zone** : `docs/VENTE-TERRAIN.md`, `docs/FICHE-POINT-FOCAL.md` (w5-23), `docs/legal/**` (w5-21), `docs/TEST-CAMPAIGN.md` (w5-22), code.

## Étapes
1. `SHOP.md` : en bref ; acteurs et autorité (schéma de la conception § 2) ; articles et grille ; parcours téléphone (captures de w5-11 si présentes) ; parcours TV ; paiement : bon (format, saisie, hors ligne), espèces (code de commande, confirmation par le point focal, 72 h) ; locations en ligne (demande, preuve, émission, scellement, cache, livraison, renouvellement, réinstallation, limites) ; commandes et états ; reçus ; conditions ; essai / réduit / enfant ; sécurité et fraude (tableau § 10) ; administration ; limites honnêtes.
2. `TOKENS.md` : principes (P4, modèle non prédateur, interdits), commodités et coûts (grille), compte par licence, bons de jetons, porte-jetons, dépense hors ligne, réconciliation et anomalies, réinstallation, parents et enfants, remboursements, indicateurs juridiques (renvoi à `docs/legal/JETONS-MINEURS-INDICATEURS.md`), formats (`wallet.txt`, enveloppe `tokens`), limites honnêtes (rejeu borné à 60).
3. Les documents existants : paragraphes ciblés, sans réécrire le reste ; marquer les sections « non vérifié sur matériel » selon les rapports.
4. Relire `docs/coordination/DESIGN-W5-…` § 14 : chaque changement annoncé doit être reflété ou noté « non fait ».

## Critères d'acceptation
```sh
test -f docs/SHOP.md && test -f docs/TOKENS.md && echo ok
grep -n 'NON IMPLÉMENTÉE' docs/RENTAL-LOTS.md   # 0 hit au § 15
grep -rn 'momo\|Orange Money\|agrégateur' docs/SHOP.md docs/TOKENS.md   # 0 hit, sauf une phrase « aucun autre moyen de paiement n'est prévu »
grep -rln 'XAF [1-9]' docs/SHOP.md docs/TOKENS.md   # 0 hit (aucun montant)
grep -n 'points de défi' docs/QUIZ.md   # ≥ 1
```

## Cas limites
- Un cahier w5 non fusionné au moment de la rédaction : section « prévu, non livré » avec renvoi au cahier (ne pas décrire comme fait).

## À ne pas faire
Pas de commit sur les branches partagées ; aucun montant, numéro, nom, secret ; ne pas éditer les fichiers de w5-21/22/23 ; pas de code.

## Rapport
`STATUT`, fichiers touchés, écarts conception/livré constatés (liste pour le coordinateur).
