# w5-17 — CastBridge-TV : porte-jetons (`TokenHub`, clé HMAC dérivée de la clé d'installation) et Quiz des Millions avec commodités à jetons, « points de défi », partie découverte en essai

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (w5-18 en parallèle)
> **Groupe : W5d-1** (vague W5d) · prérequis : w5-02, w5-03, w5-04, w4-03 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui
> **Amendement (architecte, 2026-10-02)** : lire d'abord `docs/coordination/ADDENDUM-W5-PORTE-JETONS-REPRISE-2026-10-02.md` (§ 2, § 3, § 6, § 7). Il **prévaut** sur ce cahier : `WalletKeys` fournit aussi la marque d'existence `WalletMark` (`files/tokens/wallet.mark`, mac sous la clé dérivée) ; `TokenHub` instancie `TokenWallet(file, keys, mark)`, expose `cause()` (`lost|broken|foreign_chain|none`) et un événement de reprise pour w5-12/w5-16 ; un porte-jetons vide ou illisible ne s'ouvre que par un bon d'ouverture serveur (`fresh=1`, résultat `REOPENED`, ancien fichier renommé `wallet.txt.broken-<n>`) ; textes français de l'addendum § 3 (« Porte-jetons illisible. Vos jetons sont en sécurité au serveur… », « Porte-jetons rétabli : N jetons disponibles. ») ; `QuizBoosts.charge(b, gameId, purchaseNo)` avec `gameId` unique et sérialisé dans la partie sauvée, compteur recalé par `wallet.spendCount` ; événement `tokens_reopened` ; observable émulateur : effacer `wallet.txt` ⇒ message de reprise (jamais « Aucun jeton ») ⇒ relais ⇒ rétabli. Prérequis supplémentaire : correctif cœur `claude/sonnet-w5-02-fix` (et w5-03 pour la signature de `charge`).

**Vague 5d · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après w5-02, w5-03, w5-04 ; w4-03 fusionné : `KeystoreWrapper`, `InstallKeyStore` ; w5-18 en parallèle via `TokenHub.spendGuard`).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 6 (lire en entier), § 15 (ligne Quiz). Branche `claude/sonnet-w5-17`. Rapport : `docs/agent-reports/sonnet-w5-17.md`. **Décision du propriétaire (P4) : seul le Quiz consomme des jetons ; Sudoku et Échecs ne changent pas.**

## Objectif
(1) `TokenHub` : instancie `TokenWallet` (`files/tokens/wallet.txt`) avec un `WalletKeyProvider` qui dérive la clé HMAC de la **clé privée d'installation** (via `InstallKeyStore` + `SecretWrapper` de w4-01/03 ; si le Keystore est en repli `plain`, la dérivation marche quand même : la propriété « lié à l'installation » tient, la propriété « protégé par le coffre » non : état exposé) ; expose `balance()`, `summary()`, `spend(item)` **derrière** un `spendGuard: (cost) -> GuardResult` (fourni par w5-18 : parental ; par défaut : toujours autorisé avec confirmation) ; implémente `QuizBoosts` (w5-03) ; (2) `QuizActivity` : solde visible en partie Millionnaire (hors Entraînement et Duel), boutons **« Seconde chance (5 jetons) »** (écran `LOST_OFFER`, 10 s, focus sur « Non merci » par défaut), **« Joker en plus (2) »** à côté d'un joker consommé, **« Changer de question (3) »** ; **confirmation** à chaque fois (texte `TokenPolicy.confirmText`, chiffres et lettres) ; solde insuffisant ⇒ « Jetons insuffisants : rechargez depuis la Boutique » (jamais un lien direct vers un achat pendant la partie) ; menu « avec mise » renommé **« Défi en points »** avec l'unité **« points de défi »** ; (3) **partie découverte** en essai (`TrialPolicy.tasterAllowed()`, 3 par jour, parcours Culture générale, aucune commodité) ; (4) `Games.all` : statut de la carte Quiz « Meilleur score : N · 23 jetons » ; `Games.visible()` : en essai, Quiz visible avec le sous-titre « Partie découverte (3 par jour) » (règle `Feature.QUIZ_TASTER` de w5-04).

## Pourquoi (preuves)
- `R/QuizActivity.kt:303-343` (menus : « avec mise » 50/100/200 « jetons »), `:455` (`MillionaireScreen` : rangée de jokers, `wireFocus` `:529`), `:679-684` ; `R/QuizHub.kt` (`VirtualWallet` instancié ; question sources) ; `R/Games.kt:43-58` (`visible()`, `Games.all`) ; `C/quiz/QuizBoosts.kt` (w5-03), `C/tokens/**` (w5-02), `C/owner/TrialPolicy.kt` (w5-04 : `quizTasterPerDay`), w4-01/03 (`InstallKeyStore.load(): InstallKey?`, `SecretWrapper.unwrap`).
- Décision d'architecte : jamais de mise redistribuée en jetons achetés : les « points de défi » restent gratuits et séparés.

## Fichiers possédés
Nouveaux `R/tokens/TokenHub.kt`, `R/tokens/WalletKeys.kt` ; modifiés `R/QuizActivity.kt`, `R/QuizHub.kt`, `R/Games.kt`, `R/QuizViews.kt` (si présent), `R/QuizScreens/**` (si w3-11 fusionné). **Hors zone** : `R/shop/**` (w5-15/16 : `ShopHub` consomme `TokenHub.wallet` **si** w5-17 est fusionné ; sinon il a un provisoire), `R/ParentalHub.kt` (w5-18 : fournit `spendGuard`), `R/SudokuActivity.kt`, `R/ChessActivity.kt`, `C/**`, `S/**`.

## Étapes
1. `WalletKeys.provider(ctx): WalletKeyProvider` : lit `InstallKeyStore` (w4-01/03), déballe la clé privée (`SecretWrapper`), `WalletKey.derive(priv)` ; la clé privée en clair **n'est jamais gardée** au-delà de la dérivation (tableau remis à zéro) ; la clé HMAC dérivée est gardée en mémoire du processus ; `label` (`keystore|plain|none`).
2. `TokenHub.init(ctx)` (depuis `TvApp`/`TvService` au même endroit que `RentalHub.ensure`) ; `wallet: TokenWallet` ; `spendGuard` (par défaut `GuardResult.Allowed`) ; `spend(item, activity, onDone)` : `TokenPolicy.cost`, `spendGuard(cost)` (`Allowed | NeedsPin | Denied(fr)` : `NeedsPin` ⇒ `ParentalHub` affiche son clavier, via un `PinRequester` fourni par w5-18 ; d'ici là, `Denied`), dialogue de **confirmation** (`confirmText`), `wallet.spend` ⇒ `TvConnect.event("tokens_spend", item)` ; `boosts(): QuizBoosts` (= `available` si production et `balance ≥ cost`, `charge` = `spend` sans second dialogue : la confirmation **est** le dialogue de `spend`).
3. `QuizHub` : `QuizRoom(wallet = ChallengePointsWallet(), boosts = TokenHub.boosts())` ; en essai : `boosts = NoBoosts` ; `TrialTaster` : compteur `castbridge_quiz/taster-<jour>` ; `/api/quiz/open` en essai : seulement solo culture générale ; `/quiz/*` reste fermé (politique w5-04).
4. `QuizActivity` : écran `LOST_OFFER` (« Mauvaise réponse. Seconde chance pour 5 jetons (cinq) ? Solde : 23 » ; boutons « Non merci » (focus) / « Oui, 5 jetons » ; compte à rebours 10 s visible) ; boutons « Joker en plus » et « Changer de question » dans la rangée des jokers (grisés avec la raison quand indisponibles : « déjà utilisé », « jetons insuffisants », « version d'essai ») ; indicateur de solde discret en haut à droite (pas de clignotement, pas d'animation) ; `EndScreen` : « avec jetons » quand `boosted` ; menus « Défi en points » / « points de défi » ; Entraînement et Duel : rien.
5. `Games.all` (Quiz) : statut et sous-titre ; `visible()` : règle essai (w5-04).
6. Tests : cœur déjà couvert ; ici, vérification manuelle sur émulateur + un test JVM pour `TrialTaster` (compteur par jour, remise à zéro) si extrait en cœur-compatible dans `R/` (sinon le dire).

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (SDK)
cd android && gradle --offline :core:test --tests 'castbridge.core.quiz.*' --tests 'castbridge.core.tokens.*'   # vert, inchangé
grep -n '"jetons"' android/receiver/src/main/kotlin/castbridge/receiver/QuizActivity.kt | grep -i 'mise\|stake'   # 0 hit (la mise parle de points de défi)
grep -rn 'Boutique' android/receiver/src/main/kotlin/castbridge/receiver/QuizActivity.kt | grep -i 'startActivity\|Intent'   # 0 hit (aucun lien d'achat pendant la partie)
grep -rn 'TokenHub' android/receiver/src/main/kotlin/castbridge/receiver/SudokuActivity.kt android/receiver/src/main/kotlin/castbridge/receiver/ChessActivity.kt   # 0 hit (P4)
```
Observable (émulateur TV, bon de jetons de test installé via `POST /api/tokens/install`) : partie Millionnaire ⇒ mauvaise réponse ⇒ offre de seconde chance ⇒ confirmation ⇒ reprise ⇒ solde décrémenté ; refus par défaut après 10 s ; Entraînement : aucune offre ; essai : 3 parties découverte puis « revenez demain » ; TV réinstallée : porte-jetons illisible ⇒ solde 0 et message de resynchronisation.

## Cas limites
- Partie sauvegardée avant la vague puis reprise : pas de `boostsUsed` ⇒ zéro (sérialisation tolérante, w5-03).
- Perte de focus pendant `LOST_OFFER` (téléphone qui envoie une commande) : l'offre reste 10 s puis finit la partie ; aucune dépense sans OK explicite.
- `WalletKeyProvider.key() == null` : « jetons indisponibles sur cette TV » ; les commodités sont grisées ; le reste du Quiz normal.

## À ne pas faire
Pas de commit sur les branches partagées ; aucune dépense sans confirmation ; aucune mise en jetons achetés ; aucun jeton aux Échecs ni au Sudoku ; aucun achat pendant une partie ; aucune animation incitative ; français.

## Rapport
`STATUT`, points d'intégration (`spendGuard`, `PinRequester`) pour w5-18, ce que `ShopHub` doit appeler (pour w5-16), captures.
