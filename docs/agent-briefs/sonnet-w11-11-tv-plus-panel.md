# w11-11 — CastBridge-TV : `PlusPanel` (≤ 12 lignes, 3 colonnes) remplaçant « Connexion & réglages » et le menu de 22-24 lignes ; page « À propos »
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-d** (vague W11) · prérequis : w11-10 · porte : `grep -c 'fun showPlus' android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt  # 1`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 11d (TV) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w11-10).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 4.2, § 6.1-6.3, § 7.4, § 9.3. Branche `claude/sonnet-w11-11`. Rapport : `docs/agent-reports/sonnet-w11-11.md`.

## Objectif
Quand `NAV_V2` est actif, la tuile « Plus » (et GUIDE/INFO, et MENU hors vidéo) ouvre un panneau de **≤ 12 lignes** en 3 colonnes tirées de `TvNav.plus(state)` (Version complète / Stockage / Connexions / Appareil / À propos), chaque ligne ≤ 4 mots, bascules affichées comme telles (Wi-Fi Direct, Démarrer avec la TV, Lecture à distance). Les 15-19 paires d'information de `SettingsPanel` deviennent une page « À propos » (version, identifiant, serveur, assistance, empreinte w6-12 si présente) ouverte depuis le groupe « À propos ». Pendant une vidéo, MENU garde le panneau du lecteur (inchangé).

## Pourquoi (preuves)
- `android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt:611-635` (`showSettings`, 15-19 lignes d'info) et `:704-748` (`menuItems`, après w11-04 ≤ 9 lignes mais toujours une liste plate sans groupe) ; `HomeScreen.kt:277-318` (`SettingsPanel`).
- Conception § 4.2 : groupes et lignes exacts ; § 6.1 : « Boutique » et « À propos : Boutique et jetons » (W5) ; § 6.3 : « Renouveler la clé » (W4).

## Fichiers possédés
- Nouveaux : `android/receiver/src/main/kotlin/castbridge/receiver/PlusPanel.kt` (`PlusPanel`, `PlusLine`, `ToggleLine`), `android/receiver/src/main/kotlin/castbridge/receiver/AboutPage.kt` (`AboutPage.show(act, info: List<Pair<String,String>>, actions)`).
- Modifiés : `android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt` (**seulement** : `openTile("plus")` → `showPlus()` ; nouvelle `showPlus()` ; `plusActions(): Map<String, () -> Unit>` ; `showMenu()` hors vidéo → `showPlus()` quand `NAV_V2` ; `onBackPressed`/`onKeyDown BACK` : `plusPanel.visible` → accueil), `android/receiver/src/main/res/layout/activity_player.xml` (un `FrameLayout` `@+id/plus`).
- Hors zone : `HomeGrid.kt`, `HomeScreen.kt` (w11-10), `PhonePageActivity.kt` (w11-12 : la ligne `phone` appelle `PhonePageActivity.open` si la classe existe, sinon `PairActivity.open`), `ShopActivity` (W5 : ligne `shop` présente seulement si la classe existe).

## Signatures à respecter
```kotlin
class PlusPanel(act: Activity, container: FrameLayout) {
    fun show(groups: List<PlusGroup>, actions: Map<String, () -> Unit>, toggles: Map<String, Boolean>)   // toggles: wifi_direct, autostart, overlay
    fun hide(); val visible: Boolean
}
```
Correspondance id → action (dans `PlayerActivity.plusActions()`) : `upgrade`/`renew` → `ActivationActivity` (`EXTRA_UPGRADE`) ; `usb` → `choose("Clé USB", …)` existant (`:568-574`) ; `storage_target` → `chooseTarget()` ; `downloads` → `DownloadsActivity` ; `phone` → page Téléphone ; `link` → `R/LinkDiagActivity.kt` (w7-15) **si la classe existe**, sinon la ligne est absente (le modèle w11-05 la liste sous « Connexions » avec `Lock.HIDDEN` par défaut) ; `wifi_direct` → `toggleWifiDirect()` ; `admin` → `choose("Administration", …)` existant (`:588-597`) ; `updates` → `ServerActivity.MODE_UPDATES` ; `shop` → `ShopActivity` ; `autostart` → bascule existante (`:722-725`) ; `overlay` → `openOverlaySettings` ; `assistance` → `showAssistance()` ; `privacy` → `ServerActivity.MODE_PRIVACY` ; `dev_options` → `openDevSettings()` ; `help` → `homeApi().openHelp()` ; `about` → `AboutPage.show(...)` avec les infos de l'ancien `showSettings()` (`:618-631`) + « Connexion au serveur » et « Tester le relais Bluetooth », « Stockage : choisir un dossier / oublier » comme actions de la page.

## Étapes
1. `activity_player.xml` : conteneur `plus` (gone).
2. `PlusPanel` : 3 colonnes `LinearLayout` verticales dans un `LinearLayout` horizontal, titres de groupe 16 sp `MUTED` majuscules, lignes 21 sp (`Type.SUBTITLE`) focusables (`TvStyle.focusable`), bascule = ligne + « ● / ○ » à droite ; D-pad : `nextFocusLeft/Right` entre colonnes (même rang ou dernier rang), HAUT/BAS dans la colonne ; focus initial = 1re ligne de la 1re colonne ; en-tête : « Plus » 30 sp + badge court (déjà en overlay). `ParentalHub.wrapMenu` appliqué aux actions (catégories).
3. `PlayerActivity.showPlus()` : `home.hide(); libScreen.hide(); plusPanel.show(TvNav.plus(NavStateTv.current()), plusActions(), toggles)` ; `enterScreen("settings")` (id existant) ; `refreshStatusBar()` (non interactive) ; `hideScreens()` cache aussi `plusPanel`.
4. `AboutPage` : `ScrollView` de paires clé/valeur 19 sp + boutons d'action en bas ; RETOUR → `showPlus()`.
5. Garder `showSettings()`/`SettingsPanel` pour `NAV_V2=false`.

## Critères d'acceptation
```sh
cd android && grep -c 'TvNav.plus' receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt   # ≥ 1
grep -c 'android:id="@+id/plus"' receiver/src/main/res/layout/activity_player.xml   # 1
grep -c 'class PlusPanel' receiver/src/main/kotlin/castbridge/receiver/PlusPanel.kt   # 1
grep -c 'fun showPlus' receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt   # 1
```
Observable (`-PnavV2=true`) : « Plus » montre ≤ 12 lignes, toutes visibles sans défiler sur 720p ; chaque ligne ≤ 4 mots ; GAUCHE/DROITE change de colonne, HAUT/BAS dans la colonne, aucune impasse ; RETOUR revient à l'accueil avec le focus sur « Plus » ; en essai la 1re ligne est « Passer en production » ; en mode réduit « Renouveler la clé » ; « À propos » liste ce que « Connexion & réglages » listait ; pendant une vidéo MENU ouvre toujours le panneau du lecteur.

## Cas limites
Mode enfant ⇒ « Plus » n'est pas une tuile (w11-10) mais GUIDE/MENU l'ouvrent : `ParentalHub.allow(SETTINGS)` protège comme aujourd'hui (`:612`). `ShopActivity` absente ⇒ ligne absente (le modèle la liste, l'app la filtre : noter l'écart). Plus de 12 lignes après filtrage impossible (test cœur).

## À ne pas faire
Ne pas supprimer `SettingsPanel`, `showSettings`, `menuItems` (rendu legacy et MENU en vidéo). Ne pas toucher `HomeGrid`/`HomeScreen`. Pas de nouvelle chaîne hors `NavTexts`.

## Rapport
`STATUT`, captures (production, essai, réduit), tableau id → action vérifié, parcours D-pad du panneau sur la TV de référence.
