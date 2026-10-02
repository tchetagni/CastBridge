# w4-09 — Lots, locations et balayage en mode réduit ; liste de contrôle TV

**Vague 4b · Effort S (≈ 0,5-1 j) · Statut PRÊT (après w4-07).** Conception : `docs/coordination/DESIGN-W4-MODE-DEGRADE.md` § 2 (tableau), § 3 (dernier point), § 5. Branche `claude/sonnet-w4-09`. Rapport : `docs/agent-reports/sonnet-w4-09.md`.

## Objectif
En mode réduit : un lot **acheté** n'est jamais balayé à la fin d'une location qui le couvrait aussi ; une location **en cours** continue jusqu'à sa propre fin ; aucune **nouvelle** location ne s'installe ; `EditionPolicy.reconcile` explique en français ce qui redevient « essai » (abonnement, tout ouvert) sans rien supprimer sans prévenir ; la campagne TV (w3-14) reçoit les étapes du mode réduit.

## Pourquoi (preuves)
- `C/lots/OwnedLots.kt:14-18` : `TvGate.evaluate(activations, emptyList(), nowMs).access` ⇒ avec les droits durables de w4-07, `purchased` est non vide en mode réduit (à **tester**, pas supposer).
- `C/lots/RentalSweeper.kt:36,80-85` (`owned`, `keptBecauseOwned`) ; `C/lots/RentalEngine.kt:107-132` (contrats indépendants de la clé) ; `C/lots/RentalApi.kt:27-48` (`/api/rental/install` : fermé par la garde de route en mode réduit, w4-08 ; ici, une **défense en profondeur** : refuser aussi dans l'API si l'appelant le demande via un drapeau injecté).
- `C/lots/EditionPolicy.kt` (`reconcile`, message « repasse en version d'essai »).
- `docs/TEST-CAMPAIGN.md` (w3-14) : liste de 40 étapes sans le mode réduit.

## Fichiers possédés
`C/lots/OwnedLots.kt`, `C/lots/EditionPolicy.kt`, `C/lots/RentalApi.kt` (paramètre additif `allowInstall: () -> Boolean = { true }`), `C/lots/RentalSweeper.kt` (commentaires + un garde-fou si nécessaire), tests `CT/lots/OwnedLotsDegradedTest.kt` (nouveau), `CT/lots/EditionTest.kt`, `CT/lots/RentalApiTest.kt`, `docs/TEST-CAMPAIGN.md` (nouvelle section « Mode réduit », 6-8 étapes). **Hors zone** : `C/owner/**` (w4-07), `R/**` (w4-08), `RentalLedger.kt`, `RentalKeys.kt`, `TvLotStore.kt`.

## Étapes
1. `OwnedLotsDegradedTest` : activation de production terminée portant `purchase|p|classe-cm2|…` + location de `classe-cm2` échue ⇒ `OwnedLots.of` contient les lots de `classe-cm2` **si** un catalogue est donné (w3-03) et au moins `extraLots` sinon ; le balayage (`RentalSweeper` avec `owned`) rend `keptBecauseOwned` et ne supprime pas le lot ; même scénario sans achat ⇒ supprimé.
2. `RentalApi(…, allowInstall)` : `/api/rental/install` ⇒ 403 « Mode réduit : renouvelez la clé pour installer une location » quand `allowInstall()` est faux ; `GET /api/rental` et `/api/rental/sweep` restent ouverts ; test.
3. `EditionPolicy.reconcile` : message dédié quand la rétrogradation vient d'une clé terminée : « Votre clé est terminée : « Abonnement X » repasse en version d'essai ; vos achats restent disponibles. Renouvelez la clé pour le retrouver. » (paramètre additif `keyEnded: Boolean = false`) ; test.
4. `docs/TEST-CAMPAIGN.md` : étapes « clé de production 1 j → attente/avance d'horloge → badge CLÉ TERMINÉE → bibliothèque lisible → streaming OK → upload 403 → location en cours toujours lisible → location échue balayée sauf lot acheté → nouvelle clé → achats conservés ».

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.*'   # vert
grep -n 'allowInstall' android/core/src/main/kotlin/castbridge/core/lots/RentalApi.kt   # ≥ 2
grep -n 'Mode réduit' docs/TEST-CAMPAIGN.md   # ≥ 1
```

## Cas limites
- Location **suspendue** (horloge douteuse) pendant le mode réduit : inchangé (`SUSPENDED`), rien supprimé.
- Lot acheté via bouquet nommé sans catalogue sur la TV : limite w3-03 ; si w3-03 n'est pas fusionné, le test avec catalogue est écrit **et** marqué `@Ignore` avec la raison, le test sans catalogue passe.

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas toucher `TvGate` ; ne pas modifier le balayage au-delà d'un garde-fou commenté ; textes en français.

## Rapport
`STATUT`, résultats des tests, dépendance w3-03 constatée ou non.
