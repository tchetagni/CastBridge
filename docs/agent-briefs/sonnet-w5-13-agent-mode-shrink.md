# w5-13 — CastBridge (téléphone) : mode « Point focal » recentré (clés d'activation, vente de bons de recharge, confirmation de commandes en espèces) ; plus de location, plus de scellement

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (D7 contact)
> **Groupe : W5c-1** (vague W5c) · prérequis : w4-13, w4-14, w5-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*SaleFlow*' --tests '*LedgerSync*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 5c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w4-13 et w4-14 fusionnés dans leur version réduite, w5-01 ; D7 pour le contact du reçu).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 0 (P1, P5), § 5.2 (b), § 5.4, § 14 ; `SONNET-WAVE5-INDEX.md` « Changements aux cahiers w4 » (w4-11, w4-13, w4-14). Branche `claude/sonnet-w5-13`. Rapport : `docs/agent-reports/sonnet-w5-13.md`. **Décision du propriétaire (P1) : « pour l'instant le commercial vendra les clés d'activation »** ; P5 : espèces et bons.

## Objectif
Le mode « Point focal » vend **trois choses** : des **clés** (essai, production : inchangé depuis W4-C), des **bons de recharge** (stock de serials affecté par le propriétaire ; vente = ligne de journal), et **confirme des commandes en espèces** (code `CB-XXXX-XX` montré par le client ; l'app vérifie en ligne si possible, encaisse, journalise, synchronise). Tout ce qui concernait la **location** dans w4-13 (scellement des lots, livraison de lots, `RentalSpec`, maître déballé de la délégation) est **retiré**. Le journal chaîné et la synchronisation (w4-14) sont **réutilisés tels quels**, avec les nouveaux `item`.

## Pourquoi (preuves)
- w4-13 : `S/focal/{FocalActivity,FocalStore,FocalScreens,FocalSealer,FocalDelivery,PendingSales}.kt`, `C/sales/SaleFlow.kt` ; w4-14 : `S/focal/{LedgerFile,LedgerSyncJob,LedgerScreen,ReceiptShare}.kt`, `C/sales/{LedgerFile,LedgerSync}.kt` ; w4-11 : `SalesLedger.Entry.item` (syntaxe), `Receipt.text` ; w5-07 : `POST /api/v1/agent/orders/confirm`, `GET /api/v1/agent/vouchers/stock`, `GET /agent/orders/{ref}` ; w5-01 : `OrderRef.normalize`, `VoucherCode.serial`.
- W4-C § 5 (maître enveloppé pour l'agent) : **caduc** (P1/P2).

## Fichiers possédés
`S/focal/**` (supprimer `FocalSealer.kt` et la partie « lots » de `FocalDelivery.kt` ; ajouter `ConfirmOrderScreen.kt`, `VoucherSaleScreen.kt`, `VoucherStock.kt`), `C/sales/SaleFlow.kt`, `C/sales/LedgerSync.kt`, `CT/sales/{SaleFlowTest,LedgerSyncTest}.kt`. **Hors zone** : `C/sales/{SalesLedger,PriceGrid,Receipt}.kt` (w4-11/w5-01 : utiliser), `C/owner/Delegation.kt` (w5-14 retire `master=` : d'ici là, **ignorer** le champ s'il existe et ne jamais l'ouvrir), `S/shop/**`, `S/MainActivity.kt` (w5-11), `R/**`, `backend/`, docs.

## Étapes
1. `SaleFlow` : articles = `cle-essai|<j>`, `cle-production|<j>`, `bon|<article>|<serial>`, `commande|<ref>|<montant>` ; **aucune** branche location ; entrées : délégation (`sellVouchers`, `confirmOrders`, `maxConfirmXafPerDay`, quotas), grille, stock de bons (serials affectés, état local vendu/non), demande d'appareil (pour les clés), commande vérifiée (pour la confirmation) ; sorties : `IssueSpec` (clé) **ou** `LedgerEntry` seul (bon, commande) ; refus FR : « la délégation n'autorise pas la vente de bons », « serial inconnu / déjà vendu », « plafond journalier de confirmations atteint », « commande expirée », « montant différent de la commande ». Tests ≥ 15.
2. `VoucherStock` : fichier `files/focal/vouchers.json` (serials des lots affectés, importés par le texte `batch-<id>-stock.csv` ou par `GET /agent/vouchers/stock` ; état local `vendu` dès la ligne de journal) ; écran « Mes bons » (par lot : restants, vendus, « signaler une perte » = ligne `NOTE` + message au propriétaire).
3. `VoucherSaleScreen` : choisir le lot/article, taper ou scanner le **serial** (jamais le code secret : l'app ne le connaît pas), prix de la grille, « Espèces reçues », ligne `SALE item=bon|…|<serial>` → reçu partagé (texte `Receipt.text`) ; la carte est remise au client.
4. `ConfirmOrderScreen` : saisir `CB-XXXX-XX` (`OrderRef.normalize`) ; **en ligne** : `GET /agent/orders/{ref}` → article, montant, TV masquée ; **hors ligne** : le client montre son écran (montant), l'agent saisit l'article et le montant **tels qu'affichés** (le serveur refusera un montant faux) ; « Espèces reçues » ; ligne `SALE item=commande|<ref>|<montant>` ; envoi immédiat `POST /agent/orders/confirm` si en ligne, sinon à la prochaine synchronisation (`LedgerSync` transporte la ligne : format inchangé) ; réponse affichée (« Confirmée : la location part vers le client » / refus FR) ; reçu.
5. `FocalActivity` : sections « Vendre une clé » (inchangé), « Vendre un bon », « Confirmer une commande », « Mes bons », « Mon journal », « Ma délégation » (affiche `sellVouchers`/`confirmOrders`/plafond) ; « Préparer » = catalogue + grille + stock de bons (**plus de lots**).
6. `LedgerSync` : réponse du serveur peut porter `orderConfirmations: [{seq, accepted, reason}]` : affichées dans le journal (une confirmation refusée **n'est pas** effacée : ligne `NOTE` « refus serveur : … » et l'agent rembourse le client ou corrige).
7. Supprimer `FocalSealer.kt`, les imports `RentalKeys`/`RentalSpec` de `S/focal/**` ; `FocalDelivery` ne livre que l'activation avec ticket (Bluetooth/Wi-Fi).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.sales.*'   # vert
cd android && gradle --offline :sender:compileDebugKotlin   # compile (SDK)
grep -rn 'RentalKeys\|RentalSpec\|seal(' android/sender/src/main/kotlin/castbridge/sender/focal   # 0 hit
test ! -f android/sender/src/main/kotlin/castbridge/sender/focal/FocalSealer.kt && echo "scellement retiré"
grep -rn 'openMaster\|master=' android/sender/src/main/kotlin/castbridge/sender/focal   # 0 hit
grep -rn '237\|XAF [0-9]' android/sender/src/main/kotlin/castbridge/sender/focal   # 0 hit
```
Observable (émulateur, délégation de test, faux serveur) : vente d'un bon → journal → synchronisation → `GET /agent/vouchers/stock` montre `SOLD` ; confirmation d'une commande de test → réponse acceptée → la commande passe `FULFILLED` côté serveur.

## Cas limites
- Agent dont la délégation n'a ni `sellVouchers` ni `confirmOrders` : les deux sections sont masquées ; il vend des clés seulement.
- Confirmation hors ligne d'une commande qui expire avant la synchronisation : refus serveur ⇒ `NOTE` ; l'app prévient dès la saisie « cette commande expire le … : synchronisez avant ».
- Même commande confirmée deux fois (deux agents) : la seconde est refusée ; l'agent rembourse.

## À ne pas faire
Pas de commit sur les branches partagées ; aucune location vendue ni scellée ; ne jamais stocker un code de bon secret ; aucun montant/nom/numéro en dur ; ne pas modifier le format du journal ; français.

## Rapport
`STATUT`, articles finaux du journal, écrans, ce qui dépend de D7 et de w5-07.
