# ux-05 — CastBridge (téléphone) : relier / changer de TV sans impasse (« Saisir le code à la place », « Mes TV », diagnostics)
<!-- routage Opus 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT (gel : autorisé, navigation et textes ; ne touche pas `PairFlow`/`LinkDriver`)
> **Groupe : UX-a** · prérequis : `claude/ux-ergonomie` fusionné · ordre : **avant w11-07** (qui remplace la puce par `TvChip` et la feuille « Connexion ») ou relu par lui ; jamais en parallèle de w7-19 · porte : `cd android && bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline :core:test --tests 'castbridge.core.ux.PairNavTest' --tests 'castbridge.core.journey.*' :sender:compileDebugKotlin`
> **Jauge : ≈ 150 k jetons entrée / 12 k sortie** (effort S, ≈ 0,5-1 j) · audit Opus : non

Conception : `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md` § 1 (parcours 7 et 8), § 3 rang 14. Branche `claude/sonnet-ux-05`. Rapport : `docs/agent-reports/sonnet-ux-05.md`.

## Objectif
« Saisir le code de la TV à la place » ouvre **vraiment** la saisie du code ; toute la ligne d'une TV dans « Mes TV » la rend « TV par défaut » ; « Copier le rapport » confirme ; les entrées de diagnostic portent un nom unique et cohérent.

## Pourquoi (preuves)
- `S/TvPairScreen.kt:228` : « Saisir le code de la TV à la place » appelle seulement `onClose` ; ouvert depuis « Mes TV » ou la ligne d'état (assistant fermé), il ramène à l'accueil au lieu de la saisie (impasse).
- `S/TvPairScreen.kt:300-303` : seul le petit bouton radio est touchable alors que le texte dit « Toucher pour en faire la TV par défaut ».
- `S/TvPairScreen.kt:131-134` : « Copier le rapport » sans retour ; `:71,192,248` lancements d'activité avalés.
- Diagnostics : « Diagnostic » (`:93`), « Diagnostic Bluetooth » (`:306`, même dialogue), « Copier le diagnostic (sans secret) » (`S/MyTvActivity.kt:117`), « Copier le diagnostic » (`S/BtRoutesScreen.kt:140`) ; « Mes TV » (`S/TvPairScreen.kt:92`) à côté d'écrans « Ma TV » (`S/MyTvActivity.kt:49`, `S/RemoteScreen.kt:323`).

## Fichiers possédés
- Nouveau : `C/ux/PairNav.kt` (libellés + décision « où aller » après « Saisir le code »), `CT/ux/PairNavTest.kt`.
- Modifié : `S/TvPairScreen.kt`, `S/TvHome.kt` (seulement le rappel `AddTvFlow(onClose…)` pour passer `TvHomeRequest.ENTER_PIN` ; zone disjointe de `claude/ux-ergonomie`, relire avant).

## Étapes
1. Rouge d'abord : `PairNav.afterEnterCodeChoice(fromWizard: Boolean)` ⇒ `ENTER_PIN` (jamais `HOME`) ; libellés : « Dépannage de la liaison » (une seule entrée visible par écran), « Copier le rapport (sans secret) » partout ; confirmation « Rapport copié ».
2. `AddTvFlow` : un rappel `onEnterCode` qui met `TvHomeRequest.pending = ENTER_PIN` (l'assistant existant s'ouvre sur la saisie).
3. « Mes TV » : `Modifier.selectable` sur toute la ligne.
4. `Toast`/texte « Rapport copié » ; lancements d'activité : message « Impossible d'ouvrir <écran> ».

## Critères d'acceptation
`:core:test` complet vert (P-01…P-07 verts) ; `:sender:compileDebugKotlin` vert ; aucune entrée « Diagnostic » en double sur un même écran.

## Hors périmètre
`PairFlow`, `LinkDriver`, la puce de liaison (w11-07), `RepairScreen` (w7-19).
