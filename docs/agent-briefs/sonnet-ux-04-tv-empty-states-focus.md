# ux-04 — CastBridge-TV : états vides honnêtes (cause + action) et toujours une vue focalisable au D-pad (Bibliothèque, Langues, Téléchargements)
<!-- routage Opus 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus (module TV) · statut : PRÊT **sur décision du coordinateur** (gel : défauts prouvés « écran sans focus », « erreur montrée comme vide »)
> **Groupe : UX-b** · prérequis : `claude/ux-ergonomie` fusionné · ordre : après ux-03 si les deux touchent `R/HomeScreen.kt` (étape 4 seulement) ; jamais en parallèle de w11-03 (`R/HomeScreen.kt`) · porte : `cd android && bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline :core:test --tests 'castbridge.core.ux.TvEmptyStatesTest' :receiver:compileDebugKotlin`
> **Jauge : ≈ 220 k jetons entrée / 15 k sortie** (effort M, ≈ 1 j) · audit Opus : oui (échantillon)

Conception : `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md` § 2 (pièges de focus, états vides, actions muettes), § 3 rang 13. Branche `claude/sonnet-ux-04`. Rapport : `docs/agent-reports/sonnet-ux-04.md`.

## Objectif
Aucun écran de CastBridge-TV sans vue focalisable ; chaque écran vide dit **pourquoi** et **quoi faire** ; une erreur de lecture n'est jamais affichée comme « vide ».

## Pourquoi (preuves)
- `R/LibraryScreen.kt:245` : la ligne vide « Aucun fichier sur la TV… » (`:136`) n'est pas focalisable ⇒ écran sans focus.
- `R/LanguesActivity.kt:91-103` : sans lot ni Internet, aucune vue focalisable ; `:146` « La mise à jour a échoué : réessayez plus tard. » sans cause ; `:211` audio en `runCatching{}.getOrNull()` sans message.
- `R/DownloadsActivity.kt:90` « Le gestionnaire de téléchargements n'est pas démarré. » sans action ; `:186` « Ajouter un lien » ne fait rien si `dm == null` ; `:204` « J'ai compris » n'ouvre pas ensuite la saisie ; `:92-95` erreur de liste montrée comme vide.
- `R/HomeScreen.kt:132`, `R/LibraryScreen.kt:122` : `runCatching { api.items() }.getOrDefault(emptyList())` ⇒ « Aucun fichier » trompeur.
- Le texte vide de Langues a déjà été corrigé dans le cœur par `claude/ux-ergonomie` (`LangCatalog.EMPTY_MESSAGE` donne le chemin du téléphone) : le réutiliser.

## Fichiers possédés
- Nouveau : `C/ux/TvEmptyStates.kt`, `CT/ux/TvEmptyStatesTest.kt`.
- Modifié : `R/LibraryScreen.kt`, `R/LanguesActivity.kt`, `R/DownloadsActivity.kt`, et `R/HomeScreen.kt` (`reload` seulement : distinguer erreur et vide).

## Étapes
1. Rouge d'abord : `TvEmptyStates.library(error: String?, count: Int)`, `.langues(hasInternet, hasPacks, updateError: String?)`, `.downloads(managerUp: Boolean, error: String?, count: Int)` renvoient `(texte, action?)` : ex. « Lecture de la bibliothèque impossible : <cause>. Touchez OK pour réessayer. » ; Langues sans Internet ⇒ `EMPTY_MESSAGE` + action « Retour » focalisable ; mise à jour échouée ⇒ cause + « Réessayer ».
2. Chaque état vide porte **un bouton focalisable** (OK = action ; à défaut « Retour à l'accueil ») ; `requestFocus()` dessus à l'affichage.
3. Téléchargements : « Ajouter un lien » sans gestionnaire ⇒ « Le gestionnaire de téléchargements démarre… réessayez dans un instant » puis tentative de démarrage ; « J'ai compris » ouvre la saisie.
4. `HomeScreen.reload` / `LibraryScreen` : erreur ≠ vide (texte de l'étape 1).

## Critères d'acceptation
`:core:test` complet vert ; `:receiver:compileDebugKotlin` vert ; P-33 inchangé ; **H** : sur la TV de référence, chaque écran vide reçoit le focus et OK fait l'action annoncée.

## Hors périmètre
La forme de l'accueil (W11), le contenu des leçons, la boutique (W17).
