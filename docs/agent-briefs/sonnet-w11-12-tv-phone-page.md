# w11-12 — CastBridge-TV : page « Téléphone » (code en grand, ajouter, rendre visible, téléphones de confiance, Internet) remplaçant cinq tuiles et le dialogue d'aide
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-d** (vague W11) · prérequis : w11-11 · porte : `grep -c 'PhonePageActivity' android/receiver/src/main/AndroidManifest.xml  # 1`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 11d (TV) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT (après w11-10 ; parallèle à w11-11).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 4.1 (tuile Téléphone), § 4.6, § 8.2, § 9.3. Branche `claude/sonnet-w11-12`. Rapport : `docs/agent-reports/sonnet-w11-12.md`.

## Objectif
Une seule page, lisible à 3 m, qui répond à « comment relier mon téléphone ? » : le code de la TV en 54 sp (masqué « •••••• » en mode enfant), trois boutons (« Ajouter un téléphone », « Rendre visible 2 min », « Diagnostic Internet »), la liste des téléphones de confiance (nom + « Retirer »), une ligne d'état Internet/Bluetooth/Wi-Fi en ≤ 4 mots chacune, et l'aide en 3 étapes repliée sous « Comment faire ? ». Elle remplace les tuiles « Ajouter un téléphone », « Recevoir du téléphone », « Bluetooth », « Internet », « Aide » et la puce « prêt · code » de l'en-tête.

## Pourquoi (preuves)
- `android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt:545-546, :565, :576-584, :606` : cinq tuiles pour le même besoin ; `:448-454` : dialogue d'aide avec le code et l'adresse ; `:477-500` : `internetMenu()`.
- `HomeScreen.kt:126` : code partiellement visible dans l'en-tête, révélé 10 s sur OK.
- `PairActivity.kt:238-279` : écran d'ajout avec compte à rebours et liste « Téléphones de confiance (n) » : **réutilisé** pour l'ajout, pas dupliqué.
- `StatusBarView.kt:187-191` : « Retirer ce téléphone » existe déjà dans le panneau Connexions (même logique à réutiliser : `svc.trust.revoke`).

## Fichiers possédés
- Nouveau : `android/receiver/src/main/kotlin/castbridge/receiver/PhonePageActivity.kt`.
- Modifiés : `android/receiver/src/main/AndroidManifest.xml` (déclaration `PhonePageActivity`, `exported=false`, `screenOrientation=landscape`), `android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt` (**seulement** la branche `"phone"` de `openTile` et de `plusActions` → `PhonePageActivity.open(this)` ; `homeApi().openHelp()` reste pour le legacy).
- Hors zone : `PairActivity.kt`, `HomeGrid.kt`, `HomeScreen.kt`, `PlusPanel.kt`, `StatusBarView.kt`, `TvNetDiag.kt`.

## Signatures à respecter
```kotlin
class PhonePageActivity : Activity() { companion object { fun open(ctx: Context) } }
```

## Étapes
1. Mise en page (`ScrollView` > `LinearLayout`, padding 56/40 dp, fond `TvStyle.BG`) : titre « Téléphone » 30 sp ; « Code de la TV » 19 sp `MUTED` ; le code (`ParentalHub.shownPin(svc.pin)`) 54 sp monospace gras `ACCENT` ; rangée de 3 boutons (`TvStyle.styleButton`) : « Ajouter un téléphone » → `PairActivity.open(this)` ; « Rendre visible 2 min » → même intent que `PlayerActivity.makeDiscoverable()` (dupliquer les 4 lignes, hors zone sinon) ; « Diagnostic » → `R/LinkDiagActivity.kt` (w7-15) **si la classe existe** (son « Auto-test » = `SelfTest`), sinon reprend `internetMenu()` sous forme d'un `choose(...)` local (copier les 4 entrées ; `showDiag/appendDiag` reproduits localement en ≤ 20 lignes).
2. « Téléphones de confiance (n) » : une ligne par téléphone (`svc.trust.list()`), bouton « Retirer » avec confirmation où « Annuler » a le focus (modèle `StatusBarView.confirm`).
3. État : trois lignes 19 sp « Internet : Wi-Fi ok » / « Bluetooth : prêt » / « Wi-Fi Direct : désactivé » dérivées de `svc.netSummary()` et `svc.statuses["1-bt"]`, `prefs.getBool("wd_enabled")`, tronquées à 4 mots (`TileText.status(s, 4)`).
4. « Comment faire ? » : bouton qui déplie le texte en 3 étapes de `PlayerActivity.kt:449-453` (sans l'URL si pas de réseau).
5. Service : `bindService(TvService)` comme `PairActivity` ; sans service ⇒ « Démarrage de CastBridge TV… ».
6. Focus initial : « Ajouter un téléphone » ; RETOUR → accueil (fin d'activité) ; ids `nextFocus*` posés entre les 3 boutons et la liste.
7. Télémétrie : `TvConnect.feature("bluetooth", "tile")` à l'ouverture (id existant ; `phone_page` ajouté par w11-14).

## Critères d'acceptation
```sh
cd android && grep -c 'PhonePageActivity' receiver/src/main/AndroidManifest.xml   # 1
grep -c 'PairActivity.open' receiver/src/main/kotlin/castbridge/receiver/PhonePageActivity.kt   # 1
grep -c 'shownPin' receiver/src/main/kotlin/castbridge/receiver/PhonePageActivity.kt   # ≥ 1
grep -c 'textSize = 54f\|54f' receiver/src/main/kotlin/castbridge/receiver/PhonePageActivity.kt   # ≥ 1
```
Observable (`-PnavV2=true`) : touche rouge ou tuile « Téléphone » ⇒ la page ; le code se lit à 3 m ; en mode enfant « •••••• » ; « Ajouter un téléphone » ouvre l'écran d'appairage existant ; « Retirer » demande confirmation ; D-pad sans impasse ; ≤ 8 cibles focusables sur la page (3 boutons + ≤ 3 « Retirer » + « Comment faire ? » + éventuel défilement).

## Cas limites
Pas de Bluetooth sur la TV ⇒ « Rendre visible » désactivé avec l'état « Bluetooth : absent ». Plus de 3 téléphones de confiance ⇒ liste défilante (chaque « Retirer » reste focusable). Code masqué + aide dépliée ⇒ l'aide montre « •••••• » aussi.

## À ne pas faire
Ne pas dupliquer `PairActivity`. Ne pas écrire dans `StatusBarView`. Pas de texte technique (IP, port) hors du bloc « Comment faire ? ». Ne pas retirer les anciennes tuiles du rendu legacy (`homeTools()`).

## Rapport
`STATUT`, capture de la page (production et enfant), parcours D-pad, taille du code en pixels mesurée sur 720p.
