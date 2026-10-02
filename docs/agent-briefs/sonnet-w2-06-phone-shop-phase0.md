# w2-06 — Boutique « Louer des leçons » sur le téléphone, phase 0 (preuve de paiement manuelle)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : REMPLACÉ par w5-11/w5-12 : ne pas lancer
> **Groupe : —** (vague W2) · prérequis : aucun · porte : `—`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non

**Vague 2 · Effort L (≈ 4-5 j) · Statut BLOQUÉ** — questions au propriétaire : **D8** (secret maître des locations : serveur dédié, recommandé) et **D9** (grille de prix XAF par bouquet/durée, numéro marchand MoMo/OM, validation de la phase 0 manuelle). Sans réponse, l'agent construit l'écran et le modèle avec des montants **absents** (« prix communiqué par le vendeur ») et le flux de commande, et pose `QUESTION:`. Dépend de w2-10 (serveur) pour le bout en bout ; peut être développé contre un faux serveur. Branche `claude/sonnet-w2-06`. Rapport : `docs/agent-reports/sonnet-w2-06.md`.

## Objectif
Depuis CastBridge (téléphone) : voir les bouquets du catalogue signé (titre, classe, durée `rentalDays`, taille, prix s'il existe) → « Louer N jours » crée une **commande** (référence courte) → l'écran affiche comment payer (numéro, montant, référence) et un champ « Référence de la transaction » → la commande est envoyée au serveur (ou mémorisée hors ligne et envoyée plus tard) → quand le serveur dit « clé émise », le téléphone télécharge l'activation + les lots scellés et les **livre à la TV par Bluetooth ou Wi-Fi** en un seul bouton « Livrer à la TV » ; état visible : envoyée / en attente de confirmation / payée / clé reçue / livrée.

## Pourquoi (preuves)
- `S/RentalDeliveryActivity.kt:64-92` : le client doit recevoir des `.lot`, un `catalog.json`, taper `produit@période` ; `:48-50,74` : **Wi-Fi seulement** (« TV non jointe pour le moment (Wi-Fi nécessaire) ») alors que `S/LotsRuntime.kt`/`S/BtUploadService.kt` livrent déjà des lots par Bluetooth.
- `C/lots/RentalDelivery.kt:102` : « Cette location n'est pas encore activée sur la TV : envoyez d'abord la clé d'activation » (ordre inconnu du client).
- `C/lots/ServerBundleCatalog.kt:7-9`, `C/lots/SignedBundleCatalog.kt` : catalogue signé avec `rentalDays`, déjà vérifié côté outils ; `C/lots/RentalDurations` : durée exacte par bouquet.
- `S/MainActivity.kt:108` : entrée « Locations » sans explication.
- Aucun prix dans le dépôt (`grep -rn 'prix\|price' android/core/src/main backend/src/main` → seulement les prix de jetons LLM).
- Audit : UX-5, MO-1, MO-2, MO-6 ; `docs/RENTAL-LOTS.md` § 15 (conception cible non réalisée).

## Fichiers possédés
Nouveaux `S/shop/ShopScreen.kt`, `S/shop/OrderScreen.kt`, `S/shop/ShopRuntime.kt` ; nouveau `C/lots/ShopOrder.kt` (modèle pur : états, transitions, sérialisation JSON, référence `CB-XXXX-XX` sans ambiguïté O/0) ; `C/lots/RentalDelivery.kt` ; `S/RentalDeliveryActivity.kt` (devient « avancé », ou supprimé si tout est repris) ; `S/LotsRuntime.kt` (transport Bluetooth pour une livraison de location) ; nouveau `android/core/src/test/kotlin/castbridge/core/lots/ShopOrderTest.kt` ; nouveau `docs/SHOP.md`. **Hors zone** : `S/MainActivity.kt` (w2-03 déplace l'entrée : convenir du nom « Louer des leçons »), serveur (w2-10), `RentalHub` TV.

## Étapes
1. `ShopOrder` (cœur) : `data class Order(ref, deviceCode, bundleId, days, amountXaf: Int?, currency="XAF", provider: MANUAL|…, providerRef: String?, status: DRAFT|SENT|AWAITING_CONFIRMATION|PAID|ISSUED|DELIVERED|CANCELLED, createdAt, activationText?, lots: List<LotRef>)`, machine d'états testée, persistance `SafeFile` dans `filesDir/shop/orders.json`.
2. `ShopScreen` : liste des bouquets depuis `ServerBundleCatalog` (rafraîchi sur demande, jamais au démarrage ; hors ligne : dernier catalogue) ; filtre par classe de l'élève (profil existant) ; carte « Louer 30 jours » ; prix affiché seulement si présent dans le catalogue (champ **à définir avec w2-10** : `priceXaf` optionnel dans `SignedBundleCatalog`, additif, signé).
3. `OrderScreen` : instructions de paiement (numéro marchand depuis le catalogue ou `BuildConfig.OWNER_CONTACT` ; **jamais en dur**), champ référence, bouton « J'ai payé », statut, bouton « Livrer à la TV » (activation d'abord, puis lots, par le transport disponible : Bluetooth via `LotsRuntime`, sinon Wi-Fi via `RentalDelivery`) ; file d'attente hors ligne (`JobScheduler` déjà utilisé par `LotsRuntime`).
4. `ShopRuntime` : client HTTP des endpoints de w2-10 (`/api/v1/shop/...`) derrière une interface `ShopApi` avec une implémentation **factice** pour les tests et l'émulateur.
5. `docs/SHOP.md` : parcours, états, ce qui est manuel en phase 0, ce qui change en phase 1.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.ShopOrderTest' --tests 'castbridge.core.lots.*'   # vert
cd android && gradle --offline :sender:compileDebugKotlin   # compile (si SDK)
grep -rn '237\|MoMo\|Orange' android/sender/src/main/kotlin/castbridge/sender/shop   # 0 hit en dur (numéros/noms viennent du catalogue ou de BuildConfig)
```
Observable (émulateur, faux `ShopApi`) : créer une commande → référence → saisir « MP2410… » → statut « En attente de confirmation » → (faux serveur) « Clé émise » → « Livrer à la TV » installe la clé puis les lots sur une TV émulée (`GET /api/rental` montre le contrat).

## Cas limites
- Deux commandes pour le même bouquet pendant une location active = **renouvellement** (même `period`) : l'écran le dit.
- Lot libre (CC BY-SA) : jamais proposé à la location (`RentalPolicy.refusal`).
- Téléphone hors ligne au moment de payer : la commande reste `DRAFT` localement, envoyée à la prochaine connexion.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret, aucun montant ni numéro en dur, aucun prestataire choisi ; textes en français ; « CastBridge » / « CastBridge-TV ».

## Rapport
`STATUT: BLOQUÉ` + `QUESTION:` (D8, D9), ce qui est construit malgré tout, champ `priceXaf` convenu avec w2-10.
