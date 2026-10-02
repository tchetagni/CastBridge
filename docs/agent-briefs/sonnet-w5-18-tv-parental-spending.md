# w5-18 — Contrôle parental des achats et des jetons : catégorie « Achats et jetons », allocation quotidienne par profil enfant, « PIN pour acheter », profil enfant = jamais d'achat, rapport parental ; réglages côté téléphone ; lacune `games`/`sudoku` corrigée

**Vague 5d · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w5-02 ; w5-17 en parallèle : fournit `TokenHub.spendGuard` et `PinRequester`).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 6.6, § 8, § 9. Branche `claude/sonnet-w5-18`. Rapport : `docs/agent-reports/sonnet-w5-18.md`. Rappel PARENTAL.md : **tout reste local** (TV + téléphone du parent), aucune donnée parentale au serveur.

## Objectif
(1) `Category.PURCHASES` (« Achats et jetons », bloquable, **bloquée par défaut** pour tout profil enfant existant ou nouveau) ; (2) règle : **profil enfant actif ⇒ aucune commande, aucune saisie de bon** (écran « Demandez à un parent » avec « Saisir le code parental ») ; (3) **dépenses de jetons** sous profil enfant : allocation quotidienne `kidDailyTokens` par profil (0 par défaut = code parental à chaque dépense), compteur par profil et par jour (`KidAllowance` de w5-02), message « il reste N jetons aujourd'hui pour <prénom> » ; (4) **« PIN pour acheter »** (réglage global, activé par défaut dès qu'un code parental existe) : toute commande/bon demande le code, même sans profil ; (5) rapport parental (quotidien, hebdomadaire) : « jetons dépensés : N », « achats : refusés N / avec code N » ; (6) `ParentalHub.categoryOf/categoryOfFeature` couvrent `GamesActivity`/`SudokuActivity` et `games`/`sudoku`/`shop` (lacune constatée) ; (7) téléphone : onglet Parental > « Règles de la TV » : « Achats et jetons » (bloquer, allocation par enfant, PIN pour acheter).

## Pourquoi (preuves)
- `C/parental/ParentalModel.kt:36` (`Category(code, label, blockable)`), `ParentalEngine.check(c)` `:149`, `active()/activeProfile()` `:140-142` ; `C/parental/ParentalApi.kt` (`config/get|set` avec `rev`) ; `R/ParentalHub.kt:201` (`guardTile`), `:208` (`allow(activity, Category, then, action)`), `:138-146` (`categoryOf`, `categoryOfFeature` : **ni `games` ni `sudoku`**), `:268` (`kidHomeActive`), `:273` (`filterHome`) ; `R/ParentalUi.kt` (clavier du code) ; `S/ParentalScreen.kt` (règles) ; `C/parental/ParentalReports.kt` (corps des rapports : champs additifs) ; w5-02 `TokenSync.KidAllowance`.
- Indicateurs juridiques (§ 6.6) : mineurs, information, pas de sollicitation.

## Fichiers possédés
`R/{ParentalHub,ParentalActivity,ParentalUi}.kt`, `C/parental/{ParentalModel,ParentalEngine,ParentalApi}.kt`, `CT/Parental*Test.kt`, `S/ParentalScreen.kt`. **Hors zone** : `R/tokens/**` (w5-17 : brancher `TokenHub.spendGuard = ParentalHub::tokenGuard` et `TokenHub.pinRequester` **depuis `ParentalHub.init`**, pas en modifiant `TokenHub`), `R/shop/**` (w5-15/16 : ils appellent `ParentalHub.allow(this, Category.PURCHASES, …)` et `ParentalHub.purchaseGuard()`), `C/tokens/**`, `C/parental/ParentalReports.kt` (champs additifs **seulement si** la structure l'exige : sinon utiliser les champs libres existants et le dire), `backend/`.

## Étapes
1. `ParentalModel` : `Category.PURCHASES("purchases", "Achats et jetons", blockable = true)` ; `ChildProfile.kidDailyTokens: Int = 0` (additif, JSON tolérant) ; `ParentalConfig.pinToBuy: Boolean = true` ; migration des configurations existantes : `PURCHASES` ajoutée aux catégories bloquées de chaque profil enfant **une fois** (drapeau `purchasesMigrated`).
2. `ParentalEngine` : `check(PURCHASES)` ; `tokenGuard(cost, today): GuardResult` = pas de profil enfant ⇒ `Allowed` ; profil enfant : `PURCHASES` bloquée ⇒ `Denied("Achats et jetons bloqués pour <prénom>")` ; sinon `KidAllowance.canSpend` ⇒ `Allowed` et décompte, sinon `NeedsPin` ; `purchaseGuard(): GuardResult` = profil enfant ⇒ `Denied("Demandez à un parent")` ; `pinToBuy` ⇒ `NeedsPin` ; sinon `Allowed` ; séance parent ouverte (déverrouillage 30 min) ⇒ `Allowed` partout ; journal d'usage : `UseKind.TOKENS` (minutes non comptées ; événements « dépense », « achat refusé », « achat avec code ») pour le rapport.
3. `ParentalApi` : `config/get|set` transportent les nouveaux champs (`rev` inchangé) ; `GET /api/parental` ajoute `pinToBuy` (sans secret).
4. `ParentalHub` : `tokenGuard`, `purchaseGuard`, `PinRequester` (réutilise `ParentalUi` clavier : « Code parental pour autoriser 5 jetons (cinq) ») ; `categoryOf` : `GamesActivity`, `SudokuActivity` ⇒ `GAMES` ; `ShopActivity` ⇒ `PURCHASES` ; `categoryOfFeature` : `games`, `sudoku` ⇒ `GAMES`, `shop` ⇒ `PURCHASES` ; `filterHome` : tuile `shop` masquée en mode enfant ; `init` : `TokenHub.spendGuard = ::tokenGuard` si `TokenHub` existe (réflexion **non** : dépendance directe si w5-17 fusionné ; sinon laisser le câblage à la fusion et le dire).
5. `ParentalActivity` (TV) : section « Achats et jetons » : interrupteur par profil, allocation (0, 5, 10, 20, 50 par jour), « PIN pour acheter » ; textes explicatifs (« Les jetons servent au Quiz : seconde chance, joker en plus, changer de question. Le Quiz reste jouable sans jetons. »).
6. Rapport parental : compteurs additifs (`tokensSpent`, `purchasesDenied`, `purchasesWithPin`) dans le corps quotidien/hebdomadaire ; téléphone : affichés dans le tableau de bord et « Rapports » (ligne « Jetons : N dépensés »), étiquette **● MESURÉ**.
7. `S/ParentalScreen.kt` : mêmes réglages dans « Règles de la TV » (via `config/set`).
8. Tests JVM : catégorie bloquée par défaut pour un nouvel enfant et migrée pour un ancien ; `tokenGuard` : 0 ⇒ `NeedsPin`, allocation 10 : 3 dépenses de 3 puis `NeedsPin`, changement de jour ⇒ remise à zéro ; `purchaseGuard` ; séance parent ; `pinToBuy` faux ; API `config/set` avec `rev` périmé ⇒ 409 (inchangé) ; rapport contient les compteurs ; `categoryOfFeature("sudoku") == GAMES`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.Parental*' --tests 'castbridge.core.parental.*'   # vert
cd android && gradle --offline :receiver:compileDebugKotlin && gradle --offline :sender:compileDebugKotlin   # compile (SDK)
grep -n 'PURCHASES' android/core/src/main/kotlin/castbridge/core/parental/ParentalModel.kt   # ≥ 1
grep -n '"sudoku"\|SudokuActivity' android/receiver/src/main/kotlin/castbridge/receiver/ParentalHub.kt   # ≥ 2 (lacune corrigée)
grep -rn 'TvConnect\|ServerLink' android/core/src/main/kotlin/castbridge/core/parental   # 0 hit (rien ne part au serveur)
```
Observable (émulateur TV) : profil enfant actif ⇒ tuile Boutique absente, Quiz : seconde chance ⇒ « Demandez à un parent » / clavier du code ; allocation 10 ⇒ deux dépenses de 5 puis code ; parent déverrouillé ⇒ tout passe ; rapport du jour montre « Jetons : 10 dépensés ».

## Cas limites
- Aucun code parental défini (contrôle inactif) : `tokenGuard`/`purchaseGuard` ⇒ `Allowed` (le contrôle n'existe pas) ; la Boutique le rappelle : « Créez un code parental pour protéger les achats » (une fois).
- Enfant qui change le jour de la TV pour remettre l'allocation : règle d'horloge existante (verrou d'1 h max) ; le compteur utilise `TvClock` (monotone) : une journée ne se termine jamais plus tôt qu'en temps réel.
- Deux profils enfants : allocations séparées.

## À ne pas faire
Pas de commit sur les branches partagées ; aucune donnée parentale vers le serveur ; ne pas rendre `LEARN`/`NAVIGATION` bloquables ; ne pas modifier `TokenHub` (w5-17) ni `ShopActivity` (w5-15) ; français.

## Rapport
`STATUT`, API `tokenGuard/purchaseGuard/PinRequester` (pour w5-15/16/17), migration des configurations, captures.
