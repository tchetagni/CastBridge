STATUT: TERMINÉ — CODE LIVRÉ, VALIDATION MATÉRIEL EN ATTENTE

# À Claude, coordinateur CastBridge

Demande directe du propriétaire le 2026-10-29 : refaire l’ergonomie, commencer par la TV, terminer par le téléphone et prévenir Claude. Périmètre livré : les accueils TV et téléphone, pas une refonte de tous les écrans secondaires.

- 2026-10-29 | TV d’abord : tuiles directes et groupes harmonisés à 208 × 176 dp (groupes auparavant 236 × 232), titres 23 sp, en-tête resserré ; aide contextuelle fixe pour les touches ; première connexion reformulée avec le nom du groupe.
- 2026-10-29 | TV : callbacks de description composés avec `TvStyle.focusZoom` au lieu de remplacer son écouteur ; grille alignée sur le respect du mode économe existant.
- 2026-10-29 | Téléphone ensuite : lecture en cours puis actions et reprise avant le classement des séries ; cartes à une colonne sous 360 dp disponibles ou avec police > 120 %, deux colonnes de même hauteur sinon ; icônes suivant la couleur du thème, cartes Material cliquables.
- 2026-10-29 | Validation : `:receiver:compileDebugKotlin` et `:sender:compileDebugKotlin` OK ; 78 tests, 0 échec (HomeGroupsTest 30, UiTextsTest 20, HarnessSmokeTest 18, StoreJourneyTest 10) ; `git diff --check` OK. Aucun test ajouté pour ces changements de présentation.
- 2026-10-29 | Limites : aucun appareil ADB connecté, aucun rendu réel validé. Les chemins `tools/smoke` et `core/lint` cités dans COORDINATION.md ne sont pas présents dans cette base ; le filtre `castbridge.core.lint.*` n’a exécuté aucun test. Pas de rapport F PASS, donc aucune fusion d’intégration effectuée.
- 2026-10-29 | Base : `8d2436e5` (TV 0.14.59), branche de livraison `claude/ergonomie-tv-phone-2026-10-29`. Cette base récente conserve la navigation DROITE vers la pastille d’état, contrairement à une reprise d’une ancienne intégration.
- 2026-10-29 | Livraison source uniquement : versions inchangées, aucun APK distribué ni installé. Le coordinateur décide de l’intégration et de la version après validation.

## Vérifications à faire avant publication

1. TV 720p : titres longs, lisibilité à distance, aide inférieure sans rognage ; vérifier aussi le profil enfant et l’accueil vide.
2. Télécommande : parcourir les groupes, ouvrir/fermer avec OK/RETOUR, revenir d’une vidéo, atteindre la pastille avec DROITE depuis « Prêt à recevoir » ; vérifier le focus pendant un rafraîchissement.
3. Téléphone : portrait étroit, paysage et police 130–200 % ; toutes les cartes doivent rester lisibles et entièrement touchables.
4. Vérifier copie/déplacement, télécommande, bibliothèque, lecture en cours, file d’envoi et classement annulable avec une vraie TV.

Le message est transmis par cette branche et ce rapport, conformément à docs/COORDINATION.md. La lecture par Claude n’est pas confirmée.
