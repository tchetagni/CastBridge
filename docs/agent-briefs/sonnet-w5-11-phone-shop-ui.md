# w5-11 — CastBridge (téléphone) : onglet « Boutique » (leçons à louer, jetons du Quiz, payer par code de recharge ou en espèces chez le point focal, commandes, reçus, ma TV)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (w5-12 en parallèle, contrat ShopRuntimeView)
> **Groupe : W5c-1** (vague W5c) · prérequis : w5-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non
> **Amendement W16 (architecte, 2026-10-03)** : lire `docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md` § 1.1, § 1.3, § 4.3. « 30 jours » n'est plus une durée affichée en dur : la fiche d'un bouquet ouvre le **sélecteur à trois groupes** (« Sans durée précise : 30 jours » · jours 1/3/7/14 · heures d'utilisation 1/3/6/12/24/48/96) de **w16-11** (`S/RentalPickerScreen.kt`, `PickerModel`), avec la date de fin réelle et sans conversion heures ↔ jours ; la ligne « Il vous reste … » suit l'unité du contrat (`TvRentalView.unit`, w16-03).

**Vague 5c · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après w5-01 ; w5-12 en parallèle : les deux codent contre `ShopApi` et une interface `ShopRuntimeView` convenue ici).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 5.1 (textes des écrans), § 6.1, § 6.6, § 7 (lire en entier). Branche `claude/sonnet-w5-11`. Rapport : `docs/agent-reports/sonnet-w5-11.md`. **Décisions du propriétaire** : P3 (boutique), P4 (jetons = Quiz seulement), P5 (espèces et bons seulement : **aucun** écran de mobile money).

## Objectif
Un onglet **« Boutique »** (Compose) entre « Apprendre » et « Parental » ; l'action « Locations » de la barre disparaît (« Livraison avancée » reste dans ⋯ via w5-12). Cinq sections : **Leçons à louer**, **Jetons du Quiz**, **Payer**, **Mes commandes / Mes reçus**, **Ma TV**. Tout est lisible hors ligne depuis le cache ; commander exige la connexion ; un bon saisi hors ligne est gardé. Aucun montant ni numéro en dur : tout vient du catalogue et de la grille signés.

## Pourquoi (preuves)
- `S/MainActivity.kt:106-131` (actions « Activer la TV », « Locations » ; onglets) ; `S/LearnScreen.kt:58-62` (sous-onglets : placer un lien « Louer d'autres leçons » dans « Leçons ») ; `S/GamesScreen.kt:38-42` (`PHONE_GAMES` : ligne « Jetons du Quiz : N · Acheter » sur la carte Quiz) ; `S/Theme.kt` (`Cb`, `CastTheme`, typographie) ; `branding/design-tokens.json` ; `C/shop/**` (w5-01 : `ShopCatalog`, `ShopPolicy`, `ShopOrder`, `ShopApi`, `FakeShopApi`, `VoucherCode.normalize`, `OrderRef`).
- UX-5 de l'audit (entrée « Locations » sans explication).

## Fichiers possédés
Nouveaux `S/shop/ShopScreen.kt`, `S/shop/ShopPayScreen.kt`, `S/shop/ShopOrdersScreen.kt`, `S/shop/ShopTokensScreen.kt`, `S/shop/VoucherEntry.kt`, `S/shop/ShopTexts.kt` ; modifiés `S/MainActivity.kt`, `S/LearnScreen.kt`, `S/GamesScreen.kt`. **Hors zone** : `S/shop/{ShopRuntime,HttpShopApi,TvShopCache,ShopDelivery,ShopSyncJob,PendingVoucherRelay}.kt` (w5-12 : consommer via l'interface `ShopRuntimeView` définie **dans `ShopScreen.kt`** et implémentée par w5-12 : `catalog: StateFlow<List<ShopEntry>>`, `orders`, `tokens: TokenView(balanceServer, onTv, pending)`, `tv: TvView(code, lastContact, requestCached, rentals, online)`, `fun quote(item)`, `fun order(item, payment)`, `fun redeem(code)`, `fun refresh()`, `fun deliver(ref)`, `fun receipt(ref)`), `S/focal/**`, `C/**`, `R/**`.

## Étapes
1. `ShopScreen` : en-tête (état réseau, TV liée « TV XXXX-… · dernier contact il y a 2 h »), navigation à 5 sections (chips) ; **Leçons à louer** : filtre par classe du profil, cartes `ShopEntry` : titre, « 2 lots · 4,2 Mo », « 30 jours », prix ou « prix non communiqué », badge « Aperçu gratuit inclus », état « En location jusqu'au … » / « Louer » / « Durée non disponible en ligne » (> 60 j) ; détail : matières, ce que l'essai montre, « ne peut pas être prolongée au-delà de 60 jours », « il manque X Mo sur la TV » le cas échéant.
2. **Jetons du Quiz** : solde (« 23 jetons · dont 20 sur la TV ») ; paquets de la grille (`tokenPacks`) ; bloc « À quoi servent les jetons ? » = les trois commodités + « Le Quiz reste entièrement jouable sans jetons » + « Les jetons n'expirent pas » (ou la règle de la grille) + « Non convertibles, non remboursables une fois utilisés ».
3. `ShopPayScreen(item, quote)` : récapitulatif ; deux boutons **« Saisir un code de recharge »** → `VoucherEntry` (4 groupes de 4, clavier majuscules, `VoucherCode.normalize` à la volée : groupe douteux souligné, message « Vérifiez le 3e groupe »), **« Payer en espèces chez votre point focal »** → commande créée → écran `CB-XXXX-XX` en très grand, « Montrez ce code à votre point focal et payez <montant> XAF. Valable 72 h. La location arrivera sur ce téléphone dès qu'il aura confirmé. », bouton Partager (`ACTION_SEND`), « Point focal le plus proche : <contact D7 ou rien> » ; lien « Conditions de vente » (écran de w5-19 ; en attendant, texte « disponible prochainement »).
4. `ShopOrdersScreen` : liste (ref, article, état FR, date), détail : chronologie (créée, payée, clé reçue, à livrer (TV hors de portée), livrée), bouton « Livrer à la TV » (→ `deliver(ref)`), « Vérifier » (→ `refresh`), reçu (code `R-…`, texte, Partager), « Annuler » si en attente.
5. **Ma TV** : code, protection de la clé d'installation (`installKeyProtection` de `GET /api/activation`), locations en cours (phrases `RentalEngine`), jetons sur la TV, « Demande en cache du … » si hors de portée.
6. États de politique (`ShopPolicy`) : essai ⇒ boutons de commande désactivés « Disponible avec une clé de production (voir votre point focal) » ; réduit ⇒ « Renouvelez d'abord la clé » + bon de clé autorisé ; profil enfant actif sur le téléphone (onglet Parental : `ParentalEngine`/ledger local : si l'app sait qu'un profil enfant est actif) ⇒ écran « Demandez à un parent » ; hors ligne ⇒ « Connexion nécessaire pour commander ».
7. `MainActivity` : onglet « Boutique » ; retirer l'action « Locations » ; `LearnScreen` : lien « Louer d'autres leçons » ; `GamesScreen` : ligne jetons sur la carte Quiz (« Jetons du Quiz : 23 · Acheter »).
8. Accessibilité : `contentDescription` partout, montants en chiffres **et** lettres dans les confirmations (`FrenchNumbers` de w5-02 si fusionné, sinon helper local à retirer plus tard), TalkBack testé sur l'émulateur.
9. Prévisualisation Compose avec `FakeShopApi` ; aucun test instrumenté exigé ; test JVM des textes (`ShopTexts`) : aucun montant en dur, aucun « MoMo ».

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin   # compile (SDK)
cd android && gradle --offline :core:test --tests 'castbridge.core.shop.*'   # inchangé, vert
grep -rn 'Locations"' android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt   # 0 hit (entrée retirée)
grep -rni 'momo\|orange money\|mobile money\|237\|XAF [0-9]' android/sender/src/main/kotlin/castbridge/sender/shop   # 0 hit
grep -rn 'Saisir un code de recharge\|Payer en espèces chez votre point focal' android/sender/src/main/kotlin/castbridge/sender/shop   # ≥ 2
```
Observable (émulateur, `FakeShopApi`) : parcours location avec bon ⇒ commande `FULFILLED` ⇒ « Livrer à la TV » ; parcours espèces ⇒ code `CB-…` ⇒ (faux serveur confirme) ⇒ clé reçue ; achat de jetons ⇒ solde mis à jour ; essai ⇒ boutons grisés avec la phrase.

## Cas limites
- Pas de TV liée : la boutique s'affiche (catalogue, jetons au serveur) ; « Liez une TV pour louer » sur les cartes de location ; les jetons **peuvent** être achetés (compte = licence du téléphone) : si le téléphone n'a pas d'activation (D14 : téléphone ouvert sans clé), les jetons sont **indisponibles** (« liez une TV activée ») : le dire.
- Deux TV liées : sélecteur dans « Ma TV » ; la commande nomme la TV.
- Grille absente : « Tarifs indisponibles pour le moment » ; aucun bouton actif.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun montant, numéro, nom en dur ; aucun écran de paiement mobile ; aucun appel HTTP direct (passer par `ShopRuntimeView`) ; ne pas toucher `S/focal/**` ; français ; « CastBridge » / « CastBridge-TV ».

## Rapport
`STATUT`, interface `ShopRuntimeView` finale (pour w5-12), captures d'émulateur (`docs/img/shop/phone-*.png` si possible), textes à relire par le propriétaire.
