# w7-04 — Moteur de synchronisation (`SyncEngine`, `SyncDomain`, `DeltaLog`) et domaines `act`, `lots`, `lib`, `xfer`

**Vague 7a · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (en parallèle de w7-02 : coder contre les signatures de son cahier ; si `SyncFrames.kt` n'est pas fusionné, définir les types localement dans `private` et le dire).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 7.2, § 7.3. Branche `claude/sonnet-w7-04`. Rapport : `docs/agent-reports/sonnet-w7-04.md`.

## Objectif
Le moteur commun des deux côtés (séquences, empreintes, delta/ack, snapshots, idempotence, priorités, budgets Bluetooth), l'interface `SyncDomain`, le journal borné `DeltaLog`, et quatre adaptateurs purs : `ActDomain` (état d'activation + preuve W6 à la demande), `LotsDomain` (manifeste TV ↔ `DeliveryQueue`), `LibDomain` (index de bibliothèque), `XferDomain` (grand livre et file des transferts, w7-10 fournit `TransferLedger`).

## Pourquoi (preuves)
- `C/lots/DeliveryQueue.kt:100-127` (`reconcile` : le manifeste TV est la vérité, idempotent) ; `C/lots/TvLotStore.kt:16-48` (`TvManifest`) : la source du domaine `lots` existe, il suffit de la **pousser** au lieu d'attendre `GET /api/lots`.
- `C/tv/Library.kt`, `C/tv/LibraryStore.kt` (index TV) ; `C/tv/TvInfo.kt` (`TvFile`) : entrées de `lib`.
- `C/owner/KeyStatusJson.kt` (champs `GET /api/activation`) et DESIGN-W6 § 3.3 (`TvProof`, enveloppe `proof`) : entrées de `act` ; si `C/owner/TvProof.kt` (w6-03) est absent, `ActDomain` transporte la preuve comme **texte opaque** `proof=<cbx1…>` et le dit.
- `C/tv/TransferQueue.kt` (`TransferQueueModel`, en mémoire) et w7-10 (`TransferLedger`) : entrées de `xfer`.

## Fichiers possédés
Nouveaux : `C/link/SyncEngine.kt`, `C/link/SyncDomain.kt`, `C/link/DeltaLog.kt`, `C/link/domains/ActDomain.kt`, `C/link/domains/LotsDomain.kt`, `C/link/domains/LibDomain.kt`, `C/link/domains/XferDomain.kt`, `CT/link/SyncEngineTest.kt`, `CT/link/DomainsATest.kt` ; `docs/SYNC-PROTOCOL.md` **§ 3 seulement**. **Hors zone** : `C/lots/**`, `C/tv/**` (lecture seule : les adaptateurs reçoivent des interfaces injectées).

## Signatures à respecter (contrat pour w7-05, w7-13, w7-18)
```kotlin
package castbridge.core.link
interface SyncDomain { val dom: Dom; val owner: Side /* TV, PHONE, SPLIT */
    fun state(): DomState                                   // seq + digest de l'état canonique
    fun delta(sinceSeq: Long, max: Int): SyncFrame.Delta    // snapshot si l'historique manque
    fun apply(delta: SyncFrame.Delta): ApplyResult          // idempotent ; ApplyResult(applied, newSeq, conflicts)
    fun priority(): Int }
enum class Side { TV, PHONE, SPLIT }
class DeltaLog(val dom: Dom, val maxEntries: Int = 500, val maxAgeMs: Long = 7 * 86_400_000L, store: TextStore) { fun append(entry: ByteArray, now: Long): Long; fun since(seq: Long): List<ByteArray>?; val seq: Long }
class SyncEngine(domains: List<SyncDomain>, val side: Side, val caps: Int, val now: () -> Long, val budget: Budget = Budget()) {
    fun hello(): SyncFrame.Hello
    fun onFrame(f: SyncFrame, from: Peer): List<SyncFrame>     // réponses à envoyer
    fun localChanged(dom: Dom): SyncFrame.Notify?               // à appeler quand un détenteur a persisté un changement (coalescé 250 ms par l'appelant)
    fun pending(): List<SyncFrame>                               // PULL à émettre après un HELLO/NOTIFY dont le digest diffère, dans l'ordre des priorités
    fun ageOf(dom: Dom): Long? }
data class Budget(val btBytesPerSec: Int = 8_192, val deferLibOverBytes: Int = 65_536)
interface TextStore { fun load(): String?; fun save(text: String) }   // SafeFile côté Android, mémoire en test
```
Entrées = texte `clé=valeur` par ligne (convention `HelloInfo`), UTF-8, ≤ 4 Kio ; clé d'identité `id=` ou `name=` obligatoire ; `op=put|del`.

## Étapes
1. `DeltaLog` persistant (texte : une ligne `seq\tms\tbase64(entrée)`), troncature par taille/âge, `since()` ⇒ `null` si l'historique est perdu (⇒ snapshot).
2. `SyncEngine` : règles (1)-(7) du § 7.2 ; `onFrame(Hello/Digest/Notify)` ⇒ compare digests ⇒ `pending()` ; `onFrame(Pull)` ⇒ `Delta` paginé (`more`) ; `onFrame(Delta)` ⇒ `apply` ⇒ `Ack` ; conflit (écriture d'un champ non détenu) ⇒ **le détenteur gagne**, journal `conflict` ; budget Bluetooth : `pending()` reporte `lib` si > `deferLibOverBytes` et route BT (paramètre `route` de `pending(route)`).
3. `ActDomain` (TV détient) : entrée unique `id=act` : `state=production|trial|grace|degraded|locked|suspended`, `label`, `endsAt`, `kid`, `proofSeq` ; `Pull` avec entrée `nonce=` ⇒ `Delta` contenant `proof=<cbx1…>` (fourni par une lambda `proofFor(nonce)` injectée, w7-13) ; côté téléphone `apply` appelle `onActivation(state)` (lambda) et, si `proof` présent, `onProof(nonce, token)`.
4. `LotsDomain` (SPLIT) : TV détient les entrées `id=<feature:scope>` (`version, sha, edition, rentalEnd, rejectedReason`) ; téléphone détient `queue:<id>` (`state`) ; `apply` côté téléphone ⇒ `reconcileHook(manifest)` (w7-18 branche `DeliveryQueue.reconcile`).
5. `LibDomain` (TV détient) : snapshot ≤ 2 000 entrées `name, size, mtime, folder, kind` ; deltas `add/remove/rename` ; digest sur la liste triée.
6. `XferDomain` (SPLIT) : TV détient `x:<id>` (`name,total,done,lane,state,err`), téléphone `q:<id>` (`name,size,state`) ; progression coalescée 1 Hz par l'appelant.
7. Tests (≥ 50) : digests égaux ⇒ rien ; delta paginé ; historique perdu ⇒ snapshot ; `apply` deux fois = même état ; conflit ⇒ détenteur gagne ; priorités ; budget BT ; chaque domaine : snapshot + delta + digest stable à l'ordre près ; `ActDomain` sans `TvProof` (texte opaque) ; `installId` différent ⇒ `reset()` (caches jetés).
8. `docs/SYNC-PROTOCOL.md` § 3 : table des domaines, entrées, détenteur, règles.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.link.SyncEngineTest' --tests 'castbridge.core.link.DomainsATest'   # vert, ≥ 50
grep -rn 'import android\|import castbridge.sender\|import castbridge.receiver' android/core/src/main/kotlin/castbridge/core/link/ | wc -l   # 0
```

## Cas limites
TV verrouillée : `hello()` sans `CAP_SYNC` et 0 domaine ; `Delta` d'un domaine inconnu ⇒ `Error(2)` non fatale ; entrée > 4 Kio ⇒ rejet de l'entrée seule ; horloge qui recule ⇒ `DeltaLog` garde l'ordre des `seq`, pas des `ms`.

## À ne pas faire
Pas de transport ici (sockets, HTTP) ; pas de domaine `par/shop/set/ico` (w7-05) ; ne pas modifier `DeliveryQueue`, `TvLotStore`, `LibraryStore`.

## Rapport
`STATUT`, signatures finales, table domaine → entrées, cas non couverts.
