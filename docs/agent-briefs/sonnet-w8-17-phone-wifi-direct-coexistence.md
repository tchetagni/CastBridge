# w8-17 — Wi-Fi Direct comme voie **alternative** (jamais avec le LAN), veille Bluetooth, activation conditionnée à trois essais matériels

**Vague 8c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w8-14 ; activation conditionnelle).** Conception : § 2 (ligne Wi-Fi Direct), 4.9, 6.2, 14. Branche `claude/sonnet-w8-17`. Rapport : `docs/agent-reports/sonnet-w8-17.md`.

## Objectif
Faire de Wi-Fi Direct une voie du moteur **quand le LAN manque** (pas de box, isolation client, TV sans réseau), en réutilisant la jonction existante, sans jamais la faire tourner en même temps que le LAN, et **ne l'activer par défaut qu'après** trois transferts de 100 Mo réussis sur la TV de référence (sinon elle reste derrière `LaneSwitches.wifiDirect = false`).

## Pourquoi (preuves)
- `WifiDirectLane` existe, désactivée (`core/xfer/Lanes.kt:140-143`, `LaneSwitches`), « aucun essai matériel » (`docs/TRANSFER.md` § 4).
- Jonction côté téléphone : `WifiDirectScreen.kt`, `BtUploadService.kt` (CBTN + `WifiNetworkSpecifier`, API 29+) ; groupe côté TV : `WifiDirectGroup.kt`, délai de création ≤ 8 s (`TvService.kt:275-291`) ; règle « la TV ne crée un groupe que si le propriétaire l'a autorisé ou si elle n'a aucun réseau » (`LinkPlanner.mayStartWifiDirect`, `BtProtocol.kt:417`).
- Même radio que le LAN sur le téléphone (§ 0.1).

## Fichiers possédés
`S/{WifiDirectScreen,ConnectScreens}.kt`, nouveau `S/xfer/DirectJoin.kt`, `R/WifiDirectGroup.kt` (délai de groupe, journal). **Hors zone** : `S/xfer/LaneBringup.kt` (w8-14 : il appelle `DirectJoin.join(ssid, pass): Network?` et `leave()` : convenir par le rapport), `C/xfer/Lanes.kt`.

## Étapes
1. `DirectJoin` : extraire de `BtUploadService`/`WifiDirectScreen` la jonction (`WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork`, API 29+ ; en dessous : non pris en charge, le dire à l'écran), rendre un `Network` **lié** (pour `bindSocket`), `leave()` qui libère la requête ; une seule jonction à la fois ; délai 20 s puis échec propre ; `WifiDirectScreen` et `BtUploadService` appellent `DirectJoin` (plus de duplication).
2. Règle d'exclusivité : `DirectJoin.join` refuse si une voie LAN est active avec `bps > 1 Mo/s` (information passée par l'appelant) ; `LaneBringup` (w8-14) retire la voie LAN avant de rejoindre.
3. Veille Bluetooth : rien à coder ici si la garde 8a est branchée (w8-14) ; **vérifier** sur matériel que `BulkBtLane.idle(true)` envoie bien les `PING` et que la liaison tient 10 min.
4. `WifiDirectGroup` (TV) : journal d'une ligne à la création/suppression du groupe et du canal (2,4/5 GHz si l'API le donne : `WifiP2pGroup.frequency`, API 29+) pour le diagnostic ; pas de changement de politique.
5. **Trois essais matériels** (TV de référence, téléphone, Wi-Fi de la maison **coupé** sur le téléphone) : transfert de 100 Mo × 3 ; mesurer le temps de jonction, le débit, la stabilité ; si les trois réussissent et que le débit ≥ 1 Mo/s : passer `LaneSwitches.wifiDirect = true` **par défaut** (une ligne, dans `Lanes.kt` : demander à w8-02/au coordinateur, hors zone) ; sinon laisser `false` et documenter pourquoi.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin && gradle --offline :receiver:compileDebugKotlin   # compilent
grep -rn "WifiNetworkSpecifier" android/sender/src/main/kotlin | grep -v DirectJoin.kt | wc -l   # 0 (une seule implémentation)
```
Rapport : tableau des trois essais (jonction s, débit Mo/s, incidents), décision d'activation.

## Cas limites
Téléphone API 26-28 : pas de jonction par l'app (message existant « rejoindre à la main »), la voie n'existe pas ; TV qui refuse le groupe (`mayStartWifiDirect` faux) ; perte du groupe en plein transfert → voie retirée, le moteur continue sur Bluetooth, nouvelle jonction par `LaneBringup` après 30 s ; téléphone qui perd Internet pendant la jonction : **l'écran le dit** (texte existant de `WifiDirectScreen` si présent, sinon une phrase courte).

## À ne pas faire
Ne pas activer par défaut sans les trois essais ; ne pas faire tourner LAN et Direct ensemble « pour voir » ; ne pas modifier `LinkPlanner`/CBTN ; aucune clé de groupe dans le journal.

## Rapport
`STATUT`, signature de `DirectJoin`, essais, décision d'activation, compilation.
