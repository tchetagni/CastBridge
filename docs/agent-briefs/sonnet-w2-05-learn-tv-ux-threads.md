# w2-05 — « Apprendre » sur la TV : lisible à 3 m, essai visible, fin de location annoncée, threads propres

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W2-A** (vague W2) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Learn*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 2 · Effort M (≈ 2 j) · Statut PRÊT.** Branche `claude/sonnet-w2-05`. Rapport : `docs/agent-reports/sonnet-w2-05.md`.

## Objectif
1. Tailles de texte : sous-titres ≥ 24 px, corps ≥ 26 px à 720p (1 unité = 1 px, `R/LearnViews.kt:40,44`).
2. Textes : « Contenu de base — leçons complètes à recevoir du téléphone » → « 2 leçons d'aperçu. Pour toute la classe : ouvrez CastBridge sur le téléphone > Données hors ligne > Télécharger, puis approchez le téléphone de la TV. » ; chemin `Android/data/...` déplacé sous un bouton « Détails techniques » ; plus de « · done » ni de « lot ».
3. En-tête d'Apprendre : « Essai : 3 h 20 restantes » quand la fenêtre de 12 h est active ; notice à 10 min restantes.
4. La notice « Location terminée… » (`C/lots/RentalEngine.kt:163`) atteint l'écran Apprendre, et une fin d'essai/location pendant une leçon affiche un plein écran « Temps d'essai terminé » avec les étapes de renouvellement.
5. Threads : plus de `Thread{}` à la minute, plus d'attente active dans un gestionnaire HTTP, handlers nettoyés en `onDestroy`.

## Pourquoi (preuves)
- `R/LearnViews.kt:84` (19f), `R/LearnActivity.kt:575,579,611` (19f), `R/LearnReader.kt:75` (22f) : sous la taille utile à 3 m.
- `C/learn/BaseContent.kt:26` et `R/LearnActivity.kt:517,579` : deux formulations techniques ; `R/LearnActivity.kt:573-575` : chemin de stockage affiché ; `:504` « · done ».
- `R/LearnActivity.kt:90` : `Thread { RentalHub.meterOneMinute(...) }.start()` chaque minute ; `R/LearnHub.kt:141-142` `Thread.sleep(100)` jusqu'à 3 s et `:148-149` `wait(2000)` dans un fil HTTP (pool borné) ; `LearnActivity.onDestroy` (`:93`) ne retire pas `restoreHint` (`:168`) ni le tick de `LearnReader` (`:363`).
- `R/TvService.kt:572` `notice()` n'affiche que si l'accueil est visible ; `C/lots/RentalSweeper.kt:23,56-64` produit `notices` ; pendant une leçon le balayage supprime les fichiers (`lessonActive`, report 15 min) et la grille montre « Rien d'installé ici » (`R/LearnActivity.kt:539`).
- `C/owner/KeyBadge.kt:49` « Lots locatifs : 3 h 20 min d'essai restantes » (14 sp) = seul compteur.
- Audit : UX-6, UX-7, OP-6 (T1, T3, T6).

## Fichiers possédés
`R/LearnActivity.kt`, `R/LearnViews.kt`, `R/LearnReader.kt`, `R/LearnHub.kt`, `C/learn/BaseContent.kt` (**chaînes** seulement), `C/lots/RentalEngine.kt` (**chaînes** seulement), `R/RentalHub.kt` (**hors** `ActivationInstallApi`, qui appartient à w2-01 : ajouter seulement un `notices` listener), `C/lots/RentalSweeper.kt` (ajout d'un callback `onNotice` optionnel, défaut no-op). **Hors zone** : `TvService.kt` (w2-07), `KeyBadge*` (w2-04), `LearnApi.kt`, `TrialPolicy`.

## Étapes
1. `LearnViews` : constantes `SUB_MIN = 24f`, `BODY_MIN = 26f` appliquées via `max()` partout où `19f`/`22f` sont utilisés (garder `scale` du mode classe).
2. Textes : `BaseContent.LABEL`/`HINT` (cœur) et `LearnActivity` ; `LearnReader` : chrome toujours en français (les textes de la fiche gardent la langue du pack) ; contrôler avec `grep -n '"[^"]*\bdone\b' R/LearnActivity.kt`.
3. Compteur : `RentalHub.statuses(ctx)` (existant) → `RentalEngine.remainingText` (existant, « 5 h ») : afficher dans l'en-tête de `LearnActivity` quand un contrat `TRIAL_PRODUCT` est utilisable ; minuterie de 30 s partagée avec le badge si possible (sinon `main.postDelayed`, retiré en `onDestroy`) ; à ≤ 10 min, `notice` une fois.
4. Notices : `RentalSweeper.sweep(..., onNotice)` → `RentalHub` relaie vers un `listeners: CopyOnWriteArraySet<(String)->Unit>` ; `LearnActivity` s'inscrit en `onStart`/se désinscrit en `onStop` et affiche une bannière ; si la leçon en cours appartient à un lot supprimé (comparer `removed` au `LotId` du pack ouvert), afficher l'écran plein « Temps d'essai terminé. Pour continuer : CastBridge > Activer la TV > Demander la clé complète. » avec un bouton « Retour à l'accueil ».
5. Threads : `meterTick` → `LearnHub.io.execute { RentalHub.meterOneMinute(...) }` ; `LearnHub.kt:141-149` → `CountDownLatch` (ou retour 202 immédiat « écran en cours d'ouverture ») ; `LearnActivity.onDestroy` : `main.removeCallbacksAndMessages(null)` ; `LearnReader` : le tick est retiré par son propriétaire.
6. Accessibilité : `contentDescription` sur les tuiles à glyphe (`LearnViews.kt:82`).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.*' --tests 'castbridge.core.*Learn*'   # vert
grep -n 'Thread {' android/receiver/src/main/kotlin/castbridge/receiver/LearnActivity.kt android/receiver/src/main/kotlin/castbridge/receiver/LearnHub.kt   # 0 hit
grep -n 'Thread.sleep' android/receiver/src/main/kotlin/castbridge/receiver/LearnHub.kt   # 0 hit
grep -n '19f' android/receiver/src/main/kotlin/castbridge/receiver/LearnViews.kt android/receiver/src/main/kotlin/castbridge/receiver/LearnActivity.kt   # 0 hit (ou sous max())
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (si SDK)
```
Observable (campagne, étapes 16-17, 21, 27-28, 34) : en-tête « Essai : … restantes » visible dans Apprendre ; à la fin de la fenêtre, plein écran explicatif au lieu de tuiles vides ; D-pad : focus toujours visible.

## Cas limites
- Mode classe (`scale` > 1) : ne pas doubler les planchers.
- Packs anglophones : le chrome reste français, le contenu reste anglais.
- Deux activités Apprendre ne coexistent pas ; un seul listener à la fois.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas changer les règles de balayage (`RentalSweeper` : seulement un callback) ; textes en français ; « CastBridge ».

## Rapport
`STATUT`, captures émulateur 1280×720 avant/après si possible, sorties des commandes.
