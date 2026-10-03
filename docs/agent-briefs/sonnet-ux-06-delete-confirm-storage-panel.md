# ux-06 — CastBridge (téléphone) : plus de suppression sans confirmation sur la TV, panneau de stockage jamais masqué en silence
<!-- routage Opus 2026-10-03 -->
> **Modèle : haiku** · escalade : sonnet si une ancre « avant » est introuvable · statut : PRÊT (gel : défaut prouvé « suppression destructive sans confirmation »)
> **Groupe : UX-a** · prérequis : `claude/ux-ergonomie` fusionné · porte : `cd android && bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline :core:test --tests 'castbridge.core.ux.StorageNoticesTest' :sender:compileDebugKotlin`
> **Jauge : ≈ 70 k jetons entrée / 6 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

Conception : `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md` § 1 (parcours 10), § 3 rang 15, D-UX-6. Branche `claude/sonnet-ux-06`. Rapport : `docs/agent-reports/sonnet-ux-06.md`.

## Objectif
La liste « Sur la TV » de l'écran Avancé demande une confirmation avant de supprimer (comme la bibliothèque de la TV) ; le panneau « Stockage » dit pourquoi il n'est pas affiché au lieu de disparaître.

## Pourquoi (preuves)
- `S/TvScreen.kt:271` : suppression **immédiate** d'un fichier de la TV (la bibliothèque, elle, confirme : `S/TvLibraryScreen.kt:182,196`).
- `S/TvHub.kt:168` (`sys ?: return`) et `S/StoragePanel.kt:82` (`st ?: return`) : le panneau disparaît sans un mot quand la lecture échoue ; `S/StoragePanel.kt:68` erreurs de rafraîchissement avalées.

## Fichiers possédés
- Nouveau : `C/ux/StorageNotices.kt`, `CT/ux/StorageNoticesTest.kt`.
- Modifié : `S/TvScreen.kt` (dialogue de confirmation), `S/StoragePanel.kt`, `S/TvHub.kt` (ligne `:168` seulement).

## Étapes (avant / après)
1. Rouge d'abord : `StorageNotices.confirmDelete(name)` = « Supprimer « <titre> » de la TV ? Ce fichier ne pourra pas être récupéré. » ; `StorageNotices.unavailable(cause: String?)` = « Stockage de la TV illisible pour l'instant<: cause>. Touchez « Réessayer ». ».
2. `TvScreen.kt:271` : remplacer l'appel direct par un `AlertDialog` (« Supprimer » / « Annuler ») qui appelle la même fonction.
3. `StoragePanel.kt:82` et `TvHub.kt:168` : au lieu de `return`, afficher `unavailable(...)` + bouton « Réessayer ».

## Critères d'acceptation
`:core:test` complet vert ; `:sender:compileDebugKotlin` vert ; aucune suppression sans dialogue dans `S/` (`grep -n "delete(" S/TvScreen.kt` ⇒ appel dans le bouton de confirmation seulement).

## Hors périmètre
« Libérer de la place » (D-UX-6, cahier à écrire après décision), suppression multiple.
