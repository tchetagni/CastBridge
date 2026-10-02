# w11-03 — CastBridge-TV : tuiles d'accueil lisibles (étiquette 19 sp sur une ligne, état ≤ 3 mots), description hors écran, `HomeTool.id`
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet si une ancre « avant » est introuvable ou si la compilation échoue deux fois · statut : PRÊT
> **Groupe : W11-a** (vague W11) · prérequis : aucun · porte : `grep -c 'object TileText' android/receiver/src/main/kotlin/castbridge/receiver/TvCards.kt  # 1`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 11a (gain rapide) · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 4.7, § 9.1 (Q3) et prérequis de w11-04 (`HomeTool.id`). Branche `claude/sonnet-w11-03`. Rapport : `docs/agent-reports/sonnet-w11-03.md`.

## Objectif
Sans changer le nombre de tuiles (c'est w11-10), rendre chaque tuile de la rangée d'outils lisible à 3 m sur 720p : étiquette **19 sp** sur **une** ligne, ligne d'état bornée à **3 mots**, description de 10-20 mots retirée du héros (elle reste disponible pour l'accessibilité). Ajouter un champ `id` à `HomeTool` (défaut `""`) pour que w11-04 filtre par id et non par étiquette.

## Pourquoi (preuves)
- `android/receiver/src/main/kotlin/castbridge/receiver/TvCards.kt:276` : étiquette `textSize = 16f`, `maxLines = 2` ; `:279` ligne d'état non bornée ; `:258` `data class HomeTool(icon, label, description, status, on, action)` sans id.
- `TvCards.kt:34` : minimum de la charte = 16 sp pour les légendes, 19 sp (`Type.BODY`) pour le texte.
- `android/receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt:209` : au focus, `heroSub.text = t.description + " — " + status` ⇒ 10-20 mots à chaque déplacement.
- `android/receiver/src/main/kotlin/castbridge/receiver/ParentalHub.kt:271,278` et `core/.../parental/ParentalModel.kt:227` : filtrage par **étiquette**.

## Fichiers possédés
- Modifiés : `android/receiver/src/main/kotlin/castbridge/receiver/TvCards.kt` (classes `HomeTool`, `IconTile` seulement), `android/receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt` (fonction `fillTools` seulement, lignes 196-213).
- Hors zone : `PlayerActivity.kt` (w11-04 y mettra les ids), `ParentalHub.kt`, `ParentalModel.kt`, `TvStyle`.

## Signatures à respecter (contrat pour w11-04 et w11-10)
```kotlin
data class HomeTool(val icon: Int, val label: String, val description: String, val status: String?, val on: Boolean,
                    val id: String = "", val action: () -> Unit)
object TileText { fun status(s: String?, maxWords: Int = 3): String? }   // dans TvCards.kt, pur Kotlin (sans Context)
```
`id` est le **dernier** paramètre nommé avant `action` pour que les appels existants `HomeTool(icon, label, description, status, on) { … }` compilent sans changement.

## Étapes
1. `HomeTool` : ajouter `id: String = ""` (voir signature).
2. `IconTile` : étiquette `textSize = TvStyle.Type.BODY` (19 sp), `maxLines = 1`, `ellipsize = END` ; état : `TileText.status(tool.status)` (coupe au 3e mot, ajoute « … » si coupé ; `null` reste `null`), `textSize = TvStyle.Type.CAPTION` ; hauteur de tuile recalculée : `widthPx * 3 / 4 + dp(40)` devient `widthPx * 9 / 16 + dp(56)` (icône 46 dp + 1 ligne + 1 ligne) ; `contentDescription = "${tool.label}. ${tool.description}"` sur la tuile (TalkBack) ; `focusZoom` inchangé.
3. `HomeScreen.fillTools` : au focus, `heroTitle = t.label`, `heroSub = t.status ?: ""` (plus de description). Le `toolsSig` reste calculé sur `label:status:on`.
4. Aucun changement de couleurs ni de focus.

## Éditions (forme mécanique Haiku)
Fichier 1 : `android/receiver/src/main/kotlin/castbridge/receiver/TvCards.kt`.

E1 — avant (ligne 258) :
```kotlin
data class HomeTool(val icon: Int, val label: String, val description: String, val status: String?, val on: Boolean, val action: () -> Unit)
```
après :
```kotlin
data class HomeTool(val icon: Int, val label: String, val description: String, val status: String?, val on: Boolean, val id: String = "", val action: () -> Unit)

/** Texte d'état d'une tuile : au plus [maxWords] mots (« · » ne compte pas), « … » si coupé. Pur Kotlin. */
object TileText {
    fun status(s: String?, maxWords: Int = 3): String? {
        if (s == null) return null
        val words = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val kept = ArrayList<String>(); var n = 0
        for (w in words) { if (w != "·" && n == maxWords) return kept.joinToString(" ") + "…"; kept += w; if (w != "·") n++ }
        return kept.joinToString(" ")
    }
}
```
E2 — avant (ligne 266) :
```kotlin
        layoutParams = ViewGroup.MarginLayoutParams(widthPx, widthPx * 3 / 4 + TvStyle.dp(ctx, 40)).apply { setMargins(m, m, m, m) }
```
après :
```kotlin
        layoutParams = ViewGroup.MarginLayoutParams(widthPx, widthPx * 9 / 16 + TvStyle.dp(ctx, 56)).apply { setMargins(m, m, m, m) }
        contentDescription = "${tool.label}. ${tool.description}"
```
E3 — avant (ligne 276) :
```kotlin
        addView(TextView(ctx).apply { text = tool.label; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 2; setPadding(m, m / 2, m, 0) })
```
après :
```kotlin
        addView(TextView(ctx).apply { text = tool.label; textSize = TvStyle.Type.BODY; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(m, m / 2, m, 0) })
```
E4 — avant (lignes 277-279) :
```kotlin
        tool.status?.let { st ->
            addView(TextView(ctx).apply {
                text = (if (tool.on) "● " else "") + st; textSize = TvStyle.Type.CAPTION; gravity = Gravity.CENTER; maxLines = 1
```
après :
```kotlin
        TileText.status(tool.status)?.let { st ->
            addView(TextView(ctx).apply {
                text = (if (tool.on) "● " else "") + st; textSize = TvStyle.Type.CAPTION; gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
```
Fichier 2 : `android/receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt`.

E5 — avant (ligne 209) :
```kotlin
                v.setOnFocusChangeListener { _, has -> if (has) { heroTitle.text = t.label; heroSub.text = t.description + (t.status?.let { "  —  $it" } ?: "") } }
```
après :
```kotlin
                v.setOnFocusChangeListener { _, has -> if (has) { heroTitle.text = t.label; heroSub.text = t.status ?: "" } }
```
Commandes, dans l'ordre : les `grep` ci-dessous ; puis `tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` si le SDK Android est présent (sinon le dire). Règle d'arrêt : ancre introuvable ⇒ `STATUT: BLOQUÉ`.

## Critères d'acceptation
```sh
cd android && grep -c 'textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 2' receiver/src/main/kotlin/castbridge/receiver/TvCards.kt  # 0
grep -c 'val id: String = ""' receiver/src/main/kotlin/castbridge/receiver/TvCards.kt   # 1
grep -c 'heroSub.text = t.description' receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt   # 0
grep -c 'object TileText' receiver/src/main/kotlin/castbridge/receiver/TvCards.kt   # 1
gradle --offline :receiver:compileDebugKotlin   # si le SDK est disponible ; sinon le signaler
```
Observable : sur un écran 1280 × 720 (émulateur TV), chaque étiquette tient sur une ligne à 19 sp ; « Connexion & réglages » devient « Connexion & régl… » (sera renommé en w11-10, acceptable ici) ; l'état « Aucune clé » / « 2 de confiance » / « Version 0.14.18 · à jour » ⇒ « Version 0.14.18 ·… » (3 mots).

## Cas limites
`status` vide ⇒ ligne absente (comme aujourd'hui). Étiquette de 3 mots (« Ajouter un téléphone ») ⇒ tronquée avec « … » : acceptable (w11-10 la renomme « Téléphone »). `TileText.status` ne compte pas « · » comme un mot.

## À ne pas faire
Ne pas changer `MediaCard` ni `ToolTile`. Ne pas renommer les tuiles (w11-04/w11-10). Ne pas toucher à `PlayerActivity.homeTools`.

## Rapport
`STATUT`, capture d'une rangée de tuiles, les quatre `grep`, et confirmation que tous les appels `HomeTool(...)` existants compilent (ou la raison).
