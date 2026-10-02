# w11-14 — Docs et catalogue : `docs/NAVIGATION.md`, mises à jour ADMIN/REMOTE/PARENTAL/GAMES/LANGUES/TRIAL-EDITION/TELEMETRY/HANDOFF, ids de télémétrie `plus`, `phone_page`, `quick_action`, `shop`, `tokens_spend`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-e** (vague W11) · prérequis : w11-04 · porte : `gradle --offline :core:test`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 11e (docs) · Effort S (≈ 1 j) · Modèle : sonnet (synthèse de docs : hors forme mécanique Haiku, ROUTAGE § 4.7 ; la partie `Telemetry.kt` seule serait Haiku) · Statut PRÊT (après 11c et 11d pour la partie docs ; la partie catalogue peut partir dès 11b).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` (entière). Branche `claude/sonnet-w11-14`. Rapport : `docs/agent-reports/sonnet-w11-14.md`.

## Objectif
Les docs décrivent la navigation **telle qu'elle est** (rendu legacy et `NAV_V2`), une doc unique `docs/NAVIGATION.md` porte le modèle (destinations, tuiles, Plus, états, touches, règle d'absorption § 6.5), et la liste close de télémétrie connaît les nouveaux ids.

## Pourquoi (preuves)
- `android/core/src/main/kotlin/castbridge/core/telemetry/Telemetry.kt:16-19` : listes closes `TV_FEATURES`, `PHONE_FEATURES` ; w11-08/10/12 utilisent provisoirement des ids existants en attendant `quick_action`, `plus`, `phone_page` ; W5 prévoit `shop`, `tokens_spend` (`DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md`).
- `docs/ADMIN.md:163-181` décrit l'accueil TV à 3 tuiles (périmé : 18 aujourd'hui) ; `docs/REMOTE.md:5-6` cite « onglet CastBridge TV › tuile Télécommande » ; `docs/PARENTAL.md:205`, `docs/GAMES.md:7-11`, `docs/LANGUES.md:302`, `docs/TRIAL-EDITION.md:226` décrivent des entrées qui changent ; `docs/HANDOFF.md` est daté du 2026-10-01 et ne parle pas d'UX.
- Aucune doc UX/navigation n'existe (vérifié : rien ne correspond à `UX*`, `UI*`, `HOME*`, « navigation » dans `docs/`).

## Fichiers possédés
- Nouveau : `docs/NAVIGATION.md`.
- Modifiés : `docs/ADMIN.md` (§ « Écran d'accueil (TV) et accueil du téléphone »), `docs/REMOTE.md` (§ points d'entrée), `docs/PARENTAL.md` (§ « Onglet Parental » : destination « Parents », 4 entrées de 1er niveau), `docs/GAMES.md` (entrées), `docs/LANGUES.md` (entrées : sous-onglet Apprendre › Langues ; TV : ligne du hub Apprendre), `docs/TRIAL-EDITION.md` (badge court, tuiles fermées = absentes + ligne « Version complète »), `docs/TELEMETRY.md` (ids), `docs/HANDOFF.md` (état W11, interrupteur `NAV_V2`), `android/core/src/main/kotlin/castbridge/core/telemetry/Telemetry.kt` (ids), et le test cœur qui fige ces listes s'il existe (`grep -rn "TV_FEATURES\|PHONE_FEATURES" android/core/src/test`).
- Hors zone : tout autre code ; `docs/coordination/*` ; les cahiers.

## Étapes
1. `Telemetry.kt` : `TV_FEATURES` + `langues` (si w11-04 ne l'a pas déjà fait), `plus`, `phone_page`, `shop`, `tokens_spend` ; `PHONE_FEATURES` + `quick_action`, `shop`, `tokens_spend`, `langues` ; mettre à jour le test qui fige la liste ; `docs/TELEMETRY.md` : même liste, une ligne par id avec son déclencheur.
2. `docs/NAVIGATION.md` (≤ 150 lignes) : les tables § 3.1, § 3.2, § 4.1, § 4.2, § 4.4 (touches), § 5 (états), § 6.5 (règle d'absorption), § 8 (libellés), l'interrupteur `NAV_V2` et comment l'allumer, la mesure (`tools/ux`, w11-13), et un § « Rendu legacy » qui décrit l'ancien accueil en 10 lignes.
3. Chaque doc modifiée : remplacer la description des entrées par une phrase + renvoi à `docs/NAVIGATION.md` ; ne pas réécrire les sections fonctionnelles.
4. `HANDOFF.md` : date, § « Navigation (W11) » : ce qui est fusionné, l'état de `NAV_V2`, les cahiers restants, les décisions D-W11-1..8 et leur statut.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test   # vert (test de catalogue mis à jour)
grep -c '"quick_action"\|"plus"\|"phone_page"\|"shop"\|"tokens_spend"' core/src/main/kotlin/castbridge/core/telemetry/Telemetry.kt   # ≥ 5
test -f ../docs/NAVIGATION.md && wc -l ../docs/NAVIGATION.md   # ≤ 150
grep -c 'NAVIGATION.md' ../docs/ADMIN.md ../docs/REMOTE.md ../docs/PARENTAL.md ../docs/GAMES.md ../docs/LANGUES.md ../docs/TRIAL-EDITION.md   # ≥ 1 chacun
grep -c 'W11' ../docs/HANDOFF.md   # ≥ 1
```
Observable : aucun (docs).

## Cas limites
W5 non fusionné ⇒ `shop`/`tokens_spend` ajoutés quand même (liste close, inoffensif) et notés « réservés » dans `TELEMETRY.md`. w11-04 déjà fusionné ⇒ ne pas dupliquer `langues`.

## À ne pas faire
Pas de secret, pas de capture d'écran dans `HANDOFF.md`, pas de réécriture des conceptions. Ne pas toucher au code hors `Telemetry.kt` et son test.

## Rapport
`STATUT`, liste des fichiers touchés avec le nombre de lignes changées, résultat du test cœur.
