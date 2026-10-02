# w5-15 — CastBridge-TV : écran « Boutique » à la télécommande (leçons à louer, jetons du Quiz, code de recharge, mes locations, mes reçus ; hors ligne, essai, mode réduit, profil enfant), tuile d'accueil, « À propos : Boutique et jetons »

**Vague 5d · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après w5-01 ; w5-16 en parallèle : cet écran code contre l'interface `ShopStore` définie **ici** et implémentée par w5-16 ; d'ici là, une implémentation mémoire).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 5.1 (textes), § 6.2, § 6.3, § 6.6, § 8 (lire en entier). Branche `claude/sonnet-w5-15`. Rapport : `docs/agent-reports/sonnet-w5-15.md`. TV de référence : 32 bits, 720p, D-pad seulement ; cibles : toutes les smart TV (aucune hypothèse GaiaOS).

## Objectif
`ShopActivity` (vues classiques, grille de conception `Dx` 1920×1080 comme `GamesActivity`, aucune WebView, aucune image) : bandeau d'état (« En ligne » / « Hors ligne : commandez depuis CastBridge sur votre téléphone (code TV XXXX-XXXX-XXXX-XXXX) »), colonne gauche à cinq entrées (**Leçons à louer**, **Jetons du Quiz**, **Code de recharge**, **Mes locations**, **Mes reçus**), panneau droit en cartes ; détail d'un bouquet (contenu, durée, prix, « Aperçu gratuit » ouvre Apprendre sur le lot d'essai, « Louer (prix) ») ; écran **Payer** (**Code de recharge** : clavier à l'écran 5×7 Crockford + chiffres, saisie par groupes de 4, contrôle local, « Valider » ; **Espèces chez votre point focal** : code `CB-XXXX-XX` très grand + consigne + « Vérifier ma commande ») ; **Jetons** (solde « sur cette TV : 20 · au serveur : 3 », paquets, « À quoi servent les jetons ? ») ; **Mes locations** (compte à rebours, « Renouveler ») ; **Mes reçus**. Touches couleur : rouge Annuler, verte Valider, jaune « Code de recharge », bleue « Aide ». Tuile **« Boutique »** sur l'accueil après « Jeux » ; entrée de menu « À propos : Boutique et jetons ».

## Pourquoi (preuves)
- `R/GamesActivity` (`R/Games.kt:108-189`) et `R/GamesUi.kt` (`Dx`, `GamesColors`, `focusable`, anneau de focus 6 px, échelle 1,04) : **même langage visuel** ; `R/PlayerActivity.kt:512-608` (`homeTools()`, `tile(feature, …)` avec `TvConnect.feature` et `ParentalHub.guardTile`), `:737` (menu « À propos ») ; `R/HomeScreen.kt:183-213` ; `R/TvCards.kt:258` ; `R/KeyBadgeOverlay.kt` (le badge reste visible) ; `R/ActivationActivity.kt` (grand format du code d'appareil : 54 sp mono) ; `C/owner/Base32C`, `C/shop/VoucherCode.normalize` (w5-01 : désigne le groupe douteux) ; `C/lots/RentalEngine` (phrases du compte à rebours).
- Audit UX : lisibilité à 3 m, ≥ 28 px à 720p, jamais la couleur seule.

## Fichiers possédés
Nouveaux `R/shop/ShopActivity.kt`, `R/shop/ShopViews.kt`, `R/shop/VoucherKeypad.kt`, `R/shop/ShopTexts.kt`, `android/receiver/src/main/res/drawable/ic_t_shop.xml` ; modifiés `R/PlayerActivity.kt` (tuile `shop`, entrée « À propos »), `R/HomeScreen.kt` (si nécessaire pour la tuile), `android/receiver/src/main/AndroidManifest.xml` (une `<activity android:name=".shop.ShopActivity" android:exported="false">`). **Hors zone** : `R/shop/{ShopHub,ShopStore,ShopClient,RentalOnlineInstaller}.kt` (w5-16 : implémente `ShopStore` ; **l'interface** `ShopStore` est écrite ici dans `ShopViews.kt` : `catalog(): ShopCatalogView?`, `state(): ShopStateView` (online, trial, degraded, kid, termsAccepted, installKeyProtection), `rentals()`, `tokens(): TokenView(onTv, server, pending)`, `receipts()`, `pendingVoucher(): String?`, `fun submitVoucher(code): SubmitResult` (`QUEUED_OFFLINE | SENT | REFUSED(fr)`), `fun order(item): OrderResult` (`ONLINE_CREATED(ref, amount) | USE_PHONE | REFUSED(fr)`), `fun checkOrder(ref)`), `R/tokens/**` (w5-17), `R/ParentalHub.kt` (w5-18 : appeler `ParentalHub.allow(this, Category.PURCHASES, …)` **si** la catégorie existe, sinon `kidHomeActive()`), `C/**`, `S/**`.

## Étapes
1. `ShopTexts` : tous les textes FR (constantes testables : « Saisir un code de recharge », « Payer en espèces chez votre point focal », « Hors ligne : commandez depuis CastBridge sur votre téléphone », « Disponible avec une clé de production », « Renouvelez d'abord la clé », « Demandez à un parent », « Le Quiz reste jouable sans jetons », « Les jetons n'expirent pas » (ou règle de la grille)) ; aucun montant.
2. `ShopActivity` : disposition § 8 ; focus initial sur « Leçons à louer » ; ↑↓ colonne, → panneau, ← retour colonne, Retour ferme (ou revient d'un détail) ; rafraîchissement de l'état toutes les 5 s (`ShopStore.state()`), sans réseau ici (le réseau est dans `ShopClient` de w5-16, déclenché par `ShopStore.order/submitVoucher/checkOrder`).
3. `VoucherKeypad` : grille 5×7 (A-Z sans I L O U, 0-9, ⌫, « Valider », « Coller du téléphone » = consigne) ; affichage `CB-____-____-____-____` avec curseur ; à chaque groupe complet : `VoucherCode.normalize` partielle ; groupe faux : contour **et** texte « Vérifiez le 2e groupe » ; « Valider » ⇒ `submitVoucher` ; hors ligne ⇒ « Code gardé : il sera envoyé par le téléphone à la prochaine connexion » ; **une seule** saisie en attente ; après envoi, le code n'est plus affiché.
4. Détail bouquet, **Payer** (deux options ; en ligne seulement : sinon « À commander depuis le téléphone » + la saisie d'un bon reste possible) ; code de commande en 96 px mono ; « Vérifier ma commande » ⇒ `checkOrder`.
5. Jetons : solde en 72 px, détail, paquets (prix), « Acheter » ⇒ même écran Payer ; en essai : « Passez en version complète pour acheter des jetons » ; en réduit : « Vos N jetons vous attendent : renouvelez la clé » ; profil enfant : « Demandez à un parent ».
6. Mes locations / Mes reçus : listes ; « Renouveler » ⇒ Payer avec l'article du contrat.
7. Tuile `shop` (« Boutique », statut « N jetons · M locations » ou « Consulter ») après `games` ; télémétrie : `TvConnect.feature("shop")` (id ajouté à `TV_FEATURES` par w5-16 ; d'ici là l'appel est toléré ? vérifier `EventCatalog` : s'il refuse un id inconnu, passer par `tile(feature = "shop")` **après** w5-16 et le dire) ; `ParentalHub.filterHome` : la tuile est masquée en mode enfant sauf pour « Mes locations » ? **Décision** : masquée entièrement en mode enfant (le parent gère).
8. « À propos : Boutique et jetons » : dialogue texte (ce que sont les jetons, ce qui est gratuit, que les locations ont une durée fixe, contact D7 si compilé, lien « Conditions » = texte de w5-19 si présent).
9. Accessibilité : `contentDescription`, montants en chiffres et lettres dans les confirmations, pas de couleur seule, taille minimale 28 px (720p) ; vérification sur émulateur 1280×720 @160 dpi **et** 1920×1080.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (SDK)
grep -n 'ShopActivity' android/receiver/src/main/AndroidManifest.xml | grep -c 'exported="false"'   # 1
grep -rn 'WebView' android/receiver/src/main/kotlin/castbridge/receiver/shop   # 0 hit
grep -rni 'momo\|mobile money\|237\|XAF [0-9]' android/receiver/src/main/kotlin/castbridge/receiver/shop   # 0 hit
grep -n '"shop"' android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt   # ≥ 1 (tuile)
```
Observable (émulateur TV 720p, `ShopStore` mémoire) : navigation complète au D-pad sans piège de focus ; saisie d'un bon de test avec un groupe faux ⇒ message ; hors ligne ⇒ bandeau + code gardé ; essai ⇒ lecture seule ; réduit ⇒ phrase + bon de clé ; mode enfant ⇒ tuile absente.

## Cas limites
- Catalogue absent (TV jamais reliée) : « Catalogue non reçu : connectez CastBridge sur votre téléphone » ; la saisie d'un bon reste possible.
- Écran 4:3 ou 1024×768 (certaines TV) : `Dx` s'adapte (`k = min`) ; vérifier que la colonne gauche ne déborde pas.
- Télécommande sans touches couleur : tout est accessible par les flèches et OK (les touches couleur sont des raccourcis).

## À ne pas faire
Pas de commit sur les branches partagées ; aucun appel réseau dans `R/shop/ShopActivity.kt` ; aucun montant/numéro en dur ; pas d'image ni de police ajoutée ; ne pas modifier `R/Games.kt` (w5-17) ni `R/ParentalHub.kt` (w5-18) ; français ; « CastBridge-TV ».

## Rapport
`STATUT`, interface `ShopStore` finale (pour w5-16), captures (`docs/img/shop/tv-*.png`), mesures de lisibilité, textes à relire.
