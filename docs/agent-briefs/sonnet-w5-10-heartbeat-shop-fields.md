# w5-10 — Battement de cœur : champs du porte-jetons et de la boutique (client cœur + serveur), recoupement avec les bons livrés

**Vague 5b (fin) · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT (après protect-05 **fusionné** et w5-08).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 6.5 (battement de cœur), § 9 (protection). Branche `claude/sonnet-w5-10`. Rapport : `docs/agent-reports/sonnet-w5-10.md`.

## Objectif
La TV ajoute à son battement de cœur quatre champs **additifs et facultatifs** : `tokensSeq` (dernière dépense), `tokensMac` (16 hex du résumé du porte-jetons), `walletState` (`ok|unreadable|absent`), `shopCatalogAt` (horodatage du catalogue de boutique gardé). Le serveur les stocke et recoupe `tokensSeq` avec `token_grant.spent_reported` : un recul ⇒ anomalie `TOKEN_REPLAY` (via `ShopAnomalies`), un état `unreadable` répété 3 fois ⇒ `TOKEN_WALLET_UNREADABLE`. **Rien n'est bloqué.**

## Pourquoi (preuves)
- `C/device/DeviceReport.kt` et `B/devices/DeviceReport.java`, `B/devices/DeviceService.java` (battement de cœur ; protect-05 y a ajouté l'empreinte de signature et les drapeaux d'environnement : **suivre exactement le même motif additif**) ; `C/tokens/TokenWallet.summary()` (w5-02) ; `B/shop/ShopAnomalies` (w5-09) ; `B/shop/tokens/TokenGrantService` (w5-08).

## Fichiers possédés
`C/device/DeviceReport.kt` (champs additifs, valeurs fournies par un `fun interface WalletSummaryProvider` injecté ; **sans** dépendre de `R/`), `B/devices/DeviceReport.java`, `B/devices/DeviceService.java` (persistance additive : colonnes nullable sur la table des appareils si protect-05 a posé une migration « drapeaux » : ajouter la vôtre « plus haut + 1 » **seulement** pour ces colonnes), `BT/DevicesApiTest.java`. **Hors zone** : tout le reste (`R/TvConnect.kt` branche le fournisseur : w5-16 ; `B/shop/**`).

## Étapes
1. Kotlin : `DeviceReport` gagne `tokensSeq: Long?`, `tokensMac: String?`, `walletState: String?`, `shopCatalogAt: String?` ; sérialisation JSON **omise si null** ; test cœur : rapport sans ces champs identique à l'octet près à avant.
2. Java : lecture tolérante, colonnes, `DeviceService.heartbeat` : si `tokensSeq` < dernier connu pour cet appareil ⇒ `ShopAnomalies.record(TOKEN_REPLAY, …)` ; compteur `unreadable`.
3. Tests : battement avec/sans champs ; recul ⇒ anomalie ; ancien client sans champs ⇒ rien.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.device.*'   # vert
cd backend && ./mvnw -q -o test -Dtest='DevicesApiTest'   # vert
git diff --stat -- android/core/src/main/kotlin/castbridge/core/device/DeviceReport.kt backend/src/main/java/castbridge/server/devices/   # seules ces zones
```

## Cas limites
- Appareil téléphone (pas de porte-jetons) : champs absents.
- TV sans boutique (module éteint) : champs présents, ignorés par le serveur si `ShopAnomalies` absent (garde `Optional`).

## À ne pas faire
Pas de commit sur les branches partagées ; aucun blocage d'appareil ; ne pas toucher aux champs de protect-05 ; pas de donnée personnelle.

## Rapport
`STATUT`, noms exacts des champs JSON (pour w5-16, w5-20), migration prise (ou non).
