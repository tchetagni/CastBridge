# w19-11 — Docs : `docs/SYMBIOSE.md` (contrat S-1…S-12 + S-REPRISE, autorités, enveloppe, poignée de main, matrice), R-17 dans `REGRESSIONS.md`, P-53/P-54 et H-REPRISE dans `PARCOURS-CRITIQUES.md` et la liste humaine, `HANDOFF.md` § 0, renvois

<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : sonnet si une section demande un jugement · statut : après w19-08
> **Groupe : W19-S2** · prérequis : w19-01…08, 13 fusionnés (sinon : rédiger « à venir » avec le nom du cahier) · porte : `python3 tools/tests/test_routes.py` (si présent) et relecture : aucun secret, aucune adresse, français
> **Jauge : ≈ 120 k jetons entrée / 12 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 19 · Effort S · Modèle : haiku.** Conception : DESIGN-W19 (tout, en source). Branche `claude/sonnet-w19-11`. Rapport : `docs/agent-reports/sonnet-w19-11.md`. Mémoire : « Always write a handoff ».

## Objectif
(1) `docs/SYMBIOSE.md` (≤ 250 lignes) : le contrat (table S-1…S-12 + S-REPRISE copiée du DESIGN § 1.1, allégée), la table des autorités (§ 1.3), les codes `Reason` (§ 3.1) avec leur HTTP et réessayabilité, la poignée de main (`/api/hello` exemple, `X-CB-App`, fenêtre § 2.3, replis § 2.4), l'état de synchronisation (exemple JSON) et la carte Cohérence (niveaux), le protocole de reprise (§ 3.5 : schéma de réconciliation, états finaux), la matrice et la porte (comment enregistrer une persona, comment lire `check_compat.py`), la Règle de symbiose (§ 5). (2) `docs/REGRESSIONS.md` : ligne **R-17** (symptôme verbatim « Broken pipe » toutes les 25 s pendant 30 min, versions 1.2.39 / 0.14.25, cause `TransferClient.kt:141` + fermeture sans vidage, parcours P-53, tests `TransferClientBoundedRetryTest` rouge/vert collés depuis le rapport w19-01, corrigé en w19-01/w19-13, statut « CORRIGÉE (à confirmer sur appareil) »). (3) `docs/test-plans/PARCOURS-CRITIQUES.md` : **P-53** « Refus de la TV visible des deux côtés en 5 s, jamais de boucle » (S1), **P-54** « Carte Cohérence : concordance, divergence de bibliothèque, TV ancienne » (S2), section **H-REPRISE** (les 6 gestes du DESIGN § 1.4) ; répartition mise à jour ; `CHECKLIST-HUMAINE-LIVRAISON.md` : H-REPRISE ajouté (≤ 15 min au total conservé : dire ce qui sort). (4) `docs/HANDOFF.md` § 0 : entrée datée W19 (fait, reste, décisions D-W19-1…14 avec leur recommandation, faits non vérifiés) ; `docs/TRANSFER.md`, `docs/BT-PLUG-AND-PLAY.md`, `docs/RELEASES.md` : un renvoi chacun vers `SYMBIOSE.md` et `PROTOCOL-CHANGES.md`.

## Fichiers possédés
`docs/SYMBIOSE.md` (neuf), `docs/REGRESSIONS.md` (une ligne), `docs/test-plans/PARCOURS-CRITIQUES.md` (P-53, P-54, H-REPRISE, répartition), `docs/test-plans/CHECKLIST-HUMAINE-LIVRAISON.md` (section), `docs/HANDOFF.md` (§ 0 une entrée), `docs/TRANSFER.md`, `docs/BT-PLUG-AND-PLAY.md`, `docs/RELEASES.md` (un renvoi chacun). **Hors zone** : tout code, les cahiers, le DESIGN.

## Étapes
1. Lire les rapports `docs/agent-reports/sonnet-w19-0{1,2,3,4,5,6,7,8}.md` et `sonnet-w19-13.md` : ne documenter que ce qui est fusionné ; le reste « à venir (w19-NN) ».
2. Rédiger ; vérifier : aucun PIN, jeton, adresse complète, chemin de clé ; « CastBridge » / « CastBridge-TV ».
3. Porte ; rapport.

## Critères d'acceptation
- Chaque S-n de `SYMBIOSE.md` cite son test et son cahier ; chaque code `Reason` a son HTTP.
- R-17 suit le format de la table (sortie rouge/verte citée).
- H-REPRISE tient en ≤ 5 min ajoutées à la liste humaine (dire ce qui est retiré ou fusionné).

## À ne pas faire
Inventer un chiffre non mesuré (écrire « objectif » ou « non mesuré ») ; copier un journal ; modifier un cahier.

## Rapport
RAPPORT (≤ 25 lignes) + `SYMBIOSE: docs seulement`.
