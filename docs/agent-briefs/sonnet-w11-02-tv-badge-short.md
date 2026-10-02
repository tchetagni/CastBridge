# w11-02 — CastBridge-TV : badge de clé court (≤ 5 mots), 16 sp, en haut à gauche, discret pendant une vidéo
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet si une ancre « avant » est introuvable ou si la compilation échoue deux fois · statut : PRÊT
> **Groupe : W11-a** (vague W11) · prérequis : aucun · porte : `gradle --offline :core:test --tests 'castbridge.core.owner.KeyBadgeTest'`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 11a (gain rapide) · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 4.6, § 8.2, § 9.1 (Q2) ; décision D-W11-3 (recommandation (b) retenue par défaut). Branche `claude/sonnet-w11-02`. Rapport : `docs/agent-reports/sonnet-w11-02.md`.

## Objectif
Le badge permanent reste visible sur chaque écran de CastBridge-TV (exigence conservée) mais devient **lisible et léger** : un titre et **un** complément de ≤ 4 mots, 16 sp, en haut à gauche sous le logo ; pendant une vidéo il passe à 12 sp et 50 % d'opacité. Le texte complet reste disponible (`Badge.text`) pour « À propos » et l'écran d'activation.

## Pourquoi (preuves)
- `android/core/src/main/kotlin/castbridge/core/owner/KeyBadge.kt:13` : `text = (listOf(title) + lines).joinToString("  ·  ")` ⇒ en essai jusqu'à 5 phrases (≈ 35 mots) sur une ligne.
- `android/receiver/src/main/kotlin/castbridge/receiver/KeyBadgeOverlay.kt:30-31` : `textSize = 14f`, `Gravity.TOP or Gravity.CENTER_HORIZONTAL` ⇒ sous le minimum TV de 16 sp (`TvCards.kt:34`, `Type.CAPTION = 16f`) et au-dessus du héros de l'accueil.
- `docs/coordination/RECOMMANDATIONS-FABLE-2026-10-02.md` UX-4 : « badge 14 sp et non accessible ».

## Fichiers possédés
- Modifiés : `android/core/src/main/kotlin/castbridge/core/owner/KeyBadge.kt` (ajout de `short`), `android/core/src/test/kotlin/castbridge/core/owner/KeyBadgeTest.kt` (cas ajoutés), `android/receiver/src/main/kotlin/castbridge/receiver/KeyBadgeOverlay.kt`.
- Hors zone : `ActivationActivity.kt` (utilise `badge().lines`, inchangé), `PlayerActivity.kt`, `HomeScreen.kt`, w6-14 (sa ligne « Rapports d'usage partagés… » ne va **pas** dans le badge : conception § 6.2).

## Signatures à respecter
```kotlin
// C/owner/KeyBadge.kt
data class Badge(val title: String, val lines: List<String>, val ended: Boolean = false) {
    val text: String            // inchangé
    val short: String           // NOUVEAU : "<title>" ou "<title> · <complément ≤ 4 mots>"
}
```
Compléments exacts par état : `SANS CLÉ` → « Entrez un code » ; `ACTIVATION TERMINÉE` → « Entrez un nouveau code » ; `SUPER ILLIMITÉ` → aucun ; `PRODUCTION` → « jusqu'au JJ/MM » (date la plus tardive) ou « illimitée » ; `ESSAI` → « N h restantes » / « N min restantes » (fenêtre des lots d'essai) ou « essai terminé » ou « 12 h d'essai ». Le complément est calculé dans `of(...)` à partir des mêmes données que `lines` (ne pas re-parser le texte).

## Étapes
1. `KeyBadge.of` : construire le complément en même temps que `lines`, exposer `short`. Le test vérifie, pour chaque état des cas existants de `KeyBadgeTest`, que `short.split(" ").size <= 6` (titre de 1-2 mots + ≤ 4) et que `short` commence par `title`.
2. `KeyBadgeOverlay.attach` : `textSize = 16f` ; `Gravity.TOP or Gravity.START`, `topMargin = 64 dp` (sous le logo de 52 dp de l'accueil), `marginStart = 56 dp` (= padding de l'accueil, `HomeScreen.kt:90`) ; `alpha = 0.85f` ; `v.text = b.short` ; `contentDescription = b.text` (lecture complète) ; `importantForAccessibility` inchangé.
3. Mode vidéo : ajouter `fun KeyBadgeOverlay.setPlaying(a: Activity, playing: Boolean)` (12 sp, alpha 0,5 quand `playing`) ; **ne pas** l'appeler depuis `PlayerActivity` dans ce cahier (hors zone) : documenter l'appel attendu dans le rapport pour w11-10 (`hideScreens()` → `true`, `showHome()` → `false`).
4. Couleurs inchangées (`:43`).

## Éditions (forme mécanique Haiku)
Fichier 1 : `android/core/src/main/kotlin/castbridge/core/owner/KeyBadge.kt`.

E1 — avant (lignes 12-14) :
```kotlin
data class Badge(val title: String, val lines: List<String>, val ended: Boolean = false) {
    val text: String get() = (listOf(title) + lines).joinToString("  ·  ")
}
```
après :
```kotlin
data class Badge(val title: String, val lines: List<String>, val ended: Boolean = false, val detail: String? = null) {
    val text: String get() = (listOf(title) + lines).joinToString("  ·  ")
    /** Le badge permanent : le titre et, au plus, un complément de quatre mots. */
    val short: String get() = if (detail.isNullOrBlank()) title else "$title · $detail"
}
```
E2 — avant (ligne 25) : `        if (activations.isEmpty()) return Badge("SANS CLÉ", listOf("Entrez un code d'activation"), ended = true)`
après : `        if (activations.isEmpty()) return Badge("SANS CLÉ", listOf("Entrez un code d'activation"), ended = true, detail = "Entrez un code")`

E3 — avant (ligne 27) : `        if (counting.isEmpty()) return Badge("ACTIVATION TERMINÉE", listOf("Entrez un nouveau code valide"), ended = true)`
après : `        if (counting.isEmpty()) return Badge("ACTIVATION TERMINÉE", listOf("Entrez un nouveau code valide"), ended = true, detail = "Entrez un nouveau code")`

E4 — avant (ligne 33) :
```kotlin
        lines += if (ends.any { it == null }) "Clé illimitée" else ends.filterNotNull().maxOrNull()!!.let { "Clé valable jusqu'au ${date(it, zone)} (${left(it - nowMs)})" }
```
après :
```kotlin
        lines += if (ends.any { it == null }) "Clé illimitée" else ends.filterNotNull().maxOrNull()!!.let { "Clé valable jusqu'au ${date(it, zone)} (${left(it - nowMs)})" }
        val shortDate = if (ends.any { it == null }) "illimitée" else "jusqu'au " + DateTimeFormatter.ofPattern("dd/MM").withZone(zone).format(Instant.ofEpochMilli(ends.filterNotNull().maxOrNull()!!))
```
E5 — avant (ligne 35) : `            rights.any { it is Right.Super } -> Badge("SUPER ILLIMITÉ", listOf("Tous les droits", "Clé permanente"))`
après : `            rights.any { it is Right.Super } -> Badge("SUPER ILLIMITÉ", listOf("Tous les droits", "Clé permanente"), detail = null)`

E6 — avant (ligne 42) : `                Badge("PRODUCTION", lines)`
après : `                Badge("PRODUCTION", lines, detail = shortDate)`

E7 — avant (lignes 46-52) :
```kotlin
                lines += when {
                    w == null -> "Lots locatifs : 12 h d'essai, une seule fois"
                    w.state == RentalState.EXPIRED -> "Lots locatifs : fenêtre d'essai terminée"
                    else -> "Lots locatifs : ${minutes(w.remainingUsageMinutes ?: RentalLines.TRIAL_USAGE_MINUTES.toLong())} d'essai restantes"
                }
                lines += "Streaming, Sudoku et lots d'essai seulement · « Passer en production » pour tout débloquer"
                Badge("ESSAI", lines)
```
après :
```kotlin
                lines += when {
                    w == null -> "Lots locatifs : 12 h d'essai, une seule fois"
                    w.state == RentalState.EXPIRED -> "Lots locatifs : fenêtre d'essai terminée"
                    else -> "Lots locatifs : ${minutes(w.remainingUsageMinutes ?: RentalLines.TRIAL_USAGE_MINUTES.toLong())} d'essai restantes"
                }
                lines += "Streaming, Sudoku et lots d'essai seulement · « Passer en production » pour tout débloquer"
                val trialShort = when {
                    w == null -> "12 h d'essai"
                    w.state == RentalState.EXPIRED -> "essai terminé"
                    else -> minutes(w.remainingUsageMinutes ?: RentalLines.TRIAL_USAGE_MINUTES.toLong()) + " restantes"
                }
                Badge("ESSAI", lines, detail = trialShort)
```
Fichier 2 : `android/core/src/test/kotlin/castbridge/core/owner/KeyBadgeTest.kt` — ajouter, à la fin de la classe, un test :
```kotlin
    @Test fun shortBadgeIsAtMostSixWords() {
        val cases = listOf(KeyBadge.of(emptyList(), 0L))   // + un cas par état déjà construit dans cette classe (réutiliser ses fabriques)
        for (b in cases) { assertTrue(b.short, b.short.startsWith(b.title)); assertTrue(b.short, b.short.split(" ").filter { it != "·" }.size <= 6) }
    }
```
(l'exécutant complète `cases` avec les fabriques d'activations existantes du fichier : production, super, essai ; si aucune fabrique n'existe, le test garde le seul cas `SANS CLÉ` et le rapport le dit).

Fichier 3 : `android/receiver/src/main/kotlin/castbridge/receiver/KeyBadgeOverlay.kt`.

E8 — avant (lignes 30-31) :
```kotlin
            tag = TAG; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setPadding(26, 8, 26, 8); isFocusable = false; isClickable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            root.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 6 })
```
après :
```kotlin
            tag = TAG; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setPadding(26, 8, 26, 8); isFocusable = false; isClickable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; alpha = 0.85f
            val d = a.resources.displayMetrics.density
            root.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = (64 * d).toInt(); leftMargin = (56 * d).toInt() })
```
E9 — avant (ligne 41) : `        v.text = b.text`
après : `        v.text = b.short; v.contentDescription = b.text`

E10 — après la fonction `refresh` (ligne 44), ajouter :
```kotlin
    /** Pendant une vidéo : plus petit et plus discret ; l'accueil le remet en 16 sp. (Appelé par PlayerActivity : cahier w11-10.) */
    fun setPlaying(a: Activity, playing: Boolean) {
        val v = (a.window?.decorView as? ViewGroup)?.findViewWithTag<TextView>(TAG) ?: return
        v.textSize = if (playing) 12f else 16f; v.alpha = if (playing) 0.5f else 0.85f
    }
```
Commandes, dans l'ordre : `tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.owner.KeyBadgeTest'` → `BUILD SUCCESSFUL` ; puis les `grep` ci-dessous. Règle d'arrêt : test rouge ⇒ une seule correction du test (pas du modèle), puis `STATUT: ÉCHEC`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.KeyBadgeTest'   # vert, ≥ 5 cas sur `short`
grep -c 'textSize = 14f' receiver/src/main/kotlin/castbridge/receiver/KeyBadgeOverlay.kt  # 0
grep -c 'b.short' receiver/src/main/kotlin/castbridge/receiver/KeyBadgeOverlay.kt         # ≥ 1
```
Observable : en essai le badge dit « ESSAI · 11 h restantes » (≤ 5 mots) en haut à gauche, lisible à 3 m sur 720p, sans chevaucher le titre du héros ni la colonne de puces (haut droite).

## Cas limites
Aucune activation ⇒ « SANS CLÉ · Entrez un code ». Deux activations de production ⇒ la date la plus tardive. Fenêtre d'essai à 0 min ⇒ « ESSAI · essai terminé ». Titre long (« ACTIVATION TERMINÉE ») + complément ⇒ ≤ 6 mots au total (test).

## À ne pas faire
Ne pas changer `Badge.text` ni `lines` (utilisés par `ActivationActivity.kt:76`). Ne pas déplacer le badge dans la colonne des puces de statut. Ne pas rendre le badge focusable.

## Rapport
`STATUT`, les cinq valeurs de `short` observées (une par état), une capture de l'accueil avec le badge, la ligne à appeler par w11-10.
