# w7-01 — `LinkManager` (cœur) : machine d'états de haut niveau de la liaison téléphone ↔ TV

**Vague 7a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 3.2, § 5.1, § 5.2. Branche `claude/sonnet-w7-01`. Rapport : `docs/agent-reports/sonnet-w7-01.md`. Règles communes : `docs/COORDINATION.md`, en-tête de `docs/agent-briefs/SONNET-WAVES-INDEX.md`.

## Objectif
Un `LinkManager` **pur** (aucun import Android) par TV, qui enveloppe la `LinkMachine`/`LinkDriver` existantes **sans les modifier**, ajoute les états `NO_TV / DISCOVERING / PAIRING / LINKED_BT / LINKED_LAN / LINKED_BOTH / DEGRADED / OFFLINE`, la notion de **route de contrôle** et de **route de masse**, et produit un `LinkSnapshot` que les écrans et la synchronisation consomment. Testé avec une fausse horloge, une fausse TV et de faux transports.

## Pourquoi (preuves)
- `C/trust/LinkMachine.kt:21-42` (`LinkState`), `:128-147` (`reduce`), `:193-215` (`nextAttempt`) : états fins, hystérésis et courbes à **garder**.
- `C/trust/LinkDriver.kt:78` (`Step(view, nextInMs, session)`), `:129-154` (`step`), `:169-197` (garde-vivant et repli de route avec le même jeton) : la boucle d'une étape à **envelopper**.
- `C/tv/BtProtocol.kt:403-432` (`LinkPlanner.Route` : Lan, Direct, Bluetooth, BluetoothTunnel) : les routes existantes.
- `S/TvLink.kt:251-260` : la boucle Android n'appelle que `driver.step()` ; W7 la fera appeler `LinkManager.step()` (w7-16).

## Fichiers possédés
Nouveaux : `C/link/LinkManager.kt`, `C/link/LinkConfig.kt`, `C/link/LinkSnapshot.kt`, `CT/link/LinkManagerTest.kt`. **Hors zone** : `C/trust/**` (lecture seule), `S/**`, `R/**`.

## Signatures à respecter (contrat pour w7-04, w7-09, w7-16, w7-18)
```kotlin
package castbridge.core.link
enum class LinkPhase { NO_TV, DISCOVERING, PAIRING, LINKED_BT, LINKED_LAN, LINKED_BOTH, DEGRADED, OFFLINE }
enum class RouteUse { CONTROL, SYNC, BULK }
data class LinkConfig(val btPingForegroundMs: Long = 20_000, val btPingBothMs: Long = 60_000, val lanLongPollMs: Long = 25_000,
    val offlineAfterMs: Long = 10 * 60_000, val lostGraceMs: Long = 40_000, val cachedIpProbeMs: Long = 2_500, val btConnectMs: Long = 6_000)
data class LinkSnapshot(val phase: LinkPhase, val tv: SavedTv?, val view: LinkView?, val control: LinkPlanner.Route?, val bulk: LinkPlanner.Route?,
    val session: LinkSession?, val isolation: Isolation = Isolation.NONE, val blocker: String? = null, val since: Long)
enum class Isolation { NONE, SUSPECTED, ISOLATED }
class LinkManager(val tv: SavedTv?, private val driver: LinkDriver, private val routes: RouteTable, private val cfg: LinkConfig = LinkConfig(), private val now: () -> Long) {
    fun step(trigger: Trigger): Step            // Step(snapshot, nextInMs: Long?)
    fun onRouteProbe(route: LinkPlanner.Route, alive: Boolean, ms: Long)   // nourri par w7-09/w7-16
    fun onPairing(active: Boolean)              // PairFlow en cours
    val snapshot: LinkSnapshot
}
/** Table des routes vivantes, maintenue par le planificateur de découverte (w7-09) ; ici une interface minimale. */
interface RouteTable { fun alive(use: RouteUse): LinkPlanner.Route?; fun all(): List<Pair<LinkPlanner.Route, Long>> }
```

## Étapes
1. `LinkPhase` dérivée de `LinkDriver.Step` + `RouteTable` : table de correspondance **exacte** du § 5.1 (`Connected(LAN/DIRECT)` ⇒ `LINKED_LAN` ou `LINKED_BOTH` si un PING BT vivant < `btPingBothMs`·3 ; `Connected(BLUETOOTH)` ⇒ `LINKED_BT` ; `Degraded` ⇒ `DEGRADED` ; `Reconnecting/TvUnreachable/Connecting` ⇒ `DISCOVERING` ; refus définitifs ⇒ `DISCOVERING` + `blocker` = `view.title` ; `NoTv` ⇒ `NO_TV`) ; `OFFLINE` après `offlineAfterMs` sans aucune route ou quand `foreground=false` sans route.
2. `nextInMs` : celui du `LinkDriver.Step`, **borné** par le garde-vivant de la route de contrôle (§ 5.2) ; `OFFLINE` ⇒ `null` (attendre un réveil).
3. `bulk` = `RouteTable.alive(BULK)` ; `control` = `alive(CONTROL)` ; jamais `BluetoothTunnel` pour `CONTROL` si `Bluetooth` (CBSY) est vivant.
4. Persistance : `LinkSnapshot.encode()/decode()` (phase, tvName, since ; **jamais** de jeton ni d'adresse complète) pour reconstruire la puce au redémarrage comme `LinkMachine.Model.decode` (`LinkMachine.kt:107-122`).
5. Tests (≥ 40 cas) : chaque transition du diagramme § 5.1 ; TV éteinte 48 h (nombre d'étapes borné) ; bascule LAN→BT→LAN sans changement de `session` ; `blocker` jamais réessayé ; `OFFLINE` en fond, retour `DISCOVERING` sur `Trigger.APP_OPENED` ; horloge qui recule (pas d'exception).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.link.LinkManagerTest'      # vert, ≥ 40 cas
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/link/ | wc -l         # 0
git diff --stat origin/integration/agents -- android/core/src/main/kotlin/castbridge/core/trust/ | tail -1   # vide (rien modifié)
```

## Cas limites
Deux TV enregistrées : un `LinkManager` par TV, aucun état partagé (pas d'`object`) ; `tv == null` ⇒ `NO_TV` stable ; `driver.step` qui lève une exception ⇒ `DISCOVERING` + journal (pas de crash) ; un `Step.session` dont `tv.address` ≠ `tv` ⇒ ignoré.

## À ne pas faire
Ne pas modifier `LinkMachine`, `LinkDriver`, `LinkText` ; ne pas écrire de texte utilisateur (les textes viennent de `LinkText` existant et de `LinkTexts` w7-08) ; pas de coroutine, pas de fil ; pas de synchro ici (w7-04).

## Rapport
`STATUT`, signatures finales, table phase ↔ `LinkState`, cas non couverts, durée.
