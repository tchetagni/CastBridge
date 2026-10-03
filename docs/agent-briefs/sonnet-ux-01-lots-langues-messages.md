# ux-01 — CastBridge (téléphone) : « Données hors ligne » et Langues — le résultat d'une action se lit à côté du bouton, l'erreur en rouge, le chemin PIN expliqué
<!-- routage Opus 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus si `LotsRuntime` doit changer de comportement · statut : PRÊT (gel : autorisé, texte + placement + fonction pure)
> **Groupe : UX-a** (vague UX, téléphone) · prérequis : `claude/ux-ergonomie` fusionné (`C/ux/UiTexts.kt`) · ordre : **avant ou après w17-07, jamais en parallèle** (w17-07 ajoute un bouton dans `S/LotsScreen.kt`) · porte : `cd android && bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline :core:test --tests 'castbridge.core.ux.LotsNoticesTest' :sender:compileDebugKotlin`
> **Jauge : ≈ 120 k jetons entrée / 10 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

Conception : `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md` § 1 (parcours 5 et 11), § 3 rang 11. Branche `claude/sonnet-ux-01` depuis `origin/integration/agents`. Rapport : `docs/agent-reports/sonnet-ux-01.md`. Textes en français ; dire « CastBridge » / « CastBridge-TV ».

## Objectif
Dans l'écran « Données hors ligne » (`S/LotsScreen.kt`), le message de résultat de chaque action (« Télécharger mes leçons de langue », « Télécharger et envoyer », « Envoyer à la TV », « Retirer de la TV ») apparaît **près de l'action** et **en rouge seulement si c'est un échec** ; « Retirer … de la TV » dit son résultat ; un usager relié par **code PIN** (sans TV de confiance) lit pourquoi l'envoi de lots ne part pas et quoi faire.

## Pourquoi (preuves, tête `c8be0e9`)
- `S/LotsScreen.kt:69` : `message` dessiné **en haut** de l'écran, couleur primaire même pour une erreur ; les boutons Langues sont loin dessous (`:176-194`) ⇒ résultat invisible sans remonter.
- `S/LotsScreen.kt:126` : « Retirer … de la TV » (`dropFromTv`) ne dit rien.
- `S/LotsRuntime.kt:202,243` : la livraison exige une TV de confiance (`tvId()`) ; un usager PIN lit « Aucune TV enregistrée » alors que l'onglet « CastBridge TV » marche.
- Parcours 5 : 9 touches + défilement (onglet « Apprendre » › « Données hors ligne » › 3 sélecteurs × 2 › « Télécharger mes leçons de langue »).

## Fichiers possédés
- Nouveau : `C/ux/LotsNotices.kt` (pur) et `CT/ux/LotsNoticesTest.kt`.
- Modifié : `S/LotsScreen.kt` (affichage du message seulement : variable `message` → `UiNotice?`, emplacement ; `:126` résultat), `S/LotsRuntime.kt` (le **texte** renvoyé à `:243` seulement ; aucune règle de livraison changée).
- Hors zone : `C/lots/**` (sauf lecture), `S/MainActivity.kt`, tout `R/`.

## Étapes
1. Test rouge d'abord : `LotsNoticesTest` attend `LotsNotices.classify(text: String?): UiNotice?` (null ⇒ null ; un texte d'échec connu de `LotsRuntime` — relever ceux de `S/LotsRuntime.kt:111,151-154,173,182,230,243-264` — ⇒ `error = true` ; les autres ⇒ `false`) et `LotsNotices.pinOnly()` (« Votre TV est reliée par code : l'envoi des leçons passe par une TV de confiance. Ouvrez « CastBridge TV » › « Ajouter ma TV » (Bluetooth, sans code), puis réessayez. »). Corps factices ⇒ échec **par assertion** ; puis implémenter.
2. `LotsScreen` : garder un `UiNotice?` ; l'afficher **sous la section qui l'a produit** (en-tête de section Langues pour les actions Langues, sous « Vos données » pour les cartes) avec la couleur `error` seulement si `error` ; un ✕ pour le fermer.
3. `dropFromTv` : afficher « <titre> retiré de la TV » / « Impossible de retirer <titre> : <raison> ».
4. `LotsRuntime.kt:243` : si un code PIN est connu (`HomeTv(ctx).name != null`) et aucune TV de confiance, renvoyer `LotsNotices.pinOnly()`.

## Critères d'acceptation
- `:core:test` complet vert (chien de garde 60 s/test), `:sender:compileDebugKotlin` vert.
- Parcours P-37 (Langues) de `docs/test-plans/PARCOURS-CRITIQUES.md` inchangé ; ajouter une ligne « résultat visible sans défiler » à P-37.
- Aucun texte d'erreur en couleur primaire dans `LotsScreen`.

## Hors périmètre
La vitrine (W17), le raccourci direct vers la section Langues (W11 w11-09 : « Apprendre › Langues »), tout changement de règle de livraison.
