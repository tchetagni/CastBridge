# ux-07 — CastBridge (téléphone) : la feuille « Lire sur la TV » ne grise plus « Copier sur la TV et lire » / « Déplacer » pendant un envoi si la file les accepte
<!-- routage Opus 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus (comportement de la file R-09) · statut : PRÊT **après vérification** (étape 1 ; si la file n'accepte pas, le cahier se réduit à un texte exact)
> **Groupe : UX-a** · prérequis : `claude/ux-ergonomie` fusionné (`SendWays`) · porte : `cd android && bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline :core:test --tests 'castbridge.core.ux.*' --tests 'castbridge.core.CopyQueue*' --tests 'castbridge.core.CopyAndPlay*' :sender:compileDebugKotlin`
> **Jauge : ≈ 160 k jetons entrée / 10 k sortie** (effort S, ≈ 0,5-1 j) · audit Opus : oui

Conception : `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md` § 3 rang 16, § 9. Branche `claude/sonnet-ux-07`. Rapport : `docs/agent-reports/sonnet-ux-07.md`.

## Objectif
Pendant un envoi, la feuille dit la vérité : soit le nouvel envoi **entre dans la file** (bouton actif, « Ajouté à la file : n° 2 »), soit il est refusé avec la raison exacte. Aujourd'hui elle grise et annonce « copie et déplacement reviendront à la fin » alors que `CastSession` met en file.

## Pourquoi (preuves)
- `S/player/CastSheet.kt:50,105,109` : `busy` grise COPY/MOVE ; `S/player/CastSession.kt:179` : la copie passe par `TransferQueue` (à **vérifier** : file pour la copie simple, lecture pendant la copie `playOnTv` = un seul « copier et lire » à la fois ?).
- R-09 (`docs/REGRESSIONS.md`) : une seule file, un seul chemin d'envoi.

## Fichiers possédés
- Nouveau : `C/ux/CastSheetState.kt`, `CT/ux/CastSheetStateTest.kt`.
- Modifié : `S/player/CastSheet.kt` (états des boutons et ligne d'explication seulement).

## Étapes
1. Lire `CastSession.copy` et `TransferQueue.add` : établir dans le rapport, preuves à l'appui, si une copie demandée pendant un envoi est mise en file (et à quelle place, `QueueRules.runOrder` met « copier et lire » devant).
2. Rouge d'abord : `CastSheetState.button(action, uploadBusy, queueAccepts: Boolean)` ⇒ actif + note `QueueRules.admitted`-like (« Ajouté à la file après « X » ») quand la file accepte ; sinon grisé + raison exacte.
3. Câbler.

## Critères d'acceptation
`:core:test` complet vert (P-38, P-39 verts) ; `:sender:compileDebugKotlin` vert ; aucune phrase « reviendront à la fin » si le bouton est actif.

## Hors périmètre
La file elle-même (R-09, gelée), l'ordre des boutons (fait par `claude/ux-ergonomie`).
