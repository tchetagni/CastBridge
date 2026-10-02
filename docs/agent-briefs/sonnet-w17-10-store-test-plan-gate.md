# w17-10 — Plan de test humain `docs/test-plans/STORE-PILOT.md` (10 points sur la TV de référence et le téléphone) et porte `tools/agents/gate-w17.sh`

<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : aucune · statut : PRÊT (après w17-05 ; pendant le gel : docs et script seulement)
> **Groupe : W17b-2** (vague W17b, campagne) · prérequis : w17-01…05 · porte : `bash -n tools/agents/gate-w17.sh && grep -c "^| " docs/test-plans/STORE-PILOT.md`
> **Jauge : ≈ 100 k jetons entrée / 10 k sortie** (effort S, ≈ 0,3 j) · audit Opus : non

**Vague 17b · Effort S · Modèle : haiku · Statut PRÊT.** Conception : DESIGN-W17 § 7.5, § 3.3, § 3.4, § 4.2. Branche `claude/sonnet-w17-10`. Rapport : `docs/agent-reports/sonnet-w17-10.md`. Patrons : `docs/test-plans/CHECKLIST-HUMAINE-LIVRAISON.md` (12 points, W14), `tools/agents/gate-w16.sh` (w16-14, **si présent** ; sinon même forme que les autres `gate-*.sh` de `tools/agents/`). La TV du propriétaire n'est **jamais** la cible d'un script (D-W14-3).

## Fichiers possédés
Nouveaux `docs/test-plans/STORE-PILOT.md`, `tools/agents/gate-w17.sh` ; modifié `docs/test-plans/README.md` (**une** ligne). **Hors zone** : tout le reste.

## `STORE-PILOT.md` (table : n°, appareil, pré-condition, geste, résultat attendu observable, OK/KO)
1. Téléphone : drapeau éteint ⇒ barre du haut « Activer la TV · Locations », aucune Boutique. 2. Ordre signé `flag.set store.enabled=1` reçu ⇒ « Boutique » à la place de « Locations » ; TV : tuile « Boutique » après « Langues ». 3. Téléphone, Internet : « Actualiser » ⇒ « Catalogue du JJ/MM », rayons Apprendre / Langues / Quiz. 4. Contact Wi-Fi ⇒ TV : même « Catalogue du JJ/MM », mêmes articles, mêmes états. 5. Téléphone : « Envoyer à la TV » sur CM2 ⇒ TV : « Sur cette TV » (après livraison). 6. TV : « Louer » CM2 · « 12 heures d'utilisation » ⇒ écran « Demande enregistrée » + code lisible à 3 m ; téléphone : notification « La TV demande … » ⇒ Confirmer ⇒ (bureau W16 : `louer.py --demande`) ⇒ location livrée ⇒ TV « Loué · il vous reste 12 h d'utilisation ». 7. TV d'essai (clé d'essai de TEST sur l'émulateur, **pas** la TV de référence) : vitrine visible, « Version complète nécessaire ». 8. Profil enfant actif sur la TV : « Louer » ⇒ « Demandez à un parent » ; le téléphone ne reçoit rien. 9. Téléphone éteint 24 h : la TV garde la vitrine, la demande reste « en cours » ; retour du téléphone ⇒ relevée. 10. Mémoire : `dumpsys meminfo` avant/pendant la Boutique (propriétaire) : delta < 1,5 Mo ; ouverture < 300 ms ressentie.

## `gate-w17.sh`
`set -euo pipefail` ; `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.store.*' --tests 'castbridge.core.journey.Store*' --tests 'castbridge.core.lint.*'` ; `python3 tools/activation/verify_vectors.py --only store` ; `python3 tools/tests/test_routes.py` ; `grep -rn 'XAF' android/core/src/main/kotlin/castbridge/core/store && exit 1 || true` ; sortie « GATE W17 : VERT » / code 1.

## Critères d'acceptation
`bash -n` vert ; `STORE-PILOT.md` : 10 lignes de table + en-tête (date, versions, qui exécute) ; aucun `adb` dans le script ; `README.md` : une ligne.

## À ne pas faire
Ne pas lancer la porte sur la TV réelle ; ne pas modifier d'autre plan de test.

## Rapport
`STATUT`, fichiers, sortie de `bash -n`.
