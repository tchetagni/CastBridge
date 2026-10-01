STATUT: TERMINÉ
- 2026-10-01 00:00 | départ : base integration/agents, enveloppe cbx1 (type `order`, portée POLICY) réutilisée telle quelle | commit —
- 2026-10-01 | noyau core : PolicyActions (liste fermée), PolicyState, PolicyEngine, PolicyGate, OrderFrames/TvOrderReceiver, OrderQueue/OrderCourier ; 36 tests verts (`castbridge.core.policy.*`)
- 2026-10-01 | serveur : V60, OrderService/Controller/Admin, page /admin/orders, parité d'octets avec les vecteurs ; 15 tests Java verts
- 2026-10-01 | docs/ORDRES.md, appairage téléphone↔serveur, parité Kotlin/Java ; suites complètes : core 1646 tests (seuls échecs : 10 `LearnLotsTest`, préexistants), backend 89 tests, 0 échec
RÉSUMÉ (10 lignes) :
1. Livré : `docs/ORDRES.md` (format, 14 actions de la liste fermée, états, séquences, protocole, diagrammes, limites) ; noyau `castbridge.core.policy` (PolicyActions, PolicyState, PolicyEngine, PolicyGate, OrderQueue/OrderCourier, trames Bluetooth 16–21, TvOrderReceiver, HttpOrderServer/StreamOrderLink) ; enveloppe `cbx1` réutilisée telle quelle (aucun second format).
2. Serveur (`backend/.../orders`, migration V60, éteint par défaut) : file d'ordres signés, séquence attribuée sous verrou, routes `GET /api/v1/orders`, `POST /orders/pair`, `POST /orders/acks`, API et page `/admin/orders`, audit chaîné ; `OrderSigner` à remplacer par la clé du module licences (`@Primary`).
3. Tests : `castbridge.core.policy.*` 40 verts (rejeu, séquence, portée, cible, expiration, horloge, refus puis suivants, coupure et reprise, ancien téléphone/TV, liste blanche, suspension sans perte) ; Java 15 verts (octets identiques au vecteur partagé et à une chaîne de référence « appareil » Kotlin, concurrence des séquences, visibilité par téléphone, audit).
4. Avant/après : core 1646 tests dont 10 `LearnLotsTest` en échec (déjà en échec avant, lot `maternelle`) ; backend 89/89.
5. Non compilé : `:sender` et `:receiver` (plugin Android introuvable) → `OrdersRuntime.kt`, `PolicyHub.kt`, manifeste ; non testé sur matériel ; non exécuté : `MySqlContainerTest` (Docker), vérificateur Python (non étendu aux actions).
6. À brancher (§ 13 de ORDRES.md) : `PolicyHub.init` (anneau de clés, facteurs), canal propriétaire `…0004` côté TV, `PolicyGate` dans le calcul du `GateState`, écran « À propos > Politiques appliquées », appel de `learnTvCode` à la lecture du `DEVICE_INFO`.
7. À valider par le propriétaire : texte de l'avis d'usage (proposition en § 11, à faire valider juridiquement) ; durées de validité par défaut (30 jours) ; liste des indicateurs de fonctionnalité.
8. Limites dites : accusés non signés (informatifs), ordres signés mais non chiffrés (aucune donnée personnelle), pas de rappel d'un ordre déjà reçu (envoyer l'ordre contraire), appartenance licence/groupe à fournir par le module des licences.
9. Rien déployé, aucun accès au serveur, aucun secret dans le dépôt (clés de test dérivées de textes publics).
10. Branche `claude/deferred-orders`, base `origin/integration/agents`.

