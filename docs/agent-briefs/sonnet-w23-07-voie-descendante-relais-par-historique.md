# w23-07 — Voie descendante : objets signés et scellés du serveur vers une TV hors ligne, relayés par un téléphone de son historique ; clé des ordres distincte ; accusés signés par la TV ; nouvelles actions ; interrupteur
<!-- routage architecte 2026-10-04 (W23-B) -->
> **Modèle : sonnet (4.6)** · escalade : audit Opus **obligatoire** (canal de contrôle à distance, liste blanche, clés) · statut : **ATTEND w23-06** (reçus, boîte) ; s'appuie sur les ordres différés existants (`docs/ORDRES.md`, V60, `S/OrdersRuntime.kt`)
> **Groupe : W23-B** (ordre 4) · porte : `cd backend && mvn -o test -Dtest='castbridge.server.orders.**'` + `cd android && gradle :core:test --tests 'castbridge.core.policy.*' :sender:testDebugUnitTest`
> **Jauge : ≈ 500 k jetons entrée / 28 k sortie** (effort M, ≈ 1,7 j)

**Conception** : `DESIGN-W23B-…` § 9 (entier), § 6.3. Branche `claude/w23-07-voie-descendante`. Rapport : `docs/agent-reports/sonnet-w23-07.md`.

## Fichiers possédés
- **Serveur, nouveaux** : `backend/.../orders/{RelayHistory,RelayRouter,DownItem,TvAckVerifier}.java`, migration `V<plus haut + 1>__relay_history.sql` (`relay_history`, colonnes additives d'état par objet et téléphone) ; tests.
- **Serveur, zone additive** : `PolicyCatalog` (actions § 9.3 et liste `NEVER`), `OrderService` (routage ≤ 3 téléphones, reprise après 72 h sans accusé, expiration, règle des deux personnes au-delà de 50 TV, débit ≤ 20 ordres / TV / jour), `ConfiguredOrderSigner` (**clé distincte** `orders-signing.key`, `POLICY` seule ; refus de démarrer si elle est identique à la clé des licences), `OrderController` (liste de confiance rapportée par le téléphone, retrait), évènements W23 (`ORDER_*`, `RECEIPT_SENT`), interrupteur `CASTBRIDGE_RELAY_DOWN_ENABLED`.
- **Cœur et téléphone** : `android/core/src/main/kotlin/castbridge/core/policy/{PolicyActions (ajouts), DownQueue}.kt`, `tools/orders/actions.json` ; `S/OrdersRuntime.kt` (file descendante par TV : bornes, priorités, durée de vie, budget § 6.3, rapport de la liste de confiance et des retraits, effacement de la file d'une TV retirée).
- **Interdit** : écrans, `AndroidManifest.xml` (aucune permission), `R/` (w23-08), `ownerlib`.

## Critères d'acceptation
- Parité des listes d'actions Kotlin/Java/JSON (test qui échoue si elles divergent) ; les 20+ identifiants interdits refusés **signés** des deux côtés.
- `RelayRouterTest` : TV hors ligne connue de 3 téléphones ⇒ remise au plus récent ; pas d'accusé 72 h ⇒ remise au suivant ; deux accusés ⇒ le premier gagne ; accusé non signé par `install_pub` connue ⇒ informatif seulement ; téléphone retiré ⇒ plus jamais choisi.
- `DownQueueTest` : bornes (16 objets / 64 Ko par TV, 256 Ko au total), priorités P0-P3, éviction, expiration, aucun envoi en itinérance, mobile compté ⇒ P0 seulement dans 32 Ko / jour (mutation ⇒ échec).
- Clé des ordres = clé des licences ⇒ démarrage refusé ; ordre visant 51 TV sans second administrateur ⇒ 202 `PENDING`.
