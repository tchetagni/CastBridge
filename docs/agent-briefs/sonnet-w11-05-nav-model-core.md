# w11-05 — Cœur : `castbridge.core.nav` — modèle de navigation (destinations, lignes de « Plus », visibilité par état, budgets de mots) et `QuickActions`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-b** (vague W11) · prérequis : aucun · porte : `gradle --offline :core:test --tests 'castbridge.core.nav.*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 11b (cœur, JVM, testé) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 2, § 3.1-3.3, § 4.1-4.2, § 5, § 6.5, § 8. Branche `claude/sonnet-w11-05`. Rapport : `docs/agent-reports/sonnet-w11-05.md`.

## Objectif
Un modèle **pur Kotlin** (aucune dépendance Android) qui décrit la navigation des deux apps : destinations du téléphone (5), tuiles de la TV (6), lignes de « Plus » (groupées, ≤ 12), libellés français avec budgets de mots, visibilité et cadenas selon l'état (production, essai, grâce, mode réduit, minimal W6, enfant, super, point focal), et les suggestions « Que voulez-vous faire ? ». Les apps ne feront que le rendre (w11-07, w11-08, w11-10, w11-11).

## Pourquoi (preuves)
- Ni `android/sender` ni `android/receiver` n'ont de `src/test` : toute logique testable doit vivre dans `core` (98 fichiers de test, `android/core/src/test/kotlin/castbridge/core/`).
- Aujourd'hui la liste des tuiles est un bloc de 80 lignes dans `PlayerActivity.kt:530-608` et les onglets du téléphone sont codés en dur dans `MainActivity.kt:115-123` (conception § 1).
- Les états existent déjà : `castbridge.core.owner.TrialPolicy.CLOSED_TILES`, `GateState`, `castbridge.core.parental.ParentalRules`, `PhoneGate` (w6-01, si fusionné : sinon modéliser `NavState.minimal` sans l'appeler).

## Fichiers possédés
- Nouveaux : `android/core/src/main/kotlin/castbridge/core/nav/NavModel.kt`, `android/core/src/main/kotlin/castbridge/core/nav/NavTexts.kt`, `android/core/src/main/kotlin/castbridge/core/nav/QuickActions.kt`, `android/core/src/test/kotlin/castbridge/core/nav/NavModelTest.kt`, `android/core/src/test/kotlin/castbridge/core/nav/QuickActionsTest.kt`.
- Hors zone : tout fichier existant. Ne pas modifier `TrialPolicy`, `ParentalModel`, `Telemetry`.

## Signatures à respecter (contrat pour w11-06/07/08/10/11)
```kotlin
package castbridge.core.nav

enum class Edition { PRODUCTION, TRIAL, GRACE_TRIAL, REDUCED, LOCKED, SUPER }
data class NavState(val edition: Edition = Edition.PRODUCTION, val phoneMinimal: Boolean = false, val kidActive: Boolean = false,
                    val kidGamesBlocked: Boolean = false, val agentMode: Boolean = false, val hasTv: Boolean = true)

enum class Lock { OPEN, LOCKED, HIDDEN }   // HIDDEN n'est permis que pour kidActive et pour les lignes de Plus fermées par l'édition
data class Entry(val id: String, val label: String, val status: String? = null, val lock: Lock = Lock.OPEN, val icon: String = id)
data class PlusGroup(val title: String, val lines: List<Entry>)

object PhoneNav {
    val DESTINATIONS: List<String>   // ["home","files","learn","games","parents"]
    fun destinations(s: NavState): List<Entry>          // toujours 5 ; lock = LOCKED en minimal pour files/learn/games/parents sauf règle W6 (parents reste OPEN en mode réduit)
    fun plus(s: NavState): List<PlusGroup>              // ≤ 4 groupes, ≤ 12 lignes visibles ; ids: remote, tv_library, file_exchange, downloads, shop, activate, rentals, lots, free_content, settings, advanced, help, focal (agentMode seulement)
}
object TvNav {
    val TILES: List<String>   // ["library","learn","games","phone","parental","plus"]
    fun tiles(s: NavState): List<Entry>                 // ≤ 6 ; kidActive ⇒ phone et plus HIDDEN, games HIDDEN si kidGamesBlocked
    fun plus(s: NavState): List<PlusGroup>              // ≤ 12 lignes ; 1re ligne "upgrade" (essai) ou "renew" (réduit) ; ids: usb, storage_target, downloads, phone, wifi_direct, admin, updates, shop, autostart, overlay, assistance, privacy, dev_options, help
}
object NavTexts { fun label(id: String): String; fun plusGroup(id: String): String; const val MAX_LABEL_WORDS = 2; const val MAX_STATUS_WORDS = 3; const val MAX_LINE_WORDS = 4 }

data class QuickContext(val state: NavState, val resumeTitle: String? = null, val lessonOpenOnTv: Boolean = false, val lastClass: String? = null, val tvPlaying: Boolean = false)
data class QuickAction(val id: String, val label: String, val lock: Lock = Lock.OPEN)
object QuickActions { fun suggest(c: QuickContext): List<QuickAction> }   // 3 ou 4, ordre déterministe (table § 3.3)
```
Libellés exacts : conception § 8.1 et § 8.2 (ne pas inventer de variante). Statuts dynamiques (« 124 vidéos ») sont fournis par l'app via `Entry.copy(status = …)` ; le modèle ne les calcule pas.

## Étapes
1. `NavTexts` : table id → libellé (téléphone et TV), groupes de Plus (« Ma TV », « Boutique et clés », « Données », « Réglages » ; TV : « Version complète », « Stockage », « Connexions », « Appareil », « À propos »).
2. `PhoneNav.destinations/plus` et `TvNav.tiles/plus` selon § 3.1, § 3.2, § 4.1, § 4.2, § 5, § 6 (W5 : `shop` ; W4 : `renew` ; W6 : cadenas, jamais `HIDDEN` sur une destination).
3. `QuickActions.suggest` : implémenter exactement la table § 3.3 (7 contextes) ; 4 suggestions au plus ; en minimal, « Envoyer une vidéo » est présent avec `Lock.LOCKED`.
4. Tests `NavModelTest` : (a) 5 destinations quel que soit l'état ; (b) ≤ 6 tuiles, et exactement 6 en production ; (c) ≤ 12 lignes de Plus visibles sur chaque app et chaque état ; (d) budgets : chaque `label` ≤ 2 mots, `status` ≤ 3, ligne de Plus ≤ 4 (compter les mots séparés par une espace, « · » exclu) ; (e) aucun id dupliqué dans une liste ; (f) un état n'ajoute jamais d'id de tuile absent en production (« un état retire ou cadenasse ») ; (g) kidActive ⇒ pas de `phone`/`plus` ; (h) essai ⇒ Plus commence par `upgrade`, réduit ⇒ `renew` ; (i) minimal ⇒ `files/learn/games` LOCKED, `parents` LOCKED sauf `edition == REDUCED` ; (j) `agentMode` ⇒ ligne `focal` présente, sinon absente.
5. Tests `QuickActionsTest` : un cas par ligne de la table § 3.3 (7), + déterminisme (deux appels identiques ⇒ même liste), + taille 3..4.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.nav.*'   # vert, ≥ 17 cas
grep -c 'import android' core/src/main/kotlin/castbridge/core/nav/*.kt       # 0
```
Observable : aucun (cœur). Le rapport imprime les listes produites pour les états production / essai / enfant / minimal.

## Cas limites
`hasTv = false` ⇒ suggestions « Ajouter ma TV » en premier ; `LOCKED` (TV verrouillée) ⇒ `TvNav.tiles` vide (l'écran d'activation prend tout) ; `SUPER` ⇒ identique à production (le bandeau est hors modèle).

## À ne pas faire
Pas de dépendance à `castbridge.core.owner.PhoneGate` si non fusionné (dupliquer la règle en 3 lignes et le noter). Pas de texte anglais. Pas de calcul d'état dynamique (compteurs). Pas de logique de focus (w11-06).

## Rapport
`STATUT`, les 4 listes imprimées, nombre de cas de test, écarts éventuels avec les tables § 3.3 et § 8.
