# w7-09 — `DiscoveryPlanner` (ordre, délais, fusion, isolation, cache d'adresses), `RoutePolicy` (notation des routes, étiquettes), TXT mDNS v2

> **Amendement (Fable, 2026-10-03, DESIGN-W18)** : le Wi-Fi Direct n'est plus « rang 5, isolation détectée seulement » : c'est la **voie principale dès qu'aucun LAN commun ne répond** (85 % des foyers sans point d'accès) pour **tous** les usages. `RoutePolicy.forUse` doit **déléguer** à `C/link/WdPolicy.route` (w18-02) au lieu de porter sa propre table ; `DiscoveryPlanner` ajoute la source `P2P_DNSSD` (présence `id`/nom sans identifiant) et consomme `PinBook.wdOf` (identifiants connus ⇒ jonction directe). Lire `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 3.2, § 5.1 avant d'exécuter ; le reste du cahier est inchangé.

**Vague 7a · Effort M (≈ 2,5 j) · Modèle : sonnet · Statut PRÊT (en parallèle de w7-01 : implémente l'interface `RouteTable` de son cahier).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 4.1, § 4.3, § 5.2. Branche `claude/sonnet-w7-09`. Rapport : `docs/agent-reports/sonnet-w7-09.md`.

## Objectif
Logique pure de découverte et de choix de route : (1) `DiscoveryPlan` = liste ordonnée d'actions avec délais (§ 4.1) selon le contexte (TV connue ? Bluetooth ? réseau ? isolation ?), consommée par w7-17 ; (2) fusion des candidats (SDP, mDNS v2, cache, QR, toc) par **identité** (`id` 8 hex, adresse BT, IP) ; (3) inférence d'isolation § 4.3 ; (4) `RouteTable` vivante (sondes horodatées, expiration) ; (5) `RoutePolicy.forUse(CONTROL|SYNC|BULK, bytes)` et `RoutePolicy.label(route)` (corrige l'étiquette « Wi-Fi » fausse) ; (6) `MdnsTxt` (codage/décodage du TXT v2, **jamais** d'adresse MAC ni de code d'appareil).

## Pourquoi (preuves)
- `S/TvDiscovery.kt:27-99` (mDNS, résolutions série, TXT `role`), `R/TvService.kt:960-979` (TXT `role=receiver`, `v=0.2`) : v1 sans identité.
- `S/LinkAndroid.kt:75-82` (sonde `/api/hello` 1,2 s), `C/trust/LinkDriver.kt:182-193` (repli de route), `C/tv/BtProtocol.kt:403-432` (`LinkPlanner.plan`) : à **composer**, pas à remplacer.
- `C/remote/RemoteClient.kt:109-113` (`routeName` : tout non-loopback = « Wi-Fi ») et `C/lots/LotPush.kt:102` (`label="Wi-Fi"` même par tunnel BT) : étiquettes fausses signalées dans `bt-tunnel-keepalive.md:35`.
- `R/TvService.kt:278-281` (`LinkInfo` IPv4 de site seulement).

## Fichiers possédés
Nouveaux : `C/link/DiscoveryPlanner.kt`, `C/link/RoutePolicy.kt`, `C/link/MdnsTxt.kt`, `C/link/RouteTableImpl.kt`, `CT/link/DiscoveryPlannerTest.kt`, `CT/link/RoutePolicyTest.kt`, `CT/link/MdnsTxtTest.kt`. Modifié : `C/remote/RemoteClient.kt` (**seulement** `routeName` délègue à `RoutePolicy.label`), `C/lots/LotPush.kt` (**seulement** le `label` par défaut de `HttpLotTransport` calculé par `RoutePolicy.label(base)`). **Hors zone** : `S/**`, `R/**`, `LinkPlanner`.

## Signatures à respecter (contrat pour w7-12, w7-16, w7-17, w7-21)
```kotlin
package castbridge.core.link
data class Candidate(val id: String?, val name: String, val btAddress: String?, val ips: List<String>, val port: Int, val proto: Int?, val caps: Int, val source: Source, val seenAt: Long)
enum class Source { CACHE, SDP, HELLO, MDNS, QR, KNOCK }
sealed class DiscoverAction { data class ProbeCached(val base: String, val timeoutMs: Long); data class BtHello(val address: String, val timeoutMs: Long); data class Mdns(val durationMs: Long); data class WifiDirect(val timeoutMs: Long); object ShowQrHint; object Stop }
data class Context(val tv: SavedTv?, val btUsable: Boolean, val netUp: Boolean, val foreground: Boolean, val isolation: Isolation, val wdPossible: Boolean, val pinned: TvIdentity?)
object DiscoveryPlanner { fun plan(c: Context, now: Long): List<DiscoverAction>; fun merge(cands: List<Candidate>): List<Candidate>; fun isolation(obs: IsolationObs): Isolation; fun sameSubnet(ipA: String, ipB: String, prefix: Int = 24): Boolean }
data class IsolationObs(val helloIps: List<String>, val phoneIp: String?, val lanFailures: Int, val btAlive: Boolean, val netValidated: Boolean, val windowMs: Long)
class RouteTableImpl(val now: () -> Long, val ttlMs: Long = 90_000) : RouteTable { fun probe(route: LinkPlanner.Route, alive: Boolean, ms: Long); fun expire() }
object RoutePolicy { fun forUse(use: RouteUse, table: RouteTable, bytes: Long = 0, isolation: Isolation): LinkPlanner.Route?; fun label(route: LinkPlanner.Route): String; fun label(base: String): String /* "Wi-Fi (réseau commun)", "Ethernet", "Wi-Fi Direct", "Bluetooth (API)" */ ; fun askBeforeBulkBt(bytes: Long): Boolean /* > 50 Mo */ ; fun etaMs(bytes: Long, route: LinkPlanner.Route): Long }
object MdnsTxt { fun encode(id: String, proto: Int, caps: Int, port: Int, version: String, role: String = "receiver"): Map<String, String>; fun decode(txt: Map<String, ByteArray>): Candidate? }
```

## Étapes
1. `plan()` : ordre et délais du § 4.1 avec `LinkConfig` (w7-01) ; TV connue : `ProbeCached` ∥ `BtHello` (les deux en tête, le consommateur les lance en parallèle), puis `Mdns(4 s)` si LAN raté, `WifiDirect` si `ISOLATED`, `Stop` ; TV inconnue : `Mdns`, `ShowQrHint` ; jamais de `startDiscovery` BT (hors « Ajouter ma TV »).
2. `merge()` : clé = `id` sinon `btAddress` sinon première IP ; priorité des sources `HELLO > QR > MDNS > SDP > CACHE` pour les champs ; tri : identité épinglée d'abord, puis `caps & CAP_SYNC`, puis nom.
3. `isolation()` : règle § 4.3 exacte ; `SUSPECTED` sans `netValidated`.
4. `RouteTableImpl` : TTL 90 s, `alive(CONTROL)` = LAN > WD > Bluetooth (CBSY) > BluetoothTunnel ; `alive(BULK)` = LAN > WD > BluetoothTunnel (< 5 Mo) > Bluetooth (CBT1).
5. `RoutePolicy.label` : `127.*` ⇒ « Bluetooth (API) », `192.168.49.*` ⇒ « Wi-Fi Direct », sinon « Wi-Fi (réseau commun) » — « Ethernet » n'est pas détectable côté téléphone depuis l'URL : garder « Wi-Fi (réseau commun) » et documenter ; `etaMs` : LAN 3 Mo/s, WD 2 Mo/s, BT 150 Ko/s (constantes injectables, mesurées par w7-24).
6. `MdnsTxt` : clés `id, proto, caps, port, v, role` ; ≤ 255 octets ; `decode` tolère v1 (sans `id`) ⇒ `Candidate(id=null, proto=null)`.
7. Tests (≥ 45) : plans par contexte (table figée), fusion, isolation (vrais/faux positifs), TTL, labels, TXT v1/v2, `sameSubnet`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.link.DiscoveryPlannerTest' --tests 'castbridge.core.link.RoutePolicyTest' --tests 'castbridge.core.link.MdnsTxtTest' --tests 'castbridge.core.remote.*' --tests 'castbridge.core.lots.*'   # vert
grep -rn 'startDiscovery' android/core/src/main/kotlin/castbridge/core/link/ | wc -l   # 0
```

## Cas limites
Téléphone en point d'accès (TV en `192.168.43.x`) : même sous-réseau ⇒ pas d'isolation ; IPv6 dans mDNS ⇒ ignorée ; `id` de 7 caractères ⇒ TXT invalide ⇒ v1.

## À ne pas faire
Pas d'Android ; ne pas réécrire `LinkPlanner` ; aucune adresse MAC ni code d'appareil dans le TXT.

## Rapport
`STATUT`, table contexte → plan, signatures.
